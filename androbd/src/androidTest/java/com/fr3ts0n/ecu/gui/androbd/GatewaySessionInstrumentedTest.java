package com.fr3ts0n.ecu.gui.androbd;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GatewaySessionInstrumentedTest
{
    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion=35)
    public void android15DataSyncBudgetStopsForegroundService() throws Exception
    {
        // Only CI configures the shortened budget. Never alter a user's device settings.
        org.junit.Assume.assumeTrue(android.os.Build.FINGERPRINT.contains("generic")
                || android.os.Build.MODEL.contains("sdk_gphone"));
        Context context=ApplicationProvider.getApplicationContext();
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        AtomicReference<LotoTGatewayService> service=new AtomicReference<>();
        CountDownLatch bound=new CountDownLatch(1);
        ServiceConnection connection=new ServiceConnection()
        {
            public void onServiceConnected(ComponentName name,IBinder binder)
            { service.set(((LotoTGatewayService.LocalBinder)binder).getService()); bound.countDown(); }
            public void onServiceDisconnected(ComponentName name) { }
        };
        ActivityScenario<MainActivity> activity=ActivityScenario.launch(new Intent(context,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        context.bindService(new Intent(context,LotoTGatewayService.class),connection,Context.BIND_AUTO_CREATE);
        assertTrue(bound.await(10,TimeUnit.SECONDS));
        try
        {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                try
                {
                    service.get().applyConfig("{\"mqtt\":{\"enabled\":true,\"host\":\"127.0.0.1\",\"port\":9}}");
                    LotoTGatewayService.start(context);
                }
                catch(Exception ex) { throw new AssertionError(ex); }
            });
            assertTrue(service.get().getState().getBoolean("foreground"));
            activity.close();
            long deadline=SystemClock.elapsedRealtime()+20_000;
            while(!service.get().getState().getBoolean("timed_out") && SystemClock.elapsedRealtime()<deadline)
                SystemClock.sleep(200);
            assertTrue("Android delivered the dataSync budget timeout",service.get().getState().getBoolean("timed_out"));
            assertFalse(service.get().getState().getBoolean("foreground"));
        }
        finally
        {
            activity.close(); context.unbindService(connection);
            context.stopService(new Intent(context,LotoTGatewayService.class));
            android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
            SystemClock.sleep(1500);
        }
    }

    @Test public void activityRecreationAndFinishKeepTransportUntilExplicitDisconnect() throws Exception
    {
        Context context=ApplicationProvider.getApplicationContext();
        assertTrue(context.getPackageName().endsWith(".verification"));
        // Only this test application's settings are changed.
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        try (ServerSocket server=new ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")))
        {
            Thread elm=new Thread(() -> {
                try (Socket socket=server.accept())
                {
                    StringBuilder command=new StringBuilder(); int c;
                    while((c=socket.getInputStream().read())!=-1)
                    {
                        if(c!='\r') { command.append((char)c); continue; }
                        String request=command.toString().trim().replace(" ","").toUpperCase(java.util.Locale.ROOT);
                        command.setLength(0);
                        String reply=request.startsWith("AT") ? "OK\r>" : "NO DATA\r>";
                        socket.getOutputStream().write(reply.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                        socket.getOutputStream().flush();
                    }
                }
                catch(Exception ignored) { }
            },"test-elm-adapter");
            elm.setDaemon(true); elm.start();
            AtomicReference<LotoTGatewayService> service=new AtomicReference<>();
            CountDownLatch bound=new CountDownLatch(1);
            ServiceConnection connection=new ServiceConnection()
            {
                public void onServiceConnected(ComponentName name, IBinder binder)
                { service.set(((LotoTGatewayService.LocalBinder)binder).getService()); bound.countDown(); }
                public void onServiceDisconnected(ComponentName name) { }
            };
            Intent screen=new Intent(context,MainActivity.class).putExtra("lotot_ui_url","bundled")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityScenario<MainActivity> activity=ActivityScenario.launch(screen);
            context.bindService(new Intent(context,LotoTGatewayService.class),connection,Context.BIND_AUTO_CREATE);
            assertTrue(bound.await(10,TimeUnit.SECONDS));
            AtomicReference<CommService> transport=new AtomicReference<>();
            try
            {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    CommService created=service.get().createTransport(CommService.MEDIUM.NETWORK,
                            "127.0.0.1:"+server.getLocalPort(),"network","Test ELM",null);
                    transport.set(created);
                    ((NetworkCommService)created).connect("127.0.0.1",server.getLocalPort());
                });
                long deadline=SystemClock.elapsedRealtime()+10_000;
                while(transport.get().getState()!=CommService.STATE.CONNECTED && SystemClock.elapsedRealtime()<deadline)
                    SystemClock.sleep(100);
                assertEquals(CommService.STATE.CONNECTED,transport.get().getState());
                activity.recreate();
                assertSame(transport.get(),service.get().getTransport());
                activity.close();
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    try
                    {
                        service.get().applyConfig("{\"mqtt\":{\"enabled\":true,\"host\":\"127.0.0.1\",\"port\":9,\"interval_seconds\":1}}");
                        com.fr3ts0n.ecu.EcuDataItem rpm=com.fr3ts0n.ecu.EcuDataItems.byMnemonic.get("engine_speed");
                        rpm.pv.put(com.fr3ts0n.ecu.EcuDataPv.FID_VALUE,Double.valueOf(1234.0));
                        rpm.updatedAt=System.currentTimeMillis();
                    }
                    catch(Exception ex) { throw new AssertionError(ex); }
                });
                SystemClock.sleep(2500);
                assertTrue("Service sampled a decoded PID with no activity",service.get().getState()
                        .getJSONObject("mqtt").getInt("queue_depth")>0);
                assertTrue(service.get().hasVehicleSession());
                assertEquals(CommService.STATE.CONNECTED,transport.get().getState());
                // Simulate the Android dataSync timeout callback without changing device settings.
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> service.get().onTimeout(0,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC));
                assertFalse(service.get().hasVehicleSession());
                assertNull(service.get().getTransport());
                assertTrue(service.get().getState().getBoolean("timed_out"));
                assertFalse(service.get().getState().getBoolean("foreground"));
            }
            finally
            {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> service.get().disconnectVehicle());
                activity.close(); context.unbindService(connection);
                context.stopService(new Intent(context,LotoTGatewayService.class));
                android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
            }
        }
    }
}
