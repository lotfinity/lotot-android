package com.fr3ts0n.ecu.prot.obd;

import com.fr3ts0n.ecu.EcuCodeItem;
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

    @Test
    void oneShotFaultScanAndMode09IdentityDecodeDuringLiveDemo() throws Exception
    {
        ElmProt prot = new ElmProt();
        ElmProt.setDemoScenario("misfire");
        prot.setService(ObdProt.OBD_SVC_DATA);

        EcuDataItem rpmItem = EcuDataItems.byMnemonic.get("engine_speed");
        EcuDataItem vinItem = EcuDataItems.byMnemonic.get("vehicle_identification_number");
        EcuDataItem calibrationItem = EcuDataItems.byMnemonic.get("calibration_identifier");
        EcuDataItem ecuNameItem = EcuDataItems.byMnemonic.get("ecu_name");
        assertNotNull(rpmItem);
        assertNotNull(vinItem);
        assertNotNull(calibrationItem);
        assertNotNull(ecuNameItem);
        rpmItem.updatedAt = 0L;
        vinItem.updatedAt = 0L;
        calibrationItem.updatedAt = 0L;
        ecuNameItem.updatedAt = 0L;

        Thread thread = new Thread(prot, "demo-context-integration-test");
        thread.start();
        try
        {
            long deadline = System.currentTimeMillis() + 2500L;
            while (rpmItem.updatedAt <= 0L && System.currentTimeMillis() < deadline)
                Thread.sleep(25L);
            assertTrue(rpmItem.updatedAt > 0L, "Demo Mode 01 should be active before context probes");

            prot.requestVehicleIdentity();
            deadline = System.currentTimeMillis() + 1500L;
            while (vinItem.updatedAt <= 0L && System.currentTimeMillis() < deadline)
                Thread.sleep(25L);
            assertTrue(vinItem.updatedAt > 0L, "Mode 09 VIN should decode into the normal data registry");
            String vin = String.valueOf(vinItem.pv.get(EcuDataPv.FID_VALUE))
                    .replace(String.valueOf((char) 0), "").trim();
            assertEquals("WVWZZZAUZHL0T0T01", vin);
            assertTrue(calibrationItem.updatedAt > 0L, "Mode 09 calibration ID should decode");
            assertEquals("LOTOTI-DEMO-1500",
                    String.valueOf(calibrationItem.pv.get(EcuDataPv.FID_VALUE)).trim());
            assertTrue(ecuNameItem.updatedAt > 0L, "Mode 09 ECU name should decode");
            assertEquals("ECM-LOTOTI-DEMO",
                    String.valueOf(ecuNameItem.pv.get(EcuDataPv.FID_VALUE)).trim());

            prot.requestFaultCodesOnce(ObdProt.OBD_SVC_READ_CODES);
            boolean foundMisfire = false;
            for (Object value : ObdProt.tCodes.values())
            {
                if (!(value instanceof EcuCodeItem)) continue;
                Object code = ((EcuCodeItem) value).get(EcuCodeItem.FID_CODE);
                if ("P0301".equals(String.valueOf(code)))
                {
                    foundMisfire = true;
                    break;
                }
            }
            assertTrue(foundMisfire, "one-shot confirmed-code request should return P0301 in misfire Demo");
            assertEquals(ObdProt.OBD_SVC_DATA, prot.getService(),
                    "one-shot diagnostics must not replace the live Mode 01 service");
        }
        finally
        {
            ElmProt.runDemo = false;
            thread.join(1500L);
            ElmProt.setDemoScenario("healthy");
        }
    }

    private static String hex(byte[] data)
    {
        StringBuilder out = new StringBuilder();
        for (byte b : data) out.append(String.format("%02X", b & 0xFF));
        return out.toString();
    }
}
