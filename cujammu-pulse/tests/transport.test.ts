import test from 'node:test';
import assert from 'node:assert/strict';
import { ApiError, api, requestJson, signOut } from '../src/api.ts';
import { actions } from '../src/registry.ts';

test('web requests retain same-origin credentials and reject redirects', async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async (_input, init) => {
    assert.equal(init?.credentials, 'same-origin'); assert.equal(init?.redirect, 'error'); assert.equal(init?.cache, 'no-store');
    return new Response('{"value":true}', { headers: { 'content-type': 'application/json' } });
  };
  try { assert.deepEqual(await requestJson('/api/v1/session'), { value: true }); } finally { globalThis.fetch = original; }
});
test('expired sessions and HTML login pages never become successful data', async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async () => new Response('', { status: 401 });
    await assert.rejects(requestJson('/api/v1/session'), (error: unknown) => error instanceof ApiError && error.status === 401);
    globalThis.fetch = async () => new Response('<html>login</html>', { headers: { 'content-type': 'text/html' } });
    await assert.rejects(requestJson('/api/v1/session'), /unexpected response/);
  } finally { globalThis.fetch = original; }
});
test('transport rejects an arbitrary external endpoint before fetch', async () => {
  await assert.rejects(requestJson('https://example.invalid/api/v1/session'), /Invalid API path/);
});
test('event lifecycle commands use same-origin CSRF and are registered for both clients', async () => {
  const original = globalThis.fetch; const calls: Array<{ path: string; method?: string; body?: unknown }> = [];
  globalThis.fetch = async (input, init) => {
    const path = String(input); calls.push({ path, method: init?.method, body: init?.body ? JSON.parse(String(init.body)) : undefined });
    if (path === '/api/v1/session/csrf') return Response.json({ token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' });
    return new Response(null, { status: 204 });
  };
  try {
    await api.postponeEvent(42, '2026-11-01T10:00:00', 'Weather disruption', '11111111-1111-4111-8111-111111111111', 0);
    await api.cancelEvent(42, '22222222-2222-4222-8222-222222222222', 1);
    assert.equal(calls[1]?.path, '/api/v1/cujammu/events/42/lifecycle/postpone');
    assert.equal(calls[1]?.method, 'POST');
    assert.deepEqual(calls[1]?.body, { newDate: '2026-11-01T10:00:00', reason: 'Weather disruption', requestKey: '11111111-1111-4111-8111-111111111111', expectedRevision: 0 });
    assert.equal(calls[3]?.path, '/api/v1/cujammu/events/42/lifecycle/cancel');
    assert.deepEqual(calls[3]?.body, { requestKey: '22222222-2222-4222-8222-222222222222', expectedRevision: 1 });
    for (const [actionId, featureKey] of [['events.lifecycle.postpone', 'ACTION_EVENT_POSTPONE'], ['events.lifecycle.cancel', 'ACTION_EVENT_CANCEL']] as const) {
      assert.ok(actions.some(action => action.actionId === actionId && action.featureKey === featureKey
        && action.clients.includes('web') && action.clients.includes('android')));
    }
  } finally { globalThis.fetch = original; }
});

test('library transport uses bounded same-origin entry, exit and history routes', async () => {
  const original = globalThis.fetch;
  const calls: Array<{ path: string; method?: string; body?: unknown }> = [];
  globalThis.fetch = async (input, init) => {
    const path = String(input); calls.push({ path, method: init?.method, body: init?.body ? JSON.parse(String(init.body)) : undefined });
    if (path === '/api/v1/session/csrf') return new Response(JSON.stringify({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' }), { headers: { 'content-type': 'application/json' } });
    if (path.includes('/library/active')) return new Response('null', { headers: { 'content-type': 'application/json' } });
    if (path.includes('/library/history')) return new Response('[]', { headers: { 'content-type': 'application/json' } });
    return new Response(JSON.stringify({ id: 1, sessionType: 'STUDY', entryTime: '2026-10-04T10:00:00', status: 'ACTIVE' }), { headers: { 'content-type': 'application/json' } });
  };
  try {
    await api.libraryEntry('STUDY'); await api.libraryExit('STUDY'); await api.libraryActive(); await api.libraryHistory(0, 20);
    assert.ok(calls.some(call => call.path === '/api/v1/cujammu/library/scan/entry' && call.method === 'POST' && call.body?.sessionType === 'STUDY'));
    assert.ok(calls.some(call => call.path === '/api/v1/cujammu/library/scan/exit' && call.method === 'POST'));
    assert.ok(calls.some(call => call.path === '/api/v1/cujammu/library/history?page=0&size=20' && !call.path.includes('://')));
  } finally { globalThis.fetch = original; }
});
test('event reschedule availability is advisory and shared across web and Android', async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async input => {
    assert.equal(input, '/api/v1/cujammu/events/42/lifecycle/availability-suggestions?durationMinutes=90&days=14');
    return Response.json({ facilityId: 7, facilityName: 'Synthetic Hall', durationMinutes: 90,
      searchedFrom: '2026-10-03T10:00:00', searchedUntil: '2026-10-17T10:00:00', computedAt: '2026-10-03T09:59:00',
      status: 'ADVISORY_ONLY', checkedConstraints: ['Known active facility-booking overlaps'],
      unknownConstraints: ['Opening hours', 'Event-specific suitability'],
      items: [{ startTime: '2026-10-03T10:00:00', endTime: '2026-10-03T11:30:00' }] });
  };
  try {
    const result = await api.eventRescheduleSuggestions(42, 90, 14);
    assert.equal(result.status, 'ADVISORY_ONLY'); assert.equal(result.facilityId, 7); assert.equal(result.items.length, 1);
    assert.ok(actions.some(action => action.actionId === 'events.lifecycle.availability.suggest'
      && action.featureKey === 'MODULE_EVENTS' && action.clients.includes('web') && action.clients.includes('android')));
  } finally { globalThis.fetch = original; }
});
test('event lifecycle history is DTO-decoded and shared across web and Android', async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async input => {
    assert.equal(input, '/api/v1/cujammu/events/42/lifecycle/history');
    return Response.json([{ id: 8, changeNumber: 1, changeType: 'RESCHEDULED', previousDate: '2026-10-03T10:00:00', replacementDate: '2026-10-04T10:00:00', reason: 'Weather disruption', actorRole: 'FACULTY', recordedAt: '2026-10-03T08:00:00' }]);
  };
  try {
    const result = await api.eventScheduleHistory(42);
    assert.equal(result[0]?.changeType, 'RESCHEDULED');
    assert.ok(actions.some(action => action.actionId === 'events.lifecycle.history.read'
      && action.featureKey === 'MODULE_EVENTS' && action.clients.includes('web') && action.clients.includes('android')));
  } finally { globalThis.fetch = original; }
});
test('logout requires verified CSRF and posts with session cookies', async () => {
  const original = globalThis.fetch; let calls = 0;
  globalThis.fetch = async (input, init) => {
    calls++;
    if (calls === 1) return Response.json({ token: 'synthetic-token', headerName: 'X-CSRF-TOKEN' });
    assert.equal(input, '/logout'); assert.equal(init?.method, 'POST'); assert.equal(init?.credentials, 'same-origin');
    assert.deepEqual(init?.headers, { 'X-CSRF-TOKEN': 'synthetic-token' }); return new Response('');
  };
  try { await signOut(); assert.equal(calls, 2); } finally { globalThis.fetch = original; }
});
test('malformed CSRF response prevents logout POST', async () => {
  const original = globalThis.fetch; let calls = 0;
  globalThis.fetch = async () => { calls++; return Response.json({ token: 'x', headerName: 'Authorization' }); };
  try { await assert.rejects(signOut(), /Cannot verify/); assert.equal(calls, 1); } finally { globalThis.fetch = original; }
});
