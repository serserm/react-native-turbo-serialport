package com.serserm.turboserialport;

import java.util.List;

import com.felhr.usbserial.UsbSerialDevice;

public interface SerialPortCallback {
  void onError(int code, String message);
  void onDeviceAttached(int deviceId);
  void onDeviceDetached(int deviceId);
  void onConnected(int deviceId, int portInterface);
  void onDisconnected(int deviceId, int portInterface);
  void onReadData(int deviceId, int portInterface, int returnedDataType, byte[] bytes);
}
