// LoToTi icon registry
// One coherent family: 24x24 viewBox, round caps/joins, optical stroke 2.1,
// simple geometric construction, legible at 16-24 px. Color is always currentColor.
//
// Design language:
//  - Industrial Soft : navigation, sensors, utilities, services
//  - Neo-ECU         : AI identity, OBD, diagnostic scan, DTC, health states

const S = {
  // ------------------------------------------------------------------
  // Primary navigation
  // ------------------------------------------------------------------
  nav_overview: (
    <>
      <path d="M4 21h16" />
      <path d="M12 21V9" />
      <path d="M12 9a6 6 0 0 1 6 6" />
      <path d="M12 9a6 6 0 0 0-6 6" />
    </>
  ),
  nav_live: (
    <>
      <path d="M3 12h3l2-6 3 12 3-10 2 4h5" />
      <circle cx="7" cy="12" r="1.4" />
      <circle cx="17" cy="12" r="1.4" />
    </>
  ),
  nav_ai: (
    <>
      <rect x="4.5" y="3" width="15" height="14" rx="3" />
      <circle cx="12" cy="10" r="3.4" />
      <path d="M10.7 10h2.6" />
      <path d="M12 6.6v-1M12 13.4v-1" />
      <path d="M9 21h6" />
      <path d="M12 17v4" />
      <path d="M7 3V1M12 3V1M17 3V1" />
    </>
  ),
  nav_health: (
    <>
      <path d="M12 3 5 5.7v5.3c0 4.5 3 8.1 7 9.5 4-1.4 7-5 7-9.5V5.7L12 3Z" />
      <path d="M8 12h2l1.5-2.5 2 5L14.5 12H16" />
    </>
  ),
  nav_more: (
    <>
      <rect x="4" y="4" width="6.6" height="6.6" rx="1.6" />
      <rect x="13.4" y="4" width="6.6" height="6.6" rx="1.6" />
      <rect x="4" y="13.4" width="6.6" height="6.6" rx="1.6" />
      <rect x="13.4" y="13.4" width="6.6" height="6.6" rx="1.6" />
    </>
  ),

  // ------------------------------------------------------------------
  // Automotive / diagnostic core
  // ------------------------------------------------------------------
  obd_connected: (
    <>
      <path d="M5 3h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z" />
      <path d="M5.5 6.5h2M9.5 6.5h2M13.5 6.5h2" />
      <path d="M12 10v3" />
      <path d="M12 16a4.5 4.5 0 0 0 4.5 4.5H19" />
      <path d="m15 18.5 1.5 1.5L19 17" />
    </>
  ),
  obd_disconnected: (
    <>
      <path d="M5 3h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z" />
      <path d="M5.5 6.5h2M9.5 6.5h2M13.5 6.5h2" />
      <path d="m10.5 12 3-2" />
      <path d="M12 10v6" />
      <path d="M8 20h8" />
    </>
  ),
  diagnostic_scan: (
    <>
      <circle cx="12" cy="12" r="8.2" />
      <circle cx="12" cy="12" r="3" />
      <path d="M12 12l4.4-4.4" />
      <path d="M4 4l2 2M20 4l-2 2M4 20l2-2M20 20l-2-2" />
    </>
  ),
  dtc_fault: (
    <>
      <rect x="4.5" y="4" width="15" height="11" rx="2.2" />
      <path d="M9 4V1.8M15 4V1.8" />
      <path d="M7.5 9.5 10 12l6.5-6.5" />
      <path d="M8.5 13.5h7" />
    </>
  ),
  vehicle: (
    <>
      <path d="M4 13l1.5-4.5A2 2 0 0 1 7.4 7h9.2a2 2 0 0 1 1.9 1.5L20 13" />
      <path d="M5 13h14a2 2 0 0 1 2 2v3H3v-3a2 2 0 0 1 2-2Z" />
      <path d="M5 18v2M19 18v2" />
      <path d="M7 15h.01M17 15h.01" />
    </>
  ),
  speed: (
    <>
      <circle cx="12" cy="14" r="7" />
      <path d="M12 14V9.5" />
      <path d="M12 14l3 1.6" />
      <path d="M6.5 6.5 5 5M17.5 6.5 19 5" />
    </>
  ),
  rpm: (
    <>
      <path d="M12 13a6.5 6.5 0 1 0 6.5-6.5" />
      <path d="M12 13V7.5" />
      <path d="M7.5 3.5 6 5M16.5 3.5 18 5" />
      <path d="M3 13h2M19 13h2" />
    </>
  ),
  engine: (
    <>
      <path d="M7 4h2M15 4h2" />
      <path d="M12 4v3" />
      <path d="M6 7h12a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2Z" />
      <path d="M9.5 11v4M14.5 11v4" />
    </>
  ),
  health_stable: (
    <>
      <path d="M12 3 5 5.7v5.3c0 4.5 3 8.1 7 9.5 4-1.4 7-5 7-9.5V5.7L12 3Z" />
      <path d="M8.5 12l2.3 2.3L15.5 9.5" />
    </>
  ),
  health_warning: (
    <>
      <path d="M12 3 5 5.7v5.3c0 4.5 3 8.1 7 9.5 4-1.4 7-5 7-9.5V5.7L12 3Z" />
      <path d="M12 8.5v4" />
      <path d="M12 15.4h.01" />
    </>
  ),
  health_critical: (
    <>
      <path d="M12 3 5 5.7v5.3c0 4.5 3 8.1 7 9.5 4-1.4 7-5 7-9.5V5.7L12 3Z" />
      <path d="m9.5 9.5 5 5" />
      <path d="m14.5 9.5-5 5" />
    </>
  ),

  // ------------------------------------------------------------------
  // Sensor family
  // ------------------------------------------------------------------
  sensor_coolant: (
    <>
      <path d="M9 4h6" />
      <path d="M12 4v6" />
      <path d="M12 13.2a3.2 3.2 0 1 1-1.9-2.9" />
      <path d="M5 20c1.4-1.7 3-1.7 4.4 0s3 1.7 4.4 0 3-1.7 4.4 0" />
    </>
  ),
  sensor_oil: (
    <>
      <path d="M6.5 3.5h8l2.5 3v12a2 2 0 0 1-2 2h-9a2 2 0 0 1-2-2v-15l2-0Z" />
      <path d="M6.5 11h7" />
      <path d="M8.5 6.5v2M11.5 6.5v2" />
    </>
  ),
  sensor_voltage: (
    <>
      <rect x="6" y="4" width="12" height="16" rx="2" />
      <path d="M9.5 4V1.8M14.5 4V1.8" />
      <path d="m12.8 7-3 4h3l-1.5 4.5 3-4h-3l1.5-4.5Z" />
    </>
  ),
  sensor_fuel: (
    <>
      <path d="M4 21V6a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v15" />
      <path d="M2 21h16" />
      <path d="M8 6.5h5" />
      <path d="M16 8h2l2 2v8.5" />
      <path d="M8 12h4" />
    </>
  ),
  sensor_intake: (
    <>
      <path d="M4 5h16" />
      <path d="M7 5v8.5a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2V5" />
      <path d="M9 10h6" />
      <path d="M10 15.5 8.5 20M14 15.5 15.5 20" />
    </>
  ),
  sensor_pressure: (
    <>
      <circle cx="12" cy="13" r="6" />
      <path d="M12 13V8.8" />
      <path d="M12 19v2.5" />
      <path d="M6 21.5h12" />
      <path d="M5 6.5 3.5 5M19 6.5 20.5 5" />
    </>
  ),
  sensor_airflow: (
    <>
      <path d="M3 8h10a3 3 0 1 0-3-3" />
      <path d="M3 12h15a3 3 0 1 1-3 3" />
      <path d="M3 16h7" />
      <circle cx="18" cy="8" r="1.7" />
    </>
  ),
  sensor_temperature: (
    <>
      <path d="M10 3.5h4" />
      <path d="M12 3.5v6" />
      <path d="M12 13a3.4 3.4 0 1 1-2-3.1" />
    </>
  ),
  sensor_gps: (
    <>
      <path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" />
      <circle cx="12" cy="10" r="2.5" />
    </>
  ),
  sensor_motion: (
    <>
      <path d="M4 9.5 8 11l3-6 3 10 2-4 4 1" />
      <circle cx="8" cy="11" r="1.3" />
      <circle cx="17" cy="12" r="1.3" />
    </>
  ),

  // ------------------------------------------------------------------
  // AI identity siblings
  // ------------------------------------------------------------------
  ai_core: (
    <>
      <rect x="4.5" y="3" width="15" height="14" rx="3" />
      <circle cx="12" cy="10" r="3.4" />
      <path d="M10.7 10h2.6" />
      <path d="M12 6.6v-1M12 13.4v-1" />
      <path d="M9 21h6" />
      <path d="M12 17v4" />
      <path d="M7 3V1M12 3V1M17 3V1" />
    </>
  ),
  ai_analyze: (
    <>
      <circle cx="12" cy="12" r="8.2" />
      <circle cx="12" cy="12" r="3" />
      <path d="M12 12l4.4-4.4" />
    </>
  ),
  ai_context: (
    <>
      <circle cx="5" cy="6" r="2" />
      <circle cx="19" cy="6" r="2" />
      <circle cx="12" cy="18" r="2" />
      <path d="M6.8 7.4 10.6 16M17.2 7.4 13.4 16" />
    </>
  ),
  ai_summary: (
    <>
      <path d="M4 6h16M4 12h16M4 18h10" />
      <path d="M14 18l6 0v0" />
    </>
  ),

  // ------------------------------------------------------------------
  // Demo scenarios
  // ------------------------------------------------------------------
  scenario_healthy: (
    <>
      <path d="M12 3 5 5.7v5.3c0 4.5 3 8.1 7 9.5 4-1.4 7-5 7-9.5V5.7L12 3Z" />
      <path d="M8.5 12l2.3 2.3L15.5 9.5" />
    </>
  ),
  scenario_cold_start: (
    <>
      <path d="M9 4h6" />
      <path d="M12 4v6" />
      <path d="M12 13.2a3.2 3.2 0 1 1-1.9-2.9" />
      <path d="M5.5 7l-2-2M18.5 7l2-2" />
    </>
  ),
  scenario_misfire: (
    <>
      <path d="M4 15h3l2-6 3 12 2-7 1.5 3H18" />
      <path d="M15 12h.01" />
      <path d="M18.5 9l3 3M21.5 9l-3 3" />
    </>
  ),
  scenario_lean: (
    <>
      <path d="M4 15a8 8 0 0 1 16 0Z" />
      <path d="M7 8 5 6M7 11l-2 2" />
      <path d="M17 8l2-2M17 11l2 2" />
      <path d="M12 15V8" />
      <path d="M12 8c-1.8-1.6-3-2.4-4.5-2.6M12 8c1.8-1.6 3-2.4 4.5-2.6" />
    </>
  ),
  scenario_catalyst: (
    <>
      <rect x="4" y="8" width="16" height="8" rx="2" />
      <path d="M8.2 10.5v3M11.3 10.5v3M14.4 10.5v3M17.5 10.5v3" />
      <path d="M2 12h2M20 12h2" />
    </>
  ),
  scenario_overheat: (
    <>
      <path d="M9 4h6" />
      <path d="M12 4v6" />
      <path d="M12 13.2a3.2 3.2 0 1 1-1.9-2.9" />
      <path d="M7 7 5 5M17 7l2-2" />
      <path d="M3 12l-1 3M21 12l1 3" />
    </>
  ),
  scenario_weak_charging: (
    <>
      <rect x="6" y="4" width="12" height="16" rx="2" />
      <path d="M9.5 4V1.8M14.5 4V1.8" />
      <path d="m13 7-4 5h3l-1 4.5 4-5h-3l1-4.5Z" />
      <path d="M12 21.5h.01" />
    </>
  ),

  // ------------------------------------------------------------------
  // Services / utilities
  // ------------------------------------------------------------------
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-4-4" />
    </>
  ),
  close: (
    <>
      <path d="M18 6 6 18" />
      <path d="m6 6 12 12" />
    </>
  ),
  chevron: <path d="m9 18 6-6-6-6" />,
  refresh: (
    <>
      <path d="M20 11a8.1 8.1 0 0 0-15.5-2M4 4v5h5" />
      <path d="M4 13a8.1 8.1 0 0 0 15.5 2M20 20v-5h-5" />
    </>
  ),
  star: <path d="m12 2.7 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.3l6.2-.9L12 2.7Z" />,
  bluetooth: <path d="m7 7 10 10-5 5V2l5 5L7 17" />,
  link: (
    <>
      <path d="M10 13a5 5 0 0 0 7.1.1l2-2a5 5 0 0 0-7.1-7.1l-1.1 1.1" />
      <path d="M14 11a5 5 0 0 0-7.1-.1l-2 2A5 5 0 0 0 12 20l1.1-1.1" />
    </>
  ),
  unlink: (
    <>
      <path d="m18.8 12.8.9-.9a5 5 0 0 0-7.1-7.1l-1.1 1.1" />
      <path d="m5.2 11.2-.9.9a5 5 0 0 0 7.1 7.1l1.1-1.1" />
      <path d="M2 2l20 20" />
    </>
  ),
  signal: (
    <>
      <path d="M3 19h.01" />
      <path d="M7 19v-4" />
      <path d="M12 19v-8" />
      <path d="M17 19V7" />
      <path d="M22 19V3" />
    </>
  ),
  play: <path d="m8 5 11 7-11 7V5Z" />,
  tool: (
    <>
      <path d="M14.7 6.3a4 4 0 0 0-5-5l2.1 2.1-2.4 2.4-2.1-2.1a4 4 0 0 0 5 5L20 16.4 16.4 20l-7.7-7.7a4 4 0 0 0-5-5l2.1 2.1" />
    </>
  ),
  sun: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </>
  ),
  moon: <path d="M21 12.8A8.5 8.5 0 1 1 11.2 3 6.8 6.8 0 0 0 21 12.8Z" />,
  location: (
    <>
      <path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" />
      <circle cx="12" cy="10" r="2.5" />
    </>
  ),
  cloud: (
    <>
      <path d="M17.5 19H7a5 5 0 0 1-.6-10A7 7 0 0 1 20 11.5 3.8 3.8 0 0 1 17.5 19Z" />
      <path d="M9 14h6M12 11v6" />
    </>
  ),
  phone: (
    <>
      <rect x="6" y="2" width="12" height="20" rx="2.5" />
      <path d="M10 18h4" />
    </>
  ),
  usb: (
    <>
      <path d="M12 2v14" />
      <path d="m9 5 3-3 3 3" />
      <path d="M12 10H7v5" />
      <circle cx="7" cy="17" r="2" />
      <path d="M12 13h5v3" />
      <rect x="15" y="16" width="4" height="4" rx=".6" />
    </>
  ),
  wifi: (
    <>
      <path d="M5 12.6a11 11 0 0 1 14 0" />
      <path d="M8.5 16a6 6 0 0 1 7 0" />
      <path d="M12 20h.01" />
    </>
  ),
  type: (
    <>
      <path d="M5 5V3h14v2" />
      <path d="M12 3v18" />
      <path d="M8 21h8" />
    </>
  ),
  sliders: (
    <>
      <path d="M4 6h16M4 12h16M4 18h16" />
      <circle cx="8" cy="6" r="2" />
      <circle cx="16" cy="12" r="2" />
      <circle cx="10" cy="18" r="2" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </>
  ),
  activity: (
    <>
      <path d="M3 12h4l2-7 4 14 2-7h6" />
    </>
  ),
};

// ------------------------------------------------------------------
// Selectable visual families
// ------------------------------------------------------------------
// Industrial Soft is the original LoToTi redesign above. Tech Line and
// Neo ECU override the most visible glyphs; every other semantic icon still
// inherits the selected family's stroke/cap geometry so the choice is global.
const TECH_LINE = {
  nav_overview: (<><path d="M4 18a8 8 0 0 1 16 0"/><path d="M12 18l3.2-5.2"/><path d="M6.5 14.5h.01M17.5 14.5h.01"/></>),
  nav_live: (<><path d="M3 13h4l2-5 3 9 3-7 2 3h4"/><circle cx="7" cy="13" r=".8"/><circle cx="17" cy="13" r=".8"/></>),
  nav_ai: (<><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="2"/><path d="M12 2v5M12 17v5M2 12h5M17 12h5"/><path d="m5 5 3.5 3.5M19 5l-3.5 3.5M5 19l3.5-3.5M19 19l-3.5-3.5"/></>),
  nav_health: (<><path d="M12 3 5.5 5.5v5.2c0 4.4 2.7 7.7 6.5 9.3 3.8-1.6 6.5-4.9 6.5-9.3V5.5L12 3Z"/><path d="M8 12h2l1.4-2.4 2 4.8 1.2-2.4H16"/></>),
  nav_more: (<><circle cx="7" cy="7" r="2.2"/><circle cx="17" cy="7" r="2.2"/><circle cx="7" cy="17" r="2.2"/><circle cx="17" cy="17" r="2.2"/></>),
  diagnostic_scan: (<><circle cx="12" cy="12" r="8"/><path d="M12 12l5-3"/><circle cx="12" cy="12" r="1.4"/><path d="M12 4v2M4 12h2M18 12h2M12 18v2"/></>),
  sensor_coolant: (<><path d="M12 4v9"/><circle cx="12" cy="16" r="3"/><path d="M9 4h6M6 21c1.3-1.4 2.7-1.4 4 0s2.7 1.4 4 0 2.7-1.4 4 0"/></>),
  sensor_voltage: (<path d="M13 2 7 13h5l-1 9 6-12h-5z"/>),
  sensor_fuel: (<><path d="M5 21V5h10v16M7 8h6"/><path d="M15 9h2l2 2v8"/><path d="M4 21h13"/></>),
  sensor_pressure: (<><path d="M5 17a7 7 0 0 1 14 0"/><path d="M12 17l3-5"/><path d="M4 20h16"/></>),
  sensor_intake: (<><path d="M3 8h12a3 3 0 1 0-3-3"/><path d="M3 13h16a2.5 2.5 0 1 1-2.5 2.5"/><path d="M3 18h8"/></>),
  health_stable: (<><path d="M12 3 5.5 5.5v5.2c0 4.4 2.7 7.7 6.5 9.3 3.8-1.6 6.5-4.9 6.5-9.3V5.5L12 3Z"/><path d="m8.5 12 2.2 2.2 4.8-5"/></>),
};

const NEO_ECU = {
  nav_overview: (<><path d="M4 7 7 4h10l3 3v11l-3 2H7l-3-2Z"/><path d="M8 16a4 4 0 0 1 8 0"/><path d="M12 16l2.5-4"/></>),
  nav_live: (<><path d="M3 12h4l2-5 3 10 3-8 2 3h4"/><path d="M7 9V5M17 9V5"/><circle cx="7" cy="4" r="1"/><circle cx="17" cy="4" r="1"/></>),
  nav_ai: (<><rect x="5" y="5" width="14" height="14" rx="1"/><path d="M8 5V2M12 5V2M16 5V2M8 22v-3M12 22v-3M16 22v-3M5 8H2M5 12H2M5 16H2M22 8h-3M22 12h-3M22 16h-3"/><path d="m12 8 4 4-4 4-4-4Z"/><circle cx="12" cy="12" r="1.2"/></>),
  nav_health: (<><path d="m12 3 7 4v6l-7 8-7-8V7Z"/><path d="M8 12h2l1.5-3 2 6 1.5-3h1"/></>),
  nav_more: (<><rect x="4" y="4" width="6" height="6"/><rect x="14" y="4" width="6" height="6"/><rect x="4" y="14" width="6" height="6"/><rect x="14" y="14" width="6" height="6"/><path d="M10 7h4M7 10v4M17 10v4M10 17h4"/></>),
  diagnostic_scan: (<><path d="m12 3 8 5v8l-8 5-8-5V8Z"/><circle cx="12" cy="12" r="4"/><path d="M12 12l5-2"/></>),
  sensor_coolant: (<><path d="M9 3h6v10l2 3-5 5-5-5 2-3Z"/><path d="M12 7v8M9 18h6"/></>),
  sensor_voltage: (<><path d="M6 4h12v16H6Z"/><path d="M9 4V2M15 4V2"/><path d="m13 7-4 6h4l-2 5 5-7h-4Z"/></>),
  sensor_fuel: (<><path d="M4 21V5h11v16Z"/><path d="M7 8h5v4H7Z"/><path d="M15 9h3l2 3v8M2 21h18"/></>),
  sensor_pressure: (<><path d="m12 4 7 5v8l-7 4-7-4V9Z"/><path d="M8 15a4 4 0 0 1 8 0"/><path d="M12 15l2-4"/></>),
  sensor_intake: (<><path d="M3 6h18v5H8v7h9"/><path d="m17 15 4 3-4 3"/><path d="M6 11v10"/></>),
  health_stable: (<><path d="m12 3 7 4v6l-7 8-7-8V7Z"/><path d="m8.5 12 2.3 2.3 4.8-5"/></>),
};

export const ICON_FAMILY_PROFILES = Object.freeze({
  'tech-line': Object.freeze({ strokeWidth: 1.65, strokeLinecap: 'round', strokeLinejoin: 'round' }),
  'industrial-soft': Object.freeze({ strokeWidth: 2.1, strokeLinecap: 'round', strokeLinejoin: 'round' }),
  'neo-ecu': Object.freeze({ strokeWidth: 2.15, strokeLinecap: 'square', strokeLinejoin: 'miter' }),
});
const FAMILY_OVERRIDES = Object.freeze({
  'tech-line': TECH_LINE,
  'industrial-soft': Object.freeze({}),
  'neo-ecu': NEO_ECU,
});

// Migration aliases: keep old generic names resolvable so no call site breaks.
const ALIASES = {
  home: 'nav_overview',
  gauge: 'speed',
  car: 'vehicle',
  thermometer: 'sensor_temperature',
  fuel: 'sensor_fuel',
  wind: 'sensor_airflow',
  droplet: 'sensor_pressure',
  bolt: 'sensor_voltage',
  leaf: 'scenario_catalyst',
  grid: 'nav_more',
  bot: 'nav_ai',
  shield: 'health_stable',
  activity: 'activity',
};

export const ICON_NAMES = Object.freeze(Object.keys(S).sort());
export const ICON_FAMILY_NAMES = Object.freeze(Object.keys(ICON_FAMILY_PROFILES));

// Semantic direction metadata. Only navigation/disclosure arrows mirror in RTL.
// Automotive symbols and physical/data-direction icons intentionally do not.
const RTL_MIRRORED_ICONS = Object.freeze(new Set([
  'chevron',
]));

function resolveIconName(name) {
  if (Object.prototype.hasOwnProperty.call(S, name)) return name;
  const alias = ALIASES[name];
  if (alias && Object.prototype.hasOwnProperty.call(S, alias)) return alias;
  return 'activity';
}

export function shouldMirrorIconInRtl(name) {
  return RTL_MIRRORED_ICONS.has(resolveIconName(name));
}

export function getIconProfile(family = 'industrial-soft') {
  return ICON_FAMILY_PROFILES[family] || ICON_FAMILY_PROFILES['industrial-soft'];
}

export function getIconPath(name, family = 'industrial-soft') {
  const resolvedName = resolveIconName(name);
  const overrides = FAMILY_OVERRIDES[family] || FAMILY_OVERRIDES['industrial-soft'];
  return overrides[resolvedName] || S[resolvedName] || S.activity;
}