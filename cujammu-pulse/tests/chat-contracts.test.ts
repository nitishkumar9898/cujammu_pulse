import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeGroups, decodeGroupPage, decodeMessages } from '../src/chat/contracts.ts';
import { actions } from '../src/registry.ts';

test('chat decoders exclude memberships, sender accounts and media URLs', () => {
  assert.deepEqual(decodeGroups({ items: [{ id: 1, name: 'CSIT students', type: 'PERMANENT_DEPARTMENT', nightSilenceEnabled: true, canPost: false, nightSilenceActive: true, members: [{ email: 'private@example.invalid' }] }] }), [{ id: 1, name: 'CSIT students', type: 'PERMANENT_DEPARTMENT', nightSilenceEnabled: true, canPost: false, nightSilenceActive: true }]);
  assert.deepEqual(decodeMessages({ items: [{ id: 2, senderName: 'Synthetic Student', content: 'Hello', createdAt: '2026-10-03T10:00:00', mediaUrl: '/uploads/private' }] }), [{ id: 2, senderName: 'Synthetic Student', content: 'Hello', createdAt: '2026-10-03T10:00:00' }]);
  assert.throws(() => decodeMessages({ items: [{ id: 2, senderName: 'Student', content: '', createdAt: '2026-10-03T10:00:00' }] }));
});

test('chat actions are gated for both shared clients', () => {
  for (const id of ['chat.groups.read', 'chat.message.post']) {
    const action = actions.find(action => action.actionId === id);
    assert.equal(action?.featureKey, 'MODULE_COMMUNITY_CHAT');
    assert.deepEqual(action?.clients, ['web', 'android']);
  }
});

test('group pages enforce bounds, unique group IDs and server paging metadata', () => {
  const group = { id: 1, name: 'Verified department', type: 'PERMANENT_DEPARTMENT', nightSilenceEnabled: true, canPost: true, nightSilenceActive: false };
  assert.deepEqual(decodeGroupPage({ items: [group], page: 2, hasMore: true }), { items: [group], page: 2, hasMore: true });
  assert.deepEqual(decodeGroupPage({ items: [], page: 3, hasMore: false }), { items: [], page: 3, hasMore: false });
  for (const patch of [{ page: -1 }, { page: '0' }, { page: 0.5 }, { page: Number.MAX_SAFE_INTEGER + 1 }, { hasMore: 'true' }, { items: [group, group] }, { items: Array.from({ length: 21 }, (_, i) => ({ ...group, id: i + 1 })) }]) {
    assert.throws(() => decodeGroupPage({ items: [group], page: 0, hasMore: false, ...patch }));
  }
});
