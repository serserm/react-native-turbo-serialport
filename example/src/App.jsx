import React, { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { useSerialport } from '@serserm/react-native-turbo-serialport';

export default function App() {
  const serialport = useSerialport({
    onChange: event => {
      console.log('list', event);
    },
  });
  const [length, setLength] = useState(-1);

  useEffect(() => {
    serialport.listDevices().then(res => {
      setLength(res?.length);
    });
  }, []);

  return (
    <View style={styles.container}>
      <Text>{`Result ${length}`}</Text>
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
