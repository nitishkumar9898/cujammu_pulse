import test from 'node:test';
import assert from 'node:assert/strict';
import { uploadProposalAttachment } from '../src/events/proposal-attachment-upload.ts';
import { installNativeAccess, selectApiMode } from '../src/identity/transport.ts';

test('multipart proposal upload uses CSRF, keeps retry identity and rejects mismatched receipts', async () => {
  const original = globalThis.fetch; const key = '12345678-1234-4234-8234-123456789abc';
  let posts = 0; let wrongId = false;
  globalThis.fetch = async (input, init) => {
    if (String(input).endsWith('/csrf')) return Response.json({ token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' });
    posts++;
    assert.equal(String(input), '/api/v1/organizer/event-proposals/7/attachments/banner');
    assert.equal(init?.method, 'POST'); assert.equal(init?.credentials, 'same-origin'); assert.equal(init?.redirect, 'error');
    assert.equal((init?.headers as Record<string, string>)['X-CSRF-TOKEN'], 'synthetic-csrf');
    assert.equal((init?.headers as Record<string, string>)['Content-Type'], undefined);
    const form = init?.body as FormData;
    assert.equal(form.get('requestKey'), key); assert.equal(form.get('expectedRevision'), '2');
    assert.equal((form.get('file') as Blob).size, 3);
    return Response.json({ id: wrongId ? 8 : 7, revision: 3, kind: 'banner', replayed: posts > 1,
      uploadAcceptedAt: '2035-04-01T10:00:00Z', expiresAt: '2035-05-01T10:00:00Z' });
  };
  try {
    const file = new Blob(['png'], { type: 'image/png' });
    assert.equal((await uploadProposalAttachment(7, 'banner', 2, key, file)).replayed, false);
    assert.equal((await uploadProposalAttachment(7, 'banner', 2, key, file)).replayed, true);
    wrongId = true; await assert.rejects(uploadProposalAttachment(7, 'banner', 2, key, file), /does not match/);
    assert.equal(posts, 3);
    await assert.rejects(uploadProposalAttachment(7, 'banner', 2, key, new Blob(['html'], { type: 'text/html' })));
    assert.equal(posts, 3);
  } finally { globalThis.fetch = original; }
});

test('native multipart upload uses configured origin and clears an expired session without retrying', async () => {
  selectApiMode('native'); let cleared = 0; let requests = 0;
  installNativeAccess({
    config: { issuer: 'https://identity.example.edu', clientId: 'pulse', apiOrigin: 'https://api.example.edu', apiAudience: 'pulse', scopes: ['openid'], redirectUri: 'in.ac.cujammu.pulse://oauth/callback' },
    bearer: async () => 'synthetic-access', epoch: () => 1, assertEpoch: value => assert.equal(value, 1), clear: async () => { cleared++; },
    fetcher: async (url, init) => {
      requests++; assert.equal(String(url), 'https://api.example.edu/api/v1/organizer/event-proposals/7/attachments/certificate-template');
      assert.equal(init?.credentials, 'omit'); assert.equal(init?.redirect, 'error');
      assert.equal((init?.headers as Record<string, string>).Authorization, 'Bearer synthetic-access');
      assert.equal((init?.headers as Record<string, string>)['X-CSRF-TOKEN'], undefined);
      assert.equal((init?.body as FormData).get('expectedRevision'), '2');
      return new Response(null, { status: 401 });
    },
  });
  try {
    await assert.rejects(uploadProposalAttachment(7, 'certificate-template', 2, '12345678-1234-4234-8234-123456789abc', new Blob(['png'], { type: 'image/png' })), /session has ended/);
    assert.equal(requests, 1); assert.equal(cleared, 1);
  } finally { selectApiMode('web'); }
});

test('missing or malformed CSRF prevents an attachment POST', async () => {
  const original = globalThis.fetch; let calls = 0;
  globalThis.fetch = async () => { calls++; return Response.json({ token: '', headerName: 'X-CSRF-TOKEN' }); };
  try {
    await assert.rejects(uploadProposalAttachment(7, 'banner', 2, '12345678-1234-4234-8234-123456789abc', new Blob(['png'], { type: 'image/png' })), /Cannot verify/);
    assert.equal(calls, 1);
  } finally { globalThis.fetch = original; }
});
