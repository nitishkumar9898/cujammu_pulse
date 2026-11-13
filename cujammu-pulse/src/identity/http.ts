import { secureUrl, type NativeIdentityConfig, type ApprovedDiscovery } from './config.ts';
import { tokensFromReply, type TokenReply } from './session.ts';

export async function identityJson(url: string, options: RequestInit, fetcher: typeof fetch, timeoutMs = 12000): Promise<unknown> {
  secureUrl(url); if (timeoutMs <= 0 || timeoutMs > 12000) throw new Error('Invalid identity timeout');
  const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), timeoutMs); let reader: ReadableStreamDefaultReader<Uint8Array> | undefined;
  try {
    const response = await fetcher(url, { ...options, credentials: 'omit', redirect: 'error', cache: 'no-store', signal: controller.signal });
    if (!response.ok || response.redirected || (response.url && response.url !== url) || !response.headers.get('content-type')?.includes('application/json')) throw new Error('Identity service returned an unsupported response.');
    const declared = response.headers.get('content-length'); if (declared != null && (!/^\d+$/.test(declared) || Number(declared) > 65536)) throw new Error('Identity response is too large.');
    if (!response.body?.getReader) throw new Error('Bounded identity response streaming is unavailable.');
    reader = response.body.getReader(); const chunks: Uint8Array[] = []; let total = 0;
    while (true) { const chunk = await reader.read(); if (controller.signal.aborted) throw new Error('Identity request timed out.'); if (chunk.done) break; total += chunk.value.byteLength; if (total > 65536) { controller.abort(); throw new Error('Identity response is too large.'); } chunks.push(chunk.value); }
    const bytes = new Uint8Array(total); let offset = 0; for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch { throw new Error('University identity service could not complete this request.'); }
  finally { clearTimeout(timeout); await reader?.cancel().catch(() => {}); }
}
export async function requestNativeTokens(config: NativeIdentityConfig, discovery: ApprovedDiscovery, grant: { code: string; verifier: string } | { refreshToken: string }, fetcher: typeof fetch): Promise<TokenReply> {
  if (secureUrl(discovery.tokenEndpoint).origin !== secureUrl(config.issuer).origin) throw new Error('Identity token endpoint is outside the approved issuer origin.');
  const form = new URLSearchParams({ client_id: config.clientId });
  if ('code' in grant) { if (!grant.code || grant.code.length > 2048 || !/^[A-Za-z0-9._~-]{43,128}$/.test(grant.verifier)) throw new Error('Invalid authorization code exchange.'); form.set('grant_type', 'authorization_code'); form.set('code', grant.code); form.set('redirect_uri', config.redirectUri); form.set('code_verifier', grant.verifier); }
  else { if (!grant.refreshToken || grant.refreshToken.length > 8192 || /\s/.test(grant.refreshToken)) throw new Error('Invalid refresh grant.'); form.set('grant_type', 'refresh_token'); form.set('refresh_token', grant.refreshToken); }
  const raw = await identityJson(discovery.tokenEndpoint, { method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/x-www-form-urlencoded' }, body: form.toString() }, fetcher);
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('Invalid token response'); const value = raw as Record<string, unknown>;
  if (typeof value.access_token !== 'string' || typeof value.token_type !== 'string' || typeof value.expires_in !== 'number' || (value.refresh_token !== undefined && typeof value.refresh_token !== 'string')) throw new Error('Invalid token response');
  const reply: TokenReply = { accessToken: value.access_token, tokenType: value.token_type, expiresIn: value.expires_in, ...(value.refresh_token !== undefined ? { refreshToken: value.refresh_token as string } : {}) };
  tokensFromReply(config, reply, Date.now(), 'refreshToken' in grant ? grant.refreshToken : undefined); return reply;
}
