import { type EmitterSubscription, NativeEventEmitter } from 'react-native';

import { TurboSerialport } from './TurboSerialport';
import type { ListenerType } from './types';

export class Serialport {
  #subscription?: EmitterSubscription;

  startListening = (listener: ListenerType) => {
    const eventEmitter = new NativeEventEmitter(TurboSerialport);
    this.#subscription = eventEmitter.addListener(`serialportEvent`, listener);
  };

  stopListening = (): void => {
    this.#subscription?.remove();
  };
}
