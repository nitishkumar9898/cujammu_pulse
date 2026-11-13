import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeDepartments, decodeFacilities, decodeFlags, decodeSession } from '../src/contracts.ts';
import { canOpenModule, foundationRequirements, modules, visibleModules } from '../src/registry.ts';
import { resolveTheme } from '../src/theme.ts';

test('session allowlist discards nested secrets and account records', () => {
  assert.deepEqual(decodeSession({ id: 1, name: 'Synthetic Student', role: 'STUDENT', password: 'not-a-secret-fixture', nested: { email: 'synthetic@example.invalid' } }), { id: 1, name: 'Synthetic Student', role: 'STUDENT' });
});
test('session rejects malformed and unsafe identifiers', () => {
  for (const id of [null, '1', 0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1]) assert.throws(() => decodeSession({ id, name: 'A', role: 'STUDENT' }));
});
test('flags reject truthy strings rather than enabling them', () => {
  assert.throws(() => decodeFlags({ MODULE_VENUES: 'true' }));
  assert.throws(() => decodeFlags(JSON.parse('{"__proto__":true}')));
});
test('unknown and disabled feature keys do not open routes', () => {
  assert.equal(canOpenModule('venues', {}), false);
  assert.equal(canOpenModule('venues', { MODULE_VENUES: false }), false);
  assert.deepEqual(visibleModules({ UNKNOWN_MODULE: true }), []);
});
test('enabled supported hostel request workflow becomes interactive', () => {
  assert.equal(canOpenModule('hostels', { MODULE_HOSTEL_GATEPASS: true }), true);
  assert.equal(visibleModules({ MODULE_HOSTEL_GATEPASS: true })[0]?.implemented, true);
});
test('implemented shared modules have their universal client slices wired', () => {
  for (const id of ['library', 'attendance', 'chat', 'calendar', 'disruptions']) {
    assert.equal(modules.find(module => module.id === id)?.implemented, true, `${id} should be implemented`);
  }
});
test('disable removes previously available venue route', () => {
  assert.equal(canOpenModule('venues', decodeFlags({ MODULE_VENUES: true })), true);
  assert.equal(canOpenModule('venues', decodeFlags({ MODULE_VENUES: false })), false);
});
test('department decoder strips contact details', () => {
  assert.deepEqual(decodeDepartments([{ id: 1, code: 'TEST', name: 'Synthetic Department', phone: 'not-rendered', contactEmail: 'test@example.invalid' }]), [{ id: 1, code: 'TEST', name: 'Synthetic Department', building: '' }]);
});
test('facility decoder rejects negative capacity and strips unknown fields', () => {
  assert.throws(() => decodeFacilities([{ id: 1, name: 'Hall', facilityType: 'HALL', capacity: -1 }]));
  assert.deepEqual(decodeFacilities([{ id: 1, name: 'Hall', facilityType: 'HALL', secret: 'not-rendered' }]), [{ id: 1, name: 'Hall', facilityType: 'HALL', location: '', capacity: null }]);
});
test('oversize catalogues fail closed', () => assert.throws(() => decodeDepartments(new Array(1001).fill({}))));
test('delivery inventory retains every active CF and excludes retired CF-12', () => {
  const covered = new Set([...foundationRequirements, ...modules.flatMap(module => module.requirements)]);
  assert.equal(covered.size, 29);
  for (let i = 1; i <= 30; i++) assert.equal(covered.has(`CF-${String(i).padStart(2, '0')}`), i !== 12);
});
test('system theme changes without overriding explicit preference', () => {
  assert.equal(resolveTheme('system', 'dark'), resolveTheme('dark', 'light'));
  assert.equal(resolveTheme('system', null), resolveTheme('light', 'dark'));
});
