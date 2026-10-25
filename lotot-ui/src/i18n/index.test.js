import test from 'node:test';
import assert from 'node:assert/strict';
globalThis.document = { documentElement: { lang: 'en', dataset: {} } };
globalThis.window = {};
const { t, setLanguage, getLanguageDirection } = await import('./index.js');
test('language switching changes text and direction without reloading', () => {
  setLanguage('fr-CA');
  assert.equal(t('appearance.language'), 'Langue');
  assert.equal(document.documentElement.dir, 'ltr');
  setLanguage('ar_DZ');
  assert.equal(document.documentElement.lang, 'ar-DZ');
  assert.equal(document.documentElement.dir, 'rtl');
  assert.equal(t('faults.definition_en'), 'التعريف بالإنجليزية');
  setLanguage('en');
  assert.equal(document.documentElement.dir, 'ltr');
});
test('missing sensor translations honor empty and native-label fallbacks', () => {
  for (const language of ['en', 'fr', 'ar-DZ']) {
    setLanguage(language);
    assert.equal(t('signal.unknown_pid', {}, ''), '');
    assert.equal(t('signal.unknown_pid', {}, 'Native sensor label'), 'Native sensor label');
    assert.equal(t('signal.unknown_pid', {}, '') || 'Native sensor label', 'Native sensor label');
    assert.equal(t('signal.unknown_pid'), 'signal.unknown_pid');
  }
});
test('diagnostic code and reading placeholders survive translation', () => {
  setLanguage('fr');
  assert.equal(t('temperature.normal_range', {min:70, max:105}), 'Normal 70–105 °C');
  setLanguage('ar-DZ');
  assert.ok(t('temperature.normal_range', {min:70, max:105}).includes('70'));
  assert.equal(t('faults.type_P'), 'Moteur و transmission');
  assert.equal(getLanguageDirection('ar-DZ'), 'rtl');
});
