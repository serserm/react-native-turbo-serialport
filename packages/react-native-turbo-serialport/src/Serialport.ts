import { type EmitterSubscription, NativeEventEmitter } from 'react-native';

import { Device } from './Device';
import { TurboSerialport } from './TurboSerialport';
import {
  DataBit,
  DriverType,
  FlowControl,
  Parity,
  ReturnedDataType,
  StopBit,
} from './types';
import type { ListenerType, ParamsType } from './types';

export class Serialport {
  #subscription?: EmitterSubscription;

  setParams = (params?: ParamsType, deviceId?: number) => {
    const {
      driver = DriverType.AUTO,
      portInterface = -1,
      returnedDataType = ReturnedDataType.INTARRAY,
      baudRate = 9600,
      dataBit = DataBit.DATA_BITS_8,
      stopBit = StopBit.STOP_BITS_1,
      parity = Parity.PARITY_NONE,
      flowControl = FlowControl.FLOW_CONTROL_OFF,
    } = params || {};

    TurboSerialport.setParams(
      deviceId || -1,
      driver,
      portInterface,
      returnedDataType,
      baudRate,
      dataBit,
      stopBit,
      parity,
      flowControl,
    );
  };

  startListening = (listener: ListenerType) => {
    const eventEmitter = new NativeEventEmitter(TurboSerialport);
    this.#subscription = eventEmitter.addListener(`serialportEvent`, params => {
      listener(params);
    });
  };

  stopListening = () => {
    this.#subscription?.remove();
  };

  listDevices = () => {
    return TurboSerialport.listDevices().then((devices: Array<any>) => {
      return devices.map(device => new Device(device));
    });
  };

  connect = (deviceId?: number) => {
    TurboSerialport.connect(deviceId || -1);
  };

  disconnect = (deviceId?: number) => {
    TurboSerialport.disconnect(deviceId || -1);
  };

  isConnected = (deviceId?: number): Promise<boolean> => {
    return TurboSerialport.isConnected(deviceId || -1);
  };

  isServiceStarted = (): Promise<boolean> => {
    return TurboSerialport.isServiceStarted();
  };

  writeBytes = (
    message: Array<number>,
    deviceId?: number,
    portInterface?: number,
  ) => {
    TurboSerialport.writeBytes(deviceId || -1, portInterface || 0, message);
  };

  writeString = (
    message: string,
    deviceId?: number,
    portInterface?: number,
  ) => {
    TurboSerialport.writeString(deviceId || -1, portInterface || 0, message);
  };

  writeBase64 = (
    message: string,
    deviceId?: number,
    portInterface?: number,
  ) => {
    TurboSerialport.writeBase64(deviceId || -1, portInterface || 0, message);
  };

  writeHexString = (
    message: string,
    deviceId?: number,
    portInterface?: number,
  ) => {
    TurboSerialport.writeHexString(deviceId || -1, portInterface || 0, message);
  };
}
