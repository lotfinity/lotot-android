package com.fr3ts0n.ecu.gui.androbd;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs only against the isolated .verification application, never driver data. */
@RunWith(AndroidJUnit4.class)
public class GatewayReliabilityInstrumentedTest
{
    private Context context;
    @Before public void prepare()
    {
        context = ApplicationProvider.getApplicationContext();
        assertTrue("Use the isolated verification build", context.getPackageName().endsWith(".verification"));
        context.deleteDatabase("lotot_gateway.db");
    }
    @After public void cleanup() { context.deleteDatabase("lotot_gateway.db"); }

    @Test public void queuePartitionsSurviveRestartAndClockRollback()
    {
        LotoTGatewayQueue q = new LotoTGatewayQueue(context);
        q.enqueue("A", "a/snapshot", "{}", 1, false, 200);
        q.enqueue("B", "b/snapshot", "{}", 1, false, 100);
        q.enqueue("A", "a/snapshot", "{}", 1, false, 50);
        q.close(); q = new LotoTGatewayQueue(context);
        assertEquals(3, q.count());
        assertEquals(2, q.count("A"));
        assertEquals(1, q.peek("B", 10).size());
        assertEquals(200, q.peek("A", 10).get(0).createdAt);
        q.delete(q.peek("A", 10).get(0).id);
        assertEquals(1, q.count("A"));
        assertEquals(1, q.count("B"));
        q.close();
    }

    @Test public void migrationPreservesButQuarantinesUnknownDestination()
    {
        SQLiteDatabase old = context.openOrCreateDatabase("lotot_gateway.db", 0, null);
        old.execSQL("CREATE TABLE telemetry_queue (id INTEGER PRIMARY KEY AUTOINCREMENT,topic TEXT NOT NULL,payload TEXT NOT NULL,qos INTEGER NOT NULL,retained INTEGER NOT NULL,created_at INTEGER NOT NULL)");
        old.execSQL("INSERT INTO telemetry_queue VALUES(1,'old/snapshot','{}',1,0,123)");
        old.setVersion(1); old.close();
        LotoTGatewayQueue q = new LotoTGatewayQueue(context);
        assertEquals(1, q.count());
        assertEquals(0, q.count("new-destination"));
        assertEquals(1, q.peek("", 10).size());
        q.close();
    }

    @Test public void overflowCountsLossDurably()
    {
        LotoTGatewayQueue q = new LotoTGatewayQueue(context);
        for (int i=0; i<LotoTGatewayQueue.MAX_ROWS+3; i++)
            q.enqueue("A", "snapshot", "{}", 1, false, i+1);
        assertEquals(LotoTGatewayQueue.MAX_ROWS, q.count());
        assertEquals(3, q.droppedCount());
        assertEquals(4, q.oldestCapturedAt());
        q.close(); q = new LotoTGatewayQueue(context);
        assertEquals(3, q.droppedCount()); q.close();
    }

    private JSONArray sample(long now) throws Exception
    {
        return new JSONArray().put(new JSONObject().put("mnemonic","engine_speed")
                .put("value",900).put("unit","rpm").put("source","obd").put("updated_at",now));
    }

    @Test public void withheldPubackDoesNotStopCaptureOrRetry() throws Exception
    {
        try (TestBroker broker = new TestBroker(false))
        {
            LotoTMqttPublisher publisher = new LotoTMqttPublisher(context, () -> {});
            try
            {
                LotoTMqttPublisher.Config c = new LotoTMqttPublisher.Config();
                c.enabled=true; c.host="127.0.0.1"; c.port=broker.port();
                c.intervalSeconds=1; c.deviceUid="test-a";
                publisher.setConfig(c);
                for (int i=0; i<10; i++)
                {
                    publisher.updateSnapshot(sample(System.currentTimeMillis()));
                    SystemClock.sleep(1100);
                }
                JSONObject state=publisher.getState();
                assertTrue("Capture continued during blocked PUBACK", state.getInt("queue_depth") >= 5);
                assertTrue("Bounded publish wait reached retry policy", state.getInt("retry_count") > 0);
            }
            finally { publisher.stop(); SystemClock.sleep(1500); }
        }
    }

    @Test public void acknowledgedReplayDrainsAndDestinationSwitchHoldsHistory() throws Exception
    {
        LotoTMqttPublisher.Config c = new LotoTMqttPublisher.Config();
        c.host="127.0.0.1"; c.deviceUid="test-a"; c.clientId="queue-test";
        try (TestBroker broker = new TestBroker(true))
        {
            c.port=broker.port();
            String destination=LotoTTelemetrySnapshot.destination(c);
            LotoTGatewayQueue q = new LotoTGatewayQueue(context);
            String payload=new JSONObject().put("readings",new JSONObject().put("engine_speed",900)).toString();
            q.enqueue("other", "other/snapshot", payload, 1, false, 1);
            q.enqueue(destination, "test/snapshot", payload, 1, false, 2);
            q.close();
            LotoTMqttPublisher publisher = new LotoTMqttPublisher(context, () -> {});
            try
            {
                c.enabled=true; publisher.setConfig(c);
                long end=SystemClock.elapsedRealtime()+10_000;
                while (publisher.getState().getInt("queue_active")>0 && SystemClock.elapsedRealtime()<end)
                    SystemClock.sleep(100);
                JSONObject state=publisher.getState();
                assertEquals(0,state.getInt("queue_active"));
                assertEquals(1,state.getInt("queue_held"));
                assertTrue(broker.publishes>0);
            }
            finally { publisher.stop(); SystemClock.sleep(1500); }
        }
    }

    /** Minimal loopback MQTT 3.1.1 broker: ACKs CONNECT, optionally ACKs QoS1 PUBLISH. */
    private static final class TestBroker implements AutoCloseable
    {
        final ServerSocket server;
        final AtomicBoolean running=new AtomicBoolean(true);
        volatile Socket socket;
        volatile int publishes;
        final Thread thread;
        TestBroker(boolean ack) throws Exception
        {
            server=new ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"));
            thread=new Thread(() -> {
                while(running.get()) try
                {
                    socket=server.accept();
                    InputStream in=socket.getInputStream();
                    while(running.get())
                    {
                        int header=in.read(); if(header<0) break;
                        int length=0,multiplier=1,digit;
                        do { digit=in.read(); if(digit<0) throw new java.io.EOFException(); length+=(digit&127)*multiplier; multiplier*=128; } while((digit&128)!=0);
                        byte[] body=new byte[length]; int offset=0;
                        while(offset<length) { int n=in.read(body,offset,length-offset); if(n<0) throw new java.io.EOFException(); offset+=n; }
                        if((header>>4)==1) socket.getOutputStream().write(new byte[]{0x20,2,0,0});
                        if((header>>4)==3)
                        {
                            publishes++;
                            if(ack && ((header>>1)&3)==1)
                            {
                                int topicLength=((body[0]&255)<<8)|(body[1]&255);
                                socket.getOutputStream().write(new byte[]{0x40,2,body[2+topicLength],body[3+topicLength]});
                            }
                        }
                        if((header>>4)==12) socket.getOutputStream().write(new byte[]{(byte)0xd0,0});
                        socket.getOutputStream().flush();
                    }
                }
                catch(Exception ignored) { }
            },"test-mqtt-broker");
            thread.setDaemon(true); thread.start();
        }
        int port() { return server.getLocalPort(); }
        public void close() throws Exception
        { running.set(false); server.close(); if(socket!=null) socket.close(); thread.join(2000); }
    }
}
