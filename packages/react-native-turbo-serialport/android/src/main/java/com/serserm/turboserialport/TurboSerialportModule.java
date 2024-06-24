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

import java.nio.charset.StandardCharsets;
import java.util.List;
import android.os.Build;
import android.util.Base64;
import android.hardware.usb.UsbDevice;
import com.felhr.usbserial.UsbSerialDevice;

public class TurboSerialportModule extends TurboSerialportSpec {
  public static final String NAME = "TurboSerialport";

  private final ReactApplicationContext reactContext;
  private final SerialPortBuilder builder;
  private int listenerCount = 0;

  TurboSerialportModule(ReactApplicationContext context) {
    super(context);
    reactContext = context;
    builder = SerialPortBuilder.createSerialPortBuilder(context, serialPortCallback);
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  SerialPortCallback serialPortCallback = new SerialPortCallback() {

    @Override
    public void onError(int code, String message) {
      sendError(code, message);
    }

    @Override
    public void onDeviceAttached(int deviceId) {
      sendType(deviceId, Definitions.onDeviceAttached);
    }

    @Override
    public void onDeviceDetached(int deviceId) {
      sendType(deviceId, Definitions.onDeviceDetached);
    }

    @Override
    public void onConnected(int deviceId, int portInterface) {
      sendType(deviceId, portInterface, Definitions.onConnected);
    }

    @Override
    public void onDisconnected(int deviceId, int portInterface) {
      sendType(deviceId, portInterface, Definitions.onDisconnected);
    }

    @Override
    public void onReadData(int deviceId, int portInterface, int returnedDataType, byte[] bytes) {
      WritableMap params = Arguments.createMap();
      params.putString("type", Definitions.onReadData);
      params.putInt("deviceId", deviceId);
      params.putInt("portInterface", portInterface);
      switch (returnedDataType) {
        case Definitions.RETURNED_DATA_TYPE_INTARRAY: {
          WritableArray intArray = Arguments.createArray();
          for (byte b: bytes) {
            intArray.pushInt(unsignedByteToInt(b));
          }
          params.putArray("data", intArray);
        }
        break;
        case Definitions.RETURNED_DATA_TYPE_HEXSTRING: {
          params.putString("data", bytesToHex(bytes));
        }
        break;
        case Definitions.RETURNED_DATA_TYPE_UTF8: {
          String data = new String(bytes, StandardCharsets.UTF_8);
          params.putString("data", data);
        }
        break;
      }
      sendEvent(Definitions.serialPortEvent, params);
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
    params.putInt("deviceId", deviceId);
    sendEvent(Definitions.serialPortEvent, params);
  }

  private void sendType(int deviceId, int portInterface, String type) {
    WritableMap params = Arguments.createMap();
    params.putString("type", type);
    params.putInt("deviceId", deviceId);
    params.putInt("portInterface", portInterface);
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

  /********************************** CONVERTING **************************************/

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

  private byte[] HexToBytes(String message) {
    String msg = message.toUpperCase();
    byte[] bytes = new byte[msg.length() / 2];
    for (int i = 0; i < bytes.length; i++) {
      int index = i * 2;
      String hex = msg.substring(index, index + 2);
      if (Definitions.hexChars.indexOf(hex.substring(0, 1)) == -1
          || Definitions.hexChars.indexOf(hex.substring(1, 1)) == -1) {
        return bytes;
      }
      bytes[i] = (byte) Integer.parseInt(hex, 16);
    }
    return bytes;
  }

  private byte[] Base64ToBytes(String message) {
    return (byte[]) Base64.decode(message, Base64.DEFAULT);
  }

  private byte[] StringToBytes(String message) {
    return (byte[]) message.getBytes(StandardCharsets.UTF_8);
  }

  private byte[] ArrayToBytes(ReadableArray message) {
    int length = message.size();
    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      bytes[i] = (byte) message.getInt(i);
    }
    return bytes;
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
      builder.disconnectAll();
      builder.unregisterReceiver();
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
  public void writeBytes(double deviceId, double portInterface, ReadableArray message) {
    if (message.size() < 1) {
      return;
    }
    if (listenerCount > 0) {
      builder.write((int) deviceId, (int) portInterface, ArrayToBytes(message));
    }
  }

  @ReactMethod
  public void writeString(double deviceId, double portInterface, String message) {
    if (message.length() < 1) {
      return;
    }
    if (listenerCount > 0) {
      builder.write((int) deviceId, (int) portInterface, StringToBytes(message));
    }
  }

  @ReactMethod
  public void writeBase64(double deviceId, double portInterface, String message) {
    if (message.length() < 1) {
      return;
    }
    if (listenerCount > 0) {
      builder.write((int) deviceId, (int) portInterface, Base64ToBytes(message));
    }
  }

  @ReactMethod
  public void writeHexString(double deviceId, double portInterface, String message) {
    if (message.length() < 1) {
      return;
    }
    if (listenerCount > 0) {
      builder.write((int) deviceId, (int) portInterface, HexToBytes(message));
    }
  }
}
