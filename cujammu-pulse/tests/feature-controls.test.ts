import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeFeatureControls } from '../src/admin/feature-controls.ts';
import { actions } from '../src/registry.ts';
import { decodeMediaStorageInventory } from '../src/admin/media-storage.ts';
import { decodeLibrarySession } from '../src/library/contracts.ts';
import { decodeLibraryHistory } from '../src/library/contracts.ts';

const control = { key: 'MODULE_EVENTS', name: 'Events', description: 'Campus events', category: 'EVENTS', requestedEnabled: true, effectiveEnabled: true, locked: false, revision: 3, updatedAt: '2026-10-03T12:00:00', updatedBy: 'admin' };

test('feature controls use a bounded allowlisted admin contract', () => {
  assert.deepEqual(decodeFeatureControls({ items: [{ ...control, internalTargeting: { students: true } }] }), [control]);
  assert.throws(() => decodeFeatureControls({ items: [{ ...control, requestedEnabled: 'true' }] }));
  assert.throws(() => decodeFeatureControls({ items: Array(201).fill(control) }));
});

test('control-plane actions are present in the shared web and Android registry', () => {
  for (const actionId of ['admin.feature-controls.read', 'admin.feature-controls.change', 'admin.feature-controls.audit.read']) {
    const action = actions.find(item => item.actionId === actionId);
    assert.deepEqual(action?.clients, ['web', 'android']);
    assert.equal(action?.status, 'implemented');
  }
});

test('feature audit decoder strips unbounded or malformed fields', async () => {
  const { decodeFeatureControlAudit } = await import('../src/admin/feature-controls.ts');
  const result = decodeFeatureControlAudit({ page: 0, hasNext: false, items: [{ id: 2, role: 'ADMIN', featureKey: 'MODULE_EVENTS', enabled: false, reason: 'reviewed', timestamp: '2026-10-04T10:00:00' }] });
  assert.equal(result.items[0]?.featureKey, 'MODULE_EVENTS');
  assert.throws(() => decodeFeatureControlAudit({ page: 0, hasNext: false, items: [{ id: 0, role: 'ADMIN', featureKey: 'MODULE_EVENTS', enabled: false, reason: '', timestamp: null }] }));
});

test('event lifecycle actions use independent runtime controls', () => {
  assert.equal(actions.find(item => item.actionId === 'events.lifecycle.cancel')?.featureKey, 'ACTION_EVENT_CANCEL');
  assert.equal(actions.find(item => item.actionId === 'events.lifecycle.postpone')?.featureKey, 'ACTION_EVENT_POSTPONE');
});

test('media inventory decoder is bounded and rejects negative counts', () => {
  const inventory = decodeMediaStorageInventory({ trackedObjects: 2, trackedBytes: 10, activeObjects: 1, expiredObjects: 1, heldObjects: 0, purgeFailedObjects: 0, orphanObjects: 0, orphanBytes: 0, measuredAt: '2026-10-04T10:00:00' });
  assert.equal(inventory.trackedObjects, 2);
  assert.throws(() => decodeMediaStorageInventory({ trackedObjects: -1, trackedBytes: 10, activeObjects: 1, expiredObjects: 1, heldObjects: 0, purgeFailedObjects: 0, orphanObjects: 0, orphanBytes: 0, measuredAt: '2026-10-04T10:00:00' }));
});

test('library session decoder keeps only bounded owner-session fields', () => {
  const session = decodeLibrarySession({ id: 9, sessionType: 'STUDY', entryTime: '2026-10-04T10:00:00', status: 'ACTIVE', privateUser: { email: 'secret' } });
  assert.equal(session.sessionType, 'STUDY');
  assert.equal('privateUser' in session, false);
  assert.throws(() => decodeLibrarySession({ id: 9, sessionType: 'OTHER', entryTime: '2026-10-04T10:00:00', status: 'ACTIVE' }));
});

test('library entry and exit remain explicitly unavailable until verified scan workflows exist', () => {
  for (const [actionId, featureKey] of [['library.session.entry', 'ACTION_LIBRARY_ENTRY'], ['library.session.exit', 'ACTION_LIBRARY_EXIT']] as const) {
    const action = actions.find(item => item.actionId === actionId);
    assert.equal(action?.featureKey, featureKey);
    assert.deepEqual(action?.clients, ['web', 'android']);
    assert.equal(action?.status, 'unavailable');
  }
});

test('library history action is shared and bounded', () => {
  assert.deepEqual(actions.find(item => item.actionId === 'library.history.read')?.clients, ['web', 'android']);
  assert.throws(() => decodeLibraryHistory(Array(51).fill({ id: 1, sessionType: 'STUDY', entryTime: '2026-10-04T10:00:00', status: 'ACTIVE' })));
});
