package com.serserm.turboserialport;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.annotation.SuppressLint;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableArray;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.Build;
import android.os.Looper;
import android.os.Handler;
import android.os.Message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.felhr.usbserial.SerialPortBuilder;
import com.felhr.usbserial.SerialPortCallback;
import com.felhr.usbserial.SerialInputStream;
import com.felhr.usbserial.SerialOutputStream;

public class UsbSerialport {

  private ReactApplicationContext reactContext;
  private UsbManager usbManager;
  private SerialPortBuilder builder;
  private Map<Integer, ReadThread> serialStreamMap = new HashMap<>();
  private boolean broadcastRegistered = false;

  private Handler writeHandler;
  private WriteThread writeThread;

  // Connection Settings
  private int DATA_BIT     = UsbSerialInterface.DATA_BITS_8;
  private int STOP_BIT     = UsbSerialInterface.STOP_BITS_1;
  private int PARITY       = UsbSerialInterface.PARITY_NONE;
  private int FLOW_CONTROL = UsbSerialInterface.FLOW_CONTROL_OFF;
  private int BAUD_RATE    = 9600;
  private boolean autoConnect = true;
  private int portInterface = -1;
  private int returnedDataType = Definitions.RETURNED_DATA_TYPE_INTARRAY;
  private String driver = "AUTO";

  UsbSerialport(ReactApplicationContext context) {
    reactContext = context;
    usbManager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);
    builder = SerialPortBuilder.createSerialPortBuilder(serialPortCallback);
    builder.getSerialPorts(reactContext);
  }

  private void sendEvent(String eventName, @Nullable WritableMap params) {
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }

  private void sendError(int code, String message) {
    WritableMap err = Arguments.createMap();
    err.putString("type", Definitions.onError);
    err.putInt("errorCode", code);
    err.putString("errorMessage", message);
    sendEvent(Definitions.serialPortEvent, err);
  }

  private void sendType(int deviceId, String type) {
    WritableMap params = Arguments.createMap();
    params.putString("type", type);
    params.putInt("id", deviceId);
    sendEvent(Definitions.serialPortEvent, params);
  }

  /******************************* USB SERVICE **********************************/

  SerialPortCallback serialPortCallback = new SerialPortCallback() {
    @Override
    public void onSerialPortsDetected(List<UsbSerialDevice> serialPorts) {
      if (serialPorts.size() == 0) {
        return;
      }

      if (writeThread == null) {
        writeThread = new WriteThread();
        writeThread.start();
      }

      for (UsbSerialDevice serialDevice: serialPorts) {
        int deviceId = serialDevice.getDeviceId();
        ReadThread stream = serialStreamMap.get(deviceId);
        if (stream == null && serialDevice.isOpen()) {
          ReadThread readThread = new ReadThread(deviceId, serialDevice);
          serialStreamMap.put(deviceId, readThread);
          readThread.start();
          sendType(deviceId, Definitions.onConnected);
        }
      }
    }
  };

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
      }
    }
  };

  private void registerReceiver() {
    if (!broadcastRegistered) {
      IntentFilter filter = new IntentFilter();
      filter.addAction(Definitions.ACTION_USB_ATTACHED);
      filter.addAction(Definitions.ACTION_USB_DETACHED);
      reactContext.registerReceiver(usbReceiver, filter);
      broadcastRegistered = true;
    }
  }

  private WritableMap serializeDevice(UsbDevice device) {
    WritableMap map = Arguments.createMap();
    map.putBoolean("isSupported", UsbSerialDevice.isSupported(device));
    map.putString("deviceName", device.getDeviceName());
    map.putInt("deviceId", device.getDeviceId());
    map.putInt("deviceClass", device.getDeviceClass());
    map.putInt("deviceSubclass", device.getDeviceSubclass());
    map.putInt("deviceProtocol", device.getDeviceProtocol());
    map.putInt("vendorId", device.getVendorId());
    map.putInt("productId", device.getProductId());

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      map.putInt("interfaceCount", device.getInterfaceCount());
      String manufacturerName = device.getManufacturerName();
      if (manufacturerName != null) {
        map.putString("manufacturerName", manufacturerName);
      }
      String productName = device.getProductName();
      if (productName != null) {
        map.putString("productName", productName);
      }
    }
    return map;
  }

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
    StringBuilder builder = new StringBuilder(length);
    for (int i = length - 1; i >= 0; i--) {
      builder.append((char) ((value >> (8 * i)) & 0xFF));
    }
    return builder.toString();
  }

  private UsbDevice chooseDevice(int deviceId) {
    List<UsbDevice> usbDevices = builder.getPossibleSerialPorts(reactContext);
    WritableArray deviceList = Arguments.createArray();

    if (usbDevices.size() != 0) {
      for (UsbDevice device: usbDevices) {
        if (deviceId == -1 || device.getDeviceId() == deviceId) {
          return device;
        }
      }
    }

    return null;
  }

  private boolean checkConnect() {
    if (autoConnect) {
      return builder.openSerialPorts(reactContext, BAUD_RATE, DATA_BIT, STOP_BIT, PARITY, FLOW_CONTROL);
    } else {
      return builder.getSerialPorts(reactContext);
    }
  }

  private void attachedDevices(UsbDevice device) {
    if (device != null) {
      int deviceId = device.getDeviceId();
      sendType(deviceId, Definitions.onDeviceAttached);
    }
    boolean ret = checkConnect();
    if (!ret) {
      sendError(Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT, Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT_MESSAGE);
    }
  }

  private void detachedDevices(UsbDevice device) {
    if (device != null) {
      int deviceId = device.getDeviceId();
      sendType(deviceId, Definitions.onDeviceAttached);
      boolean ret = builder.disconnectDevice(device);
      ReadThread stream = serialStreamMap.get(deviceId);
      stream.setKeep(false);
      if (!ret) {
        sendError(Definitions.ERROR_DISCONNECT_FAILED, Definitions.ERROR_DISCONNECT_FAILED_MESSAGE);
      }
    }
  }

  private class ReadThread extends Thread {
    private int deviceId;
    private AtomicBoolean keep = new AtomicBoolean(true);
    private UsbSerialDevice serialDevice;
    private SerialInputStream inputStream;
    private SerialOutputStream outputStream;

    ReadThread(int deviceId, UsbSerialDevice serialDevice) {
      this.deviceId = deviceId;
      this.serialDevice = serialDevice;
      this.inputStream = serialDevice.getInputStream();
      this.outputStream = serialDevice.getOutputStream();
    }

    @Override
    public void run() {
      while (keep.get()) {
        if (inputStream == null) {
          return;
        }

        int value = read();
        if (value != -1) {
          WritableMap params = Arguments.createMap();
          params.putString("type", Definitions.onReadData);
          params.putInt("id", deviceId);

          switch (returnedDataType) {
            //case Definitions.RETURNED_DATA_TYPE_INTARRAY: {
            //  WritableArray intArray = Arguments.createArray();
            //  for (byte b: bytes) {
            //    intArray.pushInt(unsignedByteToInt(b));
            //  }
            //  params.putArray("data", intArray);
            //}
            //break;
            //case Definitions.RETURNED_DATA_TYPE_HEXSTRING: {
            //  params.putString("data", bytesToHex(bytes));
            //}
            //break;
            case Definitions.RETURNED_DATA_TYPE_ASCII: {
              params.putString("data", toASCII(value));
            }
            break;
          }

          sendEvent(Definitions.serialPortEvent, params);
        }
      }
    }

    public void setKeep(boolean keep) {
      this.keep.set(keep);
    }

    public boolean isConnected() {
      return this.keep.get();
    }

    public int read() {
      return this.inputStream.read();
    }

    public void write(byte[] data) {
      this.outputStream.write(data);
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
          byte[] data = (byte[]) msg.obj;
          ReadThread stream = serialStreamMap.get(deviceId);
          if (stream != null) {
            stream.write(data);
          }
        }
      };
      Looper.loop();
    }
  }

  /******************************* END SERVICE **********************************/

  public void setParams(
    String driver,
    boolean autoConnect,
    int portInterface,
    int returnedDataType,
    int baudRate,
    int dataBit,
    int stopBit,
    int parity,
    int flowControl
  ) {
    this.driver = driver;
    this.autoConnect = autoConnect;
    this.portInterface = portInterface;
    this.returnedDataType = returnedDataType;
    this.BAUD_RATE = baudRate;
    this.DATA_BIT = dataBit;
    this.STOP_BIT = stopBit;
    this.PARITY = parity;
    this.FLOW_CONTROL = flowControl;
  }

  public void startListening() {
    registerReceiver();
    checkConnect();
  }

  public void stopListening() {
    disconnectAll();
    if (broadcastRegistered) {
      reactContext.unregisterReceiver(usbReceiver);
      if (builder != null) {
        builder.unregisterListeners(reactContext);
      }
      broadcastRegistered = false;
    }
  }

  public WritableArray listDevices() {
    List<UsbDevice> usbDevices = builder.getPossibleSerialPorts(reactContext);
    WritableArray deviceList = Arguments.createArray();

    if (usbDevices.size() != 0) {
      for (UsbDevice device: usbDevices) {
        deviceList.pushMap(serializeDevice(device));
      }
    }

    return deviceList;
  }

  public void connect(int deviceId) {

  }

  public void disconnectAll() {
  }

  public void disconnect(int deviceId) {
    UsbDevice device = chooseDevice(deviceId);

    if (device != null && isConnected(deviceId)) {
      boolean ret = builder.disconnectDevice(device);
      ReadThread stream = serialStreamMap.get(deviceId);
      stream.setKeep(false);
      if (!ret) {
        sendError(Definitions.ERROR_DISCONNECT_FAILED, Definitions.ERROR_DISCONNECT_FAILED_MESSAGE);
      }

//      sendType(deviceId, Definitions.onConnected);
    }
  }

  public boolean isConnected(int deviceId) {
    ReadThread stream = serialStreamMap.get(deviceId);
    if (stream != null) {
      return stream.isConnected();
    }
    return false;
  }

  public void write(int deviceId, byte[] bytes) {
    if (isConnected(deviceId)) {
      writeHandler.obtainMessage(0, deviceId, 0, bytes).sendToTarget();
    } else {
      sendError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE);
    }
  }
}
