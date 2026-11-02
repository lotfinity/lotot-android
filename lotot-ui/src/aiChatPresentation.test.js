import test from 'node:test';
import assert from 'node:assert/strict';
import { vehicleSignals, aiQuickActions } from './aiChatPresentation.js';
test('Disconnected assistant never claims phone accelerometer samples are vehicle readings', () => {
  assert.equal(vehicleSignals([{ source: 'obd', mnemonic: 'engine_speed' }], false).length, 0);
  assert.deepEqual(vehicleSignals([{ source: 'obd', mnemonic: 'engine_speed' }, { source: 'motion', mnemonic: 'ACC_X' }], true).map(s => s.mnemonic), ['engine_speed']);
});
test('No car means general automotive prompts, no invented P0301/P0420 session faults', () => {
  assert.deepEqual(aiQuickActions({session:'offline',dtcs:[{code:'P0301'}]}).map(x=>x.id), ['fault-explain','check-engine','obd-basics']);
});
test('Real vehicle context limits quick DTC buttons to actual scanned faults', () => {
  assert.deepEqual(aiQuickActions({session:'live',vehicleSignalCount:8,dtcs:[{code:'P0403'}, {code:'P2015'}, {code:'P0403'}]}).map(x=>x.id), ['analyze','summary','dtc-P0403','dtc-P2015']);
});
test('Connected session without ECU readings does not advertise live analysis', () => {
  assert.deepEqual(aiQuickActions({session:'demo',vehicleSignalCount:0}).map(x=>x.id), ['waiting']);
});
