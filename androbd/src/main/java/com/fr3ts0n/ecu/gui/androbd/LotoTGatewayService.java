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
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import com.fr3ts0n.ecu.EcuDataItems;
import com.fr3ts0n.ecu.EcuDataItem;
import com.fr3ts0n.ecu.prot.obd.ElmProt;
import com.fr3ts0n.ecu.prot.obd.ObdProt;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import android.preference.PreferenceManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Owns the vehicle transport and samples decoded OBD data independently of the UI.
 * Foreground execution follows Android's connected-device/location/dataSync rules.
 */
public final class LotoTGatewayService extends Service
        implements LotoTBuiltinServices.Listener, PropertyChangeListener
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
    private boolean closing;
    private boolean timedOut;
    private boolean lost;
    private android.os.PowerManager.WakeLock sessionWakeLock;
    private long wakeLockRenewedAt;
    private CommService transport;
    private CommService.MEDIUM transportMedium;
    private Handler uiHandler;
    private long transportGeneration;
    private String deviceAddress;
    private String deviceMedium;
    private String deviceName;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable sampler = new Runnable()
    {
        @Override public void run()
        {
            if (closing || timedOut) return;
            updateSessionWakeLock();
            sampleTelemetry();
            if (transport != null && transport.getState() == CommService.STATE.CONNECTED
                    && (transportMedium == CommService.MEDIUM.BLUETOOTH || transportMedium == CommService.MEDIUM.BLE)
                    && LotoTConnectionWatchdog.isTimedOut(System.currentTimeMillis(),
                    transport.getConnectedAtMs(), transport.getLastReceiveActivityMs(), 15_000L, 3_500L))
                transport.connectionLost();
            handler.postDelayed(this, 1000L);
        }
    };

    private void sampleTelemetry()
    {
        if (builtins == null) return;
        JSONArray signals = hasVehicleSession() && transport.getState() == CommService.STATE.CONNECTED
                ? LotoTObdSignals.collect("obd") : ElmProt.runDemo
                ? LotoTObdSignals.collect("demo") : new JSONArray();
        try { builtins.appendSignals(signals); } catch (org.json.JSONException ignored) { }
        builtins.updateTelemetry(signals);
    }

    void attachUi(Handler ui) { uiHandler = ui; }
    void detachUi(Handler ui) { if (uiHandler == ui) uiHandler = null; }
    CommService getTransport() { return transport; }
    CommService.MEDIUM getTransportMedium() { return transportMedium; }
    boolean hasVehicleSession() { return vehicleSessionActive && transport != null; }
    String getDeviceAddress() { return deviceAddress; }
    String getDeviceMedium() { return deviceMedium; }
    String getDeviceName() { return deviceName; }
    String getSessionStatus()
    {
        return hasVehicleSession() ? transport.getState() == CommService.STATE.CONNECTED
                ? "live" : "connecting" : lost ? "lost" : "offline";
    }

    CommService createTransport(CommService.MEDIUM medium, String address, String mediumName,
                               String name, Handler ui)
    {
        disconnectVehicle();
        timedOut = false;
        uiHandler = ui;
        deviceAddress = address; deviceMedium = mediumName; deviceName = name;
        long generation = ++transportGeneration;
        Handler events = new Handler(Looper.getMainLooper(), message -> {
            if (generation != transportGeneration || closing) return true;
            if (message.what == MainActivity.MESSAGE_DEVICE_NAME)
                deviceName = message.getData().getString(MainActivity.DEVICE_NAME);
            if (message.what == MainActivity.MESSAGE_CONNECTION_LOST) lost = true;
            if (message.what == MainActivity.MESSAGE_STATE_CHANGE)
            {
                CommService.STATE state = (CommService.STATE) message.obj;
                if (state == CommService.STATE.CONNECTED)
                {
                    for (EcuDataItem item : EcuDataItems.byMnemonic.values()) item.updatedAt = 0L;
                    ObdProt.setRealtimePidPriorityEnabled(true);
                    CommService.elm.reset();
                    vehicleSessionActive = true;
                }
                else if (state == CommService.STATE.OFFLINE || state == CommService.STATE.NONE)
                {
                    vehicleSessionActive = false;
                    updateSessionWakeLock();
                    ObdProt.setRealtimePidPriorityEnabled(false);
                }
                sampleTelemetry();
                reconcileForeground(true);
            }
            if (uiHandler != null) uiHandler.sendMessage(Message.obtain(message));
            return true;
        });
        switch (medium)
        {
            case BLUETOOTH: transport = new BtCommService(getApplicationContext(), events); break;
            case BLE: transport = new BleCommService(getApplicationContext(), events); break;
            case USB: transport = new UsbCommService(getApplicationContext(), events); break;
            case NETWORK: transport = new NetworkCommService(getApplicationContext(), events); break;
            default: throw new IllegalArgumentException("Unsupported transport");
        }
        transportMedium = medium;
        CommService.medium = medium;
        lost = false;
        vehicleSessionActive = true;
        reconcileForeground(true);
        LotoTGatewayService.start(this);
        return transport;
    }

    void disconnectVehicle()
    {
        transportGeneration++;
        if (transport != null)
        {
            transport.suppressConnectionLossNotification();
            transport.stop();
            transport = null;
        }
        vehicleSessionActive = false;
        lost = false;
        updateSessionWakeLock();
        ObdProt.setRealtimePidPriorityEnabled(false);
        sampleTelemetry();
    }

    private void updateSessionWakeLock()
    {
        if (!closing && !timedOut && hasVehicleSession())
        {
            if (sessionWakeLock == null)
            {
                android.os.PowerManager power = (android.os.PowerManager)getSystemService(POWER_SERVICE);
                if (power != null)
                {
                    sessionWakeLock = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,
                            getPackageName() + ":vehicle-session");
                    sessionWakeLock.setReferenceCounted(false);
                }
            }
            // Renewed by the service sampler; a failed sampler cannot hold it forever.
            long elapsed = android.os.SystemClock.elapsedRealtime();
            if (sessionWakeLock != null && (!sessionWakeLock.isHeld() || elapsed - wakeLockRenewedAt >= 5 * 60_000L))
            {
                sessionWakeLock.acquire(10 * 60_000L);
                wakeLockRenewedAt = elapsed;
            }
        }
        else if (sessionWakeLock != null && sessionWakeLock.isHeld()) sessionWakeLock.release();
    }

    @Override public void propertyChange(PropertyChangeEvent event)
    {
        if (ElmProt.PROP_STATUS.equals(event.getPropertyName())
                && event.getNewValue() == ElmProt.STAT.ECU_DETECTED)
            handler.post(() -> {
                if (hasVehicleSession() && CommService.elm.getService() == ObdProt.OBD_SVC_NONE)
                    CommService.elm.setService(ObdProt.OBD_SVC_DATA, true);
            });
    }

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
        CommService.elm.addPropertyChangeListener(this);
        handler.post(sampler);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId)
    {
        reconcileForeground(true);
        // A killed process has no vehicle transport to restore. Never claim a
        // sticky vehicle session, or start location collection from the background.
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public void onDestroy()
    {
        closing = true;
        handler.removeCallbacksAndMessages(null);
        CommService.elm.removePropertyChangeListener(this);
        uiHandler = null;
        disconnectVehicle();
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
        timedOut = false;
        handler.removeCallbacks(sampler);
        handler.post(sampler);
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
        updateSessionWakeLock();
        if (!active || transport != null) reconcileForeground(true);
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

    JSONObject getState() throws org.json.JSONException
    {
        JSONObject state = builtins == null ? new JSONObject() : builtins.getState();
        state.put("foreground", foregroundActive);
        state.put("service", foregroundActive ? "foreground" : "bound");
        state.put("vehicle_session", hasVehicleSession());
        state.put("session_status", getSessionStatus());
        state.put("timed_out", timedOut);
        return state;
    }

    @Override public void onBuiltinServicesStateChanged()
    {
        handler.post(() -> reconcileForeground(false));
        for (Listener listener : listeners) listener.onGatewayStateChanged();
    }

    void refreshForegroundService()
    {
        reconcileForeground(true);
    }

    private void reconcileForeground(boolean force)
    {
        if (closing || timedOut) return;
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
        foregroundActive = !timedOut;
    }

    @Override public void onTimeout(int startId, int fgsType)
    {
        timedOut = true;
        handler.removeCallbacks(sampler);
        disconnectVehicle();
        if (builtins != null) builtins.pauseForTimeout();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        foregroundActive = false;
        stopSelf();
        for (Listener listener : listeners) listener.onGatewayStateChanged();
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
            boolean connectedWork = hasVehicleSession();
            boolean qualifies = bluetoothGranted;
            if (transportMedium == CommService.MEDIUM.NETWORK)
                qualifies = ContextCompat.checkSelfPermission(this,
                        Manifest.permission.CHANGE_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED;
            if (transportMedium == CommService.MEDIUM.USB)
            {
                android.hardware.usb.UsbManager usb = (android.hardware.usb.UsbManager)getSystemService(USB_SERVICE);
                qualifies = false;
                if (usb != null)
                    for (android.hardware.usb.UsbDevice device : usb.getDeviceList().values())
                        if (usb.hasPermission(device)) { qualifies = true; break; }
            }
            if (connectedWork && qualifies) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;

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
            if (mqttEnabled && type == 0) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            if (type == 0) type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            try
            {
                startForeground(NOTIFICATION_ID, notification, type);
                return;
            }
            catch (SecurityException | IllegalStateException ex)
            {
                onTimeout(0, type);
                return;
            }
        }
        else startForeground(NOTIFICATION_ID, notification);
    }
}
