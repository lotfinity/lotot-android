# LoToTi Algerian Darija (`ar-DZ`) + RTL Implementation Plan

Status: **IMPLEMENTED + DEVICE-VERIFIED — full React Darija, native ar-DZ localization, unified selector, RTL/bidi, AI profile and streaming fix are active**
Target locale: **`ar-DZ`**
Display name: **الدارجة الجزائرية**
Direction: **RTL**
Translation character: **Algerian Darija in Arabic script, with natural French automotive/technical vocabulary kept in Latin script where Algerians normally use it.**

---

## Implementation checkpoint (2026-08-17)

Completed and verified on the connected Samsung debug device:

- React i18n split into locale modules; EN/FR remain 450/450 with zero placeholder mismatches.
- BCP-47 normalization preserves `ar-DZ`; generic `ar` does not silently become Algerian Darija.
- `html lang`, `dir`, and direction data attributes update together.
- Native Android locale parsing uses `Locale.forLanguageTag(...)` and wrapped locale contexts for Activities.
- Native language picker includes `الدارجة الجزائرية` (`ar-DZ`).
- `values-ar-rDZ` overlay exists and falls back to existing AndrOBD `values-ar`.
- LoToTi-facing missing native strings have an initial Algerian Darija/French technical overlay.
- Semantic CSS was converted to logical properties; only physical chart coordinates remain intentionally LTR.
- Mixed bidi helpers isolate DTCs, measurements, URLs/hosts, acronyms, and AI inline code.
- Icon registry has explicit RTL mirroring metadata; only semantic disclosure arrows mirror.
- Runtime verification returned `lang=ar-DZ`, `dir=rtl`, computed `bodyDirection=rtl`, native bridge language `ar-DZ`.
- Native Settings UI rendered the Algeria overlay plus inherited AndrOBD Arabic resources.

Completed in the content/UX phase:

- React Darija coverage is now 454/454 keys, with zero placeholder mismatches.
- Native `values-ar-rDZ` now covers all 206 translatable native strings; hand-curated LoToTi wording overrides generated drafts where stronger.
- Appearance has one app-wide language selector: System / English / Français / الدارجة الجزائرية.
- Legacy native language selection is restricted to the same supported set so React/native/AI cannot diverge.
- Unsupported system languages resolve consistently to English until a matching LoToTi locale is added.
- LoToTi AI has a dedicated Algerian Darija + French automotive terminology profile.
- Mixed RTL/LTR AI output was device-tested with P0301/P0420.
- An overlapping SSE-chunk bug discovered during Darija AI QA was fixed so streamed answers no longer duplicate/interleave text.
- `npm --prefix lotot-ui run i18n:check` enforces locale key and placeholder parity.

Remaining polish is human linguistic review from real Algerian users and visual QA on additional screen sizes.

---


## 1. Goal

Add Algerian Darija as a first-class LoToTi language without creating a split-language app.

A single selected language must control:

1. LoToTi React/WebView UI.
2. Native AndrOBD Android screens/settings/dialogs.
3. LoToTi AI response language and style.
4. Android layout direction and React document direction.

For Darija, the final contract is:

```text
Language setting: ar-DZ
        |
        +--> React UI: ar-DZ / RTL / Darija + French technical terms
        +--> Native Android: ar-DZ / RTL
        +--> AndrOBD resources: ar-DZ overlay -> existing Arabic -> base fallback
        +--> LoToTi AI: Algerian Darija prompt profile
```

No screen should independently decide its language.

---

## 2. Current repo state (audit 2026-08-17)

### React LoToTi UI

- `lotot-ui/src/i18n.js`
- 450 English keys.
- 450 French keys.
- Current dictionaries: `en`, `fr` only.
- Current normalization maps French to `fr`; every other language becomes `en`.
- React already receives the native language through `LotoTNative.getAppLanguage()`.

### Native AndrOBD Android UI

- Base Android strings: **221**.
- Existing Arabic strings (`values-ar`): **148**.
- Missing from existing Arabic: **73**.
- Existing native translation directories include Arabic, Turkish, German, Spanish, Italian, Russian, Portuguese, Chinese and many more.
- `AndroidManifest.xml` already has `android:supportsRtl="true"`.

### Current locale bug for a regional locale

`SettingsActivity` currently does:

```java
new Locale(language)
```

and system-language resolution returns only:

```java
systemLocale.getLanguage()
```

That is not sufficient for a regional locale such as `ar-DZ`.

We must preserve/parse BCP-47 tags, otherwise `ar-DZ` can become only `ar` or be interpreted incorrectly.

### RTL-sensitive React CSS

Audit found **44 direction-sensitive CSS lines** using hardcoded `left`, `right`, `padding-left`, `text-align:left`, etc.

Some should become logical properties; some represent physical/chart geometry and must intentionally stay LTR.

---

## 3. Language identity and translation policy

### 3.1 Locale identity

Use:

```text
BCP-47: ar-DZ
Android resource qualifier: values-ar-rDZ
React locale key: ar-DZ
Direction: rtl
```

Do **not** pretend Darija is generic `ar`.

Generic `ar` remains useful as the upstream AndrOBD Arabic source/fallback, while `ar-DZ` is LoToTi's Algerian localization.

### 3.2 Tone

Darija should sound Algerian, useful and professional — not MSA translated word-for-word and not exaggerated slang.

Three tone levels:

**Short UI labels**
- concise
- familiar
- technical nouns may stay French

**Warnings/explanations**
- Arabic-script Darija sentence structure
- French automotive terms naturally embedded

**AI copilot**
- conversational Algerian Darija
- concise diagnostic reasoning
- French technical terminology where natural
- never invent Arabic translations for common Algerian garage terms just to sound formal

### 3.3 Technical vocabulary policy

Keep common French/Latin automotive terminology where Algerian users naturally use it, e.g.:

```text
ECU
OBD / OBD-II
ELM327
CAN
ABS
RPM
Bluetooth
Wi-Fi
MQTT
capteur
calculateur
pression
température
injecteur
bobine
bougie
débitmètre
papillon
turbo
catalyseur
sonde lambda
batterie
alternateur
misfire
code défaut
scan
diagnostic
```

The glossary must be centralized so one concept never randomly appears as French on one screen, MSA on another, and English on a third.

### 3.4 Example style (illustrative, not final copy)

```text
دير scan للـ défauts ECU

الـ connexion تاع السيارة تقطعت. شوف l’adaptateur وعاود جرّب.

P0301 غالبًا راهو misfire فـ cylindre 1. بدا بالـ bougie و bobine قبل ما تبدّل أي pièce.
```

Final wording requires a dedicated translation/QA pass.

---

## 4. Phase A — Refactor i18n before translating

### A1. Split the current monolithic React i18n file

Target structure:

```text
lotot-ui/src/i18n/
  index.js
  languages.js
  en.js (or en.json)
  fr.js (or fr.json)
  ar-DZ.js (or ar-DZ.json)
  glossary.ar-DZ.js
```

Do not change existing English/French behavior during the refactor.

### A2. Central language registry

Create one registry containing at minimum:

```js
{
  en: { label: 'English', dir: 'ltr' },
  fr: { label: 'Français', dir: 'ltr' },
  'ar-DZ': { label: 'الدارجة الجزائرية', dir: 'rtl' }
}
```

Later Turkish can be added without changing architecture.

### A3. Normalize BCP-47 correctly

Normalization requirements:

```text
ar-DZ, ar-dz, ar_DZ -> ar-DZ
fr-FR, fr-CA -> fr (for current LoToTi French dictionary)
en-US, en-GB -> en
```

For generic `ar` system locale, product decision:

- Do NOT silently call it Algerian Darija unless the region is Algeria or the user explicitly selected Darija.
- If user explicitly selects `الدارجة الجزائرية`, persist `ar-DZ`.

### A4. Set language and direction together

On language change:

```js
document.documentElement.lang = locale;
document.documentElement.dir = direction;
```

Also expose data attributes if useful:

```text
data-language="ar-DZ"
data-direction="rtl"
```

---

## 5. Phase B — Fix native Android locale plumbing

### B1. Preserve the regional tag

Update `SettingsActivity.getResolvedLanguage()` so system locale resolution returns a proper language tag, not only `getLanguage()`.

Desired behavior:

```text
Arabic Algeria system locale -> ar-DZ
French Algeria -> fr-DZ (React can normalize to fr)
Turkish Turkey -> tr-TR
```

### B2. Parse locale tags correctly

Replace `new Locale(language)` for BCP-47 values with proper tag parsing, e.g. `Locale.forLanguageTag(...)` on this app's supported API range.

Also set layout direction in the native configuration.

### B3. One persisted setting

Keep one source of truth (`app_language`) for native + React + AI.

The preference must accept:

```text
system
en
fr
ar-DZ
```

(and later `tr`).

### B4. Bridge sends exact locale

`LotoTWebBridge.getAppLanguage()` and `publishLotoTLanguage()` must send `ar-DZ`, not collapse it to `ar`.

### B5. Native picker

Add:

```text
الدارجة الجزائرية
value: ar-DZ
```

Eventually expose the same language selector in LoToTi's own Appearance screen and keep the legacy native preference synchronized.

---

## 6. Phase C — Native AndrOBD Darija resource strategy

Do not throw away existing AndrOBD Arabic translation work.

### C1. Create Algeria-specific overlay

Add:

```text
androbd/src/main/res/values-ar-rDZ/strings.xml
```

### C2. Leverage existing `values-ar`

Existing generic Arabic has 148/221 strings.

Use those strings as **translation source/reference**, not blindly as the final Algerian copy.

Resource fallback can provide generic Arabic for legacy strings while the Algeria-specific overlay is built out.

### C3. Eliminate English holes

Existing Arabic is missing 73 base strings, including many newer LoToTi strings such as:

- LoToTi Bluetooth errors
- GPS/motion labels
- gateway status
- MQTT/network/USB/OBD labels
- advanced settings labels
- language selection strings

The `ar-DZ` overlay should cover these missing LoToTi-facing strings first so Darija mode never drops obviously into English during normal use.

### C4. Progressive Darija conversion

After coverage is complete, review the inherited 148 generic-Arabic translations and override the ones that feel unnatural for Algeria.

Priority:

1. LoToTi-facing screens/messages.
2. Connection/settings/diagnostics.
3. Frequently used AndrOBD screens.
4. Rare legacy/help strings.

---

## 7. Phase D — React RTL infrastructure

### D1. Classify all 44 direction-sensitive CSS lines

Each occurrence goes into one of two buckets:

**Semantic direction — convert to logical CSS**

Examples:

```css
text-align: start;
margin-inline-start: ...;
padding-inline-end: ...;
border-inline-end: ...;
inset-inline-start: ...;
```

**Physical/data direction — keep LTR intentionally**

Examples:

- graph axes
- temperature/range marker coordinate systems
- gauges where 0->100 progression is physical/data-defined
- vehicle diagrams where left/right means actual vehicle side

Do not globally mirror the whole app using `transform: scaleX(-1)`.

### D2. Layouts that must flip semantically

- sheet/header flows
- text alignment
- connection rows
- settings rows
- onboarding cards
- chevrons/back/forward controls
- message layout where appropriate

### D3. Layouts/components that stay physically LTR

Mark explicit LTR islands for:

- DTC codes
- ECU identifiers
- VIN
- hexadecimal/CAN frames
- IP addresses / ports
- MQTT topics
- units and numeric measurements
- charts/range tracks when direction conveys numeric progression
- technical log output

---

## 8. Phase E — Bidirectional text safety (critical)

Mixed Arabic + French + numbers/codes must not be left to chance.

### E1. Add reusable bidi components/helpers

Examples:

```jsx
<TechnicalToken>P0301</TechnicalToken>
<Measurement value="13.8" unit="V" />
<LtrText>misfire cylindre 1</LtrText>
```

Implementation should use `dir="ltr"` plus bidi isolation (`<bdi>` / `unicode-bidi:isolate`) where appropriate.

### E2. Always isolate

- `P0301`, `U0100`, etc.
- `13.8 V`, `2.4 bar`, `108 °C`
- percentages
- VIN
- `7E0`, `7E8`
- `ELM327`
- URLs, hosts, IPs, MQTT topics
- model/provider names
- Latin/French multi-word technical phrases when punctuation could reorder

### E3. AI/Markdown renderer

AI output is especially risky because it contains arbitrary combinations of:

```text
Arabic + French + P-codes + numbers + Markdown lists
```

Add bidi-safe CSS/rendering to AI Markdown:

- paragraph base direction RTL in Darija
- code spans always LTR
- numbers/units isolated
- lists indent from inline-start
- inline code and code blocks `direction:ltr; text-align:left`

---

## 9. Phase F — Icon RTL behavior

Do not mirror the entire icon system.

Extend icon metadata with a semantic flag such as:

```js
mirrorInRtl: true / false
```

### Mirror

- back/forward chevrons
- navigation arrows
- directional disclosure affordances

### Do not mirror

- engine
- battery
- Bluetooth
- OBD connector
- ECU/chip
- health shield
- diagnostic scan
- warning icons
- gauges unless direction is explicitly semantic
- vehicle-side diagrams where physical side matters

This plugs cleanly into the icon-family system already added.

---

## 10. Phase G — React Darija translation creation

React has **450 keys**.

Translation workflow:

1. Freeze English keys as source-of-truth IDs.
2. Seed `ar-DZ` translations using:
   - existing French LoToTi copy for technical vocabulary,
   - existing AndrOBD Arabic translations for semantic reference,
   - Algerian Darija style glossary.
3. Translate in functional groups, not alphabetically:
   - navigation/common
   - connection
   - health/alerts
   - signals/sensors
   - faults/diagnostics
   - AI
   - demo
   - services/MQTT
   - appearance
   - onboarding
4. Automated missing-key test: `ar-DZ` must contain every English key before release.
5. Placeholder parity test: `{count}`, `{value}`, etc. must match the English source placeholders.

### Proposed fallback during development

While `ar-DZ` is incomplete:

```text
ar-DZ -> fr -> en
```

This is preferable for the React layer during development because French is deliberately part of the Algerian technical language profile.

Before release, target **450/450** so fallback is not visible in normal UI.

---

## 11. Phase H — Darija AI profile

Do not merely tell the model `language = ar-DZ`.

Add a dedicated language-style instruction approximately equivalent to:

```text
Reply in Algerian Darija, primarily Arabic script.
Use natural French automotive technical terms commonly used in Algeria.
Keep OBD codes, ECU names, acronyms, measurements and technical identifiers in LTR form.
Avoid formal Modern Standard Arabic when a natural Algerian expression is clearer.
Stay professional and diagnostic; do not exaggerate slang.
```

AI context should continue carrying exact DTC codes/signals unchanged.

Test prompts must include mixed bidi content:

```text
P0301
P0420
13.8 V
-12.5 % STFT
7E0 -> 7E8
```

---

## 12. Phase I — Fonts and typography

Arabic glyph coverage must be guaranteed in every Appearance font option.

Requirements:

- Arabic body text must always have a reliable system Arabic fallback.
- User-selected font family can remain effective for Latin/French technical tokens where supported.
- Verify `technical`/monospace Appearance mode does not create broken Arabic glyphs or extreme baseline mismatch.
- Do not bundle a new font unless needed; prefer Android/WebView system Arabic fonts first.

---

## 13. Phase J — Language selector UX

Preferred final location:

```text
Plus -> Appearance -> Language
```

Options initially:

```text
System default
English
Français
الدارجة الجزائرية
```

Later add Türkçe after the RTL/Darija foundation is stable.

Changing language should:

1. persist `app_language`;
2. update React instantly;
3. update `lang` + `dir`;
4. update AI language immediately;
5. apply native locale;
6. recreate only native Activities that actually require recreation.

Avoid unnecessary full app/data reset.

---

## 14. Automated checks

Add checks/scripts for:

### React

- Every locale has the same key set as English.
- Placeholder tokens match source.
- No hardcoded user-facing English in normal React screens (allow explicit technical identifiers).
- RTL mode produces `html[dir=rtl]`.

### Android

- `ar-DZ` resolves as `ar-DZ`, not `ar`/`en`.
- `Locale.forLanguageTag` path works.
- resource lookup selects `values-ar-rDZ` where present.
- no crash switching locale.

### CSS

- track remaining hardcoded directional properties.
- whitelist physical/data-direction cases instead of silently ignoring them.

---

## 15. Visual/device QA matrix

Primary connected phone class: Samsung/Android WebView.

Test all five primary tabs in both dark and light themes:

```text
Overview
Live Data
AI
Health
More
```

Also test:

- Appearance sheet
- Connection sheet
- Onboarding
- Bluetooth device list
- services/MQTT settings
- DTC scan/results
- demo scenarios
- native Advanced AndrOBD settings
- notification text
- AI conversation with codes and measurements

### Required RTL screenshots

Capture side-by-side:

```text
English LTR | Darija RTL
```

for at least:

1. Overview
2. Live Data
3. AI with mixed Arabic/French/code content
4. Health
5. More / Appearance
6. Native advanced settings

---

## 16. Release acceptance criteria

Darija is release-ready only when all are true:

- [ ] `ar-DZ` remains intact end-to-end; region is not stripped.
- [ ] One language setting controls React, native AndrOBD and AI.
- [ ] React is fully RTL in Darija.
- [ ] No blanket icon/image mirroring.
- [ ] Codes/units/technical identifiers remain readable LTR.
- [ ] React translation coverage is 450/450.
- [ ] Placeholder parity test passes.
- [ ] Normal LoToTi native flows do not fall into English.
- [ ] Existing AndrOBD Arabic translation work is reused as reference/fallback.
- [ ] Darija vocabulary is consistent with the approved glossary.
- [ ] AI replies in Algerian Darija + natural French technical vocabulary.
- [ ] English and French regression tests pass.
- [ ] Debug APK builds with the existing LoToTi signing certificate.
- [ ] APK installs as an update without wiping user data.
- [ ] RTL visual QA passes on the actual Samsung device.

---

## 17. Recommended implementation order

Execute in this exact order:

```text
1. Refactor i18n structure without changing EN/FR output.
2. Fix BCP-47 native locale handling (`ar-DZ`).
3. Add direction registry + html dir switching.
4. Refactor the 44 direction-sensitive CSS cases.
5. Add bidi-safe technical components.
6. Add icon mirror metadata.
7. Add native `values-ar-rDZ` overlay and language selector entry.
8. Create Darija glossary.
9. Translate React 450 keys by functional section.
10. Fill/override native Darija strings using existing AndrOBD Arabic as source.
11. Add Darija-specific AI prompt profile.
12. Add automated key/placeholder/RTL tests.
13. Build as user `lofa` (never root).
14. Install update on connected Samsung.
15. Capture RTL screenshots and perform linguistic + visual QA.
```

This sequence makes RTL infrastructure correct before the translation corpus is populated, minimizing rework.
