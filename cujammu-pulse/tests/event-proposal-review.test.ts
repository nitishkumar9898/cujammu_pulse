import assert from 'node:assert/strict';
import test from 'node:test';
import { decodeEventProposal, decodeProposalPage, decodeReviewerPage } from '../src/admin/event-proposal-review.ts';
import { api } from '../src/api.ts';
const proposal = { id: 7, title: 'Synthetic proposal', description: 'Synthetic proposal with complete review details.', venue: 'Synthetic Hall', eventDate: '2035-04-02T10:00:00', eventEndsAt: null, organizerName: 'Synthetic faculty', maxSeats: 30, fees: 25, targetDepartment: 'ALL', categoryName: 'Synthetic workshop', generateCertificate: true, hasBanner: false, hasCertificateTemplate: false, status: 'PENDING', revision: 4, assignedReviewerId: null, assignedReviewerName: null, assignedReviewerActive: false, canAssign: true, canDecide: false, canPublish: false };
const assigned = { ...proposal, revision: 5, assignedReviewerId: 1, assignedReviewerName: 'Synthetic admin', assignedReviewerActive: true, canDecide: true };
test('proposal projection preserves complete review details and excludes internal/private entity fields', () => {
  assert.deepEqual(decodeEventProposal({ ...proposal, organizer: { password: 'private' }, exitQrToken: 'private', lastProposalDecisionRevision: 3 }), proposal);
  assert.deepEqual(decodeEventProposal(assigned), assigned);
  assert.deepEqual(decodeReviewerPage({ items: [{ id: 1, name: 'Synthetic admin', email: 'private', password: 'private' }], page: 0, hasNext: false }).items, [{ id: 1, name: 'Synthetic admin' }]);
});
test('proposal decoding rejects impossible assignment capability, invalid state and unbounded fields', () => {
  for (const change of [{ canDecide: true }, { assignedReviewerActive: true }, { assignedReviewerName: 'Unexpected' }, { id: -1 }, { revision: 1.5 }, { revision: Number.MAX_SAFE_INTEGER + 1 }, { status: 'APPROVED' }, { canAssign: 'true' }, { description: 'x'.repeat(10001) }, { eventDate: 'not-a-date' }, { eventEndsAt: undefined }]) assert.throws(() => decodeEventProposal({ ...proposal, ...change }));
  assert.throws(() => decodeEventProposal({ ...assigned, assignedReviewerActive: false }));
  assert.throws(() => decodeEventProposal({ ...assigned, assignedReviewerName: null }));
  for (const change of [{ fees: -1 }, { fees: Number.POSITIVE_INFINITY }, { maxSeats: 0 }, { hasBanner: 'false' }, { targetDepartment: 'x'.repeat(201) }, { generateCertificate: null }]) assert.throws(() => decodeEventProposal({ ...proposal, ...change }));
});
test('review pages reject oversized or duplicate inventories and inconsistent envelope types', () => {
  assert.deepEqual(decodeProposalPage({ items: [proposal], page: 0, hasNext: true }), { items: [proposal], page: 0, hasNext: true });
  for (const value of [{ items: [proposal, proposal], page: 0, hasNext: false }, { items: Array.from({ length: 21 }, (_, i) => ({ ...proposal, id: i + 1 })), page: 0, hasNext: true }, { items: [], page: -1, hasNext: false }, { items: [], page: 0, hasNext: 0 }]) assert.throws(() => decodeProposalPage(value));
  assert.throws(() => decodeReviewerPage({ items: [{ id: 1, name: '' }], page: 0, hasNext: false }));
});
test('assignment receipts must match the event, requested reviewer and observed revision', async () => {
  const original = globalThis.fetch; let response = assigned;
  globalThis.fetch = async input => String(input).endsWith('/session/csrf') ? Response.json({ token: 'synthetic', headerName: 'X-CSRF-TOKEN' }) : Response.json(response);
  try {
    assert.deepEqual(await api.assignEventProposalReviewer(7, { expectedRevision: 4, reviewerId: 1 }), assigned);
    for (const change of [{ id: 8 }, { assignedReviewerId: 2 }, { revision: 3 }]) { response = { ...assigned, ...change }; await assert.rejects(api.assignEventProposalReviewer(7, { expectedRevision: 4, reviewerId: 1 }), /does not match/); }
  } finally { globalThis.fetch = original; }
});
test('review decisions require HTTP204 and preserve exact CSRF/revision/reason command', async () => {
  const original = globalThis.fetch; let status = 200; let body: unknown;
  globalThis.fetch = async (input, init) => {
    if (String(input).endsWith('/session/csrf')) return Response.json({ token: 'synthetic', headerName: 'X-CSRF-TOKEN' });
    assert.equal((init?.headers as Record<string, string>)['X-CSRF-TOKEN'], 'synthetic'); body = JSON.parse(String(init?.body));
    return status === 204 ? new Response(null, { status }) : Response.json(null, { status });
  };
  try {
    await assert.rejects(api.decideEventProposal(7, 5, 'approve', ''), /did not confirm/);
    status = 204; await api.decideEventProposal(7, 5, 'reject', 'Synthetic reviewed reason');
    assert.deepEqual(body, { expectedRevision: 5, reason: 'Synthetic reviewed reason' });
    await api.decideEventProposal(7, 5, 'approve', ''); assert.deepEqual(body, { expectedRevision: 5 });
  } finally { globalThis.fetch = original; }
});
test('queue responses cannot substitute another page', async () => {
  const original = globalThis.fetch; globalThis.fetch = async () => Response.json({ items: [], page: 1, hasNext: false });
  try { await assert.rejects(api.eventProposalQueue(0), /does not match/); await assert.rejects(api.eventProposalReviewers(0), /does not match/); }
  finally { globalThis.fetch = original; }
});
test('publication queue accepts only the approved private state and publish command is revision-bound', async () => {
  const original = globalThis.fetch; let path = ''; let body: unknown;
  globalThis.fetch = async (input, init) => {
    path = String(input);
    if (path.endsWith('/session/csrf')) return Response.json({ token: 'synthetic', headerName: 'X-CSRF-TOKEN' });
    if (init?.method === 'POST') { body = JSON.parse(String(init.body)); return new Response(null, { status: 204 }); }
    return Response.json({ items: [{ ...proposal, status: 'APPROVED_PENDING_PUBLICATION', canAssign: false, canDecide: false, canPublish: true }], page: 0, hasNext: false });
  };
  try {
    const page = await api.eventProposalPublicationQueue(0);
    assert.equal(page.items[0].status, 'APPROVED_PENDING_PUBLICATION');
    await api.publishEventProposal(7, 6);
    assert.match(path, /\/api\/v1\/admin\/event-proposals\/7\/publish$/);
    assert.deepEqual(body, { expectedRevision: 6 });
  } finally { globalThis.fetch = original; }
});
