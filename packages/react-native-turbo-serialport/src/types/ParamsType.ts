export enum DriverType {
  AUTO = 'AUTO',
  CDC = 'cdc',
  CH34x = 'ch34x',
  CP210x = 'cp210x',
  FTDI = 'ftdi',
  PL2303 = 'pl2303',
}

export type BaudRate =
  | 300
  | 600
  | 1200
  | 2400
  | 4800
  | 9600
  | 19200
  | 38400
  | 57600
  | 115200
  | 230400
  | 460800
  | 921600;

export enum DataBit {
  DATA_BITS_5 = 5,
  DATA_BITS_6,
  DATA_BITS_7,
  DATA_BITS_8,
}

export enum StopBit {
  STOP_BITS_1 = 1,
  STOP_BITS_2,
  STOP_BITS_15,
}

export enum Parity {
  PARITY_NONE,
  PARITY_ODD,
  PARITY_EVEN,
  PARITY_MARK,
  PARITY_SPACE,
}

export enum FlowControl {
  FLOW_CONTROL_OFF,
  FLOW_CONTROL_RTS_CTS,
  FLOW_CONTROL_DSR_DTR,
  FLOW_CONTROL_XON_XOFF,
}

export enum ReturnedDataType {
  INTARRAY = 1,
  HEXSTRING,
}

export interface ParamsType {
  driver?: DriverType;
  portInterface?: number;
  baudRate?: BaudRate;
  dataBit?: DataBit;
  stopBit?: StopBit;
  parity?: Parity;
  flowControl?: FlowControl;
  returnedDataType?: ReturnedDataType;
}

export enum Mode {
  ASYNC,
  SYNC,
}
