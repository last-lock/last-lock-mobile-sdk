import {TurboModuleRegistry} from 'react-native';

function native() {
  const module = TurboModuleRegistry.get('LastLockMobileSdk');
  if (!module) {
    throw new Error(
      'LastLockMobileSdk native module is not available. Rebuild the app after installing @last-lock/lastlock-mobile-sdk.',
    );
  }
  return module;
}

// Core integration.

export async function initialize(serverAddress = 'plane.lastlock.com') {
  await native().initialize(serverAddress);
}

export async function start(uuid = 'FC5A') {
  await native().start(uuid);
}

export async function startLimited(uuid = 'FC5A') {
  await native().startLimited(uuid);
}

export function getOrCreatePassPublicKey(passId) {
  return native().getOrCreatePassPublicKey(passId);
}

/** Stores a signed pass with all reader groups. Binary fields are Base64 strings. */
export async function storePass(passId, pass) {
  await native().storePass(passId, pass);
}

export async function unlock(identifier, passId) {
  await native().unlock(identifier, passId);
}

/** Explicitly deletes the stored pass and its credential key pair. */
export async function deletePass(passId) {
  await native().deletePass(passId);
}

export function onDeviceDiscovered(listener) {
  return native().onDeviceDiscovered(listener);
}

export function onBLEStateChanged(listener) {
  return native().onBLEStateChanged(listener);
}

export function onUnlockResult(listener) {
  return native().onUnlockResult(listener);
}

export function onServerConnectionChange(listener) {
  return native().onServerConnectionChange(listener);
}

export async function shutdown() {
  await native().shutdown();
}

export async function getPass(passId) {
  return (await native().getPass(passId)) ?? null;
}

// Additional device controls and events.

export async function identify(identifier) {
  await native().identify(identifier);
}

export function onDeviceConnected(listener) {
  return native().onDeviceConnected(listener);
}

export function onDeviceDisconnected(listener) {
  return native().onDeviceDisconnected(listener);
}

export function onDeviceInformation(listener) {
  return native().onDeviceInformation(listener);
}

// Beta: installation controls and firmware updates.

/** @beta Only works while the device is in install mode. True means queued/sent, not accepted. */
export async function setLockEngagedState(identifier, engaged) {
  await native().setLockEngagedState(identifier, engaged);
}

/** @beta Firmware update/install-mode API; may change between releases. */
export function checkForDFU(identifier) {
  return native().checkForDFU(identifier);
}

/** @beta Firmware update/install-mode API; may change between releases. */
export async function startDFU(identifier) {
  await native().startDFU(identifier);
}

/** @beta Firmware update/install-mode API; may change between releases. */
export async function cancelDFU() {
  await native().cancelDFU();
}

/** @beta Waits for install/DFU mode. When dfuRequired is true, also starts DFU. Does not send the install command. */
export async function waitForDeviceMode(identifier, dfuRequired) {
  await native().waitForDeviceMode(identifier, dfuRequired);
}

/** @beta Firmware update/install-mode API; may change between releases. */
export async function exitDFUInstallMode(identifier) {
  await native().exitDFUInstallMode(identifier);
}

/** @beta Firmware update/install-mode API; may change between releases. */
export function onDFUStateChanged(listener) {
  return native().onDFUStateChanged(listener);
}

export function isDeviceBlacklisted(identifier) {
  return native().isDeviceBlacklisted(identifier);
}
