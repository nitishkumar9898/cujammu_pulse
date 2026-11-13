import test from 'node:test';
import assert from 'node:assert/strict';
import { api, ApiError } from '../src/api.ts';

test('chat posting preserves the caller request key and protects each POST with CSRF', async () => {
  const original = globalThis.fetch;
  const key = '4d0a8174-6b29-47d7-963c-becf8012e21d';
  const bodies: unknown[] = [];
  globalThis.fetch = async (input, init) => {
    if (String(input) === '/api/v1/session/csrf') return Response.json({ token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' });
    assert.equal(input, '/api/v1/cujammu/chat/groups/7/messages');
    assert.equal(init?.method, 'POST');
    assert.equal((init?.headers as Record<string, string>)['X-CSRF-TOKEN'], 'synthetic-csrf');
    bodies.push(JSON.parse(String(init?.body)));
    return Response.json({ id: 41, senderName: 'Synthetic member', content: 'Hello team', createdAt: '2026-10-03T10:00:00' });
  };
  try {
    const first = await api.postChatMessage(7, 'Hello team', key);
    const retried = await api.postChatMessage(7, 'Hello team', key);
    assert.equal(first.id, retried.id);
    assert.deepEqual(bodies, [{ requestKey: key, content: 'Hello team' }, { requestKey: key, content: 'Hello team' }]);
  } finally { globalThis.fetch = original; }
});

test('chat transport never automatically retries denied, conflicting, or limited sends', async () => {
  const original = globalThis.fetch;
  try {
    for (const status of [403, 409, 429]) {
      let posts = 0;
      globalThis.fetch = async input => {
        if (String(input) === '/api/v1/session/csrf') return Response.json({ token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' });
        posts++;
        return Response.json({ error: 'PRIVATE_SERVER_DETAIL' }, { status });
      };
      await assert.rejects(api.postChatMessage(7, 'Hello team', '4d0a8174-6b29-47d7-963c-becf8012e21d'),
        (error: unknown) => error instanceof ApiError && error.status === status && !error.message.includes('PRIVATE_SERVER_DETAIL'));
      assert.equal(posts, 1);
    }
  } finally { globalThis.fetch = original; }
});
