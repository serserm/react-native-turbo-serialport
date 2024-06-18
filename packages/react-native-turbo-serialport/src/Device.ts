import { TurboSerialport } from './TurboSerialport';
import {
  DataBit,
  DriverType,
  FlowControl,
  Parity,
  ReturnedDataType,
  StopBit,
} from './types';
import type { ParamsType } from './types';

export class Device {
  readonly #isSupported: boolean;
  readonly #deviceId: number;
  readonly #deviceName: string;
  readonly #deviceClass: number;
  readonly #deviceSubclass: number;
  readonly #deviceProtocol: number;
  readonly #vendorId: number;
  readonly #productId: number;
  readonly #manufacturerName: string;
  readonly #productName: string;
  readonly #serialNumber: string;
  readonly #interfaceCount: number;

  constructor(data: any) {
    this.#isSupported = data.isSupported ?? false;
    this.#deviceId = data.deviceId || 0;
    this.#deviceName = data.deviceName || '';
    this.#deviceClass = data.deviceClass || 0;
    this.#deviceSubclass = data.deviceSubclass || 0;
    this.#deviceProtocol = data.deviceProtocol || 0;
    this.#vendorId = data.vendorId || 0;
    this.#productId = data.productId || 0;
    this.#manufacturerName = data.manufacturerName || '';
    this.#productName = data.productName || '';
    this.#serialNumber = data.serialNumber || '';
    this.#interfaceCount = data.interfaceCount || 0;
  }

  get isSupported() {
    return this.#isSupported;
  }
  get deviceId() {
    return this.#deviceId;
  }
  get deviceName() {
    return this.#deviceName;
  }
  get deviceClass() {
    return this.#deviceClass;
  }
  get deviceSubclass() {
    return this.#deviceSubclass;
  }
  get deviceProtocol() {
    return this.#deviceProtocol;
  }
  get vendorId() {
    return this.#vendorId;
  }
  get productId() {
    return this.#productId;
  }
  get manufacturerName() {
    return this.#manufacturerName;
  }
  get productName() {
    return this.#productName;
  }
  get serialNumber() {
    return this.#serialNumber;
  }
  get interfaceCount() {
    return this.#interfaceCount;
  }

  setParams = (params?: ParamsType) => {
    const {
      driver = DriverType.AUTO,
      portInterface = -1,
      returnedDataType = ReturnedDataType.UTF8,
      baudRate = 9600,
      dataBit = DataBit.DATA_BITS_8,
      stopBit = StopBit.STOP_BITS_1,
      parity = Parity.PARITY_NONE,
      flowControl = FlowControl.FLOW_CONTROL_OFF,
    } = params || {};

    TurboSerialport.setParams(
      this.#deviceId,
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

  connect = () => {
    TurboSerialport.connect(this.#deviceId);
  };

  disconnect = () => {
    TurboSerialport.disconnect(this.#deviceId);
  };

  isConnected = (): Promise<boolean> => {
    return TurboSerialport.isConnected(this.#deviceId);
  };

  writeBytes = (message: Array<number>, portInterface?: number) => {
    TurboSerialport.writeBytes(this.#deviceId, portInterface || 0, message);
  };

  writeString = (message: string, portInterface?: number) => {
    TurboSerialport.writeString(this.#deviceId, portInterface || 0, message);
  };

  writeBase64 = (message: string, portInterface?: number) => {
    TurboSerialport.writeBase64(this.#deviceId, portInterface || 0, message);
  };

  writeHexString = (message: string, portInterface?: number) => {
    TurboSerialport.writeHexString(this.#deviceId, portInterface || 0, message);
  };
}
