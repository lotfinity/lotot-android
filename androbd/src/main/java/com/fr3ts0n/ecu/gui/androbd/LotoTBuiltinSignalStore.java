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
        signals.putIfAbsent(mnemonic,
                new Signal(mnemonic, label, unit, min, max, source));
    }

    synchronized void update(String mnemonic, Object value)
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
        signal.updatedAt = System.currentTimeMillis();
    }

    synchronized void appendTo(JSONArray target) throws org.json.JSONException
    {
        List<Signal> ordered = new ArrayList<>(signals.values());
        ordered.sort(Comparator.comparing(signal -> signal.mnemonic));
        for (Signal signal : ordered)
        {
            if (signal.updatedAt <= 0L || signal.value == null) continue;
            JSONObject json = new JSONObject();
            json.put("key", "builtin." + signal.source + "." + signal.mnemonic);
            json.put("mnemonic", signal.mnemonic);
            json.put("label", signal.label);
            json.put("value", signal.value);
            json.put("unit", signal.unit);
            json.put("source", signal.source);
            json.put("updated_at", signal.updatedAt);
            json.put("min", signal.min);
            json.put("max", signal.max);
            target.put(json);
        }
    }
}
