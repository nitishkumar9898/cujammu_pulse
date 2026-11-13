import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeFacilityBookingReceipt, decodeFacilitySuggestions, decodeMyFacilityBookings } from '../src/contracts.ts';
import { api } from '../src/api.ts';
import { actions } from '../src/registry.ts';

const valid = {
  facilityId: 4, facilityName: 'Synthetic Hall', durationMinutes: 60,
  searchedFrom: '2026-10-03T09:00:00', searchedUntil: '2026-10-17T09:00:00', computedAt: '2026-10-03T08:55:00',
  status: 'ADVISORY_ONLY', checkedConstraints: ['Known active facility-booking overlaps'],
  unknownConstraints: ['Opening hours', 'Maintenance closures'],
  items: [{ startTime: '2026-10-03T09:00:00', endTime: '2026-10-03T10:00:00', bookedBy: 'private', title: 'hidden' }],
};

test('venue suggestions disclose advisory status and only interval fields', () => {
  const decoded = decodeFacilitySuggestions(valid);
  assert.equal(decoded.status, 'ADVISORY_ONLY');
  assert.deepEqual(decoded.items, [{ startTime: '2026-10-03T09:00:00', endTime: '2026-10-03T10:00:00' }]);
  assert.ok(decoded.unknownConstraints.includes('Opening hours'));
});

test('venue suggestion decoder rejects invalid windows, unbounded results, and false confirmation', () => {
  assert.throws(() => decodeFacilitySuggestions({ ...valid, status: 'AVAILABLE' }));
  assert.throws(() => decodeFacilitySuggestions({ ...valid, items: Array(21).fill(valid.items[0]) }));
  assert.throws(() => decodeFacilitySuggestions({ ...valid, items: [{ startTime: 'bad', endTime: '2026-10-03T10:00:00' }] }));
});

test('booking receipt accepts only the status and fields allowlist', () => {
  assert.deepEqual(decodeFacilityBookingReceipt({ id: 9, facilityId: 4, version: 2, status: 'CONFIRMED',
    startTime: '2026-10-03T09:00:00', endTime: '2026-10-03T10:00:00', bookedBy: { email: 'private' } }),
  { id: 9, facilityId: 4, version: 2, status: 'CONFIRMED', startTime: '2026-10-03T09:00:00', endTime: '2026-10-03T10:00:00' });
  for (const status of ['PENDING', 'REJECTED', 'CANCELLED']) assert.equal(decodeFacilityBookingReceipt({ id: 9, facilityId: 4, version: 0, status, startTime: '2026-10-03T09:00:00', endTime: '2026-10-03T10:00:00' }).status, status);
  assert.throws(() => decodeFacilityBookingReceipt({ id: 9, facilityId: 4, status: 'PENDING', startTime: '2026-10-03T09:00:00', endTime: '2026-10-03T10:00:00' }));
});

test('my venue booking history is bounded, owner-scoped and strips unapproved server fields', () => {
  const decoded = decodeMyFacilityBookings({ page: 0, hasNext: false, items: [{ id: 12, version: 1, facilityId: 4,
    facilityName: 'Synthetic Hall', title: 'Study group', status: 'PENDING', startTime: '2026-10-03T09:00:00',
    endTime: '2026-10-03T10:00:00', bookedBy: { email: 'private@example.invalid' } }] });
  assert.equal(decoded.items[0]?.status, 'PENDING');
  assert.equal('bookedBy' in (decoded.items[0] ?? {}), false);
  assert.throws(() => decodeMyFacilityBookings({ page: 0, hasNext: false, items: Array(31).fill({}) }));
  assert.throws(() => decodeMyFacilityBookings({ page: 0, hasNext: false, items: [{ id: 12, version: 1, facilityId: 4,
    facilityName: 'Synthetic Hall', title: 'Study group', status: 'PRIVATE', startTime: '2026-10-03T09:00:00', endTime: '2026-10-03T10:00:00' }] }));
});

test('venue suggestion action is shared by web and Android and remains under venue feature control', () => {
  assert.ok(actions.some(action => action.actionId === 'venues.availability.suggest'
    && action.featureKey === 'MODULE_VENUES' && action.clients.includes('web') && action.clients.includes('android')));
  assert.ok(actions.some(action => action.actionId === 'venues.booking.create'
    && action.operationId === 'POST /api/v1/cujammu/facilities/{id}/book'
    && action.featureKey === 'MODULE_VENUES' && action.clients.includes('web') && action.clients.includes('android')));
  for (const actionId of ['venues.booking.review.pending.read', 'venues.booking.review.decision']) {
    assert.ok(actions.some(action => action.actionId === actionId && action.featureKey === 'MODULE_VENUES'
      && action.clients.includes('web') && action.clients.includes('android')));
  }
  assert.ok(actions.some(action => action.actionId === 'venues.booking.mine.read'
    && action.operationId === 'GET /api/v1/cujammu/facilities/bookings/mine'
    && action.featureKey === 'MODULE_VENUES' && action.clients.includes('web') && action.clients.includes('android')));
  assert.ok(actions.some(action => action.actionId === 'venues.booking.cancel.pending'
    && action.featureKey === 'MODULE_VENUES' && action.clients.includes('web') && action.clients.includes('android')));
});

test('booking POST carries a stable request key with CSRF and no client priority', async () => {
  const originalFetch = globalThis.fetch;
  try {
    let sentBody: Record<string, unknown> | null = null;
    globalThis.fetch = async (input, init) => {
      if (input === '/api/v1/session/csrf') return Response.json({ token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' });
      assert.equal(input, '/api/v1/cujammu/facilities/4/book');
      assert.equal(init?.method, 'POST');
      assert.equal((init?.headers as Record<string, string>)['X-CSRF-TOKEN'], 'synthetic-csrf');
      sentBody = JSON.parse(init?.body as string) as Record<string, unknown>;
      return Response.json({ id: 17, facilityId: 4, version: 0, status: 'PENDING', startTime: '2026-10-03T09:00:00', endTime: '2026-10-03T10:00:00' });
    };
    const result = await api.bookFacility(4, { title: 'Synthetic meeting', startTime: '2026-10-03T09:00:00', endTime: '2026-10-03T10:00:00', requestedMics: 1, requestedProjector: false, requestKey: '3f241fdf-8a72-4d10-9c37-9a2a8b7ce621' });
    assert.equal(result.id, 17);
    assert.equal(sentBody?.requestKey, '3f241fdf-8a72-4d10-9c37-9a2a8b7ce621');
    assert.equal('priorityLevel' in (sentBody ?? {}), false);
  } finally { globalThis.fetch = originalFetch; }
});

test('pending booking cancellation sends CSRF, expected version and reason', async () => {
  const originalFetch = globalThis.fetch;
  try {
    globalThis.fetch = async (input, init) => {
      if (input === '/api/v1/session/csrf') return Response.json({ token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' });
      assert.equal(input, '/api/v1/cujammu/facilities/bookings/17/cancel');
      assert.equal(init?.method, 'PUT');
      assert.equal((init?.headers as Record<string, string>)['X-CSRF-TOKEN'], 'synthetic-csrf');
      assert.deepEqual(JSON.parse(init?.body as string), { expectedVersion: 0, reason: 'Schedule changed' });
      return Response.json({ id: 17, facilityId: 4, version: 1, status: 'CANCELLED', startTime: '2026-10-03T09:00:00', endTime: '2026-10-03T10:00:00' });
    };
    const result = await api.cancelFacilityBooking(17, 0, 'Schedule changed');
    assert.equal(result.status, 'CANCELLED');
  } finally { globalThis.fetch = originalFetch; }
});
