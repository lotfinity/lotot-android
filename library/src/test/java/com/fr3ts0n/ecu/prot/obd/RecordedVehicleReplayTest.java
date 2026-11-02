package com.fr3ts0n.ecu.prot.obd;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class RecordedVehicleReplayTest {
    @Test void profilesContainOnlySelectedOriginalRowsAndExactFaultSets() {
        Object[][] cases = {
            {"replay_peugeot_expert_2014", 5, "P1351"},
            {"replay_chevrolet_captiva_2011", 3, "P0089"},
            {"replay_kia_sportage_2017", 4, "P2015"},
            {"replay_citroen_berlingo_2014", 3, "P2562"},
            {"replay_peugeot_2008_2022", 1, "P0562"},
            {"replay_healthy_unattributed", 0, ""}
        };
        for (Object[] row : cases) {
            RecordedVehicleReplay.Profile p = RecordedVehicleReplay.find((String)row[0]);
            assertNotNull(p);
            assertEquals(320, p.rows.length, p.id);
            assertEquals(row[1], p.codes.length, p.id);
            if (!((String)row[2]).isEmpty()) assertTrue(Arrays.asList(p.codes).contains(row[2]));
            for (double[] sample:p.rows) {
                assertEquals(11, sample.length);
                for(double v:sample)assertTrue(Double.isFinite(v));
            }
            assertArrayEquals(p.rows[0], p.frame(0));
            assertArrayEquals(p.rows[1], p.frame(RecordedVehicleReplay.FRAME_MS));
            assertArrayEquals(p.rows[0], p.frame(320*RecordedVehicleReplay.FRAME_MS));
        }
        assertEquals("", RecordedVehicleReplay.find("replay_healthy_unattributed").manufacturer);
    }
    @Test void replaysUseOnlySupportedMeasuredPidsAndNeverInventFreezeFrames() {
        long now=123400000L;
        DemoVehicleModel sim=new DemoVehicleModel(DemoVehicleModel.Scenario.REPLAY_KIA_SPORTAGE,now);
        assertEquals(4,sim.storedCodes(now).size());
        assertTrue(sim.pendingCodes(now).isEmpty());
        assertTrue(sim.permanentCodes(now).isEmpty());
        assertNull(sim.payloadForPid(0x2F,false,now)); // no published fuel tank level
        assertNull(sim.payloadForPid(0x1F,false,now)); // no published engine runtime
        assertNull(sim.payloadForPid(0x05,true,now)); // no recorded freeze frame
        assertNotNull(sim.payloadForPid(0x0C,false,now));
        assertNotNull(sim.payloadForPid(0x42,false,now));
        assertTrue(sim.supportedMaskForCurrent(0) != 0);
        sim.clearCodes(now+5_000);
        assertEquals(4,sim.storedCodes(now+10_000).size()); // historical dataset is immutable
    }
    @Test void actualElmDemoThreadDecodesRecordedVinCalibrationAndMultipleCodes() throws Exception {
        ElmProt prot=new ElmProt();
        ElmProt.setDemoScenario("replay_kia_sportage_2017");
        prot.setService(ObdProt.OBD_SVC_DATA);
        com.fr3ts0n.ecu.EcuDataItem vinItem=com.fr3ts0n.ecu.EcuDataItems.byMnemonic.get("vehicle_identification_number");
        com.fr3ts0n.ecu.EcuDataItem calItem=com.fr3ts0n.ecu.EcuDataItems.byMnemonic.get("calibration_identifier");
        vinItem.updatedAt=0;calItem.updatedAt=0;
        Thread worker=new Thread(prot,"recorded-dataset-integration");worker.start();
        try {
            long deadline=System.currentTimeMillis()+3000;
            while(System.currentTimeMillis()<deadline && prot.getNextSupportedPid()==0) Thread.sleep(30);
            prot.requestVehicleIdentity();
            deadline=System.currentTimeMillis()+2500;
            while(System.currentTimeMillis()<deadline && (vinItem.updatedAt==0 || calItem.updatedAt==0)) Thread.sleep(30);
            assertTrue(vinItem.updatedAt>0,"Synthetic Mode 09 VIN decoded");
            String vin=String.valueOf(vinItem.pv.get(com.fr3ts0n.ecu.EcuDataPv.FID_VALUE)).trim();
            assertTrue(vin.startsWith("L0T0T"),vin);
            assertEquals(17,vin.length());
            assertEquals("LOTOT-EMU-KIASPT",String.valueOf(calItem.pv.get(com.fr3ts0n.ecu.EcuDataPv.FID_VALUE)).trim());
            prot.requestFaultCodesOnce(ObdProt.OBD_SVC_READ_CODES);
            deadline=System.currentTimeMillis()+1500;
            while(System.currentTimeMillis()<deadline && ObdProt.tCodes.size()<4)Thread.sleep(30);
            assertEquals(4,ObdProt.tCodes.size(),"Kia dataset label set, not generic P0301");
        } finally {
            ElmProt.runDemo=false;worker.join(1500);
            ElmProt.setDemoScenario("healthy");
        }
    }
    @Test void scenarioSelectionPreservesExistingSyntheticDemo() {
        assertEquals(DemoVehicleModel.Scenario.HEALTHY,DemoVehicleModel.Scenario.fromId("healthy"));
        assertEquals(DemoVehicleModel.Scenario.REPLAY_PEUGEOT_2008,DemoVehicleModel.Scenario.fromId("replay_peugeot_2008_2022"));
        assertEquals(DemoVehicleModel.Scenario.HEALTHY,DemoVehicleModel.Scenario.fromId("bogus"));
    }
}
