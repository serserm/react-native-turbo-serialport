package com.serserm.turboserialport;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

public class UsbDeviceStatus {
  private AtomicBoolean keep = new AtomicBoolean(true);
  public int deviceId;
  public UsbDevice usbDevice;
  public List<UsbSerialDevice> serialDevices = new ArrayList<>();
  public UsbDeviceConnection usbDeviceConnection;

  public int DATA_BIT     = UsbSerialInterface.DATA_BITS_8;
  public int STOP_BIT     = UsbSerialInterface.STOP_BITS_1;
  public int PARITY       = UsbSerialInterface.PARITY_NONE;
  public int FLOW_CONTROL = UsbSerialInterface.FLOW_CONTROL_OFF;
  public int BAUD_RATE    = 9600;
  public int portInterface = -1;
  public int returnedDataType = Definitions.RETURNED_DATA_TYPE_INTARRAY;
  public String driver    = "AUTO";

  public UsbDeviceStatus(UsbDevice usbDevice) {
    this.deviceId = usbDevice.getDeviceId();
    this.usbDevice = usbDevice;
  }

  public void setKeep(boolean keep) {
    this.keep.set(keep);
  }

  public boolean isConnected() {
    return this.keep.get();
  }

  @Override
  public boolean equals(Object obj) {
    UsbDeviceStatus usbDeviceStatus = (UsbDeviceStatus) obj;
    return usbDeviceStatus.usbDevice.getDeviceId() == usbDevice.getDeviceId();
  }
}
