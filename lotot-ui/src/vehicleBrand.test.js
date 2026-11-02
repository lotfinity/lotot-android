import test from 'node:test';
import assert from 'node:assert/strict';
import { brandIconSlug } from './vehicleBrand.js';

test('Brand icons resolve for every known DieselOBD replay make', () => {
  for (const [input, expected] of Object.entries({Peugeot:'peugeot', 'Citroën':'citroen',CHEVROLET:'chevrolet',Kia:'kia'}))
    assert.equal(brandIconSlug(input), expected);
});
test('Accent normalization and aliases work while unknown makes remain generic', () => {
  assert.equal(brandIconSlug('VW'), 'volkswagen');
  assert.equal(brandIconSlug('Citroen'), 'citroen');
  assert.equal(brandIconSlug('SsangYong'), null);
  assert.equal(brandIconSlug('Google'), null);
  assert.equal(brandIconSlug(''), null);
  assert.equal(brandIconSlug('Peugeot Expert'), null);
});
