package com.fr3ts0n.ecu.gui.androbd;

import com.fr3ts0n.ecu.EcuDataPv;
import com.fr3ts0n.pvs.PvList;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class ObdItemAdapterSelectionTest
{
    @Test public void defaultSelectionPreservesNumericRegistryKeys()
    {
        PvList list = new PvList();
        EcuDataPv rpm = new EcuDataPv();
        list.put(Integer.valueOf(12), rpm);
        assertTrue(ObdItemAdapter.getMatchingItems(list, null).contains(rpm));
    }

    @Test public void stringPreferenceSelectsTheActualNumericKey()
    {
        PvList list = new PvList();
        EcuDataPv rpm = new EcuDataPv(), speed = new EcuDataPv();
        // Distinguish ProcessVar equality (otherwise both empty values compare equal).
        rpm.put(EcuDataPv.FID_MNEMONIC, "engine_speed");
        speed.put(EcuDataPv.FID_MNEMONIC, "vehicle_speed");
        list.put(Integer.valueOf(12), rpm); list.put(Integer.valueOf(13), speed);
        assertEquals(Collections.singleton(rpm), ObdItemAdapter.getMatchingItems(list, Collections.singleton("12")));
        assertTrue(ObdItemAdapter.getMatchingItems(list, Collections.emptySet()).isEmpty());
    }

    @Test public void stringRegistryKeysRemainSupported()
    {
        PvList list = new PvList(); EcuDataPv rpm = new EcuDataPv();
        list.put("rpm", rpm);
        assertEquals(Collections.singleton(rpm), ObdItemAdapter.getMatchingItems(list, Collections.singleton("rpm")));
        assertTrue(ObdItemAdapter.getMatchingItems(list, Collections.singleton("missing")).isEmpty());
    }
}
