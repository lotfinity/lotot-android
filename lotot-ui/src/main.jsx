import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';
import { getLanguage, setLanguage, t, LANGUAGE_REGISTRY } from './i18n.js';
import Icon from './icons/Icon.jsx';
import IconShowcase from './icons/IconShowcase.jsx';
import { renderBidiText } from './i18n/BidiText.jsx';
import { DEFAULT_ICON_FAMILY, IconFamilyProvider, normalizeIconFamily } from './icons/IconFamilyContext.jsx';

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
    enabled: false, status: 'disabled', broker: null, last_publish: 0, last_attempt: 0, published_messages: 0, queue_depth: 0, queue_capacity: 10000, syncing_total: 0, syncing_remaining: 0, next_retry: 0, retry_count: 0, error: null,
    config: { protocol: 'tcp://', host: '', port: 1883, username: '', password_set: false, device_uid: '', client_id: '', prefix: '', qos: 1, retain: false, include_gps: false, include_sensors: false, interval_seconds: 5, selected_signals: [] },
  },
};

const PRIMARY_NAV = [
  { id: 'overview', labelKey: 'nav.overview', icon: 'nav_overview' },
  { id: 'live', labelKey: 'nav.live', icon: 'nav_live' },
  { id: 'ai', labelKey: 'nav.ai', icon: 'nav_ai', center: true },
  { id: 'health', labelKey: 'nav.health', icon: 'nav_health' },
  { id: 'more', labelKey: 'nav.more', icon: 'nav_more' },
];

const DEMO_SCENARIOS = [
  { id: 'healthy', labelKey: 'demo.healthy', detailKey: 'demo.healthy_detail', code: null, icon: 'scenario_healthy' },
  { id: 'cold_start', labelKey: 'demo.cold_start', detailKey: 'demo.cold_start_detail', code: null, icon: 'scenario_cold_start' },
  { id: 'misfire', labelKey: 'demo.misfire', detailKey: 'demo.misfire_detail', code: 'P0301', icon: 'scenario_misfire' },
  { id: 'lean', labelKey: 'demo.lean', detailKey: 'demo.lean_detail', code: 'P0171', icon: 'scenario_lean' },
  { id: 'catalyst', labelKey: 'demo.catalyst', detailKey: 'demo.catalyst_detail', code: 'P0420', icon: 'scenario_catalyst' },
  { id: 'overheat', labelKey: 'demo.overheat', detailKey: 'demo.overheat_detail', code: 'P0217', icon: 'scenario_overheat' },
  { id: 'weak_charging', labelKey: 'demo.weak_charging', detailKey: 'demo.weak_charging_detail', code: 'P0562', icon: 'scenario_weak_charging' },
];
const demoScenarioOption = (id) => DEMO_SCENARIOS.find((item) => item.id === id) || DEMO_SCENARIOS[0];

const FONT_FAMILIES = [
  { id: 'system', labelKey: 'appearance.font_system', stack: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif' },
  { id: 'clean', labelKey: 'appearance.font_clean', stack: 'sans-serif' },
  { id: 'compact', labelKey: 'appearance.font_compact', stack: '"sans-serif-condensed", sans-serif' },
  { id: 'technical', labelKey: 'appearance.font_technical', stack: 'ui-monospace, "Roboto Mono", monospace' },
];
const ICON_FAMILIES = [
  { id: 'tech-line', labelKey: 'appearance.icon_tech_line', detailKey: 'appearance.icon_tech_line_detail' },
  { id: 'industrial-soft', labelKey: 'appearance.icon_industrial_soft', detailKey: 'appearance.icon_industrial_soft_detail', recommended: true },
  { id: 'neo-ecu', labelKey: 'appearance.icon_neo_ecu', detailKey: 'appearance.icon_neo_ecu_detail' },
];
const iconFamilyOption = (id) => ICON_FAMILIES.find((item) => item.id === id) || ICON_FAMILIES[1];
const LANGUAGE_OPTIONS = [
  { id: 'system', labelKey: 'appearance.language_system', detailKey: 'appearance.language_system_detail', dir: 'auto' },
  ...Object.values(LANGUAGE_REGISTRY).map((item) => ({ id: item.id, label: item.label, dir: item.dir })),
];
const normalizeLanguagePreference = (value) => {
  const raw = String(value || '').trim();
  return raw === 'system' || LANGUAGE_OPTIONS.some((item) => item.id === raw) ? raw : 'system';
};
const languageOption = (id) => LANGUAGE_OPTIONS.find((item) => item.id === id) || LANGUAGE_OPTIONS[0];

const DEFAULT_FONT_FAMILY = 'system';
const DEFAULT_FONT_SCALE = 115;
const clampFontScale = (value) => Math.min(140, Math.max(90, Math.round(Number(value) / 5) * 5 || DEFAULT_FONT_SCALE));
const fontFamilyOption = (id) => FONT_FAMILIES.find((item) => item.id === id) || FONT_FAMILIES[0];

function readNativeLanguagePreference() {
  try {
    return normalizeLanguagePreference(window.LotoTNative?.getAppLanguagePreference?.() || localStorage.getItem('lotot-language-preference') || 'system');
  } catch (_) {
    return normalizeLanguagePreference(localStorage.getItem('lotot-language-preference') || 'system');
  }
}

function readNativeAppearanceSettings() {
  try {
    const raw = window.LotoTNative?.getAppearanceSettings?.();
    const parsed = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch (_) {
    return {};
  }
}

function readNativeOnboardingState() {
  try {
    const raw = window.LotoTNative?.getOnboardingState?.();
    const parsed = raw ? JSON.parse(raw) : {};
    if (parsed && typeof parsed === 'object') return parsed;
  } catch (_) {
    // Browser previews fall back to local state.
  }
  return { complete: localStorage.getItem('lototi-onboarding-complete') === '1', current_version: 1 };
}

function readNativeDemoScenario() {
  try {
    const value = String(window.LotoTNative?.getDemoScenario?.() || '').trim();
    return DEMO_SCENARIOS.some((item) => item.id === value) ? value : '';
  } catch (_) {
    return '';
  }
}

function readNativeAiStatus() {
  try {
    const raw = window.LotoTNative?.getAiStatus?.();
    const parsed = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch (_) {
    return {};
  }
}

const CATEGORIES = [
  { id: 'all', labelKey: 'category.all', icon: 'activity' },
  { id: 'favorites', labelKey: 'category.favorites', icon: 'star' },
  { id: 'diagnostic', labelKey: 'category.diagnostic', icon: 'diagnostic_scan' },
  { id: 'engine', labelKey: 'category.engine', icon: 'engine' },
  { id: 'driving', labelKey: 'category.driving', icon: 'vehicle' },
  { id: 'temperature', labelKey: 'category.temperature', icon: 'sensor_temperature' },
  { id: 'fuel', labelKey: 'category.fuel', icon: 'sensor_fuel' },
  { id: 'air', labelKey: 'category.air', icon: 'sensor_airflow' },
  { id: 'pressure', labelKey: 'category.pressure', icon: 'sensor_pressure' },
  { id: 'electrical', labelKey: 'category.electrical', icon: 'sensor_voltage' },
  { id: 'emissions', labelKey: 'category.emissions', icon: 'scenario_catalyst' },
  { id: 'location', labelKey: 'category.location', icon: 'location' },
  { id: 'motion', labelKey: 'category.motion', icon: 'sensor_motion' },
  { id: 'other', labelKey: 'category.other', icon: 'nav_more' },
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

const aiBridge = window.lototAiBridge = window.lototAiBridge || {
  lastState: { status: 'idle', configured: false, providers: [] },
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


window.lototSetAiState = (payload) => {
  try {
    const parsed = typeof payload === 'string' ? JSON.parse(payload) : payload;
    if (!parsed || typeof parsed !== 'object') return;
    aiBridge.lastState = { ...(aiBridge.lastState || {}), ...parsed };
    const snapshot = { ...aiBridge.lastState };
    if (typeof window.lototAiStateListener === 'function') window.lototAiStateListener(snapshot);
    window.dispatchEvent(new CustomEvent('lotot:ai-state', { detail: snapshot }));
  } catch (error) {
    console.error('Invalid native AI payload', error);
  }
};

window.lototSetLanguage = (language) => {
  const normalized = setLanguage(language);
  window.dispatchEvent(new CustomEvent('lotot:language', { detail: { language: normalized } }));
};

const isNumericReading = (value) => value !== null
  && value !== undefined
  && value !== ''
  && Number.isFinite(Number(value));

const clamp = (value, min, max) => Math.max(min, Math.min(max, isNumericReading(value) ? Number(value) : min));
const mediumLabel = (medium) => medium === 'ble' ? t('connection.bluetooth_le') : t('connection.bluetooth_classic');
const signalKey = (signal) => signal?.key || signal?.mnemonic || signal?.label || 'unknown';
const cleanText = (value) => String(value || '').trim();


function renderInlineMarkdown(text, keyPrefix = 'inline') {
  const source = String(text || '');
  const tokenPattern = /(\*\*[^*]+\*\*|`[^`]+`|\*[^*\n]+\*)/g;
  const parts = source.split(tokenPattern).filter((part) => part !== '');
  return parts.map((part, index) => {
    const key = `${keyPrefix}-${index}`;
    if (part.startsWith('**') && part.endsWith('**')) return <strong key={key}>{renderBidiText(part.slice(2, -2), `${key}-strong`)}</strong>;
    if (part.startsWith('`') && part.endsWith('`')) return <code key={key} dir="ltr">{part.slice(1, -1)}</code>;
    if (part.startsWith('*') && part.endsWith('*')) return <em key={key}>{renderBidiText(part.slice(1, -1), `${key}-em`)}</em>;
    return <React.Fragment key={key}>{renderBidiText(part, key)}</React.Fragment>;
  });
}

function MarkdownMessage({ text }) {
  const lines = String(text || '').replace(/\r/g, '').split('\n');
  const blocks = [];
  let paragraph = [];
  let list = [];
  let listType = null;

  const flushParagraph = () => {
    if (!paragraph.length) return;
    const value = paragraph.join(' ').trim();
    if (value) blocks.push(<p key={`p-${blocks.length}`}>{renderInlineMarkdown(value, `p-${blocks.length}`)}</p>);
    paragraph = [];
  };
  const flushList = () => {
    if (!list.length) return;
    const Tag = listType === 'ol' ? 'ol' : 'ul';
    blocks.push(<Tag key={`list-${blocks.length}`}>{list.map((item, index) => <li key={index}>{renderInlineMarkdown(item, `li-${blocks.length}-${index}`)}</li>)}</Tag>);
    list = [];
    listType = null;
  };

  lines.forEach((rawLine) => {
    const line = rawLine.trim();
    if (!line) { flushParagraph(); flushList(); return; }
    const heading = line.match(/^(#{1,3})\s+(.+)$/);
    if (heading) {
      flushParagraph(); flushList();
      const level = Math.min(3, heading[1].length);
      const Tag = `h${level + 2}`;
      blocks.push(<Tag key={`h-${blocks.length}`}>{renderInlineMarkdown(heading[2], `h-${blocks.length}`)}</Tag>);
      return;
    }
    const unordered = line.match(/^[-•]\s+(.+)$/);
    if (unordered) {
      flushParagraph();
      if (listType && listType !== 'ul') flushList();
      listType = 'ul'; list.push(unordered[1]); return;
    }
    const ordered = line.match(/^\d+[.)]\s+(.+)$/);
    if (ordered) {
      flushParagraph();
      if (listType && listType !== 'ol') flushList();
      listType = 'ol'; list.push(ordered[1]); return;
    }
    flushList();
    paragraph.push(line);
  });
  flushParagraph(); flushList();
  return <div className="ai-markdown">{blocks}</div>;
}

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
  const mnemonic = cleanText(signal?.mnemonic);
  return t(`signal.${mnemonic}`, {}, '') || cleanText(signal?.label) || mnemonic || t('generic.obd_sensor');
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
  if (age < 1000) return t('generic.now');
  if (age < 60000) return `${Math.floor(age / 1000)} s`;
  return `${Math.floor(age / 60000)} min`;
}


const TEMPERATURE_PROFILES = {
  engine_coolant_temperature: { min: 0, max: 130, normalMin: 70, normalMax: 105, warningMax: 115, shortKey: 'temperature.coolant' },
  coolant_temp: { min: 0, max: 130, normalMin: 70, normalMax: 105, warningMax: 115, shortKey: 'temperature.coolant' },
  engine_oil_temperature: { min: 0, max: 150, normalMin: 70, normalMax: 110, warningMax: 125, shortKey: 'temperature.oil' },
  intake_air_temperature: { min: -20, max: 100, normalMin: -10, normalMax: 60, warningMax: 80, shortKey: 'temperature.intake' },
  ambient_air_temperature: { min: -30, max: 60, normalMin: -10, normalMax: 40, warningMax: 50, shortKey: 'temperature.ambient' },
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
  if (mnemonic === 'number_fault_codes' && value > 0) return issue('danger', t('alert.fault_count', { count: formatValue(value, 0) }));
  if (mnemonic === 'status_mil' && value > 0) return issue('danger', t('alert.mil'));
  if (['engine_coolant_temperature', 'coolant_temp'].includes(mnemonic)) {
    if (value > 115) return issue('danger', t('alert.coolant_critical', { value: formatValue(value, 0) }));
    if (value > 105) return issue('warning', t('alert.coolant_high', { value: formatValue(value, 0) }));
  }
  if (mnemonic === 'engine_oil_temperature') {
    if (value > 125) return issue('danger', t('alert.oil_critical', { value: formatValue(value, 0) }));
    if (value > 110) return issue('warning', t('alert.oil_high', { value: formatValue(value, 0) }));
  }
  if (mnemonic === 'intake_air_temperature') {
    if (value > 80) return issue('danger', t('alert.intake_critical', { value: formatValue(value, 0) }));
    if (value > 60) return issue('warning', t('alert.intake_high', { value: formatValue(value, 0) }));
  }
  if (['ecu_voltage', 'module_voltage'].includes(mnemonic)) {
    if (value < 11.8 || value > 15.2) return issue('danger', t('alert.voltage_abnormal', { value: formatValue(value, 2) }));
    if (value < 12.3 || value > 14.8) return issue('warning', t('alert.voltage_watch', { value: formatValue(value, 2) }));
  }
  if (['fuel_level', 'fuel_tank_level_input'].includes(mnemonic)) {
    if (value < 10) return issue('danger', t('alert.fuel_empty', { value: formatValue(value, 0) }));
    if (value < 20) return issue('warning', t('alert.fuel_low', { value: formatValue(value, 0) }));
  }
  if (/fuel_trim_(short|long)_b[12]/.test(mnemonic)) {
    if (absolute > 25) return issue('danger', t('alert.trim_range', { label: signalLabel(signal), value: formatValue(value, 1) }));
    if (absolute > 15) return issue('warning', t('alert.trim_high', { label: signalLabel(signal), value: formatValue(value, 1) }));
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
  return tone === 'danger' ? t('generic.critical') : tone === 'warning' ? t('generic.high') : tone === 'cold' ? t('generic.cold') : tone === 'normal' ? t('generic.normal') : t('generic.unavailable');
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

function RingGauge({ label, value, unit, max, decimals = 0, icon }) {
  const available = isNumericReading(value);
  const progress = available ? clamp(value, 0, max) / max * 100 : 0;
  return (
    <article className={`metric-card ${available ? '' : 'is-unavailable'}`}>
      <div className="metric-head"><span>{label}</span><Icon name={icon}/></div>
      <div className="metric-ring" style={{ '--value': `${progress}%` }}>
        <div dir="ltr"><strong>{formatValue(value, decimals)}</strong><small>{unit}</small></div>
      </div>
    </article>
  );
}

function QuickMetric({ label, value, unit, icon }) {
  return (
    <article className={`quick-metric ${isNumericReading(value) ? '' : 'is-unavailable'}`}>
      <span><Icon name={icon}/></span>
      <div><small>{label}</small><strong dir="ltr">{formatValue(value)} <em>{unit}</em></strong></div>
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
      <div className="temperature-status"><span>{toneLabel(tone)}</span><small>{t('temperature.normal_range', { min: profile.normalMin, max: profile.normalMax })}</small></div>
      <div className="temperature-track" style={{ '--marker': `${position}%`, '--normal-start': `${normalStart}%`, '--normal-end': `${normalEnd}%`, '--warning-end': `${warningEnd}%` }}>
        <i className="normal-zone"/><b className="temperature-marker"/>
      </div>
      <div className="temperature-labels" dir="ltr"><span>{profile.min}°</span><span>{t('temperature.normal_mark', { value: profile.normalMax })}</span><span>{t('temperature.high_mark', { value: profile.warningMax })}</span><span>{profile.max}°</span></div>
      <Sparkline values={historyValues} tone={tone === 'danger' ? 'danger' : tone === 'warning' ? 'warning' : 'temperature'}/>
    </div>
  );
}

function VoltageBand({ signal, historyValues }) {
  const value = isNumericReading(signal.value) ? Number(signal.value) : null;
  const min = 10; const max = 16;
  const position = value === null ? 0 : (clamp(value, min, max) - min) / (max - min) * 100;
  const tone = value === null ? 'unknown' : value < 11.8 || value > 15.2 ? 'danger' : value < 12.3 || value > 14.8 ? 'warning' : 'normal';
  return <div className={`voltage-chart tone-${tone}`}><div className="voltage-track" style={{ '--marker': `${position}%` }}><b/></div><div className="voltage-labels" dir="ltr"><span>10 V</span><span>12.3–14.8 V normal</span><span>16 V</span></div><Sparkline values={historyValues} tone={tone === 'danger' ? 'danger' : tone === 'warning' ? 'warning' : 'electrical'}/></div>;
}

function RangeChart({ signal, historyValues, category }) {
  const value = isNumericReading(signal.value) ? Number(signal.value) : null;
  const unit = signal.unit || '';
  const percentLike = unit === '%' || /level|load|throttle|pedal|trim|torque/.test(signal.mnemonic || '');
  const min = percentLike ? (/trim/.test(signal.mnemonic || '') ? -100 : 0) : isNumericReading(signal.min) ? Number(signal.min) : Math.min(0, value || 0);
  const max = percentLike ? 100 : isNumericReading(signal.max) && Number(signal.max) > min ? Number(signal.max) : Math.max(1, value || 1);
  const progress = value === null ? 0 : (clamp(value, min, max) - min) / (max - min) * 100;
  return <div className={`range-chart range-${category}`}><div className="range-track"><i style={{ width: `${progress}%` }}/><b style={{ left: `${progress}%` }}/></div><div className="range-labels" dir="ltr"><span>{formatValue(min)}</span><span>{formatValue(max)} {unit}</span></div><Sparkline values={historyValues} tone={category}/></div>;
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
  const categoryLabel = t(CATEGORIES.find((item) => item.id === category)?.labelKey || 'category.other');
  const sourceLabel = signal.source
    ? `${signal.source === 'gps' ? t('source.gps') : signal.source === 'motion' ? t('source.motion') : signal.source} · ${signal.mnemonic || t('source.sensor')}`
    : `PID ${Number(signal.pid || 0).toString(16).toUpperCase().padStart(2, '0')} · ${signal.mnemonic || t('source.sensor')}`;

  return (
    <article className={`signal-card category-${category} ${assessment ? `has-${assessment.level}` : ''}`}>
      <header>
        <div><span>{categoryLabel}</span><h3>{signalLabel(signal)}</h3></div>
        <button type="button" className={favorite ? 'favorite-button is-active' : 'favorite-button'} onClick={() => onToggleFavorite(key)} aria-label={favorite ? t('explorer.remove_favorite') : t('explorer.add_favorite')}>
          <Icon name="star"/>
        </button>
      </header>
      <div className="signal-value-row">
        <div className="signal-value" dir="ltr"><strong>{formatValue(signal.value)}</strong><span>{signal.unit || ''}</span></div>
        <span className={`trend trend-${trend}`}>{trend === 'up' ? '↑' : trend === 'down' ? '↓' : '—'}</span>
      </div>
      {assessment && <div className={`signal-assessment ${assessment.level}`}>{assessment.message}</div>}
      <SignalVisualization signal={signal} historyValues={historyValues}/>
      <footer><code dir="ltr">{sourceLabel}</code><span><Icon name="clock"/><bdi dir="ltr">{ageLabel(signal.updated_at, now)}</bdi></span></footer>
    </article>
  );
}

function OverviewMetric({ label, value, unit, icon, tone = 'neutral', detail }) {
  return <article className={`overview-metric tone-${tone}`}><span><Icon name={icon}/></span><div><small>{label}</small><strong dir="ltr">{formatValue(value)} <em>{unit}</em></strong>{detail && <p>{detail}</p>}</div></article>;
}

function PageHeader({ kicker, title, detail }) {
  return (
    <header className="page-header">
      <div><span>{kicker}</span><h1>{title}</h1></div>
      {detail && <small>{detail}</small>}
    </header>
  );
}

function BottomNavigation({ active, onChange }) {
  return (
    <nav className="bottom-navigation" aria-label={t('nav.label')}>
      <div className="bottom-navigation-inner">
        {PRIMARY_NAV.map((item) => (
          <button
            type="button"
            key={item.id}
            className={`${active === item.id ? 'is-active' : ''} ${item.center ? 'is-center-ai' : ''}`.trim()}
            aria-current={active === item.id ? 'page' : undefined}
            onClick={() => onChange(item.id)}
          >
            <span><Icon name={item.icon}/></span>
            <small>{t(item.labelKey)}</small>
          </button>
        ))}
      </div>
    </nav>
  );
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
      <header><div><span>{t('drive.title')}</span><strong>{t('drive.live')}</strong></div><Icon name="speed"/></header>
      <div className="drive-main-values">
        <div className="speed-value"><strong>{formatValue(speed, 0)}</strong><span>km/h</span></div>
        <div className="rpm-value"><small>{t('drive.rpm')}</small><strong>{formatValue(rpm, 0)}</strong><span>rpm</span></div>
      </div>
      <div className="drive-bars">
        <div><span>{t('drive.speed')}</span><i><b style={{ width: `${speedProgress}%` }}/></i></div>
        <div><span>RPM</span><i className="rpm-scale"><b style={{ width: `${rpmProgress}%` }}/></i></div>
      </div>
      <Sparkline values={historyOf('vehicle_speed')} tone="driving"/>
      <footer><span><small>{t('drive.load')}</small><strong>{formatValue(load, 0)}%</strong></span><span><small>{t('drive.accelerator')}</small><strong>{formatValue(throttle, 0)}%</strong></span><span><small>{t('drive.torque')}</small><strong>{formatValue(torque, 0)}%</strong></span></footer>
    </section>
  );
}

function HealthOverview({ alerts, signals, lastCapturedAt, now }) {
  const dangerCount = alerts.filter((item) => item.level === 'danger').length;
  const warningCount = alerts.filter((item) => item.level === 'warning').length;
  const tone = dangerCount ? 'danger' : warningCount ? 'warning' : signals.length ? 'normal' : 'unknown';
  const primary = alerts.find((item) => item.level === 'danger') || alerts[0];
  const title = dangerCount ? t('health.action') : warningCount ? t('health.watch') : signals.length ? t('health.stable') : t('health.waiting');
  const healthIcon = tone === 'danger' ? 'health_critical' : tone === 'warning' ? 'health_warning' : 'health_stable';
  return (
    <section className={`health-overview tone-${tone}`}>
      <header><span><Icon name={healthIcon}/></span><i/></header>
      <strong>{title}</strong>
      <p>{primary?.message || (signals.length ? t('health.signals', { count: signals.length }) : t('health.connect'))}</p>
      <div className="health-counts"><span><b>{dangerCount}</b><small>{t('health.critical')}</small></span><span><b>{warningCount}</b><small>{t('health.follow')}</small></span></div>
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
        <strong>{device.name || t('connection.adapter')}</strong>
        <small>{device.address}</small>
        <span className="device-badges">
          {device.paired && <em>{t('connection.paired')}</em>}
          {Number.isFinite(device.rssi) && <em className="signal-badge"><Icon name="signal"/>{device.rssi} dBm</em>}
        </span>
      </span>
      <span className="device-action">{isConnected ? t('connection.connected') : isConnecting ? t('connection.connecting') : t('connection.connect')}</span>
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
      <section className="connection-sheet" role="dialog" aria-modal="true" aria-label={t('connection.dialog')} onClick={(event) => event.stopPropagation()}>
        <div className="sheet-handle"/>
        <header className="sheet-header">
          <div><span>{t('connection.section')}</span><h2>{t('connection.choose')}</h2></div>
          <button className="icon-button" type="button" onClick={onClose} aria-label={t('generic.close')}><Icon name="close"/></button>
        </header>

        {bluetooth.connectedDevice && (
          <article className="connected-banner">
            <span className="connected-icon"><Icon name="link"/></span>
            <div><small>{t('connection.active_adapter')}</small><strong>{bluetooth.connectedDevice.name}</strong><span>{bluetooth.connectedDevice.address} · {mediumLabel(bluetooth.connectedDevice.medium)}</span></div>
            <button type="button" onClick={onDisconnect}><Icon name="unlink"/><span>{t('connection.disconnect')}</span></button>
          </article>
        )}

        <div className="medium-tabs" role="tablist">
          <button type="button" className={medium === 'classic' ? 'is-active' : ''} onClick={() => setMedium('classic')}>{t('connection.classic')}</button>
          <button type="button" className={medium === 'ble' ? 'is-active' : ''} onClick={() => setMedium('ble')}>BLE</button>
        </div>

        <div className="scan-toolbar">
          <div><strong>{mediumLabel(medium)}</strong><span>{t('connection.device_count', { count: devices.length })}</span></div>
          <button type="button" onClick={() => onScan(medium)} disabled={bluetooth.scanning}><Icon name="refresh"/><span>{bluetooth.scanning ? t('connection.searching') : t('connection.refresh')}</span></button>
        </div>

        {bluetooth.error && <div className="connection-error">{bluetooth.error}</div>}

        <div className="device-list">
          {devices.map((device) => (
            <DeviceRow key={`${device.medium}:${device.address}`} device={device} connected={bluetooth.connectedDevice} selected={bluetooth.selectedDevice} connecting={connecting} onConnect={onConnect}/>
          ))}
          {!devices.length && (
            <div className="empty-devices">
              <span className={bluetooth.scanning ? 'scanner-orbit is-scanning' : 'scanner-orbit'}><Icon name="bluetooth"/></span>
              <strong>{bluetooth.scanning ? t('connection.searching_adapters') : t('connection.none')}</strong>
              <p>{t('connection.none_help')}</p>
            </div>
          )}
        </div>
        <p className="sheet-note">{t('connection.pairing_note')}</p>
      </section>
    </div>
  );
}

function serviceLabel(service, kind) {
  if (!service?.enabled) return t('generic.disabled');
  if (service.status === 'active' || service.status === 'online') return t('generic.active');
  if (service.status === 'up_to_date') return t('generic.up_to_date');
  if (service.status === 'syncing') return t('generic.syncing');
  if (service.status === 'queued') return t('generic.offline_stored');
  if (service.status === 'permission') return t('generic.permission');
  if (service.status === 'configuration') return t('generic.configuration');
  if (service.status === 'error' || service.status === 'unavailable') return t('generic.error');
  if (service.status === 'connecting') return t('generic.connection');
  return kind === 'mqtt' ? t('generic.waiting_stream') : t('generic.waiting');
}

function ServiceTile({ icon, title, service, kind, detail, onClick }) {
  const healthy = ['active', 'online', 'up_to_date'].includes(service?.status);
  const warning = ['permission', 'configuration', 'error', 'unavailable', 'queued'].includes(service?.status);
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
  const [dirty, setDirty] = useState(false);
  const initializedForOpen = React.useRef(false);

  useEffect(() => {
    if (!open) {
      initializedForOpen.current = false;
      return;
    }
    if (initializedForOpen.current) return;
    initializedForOpen.current = true;
    setGpsEnabled(Boolean(services.gps?.enabled));
    setSensorsEnabled(Boolean(services.sensors?.enabled));
    setMqtt({
      ...EMPTY_BUILTINS.mqtt.config,
      ...(services.mqtt?.config || {}),
      enabled: Boolean(services.mqtt?.enabled),
      password: '',
    });
    setDirty(false);
  }, [open, services]);

  if (!open) return null;
  const selected = Array.isArray(mqtt.selected_signals) ? mqtt.selected_signals : [];
  const updateMqtt = (key, value) => {
    setDirty(true);
    setMqtt((current) => ({ ...current, [key]: value }));
  };
  const updateGpsEnabled = (value) => {
    setDirty(true);
    setGpsEnabled(value);
  };
  const updateSensorsEnabled = (value) => {
    setDirty(true);
    setSensorsEnabled(value);
  };
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
      include_gps: Boolean(mqtt.include_gps),
      include_sensors: Boolean(mqtt.include_sensors),
      interval_seconds: Math.max(1, Number(mqtt.interval_seconds) || 5),
      selected_signals: selected,
    };
    if (mqtt.password) mqttPayload.password = mqtt.password;
    onSave({ gps_enabled: gpsEnabled, sensors_enabled: sensorsEnabled, mqtt: mqttPayload });
    setDirty(false);
  };

  const queued = Number(services.mqtt?.queue_depth || 0);
  const gatewayDetail = queued
    ? t('service.local_saved', { count: queued })
    : t('service.local_empty');

  return (
    <div className="sheet-backdrop services-backdrop" role="presentation" onClick={onClose}>
      <section className="connection-sheet services-sheet" role="dialog" aria-modal="true" aria-label={t('service.dialog')} onClick={(event) => event.stopPropagation()}>
        <div className="sheet-handle"/>
        <header className="sheet-header">
          <div><span>{t('service.native_title')}</span><h2>{t('service.sources_sync')}</h2></div>
          <button className="icon-button" type="button" onClick={onClose} aria-label={t('generic.close')}><Icon name="close"/></button>
        </header>

        <section className="service-config-block">
          <div className="service-config-title"><span><Icon name="location"/></span><div><strong>{t('service.gps_integrated')}</strong><small>{t('service.gps_detail')}</small></div></div>
          <SwitchRow checked={gpsEnabled} onChange={updateGpsEnabled} title={t('service.enable_position')} description={t('service.enable_position_detail')} disabled={!services.gps?.available}/>
          {gpsEnabled && !services.gps?.permission_granted && <button className="permission-button" type="button" onClick={onRequestLocation}><Icon name="location"/><span>{t('service.allow_location')}</span></button>}
          {services.gps?.error && <p className="inline-service-error">{services.gps.error}</p>}
        </section>

        <section className="service-config-block">
          <div className="service-config-title"><span><Icon name="phone"/></span><div><strong>{t('service.phone_title')}</strong><small>{t('service.phone_detail')}</small></div></div>
          <SwitchRow checked={sensorsEnabled} onChange={updateSensorsEnabled} title={t('service.enable_accelerometer')} description={t('service.accelerometer_detail')} disabled={!services.sensors?.available}/>
        </section>

        <section className="service-config-block mqtt-config-block">
          <div className="service-config-title"><span><Icon name="cloud"/></span><div><strong>{t('service.gateway_title')}</strong><small>{t('service.gateway_detail')}</small></div></div>
          <div className={`gateway-runtime ${services.mqtt?.status || 'disabled'}`}>
            <span><Icon name="nav_health"/></span>
            <div><small>{t('service.background_gateway')}</small><strong>{services.foreground ? t('service.running') : t('service.starting')}</strong><em>{gatewayDetail}</em></div>
            <b>{services.mqtt?.status === 'syncing' ? `${Math.max(0, (services.mqtt.syncing_total || 0) - (services.mqtt.syncing_remaining || 0))}/${services.mqtt.syncing_total || 0}` : queued}</b>
          </div>
          <p className="gateway-security"><Icon name="health_stable"/><span>{t('service.security', { count: services.mqtt?.queue_capacity || 10000 })}</span></p>
          <SwitchRow checked={Boolean(mqtt.enabled)} onChange={(value) => updateMqtt('enabled', value)} title={t('service.enable_mqtt')} description={t('service.enable_mqtt_detail')}/>
          <div className="mqtt-form">
            <label><span>{t('service.protocol')}</span><select value={mqtt.protocol} onChange={(event) => updateMqtt('protocol', event.target.value)}><option value="tcp://">TCP</option><option value="ssl://">SSL/TLS</option><option value="ws://">WebSocket</option><option value="wss://">Secure WebSocket</option></select></label>
            <label className="is-wide"><span>{t('service.server')}</span><input value={mqtt.host} onChange={(event) => updateMqtt('host', event.target.value)} placeholder="mqtt.example.com"/></label>
            <label><span>{t('service.port')}</span><input type="number" value={mqtt.port} onChange={(event) => updateMqtt('port', event.target.value)} inputMode="numeric"/></label>
            <label><span>{t('service.interval')}</span><div className="input-unit"><input type="number" min="1" value={mqtt.interval_seconds} onChange={(event) => updateMqtt('interval_seconds', event.target.value)}/><em>s</em></div></label>
            <label className="is-wide"><span>{t('service.device_uid')}</span><input value={mqtt.device_uid} onChange={(event) => updateMqtt('device_uid', event.target.value)} placeholder="demo-obd-001" autoCapitalize="none"/><small className="field-hint">{t('service.topic')}: LotoT/devices/{mqtt.device_uid?.trim() || 'android-xxxxxx'}/snapshot</small></label>
            <label className="is-wide"><span>{t('service.client_id')}</span><input value={mqtt.client_id} onChange={(event) => updateMqtt('client_id', event.target.value)} placeholder={t('service.generated')}/></label>
            <label><span>{t('service.username')}</span><input value={mqtt.username} onChange={(event) => updateMqtt('username', event.target.value)} autoCapitalize="none"/></label>
            <label><span>{t('service.password')}</span><input type="password" value={mqtt.password} onChange={(event) => updateMqtt('password', event.target.value)} placeholder={mqtt.password_set ? t('service.saved_leave_blank') : t('service.optional')}/></label>
            <label><span>QoS</span><select value={mqtt.qos} onChange={(event) => updateMqtt('qos', event.target.value)}><option value="0">0 · {t('service.qos_fast')}</option><option value="1">1 · {t('service.qos_confirmed')}</option><option value="2">2 · {t('service.qos_exactly_once')}</option></select></label>
            <SwitchRow checked={Boolean(mqtt.retain)} onChange={(value) => updateMqtt('retain', value)} title={t('service.retained')} description={t('service.retained_detail')}/>
            <SwitchRow checked={Boolean(mqtt.include_gps)} onChange={(value) => updateMqtt('include_gps', value)} title={t('service.mqtt_share_gps')} description={t('service.mqtt_share_gps_detail')} disabled={!services.gps?.enabled}/>
            <SwitchRow checked={Boolean(mqtt.include_sensors)} onChange={(value) => updateMqtt('include_sensors', value)} title={t('service.mqtt_share_sensors')} description={t('service.mqtt_share_sensors_detail')} disabled={!services.sensors?.enabled}/>
          </div>

          <div className="signal-publish-head"><div><strong>{t('service.published_signals')}</strong><small>{selected.length ? t('service.selected', { count: selected.length }) : t('service.all_live')}</small></div><button type="button" onClick={() => updateMqtt('selected_signals', [])}>{t('service.publish_all')}</button></div>
          <div className="publish-signal-list">
            {signals.map((signal) => {
              const mnemonic = signal.mnemonic || signalKey(signal);
              const active = !selected.length || selected.includes(mnemonic);
              return <button type="button" key={signalKey(signal)} className={active ? 'is-selected' : ''} onClick={() => toggleSignal(mnemonic)}><i/><span>{signalLabel(signal)}</span><code dir="ltr">{mnemonic}</code></button>;
            })}
            {!signals.length && <p>{t('service.signals_later')}</p>}
          </div>
          {services.mqtt?.error && <p className="inline-service-error">{services.mqtt.error}</p>}
          <button className="mqtt-test-button" type="button" onClick={onPublishNow} disabled={!services.mqtt?.enabled}><Icon name="cloud"/><span>{t('service.publish_test')}</span><em>{serviceLabel(services.mqtt, 'mqtt')}</em></button>
        </section>

        <div className={`services-actions ${dirty ? 'is-dirty' : ''}`}><button type="button" className="secondary" onClick={onClose}>{t('generic.cancel')}</button><button type="button" className="primary" onClick={save} disabled={!dirty}>{dirty ? t('generic.save_changes') : t('generic.saved')}</button></div>
      </section>
    </div>
  );
}


function AppearanceSheet({ open, onClose, theme, onThemeChange, languagePreference, onLanguageChange, fontFamily, onFontFamilyChange, fontScale, onFontScaleChange, iconFamily, onIconFamilyChange }) {
  useEffect(() => {
    if (!open) return undefined;
    document.body.classList.add('sheet-open');
    return () => document.body.classList.remove('sheet-open');
  }, [open]);

  if (!open) return null;
  const selectedFamily = fontFamilyOption(fontFamily);
  const changeScale = (value) => onFontScaleChange(clampFontScale(value));

  return (
    <div className="sheet-backdrop appearance-backdrop" role="presentation" onClick={onClose}>
      <section className="connection-sheet appearance-sheet" role="dialog" aria-modal="true" aria-label={t('appearance.title')} onClick={(event) => event.stopPropagation()}>
        <div className="sheet-handle"/>
        <header className="sheet-header">
          <div><span>{t('appearance.kicker')}</span><h2>{t('appearance.title')}</h2></div>
          <button className="icon-button" type="button" onClick={onClose} aria-label={t('generic.close')}><Icon name="close"/></button>
        </header>

        <div className="appearance-preview" style={{ fontFamily: selectedFamily.stack }}>
          <span>{t('appearance.preview_kicker')}</span>
          <strong>{t('appearance.preview_title')}</strong>
          <p>{t('appearance.preview_body')}</p>
          <div><b>92</b><small>km/h</small><em>{fontScale}%</em></div>
          <div className="appearance-icon-preview" aria-hidden="true">
            <i><Icon name="nav_overview" family={iconFamily}/></i><i><Icon name="nav_live" family={iconFamily}/></i><i className="is-ai"><Icon name="nav_ai" family={iconFamily}/></i><i><Icon name="sensor_voltage" family={iconFamily}/></i><i><Icon name="health_stable" family={iconFamily}/></i>
          </div>
        </div>

        <section className="appearance-setting-block language-setting-block">
          <div className="appearance-setting-title"><span><Icon name="type"/></span><div><strong>{t('appearance.language')}</strong><small>{t('appearance.language_detail')}</small></div></div>
          <div className="appearance-choice-grid language-choice-grid">
            {LANGUAGE_OPTIONS.map((item) => (
              <button type="button" key={item.id} className={languagePreference === item.id ? 'is-selected' : ''} onClick={() => onLanguageChange(item.id)}>
                <strong dir={item.dir || 'auto'}>{item.labelKey ? t(item.labelKey) : item.label}</strong>
                <small dir="ltr">{item.id === 'system' ? t(item.detailKey) : item.id}</small>
              </button>
            ))}
          </div>
        </section>

        <section className="appearance-setting-block">
          <div className="appearance-setting-title"><span><Icon name={theme === 'dark' ? 'moon' : 'sun'}/></span><div><strong>{t('appearance.theme')}</strong><small>{t('appearance.theme_detail')}</small></div></div>
          <div className="appearance-choice-grid theme-choice-grid">
            <button type="button" className={theme === 'dark' ? 'is-selected' : ''} onClick={() => onThemeChange('dark')}><Icon name="moon"/><span>{t('appearance.dark')}</span></button>
            <button type="button" className={theme === 'light' ? 'is-selected' : ''} onClick={() => onThemeChange('light')}><Icon name="sun"/><span>{t('appearance.light')}</span></button>
          </div>
        </section>

        <section className="appearance-setting-block">
          <div className="appearance-setting-title"><span><Icon name="type"/></span><div><strong>{t('appearance.font_family')}</strong><small>{t('appearance.font_family_detail')}</small></div></div>
          <div className="appearance-choice-grid font-choice-grid">
            {FONT_FAMILIES.map((item) => (
              <button type="button" key={item.id} className={fontFamily === item.id ? 'is-selected' : ''} onClick={() => onFontFamilyChange(item.id)} style={{ fontFamily: item.stack }}>
                <b>Aa</b><span>{t(item.labelKey)}</span>
              </button>
            ))}
          </div>
        </section>

        <section className="appearance-setting-block icon-family-block">
          <div className="appearance-setting-title"><span><Icon name="nav_more"/></span><div><strong>{t('appearance.icon_family')}</strong><small>{t('appearance.icon_family_detail')}</small></div></div>
          <div className="appearance-choice-grid icon-family-choice-grid">
            {ICON_FAMILIES.map((item) => (
              <button type="button" key={item.id} className={`icon-family-choice ${iconFamily === item.id ? 'is-selected' : ''}`} onClick={() => onIconFamilyChange(item.id)}>
                <span className="icon-family-samples" aria-hidden="true"><i><Icon name="nav_overview" family={item.id}/></i><i><Icon name="nav_ai" family={item.id}/></i><i><Icon name="sensor_pressure" family={item.id}/></i><i><Icon name="health_stable" family={item.id}/></i></span>
                <span className="icon-family-copy"><strong>{t(item.labelKey)}</strong><small>{t(item.detailKey)}</small></span>
                {item.recommended && <em>{t('appearance.icon_recommended')}</em>}
              </button>
            ))}
          </div>
        </section>

        <section className="appearance-setting-block text-scale-block">
          <div className="appearance-setting-title"><span><Icon name="sliders"/></span><div><strong>{t('appearance.text_size')}</strong><small>{t('appearance.text_size_detail')}</small></div><output>{fontScale}%</output></div>
          <div className="font-scale-control">
            <button type="button" onClick={() => changeScale(fontScale - 5)} disabled={fontScale <= 90} aria-label={t('appearance.smaller')}>A−</button>
            <input type="range" min="90" max="140" step="5" value={fontScale} onChange={(event) => changeScale(event.target.value)} aria-label={t('appearance.text_size')} aria-valuetext={`${fontScale}%`}/>
            <button type="button" onClick={() => changeScale(fontScale + 5)} disabled={fontScale >= 140} aria-label={t('appearance.larger')}>A+</button>
          </div>
          <div className="font-scale-labels"><span>90%</span><span>{t('appearance.recommended')}</span><span>140%</span></div>
        </section>

        <div className="appearance-actions">
          <button type="button" className="secondary" onClick={() => { onFontFamilyChange(DEFAULT_FONT_FAMILY); onFontScaleChange(DEFAULT_FONT_SCALE); onIconFamilyChange(DEFAULT_ICON_FAMILY); }}>{t('appearance.reset')}</button>
          <button type="button" className="primary" onClick={onClose}>{t('generic.done')}</button>
        </div>
      </section>
    </div>
  );
}

function OnboardingFlow({ services, bluetooth, status, signals, initialPermissionState, canClose, onClose, onComplete }) {
  const [step, setStep] = useState('welcome');
  const [gpsEnabled, setGpsEnabled] = useState(Boolean(services.gps?.enabled));
  const [sensorsEnabled, setSensorsEnabled] = useState(Boolean(services.sensors?.enabled));
  const [mqttEnabled, setMqttEnabled] = useState(Boolean(services.mqtt?.enabled));
  const [mqttShareGps, setMqttShareGps] = useState(Boolean(services.mqtt?.config?.include_gps));
  const [mqttShareSensors, setMqttShareSensors] = useState(Boolean(services.mqtt?.config?.include_sensors));
  const [notificationsEnabled, setNotificationsEnabled] = useState(initialPermissionState?.notification_granted !== false);
  const [method, setMethod] = useState(null);
  const [networkHost, setNetworkHost] = useState('');
  const [networkPort, setNetworkPort] = useState('23');
  const [attemptedMethod, setAttemptedMethod] = useState(null);
  const devices = [...(bluetooth.devices || [])].sort((a, b) => {
    if (a.paired !== b.paired) return a.paired ? -1 : 1;
    return (b.rssi ?? -999) - (a.rssi ?? -999);
  });
  const connected = status === 'live';
  const demo = status === 'demo';
  const hasLiveData = signals.length > 0;
  const verified = demo || (connected && hasLiveData);
  const stepIndex = step === 'welcome' ? 0 : step === 'features' ? 1 : step === 'connect' ? 2 : 3;

  useEffect(() => {
    if (!verified || step !== 'verify') return undefined;
    const timer = window.setTimeout(onComplete, 1250);
    return () => window.clearTimeout(timer);
  }, [verified, step, onComplete]);

  const applyCapabilities = () => {
    window.LotoTNative?.configureBuiltins?.(JSON.stringify({
      gps_enabled: gpsEnabled,
      sensors_enabled: sensorsEnabled,
      mqtt: {
        enabled: mqttEnabled,
        include_gps: mqttEnabled && gpsEnabled && mqttShareGps,
        include_sensors: mqttEnabled && sensorsEnabled && mqttShareSensors,
      },
    }));
    if (notificationsEnabled) window.LotoTNative?.requestNotificationPermission?.();
    setStep('connect');
  };

  const chooseMethod = (next) => {
    setMethod(next);
    if (next === 'classic' || next === 'ble') window.LotoTNative?.scanBluetooth?.(next);
  };

  const connectBluetooth = (device) => {
    setAttemptedMethod(method);
    setStep('verify');
    window.LotoTNative?.connectBluetooth?.(device.address, device.medium || method);
  };

  const startSelectedMethod = () => {
    setAttemptedMethod(method);
    setStep('verify');
    if (method === 'demo') window.LotoTNative?.startDemo?.();
    else if (method === 'usb') window.LotoTNative?.startUsbConnection?.();
    else if (method === 'network') window.LotoTNative?.connectNetwork?.(networkHost.trim(), Number(networkPort) || 23);
  };

  const backToConnections = () => {
    setStep('connect');
    setAttemptedMethod(null);
    if (method === 'classic' || method === 'ble') window.LotoTNative?.scanBluetooth?.(method);
  };

  return (
    <main className="onboarding-shell">
      <div className="onboarding-orb onboarding-orb-one"/><div className="onboarding-orb onboarding-orb-two"/>
      <header className="onboarding-topbar">
        <div className="onboarding-brand"><img src="./app-icon.png" alt=""/><span>LoToTi</span></div>
        <div className="onboarding-progress" aria-label={t('onboarding.progress')}>
          {[0,1,2,3].map((index) => <i key={index} className={index <= stepIndex ? 'is-active' : ''}/>) }
        </div>
        {canClose ? <button type="button" className="onboarding-close" onClick={onClose} aria-label={t('generic.close')}><Icon name="close"/></button> : <span className="onboarding-top-spacer"/>}
      </header>

      {step === 'welcome' && (
        <section className="onboarding-stage onboarding-welcome">
          <div className="onboarding-hero-icon"><img src="./app-icon.png" alt=""/></div>
          <span className="onboarding-kicker">{t('onboarding.welcome_kicker')}</span>
          <h1>{t('onboarding.welcome_title')}</h1>
          <p>{t('onboarding.welcome_body')}</p>
          <div className="onboarding-promise-grid">
            <article><span><Icon name="health_stable"/></span><strong>{t('onboarding.promise_control')}</strong><small>{t('onboarding.promise_control_detail')}</small></article>
            <article><span><Icon name="vehicle"/></span><strong>{t('onboarding.promise_vehicle')}</strong><small>{t('onboarding.promise_vehicle_detail')}</small></article>
            <article><span><Icon name="nav_live"/></span><strong>{t('onboarding.promise_demo')}</strong><small>{t('onboarding.promise_demo_detail')}</small></article>
          </div>
          <button className="onboarding-primary" type="button" onClick={() => setStep('features')}>{t('onboarding.start')}<Icon name="chevron"/></button>
          <small className="onboarding-legal">{t('onboarding.change_later')}</small>
        </section>
      )}

      {step === 'features' && (
        <section className="onboarding-stage">
          <div className="onboarding-heading"><span>{t('onboarding.features_kicker')}</span><h1>{t('onboarding.features_title')}</h1><p>{t('onboarding.features_body')}</p></div>
          <div className="capability-list">
            <article className="capability-card is-core"><span className="capability-icon"><Icon name="vehicle"/></span><div><strong>{t('onboarding.core_title')}</strong><small>{t('onboarding.core_detail')}</small></div><b>{t('onboarding.required')}</b></article>
            <CapabilityChoice icon="location" checked={gpsEnabled} onChange={(v) => { setGpsEnabled(v); if (!v) setMqttShareGps(false); }} title={t('onboarding.gps_title')} detail={t('onboarding.gps_detail')}/>
            <CapabilityChoice icon="phone" checked={sensorsEnabled} onChange={(v) => { setSensorsEnabled(v); if (!v) setMqttShareSensors(false); }} title={t('onboarding.sensors_title')} detail={t('onboarding.sensors_detail')}/>
            <CapabilityChoice icon="cloud" checked={mqttEnabled} onChange={setMqttEnabled} title={t('onboarding.mqtt_title')} detail={t('onboarding.mqtt_detail')}/>
            {mqttEnabled && <div className="mqtt-sharing-choices">
              <span>{t('onboarding.mqtt_sharing')}</span>
              <CapabilityChoice compact icon="location" checked={mqttShareGps} onChange={setMqttShareGps} disabled={!gpsEnabled} title={t('onboarding.share_gps')} detail={t('onboarding.share_gps_detail')}/>
              <CapabilityChoice compact icon="phone" checked={mqttShareSensors} onChange={setMqttShareSensors} disabled={!sensorsEnabled} title={t('onboarding.share_sensors')} detail={t('onboarding.share_sensors_detail')}/>
            </div>}
            <CapabilityChoice icon="activity" checked={notificationsEnabled} onChange={setNotificationsEnabled} title={t('onboarding.notifications_title')} detail={t('onboarding.notifications_detail')}/>
          </div>
          <div className="onboarding-actions"><button type="button" className="onboarding-secondary" onClick={() => setStep('welcome')}>{t('onboarding.back')}</button><button type="button" className="onboarding-primary" onClick={applyCapabilities}>{t('onboarding.continue')}<Icon name="chevron"/></button></div>
        </section>
      )}

      {step === 'connect' && (
        <section className="onboarding-stage">
          <div className="onboarding-heading"><span>{t('onboarding.connection_kicker')}</span><h1>{t('onboarding.connection_title')}</h1><p>{t('onboarding.connection_body')}</p></div>
          <div className="connection-method-grid">
            <ConnectionMethod icon="bluetooth" active={method === 'classic'} title={t('connection.bluetooth_classic')} detail={t('onboarding.classic_detail')} onClick={() => chooseMethod('classic')}/>
            <ConnectionMethod icon="bluetooth" active={method === 'ble'} title={t('connection.bluetooth_le')} detail={t('onboarding.ble_detail')} onClick={() => chooseMethod('ble')}/>
            <ConnectionMethod icon="usb" active={method === 'usb'} title="USB" detail={t('onboarding.usb_detail')} onClick={() => chooseMethod('usb')}/>
            <ConnectionMethod icon="wifi" active={method === 'network'} title={t('onboarding.network_title')} detail={t('onboarding.network_detail')} onClick={() => chooseMethod('network')}/>
            <ConnectionMethod icon="play" active={method === 'demo'} title={t('onboarding.demo_title')} detail={t('onboarding.demo_detail')} onClick={() => chooseMethod('demo')}/>
          </div>

          {(method === 'classic' || method === 'ble') && <div className="onboarding-device-panel">
            <header><div><strong>{method === 'ble' ? t('connection.bluetooth_le') : t('connection.bluetooth_classic')}</strong><small>{t('connection.device_count', { count: devices.length })}</small></div><button type="button" onClick={() => window.LotoTNative?.scanBluetooth?.(method)} disabled={bluetooth.scanning}><Icon name="refresh"/>{bluetooth.scanning ? t('connection.searching') : t('connection.refresh')}</button></header>
            {bluetooth.error && <div className="connection-error">{bluetooth.error}</div>}
            <div className="onboarding-device-list">
              {devices.map((device) => <DeviceRow key={`${device.medium}:${device.address}`} device={device} connected={bluetooth.connectedDevice} selected={bluetooth.selectedDevice} connecting={bluetooth.status === 'connecting'} onConnect={connectBluetooth}/>)}
              {!devices.length && <div className="onboarding-empty"><span className={bluetooth.scanning ? 'scanner-orbit is-scanning' : 'scanner-orbit'}><Icon name="bluetooth"/></span><strong>{bluetooth.scanning ? t('connection.searching_adapters') : t('connection.none')}</strong><small>{t('connection.none_help')}</small></div>}
            </div>
          </div>}

          {method === 'network' && <div className="network-onboarding-form"><label><span>{t('onboarding.network_host')}</span><input value={networkHost} onChange={(e) => setNetworkHost(e.target.value)} placeholder="192.168.0.10" inputMode="decimal" autoCapitalize="none"/></label><label><span>{t('service.port')}</span><input value={networkPort} onChange={(e) => setNetworkPort(e.target.value)} type="number" min="1" max="65535" inputMode="numeric"/></label></div>}
          {method && !['classic','ble'].includes(method) && <button className="onboarding-connect-button" type="button" disabled={method === 'network' && !networkHost.trim()} onClick={startSelectedMethod}><Icon name={method === 'usb' ? 'usb' : method === 'network' ? 'wifi' : 'play'}/><span>{method === 'demo' ? t('onboarding.start_demo') : t('onboarding.connect_now')}</span></button>}

          <div className="onboarding-actions"><button type="button" className="onboarding-secondary" onClick={() => setStep('features')}>{t('onboarding.back')}</button></div>
        </section>
      )}

      {step === 'verify' && (
        <section className="onboarding-stage onboarding-verify">
          <div className={`verification-emblem ${verified ? 'is-ready' : ''}`}><Icon name={verified ? 'health_stable' : 'activity'}/><i/></div>
          <span className="onboarding-kicker">{t('onboarding.verify_kicker')}</span>
          <h1>{verified ? t('onboarding.ready_title') : t('onboarding.verify_title')}</h1>
          <p>{verified ? t('onboarding.ready_body') : t('onboarding.verify_body')}</p>
          <div className="verification-list">
            <VerificationRow done={status === 'connecting' || connected || demo} active={status === 'connecting'} title={t('onboarding.verify_transport')} detail={t(`onboarding.method_${attemptedMethod || method || 'demo'}`)}/>
            <VerificationRow done={connected || demo} active={status === 'connecting'} title={t('onboarding.verify_adapter')} detail={demo ? t('onboarding.demo_engine') : t('onboarding.elm_handshake')}/>
            <VerificationRow done={demo || hasLiveData} active={connected && !hasLiveData} title={t('onboarding.verify_ecu')} detail={t('onboarding.verify_ecu_detail')}/>
            <VerificationRow done={verified} active={(connected || demo) && !verified} title={t('onboarding.verify_data')} detail={hasLiveData ? t('onboarding.signals_received', { count: signals.length }) : t('onboarding.waiting_signals')}/>
          </div>
          {bluetooth.error && <div className="connection-error verification-error">{bluetooth.error}</div>}
          {verified ? <button className="onboarding-primary onboarding-enter" type="button" onClick={onComplete}>{t('onboarding.enter_dashboard')}<Icon name="chevron"/></button> : <div className="verification-actions"><button type="button" className="onboarding-secondary" onClick={backToConnections}>{t('onboarding.change_method')}</button><button type="button" className="onboarding-ghost" onClick={() => { setAttemptedMethod('demo'); window.LotoTNative?.startDemo?.(); }}>{t('onboarding.try_demo')}</button></div>}
        </section>
      )}
    </main>
  );
}

function CapabilityChoice({ icon, checked, onChange, title, detail, disabled = false, compact = false }) {
  return <label className={`capability-card is-choice ${checked ? 'is-selected' : ''} ${disabled ? 'is-disabled' : ''} ${compact ? 'is-compact' : ''}`}><span className="capability-icon"><Icon name={icon}/></span><div><strong>{title}</strong><small>{detail}</small></div><input type="checkbox" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)}/><i className="capability-switch"/></label>;
}

function ConnectionMethod({ icon, active, title, detail, onClick }) {
  return <button type="button" className={`connection-method ${active ? 'is-active' : ''}`} onClick={onClick}><span><Icon name={icon}/></span><div><strong>{title}</strong><small>{detail}</small></div><i>{active ? '✓' : '›'}</i></button>;
}

function VerificationRow({ done, active, title, detail }) {
  return <article className={`verification-row ${done ? 'is-done' : ''} ${active ? 'is-active' : ''}`}><span>{done ? '✓' : active ? '…' : ''}</span><div><strong>{title}</strong><small>{detail}</small></div></article>;
}

function App() {
  const retainedPayload = telemetryBridge.lastPayload || {};
  const [initialAppearance] = useState(readNativeAppearanceSettings);
  const [initialOnboarding] = useState(readNativeOnboardingState);
  const [onboardingComplete, setOnboardingComplete] = useState(Boolean(initialOnboarding.complete));
  const [onboardingOpen, setOnboardingOpen] = useState(!initialOnboarding.complete);
  const [onboardingRequired, setOnboardingRequired] = useState(!initialOnboarding.complete);
  const [language, setLanguageState] = useState(getLanguage());
  const [languagePreference, setLanguagePreferenceState] = useState(readNativeLanguagePreference);
  setLanguage(language);
  const [readings, setReadings] = useState({ ...EMPTY_READINGS, ...(retainedPayload.readings || {}) });
  const [signals, setSignals] = useState(Array.isArray(retainedPayload.signals) ? retainedPayload.signals : []);
  const [diagnostics, setDiagnostics] = useState(retainedPayload.diagnostics || { scan_status: 'idle', scanned_at: 0, dtcs: [], mil: null, reported_dtc_count: null });
  const [vehicle, setVehicle] = useState(retainedPayload.vehicle && typeof retainedPayload.vehicle === 'object' ? retainedPayload.vehicle : {});
  const [history, setHistory] = useState({});
  const [status, setStatus] = useState(telemetryBridge.status || 'offline');
  const [bluetooth, setBluetooth] = useState({ ...EMPTY_BLUETOOTH, ...(bluetoothBridge.lastState || {}) });
  const [services, setServices] = useState({ ...EMPTY_BUILTINS, ...(builtinBridge.lastState || {}) });
  const [nativeAvailable, setNativeAvailable] = useState(Boolean(window.LotoTNative));
  const [connectionOpen, setConnectionOpen] = useState(false);
  const [servicesOpen, setServicesOpen] = useState(false);
  const [appearanceOpen, setAppearanceOpen] = useState(false);
  const [fontFamily, setFontFamily] = useState(() => {
    const saved = initialAppearance.font_family || localStorage.getItem('lotot-font-family');
    return FONT_FAMILIES.some((item) => item.id === saved) ? saved : DEFAULT_FONT_FAMILY;
  });
  const [fontScale, setFontScale] = useState(() => clampFontScale(initialAppearance.font_scale || localStorage.getItem('lotot-font-scale') || DEFAULT_FONT_SCALE));
  const [iconFamily, setIconFamily] = useState(() => normalizeIconFamily(initialAppearance.icon_family || localStorage.getItem('lotot-icon-family') || DEFAULT_ICON_FAMILY));
  const [medium, setMediumState] = useState(bluetooth.medium || 'classic');
  const [demoScenario, setDemoScenarioState] = useState(() => {
    const nativeScenario = readNativeDemoScenario();
    const saved = localStorage.getItem('lototi-demo-scenario');
    if (nativeScenario) return nativeScenario;
    return DEMO_SCENARIOS.some((item) => item.id === saved) ? saved : 'healthy';
  });
  const [query, setQuery] = useState('');
  const [aiQuestion, setAiQuestion] = useState('');
  const [aiState, setAiState] = useState(() => ({ ...aiBridge.lastState, ...readNativeAiStatus() }));
  const [aiStreamingText, setAiStreamingText] = useState('');
  const [aiMessages, setAiMessages] = useState(() => [{
    role: 'assistant',
    text: t('ai.welcome'),
    meta: 'LoToTi AI',
  }]);
  const aiThreadRef = React.useRef(null);
  const [category, setCategory] = useState('all');
  const [lastCapturedAt, setLastCapturedAt] = useState(Number(retainedPayload.captured_at) || 0);
  const [packetCount, setPacketCount] = useState(0);
  const [now, setNow] = useState(Date.now());
  const [theme, setTheme] = useState(() => (initialAppearance.theme || localStorage.getItem('lotot-theme')) === 'light' ? 'light' : 'dark');
  const [activeTab, setActiveTab] = useState(() => {
    const saved = localStorage.getItem('lotot-active-tab');
    return PRIMARY_NAV.some((item) => item.id === saved) ? saved : 'overview';
  });
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
    const selected = fontFamilyOption(fontFamily);
    document.documentElement.dataset.fontFamily = selected.id;
    document.documentElement.style.setProperty('--app-font-family', selected.stack);
    document.documentElement.style.webkitTextSizeAdjust = `${fontScale}%`;
    document.documentElement.style.textSizeAdjust = `${fontScale}%`;
    document.documentElement.dataset.iconFamily = iconFamily;
    localStorage.setItem('lotot-font-family', selected.id);
    localStorage.setItem('lotot-font-scale', String(fontScale));
    localStorage.setItem('lotot-icon-family', iconFamily);
    try {
      window.LotoTNative?.setAppearanceSettings?.(JSON.stringify({
        theme,
        font_family: selected.id,
        font_scale: fontScale,
        icon_family: iconFamily,
      }));
    } catch (_) {
      // Older debug APKs simply keep using localStorage until the native bridge is updated.
    }
  }, [theme, fontFamily, fontScale, iconFamily]);

  const changeLanguagePreference = (value) => {
    const preference = normalizeLanguagePreference(value);
    localStorage.setItem('lotot-language-preference', preference);
    let resolved = preference === 'system' ? (navigator.language || 'en') : preference;
    try {
      resolved = window.LotoTNative?.setAppLanguage?.(preference) || resolved;
    } catch (_) {
      // Browser preview or an older APK: use the requested locale locally.
    }
    setLanguagePreferenceState(preference);
    setLanguageState(setLanguage(resolved));
  };

  useEffect(() => {
    document.documentElement.lang = language;
  }, [language]);

  useEffect(() => {
    localStorage.setItem('lototi-demo-scenario', demoScenario);
    window.LotoTNative?.setDemoScenario?.(demoScenario);
  }, [demoScenario]);

  useEffect(() => {
    setAiMessages((current) => {
      if (current.length !== 1 || current[0]?.role !== 'assistant') return current;
      return [{ ...current[0], text: t('ai.welcome') }];
    });
  }, [language]);

  useEffect(() => {
    if (activeTab !== 'ai') return;
    const node = aiThreadRef.current;
    if (node) window.requestAnimationFrame(() => node.scrollTo({ top: node.scrollHeight, behavior: 'smooth' }));
  }, [aiMessages, aiState.status, activeTab]);

  useEffect(() => {
    localStorage.setItem('lotot-active-tab', activeTab);
    window.scrollTo({ top: 0, behavior: 'auto' });
  }, [activeTab]);

  useEffect(() => {
    localStorage.setItem('lotot-signal-favorites', JSON.stringify(favorites));
  }, [favorites]);

  useEffect(() => {
    const onTelemetry = (event) => {
      const payload = event.detail || {};
      if (payload.readings) setReadings((current) => ({ ...current, ...payload.readings }));
      if (payload.diagnostics) setDiagnostics(payload.diagnostics);
      if (payload.vehicle && typeof payload.vehicle === 'object') setVehicle(payload.vehicle);
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
      if (payload.demo_scenario && DEMO_SCENARIOS.some((item) => item.id === payload.demo_scenario)) setDemoScenarioState(payload.demo_scenario);
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
    const onLanguage = (event) => { setLanguageState(setLanguage(event.detail?.language || 'en')); const pref = window.LotoTNative?.getAppLanguagePreference?.(); if (pref) setLanguagePreferenceState(normalizeLanguagePreference(pref)); };
    const onAi = (eventOrState) => {
      const next = eventOrState?.detail || eventOrState || {};
      setAiState((current) => ({ ...current, ...next }));
      if (next.status === 'trying') {
        setAiStreamingText('');
      } else if (next.status === 'streaming' && next.delta) {
        setAiStreamingText((current) => current + String(next.delta));
      } else if (next.status === 'ready' && next.text) {
        setAiStreamingText('');
        setAiMessages((current) => {
          const last = current[current.length - 1];
          if (last?.role === 'assistant' && last.text === next.text) return current;
          return [...current, { role: 'assistant', text: next.text, meta: [next.provider, next.model].filter(Boolean).join(' · ') }];
        });
      } else if (next.status === 'error' && next.error) {
        setAiStreamingText('');
        setAiMessages((current) => {
          const last = current[current.length - 1];
          if (last?.role === 'error' && last.text === next.error) return current;
          return [...current, { role: 'error', text: next.error, meta: t('generic.error') }];
        });
      }
    };
    window.addEventListener('lotot:telemetry', onTelemetry);
    window.addEventListener('lotot:fast-telemetry', onFastTelemetry);
    window.addEventListener('lotot:telemetry-status', onStatus);
    window.addEventListener('lotot:bluetooth-state', onBluetooth);
    window.addEventListener('lotot:builtin-state', onBuiltins);
    window.addEventListener('lotot:language', onLanguage);
    window.addEventListener('lotot:ai-state', onAi);
    window.lototAiStateListener = onAi;
    if (aiBridge.lastState && aiBridge.lastState.status && aiBridge.lastState.status !== 'idle') onAi({ ...aiBridge.lastState });
    setNativeAvailable(Boolean(window.LotoTNative));
    const nativeLanguage = window.LotoTNative?.getAppLanguage?.();
    if (nativeLanguage) setLanguageState(setLanguage(nativeLanguage));
    const nativeLanguagePreference = window.LotoTNative?.getAppLanguagePreference?.();
    if (nativeLanguagePreference) setLanguagePreferenceState(normalizeLanguagePreference(nativeLanguagePreference));
    window.LotoTNative?.ready?.();
    return () => {
      window.removeEventListener('lotot:telemetry', onTelemetry);
      window.removeEventListener('lotot:fast-telemetry', onFastTelemetry);
      window.removeEventListener('lotot:telemetry-status', onStatus);
      window.removeEventListener('lotot:bluetooth-state', onBluetooth);
      window.removeEventListener('lotot:builtin-state', onBuiltins);
      window.removeEventListener('lotot:language', onLanguage);
      window.removeEventListener('lotot:ai-state', onAi);
      if (window.lototAiStateListener === onAi) window.lototAiStateListener = null;
    };
  }, []);

  const state = useMemo(() => ({
    live: ['live', 'demo'].includes(status),
    lost: status === 'lost',
    label: status === 'demo'
      ? t('status.demo')
      : status === 'live'
        ? t('status.live')
        : status === 'connecting'
          ? t('status.connecting')
          : status === 'lost'
            ? t('status.lost')
            : t('status.offline'),
  }), [status, language]);

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
  }), [signals, language]);

  const healthIcon = liveAlerts.some((item) => item.level === 'danger') ? 'health_critical' : liveAlerts.length ? 'health_warning' : 'health_stable';

  const alertSignals = useMemo(() => liveAlerts.map((item) => item.signal).filter(Boolean), [liveAlerts]);

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
  }, [signals, category, query, favorites, language]);

  const connected = bluetooth.connectedDevice;
  const lastDevice = bluetooth.lastDevice;
  const toggleFavorite = (key) => setFavorites((current) => current.includes(key) ? current.filter((entry) => entry !== key) : [...current, key]);
  const lastAge = lastCapturedAt ? Math.max(0, now - lastCapturedAt) : null;
  const fluxLabel = !['live', 'demo'].includes(status)
    ? t('status.flow_stopped')
    : lastAge === null
      ? t('status.no_flow')
      : lastAge < 1500
        ? t('status.instant')
        : t('status.delay', { seconds: Math.floor(lastAge / 1000) });

  const diagnosticDtcs = Array.isArray(diagnostics?.dtcs) ? diagnostics.dtcs : [];
  const diagnosticMil = diagnostics?.mil === null || diagnostics?.mil === undefined ? null : Number(diagnostics.mil) > 0;
  const rawReportedDtcCount = diagnostics?.reported_dtc_count === null || diagnostics?.reported_dtc_count === undefined
    ? null : Number(diagnostics.reported_dtc_count);
  const diagnosticReportedCount = rawReportedDtcCount !== null && Number.isFinite(rawReportedDtcCount)
    ? Math.max(0, Math.round(rawReportedDtcCount)) : null;
  const diagnosticUniqueCodes = [...new Set(diagnosticDtcs
    .map((dtc) => String(dtc?.code || '').trim().toUpperCase())
    .filter(Boolean))];
  const diagnosticScannedCount = diagnostics?.scan_status === 'ready' ? diagnosticUniqueCodes.length : null;
  const aiFaultContextLabel = diagnosticScannedCount !== null
    ? t('ai.dtc_current', { count: diagnosticScannedCount })
    : diagnosticReportedCount !== null
      ? t('ai.dtc_reported', { count: diagnosticReportedCount })
      : t('ai.dtc_unscanned');
  const faultScanBusy = diagnostics?.scan_status === 'scanning';

  const diagnosticCopy = signals.length
    ? { title: t('diagnostic.live_sensors', { count: signals.length }), body: t('diagnostic.last_packet', { age: ageLabel(lastCapturedAt, now), count: packetCount }) }
    : connected
      ? { title: t('diagnostic.pid_wait'), body: t('diagnostic.pid_wait_detail') }
      : status === 'demo'
        ? { title: t('diagnostic.demo'), body: t('diagnostic.demo_detail') }
        : status === 'lost'
          ? { title: t('diagnostic.lost'), body: t('diagnostic.lost_detail', { name: lastDevice?.name || t('connection.adapter') }) }
          : { title: t('diagnostic.ready'), body: t('diagnostic.ready_detail') };

  const askLoToTiAi = (presetQuestion = null) => {
    const question = String(presetQuestion || aiQuestion || '').trim();
    if (!question || ['thinking', 'trying', 'streaming'].includes(aiState.status)) return;

    const sourceFor = (signal) => signal?.source || (['live', 'demo'].includes(status) ? 'obd' : 'unknown');
    const obdSignals = signals.filter((signal) => sourceFor(signal) === 'obd');
    const motionSignals = signals.filter((signal) => sourceFor(signal) === 'motion');
    const gpsSignals = signals.filter((signal) => sourceFor(signal) === 'gps');
    const ecuDataAvailable = ['live', 'demo'].includes(status) && obdSignals.length > 0;
    const interestingSignals = signals
      .filter((signal) => isNumericReading(signal.value) || categoryFor(signal) === 'diagnostic')
      .slice(0, 40)
      .map((signal) => {
        const source = sourceFor(signal);
        const label = signalLabel(signal);
        const age = signal.updated_at ? Math.max(0, Math.round((Date.now() - Number(signal.updated_at)) / 1000)) : null;
        return `[${source}] ${signal.mnemonic || signal.label} | ${label} = ${signal.value}${signal.unit ? ` ${signal.unit}` : ''}${age !== null ? ` | age=${age}s` : ''}`;
      });
    const scannedDtcLines = diagnosticDtcs.map((dtc) => `[${dtc.status || 'scanned'}] ${dtc.code}${dtc.description ? ` — ${dtc.description}` : ''}`);
    const vehicleIdentityLines = [
      vehicle?.vin ? `VIN: ${vehicle.vin}` : null,
      vehicle?.wmi ? `WMI: ${vehicle.wmi}` : null,
      vehicle?.calibration_id ? `Calibration ID: ${vehicle.calibration_id}` : null,
      vehicle?.calibration_id_2 ? `Calibration ID 2: ${vehicle.calibration_id_2}` : null,
      vehicle?.source_type ? `Source type: ${vehicle.source_type}` : 'Source type: unknown',
      vehicle?.source_profile ? `Source profile: ${vehicle.source_profile}` : null,
      vehicle?.profile_id ? `Profile ID: ${vehicle.profile_id}` : null,
      vehicle?.manufacturer ? `Manufacturer: ${vehicle.manufacturer}` : null,
      vehicle?.model ? `Model: ${vehicle.model}` : null,
      vehicle?.model_year ? `Model year: ${vehicle.model_year}` : null,
      vehicle?.engine ? `Engine: ${vehicle.engine}` : null,
      vehicle?.ecu_name ? `ECU name: ${vehicle.ecu_name}` : null,
      vehicle?.vin_synthetic ? 'VIN provenance: synthetic emulator marker' : null,
      vehicle?.source ? `Evidence source: ${vehicle.source}` : null,
    ].filter(Boolean);
    const recentConversation = aiMessages
      .filter((message) => ['user', 'assistant'].includes(message.role))
      .slice(-6)
      .map((message) => `${message.role.toUpperCase()}: ${message.text}`)
      .join('\n');
    const context = [
      `App language: ${language}`,
      `Session state: ${status}`,
      `OBD/ECU data available: ${ecuDataAvailable ? 'yes' : 'no'}`,
      `Data source counts: OBD=${obdSignals.length}, phone_motion=${motionSignals.length}, GPS=${gpsSignals.length}`,
      !ecuDataAvailable ? 'Evidence boundary: There is no live ECU/OBD evidence in this request. Do not infer engine, transmission, emissions, suspension, or DTC health from auxiliary phone sensors.' : null,
      connected ? `Adapter: ${connected.name || connected.address || 'connected'}` : 'Adapter: none',
      vehicleIdentityLines.length ? `OBD MODE 09 VEHICLE IDENTITY:
- ${vehicleIdentityLines.join('\n- ')}` : 'OBD Mode 09 vehicle identity: unavailable in this session.',
      `ECU MIL status: ${diagnosticMil === null ? 'unknown' : diagnosticMil ? 'ON' : 'OFF'}`,
      `ECU-reported DTC count: ${diagnosticReportedCount === null ? 'unknown' : diagnosticReportedCount}`,
      `Scan-confirmed unique DTC count: ${diagnosticScannedCount === null ? 'not scanned' : diagnosticScannedCount}`,
      `DTC scan state: ${diagnostics?.scan_status || 'idle'}${diagnostics?.scanned_at ? ` at ${new Date(Number(diagnostics.scanned_at)).toISOString()}` : ''}`,
      scannedDtcLines.length ? `SCAN-CONFIRMED DTC EVIDENCE:\n- ${scannedDtcLines.join('\n- ')}` : 'DTC evidence: no explicit code list has been scanned in this session. Missing scan results do NOT mean zero DTCs.',
      liveAlerts.length ? `UI-derived alerts (not scan-confirmed): ${liveAlerts.map((alert) => alert.message).slice(0, 10).join(' | ')}` : 'UI-derived alerts: none',
      interestingSignals.length ? `Current signals:\n- ${interestingSignals.join('\n- ')}` : 'Current signals: unavailable',
      recentConversation ? `Recent conversation:\n${recentConversation}` : null,
    ].filter(Boolean).join('\n');

    setAiMessages((current) => [...current, { role: 'user', text: question, meta: status === 'demo' ? t('status.demo') : t('ai.you') }]);
    setAiQuestion('');
    setAiStreamingText('');
    setAiState((current) => ({ ...current, status: 'thinking', provider: null, error: null, text: null }));
    window.LotoTNative?.askAi?.(JSON.stringify({
      question, context, language, manufacturer: vehicle?.dtc_manufacturer || vehicle?.manufacturer || ''
    }));
  };

  const selectDemoScenario = (scenarioId) => {
    if (!DEMO_SCENARIOS.some((item) => item.id === scenarioId)) return;
    setDemoScenarioState(scenarioId);
    window.LotoTNative?.setDemoScenario?.(scenarioId);
  };

  const startSelectedDemo = () => {
    window.LotoTNative?.setDemoScenario?.(demoScenario);
    window.LotoTNative?.startDemo?.();
  };

  const openConnection = () => {
    setConnectionOpen(true);
    window.LotoTNative?.scanBluetooth?.(medium);
  };
  const setMedium = (nextMedium) => {
    setMediumState(nextMedium);
    window.LotoTNative?.scanBluetooth?.(nextMedium);
  };
  const connectDevice = (device) => window.LotoTNative?.connectBluetooth?.(device.address, device.medium || medium);
  const reconnectLastDevice = () => {
    if (!lastDevice) return openConnection();
    if (lastDevice.medium === 'usb') return window.LotoTNative?.startUsbConnection?.();
    if (lastDevice.medium === 'network') {
      const endpoint = String(lastDevice.address || '');
      const split = endpoint.lastIndexOf(':');
      const host = split > 0 ? endpoint.slice(0, split) : endpoint;
      const port = split > 0 ? Number(endpoint.slice(split + 1)) || 23 : 23;
      return window.LotoTNative?.connectNetwork?.(host, port);
    }
    return connectDevice(lastDevice);
  };
  const connectionAction = status === 'lost' && lastDevice ? reconnectLastDevice : openConnection;
  const gatewayDetail = services.mqtt?.status === 'syncing'
    ? t('service.remaining', { count: services.mqtt.syncing_remaining || 0 })
    : services.mqtt?.queue_depth
      ? t('service.queued', { count: services.mqtt.queue_depth })
      : services.mqtt?.last_publish
        ? t('service.updated', { age: ageLabel(services.mqtt.last_publish, now) })
        : services.mqtt?.broker || t('service.no_broker');

  const finishOnboarding = React.useCallback(() => {
    window.LotoTNative?.setOnboardingComplete?.(true);
    localStorage.setItem('lototi-onboarding-complete', '1');
    setOnboardingComplete(true);
    setOnboardingRequired(false);
    setOnboardingOpen(false);
  }, []);
  const reopenOnboarding = () => {
    setOnboardingRequired(false);
    setOnboardingOpen(true);
  };

  const aiBusy = ['thinking', 'trying', 'streaming'].includes(aiState.status);
  const aiProviderLabel = aiState.provider || (aiBusy ? aiState.primary_label : aiState.provider) || aiState.primary_label || t('ai.ready');
  const aiProviderIsFallback = Boolean(aiState.provider && aiState.primary_label && aiState.provider !== aiState.primary_label);
  const aiModelLabel = aiState.model || aiState.primary_model || t('ai.model_auto');

  if (onboardingOpen || !onboardingComplete) {
    return <IconFamilyProvider family={iconFamily}><OnboardingFlow services={services} bluetooth={bluetooth} status={status} signals={signals} initialPermissionState={initialOnboarding} canClose={!onboardingRequired && onboardingComplete} onClose={() => setOnboardingOpen(false)} onComplete={finishOnboarding}/></IconFamilyProvider>;
  }

  return (
    <IconFamilyProvider family={iconFamily}>
    <main className="app-shell">
      <header className="topbar">
        <div className="brand" aria-label="LoToTi">
          <img className="brand-logo brand-logo-dark" src="./logo-dark.png" alt="" />
          <img className="brand-logo brand-logo-light" src="./logo-light.png" alt="" />
        </div>
        <button className={`status-pill ${state.live ? 'is-live' : ''} ${state.lost ? 'is-lost' : ''}`} type="button" onClick={openConnection}><i/><span>{state.label}</span></button>
        <div className="topbar-actions">
          <button className="topbar-icon-button topbar-settings" type="button" onClick={() => window.LotoTNative?.openNativeTools?.()} disabled={!nativeAvailable} aria-label={t('action.advanced_settings')} title={t('action.advanced_settings')}><Icon name="tool"/></button>
          <button className="topbar-icon-button theme-toggle" type="button" onClick={() => setTheme((current) => current === 'dark' ? 'light' : 'dark')} aria-label={theme === 'dark' ? t('theme.light') : t('theme.dark')} title={theme === 'dark' ? t('theme.light') : t('theme.dark')}><Icon name={theme === 'dark' ? 'sun' : 'moon'}/></button>
        </div>
      </header>

      <div className="page-stack" data-active-page={activeTab}>
        {activeTab === 'overview' && (
          <section className="app-page app-page-overview">
            <section className={`connection-summary ${state.live ? 'is-live' : ''} ${state.lost ? 'is-lost' : ''}`}>
              <button type="button" onClick={connectionAction} disabled={!nativeAvailable}>
                <span className="connection-icon"><Icon name={state.live || connected ? 'obd_connected' : 'obd_disconnected'}/></span>
                <span className="connection-copy"><small>{state.label}</small><strong>{connected?.name || (state.lost ? lastDevice?.name || t('connection.lost') : t('connection.connect_adapter'))}</strong></span>
                <span className="connection-meta"><em>{t('connection.live_signals', { count: signals.length })}</em><small>{fluxLabel}</small></span>
                <Icon name="chevron"/>
              </button>
            </section>

            <section className="overview-dashboard">
              <DriveOverview readings={readings} valueOf={valueOf} historyOf={historyOf}/>
              <HealthOverview alerts={liveAlerts} signals={signals} lastCapturedAt={lastCapturedAt} now={now}/>
            </section>

            <section className="critical-overview">
              <OverviewMetric label={t('overview.coolant')} value={valueOf('engine_coolant_temperature', 'coolant_temp')} unit="°C" icon="sensor_coolant" tone={toneForTemperature(valueOf('engine_coolant_temperature', 'coolant_temp'), TEMPERATURE_PROFILES.engine_coolant_temperature)} detail="70–105 normal"/>
              <OverviewMetric label={t('overview.oil')} value={valueOf('engine_oil_temperature')} unit="°C" icon="sensor_oil" tone={toneForTemperature(valueOf('engine_oil_temperature'), TEMPERATURE_PROFILES.engine_oil_temperature)} detail="70–110 normal"/>
              <OverviewMetric label={t('overview.voltage')} value={readings.module_voltage ?? valueOf('ecu_voltage')} unit="V" icon="sensor_voltage" tone={toneForVoltage(readings.module_voltage ?? valueOf('ecu_voltage'))} detail="12.3–14.8 V"/>
              <OverviewMetric label={t('overview.fuel')} value={valueOf('fuel_level', 'fuel_tank_level_input')} unit="%" icon="sensor_fuel" tone={toneForFuel(valueOf('fuel_level', 'fuel_tank_level_input'))} detail={`${formatValue(valueOf('engine_fuel_rate'), 1)} L/h`}/>
              <OverviewMetric label={t('overview.intake')} value={valueOf('intake_manifold_pressure')} unit="kPa" icon="sensor_intake" detail={`${formatValue(valueOf('intake_air_temperature'), 0)} °C`}/>
              <OverviewMetric label={t('overview.pressure')} value={valueOf('fuel_pressure')} unit="kPa" icon="sensor_pressure" detail={`MAF ${formatValue(readings.maf, 1)} g/s`}/>
            </section>

            <section className={`health-card compact-health ${liveAlerts.length ? 'has-alerts' : ''}`} onClick={() => setActiveTab('health')} role="button" tabIndex="0">
              <span className="health-icon"><Icon name={healthIcon}/></span>
              <div><strong>{liveAlerts.length ? t('health.points', { count: liveAlerts.length }) : diagnosticCopy.title}</strong><p>{liveAlerts[0]?.message || diagnosticCopy.body}</p></div>
              <Icon name="chevron"/>
            </section>
          </section>
        )}

        {activeTab === 'live' && (
          <section className="app-page app-page-live">
            <PageHeader kicker={t('page.live.kicker')} title={t('page.live.title')} detail={t('page.live.detail', { count: signals.length })}/>
            <section className="data-panel">
              <header className="data-panel-header">
                <div><span>{t('explorer.kicker')}</span><h2>{t('explorer.title')}</h2></div>
                <strong>{filteredSignals.length}/{signals.length}</strong>
              </header>

              <label className="search-box">
                <Icon name="search"/>
                <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('explorer.search')} />
                {query && <button type="button" onClick={() => setQuery('')} aria-label={t('generic.clear')}><Icon name="close"/></button>}
              </label>

              <nav className="category-tabs" aria-label={t('explorer.categories')}>
                {CATEGORIES.map((item) => {
                  const count = item.id === 'all' ? signals.length : item.id === 'favorites' ? favorites.filter((key) => signals.some((signal) => signalKey(signal) === key)).length : (categoryCounts[item.id] || 0);
                  if (!['all', 'favorites'].includes(item.id) && count === 0) return null;
                  return <button type="button" key={item.id} className={category === item.id ? 'is-active' : ''} onClick={() => setCategory(item.id)}><Icon name={item.icon}/><span>{t(item.labelKey)}</span><em>{count}</em></button>;
                })}
              </nav>

              <div className="signal-grid">
                {filteredSignals.map((signal) => (
                  <SignalCard key={signalKey(signal)} signal={signal} favorite={favorites.includes(signalKey(signal))} onToggleFavorite={toggleFavorite} history={history} now={now}/>
                ))}
                {!filteredSignals.length && (
                  <div className="empty-signals"><Icon name="activity"/><strong>{signals.length ? t('explorer.none_match') : t('explorer.waiting')}</strong><p>{signals.length ? t('explorer.adjust') : t('explorer.valid_later')}</p></div>
                )}
              </div>
            </section>
          </section>
        )}

        {activeTab === 'ai' && (
          <section className="app-page app-page-ai">
            <section className="ai-page-shell">
              <header className="ai-page-header">
                <div className="ai-page-identity">
                  <span className="ai-page-orb"><Icon name="nav_ai"/></span>
                  <div><small>{t('ai.kicker')}</small><h1>LoToTi AI</h1><p>{t('ai.subtitle')}</p></div>
                </div>
                <div className="ai-provider-pill">
                  <i className={aiState.configured ? 'is-online' : ''}/>
                  <span>{aiBusy && !aiState.provider ? t('ai.thinking') : aiProviderLabel}{aiProviderIsFallback ? ` · ${t('ai.fallback')}` : ''}</span>
                </div>
              </header>

              <section className="ai-context-strip" aria-label={t('ai.context')}>
                <span><Icon name={status === 'demo' ? 'play' : 'vehicle'}/>{status === 'demo' ? `${t('status.demo')} · ${t(demoScenarioOption(demoScenario).labelKey)}` : connected ? (vehicle?.vin ? t('ai.vin_short', { vin: String(vehicle.vin).slice(-6) }) : t('status.live')) : t('status.offline')}</span>
                <span><Icon name={(diagnosticScannedCount || diagnosticReportedCount || 0) > 0 ? 'dtc_fault' : 'health_stable'}/>{aiFaultContextLabel}</span>
                <span><Icon name="activity"/>{signals.length} {t('ai.signals')}</span>
              </section>

              <div className="ai-chat-thread" ref={aiThreadRef}>
                {aiMessages.map((message, index) => (
                  <article key={`${message.role}-${index}`} className={`ai-message is-${message.role}`}>
                    {message.role !== 'user' && <span className="ai-message-avatar"><Icon name={message.role === 'error' ? 'health_critical' : 'nav_ai'}/></span>}
                    <div className="ai-message-bubble">
                      <small>{message.meta || (message.role === 'user' ? t('ai.you') : 'LoToTi AI')}</small>
                      <MarkdownMessage text={message.text}/>
                    </div>
                  </article>
                ))}
                {aiBusy && (
                  <article className="ai-message is-assistant is-thinking">
                    <span className="ai-message-avatar"><Icon name="nav_ai"/></span>
                    <div className="ai-message-bubble">
                      <small>{aiState.provider ? [aiState.provider, aiState.model].filter(Boolean).join(' · ') : 'LoToTi AI'}</small>
                      {aiStreamingText ? <MarkdownMessage text={aiStreamingText}/> : <div className="ai-thinking-dots"><i/><i/><i/></div>}
                    </div>
                  </article>
                )}
              </div>

              <section className="ai-quick-prompts">
                <button type="button" onClick={() => askLoToTiAi(t('ai.prompt.analyze_live'))}>{t('ai.analyze_live')}</button>
                <button type="button" onClick={() => askLoToTiAi(t('ai.prompt.p0301'))}>P0301</button>
                <button type="button" onClick={() => askLoToTiAi(t('ai.prompt.p0420'))}>P0420</button>
                <button type="button" onClick={() => askLoToTiAi(t('ai.prompt.health_summary'))}>{t('ai.health_summary')}</button>
              </section>

              <form className="ai-composer" onSubmit={(event) => { event.preventDefault(); askLoToTiAi(); }}>
                <textarea dir="auto" value={aiQuestion} onChange={(event) => setAiQuestion(event.target.value)} placeholder={t('ai.placeholder')} rows="1" onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); askLoToTiAi(); } }}/>
                <button type="submit" aria-label={t('ai.ask')} disabled={!nativeAvailable || !aiState.configured || !aiQuestion.trim() || aiBusy}><Icon name="chevron"/></button>
              </form>
              <footer className="ai-page-footer"><span>{aiModelLabel}</span><span>·</span><span>{t('ai.context_auto')}</span></footer>
            </section>
          </section>
        )}

        {activeTab === 'health' && (
          <section className="app-page app-page-health">
            <PageHeader kicker={t('page.health.kicker')} title={t('page.health.title')} detail={t('page.health.detail', { count: liveAlerts.length })}/>

            <section className="health-page-summary">
              <HealthOverview alerts={liveAlerts} signals={signals} lastCapturedAt={lastCapturedAt} now={now}/>
              <section className={`health-card health-page-message ${liveAlerts.length ? 'has-alerts' : ''}`}>
                <span className="health-icon"><Icon name={healthIcon}/></span>
                <div><strong>{liveAlerts.length ? t('health.points', { count: liveAlerts.length }) : t('health.no_alerts')}</strong><p>{liveAlerts[0]?.message || t('health.no_alerts_detail')}</p></div>
              </section>
            </section>

            <section className={`fault-scan-panel ${diagnosticMil ? 'has-mil' : ''}`}>
              <header className="fault-scan-header">
                <div><span>{t('faults.kicker')}</span><h2>{t('faults.title')}</h2><p>{t('faults.detail')}</p></div>
                <button type="button" onClick={() => window.LotoTNative?.scanFaults?.()} disabled={!nativeAvailable || faultScanBusy || !['live', 'demo'].includes(status)}>
                  <Icon name={faultScanBusy ? 'activity' : 'diagnostic_scan'}/><span>{faultScanBusy ? t('faults.scanning') : t('faults.scan')}</span>
                </button>
              </header>
              <div className="fault-scan-summary">
                <div><small>{t('faults.mil')}</small><strong className={diagnosticMil ? 'is-danger' : ''}>{diagnosticMil === null ? '—' : diagnosticMil ? t('faults.on') : t('faults.off')}</strong></div>
                <div><small>{t('faults.reported')}</small><strong>{diagnosticReportedCount === null ? '—' : diagnosticReportedCount}</strong></div>
                <div><small>{t('faults.last_scan')}</small><strong>{diagnostics?.scanned_at ? ageLabel(Number(diagnostics.scanned_at), now) : t('faults.not_scanned')}</strong></div>
              </div>
              {diagnosticDtcs.length > 0 ? (
                <div className="fault-code-grid">
                  {diagnosticDtcs.map((dtc, index) => (
                    <article className="fault-code-card" key={`${dtc.code}-${dtc.status}-${index}`}>
                      <div><code dir="ltr">{dtc.code}</code><span>{t(`faults.status_${dtc.status || 'confirmed'}`)}</span></div>
                      <strong>{dtc.description || t('faults.no_description')}</strong>
                      <small>{[dtc.type, dtc.manufacturer && dtc.manufacturer !== 'GENERIC' ? dtc.manufacturer : null].filter(Boolean).join(' · ')}</small>
                    </article>
                  ))}
                </div>
              ) : (
                <div className="fault-scan-empty"><Icon name="dtc_fault"/><div><strong>{diagnostics?.scan_status === 'ready' ? t('faults.none_found') : t('faults.scan_needed')}</strong><p>{diagnostics?.scan_status === 'ready' ? t('faults.none_found_detail') : t('faults.scan_needed_detail')}</p></div></div>
              )}
            </section>

            <section className="critical-overview">
              <OverviewMetric label={t('overview.coolant')} value={valueOf('engine_coolant_temperature', 'coolant_temp')} unit="°C" icon="sensor_coolant" tone={toneForTemperature(valueOf('engine_coolant_temperature', 'coolant_temp'), TEMPERATURE_PROFILES.engine_coolant_temperature)} detail="70–105 normal"/>
              <OverviewMetric label={t('overview.oil')} value={valueOf('engine_oil_temperature')} unit="°C" icon="sensor_oil" tone={toneForTemperature(valueOf('engine_oil_temperature'), TEMPERATURE_PROFILES.engine_oil_temperature)} detail="70–110 normal"/>
              <OverviewMetric label={t('overview.voltage')} value={readings.module_voltage ?? valueOf('ecu_voltage')} unit="V" icon="sensor_voltage" tone={toneForVoltage(readings.module_voltage ?? valueOf('ecu_voltage'))} detail="12.3–14.8 V"/>
              <OverviewMetric label={t('overview.fuel')} value={valueOf('fuel_level', 'fuel_tank_level_input')} unit="%" icon="sensor_fuel" tone={toneForFuel(valueOf('fuel_level', 'fuel_tank_level_input'))} detail={`${formatValue(valueOf('engine_fuel_rate'), 1)} L/h`}/>
              <OverviewMetric label={t('overview.intake')} value={valueOf('intake_manifold_pressure')} unit="kPa" icon="sensor_intake" detail={`${formatValue(valueOf('intake_air_temperature'), 0)} °C`}/>
              <OverviewMetric label={t('overview.pressure')} value={valueOf('fuel_pressure')} unit="kPa" icon="sensor_pressure" detail={`MAF ${formatValue(readings.maf, 1)} g/s`}/>
            </section>

            <section className="data-panel health-alert-panel">
              <header className="data-panel-header">
                <div><span>{t('page.health.active_kicker')}</span><h2>{t('page.health.active_title')}</h2></div>
                <strong>{alertSignals.length}</strong>
              </header>
              <div className="signal-grid health-alert-grid">
                {alertSignals.map((signal) => (
                  <SignalCard key={signalKey(signal)} signal={signal} favorite={favorites.includes(signalKey(signal))} onToggleFavorite={toggleFavorite} history={history} now={now}/>
                ))}
                {!alertSignals.length && (
                  <div className="empty-signals health-clear-state"><Icon name="health_stable"/><strong>{t('health.no_alerts')}</strong><p>{t('health.no_alerts_detail')}</p></div>
                )}
              </div>
            </section>
          </section>
        )}

        {activeTab === 'more' && (
          <section className="app-page app-page-more">
            <PageHeader kicker={t('page.more.kicker')} title={t('page.more.title')} detail={t('page.more.detail')}/>
            <section className="builtins-panel">
              <header className="builtins-header"><div><span>{t('service.integrated')}</span><h2>{t('service.sources_cloud')}</h2></div><button type="button" onClick={() => setServicesOpen(true)}><Icon name="sliders"/><span>{t('generic.configure')}</span></button></header>
              <div className="service-grid">
                <ServiceTile icon="location" title={t('service.gps_position')} service={services.gps} detail={services.gps?.last_update ? ageLabel(services.gps.last_update, now) : services.gps?.permission_granted ? t('service.ready_location') : t('service.permission_required')} onClick={() => setServicesOpen(true)}/>
                <ServiceTile icon="phone" title={t('service.phone_sensors')} service={services.sensors} detail={services.sensors?.last_update ? ageLabel(services.sensors.last_update, now) : t('service.accelerometer')} onClick={() => setServicesOpen(true)}/>
                <ServiceTile icon="cloud" title={t('service.cloud_gateway')} kind="mqtt" service={services.mqtt} detail={gatewayDetail} onClick={() => setServicesOpen(true)}/>
              </div>
              <p className="builtins-note">{t('service.note')}</p>
            </section>

            <section className="demo-lab-panel">
              <header className="demo-lab-header">
                <div><span>{t('demo.kicker')}</span><h2>{t('demo.title')}</h2><p>{t('demo.detail')}</p></div>
                <span className={`demo-runtime-badge ${status === 'demo' ? 'is-running' : ''}`}><i/>{status === 'demo' ? t('demo.running') : t('demo.ready')}</span>
              </header>
              <div className="demo-scenario-grid">
                {DEMO_SCENARIOS.map((item) => (
                  <button type="button" key={item.id} className={demoScenario === item.id ? 'is-active' : ''} onClick={() => selectDemoScenario(item.id)}>
                    <span><Icon name={item.icon}/></span>
                    <div><strong>{t(item.labelKey)}</strong><small>{t(item.detailKey)}</small></div>
                    {item.code && <code dir="ltr">{item.code}</code>}
                  </button>
                ))}
              </div>
              <div className="demo-lab-footer">
                <div><small>{t('demo.selected')}</small><strong>{t(demoScenarioOption(demoScenario).labelKey)}</strong></div>
                <button type="button" onClick={startSelectedDemo} disabled={!nativeAvailable}><Icon name="play"/><span>{status === 'demo' ? t('demo.apply') : t('demo.start')}</span></button>
              </div>
            </section>

            <section className="appearance-panel">
              <header><div><span>{t('appearance.kicker')}</span><h2>{t('appearance.title')}</h2></div><button type="button" onClick={() => setAppearanceOpen(true)}><Icon name="sliders"/><span>{t('generic.configure')}</span></button></header>
              <button className="appearance-entry" type="button" onClick={() => setAppearanceOpen(true)}>
                <span className="appearance-entry-icon"><Icon name="nav_more"/></span>
                <span className="appearance-entry-copy"><small>{t('appearance.font_and_size')}</small><strong>{languageOption(languagePreference).labelKey ? t(languageOption(languagePreference).labelKey) : languageOption(languagePreference).label} · {t(fontFamilyOption(fontFamily).labelKey)} · {fontScale}% · {t(iconFamilyOption(iconFamily).labelKey)}</strong><em>{t('appearance.entry_detail')}</em></span>
                <Icon name="chevron"/>
              </button>
            </section>

            <section className="actions">
              <button className="primary" type="button" onClick={connectionAction} disabled={!nativeAvailable}><Icon name={status === 'lost' ? 'refresh' : (state.live || connected) ? 'obd_connected' : 'obd_disconnected'}/><span>{connected ? t('action.connection') : status === 'lost' && lastDevice ? t('action.reconnect') : t('action.connect')}</span></button>
              <button className="secondary" type="button" onClick={startSelectedDemo} disabled={!nativeAvailable}><Icon name="play"/><span>{t('action.demo')}</span></button>
              <button className="tools-button" type="button" onClick={reopenOnboarding}><Icon name="health_stable"/><span>{t('action.setup_privacy')}</span><Icon name="chevron"/></button>
              <button className="tools-button" type="button" onClick={() => window.LotoTNative?.openNativeTools?.()} disabled={!nativeAvailable}><Icon name="tool"/><span>{t('action.advanced_settings')}</span><Icon name="chevron"/></button>
            </section>

            <footer className="app-footer">{t('footer.copy')}</footer>
          </section>
        )}
      </div>

      <BottomNavigation active={activeTab} onChange={setActiveTab}/>

      <ConnectionSheet open={connectionOpen} onClose={() => setConnectionOpen(false)} bluetooth={bluetooth} medium={medium} setMedium={setMedium} onScan={(selectedMedium) => window.LotoTNative?.scanBluetooth?.(selectedMedium)} onConnect={connectDevice} onDisconnect={() => window.LotoTNative?.disconnectBluetooth?.()}/>
      <ServicesSheet open={servicesOpen} onClose={() => setServicesOpen(false)} services={services} signals={signals} onSave={(payload) => window.LotoTNative?.configureBuiltins?.(JSON.stringify(payload))} onRequestLocation={() => window.LotoTNative?.requestLocationPermission?.()} onPublishNow={() => window.LotoTNative?.publishMqttNow?.()}/>
      <AppearanceSheet open={appearanceOpen} onClose={() => setAppearanceOpen(false)} theme={theme} onThemeChange={setTheme} languagePreference={languagePreference} onLanguageChange={changeLanguagePreference} fontFamily={fontFamily} onFontFamilyChange={setFontFamily} fontScale={fontScale} onFontScaleChange={(value) => setFontScale(clampFontScale(value))} iconFamily={iconFamily} onIconFamilyChange={(value) => setIconFamily(normalizeIconFamily(value))}/>
    </main>
    </IconFamilyProvider>
  );
}

const root = createRoot(document.getElementById('root'));
if (new URLSearchParams(window.location.search).get('icons') === '1') {
  root.render(<IconShowcase />);
} else {
  root.render(<App />);
}
