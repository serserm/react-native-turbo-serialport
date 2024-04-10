package com.serserm.turboserialport;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import android.app.PendingIntent;

import java.util.HashMap;
import java.util.ArrayList;
import java.lang.SecurityException;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

public class UsbSerialport {

  private ReactApplicationContext reactContext;
  private UsbManager usbManager;
  private UsbSerialDevice serialPort;
  private UsbDeviceConnection connection;
  private UsbDevice usbDevice;

  //Connection Settings
  private int DATA_BIT     = UsbSerialInterface.DATA_BITS_8;
  private int STOP_BIT     = UsbSerialInterface.STOP_BITS_1;
  private int PARITY       = UsbSerialInterface.PARITY_NONE;
  private int FLOW_CONTROL = UsbSerialInterface.FLOW_CONTROL_OFF;
  private int BAUD_RATE    = 9600;
  private boolean autoConnect = true;
  private int portInterface = -1;
  private int returnedDataType = Definitions.RETURNED_DATA_TYPE_INTARRAY;
  private String driver = "AUTO";
  private ArrayList<String> driverList;

  UsbSerialport(ReactApplicationContext context) {
    reactContext = context;
    fillDriverList();
    usbManager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);
  }

  private void sendEvent(String eventName, @Nullable WritableMap params) {
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }

  private void createError(int code, String message) {
    WritableMap err = Arguments.createMap();
    err.putString("type", Definitions.onError);
    err.putInt("errorCode", code);
    err.putString("errorMessage", message);
    sendEvent(Definitions.serialportEvent, err);
  }

  private void fillDriverList() {
    driverList = new ArrayList<String>();
    driverList.add("ftdi");
    driverList.add("cp210x");
    driverList.add("pl2303");
    driverList.add("ch34x");
    driverList.add("cdc");
  }

  /******************************* USB SERVICE **********************************/

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
          if (!isConnected()) {
            UsbDevice device = getUsbDeviceFromIntent(intent);
            WritableMap params = Arguments.createMap();
            params.putString("type", Definitions.onDeviceAttached);
            if (device != null) {
              params.putMap("data", serializeDevice(device));
            }
            sendEvent(Definitions.serialportEvent, params);
            if (autoConnect) {
              if (device != null) {
                startConnection(device);
              } else {
                connect(-1);
              }
            }
          }
        }
        break;
        case Definitions.ACTION_USB_DETACHED: {
          UsbDevice device = getUsbDeviceFromIntent(intent);
          WritableMap params = Arguments.createMap();
          params.putString("type", Definitions.onDeviceDetached);
          if (device != null) {
            params.putMap("data", serializeDevice(device));
          }
          sendEvent(Definitions.serialportEvent, params);
          disconnect();
        }
        break;
        case Definitions.ACTION_USB_PERMISSION: {
          if (usbDevice != null) {
            if (usbManager.hasPermission(usbDevice)) {
              startConnection(usbDevice);
            } else {
              createError(Definitions.ERROR_USER_DID_NOT_ALLOW_TO_CONNECT, Definitions.ERROR_USER_DID_NOT_ALLOW_TO_CONNECT_MESSAGE);
            }
            usbDevice = null;
          }
        }
        break;
      }
    }
  };

  private void registerReceiver() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(Definitions.ACTION_USB_ATTACHED);
    filter.addAction(Definitions.ACTION_USB_DETACHED);
    filter.addAction(Definitions.ACTION_USB_PERMISSION);
    reactContext.registerReceiver(usbReceiver, filter);
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
    for ( int j = 0; j < bytes.length; j++ ) {
      int v = bytes[j] & 0xFF;
      chars[j * 2] = Definitions.hexArray[v >>> 4];
      chars[j * 2 + 1] = Definitions.hexArray[v & 0x0F];
    }
    return new String(chars);
  }

  private UsbDevice chooseDevice(int deviceId) {
    HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();

    if (!usbDevices.isEmpty()) {
      for (UsbDevice device : usbDevices.values()) {
        if (deviceId == -1 || device.getDeviceId() == deviceId) {
          return device;
        }
      }
    }

    return null;
  }

  private void requestUserPermission(UsbDevice device) {
    usbDevice = device;
    int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0;
    PendingIntent mPendingIntent = PendingIntent.getBroadcast(reactContext, 0 , new Intent(Definitions.ACTION_USB_PERMISSION), flags);
    usbManager.requestPermission(device, mPendingIntent);
  }

  private void startConnection(UsbDevice device) {
    boolean granted = usbManager.hasPermission(device);
    if (granted && UsbSerialDevice.isSupported(device)) {
      connection = usbManager.openDevice(device);

      if (connection == null) {
        createError(Definitions.ERROR_CONNECTION_FAILED, Definitions.ERROR_CONNECTION_FAILED_MESSAGE);
        return;
      }
      if (driver.equals("AUTO")) {
        serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection, portInterface);
      } else {
        serialPort = UsbSerialDevice.createUsbSerialDevice(driver, device, connection, portInterface);
      }
      if (!isConnected() || !serialPort.open()) {
        createError(Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT, Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT_MESSAGE);
        return;
      }

      serialPort.setBaudRate(BAUD_RATE);
      serialPort.setDataBits(DATA_BIT);
      serialPort.setStopBits(STOP_BIT);
      serialPort.setParity(PARITY);
      serialPort.setFlowControl(FLOW_CONTROL);

      UsbSerialInterface.UsbReadCallback usbReadCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] bytes) {
          if (bytes.length != 0) {
            WritableMap params = Arguments.createMap();
            params.putString("type", Definitions.onReadData);
            if (returnedDataType == Definitions.RETURNED_DATA_TYPE_INTARRAY) {
              WritableArray intArray = Arguments.createArray();
              for (byte b: bytes) {
                intArray.pushInt(unsignedByteToInt(b));
              }
              params.putArray("data", intArray);
            } else if (returnedDataType == Definitions.RETURNED_DATA_TYPE_HEXSTRING) {
              params.putString("data", bytesToHex(bytes));
            }


            sendEvent(Definitions.serialportEvent, params);
          }
        }
      };
      serialPort.read(usbReadCallback);

      WritableMap params = Arguments.createMap();
      params.putString("type", Definitions.onConnected);
      params.putBoolean("data", true);
      sendEvent(Definitions.serialportEvent, params);
    } else if (granted) {
      createError(Definitions.ERROR_DEVICE_NOT_SUPPORTED, Definitions.ERROR_DEVICE_NOT_SUPPORTED_MESSAGE);
    } else {
      requestUserPermission(device);
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
    if (autoConnect) {
      connect(-1);
    }
  }

  public void stopListening() {
    disconnect();
    reactContext.unregisterReceiver(usbReceiver);
  }

  public WritableArray listDevices() {
    HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
    WritableArray deviceList = Arguments.createArray();

    if (!usbDevices.isEmpty()) {
      for (UsbDevice device: usbDevices.values()) {
        deviceList.pushMap(serializeDevice(device));
      }
    }

    return deviceList;
  }

  public void connect(int deviceId) {
    UsbDevice device = chooseDevice(deviceId);

    if (device != null) {
      startConnection(device);
    }
  }

  public void disconnect() {
    if (isConnected()) {
      serialPort.close();
      serialPort = null;

      WritableMap params = Arguments.createMap();
      params.putString("type", Definitions.onConnected);
      params.putBoolean("data", false);
      sendEvent(Definitions.serialportEvent, params);
    }
  }

  public boolean isConnected() {
    return serialPort != null;
  }

  public void write(byte[] bytes) {
    if (isConnected()) {
      serialPort.write(bytes);
    } else {
      createError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE);
    }
  }
}
