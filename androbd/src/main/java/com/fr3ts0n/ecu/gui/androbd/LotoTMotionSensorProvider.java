/*
 * In-process accelerometer provider adapted from AndrOBD SensorProvider
 * (GPLv3+).
 */
package com.fr3ts0n.ecu.gui.androbd;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import org.json.JSONObject;

final class LotoTMotionSensorProvider implements SensorEventListener
{
    interface Listener { void onSensorStateChanged(); }

    private final Context context;
    private final LotoTBuiltinSignalStore store;
    private final Listener listener;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private boolean enabled;
    private boolean registered;
    private String status = "disabled";
    private long lastUpdateAt;

    LotoTMotionSensorProvider(Context context, LotoTBuiltinSignalStore store, Listener listener)
    {
        this.context = context.getApplicationContext();
        this.store = store;
        this.listener = listener;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        store.define("ACC_X", context.getString(R.string.lotot_acc_lateral), "m/s²", -20, 20, "motion");
        store.define("ACC_Y", context.getString(R.string.lotot_acc_longitudinal), "m/s²", -20, 20, "motion");
        store.define("ACC_Z", context.getString(R.string.lotot_acc_vertical), "m/s²", -20, 20, "motion");
    }

    void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
        if (enabled) start(); else stop();
    }

    void start()
    {
        if (!enabled) return;
        if (sensorManager == null || accelerometer == null)
        {
            status = "unavailable";
            notifyState();
            return;
        }
        if (!registered)
            registered = sensorManager.registerListener(this, accelerometer, 100_000);
        status = registered ? "waiting" : "unavailable";
        notifyState();
    }

    void stop()
    {
        if (sensorManager != null && registered) sensorManager.unregisterListener(this);
        registered = false;
        status = "disabled";
        notifyState();
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if (!enabled || event == null || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER
                || event.values.length < 3) return;
        store.update("ACC_X", event.values[0]);
        store.update("ACC_Y", event.values[1]);
        store.update("ACC_Z", event.values[2]);
        lastUpdateAt = System.currentTimeMillis();
        if (!"active".equals(status))
        {
            status = "active";
            notifyState();
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    JSONObject getState() throws org.json.JSONException
    {
        JSONObject state = new JSONObject();
        state.put("enabled", enabled);
        state.put("available", accelerometer != null);
        state.put("status", status);
        state.put("last_update", lastUpdateAt);
        state.put("error", accelerometer == null
                ? context.getString(R.string.lotot_acc_unavailable) : JSONObject.NULL);
        return state;
    }

    private void notifyState()
    {
        if (listener != null) listener.onSensorStateChanged();
    }
}
