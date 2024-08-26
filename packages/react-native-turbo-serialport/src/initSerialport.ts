import { TurboSerialport } from './TurboSerialport';
import {
  DataBit,
  DriverType,
  FlowControl,
  Mode,
  Parity,
  ReturnedDataType,
  StopBit,
} from './types';
import type { ConfigType } from './types';

export function initSerialport(consfig?: ConfigType) {
  const { autoConnect, mode, params } = consfig || {};

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

  TurboSerialport.init(
    autoConnect ?? false,
    mode ?? Mode.ASYNC,
    driver,
    portInterface,
    returnedDataType,
    baudRate,
    dataBit,
    stopBit,
    parity,
    flowControl,
  );
}
