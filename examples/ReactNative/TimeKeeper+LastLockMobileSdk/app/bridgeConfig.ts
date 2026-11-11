import AsyncStorage from '@react-native-async-storage/async-storage';

export const DEFAULT_SERVER_ADDRESS = 'plane.lastlock.com';
export const DEFAULT_SERVICE_UUID = 'FC5A';

const SERVER_KEY = 'linkMobile.serverAddress';
const UUID_KEY = 'linkMobile.serviceUUID';

export type BridgeConfig = {
  serverAddress: string;
  serviceUUID: string;
};

export function isServiceUuid(value: string): boolean {
  return /^[0-9A-Fa-f]{4}$/.test(value);
}

export async function loadBridgeConfig(): Promise<BridgeConfig> {
  const [serverAddress, serviceUUID] = await Promise.all([
    AsyncStorage.getItem(SERVER_KEY),
    AsyncStorage.getItem(UUID_KEY),
  ]);
  return {
    serverAddress: serverAddress?.trim() || DEFAULT_SERVER_ADDRESS,
    serviceUUID: (serviceUUID?.trim() || DEFAULT_SERVICE_UUID).toUpperCase(),
  };
}

export async function saveBridgeConfig(serverAddress: string, serviceUUID: string): Promise<void> {
  await AsyncStorage.multiSet([
    [SERVER_KEY, serverAddress],
    [UUID_KEY, serviceUUID.toUpperCase()],
  ]);
}
