import { useEffect, useRef } from 'react';

import { Serialport } from './Serialport';
import type { SerialportParamsType, UseSerialportType } from './types';

export function useSerialport(params: SerialportParamsType): UseSerialportType {
  const serialport = useRef<Serialport>(new Serialport());

  useEffect(() => {
    serialport.current.startListening(
      ({ type, deviceId, portInterface, errorCode, errorMessage, data }) => {
        switch (type) {
          case 'onReadData':
            params.onReadData?.({ deviceId, portInterface, data });
            break;
          case 'onError':
            params.onError?.({ errorCode, errorMessage });
            break;
          case 'onConnected':
            params.onConnected?.({ deviceId, portInterface });
            break;
          case 'onDisconnected':
            params.onDisconnected?.({ deviceId, portInterface });
            break;
          case 'onDeviceAttached':
            params.onDeviceAttached?.({ deviceId });
            break;
          case 'onDeviceDetached':
            params.onDeviceDetached?.({ deviceId });
            break;
        }
      },
    );

    return () => {
      serialport.current.stopListening();
    };
  }, []);

  return {
    setParams: serialport.current.setParams,
    listDevices: serialport.current.listDevices,
    connect: serialport.current.connect,
    disconnect: serialport.current.disconnect,
    isConnected: serialport.current.isConnected,
    isServiceStarted: serialport.current.isServiceStarted,
    writeBytes: serialport.current.writeBytes,
    writeString: serialport.current.writeString,
    writeBase64: serialport.current.writeBase64,
    writeHexString: serialport.current.writeHexString,
  };
}
