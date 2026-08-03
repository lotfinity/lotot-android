/*
 * LotoT Android integration layer for AndrOBD.
 *
 * This file is distributed under the GNU General Public License in the same
 * manner as the surrounding AndrOBD application.
 */
package com.fr3ts0n.ecu.gui.androbd;

import android.app.Activity;
import android.webkit.JavascriptInterface;

/**
 * Narrow, explicit JavaScript bridge exposed to the bundled LotoT React UI.
 * No arbitrary Android context or WebView object is exposed to JavaScript.
 */
final class LotoTWebBridge
{
    interface Host
    {
        void onLotoTReady();
        void startLotoTDemo();
        void requestLotoTBluetoothDevices(String medium);
        void connectLotoTBluetoothDevice(String address, String medium);
        void disconnectLotoTBluetooth();
        void setLotoTTheme(String theme);
        String getLotoTAppearanceSettings();
        void setLotoTAppearanceSettings(String json);
        void updateLotoTBuiltinConfig(String json);
        void requestLotoTLocationPermission();
        void publishLotoTMqttNow();
        void openLotoTNativeTools();
    }

    private final Activity activity;
    private final Host host;

    LotoTWebBridge(Activity activity, Host host)
    {
        this.activity = activity;
        this.host = host;
    }

    @JavascriptInterface
    public String getAppLanguage()
    {
        return SettingsActivity.getResolvedLanguage(activity);
    }

    @JavascriptInterface
    public void ready()
    {
        activity.runOnUiThread(host::onLotoTReady);
    }

    @JavascriptInterface
    public void startDemo()
    {
        activity.runOnUiThread(host::startLotoTDemo);
    }

    @JavascriptInterface
    public void scanBluetooth(String medium)
    {
        activity.runOnUiThread(() -> host.requestLotoTBluetoothDevices(medium));
    }

    @JavascriptInterface
    public void connectBluetooth(String address, String medium)
    {
        activity.runOnUiThread(() -> host.connectLotoTBluetoothDevice(address, medium));
    }

    @JavascriptInterface
    public void disconnectBluetooth()
    {
        activity.runOnUiThread(host::disconnectLotoTBluetooth);
    }

    @JavascriptInterface
    public void setTheme(String theme)
    {
        activity.runOnUiThread(() -> host.setLotoTTheme(theme));
    }

    @JavascriptInterface
    public String getAppearanceSettings()
    {
        return host.getLotoTAppearanceSettings();
    }

    @JavascriptInterface
    public void setAppearanceSettings(String json)
    {
        activity.runOnUiThread(() -> host.setLotoTAppearanceSettings(json));
    }

    @JavascriptInterface
    public void configureBuiltins(String json)
    {
        activity.runOnUiThread(() -> host.updateLotoTBuiltinConfig(json));
    }

    @JavascriptInterface
    public void requestLocationPermission()
    {
        activity.runOnUiThread(host::requestLotoTLocationPermission);
    }

    @JavascriptInterface
    public void publishMqttNow()
    {
        activity.runOnUiThread(host::publishLotoTMqttNow);
    }

    @JavascriptInterface
    public void openNativeTools()
    {
        activity.runOnUiThread(host::openLotoTNativeTools);
    }
}
