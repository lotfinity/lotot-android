package com.fr3ts0n.ecu.gui.androbd;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Sticky foreground owner for GPS, motion, encrypted MQTT credentials and the
 * durable telemetry queue. MainActivity only feeds decoded OBD snapshots.
 */
public final class LotoTGatewayService extends Service
        implements LotoTBuiltinServices.Listener
{
    interface Listener { void onGatewayStateChanged(); }

    private static final String CHANNEL_ID = "lotot_gateway";
    private static final int NOTIFICATION_ID = 2080;

    final class LocalBinder extends Binder
    {
        LotoTGatewayService getService() { return LotoTGatewayService.this; }
    }

    private final IBinder binder = new LocalBinder();
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private LotoTBuiltinServices builtins;
    private long lastNotificationAt;
    private String lastNotificationFingerprint = "";
    private boolean vehicleSessionActive;
    private boolean foregroundActive;

    static void start(Context context)
    {
        Intent intent = new Intent(context, LotoTGatewayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ContextCompat.startForegroundService(context, intent);
        else
            context.startService(intent);
    }

    @Override public void onCreate()
    {
        super.onCreate();
        createNotificationChannel();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        builtins = new LotoTBuiltinServices(getApplicationContext(), preferences, this);
        builtins.start();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId)
    {
        reconcileForeground(true);
        return requiresForeground() ? START_STICKY : START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public void onDestroy()
    {
        if (builtins != null)
        {
            builtins.stop();
            builtins = null;
        }
        super.onDestroy();
    }

    void addListener(Listener listener)
    {
        if (listener != null) listeners.add(listener);
    }

    void removeListener(Listener listener)
    {
        if (listener != null) listeners.remove(listener);
    }

    boolean applyConfig(String json) throws org.json.JSONException
    {
        boolean permission = builtins != null && builtins.applyConfig(json);
        reconcileForeground(true);
        return permission;
    }

    void onLocationPermissionResult()
    {
        if (builtins != null) builtins.onLocationPermissionResult();
        reconcileForeground(true);
    }

    boolean needsLocationPermission()
    {
        return builtins != null && builtins.needsLocationPermission();
    }

    void setVehicleSessionActive(boolean active)
    {
        vehicleSessionActive = active;
        reconcileForeground(true);
    }

    boolean requiresForeground()
    {
        if (vehicleSessionActive) return true;
        try
        {
            JSONObject state = builtins == null ? null : builtins.getState();
            if (state == null) return false;
            JSONObject gps = state.optJSONObject("gps");
            JSONObject mqtt = state.optJSONObject("mqtt");
            return (gps != null && gps.optBoolean("enabled"))
                    || (mqtt != null && mqtt.optBoolean("enabled"));
        }
        catch (Exception ignored)
        {
            return vehicleSessionActive;
        }
    }

    void publishMqttNow()
    {
        if (builtins != null) builtins.publishMqttNow();
    }

    void appendSignals(JSONArray target) throws org.json.JSONException
    {
        if (builtins != null) builtins.appendSignals(target);
    }

    void updateTelemetry(JSONArray signals)
    {
        if (builtins != null) builtins.updateTelemetry(signals);
    }

    JSONObject getState() throws org.json.JSONException
    {
        JSONObject state = builtins == null ? new JSONObject() : builtins.getState();
        state.put("foreground", foregroundActive);
        state.put("service", foregroundActive ? "foreground" : "bound");
        state.put("vehicle_session", vehicleSessionActive);
        return state;
    }

    @Override public void onBuiltinServicesStateChanged()
    {
        reconcileForeground(false);
        for (Listener listener : listeners) listener.onGatewayStateChanged();
    }

    void refreshForegroundService()
    {
        reconcileForeground(true);
    }

    private void reconcileForeground(boolean force)
    {
        if (!requiresForeground())
        {
            if (foregroundActive)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    stopForeground(STOP_FOREGROUND_REMOVE);
                else
                    stopForeground(true);
                foregroundActive = false;
            }
            lastNotificationFingerprint = "";
            stopSelf();
            return;
        }
        refreshForegroundNotification(force);
    }

    private void refreshForegroundNotification(boolean force)
    {
        long now = System.currentTimeMillis();
        String fingerprint = notificationFingerprint();
        if (!force && fingerprint.equals(lastNotificationFingerprint)
                && now - lastNotificationAt < 15_000L) return;
        lastNotificationFingerprint = fingerprint;
        lastNotificationAt = now;
        startForegroundSafely(buildNotification());
        foregroundActive = true;
    }

    private String notificationFingerprint()
    {
        try
        {
            JSONObject mqtt = getState().optJSONObject("mqtt");
            if (mqtt == null) return "starting";
            return vehicleSessionActive + ":" + mqtt.optBoolean("enabled") + ":" + mqtt.optString("status", "")
                    + ":" + mqtt.optInt("queue_depth", 0)
                    + ":" + mqtt.optInt("syncing_remaining", 0);
        }
        catch (Exception ignored)
        {
            return "starting";
        }
    }

    private void createNotificationChannel()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.lotot_gateway_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Maintient la collecte OBD et la synchronisation hors ligne");
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification buildNotification()
    {
        String title = getString(R.string.lotot_gateway_active);
        String text = getString(R.string.lotot_gateway_ready);
        try
        {
            JSONObject mqtt = getState().optJSONObject("mqtt");
            if (mqtt != null && mqtt.optBoolean("enabled"))
            {
                int queued = mqtt.optInt("queue_depth", 0);
                String status = mqtt.optString("status", "waiting");
                if ("syncing".equals(status))
                    text = "Synchronisation · " + mqtt.optInt("syncing_remaining", queued)
                            + "/" + Math.max(queued, mqtt.optInt("syncing_total", queued));
                else if (queued > 0)
                    text = getString(R.string.lotot_gateway_offline, queued);
                else if ("up_to_date".equals(status) || "online".equals(status))
                    text = getString(R.string.lotot_gateway_current);
                else if ("configuration".equals(status))
                    text = "MQTT en attente de configuration";
            }
        }
        catch (Exception ignored) { }

        Intent launch = new Intent(this, SplashActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void startForegroundSafely(Notification notification)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            int type = 0;
            boolean bluetoothGranted;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                bluetoothGranted = ContextCompat.checkSelfPermission(this,
                        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            else
                bluetoothGranted = ContextCompat.checkSelfPermission(this,
                        Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
            if (bluetoothGranted) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;

            boolean locationGranted = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean locationEnabled = false;
            boolean mqttEnabled = false;
            try
            {
                JSONObject state = builtins == null ? null : builtins.getState();
                JSONObject gps = state == null ? null : state.optJSONObject("gps");
                JSONObject mqtt = state == null ? null : state.optJSONObject("mqtt");
                locationEnabled = gps != null && gps.optBoolean("enabled");
                mqttEnabled = mqtt != null && mqtt.optBoolean("enabled");
            }
            catch (Exception ignored) { }
            if (locationGranted && locationEnabled)
                type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            if (mqttEnabled) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            if (type == 0) type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            try
            {
                startForeground(NOTIFICATION_ID, notification, type);
                return;
            }
            catch (SecurityException ignored) { }
        }
        startForeground(NOTIFICATION_ID, notification);
    }
}
