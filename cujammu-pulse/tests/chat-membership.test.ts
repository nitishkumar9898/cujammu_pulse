import test from 'node:test';
import assert from 'node:assert/strict';
import { api, ApiError } from '../src/api.ts';
import { decodeDepartmentChatGroups, decodeChatMembership, decodeChatMembershipPage, parseMembershipUserId } from '../src/chat/membership-contracts.ts';

const member = { userId: 42, name: 'Synthetic Member', active: true, revision: 3 };
test('membership decoders project operational fields and omit personal verification evidence', () => {
  assert.deepEqual(decodeChatMembership({ ...member, email: 'private@example.invalid', sourceReference: 'internal-ref', reason: 'restricted', user: { password: 'never expose' } }), member);
  assert.deepEqual(decodeDepartmentChatGroups({ items: [{ id: 7, name: 'Computing', department: 'CSIT', members: [member] }] }), [{ id: 7, name: 'Computing', department: 'CSIT' }]);
});
test('membership paging rejects duplicate subjects, oversized pages and invalid versions', () => {
  assert.deepEqual(decodeChatMembershipPage({ items: [member], page: 0, hasMore: false }), { items: [member], page: 0, hasMore: false });
  for (const items of [[member, member], Array.from({ length: 51 }, (_, i) => ({ ...member, userId: i + 1 }))]) {
    assert.throws(() => decodeChatMembershipPage({ items, page: 0, hasMore: false }));
  }
  for (const revision of [-1, 1.5, '3', Number.MAX_SAFE_INTEGER + 1, null]) assert.throws(() => decodeChatMembership({ ...member, revision }));
  for (const patch of [{ page: -1 }, { page: '0' }, { hasMore: 'false' }, { items: null }]) assert.throws(() => decodeChatMembershipPage({ items: [member], page: 0, hasMore: false, ...patch }));
  assert.throws(() => decodeChatMembership({ ...member, active: 'true' }));
  assert.throws(() => decodeChatMembership({ ...member, name: ' '.repeat(10) }));
});
test('department directory rejects duplicate groups and excessive or malformed scope', () => {
  const group = { id: 7, name: 'Computing', department: 'CSIT' };
  assert.throws(() => decodeDepartmentChatGroups({ items: [group, group] }));
  assert.throws(() => decodeDepartmentChatGroups({ items: Array.from({ length: 101 }, (_, i) => ({ ...group, id: i + 1 })) }));
  assert.throws(() => decodeDepartmentChatGroups({ items: [{ ...group, department: '' }] }));
  assert.throws(() => decodeDepartmentChatGroups({ items: [{ ...group, id: 0 }] }));
});
test('subject entry rejects ambiguous, rounded and nonnumeric IDs', () => {
  assert.equal(parseMembershipUserId(' 42 '), 42);
  assert.equal(parseMembershipUserId('9007199254740991'), Number.MAX_SAFE_INTEGER);
  for (const input of ['', '0', '-1', '1.5', '1e3', '42abc', '0042', '9007199254740992']) assert.equal(parseMembershipUserId(input), null);
});
test('membership transport uses CSRF and preserves expected revision and explicit revoke intent', async () => {
  const original = globalThis.fetch;
  const calls: { path: string; method?: string; body?: string }[] = [];
  globalThis.fetch = async (input, init) => {
    const path = String(input); calls.push({ path, method: init?.method, body: init?.body as string | undefined });
    assert.equal(init?.credentials, 'same-origin'); assert.equal(init?.cache, 'no-store');
    if (path === '/api/v1/session/csrf') return Response.json({ token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' });
    assert.equal(path, '/api/v1/cujammu/chat/admin/groups/7/members/42');
    assert.equal((init?.headers as Record<string, string>)['X-CSRF-TOKEN'], 'synthetic-csrf');
    return Response.json({ ...member, active: false, revision: 4 });
  };
  try {
    const command = { expectedRevision: 3, active: false, sourceReference: '', reason: 'Approved membership removal' };
    assert.deepEqual(await api.setChatMembership(7, 42, command), { ...member, active: false, revision: 4 });
    assert.equal(calls.length, 2); assert.equal(calls[1]?.method, 'PUT'); assert.deepEqual(JSON.parse(calls[1]!.body!), command);
  } finally { globalThis.fetch = original; }
});
test('membership conflict is surfaced without silently retrying the privilege change', async () => {
  const original = globalThis.fetch; let mutations = 0;
  globalThis.fetch = async input => {
    if (String(input) === '/api/v1/session/csrf') return Response.json({ token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' });
    mutations++; return new Response(null, { status: 409 });
  };
  try {
    await assert.rejects(api.setChatMembership(7, 42, { expectedRevision: null, active: true, sourceReference: 'APPROVED-42', reason: 'Verified membership' }), (e: unknown) => e instanceof ApiError && e.status === 409);
    assert.equal(mutations, 1);
  } finally { globalThis.fetch = original; }
});
