import { EN } from './en.js';
import { FR } from './fr.js';
import { AR_DZ } from './ar-DZ.js';
import { applyDocumentLanguage, normalizeLanguage } from './languages.js';

const DICTIONARIES = Object.freeze({ en: EN, fr: FR, 'ar-DZ': AR_DZ });
const FALLBACKS = Object.freeze({
  en: [],
  fr: ['en'],
  'ar-DZ': ['fr', 'en'],
});

let activeLanguage = applyDocumentLanguage(
  window.LotoTNative?.getAppLanguage?.() || document.documentElement.lang || navigator.language,
);

export { normalizeLanguage } from './languages.js';
export { LANGUAGE_REGISTRY, DEFAULT_LANGUAGE, getLanguageDirection, applyDocumentLanguage } from './languages.js';

export function setLanguage(value) {
  activeLanguage = applyDocumentLanguage(value);
  return activeLanguage;
}

export function getLanguage() {
  return activeLanguage;
}

export function t(key, values = {}, fallback = '') {
  const chain = [activeLanguage, ...(FALLBACKS[activeLanguage] || ['en'])];
  let text;
  for (const locale of chain) {
    const dictionary = DICTIONARIES[locale];
    if (dictionary && dictionary[key] !== undefined) {
      text = dictionary[key];
      break;
    }
  }
  if (text === undefined) text = fallback || key;
  if (text === null || text === undefined) return '';
  return String(text).replace(/\{([A-Za-z0-9_]+)\}/g, (_, name) => (
    values[name] === null || values[name] === undefined ? '' : String(values[name])
  ));
}
