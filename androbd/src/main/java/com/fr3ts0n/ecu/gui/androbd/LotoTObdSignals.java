package com.fr3ts0n.ecu.gui.androbd;

import com.fr3ts0n.ecu.EcuDataItem;
import com.fr3ts0n.ecu.EcuDataItems;
import com.fr3ts0n.ecu.EcuDataPv;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;

/** Reads decoded measurements without depending on an activity or WebView. */
final class LotoTObdSignals
{
    static JSONArray collect(String source)
    {
        JSONArray signals = new JSONArray();
        try
        {
            ArrayList<EcuDataItem> updatedItems = new ArrayList<>();
            for (EcuDataItem item : EcuDataItems.byMnemonic.values())
            {
                if (item == null || item.pv == null || item.updatedAt <= 0L
                        || System.currentTimeMillis() - item.updatedAt > 60_000L) continue;
                updatedItems.add(item);
            }
            java.util.Collections.sort(updatedItems, (a, b) -> {
                int order = Integer.compare(a.pid, b.pid);
                if (order == 0) order = Integer.compare(a.ofs, b.ofs);
                if (order == 0) order = String.valueOf(a.pv.get(EcuDataPv.FID_MNEMONIC))
                        .compareTo(String.valueOf(b.pv.get(EcuDataPv.FID_MNEMONIC)));
                return order;
            });

            for (EcuDataItem item : updatedItems)
            {
                Object value = item.pv.get(EcuDataPv.FID_VALUE);
                if (value instanceof Number)
                {
                    double numeric = ((Number) value).doubleValue();
                    if (!Double.isFinite(numeric)) continue;
                    value = numeric;
                }
                else
                {
                    if (value == null) continue;
                    String stringValue = String.valueOf(value).trim();
                    if (stringValue.isEmpty()
                            || "n/a".equalsIgnoreCase(stringValue)
                            || "nan".equalsIgnoreCase(stringValue)) continue;
                    value = stringValue;
                }

                Object mnemonicObject = item.pv.get(EcuDataPv.FID_MNEMONIC);
                String mnemonic = mnemonicObject == null
                        ? String.format(java.util.Locale.ROOT, "PID_%02X_%d", item.pid, item.ofs)
                        : String.valueOf(mnemonicObject);
                Object descriptionObject = item.pv.get(EcuDataPv.FID_DESCRIPT);
                String description = descriptionObject == null
                        ? mnemonic : String.valueOf(descriptionObject);

                JSONObject signal = new JSONObject();
                signal.put("key", String.format(java.util.Locale.ROOT, "%02X.%d.%s", item.pid, item.ofs, mnemonic));
                signal.put("mnemonic", mnemonic);
                signal.put("label", description);
                signal.put("value", value);
                signal.put("unit", item.pv.getUnits());
                signal.put("pid", item.pid);
                signal.put("offset", item.ofs);
                signal.put("updated_at", item.updatedAt);
                signal.put("source", "obd");
                signal.put("simulated", "demo".equals(source));

                Object minObject = item.pv.get(EcuDataPv.FID_MIN);
                if (minObject instanceof Number)
                {
                    double min = ((Number) minObject).doubleValue();
                    if (Double.isFinite(min)) signal.put("min", min);
                }
                Object maxObject = item.pv.get(EcuDataPv.FID_MAX);
                if (maxObject instanceof Number)
                {
                    double max = ((Number) maxObject).doubleValue();
                    if (Double.isFinite(max)) signal.put("max", max);
                }
                signals.put(signal);
            }
        }
        catch (Exception ignored) { /* Registry may be rebuilding after a service change. */ }
        return signals;
    }
}
