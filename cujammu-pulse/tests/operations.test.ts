import test from 'node:test';
import assert from 'node:assert/strict';
import { actions, modules } from '../src/registry.ts';

test('operations console is admin-feature gated and shared', () => {
  assert.equal(modules.find(item => item.id === 'operations')?.implemented, true);
  const action = actions.find(item => item.actionId === 'operations.telemetry.read');
  assert.equal(action?.featureKey, 'MODULE_OPERATIONS');
  assert.deepEqual(action?.clients, ['web', 'android']);
});
