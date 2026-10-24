/*
 * Built-in LotoT signal registry, adapted from the GPLv3 AndrOBD provider
 * plugins and distributed under the same license as the surrounding app.
 */
package com.fr3ts0n.ecu.gui.androbd;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LotoTBuiltinSignalStore
{
    private static final class Signal
    {
        final String mnemonic;
        final String label;
        final String unit;
        final double min;
        final double max;
        final String source;
        Object value;
        long updatedAt;
        Double accuracy;

        Signal(String mnemonic, String label, String unit,
               double min, double max, String source)
        {
            this.mnemonic = mnemonic;
            this.label = label;
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.source = source;
        }
    }

    private final Map<String, Signal> signals = new LinkedHashMap<>();

    synchronized void define(String mnemonic, String label, String unit,
                             double min, double max, String source)
    {
        if (!signals.containsKey(mnemonic))
            signals.put(mnemonic, new Signal(mnemonic, label, unit, min, max, source));
    }

    synchronized void update(String mnemonic, Object value)
    { update(mnemonic, value, System.currentTimeMillis(), null); }

    synchronized void clearSource(String source)
    {
        for (Signal signal : signals.values())
            if (source.equals(signal.source)) { signal.value = null; signal.updatedAt = 0; }
    }

    synchronized void update(String mnemonic, Object value, long observedAt, Double accuracy)
    {
        Signal signal = signals.get(mnemonic);
        if (signal == null || value == null) return;
        if (value instanceof Number)
        {
            double numeric = ((Number) value).doubleValue();
            if (!Double.isFinite(numeric)) return;
            signal.value = numeric;
        }
        else
        {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) return;
            signal.value = text;
        }
        signal.updatedAt = observedAt;
        signal.accuracy = accuracy;
    }

    synchronized void appendTo(JSONArray target) throws org.json.JSONException
    {
        List<Signal> ordered = new ArrayList<>(signals.values());
        java.util.Collections.sort(ordered, (a, b) -> a.mnemonic.compareTo(b.mnemonic));
        for (Signal signal : ordered)
        {
            long maxAge = "gps".equals(signal.source) ? 30_000L : 5_000L;
            if (signal.updatedAt <= 0L || signal.value == null
                    || System.currentTimeMillis() - signal.updatedAt > maxAge) continue;
            JSONObject json = new JSONObject();
            json.put("key", "builtin." + signal.source + "." + signal.mnemonic);
            json.put("mnemonic", signal.mnemonic);
            json.put("label", signal.label);
            json.put("value", signal.value);
            json.put("unit", signal.unit);
            json.put("source", signal.source);
            json.put("updated_at", signal.updatedAt);
            if (signal.accuracy != null) json.put("accuracy_m", signal.accuracy);
            json.put("min", signal.min);
            json.put("max", signal.max);
            target.put(json);
        }
    }
}
