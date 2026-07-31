import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

const EMPTY_READINGS = {
  vehicle_speed: null,
  engine_rpm: null,
  engine_load: null,
  module_voltage: null,
  maf: null,
};

const EMPTY_BLUETOOTH = {
  available: true,
  enabled: false,
  scanning: false,
  medium: 'classic',
  status: 'offline',
  devices: [],
  connectedDevice: null,
  selectedDevice: null,
  lastDevice: null,
  error: null,
};

const EMPTY_BUILTINS = {
  embedded: true,
  gps: { enabled: false, available: true, permission_granted: false, status: 'disabled', last_update: 0, error: null },
  sensors: { enabled: true, available: true, status: 'waiting', last_update: 0, error: null },
  mqtt: {
    enabled: false, status: 'disabled', broker: null, last_publish: 0, published_messages: 0, error: null,
    config: { protocol: 'tcp://', host: '', port: 1883, username: '', password_set: false, device_uid: '', client_id: '', prefix: '', qos: 1, retain: false, interval_seconds: 5, selected_signals: [] },
  },
};

const LABEL_OVERRIDES = {
  number_fault_codes: 'Codes défaut détectés',
  status_mil: 'Voyant moteur (MIL)',
  status_component_test: 'Moniteur composants',
  status_fuel_system: 'Moniteur système carburant',
  status_ignition_monitoring: 'Surveillance allumage',
  status_misfires: 'Moniteur ratés moteur',
  status_ac_refrigerant_test: 'Moniteur climatisation',
  status_catalyst_test: 'Moniteur catalyseur',
  status_catalyst_test_nox_monitor: 'Moniteur catalyseur / NOx',
  status_egr_system_test: 'Moniteur système EGR',
  status_evaporative_system_test: 'Moniteur système EVAP',
  status_oxygen_sensor_heater_test: 'Moniteur chauffage sonde O₂',
  status_oxygen_sensor_test: 'Moniteur sonde O₂',
  status_secondary_air_system_test: 'Moniteur air secondaire',
  engine_load_calculated: 'Charge moteur calculée',
  engine_load: 'Charge moteur absolue',
  engine_coolant_temperature: 'Température liquide moteur',
  coolant_temp: 'Température liquide moteur',
  fuel_trim_short_b1: 'Correction carburant courte B1',
  fuel_trim_long_b1: 'Correction carburant longue B1',
  fuel_trim_short_b2: 'Correction carburant courte B2',
  fuel_trim_long_b2: 'Correction carburant longue B2',
  fuel_pressure: 'Pression carburant',
  fuel_pressure_rel: 'Pression carburant relative',
  frp_relative: 'Pression rampe relative',
  intake_manifold_pressure: 'Pression collecteur admission',
  engine_speed: 'Régime moteur',
  vehicle_speed: 'Vitesse véhicule',
  ignition_timing_advance_cyl1: 'Avance allumage cylindre 1',
  timing_advance_cycle_1: 'Avance allumage',
  intake_air_temperature: 'Température air admission',
  mass_airflow: 'Débit d’air MAF',
  throttle_position_abs: 'Position papillon absolue',
  throttle_position_rel: 'Position papillon relative',
  running_time: 'Temps moteur actif',
  seconds_since_engine_start: 'Temps moteur actif',
  fuel_level: 'Niveau carburant',
  fuel_tank_level_input: 'Niveau carburant',
  barometric_pressure: 'Pression atmosphérique',
  ecu_voltage: 'Tension ECU',
  module_voltage: 'Tension module',
  ambient_air_temperature: 'Température extérieure',
  ethanol_fuel_percentage: 'Taux d’éthanol',
  accelerator_pedal_position: 'Position pédale accélérateur',
  engine_oil_temperature: 'Température huile moteur',
  fuel_injection_timing: 'Avance injection',
  engine_fuel_rate: 'Débit carburant',
  engine_torque_demand: 'Demande de couple moteur',
  engine_torque: 'Couple moteur actuel',
  engine_torque_reference: 'Couple moteur de référence',
  GPS_LATITUDE: 'Latitude GPS',
  GPS_LONGITUDE: 'Longitude GPS',
  GPS_ALTITUDE: 'Altitude GPS',
  GPS_BEARING: 'Cap GPS',
  GPS_SPEED: 'Vitesse GPS',
  ACC_X: 'Accélération latérale',
  ACC_Y: 'Accélération longitudinale',
  ACC_Z: 'Accélération verticale',
};

const CATEGORIES = [
  { id: 'all', label: 'Tout', icon: 'activity' },
  { id: 'favorites', label: 'Favoris', icon: 'star' },
  { id: 'diagnostic', label: 'Diagnostic', icon: 'shield' },
  { id: 'engine', label: 'Moteur', icon: 'gauge' },
  { id: 'driving', label: 'Conduite', icon: 'car' },
  { id: 'temperature', label: 'Températures', icon: 'thermometer' },
  { id: 'fuel', label: 'Carburant', icon: 'fuel' },
  { id: 'air', label: 'Air', icon: 'wind' },
  { id: 'pressure', label: 'Pressions', icon: 'droplet' },
  { id: 'electrical', label: 'Électrique', icon: 'bolt' },
  { id: 'emissions', label: 'Émissions', icon: 'leaf' },
  { id: 'location', label: 'GPS', icon: 'location' },
  { id: 'motion', label: 'Mouvement', icon: 'phone' },
  { id: 'other', label: 'Autres', icon: 'grid' },
];

const telemetryBridge = window.lototTelemetryBridge = window.lototTelemetryBridge || {
  status: 'offline',
  lastPayload: null,
};

const bluetoothBridge = window.lototBluetoothBridge = window.lototBluetoothBridge || {
  lastState: EMPTY_BLUETOOTH,
};

const builtinBridge = window.lototBuiltinBridge = window.lototBuiltinBridge || {
  lastState: EMPTY_BUILTINS,
};

window.lototReceiveTelemetry = (payload) => {
  try {
    const parsed = typeof payload === 'string' ? JSON.parse(payload) : payload;
    if (!parsed || typeof parsed !== 'object') return;
    telemetryBridge.lastPayload = parsed;
    if (['demo', 'live', 'connecting', 'offline', 'lost'].includes(parsed.mode)) {
      telemetryBridge.status = parsed.mode;
    }
    window.dispatchEvent(new CustomEvent('lotot:telemetry', { detail: parsed }));
  } catch (error) {
    console.error('Invalid native telemetry payload', error);
  }
};

window.lototReceiveFastTelemetry = (payload) => {
  try {
    const parsed = typeof payload === 'string' ? JSON.parse(payload) : payload;
    if (!parsed || typeof parsed !== 'object') return;
    const previous = telemetryBridge.lastPayload || {};
    telemetryBridge.lastPayload = {
      ...previous,
      ...parsed,
      readings: { ...(previous.readings || {}), ...(parsed.readings || {}) },
    };
    if (['demo', 'live', 'connecting', 'offline', 'lost'].includes(parsed.mode)) {
      telemetryBridge.status = parsed.mode;
    }
    window.dispatchEvent(new CustomEvent('lotot:fast-telemetry', { detail: parsed }));
  } catch (error) {
    console.error('Invalid native fast telemetry payload', error);
  }
};

window.lototSetStatus = (status) => {
  if (!['live', 'connecting', 'offline', 'demo', 'lost'].includes(status)) return;
  telemetryBridge.status = status;
  window.dispatchEvent(new CustomEvent('lotot:telemetry-status', { detail: { status } }));
};

window.lototSetBluetoothState = (payload) => {
  try {
    const parsed = typeof payload === 'string' ? JSON.parse(payload) : payload;
    if (!parsed || typeof parsed !== 'object') return;
    const next = { ...EMPTY_BLUETOOTH, ...parsed };
    bluetoothBridge.lastState = next;
    window.dispatchEvent(new CustomEvent('lotot:bluetooth-state', { detail: next }));
  } catch (error) {
    console.error('Invalid native Bluetooth payload', error);
  }
};

window.lototSetBuiltinState = (payload) => {
  try {
    const parsed = typeof payload === 'string' ? JSON.parse(payload) : payload;
    if (!parsed || typeof parsed !== 'object') return;
    const next = {
      ...EMPTY_BUILTINS,
      ...parsed,
      gps: { ...EMPTY_BUILTINS.gps, ...(parsed.gps || {}) },
      sensors: { ...EMPTY_BUILTINS.sensors, ...(parsed.sensors || {}) },
      mqtt: {
        ...EMPTY_BUILTINS.mqtt,
        ...(parsed.mqtt || {}),
        config: { ...EMPTY_BUILTINS.mqtt.config, ...(parsed.mqtt?.config || {}) },
      },
    };
    builtinBridge.lastState = next;
    window.dispatchEvent(new CustomEvent('lotot:builtin-state', { detail: next }));
  } catch (error) {
    console.error('Invalid native built-in services payload', error);
  }
};

const isNumericReading = (value) => value !== null
  && value !== undefined
  && value !== ''
  && Number.isFinite(Number(value));

const clamp = (value, min, max) => Math.max(min, Math.min(max, isNumericReading(value) ? Number(value) : min));
const mediumLabel = (medium) => medium === 'ble' ? 'Bluetooth LE' : 'Bluetooth classique';
const signalKey = (signal) => signal?.key || signal?.mnemonic || signal?.label || 'unknown';
const cleanText = (value) => String(value || '').trim();

function formatValue(value, decimals = null) {
  if (!isNumericReading(value)) return value === null || value === undefined || value === '' ? '—' : String(value);
  const number = Number(value);
  if (decimals !== null) return number.toFixed(decimals);
  const absolute = Math.abs(number);
  if (absolute >= 1000) return number.toFixed(0);
  if (absolute >= 100) return number.toFixed(1).replace(/\.0$/, '');
  if (absolute >= 10) return number.toFixed(1).replace(/\.0$/, '');
  return number.toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
}

function signalLabel(signal) {
  return LABEL_OVERRIDES[signal?.mnemonic] || cleanText(signal?.label) || cleanText(signal?.mnemonic) || 'Capteur OBD';
}

function categoryFor(signal) {
  const mnemonic = signal?.mnemonic || '';
  const text = `${mnemonic} ${signal?.label || ''} ${signal?.unit || ''}`.toLowerCase();
  if (signal?.source === 'gps' || /^GPS_/.test(mnemonic)) return 'location';
  if (signal?.source === 'motion' || /^ACC_[XYZ]$/.test(mnemonic)) return 'motion';
  if (/^status_|fault|number_fault|readiness|monitor/.test(text)) return 'diagnostic';
  if (/temp|temperature|coolant|therm/.test(text)) return 'temperature';
  if (/voltage|battery|electr/.test(text)) return 'electrical';
  if (/fuel|ethanol|injection|trim|tank/.test(text)) return 'fuel';
  if (/pressure|barometric|manifold|frp|evap.*pressure/.test(text)) return 'pressure';
  if (/maf|air.?flow|intake_air|airmass/.test(text)) return 'air';
  if (/o2|oxygen|lambda|catalyst|egr|emission|dpf|particulate|mil/.test(text)) return 'emissions';
  if (/engine_speed|engine.*rpm|rpm|engine|torque|throttle|timing|power|load/.test(text)) return 'engine';
  if (/speed|distance|odometer|accelerator|pedal|travel/.test(text)) return 'driving';
  return 'other';
}

function ageLabel(timestamp, now) {
  if (!Number.isFinite(Number(timestamp)) || Number(timestamp) <= 0) return '—';
  const age = Math.max(0, now - Number(timestamp));
  if (age < 1000) return 'maintenant';
  if (age < 60000) return `${Math.floor(age / 1000)} s`;
  return `${Math.floor(age / 60000)} min`;
}


const TEMPERATURE_PROFILES = {
  engine_coolant_temperature: { min: 0, max: 130, normalMin: 70, normalMax: 105, warningMax: 115, short: 'Liquide moteur' },
  coolant_temp: { min: 0, max: 130, normalMin: 70, normalMax: 105, warningMax: 115, short: 'Liquide moteur' },
  engine_oil_temperature: { min: 0, max: 150, normalMin: 70, normalMax: 110, warningMax: 125, short: 'Huile moteur' },
  intake_air_temperature: { min: -20, max: 100, normalMin: -10, normalMax: 60, warningMax: 80, short: 'Air admission' },
  ambient_air_temperature: { min: -30, max: 60, normalMin: -10, normalMax: 40, warningMax: 50, short: 'Air extérieur' },
};

function temperatureProfile(signal) {
  const mnemonic = signal?.mnemonic || '';
  if (TEMPERATURE_PROFILES[mnemonic]) return TEMPERATURE_PROFILES[mnemonic];
  return { min: -20, max: 140, normalMin: 0, normalMax: 100, warningMax: 120, short: signalLabel(signal) };
}

function assessSignal(signal) {
  if (!signal || !isNumericReading(signal.value)) return null;
  const mnemonic = signal.mnemonic || '';
  const value = Number(signal.value);
  const absolute = Math.abs(value);
  const issue = (level, message) => ({ level, message, signal });
  if (mnemonic === 'number_fault_codes' && value > 0) return issue('danger', `${formatValue(value, 0)} code(s) défaut enregistré(s)`);
  if (mnemonic === 'status_mil' && value > 0) return issue('danger', 'Voyant moteur allumé');
  if (['engine_coolant_temperature', 'coolant_temp'].includes(mnemonic)) {
    if (value > 115) return issue('danger', `Liquide moteur critique à ${formatValue(value, 0)} °C`);
    if (value > 105) return issue('warning', `Liquide moteur élevé à ${formatValue(value, 0)} °C`);
  }
  if (mnemonic === 'engine_oil_temperature') {
    if (value > 125) return issue('danger', `Huile moteur critique à ${formatValue(value, 0)} °C`);
    if (value > 110) return issue('warning', `Huile moteur élevée à ${formatValue(value, 0)} °C`);
  }
  if (mnemonic === 'intake_air_temperature') {
    if (value > 80) return issue('danger', `Air d’admission critique à ${formatValue(value, 0)} °C`);
    if (value > 60) return issue('warning', `Air d’admission chaud à ${formatValue(value, 0)} °C`);
  }
  if (['ecu_voltage', 'module_voltage'].includes(mnemonic)) {
    if (value < 11.8 || value > 15.2) return issue('danger', `Tension électrique anormale : ${formatValue(value, 2)} V`);
    if (value < 12.3 || value > 14.8) return issue('warning', `Tension électrique à surveiller : ${formatValue(value, 2)} V`);
  }
  if (['fuel_level', 'fuel_tank_level_input'].includes(mnemonic)) {
    if (value < 10) return issue('danger', `Réservoir presque vide : ${formatValue(value, 0)} %`);
    if (value < 20) return issue('warning', `Niveau carburant bas : ${formatValue(value, 0)} %`);
  }
  if (/fuel_trim_(short|long)_b[12]/.test(mnemonic)) {
    if (absolute > 25) return issue('danger', `${signalLabel(signal)} hors plage : ${formatValue(value, 1)} %`);
    if (absolute > 15) return issue('warning', `${signalLabel(signal)} élevé : ${formatValue(value, 1)} %`);
  }
  return null;
}

function toneForTemperature(value, profile) {
  if (!isNumericReading(value)) return 'unknown';
  const numeric = Number(value);
  if (numeric > profile.warningMax) return 'danger';
  if (numeric > profile.normalMax) return 'warning';
  if (numeric < profile.normalMin) return 'cold';
  return 'normal';
}

function toneLabel(tone) {
  return tone === 'danger' ? 'CRITIQUE' : tone === 'warning' ? 'ÉLEVÉ' : tone === 'cold' ? 'FROID' : tone === 'normal' ? 'NORMAL' : 'INDISPONIBLE';
}

function toneForVoltage(value) {
  if (!isNumericReading(value)) return 'unknown';
  const numeric = Number(value);
  if (numeric < 11.8 || numeric > 15.2) return 'danger';
  if (numeric < 12.3 || numeric > 14.8) return 'warning';
  return 'normal';
}

function toneForFuel(value) {
  if (!isNumericReading(value)) return 'unknown';
  const numeric = Number(value);
  if (numeric < 10) return 'danger';
  if (numeric < 20) return 'warning';
  return 'normal';
}

function Icon({ name }) {
  const paths = {
    car: <><path d="M4 13l1.5-4.5A2 2 0 0 1 7.4 7h9.2a2 2 0 0 1 1.9 1.5L20 13"/><path d="M5 13h14a2 2 0 0 1 2 2v3H3v-3a2 2 0 0 1 2-2Z"/><path d="M5 18v2M19 18v2M7 15h.01M17 15h.01"/></>,
    bolt: <path d="m13 2-8 12h7l-1 8 8-12h-7l1-8Z" />,
    gauge: <><path d="M4.9 19a9 9 0 1 1 14.2 0"/><path d="m12 13 3-3"/><path d="M12 19v.01"/></>,
    wind: <><path d="M3 8h10a3 3 0 1 0-3-3"/><path d="M3 12h15a3 3 0 1 1-3 3"/><path d="M3 16h7"/></>,
    tool: <><path d="M14.7 6.3a4 4 0 0 0-5-5l2.1 2.1-2.4 2.4-2.1-2.1a4 4 0 0 0 5 5L20 16.4 16.4 20l-7.7-7.7a4 4 0 0 0-5-5l2.1 2.1"/></>,
    play: <path d="m8 5 11 7-11 7V5Z" />,
    shield: <><path d="M12 3 4 6v5c0 5 3.4 8.7 8 10 4.6-1.3 8-5 8-10V6l-8-3Z"/><path d="m9 12 2 2 4-4"/></>,
    bluetooth: <path d="m7 7 10 10-5 5V2l5 5L7 17" />,
    refresh: <><path d="M20 11a8.1 8.1 0 0 0-15.5-2M4 4v5h5"/><path d="M4 13a8.1 8.1 0 0 0 15.5 2M20 20v-5h-5"/></>,
    close: <><path d="M18 6 6 18"/><path d="m6 6 12 12"/></>,
    link: <><path d="M10 13a5 5 0 0 0 7.1.1l2-2a5 5 0 0 0-7.1-7.1l-1.1 1.1"/><path d="M14 11a5 5 0 0 0-7.1-.1l-2 2A5 5 0 0 0 12 20l1.1-1.1"/></>,
    unlink: <><path d="m18.8 12.8.9-.9a5 5 0 0 0-7.1-7.1l-1.1 1.1"/><path d="m5.2 11.2-.9.9a5 5 0 0 0 7.1 7.1l1.1-1.1"/><path d="M2 2l20 20"/></>,
    signal: <><path d="M2 20h.01"/><path d="M7 20v-4"/><path d="M12 20v-8"/><path d="M17 20V8"/><path d="M22 20V4"/></>,
    chevron: <path d="m9 18 6-6-6-6" />,
    activity: <><path d="M3 12h4l2-7 4 14 2-7h6"/></>,
    star: <path d="m12 2.7 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.3l6.2-.9L12 2.7Z" />,
    search: <><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></>,
    thermometer: <><path d="M14 14.8V5a2 2 0 0 0-4 0v9.8a4 4 0 1 0 4 0Z"/><path d="M12 9v7"/></>,
    fuel: <><path d="M4 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16"/><path d="M2 21h16M7 7h6M16 8h2l2 2v7a2 2 0 0 0 2 2"/></>,
    droplet: <path d="M12 2s6 7 6 12a6 6 0 0 1-12 0c0-5 6-12 6-12Z" />,
    leaf: <><path d="M11 20A7 7 0 0 1 9.8 6.1C14 3 20 4 21 4c0 1 .8 8-4.2 11.8A7 7 0 0 1 11 20Z"/><path d="M2 21c0-3 1.8-5.3 5-7"/></>,
    grid: <><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></>,
    clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
    sun: <><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></>,
    moon: <path d="M21 12.8A8.5 8.5 0 1 1 11.2 3 6.8 6.8 0 0 0 21 12.8Z"/>,
    location: <><path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.5"/></>,
    cloud: <><path d="M17.5 19H7a5 5 0 0 1-.6-10A7 7 0 0 1 20 11.5 3.8 3.8 0 0 1 17.5 19Z"/><path d="M9 14h6M12 11v6"/></>,
    phone: <><rect x="6" y="2" width="12" height="20" rx="2"/><path d="M10 18h4"/></>,
    sliders: <><path d="M4 6h16M4 12h16M4 18h16"/><circle cx="8" cy="6" r="2"/><circle cx="16" cy="12" r="2"/><circle cx="10" cy="18" r="2"/></>,
  };
  return <svg viewBox="0 0 24 24" aria-hidden="true">{paths[name] || paths.activity}</svg>;
}

function RingGauge({ label, value, unit, max, decimals = 0, icon }) {
  const available = isNumericReading(value);
  const progress = available ? clamp(value, 0, max) / max * 100 : 0;
  return (
    <article className={`metric-card ${available ? '' : 'is-unavailable'}`}>
      <div className="metric-head"><span>{label}</span><Icon name={icon}/></div>
      <div className="metric-ring" style={{ '--value': `${progress}%` }}>
        <div><strong>{formatValue(value, decimals)}</strong><small>{unit}</small></div>
      </div>
    </article>
  );
}

function QuickMetric({ label, value, unit, icon }) {
  return (
    <article className={`quick-metric ${isNumericReading(value) ? '' : 'is-unavailable'}`}>
      <span><Icon name={icon}/></span>
      <div><small>{label}</small><strong>{formatValue(value)} <em>{unit}</em></strong></div>
    </article>
  );
}

function Sparkline({ values = [], tone = 'accent' }) {
  const numeric = values.filter(isNumericReading).map(Number);
  if (numeric.length < 2) return <div className="sparkline-placeholder"/>;
  const min = Math.min(...numeric);
  const max = Math.max(...numeric);
  const spread = max - min || 1;
  const points = numeric.map((value, index) => {
    const x = numeric.length === 1 ? 50 : index / (numeric.length - 1) * 100;
    const y = 28 - ((value - min) / spread * 24);
    return `${x},${y}`;
  }).join(' ');
  return <svg className={`sparkline sparkline-${tone}`} viewBox="0 0 100 32" preserveAspectRatio="none"><polyline points={points}/></svg>;
}

function TemperatureBand({ signal, historyValues }) {
  const profile = temperatureProfile(signal);
  const value = isNumericReading(signal.value) ? Number(signal.value) : null;
  const position = value === null ? 0 : (clamp(value, profile.min, profile.max) - profile.min) / (profile.max - profile.min) * 100;
  const normalStart = (profile.normalMin - profile.min) / (profile.max - profile.min) * 100;
  const normalEnd = (profile.normalMax - profile.min) / (profile.max - profile.min) * 100;
  const warningEnd = (profile.warningMax - profile.min) / (profile.max - profile.min) * 100;
  const tone = toneForTemperature(value, profile);
  return (
    <div className={`temperature-chart tone-${tone}`}>
      <div className="temperature-status"><span>{toneLabel(tone)}</span><small>Normal {profile.normalMin}–{profile.normalMax} °C</small></div>
      <div className="temperature-track" style={{ '--marker': `${position}%`, '--normal-start': `${normalStart}%`, '--normal-end': `${normalEnd}%`, '--warning-end': `${warningEnd}%` }}>
        <i className="normal-zone"/><b className="temperature-marker"/>
      </div>
      <div className="temperature-labels"><span>{profile.min}°</span><span>{profile.normalMax}° normal</span><span>{profile.warningMax}° haut</span><span>{profile.max}°</span></div>
      <Sparkline values={historyValues} tone={tone === 'danger' ? 'danger' : tone === 'warning' ? 'warning' : 'temperature'}/>
    </div>
  );
}

function VoltageBand({ signal, historyValues }) {
  const value = isNumericReading(signal.value) ? Number(signal.value) : null;
  const min = 10; const max = 16;
  const position = value === null ? 0 : (clamp(value, min, max) - min) / (max - min) * 100;
  const tone = value === null ? 'unknown' : value < 11.8 || value > 15.2 ? 'danger' : value < 12.3 || value > 14.8 ? 'warning' : 'normal';
  return <div className={`voltage-chart tone-${tone}`}><div className="voltage-track" style={{ '--marker': `${position}%` }}><b/></div><div className="voltage-labels"><span>10 V</span><span>12.3–14.8 V normal</span><span>16 V</span></div><Sparkline values={historyValues} tone={tone === 'danger' ? 'danger' : tone === 'warning' ? 'warning' : 'electrical'}/></div>;
}

function RangeChart({ signal, historyValues, category }) {
  const value = isNumericReading(signal.value) ? Number(signal.value) : null;
  const unit = signal.unit || '';
  const percentLike = unit === '%' || /level|load|throttle|pedal|trim|torque/.test(signal.mnemonic || '');
  const min = percentLike ? (/trim/.test(signal.mnemonic || '') ? -100 : 0) : isNumericReading(signal.min) ? Number(signal.min) : Math.min(0, value || 0);
  const max = percentLike ? 100 : isNumericReading(signal.max) && Number(signal.max) > min ? Number(signal.max) : Math.max(1, value || 1);
  const progress = value === null ? 0 : (clamp(value, min, max) - min) / (max - min) * 100;
  return <div className={`range-chart range-${category}`}><div className="range-track"><i style={{ width: `${progress}%` }}/><b style={{ left: `${progress}%` }}/></div><div className="range-labels"><span>{formatValue(min)}</span><span>{formatValue(max)} {unit}</span></div><Sparkline values={historyValues} tone={category}/></div>;
}

function SignalVisualization({ signal, historyValues }) {
  const category = categoryFor(signal);
  if (category === 'temperature') return <TemperatureBand signal={signal} historyValues={historyValues}/>;
  if (category === 'electrical' && /voltage/.test(signal.mnemonic || '')) return <VoltageBand signal={signal} historyValues={historyValues}/>;
  if (['fuel', 'pressure', 'air'].includes(category) || signal.unit === '%') return <RangeChart signal={signal} historyValues={historyValues} category={category}/>;
  return <Sparkline values={historyValues} tone={category}/>;
}

function SignalCard({ signal, favorite, onToggleFavorite, history, now }) {
  const key = signalKey(signal);
  const numeric = isNumericReading(signal.value);
  const historyValues = history[key] || [];
  const previous = historyValues.length > 1 ? Number(historyValues[historyValues.length - 2]) : null;
  const current = numeric ? Number(signal.value) : null;
  const trend = previous === null || current === null || Math.abs(current - previous) < 0.0001
    ? 'steady'
    : current > previous ? 'up' : 'down';
  const assessment = assessSignal(signal);
  const category = categoryFor(signal);

  return (
    <article className={`signal-card category-${category} ${assessment ? `has-${assessment.level}` : ''}`}>
      <header>
        <div>
          <span>{CATEGORIES.find((item) => item.id === category)?.label || 'Autres'}</span>
          <h3>{signalLabel(signal)}</h3>
        </div>
        <button type="button" className={favorite ? 'favorite-button is-active' : 'favorite-button'} onClick={() => onToggleFavorite(key)} aria-label={favorite ? 'Retirer des favoris' : 'Ajouter aux favoris'}>
          <Icon name="star"/>
        </button>
      </header>
      <div className="signal-value-row">
        <div className="signal-value"><strong>{formatValue(signal.value)}</strong><span>{signal.unit || ''}</span></div>
        <span className={`trend trend-${trend}`}>{trend === 'up' ? '↑' : trend === 'down' ? '↓' : '—'}</span>
      </div>
      {assessment && <div className={`signal-assessment ${assessment.level}`}>{assessment.message}</div>}
      <SignalVisualization signal={signal} historyValues={historyValues}/>
      <footer>
        <code>{signal.source ? `${signal.source === 'gps' ? 'GPS intégré' : signal.source === 'motion' ? 'Capteur téléphone' : signal.source} · ${signal.mnemonic || 'capteur'}` : `PID ${Number(signal.pid || 0).toString(16).toUpperCase().padStart(2, '0')} · ${signal.mnemonic || 'capteur'}`}</code>
        <span><Icon name="clock"/>{ageLabel(signal.updated_at, now)}</span>
      </footer>
    </article>
  );
}

function OverviewMetric({ label, value, unit, icon, tone = 'neutral', detail }) {
  return <article className={`overview-metric tone-${tone}`}><span><Icon name={icon}/></span><div><small>{label}</small><strong>{formatValue(value)} <em>{unit}</em></strong>{detail && <p>{detail}</p>}</div></article>;
}

function DriveOverview({ readings, valueOf, historyOf }) {
  const speed = readings.vehicle_speed;
  const rpm = readings.engine_rpm;
  const load = readings.engine_load;
  const throttle = valueOf('accelerator_pedal_position', 'throttle_position_rel', 'throttle_position_abs');
  const torque = valueOf('engine_torque');
  const speedProgress = isNumericReading(speed) ? clamp(speed, 0, 240) / 240 * 100 : 0;
  const rpmProgress = isNumericReading(rpm) ? clamp(rpm, 0, 7000) / 7000 * 100 : 0;
  return (
    <section className="drive-overview">
      <header><div><span>CONDUITE</span><strong>En temps réel</strong></div><Icon name="gauge"/></header>
      <div className="drive-main-values">
        <div className="speed-value"><strong>{formatValue(speed, 0)}</strong><span>km/h</span></div>
        <div className="rpm-value"><small>RÉGIME</small><strong>{formatValue(rpm, 0)}</strong><span>tr/min</span></div>
      </div>
      <div className="drive-bars">
        <div><span>Vitesse</span><i><b style={{ width: `${speedProgress}%` }}/></i></div>
        <div><span>RPM</span><i className="rpm-scale"><b style={{ width: `${rpmProgress}%` }}/></i></div>
      </div>
      <Sparkline values={historyOf('vehicle_speed')} tone="driving"/>
      <footer><span><small>CHARGE</small><strong>{formatValue(load, 0)}%</strong></span><span><small>ACCÉL.</small><strong>{formatValue(throttle, 0)}%</strong></span><span><small>COUPLE</small><strong>{formatValue(torque, 0)}%</strong></span></footer>
    </section>
  );
}

function HealthOverview({ alerts, signals, lastCapturedAt, now }) {
  const dangerCount = alerts.filter((item) => item.level === 'danger').length;
  const warningCount = alerts.filter((item) => item.level === 'warning').length;
  const tone = dangerCount ? 'danger' : warningCount ? 'warning' : signals.length ? 'normal' : 'unknown';
  const primary = alerts.find((item) => item.level === 'danger') || alerts[0];
  const title = dangerCount ? 'Action requise' : warningCount ? 'À surveiller' : signals.length ? 'Paramètres stables' : 'En attente';
  return (
    <section className={`health-overview tone-${tone}`}>
      <header><span><Icon name="shield"/></span><i/></header>
      <strong>{title}</strong>
      <p>{primary?.message || (signals.length ? `${signals.length} signaux analysés en direct` : 'Connectez le véhicule pour analyser les seuils.')}</p>
      <div className="health-counts"><span><b>{dangerCount}</b><small>critique</small></span><span><b>{warningCount}</b><small>à suivre</small></span></div>
      <footer>{ageLabel(lastCapturedAt, now)}</footer>
    </section>
  );
}

function DeviceRow({ device, connected, selected, connecting, onConnect }) {
  const isConnected = connected?.address === device.address;
  const isConnecting = connecting && selected?.address === device.address;
  return (
    <button type="button" className={`device-row ${isConnected ? 'is-connected' : ''} ${isConnecting ? 'is-connecting' : ''}`} onClick={() => onConnect(device)} disabled={isConnected || connecting}>
      <span className="device-icon"><Icon name="bluetooth"/></span>
      <span className="device-copy">
        <strong>{device.name || 'Adaptateur Bluetooth'}</strong>
        <small>{device.address}</small>
        <span className="device-badges">
          {device.paired && <em>ASSOCIÉ</em>}
          {Number.isFinite(device.rssi) && <em className="signal-badge"><Icon name="signal"/>{device.rssi} dBm</em>}
        </span>
      </span>
      <span className="device-action">{isConnected ? 'CONNECTÉ' : isConnecting ? 'CONNEXION…' : 'CONNECTER'}</span>
    </button>
  );
}

function ConnectionSheet({ open, onClose, bluetooth, medium, setMedium, onScan, onConnect, onDisconnect }) {
  useEffect(() => {
    if (!open) return undefined;
    document.body.classList.add('sheet-open');
    return () => document.body.classList.remove('sheet-open');
  }, [open]);

  if (!open) return null;
  const connecting = bluetooth.status === 'connecting';
  const devices = [...(bluetooth.devices || [])].sort((a, b) => {
    if (a.paired !== b.paired) return a.paired ? -1 : 1;
    return (b.rssi ?? -999) - (a.rssi ?? -999);
  });

  return (
    <div className="sheet-backdrop" role="presentation" onClick={onClose}>
      <section className="connection-sheet" role="dialog" aria-modal="true" aria-label="Connexion Bluetooth" onClick={(event) => event.stopPropagation()}>
        <div className="sheet-handle"/>
        <header className="sheet-header">
          <div><span>CONNEXION OBD</span><h2>Choisir un adaptateur</h2></div>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Fermer"><Icon name="close"/></button>
        </header>

        {bluetooth.connectedDevice && (
          <article className="connected-banner">
            <span className="connected-icon"><Icon name="link"/></span>
            <div><small>ADAPTATEUR ACTIF</small><strong>{bluetooth.connectedDevice.name}</strong><span>{bluetooth.connectedDevice.address} · {mediumLabel(bluetooth.connectedDevice.medium)}</span></div>
            <button type="button" onClick={onDisconnect}><Icon name="unlink"/><span>Déconnecter</span></button>
          </article>
        )}

        <div className="medium-tabs" role="tablist">
          <button type="button" className={medium === 'classic' ? 'is-active' : ''} onClick={() => setMedium('classic')}>CLASSIQUE</button>
          <button type="button" className={medium === 'ble' ? 'is-active' : ''} onClick={() => setMedium('ble')}>BLE</button>
        </div>

        <div className="scan-toolbar">
          <div><strong>{mediumLabel(medium)}</strong><span>{devices.length} appareil{devices.length === 1 ? '' : 's'}</span></div>
          <button type="button" onClick={() => onScan(medium)} disabled={bluetooth.scanning}><Icon name="refresh"/><span>{bluetooth.scanning ? 'Recherche…' : 'Actualiser'}</span></button>
        </div>

        {bluetooth.error && <div className="connection-error">{bluetooth.error}</div>}

        <div className="device-list">
          {devices.map((device) => (
            <DeviceRow key={`${device.medium}:${device.address}`} device={device} connected={bluetooth.connectedDevice} selected={bluetooth.selectedDevice} connecting={connecting} onConnect={onConnect}/>
          ))}
          {!devices.length && (
            <div className="empty-devices">
              <span className={bluetooth.scanning ? 'scanner-orbit is-scanning' : 'scanner-orbit'}><Icon name="bluetooth"/></span>
              <strong>{bluetooth.scanning ? 'Recherche des adaptateurs…' : 'Aucun adaptateur trouvé'}</strong>
              <p>Allumez le boîtier OBD, rapprochez-le du téléphone puis relancez la recherche.</p>
            </div>
          )}
        </div>
        <p className="sheet-note">Les adaptateurs ELM327 classiques doivent parfois être associés dans les réglages Bluetooth Android avec le code 1234 ou 0000.</p>
      </section>
    </div>
  );
}


function serviceLabel(service, kind) {
  if (!service?.enabled) return 'DÉSACTIVÉ';
  if (service.status === 'active' || service.status === 'online') return 'ACTIF';
  if (service.status === 'permission') return 'AUTORISATION';
  if (service.status === 'configuration') return 'À CONFIGURER';
  if (service.status === 'error' || service.status === 'unavailable') return 'ERREUR';
  if (service.status === 'connecting') return 'CONNEXION';
  return kind === 'mqtt' ? 'EN ATTENTE DU FLUX' : 'EN ATTENTE';
}

function ServiceTile({ icon, title, service, kind, detail, onClick }) {
  const healthy = ['active', 'online'].includes(service?.status);
  const warning = ['permission', 'configuration', 'error', 'unavailable'].includes(service?.status);
  return (
    <button type="button" className={`service-tile ${healthy ? 'is-active' : ''} ${warning ? 'is-warning' : ''}`} onClick={onClick}>
      <span className="service-icon"><Icon name={icon}/></span>
      <span className="service-copy"><small>{title}</small><strong>{serviceLabel(service, kind)}</strong><em>{detail}</em></span>
      <span className="service-chevron"><Icon name="chevron"/></span>
    </button>
  );
}

function SwitchRow({ checked, onChange, title, description, disabled = false }) {
  return (
    <label className={`switch-row ${disabled ? 'is-disabled' : ''}`}>
      <span><strong>{title}</strong><small>{description}</small></span>
      <input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} disabled={disabled}/>
      <i/>
    </label>
  );
}

function ServicesSheet({ open, onClose, services, signals, onSave, onRequestLocation, onPublishNow }) {
  const [gpsEnabled, setGpsEnabled] = useState(false);
  const [sensorsEnabled, setSensorsEnabled] = useState(true);
  const [mqtt, setMqtt] = useState({ ...EMPTY_BUILTINS.mqtt.config, enabled: false, password: '' });

  useEffect(() => {
    if (!open) return;
    setGpsEnabled(Boolean(services.gps?.enabled));
    setSensorsEnabled(Boolean(services.sensors?.enabled));
    setMqtt({
      ...EMPTY_BUILTINS.mqtt.config,
      ...(services.mqtt?.config || {}),
      enabled: Boolean(services.mqtt?.enabled),
      password: '',
    });
  }, [open, services]);

  if (!open) return null;
  const selected = Array.isArray(mqtt.selected_signals) ? mqtt.selected_signals : [];
  const updateMqtt = (key, value) => setMqtt((current) => ({ ...current, [key]: value }));
  const toggleSignal = (mnemonic) => updateMqtt('selected_signals', selected.includes(mnemonic)
    ? selected.filter((item) => item !== mnemonic)
    : [...selected, mnemonic]);
  const save = () => {
    const mqttPayload = {
      enabled: Boolean(mqtt.enabled),
      protocol: mqtt.protocol,
      host: mqtt.host.trim(),
      port: Number(mqtt.port) || 1883,
      username: mqtt.username,
      device_uid: mqtt.device_uid.trim(),
      client_id: mqtt.client_id,
      qos: Number(mqtt.qos) || 0,
      retain: Boolean(mqtt.retain),
      interval_seconds: Math.max(1, Number(mqtt.interval_seconds) || 5),
      selected_signals: selected,
    };
    if (mqtt.password) mqttPayload.password = mqtt.password;
    onSave({ gps_enabled: gpsEnabled, sensors_enabled: sensorsEnabled, mqtt: mqttPayload });
  };

  return (
    <div className="sheet-backdrop services-backdrop" role="presentation" onClick={onClose}>
      <section className="connection-sheet services-sheet" role="dialog" aria-modal="true" aria-label="Services intégrés" onClick={(event) => event.stopPropagation()}>
        <div className="sheet-handle"/>
        <header className="sheet-header">
          <div><span>SERVICES NATIFS LOTOT</span><h2>Sources et synchronisation</h2></div>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Fermer"><Icon name="close"/></button>
        </header>

        <section className="service-config-block">
          <div className="service-config-title"><span><Icon name="location"/></span><div><strong>Position GPS intégrée</strong><small>Latitude, longitude, altitude, cap et vitesse GPS</small></div></div>
          <SwitchRow checked={gpsEnabled} onChange={setGpsEnabled} title="Activer la position" description="Alimente le tableau de bord et MQTT sans application séparée." disabled={!services.gps?.available}/>
          {gpsEnabled && !services.gps?.permission_granted && <button className="permission-button" type="button" onClick={onRequestLocation}><Icon name="location"/><span>Autoriser la localisation</span></button>}
          {services.gps?.error && <p className="inline-service-error">{services.gps.error}</p>}
        </section>

        <section className="service-config-block">
          <div className="service-config-title"><span><Icon name="phone"/></span><div><strong>Capteurs du téléphone</strong><small>Accélérations latérale, longitudinale et verticale</small></div></div>
          <SwitchRow checked={sensorsEnabled} onChange={setSensorsEnabled} title="Activer l’accéléromètre" description="Échantillonnage natif à 10 Hz, affichage réduit à la cadence du dashboard." disabled={!services.sensors?.available}/>
        </section>

        <section className="service-config-block mqtt-config-block">
          <div className="service-config-title"><span><Icon name="cloud"/></span><div><strong>Publication MQTT intégrée</strong><small>Un topic par signal plus un snapshot JSON complet</small></div></div>
          <SwitchRow checked={Boolean(mqtt.enabled)} onChange={(value) => updateMqtt('enabled', value)} title="Activer MQTT" description="Connexion automatique au broker lorsque des données sont disponibles."/>
          <div className="mqtt-form">
            <label><span>Protocole</span><select value={mqtt.protocol} onChange={(event) => updateMqtt('protocol', event.target.value)}><option value="tcp://">TCP</option><option value="ssl://">SSL/TLS</option><option value="ws://">WebSocket</option><option value="wss://">WebSocket sécurisé</option></select></label>
            <label className="is-wide"><span>Serveur MQTT</span><input value={mqtt.host} onChange={(event) => updateMqtt('host', event.target.value)} placeholder="mqtt.example.com"/></label>
            <label><span>Port</span><input type="number" value={mqtt.port} onChange={(event) => updateMqtt('port', event.target.value)} inputMode="numeric"/></label>
            <label><span>Intervalle</span><div className="input-unit"><input type="number" min="1" value={mqtt.interval_seconds} onChange={(event) => updateMqtt('interval_seconds', event.target.value)}/><em>s</em></div></label>
            <label className="is-wide"><span>Identifiant appareil Django</span><input value={mqtt.device_uid} onChange={(event) => updateMqtt('device_uid', event.target.value)} placeholder="demo-obd-001" autoCapitalize="none"/><small className="field-hint">Topic : LotoT/devices/{mqtt.device_uid?.trim() || 'android-xxxxxx'}/snapshot</small></label>
            <label className="is-wide"><span>Identifiant client MQTT</span><input value={mqtt.client_id} onChange={(event) => updateMqtt('client_id', event.target.value)} placeholder="Généré automatiquement"/></label>
            <label><span>Utilisateur</span><input value={mqtt.username} onChange={(event) => updateMqtt('username', event.target.value)} autoCapitalize="none"/></label>
            <label><span>Mot de passe</span><input type="password" value={mqtt.password} onChange={(event) => updateMqtt('password', event.target.value)} placeholder={mqtt.password_set ? 'Enregistré · laisser vide' : 'Facultatif'}/></label>
            <label><span>QoS</span><select value={mqtt.qos} onChange={(event) => updateMqtt('qos', event.target.value)}><option value="0">0 · rapide</option><option value="1">1 · confirmé</option><option value="2">2 · exactement une fois</option></select></label>
            <SwitchRow checked={Boolean(mqtt.retain)} onChange={(value) => updateMqtt('retain', value)} title="Messages retenus" description="Déconseillé pour la télémétrie live : peut rejouer une ancienne mesure."/>
          </div>

          <div className="signal-publish-head"><div><strong>Signaux publiés</strong><small>{selected.length ? `${selected.length} sélectionné(s)` : 'Tous les signaux live'}</small></div><button type="button" onClick={() => updateMqtt('selected_signals', [])}>TOUT PUBLIER</button></div>
          <div className="publish-signal-list">
            {signals.map((signal) => {
              const mnemonic = signal.mnemonic || signalKey(signal);
              const active = !selected.length || selected.includes(mnemonic);
              return <button type="button" key={signalKey(signal)} className={active ? 'is-selected' : ''} onClick={() => toggleSignal(mnemonic)}><i/><span>{signalLabel(signal)}</span><code>{mnemonic}</code></button>;
            })}
            {!signals.length && <p>Les signaux apparaîtront ici après la première session OBD ou GPS.</p>}
          </div>
          {services.mqtt?.error && <p className="inline-service-error">{services.mqtt.error}</p>}
          <button className="mqtt-test-button" type="button" onClick={onPublishNow} disabled={!services.mqtt?.enabled}><Icon name="cloud"/><span>Publier un test maintenant</span><em>{serviceLabel(services.mqtt, 'mqtt')}</em></button>
        </section>

        <div className="services-actions"><button type="button" className="secondary" onClick={onClose}>Annuler</button><button type="button" className="primary" onClick={save}>Enregistrer</button></div>
      </section>
    </div>
  );
}

function App() {
  const retainedPayload = telemetryBridge.lastPayload || {};
  const [readings, setReadings] = useState({ ...EMPTY_READINGS, ...(retainedPayload.readings || {}) });
  const [signals, setSignals] = useState(Array.isArray(retainedPayload.signals) ? retainedPayload.signals : []);
  const [history, setHistory] = useState({});
  const [status, setStatus] = useState(telemetryBridge.status || 'offline');
  const [bluetooth, setBluetooth] = useState({ ...EMPTY_BLUETOOTH, ...(bluetoothBridge.lastState || {}) });
  const [services, setServices] = useState({ ...EMPTY_BUILTINS, ...(builtinBridge.lastState || {}) });
  const [nativeAvailable, setNativeAvailable] = useState(Boolean(window.LotoTNative));
  const [connectionOpen, setConnectionOpen] = useState(false);
  const [servicesOpen, setServicesOpen] = useState(false);
  const [medium, setMediumState] = useState(bluetooth.medium || 'classic');
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('all');
  const [lastCapturedAt, setLastCapturedAt] = useState(Number(retainedPayload.captured_at) || 0);
  const [packetCount, setPacketCount] = useState(0);
  const [now, setNow] = useState(Date.now());
  const [theme, setTheme] = useState(() => localStorage.getItem('lotot-theme') === 'light' ? 'light' : 'dark');
  const [favorites, setFavorites] = useState(() => {
    try {
      const saved = JSON.parse(localStorage.getItem('lotot-signal-favorites') || '[]');
      return Array.isArray(saved) ? saved : [];
    } catch (_) {
      return [];
    }
  });

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('lotot-theme', theme);
    window.LotoTNative?.setTheme?.(theme);
  }, [theme]);

  useEffect(() => {
    localStorage.setItem('lotot-signal-favorites', JSON.stringify(favorites));
  }, [favorites]);

  useEffect(() => {
    const onTelemetry = (event) => {
      const payload = event.detail || {};
      if (payload.readings) setReadings((current) => ({ ...current, ...payload.readings }));
      if (Array.isArray(payload.signals)) {
        const validSignals = payload.signals.filter((signal) => signal && signal.value !== null && signal.value !== undefined);
        setSignals(validSignals);
        setHistory((currentHistory) => {
          const next = { ...currentHistory };
          validSignals.forEach((signal) => {
            if (!isNumericReading(signal.value)) return;
            const key = signalKey(signal);
            const values = [...(next[key] || []), Number(signal.value)];
            next[key] = values.slice(-30);
          });
          return next;
        });
      }
      if (payload.mode) setStatus(payload.mode);
      if (payload.captured_at) setLastCapturedAt(Number(payload.captured_at));
      setPacketCount((count) => count + 1);
    };
    const onFastTelemetry = (event) => {
      const payload = event.detail || {};
      if (payload.readings) setReadings((current) => ({ ...current, ...payload.readings }));
      if (payload.mode) setStatus(payload.mode);
      if (payload.captured_at) setLastCapturedAt(Number(payload.captured_at));
    };
    const onStatus = (event) => setStatus(event.detail?.status || 'offline');
    const onBluetooth = (event) => {
      setBluetooth(event.detail || EMPTY_BLUETOOTH);
      if (event.detail?.medium) setMediumState(event.detail.medium);
    };
    const onBuiltins = (event) => setServices(event.detail || EMPTY_BUILTINS);
    window.addEventListener('lotot:telemetry', onTelemetry);
    window.addEventListener('lotot:fast-telemetry', onFastTelemetry);
    window.addEventListener('lotot:telemetry-status', onStatus);
    window.addEventListener('lotot:bluetooth-state', onBluetooth);
    window.addEventListener('lotot:builtin-state', onBuiltins);
    setNativeAvailable(Boolean(window.LotoTNative));
    window.LotoTNative?.ready?.();
    return () => {
      window.removeEventListener('lotot:telemetry', onTelemetry);
      window.removeEventListener('lotot:fast-telemetry', onFastTelemetry);
      window.removeEventListener('lotot:telemetry-status', onStatus);
      window.removeEventListener('lotot:bluetooth-state', onBluetooth);
      window.removeEventListener('lotot:builtin-state', onBuiltins);
    };
  }, []);

  const state = useMemo(() => ({
    live: ['live', 'demo'].includes(status),
    lost: status === 'lost',
    label: status === 'demo'
      ? 'MODE DÉMO'
      : status === 'live'
        ? 'EN DIRECT'
        : status === 'connecting'
          ? 'CONNEXION'
          : status === 'lost'
            ? 'CONNEXION PERDUE'
            : 'HORS LIGNE',
  }), [status]);

  const signalMap = useMemo(() => {
    const map = new Map();
    signals.forEach((signal) => map.set(signal.mnemonic, signal));
    return map;
  }, [signals]);

  const valueOf = (...mnemonics) => {
    for (const mnemonic of mnemonics) {
      const signal = signalMap.get(mnemonic);
      if (signal && signal.value !== null && signal.value !== undefined) return signal.value;
    }
    return null;
  };


  const historyOf = (...mnemonics) => {
    for (const mnemonic of mnemonics) {
      const signal = signalMap.get(mnemonic);
      if (signal) return history[signalKey(signal)] || [];
    }
    return [];
  };

  const liveAlerts = useMemo(() => signals.map(assessSignal).filter(Boolean).sort((a, b) => {
    const rank = { danger: 2, warning: 1 };
    return (rank[b.level] || 0) - (rank[a.level] || 0);
  }), [signals]);

  const categoryCounts = useMemo(() => signals.reduce((counts, signal) => {
    const signalCategory = categoryFor(signal);
    counts[signalCategory] = (counts[signalCategory] || 0) + 1;
    return counts;
  }, {}), [signals]);

  const filteredSignals = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return signals
      .filter((signal) => {
        const key = signalKey(signal);
        if (category === 'favorites' && !favorites.includes(key)) return false;
        if (!['all', 'favorites'].includes(category) && categoryFor(signal) !== category) return false;
        if (!normalizedQuery) return true;
        return `${signalLabel(signal)} ${signal.mnemonic || ''} ${signal.unit || ''}`.toLowerCase().includes(normalizedQuery);
      })
      .sort((a, b) => {
        const favoriteDifference = Number(favorites.includes(signalKey(b))) - Number(favorites.includes(signalKey(a)));
        if (favoriteDifference) return favoriteDifference;
        const categoryDifference = categoryFor(a).localeCompare(categoryFor(b));
        if (categoryDifference) return categoryDifference;
        return signalLabel(a).localeCompare(signalLabel(b));
      });
  }, [signals, category, query, favorites]);

  const connected = bluetooth.connectedDevice;
  const selectedDevice = bluetooth.selectedDevice;
  const lastDevice = bluetooth.lastDevice;
  const visibleDevice = connected || selectedDevice || (status === 'lost' ? lastDevice : null);
  const speedAvailable = isNumericReading(readings.vehicle_speed);
  const speedProgress = speedAvailable ? clamp(readings.vehicle_speed, 0, 240) / 240 * 270 : 0;
  const toggleFavorite = (key) => setFavorites((current) => current.includes(key) ? current.filter((entry) => entry !== key) : [...current, key]);
  const lastAge = lastCapturedAt ? Math.max(0, now - lastCapturedAt) : null;
  const fluxLabel = !['live', 'demo'].includes(status) ? 'Flux arrêté' : lastAge === null ? 'Aucun flux' : lastAge < 1500 ? 'Flux instantané' : `${Math.floor(lastAge / 1000)} s de retard`;

  const diagnosticCopy = signals.length
    ? { title: `${signals.length} capteur${signals.length === 1 ? '' : 's'} en direct`, body: `Dernier paquet reçu ${ageLabel(lastCapturedAt, now)} · ${packetCount} mises à jour depuis l’ouverture.` }
    : connected
      ? { title: 'Connexion active · attente des PIDs', body: 'L’adaptateur répond, mais aucune mesure valide n’a encore été décodée.' }
      : status === 'demo'
        ? { title: 'Simulation AndrOBD active', body: 'Les valeurs de démonstration alimentent le cockpit.' }
        : status === 'lost'
          ? { title: 'Connexion Bluetooth perdue', body: `${lastDevice?.name || 'L’adaptateur'} ne répond plus. La session a été arrêtée automatiquement.` }
          : { title: 'Prêt pour le diagnostic', body: 'Connectez un adaptateur ou lancez la simulation.' };

  const openConnection = () => {
    setConnectionOpen(true);
    window.LotoTNative?.scanBluetooth?.(medium);
  };
  const setMedium = (nextMedium) => {
    setMediumState(nextMedium);
    window.LotoTNative?.scanBluetooth?.(nextMedium);
  };
  const connectDevice = (device) => window.LotoTNative?.connectBluetooth?.(device.address, device.medium || medium);
  const reconnectLastDevice = () => lastDevice ? connectDevice(lastDevice) : openConnection();
  const connectionAction = status === 'lost' && lastDevice ? reconnectLastDevice : openConnection;

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="brand">Loto<span>T</span></div>
        <button className={`status-pill ${state.live ? 'is-live' : ''} ${state.lost ? 'is-lost' : ''}`} type="button" onClick={openConnection}><i/><span>{state.label}</span></button>
        <button className="theme-toggle" type="button" onClick={() => setTheme((current) => current === 'dark' ? 'light' : 'dark')} aria-label={theme === 'dark' ? 'Activer le thème clair' : 'Activer le thème sombre'}><Icon name={theme === 'dark' ? 'sun' : 'moon'}/></button>
      </header>

      <section className={`connection-summary ${state.live ? 'is-live' : ''} ${state.lost ? 'is-lost' : ''}`}>
        <button type="button" onClick={connectionAction} disabled={!nativeAvailable}>
          <span className="connection-icon"><Icon name={state.lost ? 'unlink' : connected ? 'link' : 'bluetooth'}/></span>
          <span className="connection-copy"><small>{state.label}</small><strong>{connected?.name || (state.lost ? lastDevice?.name || 'Connexion perdue' : 'Connecter un adaptateur')}</strong></span>
          <span className="connection-meta"><em>{signals.length} signaux</em><small>{fluxLabel}</small></span>
          <Icon name="chevron"/>
        </button>
      </section>

      <section className="overview-dashboard">
        <DriveOverview readings={readings} valueOf={valueOf} historyOf={historyOf}/>
        <HealthOverview alerts={liveAlerts} signals={signals} lastCapturedAt={lastCapturedAt} now={now}/>
      </section>

      <section className="critical-overview">
        <OverviewMetric label="LIQUIDE" value={valueOf('engine_coolant_temperature', 'coolant_temp')} unit="°C" icon="thermometer" tone={toneForTemperature(valueOf('engine_coolant_temperature', 'coolant_temp'), TEMPERATURE_PROFILES.engine_coolant_temperature)} detail="70–105 normal"/>
        <OverviewMetric label="HUILE" value={valueOf('engine_oil_temperature')} unit="°C" icon="thermometer" tone={toneForTemperature(valueOf('engine_oil_temperature'), TEMPERATURE_PROFILES.engine_oil_temperature)} detail="70–110 normal"/>
        <OverviewMetric label="TENSION" value={readings.module_voltage ?? valueOf('ecu_voltage')} unit="V" icon="bolt" tone={toneForVoltage(readings.module_voltage ?? valueOf('ecu_voltage'))} detail="12.3–14.8 V"/>
        <OverviewMetric label="CARBURANT" value={valueOf('fuel_level', 'fuel_tank_level_input')} unit="%" icon="fuel" tone={toneForFuel(valueOf('fuel_level', 'fuel_tank_level_input'))} detail={`${formatValue(valueOf('engine_fuel_rate'), 1)} L/h`}/>
        <OverviewMetric label="ADMISSION" value={valueOf('intake_manifold_pressure')} unit="kPa" icon="wind" detail={`${formatValue(valueOf('intake_air_temperature'), 0)} °C`}/>
        <OverviewMetric label="PRESSION" value={valueOf('fuel_pressure')} unit="kPa" icon="droplet" detail={`MAF ${formatValue(readings.maf, 1)} g/s`}/>
      </section>

      <section className={`health-card compact-health ${liveAlerts.length ? 'has-alerts' : ''}`}>
        <span className="health-icon"><Icon name="shield"/></span>
        <div><strong>{liveAlerts.length ? `${liveAlerts.length} point${liveAlerts.length > 1 ? 's' : ''} à vérifier` : diagnosticCopy.title}</strong><p>{liveAlerts[0]?.message || diagnosticCopy.body}</p></div>
      </section>

      <section className="builtins-panel">
        <header className="builtins-header"><div><span>SERVICES INTÉGRÉS</span><h2>Sources natives et cloud</h2></div><button type="button" onClick={() => setServicesOpen(true)}><Icon name="sliders"/><span>Configurer</span></button></header>
        <div className="service-grid">
          <ServiceTile icon="location" title="POSITION GPS" service={services.gps} detail={services.gps?.last_update ? ageLabel(services.gps.last_update, now) : services.gps?.permission_granted ? 'Prêt à localiser' : 'Permission requise'} onClick={() => setServicesOpen(true)}/>
          <ServiceTile icon="phone" title="CAPTEURS TÉLÉPHONE" service={services.sensors} detail={services.sensors?.last_update ? ageLabel(services.sensors.last_update, now) : 'Accéléromètre natif'} onClick={() => setServicesOpen(true)}/>
          <ServiceTile icon="cloud" title="SYNCHRONISATION MQTT" kind="mqtt" service={services.mqtt} detail={services.mqtt?.last_publish ? `Publié ${ageLabel(services.mqtt.last_publish, now)}` : services.mqtt?.broker || 'Broker non configuré'} onClick={() => setServicesOpen(true)}/>
        </div>
        <p className="builtins-note">Ces services font partie de LotoT. Aucun APK complémentaire ni système de plugin n’est requis.</p>
      </section>

      <section className="data-panel">
        <header className="data-panel-header">
          <div><span>EXPLORATEUR OBD</span><h2>Toutes les données live</h2></div>
          <strong>{filteredSignals.length}/{signals.length}</strong>
        </header>

        <label className="search-box">
          <Icon name="search"/>
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Rechercher un capteur, PID ou unité…" />
          {query && <button type="button" onClick={() => setQuery('')} aria-label="Effacer"><Icon name="close"/></button>}
        </label>

        <nav className="category-tabs" aria-label="Catégories des capteurs">
          {CATEGORIES.map((item) => {
            const count = item.id === 'all' ? signals.length : item.id === 'favorites' ? favorites.filter((key) => signals.some((signal) => signalKey(signal) === key)).length : (categoryCounts[item.id] || 0);
            if (!['all', 'favorites'].includes(item.id) && count === 0) return null;
            return <button type="button" key={item.id} className={category === item.id ? 'is-active' : ''} onClick={() => setCategory(item.id)}><Icon name={item.icon}/><span>{item.label}</span><em>{count}</em></button>;
          })}
        </nav>

        <div className="signal-grid">
          {filteredSignals.map((signal) => (
            <SignalCard key={signalKey(signal)} signal={signal} favorite={favorites.includes(signalKey(signal))} onToggleFavorite={toggleFavorite} history={history} now={now}/>
          ))}
          {!filteredSignals.length && (
            <div className="empty-signals"><Icon name="activity"/><strong>{signals.length ? 'Aucun capteur ne correspond' : 'En attente des données OBD'}</strong><p>{signals.length ? 'Modifiez la recherche ou la catégorie.' : 'Les mesures valides apparaîtront ici automatiquement.'}</p></div>
          )}
        </div>
      </section>

      <section className="actions">
        <button className="primary" type="button" onClick={connectionAction} disabled={!nativeAvailable}><Icon name={status === 'lost' ? 'refresh' : 'bluetooth'}/><span>{connected ? 'Connexion' : status === 'lost' && lastDevice ? 'Reconnecter' : 'Connecter'}</span></button>
        <button className="secondary" type="button" onClick={() => window.LotoTNative?.startDemo?.()} disabled={!nativeAvailable}><Icon name="play"/><span>Mode démo</span></button>
        <button className="tools-button" type="button" onClick={() => window.LotoTNative?.openNativeTools?.()} disabled={!nativeAvailable}><Icon name="tool"/><span>Diagnostics avancés AndrOBD</span><Icon name="chevron"/></button>
      </section>

      <footer className="app-footer">GPL · Moteur AndrOBD · GPS, capteurs et MQTT intégrés · Interface LotoT</footer>

      <ConnectionSheet open={connectionOpen} onClose={() => setConnectionOpen(false)} bluetooth={bluetooth} medium={medium} setMedium={setMedium} onScan={(selectedMedium) => window.LotoTNative?.scanBluetooth?.(selectedMedium)} onConnect={connectDevice} onDisconnect={() => window.LotoTNative?.disconnectBluetooth?.()}/>
      <ServicesSheet open={servicesOpen} onClose={() => setServicesOpen(false)} services={services} signals={signals} onSave={(payload) => window.LotoTNative?.configureBuiltins?.(JSON.stringify(payload))} onRequestLocation={() => window.LotoTNative?.requestLocationPermission?.()} onPublishNow={() => window.LotoTNative?.publishMqttNow?.()}/>
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);
