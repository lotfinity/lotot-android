/** Only vehicle ECU observations count as vehicle context, not phone sensors. */
export function vehicleSignals(signals = [], sessionActive = false) {
  if (!sessionActive) return [];
  return signals.filter((signal) => signal?.source === 'obd' && signal?.mnemonic && !String(signal.mnemonic).startsWith('ACC_'));
}

/** Quick actions must be grounded in what this session can actually know. */
export function aiQuickActions({ session = 'offline', vehicleSignalCount = 0, dtcs = [] } = {}) {
  if (session === 'offline') return [
    { id: 'fault-explain', labelKey: 'ai.quick.explain_code', questionKey: 'ai.quick.question_code' },
    { id: 'check-engine', labelKey: 'ai.quick.check_engine', questionKey: 'ai.quick.question_check_engine' },
    { id: 'obd-basics', labelKey: 'ai.quick.obd_basics', questionKey: 'ai.quick.question_obd_basics' },
  ];
  const codeActions = [...new Set(dtcs.map((dtc) => typeof dtc === 'string' ? dtc : dtc?.code)
    .filter((code) => /^[PCBU][0-9A-F]{4}$/i.test(String(code || '')))
    .map((code) => String(code).toUpperCase()))].slice(0, 2)
    .map((code) => ({ id: `dtc-${code}`, code, label: code, questionKey: 'ai.quick.question_session_code' }));
  return [
    ...(vehicleSignalCount > 0 ? [
      { id: 'analyze', labelKey: 'ai.analyze_live', questionKey: 'ai.prompt.analyze_live' },
      { id: 'summary', labelKey: 'ai.health_summary', questionKey: 'ai.prompt.health_summary' },
    ] : [{ id: 'waiting', labelKey: 'ai.quick.waiting_data', questionKey: 'ai.quick.question_waiting_data' }]),
    ...codeActions,
  ];
}
