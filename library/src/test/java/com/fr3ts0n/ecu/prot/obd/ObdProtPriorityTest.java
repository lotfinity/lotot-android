package com.fr3ts0n.ecu.prot.obd;

import com.fr3ts0n.ecu.ObdPid;

import org.junit.jupiter.api.Test;

import java.util.Vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ObdProtPriorityTest
{
    @Test
    void speedPreemptsLargeNormalRotationWhenDue()
    {
        long now = 10_000L;
        ObdPid normal = new ObdPid(0x05);
        ObdPid rpm = new ObdPid(ObdProt.PID_ENGINE_RPM);
        ObdPid speed = new ObdPid(ObdProt.PID_VEHICLE_SPEED);
        Vector<ObdPid> pids = new Vector<>();
        pids.add(normal);
        pids.add(rpm);
        pids.add(speed);

        ObdPid first = ObdProt.selectNextPid(pids, now, true);
        assertSame(speed, first);
        assertEquals(now + ObdProt.SPEED_POLL_INTERVAL_MS, speed.getNextRequest());

        ObdPid second = ObdProt.selectNextPid(pids, now + 1L, true);
        assertSame(rpm, second);
        assertEquals(now + 1L + ObdProt.RPM_POLL_INTERVAL_MS, rpm.getNextRequest());

        ObdPid third = ObdProt.selectNextPid(pids, now + 2L, true);
        assertSame(normal, third);

        // Even though the normal PID rotation is older, speed takes the next slot
        // as soon as its 100 ms deadline is reached.
        ObdPid fourth = ObdProt.selectNextPid(pids,
                now + ObdProt.SPEED_POLL_INTERVAL_MS, true);
        assertSame(speed, fourth);
    }

    @Test
    void responseDoesNotEraseRealtimeDeadline()
    {
        long requestedAt = 20_000L;
        ObdPid speed = new ObdPid(ObdProt.PID_VEHICLE_SPEED);
        speed.setNextRequest(requestedAt + ObdProt.SPEED_POLL_INTERVAL_MS);

        ObdProt.updatePidDeadlineAfterResponse(speed, requestedAt + 10L, 0L, true);

        assertEquals(requestedAt + ObdProt.SPEED_POLL_INTERVAL_MS,
                speed.getNextRequest());
    }
}
