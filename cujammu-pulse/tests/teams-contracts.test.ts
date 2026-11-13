import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeEligibleEvents, decodeTeam, decodeTeams, decodeTeamHistory } from '../src/teams/contracts.ts';
import { actions, canOpenModule } from '../src/registry.ts';

const team = { id: 1, revision: 0, name: 'Synthetic Team', minSize: 2, maxSize: 5, memberCount: 1, locked: false, canManage: true, inviteCode: 'CUJ-ABCD23', createdAt: '2026-10-03T10:00:00' };
test('team decoder keeps invite code only from the server-projected leader view', () => {
  assert.deepEqual(decodeTeam({ ...team, leader: { email: 'private@example.invalid' } }), team);
  assert.equal(decodeTeam({ ...team, canManage: false, inviteCode: null }).inviteCode, null);
  assert.throws(() => decodeTeam({ ...team, inviteCode: 'CUJ-INVALID' }));
  assert.throws(() => decodeTeams({ items: Array(101).fill(team) }));
  assert.deepEqual(decodeEligibleEvents({ items: [{ id: 2, title: 'Synthetic event', eventDate: '2026-10-04T10:00:00' }] }), [{ id: 2, title: 'Synthetic event', eventDate: '2026-10-04T10:00:00' }]);
});

test('team history keeps closed events without permitting roster changes and validates pagination', () => {
  const event = { id: 2, title: 'Closed event', eventDate: '2025-01-01T10:00:00' };
  const item = { event, team: { ...team, locked: true }, canChangeRoster: false };
  assert.deepEqual(decodeTeamHistory({ items: [item], page: 1, hasMore: false }), { items: [item], page: 1, hasMore: false });
  assert.throws(() => decodeTeamHistory({ items: [{ ...item, canChangeRoster: true }], page: 0, hasMore: false }));
  assert.throws(() => decodeTeamHistory({ items: [{ ...item, team: { ...team, canManage: false, inviteCode: null }, canChangeRoster: true }], page: 0, hasMore: false }));
  assert.throws(() => decodeTeamHistory({ items: Array(21).fill(item), page: 0, hasMore: true }));
  assert.throws(() => decodeTeamHistory({ items: [], page: -1, hasMore: false }));
});
test('team workflow actions are shared and feature-gated', () => {
  assert.equal(canOpenModule('teams', { MODULE_TEAMS: true }), true);
  for (const id of ['teams.eligible-events.read', 'teams.create', 'teams.join', 'teams.lock']) {
    const action = actions.find(action => action.actionId === id);
    assert.equal(action?.featureKey, 'MODULE_TEAMS'); assert.deepEqual(action?.clients, ['web', 'android']);
  }
});
