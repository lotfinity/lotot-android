/*
 * Reliable in-process MQTT gateway adapted from AndrOBD MqttPublisher (GPLv3+).
 */
package com.fr3ts0n.ecu.gui.androbd;

import android.content.Context;
import android.content.SharedPreferences;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
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
        String deviceUid = "";
        String clientId = "";
        String prefix = "";
        int qos = 1;
        boolean retain;
        boolean includeGps;
        boolean includeSensors;
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
            copy.deviceUid = deviceUid;
            copy.clientId = clientId;
            copy.prefix = prefix;
            copy.qos = qos;
            copy.retain = retain;
            copy.includeGps = includeGps;
            copy.includeSensors = includeSensors;
            copy.intervalSeconds = intervalSeconds;
            copy.selectedSignals = new LinkedHashSet<>(selectedSignals);
            return copy;
        }
    }

    private static final int FLUSH_BATCH_SIZE = 50;
    private static final long MAX_RETRY_DELAY_MS = 5 * 60_000L;

    private final Context context;
    private final Listener listener;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final LotoTGatewayQueue queue;
    private final String fallbackClientId;
    private final String fallbackDeviceUid;
    private Config config = new Config();
    private Map<String, String> latestValues = Collections.emptyMap();
    private MqttClient client;
    private String clientUri;
    private String clientIdentity;
    private String status = "disabled";
    private String error;
    private long lastPublishAt;
    private long lastAttemptAt;
    private long lastEnqueueAt;
    private long nextRetryAt;
    private long publishedMessages;
    private int consecutiveFailures;
    private int syncingTotal;
    private int syncingRemaining;
    private boolean stopped;

    LotoTMqttPublisher(Context context, Listener listener)
    {
        this.context = context.getApplicationContext();
        this.listener = listener;
        Context appContext = this.context;
        queue = new LotoTGatewayQueue(appContext);
        SharedPreferences identity = appContext.getSharedPreferences(
                "lotot_gateway_identity", Context.MODE_PRIVATE);
        String suffix = identity.getString("installation_suffix", "");
        if (suffix == null || suffix.length() < 6)
        {
            suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            identity.edit().putString("installation_suffix", suffix).apply();
        }
        fallbackClientId = "LotoT-" + suffix;
        fallbackDeviceUid = "android-" + suffix;
        executor.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    synchronized void setConfig(Config config)
    {
        Config normalized = config.copy();
        normalized.protocol = normalizeProtocol(normalized.protocol);
        normalized.host = safe(normalized.host);
        normalized.port = clamp(normalized.port, 1, 65535);
        normalized.deviceUid = normalizeDeviceUid(normalized.deviceUid);
        if (normalized.deviceUid.isEmpty()) normalized.deviceUid = fallbackDeviceUid;
        normalized.clientId = safe(normalized.clientId);
        if (normalized.clientId.isEmpty()) normalized.clientId = fallbackClientId;
        normalized.prefix = topicPrefixForDevice(normalized.deviceUid);
        normalized.qos = clamp(normalized.qos, 0, 2);
        normalized.intervalSeconds = clamp(normalized.intervalSeconds, 1, 3600);
        boolean endpointChanged = !buildBrokerUri(this.config).equals(buildBrokerUri(normalized))
                || !safe(this.config.clientId).equals(normalized.clientId)
                || !safe(this.config.username).equals(safe(normalized.username))
                || !safe(this.config.password).equals(safe(normalized.password));
        this.config = normalized;
        consecutiveFailures = 0;
        nextRetryAt = 0L;
        if (!normalized.enabled)
        {
            status = "disabled";
            error = null;
            disconnectLocked();
        }
        else
        {
            status = normalized.host.isEmpty() ? "configuration"
                    : queue.count() > 0 ? "queued" : "waiting";
            error = null;
            if (endpointChanged) disconnectLocked();
            executor.execute(() -> runCycle(false));
        }
        notifyState();
    }

    synchronized Config getConfig() { return config.copy(); }

    synchronized void updateSnapshot(Map<String, String> values)
    {
        latestValues = values == null ? Collections.emptyMap() : new LinkedHashMap<>(values);
    }

    void publishNow() { executor.execute(() -> runCycle(true)); }

    private void tick() { runCycle(false); }

    private void runCycle(boolean force)
    {
        Config current;
        Map<String, String> values;
        long now = System.currentTimeMillis();
        synchronized (this)
        {
            if (stopped || !config.enabled) return;
            current = config.copy();
            values = new LinkedHashMap<>(latestValues);
        }

        if (current.host.isEmpty())
        {
            synchronized (this)
            {
                status = "configuration";
                error = context.getString(R.string.lotot_mqtt_host_required);
            }
            notifyState();
            return;
        }

        boolean due;
        synchronized (this)
        {
            due = force || now - lastEnqueueAt >= current.intervalSeconds * 1000L;
        }
        if (due && !values.isEmpty()) enqueueSnapshot(current, values, now);

        int queued = queue.count();
        if (queued <= 0)
        {
            synchronized (this)
            {
                if (!"online".equals(status)) status = values.isEmpty() ? "waiting" : "up_to_date";
                syncingTotal = 0;
                syncingRemaining = 0;
            }
            notifyState();
            return;
        }

        synchronized (this)
        {
            if (!force && now < nextRetryAt)
            {
                status = "queued";
                syncingRemaining = queued;
                notifyState();
                return;
            }
        }
        flushQueue(current);
    }

    private void enqueueSnapshot(Config current, Map<String, String> values, long capturedAt)
    {
        try
        {
            JSONObject snapshot = new JSONObject();
            snapshot.put("captured_at", capturedAt);
            snapshot.put("external_id", "android-" + capturedAt + "-" + UUID.randomUUID());
            snapshot.put("device_uid", current.deviceUid);
            JSONObject metadata = new JSONObject();
            metadata.put("app_version", BuildConfig.VERSION_NAME);
            metadata.put("publisher", "lotot-android-gateway");
            metadata.put("queued_at", capturedAt);
            snapshot.put("metadata", metadata);
            JSONObject readings = new JSONObject();
            for (Map.Entry<String, String> entry : values.entrySet())
            {
                String key = entry.getKey();
                if (key.startsWith("GPS_") && !current.includeGps) continue;
                if (key.startsWith("ACC_") && !current.includeSensors) continue;
                if (!current.selectedSignals.isEmpty()
                        && !current.selectedSignals.contains(key)) continue;
                readings.put(key, parseValue(entry.getValue()));
            }
            if (readings.length() == 0) return;
            snapshot.put("readings", readings);
            queue.enqueue(current.prefix + "snapshot", snapshot.toString(), current.qos,
                    current.retain, capturedAt);
            synchronized (this)
            {
                lastEnqueueAt = capturedAt;
                if (!"syncing".equals(status)) status = "queued";
                syncingRemaining = queue.count();
            }
            notifyState();
        }
        catch (Exception ex)
        {
            synchronized (this)
            {
                status = "error";
                error = "File locale: " + errorMessage(ex);
            }
            notifyState();
        }
    }

    private void flushQueue(Config current)
    {
        int initial = queue.count();
        synchronized (this)
        {
            status = "syncing";
            error = null;
            lastAttemptAt = System.currentTimeMillis();
            syncingTotal = initial;
            syncingRemaining = initial;
        }
        notifyState();

        try
        {
            MqttClient active = ensureConnected(current);
            while (!stopped)
            {
                List<LotoTGatewayQueue.Entry> batch = queue.peek(FLUSH_BATCH_SIZE);
                if (batch.isEmpty()) break;
                for (LotoTGatewayQueue.Entry entry : batch)
                {
                    publishEntry(active, entry);
                    queue.delete(entry.id);
                    synchronized (this)
                    {
                        lastPublishAt = System.currentTimeMillis();
                        syncingRemaining = queue.count();
                    }
                    notifyState();
                }
                if (batch.size() < FLUSH_BATCH_SIZE) break;
            }
            synchronized (this)
            {
                consecutiveFailures = 0;
                nextRetryAt = 0L;
                error = null;
                syncingRemaining = queue.count();
                status = syncingRemaining == 0 ? "up_to_date" : "syncing";
            }
        }
        catch (Exception ex)
        {
            synchronized (this)
            {
                consecutiveFailures++;
                long delay = retryDelayMs(consecutiveFailures);
                nextRetryAt = System.currentTimeMillis() + delay;
                syncingRemaining = queue.count();
                status = "queued";
                error = errorMessage(ex);
                disconnectLocked();
            }
        }
        notifyState();
    }

    private void publishEntry(MqttClient active, LotoTGatewayQueue.Entry entry) throws Exception
    {
        JSONObject snapshot = new JSONObject(entry.payload);
        JSONObject readings = snapshot.optJSONObject("readings");
        String prefix = entry.topic.endsWith("snapshot")
                ? entry.topic.substring(0, entry.topic.length() - "snapshot".length())
                : entry.topic + "/";
        int sent = 0;
        if (readings != null)
        {
            java.util.Iterator<String> keys = readings.keys();
            while (keys.hasNext())
            {
                String key = keys.next();
                String value = String.valueOf(readings.opt(key));
                active.publish(prefix + sanitizeTopicPart(key),
                        value.getBytes(StandardCharsets.UTF_8), entry.qos, entry.retained);
                sent++;
            }
        }
        active.publish(entry.topic, entry.payload.getBytes(StandardCharsets.UTF_8),
                entry.qos, entry.retained);
        synchronized (this) { publishedMessages += sent + 1L; }
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
            active.connect(options);
        }
        return active;
    }

    synchronized JSONObject getState() throws org.json.JSONException
    {
        int queued = queue.count();
        JSONObject state = new JSONObject();
        state.put("enabled", config.enabled);
        state.put("status", status);
        state.put("broker", config.host.isEmpty() ? JSONObject.NULL : buildBrokerUri(config));
        state.put("last_publish", lastPublishAt);
        state.put("last_attempt", lastAttemptAt);
        state.put("published_messages", publishedMessages);
        state.put("queue_depth", queued);
        state.put("queue_capacity", LotoTGatewayQueue.MAX_ROWS);
        state.put("syncing_total", syncingTotal);
        state.put("syncing_remaining", syncingRemaining);
        state.put("next_retry", nextRetryAt);
        state.put("retry_count", consecutiveFailures);
        state.put("error", error == null ? JSONObject.NULL : error);
        JSONObject cfg = new JSONObject();
        cfg.put("protocol", config.protocol);
        cfg.put("host", config.host);
        cfg.put("port", config.port);
        cfg.put("username", config.username);
        cfg.put("password_set", !safe(config.password).isEmpty());
        cfg.put("credentials_encrypted", true);
        cfg.put("device_uid", config.deviceUid);
        cfg.put("client_id", config.clientId);
        cfg.put("prefix", config.prefix);
        cfg.put("qos", config.qos);
        cfg.put("retain", config.retain);
        cfg.put("include_gps", config.includeGps);
        cfg.put("include_sensors", config.includeSensors);
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
        queue.close();
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

    static long retryDelayMs(int failureCount)
    {
        int exponent = Math.max(0, Math.min(8, failureCount - 1));
        return Math.min(MAX_RETRY_DELAY_MS, 2_000L << exponent);
    }

    static String normalizeDeviceUid(String value)
    {
        String normalized = safe(value).replaceAll("[^A-Za-z0-9._:-]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
    }

    static String topicPrefixForDevice(String deviceUid)
    {
        String normalized = normalizeDeviceUid(deviceUid);
        return normalized.isEmpty() ? "LotoT/devices/unknown/"
                : "LotoT/devices/" + normalized + "/";
    }

    static String normalizeProtocol(String protocol)
    {
        String normalized = safe(protocol).toLowerCase(Locale.ROOT);
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

    private static String errorMessage(Exception ex)
    {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
