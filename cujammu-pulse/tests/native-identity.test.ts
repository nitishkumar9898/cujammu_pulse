import test from 'node:test';
import assert from 'node:assert/strict';
import { nativeIdentityConfig, validateDiscovery } from '../src/identity/config.ts';
import { NativeSession, identityBinding, tokensFromReply, type TokenReply } from '../src/identity/session.ts';
import { installNativeAccess, nativeApiUrl, selectApiMode } from '../src/identity/transport.ts';
import { requestJson, mutateJson } from '../src/api.ts';
import { identityJson, requestNativeTokens } from '../src/identity/http.ts';

const env = { EXPO_PUBLIC_NATIVE_OIDC_ISSUER: 'https://identity.example.edu/realms/campus', EXPO_PUBLIC_NATIVE_OIDC_CLIENT_ID: 'campus-native', EXPO_PUBLIC_NATIVE_API_ORIGIN: 'https://api.example.edu', EXPO_PUBLIC_NATIVE_API_AUDIENCE: 'campus-api', EXPO_PUBLIC_NATIVE_OIDC_SCOPES: 'openid campus-api offline_access' };
const config = nativeIdentityConfig(env)!;
const reply: TokenReply = { accessToken: 'synthetic-access', refreshToken: 'synthetic-refresh', expiresIn: 300, tokenType: 'Bearer' };
function storage() { let value: string | null = null; return { read: async () => value, write: async (next: string) => { value = next; }, remove: async () => { value = null; } }; }

test('native config denies absent/partial/nonHTTPS/example-invalid/credential and invalid scope inputs', () => {
  assert.equal(nativeIdentityConfig({}), null);
  for (const patch of [{ EXPO_PUBLIC_NATIVE_API_ORIGIN: '' }, { EXPO_PUBLIC_NATIVE_API_ORIGIN: 'http://api.example.edu' }, { EXPO_PUBLIC_NATIVE_API_ORIGIN: 'https://api.example.edu/app' }, { EXPO_PUBLIC_NATIVE_OIDC_ISSUER: 'https://user:password@identity.example.edu' }, { EXPO_PUBLIC_NATIVE_OIDC_ISSUER: 'https://identity.example.invalid' }, { EXPO_PUBLIC_NATIVE_OIDC_SCOPES: 'offline_access' }]) assert.throws(() => nativeIdentityConfig({ ...env, ...patch }));
  assert.equal(config.redirectUri, 'in.ac.cujammu.pulse://oauth/callback');
});
test('discovery requires exact issuer, same HTTPS endpoint origin, public code flow and S256', () => {
  const metadata = { issuer: config.issuer, authorization_endpoint: `${config.issuer}/auth`, token_endpoint: `${config.issuer}/token`, response_types_supported: ['code'], token_endpoint_auth_methods_supported: ['none'], code_challenge_methods_supported: ['S256'] };
  assert.equal(validateDiscovery(config, metadata).tokenEndpoint, `${config.issuer}/token`);
  for (const patch of [{ issuer: 'https://attacker.example.edu' }, { token_endpoint: 'https://attacker.example.edu/token' }, { token_endpoint_auth_methods_supported: ['client_secret_basic'] }, { code_challenge_methods_supported: ['plain'] }]) assert.throws(() => validateDiscovery(config, { ...metadata, ...patch }));
});
test('native token responses require short-lived bearer and actual refresh rotation', () => {
  const value = tokensFromReply(config, reply, 1000); assert.equal(value.expiresAt, 301000); assert.equal(value.binding, identityBinding(config)); assert.equal('idToken' in value, false);
  for (const patch of [{ expiresIn: 3600 }, { expiresIn: undefined }, { tokenType: 'mac' }, { accessToken: 'unsafe token' }]) assert.throws(() => tokensFromReply(config, { ...reply, ...patch }, 1000));
  assert.throws(() => tokensFromReply(config, reply, 1000, reply.refreshToken));
});
test('native refresh deduplicates concurrent requests, rotates protected storage and cannot resurrect after logout', async () => {
  let now = 1000; let calls = 0; let release!: (value: TokenReply) => void;
  const store = storage(); const session = new NativeSession(config, store, () => { calls++; return new Promise(resolve => { release = resolve; }); }, () => now);
  await session.accept(reply, session.epoch()); now = 300000;
  const a = session.bearer(); const b = session.bearer(); assert.equal(calls, 1);
  release({ ...reply, accessToken: 'synthetic-new', refreshToken: 'synthetic-rotated' }); assert.equal(await a, 'synthetic-new'); assert.equal(await b, 'synthetic-new');
  assert.equal(JSON.parse((await store.read())!).refreshToken, 'synthetic-rotated');
  now += 300000; const pending = session.bearer(); await session.clear(); release({ ...reply, accessToken: 'stale-access', refreshToken: 'stale-refresh' }); await assert.rejects(pending);
  assert.equal(await store.read(), null); await assert.rejects(session.bearer());
});
test('native restore rejects configuration changes and in-flight account data is discarded', async () => {
  const store = storage(); const session = new NativeSession(config, store, async () => reply);
  await session.accept(reply, session.epoch()); const oldEpoch = session.epoch(); await session.clear(); assert.throws(() => session.assertEpoch(oldEpoch));
  await session.accept(reply, session.epoch());
  const other = new NativeSession({ ...config, clientId: 'another-client' }, store, async () => reply); await other.restore(); assert.equal(await store.read(), null); await assert.rejects(other.bearer());
});
test('native transport is HTTPS bearer only, skips cookie CSRF, rejects redirects/paths and clears on unauthorized', async () => {
  const original = globalThis.fetch; let epoch = 0; let cleared = 0; let unauthorized = false; const calls: { url: string; init: RequestInit }[] = [];
  selectApiMode('native');
  installNativeAccess({ config, bearer: async () => 'synthetic-access', epoch: () => epoch, assertEpoch: value => { if (value !== epoch) throw new Error('Account changed'); }, clear: async () => { cleared++; epoch++; } });
  globalThis.fetch = async (input, init) => { calls.push({ url: String(input), init: init! }); return new Response(JSON.stringify({ accepted: true }), { status: unauthorized ? 401 : 200, headers: { 'Content-Type': 'application/json' } }); };
  try {
    await mutateJson('/api/v1/cujammu/example', 'POST', { synthetic: true }); assert.equal(calls.length, 1); const call = calls[0]!;
    assert.equal(call.url, 'https://api.example.edu/api/v1/cujammu/example'); assert.equal(call.init.credentials, 'omit'); assert.equal(call.init.redirect, 'error');
    const headers = new Headers(call.init.headers); assert.equal(headers.get('Authorization'), 'Bearer synthetic-access'); assert.equal(headers.has('X-CSRF-TOKEN'), false);
    for (const path of ['/api/v1/../logout', '/api/v1/%2e%2e/logout', 'https://attacker.example.edu/api/v1/x', '/api/v1/\\attacker']) assert.throws(() => nativeApiUrl(config, path));
    unauthorized = true; await assert.rejects(requestJson('/api/v1/session')); assert.equal(cleared, 1);
    const count = calls.length; selectApiMode('web'); selectApiMode('native'); await assert.rejects(requestJson('/api/v1/session')); assert.equal(calls.length, count);
  } finally { globalThis.fetch = original; selectApiMode('web'); }
});
test('native transport drops an old-account response even when server replied successfully', async () => {
  const original = globalThis.fetch; let epoch = 0; let release!: () => void;
  selectApiMode('native'); installNativeAccess({ config, bearer: async () => 'synthetic-access', epoch: () => epoch, assertEpoch: value => { if (value !== epoch) throw new Error('Account changed'); }, clear: async () => { epoch++; } });
  globalThis.fetch = async () => { await new Promise<void>(resolve => { release = resolve; }); return new Response(JSON.stringify({ private: 'old account' }), { headers: { 'Content-Type': 'application/json' } }); };
  try { const pending = requestJson('/api/v1/session'); await new Promise(resolve => setTimeout(resolve, 0)); epoch++; release(); await assert.rejects(pending, /Account changed/); }
  finally { globalThis.fetch = original; selectApiMode('web'); }
});
test('native token HTTP uses bounded redirect-denying form POST and ignores ID tokens', async () => {
  const discovery = { authorizationEndpoint: `${config.issuer}/auth`, tokenEndpoint: `${config.issuer}/token` }; let init: RequestInit | undefined;
  const fetcher: typeof fetch = async (_input, options) => { init = options; return new Response(JSON.stringify({ access_token: 'synthetic-access', refresh_token: 'synthetic-new-refresh', token_type: 'Bearer', expires_in: 300, id_token: 'never-stored' }), { headers: { 'Content-Type': 'application/json' } }); };
  const result = await requestNativeTokens(config, discovery, { code: 'synthetic-code', verifier: 'x'.repeat(43) }, fetcher);
  assert.equal(init!.credentials, 'omit'); assert.equal(init!.redirect, 'error'); assert.equal(init!.method, 'POST');
  const form = new URLSearchParams(String(init!.body)); assert.equal(form.get('grant_type'), 'authorization_code'); assert.equal(form.get('code_verifier'), 'x'.repeat(43)); assert.equal(form.has('client_secret'), false); assert.equal('idToken' in result, false);
  await assert.rejects(requestNativeTokens(config, { ...discovery, tokenEndpoint: 'https://attacker.example.edu/token' }, { refreshToken: 'synthetic-refresh' }, fetcher));
  await assert.rejects(identityJson(discovery.tokenEndpoint, {}, async () => new Response('', { status: 302, headers: { Location: 'https://attacker.example.edu', 'Content-Type': 'application/json' } })));
  await assert.rejects(identityJson(discovery.tokenEndpoint, {}, async () => new Response('x'.repeat(65537), { headers: { 'Content-Type': 'application/json' } })));
  await assert.rejects(identityJson(discovery.tokenEndpoint, {}, async (_input, options) => new Promise((_resolve, reject) => options!.signal!.addEventListener('abort', () => reject(new Error('aborted')), { once: true })), 5));
});
test('native account switch while response body is being decoded rejects private old data', async () => {
  const original = globalThis.fetch; let epoch = 0; let release!: () => void;
  selectApiMode('native'); installNativeAccess({ config, bearer: async () => 'synthetic-access', epoch: () => epoch, assertEpoch: value => { if (value !== epoch) throw new Error('Account changed'); }, clear: async () => { epoch++; } });
  globalThis.fetch = async () => new Response(new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(new TextEncoder().encode('{"private":'));
      release = () => { controller.enqueue(new TextEncoder().encode('"old account"}')); controller.close(); };
    },
  }), { headers: { 'Content-Type': 'application/json' } });
  try { const pending = requestJson('/api/v1/session'); await new Promise(resolve => setTimeout(resolve, 0)); epoch++; release(); await assert.rejects(pending, /Account changed/); }
  finally { globalThis.fetch = original; selectApiMode('web'); }
});
