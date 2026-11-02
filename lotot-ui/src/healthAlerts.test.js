import test from 'node:test';
import assert from 'node:assert/strict';
import { collapseDuplicateMilAlert } from './healthAlerts.js';

const fault = { level: 'danger', signal: { mnemonic: 'number_fault_codes' } };
const mil = { level: 'danger', signal: { mnemonic: 'status_mil' } };
const temperature = { level: 'danger', signal: { mnemonic: 'engine_coolant_temperature' } };

test('MIL and reported DTCs are one fault summary, not two', () => {
  assert.deepEqual(collapseDuplicateMilAlert([fault, mil]), [fault]);
});
test('MIL remains visible if no DTC count was reported', () => {
  assert.deepEqual(collapseDuplicateMilAlert([mil]), [mil]);
});
test('Independent critical sensor warnings are preserved', () => {
  assert.deepEqual(collapseDuplicateMilAlert([fault, temperature, mil]), [fault, temperature]);
});
