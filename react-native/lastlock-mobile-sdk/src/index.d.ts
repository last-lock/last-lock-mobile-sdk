/** Stable SDK failure code exposed by rejected promises. */
export type MobileErrorCode = 'NOT_INITIALIZED' | 'INVALID_ARGUMENT' | 'PERMISSION_DENIED' |
  'DEVICE_NOT_DISCOVERED' | 'PASS_NOT_FOUND' | 'STORAGE_FAILED' | 'TIMEOUT' |
  'OPERATION_FAILED' | 'SHUTDOWN_FAILED' | 'OPERATION_BUSY';

export type UnlockResultCode = 'ACCESS_GRANTED' | 'ACCESS_DENIED' | 'CRYPTOGRAPHIC_ERROR' |
  'DEVICE_DISCONNECTED_DURING_OPERATION' | 'CREDENTIAL_NOT_FOUND' | 'OUT_OF_SCHEDULE' |
  'OTHER' | 'CREDENTIAL_EXCHANGE_BUSY' | 'TIMEOUT';

// Core integration: start the SDK, discover a lock, store a pass, and unlock.

/** Configures Link; defaults to the production hostname plane.lastlock.com. */
export function initialize(serverAddress?: string): Promise<void>;
/** Starts BLE and Proxy; defaults to service UUID FC5A. */
export function start(uuid?: string): Promise<void>;
/** Uses foreground-only service operation on Android and regular start on iOS; defaults to FC5A. */
export function startLimited(uuid?: string): Promise<void>;
/** Returns the SDK-managed credential public key as Base64. Reuse the passId. */
export function getOrCreatePassPublicKey(passId: string): Promise<string>;
/** Stores the pass with the passId used for getOrCreatePassPublicKey. */
export function storePass(passId: string, pass: UnlockPass): Promise<void>;
/** Queues an unlock; onUnlockResult reports the final authorization result. */
export function unlock(identifier: string, passId: string): Promise<void>;
/** Explicitly deletes the pass and its key pair. Passes persist across logout and shutdown. */
export function deletePass(passId: string): Promise<void>;

export function onDeviceDiscovered(listener: (device: DiscoveredDevice) => void): LastLockMobileSdkSubscription;
export function onBLEStateChanged(listener: (event: BLEState) => void): LastLockMobileSdkSubscription;
export function onUnlockResult(listener: (event: UnlockResult) => void): LastLockMobileSdkSubscription;
export function onServerConnectionChange(listener: (event: ServerConnectionState) => void): LastLockMobileSdkSubscription;

/** Stops SDK activity and clears native callbacks. Preserves stored passes. */
export function shutdown(): Promise<void>;
/** Reads a stored pass, or null if absent. */
export function getPass(passId: string): Promise<UnlockPass | null>;

// Additional device controls and events.

export function identify(identifier: string): Promise<void>;
/** Returns the local proxy blacklist state for a discovered reader. */
export function isDeviceBlacklisted(identifier: string): Promise<boolean>;
export function onDeviceConnected(listener: (device: DeviceEvent) => void): LastLockMobileSdkSubscription;
export function onDeviceDisconnected(listener: (device: DeviceEvent) => void): LastLockMobileSdkSubscription;
export function onDeviceInformation(listener: (event: DeviceInformation) => void): LastLockMobileSdkSubscription;

/** Public trust material for one authorized reader group. Binary fields are Base64. */
export type ReaderGroupTrust = {
  readerGroupIdentifier: string;
  issuerCAPublicKey: string;
  issuerCACertificateDer?: string;
};

/** Signed pass and trust material, matching the native SDK pass structure. */
export type UnlockPass = {
  accessDocument: string;
  readerGroups: Array<ReaderGroupTrust>;
};

export type DiscoveredDevice = {
  identifier: string;
  linkDeviceId: string;
  name: string;
  rssi: number;
  connectionStatus: 'CONNECTED' | 'DISCONNECTED' | 'CONNECTING' | 'RECEIVED_ADV';
  signalStrength: 'EXCELLENT' | 'GOOD' | 'OKAY' | 'WEAK';
  installMode: boolean;
  dfuMode: boolean;
  hibernate: boolean;
  serviceMode: boolean;
  lowBattery: boolean;
  criticalBattery: boolean;
  cooldown: boolean;
  emergency: boolean;
  busy: boolean;
  timeSyncRequest: boolean;
  overridingMode: 'NONE' | 'HIBERNATE' | 'SERVICE' | 'CONSTRUCTION' | 'SILENT' | 'UNKNOWN';
  deviceStateCounter: number;
  serverStateCounter: number;
  oobTimestamp: number;
};

export type DeviceInformation = {
  identifier: string;
  firmwareVersion: string;
  bootloaderVersion: string | null;
  deviceStateCounter: number;
  serverStateCounter: number;
  oobTimestamp: number | null;
};

export type ServerConnectionState = {
  state: 'connected' | 'disconnected';
};

export type DeviceEvent = {
  identifier: string;
  name: string;
};

export type BLEState = {
  state: boolean;
};

export type UnlockResult = {
  identifier: string;
  name: string;
  passId: string;
  result: UnlockResultCode;
  message: string;
};

export type LastLockMobileSdkSubscription = {
  remove(): void;
};

// Beta: installation controls and firmware updates. These APIs may change between releases.

/** @beta Only works while the device is in install mode. Completion means queued/sent, not accepted. */
export function setLockEngagedState(identifier: string, engaged: boolean): Promise<void>;
/** @beta Firmware update/install-mode API; may change between releases. */
export function checkForDFU(identifier: string): Promise<DFUCheck>;
/** @beta Firmware update/install-mode API; may change between releases. */
export function startDFU(identifier: string): Promise<void>;
/** @beta Firmware update/install-mode API; may change between releases. */
export function cancelDFU(): Promise<void>;
/** @beta Waits for install/DFU mode. When dfuRequired is true, also starts DFU. Does not send the install command. */
export function waitForDeviceMode(identifier: string, dfuRequired: boolean): Promise<void>;
/** @beta Firmware update/install-mode API; may change between releases. */
export function exitDFUInstallMode(identifier: string): Promise<void>;
/** @beta Firmware update/install-mode API; may change between releases. */
export function onDFUStateChanged(listener: (event: DFUState) => void): LastLockMobileSdkSubscription;

/** @beta Firmware-update payload; may change between releases. */
export type DFUCheck = {
  status: 'update_available' | 'up_to_date' | 'timeout';
  firmwareVersion: string;
  availableVersion: string;
};

/** @beta Firmware-update payload; may change between releases. */
export type DFUState = {
  flowState: 'REST' | 'QUEUED' | 'DOWNLOADING' | 'INSTALLING' | 'VALIDATING' | 'SUCCESS' | 'FAILED';
  installProgress: number;
  postInstallProgress: number;
  dfuAvailable: boolean;
  dfuImageLoading: boolean;
  availableVersion: string;
};
