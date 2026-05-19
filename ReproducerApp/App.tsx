import React, { useEffect, useState } from 'react';
import {
  Dimensions,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';

const fmt = (d: { width: number; height: number; scale: number }) =>
  `${d.width}×${d.height} @${d.scale}`;

function App(): React.JSX.Element {
  const win = useWindowDimensions();
  const [dimsLog, setDimsLog] = useState(() => ({
    window: Dimensions.get('window'),
    screen: Dimensions.get('screen'),
  }));

  useEffect(() => {
    const sub = Dimensions.addEventListener('change', next => {
      setDimsLog(next);
      console.log(
        '[repro] Dimensions.change',
        JSON.stringify({ window: next.window, screen: next.screen }),
      );
    });
    console.log(
      '[repro] Dimensions.initial',
      JSON.stringify({
        window: Dimensions.get('window'),
        screen: Dimensions.get('screen'),
      }),
    );
    return () => sub.remove();
  }, []);

  return (
    <View style={styles.root}>
      {/* Edge markers — these should hug all four sides of the activity window.
          On secondary displays they don't, because Fabric sizes the surface from
          the (primary-display) screenDisplayMetrics. */}
      <View style={[styles.edge, styles.top]} />
      <View style={[styles.edge, styles.bottom]} />
      <View style={[styles.edge, styles.left]} />
      <View style={[styles.edge, styles.right]} />

      <View style={styles.center}>
        <Text style={styles.title}>RN secondary-display repro</Text>
        <Text style={styles.row}>useWindowDimensions: {fmt(win)}</Text>
        <Text style={styles.row}>
          Dimensions.get('window'): {fmt(dimsLog.window)}
        </Text>
        <Text style={styles.row}>
          Dimensions.get('screen'): {fmt(dimsLog.screen)}
        </Text>
        <Text style={styles.hint}>
          If `screen.scale` does not match `window.scale`, the bug is
          reproducing. The red edge bars should hug all four sides of the
          activity window.
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#111' },
  edge: { position: 'absolute', backgroundColor: 'red' },
  top: { top: 0, left: 0, right: 0, height: 8 },
  bottom: { bottom: 0, left: 0, right: 0, height: 8 },
  left: { top: 0, bottom: 0, left: 0, width: 8 },
  right: { top: 0, bottom: 0, right: 0, width: 8 },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  title: { color: 'white', fontSize: 22, fontWeight: '600', marginBottom: 16 },
  row: {
    color: 'white',
    fontSize: 16,
    marginVertical: 4,
    fontVariant: ['tabular-nums'],
  },
  hint: { color: '#bbb', fontSize: 14, marginTop: 24, textAlign: 'center' },
});

export default App;
