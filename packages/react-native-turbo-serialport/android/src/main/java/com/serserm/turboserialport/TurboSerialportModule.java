package com.serserm.turboserialport;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;

import java.util.List;
import android.os.Build;
import android.util.Base64;
import android.hardware.usb.UsbDevice;
import com.felhr.usbserial.UsbSerialDevice;

public class TurboSerialportModule extends TurboSerialportSpec {
  public static final String NAME = "TurboSerialport";

  private final ReactApplicationContext reactContext;
  private final SerialPortBuilder builder;
//  private final UsbSerialport usbSerialport;
  private int listenerCount = 0;

  TurboSerialportModule(ReactApplicationContext context) {
    super(context);
    reactContext = context;
    builder = SerialPortBuilder.createSerialPortBuilder(serialPortCallback);
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  SerialPortCallback serialPortCallback = new SerialPortCallback() {

    @Override
    public void onSerialPortsDetected(UsbDeviceStatus usbDeviceStatus, boolean hasQueue) {
      if (usbDeviceStatus.serialDevices.size() > 0) {
        for (UsbSerialDevice serialDevice: usbDeviceStatus.serialDevices) {
          if (serialDevice.isOpen()) {
  //          sendType(deviceId, Definitions.onConnected);
          }
        }
      }
    }
  };

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

  /********************************** REACT **************************************/

  @ReactMethod
  public void init(
    boolean autoConnect,
    double mode,
    String driver,
    double portInterface,
    double returnedDataType,
    double baudRate,
    double dataBit,
    double stopBit,
    double parity,
    double flowControl
  ) {
    builder.init(
      autoConnect,
      (int) mode,
      driver,
      (int) portInterface,
      (int) returnedDataType,
      (int) baudRate,
      (int) dataBit,
      (int) stopBit,
      (int) parity,
      (int) flowControl
    );
  }

  @ReactMethod
  public void addListener(String eventName) {
    if (listenerCount == 0) {
      // Set up any upstream listeners or background tasks as necessary
      builder.getSerialPorts();
    }
    listenerCount += 1;
  }

  @ReactMethod
  public void removeListeners(double count) {
    listenerCount -= (int) count;
    if (listenerCount <= 0) {
      listenerCount = 0;
      // Remove upstream listeners, stop unnecessary background tasks
      disconnectAll();
      unregisterReceiver();
    }
  }

  @ReactMethod
  public void setParams(
    double deviceId,
    String driver,
    double portInterface,
    double returnedDataType,
    double baudRate,
    double dataBit,
    double stopBit,
    double parity,
    double flowControl
   ) {
    builder.setParams(
      (int) deviceId,
      driver,
      (int) portInterface,
      (int) returnedDataType,
      (int) baudRate,
      (int) dataBit,
      (int) stopBit,
      (int) parity,
      (int) flowControl
    );
  }

  @ReactMethod
  public void listDevices(Promise promise) {
    List<UsbDevice> usbDevices = builder.getPossibleSerialPorts();
    WritableArray deviceList = Arguments.createArray();
    if (usbDevices.size() != 0) {
      for (UsbDevice device: usbDevices) {
        deviceList.pushMap(serializeDevice(device));
      }
    }
    promise.resolve(deviceList);
  }

  @ReactMethod
  public void connect(double deviceId) {
    if (listenerCount > 0) {
      builder.connect((int) deviceId);
    }
  }

  @ReactMethod
  public void disconnect(double deviceId) {
    if (deviceId > 0) {
      builder.disconnect((int) deviceId);
    } else {
      builder.disconnectAll();
    }
  }

  @ReactMethod
  public void isConnected(double deviceId, Promise promise) {
    promise.resolve(builder.isConnected((int) deviceId));
  }

  @ReactMethod
  public void isServiceStarted(Promise promise) {
    promise.resolve(listenerCount > 0);
  }

  @ReactMethod
  public void writeBytes(double deviceId, ReadableArray message) {
    int length = message.size();
    if (length < 1) {
      return;
    }
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      bytes[i] = (byte) message.getInt(i);
    }
    if (listenerCount > 0) {
      builder.write((int) deviceId, bytes);
    }
  }

  @ReactMethod
  public void writeString(double deviceId, String message) {
    if (message.length() < 1) {
      return;
    }
    byte[] bytes = message.getBytes();
    if (listenerCount > 0) {
      builder.write((int) deviceId, bytes);
    }
  }

  @ReactMethod
  public void writeBase64(double deviceId, String message) {
    if (message.length() < 1) {
      return;
    }
    byte[] bytes = Base64.decode(message, Base64.DEFAULT);
    if (listenerCount > 0) {
      builder.write((int) deviceId, bytes);
    }
  }

  @ReactMethod
  public void writeHexString(double deviceId, String message) {
    if (message.length() < 1) {
      return;
    }
    String msg = message.toUpperCase();
    byte[] bytes = new byte[msg.length() / 2];
    for (int i = 0; i < bytes.length; i++) {
      int index = i * 2;
      String hex = msg.substring(index, index + 2);
      if (Definitions.hexChars.indexOf(hex.substring(0, 1)) == -1
          || Definitions.hexChars.indexOf(hex.substring(1, 1)) == -1) {
        return;
      }
      bytes[i] = (byte) Integer.parseInt(hex, 16);
    }
    if (listenerCount > 0) {
      builder.write((int) deviceId, bytes);
    }
  }
}
