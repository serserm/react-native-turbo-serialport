package com.serserm.turboserialport;

import java.util.List;

public interface SerialPortCallback {
  void onSerialPortsDetected(List<UsbSerialDevice> serialPorts);
}
