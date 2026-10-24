package com.fr3ts0n.ecu.gui.androbd;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Pure payload policy. Keep the existing readings map, add observation provenance. */
final class LotoTTelemetrySnapshot
{
    private LotoTTelemetrySnapshot() { }

    static JSONObject build(JSONArray signals, LotoTMqttPublisher.Config config, long now) throws Exception
    {
        JSONObject readings = new JSONObject();
        JSONObject observations = new JSONObject();
        long capturedAt = 0;
        for (int i=0; i<signals.length(); i++)
        {
            JSONObject s = signals.optJSONObject(i);
            if (s == null || s.isNull("value")) continue;
            String key = s.optString("mnemonic", "");
            String source = s.optString("source", "obd");
            long observedAt = s.optLong("updated_at", 0);
            long ttl = "motion".equals(source) ? 5_000L : "gps".equals(source) ? 30_000L : 60_000L;
            if (key.isEmpty() || observedAt <= 0 || observedAt > now || now-observedAt > ttl) continue;
            if ((key.startsWith("GPS_") || "gps".equals(source)) && !config.includeGps) continue;
            if ((key.startsWith("ACC_") || "motion".equals(source)) && !config.includeSensors) continue;
            if (!config.selectedSignals.isEmpty() && !config.selectedSignals.contains(key)) continue;
            Object value = s.get("value");
            if (value instanceof Number && !Double.isFinite(((Number)value).doubleValue())) continue;
            readings.put(key, value);
            JSONObject observation = new JSONObject();
            observation.put("observed_at", observedAt);
            observation.put("unit", s.optString("unit", ""));
            observation.put("source", source);
            observation.put("simulated", s.optBoolean("simulated", false));
            if (s.has("accuracy_m")) observation.put("accuracy_m", s.get("accuracy_m"));
            observations.put(key, observation);
            capturedAt = Math.max(capturedAt, observedAt);
        }
        if (readings.length()==0) return null;
        JSONObject snapshot = new JSONObject();
        snapshot.put("captured_at", capturedAt);
        snapshot.put("readings", readings);
        JSONObject metadata = new JSONObject();
        metadata.put("observations", observations);
        metadata.put("queued_at", now);
        metadata.put("schema_version", 2);
        metadata.put("app_version", BuildConfig.VERSION_NAME);
        metadata.put("publisher", "lotot-android-gateway");
        snapshot.put("metadata", metadata);
        return snapshot;
    }

    static String destination(LotoTMqttPublisher.Config config)
    {
        // Password rotation for the same principal keeps its backlog. A broker,
        // account, client or provisioned device change starts a separate partition.
        return hash(LotoTMqttPublisher.buildBrokerUri(config) + "\n" + config.username.trim()
                + "\n" + config.clientId + "\n" + config.deviceUid);
    }

    static String hash(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
