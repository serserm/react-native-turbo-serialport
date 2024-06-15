package com.serserm.turboserialport;

import android.app.PendingIntent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.Build;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.felhr.usbserial.SerialPortCallback;

public class SerialPortBuilder {

  private Context context;
  private UsbManager usbManager;
  private Map<Integer, UsbDeviceStatus> serialStatusMap = new HashMap<>();
  private boolean broadcastRegistered = false;
  private boolean autoConnect = true;
  private int mode = Definitions.MODE_ASYNC;

  private int DATA_BIT     = UsbSerialInterface.DATA_BITS_8;
  private int STOP_BIT     = UsbSerialInterface.STOP_BITS_1;
  private int PARITY       = UsbSerialInterface.PARITY_NONE;
  private int FLOW_CONTROL = UsbSerialInterface.FLOW_CONTROL_OFF;
  private int BAUD_RATE    = 9600;
  private int portInterface = -1;
  private int returnedDataType = Definitions.RETURNED_DATA_TYPE_INTARRAY;
  private String driver    = "AUTO";

  private final ArrayBlockingQueue<PendingUsbPermission> queuedPermissions = new ArrayBlockingQueue<>(100);
  private PendingUsbPermission currentPendingPermission;
  private volatile boolean processingPermission = false;

  SerialPortBuilder(Context context) {
    this.context = context;
    this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
  }

  private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

    private UsbDevice getUsbDeviceFromIntent(Intent intent) {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
      } else {
        // Create local variable to keep scope of deprecation suppression smallest
        @SuppressWarnings("deprecation")
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        return device;
      }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      switch (intent.getAction()) {
        case Definitions.ACTION_USB_ATTACHED: {
          UsbDevice device = getUsbDeviceFromIntent(intent);
          attachedDevices(device);
        }
        break;
        case Definitions.ACTION_USB_DETACHED: {
          UsbDevice device = getUsbDeviceFromIntent(intent);
          detachedDevices(device);
        }
        break;
        case Definitions.ACTION_USB_PERMISSION: {
          boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
          boolean hasQueue = queuedPermissions.size() > 0;
          if (granted && autoConnect) {
            createAllPorts(currentPendingPermission.usbDeviceStatus);
          }
          onSerialPortsDetected(currentPendingPermission.usbDeviceStatus, hasQueue);
          if (hasQueue) {
            launchPermission();
          } else {
            processingPermission = false;
          }
        }
        break;
      }
    }
  };

  private int unsignedByteToInt(byte value) {
    return value & 0xFF;
  }

  private String bytesToHex(byte[] bytes) {
    char[] chars = new char[bytes.length * 2];
    for (int j = 0, l = bytes.length; j < l; j++) {
      int v = bytes[j] & 0xFF;
      chars[j * 2] = Definitions.hexArray[v >>> 4];
      chars[j * 2 + 1] = Definitions.hexArray[v & 0x0F];
    }
    return new String(chars);
  }

  private String toASCII(int value) {
    int length = 4;
    StringBuilder stringBuilder = new StringBuilder(length);
    for (int i = length - 1; i >= 0; i--) {
      stringBuilder.append((char) ((value >> (8 * i)) & 0xFF));
    }
    return stringBuilder.toString();
  }

  private class PendingUsbPermission {
    public PendingIntent pendingIntent;
    public UsbDeviceStatus usbDeviceStatus;
  }

  private PendingUsbPermission createUsbPermission(UsbDeviceStatus usbDeviceStatus) {
    int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0;
    PendingIntent mPendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(Definitions.ACTION_USB_PERMISSION), flags);
    PendingUsbPermission pendingUsbPermission = new PendingUsbPermission();
    pendingUsbPermission.pendingIntent = mPendingIntent;
    pendingUsbPermission.usbDeviceStatus = usbDeviceStatus;
    return pendingUsbPermission;
  }

  private void launchPermission() {
    try {
      processingPermission = true;
      currentPendingPermission = queuedPermissions.take();
      usbManager.requestPermission(currentPendingPermission.usbDeviceStatus.usbDevice,
              currentPendingPermission.pendingIntent);
    } catch (InterruptedException e) {
      e.printStackTrace();
      processingPermission = false;
    }
  }

  public void registerReceiver() {
    if (!broadcastRegistered) {
      IntentFilter filter = new IntentFilter();
      filter.addAction(Definitions.ACTION_USB_ATTACHED);
      filter.addAction(Definitions.ACTION_USB_DETACHED);
      filter.addAction(Definitions.ACTION_USB_PERMISSION);
      context.registerReceiver(usbReceiver, filter);
      broadcastRegistered = true;
    }
  }

  public void unregisterReceiver() {
    if (broadcastRegistered) {
      context.unregisterReceiver(usbReceiver);
      broadcastRegistered = false;
    }
  }

  private void attachedDevices(UsbDevice device) {
    if (device != null) {
      int deviceId = device.getDeviceId();
      sendType(deviceId, Definitions.onDeviceAttached);
    }
    boolean ret = getSerialPorts();
    if (!ret) {
      sendError(Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT, Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT_MESSAGE);
    }
  }

  private void detachedDevices(UsbDevice device) {
    if (device != null) {
      int deviceId = device.getDeviceId();
      sendType(deviceId, Definitions.onDeviceAttached);
//      boolean ret = builder.disconnectDevice(device);
//      ReadThread stream = serialStreamMap.get(deviceId);
//      stream.setKeep(false);
//      if (!ret) {
//        sendError(Definitions.ERROR_DISCONNECT_FAILED, Definitions.ERROR_DISCONNECT_FAILED_MESSAGE);
//      }
    }
  }

  public List<UsbDevice> getPossibleSerialPorts() {
    HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
    List<UsbDevice> deviceList = new ArrayList<>();
    if (!usbDevices.isEmpty()) {
      for (UsbDevice device: usbDevices.values()) {
        if (UsbSerialDevice.isSupported(device)) {
          deviceList.add(device);
        }
      }
    }
    return deviceList;
  }

  private boolean getSerialPorts() {
    registerReceiver();
    List<UsbDevice> usbDevices = getPossibleSerialPorts();
    List<UsbDeviceStatus> devices = new ArrayList<>();
    if (usbDevices.size() != 0) {
      for (UsbDevice device: usbDevices) {
        boolean granted = usbManager.hasPermission(device);
        if (!granted) {
          UsbDeviceStatus deviceStatus = new UsbDeviceStatus(device);
          deviceStatus.DATA_BIT = DATA_BIT;
          deviceStatus.STOP_BIT = STOP_BIT;
          deviceStatus.PARITY = PARITY;
          deviceStatus.FLOW_CONTROL = FLOW_CONTROL;
          deviceStatus.BAUD_RATE = BAUD_RATE;
          deviceStatus.portInterface = portInterface;
          deviceStatus.returnedDataType = returnedDataType;
          deviceStatus.driver = driver;
          serialStatusMap.add(deviceStatus.deviceId, deviceStatus);
          queuedPermissions.add(createUsbPermission(deviceStatus));
        }
      }
    } else {
      return false;
    }
    if (!processingPermission) {
      launchPermission();
    }
    return true;
  }

  public void createAllPorts(UsbDeviceStatus usbDeviceStatus) {
    int interfaceCount = usbDeviceStatus.usbDevice.getInterfaceCount();
    List<UsbSerialDevice> serialDevices = new ArrayList<>();
    if (interfaceCount > 0 && usbDeviceStatus.usbDeviceConnection == null) {
      usbDeviceStatus.usbDeviceConnection = usbManager.openDevice(usbDeviceStatus.usbDevice);
    }
    for (int portInterface = 0; portInterface < interfaceCount; portInterface++) {
      UsbSerialDevice usbSerialDevice = usbDeviceStatus.driver.equals("AUTO")
        ? UsbSerialDevice.createUsbSerialDevice(
                      usbDeviceStatus.usbDevice,
                      usbDeviceStatus.usbDeviceConnection,
                      portInterface)
        : UsbSerialDevice.createUsbSerialDevice(
                      usbDeviceStatus.driver,
                      usbDeviceStatus.usbDevice,
                      usbDeviceStatus.usbDeviceConnection,
                      portInterface);
      serialDevices.add(usbSerialDevice);
    }
    usbDeviceStatus.serialDevices = serialDevices;
  }

  private void openSyncPorts(UsbDeviceStatus usbDeviceStatus) {
    int n = 1;
    for (UsbSerialDevice usbSerialDevice: usbDeviceStatus.serialDevices) {
      if (usbSerialDevice.isOpen()) {
          usbSerialDevice.setDataBits(usbDeviceStatus.DATA_BIT);
          usbSerialDevice.setStopBits(usbDeviceStatus.STOP_BIT);
          usbSerialDevice.setParity(usbDeviceStatus.PARITY);
          usbSerialDevice.setFlowControl(usbDeviceStatus.FLOW_CONTROL);
          usbSerialDevice.setBaudRate(usbDeviceStatus.BAUD_RATE);
          n++;
      }
    }
  }

  private void closeSyncPorts(UsbDeviceStatus usbDeviceStatus) {
    for (UsbSerialDevice usbSerialDevice: usbDeviceStatus.serialDevices) {
//      usbSerialDevice.close();
    }
  }

  public UsbDevice chooseDevice(int deviceId) {
    List<UsbDevice> usbDevices = getPossibleSerialPorts();
    if (usbDevices.size() != 0) {
      for (UsbDevice device: usbDevices) {
        if (deviceId == -1 || device.getDeviceId() == deviceId) {
          return device;
        }
      }
    }
    return null;
  }

  public void init(
    boolean autoConnect,
    int mode,
    String driver,
    int portInterface,
    int returnedDataType,
    int baudRate,
    int dataBit,
    int stopBit,
    int parity,
    int flowControl
  ) {
    this.autoConnect = autoConnect;
    this.mode = mode;
    this.driver = driver;
    this.portInterface = portInterface;
    this.returnedDataType = returnedDataType;
    this.BAUD_RATE = baudRate;
    this.DATA_BIT = dataBit;
    this.STOP_BIT = stopBit;
    this.PARITY = parity;
    this.FLOW_CONTROL = flowControl;
  }

  public void setParams(
    int deviceId,
    String driver,
    int portInterface,
    int returnedDataType,
    int baudRate,
    int dataBit,
    int stopBit,
    int parity,
    int flowControl
  ) {
//     deviceStatus.DATA_BIT = dataBit;
//     deviceStatus.STOP_BIT = stopBit;
//     deviceStatus.PARITY = parity;
//     deviceStatus.FLOW_CONTROL = flowControl;
//     deviceStatus.BAUD_RATE = baudRate;
//     deviceStatus.portInterface = portInterface;
//     deviceStatus.returnedDataType = returnedDataType;
//     deviceStatus.driver = driver;
  }

  public void connect(int deviceId) {

  }

  public void disconnectAll() {
  }

  public void disconnect(int deviceId) {
    UsbDevice device = chooseDevice(deviceId);

    if (device != null && isConnected(deviceId)) {
//      boolean ret = builder.disconnectDevice(device);
//      ReadThread stream = serialStreamMap.get(deviceId);
//      stream.setKeep(false);
//      if (!ret) {
//        sendError(Definitions.ERROR_DISCONNECT_FAILED, Definitions.ERROR_DISCONNECT_FAILED_MESSAGE);
//      }

//      sendType(deviceId, Definitions.onConnected);
    }
  }

  public boolean isConnected(int deviceId) {
//    ReadThread stream = serialStreamMap.get(deviceId);
//    if (stream != null) {
//      return stream.isConnected();
//    }
    return false;
  }

  public void write(int deviceId, byte[] bytes) {
    if (isConnected(deviceId)) {
//      writeHandler.obtainMessage(0, deviceId, 0, bytes).sendToTarget();
    } else {

    }
  }
}
