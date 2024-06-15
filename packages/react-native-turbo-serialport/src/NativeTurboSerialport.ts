import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  init(
    autoConnect: boolean,
    mode: number,
    driver: string,
    portInterface: number,
    returnedDataType: number,
    baudRate: number,
    dataBit: number,
    stopBit: number,
    parity: number,
    flowControl: number,
  ): void;

  setParams(
    deviceId: number,
    driver: string,
    portInterface: number,
    returnedDataType: number,
    baudRate: number,
    dataBit: number,
    stopBit: number,
    parity: number,
    flowControl: number,
  ): void;

  addListener(eventName: string): void;

  removeListeners(count: number): void;

  listDevices(): Promise<any>;

  connect(deviceId: number): void;

  disconnect(deviceId: number): void;

  isConnected(deviceId: number): Promise<any>;

  isServiceStarted(): Promise<any>;

  writeBytes(deviceId: number, message: Array<number>): void;

  writeString(deviceId: number, message: string): void;

  writeBase64(deviceId: number, message: string): void;

  writeHexString(deviceId: number, message: string): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('TurboSerialport');
