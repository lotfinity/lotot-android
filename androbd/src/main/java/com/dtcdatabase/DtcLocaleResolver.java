package com.dtcdatabase;

import org.json.JSONObject;
import java.util.Locale;

/** Language fallback never substitutes a generic definition for a known
 * manufacturer-specific definition. English evidence is always preserved. */
public final class DtcLocaleResolver {
    public interface Source { Definition find(String code, String manufacturer, String locale); }
    public static final class Definition {
        public final String code, description, type, manufacturer, locale;
        public Definition(String code, String description, String type, String manufacturer, String locale) {
            this.code=code; this.description=description; this.type=type;
            this.manufacturer=manufacturer; this.locale=locale;
        }
    }
    private final JSONObject catalog;
    public DtcLocaleResolver(JSONObject catalog) { this.catalog=catalog == null ? new JSONObject() : catalog; }
    public static String normalize(String tag) {
        Locale locale=Locale.forLanguageTag(tag == null ? "" : tag.replace('_','-'));
        if ("fr".equals(locale.getLanguage())) return "fr";
        if ("ar".equals(locale.getLanguage()) && "DZ".equals(locale.getCountry())) return "ar-DZ";
        return "en";
    }
    public static String[] chain(String tag) {
        switch (normalize(tag)) {
            case "ar-DZ": return new String[]{"ar-DZ","fr","en"};
            case "fr": return new String[]{"fr","en"};
            default: return new String[]{"en"};
        }
    }
    public Definition resolve(String rawCode, String rawManufacturer, String tag, Source source) {
        if (rawCode == null || rawCode.trim().isEmpty()) return null;
        String code=rawCode.trim().toUpperCase(Locale.ROOT);
        String manufacturer=rawManufacturer == null ? "" : rawManufacturer.trim().toUpperCase(Locale.ROOT);
        String[] manufacturers=manufacturer.isEmpty() || "GENERIC".equals(manufacturer)
                ? new String[]{"GENERIC"} : new String[]{manufacturer,"GENERIC"};
        for (String make:manufacturers) {
            for (String locale:chain(tag)) {
                Definition stored=source.find(code,make,locale);
                if (stored != null) return stored;
                if ("GENERIC".equals(make) && !"en".equals(locale)) {
                    JSONObject translations=catalog.optJSONObject("translations");
                    JSONObject language=translations == null ? null : translations.optJSONObject(locale);
                    String text=language == null ? "" : language.optString(code,"");
                    JSONObject sources=catalog.optJSONObject("source_en");
                    Definition base=text.isEmpty() ? null : source.find(code,make,"en");
                    // Apply a translation only to its exact English source definition.
                    if (base != null && sources != null && base.description.equals(sources.optString(code,""))) {
                        return new Definition(base.code,text,base.type,base.manufacturer,locale);
                    }
                }
            }
        }
        return null;
    }
}
