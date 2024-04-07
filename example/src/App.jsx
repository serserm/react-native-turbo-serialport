import React from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { useSerialport } from '@serserm/react-native-turbo-serialport';

export default function App() {
  useSerialport({
    onChange: event => {
      console.log('list', event);
    },
  });

  return (
    <View style={styles.container}>
      <Text>Result</Text>
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
