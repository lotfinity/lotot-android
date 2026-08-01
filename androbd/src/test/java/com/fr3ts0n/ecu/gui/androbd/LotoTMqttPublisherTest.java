package com.fr3ts0n.ecu.gui.androbd;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LotoTMqttPublisherTest
{
    @Test
    public void deviceUidProducesDjangoSnapshotTopicPrefix()
    {
        assertEquals("demo-obd-001", LotoTMqttPublisher.normalizeDeviceUid(" demo-obd-001 "));
        assertEquals("android-bad-id", LotoTMqttPublisher.normalizeDeviceUid("android bad/id"));
        assertEquals("LotoT/devices/demo-obd-001/",
                LotoTMqttPublisher.topicPrefixForDevice("demo-obd-001"));
    }

    @Test
    public void unsupportedProtocolFallsBackToTcp()
    {
        assertEquals("tcp://", LotoTMqttPublisher.normalizeProtocol("http"));
        assertEquals("wss://", LotoTMqttPublisher.normalizeProtocol("WSS"));
    }

    @Test
    public void brokerUriCombinesNormalizedEndpoint()
    {
        LotoTMqttPublisher.Config config = new LotoTMqttPublisher.Config();
        config.protocol = "ssl";
        config.host = "mqtt.example.com";
        config.port = 8883;
        assertEquals("ssl://mqtt.example.com:8883",
                LotoTMqttPublisher.buildBrokerUri(config));
    }

    @Test
    public void brokerPortIsClampedToValidRange()
    {
        LotoTMqttPublisher.Config config = new LotoTMqttPublisher.Config();
        config.host = "broker";
        config.port = 99999;
        assertEquals("tcp://broker:65535", LotoTMqttPublisher.buildBrokerUri(config));
    }
    @Test
    public void retryBackoffStartsFastAndCapsAtFiveMinutes()
    {
        assertEquals(2_000L, LotoTMqttPublisher.retryDelayMs(1));
        assertEquals(4_000L, LotoTMqttPublisher.retryDelayMs(2));
        assertEquals(32_000L, LotoTMqttPublisher.retryDelayMs(5));
        assertEquals(300_000L, LotoTMqttPublisher.retryDelayMs(99));
    }

}
