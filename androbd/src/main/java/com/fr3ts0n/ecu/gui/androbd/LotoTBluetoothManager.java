/*
 * LotoT Bluetooth discovery layer for AndrOBD.
 *
 * This file is distributed under the GNU General Public License in the same
 * manner as the surrounding AndrOBD application.
 */
package com.fr3ts0n.ecu.gui.androbd;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns Bluetooth discovery for the bundled LotoT React interface.
 * Connection itself remains in AndrOBD's existing communication services.
 */
final class LotoTBluetoothManager
{
    interface Listener
    {
        void onLotoTBluetoothChanged();
    }

    static final String MEDIUM_CLASSIC = "classic";
    static final String MEDIUM_BLE = "ble";

    private static final long BLE_SCAN_DURATION_MS = 10_000L;
    private static final long CLASSIC_SCAN_DURATION_MS = 14_000L;

    private final Activity activity;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, DeviceRecord> devices = new LinkedHashMap<>();

    private BluetoothAdapter adapter;
    private BluetoothLeScanner leScanner;
    private boolean classicReceiverRegistered;
    private boolean scanning;
    private String scanningMedium = MEDIUM_CLASSIC;

    LotoTBluetoothManager(Activity activity, Listener listener)
    {
        this.activity = activity;
        this.listener = listener;
        adapter = BluetoothAdapter.getDefaultAdapter();
    }

    BluetoothAdapter getAdapter()
    {
        if (adapter == null) adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter;
    }

    boolean isAvailable()
    {
        return getAdapter() != null;
    }

    @SuppressLint("MissingPermission")
    boolean isEnabled()
    {
        try
        {
            return getAdapter() != null && getAdapter().isEnabled();
        }
        catch (SecurityException ignored)
        {
            return false;
        }
    }

    boolean isScanning()
    {
        return scanning;
    }

    String getScanningMedium()
    {
        return scanningMedium;
    }

    String getName(String address)
    {
        if (address == null) return null;
        DeviceRecord record = devices.get(MEDIUM_CLASSIC + ":" + address);
        if (record == null) record = devices.get(MEDIUM_BLE + ":" + address);
        return record != null ? record.name : null;
    }

    JSONArray getDevices(String medium)
    {
        JSONArray result = new JSONArray();
        for (DeviceRecord record : devices.values())
        {
            if (medium != null && !medium.equals(record.medium)) continue;
            result.put(record.toJson());
        }
        return result;
    }

    @SuppressLint("MissingPermission")
    void startScan(String requestedMedium)
    {
        String medium = MEDIUM_BLE.equals(requestedMedium) ? MEDIUM_BLE : MEDIUM_CLASSIC;
        stopScan();
        scanningMedium = medium;
        removeMediumDevices(medium);

        BluetoothAdapter bt = getAdapter();
        if (bt == null || !bt.isEnabled())
        {
            setScanning(false);
            return;
        }

        for (BluetoothDevice device : bt.getBondedDevices())
        {
            if (supportsMedium(device, medium)) addDevice(device, medium, true, null);
        }

        if (MEDIUM_BLE.equals(medium))
        {
            leScanner = bt.getBluetoothLeScanner();
            if (leScanner == null)
            {
                setScanning(false);
                return;
            }
            setScanning(true);
            leScanner.startScan(leScanCallback);
            handler.postDelayed(this::stopScan, BLE_SCAN_DURATION_MS);
        }
        else
        {
            registerClassicReceiver();
            setScanning(true);
            if (!bt.startDiscovery())
            {
                setScanning(false);
            }
            handler.postDelayed(this::stopScan, CLASSIC_SCAN_DURATION_MS);
        }
    }

    @SuppressLint("MissingPermission")
    void stopScan()
    {
        handler.removeCallbacksAndMessages(null);
        BluetoothAdapter bt = getAdapter();
        if (bt != null)
        {
            try
            {
                if (bt.isDiscovering()) bt.cancelDiscovery();
                if (leScanner != null) leScanner.stopScan(leScanCallback);
            }
            catch (Exception ignored)
            {
                // Permission may be revoked or the scanner may already be stopped.
            }
        }
        leScanner = null;
        if (classicReceiverRegistered)
        {
            try
            {
                activity.unregisterReceiver(classicReceiver);
            }
            catch (IllegalArgumentException ignored)
            {
                // Receiver was already unregistered.
            }
            classicReceiverRegistered = false;
        }
        setScanning(false);
    }

    void destroy()
    {
        stopScan();
        devices.clear();
    }

    private void removeMediumDevices(String medium)
    {
        devices.entrySet().removeIf(entry -> medium.equals(entry.getValue().medium));
        notifyChanged();
    }

    @SuppressLint("MissingPermission")
    private void addDevice(BluetoothDevice device, String medium, boolean paired, Integer rssi)
    {
        if (device == null || device.getAddress() == null) return;
        String key = medium + ":" + device.getAddress();
        DeviceRecord existing = devices.get(key);
        String name = safeDeviceName(device);
        boolean isPaired = paired || device.getBondState() == BluetoothDevice.BOND_BONDED;
        if (existing == null)
        {
            devices.put(key, new DeviceRecord(name, device.getAddress(), medium, isPaired, rssi));
        }
        else
        {
            existing.name = name;
            existing.paired = existing.paired || isPaired;
            if (rssi != null) existing.rssi = rssi;
        }
        notifyChanged();
    }

    @SuppressLint("MissingPermission")
    private boolean supportsMedium(BluetoothDevice device, String medium)
    {
        try
        {
            int type = device.getType();
            if (MEDIUM_BLE.equals(medium))
            {
                return type == BluetoothDevice.DEVICE_TYPE_LE
                        || type == BluetoothDevice.DEVICE_TYPE_DUAL;
            }
            return type == BluetoothDevice.DEVICE_TYPE_CLASSIC
                    || type == BluetoothDevice.DEVICE_TYPE_DUAL
                    || type == BluetoothDevice.DEVICE_TYPE_UNKNOWN;
        }
        catch (SecurityException ignored)
        {
            return true;
        }
    }

    @SuppressLint("MissingPermission")
    private String safeDeviceName(BluetoothDevice device)
    {
        try
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            {
                String alias = device.getAlias();
                if (isUsableName(alias)) return alias.trim();
            }
            String name = device.getName();
            if (isUsableName(name)) return name.trim();
        }
        catch (SecurityException ignored)
        {
            // Permission state changed while scanning.
        }
        return "Appareil Bluetooth";
    }

    private boolean isUsableName(String value)
    {
        if (value == null || value.trim().isEmpty()) return false;
        String normalized = value.trim();
        return !"Missing permission".equalsIgnoreCase(normalized)
                && !"Unknown".equalsIgnoreCase(normalized);
    }

    private void setScanning(boolean value)
    {
        if (scanning == value) return;
        scanning = value;
        notifyChanged();
    }

    private void notifyChanged()
    {
        listener.onLotoTBluetoothChanged();
    }

    private void registerClassicReceiver()
    {
        if (classicReceiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        {
            activity.registerReceiver(classicReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }
        else
        {
            activity.registerReceiver(classicReceiver, filter);
        }
        classicReceiverRegistered = true;
    }

    private final BroadcastReceiver classicReceiver = new BroadcastReceiver()
    {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action))
            {
                BluetoothDevice device;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                {
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                }
                else
                {
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                }
                int rawRssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                Integer rssi = rawRssi == Short.MIN_VALUE ? null : rawRssi;
                addDevice(device, MEDIUM_CLASSIC, false, rssi);
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action))
            {
                setScanning(true);
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
            {
                setScanning(false);
            }
        }
    };

    private final ScanCallback leScanCallback = new ScanCallback()
    {
        @Override
        public void onScanResult(int callbackType, ScanResult result)
        {
            addDevice(result.getDevice(), MEDIUM_BLE, false, result.getRssi());
        }

        @Override
        public void onBatchScanResults(java.util.List<ScanResult> results)
        {
            for (ScanResult result : results)
            {
                addDevice(result.getDevice(), MEDIUM_BLE, false, result.getRssi());
            }
        }

        @Override
        public void onScanFailed(int errorCode)
        {
            setScanning(false);
        }
    };

    private static final class DeviceRecord
    {
        private String name;
        private final String address;
        private final String medium;
        private boolean paired;
        private Integer rssi;

        DeviceRecord(String name, String address, String medium, boolean paired, Integer rssi)
        {
            this.name = name;
            this.address = address;
            this.medium = medium;
            this.paired = paired;
            this.rssi = rssi;
        }

        JSONObject toJson()
        {
            JSONObject result = new JSONObject();
            try
            {
                result.put("name", name);
                result.put("address", address);
                result.put("medium", medium);
                result.put("paired", paired);
                if (rssi == null) result.put("rssi", JSONObject.NULL);
                else result.put("rssi", rssi);
            }
            catch (Exception ignored)
            {
                // JSONObject writes for primitive fields cannot realistically fail.
            }
            return result;
        }
    }
}
