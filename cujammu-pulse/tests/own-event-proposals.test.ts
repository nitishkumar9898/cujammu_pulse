import assert from 'node:assert/strict';
import test from 'node:test';
import { decodeEditableProposal, decodeOwnProposals, isCampusLocalDateTime, proposalMediaPath } from '../src/events/proposals.ts';
import { api } from '../src/api.ts';

test('campus-local proposal date/time validation rejects normalized dates without timezone conversion', () => {
  for (const value of ['2035-02-31T10:00', '2035-02-29T10:00', '2035-04-31T10:00', '2036-13-01T10:00', '2036-01-01T24:00', '2036-01-01T10:60', '2036-01-01T10:00Z']) {
    assert.equal(isCampusLocalDateTime(value), false, value);
  }
  for (const value of ['2036-02-29T10:00', '2000-02-29T23:59', '2036-04-30T00:00', '0000-02-29T12:30']) {
    assert.equal(isCampusLocalDateTime(value), true, value);
  }
});
test('proposal media URLs are opaque ID-scoped routes and validate kind and identifier', () => {
  assert.equal(proposalMediaPath('organizer', 43, 'banner'), '/api/v1/organizer/event-proposals/43/media/banner');
  assert.equal(proposalMediaPath('admin', 43, 'certificate-template'), '/api/v1/admin/event-proposals/43/media/certificate-template');
  assert.throws(() => proposalMediaPath('admin', 0, 'banner'));
  assert.throws(() => proposalMediaPath('admin', 43, '../uploads'));
});

const proposal = { id: 1, title: 'Synthetic proposal', venue: 'Synthetic hall', eventDate: '2035-04-02T10:00:00', status: 'APPROVED_PENDING_PUBLICATION', revision: 4, rejectionReason: null, submittedAt: '2035-01-01T10:00:00' };
test('own proposal page accepts bounded lifecycle projection and only known statuses', () => {
  assert.deepEqual(decodeOwnProposals({ items: [proposal], page: 0, hasNext: false }), { items: [proposal], page: 0, hasNext: false });
  for (const change of [{ status: 'UNKNOWN' }, { revision: -1 }, { title: 'x'.repeat(201) }, { rejectionReason: 'x'.repeat(501) }, { eventDate: 'invalid' }])
    assert.throws(() => decodeOwnProposals({ items: [{ ...proposal, ...change }], page: 0, hasNext: false }));
  assert.throws(() => decodeOwnProposals({ items: [proposal, proposal], page: 0, hasNext: false }));
  assert.throws(() => decodeOwnProposals({ items: Array.from({ length: 21 }, (_, id) => ({ ...proposal, id: id + 1 })), page: 0, hasNext: false }));
});
test('proposal create transport sends the same request key and exact validated payload under CSRF', async () => {
  const original = globalThis.fetch; let request: RequestInit | undefined;
  const command = { requestKey: '123e4567-e89b-42d3-a456-426614174000', title: 'Synthetic proposal', description: 'Synthetic proposal details for API verification.', categoryId: 3, venue: 'Synthetic hall', eventDate: '2035-04-02T10:00:00', eventEndsAt: null, maxSeats: 30, fees: 0, contactEmail: null, targetDepartment: 'ALL', generateCertificate: true };
  globalThis.fetch = async (input, init) => { if (String(input).endsWith('/session/csrf')) return Response.json({ token: 'synthetic', headerName: 'X-CSRF-TOKEN' }); request = init; return Response.json({ id: 42, status: 'PENDING', revision: 0, replayed: false }); };
  try { assert.deepEqual(await api.submitEventProposal(command), { id: 42, status: 'PENDING', revision: 0, replayed: false }); assert.deepEqual(JSON.parse(String(request?.body)), command); assert.equal((request?.headers as Record<string, string>)['X-CSRF-TOKEN'], 'synthetic'); }
  finally { globalThis.fetch = original; }
});
test('editable proposal projection validates bounded private fields and attachment flags', () => {
  const detail = { id: 43, title: 'Synthetic proposal', description: 'Synthetic proposal details with enough words.', categoryId: 3, venue: 'Synthetic hall', eventDate: '2035-04-02T10:00:00', eventEndsAt: null, maxSeats: 30, fees: 0, contactEmail: null, targetDepartment: 'ALL', generateCertificate: true, revision: 8, hasBanner: true, hasCertificateTemplate: false };
  assert.deepEqual(decodeEditableProposal(detail), detail);
  for (const change of [{ revision: -1 }, { categoryId: 0 }, { maxSeats: 10001 }, { hasBanner: 'true' }, { eventEndsAt: '2035-04-02T09:00:00' }, { contactEmail: 'x'.repeat(255) }])
    assert.throws(() => decodeEditableProposal({ ...detail, ...change }));
});
test('proposal update transport carries the observed revision and checks the returned revision', async () => {
  const original = globalThis.fetch; const calls: RequestInit[] = [];
  const command = { expectedRevision: 8, title: 'Revised proposal', description: 'Updated synthetic proposal details with enough words.', categoryId: 3, venue: 'Synthetic hall', eventDate: '2035-04-02T10:00:00', eventEndsAt: null, maxSeats: 30, fees: 0, contactEmail: null, targetDepartment: 'ALL', generateCertificate: true };
  const receipt = { id: 43, ...command, revision: 9, hasBanner: false, hasCertificateTemplate: false };
  globalThis.fetch = async (input, init) => { if (String(input).endsWith('/session/csrf')) return Response.json({ token: 'synthetic', headerName: 'X-CSRF-TOKEN' }); calls.push(init!); return Response.json(receipt); };
  try { assert.deepEqual(await api.updateEventProposal(43, command), decodeEditableProposal(receipt)); assert.deepEqual(JSON.parse(String(calls[0].body)), command); assert.equal((calls[0].headers as Record<string, string>)['X-CSRF-TOKEN'], 'synthetic'); }
  finally { globalThis.fetch = original; }
});
