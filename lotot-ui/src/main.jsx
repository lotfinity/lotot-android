import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

const EMPTY_READINGS = {
  vehicle_speed: 0,
  engine_rpm: 0,
  engine_load: 0,
  module_voltage: 0,
  maf: 0,
};

const bridge = window.lototTelemetryBridge = window.lototTelemetryBridge || {
  status: 'offline',
  lastPayload: null,
};

window.lototReceiveTelemetry = (payload) => {
  try {
    const parsed = typeof payload === 'string' ? JSON.parse(payload) : payload;
    if (!parsed || typeof parsed !== 'object') return;
    bridge.lastPayload = parsed;
    if (parsed.mode === 'demo' || parsed.mode === 'live') bridge.status = parsed.mode;
    window.dispatchEvent(new CustomEvent('lotot:telemetry', { detail: parsed }));
  } catch (error) {
    console.error('Invalid native telemetry payload', error);
  }
};

window.lototSetStatus = (status) => {
  if (!['live', 'connecting', 'offline', 'demo'].includes(status)) return;
  bridge.status = status;
  window.dispatchEvent(new CustomEvent('lotot:telemetry-status', { detail: { status } }));
};

const clamp = (value, min, max) => Math.max(min, Math.min(max, Number(value) || 0));
const format = (value, decimals = 0) => Number.isFinite(Number(value)) ? Number(value).toFixed(decimals) : '—';

function Icon({ name }) {
  const paths = {
    car: <><path d="M4 13l1.5-4.5A2 2 0 0 1 7.4 7h9.2a2 2 0 0 1 1.9 1.5L20 13"/><path d="M5 13h14a2 2 0 0 1 2 2v3H3v-3a2 2 0 0 1 2-2Z"/><path d="M5 18v2M19 18v2M7 15h.01M17 15h.01"/></>,
    bolt: <path d="m13 2-8 12h7l-1 8 8-12h-7l1-8Z" />,
    gauge: <><path d="M4.9 19a9 9 0 1 1 14.2 0"/><path d="m12 13 3-3"/><path d="M12 19v.01"/></>,
    wind: <><path d="M3 8h10a3 3 0 1 0-3-3"/><path d="M3 12h15a3 3 0 1 1-3 3"/><path d="M3 16h7"/></>,
    tool: <><path d="M14.7 6.3a4 4 0 0 0-5-5l2.1 2.1-2.4 2.4-2.1-2.1a4 4 0 0 0 5 5L20 16.4 16.4 20l-7.7-7.7a4 4 0 0 0-5-5l2.1 2.1"/></>,
    play: <path d="m8 5 11 7-11 7V5Z" />,
    shield: <><path d="M12 3 4 6v5c0 5 3.4 8.7 8 10 4.6-1.3 8-5 8-10V6l-8-3Z"/><path d="m9 12 2 2 4-4"/></>,
  };
  return <svg viewBox="0 0 24 24" aria-hidden="true">{paths[name]}</svg>;
}

function RingGauge({ label, value, unit, max, decimals = 0, icon }) {
  const progress = clamp(value, 0, max) / max * 100;
  return (
    <article className="metric-card">
      <div className="metric-head"><span>{label}</span><Icon name={icon}/></div>
      <div className="metric-ring" style={{ '--value': `${progress}%` }}>
        <div><strong>{format(value, decimals)}</strong><small>{unit}</small></div>
      </div>
    </article>
  );
}

function App() {
  const retained = bridge.lastPayload?.readings || {};
  const [readings, setReadings] = useState({ ...EMPTY_READINGS, ...retained });
  const [status, setStatus] = useState(bridge.status || 'offline');
  const [nativeAvailable, setNativeAvailable] = useState(Boolean(window.LotoTNative));

  useEffect(() => {
    const onTelemetry = (event) => {
      const incoming = event.detail?.readings;
      if (!incoming) return;
      setReadings((current) => ({ ...current, ...incoming }));
      setStatus(event.detail?.mode === 'demo' ? 'demo' : 'live');
    };
    const onStatus = (event) => setStatus(event.detail?.status || 'offline');
    window.addEventListener('lotot:telemetry', onTelemetry);
    window.addEventListener('lotot:telemetry-status', onStatus);
    setNativeAvailable(Boolean(window.LotoTNative));
    window.LotoTNative?.ready?.();
    return () => {
      window.removeEventListener('lotot:telemetry', onTelemetry);
      window.removeEventListener('lotot:telemetry-status', onStatus);
    };
  }, []);

  const state = useMemo(() => ({
    live: ['live', 'demo'].includes(status),
    label: status === 'demo' ? 'MODE DÉMO' : status === 'live' ? 'EN DIRECT' : status === 'connecting' ? 'CONNEXION' : 'HORS LIGNE',
  }), [status]);

  const speedProgress = clamp(readings.vehicle_speed, 0, 240) / 240 * 270;

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="brand">Loto<span>T</span></div>
        <div className={`status-pill ${state.live ? 'is-live' : ''}`}><i></i>{state.label}</div>
      </header>

      <section className="vehicle-card">
        <div>
          <span className="eyebrow">VÉHICULE CONNECTÉ</span>
          <h1>Votre voiture, enfin compréhensible.</h1>
          <p>Diagnostic OBD local alimenté par AndrOBD, présenté par LotoT.</p>
        </div>
        <div className="vehicle-symbol"><Icon name="car"/></div>
      </section>

      <section className="speed-card">
        <div className="card-title"><span>CONDUITE EN DIRECT</span><Icon name="gauge"/></div>
        <div className="speed-ring" style={{ '--angle': `${speedProgress}deg` }}>
          <div className="speed-core">
            <small>VITESSE</small>
            <strong>{format(readings.vehicle_speed)}</strong>
            <span>km/h</span>
          </div>
        </div>
        <div className="rpm-row"><span>Régime moteur</span><strong>{format(readings.engine_rpm)} <small>tr/min</small></strong></div>
      </section>

      <section className="metric-grid">
        <RingGauge label="CHARGE MOTEUR" value={readings.engine_load} unit="%" max={100} icon="gauge" />
        <RingGauge label="BATTERIE" value={readings.module_voltage} unit="V" max={16} decimals={1} icon="bolt" />
        <RingGauge label="DÉBIT D’AIR" value={readings.maf} unit="g/s" max={30} decimals={1} icon="wind" />
      </section>

      <section className="health-card">
        <span className="health-icon"><Icon name="shield"/></span>
        <div><strong>Moteur de diagnostic prêt</strong><p>Les lectures restent locales sur le téléphone. La synchronisation Django sera ajoutée au prochain jalon.</p></div>
      </section>

      <section className="actions">
        <button className="primary" type="button" onClick={() => window.LotoTNative?.startDemo?.()} disabled={!nativeAvailable}>
          <Icon name="play"/><span>Lancer le mode démo</span>
        </button>
        <button className="secondary" type="button" onClick={() => window.LotoTNative?.openNativeTools?.()} disabled={!nativeAvailable}>
          <Icon name="tool"/><span>Outils AndrOBD</span>
        </button>
      </section>

      <footer>Prototype GPL · Moteur AndrOBD · Interface LotoT</footer>
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);
