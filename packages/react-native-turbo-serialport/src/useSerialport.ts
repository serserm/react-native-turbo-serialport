import { useEffect } from 'react';
import { NativeEventEmitter } from 'react-native';

import { TurboSerialport } from './TurboSerialport';

export function useSerialport(params: { onChange?: (data: any) => void }): {
  send: () => void;
  state: () => void;
} {
  const { onChange } = params || {};

  useEffect(() => {
    const eventEmitter = new NativeEventEmitter(TurboSerialport);

    const eventListener =
      onChange && eventEmitter.addListener(`serialportChange`, onChange);

    return () => {
      eventListener?.remove();
    };
  }, []);

  function send() {
    TurboSerialport.send();
  }

  function state() {
    TurboSerialport.state().then((res: object) => {
      console.log('res', res);
    });
  }

  return {
    send,
    state,
  };
}
