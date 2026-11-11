import React, { createContext, useContext, useState, ReactNode } from 'react';

interface BridgeContextType {
  isInitialized: boolean;
  isScanning: boolean;
  setInitialized: (value: boolean) => void;
  setScanning: (value: boolean) => void;
}

const BridgeContext = createContext<BridgeContextType | undefined>(undefined);

export function BridgeProvider({ children }: { children: ReactNode }) {
  const [isInitialized, setIsInitialized] = useState(false);
  const [isScanning, setIsScanning] = useState(false);

  return (
    <BridgeContext.Provider
      value={{
        isInitialized,
        isScanning,
        setInitialized: setIsInitialized,
        setScanning: setIsScanning,
      }}
    >
      {children}
    </BridgeContext.Provider>
  );
}

export function useBridgeContext() {
  const context = useContext(BridgeContext);
  if (context === undefined) {
    throw new Error('useBridgeContext must be used within a BridgeProvider');
  }
  return context;
}
