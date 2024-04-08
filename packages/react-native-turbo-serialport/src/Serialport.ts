import { type EmitterSubscription, NativeEventEmitter } from 'react-native';

import { Device } from './Device';
import { TurboSerialport } from './TurboSerialport';
import type { ListenerType } from './types';

export class Serialport {
  #subscription?: EmitterSubscription;

  startListening = (listener: ListenerType) => {
    const eventEmitter = new NativeEventEmitter(TurboSerialport);
    this.#subscription = eventEmitter.addListener(`serialportEvent`, listener);
  };

  stopListening = () => {
    this.#subscription?.remove();
  };

  listDevices = () => {
    return TurboSerialport.listDevices().then((devices: Array<any>) => {
      return devices.map(device => new Device(device));
    });
  };

  connect = (deviceId: number) => {
    TurboSerialport.connect(deviceId);
  };

  disconnect = () => {
    TurboSerialport.disconnect();
  };

  isSupported = (deviceId: number): Promise<boolean> => {
    return TurboSerialport.isSupported(deviceId);
  };

  isConnected = (): Promise<boolean> => {
    return TurboSerialport.isConnected();
  };

  isServiceStarted = (): Promise<boolean> => {
    return TurboSerialport.isServiceStarted();
  };

  writeBytes = (message: Array<number>) => {
    TurboSerialport.writeBytes(message);
  };

  writeString = (message: string) => {
    TurboSerialport.writeString(message);
  };

  writeBase64 = (message: string) => {
    TurboSerialport.writeBase64(message);
  };

  writeHexString = (message: string) => {
    TurboSerialport.writeHexString(message);
  };
}
