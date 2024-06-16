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
    serialport.current.startListening(
      ({ type, id, portInterface, errorCode, errorMessage, data }) => {
        switch (type) {
          case 'onReadData':
            params.onReadData?.({ id, portInterface, data });
            break;
          case 'onError':
            params.onError?.({ errorCode, errorMessage });
            break;
          case 'onConnected':
            params.onConnected?.({ id, portInterface });
            break;
          case 'onDisconnected':
            params.onDisconnected?.({ id, portInterface });
            break;
          case 'onDeviceAttached':
            params.onDeviceAttached?.({ id });
            break;
          case 'onDeviceDetached':
            params.onDeviceDetached?.({ id });
            break;
        }
      },
    );

    return () => {
      serialport.current.stopListening();
    };
  }, []);

  function setParams(params?: ParamsType, deviceId?: number) {
    serialport.current.setParams(params, deviceId);
  }

  function listDevices() {
    return serialport.current.listDevices();
  }

  function connect(deviceId?: number) {
    serialport.current.connect(deviceId);
  }

  function disconnect(deviceId?: number) {
    serialport.current.disconnect(deviceId);
  }

  function isConnected(deviceId?: number) {
    return serialport.current.isConnected(deviceId);
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
