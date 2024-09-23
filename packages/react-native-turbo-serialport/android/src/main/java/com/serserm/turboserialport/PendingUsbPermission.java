package com.serserm.turboserialport;

import android.app.PendingIntent;

public class PendingUsbPermission {
  public PendingIntent pendingIntent;
  public int deviceId;

  public PendingUsbPermission(PendingIntent pendingIntent, int deviceId) {
    this.pendingIntent = pendingIntent;
    this.deviceId = deviceId;
  }
}
