package com.serserm.turboserialport;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class TurboSerialportModule extends TurboSerialportSpec {
  public static final String NAME = "TurboSerialport";

  private ReactApplicationContext reactContext;
  private int listenerCount = 0;

  TurboSerialportModule(ReactApplicationContext context) {
    super(context);
    reactContext = context;
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  private void sendEvent(String eventName,
                         @Nullable WritableMap params) {
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }

  @ReactMethod
  public void send() {
    WritableMap params = Arguments.createMap();
    params.putString("data", "someValue");
    sendEvent("serialportChange", params);
  }

  @ReactMethod
  public void addListener(String eventName) {
    if (listenerCount == 0) {
      // Set up any upstream listeners or background tasks as necessary
    }

    listenerCount += 1;
  }

  @ReactMethod
  public void removeListeners(double count) {
    listenerCount -= (int) count;
    if (listenerCount == 0) {
      // Remove upstream listeners, stop unnecessary background tasks
    }
  }

  @ReactMethod
  public void state(Promise promise) {
    WritableMap params = Arguments.createMap();
    params.putString("name", "serialport");
    promise.resolve(params);
  }
}
