# Localization coverage and limits

The same APK contains English, French and Algerian Darija. The saved language
preference controls the React UI, Android native resources, diagnostic lookups
and the response-language instruction sent to LoToTi AI.

The APK packages the three selectable locales (en, fr, ar-DZ), matching the native and React language registries. Unreachable upstream locale resources are filtered during packaging.

The UI dictionaries contain 477 matching keys. French and Darija cover all 206
translatable strings in the native base strings.xml. Checks compare keys and
format placeholders; they do not substitute for translation or layout review.
Unknown sensor labels fall back to the native human-readable label, then mnemonic,
rather than displaying an internal signal.* translation key.

## Offline DTC definitions

The bundled SQLite database contains 18,805 English rows. It is unchanged.
dtc_translations.json supplies French and Darija translations of 56 common
GENERIC definitions, with the exact original English source for each entry.
Translations are applied only when that source still matches the bundled row.

Manufacturer-specific evidence takes precedence over a generic translation.
Within the same manufacturer scope, lookups try the selected language, French
for Darija, then English. Unknown codes remain unknown. Cached descriptions are
cleared when the selected language changes. The health screen indicates when a
definition falls back to English or French.

Switching language re-renders descriptions from a copy of the existing scan.
It does not trigger an ECU rescan or change codes, statuses, counts or scan time.
Search/type/manufacturer browsing retains an English fallback when the database
has no rows in the requested language. Translated-keyword search over the overlay
is not implemented.

This is 56 translated generic definitions per language, not a claim that all
18,805 rows or manufacturer-specific entries have been translated. Extend the
catalogue with reviewed definitions and retain source verification.

## RTL and testing

Arabic uses RTL page layout. Fault codes, units, technical tokens and data-chart
coordinates retain LTR ordering. Browser QA covers French/Darija overview, live
data, health, AI and tools screens across dark/light contexts at phone width.
Native device QA remains separate from mocked browser checks.

Run npm --prefix lotot-ui test, node lotot-ui/scripts/check-i18n.mjs,
python3 tools/check-native-translations.py and python3 tools/check-dtc-translations.py.
DtcLocaleResolverTest covers language/source/manufacturer fallback rules.
DtcLocalizationInstrumentedTest verifies the packaged database/catalogue,
cache changes and scan-evidence preservation in the isolated verification app.
