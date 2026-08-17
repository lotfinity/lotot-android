import { EN } from '../src/i18n/en.js';
import { FR } from '../src/i18n/fr.js';
import { AR_DZ } from '../src/i18n/ar-DZ.js';

const locales = { fr: FR, 'ar-DZ': AR_DZ };
const sourceKeys = Object.keys(EN).sort();
const placeholders = (value) => [...String(value).matchAll(/\{([A-Za-z0-9_]+)\}/g)].map((match) => match[1]).sort();
let failed = false;
for (const [locale, dictionary] of Object.entries(locales)) {
  const keys = Object.keys(dictionary).sort();
  const missing = sourceKeys.filter((key) => !(key in dictionary));
  const extra = keys.filter((key) => !(key in EN));
  const mismatch = sourceKeys.filter((key) => JSON.stringify(placeholders(EN[key])) !== JSON.stringify(placeholders(dictionary[key])));
  console.log(`${locale}: ${keys.length}/${sourceKeys.length} keys · missing=${missing.length} · extra=${extra.length} · placeholderMismatch=${mismatch.length}`);
  if (missing.length || extra.length || mismatch.length) {
    failed = true;
    if (missing.length) console.error('  missing:', missing.join(', '));
    if (extra.length) console.error('  extra:', extra.join(', '));
    if (mismatch.length) console.error('  placeholder mismatch:', mismatch.join(', '));
  }
}
if (failed) process.exit(1);
