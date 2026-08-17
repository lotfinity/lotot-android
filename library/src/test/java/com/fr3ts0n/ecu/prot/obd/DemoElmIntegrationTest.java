package com.fr3ts0n.ecu.prot.obd;

import com.fr3ts0n.ecu.EcuDataItem;
import com.fr3ts0n.ecu.EcuDataItems;
import com.fr3ts0n.ecu.EcuDataPv;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DemoElmIntegrationTest
{
    @Test
    void curatedSupportAndRawPidsDecodeThroughElmProt()
    {
        ElmProt prot = new ElmProt();
        prot.setService(ObdProt.OBD_SVC_DATA);
        for (int start = 0; start <= 0x40; start += 0x20)
        {
            long mask = DemoVehicleModel.supportedMask(start);
            String telegram = String.format("41%02X%08X", start, mask);
            prot.handleTelegram(telegram.toCharArray());
            if ((mask & 1L) == 0L) break;
        }

        int first = prot.getNextSupportedPid();
        assertNotEquals(0, first, "support bitmap should populate pidSupported");

        long now = 1_000_000L;
        DemoVehicleModel model = new DemoVehicleModel(DemoVehicleModel.Scenario.HEALTHY, now);
        byte[] rpm = model.payloadForPid(0x0C, false, now + 5_000L);
        byte[] speed = model.payloadForPid(0x0D, false, now + 5_000L);
        prot.handleTelegram(("410C" + hex(rpm)).toCharArray());
        prot.handleTelegram(("410D" + hex(speed)).toCharArray());

        EcuDataItem rpmItem = EcuDataItems.byMnemonic.get("engine_speed");
        EcuDataItem speedItem = EcuDataItems.byMnemonic.get("vehicle_speed");
        assertNotNull(rpmItem);
        assertNotNull(speedItem);
        assertTrue(rpmItem.updatedAt > 0L);
        assertTrue(speedItem.updatedAt > 0L);
        assertTrue(((Number)rpmItem.pv.get(EcuDataPv.FID_VALUE)).doubleValue() > 700.0);
        assertEquals(0.0, ((Number)speedItem.pv.get(EcuDataPv.FID_VALUE)).doubleValue(), 1.0);
    }

    @Test
    void realDemoThreadPublishesDecodedMode01Values() throws Exception
    {
        ElmProt prot = new ElmProt();
        ElmProt.setDemoScenario("healthy");
        prot.setService(ObdProt.OBD_SVC_DATA);
        Thread thread = new Thread(prot, "demo-integration-test");
        thread.start();
        try
        {
            long deadline = System.currentTimeMillis() + 2500L;
            EcuDataItem rpmItem;
            EcuDataItem speedItem;
            do
            {
                rpmItem = EcuDataItems.byMnemonic.get("engine_speed");
                speedItem = EcuDataItems.byMnemonic.get("vehicle_speed");
                if (rpmItem != null && speedItem != null
                        && rpmItem.updatedAt > 0L && speedItem.updatedAt > 0L) break;
                Thread.sleep(50L);
            } while (System.currentTimeMillis() < deadline);

            assertNotNull(rpmItem);
            assertNotNull(speedItem);
            assertTrue(rpmItem.updatedAt > 0L, "RPM should be decoded by the actual Demo thread");
            assertTrue(speedItem.updatedAt > 0L, "speed should be decoded by the actual Demo thread");
            assertTrue(((Number)rpmItem.pv.get(EcuDataPv.FID_VALUE)).doubleValue() >= 700.0);
        }
        finally
        {
            ElmProt.runDemo = false;
            thread.join(1500L);
        }
    }

    private static String hex(byte[] data)
    {
        StringBuilder out = new StringBuilder();
        for (byte b : data) out.append(String.format("%02X", b & 0xFF));
        return out.toString();
    }
}
