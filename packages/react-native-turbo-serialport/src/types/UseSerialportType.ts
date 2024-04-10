import type { ParamsType } from './ParamsType';

export interface UseSerialportType {
  setParams: (params?: ParamsType) => void;
  listDevices: () => Promise<any>;
  connect: (deviceId: number) => void;
  disconnect: () => void;
  isConnected: () => Promise<boolean>;
  isServiceStarted: () => Promise<boolean>;
  writeBytes: (message: Array<number>) => void;
  writeString: (message: string) => void;
  writeBase64: (message: string) => void;
  writeHexString: (message: string) => void;
}
