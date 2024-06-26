package com.serserm.turboserialport;

import android.annotation.SuppressLint;
import android.app.PendingIntent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.felhr.usbserial.SerialInputStream;
import com.felhr.usbserial.SerialOutputStream;

public class SerialPortBuilder {
  private static SerialPortBuilder SerialPortBuilder;
  private final SerialPortCallback serialPortCallback;

  private Context context;
  private UsbManager usbManager;
  private Map<Integer, UsbDeviceStatus> serialStatusMap = new HashMap<>();
  private Map<Integer, List<ReadThread>> serialStreamMap = new HashMap<>();
  private WriteThread writeThread;
  private Handler writeHandler;
  private boolean broadcastRegistered = false;
  private boolean autoConnect = false;
  private int mode = Definitions.MODE_ASYNC;

  private int DATA_BIT     = UsbSerialInterface.DATA_BITS_8;
  private int STOP_BIT     = UsbSerialInterface.STOP_BITS_1;
  private int PARITY       = UsbSerialInterface.PARITY_NONE;
  private int FLOW_CONTROL = UsbSerialInterface.FLOW_CONTROL_OFF;
  private int BAUD_RATE    = 9600;
  private int portInterface = -1;
  private int returnedDataType = Definitions.RETURNED_DATA_TYPE_UTF8;
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
          int deviceId = currentPendingPermission.deviceId;
          UsbDeviceStatus usbDeviceStatus = serialStatusMap.get(deviceId);
          boolean hasQueue = queuedPermissions.size() > 0;
          if (usbDeviceStatus != null) {
            UsbDevice usbDevice = usbDeviceStatus.usbDevice;
            boolean granted = usbManager.hasPermission(usbDevice);
            if (granted) {
              createAllPorts(usbDeviceStatus);
              if (!openAllPorts(usbDeviceStatus)) {
                serialPortCallback.onError(Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT, Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT_MESSAGE);
              }
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

  private class ReadThread extends Thread {
    private AtomicBoolean keep = new AtomicBoolean(true);
    private int deviceId;
    private int portInterface;
    private int returnedDataType;
    private SerialInputStream serialInputStream;

    public ReadThread(int deviceId, int portInterface, int returnedDataType, SerialInputStream serialInputStream) {
      this.deviceId = deviceId;
      this.portInterface = portInterface;
      this.returnedDataType = returnedDataType;
      this.serialInputStream = serialInputStream;
    }

    @Override
    public void run() {
      while (keep.get()) {
        if (serialInputStream == null) {
          return;
        }
        byte[] buffer = new byte[100];
        int n = serialInputStream.read(buffer);
        if (n > 0) {
          byte[] received = new byte[n];
          System.arraycopy(buffer, 0, received, 0, n);
          serialPortCallback.onReadData(deviceId, portInterface, returnedDataType, received);
        }
      }
    }

    public void setKeep(boolean keep) {
      this.keep.set(keep);
    }
  }

  private class WriteThread extends Thread {

    @Override
    @SuppressLint("HandlerLeak")
    public void run() {
      Looper.prepare();
      writeHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
          int deviceId = msg.arg1;
          int port = msg.arg2;
          byte[] data = (byte[]) msg.obj;
          UsbDeviceStatus usbDeviceStatus = serialStatusMap.get(deviceId);
          if (usbDeviceStatus != null && port < usbDeviceStatus.serialDevices.size()) {
            UsbSerialDevice usbSerialDevice = usbDeviceStatus.serialDevices.get(port);
            if (usbSerialDevice != null && usbSerialDevice.isOpen()) {
              usbSerialDevice.getOutputStream().write(data);
            }
          }
        }
      };
      Looper.loop();
    }
  }

  private class PendingUsbPermission {
    public PendingIntent pendingIntent;
    public int deviceId;
  }

  private PendingUsbPermission createUsbPermission(int deviceId) {
    int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0;
    PendingIntent mPendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(Definitions.ACTION_USB_PERMISSION), flags);
    PendingUsbPermission pendingUsbPermission = new PendingUsbPermission();
    pendingUsbPermission.pendingIntent = mPendingIntent;
    pendingUsbPermission.deviceId = deviceId;
    return pendingUsbPermission;
  }

  private void launchPermission() {
    try {
      boolean hasQueue = queuedPermissions.size() > 0;
      if (hasQueue) {
        processingPermission = true;
        currentPendingPermission = queuedPermissions.take();
        int deviceId = currentPendingPermission.deviceId;
        UsbDeviceStatus usbDeviceStatus = serialStatusMap.get(deviceId);
        if (usbDeviceStatus != null) {
          usbManager.requestPermission(usbDeviceStatus.usbDevice,
                                currentPendingPermission.pendingIntent);
        }
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
      processingPermission = false;
    }
  }

  public void registerReceiver() {
    if (writeThread == null) {
      writeThread = new WriteThread();
      writeThread.start();
    }
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
    if (getSerialPorts()) {
      if (device != null) {
        int deviceId = device.getDeviceId();
        serialPortCallback.onDeviceAttached(deviceId);
        if (autoConnect) {
          connect(deviceId);
        }
      }
    } else {
      serialPortCallback.onError(Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT, Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT_MESSAGE);
    }
  }

  private void detachedDevices(UsbDevice device) {
    if (device != null) {
      int deviceId = device.getDeviceId();
      UsbDeviceStatus usbDeviceStatus = serialStatusMap.get(deviceId);
      if (usbDeviceStatus != null) {
        closeAllPorts(usbDeviceStatus);
        serialStatusMap.remove(deviceId);
      } else {
        serialPortCallback.onError(Definitions.ERROR_DISCONNECT_FAILED, Definitions.ERROR_DISCONNECT_FAILED_MESSAGE);
      }
      serialPortCallback.onDeviceDetached(deviceId);
    } else {
      serialPortCallback.onError(Definitions.ERROR_DISCONNECT_FAILED, Definitions.ERROR_DISCONNECT_FAILED_MESSAGE);
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
    if (usbDevices.size() > 0) {
      for (UsbDevice device: usbDevices) {
        int deviceId = device.getDeviceId();
        if (serialStatusMap.get(deviceId) == null) {
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
          serialStatusMap.put(deviceId, usbDeviceStatus);
        }
      }
    } else {
      return false;
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
        UsbSerialDevice usbSerialDevice = null;
        if (usbDeviceStatus.portInterface < 0
            || usbDeviceStatus.portInterface >= 0 && usbDeviceStatus.portInterface == portInterface) {
          usbSerialDevice = usbDeviceStatus.driver.equals("AUTO")
                    ? UsbSerialDevice.createUsbSerialDevice(
                                  usbDeviceStatus.usbDevice,
                                  usbDeviceStatus.usbDeviceConnection,
                                  portInterface)
                    : UsbSerialDevice.createUsbSerialDevice(
                                  usbDeviceStatus.driver,
                                  usbDeviceStatus.usbDevice,
                                  usbDeviceStatus.usbDeviceConnection,
                                  portInterface);
        }
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
    List<ReadThread> streamDevices = new ArrayList<>();
    int portInterface = 0;
    int openCount = 0;
    for (UsbSerialDevice usbSerialDevice: usbDeviceStatus.serialDevices) {
      boolean isOpen = false;
      ReadThread stream = null;
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
          stream = new ReadThread(
                              usbDeviceStatus.deviceId,
                              portInterface,
                              usbDeviceStatus.returnedDataType,
                              usbSerialDevice.getInputStream());
          stream.start();
        } else {
          UsbSerialInterface.UsbReadCallback mCallback = usbReadCallback(usbDeviceStatus.deviceId, portInterface, usbDeviceStatus.returnedDataType);
          usbSerialDevice.read(mCallback);
        }
        openCount++;
      }
      streamDevices.add(stream);
      portInterface++;
    }
    if (mode == Definitions.MODE_SYNC) {
      serialStreamMap.put(usbDeviceStatus.deviceId, streamDevices);
    }
    usbDeviceStatus.setConnect(openCount > 0);
    return openCount > 0;
  }

  public void closeAllPorts(UsbDeviceStatus usbDeviceStatus) {
    int portInterface = 0;
    for (UsbSerialDevice usbSerialDevice: usbDeviceStatus.serialDevices) {
      if (usbSerialDevice != null) {
        if (mode == Definitions.MODE_SYNC) {
          List<ReadThread> streamDevices = serialStreamMap.get(usbDeviceStatus.deviceId);
          if (streamDevices != null) {
            ReadThread stream = streamDevices.get(portInterface);
            if (stream != null) {
              stream.setKeep(false);
            }
          }
          usbSerialDevice.syncClose();
        } else {
          usbSerialDevice.close();
        }
        serialPortCallback.onDisconnected(usbDeviceStatus.deviceId, portInterface);
      }
      portInterface++;
    }
    usbDeviceStatus.setConnect(false);
  }

  public UsbDeviceStatus chooseDevice(int deviceId) {
    if (deviceId > 0) {
      return serialStatusMap.get(deviceId);
    }
    List<UsbDevice> usbDevices = getPossibleSerialPorts();
    if (usbDevices.size() > 0) {
      UsbDevice usbDevice = usbDevices.get(0);
      return serialStatusMap.get((int) usbDevice.getDeviceId());
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
    UsbDeviceStatus usbDeviceStatus = chooseDevice(deviceId);
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
    UsbDeviceStatus usbDeviceStatus = chooseDevice(deviceId);
    if (usbDeviceStatus != null && !usbDeviceStatus.isConnected()) {
      boolean granted = usbManager.hasPermission(usbDeviceStatus.usbDevice);
      if (granted) {
        createAllPorts(usbDeviceStatus);
        if (!openAllPorts(usbDeviceStatus)) {
          serialPortCallback.onError(Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT, Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT_MESSAGE);
        }
      } else {
        queuedPermissions.add(createUsbPermission(deviceId));
        if (!processingPermission) {
          launchPermission();
        }
      }
    } else {
      getSerialPorts();
      serialPortCallback.onError(Definitions.ERROR_CONNECTION_FAILED, Definitions.ERROR_CONNECTION_FAILED_MESSAGE);
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
    UsbDeviceStatus usbDeviceStatus = chooseDevice(deviceId);
    if (usbDeviceStatus != null) {
      closeAllPorts(usbDeviceStatus);
    }
  }

  public boolean isConnected(int deviceId) {
    UsbDeviceStatus usbDeviceStatus = chooseDevice(deviceId);
    if (usbDeviceStatus != null) {
      return usbDeviceStatus.isConnected();
    }
    return false;
  }

  public void write(int deviceId, int portInterface, byte[] bytes) {
    UsbDeviceStatus usbDeviceStatus = chooseDevice(deviceId);
    if (usbDeviceStatus != null && usbDeviceStatus.isConnected() && usbDeviceStatus.serialDevices.size() > 0) {
      UsbSerialDevice usbSerialDevice = usbDeviceStatus.serialDevices.get(portInterface);
      if (usbSerialDevice != null && usbSerialDevice.isOpen()) {
        if (mode == Definitions.MODE_SYNC) {
          if (writeThread != null) {
            writeHandler.obtainMessage(0, deviceId, portInterface, bytes).sendToTarget();
          }
        } else {
          usbSerialDevice.write(bytes);
        }
      }
    } else {
      serialPortCallback.onError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE);
    }
  }
}
