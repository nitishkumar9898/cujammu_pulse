import test from 'node:test';
import assert from 'node:assert/strict';
import { decodePoll, decodePollEvents, decodePollPage } from '../src/polls/contracts.ts';
import { api, ApiError } from '../src/api.ts';

const poll = { id: 1, eventId: 2, question: 'Audience choice?', options: ['Alpha', 'Beta'], state: 'OPEN', revision: 3, opensAt: '2026-10-03T10:00:00Z', closesAt: '2026-10-03T11:00:00Z', canManage: false, canVote: true, myChoice: null, totalVotes: 3, results: [{ option: 'Alpha', votes: 2, percentage: 66.67 }, { option: 'Beta', votes: 1, percentage: 33.33 }] };
test('poll decoding exposes aggregates and own choice only, rejects inconsistent or unbounded results', () => {
  const decoded = decodePoll({ ...poll, voterIds: ['private'], requestKey: 'private' });
  assert.deepEqual(decoded, poll);
  assert.equal(decodePoll({ ...poll, results: [{ option: 'Alpha', votes: 2, percentage: 66.7 }, { option: 'Beta', votes: 1, percentage: 33.3 }] }).totalVotes, 3);
  assert.equal(decodePoll({ ...poll, state: 'CLOSED', canVote: false, closesAt: poll.opensAt }).state, 'CLOSED');
  for (const change of [{ totalVotes: 2 }, { options: ['Alpha', 'alpha'] }, { myChoice: 'Other' }, { canVote: 'true' }, { state: 'UNKNOWN' }, { results: [{ option: 'Alpha', votes: 2, percentage: 101 }, poll.results[1]] }, { revision: -1 }, { closesAt: 'bad date' }]) assert.throws(() => decodePoll({ ...poll, ...change }));
  assert.throws(() => decodePollPage({ items: [poll, poll], page: 0, hasMore: false }));
  assert.throws(() => decodePollPage({ items: Array(21).fill(poll), page: 0, hasMore: false }));
  assert.throws(() => decodePollEvents({ items: [], page: -1, hasMore: false }));
  assert.throws(() => decodePoll({ ...poll, options: ['1', 'Beta'], myChoice: 1, results: [{ ...poll.results[0], option: '1' }, poll.results[1]] }));
  for (const change of [{ opensAt: '2026-10-03T10:00:00' }, { closesAt: poll.opensAt }, { state: 'DRAFT' }, { myChoice: 'Alpha' }, { state: 'CLOSED' }]) assert.throws(() => decodePoll({ ...poll, ...change }));
});
test('poll command transport preserves retry key/revision/choice and CSRF without privileged body fields', async () => {
  const original = globalThis.fetch;
  const calls: { path: string; body: unknown }[] = [];
  globalThis.fetch = async (path, init) => {
    if (path === '/api/v1/session/csrf') return Response.json({ token: 'synthetic', headerName: 'X-CSRF-TOKEN' });
    assert.equal((init?.headers as Record<string, string>)['X-CSRF-TOKEN'], 'synthetic');
    assert.equal(init?.method, 'POST');
    calls.push({ path: String(path), body: JSON.parse(String(init?.body)) }); return Response.json(poll);
  };
  try {
    const command = { question: 'Audience choice?', options: ['Alpha', 'Beta'], durationMinutes: 60, requestKey: '8f6b2e5e-f6e6-48bc-b4a3-94b858cb175c' };
    await api.createPoll(2, command); await api.createPoll(2, command); await api.publishPoll(1, 3); await api.closePoll(1, 4); await api.votePoll(1, 'Alpha');
    assert.deepEqual(calls.map(c => c.body), [command, command, { expectedRevision: 3 }, { expectedRevision: 4 }, { option: 'Alpha' }]);
    assert.deepEqual(calls.map(c => c.path), ['/api/v1/cujammu/events/2/polls', '/api/v1/cujammu/events/2/polls', '/api/v1/cujammu/polls/1/publish', '/api/v1/cujammu/polls/1/close', '/api/v1/cujammu/polls/1/vote']);
  } finally { globalThis.fetch = original; }
});
test('poll transport does not replay denied or conflicting votes automatically', async () => {
  const original = globalThis.fetch;
  try { for (const status of [401, 403, 404, 409]) {
    let posts = 0; globalThis.fetch = async path => { if (path === '/api/v1/session/csrf') return Response.json({ token: 'synthetic', headerName: 'X-CSRF-TOKEN' }); posts++; return Response.json({ error: 'private details' }, { status }); };
    await assert.rejects(api.votePoll(1, 'Beta'), (e: unknown) => e instanceof ApiError && e.status === status && !e.message.includes('private details'));
    assert.equal(posts, 1);
  } } finally { globalThis.fetch = original; }
});
