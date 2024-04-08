import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  addListener(eventName: string): void;

  removeListeners(count: number): void;

  listDevices(): Promise<any>;

  connect(deviceId: number): void;

  disconnect(): void;

  isSupported(deviceId: number): Promise<any>;

  isConnected(): Promise<any>;

  isServiceStarted(): Promise<any>;

  writeBytes(message: Array<number>): void;

  writeString(message: string): void;

  writeBase64(message: string): void;

  writeHexString(message: string): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('TurboSerialport');
