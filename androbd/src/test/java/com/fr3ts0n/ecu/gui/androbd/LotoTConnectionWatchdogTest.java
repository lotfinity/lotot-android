package com.fr3ts0n.ecu.gui.androbd;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LotoTConnectionWatchdogTest
{
    @Test public void allowsInitialHandshakeGrace()
    {
        assertFalse(LotoTConnectionWatchdog.isTimedOut(11_000, 1_000, 0, 12_000, 3_500));
        assertTrue(LotoTConnectionWatchdog.isTimedOut(13_001, 1_000, 0, 12_000, 3_500));
    }

    @Test public void detectsSilenceAfterTrafficBegins()
    {
        assertFalse(LotoTConnectionWatchdog.isTimedOut(10_000, 1_000, 7_000, 12_000, 3_500));
        assertTrue(LotoTConnectionWatchdog.isTimedOut(10_501, 1_000, 7_000, 12_000, 3_500));
    }

    @Test public void ignoresInvalidClockState()
    {
        assertFalse(LotoTConnectionWatchdog.isTimedOut(1_000, 0, 0, 12_000, 3_500));
        assertFalse(LotoTConnectionWatchdog.isTimedOut(1_000, 2_000, 0, 12_000, 3_500));
    }
}
