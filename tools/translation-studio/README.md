# LoToTi Translation Studio

Private ASUS dashboard: http://100.73.225.127:8787 (Tailscale required).

Search the 477 UI strings, 206 native strings and 56 reviewed generic DTC definitions.
Edit Algerian Darija or French alongside the English source. Browser drafts survive
reloads. AI supplies three alternatives through the existing `lototi-auto` gateway;
provider credentials stay on the server. Suggestions apply only when selected.

**Save draft to source** validates and saves without building. **OK · Build & install**
saves, runs all three localization checks, rebuilds Vite and the aiPreview APK as
`lofa`, checks bundled assets and the signing certificate, updates the Note20,
launches it and compares installed/built APK SHA-256. Existing app data is retained.
Offline/unauthorized devices leave a downloadable APK and a retry-install button.
The original `com.lotot.android` package is not the installation target; the current
0.9.5 preview uses `com.lotot.android.ai.preview`.

Source edits go directly into the repository dictionaries/native XML/DTC overlay.
Before each save, originals and the change set are retained under
`~/.local/state/lotot-translation-studio/history/`. Failed validation restores originals.
Stale revisions and concurrent saves during builds are rejected. Build logs, metadata
and immutable APK copies live in the same state directory. Source edits are deliberately
not automatically committed; review with `git diff` and commit when desired.

## Service

Copy `lotot-translation-studio.service` to `~/.config/systemd/user/`, then run:

```sh
systemctl --user daemon-reload
systemctl --user enable --now lotot-translation-studio
```

Run as `lofa`, never root. Dependencies: Python 3 standard library, Node/npm, the
existing Android SDK/JDK/Gradle, and the existing lofa signing key. Defaults match
ASUS. Override `LOTOT_BIND`, `LOTOT_PORT`, `LOTOT_DEVICE`, `ANDROID_HOME`,
`LOTOT_JAVA_HOME`, `LOTOT_NODE`, `LOTOT_ADB`, `LOTOT_STUDIO_STATE` as needed.
For a host/key migration, verify the installed package certificate before changing
`EXPECTED_CERT`. Do not uninstall the app to bypass a signing mismatch.

The service listens only on the ASUS Tailscale IP; no public tunnel is configured.
Writes require JSON and an exact matching Origin. Any device with tailnet access to
this port can edit/build; use tailnet access controls accordingly.

```sh
python3 -m unittest discover -s tools/translation-studio -p 'test_*.py'
```
