package com.serserm.turboserialport;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.Promise;

abstract class TurboSerialportSpec extends ReactContextBaseJavaModule {
  TurboSerialportSpec(ReactApplicationContext context) {
    super(context);
  }

  public abstract void addListener(String eventName);

  public abstract void removeListeners(double count);
}
