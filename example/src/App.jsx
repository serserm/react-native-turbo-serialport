import React, { useEffect, useState } from 'react';
import { Alert, StyleSheet, Text, View } from 'react-native';

import { useSerialport } from '@serserm/react-native-turbo-serialport';

export default function App() {
  const serialport = useSerialport({
    onError: event => {
      Alert.alert('Error', event.errorMessage);
    },
    onConnected: event => {
      Alert.alert('Connected', event.data);
    },
  });
  const [device, setDevice] = useState('');

  useEffect(() => {
    serialport.listDevices().then(res => {
      if (res?.length) {
        const {
          isSupported,
          deviceId,
          deviceName,
          manufacturerName,
          productName,
          serialNumber,
        } = res[0];
        setDevice(
          `${isSupported}\n${deviceId}\n${deviceName}\n${manufacturerName}\n${productName}\n${serialNumber}`,
        );
      }
    });
  }, []);

  return (
    <View style={styles.container}>
      <Text style={{ textAlign: 'center' }}>{`Result ${device}`}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
