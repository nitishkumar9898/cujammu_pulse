import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeFoundClaim, decodeFoundClaims, decodeLostFoundPage } from '../src/lost-found/contracts.ts';
import { actions, modules } from '../src/registry.ts';
import { api } from '../src/api.ts';
const item = { id: 1, version: 0, title: 'Wallet', category: 'BAGS_WALLETS', description: 'Found near library', locationFound: 'Library', status: 'REPORTED', reportedAt: '2026-10-04T10:00:00Z', canReview: false };
const claim = { id: 2, itemId: 1, status: 'PENDING', evidence: 'Synthetic private identifying mark', submittedAt: '2026-10-04T10:00:00Z' };

test('lost and found page excludes identities and URLs and bounds paging and capabilities', () => {
  const value = decodeLostFoundPage({ items: [{ ...item, reportedBy: { email: 'secret' }, claimantContact: 'private', imageUrl: '/secret' }], page: 0, hasNext: false }).items[0]!;
  assert.equal('reportedBy' in value, false); assert.equal('imageUrl' in value, false);
  for (const bad of [{ ...item, title: 'x'.repeat(121) }, { ...item, canReview: 'true' }, { ...item, version: -1 }]) assert.throws(() => decodeLostFoundPage({ items: [bad], page: 0, hasNext: false }));
  assert.throws(() => decodeLostFoundPage({ items: Array(21).fill(item), page: 0, hasNext: false }));
  assert.throws(() => decodeLostFoundPage({ items: [item, item], page: 0, hasNext: false }));
  assert.throws(() => decodeLostFoundPage({ items: [], page: 0, hasNext: 'true' }));
});
test('private claim decoder strips principal/contact fields and rejects oversize or duplicate pages', () => {
  assert.deepEqual(decodeFoundClaim({ ...claim, claimantName: 'Secret name', claimant: { id: 100 } }), claim);
  assert.throws(() => decodeFoundClaims([{ ...claim, evidence: 'x'.repeat(2001) }]));
  assert.throws(() => decodeFoundClaims([claim, claim]));
});
test('lost and found actions are shared and feature-gated', () => {
  for (const id of ['lost-found.items.read', 'lost-found.item.report', 'lost-found.item.claim', 'lost-found.claims.read', 'lost-found.item.handover']) { const action = actions.find(item => item.actionId === id); assert.deepEqual(action?.clients, ['web', 'android']); assert.equal(action?.featureKey, 'MODULE_LOST_FOUND'); }
  assert.equal(modules.find(item => item.id === 'lostfound')?.implemented, true);
});
test('found-item transport preserves request keys/revisions, scoped paging and CSRF without identity assertions', async () => {
  const original = globalThis.fetch; const calls: { url: string; body: unknown; headers: Headers }[] = [];
  globalThis.fetch = async (input, init) => {
    const url = String(input); if (url.endsWith('/session/csrf')) return new Response(JSON.stringify({ token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' }), { headers: { 'Content-Type': 'application/json' } });
    calls.push({ url, body: init?.body ? JSON.parse(String(init.body)) : null, headers: new Headers(init?.headers) });
    const response = url.endsWith('/claim') ? claim : url.includes('/claims?') ? [claim] : url.includes('?page=') ? { items: [item], page: 2, hasNext: false } : item;
    return new Response(JSON.stringify(response), { headers: { 'Content-Type': 'application/json' } });
  };
  try {
    const report = { requestKey: '00000000-0000-4000-8000-000000000001', title: 'Wallet', category: 'BAGS_WALLETS' as const, description: 'Synthetic item', locationFound: 'Library' };
    const command = { requestKey: '00000000-0000-4000-8000-000000000002', expectedVersion: 0, evidence: claim.evidence };
    await api.lostFound('BAGS_WALLETS', 2); await api.reportFoundItem(report); await api.claimLostFound(1, command); await api.lostFoundClaims(1, 0);
    await api.handoverLostFound(1, { requestKey: '00000000-0000-4000-8000-000000000003', expectedVersion: 0, claimId: 2, verificationReason: 'Verified identifying mark' });
    assert.equal(calls[0]!.url, '/api/v1/cujammu/lost-and-found?page=2&category=BAGS_WALLETS');
    assert.deepEqual(calls[1]!.body, report); assert.deepEqual(calls[2]!.body, command);
    for (const call of calls.filter(value => value.body != null)) assert.equal(call.headers.get('X-CSRF-TOKEN'), 'synthetic-csrf');
    assert.equal(JSON.stringify(calls).includes('claimantName'), false);
  } finally { globalThis.fetch = original; }
});
