import test from 'node:test'; import assert from 'node:assert/strict';
import { decodeRecoverableVisits, decodeSafeExit } from '../src/library/recovery-contracts.ts';
import { actions } from '../src/registry.ts'; import { api } from '../src/api.ts';
const visit = { visitId: 2, revision: 1, purpose: 'STUDY', entryTime: '2026-10-10T10:00:00Z' };
const exit = { visitId: 2, revision: 2, status: 'SAFE_EXIT_UNVERIFIED', durationQuality: 'UNVERIFIED', exitTime: '2026-10-10T11:00:00Z' };
test('protected recovery only exposes bounded own visit fields and preserves unverified quality', () => {
  assert.deepEqual(decodeRecoverableVisits([{ ...visit, studentName: 'private', qrToken: 'secret' }]), [visit]);
  assert.throws(() => decodeRecoverableVisits(Array(21).fill(visit))); assert.throws(() => decodeRecoverableVisits([visit, visit]));
  assert.throws(() => decodeSafeExit({ ...exit, durationQuality: 'VERIFIED' })); assert.throws(() => decodeSafeExit({ ...exit, status: 'COMPLETED' }));
  assert.deepEqual(decodeSafeExit({ ...exit, durationSeconds: 3600 }), exit);
  const action = actions.find(item => item.actionId === 'library.recovery.safe-exit')!; assert.equal('featureKey' in action, false);
});
test('safe exit uses protected endpoint, scoped revision/reason/request key and CSRF', async () => {
  const original = globalThis.fetch; const calls: { url: string; body: any }[] = [];
  globalThis.fetch = async (input, init) => { const url = String(input); if (url.endsWith('/session/csrf')) return new Response(JSON.stringify({ token: 'synthetic', headerName: 'X-CSRF-TOKEN' }), { headers: { 'Content-Type': 'application/json' } }); calls.push({ url, body: init?.body ? JSON.parse(String(init.body)) : null }); if (init?.body) assert.equal(new Headers(init.headers).get('X-CSRF-TOKEN'), 'synthetic'); return new Response(JSON.stringify(url.endsWith('/active') ? [visit] : exit), { headers: { 'Content-Type': 'application/json' } }); };
  try { await api.recoverableLibraryVisits(); const command = { expectedRevision: 1, requestKey: '00000000-0000-4000-8000-000000000001', reasonCode: 'SERVICE_DISABLED' as const }; await api.safeExitLibraryVisit(2, command); assert.deepEqual(calls[1]!.body, command); assert.equal(calls[0]!.url, '/api/v1/cujammu/recovery/library/active'); }
  finally { globalThis.fetch = original; }
});
