import test from 'node:test';
import assert from 'node:assert/strict';
import { healthAlertCounts, googleFaultSearchUrl } from './healthScreen.js';

test('alert counts do not confuse number of reported DTCs with warning thresholds', () => {
  assert.deepEqual(healthAlertCounts([{level:'warning'},{level:'danger'},{level:'warning'}]), {critical:1,warnings:2});
  assert.deepEqual(healthAlertCounts([]),{critical:0,warnings:0});
});
test('Google search includes valid DTC and vehicle make, excludes VIN', () => {
  const url = googleFaultSearchUrl('p2562',{manufacturer:'Citroën',model:'Berlingo',vin:'WVWZZZAUZHL0T0T01'});
  assert.equal(new URL(url).hostname,'www.google.com');
  const query = new URL(url).searchParams.get('q');
  assert.match(query,/P2562 Citroën Berlingo/);
  assert.doesNotMatch(query,/WVWZZZ/);
});
test('Rejects unrelated or malicious search codes', () => {
  assert.equal(googleFaultSearchUrl('P2562?search=elsewhere'),null);
  assert.equal(googleFaultSearchUrl(''),null);
});
