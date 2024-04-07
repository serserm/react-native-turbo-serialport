import { useEffect, useRef } from 'react';

import { Serialport } from './Serialport';
import type { SerialportParamsType } from './types';
import type { UseSerialportType } from './types/UseSerialportType';

export function useSerialport(params: SerialportParamsType): UseSerialportType {
  const serialport = useRef<Serialport>(new Serialport());

  useEffect(() => {
    serialport.current.startListening((data: any) => {
      params.onChange?.(data);
    });

    return () => {
      serialport.current.stopListening();
    };
  }, []);

  return {};
}
