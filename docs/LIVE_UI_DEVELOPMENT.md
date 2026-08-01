# Live React UI development

The debug APK can load the React cockpit from a private LAN or Tailscale Vite server. React, CSS, translations, and layout changes then update through Vite HMR without rebuilding the APK.

## Start live mode

```bash
cd /home/lofa/DEV/lotot-android-spike
./tools/lotot-ui-live.sh start 100.68.236.4:5555 100.120.107.43
```

The first address is the ADB device serial. The second is the development host address reachable from the phone. When Tailscale is installed, the host argument may be omitted.

The debug app stores the approved private URL and reuses it on later launches. If Vite cannot be reached during startup, the WebView falls back to the bundled interface.

## Return to bundled UI

```bash
./tools/lotot-ui-live.sh bundled 100.68.236.4:5555
```

This removes the saved development URL without clearing app data, MQTT credentials, vehicle settings, or the offline queue.

## Stop Vite

```bash
./tools/lotot-ui-live.sh stop
```

## What updates live

No APK rebuild is needed for changes under:

- `lotot-ui/src/main.jsx`
- `lotot-ui/src/styles.css`
- `lotot-ui/src/i18n.js`

Native Java, Android resources, manifest, permissions, foreground services, and bridge method changes still require Android Studio Apply Changes or a new APK build.

Live mode is debug-only. Release builds always load `file:///android_asset/lotot/index.html` and do not allow cleartext remote UI content.
