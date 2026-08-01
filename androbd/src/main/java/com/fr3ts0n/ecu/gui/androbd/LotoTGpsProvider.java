/*
 * In-process GPS provider adapted from AndrOBD GpsProvider (GPLv3+).
 */
package com.fr3ts0n.ecu.gui.androbd;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;

final class LotoTGpsProvider implements LocationListener
{
    interface Listener { void onGpsStateChanged(); }

    private final Context context;
    private final LotoTBuiltinSignalStore store;
    private final Listener listener;
    private final LocationManager locationManager;
    private boolean enabled;
    private boolean registered;
    private String status = "disabled";
    private String error;
    private long lastUpdateAt;

    LotoTGpsProvider(Context context, LotoTBuiltinSignalStore store, Listener listener)
    {
        this.context = context.getApplicationContext();
        this.store = store;
        this.listener = listener;
        locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
        store.define("GPS_LATITUDE", "Latitude GPS", "°", -90, 90, "gps");
        store.define("GPS_LONGITUDE", "Longitude GPS", "°", -180, 180, "gps");
        store.define("GPS_ALTITUDE", "Altitude GPS", "m", -500, 9000, "gps");
        store.define("GPS_BEARING", "Cap GPS", "°", 0, 360, "gps");
        store.define("GPS_SPEED", "Vitesse GPS", "km/h", 0, 350, "gps");
    }

    void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
        if (enabled) start(); else stop();
    }

    private boolean hasPermission()
    {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    boolean needsPermission()
    {
        return enabled && !hasPermission();
    }

    @SuppressLint("MissingPermission")
    void start()
    {
        if (!enabled) return;
        if (locationManager == null)
        {
            status = "unavailable";
            error = "Service de localisation indisponible";
            notifyState();
            return;
        }
        if (needsPermission())
        {
            status = "permission";
            error = null;
            notifyState();
            return;
        }
        stopUpdates();
        boolean requested = false;
        try
        {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    500L, 0f, this);
            requested = true;
        }
        catch (RuntimeException ignored) { }
        try
        {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    1000L, 0f, this);
            requested = true;
        }
        catch (RuntimeException ignored) { }
        registered = requested;
        status = requested ? "waiting" : "unavailable";
        error = requested ? null : "Aucun fournisseur de localisation disponible";
        Location last = null;
        try { last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); }
        catch (RuntimeException ignored) { }
        if (last == null)
        {
            try { last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); }
            catch (RuntimeException ignored) { }
        }
        if (last != null) onLocationChanged(last); else notifyState();
    }

    void stop()
    {
        stopUpdates();
        status = "disabled";
        error = null;
        notifyState();
    }

    private void stopUpdates()
    {
        if (locationManager != null && registered)
        {
            try { locationManager.removeUpdates(this); }
            catch (RuntimeException ignored) { }
        }
        registered = false;
    }

    @Override
    public void onLocationChanged(Location location)
    {
        if (!enabled || location == null) return;
        store.update("GPS_LATITUDE", location.getLatitude());
        store.update("GPS_LONGITUDE", location.getLongitude());
        if (location.hasAltitude()) store.update("GPS_ALTITUDE", location.getAltitude());
        if (location.hasBearing()) store.update("GPS_BEARING", location.getBearing());
        if (location.hasSpeed()) store.update("GPS_SPEED", location.getSpeed() * 3.6d);
        lastUpdateAt = System.currentTimeMillis();
        status = "active";
        error = null;
        notifyState();
    }

    @Override public void onProviderEnabled(String provider) { if (enabled) start(); }
    @Override public void onProviderDisabled(String provider) { if (enabled) { status = "waiting"; notifyState(); } }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    JSONObject getState() throws org.json.JSONException
    {
        JSONObject state = new JSONObject();
        state.put("enabled", enabled);
        state.put("available", locationManager != null);
        state.put("permission_granted", hasPermission());
        state.put("status", status);
        state.put("last_update", lastUpdateAt);
        state.put("error", error == null ? JSONObject.NULL : error);
        return state;
    }

    private void notifyState()
    {
        if (listener != null) listener.onGpsStateChanged();
    }
}
