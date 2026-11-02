/** The health summary shows sensor threshold alerts, separately from ECU DTCs. */
export function healthAlertCounts(alerts = []) {
  return alerts.reduce((counts, alert) => {
    if (alert?.level === 'danger') counts.critical += 1;
    else if (alert?.level === 'warning') counts.warnings += 1;
    return counts;
  }, {critical: 0, warnings: 0});
}

/** Never put potentially personal VIN/session identifiers into external searches. */
export function googleFaultSearchUrl(code, vehicle = {}) {
  const normalized = String(code || '').trim().toUpperCase();
  if (!/^[PBCU][0-9A-F]{4}$/.test(normalized)) return null;
  const clean = (value) => String(value || '').replace(/[^\p{L}\p{N} .-]/gu, ' ').trim().slice(0, 45);
  const terms = [normalized, clean(vehicle.manufacturer), clean(vehicle.model), 'OBD2 fault code'].filter(Boolean);
  return 'https://www.google.com/search?q=' + encodeURIComponent(terms.join(' '));
}
