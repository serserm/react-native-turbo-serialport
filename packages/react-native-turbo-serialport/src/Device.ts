import { TurboSerialport } from './TurboSerialport';

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

  connect = () => {
    TurboSerialport.connect(this.#deviceId);
  };

  disconnect = () => {
    TurboSerialport.disconnect();
  };
}
