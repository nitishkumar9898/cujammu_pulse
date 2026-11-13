import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeEvents, decodeRegistration, decodeRegistrations } from '../src/events/contracts.ts';
import { actions } from '../src/registry.ts';
import { decodeEventSummaryPage } from '../src/event-operations/contracts.ts';

const event = { id: 7, title: 'Campus Hackathon', description: 'Build useful tools', venue: 'Auditorium', eventDate: '2026-10-10T10:00:00Z', maxSeats: 120, status: 'APPROVED', targetDepartment: 'CSIT' };

test('delegated attendance event pages enforce bounded event and continuation metadata', () => {
  const page = decodeEventSummaryPage({ page: 1, hasNext: true, items: [{ id: 8, title: 'Delegated event', status: 'COMPLETED', eventDate: '2026-10-11T10:00:00Z', operationsRevision: 5, secret: 'discarded' }] });
  assert.equal(page.page, 1);
  assert.equal(page.hasNext, true);
  assert.equal(page.items[0]?.id, 8);
  assert.equal('secret' in (page.items[0] ?? {}), false);
  assert.throws(() => decodeEventSummaryPage({ page: -1, hasNext: false, items: [] }));
  assert.throws(() => decodeEventSummaryPage({ page: 0, hasNext: 'true', items: [] }));
  assert.throws(() => decodeEventSummaryPage({ page: 0, hasNext: false, items: Array(501).fill({ id: 8, title: 'x', status: 'COMPLETED', eventDate: '2026-10-11T10:00:00Z', operationsRevision: 5 }) }));
});

test('event catalog decoder is bounded and strips unknown fields', () => {
  const result = decodeEvents([{ ...event, privateOrganizer: { email: 'hidden' } }]);
  assert.equal(result[0]?.title, 'Campus Hackathon');
  assert.equal('privateOrganizer' in (result[0] ?? {}), false);
  assert.throws(() => decodeEvents([{ ...event, title: 'x'.repeat(201) }]));
  assert.throws(() => decodeEvents([{ ...event, maxSeats: -1 }]));
  assert.throws(() => decodeEvents(Array(201).fill(event)));
});

test('registration decoders accept only bounded server projections', () => {
  assert.deepEqual(decodeRegistration({ id: 3, eventId: 7, status: 'REGISTERED', registeredAt: null }), { id: 3, eventId: 7, status: 'REGISTERED', registeredAt: null });
  assert.deepEqual(decodeRegistrations([{ id: 3, eventId: 7, status: 'REGISTERED', registeredAt: '2026-10-04T10:00:00Z', email: 'secret' }])[0]?.eventId, 7);
  assert.throws(() => decodeRegistration({ id: 3, eventId: 7, status: 'REGISTERED', registeredAt: 'x'.repeat(81) }));
});

test('event catalog and registration actions are shared and feature-gated', () => {
  for (const id of ['events.catalog.read', 'events.registration.create', 'events.registration.mine.read', 'events.registration.cancel']) {
    const action = actions.find(item => item.actionId === id);
    assert.deepEqual(action?.clients, ['web', 'android']);
    assert.equal(action?.featureKey, 'MODULE_EVENTS');
    assert.equal(action?.status, 'web-session-bridge');
  }
});
