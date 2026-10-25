// Only a recent successful server check may turn the LoToTi indicator green.
export function aiConnectionState(state = {}, now = Date.now()) {
  if (!state.configured) return 'unconfigured';
  if (state.availability === 'checking') return 'checking';
  const age = now - Number(state.checked_at || 0);
  if (!state.checked_at || age < 0 || age >= 90000) return 'unknown';
  if (state.available && state.connected && state.availability === 'available') return 'available';
  return state.connected ? 'unavailable' : 'offline';
}
