import test from 'node:test';
import assert from 'node:assert/strict';
import { loadNativeProposalMedia } from '../src/events/native-proposal-media.ts';
import { installNativeAccess, selectApiMode, type NativeAccess } from '../src/identity/transport.ts';
import { nativeIdentityConfig } from '../src/identity/config.ts';

const config = nativeIdentityConfig({
  EXPO_PUBLIC_NATIVE_OIDC_ISSUER: 'https://identity.example.edu/realm', EXPO_PUBLIC_NATIVE_OIDC_CLIENT_ID: 'pulse',
  EXPO_PUBLIC_NATIVE_API_ORIGIN: 'https://api.example.edu', EXPO_PUBLIC_NATIVE_API_AUDIENCE: 'pulse-api',
  EXPO_PUBLIC_NATIVE_OIDC_SCOPES: 'openid pulse-api',
})!;
function setup(response: Response | (() => Promise<Response>)) {
  selectApiMode('native'); let epoch = 4; let cleared = 0; let requestUrl = ''; let requestInit: RequestInit | undefined;
  const access: NativeAccess = {
    config, bearer: async () => 'synthetic-bearer', epoch: () => epoch,
    assertEpoch: value => { if (value !== epoch) throw new Error('Stale native session'); },
    clear: async () => { cleared++; epoch++; },
    fetcher: async (input, init) => { requestUrl = String(input); requestInit = init; return typeof response === 'function' ? response() : response; },
  };
  installNativeAccess(access);
  return { access, get requestUrl() { return requestUrl; }, get requestInit() { return requestInit; }, get cleared() { return cleared; }, expire() { epoch++; }, cleanup() { selectApiMode('web'); } };
}
function privateImage(body = new Uint8Array([0x89, 0x50, 0x4e, 0x47])) {
  return new Response(body, { status: 200, headers: { 'Content-Type': 'image/png', 'Cache-Control': 'no-store, private', 'Content-Length': String(body.length), 'X-Content-Type-Options': 'nosniff' } });
}
function trackedResponse(status: number, headers: Record<string, string>) {
  let cancelled = false;
  const body = new ReadableStream<Uint8Array>({ start(controller) { controller.enqueue(new Uint8Array([1])); }, cancel() { cancelled = true; } });
  return { response: new Response(body, { status, headers }), get cancelled() { return cancelled; } };
}

test('native proposal media uses pinned bearer transport and returns only bounded in-memory image data', async () => {
  const state = setup(privateImage());
  try {
    const value = await loadNativeProposalMedia('/api/v1/admin/event-proposals/7/media/banner');
    assert.equal(value, 'data:image/png;base64,iVBORw==');
    assert.equal(state.requestUrl, 'https://api.example.edu/api/v1/admin/event-proposals/7/media/banner');
    assert.equal((state.requestInit?.headers as Record<string, string>).Authorization, 'Bearer synthetic-bearer');
    assert.equal(state.requestInit?.credentials, 'omit'); assert.equal(state.requestInit?.redirect, 'error');
    assert.equal('body' in (state.requestInit ?? {}), false);
  } finally { state.cleanup(); }
});

test('native proposal media rejects untrusted paths, response types, cache policy, declared and actual byte overruns', async () => {
  const wrongType = setup(new Response(new Uint8Array([1]), { status: 200, headers: { 'Content-Type': 'text/html', 'Cache-Control': 'no-store, private' } }));
  try { await assert.rejects(loadNativeProposalMedia('/api/v1/admin/event-proposals/7/media/banner'), /unsupported attachment/); } finally { wrongType.cleanup(); }
  const cacheable = setup(new Response(new Uint8Array([1]), { status: 200, headers: { 'Content-Type': 'image/png', 'Cache-Control': 'private' } }));
  try { await assert.rejects(loadNativeProposalMedia('/api/v1/admin/event-proposals/7/media/banner'), /unsupported attachment/); } finally { cacheable.cleanup(); }
  const tooLarge = setup(new Response(new Uint8Array([1]), { status: 200, headers: { 'Content-Type': 'image/png', 'Cache-Control': 'no-store, private', 'Content-Length': String(5 * 1024 * 1024 + 1) } }));
  try { await assert.rejects(loadNativeProposalMedia('/api/v1/admin/event-proposals/7/media/banner'), /unsupported attachment/); } finally { tooLarge.cleanup(); }
  const streamedTooLarge = setup(new Response(new Uint8Array(5 * 1024 * 1024 + 1), { status: 200, headers: { 'Content-Type': 'image/png', 'Cache-Control': 'no-store, private' } }));
  try { await assert.rejects(loadNativeProposalMedia('/api/v1/admin/event-proposals/7/media/banner'), /invalid attachment size/); } finally { streamedTooLarge.cleanup(); }
  const pathState = setup(privateImage());
  try { await assert.rejects(loadNativeProposalMedia('/api/v1/../uploads/private.png'), /Invalid native API path/); } finally { pathState.cleanup(); }
});

test('native proposal media clears expired sessions and discards responses after epoch revocation', async () => {
  const expired = setup(new Response(null, { status: 401 }));
  try { await assert.rejects(loadNativeProposalMedia('/api/v1/admin/event-proposals/7/media/banner'), /session has ended/); assert.equal(expired.cleared, 1); } finally { expired.cleanup(); }
  let release!: (response: Response) => void;
  const lateBody = trackedResponse(200, { 'Content-Type': 'image/png', 'Cache-Control': 'no-store, private' });
  const late = setup(() => new Promise(resolve => { release = resolve; }));
  const pending = loadNativeProposalMedia('/api/v1/admin/event-proposals/7/media/banner');
  await new Promise(resolve => setImmediate(resolve)); late.expire(); release(lateBody.response);
  try { await assert.rejects(pending, /Stale native session/); assert.equal(lateBody.cancelled, true); } finally { late.cleanup(); }
});

test('native proposal media cancels response bodies before rejecting status and header policy', async () => {
  const denied = trackedResponse(403, { 'Content-Type': 'text/plain' }); const deniedState = setup(denied.response);
  try { await assert.rejects(loadNativeProposalMedia('/api/v1/admin/event-proposals/7/media/banner')); assert.equal(denied.cancelled, true); } finally { deniedState.cleanup(); }
  const invalid = trackedResponse(200, { 'Content-Type': 'text/html', 'Cache-Control': 'no-store, private' }); const invalidState = setup(invalid.response);
  try { await assert.rejects(loadNativeProposalMedia('/api/v1/admin/event-proposals/7/media/banner'), /unsupported attachment/); assert.equal(invalid.cancelled, true); } finally { invalidState.cleanup(); }
});
