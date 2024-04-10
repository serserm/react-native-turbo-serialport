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

  setParams = (params?: ParamsType) => {
    const {
      driver = DriverType.AUTO,
      autoConnect = true,
      portInterface = -1,
      returnedDataType = ReturnedDataType.INTARRAY,
      baudRate = 9600,
      dataBit = DataBit.DATA_BITS_8,
      stopBit = StopBit.STOP_BITS_1,
      parity = Parity.PARITY_NONE,
      flowControl = FlowControl.FLOW_CONTROL_OFF,
    } = params || {};

    TurboSerialport.setParams(
      driver,
      autoConnect,
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
      switch (params.type) {
        case 'onDeviceAttached':
          listener({ type: params.type, data: new Device(params.data) });
          break;
        case 'onDeviceDetached':
          listener({ type: params.type, data: new Device(params.data) });
          break;
        default:
          listener(params);
      }
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

  connect = (deviceId: number) => {
    TurboSerialport.connect(deviceId);
  };

  disconnect = () => {
    TurboSerialport.disconnect();
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
