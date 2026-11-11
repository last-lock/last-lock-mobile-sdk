import React from 'react';
import { Alert, View, StyleSheet } from 'react-native';
import { Text, Card, Title, Paragraph, Chip, Divider, Button, TextInput, useTheme } from 'react-native-paper';
import { LinearGradient } from 'expo-linear-gradient';
import {
  cancelDFU,
  checkForDFU,
  exitDFUInstallMode,
  getOrCreatePassPublicKey,
  identify,
  initialize,
  onDFUStateChanged,
  onServerConnectionChange,
  onUnlockResult,
  setLockEngagedState,
  start,
  startDFU,
  storePass,
  unlock,
  waitForDeviceMode,
} from '@last-lock/lastlock-mobile-sdk';
import {
  DEFAULT_SERVER_ADDRESS,
  DEFAULT_SERVICE_UUID,
  isServiceUuid,
  loadBridgeConfig,
  saveBridgeConfig,
} from '../app/bridgeConfig';
import { enterInstallMode, exitInstallMode, issueUnlockPass, TOKEN_PASS_ID } from '../app/exampleServer';
import { useDeviceDiscovery } from '../app/useDeviceDiscovery';
import { useBridgeContext } from '../context/BridgeContext';

interface Device {
  identifier: string;
  linkDeviceId: string;
  name?: string;
  rssi?: number;
  connectionStatus?: string;
  signalStrength?: string;
  [key: string]: any;
}

export function DeviceDebugPanel() {
  const devices = useDeviceDiscovery();
  const { isInitialized, isScanning, setScanning } = useBridgeContext();
  const theme = useTheme();
  const [dfuState, setDfuState] = React.useState('REST');
  const [serverUrl, setServerUrl] = React.useState('http://127.0.0.1:3006');
  const [planeServer, setPlaneServer] = React.useState(DEFAULT_SERVER_ADDRESS);
  const [serviceUuid, setServiceUuid] = React.useState(DEFAULT_SERVICE_UUID);
  const [appliedConfig, setAppliedConfig] = React.useState({
    serverAddress: DEFAULT_SERVER_ADDRESS,
    serviceUUID: DEFAULT_SERVICE_UUID,
  });
  const [proxyStream, setProxyStream] = React.useState('unknown');
  const [passReady, setPassReady] = React.useState(false);
  const [unlockingDeviceId, setUnlockingDeviceId] = React.useState<string | null>(null);
  const [unlockResult, setUnlockResult] = React.useState<string | null>(null);
  const [dfuReadyIds, setDfuReadyIds] = React.useState<ReadonlySet<string>>(() => new Set());

  React.useEffect(() => {
    const dfuSubscription = onDFUStateChanged((event) => {
      const progress = Math.round((event.installProgress ?? 0) * 100);
      setDfuState(`${event.flowState} ${progress}%`);
    });
    const streamSubscription = onServerConnectionChange((event) => {
      setProxyStream(event.state);
    });
    const unlockSubscription = onUnlockResult((event) => {
      if (event.passId !== TOKEN_PASS_ID) return;
      setUnlockingDeviceId((current) => current === event.identifier ? null : current);
      setUnlockResult(`${event.identifier}: ${event.message}`);
    });
    return () => {
      unlockSubscription.remove();
      dfuSubscription.remove();
      streamSubscription.remove();
    };
  }, []);

  React.useEffect(() => {
    let active = true;
    loadBridgeConfig().then((config) => {
      if (!active) return;
      setPlaneServer(config.serverAddress);
      setServiceUuid(config.serviceUUID);
      setAppliedConfig(config);
    });
    return () => {
      active = false;
    };
  }, []);

  const configChanged =
    planeServer.trim() !== appliedConfig.serverAddress ||
    serviceUuid.trim().toUpperCase() !== appliedConfig.serviceUUID;

  async function restartBridge() {
    const address = planeServer.trim();
    const uuid = serviceUuid.trim().toUpperCase();
    if (!address || address.includes('://') || address.includes('/')) {
      throw new Error('Server address must be a host, such as plane.lastlock.com.');
    }
    if (!isServiceUuid(uuid)) {
      throw new Error('Service UUID must be exactly 4 hexadecimal characters (e.g. 4B4A).');
    }
    await initialize(address);
    await start(uuid);
    await saveBridgeConfig(address, uuid);
    setPlaneServer(address);
    setServiceUuid(uuid);
    setAppliedConfig({ serverAddress: address, serviceUUID: uuid });
    setScanning(true);
  }

  async function issuePass() {
    const publicKey = await getOrCreatePassPublicKey(TOKEN_PASS_ID);
    const pass = await issueUnlockPass(serverUrl, publicKey);
    await storePass(TOKEN_PASS_ID, pass);
    setPassReady(true);
    return 'Unlock pass is ready for seven days.';
  }

  async function unlockDevice(identifier: string) {
    setUnlockingDeviceId(identifier);
    setUnlockResult(null);
    try {
      await unlock(identifier, TOKEN_PASS_ID);
    } catch (error) {
      setUnlockingDeviceId(null);
      throw error;
    }
  }

  async function run(action: () => Promise<unknown>) {
    try {
      const result = await action();
      if (typeof result === 'string') {
        Alert.alert('Link Mobile', result);
      } else if (result && typeof result === 'object') {
        Alert.alert('Link Mobile', JSON.stringify(result));
      }
    } catch (error) {
      Alert.alert('Link Mobile', error instanceof Error ? error.message : String(error));
    }
  }

  const containerStyle = [
    styles.container,
    { backgroundColor: theme.colors.surface, borderColor: theme.colors.outline }
  ];

  const titleStyle = [styles.title, { color: theme.colors.onSurface }];
  const subtitleStyle = [styles.subtitle, { color: theme.colors.onSurfaceVariant }];

  return (
    <Card style={containerStyle} mode="outlined">
      <Card.Content>
        <Title style={titleStyle}>🔍 Device Discovery Debug</Title>
        <View style={styles.statusContainer}>
          <Chip 
            mode="outlined" 
            style={[
              styles.statusChip, 
              { 
                backgroundColor: isInitialized ? theme.colors.primaryContainer : theme.colors.surfaceVariant,
                borderColor: isInitialized ? theme.colors.primary : theme.colors.outline
              }
            ]}
            textStyle={{ color: isInitialized ? theme.colors.onPrimaryContainer : theme.colors.onSurfaceVariant }}
          >
            LastLockMobileSdk: {isInitialized ? 'Initialized' : 'Initializing...'}
          </Chip>
          <Chip 
            mode="outlined" 
            style={[
              styles.statusChip, 
              { 
                backgroundColor: isScanning ? theme.colors.primaryContainer : theme.colors.surfaceVariant,
                borderColor: isScanning ? theme.colors.primary : theme.colors.outline
              }
            ]}
            textStyle={{ color: isScanning ? theme.colors.onPrimaryContainer : theme.colors.onSurfaceVariant }}
          >
            Scanning: {isScanning ? 'Active' : 'Inactive'}
          </Chip>
          <Chip mode="outlined" style={styles.statusChip}>
            Server connection: {proxyStream}
          </Chip>
          <Chip mode="outlined" style={styles.statusChip}>
            Unlock pass: {passReady ? 'Ready' : 'None'}
          </Chip>
        </View>
        <Paragraph style={subtitleStyle}>
          Found {devices.length} device{devices.length !== 1 ? 's' : ''}. DFU: {dfuState}
        </Paragraph>
        <TextInput
          mode="outlined"
          label="Server address"
          value={planeServer}
          onChangeText={setPlaneServer}
          autoCapitalize="none"
          autoCorrect={false}
          style={styles.payloadInput}
        />
        <TextInput
          mode="outlined"
          label="Service UUID"
          value={serviceUuid}
          onChangeText={(value) => setServiceUuid(value.trim())}
          autoCapitalize="characters"
          autoCorrect={false}
          maxLength={4}
          style={styles.payloadInput}
        />
        <Button
          compact
          mode="contained"
          disabled={!configChanged}
          onPress={() => run(restartBridge)}
          style={styles.passButton}
        >
          Restart bridge
        </Button>
        <TextInput
          mode="outlined"
          label="Example server URL"
          value={serverUrl}
          onChangeText={setServerUrl}
          autoCapitalize="none"
          autoCorrect={false}
          style={styles.payloadInput}
        />
        <Button compact mode="contained" onPress={() => run(issuePass)} style={styles.passButton}>
          Issue a 7 day unlock pass
        </Button>
        
        {unlockResult && <Text accessibilityLiveRegion="polite">{unlockResult}</Text>}
        <Divider style={styles.divider} />
        
        {devices.length === 0 ? (
          <View style={styles.emptyState}>
            <Text style={[styles.emptyText, { color: theme.colors.onSurfaceVariant }]}>
              No devices discovered yet...
            </Text>
            <Text style={[styles.emptySubtext, { color: theme.colors.outline }]}>
              Make sure your bridge is running and devices are discoverable
            </Text>
          </View>
        ) : (
          <View style={styles.deviceListContent}>
            {devices.map((device: Device, index: number) => (
              <Card 
                key={device.identifier || index} 
                style={[
                  styles.deviceCard,
                  { 
                    backgroundColor: 'transparent',
                    borderColor: theme.colors.outline,
                  }
                ]}
                mode="outlined"
              >
                <LinearGradient
                  colors={[
                    theme.colors.surfaceVariant,
                    theme.colors.surfaceVariant + '80'
                  ]}
                  start={{ x: 0, y: 0 }}
                  end={{ x: 1, y: 1 }}
                  style={styles.gradientBackground}
                >
                  <Card.Content>
                    <View style={styles.deviceHeader}>
                      <Text style={[styles.deviceName, { color: theme.colors.onSurfaceVariant }]}>
                        {"Device ID: " + device.identifier || 'Unknown Device'}
                      </Text>
                    </View>
                    <View style={styles.actions}>
                      <Button
                        compact
                        mode="outlined"
                        disabled={!passReady || unlockingDeviceId !== null}
                        onPress={() => run(() => unlockDevice(device.identifier))}
                      >
                        BLE Unlock
                      </Button>
                      <Button compact mode="outlined" onPress={() => run(() => identify(device.identifier))}>
                        Identify
                      </Button>
                      <Button
                        compact
                        mode="outlined"
                        onPress={() =>
                          run(async () => {
                            await enterInstallMode(serverUrl, device.linkDeviceId);
                            return waitForDeviceMode(device.identifier, false);
                          })
                        }
                      >
                        Enter install
                      </Button>
                      <Button
                        compact
                        mode="outlined"
                        onPress={() =>
                          run(() =>
                            device.dfuMode
                              ? exitDFUInstallMode(device.identifier)
                              : exitInstallMode(serverUrl, device.linkDeviceId),
                          )
                        }
                      >
                        Exit install
                      </Button>
                      <Button compact mode="outlined" onPress={() => run(() => setLockEngagedState(device.identifier, true))}>
                        Engage (beta, install mode)
                      </Button>
                      <Button compact mode="outlined" onPress={() => run(() => setLockEngagedState(device.identifier, false))}>
                        Disengage (beta, install mode)
                      </Button>
                      <Button
                        compact
                        mode="outlined"
                        onPress={() =>
                          run(async () => {
                            const result = await checkForDFU(device.identifier);
                            setDfuReadyIds((current) => {
                              const next = new Set(current);
                              if (result?.status === 'update_available') {
                                next.add(device.identifier);
                              } else {
                                next.delete(device.identifier);
                              }
                              return next;
                            });
                          })
                        }
                      >
                        Check DFU
                      </Button>
                      <Button
                        compact
                        mode="outlined"
                        onPress={() => {
                          if (!dfuReadyIds.has(device.identifier)) {
                            Alert.alert('Link Mobile', 'Check for a firmware update before starting DFU.');
                            return;
                          }
                          setDfuState('requesting install mode');
                          run(async () => {
                            await enterInstallMode(serverUrl, device.linkDeviceId);
                            setDfuState('waiting for DFU mode');
                            return startDFU(device.identifier);
                          });
                        }}
                      >
                        Start DFU
                      </Button>
                      <Button compact mode="outlined" onPress={() => run(() => cancelDFU())}>
                        Cancel DFU
                      </Button>
                    </View>
                    
                    <View style={styles.propertiesContainer}>
                      <Text style={[styles.propertiesTitle, { color: theme.colors.onSurfaceVariant }]}>
                        Properties:
                      </Text>
                      {Object.entries(device).sort((a, b) => a[0].localeCompare(b[0])).map(([key, value]) => (
                        <View key={key} style={styles.propertyRow}>
                          <Text style={[styles.propertyKey, { color: theme.colors.primary }]}>
                            {key}:
                          </Text>
                          <Text style={[styles.propertyValue, { color: theme.colors.onSurfaceVariant }]}>
                            {typeof value === 'object' 
                              ? JSON.stringify(value, null, 2)
                              : String(value)
                            }
                          </Text>
                        </View>
                      ))}
                    </View>
                  </Card.Content>
                </LinearGradient>
              </Card>
            ))}
          </View>
        )}
      </Card.Content>
    </Card>
  );
}

const styles = StyleSheet.create({
  container: {
    margin: 16,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  title: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 14,
    marginBottom: 8,
  },
  divider: {
    marginVertical: 12,
  },
  statusContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 8,
  },
  payloadInput: {
    marginBottom: 8,
  },
  passButton: {
    marginBottom: 8,
  },
  statusChip: {
    marginRight: 4,
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: 20,
  },
  emptyText: {
    fontSize: 16,
    marginBottom: 4,
    textAlign: 'center',
  },
  emptySubtext: {
    fontSize: 12,
    textAlign: 'center',
    lineHeight: 16,
  },
  deviceListContent: {
    paddingBottom: 4,
  },
  deviceCard: {
    marginBottom: 8,
    elevation: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
  },
  gradientBackground: {
    borderRadius: 8,
    padding: 10,
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 4,
    marginBottom: 8,
    width: '100%',
  },
  deviceHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  deviceName: {
    fontSize: 16,
    fontWeight: 'bold',
    flex: 1,
    marginRight: 8,
  },
  identifierChip: {
    // Theme colors applied dynamically
  },
  propertiesContainer: {
    marginTop: 8,
  },
  propertiesTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    marginBottom: 6,
  },
  propertyRow: {
    flexDirection: 'row',
    marginBottom: 4,
    flexWrap: 'wrap',
  },
  propertyKey: {
    fontSize: 12,
    fontWeight: 'bold',
    minWidth: 80,
    marginRight: 8,
  },
  propertyValue: {
    fontSize: 12,
    flex: 1,
    fontFamily: 'monospace',
    lineHeight: 16,
  },
});
