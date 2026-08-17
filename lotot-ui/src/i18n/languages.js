export const LANGUAGE_REGISTRY = Object.freeze({
  en: Object.freeze({ id: 'en', label: 'English', dir: 'ltr' }),
  fr: Object.freeze({ id: 'fr', label: 'Français', dir: 'ltr' }),
  'ar-DZ': Object.freeze({ id: 'ar-DZ', label: 'الدارجة الجزائرية', dir: 'rtl' }),
});

export const DEFAULT_LANGUAGE = 'en';

export function normalizeLanguage(value) {
  const raw = String(value || '').trim().replace(/_/g, '-');
  if (!raw) return DEFAULT_LANGUAGE;
  const parts = raw.split('-').filter(Boolean);
  const language = String(parts[0] || '').toLowerCase();
  const region = String(parts[1] || '').toUpperCase();
  if (language === 'ar' && region === 'DZ') return 'ar-DZ';
  if (language === 'fr') return 'fr';
  if (language === 'en') return 'en';
  return DEFAULT_LANGUAGE;
}

export function getLanguageDirection(value) {
  return LANGUAGE_REGISTRY[normalizeLanguage(value)]?.dir || 'ltr';
}

export function applyDocumentLanguage(value) {
  const language = normalizeLanguage(value);
  const direction = getLanguageDirection(language);
  const root = document.documentElement;
  root.lang = language;
  root.dir = direction;
  root.dataset.language = language;
  root.dataset.direction = direction;
  return language;
}
