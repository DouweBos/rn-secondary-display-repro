# RN secondary-display `Dimensions.get('screen')` bug

Minimal reproducer for an Android bug in React Native where
`Dimensions.get('screen')` (and `screenDisplayMetrics` in the native layer)
report the **primary** display's `scale`/density even when the activity is
running on a secondary display (Samsung DeX, external monitor, virtual
display, or `am start --display N`).

The activity's own context, `Dimensions.get('window')`, and
`useWindowDimensions()` are all reported correctly — only `screen` is wrong.

## What the app shows

`App.tsx` logs `Dimensions.get('window')` / `Dimensions.get('screen')` to
`adb logcat` and renders the same values on screen. Red border bars hug the
edges of the activity window so it's obvious when the surface is mis-sized.

## How to reproduce

1. Install Node + Android SDK and boot a phone-form-factor AVD (verified on
   Pixel 9 Pro, API 36):

   ```sh
   ~/Library/Android/sdk/emulator/emulator -avd Pixel_9_Pro
   ```

2. Attach a virtual secondary display at a density different from the
   primary's. On Pixel 9 Pro (3.0× primary), `240dpi` (1.5×) is enough to
   expose the bug:

   ```sh
   adb emu multidisplay add 1 2400 1080 240 0
   ```

   A second emulator window appears for the secondary display.

3. Install + start Metro + launch the app onto the secondary display:

   ```sh
   cd ReproducerApp
   npm install
   npm run android      # builds + installs the debug APK
   npm start            # in a second terminal, runs Metro

   adb shell am force-stop com.reproducerapp
   adb shell am start -n com.reproducerapp/.MainActivity --display 3
   ```

   (`--display 3` matches the id assigned by `multidisplay add` — confirm
   with `adb shell dumpsys display`.)

4. Capture the secondary display via `screencap -d <SF id>`:

   ```sh
   SF_ID=$(adb shell dumpsys SurfaceFlinger --display-id \
            | awk '/Virtual display/ {print $2; exit}')
   adb shell screencap -d "$SF_ID" -p /sdcard/d.png
   adb pull /sdcard/d.png ./repro-secondary.png
   ```

   (`adb exec-out screencap` and `conductor take-screenshot` only see the
   primary display.)

## Expected vs actual

**Expected:** `Dimensions.get('screen').scale === Dimensions.get('window').scale`
when the activity is on a single display. Screen and window report the
same display.

**Actual** (Pixel 9 Pro emulator, secondary at 2400×1080 @ 240dpi):

```
Dimensions.get('window'): { width: 1600, height: 720, scale: 1.5 }   ← correct
Dimensions.get('screen'): { width: 800,  height: 360, scale: 3   }   ← wrong
```

`screen` is reporting the primary display's `scale=3` applied to the
secondary's pixel dimensions, producing impossible math (800 dp × scale=3
would be 2400 px, but at scale=3 the activity is on a 3× density display,
which it isn't).

## Root cause

`ReactAndroid/.../uimanager/DisplayMetricsHolder.kt::initDisplayMetrics`:

```kotlin
val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
wm.defaultDisplay.getRealMetrics(screenDisplayMetrics)
```

`wm.defaultDisplay` returns the _device's_ default (primary) display
regardless of which display the activity is on. Combined with the call
sites — `ReactRootView.java` passes `getContext().getApplicationContext()`,
and `ReactInstance.kt:251` / `ReactHostImpl.kt:727` pass a
`BridgelessReactContext` that wraps the application context — every init
path lands on the primary display's metrics.

## Suggested fix

1. In `DisplayMetricsHolder.initDisplayMetrics`, prefer the activity's
   `Display` when the context is an `Activity` (API 30+:
   `(context as? Activity)?.display`).
2. In `ReactRootView`, drop `.getApplicationContext()` on the three call
   sites and re-init in `onAttachedToWindow` so display moves after surface
   attach also propagate.
3. Defer Fabric's screen-metrics init until the first `ReactSurfaceView`
   attaches, or expose a host-side API for apps to refresh metrics once an
   activity is alive on a known display.

## Real-world impact

This is the root cause of broken Samsung DeX / external-monitor rendering
in the Plex React Native client (and probably others). With the wrong
`screen.scale`, Fabric's per-pixel computations are off, font rendering is
sub-pixel-blurry, and any app code that uses `screen` metrics (commonly via
`PixelRatio.get()`, which reads `screen.scale`) sizes content for the wrong
density.
