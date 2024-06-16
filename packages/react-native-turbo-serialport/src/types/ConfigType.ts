import { Mode } from './ParamsType';
import type { ParamsType } from './ParamsType';

export interface ConfigType {
  autoConnect?: boolean;
  mode?: Mode;
  params?: ParamsType;
}
