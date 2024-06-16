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

public class SerialPortBuilder {
  private static SerialPortBuilder SerialPortBuilder;
  private final SerialPortCallback serialPortCallback;

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

  private SerialPortBuilder(Context context, SerialPortCallback serialPortCallback) {
    this.context = context;
    this.serialPortCallback = serialPortCallback;
    this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
  }

  public static SerialPortBuilder createSerialPortBuilder(Context context, SerialPortCallback serialPortCallback) {
    if(SerialPortBuilder == null) {
      SerialPortBuilder = new SerialPortBuilder(context, serialPortCallback);
    }
    return SerialPortBuilder;
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
          UsbDevice device = currentPendingPermission.usbDeviceStatus.usbDevice;
          boolean granted = usbManager.hasPermission(device);
          boolean hasQueue = queuedPermissions.size() > 0;
          if (granted && autoConnect) {
            createAllPorts(currentPendingPermission.usbDeviceStatus);
            if (!openAllPorts(currentPendingPermission.usbDeviceStatus)) {
              serialPortCallback.onError(Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT, Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT_MESSAGE);
            }
          }
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

  private UsbSerialInterface.UsbReadCallback usbReadCallback(int deviceId, int portInterface, int returnedDataType) {
    return new UsbSerialInterface.UsbReadCallback() {
      @Override
      public void onReceivedData(byte[] bytes) {
        serialPortCallback.onReadData(deviceId, portInterface, returnedDataType, bytes);
      }
    };
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
      serialPortCallback.onDeviceAttached(deviceId);
    }
    if (!getSerialPorts()) {
      serialPortCallback.onError(Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT, Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT_MESSAGE);
    }
  }

  private void detachedDevices(UsbDevice device) {
    if (device != null) {
      int deviceId = device.getDeviceId();
      UsbDeviceStatus usbDeviceStatus = serialStatusMap.get(deviceId);
      if (usbDeviceStatus != null) {
        closeAllPorts(usbDeviceStatus);
      } else {
        serialPortCallback.onError(Definitions.ERROR_DISCONNECT_FAILED, Definitions.ERROR_DISCONNECT_FAILED_MESSAGE);
      }
      serialPortCallback.onDeviceDetached(deviceId);
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

  public boolean getSerialPorts() {
    registerReceiver();
    List<UsbDevice> usbDevices = getPossibleSerialPorts();
    List<UsbDeviceStatus> devices = new ArrayList<>();
    if (usbDevices.size() != 0) {
      for (UsbDevice device: usbDevices) {
        boolean granted = usbManager.hasPermission(device);
        UsbDeviceStatus usbDeviceStatus = new UsbDeviceStatus(device);
        usbDeviceStatus.setParams(
          driver,
          portInterface,
          returnedDataType,
          BAUD_RATE,
          DATA_BIT,
          STOP_BIT,
          PARITY,
          FLOW_CONTROL
        );
        if (serialStatusMap.get(usbDeviceStatus.deviceId) == null) {
          serialStatusMap.put(usbDeviceStatus.deviceId, usbDeviceStatus);
        }
        if (granted && autoConnect) {
          createAllPorts(usbDeviceStatus);
          if (!openAllPorts(usbDeviceStatus)) {
            serialPortCallback.onError(Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT, Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT_MESSAGE);
          }
        }
        if (!granted) {
          queuedPermissions.add(createUsbPermission(usbDeviceStatus));
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
    boolean granted = usbManager.hasPermission(usbDeviceStatus.usbDevice);
    if (!granted) {
      return;
    }
    int interfaceCount = usbDeviceStatus.usbDevice.getInterfaceCount();
    List<UsbSerialDevice> serialDevices = new ArrayList<>();
    if (interfaceCount > 0 && usbDeviceStatus.usbDeviceConnection == null) {
      usbDeviceStatus.usbDeviceConnection = usbManager.openDevice(usbDeviceStatus.usbDevice);
    }
    if (usbDeviceStatus.usbDeviceConnection != null) {
      for (int portInterface = 0; portInterface < interfaceCount; portInterface++) {
        if (usbDeviceStatus.portInterface >= 0 && usbDeviceStatus.portInterface != portInterface) {
          break;
        }
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
    }
    usbDeviceStatus.serialDevices = serialDevices;
  }

  public boolean openAllPorts(UsbDeviceStatus usbDeviceStatus) {
    boolean granted = usbManager.hasPermission(usbDeviceStatus.usbDevice);
    if (!granted) {
      return false;
    }
    int portInterface = 0;
    int openCount = 0;
    for (UsbSerialDevice usbSerialDevice: usbDeviceStatus.serialDevices) {
      boolean isOpen = false;
      if (usbSerialDevice != null && !usbSerialDevice.isOpen()) {
        isOpen = mode == Definitions.MODE_SYNC ? usbSerialDevice.syncOpen() : usbSerialDevice.open();
      }
      if (isOpen) {
        serialPortCallback.onConnected(usbDeviceStatus.deviceId, portInterface);
        usbSerialDevice.setDataBits(usbDeviceStatus.DATA_BIT);
        usbSerialDevice.setStopBits(usbDeviceStatus.STOP_BIT);
        usbSerialDevice.setParity(usbDeviceStatus.PARITY);
        usbSerialDevice.setFlowControl(usbDeviceStatus.FLOW_CONTROL);
        usbSerialDevice.setBaudRate(usbDeviceStatus.BAUD_RATE);
        if (mode == Definitions.MODE_SYNC) {
          // TODO
        } else {
          UsbSerialInterface.UsbReadCallback mCallback = usbReadCallback(usbDeviceStatus.deviceId, portInterface, usbDeviceStatus.returnedDataType);
          usbSerialDevice.read(mCallback);
        }
        openCount++;
      }
      portInterface++;
    }
    usbDeviceStatus.setConnect(openCount > 0);
    return openCount > 0;
  }

  public void closeAllPorts(UsbDeviceStatus usbDeviceStatus) {
    int portInterface = usbDeviceStatus.serialDevices.size();
    for (UsbSerialDevice usbSerialDevice: usbDeviceStatus.serialDevices) {
      if (usbSerialDevice != null && usbSerialDevice.isOpen()) {
        if (mode == Definitions.MODE_SYNC) {
          usbSerialDevice.syncClose();
        } else {
          usbSerialDevice.close();
        }
        serialPortCallback.onDisconnected(usbDeviceStatus.deviceId, portInterface);
      }
      portInterface--;
    }
    usbDeviceStatus.setConnect(false);
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
    if (!broadcastRegistered) {
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
    UsbDeviceStatus usbDeviceStatus = serialStatusMap.get(deviceId);
    if (usbDeviceStatus != null) {
      usbDeviceStatus.setParams(
        driver,
        portInterface,
        returnedDataType,
        baudRate,
        dataBit,
        stopBit,
        parity,
        flowControl
      );
    }
  }

  public void connect(int deviceId) {
    UsbDeviceStatus usbDeviceStatus = serialStatusMap.get(deviceId);
    if (usbDeviceStatus != null && !usbDeviceStatus.isConnected()) {
      boolean granted = usbManager.hasPermission(usbDeviceStatus.usbDevice);
      if (granted) {
        createAllPorts(usbDeviceStatus);
        if (!openAllPorts(usbDeviceStatus)) {
          serialPortCallback.onError(Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT, Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT_MESSAGE);
        }
      } else {
        queuedPermissions.add(createUsbPermission(usbDeviceStatus));
        if (!processingPermission) {
          launchPermission();
        }
      }
    }
  }

  public void disconnectAll() {
    for (Map.Entry<Integer, UsbDeviceStatus> entry : serialStatusMap.entrySet()) {
      UsbDeviceStatus usbDeviceStatus = entry.getValue();
      if (usbDeviceStatus != null) {
        closeAllPorts(usbDeviceStatus);
      }
    }
  }

  public void disconnect(int deviceId) {
    UsbDeviceStatus usbDeviceStatus = serialStatusMap.get(deviceId);
    if (usbDeviceStatus != null) {
      closeAllPorts(usbDeviceStatus);
    }
  }

  public boolean isConnected(int deviceId) {
    UsbDeviceStatus usbDeviceStatus = serialStatusMap.get(deviceId);
    if (usbDeviceStatus != null) {
      return usbDeviceStatus.isConnected();
    }
    return false;
  }

  public void write(int deviceId, byte[] bytes) {
    UsbDeviceStatus usbDeviceStatus = serialStatusMap.get(deviceId);
    if (usbDeviceStatus != null && usbDeviceStatus.isConnected()) {
      int portInterface = 0;
      for (UsbSerialDevice usbSerialDevice: usbDeviceStatus.serialDevices) {
        if (usbSerialDevice != null && usbSerialDevice.isOpen()) {
          usbSerialDevice.write(bytes);
        }
        portInterface++;
      }
    } else {
      serialPortCallback.onError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE);
    }
  }
}
