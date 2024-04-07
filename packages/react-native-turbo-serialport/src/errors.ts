import { Platform } from 'react-native';

export const LINKING_ERROR: string =
  `The package 'react-native-turbo-sensors' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- Not supported for iOS'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';
