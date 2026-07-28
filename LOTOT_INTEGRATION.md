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

## Complete live PID dashboard (0.3.0 spike)

The Android bridge now publishes every successfully decoded `EcuDataItem`, not
only the original five headline measurements. Each signal includes its PID,
mnemonic, label, value, unit, min/max range and last update timestamp.

The React dashboard provides:

- automatic cards for every PID that starts producing valid data;
- category filters for engine, driving, temperatures, fuel, air, pressures,
  electrical and emissions data;
- sensor/PID/unit search;
- persistent favorites;
- 30-sample sparklines, trend indicators and range progress bars;
- live packet age, signal count and adapter identity;
- quick cards for coolant temperature, fuel rate, intake pressure and fuel
  level when those signals are present.

Freshness is stored as a lightweight field on `EcuDataItem`; the process
variable map remains unchanged so the upstream AndrOBD library contract and
unit tests are preserved.

## Low-latency driving telemetry (0.3.1 spike)

The driving cockpit uses a split telemetry cadence. Vehicle speed and engine RPM
are sent through a small native-to-React payload every 100 ms, while the complete
PID explorer remains on a throttled full snapshot. This avoids repeatedly
rerendering the complete dashboard just to move the speedometer.

For Mode 01 live data, the OBD scheduler gives PID `0x0D` (vehicle speed) a
100 ms priority deadline and PID `0x0C` (engine RPM) a 200 ms deadline. These
requests pre-empt very large normal PID rotations, including simulators that
advertise nearly every PID as supported. The original AndrOBD ordering remains
unchanged outside the LotoT realtime session.
