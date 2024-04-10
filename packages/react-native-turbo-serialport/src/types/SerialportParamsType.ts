import type { ListenerType } from './ListenerType';
import type { ParamsType } from './ParamsType';

export interface SerialportParamsType extends ParamsType {
  onError?: ListenerType;
  onReadData?: ListenerType;
  onConnected?: ListenerType;
  onDeviceAttached?: ListenerType;
  onDeviceDetached?: ListenerType;
}
