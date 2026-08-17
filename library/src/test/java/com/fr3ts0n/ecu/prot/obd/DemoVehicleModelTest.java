package com.fr3ts0n.ecu.prot.obd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DemoVehicleModelTest
{
    @Test
    public void supportMapIsCuratedAndContinuesAcrossBlocks()
    {
        long first = DemoVehicleModel.supportedMask(0x00);
        long second = DemoVehicleModel.supportedMask(0x20);
        long third = DemoVehicleModel.supportedMask(0x40);
        assertTrue((first & 1L) != 0L);
        assertTrue((second & 1L) != 0L);
        assertEquals(0L, third & 1L);
        assertNotEquals(0L, first & 0x00100000L); // PID 0x0C RPM
        assertNotEquals(0L, first & 0x00080000L); // PID 0x0D speed
    }

    @Test
    public void healthyDriveProducesPlausibleCorrelatedValues()
    {
        long start = 1_000_000L;
        DemoVehicleModel model = new DemoVehicleModel(DemoVehicleModel.Scenario.HEALTHY, start);
        DemoVehicleModel.State idle = model.state(start + 5_000L);
        DemoVehicleModel.State cruise = model.state(start + 35_000L);
        assertTrue(idle.speedKph < 1.0);
        assertTrue(idle.rpm >= 700 && idle.rpm <= 1000);
        assertTrue(idle.voltageV >= 13.7 && idle.voltageV <= 14.5);
        assertTrue(cruise.speedKph >= 50 && cruise.speedKph <= 60);
        assertTrue(cruise.rpm > 1200 && cruise.rpm < 4000);
        assertTrue(cruise.mafGps > idle.mafGps);
        assertTrue(cruise.fuelRateLph > idle.fuelRateLph);
        assertTrue(cruise.coolantC > 70 && cruise.coolantC < 105);
    }

    @Test
    public void faultCodesClearAndRecurringFaultReturns()
    {
        long start = 2_000_000L;
        DemoVehicleModel model = new DemoVehicleModel(DemoVehicleModel.Scenario.MISFIRE, start);
        assertEquals("P0301", model.storedCodes(start).get(0));
        model.clearCodes(start + 1_000L);
        assertTrue(model.storedCodes(start + 5_000L).isEmpty());
        assertFalse(model.permanentCodes(start + 5_000L).isEmpty());
        assertEquals("P0301", model.storedCodes(start + 25_000L).get(0));
    }

    @Test
    public void weakChargingScenarioReportsLowVoltage()
    {
        long start = 3_000_000L;
        DemoVehicleModel model = new DemoVehicleModel(DemoVehicleModel.Scenario.WEAK_CHARGING, start);
        DemoVehicleModel.State state = model.state(start + 5_000L);
        assertTrue(state.voltageV < 12.5);
        assertEquals("P0562", model.storedCodes(start + 5_000L).get(0));
    }

    @Test
    public void dtcEncodingMatchesObdTwoByteFormat()
    {
        assertArrayEquals(new byte[] {0x03, 0x01}, DemoVehicleModel.encodeDtc("P0301"));
        assertArrayEquals(new byte[] {0x01, 0x71}, DemoVehicleModel.encodeDtc("P0171"));
        assertArrayEquals(new byte[] {0x04, 0x20}, DemoVehicleModel.encodeDtc("P0420"));
        assertArrayEquals(new byte[] {0x05, 0x62}, DemoVehicleModel.encodeDtc("P0562"));
    }
    @Test
    public void coldStartWarmsWithoutInventingFaults()
    {
        long start = 4_000_000L;
        DemoVehicleModel model = new DemoVehicleModel(DemoVehicleModel.Scenario.COLD_START, start);
        DemoVehicleModel.State cold = model.state(start + 2_000L);
        DemoVehicleModel.State warmer = model.state(start + 60_000L);
        assertTrue(cold.coolantC < 35.0);
        assertTrue(cold.oilC < 35.0);
        assertTrue(warmer.coolantC > cold.coolantC);
        assertTrue(warmer.oilC > cold.oilC);
        assertTrue(model.storedCodes(start + 60_000L).isEmpty());
        assertFalse(model.hasMil(start + 60_000L));
    }

    @Test
    public void overheatStartsInVisibleWarningRange()
    {
        long start = 5_000_000L;
        DemoVehicleModel model = new DemoVehicleModel(DemoVehicleModel.Scenario.OVERHEAT, start);
        assertTrue(model.state(start + 1_000L).coolantC > 105.0);
    }

    @Test
    void rawFramesDecodeThroughElmProt()
    {
        ElmProt prot = new ElmProt();
        prot.setService(ObdProt.OBD_SVC_DATA);
        long now = 1_000_000L;
        DemoVehicleModel model = new DemoVehicleModel(DemoVehicleModel.Scenario.HEALTHY, now);
        for (int start = 0; start <= 0xE0; start += 0x20)
        {
            long mask = DemoVehicleModel.supportedMask(start);
            prot.handleTelegram(String.format("41%02X%08X", start, mask).toCharArray());
            if ((mask & 1L) == 0L) break;
        }
        Integer pid = prot.getNextSupportedPid();
        assertTrue(pid != 0);
        byte[] payload = model.payloadForPid(pid, false, now + 12_000L);
        assertNotNull(payload);
        prot.handleTelegram(("41" + String.format("%02X", pid) + hex(payload)).toCharArray());
        boolean anyUpdated = com.fr3ts0n.ecu.EcuDataItems.byMnemonic.values().stream().anyMatch(i -> i.updatedAt > 0L);
        assertTrue(anyUpdated);
    }

    private static String hex(byte[] data)
    {
        StringBuilder b = new StringBuilder();
        for (byte value : data) b.append(String.format("%02X", value & 0xFF));
        return b.toString();
    }

}
