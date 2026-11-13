import test from 'node:test';
import assert from 'node:assert/strict';
import { decodePass, decodePasses, decodePendingPasses } from '../src/hostels/contracts.ts';
import { actions } from '../src/registry.ts';
const pass = { id: 7, hostel: 'Synthetic Hostel', type: 'DAY_OUT', status: 'PENDING', destination: 'Jammu Market', reason: 'Books', expectedExit: '2030-01-01T10:00:00', expectedReturn: '2030-01-01T18:00:00', actualExit: null, actualReturn: null, qrToken: 'private-secret' };
test('own gate pass decoder excludes QR bearer secret', () => { const decoded = decodePass(pass); assert.equal('qrToken' in decoded, false); assert.deepEqual(decodePasses([pass]), [decoded]); });
test('hostel request action is feature-gated and shared by web and Android', () => { assert.ok(actions.some(action => action.actionId === 'hostel.pass.apply' && action.featureKey === 'MODULE_HOSTEL_GATEPASS' && action.clients.includes('web') && action.clients.includes('android'))); });
test('warden queue admits applicant identity only in scoped approval response', () => { assert.equal(decodePendingPasses([{ id: 8, hostel: 'Synthetic Hostel', type: 'NIGHT_OUT', destination: 'Jammu', reason: 'Family', expectedExit: '2030-01-01T10:00:00', expectedReturn: '2030-01-02T10:00:00', studentName: 'Synthetic Student', enrollmentNo: 'ENR001' }])[0]?.enrollmentNo, 'ENR001'); for (const actionId of ['hostel.pass.approve', 'hostel.pass.reject']) assert.ok(actions.some(action => action.actionId === actionId && action.clients.includes('web') && action.clients.includes('android'))); });
