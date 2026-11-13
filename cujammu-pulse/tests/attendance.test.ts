import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeAttendanceCredential, decodeAttendanceReceipt, decodePendingAttendance } from '../src/attendance/contracts.ts';
import { actions } from '../src/registry.ts';

test('attendance decoders keep credentials bounded and action-specific', () => {
  const value = decodeAttendanceCredential({ action: 'ENTRY', credential: 'opaque-token', expiresAt: '2026-10-04T10:00:00Z', credentialVersion: 1, registration: { email: 'secret' } });
  assert.equal(value.credential, 'opaque-token');
  assert.throws(() => decodeAttendanceCredential({ action: 'BAD', credential: 'x', expiresAt: 'x', credentialVersion: 1 }));
  assert.throws(() => decodeAttendanceCredential({ action: 'ENTRY', credential: 'x'.repeat(513), expiresAt: 'x', credentialVersion: 1 }));
  for (const expiresAt of ['not-a-date', '2026-02-30T10:00:00Z', '2026-10-04T10:00:00+00:00']) assert.throws(() => decodeAttendanceCredential({ action: 'ENTRY', credential: 'x', expiresAt, credentialVersion: 1 }));
});

test('pending intents and receipts reject malformed or unbounded data', () => {
  assert.equal(decodePendingAttendance({ items: [{ intentKey: 'intent-1', eventTitle: 'Hackathon', action: 'ENTRY', expiresAt: '2026-10-04T10:00:00Z', status: 'PENDING' }] })[0]?.status, 'PENDING');
  assert.throws(() => decodePendingAttendance({ items: Array(101).fill({ intentKey: 'i', eventTitle: 'e', action: 'ENTRY', expiresAt: 'x', status: 'PENDING' }) }));
  assert.equal(decodeAttendanceReceipt({ evidenceId: 4, action: 'ENTRY', status: 'RECORDED', occurredAt: '2026-10-04T10:00:00Z' }).evidenceId, 4);
  assert.throws(() => decodeAttendanceReceipt({ evidenceId: 0, action: 'ENTRY', status: 'RECORDED', occurredAt: 'x' }));
});

test('attendance actions are shared and independently feature-gated', () => {
  for (const id of ['attendance.credential.issue', 'attendance.credential.hide', 'events.catalog.refresh', 'attendance.scan-intent.create', 'attendance.scan-intent.pending.read', 'attendance.scan-intent.confirm', 'events.operator.scanner.control', 'events.operator.scanner.grant', 'events.operator.scanner.revoke']) {
    const action = actions.find(item => item.actionId === id);
    assert.deepEqual(action?.clients, ['web', 'android']);
    assert.equal(action?.featureKey, 'MODULE_EVENTS');
  }
  for (const id of ['attendance.report.csv.download', 'attendance.report.xlsx.download', 'attendance.report.scan-log.download']) {
    const action = actions.find(item => item.actionId === id);
    assert.deepEqual(action?.clients, ['web', 'android']);
    assert.equal(action?.featureKey, 'MODULE_EVENTS');
    assert.equal(action?.status, 'implemented');
  }
  for (const id of ['attendance.report.access.grant', 'attendance.report.access.revoke']) {
    const action = actions.find(item => item.actionId === id);
    assert.deepEqual(action?.clients, ['web']);
    assert.equal(action?.featureKey, 'MODULE_EVENTS');
    assert.equal(action?.status, 'web-session-bridge');
  }
  assert.equal(actions.find(item => item.actionId === 'attendance.report.access.grant')?.operationId, 'PUT /events/{eventId}/attendance-export-grants/{userId}');
});

test('attendance report URLs remain same-origin and format allowlisted', async () => {
  const { api } = await import('../src/api.ts');
  assert.equal(api.attendanceReportUrl(12, 'csv'), '/api/v1/cujammu/events/12/attendance/report.csv');
  assert.equal(api.attendanceReportUrl(12, 'xlsx'), '/api/v1/cujammu/events/12/attendance/report.xlsx');
  assert.equal(api.attendanceScanLogUrl(12), '/api/v1/cujammu/events/12/attendance/scan-log.csv');
  assert.throws(() => api.attendanceReportUrl(0, 'csv'));
  assert.throws(() => api.attendanceScanLogUrl(Number.MAX_SAFE_INTEGER + 1));
});
