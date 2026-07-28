# LotoT + AndrOBD integration spike

This branch keeps AndrOBD's mature ELM327/OBD connection and protocol engine and
adds a bundled LotoT React dashboard as the default Android home screen.

## Current vertical slice

- Separate Android package: `com.lotot.android`
- LotoT-branded React UI bundled under `androbd/src/main/assets/lotot/`
- JavaScript bridge limited to three explicit actions:
  - notify native code that React is ready;
  - start AndrOBD Demo mode;
  - open the existing AndrOBD options menu.
- Native telemetry snapshot every 250 ms using AndrOBD's existing UI timer.
- Mapped signals:
  - `vehicle_speed` ← AndrOBD `vehicle_speed`
  - `engine_rpm` ← AndrOBD `engine_speed`
  - `engine_load` ← AndrOBD `engine_load_calculated`
  - `module_voltage` ← AndrOBD `ecu_voltage`
  - `maf` ← AndrOBD `mass_airflow`
- The normal AndrOBD services, DTC screens, settings, plugins and connection
  transports remain in place.

## Build the React assets

```bash
cd lotot-ui
npm install
npm run build
```

The build writes a self-contained offline bundle to:

```text
androbd/src/main/assets/lotot/
```

CSS is inlined into `index.html` because Android WebView blocks a separate
stylesheet loaded from a `file://` origin. The JavaScript bundle is emitted as a
classic deferred script for the same local-WebView compatibility reason.

## Build the APK

```bash
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew :androbd:assembleDebug
```

Output:

```text
androbd/build/outputs/apk/debug/androbd-debug.apk
```

## Licensing

AndrOBD is GPL licensed. This combined Android application must be distributed
with corresponding source under GPL-compatible terms. The separately deployed
Django service is not included in this APK.

## Integrated Bluetooth connection UI (0.2.0 spike)

The LotoT WebView now owns the user-facing adapter workflow instead of opening
AndrOBD's legacy Bluetooth picker:

- Classic Bluetooth discovery and bonded-device listing
- BLE scanning with RSSI updates
- Classic/BLE filtering based on Android device type
- Direct connect/disconnect calls into `BtCommService` and `BleCommService`
- Selected and connected device identity published to React
- Visible connection errors inside the LotoT drawer
- Automatic live-data service selection after ECU detection
- Passive app startup with no automatic Bluetooth prompt

The native AndrOBD action bar is hidden on the LotoT dashboard. Remaining
advanced diagnostic screens use the LotoT dark Android theme. Automatic plugin
discovery is deferred until the plugin manager is explicitly opened, avoiding
Android 12+ background-service crashes.
