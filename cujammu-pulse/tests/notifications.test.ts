import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeNotifications } from '../src/notifications/contracts.ts';
import { actions } from '../src/registry.ts';

test('notification inbox cursor pages are bounded, allowlisted and validate acknowledgement state', () => {
  assert.deepEqual(decodeNotifications({ nextBeforeId: 2, items: [{ id: 2, message: 'Schedule changed', read: true, acknowledged: false,
    createdAt: '2026-10-02T12:00:00', user: { email: 'private@example.invalid' }, actionUrl: 'https://attacker.invalid' }] }),
  { nextBeforeId: 2, items: [{ id: 2, message: 'Schedule changed', read: true, acknowledged: false, createdAt: '2026-10-02T12:00:00' }] });
  assert.throws(() => decodeNotifications({ nextBeforeId: null, items: [{ id: 2, message: 'Notice', read: true, createdAt: null }] }));
  assert.throws(() => decodeNotifications({ nextBeforeId: 0, items: [] }));
  assert.throws(() => decodeNotifications({ nextBeforeId: null, items: Array(21).fill({}) }));
});

test('acknowledgement action is registered for both shared clients under notification capability', () => {
  assert.ok(actions.some(action => action.actionId === 'notifications.inbox.acknowledge'
    && action.operationId === 'POST /api/v1/cujammu/notifications/{id}/acknowledge'
    && action.featureKey === 'MODULE_NOTIFICATIONS'
    && action.clients.includes('web') && action.clients.includes('android')));
});

test('cursor inbox pagination is registered for both shared clients under notification capability', () => {
  assert.ok(actions.some(action => action.actionId === 'notifications.inbox.page.read'
    && action.operationId === 'GET /api/v1/cujammu/notifications?beforeId={cursor}'
    && action.featureKey === 'MODULE_NOTIFICATIONS'
    && action.clients.includes('web') && action.clients.includes('android')));
});
