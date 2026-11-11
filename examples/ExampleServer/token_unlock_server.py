#!/usr/bin/env python3
"""Reference backend for Aliro BLE credential exchange with Last Lock Mobile SDK.

The lock is the *reader*, the phone is the *user device*, and this server is the
*Credential Issuer*. An Access Document (AD) is an issuer-signed statement that
binds a phone credential's public key to a validity interval and access data.
It is not an unlock command or the credential's private key. The phone stores
the AD and presents it to the lock during a BLE unlock. See the Aliro 1.0
specification, sections 7 and 8.4, for the document and presentation formats:
https://csa-iot.org/wp-content/uploads/2026/02/26-42802-001_Aliro_1.0_specification.pdf

This example has three separate operations:

1. ``POST /v1/configure`` prepares a provisioned lock to recognize this issuer.
   After the lock reports its reader public key to Link, the server signs a
   reader certificate and asks Link to deliver the reader-group identifier,
   certificate, issuer public key, and accepted access element identifier to
   the lock. A successful response means delivery was queued, not completed.
2. ``POST /v1/issue-unlock-pass`` signs an AD for a public key created by the
   mobile SDK. The phone keeps the matching private key. The response includes
   the AD and reader-group trust material: the lock uses the former to check
   the phone, and the SDK uses the latter to authenticate the lock.
3. ``POST /v1/install-mode`` asks Link to enter or exit install mode. Link
   builds the signed install payload and pushes it on the plane stream. If a
   firmware image is pending, an enter request includes that DFU. A successful
   response means the command was queued. The phone delivers it and waits
   until the lock advertises the new mode.

The AD does not name a device ID. Every lock configured to accept this issuer
key and access element may accept the same pass. This sample does not
authenticate HTTP callers or implement per-user/per-lock authorization.

The server persists two private signing keys and its reader-group identifier.
Do not delete TOKEN_UNLOCK_DATA_DIR after configuring locks: they will not
trust newly issued passes from a replacement issuer key.

The only Link dependency is the generated public Link v2 API
(``proto/link_v2.proto``) Python client in this directory's ``gen/`` folder.
Device IDs are Link v2 IDs: 32 lowercase hex characters. The mobile SDK
reports each discovered lock's ``linkDeviceId`` in that form. Set ``ORG_NAME`` to the hosted
Link organization slug; the server connects to that org over TLS on port 443.
Set ``LINK_GRPC_ADDR`` to dial another address, such as a local org at
``localhost:3001``, and ``LINK_GRPC_SECURE=false`` for a plaintext connection.

Note: Example code only — not for production use. This code demonstrates SDK integration and is 
provided “as is,” without warranties or guarantees of security, correctness, or support. It omits 
production safeguards and has not been validated for use in a production access-control system. 
You are responsible for implementing and independently validating authentication, authorization, 
key management, and other security controls before deploying any system based on this example.
"""

import base64
import hashlib
import json
import os
import re
import secrets
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

import cbor2
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature
from cryptography.x509.oid import NameOID
from flask import Flask, jsonify, request
import grpc

# Public generated stubs from proto/link_v2.proto, defined in https://github.com/last-lock/seamless-link
sys.path.insert(0, str(Path(__file__).with_name("gen")))
import link_v2_pb2  # noqa: E402
import link_v2_pb2_grpc  # noqa: E402
import lockmodes_pb2  # noqa: E402

# A Link v2 device ID: 32 lowercase hex characters, opaque to clients.
DEVICE_ID_RE = re.compile(r"[0-9a-f]{32}")


# Read an optional setting without repeating os.environ access at each call site.
def env(name, default=""):
    return os.environ.get(name, default)


# Link credentials remain on this backend. Never put LINK_API_KEY in a mobile app.
LINK_API_KEY = env("LINK_API_KEY")
# The org slug selects its hosted Link API. Integrators need not supply a URL.
ORG_NAME = env("ORG_NAME").strip()
# LINK_GRPC_ADDR overrides the hosted target. Local Link listens on localhost:3001
# without TLS, so set LINK_GRPC_SECURE=false for that address.
LINK_GRPC_ADDR = env("LINK_GRPC_ADDR").strip()
LINK_GRPC_TARGET = LINK_GRPC_ADDR or f"{ORG_NAME}.link.lastlock.com:443"
LINK_GRPC_SECURE = env("LINK_GRPC_SECURE", "true").strip().lower() not in {"0", "false", "no"}
# This directory is the server's durable identity: keys and reader-group state
# must be preserved or configured locks will no longer trust new passes.
# By default, use ./token-unlock-dir in the directory where the server is started.
DATA_DIR = Path(env("TOKEN_UNLOCK_DATA_DIR") or "token-unlock-dir")
# Aliro Access Documents can contain multiple named pieces of access data.
# During the exchange, the lock asks the phone for specific element names in
# its DeviceRequest. This sample uses one name: /v1/configure puts ELEMENT_ID
# in the lock's access_document_element_ids, and issue_access_document puts
# that same name in the signed IssuerSignedItem (CBOR field "3"). If they do
# not match, the phone cannot supply the item the lock requested, and the lock
# rejects an unrequested item. This is a data-element name, NOT a device ID,
# reader-group ID, or authorization check. Changing it requires reconfiguring
# existing locks and issuing new passes that use the new name.
# This sample uses one global name for every configured lock. An integrator
# who needs narrower access must design distinct accepted elements and issuer
# policy; merely passing a device ID to the issuance API would not restrict
# where this signed AD can be used.
ELEMENT_ID = env("TOKEN_UNLOCK_ACCESS_ELEMENT_ID", "sample.token-unlock")
# Keep requested pass validity within the seven-day window used by this example.
# An ephemeral SDK credential still requires a pass no longer than two minutes.
MAX_PASS_DURATION_SECONDS = 7 * 24 * 60 * 60
# Link rejects an install-mode enter outside this window. 300 is the default.
MIN_INSTALL_SECONDS = 300
MAX_INSTALL_SECONDS = 2 * 60 * 60

if not LINK_API_KEY or not ORG_NAME:
    raise RuntimeError("LINK_API_KEY and ORG_NAME must be set")
if not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", ORG_NAME):
    raise RuntimeError("ORG_NAME must be a lowercase Link organization name, not a URL")
if not 0 < len(ELEMENT_ID.encode()) <= 128:
    raise RuntimeError("TOKEN_UNLOCK_ACCESS_ELEMENT_ID must be 1..128 bytes")

app = Flask(__name__)
DATA_DIR.mkdir(mode=0o700, parents=True, exist_ok=True)


# Encode a P-256 key in the 65-byte form used by Link and Aliro.
def point(public_key):
    """P-256 public key in the 65-byte SEC1 format expected by Link/Aliro."""
    return public_key.public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)


# Load one of the server's durable signing keys or make it on first startup.
def load_or_create_key(filename):
    """Load a persisted P-256 signing key, or create it on first startup."""
    path = DATA_DIR / filename
    if path.exists():
        key = serialization.load_pem_private_key(path.read_bytes(), password=None)
        if not isinstance(key, ec.EllipticCurvePrivateKey) or not isinstance(key.curve, ec.SECP256R1):
            raise RuntimeError(f"{path} is not a P-256 private key")
        return key
    key = ec.generate_private_key(ec.SECP256R1())
    path.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    os.chmod(path, 0o600)
    return key


# Derive the 20-byte Reader CA identifier that Profile0000 expects in a leaf AKI.
def ca_key_id(key):
    # The mobile SDK uses this SHA-1 key id when validating the
    # reader certificate restored from the lock's Profile0000 representation.
    return hashlib.sha1(point(key.public_key())).digest()


# Load or issue the self-signed Reader CA certificate associated with the persisted CA key.
def load_or_create_reader_ca(key):
    path = DATA_DIR / "reader-ca-cert.der"
    if path.exists():
        certificate = x509.load_der_x509_certificate(path.read_bytes())
        try:
            subject_key_id = certificate.extensions.get_extension_for_class(x509.SubjectKeyIdentifier).value.digest
        except x509.ExtensionNotFound:
            subject_key_id = b""
        if point(certificate.public_key()) == point(key.public_key()) and subject_key_id == ca_key_id(key):
            return certificate
        # Keep the CA private key (and therefore the lock's trust anchor), but
        # replace an old certificate whose Subject Key Identifier does not
        # match the Profile0000 certificate iOS reconstructs during BLE.
    now = datetime.now(timezone.utc)
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "issuer")])
    certificate = (x509.CertificateBuilder().subject_name(subject).issuer_name(subject)
        .public_key(key.public_key()).serial_number(1)
        .not_valid_before(now - timedelta(days=1)).not_valid_after(now + timedelta(days=3650))
        .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
        .add_extension(x509.KeyUsage(False, False, False, False, False, True, False, False, False), critical=True)
        .add_extension(x509.SubjectKeyIdentifier(ca_key_id(key)), critical=False)
        .sign(key, hashes.SHA256()))
    path.write_bytes(certificate.public_bytes(serialization.Encoding.DER))
    os.chmod(path, 0o600)
    return certificate


# These keys serve opposite directions of trust. The lock uses the Credential
# Issuer public key to verify an AD from the phone. The phone uses the Reader
# CA certificate to verify the lock's reader certificate. The private keys
# stay on this backend and must remain stable across restarts.
ISSUER_KEY = load_or_create_key("credential-issuer-key.pem")
READER_CA_KEY = load_or_create_key("reader-ca-key.pem")
READER_CA_CERT = load_or_create_reader_ca(READER_CA_KEY)


# Load the stable reader-group ID and configured locks.
def load_state():
    path = DATA_DIR / "state.json"
    if not path.exists():
        return {"reader_group_id": "", "configured_devices": []}
    state = json.loads(path.read_text())
    if "configured_devices" not in state:
        state["configured_devices"] = [device_id for device_id, configured in state.pop("devices", {}).items() if configured]
    state.setdefault("reader_group_id", "")
    state.pop("granted_device_ids", None)  # Ignore grants written by older versions.
    return state

STATE = load_state()

# Write the server's non-secret state after a group or lock configuration changes.
def save_state():
    """Persist non-secret state needed to issue compatible passes after restart."""
    path = DATA_DIR / "state.json"
    path.write_text(json.dumps(STATE))
    os.chmod(path, 0o600)


# Identify the reader trust configuration shared by this server's locks.
def reader_group_id():
    """Return a stable group ID used by the SDK to find a lock's Reader CA.

    This ID is supplied to both the lock and the phone. It selects trust
    material; knowing the ID alone does not grant access.
    """
    if not STATE["reader_group_id"]:
        STATE["reader_group_id"] = base64.b64encode(secrets.token_bytes(16)).decode()
        save_state()
    return base64.b64decode(STATE["reader_group_id"])


# Certify the lock's own reader key so the phone can recognize a trusted lock.
def reader_certificate(reader_public_key):
    """Sign the lock's reported public key with the persistent Reader CA.

    This certificate authenticates the *lock* to the SDK. It is distinct from
    the Credential Issuer signature on the phone's Access Document.
    """
    now = datetime.now(timezone.utc)
    certificate = (x509.CertificateBuilder()
        .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "subject")]))
        .issuer_name(READER_CA_CERT.subject).public_key(reader_public_key)
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(days=1)).not_valid_after(now + timedelta(days=3650))
        # This order is part of the signed X.509 TBS bytes. Link compresses
        # the cert to Profile0000, and iOS reconstructs it in this exact order.
        .add_extension(x509.AuthorityKeyIdentifier(ca_key_id(READER_CA_KEY), None, None), critical=False)
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .add_extension(x509.KeyUsage(True, False, False, False, False, False, False, False, False), critical=True)
        .sign(READER_CA_KEY, hashes.SHA256()))
    return certificate.public_bytes(serialization.Encoding.DER)


# Encode a protocol object as CBOR without reordering Aliro's compact map keys.
def cbor(value):
    # Aliro expects compact string keys in the order written below.
    return cbor2.dumps(value, canonical=False)


# Generate an opaque UUIDv7-shaped ID for the access data carried by a pass.
def uuid7_bytes():
    """A dependency-free UUIDv7-shaped 16-byte access-data identifier."""
    milliseconds = int(time.time() * 1000)
    random_a = int.from_bytes(secrets.token_bytes(2), "big") & 0x0FFF  # 12 bits
    random_b = int.from_bytes(secrets.token_bytes(8), "big") & ((1 << 62) - 1)
    value = (milliseconds << 80) | (7 << 76) | (random_a << 64) | (2 << 62) | random_b
    return value.to_bytes(16, "big")


# Represent a pass validity timestamp in the CBOR form required by Aliro.
def date_tag(value):
    """Aliro encodes validity times as CBOR tag-0 RFC 3339 strings."""
    return cbor2.CBORTag(0, value.astimezone(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"))


# Build the signed authorization data that the SDK later presents to a lock.
def issue_access_document(device_key, valid_from, valid_until):
    """Create an Aliro IssuerSigned Access Document for one phone public key.

    The document contains an IssuerSignedItem (the access data a lock asks for)
    and IssuerAuth (a signature over the MSO that binds the item digest, phone
    public key, and validity window). It contains no lock or device ID.
    """
    # The IssuerSignedItem has four compact fields: digest index ("1"), random
    # bytes ("2"), requested element name ("3"), and AccessData value ("4").
    # This example's AccessData is version 1 plus a fresh opaque ID, not a
    # device ID or a per-lock rule. The name must equal ELEMENT_ID provisioned
    # via access_document_element_ids in /v1/configure; otherwise the lock
    # cannot use this item to authorize the exchange.
    # CBOR tag 24 wraps the item as embedded CBOR. The MSO hashes the complete
    # tagged bytes, so changing the access data breaks issuer verification.
    item = cbor({"1": 0, "2": secrets.token_bytes(16), "3": ELEMENT_ID, "4": {0: 1, 1: uuid7_bytes()}})
    tagged_item = cbor2.CBORTag(24, item)
    device_key_bytes = point(device_key)
    # The Mobile Security Object (MSO) ties three things together: the exact
    # access item above (valueDigests, "3"), the SDK-generated public key
    # (DeviceKeyInfo, "4"), and the time window (ValidityInfo, "6").
    # The lock verifies this binding with the configured issuer public key;
    # the phone must separately prove it holds the corresponding private key.
    mso = {
        "1": "1.0", "2": "SHA-256", "3": {"aliro-a": {0: hashlib.sha256(cbor(tagged_item)).digest()}},
        "4": {"1": {1: 2, -1: 1, -2: device_key_bytes[1:33], -3: device_key_bytes[33:]}}, "5": "aliro-a",
        "6": {"1": date_tag(datetime.now(timezone.utc)), "2": date_tag(valid_from), "3": date_tag(valid_until), "5": 0}, "7": True,
    }
    # COSE Sign1 carries the issuer signature on the tag-24-wrapped MSO. The
    # SDK later packages IssuerSigned into the BLE presentation. This server
    # signs the document but does not perform the BLE exchange or unlock.
    payload = cbor(cbor2.CBORTag(24, cbor(mso)))
    protected = cbor({1: -7})  # COSE ES256
    sig_structure = cbor(["Signature1", protected, b"", payload])
    r, s = decode_dss_signature(ISSUER_KEY.sign(sig_structure, ec.ECDSA(hashes.SHA256())))
    # COSE ES256 stores ECDSA as fixed-width R || S, not ASN.1 DER.
    signature = r.to_bytes(32, "big") + s.to_bytes(32, "big")
    # The key ID lets the lock select the issuer public key that /v1/configure
    # provisioned to verify this signature.
    kid = hashlib.sha256(b"key-identifier" + point(ISSUER_KEY.public_key())).digest()[:8]
    return cbor({"1": {"aliro-a": [tagged_item]}, "2": [protected, {4: kid}, payload, signature]})


# Lazily create and reuse the gRPC client for Link's v2 Devices service.
def devices():
    """Create one reusable Link v2 Devices gRPC client for this process."""
    if not hasattr(app, "link_channel"):
        if LINK_GRPC_SECURE:
            app.link_channel = grpc.secure_channel(LINK_GRPC_TARGET, grpc.ssl_channel_credentials())
        else:
            app.link_channel = grpc.insecure_channel(LINK_GRPC_TARGET)
        app.devices_client = link_v2_pb2_grpc.DevicesStub(app.link_channel)
    return app.devices_client


# Read a Link v2 device ID from a JSON request body.
def body_device_id(body):
    """Return the body's device_id if it is a Link v2 device ID, else None."""
    device_id = body.get("device_id")
    if not isinstance(device_id, str) or not DEVICE_ID_RE.fullmatch(device_id):
        return None
    return device_id


# Supply the backend Link API credential as gRPC request metadata.
def link_auth():
    """Attach the backend's Link API key to Link API calls."""
    return (("authorization", f"Bearer {LINK_API_KEY}"),)


# Report liveness.
@app.get("/healthz")
def health():
    return jsonify(status="ok")


# Configure one lock with this issuer's reader and credential trust material.
@app.post("/v1/configure")
def configure():
    """Queue the Aliro trust configuration that a lock needs before unlocks.

    The lock reports its own reader public key to Link after provisioning. We
    certify that key, then give Link the reader-group ID, certificate, issuer
    verification key, Reader CA public key, and access element name to deliver
    to the lock. This does not create an Access Document or grant access to a
    phone. A 202 response means Link accepted the configuration for delivery;
    the lock may not have received it yet.
    """
    body = request.get_json(silent=True) or {}
    if not isinstance(body, dict):
        return jsonify(error="request body must be a JSON object"), 400
    device_id = body_device_id(body)
    if device_id is None:
        return jsonify(error="device_id must be a Link v2 device ID (32 lowercase hex characters)"), 400
    try:
        # Public Link API: discover the lock's reader public key. It appears
        # only after the lock has completed its Aliro reader-key report.
        summaries = devices().ListDevices(link_v2_pb2.ListDevicesRequest(device_ids=[device_id]), metadata=link_auth(), timeout=15)
        if len(summaries.device_summaries) != 1:
            return jsonify(error="device was not found"), 404
        reader_bytes = summaries.device_summaries[0].aliro_reader_public_key
        if not reader_bytes:
            return jsonify(error="device has not reported an Aliro reader public key yet"), 409
        reader_key = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), reader_bytes)
        # SetAliroConfig queues lock-side trust material through the public
        # Link API. Link converts the DER reader certificate to Profile0000.
        # reader_group_id identifies which reader trust entry the SDK will use.
        # The reader certificate and Reader CA key authenticate this lock;
        # issuer_public_keys lets it verify AD signatures from this server.
        # access_document_element_ids tells the lock which named data item to
        # request from the phone. The item we sign below must use that name.
        config = link_v2_pb2.AliroConfigForDevice(
            device_id=device_id, reader_group_id=reader_group_id(), reader_certificate=reader_certificate(reader_key),
            issuer_public_keys=[point(ISSUER_KEY.public_key())],
            reader_system_issuer_ca_pub_key=point(READER_CA_KEY.public_key()),
            access_document_element_ids=[ELEMENT_ID.encode()],
        )
        devices().SetAliroConfig(link_v2_pb2.SetAliroConfigRequest(configs=[config]), metadata=link_auth(), timeout=15)
    except grpc.RpcError as error:
        return jsonify(error=f"Link API error: {error.code().name}: {error.details()}"), 502
    except ValueError:
        return jsonify(error="device reported an invalid Aliro reader public key"), 400
    # Remember that at least one lock has had matching trust configuration
    # queued. This list is NOT an access-control list and is not signed into
    # passes; every lock with matching issuer key and element name may accept
    # them. A production issuer must authorize the caller for each lock.
    if device_id not in STATE["configured_devices"]:
        STATE["configured_devices"].append(device_id)
        save_state()
    return jsonify(configuration_queued=True, reader_group_id=base64.b64encode(reader_group_id()).decode()), 202


# Ask Link to enter or exit install mode. Link signs the payload and pushes it
# on the plane stream; this process never writes bytes to the lock.
@app.post("/v1/install-mode")
def install_mode():
    """Queue an install-mode enter or exit for one device.

    Enter accepts ``duration_seconds`` from 300 to 7200 and defaults to 300.
    When Link has a firmware image for the device, the enter command includes
    that DFU version. Exit asks the lock to reboot out of install mode. A 202
    response means Link accepted the command. The phone must be streaming so
    the proxy can deliver it, then wait for the advertisement to change.
    """
    body = request.get_json(silent=True) or {}
    if not isinstance(body, dict):
        return jsonify(error="request body must be a JSON object"), 400
    device_id = body_device_id(body)
    action = body.get("action")
    if device_id is None:
        return jsonify(error="device_id must be a Link v2 device ID (32 lowercase hex characters)"), 400
    if action == "enter":
        duration = body.get("duration_seconds", MIN_INSTALL_SECONDS)
        if isinstance(duration, bool) or not isinstance(duration, int) or not MIN_INSTALL_SECONDS <= duration <= MAX_INSTALL_SECONDS:
            return jsonify(error=f"duration_seconds must be an integer from {MIN_INSTALL_SECONDS} to {MAX_INSTALL_SECONDS}"), 400
        modes = lockmodes_pb2.LockModes(install=lockmodes_pb2.MoveType.ENTER, duration_seconds=duration)
    elif action == "exit":
        modes = lockmodes_pb2.LockModes(install=lockmodes_pb2.MoveType.EXIT)
    else:
        return jsonify(error="action must be enter or exit"), 400
    try:
        devices().SendLockMode(
            link_v2_pb2.SetDeviceModeRequest(device_id=device_id, modes=modes),
            metadata=link_auth(),
            timeout=15,
        )
    except grpc.RpcError as error:
        return jsonify(error=f"Link API error: {error.code().name}: {error.details()}"), 502
    return jsonify(install_mode_queued=True, device_id=device_id, action=action), 202


# Create and return a token-unlock pass for the supplied public key and duration.
@app.post("/v1/issue-unlock-pass")
def issue_unlock_pass():
    """Return a signed AD and phone-side reader trust for an SDK credential.

    The SDK creates the P-256 key pair before this request. We receive only
    its public key and a requested duration in seconds. The AD binds that key
    and time window to our issuer signature. The response also gives the SDK
    the reader-group ID and Reader CA material needed to authenticate locks.
    No device ID is included; this example does not authorize an HTTP caller.
    """
    # The phone creates this key pair locally. Only its public half crosses the
    # network; the private key remains in the Last Lock Mobile SDK key store.
    body = request.get_json(silent=True) or {}
    if not isinstance(body, dict):
        return jsonify(error="request body must be a JSON object"), 400
    encoded_key = body.get("credential_public_key", "")
    duration = body.get("duration")
    if isinstance(duration, bool) or not isinstance(duration, int) or not 0 < duration <= MAX_PASS_DURATION_SECONDS:
        return jsonify(error=f"duration must be an integer number of seconds from 1 to {MAX_PASS_DURATION_SECONDS}"), 400
    # This is only a setup check, not proof that this caller should access any
    # of the configured locks. Production code must make that policy decision.
    if not STATE["configured_devices"]:
        return jsonify(error="configure at least one device before issuing a pass"), 409
    try:
        key_bytes = base64.b64decode(encoded_key, validate=True)
        if len(key_bytes) != 65 or key_bytes[0] != 4:
            raise ValueError
        credential_key = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), key_bytes)
    except (TypeError, ValueError):
        return jsonify(error="credential_public_key must be a base64 65-byte P-256 key"), 400
    # These times are signed into the AD so the lock can reject an expired pass.
    # Ephemeral SDK credentials have their own shorter session lifetime.
    valid_from = datetime.now(timezone.utc).replace(microsecond=0)
    valid_until = valid_from + timedelta(seconds=duration)
    try:
        document = issue_access_document(credential_key, valid_from, valid_until)
    except Exception:
        app.logger.exception("creating token-unlock access document")
        return jsonify(error="unable to create token-unlock pass"), 500
    # The app decodes access_document and reader_groups into TokenUnlockPass.
    # reader_groups is not a list of locks the phone may open: it tells the SDK
    # which Reader CA certificate to trust for this group of configured locks.
    # The signed AD is what the phone presents and the lock verifies over BLE.
    return jsonify(
        access_document=base64.b64encode(document).decode(),
        reader_groups=[{
            "reader_group_id": base64.b64encode(reader_group_id()).decode(),
            "issuer_ca_public_key": base64.b64encode(point(READER_CA_KEY.public_key())).decode(),
            "issuer_ca_certificate_der": base64.b64encode(READER_CA_CERT.public_bytes(serialization.Encoding.DER)).decode(),
        }],
        valid_from=valid_from.isoformat().replace("+00:00", "Z"),
        valid_until=valid_until.isoformat().replace("+00:00", "Z"),
    )


if __name__ == "__main__":
    app.run(host=env("TOKEN_UNLOCK_LISTEN_ADDR", "0.0.0.0"), port=int(env("TOKEN_UNLOCK_PORT", "3006")))
