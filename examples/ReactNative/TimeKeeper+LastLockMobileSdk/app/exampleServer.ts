import type { UnlockPass } from '@last-lock/lastlock-mobile-sdk';

export const TOKEN_PASS_ID = 'timekeeper';

function base(url: string) {
  return url.trim().replace(/\/$/, '');
}

async function post(url: string, path: string, body: unknown) {
  const response = await fetch(`${base(url)}${path}`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(body),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = typeof payload.error === 'string' ? payload.error : `Request failed (${response.status})`;
    throw new Error(message);
  }
  return payload;
}

export function enterInstallMode(serverUrl: string, deviceId: string) {
  return post(serverUrl, '/v1/install-mode', {
    device_id: deviceId,
    action: 'enter',
    duration_seconds: 300,
  });
}

export function exitInstallMode(serverUrl: string, deviceId: string) {
  return post(serverUrl, '/v1/install-mode', {
    device_id: deviceId,
    action: 'exit',
  });
}

export async function issueUnlockPass(serverUrl: string, publicKeyBase64: string): Promise<UnlockPass> {
  const payload = await post(serverUrl, '/v1/issue-unlock-pass', {
    credential_public_key: publicKeyBase64,
    duration: 7 * 24 * 60 * 60,
  });
  if (typeof payload.access_document !== 'string' || !Array.isArray(payload.reader_groups) || !payload.reader_groups.length) {
    throw new Error('The example server returned an incomplete unlock pass.');
  }
  return {
    accessDocument: payload.access_document,
    readerGroups: payload.reader_groups.map((group: Record<string, unknown>) => {
      if (typeof group.reader_group_id !== 'string' || typeof group.issuer_ca_public_key !== 'string' ||
          (group.issuer_ca_certificate_der != null && typeof group.issuer_ca_certificate_der !== 'string')) {
        throw new Error('The example server returned incomplete reader group trust material.');
      }
      return {
        readerGroupIdentifier: group.reader_group_id,
        issuerCAPublicKey: group.issuer_ca_public_key,
        ...(group.issuer_ca_certificate_der != null ? {issuerCACertificateDer: group.issuer_ca_certificate_der as string} : {}),
      };
    }),
  };
}
