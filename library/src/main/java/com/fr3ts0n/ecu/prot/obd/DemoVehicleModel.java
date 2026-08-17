/*
 * LoToTi stateful OBD-II demo vehicle model.
 *
 * This file is distributed under the GNU General Public License in the same
 * manner as the surrounding AndrOBD library.
 */
package com.fr3ts0n.ecu.prot.obd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, stateful vehicle simulation used by ElmProt demo mode.
 *
 * The model produces physical state first; ElmProt converts the resulting
 * values into raw Mode 01/02 OBD replies and sends them through AndrOBD's
 * normal decoder. This keeps Demo on the same pipeline as a real adapter.
 */
final class DemoVehicleModel
{
    enum Scenario
    {
        HEALTHY("healthy", "Healthy warm drive"),
        COLD_START("cold_start", "Cold start / warm-up"),
        MISFIRE("misfire", "Cylinder 1 misfire"),
        LEAN("lean", "Lean mixture"),
        CATALYST("catalyst", "Catalyst efficiency"),
        OVERHEAT("overheat", "Engine overheating"),
        WEAK_CHARGING("weak_charging", "Weak battery / alternator");

        final String id;
        final String label;

        Scenario(String id, String label)
        {
            this.id = id;
            this.label = label;
        }

        static Scenario fromId(String value)
        {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
            for (Scenario scenario : values())
            {
                if (scenario.id.equals(normalized)) return scenario;
            }
            return HEALTHY;
        }
    }

    static final int[] SUPPORTED_PIDS = {
            0x01, 0x04, 0x05, 0x06, 0x07,
            0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
            0x10, 0x11, 0x1F,
            0x21, 0x2F, 0x30, 0x31, 0x33,
            0x42, 0x46, 0x47, 0x49, 0x4A, 0x4C,
            0x5C, 0x5E
    };

    static final class State
    {
        double speedKph;
        double rpm;
        double loadPct;
        double coolantC;
        double stft1Pct;
        double ltft1Pct;
        double mapKpa;
        double timingAdvanceDeg;
        double intakeC;
        double mafGps;
        double throttlePct;
        double fuelPct;
        double baroKpa;
        double voltageV;
        double ambientC;
        double pedalPct;
        double oilC;
        double fuelRateLph;
        double distanceKm;
        double distanceWithMilKm;
        double distanceSinceClearKm;
        int runtimeSec;
        int warmupsSinceClear;
        int gear;

        State copy()
        {
            State result = new State();
            result.speedKph = speedKph;
            result.rpm = rpm;
            result.loadPct = loadPct;
            result.coolantC = coolantC;
            result.stft1Pct = stft1Pct;
            result.ltft1Pct = ltft1Pct;
            result.mapKpa = mapKpa;
            result.timingAdvanceDeg = timingAdvanceDeg;
            result.intakeC = intakeC;
            result.mafGps = mafGps;
            result.throttlePct = throttlePct;
            result.fuelPct = fuelPct;
            result.baroKpa = baroKpa;
            result.voltageV = voltageV;
            result.ambientC = ambientC;
            result.pedalPct = pedalPct;
            result.oilC = oilC;
            result.fuelRateLph = fuelRateLph;
            result.distanceKm = distanceKm;
            result.distanceWithMilKm = distanceWithMilKm;
            result.distanceSinceClearKm = distanceSinceClearKm;
            result.runtimeSec = runtimeSec;
            result.warmupsSinceClear = warmupsSinceClear;
            result.gear = gear;
            return result;
        }
    }

    private Scenario scenario;
    private long startedAtMs;
    private long lastUpdateMs;
    private long codesClearedAtMs = -1L;
    private double distanceAtClearKm;
    private double distanceKm;
    private double distanceWithMilKm;
    private double fuelPct;
    private double coolantC;
    private double oilC;
    private double previousSpeedKph;
    private State current = new State();
    private State freezeFrame = new State();

    DemoVehicleModel(Scenario scenario, long nowMs)
    {
        reset(scenario, nowMs);
    }

    synchronized void reset(Scenario newScenario, long nowMs)
    {
        scenario = newScenario == null ? Scenario.HEALTHY : newScenario;
        startedAtMs = nowMs;
        lastUpdateMs = nowMs;
        codesClearedAtMs = -1L;
        distanceAtClearKm = 0.0;
        distanceKm = 12482.4;
        distanceWithMilKm = scenario == Scenario.HEALTHY ? 0.0 : 18.0;
        fuelPct = 68.0;
        coolantC = scenario == Scenario.OVERHEAT ? 112.0 : scenario == Scenario.COLD_START ? 24.0 : 86.0;
        oilC = scenario == Scenario.OVERHEAT ? 104.0 : scenario == Scenario.COLD_START ? 22.0 : 78.0;
        previousSpeedKph = 0.0;
        current = calculateState(nowMs, false);
        freezeFrame = makeFreezeFrame(scenario);
    }

    synchronized Scenario getScenario()
    {
        return scenario;
    }

    synchronized State state(long nowMs)
    {
        update(nowMs);
        return current.copy();
    }

    synchronized State freezeFrame()
    {
        return freezeFrame.copy();
    }

    synchronized boolean hasMil(long nowMs)
    {
        return !storedCodes(nowMs).isEmpty();
    }

    synchronized void clearCodes(long nowMs)
    {
        update(nowMs);
        codesClearedAtMs = nowMs;
        distanceAtClearKm = distanceKm;
        distanceWithMilKm = 0.0;
    }

    synchronized List<String> storedCodes(long nowMs)
    {
        String code = primaryDtc(scenario);
        if (code == null) return Collections.emptyList();
        if (codesClearedAtMs >= 0L && nowMs - codesClearedAtMs < 20_000L)
            return Collections.emptyList();
        return Collections.singletonList(code);
    }

    synchronized List<String> pendingCodes(long nowMs)
    {
        String code = primaryDtc(scenario);
        if (code == null) return Collections.emptyList();
        if (codesClearedAtMs >= 0L && nowMs - codesClearedAtMs < 12_000L)
            return Collections.emptyList();
        return Collections.singletonList(code);
    }

    synchronized List<String> permanentCodes(long nowMs)
    {
        if (!isEmissionsFault(scenario)) return Collections.emptyList();
        if (codesClearedAtMs >= 0L && nowMs - codesClearedAtMs > 75_000L)
            return Collections.emptyList();
        return Collections.singletonList(primaryDtc(scenario));
    }

    synchronized byte[] payloadForPid(int pid, boolean frozen, long nowMs)
    {
        State s;
        if (frozen)
        {
            s = freezeFrame;
        }
        else
        {
            update(nowMs);
            s = current;
        }

        switch (pid)
        {
            case 0x01:
            {
                int dtcCount = frozen ? (scenario == Scenario.HEALTHY ? 0 : 1) : storedCodes(nowMs).size();
                int a = (dtcCount > 0 ? 0x80 : 0x00) | Math.min(0x7F, dtcCount);
                // Common spark-ignition readiness pattern. Unsupported/complete state
                // is intentionally stable; it is not a claim about a real vehicle.
                return bytes(a, 0x07, 0x85, 0x00);
            }
            case 0x04: return bytes(percent(s.loadPct));
            case 0x05: return bytes(temp(s.coolantC));
            case 0x06: return bytes(percent7Relative(s.stft1Pct));
            case 0x07: return bytes(percent7Relative(s.ltft1Pct));
            case 0x0B: return bytes((int)Math.round(clamp(s.mapKpa, 0, 255)));
            case 0x0C: return u16((int)Math.round(clamp(s.rpm * 4.0, 0, 65535)));
            case 0x0D: return bytes((int)Math.round(clamp(s.speedKph, 0, 255)));
            case 0x0E: return bytes((int)Math.round(clamp(s.timingAdvanceDeg * 2.0 + 128.0, 0, 255)));
            case 0x0F: return bytes(temp(s.intakeC));
            case 0x10: return u16((int)Math.round(clamp(s.mafGps * 100.0, 0, 65535)));
            case 0x11: return bytes(percent(s.throttlePct));
            case 0x1F: return u16(Math.min(65535, Math.max(0, s.runtimeSec)));
            case 0x21: return u16((int)Math.round(clamp(s.distanceWithMilKm, 0, 65535)));
            case 0x2F: return bytes(percent(s.fuelPct));
            case 0x30: return bytes(Math.max(0, Math.min(255, s.warmupsSinceClear)));
            case 0x31: return u16((int)Math.round(clamp(s.distanceSinceClearKm, 0, 65535)));
            case 0x33: return bytes((int)Math.round(clamp(s.baroKpa, 0, 255)));
            case 0x42: return u16((int)Math.round(clamp(s.voltageV * 1000.0, 0, 65535)));
            case 0x46: return bytes(temp(s.ambientC));
            case 0x47: return bytes(percent(s.throttlePct));
            case 0x49:
            case 0x4A: return bytes(percent(s.pedalPct));
            case 0x4C: return bytes(percent(s.throttlePct));
            case 0x5C: return bytes(temp(s.oilC));
            case 0x5E: return u16((int)Math.round(clamp(s.fuelRateLph * 20.0, 0, 65535)));
            default: return null;
        }
    }

    static long supportedMask(int start)
    {
        long mask = 0L;
        for (int pid : SUPPORTED_PIDS)
        {
            if (pid > start && pid < start + 0x20)
            {
                int bit = pid - start - 1;
                mask |= (0x80000000L >>> bit);
            }
        }
        for (int pid : SUPPORTED_PIDS)
        {
            if (pid >= start + 0x20)
            {
                mask |= 1L; // More PID blocks follow.
                break;
            }
        }
        return mask & 0xFFFFFFFFL;
    }

    static String primaryDtc(Scenario scenario)
    {
        if (scenario == null) return null;
        switch (scenario)
        {
            case MISFIRE: return "P0301";
            case LEAN: return "P0171";
            case CATALYST: return "P0420";
            case OVERHEAT: return "P0217";
            case WEAK_CHARGING: return "P0562";
            case HEALTHY:
            default: return null;
        }
    }

    static boolean isEmissionsFault(Scenario scenario)
    {
        return scenario == Scenario.MISFIRE
                || scenario == Scenario.LEAN
                || scenario == Scenario.CATALYST;
    }

    static byte[] encodeDtc(String code)
    {
        if (code == null || !code.matches("[PBCU][0-3][0-9A-Fa-f]{3}")) return new byte[] {0, 0};
        int family;
        switch (Character.toUpperCase(code.charAt(0)))
        {
            case 'C': family = 1; break;
            case 'B': family = 2; break;
            case 'U': family = 3; break;
            case 'P':
            default: family = 0; break;
        }
        int classBits = Character.digit(code.charAt(1), 16) & 0x03;
        int a = (family << 6) | (classBits << 4) | Character.digit(code.charAt(2), 16);
        int b = (Character.digit(code.charAt(3), 16) << 4) | Character.digit(code.charAt(4), 16);
        return bytes(a, b);
    }

    static String scenarioIdsCsv()
    {
        List<String> ids = new ArrayList<>();
        for (Scenario s : Scenario.values()) ids.add(s.id);
        return String.join(",", ids);
    }

    private void update(long nowMs)
    {
        if (nowMs <= lastUpdateMs) return;
        current = calculateState(nowMs, true);
        lastUpdateMs = nowMs;
    }

    private State calculateState(long nowMs, boolean integrate)
    {
        double dt = Math.max(0.0, Math.min(1.0, (nowMs - lastUpdateMs) / 1000.0));
        double elapsed = Math.max(0.0, (nowMs - startedAtMs) / 1000.0);
        double phase = elapsed % 120.0;

        double speed = driveSpeed(phase);
        double accelKphPerSec = dt > 0.0001 ? (speed - previousSpeedKph) / dt : 0.0;
        int gear = gearFor(speed);
        double throttle = driveThrottle(phase, speed, accelKphPerSec);

        double gearFactor;
        switch (gear)
        {
            case 1: gearFactor = 126.0; break;
            case 2: gearFactor = 73.0; break;
            case 3: gearFactor = 50.0; break;
            case 4: gearFactor = 37.0; break;
            case 5: gearFactor = 29.0; break;
            case 6: gearFactor = 23.5; break;
            default: gearFactor = 0.0; break;
        }

        double rpm = speed < 1.0 ? 820.0 : Math.max(900.0, speed * gearFactor);
        if (accelKphPerSec > 1.0) rpm += Math.min(350.0, accelKphPerSec * 22.0);
        double roughness = 0.0;
        if (scenario == Scenario.MISFIRE)
        {
            roughness = Math.sin(elapsed * 8.7) * (speed < 8.0 ? 115.0 : 48.0);
            rpm += roughness;
        }

        double load = clamp(15.0 + throttle * 0.93 + Math.max(0.0, accelKphPerSec) * 1.7, 12.0, 92.0);
        if (scenario == Scenario.MISFIRE) load = clamp(load + 5.0 + Math.abs(roughness) * 0.025, 15.0, 95.0);
        double ambient = 24.0 + Math.sin(elapsed / 43.0) * 0.8;
        double intake = ambient + 4.5 + load * 0.055;
        double map = clamp(27.0 + load * 0.75, 22.0, 99.0);
        double maf = Math.max(2.2, 1.2 + rpm * (load / 100.0) * 0.012);
        if (scenario == Scenario.MISFIRE) maf *= 0.97;
        double fuelRate = Math.max(0.7, maf * 0.33);

        double stft = Math.sin(elapsed * 0.73) * 1.6;
        double ltft = 2.1 + Math.sin(elapsed * 0.08) * 0.4;
        if (scenario == Scenario.LEAN)
        {
            stft += 18.0 + throttle * 0.05;
            ltft = 15.5;
        }
        else if (scenario == Scenario.MISFIRE)
        {
            stft += 4.5 + Math.sin(elapsed * 2.4) * 2.0;
            ltft = 3.8;
        }

        if (integrate && dt > 0.0)
        {
            double coolantTarget = scenario == Scenario.OVERHEAT ? 127.0 : 92.0 + load * 0.015;
            double coolantTau = scenario == Scenario.OVERHEAT ? 38.0 : 85.0;
            coolantC += (coolantTarget - coolantC) * (dt / coolantTau);
            double oilTarget = scenario == Scenario.OVERHEAT ? 116.0 : 95.0;
            oilC += (oilTarget - oilC) * (dt / 150.0);
            distanceKm += speed * dt / 3600.0;
            if (!storedCodes(nowMs).isEmpty()) distanceWithMilKm += speed * dt / 3600.0;
            fuelPct = Math.max(3.0, fuelPct - (fuelRate * dt / 3600.0 / 50.0 * 100.0));
            previousSpeedKph = speed;
        }

        double voltage = 14.18 - load * 0.0022 + Math.sin(elapsed * 0.41) * 0.045;
        if (scenario == Scenario.WEAK_CHARGING)
        {
            voltage = speed < 5.0 ? 11.65 + Math.sin(elapsed) * 0.08
                    : 12.15 + Math.sin(elapsed * 0.5) * 0.12;
        }

        double timing = speed < 1.0 ? 10.5 : 30.0 - throttle * 0.20;
        if (scenario == Scenario.MISFIRE) timing -= 3.5;

        State s = new State();
        s.speedKph = clamp(speed, 0, 140);
        s.rpm = clamp(rpm, 650, 5200);
        s.loadPct = load;
        s.coolantC = coolantC;
        s.stft1Pct = clamp(stft, -35, 35);
        s.ltft1Pct = clamp(ltft, -35, 35);
        s.mapKpa = map;
        s.timingAdvanceDeg = clamp(timing, -10, 45);
        s.intakeC = intake;
        s.mafGps = maf;
        s.throttlePct = clamp(throttle, 1.5, 78);
        s.fuelPct = fuelPct;
        s.baroKpa = 101.0;
        s.voltageV = voltage;
        s.ambientC = ambient;
        s.pedalPct = clamp(Math.max(0.0, throttle - 2.5) * 0.92, 0, 75);
        s.oilC = oilC;
        s.fuelRateLph = fuelRate;
        s.distanceKm = distanceKm;
        s.distanceWithMilKm = distanceWithMilKm;
        s.distanceSinceClearKm = codesClearedAtMs < 0L ? 184.0 : Math.max(0.0, distanceKm - distanceAtClearKm);
        s.runtimeSec = Math.min(65535, (int)Math.round(elapsed + 420));
        s.warmupsSinceClear = codesClearedAtMs < 0L ? 18 : 0;
        s.gear = gear;
        return s;
    }

    private static State makeFreezeFrame(Scenario scenario)
    {
        State s = new State();
        s.fuelPct = 68.0;
        s.baroKpa = 101.0;
        s.ambientC = 24.0;
        s.distanceKm = 12461.0;
        s.distanceWithMilKm = scenario == Scenario.HEALTHY ? 0 : 2;
        s.distanceSinceClearKm = 162;
        s.runtimeSec = 612;
        s.warmupsSinceClear = 17;
        s.voltageV = 14.05;
        s.oilC = 86.0;
        s.intakeC = 31.0;
        s.ltft1Pct = 2.5;
        s.stft1Pct = 1.8;
        s.gear = 3;

        switch (scenario)
        {
            case MISFIRE:
                s.speedKph = 32; s.rpm = 1680; s.loadPct = 47; s.coolantC = 88;
                s.stft1Pct = 7.0; s.ltft1Pct = 3.8; s.mapKpa = 61; s.timingAdvanceDeg = 13;
                s.mafGps = 9.6; s.throttlePct = 18; s.pedalPct = 15; s.fuelRateLph = 3.4;
                break;
            case LEAN:
                s.speedKph = 82; s.rpm = 2440; s.loadPct = 43; s.coolantC = 92;
                s.stft1Pct = 24; s.ltft1Pct = 16; s.mapKpa = 59; s.timingAdvanceDeg = 22;
                s.mafGps = 13.8; s.throttlePct = 20; s.pedalPct = 17; s.fuelRateLph = 4.5;
                break;
            case CATALYST:
                s.speedKph = 92; s.rpm = 2260; s.loadPct = 36; s.coolantC = 94;
                s.stft1Pct = 1.5; s.ltft1Pct = 2.2; s.mapKpa = 54; s.timingAdvanceDeg = 26;
                s.mafGps = 11.0; s.throttlePct = 17; s.pedalPct = 14; s.fuelRateLph = 3.7;
                break;
            case OVERHEAT:
                s.speedKph = 46; s.rpm = 2140; s.loadPct = 58; s.coolantC = 124;
                s.stft1Pct = 2.0; s.ltft1Pct = 2.5; s.mapKpa = 71; s.timingAdvanceDeg = 16;
                s.mafGps = 16.1; s.throttlePct = 28; s.pedalPct = 23; s.fuelRateLph = 5.3;
                s.oilC = 112;
                break;
            case WEAK_CHARGING:
                s.speedKph = 0; s.rpm = 790; s.loadPct = 24; s.coolantC = 89;
                s.stft1Pct = 0.8; s.ltft1Pct = 2.1; s.mapKpa = 35; s.timingAdvanceDeg = 9;
                s.mafGps = 3.1; s.throttlePct = 4; s.pedalPct = 0; s.fuelRateLph = 1.0;
                s.voltageV = 11.58;
                break;
            case HEALTHY:
            default:
                s.speedKph = 56; s.rpm = 1880; s.loadPct = 34; s.coolantC = 91;
                s.mapKpa = 52; s.timingAdvanceDeg = 25; s.mafGps = 9.8;
                s.throttlePct = 15; s.pedalPct = 12; s.fuelRateLph = 3.2;
                break;
        }
        return s;
    }

    private static double driveSpeed(double phase)
    {
        if (phase < 10) return 0;
        if (phase < 25) return lerp(0, 55, (phase - 10) / 15.0);
        if (phase < 45) return 55 + Math.sin(phase * 0.55) * 1.2;
        if (phase < 55) return lerp(55, 0, (phase - 45) / 10.0);
        if (phase < 65) return 0;
        if (phase < 85) return lerp(0, 100, (phase - 65) / 20.0);
        if (phase < 105) return 100 + Math.sin(phase * 0.42) * 1.5;
        return lerp(100, 0, (phase - 105) / 15.0);
    }

    private static double driveThrottle(double phase, double speed, double accelKphPerSec)
    {
        double base;
        if (phase < 10 || (phase >= 55 && phase < 65)) base = 4.0;
        else if (phase < 25) base = 42.0;
        else if (phase < 45) base = 16.0;
        else if (phase < 55) base = 2.0;
        else if (phase < 85) base = 54.0;
        else if (phase < 105) base = 19.0;
        else base = 2.0;
        if (accelKphPerSec > 0.5) base += Math.min(12.0, accelKphPerSec * 0.9);
        if (speed < 1.0 && base > 10) base = 4.0;
        return clamp(base, 1.5, 78.0);
    }

    private static int gearFor(double speed)
    {
        if (speed < 1.0) return 0;
        if (speed < 18) return 1;
        if (speed < 35) return 2;
        if (speed < 60) return 3;
        if (speed < 82) return 4;
        if (speed < 112) return 5;
        return 6;
    }

    private static double lerp(double a, double b, double t)
    {
        return a + (b - a) * clamp(t, 0, 1);
    }

    private static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static int percent(double value)
    {
        return (int)Math.round(clamp(value, 0, 100) * 255.0 / 100.0);
    }

    private static int percent7Relative(double value)
    {
        return (int)Math.round(clamp(value * 128.0 / 100.0 + 128.0, 0, 255));
    }

    private static int temp(double celsius)
    {
        return (int)Math.round(clamp(celsius + 40.0, 0, 255));
    }

    private static byte[] u16(int value)
    {
        int v = Math.max(0, Math.min(0xFFFF, value));
        return bytes((v >>> 8) & 0xFF, v & 0xFF);
    }

    private static byte[] bytes(int... values)
    {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte)(values[i] & 0xFF);
        return result;
    }
}
