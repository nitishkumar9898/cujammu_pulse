import test from 'node:test';
import assert from 'node:assert/strict';
import { actions, modules } from '../src/registry.ts';
import { decodeCertificates, decodeCertificateFinalization } from '../src/certificates/contracts.ts';
import { api } from '../src/api.ts';

test('certificates are feature-gated and shared', () => {
  assert.equal(modules.find(item => item.id === 'certificates')?.implemented, true);
  for (const id of ['certificates.mine.read', 'certificates.download', 'certificates.policy.configure', 'certificates.event.finalize']) {
    const action = actions.find(item => item.actionId === id);
    assert.equal(action?.featureKey, 'MODULE_CERTIFICATES');
    assert.deepEqual(action?.clients, ['web', 'android']);
  }
});
const item = { registrationId: 2, eventId: 7, eventTitle: 'Synthetic Event', certificateNumber: 'CUJ-synthetic-opaque', certificateType: 'POINT_PARTICIPATION', issuedAt: '2026-10-10T10:00:00Z', downloadUrl: '/api/v1/cujammu/certificates/2/download' };
test('issued certificates require typed issuance and owner download path, never infer from registration state', () => {
  const result = decodeCertificates({ items: [{ ...item, status: 'ATTENDED', qrToken: 'private' }], page: 0, hasNext: false }); assert.equal('qrToken' in result.items[0]!, false); assert.equal('status' in result.items[0]!, false);
  for (const patch of [{ downloadUrl: 'https://attacker.example/secret' }, { downloadUrl: '/student/certificates/2/download' }, { certificateType: 'DURATION_80_PERCENT' }]) assert.throws(() => decodeCertificates({ items: [{ ...item, ...patch }], page: 0, hasNext: false }));
  assert.throws(() => decodeCertificates({ items: [item, item], page: 0, hasNext: false })); assert.throws(() => decodeCertificates({ items: Array(21).fill(item), page: 0, hasNext: false }));
  assert.throws(() => decodeCertificateFinalization({ eventId: 7, revision: 1, status: 'FINALIZED', certificateType: 'POINT_PARTICIPATION', policyVersion: 1, issuedCount: 0, ineligibleCount: -1, unresolvedCount: 0, finalizedAt: '2026-10-10T10:00:00Z' }));
});
test('certificate API sends explicit policy and stable finalization request with CSRF', async () => {
  const original = globalThis.fetch; const calls: { url: string; body: any }[] = [];
  globalThis.fetch = async (input, init) => {
    const url = String(input); if (url.endsWith('/session/csrf')) return new Response(JSON.stringify({ token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' }), { headers: { 'Content-Type': 'application/json' } });
    calls.push({ url, body: init?.body ? JSON.parse(String(init.body)) : null });
    if (init?.body) assert.equal(new Headers(init.headers).get('X-CSRF-TOKEN'), 'synthetic-csrf');
    return new Response(JSON.stringify(url.includes('/own?') ? { items: [item], page: 0, hasNext: false } : { eventId: 7, revision: 1, status: 'FINALIZED_WITH_UNRESOLVED', certificateType: 'POINT_PARTICIPATION', policyVersion: 1, issuedCount: 1, ineligibleCount: 2, unresolvedCount: 3, finalizedAt: '2026-10-10T10:00:00Z' }), { headers: { 'Content-Type': 'application/json' } });
  };
  try { await api.myCertificates(); const command = { expectedOperationsRevision: 4, requestKey: '00000000-0000-4000-8000-000000000004', reason: 'Reviewed owner evidence' }; const result = await api.finalizeEventCertificates(7, command); assert.equal(result.unresolvedCount, 3); assert.deepEqual(calls[1]!.body, command); assert.equal(calls[0]!.url, '/api/v1/cujammu/certificates/own?page=0'); }
  finally { globalThis.fetch = original; }
});
