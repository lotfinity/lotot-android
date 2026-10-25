# Gateway reliability and verification

The gateway owns the physical adapter transport and samples decoded measurements without a running activity or WebView. Closing/recreating the screen leaves an active vehicle session running; explicit disconnect ends it. A killed process does not claim to restore an unknown vehicle connection. Returning to the app allows the user to reconnect.

Native list selection preserves the registry's numeric PID keys during recreation. Legacy external-plugin hosting is activity-scoped and discovery is explicit; the upstream submodule remains unchanged. Screen destruction detaches native measurement listeners, so old activities cannot receive new session updates. A rejected foreground-service start reports a paused/error state and does not start an orphan transport.

Only an actual qualifying adapter session uses `connectedDevice`. GPS uses `location` when authorized. Standalone MQTT uses `dataSync` and respects Android's timeout: it preserves the queue, stops foreground execution, and exposes a paused state. Reopening the app and saving service settings can resume work under Android's rules. The code does not promise permanent background execution or process-death survival.

Capture and delivery run independently. Synchronous Paho operations have a five-second completion deadline, connection attempts have a six-second timeout, and replay yields between bounded batches. Successful rows are removed only after configured MQTT operations finish; QoS 0 retains its normal best-effort semantics. Broker acknowledgement is not proof of backend ingestion. Consumers must deduplicate `external_id` because replay after process death can redeliver a snapshot.

## Payload compatibility and freshness

The existing topic and numeric/string `readings` map remain available. `captured_at` is now the latest included source observation time, rather than the enqueue time. `metadata.queued_at` represents enqueue time. `metadata.observations` maps each reading to `observed_at`, `unit`, `source`, and `simulated`; GPS can include `accuracy_m`. Consumers should use the individual observation time for per-signal history, alerts, and freshness.

Unchanged source observations are not repeatedly queued. OBD observations expire after 60 seconds, GPS after 30 seconds, and motion after five seconds. A stopped source cannot receive a new observation time just because a publishing timer fired. GPS uses the actual fix time/age, and optional fields are removed when absent from the next fix. The UI also expires unavailable readings.

## Queue isolation and retention

SQLite schema v2 partitions rows by broker URI, authenticated username, MQTT client ID and provisioned device UID. Password rotation for the same identity keeps its queue. Changing destination holds older partitions; returning to that same destination can drain them. Credentials are never stored in queue rows.

The v1 migration preserves old rows but quarantines them with an unknown destination. It cannot safely infer where historical data should be sent. Held rows are reported, never silently redirected. Capacity is 10,000 rows across all partitions. Insert, trim, and the persistent dropped counter are transactional; the oldest inserted rows are evicted first even when wall time moves backwards. At five-second capture cadence capacity is approximately 13.9 hours; at one second it is 2.8 hours, assuming continually updated observations. UI reports held rows and historical overflow losses.

Queue databases, generated installation identity and Keystore-encrypted passwords are excluded from cloud backup/device transfer. The Android Keystore key cannot be restored through ordinary app backup.

## Build and tests

Requirements: JDK 17, Node 22.14+, Android SDK including platform 37, and network access for pinned dependencies. The existing reachable plugin revision `4ff67cd71c4c6ee30eee6743c20746abbe5e789f` is retained.

```sh
git submodule update --init --recursive
node lotot-ui/scripts/check-i18n.mjs
./gradlew --no-daemon :library:test :androbd:testVerificationUnitTest :androbd:lintDebug \
  :androbd:assembleDebug :androbd:assembleVerification :androbd:assembleVerificationAndroidTest
```

AGP 9 only enables unit tests for the selected test build type by default, so use `testVerificationUnitTest`. Gradle's `preBuild` compiles current React sources using the lockfile. Both GitHub workflows build the UI, run checks, and retain APKs/reports.

The `verification` build uses application ID `com.lotot.android.verification`. Instrumentation runs against its own storage and loopback test servers. It must never run against the installed driver application's storage. Install the verification APK and its test APK, then run:

```sh
adb shell am instrument -w -r \
  -e class com.fr3ts0n.ecu.gui.androbd.GatewayReliabilityInstrumentedTest,com.fr3ts0n.ecu.gui.androbd.GatewaySessionInstrumentedTest \
  com.lotot.android.verification.test/androidx.test.runner.AndroidJUnitRunner
```

Tests cover migration, partition persistence, FIFO across clock rollback, overflow accounting, withheld PUBACK, acknowledged replay, source timestamps, expiry, privacy selection, activity recreation/finish, background sampling and the timeout callback. The lifecycle test's adapter is a local synthetic TCP server; it is not a real vehicle test. A direct timeout-callback test is not proof of Android 15's system time-budget enforcement.

The ordinary APK still uses `com.lotot.android`. Existing installs require the original compatible signing key for an update. Never uninstall the driver app to work around a signing mismatch.

The vehicle reliability layer is independent of AI routing. Current LoToTi AI builds use one gateway and a restricted external gateway token; upstream provider credentials stay on the server. See LOTOTI_AI.md for availability checks and build configuration. Blank CI builds do not acquire gateway credentials automatically.
