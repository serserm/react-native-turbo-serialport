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

  function writeBytes(
    message: Array<number>,
    deviceId?: number,
    portInterface?: number,
  ) {
    return serialport.current.writeBytes(message, deviceId, portInterface);
  }

  function writeString(
    message: string,
    deviceId?: number,
    portInterface?: number,
  ) {
    return serialport.current.writeString(message, deviceId, portInterface);
  }

  function writeBase64(
    message: string,
    deviceId?: number,
    portInterface?: number,
  ) {
    return serialport.current.writeBase64(message, deviceId, portInterface);
  }

  function writeHexString(
    message: string,
    deviceId?: number,
    portInterface?: number,
  ) {
    return serialport.current.writeHexString(message, deviceId, portInterface);
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
