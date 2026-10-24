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
import org.json.JSONArray;

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

    private static final int FLUSH_BATCH_SIZE = 10;
    private static final long PUBLISH_TIMEOUT_MS = 5_000L;
    private static final long MAX_RETRY_DELAY_MS = 5 * 60_000L;

    private final Context context;
    private final Listener listener;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService captureExecutor = Executors.newSingleThreadScheduledExecutor();
    private final LotoTGatewayQueue queue;
    private final String fallbackClientId;
    private final String fallbackDeviceUid;
    private Config config = new Config();
    private JSONArray latestSignals = new JSONArray();
    private String lastObservationFingerprint = "";
    private volatile long configGeneration;
    private long clientGeneration = -1;
    private String lastDestination = "";
    private volatile boolean networkSuspended;
    private MqttClient client;
    private String clientUri;
    private String clientIdentity;
    private String status = "disabled";
    private String error;
    private long lastPublishAt;
    private long lastAttemptAt;
    private long lastEnqueueElapsed = -1;
    private long nextRetryElapsed;
    private long nextRetryAt;
    private long publishedMessages;
    private int consecutiveFailures;
    private int syncingTotal;
    private int syncingRemaining;
    private volatile boolean stopped;

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
        captureExecutor.scheduleWithFixedDelay(() -> captureSafely(false), 1, 1, TimeUnit.SECONDS);
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
        this.config = normalized;
        configGeneration++;
        networkSuspended = false;
        // A new destination must get an initial observation, even if the source
        // has not changed since a snapshot was queued for the previous one.
        if (!LotoTTelemetrySnapshot.destination(config).equals(lastDestination))
            lastObservationFingerprint = "";
        lastDestination = LotoTTelemetrySnapshot.destination(config);
        consecutiveFailures = 0;
        nextRetryAt = 0L;
        nextRetryElapsed = 0L;
        if (!normalized.enabled)
        {
            status = "disabled";
            error = null;
        }
        else
        {
            status = normalized.host.isEmpty() ? "configuration"
                    : queue.count() > 0 ? "queued" : "waiting";
            error = null;
            executor.execute(this::tick);
        }
        notifyState();
    }

    synchronized Config getConfig() { return config.copy(); }

    synchronized void updateSnapshot(JSONArray signals)
    {
        // Callers hand over a freshly created immutable-for-publishing snapshot.
        latestSignals = signals == null ? new JSONArray() : signals;
    }

    void publishNow()
    {
        captureExecutor.execute(() -> captureSafely(true));
        executor.execute(this::tick);
    }

    void suspendNetwork()
    {
        networkSuspended = true;
        configGeneration++;
        executor.execute(this::disconnectLocked);
    }

    private void captureSafely(boolean force)
    {
        try { capture(force); }
        catch (Exception ex)
        {
            synchronized (this) { status = "error"; error = "Local queue: " + ex.getClass().getSimpleName(); }
            notifyState();
        }
    }

    private void capture(boolean force) throws Exception
    {
        long now = System.currentTimeMillis();
        synchronized (this)
        {
            if (stopped || !config.enabled || config.host.isEmpty()) return;
            long elapsed = android.os.SystemClock.elapsedRealtime();
            if (!force && lastEnqueueElapsed >= 0
                    && elapsed-lastEnqueueElapsed < config.intervalSeconds*1000L) return;
            JSONObject snapshot = LotoTTelemetrySnapshot.build(latestSignals, config, now);
            if (snapshot == null) return;
            String fingerprint = snapshot.getJSONObject("metadata").getJSONObject("observations").toString();
            if (fingerprint.equals(lastObservationFingerprint)) return;
            snapshot.put("external_id", "android-" + UUID.randomUUID());
            snapshot.put("device_uid", config.deviceUid);
            queue.enqueue(LotoTTelemetrySnapshot.destination(config), config.prefix + "snapshot",
                    snapshot.toString(), config.qos, config.retain, snapshot.getLong("captured_at"));
            lastObservationFingerprint = fingerprint;
            lastEnqueueElapsed = elapsed;
        }
        notifyState();
    }

    private void tick()
    {
        try
        {
            Config current;
            long generation;
            synchronized (this)
            {
                if (stopped) return;
                current = config.copy();
                generation = configGeneration;
            }
            if (clientGeneration != generation) disconnectLocked();
            if (!current.enabled || networkSuspended) return;
            if (current.host.isEmpty())
            {
                synchronized (this) { status = "configuration"; error = context.getString(R.string.lotot_mqtt_host_required); }
            }
            else if (queue.count(LotoTTelemetrySnapshot.destination(current)) == 0)
            {
                synchronized (this) { status = "up_to_date"; syncingRemaining = 0; syncingTotal = 0; }
            }
            else if (android.os.SystemClock.elapsedRealtime() >= nextRetryElapsed) flushQueue(current, generation);
            notifyState();
        }
        catch (Exception ex)
        {
            // An uncaught ScheduledExecutor exception cancels all future ticks.
            synchronized (this) { status = "error"; error = ex.getClass().getSimpleName(); }
            notifyState();
        }
    }

    private void flushQueue(Config current, long generation)
    {
        String destination = LotoTTelemetrySnapshot.destination(current);
        int initial = queue.count(destination);
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
            MqttClient active = ensureConnected(current, generation);
            long deadline = android.os.SystemClock.elapsedRealtime() + 15_000L;
            while (!stopped && !networkSuspended && generation == configGeneration
                    && android.os.SystemClock.elapsedRealtime() < deadline)
            {
                List<LotoTGatewayQueue.Entry> batch = queue.peek(destination, FLUSH_BATCH_SIZE);
                if (batch.isEmpty()) break;
                for (LotoTGatewayQueue.Entry entry : batch)
                {
                    if (stopped || networkSuspended || generation != configGeneration) return;
                    publishEntry(active, entry, generation);
                    queue.delete(entry.id);
                    synchronized (this)
                    {
                        lastPublishAt = System.currentTimeMillis();
                        syncingRemaining = queue.count(destination);
                    }
                    notifyState();
                    if (android.os.SystemClock.elapsedRealtime() >= deadline) break;
                }
                if (batch.size() < FLUSH_BATCH_SIZE) break;
            }
            synchronized (this)
            {
                consecutiveFailures = 0;
                nextRetryAt = 0L;
                nextRetryElapsed = 0L;
                error = null;
                syncingRemaining = queue.count(destination);
                status = syncingRemaining == 0 ? "up_to_date" : "syncing";
            }
        }
        catch (Exception ex)
        {
            if (generation != configGeneration || stopped || networkSuspended) return;
            synchronized (this)
            {
                consecutiveFailures++;
                long delay = retryDelayMs(consecutiveFailures);
                nextRetryAt = System.currentTimeMillis() + delay;
                nextRetryElapsed = android.os.SystemClock.elapsedRealtime() + delay;
                syncingRemaining = queue.count(destination);
                status = "queued";
                error = ex.getClass().getSimpleName();
                disconnectLocked();
            }
        }
        notifyState();
    }

    private void publishEntry(MqttClient active, LotoTGatewayQueue.Entry entry, long generation) throws Exception
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
                if (stopped || networkSuspended || generation != configGeneration)
                    throw new InterruptedException("Publisher configuration changed");
                String key = keys.next();
                String value = String.valueOf(readings.opt(key));
                active.publish(prefix + sanitizeTopicPart(key),
                        value.getBytes(StandardCharsets.UTF_8), entry.qos, entry.retained);
                sent++;
            }
        }
        if (stopped || networkSuspended || generation != configGeneration)
            throw new InterruptedException("Publisher configuration changed");
        active.publish(entry.topic, entry.payload.getBytes(StandardCharsets.UTF_8),
                entry.qos, entry.retained);
        synchronized (this) { publishedMessages += sent + 1L; }
    }

    private MqttClient ensureConnected(Config current, long generation) throws MqttException
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
                client.setTimeToWait(PUBLISH_TIMEOUT_MS);
                clientGeneration = generation;
                clientUri = uri;
                clientIdentity = identity;
            }
            active = client;
        }
        if (!active.isConnected())
        {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);
            options.setConnectionTimeout(6);
            options.setKeepAliveInterval(30);
            if (!safe(current.username).isEmpty())
            {
                options.setUserName(current.username);
                options.setPassword((current.password == null ? "" : current.password).toCharArray());
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
        int activeRows = queue.count(LotoTTelemetrySnapshot.destination(config));
        state.put("queue_capacity", LotoTGatewayQueue.MAX_ROWS);
        state.put("queue_active", activeRows);
        state.put("queue_held", queued - activeRows);
        state.put("queue_dropped", queue.droppedCount());
        state.put("queue_oldest_captured_at", queue.oldestCapturedAt());
        state.put("network_suspended", networkSuspended);
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

    void stop()
    {
        synchronized (this) { stopped = true; configGeneration++; status = "disabled"; }
        captureExecutor.shutdownNow();
        // Network operations have bounded waits; close only after both workers exit.
        executor.shutdownNow();
        new Thread(() -> {
            try
            {
                captureExecutor.awaitTermination(10, TimeUnit.SECONDS);
                executor.awaitTermination(15, TimeUnit.SECONDS);
            }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            disconnectLocked();
            queue.close();
        }, "lotot-mqtt-close").start();
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
        clientGeneration = -1;
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
