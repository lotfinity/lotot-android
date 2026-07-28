package com.fr3ts0n.ecu.gui.androbd;

/** Pure timeout policy for detecting Bluetooth sockets that remain falsely connected. */
final class LotoTConnectionWatchdog
{
    private LotoTConnectionWatchdog() {}

    static boolean isTimedOut(long nowMs, long connectedAtMs, long lastRxMs,
                              long initialGraceMs, long silenceTimeoutMs)
    {
        if (connectedAtMs <= 0L || nowMs < connectedAtMs) return false;
        if (lastRxMs <= 0L) return nowMs - connectedAtMs > initialGraceMs;
        if (lastRxMs < connectedAtMs) return false;
        return nowMs - lastRxMs > silenceTimeoutMs;
    }
}
