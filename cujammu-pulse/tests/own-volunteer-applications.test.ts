import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeOwnVolunteerApplications } from '../src/event-operations/own-applications.ts';

const item = { id: 1, revision: 2, status: 'ACCEPTED', eventId: 3, eventTitle: 'Synthetic event', eventStatus: 'COMPLETED', eventDate: '2035-01-01T10:00:00', appliedAt: '2034-12-01T10:00:00', decidedAt: '2034-12-02T10:00:00' };
test('own application projection excludes private identifiers and preserves historical outcome', () => {
  const result = decodeOwnVolunteerApplications({ items: [{ ...item, identifier: 'private', contact: 'private', userId: 9 }], page: 0, size: 20, hasNext: false });
  assert.deepEqual(result.items, [item]);
  assert.equal(decodeOwnVolunteerApplications({ items: [{ ...item, status: 'REMOVED' }], page: 0, size: 20, hasNext: false }).items[0].status, 'REMOVED');
  assert.deepEqual(decodeOwnVolunteerApplications({ items: [], page: 0, size: 20, hasNext: false }).items, []);
});
test('own application decoder rejects malformed bounds, duplicates and unsupported status', () => {
  const page = { items: [item], page: 0, size: 20, hasNext: false };
  for (const invalid of [null, { ...page, size: 51 }, { ...page, size: 0 }, { ...page, page: -1 }, { ...page, hasNext: 'false' }, { ...page, items: [item, item] }, { ...page, items: [{ ...item, status: 'UNKNOWN' }] }, { ...page, items: [{ ...item, eventDate: 'invalid' }] }, { ...page, items: [{ ...item, id: 0 }] }]) {
    assert.throws(() => decodeOwnVolunteerApplications(invalid));
  }
});
