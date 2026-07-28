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
    public void openNativeTools()
    {
        activity.runOnUiThread(host::openLotoTNativeTools);
    }
}
