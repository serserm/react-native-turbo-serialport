import { useEffect, useRef } from 'react';

import { Serialport } from './Serialport';
import type { SerialportParamsType } from './types';
import type { UseSerialportType } from './types/UseSerialportType';

export function useSerialport(params: SerialportParamsType): UseSerialportType {
  const serialport = useRef<Serialport>(new Serialport());

  useEffect(() => {
    serialport.current.startListening(({ type, ...data }) => {
      switch (type) {
        case 'onError':
          params.onError?.(data);
          break;
        case 'onService':
          params.onService?.(data);
          break;
        case 'onConnected':
          params.onConnected?.(data);
          break;
        case 'onDeviceAttached':
          params.onDeviceAttached?.(data);
          break;
        case 'onDeviceDetached':
          params.onDeviceDetached?.(data);
          break;
        case 'onReadData':
          params.onReadData?.(data);
          break;
      }
    });

    return () => {
      serialport.current.stopListening();
    };
  }, []);

  function listDevices() {
    return serialport.current.listDevices();
  }

  return {
    listDevices,
  };
}
