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

```javascript
import {
  intArrayToUtf16,
  hexToUtf16,
  useSerialport,
  Serialport,
  Device,
} from '@serserm/react-native-turbo-serialport';

// ....

const serialport = useSerialport({
  // ParamsType
  autoConnect: false,
  // ...
  // events callback
  onError: ({errorCode, errorMessage}) => {},
  onReadData: ({data}) => {},        // Array<number> | string
  onConnected: ({id}) => {},       // number
  onDeviceAttached: ({id}) => {},  // number
  onDeviceDetached: ({id}) => {},  // number
});

const {
  setParams,                // (params: ParamsType) => void
  listDevices,              // () => Promise<Array>
  connect,                  // (deviceId: number) => void
  disconnect,               // (deviceId: number) => void
  isConnected,              // (deviceId: number) => Promise<boolean>
  isServiceStarted,         // () => Promise<boolean>
  writeBytes,               // (message: Array<number>) => void
  writeString,              // (message: string) => void
  writeBase64,              // (message: string) => void
  writeHexString,           // (message: string) => void
} = serialport;

useEffect(() => {
  // example listDevices
  serialport.listDevices().then(res => {
    if (res.length) {
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
        connect,            // () => void
        disconnect,         // () => void
      } = res[0];           // Device

      connect();
    }
  });
}, []);
```

### Default ParamsType
| KEY              | VALUE            |
|------------------|------------------|
| driver           | AUTO             |
| autoConnect      | true             |
| portInterface    | -1               |
| returnedDataType | INTARRAY         |
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

The vendor-id and product-id here have to be given in decimal, and can be retrieved using deviceList()
