# Installation

>**Note**: Not supported for iOS

React native app that uses npm, run:

```sh
npm install @serserm/react-native-turbo-serialport
```

React native app that uses yarn, run:

```sh
yarn add @serserm/react-native-turbo-serialport
```

Expo app run:

```sh
npx expo install @serserm/react-native-turbo-serialport
```

## Usage

>**Note**: [IDs are not persistent across USB disconnects.](https://developer.android.com/reference/android/hardware/usb/UsbDevice#getDeviceId())

```javascript
import {
  DataBit,
  DriverType,
  FlowControl,
  Mode,
  Parity,
  ReturnedDataType,
  StopBit,
  initSerialport,
  useSerialport,
  Serialport,
  Device,
} from '@serserm/react-native-turbo-serialport';

// this method is called once
// but it is optional
initSerialport({
  autoConnect: false,    // boolean
  mode: Mode.ASYNC,
  params: {
    driver: DriverType.AUTO,
    portInterface: -1,   // default all ports (int number)
    returnedDataType: ReturnedDataType.UTF8,
    baudRate: 9600,      // BaudRate
    dataBit: DataBit.DATA_BITS_8,
    stopBit: StopBit.STOP_BITS_1,
    parity: Parity.PARITY_NONE,
    flowControl: FlowControl.FLOW_CONTROL_OFF,
  },
});

function App() {
  const serialport = useSerialport({
    // events callback
    onError: ({errorCode, errorMessage}) => {},
    onReadData: ({deviceId, portInterface, data}) => {},     // data is depends on the returnedDataType
    onConnected: ({deviceId, portInterface}) => {},          // number
    onDisconnected: ({deviceId, portInterface}) => {},       // number
    onDeviceAttached: ({deviceId}) => {},                    // number
    onDeviceDetached: ({deviceId}) => {},                    // number
  });

  const {
    setParams,                // (params: ParamsType, deviceId?: number) => void
    listDevices,              // () => Promise<Array>
    connect,                  // (deviceId?: number) => void
    disconnect,               // (deviceId?: number) => void
    isConnected,              // (deviceId?: number) => Promise<boolean>
    isServiceStarted,         // () => Promise<boolean>
    writeBytes,               // (message: Array<number>, deviceId?: number, portInterface?: number) => void
    writeString,              // (message: string, deviceId?: number, portInterface?: number) => void
    writeBase64,              // (message: string, deviceId?: number, portInterface?: number) => void
    writeHexString,           // (message: string, deviceId?: number, portInterface?: number) => void
  } = serialport;

  function onPressSearch() {
    serialport.listDevices().then(res => {
      res.forEach(device => {
        const {
          isSupported,
          deviceId,
          deviceName,
          deviceClass,
          deviceSubclass,
          deviceProtocol,
          vendorId,
          productId,
          manufacturerName,
          productName,
          serialNumber,
          interfaceCount,
          setParams,          // (params: ParamsType) => void
          connect,            // () => void
          disconnect,         // () => void
          isConnected,        // () => Promise<boolean>
          writeBytes,         // (message: Array<number>, portInterface?: number) => void
          writeString,        // (message: string, portInterface?: number) => void
          writeBase64,        // (message: string, portInterface?: number) => void
          writeHexString,     // (message: string, portInterface?: number) => void
        } = device;           // Device
        // TODO
      });
    });
  }

  // ............
}
```

### Default ParamsType
| KEY              | VALUE            |
|------------------|------------------|
| driver           | AUTO             |
| portInterface    | -1               |
| returnedDataType | UTF8             |
| baudRate         | 9600             |
| dataBit          | DATA_BITS_8      |
| stopBit          | STOP_BITS_1      |
| parity           | PARITY_NONE      |
| flowControl      | FLOW_CONTROL_OFF |

### Optional

Add the following android intent to android/app/src/main/AndroidManifest.xml so that permissions are remembered on android (VS not remembered by usbManager.requestPermission())
```xml
<activity>
  <intent-filter>
    <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
  </intent-filter>

  <meta-data android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
    android:resource="@xml/device_filter" />
</activity>
```

And create a filter file in android/app/src/main/res/xml/usb_device_filter.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
  <!-- 0x0403 / 0x6001: FTDI FT232R UART -->
  <usb-device vendor-id="1027" product-id="24577" />

  <!-- 0x0403 / 0x6015: FTDI FT231X -->
  <usb-device vendor-id="1027" product-id="24597" />

  <!-- 0x2341 / Arduino -->
  <usb-device vendor-id="9025" />

  <!-- 0x16C0 / 0x0483: Teensyduino  -->
  <usb-device vendor-id="5824" product-id="1155" />

  <!-- 0x10C4 / 0xEA60: CP210x UART Bridge -->
  <usb-device vendor-id="4292" product-id="60000" />

  <!-- 0x067B / 0x2303: Prolific PL2303 -->
  <usb-device vendor-id="1659" product-id="8963" />

  <!-- 0x1366 / 0x0105: Segger JLink -->
  <usb-device vendor-id="4966" product-id="261" />

  <!-- 0x1366 / 0x0105: CH340 JLink -->
  <usb-device vendor-id="1A86" product-id="7523" />

</resources>
```

The vendor-id and product-id here have to be given in decimal, and can be retrieved using listDevices()
