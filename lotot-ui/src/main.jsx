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
  error: null,
};

const telemetryBridge = window.lototTelemetryBridge = window.lototTelemetryBridge || {
  status: 'offline',
  lastPayload: null,
};

const bluetoothBridge = window.lototBluetoothBridge = window.lototBluetoothBridge || {
  lastState: EMPTY_BLUETOOTH,
};

window.lototReceiveTelemetry = (payload) => {
  try {
    const parsed = typeof payload === 'string' ? JSON.parse(payload) : payload;
    if (!parsed || typeof parsed !== 'object') return;
    telemetryBridge.lastPayload = parsed;
    if (['demo', 'live', 'connecting', 'offline'].includes(parsed.mode)) {
      telemetryBridge.status = parsed.mode;
    }
    window.dispatchEvent(new CustomEvent('lotot:telemetry', { detail: parsed }));
  } catch (error) {
    console.error('Invalid native telemetry payload', error);
  }
};

window.lototSetStatus = (status) => {
  if (!['live', 'connecting', 'offline', 'demo'].includes(status)) return;
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

const isNumericReading = (value) => value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value));
const clamp = (value, min, max) => Math.max(min, Math.min(max, isNumericReading(value) ? Number(value) : 0));
const format = (value, decimals = 0) => isNumericReading(value) ? Number(value).toFixed(decimals) : '—';
const mediumLabel = (medium) => medium === 'ble' ? 'Bluetooth LE' : 'Bluetooth classique';

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
  };
  return <svg viewBox="0 0 24 24" aria-hidden="true">{paths[name]}</svg>;
}

function RingGauge({ label, value, unit, max, decimals = 0, icon }) {
  const available = isNumericReading(value);
  const progress = available ? clamp(value, 0, max) / max * 100 : 0;
  return (
    <article className={`metric-card ${available ? '' : 'is-unavailable'}`}>
      <div className="metric-head"><span>{label}</span><Icon name={icon}/></div>
      <div className="metric-ring" style={{ '--value': `${progress}%` }}>
        <div><strong>{format(value, decimals)}</strong><small>{unit}</small></div>
      </div>
    </article>
  );
}

function DeviceRow({ device, connected, selected, connecting, onConnect }) {
  const isConnected = connected?.address === device.address;
  const isConnecting = connecting && selected?.address === device.address;
  return (
    <button
      type="button"
      className={`device-row ${isConnected ? 'is-connected' : ''} ${isConnecting ? 'is-connecting' : ''}`}
      onClick={() => onConnect(device)}
      disabled={isConnected || connecting}
    >
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
          <div>
            <span>CONNEXION OBD</span>
            <h2>Choisir un adaptateur</h2>
          </div>
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
          <button type="button" onClick={() => onScan(medium)} disabled={bluetooth.scanning}>
            <Icon name="refresh"/><span>{bluetooth.scanning ? 'Recherche…' : 'Actualiser'}</span>
          </button>
        </div>

        {bluetooth.error && <div className="connection-error">{bluetooth.error}</div>}

        <div className="device-list">
          {devices.map((device) => (
            <DeviceRow
              key={`${device.medium}:${device.address}`}
              device={device}
              connected={bluetooth.connectedDevice}
              selected={bluetooth.selectedDevice}
              connecting={connecting}
              onConnect={onConnect}
            />
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

function App() {
  const retained = telemetryBridge.lastPayload?.readings || {};
  const [readings, setReadings] = useState({ ...EMPTY_READINGS, ...retained });
  const [status, setStatus] = useState(telemetryBridge.status || 'offline');
  const [bluetooth, setBluetooth] = useState({ ...EMPTY_BLUETOOTH, ...(bluetoothBridge.lastState || {}) });
  const [nativeAvailable, setNativeAvailable] = useState(Boolean(window.LotoTNative));
  const [connectionOpen, setConnectionOpen] = useState(false);
  const [medium, setMediumState] = useState(bluetooth.medium || 'classic');

  useEffect(() => {
    const onTelemetry = (event) => {
      const incoming = event.detail?.readings;
      if (!incoming) return;
      setReadings((current) => ({ ...current, ...incoming }));
      if (event.detail?.mode) setStatus(event.detail.mode);
    };
    const onStatus = (event) => setStatus(event.detail?.status || 'offline');
    const onBluetooth = (event) => {
      setBluetooth(event.detail || EMPTY_BLUETOOTH);
      if (event.detail?.medium) setMediumState(event.detail.medium);
    };
    window.addEventListener('lotot:telemetry', onTelemetry);
    window.addEventListener('lotot:telemetry-status', onStatus);
    window.addEventListener('lotot:bluetooth-state', onBluetooth);
    setNativeAvailable(Boolean(window.LotoTNative));
    window.LotoTNative?.ready?.();
    return () => {
      window.removeEventListener('lotot:telemetry', onTelemetry);
      window.removeEventListener('lotot:telemetry-status', onStatus);
      window.removeEventListener('lotot:bluetooth-state', onBluetooth);
    };
  }, []);

  const state = useMemo(() => ({
    live: ['live', 'demo'].includes(status),
    label: status === 'demo' ? 'MODE DÉMO' : status === 'live' ? 'EN DIRECT' : status === 'connecting' ? 'CONNEXION' : 'HORS LIGNE',
  }), [status]);

  const connected = bluetooth.connectedDevice;
  const selectedDevice = bluetooth.selectedDevice;
  const visibleDevice = connected || selectedDevice;
  const speedAvailable = isNumericReading(readings.vehicle_speed);
  const speedProgress = speedAvailable ? clamp(readings.vehicle_speed, 0, 240) / 240 * 270 : 0;
  const numericReadings = Object.values(readings).filter(isNumericReading);
  const availableReadingCount = numericReadings.length;
  const unavailableReadingCount = Object.keys(EMPTY_READINGS).length - availableReadingCount;
  const allNumericReadingsZero = availableReadingCount > 0 && numericReadings.every((value) => Number(value) === 0);
  const diagnosticCopy = connected && unavailableReadingCount > 0
    ? {
        title: 'Flux OBD actif · données partielles',
        body: `${availableReadingCount}/5 valeurs numériques reçues. Les autres réponses sont vides ou NaN.`,
      }
    : connected && allNumericReadingsZero
      ? {
          title: 'Flux OBD actif · valeurs à zéro',
          body: 'Le simulateur répond, mais les capteurs affichés sont actuellement réglés à zéro.',
        }
      : connected
        ? {
            title: 'Diagnostic local actif',
            body: `Les données arrivent depuis ${connected.name}.`,
          }
        : status === 'demo'
          ? {
              title: 'Simulation AndrOBD active',
              body: 'Les jauges utilisent les valeurs générées par le moteur de démonstration.',
            }
          : {
              title: 'Prêt pour le diagnostic',
              body: 'Connectez un adaptateur ou lancez la simulation pour tester les jauges.',
            };

  const openConnection = () => {
    setConnectionOpen(true);
    window.LotoTNative?.scanBluetooth?.(medium);
  };
  const setMedium = (nextMedium) => {
    setMediumState(nextMedium);
    window.LotoTNative?.scanBluetooth?.(nextMedium);
  };
  const connectDevice = (device) => {
    window.LotoTNative?.connectBluetooth?.(device.address, device.medium || medium);
  };

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="brand">Loto<span>T</span></div>
        <button className={`status-pill ${state.live ? 'is-live' : ''}`} type="button" onClick={openConnection}>
          <i></i><span>{state.label}</span>
        </button>
      </header>

      <section className={`vehicle-card ${connected ? 'is-connected' : ''} ${status === 'connecting' ? 'is-connecting' : ''}`}>
        <div>
          <span className="eyebrow">{connected ? 'ADAPTATEUR CONNECTÉ' : status === 'connecting' ? 'CONNEXION EN COURS' : 'AUCUN ADAPTATEUR'}</span>
          <h1>{visibleDevice ? visibleDevice.name : 'Connectez votre boîtier OBD'}</h1>
          <p>{visibleDevice ? `${mediumLabel(visibleDevice.medium)} · ${visibleDevice.address}` : 'Recherchez votre adaptateur Bluetooth directement depuis LotoT.'}</p>
          <button className="vehicle-connect" type="button" onClick={openConnection} disabled={!nativeAvailable}>
            <Icon name="bluetooth"/><span>{connected ? 'Gérer la connexion' : 'Choisir un appareil'}</span><Icon name="chevron"/>
          </button>
        </div>
        <div className="vehicle-symbol"><Icon name={connected ? 'link' : status === 'connecting' ? 'bluetooth' : 'car'}/></div>
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
        <RingGauge label="DÉBIT D’AIR" value={readings.maf} unit="g/s" max={50} decimals={1} icon="wind" />
      </section>

      <section className={`health-card ${connected && unavailableReadingCount > 0 ? 'has-data-warning' : ''}`}>
        <span className="health-icon"><Icon name="shield"/></span>
        <div>
          <strong>{diagnosticCopy.title}</strong>
          <p>{diagnosticCopy.body}</p>
        </div>
      </section>

      <section className="actions">
        <button className="primary" type="button" onClick={openConnection} disabled={!nativeAvailable}>
          <Icon name="bluetooth"/><span>{connected ? 'Connexion' : 'Connecter'}</span>
        </button>
        <button className="secondary" type="button" onClick={() => window.LotoTNative?.startDemo?.()} disabled={!nativeAvailable}>
          <Icon name="play"/><span>Mode démo</span>
        </button>
        <button className="tools-button" type="button" onClick={() => window.LotoTNative?.openNativeTools?.()} disabled={!nativeAvailable}>
          <Icon name="tool"/><span>Diagnostics avancés AndrOBD</span><Icon name="chevron"/>
        </button>
      </section>

      <footer>Prototype GPL · Moteur AndrOBD · Interface LotoT</footer>

      <ConnectionSheet
        open={connectionOpen}
        onClose={() => setConnectionOpen(false)}
        bluetooth={bluetooth}
        medium={medium}
        setMedium={setMedium}
        onScan={(selectedMedium) => window.LotoTNative?.scanBluetooth?.(selectedMedium)}
        onConnect={connectDevice}
        onDisconnect={() => window.LotoTNative?.disconnectBluetooth?.()}
      />
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);
