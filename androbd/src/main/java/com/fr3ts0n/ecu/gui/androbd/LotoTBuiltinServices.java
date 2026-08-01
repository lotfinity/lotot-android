/*
 * Unified in-process replacement for the separate AndrOBD MQTT, GPS and
 * SensorProvider APKs. GPLv3+ source attribution is documented in
 * LOTOT_INTEGRATION.md.
 */
package com.fr3ts0n.ecu.gui.androbd;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

final class LotoTBuiltinServices implements
        LotoTGpsProvider.Listener,
        LotoTMotionSensorProvider.Listener,
        LotoTMqttPublisher.Listener
{
    interface Listener { void onBuiltinServicesStateChanged(); }

    private static final String GPS_ENABLED = "lotot_builtin_gps_enabled";
    private static final String SENSOR_ENABLED = "lotot_builtin_sensor_enabled";
    private static final String MQTT_ENABLED = "lotot_builtin_mqtt_enabled";
    private static final String MQTT_PROTOCOL = "lotot_builtin_mqtt_protocol";
    private static final String MQTT_HOST = "lotot_builtin_mqtt_host";
    private static final String MQTT_PORT = "lotot_builtin_mqtt_port";
    private static final String MQTT_USERNAME = "lotot_builtin_mqtt_username";
    private static final String MQTT_PASSWORD = "lotot_builtin_mqtt_password";
    private static final String MQTT_DEVICE_UID = "lotot_builtin_mqtt_device_uid";
    private static final String MQTT_CLIENT_ID = "lotot_builtin_mqtt_client_id";
    private static final String MQTT_QOS = "lotot_builtin_mqtt_qos";
    private static final String MQTT_RETAIN = "lotot_builtin_mqtt_retain";
    private static final String MQTT_INTERVAL = "lotot_builtin_mqtt_interval";
    private static final String MQTT_SELECTED = "lotot_builtin_mqtt_selected";

    private final SharedPreferences preferences;
    private final LotoTSecureCredentialStore secureCredentials;
    private final Listener listener;
    private final LotoTBuiltinSignalStore store = new LotoTBuiltinSignalStore();
    private final LotoTGpsProvider gps;
    private final LotoTMotionSensorProvider sensors;
    private final LotoTMqttPublisher mqtt;

    LotoTBuiltinServices(Context context, SharedPreferences preferences, Listener listener)
    {
        Context appContext = context.getApplicationContext();
        this.preferences = preferences;
        this.listener = listener;
        secureCredentials = new LotoTSecureCredentialStore(appContext);
        secureCredentials.migrateLegacyPassword(preferences, MQTT_PASSWORD);
        gps = new LotoTGpsProvider(appContext, store, this);
        sensors = new LotoTMotionSensorProvider(appContext, store, this);
        mqtt = new LotoTMqttPublisher(appContext, this);
    }

    void start()
    {
        gps.setEnabled(preferences.getBoolean(GPS_ENABLED, false));
        sensors.setEnabled(preferences.getBoolean(SENSOR_ENABLED, true));
        mqtt.setConfig(readMqttConfig());
        notifyState();
    }

    void stop()
    {
        gps.setEnabled(false);
        sensors.setEnabled(false);
        mqtt.stop();
    }

    boolean applyConfig(String json) throws org.json.JSONException
    {
        JSONObject payload = new JSONObject(json == null ? "{}" : json);
        SharedPreferences.Editor edit = preferences.edit();
        if (payload.has("gps_enabled"))
            edit.putBoolean(GPS_ENABLED, payload.optBoolean("gps_enabled"));
        if (payload.has("sensors_enabled"))
            edit.putBoolean(SENSOR_ENABLED, payload.optBoolean("sensors_enabled"));

        JSONObject mqttPayload = payload.optJSONObject("mqtt");
        if (mqttPayload != null)
        {
            if (mqttPayload.has("enabled")) edit.putBoolean(MQTT_ENABLED,
                    mqttPayload.optBoolean("enabled"));
            putString(edit, MQTT_PROTOCOL, mqttPayload, "protocol");
            putString(edit, MQTT_HOST, mqttPayload, "host");
            if (mqttPayload.has("port")) edit.putInt(MQTT_PORT,
                    mqttPayload.optInt("port", 1883));
            putString(edit, MQTT_USERNAME, mqttPayload, "username");
            if (mqttPayload.has("password"))
            {
                String password = mqttPayload.optString("password", "");
                if (!secureCredentials.putPassword(password))
                    throw new org.json.JSONException("Impossible de chiffrer le mot de passe MQTT");
                edit.remove(MQTT_PASSWORD);
            }
            putString(edit, MQTT_DEVICE_UID, mqttPayload, "device_uid");
            putString(edit, MQTT_CLIENT_ID, mqttPayload, "client_id");
            if (mqttPayload.has("qos")) edit.putInt(MQTT_QOS,
                    mqttPayload.optInt("qos", 1));
            if (mqttPayload.has("retain")) edit.putBoolean(MQTT_RETAIN,
                    mqttPayload.optBoolean("retain", false));
            if (mqttPayload.has("interval_seconds")) edit.putInt(MQTT_INTERVAL,
                    mqttPayload.optInt("interval_seconds", 5));
            if (mqttPayload.has("selected_signals"))
            {
                JSONArray selected = mqttPayload.optJSONArray("selected_signals");
                LinkedHashSet<String> values = new LinkedHashSet<>();
                if (selected != null)
                    for (int i = 0; i < selected.length(); i++)
                    {
                        String value = selected.optString(i, "").trim();
                        if (!value.isEmpty()) values.add(value);
                    }
                edit.putStringSet(MQTT_SELECTED, values);
            }
        }
        edit.apply();
        gps.setEnabled(preferences.getBoolean(GPS_ENABLED, false));
        sensors.setEnabled(preferences.getBoolean(SENSOR_ENABLED, true));
        mqtt.setConfig(readMqttConfig());
        notifyState();
        return gps.needsPermission();
    }

    void onLocationPermissionResult()
    {
        gps.start();
        notifyState();
    }

    boolean needsLocationPermission() { return gps.needsPermission(); }

    void publishMqttNow() { mqtt.publishNow(); }

    void appendSignals(JSONArray target) throws org.json.JSONException { store.appendTo(target); }

    void updateTelemetry(JSONArray signals)
    {
        Map<String, String> values = new LinkedHashMap<>();
        if (signals != null)
        {
            for (int i = 0; i < signals.length(); i++)
            {
                JSONObject signal = signals.optJSONObject(i);
                if (signal == null || signal.isNull("value")) continue;
                String mnemonic = signal.optString("mnemonic", "").trim();
                if (mnemonic.isEmpty()) continue;
                values.put(mnemonic, String.valueOf(signal.opt("value")));
            }
        }
        mqtt.updateSnapshot(values);
    }

    JSONObject getState() throws org.json.JSONException
    {
        JSONObject state = new JSONObject();
        state.put("embedded", true);
        state.put("gps", gps.getState());
        state.put("sensors", sensors.getState());
        state.put("mqtt", mqtt.getState());
        return state;
    }

    private LotoTMqttPublisher.Config readMqttConfig()
    {
        LotoTMqttPublisher.Config config = new LotoTMqttPublisher.Config();
        config.enabled = preferences.getBoolean(MQTT_ENABLED, false);
        config.protocol = preferences.getString(MQTT_PROTOCOL, "tcp://");
        config.host = preferences.getString(MQTT_HOST, "");
        config.port = preferences.getInt(MQTT_PORT, 1883);
        config.username = preferences.getString(MQTT_USERNAME, "");
        config.password = secureCredentials.getPassword();
        config.deviceUid = preferences.getString(MQTT_DEVICE_UID, "");
        config.clientId = preferences.getString(MQTT_CLIENT_ID, "");
        config.qos = preferences.getInt(MQTT_QOS, 1);
        config.retain = preferences.getBoolean(MQTT_RETAIN, false);
        config.intervalSeconds = preferences.getInt(MQTT_INTERVAL, 5);
        config.selectedSignals = new LinkedHashSet<>(preferences.getStringSet(
                MQTT_SELECTED, new LinkedHashSet<>()));
        return config;
    }

    private static void putString(SharedPreferences.Editor edit, String preferenceKey,
                                  JSONObject source, String jsonKey)
    {
        if (source.has(jsonKey)) edit.putString(preferenceKey,
                source.optString(jsonKey, ""));
    }

    @Override public void onGpsStateChanged() { notifyState(); }
    @Override public void onSensorStateChanged() { notifyState(); }
    @Override public void onMqttStateChanged() { notifyState(); }

    private void notifyState()
    {
        if (listener != null) listener.onBuiltinServicesStateChanged();
    }
}
