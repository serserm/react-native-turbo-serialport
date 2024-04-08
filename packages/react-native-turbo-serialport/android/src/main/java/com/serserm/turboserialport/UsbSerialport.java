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

  //Connection Settings
  private int DATA_BIT     = UsbSerialInterface.DATA_BITS_8;
  private int STOP_BIT     = UsbSerialInterface.STOP_BITS_1;
  private int PARITY       = UsbSerialInterface.PARITY_NONE;
  private int FLOW_CONTROL = UsbSerialInterface.FLOW_CONTROL_OFF;
  private int BAUD_RATE    = 9600;
  private boolean autoConnect = true;
  private int portInterface = -1;
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
    sendEvent("serialportEvent", err);
  }

  private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      switch (intent.getAction()) {
        case Definitions.ACTION_USB_ATTACHED: {
          UsbDevice device = intent.getExtras().getParcelable(UsbManager.EXTRA_DEVICE);
          int deviceId = device.getDeviceId();
          WritableMap params = Arguments.createMap();
          params.putString("type", Definitions.onDeviceAttached);
          params.putInt("data", deviceId);
          sendEvent("serialportEvent", params);
          if (autoConnect) {
            connect(deviceId);
          }
        }
        break;
        case Definitions.ACTION_USB_DETACHED: {
          UsbDevice device = intent.getExtras().getParcelable(UsbManager.EXTRA_DEVICE);
          int deviceId = device.getDeviceId();
          WritableMap params = Arguments.createMap();
          params.putString("type", Definitions.onDeviceDetached);
          params.putInt("data", deviceId);
          sendEvent("serialportEvent", params);
          disconnect();
        }
        break;
        case Definitions.ACTION_USB_PERMISSION: {
          UsbDevice device = intent.getExtras().getParcelable(UsbManager.EXTRA_DEVICE);
          boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
          startConnection(device, granted);
        }
        break;
      }
    }
  };

  private void fillDriverList() {
    driverList = new ArrayList<String>();
    driverList.add("ftdi");
    driverList.add("cp210x");
    driverList.add("pl2303");
    driverList.add("ch34x");
    driverList.add("cdc");
  }

  private void registerReceiver() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(Definitions.ACTION_USB_ATTACHED);
    filter.addAction(Definitions.ACTION_USB_DETACHED);
    filter.addAction(Definitions.ACTION_USB_PERMISSION);
    reactContext.registerReceiver(usbReceiver, filter);
  }

  private WritableMap serializeDevice(UsbDevice device) {
    WritableMap map = Arguments.createMap();

    try {
      map.putBoolean("isSupported", UsbSerialDevice.isSupported(device));
      map.putString("deviceName", device.getDeviceName());
      map.putInt("deviceId", device.getDeviceId());
      map.putInt("deviceClass", device.getDeviceClass());
      map.putInt("deviceSubclass", device.getDeviceSubclass());
      map.putInt("deviceProtocol", device.getDeviceProtocol());
      map.putInt("vendorId", device.getVendorId());
      map.putInt("productId", device.getProductId());

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        map.putString("manufacturerName", device.getManufacturerName());
        map.putString("productName", device.getProductName());
      }
    } catch  (SecurityException e) {
//      Log.e(TurboSerialportModule.NAME, e.toString());
    }

    return map;
  }

  /******************************* USB SERVICE **********************************/

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
    int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0;
    PendingIntent mPendingIntent = PendingIntent.getBroadcast(reactContext, 0 , new Intent(Definitions.ACTION_USB_PERMISSION), flags);
    usbManager.requestPermission(device, mPendingIntent);
  }

  private void startConnection(UsbDevice device, boolean granted) {
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
            params.putString("data", bytesToHex(bytes));
            sendEvent("serialportEvent", params);
          }
        }
      };
      serialPort.read(usbReadCallback);

      WritableMap params = Arguments.createMap();
      params.putString("type", Definitions.onConnected);
      params.putBoolean("data", true);
      sendEvent("serialportEvent", params);
    } else if (granted) {
      createError(Definitions.ERROR_DEVICE_NOT_SUPPORTED, Definitions.ERROR_DEVICE_NOT_SUPPORTED_MESSAGE);
    } else {
      createError(Definitions.ERROR_USER_DID_NOT_ALLOW_TO_CONNECT, Definitions.ERROR_USER_DID_NOT_ALLOW_TO_CONNECT_MESSAGE);
    }
  }

  /******************************* END SERVICE **********************************/

  public void startListening() {
    registerReceiver();

    WritableMap params = Arguments.createMap();
    params.putString("type", Definitions.onService);
    params.putBoolean("data", true);
    sendEvent("serialportEvent", params);

    if (autoConnect) {
      connect(-1);
    }
  }

  public void stopListening() {
    disconnect();
    reactContext.unregisterReceiver(usbReceiver);

    WritableMap params = Arguments.createMap();
    params.putString("type", Definitions.onService);
    params.putBoolean("data", false);
    sendEvent("serialportEvent", params);
  }

  public WritableArray listDevices() {
    HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
    WritableArray deviceList = Arguments.createArray();

    if (!usbDevices.isEmpty()) {
      for (UsbDevice device : usbDevices.values()) {
        deviceList.pushMap(serializeDevice(device));
      }
    }

    return deviceList;
  }

  public void connect(int deviceId) {
    UsbDevice device = chooseDevice(deviceId);

    if (device != null) {
      if (usbManager.hasPermission(device)) {
        startConnection(device, true);
      } else {
        requestUserPermission(device);
      }
    }
  }

  public void disconnect() {
    if (isConnected()) {
      serialPort.close();
      serialPort = null;

      WritableMap params = Arguments.createMap();
      params.putString("type", Definitions.onConnected);
      params.putBoolean("data", false);
      sendEvent("serialportEvent", params);
    }
  }

  public boolean isConnected() {
    return serialPort != null;
  }

  public boolean isSupported(int deviceId) {
    UsbDevice device = chooseDevice(deviceId);
    return UsbSerialDevice.isSupported(device);
  }

  public void write(byte[] bytes) {
    if (isConnected()) {
      serialPort.write(bytes);
    } else {
      createError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE);
    }
  }
}
