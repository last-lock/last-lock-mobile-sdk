import React from 'react';
import {
  onDeviceDiscovered,
  onDeviceInformation,
} from '@last-lock/lastlock-mobile-sdk';

interface Device {
  identifier: string;
  linkDeviceId: string;
  name?: string;
  rssi?: number;
  connectionStatus?: string;
  signalStrength?: string;
  oobTimestamp?: number;
  firmwareVersion?: string;
}

export function useDeviceDiscovery() {
  const [devices, setDevices] = React.useState<Device[]>([]);

  React.useEffect(() => {
    const discoveredListener = onDeviceDiscovered((event) => {
      setDevices((prev) => {
        const existingIndex = prev.findIndex((device) => device.identifier === event.identifier);
        if (existingIndex >= 0) {
          const updatedDevices = [...prev];
          updatedDevices[existingIndex] = {...prev[existingIndex], ...event};
          return updatedDevices;
        }
        return [...prev, event];
      });
    });

    const informationListener = onDeviceInformation((event) => {
      setDevices((prev) =>
        prev.map((device) =>
          device.identifier === event.identifier ? {...device, oobTimestamp: event.oobTimestamp ?? device.oobTimestamp, firmwareVersion: event.firmwareVersion} : device,
        ),
      );
    });

    return () => {
      discoveredListener.remove();
      informationListener.remove();
    };
  }, []);

  return devices;
}
