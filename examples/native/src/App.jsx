import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';

import {
  DataBit,
  DriverType,
  FlowControl,
  initSerialport,
  Mode,
  Parity,
  ReturnedDataType,
  StopBit,
  useSerialport,
} from '@serserm/react-native-turbo-serialport';

// this method is called once
// but it is optional
initSerialport({
  autoConnect: false, // boolean (default false)
  mode: Mode.ASYNC,
  params: {
    driver: DriverType.AUTO,
    portInterface: -1, // all ports (int number)
    returnedDataType: ReturnedDataType.UTF8,
    baudRate: 9600, // (int number)
    dataBit: DataBit.DATA_BITS_8,
    stopBit: StopBit.STOP_BITS_1,
    parity: Parity.PARITY_NONE,
    flowControl: FlowControl.FLOW_CONTROL_OFF,
  },
});

export function App() {
  const [allDevices, setAllDevices] = useState([]);
  const [device, setDevice] = useState('');
  const [data, setData] = useState('');
  const serialport = useSerialport({
    onError: ({ errorMessage }) => {
      Alert.alert('Error', `${errorMessage}`);
    },
    onConnected: ({ deviceId, portInterface }) => {
      setDevice(`id: ${deviceId} ${portInterface} +`);
    },
    onDisconnected: ({ deviceId, portInterface }) => {
      setDevice(`id: ${deviceId} ${portInterface} -`);
    },
    onDeviceAttached: onSearch,
    onDeviceDetached: onSearch,
    onReadData: ({ data }) => {
      setData(prev => `${prev}${data}`);
    },
  });

  useEffect(() => {
    serialport.listDevices().then(res => {
      if (res?.length) {
        const { isSupported, deviceId, deviceName, manufacturerName } = res[0];
        setDevice(
          `${isSupported}\n${deviceId}\n${deviceName}\n${manufacturerName}`,
        );
      }
    });
  }, []);

  function onSearch() {
    serialport.listDevices().then(res => {
      const devices = res.map(({ deviceId, deviceName }) => ({
        deviceId,
        deviceName,
      }));
      setAllDevices(devices);
    });
  }

  function onConnect(deviceId) {
    return () => {
      serialport.connect(deviceId);
    };
  }

  function write(deviceId) {
    return () => {
      serialport.writeString(`${Math.random()}`, deviceId, 0);
    };
  }

  function renderButton({ deviceId }, index) {
    return (
      <View key={`${index}-${deviceId}`} style={styles.row}>
        <TouchableOpacity
          style={styles.button_box}
          onPress={onConnect(deviceId)}>
          <View style={styles.button}>
            <Text style={styles.text}>{`Connect\nid: ${deviceId}`}</Text>
          </View>
        </TouchableOpacity>
        <TouchableOpacity style={styles.button_box} onPress={write(deviceId)}>
          <View style={styles.button}>
            <Text style={styles.text}>{`Write\nid: ${deviceId}`}</Text>
          </View>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Button title={`Search: ${allDevices.length}`} onPress={onSearch} />
      <Text style={{ textAlign: 'center' }}>{`Result`}</Text>
      {allDevices.map(renderButton)}
      {!!device && (
        <Text style={{ textAlign: 'center' }}>{`Connected\n${device}`}</Text>
      )}
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={{ textAlign: 'center' }}>{`${data}`}</Text>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: 50,
  },
  scroll: { flexGrow: 1 },
  row: {
    marginTop: 10,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  button_box: {
    flex: 1,
  },
  button: {
    flexDirection: 'row',
    minHeight: 50,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    backgroundColor: '#c7c7c7',
  },
  text: {
    flex: 1,
    textAlign: 'center',
  },
});
