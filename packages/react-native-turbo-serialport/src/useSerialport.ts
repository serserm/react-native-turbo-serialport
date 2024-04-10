import { useEffect, useRef } from 'react';

import { Serialport } from './Serialport';
import type {
  ParamsType,
  SerialportParamsType,
  UseSerialportType,
} from './types';

export function useSerialport(params: SerialportParamsType): UseSerialportType {
  const serialport = useRef<Serialport>(new Serialport());

  useEffect(() => {
    serialport.current.setParams(params);
    serialport.current.startListening(
      ({ type, errorCode, errorMessage, data }) => {
        switch (type) {
          case 'onReadData':
            params.onReadData?.({ data });
            break;
          case 'onError':
            params.onError?.({ errorCode, errorMessage });
            break;
          case 'onConnected':
            params.onConnected?.({ data });
            break;
          case 'onDeviceAttached':
            params.onDeviceAttached?.({ data });
            break;
          case 'onDeviceDetached':
            params.onDeviceDetached?.({ data });
            break;
        }
      },
    );

    return () => {
      serialport.current.stopListening();
    };
  }, []);

  function setParams(params?: ParamsType) {
    serialport.current.setParams(params);
  }

  function listDevices() {
    return serialport.current.listDevices();
  }

  function connect(deviceId: number) {
    serialport.current.connect(deviceId);
  }

  function disconnect() {
    serialport.current.disconnect();
  }

  function isConnected() {
    return serialport.current.isConnected();
  }

  function isServiceStarted() {
    return serialport.current.isServiceStarted();
  }

  function writeBytes(message: Array<number>) {
    return serialport.current.writeBytes(message);
  }

  function writeString(message: string) {
    return serialport.current.writeString(message);
  }

  function writeBase64(message: string) {
    return serialport.current.writeBase64(message);
  }

  function writeHexString(message: string) {
    return serialport.current.writeHexString(message);
  }

  return {
    setParams,
    listDevices,
    connect,
    disconnect,
    isConnected,
    isServiceStarted,
    writeBytes,
    writeString,
    writeBase64,
    writeHexString,
  };
}
