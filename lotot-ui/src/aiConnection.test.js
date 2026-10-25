import test from 'node:test';
import assert from 'node:assert/strict';
import { aiConnectionState } from './aiConnection.js';
const now = 1000000;
const available = { configured: true, availability: 'available', connected: true, available: true, checked_at: now };
test('configuration alone never means connected', () => {
  assert.equal(aiConnectionState({ configured: true }, now), 'unknown');
  assert.equal(aiConnectionState({}, now), 'unconfigured');
});
test('only a recent successful server check is connected', () => {
  assert.equal(aiConnectionState(available, now), 'available');
  assert.equal(aiConnectionState(available, now + 90000), 'unknown');
  assert.equal(aiConnectionState(available, now - 1), 'unknown');
});
test('checking, unreachable and failed inference stay distinct', () => {
  assert.equal(aiConnectionState({ ...available, availability: 'checking' }, now), 'checking');
  assert.equal(aiConnectionState({ ...available, available: false, availability: 'unavailable' }, now), 'unavailable');
  assert.equal(aiConnectionState({ ...available, connected: false, available: false, availability: 'offline' }, now), 'offline');
});
