# LotoT + AndrOBD integration spike

This branch keeps AndrOBD's mature ELM327/OBD connection and protocol engine and
adds a bundled LotoT React dashboard as the default Android home screen.

## Current vertical slice

- Separate Android package: `com.lotot.android`
- LotoT React cockpit bundled under `androbd/src/main/assets/lotot/`
- Native JavaScript bridge for Bluetooth, telemetry, themes and integrated
  services
- AndrOBD remains the in-process ELM327/OBD transport, polling and decoding
  engine
- Low-latency speed/RPM updates plus a complete live signal explorer
- Classic Bluetooth and BLE discovery/connection entirely inside the LotoT UI
- Built-in GPS, accelerometer and MQTT services in the same APK
- No separate AndrOBD provider or MQTT plugin APK is required by LotoT
- Advanced DTC, vehicle-information, freeze-frame and native diagnostic tools
  remain available behind the LotoT advanced-tools entry

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

## Connection stability and light theme (0.4.0 spike)

LotoT no longer treats an Android Bluetooth socket flag as proof that the ELM
session is alive. Classic RFCOMM bytes and BLE notifications update a receive
heartbeat. After the initial adapter/ECU handshake grace period, 3.5 seconds of
complete receive silence terminates the stale transport and publishes a
separate `lost` state. Unexpected loss clears `connectedDevice`, preserves the
last adapter for one-tap reconnect, and displays a connection-lost warning.
Manual disconnect remains a clean `offline` state.

The React cockpit now has a persistent dark/light toggle. The bundled WebView,
status/navigation bars and native AndrOBD theme preference use the same saved
selection. Existing sessions are not recreated when the theme changes, so an
active OBD connection is not interrupted.

## Built-in services (0.5.0 spike)

The former AndrOBD GPS Provider, Sensor Provider and MQTT Publisher capabilities
are now direct LotoT components rather than externally discovered services. The
user installs one APK and configures everything from the React cockpit.

- `LotoTGpsProvider` publishes latitude, longitude, altitude, bearing and GPS
  speed into the same signal explorer as OBD data.
- `LotoTMotionSensorProvider` publishes `ACC_X`, `ACC_Y` and `ACC_Z` from the
  phone accelerometer.
- `LotoTMqttPublisher` supports TCP, TLS, WebSocket and secure WebSocket broker
  endpoints, authentication, topic prefixes, QoS, retained values, update
  intervals and per-signal selection. It emits one topic per signal plus a
  complete `snapshot` JSON topic.
- The integrated-services panel reports live state and exposes all controls in
  the dark/light LotoT UI. The legacy external-plugin manager is hidden.
- GPS and motion signals remain available while OBD is offline; when OBD is
  connected, MQTT publishes the combined OBD, GPS and phone-sensor snapshot.

The implementation is adapted from the GPLv3 AndrOBD-plugin repository,
upstream commit `fec003c` (`Plugins: SDK36 UI tuning`). It remains covered by
the GPL terms of this combined Android application.

## Django synchronization milestone

The existing Django backend already separates human and device authentication:

- `/api/v1/auth/login/` authenticates the driver and returns their account and
  vehicle context;
- `/api/v1/telemetry/ingest/` accepts background telemetry using a provisioned
  `device_uid` and `api_key`;
- `/ws/vehicles/<vehicle_id>/live/` broadcasts accepted readings to web clients.

The next cloud milestone should add account login, vehicle selection and secure
device provisioning. Django can ingest the built-in MQTT `snapshot` topic via a
broker consumer, preserving the existing mobile publisher, or the same native
signal snapshot can be placed into a Room-backed HTTP upload queue. In either
case, Android keeps the 10 Hz local cockpit independent from the slower cloud
transport and Django remains responsible for history, alerts, predictions and
websocket fan-out.

## Django MQTT device topics (0.6.0 spike)

The built-in publisher now uses a stable `device_uid` and emits snapshots only
under `LotoT/devices/<device_uid>/snapshot`. New configurations default to QoS
1 with retained messages disabled, preventing stale telemetry replay. Snapshot
payloads include `external_id`, `device_uid`, app version and publisher metadata
for idempotent Django ingestion and auditing.
