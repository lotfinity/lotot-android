package com.fr3ts0n.ecu.gui.androbd;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class LotoTTelemetrySnapshotTest
{
    private JSONObject signal(long observedAt, String source) throws Exception
    {
        return new JSONObject().put("mnemonic", "engine_speed").put("value", 840)
                .put("unit", "rpm").put("source", source).put("updated_at", observedAt);
    }

    @Test public void observationTimeSurvivesLaterEnqueue() throws Exception
    {
        LotoTMqttPublisher.Config config = new LotoTMqttPublisher.Config();
        JSONArray signals = new JSONArray().put(signal(100_000, "obd"));
        JSONObject first = LotoTTelemetrySnapshot.build(signals, config, 101_000);
        JSONObject later = LotoTTelemetrySnapshot.build(signals, config, 110_000);
        assertEquals(100_000, later.getLong("captured_at"));
        assertEquals(first.getJSONObject("metadata").getJSONObject("observations").toString(),
                later.getJSONObject("metadata").getJSONObject("observations").toString());
        JSONObject provenance = later.getJSONObject("metadata").getJSONObject("observations").getJSONObject("engine_speed");
        assertEquals("rpm", provenance.getString("unit"));
        assertEquals("obd", provenance.getString("source"));
        assertEquals(840, later.getJSONObject("readings").getInt("engine_speed"));
    }

    @Test public void staleMissingAndFutureSamplesAreNotPublished() throws Exception
    {
        LotoTMqttPublisher.Config config = new LotoTMqttPublisher.Config();
        assertNull(LotoTTelemetrySnapshot.build(new JSONArray().put(signal(100_000, "obd")), config, 160_001));
        assertNull(LotoTTelemetrySnapshot.build(new JSONArray().put(signal(0, "obd")), config, 100_000));
        assertNull(LotoTTelemetrySnapshot.build(new JSONArray().put(signal(100_001, "obd")), config, 100_000));
    }

    @Test public void selectionAndAuxiliaryPrivacyControlsAreApplied() throws Exception
    {
        LotoTMqttPublisher.Config config = new LotoTMqttPublisher.Config();
        JSONObject gps = signal(100_000, "gps").put("mnemonic", "GPS_LATITUDE").put("accuracy_m", 7.0);
        JSONArray signals = new JSONArray().put(gps);
        assertNull(LotoTTelemetrySnapshot.build(signals, config, 101_000));
        config.includeGps = true;
        assertNotNull(LotoTTelemetrySnapshot.build(signals, config, 101_000));
        config.selectedSignals.add("engine_speed");
        assertNull(LotoTTelemetrySnapshot.build(signals, config, 101_000));
    }

    @Test public void destinationChangesForBrokerAccountOrVehicleButNotPassword()
    {
        LotoTMqttPublisher.Config config = new LotoTMqttPublisher.Config();
        config.host="a.example"; config.username="alice"; config.deviceUid="vehicle-a";
        String original = LotoTTelemetrySnapshot.destination(config);
        config.password="rotated";
        assertEquals(original, LotoTTelemetrySnapshot.destination(config));
        config.host="b.example";
        assertNotEquals(original, LotoTTelemetrySnapshot.destination(config));
        config.host="a.example"; config.username="bob";
        assertNotEquals(original, LotoTTelemetrySnapshot.destination(config));
        config.username="alice"; config.deviceUid="vehicle-b";
        assertNotEquals(original, LotoTTelemetrySnapshot.destination(config));
    }

    @Test public void disabledSourceClearsOldOptionalFields() throws Exception
    {
        LotoTBuiltinSignalStore store = new LotoTBuiltinSignalStore();
        store.define("GPS_SPEED", "Speed", "km/h", 0, 300, "gps");
        store.update("GPS_SPEED", 42, System.currentTimeMillis(), 5.0);
        JSONArray before = new JSONArray(); store.appendTo(before);
        assertEquals(1, before.length());
        store.clearSource("gps");
        JSONArray after = new JSONArray(); store.appendTo(after);
        assertEquals(0, after.length());
    }
}
