/*
 * In-process MQTT publisher adapted from AndrOBD MqttPublisher (GPLv3+).
 */
package com.fr3ts0n.ecu.gui.androbd;

import android.content.Context;
import android.provider.Settings;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class LotoTMqttPublisher
{
    interface Listener { void onMqttStateChanged(); }

    static final class Config
    {
        boolean enabled;
        String protocol = "tcp://";
        String host = "";
        int port = 1883;
        String username = "";
        String password = "";
        String clientId = "";
        String prefix = "LotoT/";
        int qos;
        boolean retain = true;
        int intervalSeconds = 5;
        Set<String> selectedSignals = new LinkedHashSet<>();

        Config copy()
        {
            Config copy = new Config();
            copy.enabled = enabled;
            copy.protocol = protocol;
            copy.host = host;
            copy.port = port;
            copy.username = username;
            copy.password = password;
            copy.clientId = clientId;
            copy.prefix = prefix;
            copy.qos = qos;
            copy.retain = retain;
            copy.intervalSeconds = intervalSeconds;
            copy.selectedSignals = new LinkedHashSet<>(selectedSignals);
            return copy;
        }
    }

    private final Listener listener;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final String fallbackClientId;
    private Config config = new Config();
    private Map<String, String> latestValues = Collections.emptyMap();
    private MqttClient client;
    private String clientUri;
    private String clientIdentity;
    private String status = "disabled";
    private String error;
    private long lastPublishAt;
    private long lastAttemptAt;
    private long publishedMessages;
    private boolean stopped;

    LotoTMqttPublisher(Context context, Listener listener)
    {
        this.listener = listener;
        String androidId = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        String suffix = androidId == null || androidId.length() < 6
                ? "device" : androidId.substring(androidId.length() - 6);
        fallbackClientId = "LotoT-" + suffix;
        executor.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    synchronized void setConfig(Config config)
    {
        Config normalized = config.copy();
        normalized.protocol = normalizeProtocol(normalized.protocol);
        normalized.host = safe(normalized.host);
        normalized.port = clamp(normalized.port, 1, 65535);
        normalized.clientId = safe(normalized.clientId);
        if (normalized.clientId.isEmpty()) normalized.clientId = fallbackClientId;
        normalized.prefix = normalizePrefix(normalized.prefix);
        normalized.qos = clamp(normalized.qos, 0, 2);
        normalized.intervalSeconds = clamp(normalized.intervalSeconds, 1, 3600);
        boolean endpointChanged = !buildBrokerUri(this.config).equals(buildBrokerUri(normalized))
                || !safe(this.config.clientId).equals(normalized.clientId)
                || !safe(this.config.username).equals(safe(normalized.username))
                || !safe(this.config.password).equals(safe(normalized.password));
        this.config = normalized;
        if (!normalized.enabled)
        {
            status = "disabled";
            error = null;
            disconnectLocked();
        }
        else
        {
            status = normalized.host.isEmpty() ? "configuration" : "waiting";
            error = null;
            if (endpointChanged) disconnectLocked();
            executor.execute(() -> publish(false));
        }
        notifyState();
    }

    synchronized Config getConfig()
    {
        return config.copy();
    }

    synchronized void updateSnapshot(Map<String, String> values)
    {
        latestValues = new LinkedHashMap<>(values);
    }

    void publishNow()
    {
        executor.execute(() -> publish(true));
    }

    private void tick()
    {
        Config current;
        long now = System.currentTimeMillis();
        synchronized (this)
        {
            if (stopped || !config.enabled || config.host.isEmpty()) return;
            if (now - lastAttemptAt < config.intervalSeconds * 1000L) return;
            current = config.copy();
        }
        publishWithConfig(current, false);
    }

    private void publish(boolean force)
    {
        Config current;
        synchronized (this)
        {
            if (stopped || !config.enabled)
            {
                status = "disabled";
                notifyState();
                return;
            }
            current = config.copy();
        }
        publishWithConfig(current, force);
    }

    private void publishWithConfig(Config current, boolean force)
    {
        Map<String, String> values;
        synchronized (this)
        {
            long now = System.currentTimeMillis();
            if (!force && now - lastAttemptAt < current.intervalSeconds * 1000L) return;
            lastAttemptAt = now;
            if (current.host.isEmpty())
            {
                status = "configuration";
                error = "Renseignez l’adresse du broker MQTT";
                notifyState();
                return;
            }
            values = new LinkedHashMap<>(latestValues);
            status = "connecting";
            error = null;
        }
        notifyState();

        if (values.isEmpty())
        {
            synchronized (this) { status = "waiting"; }
            notifyState();
            return;
        }

        try
        {
            MqttClient active = ensureConnected(current);
            JSONObject snapshot = new JSONObject();
            long capturedAt = System.currentTimeMillis();
            snapshot.put("captured_at", capturedAt);
            JSONObject readings = new JSONObject();
            int sent = 0;
            for (Map.Entry<String, String> entry : values.entrySet())
            {
                if (!current.selectedSignals.isEmpty()
                        && !current.selectedSignals.contains(entry.getKey())) continue;
                String key = sanitizeTopicPart(entry.getKey());
                String value = entry.getValue();
                active.publish(current.prefix + key,
                        value.getBytes(StandardCharsets.UTF_8), current.qos, current.retain);
                readings.put(entry.getKey(), parseValue(value));
                sent++;
            }
            snapshot.put("readings", readings);
            active.publish(current.prefix + "snapshot",
                    snapshot.toString().getBytes(StandardCharsets.UTF_8),
                    current.qos, current.retain);
            synchronized (this)
            {
                status = "online";
                error = null;
                lastPublishAt = capturedAt;
                publishedMessages += sent + 1L;
            }
        }
        catch (Exception ex)
        {
            synchronized (this)
            {
                status = "error";
                error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                disconnectLocked();
            }
        }
        notifyState();
    }

    private MqttClient ensureConnected(Config current) throws MqttException
    {
        String uri = buildBrokerUri(current);
        String identity = current.clientId;
        MqttClient active;
        synchronized (this)
        {
            if (client == null || !uri.equals(clientUri) || !identity.equals(clientIdentity))
            {
                disconnectLocked();
                client = new MqttClient(uri, identity, new MemoryPersistence());
                clientUri = uri;
                clientIdentity = identity;
            }
            active = client;
        }
        if (!active.isConnected())
        {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(6);
            options.setKeepAliveInterval(30);
            if (!safe(current.username).isEmpty())
            {
                options.setUserName(current.username);
                options.setPassword(safe(current.password).toCharArray());
            }
            // The network handshake must not hold the publisher monitor: UI state
            // reads and WebView updates remain responsive while a broker is slow.
            active.connect(options);
        }
        return active;
    }

    synchronized JSONObject getState() throws org.json.JSONException
    {
        JSONObject state = new JSONObject();
        state.put("enabled", config.enabled);
        state.put("status", status);
        state.put("broker", config.host.isEmpty() ? JSONObject.NULL : buildBrokerUri(config));
        state.put("last_publish", lastPublishAt);
        state.put("published_messages", publishedMessages);
        state.put("error", error == null ? JSONObject.NULL : error);
        JSONObject cfg = new JSONObject();
        cfg.put("protocol", config.protocol);
        cfg.put("host", config.host);
        cfg.put("port", config.port);
        cfg.put("username", config.username);
        cfg.put("password_set", !safe(config.password).isEmpty());
        cfg.put("client_id", config.clientId);
        cfg.put("prefix", config.prefix);
        cfg.put("qos", config.qos);
        cfg.put("retain", config.retain);
        cfg.put("interval_seconds", config.intervalSeconds);
        org.json.JSONArray selected = new org.json.JSONArray();
        for (String signal : config.selectedSignals) selected.put(signal);
        cfg.put("selected_signals", selected);
        state.put("config", cfg);
        return state;
    }

    synchronized void stop()
    {
        stopped = true;
        disconnectLocked();
        executor.shutdownNow();
        status = "disabled";
    }

    private void disconnectLocked()
    {
        if (client != null)
        {
            try { if (client.isConnected()) client.disconnectForcibly(500L, 500L); }
            catch (Exception ignored) { }
            try { client.close(); }
            catch (Exception ignored) { }
        }
        client = null;
        clientUri = null;
        clientIdentity = null;
    }

    private void notifyState()
    {
        if (listener != null) listener.onMqttStateChanged();
    }

    static String normalizePrefix(String prefix)
    {
        String normalized = safe(prefix).replaceAll("^/+", "");
        if (normalized.isEmpty()) normalized = "LotoT/";
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    static String normalizeProtocol(String protocol)
    {
        String normalized = safe(protocol).toLowerCase();
        if (!normalized.endsWith("://")) normalized += "://";
        if (!normalized.equals("tcp://") && !normalized.equals("ssl://")
                && !normalized.equals("ws://") && !normalized.equals("wss://"))
            return "tcp://";
        return normalized;
    }

    static String buildBrokerUri(Config config)
    {
        return normalizeProtocol(config.protocol) + safe(config.host) + ":"
                + clamp(config.port, 1, 65535);
    }

    private static Object parseValue(String value)
    {
        try { return Double.parseDouble(value); }
        catch (Exception ignored) { return value; }
    }

    private static String sanitizeTopicPart(String value)
    {
        return safe(value).replace('+', '_').replace('#', '_').replace(' ', '_');
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
