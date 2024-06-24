import type { ParamsType } from './ParamsType';

export interface UseSerialportType {
  setParams: (params?: ParamsType, deviceId?: number) => void;
  listDevices: () => Promise<any>;
  connect: (deviceId: number) => void;
  disconnect: () => void;
  isConnected: () => Promise<boolean>;
  isServiceStarted: () => Promise<boolean>;
  writeBytes: (
    message: Array<number>,
    deviceId?: number,
    portInterface?: number,
  ) => void;
  writeString: (
    message: string,
    deviceId?: number,
    portInterface?: number,
  ) => void;
  writeBase64: (
    message: string,
    deviceId?: number,
    portInterface?: number,
  ) => void;
  writeHexString: (
    message: string,
    deviceId?: number,
    portInterface?: number,
  ) => void;
}
