package com.serserm.turboserialport;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

public class UsbDeviceStatus {
  private AtomicBoolean connect = new AtomicBoolean(false);
  public final int deviceId;
  public final UsbDevice usbDevice;
  public List<UsbSerialDevice> serialDevices = new ArrayList<>();
  public UsbDeviceConnection usbDeviceConnection;

  public String driver    = "AUTO";
  public int portInterface = -1;
  public int returnedDataType = Definitions.RETURNED_DATA_TYPE_UTF8;
  public int BAUD_RATE    = 9600;
  public int DATA_BIT     = UsbSerialInterface.DATA_BITS_8;
  public int STOP_BIT     = UsbSerialInterface.STOP_BITS_1;
  public int PARITY       = UsbSerialInterface.PARITY_NONE;
  public int FLOW_CONTROL = UsbSerialInterface.FLOW_CONTROL_OFF;

  public UsbDeviceStatus(UsbDevice usbDevice) {
    this.deviceId = usbDevice.getDeviceId();
    this.usbDevice = usbDevice;
  }

  public void setParams(
    String driver,
    int portInterface,
    int returnedDataType,
    int baudRate,
    int dataBit,
    int stopBit,
    int parity,
    int flowControl
  ) {
    this.driver = driver;
    this.portInterface = portInterface;
    this.returnedDataType = returnedDataType;
    this.BAUD_RATE = baudRate;
    this.DATA_BIT = dataBit;
    this.STOP_BIT = stopBit;
    this.PARITY = parity;
    this.FLOW_CONTROL = flowControl;
  }

  public void setConnect(boolean connect) {
    this.connect.set(connect);
  }

  public boolean isConnected() {
    return this.connect.get();
  }

  @Override
  public boolean equals(Object obj) {
    UsbDeviceStatus usbDeviceStatus = (UsbDeviceStatus) obj;
    return usbDeviceStatus.usbDevice.getDeviceId() == usbDevice.getDeviceId();
  }
}
