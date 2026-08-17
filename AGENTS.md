# AGENTS.md — LoToTi Icon System Redesign

## Mission

You are responsible for redesigning and implementing the complete icon system for the LoToTi Android application **end to end**.

The current application is visually strong, but the iconography is not. Existing icons feel generic, thin, inconsistent, and too close to basic Android/Lucide-style UI symbols. The goal is to make the icon system feel like an intentional part of a premium automotive diagnostic product.

Do not stop at recommendations, mockups, or source edits. Audit the existing icon usage, implement the new icon system, rebuild the web UI, rebuild the Android APK, install it on available ADB devices, launch it, and perform visual/functional QA.

The final result should feel:

- premium
- automotive
- technical
- precise
- slightly futuristic
- confident
- coherent across every screen

It must **not** feel:

- childish or cartoonish
- like generic Material Design
- like a random mixture of icon libraries
- excessively thin or timid
- over-decorated cyberpunk
- inconsistent between screens

---

## Repository and Architecture Facts

Repository root:

```text
/home/lofa/DEV/lotot-android-spike
```

Main LoToTi UI source:

```text
lotot-ui/src/main.jsx
lotot-ui/src/styles.css
lotot-ui/src/i18n.js
```

Bundled Android web assets:

```text
androbd/src/main/assets/lotot/
```

Android module:

```text
androbd/
```

The LoToTi front-end is a **React + Vite application rendered inside the Android app**. It is **not** a Jetpack Compose UI.

This matters: **do not migrate the UI to Jetpack Compose merely to redesign icons.** That would create unnecessary architecture churn and risk unrelated behavior.

The current icon implementation is a React `Icon()` component in `lotot-ui/src/main.jsx`, backed by hand-written inline SVG paths on a 24×24 viewBox. The correct primary implementation layer is therefore a custom React/SVG icon system.

Android VectorDrawable/ImageVector may still be appropriate for native-only Android surfaces outside the React UI, but they are not the primary task.

---

## Repository Safety Rules

This working tree already contains important uncommitted work.

Before changing anything, run:

```bash
git status --short
```

Rules:

1. Do **not** run `git reset --hard`.
2. Do **not** run `git clean -fd`.
3. Do **not** blindly replace the working tree with `origin/main`.
4. Do **not** discard, stash, or overwrite unrelated local modifications.
5. Do **not** modify AI routing, LiteLLM configuration, OBD behavior, telemetry logic, MQTT logic, demo-model behavior, DTC logic, onboarding behavior, or native bridges unless an icon-rendering change directly requires it.
6. Preserve all existing interaction behavior, labels, navigation, accessibility attributes, and data flow.
7. Prefer small, focused structural changes around icon rendering rather than broad rewrites.

---

# Visual Direction

Use a hybrid design language:

- **Industrial Soft** for the global icon family
- **Neo-ECU** for the signature AI identity and selected diagnostic/scan states

This does **not** mean putting every icon inside a futuristic hexagon. The ordinary family should remain clean and restrained. Distinctive treatment should be reserved for LoToTi-specific concepts such as AI, OBD, scan, DTC, and selected diagnostic modules.

## Core design language

All primary icons should share:

- `viewBox="0 0 24 24"`
- rounded line caps
- rounded line joins
- consistent optical weight
- effective stroke around `2.0–2.2`
- simple geometric construction
- high legibility at 16–24 px
- no tiny details that disappear at bottom-navigation size
- no arbitrary mixing of filled, outlined, sharp, and soft icon styles
- `currentColor` for normal theming

Do not copy an off-the-shelf icon library wholesale as the final result. Lucide, Material Symbols, Heroicons, Phosphor, Font Awesome, etc. may be used as anatomical references, but the primary LoToTi icons should form a coherent custom family.

---

# Icon Presentation Modes

There are only three normal presentation modes.

## 1. Bare icon

Use for:

- metadata
- tiny context strips
- chevrons
- search
- timestamps
- passive categories
- supporting information

Do not add a background container unless interaction or state requires one.

## 2. Module icon

Use an icon inside a restrained rounded-square module for:

- leading sensor cards
- health status
- OBD connection status
- important services
- main AI identity
- selected diagnostic modules

Do not automatically put every icon inside a green rounded rectangle.

## 3. Navigation icon

Use a consistent family for the five bottom-navigation destinations. Match optical width, height, and stroke weight across all five.

The center AI control may have a stronger selected treatment, but its glyph itself must still belong to the same system.

---

# Mandatory Signature Icons

Create/redesign these first.

## Navigation

### `nav_overview`

Meaning: vehicle cockpit / dashboard.

**Do not use a house/home glyph.**

Recommended anatomy:

- semicircular instrument cluster or twin-gauge cockpit
- one clean center needle or dashboard horizon
- avoid excessive tiny ticks

### `nav_live`

Meaning: live telemetry / data stream.

Recommended anatomy:

- controlled waveform
- optional two small anchor nodes or signal terminals
- should read as telemetry, not a medical ECG/heartbeat

### `nav_ai`

Meaning: LoToTi diagnostic intelligence.

This is the application's signature icon.

**Do not use the current cute robot/chatbot mascot.**

Recommended anatomy:

- compact ECU/chip body
- central intelligence core, lens, pulse node, or diagnostic nucleus
- subtle circuit/diagnostic motif
- instantly readable at 20 px
- automotive + AI, not chatbot mascot

The same base glyph must work:

- in bottom nav
- as the large AI page identity
- as the small assistant avatar
- in AI thinking state
- in onboarding where applicable

Use CSS/container treatment for larger hero states rather than inventing a completely different icon.

### `nav_health`

Meaning: vehicle health.

Recommended anatomy:

- geometric shield or protective vehicle-health form
- integrated check/pulse/diagnostic cue
- avoid looking like banking/security software

### `nav_more`

Meaning: modules / tools / services.

Recommended anatomy:

- modular 2×2 system blocks
- slightly mechanical/module-like
- not a generic Android app drawer

---

# Automotive / Diagnostic Core Icons

Create distinct icons for:

- `obd_connected`
- `obd_disconnected`
- `diagnostic_scan`
- `dtc_fault`
- `vehicle`
- `speed`
- `rpm`
- `engine`
- `health_stable`
- `health_warning`
- `health_critical`

## OBD connected / disconnected

Do not use raw Bluetooth as the default vehicle-connection identity.

Bluetooth may remain where Bluetooth itself is specifically the subject.

For general OBD state, create a compact diagnostic connector/ECU-port symbol:

- trapezoid or diagnostic connector anatomy
- 3–5 simplified pin cues maximum
- connected variant gets a clear link/status cue
- disconnected variant gets a clean break/slash
- avoid clutter

## Diagnostic scan

Use a diagnostic/radar concept:

- scan ring
- focus brackets
- ECU target
- sweep/pulse motif

Do not use a plain magnifying glass as the primary scan identity. Magnifier is fine for text search.

## DTC fault

Use a compact diagnostic module/engine/fault-code identity. It should mean “vehicle diagnostic trouble” rather than generic electricity or a naked warning symbol.

---

# Sensor Icon Family

Create a coherent semantic family for:

- `sensor_coolant`
- `sensor_oil`
- `sensor_voltage`
- `sensor_fuel`
- `sensor_intake`
- `sensor_pressure`
- `sensor_airflow`
- `sensor_temperature`
- `sensor_gps`
- `sensor_motion`

## Coolant

Do **not** reuse the same plain thermometer used for oil.

Use:

- thermometer stem
- fluid/wave cue at bottom
- optionally a reservoir cue

## Oil

Use:

- simplified oil can, or
- oil droplet integrated with engine/temperature anatomy

It must be visually distinct from coolant.

## Voltage

Use:

- battery/electrical module or circuit frame
- bolt may be a secondary interior cue

Do **not** make a naked lightning bolt the entire icon.

## Fuel

Use a simplified fuel pump or tank gauge using the same stroke system. Keep nozzle/hose readable but not fussy.

## Intake

Use a purposeful intake / air-stream concept.

Do **not** keep the generic “wind” symbol.

Consider:

- intake throat
- directional stream lines
- one small sensor/node cue

## Pressure

Do **not** use a generic water droplet.

Use:

- gauge/pressure arc
- manifold/pressure chamber
- compact pressure-sensor motif

---

# Demo Scenario Icons

The demo laboratory currently reuses generic icons for different mechanical conditions. Replace them with semantic siblings:

- `scenario_healthy`
- `scenario_cold_start`
- `scenario_misfire`
- `scenario_lean`
- `scenario_catalyst`
- `scenario_overheat`
- `scenario_weak_charging`

Guidance:

### Healthy
Shield/cockpit/pulse with stable cue.

### Cold start
Cold thermometer + engine/start cue. Do not use a plain thermometer alone.

### Misfire
Broken combustion pulse, cylinder interruption, or irregular waveform.

### Lean mixture
Air/fuel imbalance motif. Do not use generic wind.

### Catalyst
Exhaust/catalyst honeycomb/converter motif. Do not use a leaf.

### Overheat
Engine or coolant temperature with clear rising-heat/danger cue.

### Weak charging
Battery/alternator/low-voltage concept, not only a bolt.

---

# Secondary Utility Icons

The following can stay simpler, but must match the same stroke/corner language:

- search
- close
- chevron
- refresh
- star/favorite
- bluetooth
- link
- unlink
- signal
- play
- tool/settings
- sun
- moon
- location
- cloud
- phone
- usb
- wifi
- typography
- sliders
- clock

Do not over-design utility glyphs. Clarity beats novelty for small controls.

---

# Implementation Architecture

Refactor the current giant inline `Icon()` map out of `lotot-ui/src/main.jsx`.

Recommended layout:

```text
lotot-ui/src/icons/
  Icon.jsx
  iconRegistry.js
  paths.js                 # optional; may split by family instead
  IconShowcase.jsx         # development-only visual QA surface
```

A good public API is:

```jsx
<Icon name="nav_ai" size={20} />
```

or:

```jsx
<Icon name="sensor_coolant" className="..." />
```

Base SVG behavior should generally be equivalent to:

```jsx
<svg
  viewBox="0 0 24 24"
  fill="none"
  stroke="currentColor"
  strokeWidth="2.1"
  strokeLinecap="round"
  strokeLinejoin="round"
  aria-hidden="true"
>
  ...
</svg>
```

Individual icons may contain selective fills only when visually necessary.

Keep color controlled by CSS through `currentColor`. Do not hardcode lime into every SVG path.

Avoid duplicating the same SVG shell at every call site.

---

# Semantic Naming and Migration

Current code uses generic names such as:

- `home`
- `activity`
- `bot`
- `shield`
- `grid`
- `gauge`
- `car`
- `thermometer`
- `fuel`
- `wind`
- `droplet`
- `bolt`

Prefer semantic names in final call sites where meaning matters.

Examples:

Bad:

```jsx
<Icon name="wind" />
```

Better:

```jsx
<Icon name="sensor_intake" />
```

Bad:

```jsx
<Icon name="bot" />
```

Better:

```jsx
<Icon name="nav_ai" />
```

Temporary aliases are acceptable during migration, but the preferred final state is semantic naming for primary/automotive icons.

---

# Bottom Navigation Redesign

Current navigation conceptually maps:

- Aperçu → home
- Données → activity
- AI → bot
- Santé → shield
- Plus → grid

Replace **all five** with:

- `nav_overview`
- `nav_live`
- `nav_ai`
- `nav_health`
- `nav_more`

Preserve:

- labels
- click behavior
- active-tab behavior
- accessibility
- center AI prominence

Improve:

- optical consistency
- icon size
- inactive contrast
- active-state hierarchy

The AI button must feel premium, not toy-like. Do not rely on an oversized lime blob to make a weak AI glyph important. The glyph itself must be good.

---

# AI Screen Redesign

Replace every use of the old robot/bot icon on the LoToTi UI, including where applicable:

- AI hero/orb
- bottom navigation
- assistant avatar
- thinking state
- onboarding/promotional surfaces

Use the same `nav_ai` / `ai_core` base identity consistently.

If useful, create related siblings:

- `ai_core`
- `ai_analyze`
- `ai_context`
- `ai_summary`

Do not create arbitrary icons merely to decorate text.

The AI hero may use:

- subtle gradient
- restrained glow
- soft depth

The small avatar should use a simplified version of the same identity if necessary.

---

# Sensor Card Redesign

Current screenshots show tiny generic glyphs inside similar green rounded rectangles. Fix this.

Requirements:

- make glyphs visually stronger and more readable
- typical metric-card glyph: 18–20 px
- typical module container: roughly 36–42 px depending on card
- consistent padding
- container should not dominate glyph
- six critical overview metrics must each have a distinct semantic icon:
  - coolant
  - oil
  - voltage
  - fuel
  - intake
  - pressure

Do not reuse `thermometer` for coolant + oil in the final result.

---

# Health Screen Redesign

Create an intentional health family:

- stable
- warning
- critical
- scan
- DTC/fault

Avoid using the exact same generic shield for every health-related concept.

Examples:

- stable → shield + centered confirmation cue
- warning → shield + diagnostic mark
- scan → scan ring / ECU target
- DTC → fault module / code glyph

Color and text must continue to communicate state; icon shape is not the sole semantic carrier.

---

# Animation Rules

Animation is allowed only where it adds meaning.

Good candidates:

- active diagnostic scan
- AI thinking
- live telemetry pulse
- reconnecting / refresh

Do not animate:

- every selected tab
- every sensor
- static health cards

Prefer CSS animation of SVG groups/strokes. Do not add a heavy Canvas engine unless genuinely necessary.

Respect reduced motion:

```css
@media (prefers-reduced-motion: reduce) {
  /* disable or greatly reduce icon animation */
}
```

---

# Color Rules

Icons should generally inherit `currentColor`.

Existing main accent is approximately:

```text
#caff00
```

Do not hardcode the accent inside every SVG path.

Use semantic CSS/theme colors for:

- accent / selected
- muted inactive
- warning
- danger
- optional informational state

Both dark and light themes must remain usable.

---

# Sizing Guidance

Suggested visual sizes:

- bottom nav: 19–21 px
- center AI nav: 21–23 px
- card-leading icon: 18–21 px
- header action: 18–20 px
- context chip: 12–14 px
- metadata: 10–13 px
- AI hero glyph: 24–30 px inside a 44–52 px module
- assistant avatar glyph: 14–16 px

Use optical normalization rather than assuming identical SVG bounds look equally large. A wide cockpit glyph may need different internal margins than a narrow bolt or thermometer.

---

# Visual QA Requirement

Before finalizing, create a **development-only icon inspection surface** such as:

```text
lotot-ui/src/icons/IconShowcase.jsx
```

or an equivalent dev-only route/component.

It should display every new icon with:

- its name
- 16 px preview
- 20 px preview
- 24 px preview
- normal color
- muted color
- active lime
- dark-theme context
- light-theme context

The goal is to catch:

- inconsistent stroke weights
- bad optical sizing
- icons that collapse at 16 px
- accidental fills
- inconsistent curves
- awkward container spacing

Do not ship an obvious public menu entry solely for this showcase. Gate it behind a development flag/query parameter or keep it as a reusable dev component.

---

# Functional QA

After icon replacement, verify that no interaction behavior changed.

## Overview

Check:

- connection summary
- live cockpit
- health card
- six critical overview metrics
- bottom nav

## Live Data

Check:

- search
- category tabs
- favorites
- signal cards
- trend/status icons
- bottom nav

## AI

Check:

- AI hero
- provider status
- context chips
- assistant avatar
- thinking state
- send button
- bottom nav

## Health

Check:

- stable/alert summary
- fault scan button
- empty scan state
- sensor metrics
- bottom nav

## More

Check:

- services
- demo scenarios
- appearance/settings
- connection actions
- bottom nav

## Onboarding

Check:

- capabilities
- connection methods
- verification states

---

# Build Workflow

Run builds as user `lofa`.

**Do not produce the final Android APK as root.** Root uses/has used a different Android debug signing key and can cause:

```text
INSTALL_FAILED_UPDATE_INCOMPATIBLE
```

Existing app installs on the primary Android devices use the `lofa` debug signing certificate.

Known signing certificate SHA-256:

```text
cf11032b82b77bfc7837d29e3a52b2515ac1966a0c6be4afe4db92cae78145ea
```

## Web UI build

From repo root:

```bash
sudo -u lofa -H bash -lc '
  cd /home/lofa/DEV/lotot-android-spike
  npm --prefix lotot-ui run build
'
```

The Vite build/postbuild writes the bundled UI into:

```text
androbd/src/main/assets/lotot/
```

Do **not** manually treat generated `androbd/src/main/assets/lotot/lotot.js` as the source of truth. Edit `lotot-ui/src/...`, then rebuild.

## Android build

Run as `lofa`:

```bash
sudo -u lofa -H bash -lc '
  cd /home/lofa/DEV/lotot-android-spike
  ./gradlew --no-daemon --no-configuration-cache :androbd:assembleDebug
'
```

Expected APK:

```text
androbd/build/outputs/apk/debug/androbd-debug.apk
```

Validate the APK archive:

```bash
unzip -t androbd/build/outputs/apk/debug/androbd-debug.apk
```

If `apksigner` is available, print the signing certificate digest and confirm update compatibility with existing installs.

Do not deliberately uninstall existing builds merely to bypass a signing mismatch.

---

# Live UI Iteration

The repository includes:

```text
tools/lotot-ui-live.sh
```

Use it for rapid icon visual iteration when practical.

Example:

```bash
./tools/lotot-ui-live.sh start 100.68.236.4:5555
```

Restore bundled UI with:

```bash
./tools/lotot-ui-live.sh bundled 100.68.236.4:5555
```

Live mode is only for fast iteration. Final verification must use the bundled UI inside a freshly built APK.

---

# Device Deployment

Expected/known ADB endpoints include:

```text
100.68.236.4:5555
100.83.162.20:5555
100.89.34.87:5555
```

Always discover actual current availability:

```bash
adb devices -l
```

Install only to devices in `device` state.

Use streamed update install:

```bash
adb -s SERIAL install -r androbd/build/outputs/apk/debug/androbd-debug.apk
```

Do not uninstall first unless explicitly instructed.

After successful install, launch the package:

```bash
adb -s SERIAL shell monkey -p com.lotot.android -c android.intent.category.LAUNCHER 1
```

or start the known launcher activity if that is more reliable.

---

# Acceptance Criteria

The task is **not complete** until all applicable points below are true:

1. The old cartoon/generic bot icon is gone from the LoToTi UI.
2. Bottom navigation has five newly designed, coherent icons.
3. Overview no longer uses a house to represent the vehicle dashboard.
4. Coolant and oil use visibly different icons.
5. Intake is not represented by a generic wind symbol.
6. Pressure is not represented by a generic water droplet.
7. Voltage is more meaningful than a naked lightning bolt.
8. DTC/fault and diagnostic scan have dedicated automotive diagnostic iconography.
9. Demo scenarios have semantic custom icons rather than generic reused glyphs.
10. All primary icons share one stroke/geometry language.
11. Icons remain legible at bottom-navigation size.
12. Dark theme is visually correct.
13. Light theme is visually correct.
14. Existing app functionality and navigation behavior are preserved.
15. `npm --prefix lotot-ui run build` succeeds.
16. `:androbd:assembleDebug` succeeds.
17. APK validates as a proper archive.
18. APK installs as an update on available compatible devices.
19. App launches after install.
20. A visual icon audit/showcase exists for assessment.

---

# Strong Design Judgment

Do not treat every recommendation above as an immutable drawing recipe if actual on-device results reveal a better solution. Use design judgment.

However, when choosing between a generic familiar symbol and a distinctive but clear LoToTi symbol, prefer the distinctive LoToTi treatment for:

- AI
- cockpit / overview
- diagnostics
- OBD
- DTC
- core sensor families

For tiny utility actions, prefer clarity over brand novelty.

The finished application should look as if **one icon designer created the entire system for LoToTi**, not as if icons were collected from several libraries.

---

# Final Deliverable Report

When finished, report:

1. Files changed
2. Icon architecture created
3. Complete list of new semantic icon names
4. Old icons removed/replaced
5. Any icons intentionally retained and why
6. Web UI build result
7. Android build result
8. Final APK path
9. Final APK SHA-256
10. Signing certificate SHA-256
11. Devices installed successfully
12. Devices unavailable or failed, with exact reason
13. Screens visually checked
14. Remaining concerns or recommended polish

Do **not** stop after editing source files. Complete build + install + launch + visual QA as far as the connected environment allows.
