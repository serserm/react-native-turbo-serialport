import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  addListener(eventName: string): void;

  removeListeners(count: number): void;

  send(): void;

  state(): Promise<object>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('TurboSerialport');
