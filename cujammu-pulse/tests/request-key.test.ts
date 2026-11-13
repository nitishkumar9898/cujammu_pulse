import test from 'node:test';
import assert from 'node:assert/strict';
import { newRequestKey } from '../src/api.ts';

test('request keys are unique RFC 4122 version 4 UUIDs', () => {
  const keys = Array.from({ length: 256 }, () => newRequestKey());
  assert.equal(new Set(keys).size, keys.length);
  for (const key of keys) assert.match(key, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
});
