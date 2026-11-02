/**
 * Allowlisted offline manufacturer marks from Simple Icons v16.36.0 (CC0-1.0).
 * Logos are trademarks of their respective owners. Unknown marques use a generic
 * vehicle icon; never guess a manufacturer from a model or synthetic VIN.
 */
const supportedBrands = new Set([
  'audi', 'bmw', 'chevrolet', 'citroen', 'dacia', 'fiat', 'ford',
  'honda', 'hyundai', 'kia', 'mitsubishi', 'nissan', 'opel', 'peugeot',
  'renault', 'seat', 'skoda', 'toyota', 'volkswagen',
]);

export function brandIconSlug(manufacturer) {
  const normalized = String(manufacturer || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/[^a-z0-9]/g, '');
  const aliases = { vw: 'volkswagen', chevy: 'chevrolet', citroen: 'citroen', peugeot: 'peugeot' };
  const slug = aliases[normalized] || normalized;
  return supportedBrands.has(slug) ? slug : null;
}
