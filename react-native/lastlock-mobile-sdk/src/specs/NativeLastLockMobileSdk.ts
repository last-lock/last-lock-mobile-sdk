/** Stable SDK failure code exposed by rejected promises. */
export type MobileErrorCode = 'NOT_INITIALIZED' | 'INVALID_ARGUMENT' | 'PERMISSION_DENIED' |
  'DEVICE_NOT_DISCOVERED' | 'PASS_NOT_FOUND' | 'STORAGE_FAILED' | 'TIMEOUT' |
  'OPERATION_FAILED' | 'SHUTDOWN_FAILED' | 'OPERATION_BUSY';

export type UnlockResultCode = 'ACCESS_GRANTED' | 'ACCESS_DENIED' | 'CRYPTOGRAPHIC_ERROR' |
  'DEVICE_DISCONNECTED_DURING_OPERATION' | 'CREDENTIAL_NOT_FOUND' | 'OUT_OF_SCHEDULE' |
  'OTHER' | 'CREDENTIAL_EXCHANGE_BUSY' | 'TIMEOUT';

import type {CodegenTypes, TurboModule} from 'react-native';
import {TurboModuleRegistry} from 'react-native';

/**
 * A lock seen in a BLE advertisement. `identifier` is the device id from the
 * advertising packet, not the Bluetooth address; pass it to this library's
 * methods. `linkDeviceId` is the same lock's ID in the Link v2 API; send it to
 * your backend. The boolean flags and state
 * counters are parsed from that advertisement. `oobTimestamp` is the last
 * out-of-band server timestamp observed for this lock, or 0.
 * Delivered by `onDeviceDiscovered`.
 */
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

/**
 * A heartbeat from a connected lock. `oobTimestamp` is the lock's last
 * out-of-band server timestamp. Delivered by `onDeviceInformation`.
 */
export type DeviceInformation = {
  identifier: string;
  firmwareVersion: string;
  bootloaderVersion: string | null;
  deviceStateCounter: number;
  serverStateCounter: number;
  oobTimestamp: number | null;
};

/**
 * Plane proxy stream liveness. `state` is `connected` or `disconnected`.
 * Delivered by `onServerConnectionChange`.
 */
export type ServerConnectionState = {
  state: 'connected' | 'disconnected';
};

/**
 * A lock that connected or disconnected. Delivered by `onDeviceConnected`
 * and `onDeviceDisconnected`.
 */
export type DeviceEvent = {
  identifier: string;
  name: string;
};

/**
 * Bluetooth radio state. `state` is true when the radio is on.
 * Delivered by `onBLEStateChanged`.
 */
export type BLEState = {
  state: boolean;
};

/**
 * Outcome of a token unlock. `result` is the SDK credential-exchange result
 * name, such as `ACCESS_GRANTED` or `TIMEOUT`. `message` is the same result
 * rendered as text. Delivered by `onUnlockResult`.
 */
export type UnlockResult = {
  identifier: string;
  name: string;
  passId: string;
  result: UnlockResultCode;
  message: string;
};

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

export interface Spec extends TurboModule {
  // Core integration.
  /** Stores the plane host. Does not start scanning. */
  initialize(serverAddress: string): Promise<void>;
  /** Starts, or restarts, a BLE scan for a 4-character service UUID. */
  start(uuid: string): Promise<void>;
  /** Uses foreground-only service operation on Android and regular start on iOS. */
  startLimited(uuid: string): Promise<void>;
  /** Returns the credential public key for the passId as Base64. */
  getOrCreatePassPublicKey(passId: string): Promise<string>;
  /** Stores the signed access document and every authorized reader group's trust material. */
  storePass(passId: string, pass: UnlockPass): Promise<void>;
  /** Presents the stored pass; onUnlockResult reports the final result. */
  unlock(identifier: string, passId: string): Promise<void>;
  /** Explicitly removes the stored pass and its credential key pair. */
  deletePass(passId: string): Promise<void>;
  readonly onDeviceDiscovered: CodegenTypes.EventEmitter<DiscoveredDevice>;
  readonly onBLEStateChanged: CodegenTypes.EventEmitter<BLEState>;
  readonly onUnlockResult: CodegenTypes.EventEmitter<UnlockResult>;
  readonly onServerConnectionChange: CodegenTypes.EventEmitter<ServerConnectionState>;

  /** Stops SDK activity and clears native callbacks. Preserves stored passes. */
  shutdown(): Promise<void>;
  /** Reads a stored pass, or null if absent. */
  getPass(passId: string): Promise<UnlockPass | null>;

  // Additional device controls and events.
  identify(identifier: string): Promise<void>;
  isDeviceBlacklisted(identifier: string): Promise<boolean>;
  readonly onDeviceConnected: CodegenTypes.EventEmitter<DeviceEvent>;
  readonly onDeviceDisconnected: CodegenTypes.EventEmitter<DeviceEvent>;
  readonly onDeviceInformation: CodegenTypes.EventEmitter<DeviceInformation>;

  // Beta: installation controls and firmware updates.
  /** @beta Only works while the device is in install mode. Completion means queued/sent, not accepted. */
  setLockEngagedState(identifier: string, engaged: boolean): Promise<void>;
  /** @beta Asks the plane whether a firmware image is available for this lock. */
  checkForDFU(identifier: string): Promise<DFUCheck>;
  /** @beta Starts DFU after checkForDFU reported update_available for this lock. */
  startDFU(identifier: string): Promise<void>;
  /** @beta Cancels an in-progress firmware update. */
  cancelDFU(): Promise<void>;
  /** @beta Waits for install/DFU mode; true also starts the firmware transfer. Does not send the install command. */
  waitForDeviceMode(identifier: string, dfuRequired: boolean): Promise<void>;
  /** @beta Sends the DFU reboot and waits for the lock to leave install mode. */
  exitDFUInstallMode(identifier: string): Promise<void>;
  /** @beta Firmware update/install-mode event; may change between releases. */
  readonly onDFUStateChanged: CodegenTypes.EventEmitter<DFUState>;
}

/**
 * Result of `checkForDFU`. `status` is `update_available`, `up_to_date`, or
 * `timeout`. Version strings are empty when the check times out.
 */
/** @beta Firmware-update payload; may change between releases. */
export type DFUCheck = {
  status: 'update_available' | 'up_to_date' | 'timeout';
  firmwareVersion: string;
  availableVersion: string;
};

/**
 * Firmware update progress. `flowState` is the SDK DFU flow name.
 * Progress values run from 0 to 1. Delivered by `onDFUStateChanged`.
 */
/** @beta Firmware-update payload; may change between releases. */
export type DFUState = {
  flowState: 'REST' | 'QUEUED' | 'DOWNLOADING' | 'INSTALLING' | 'VALIDATING' | 'SUCCESS' | 'FAILED';
  installProgress: number;
  postInstallProgress: number;
  dfuAvailable: boolean;
  dfuImageLoading: boolean;
  availableVersion: string;
};

export default TurboModuleRegistry.getEnforcing<Spec>('LastLockMobileSdk');
