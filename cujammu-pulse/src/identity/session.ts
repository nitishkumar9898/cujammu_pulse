import type { NativeIdentityConfig } from './config.ts';
export type NativeTokens = { accessToken: string; refreshToken: string | null; expiresAt: number; binding: string };
export type TokenStore = { read(): Promise<string | null>; write(value: string): Promise<void>; remove(): Promise<void> };
export type TokenReply = { accessToken: string; refreshToken?: string; expiresIn?: number; tokenType: string };
export const identityBinding = (config: NativeIdentityConfig) => JSON.stringify(config);
function secret(value: unknown): value is string { return typeof value === 'string' && value.length > 0 && value.length <= 8192 && !/\s/.test(value); }
export function tokensFromReply(config: NativeIdentityConfig, value: TokenReply, now: number, previousRefresh?: string): NativeTokens {
  if (!secret(value.accessToken) || value.tokenType.toLowerCase() !== 'bearer' || typeof value.expiresIn !== 'number' || !Number.isInteger(value.expiresIn) || value.expiresIn < 30 || value.expiresIn > 900 || (value.refreshToken != null && !secret(value.refreshToken))) throw new Error('Identity provider returned an unsupported access token.');
  if (previousRefresh && (!value.refreshToken || value.refreshToken === previousRefresh)) throw new Error('Identity provider did not rotate the refresh token. Sign in again.');
  return { accessToken: value.accessToken, refreshToken: value.refreshToken ?? null, expiresAt: now + value.expiresIn * 1000, binding: identityBinding(config) };
}
export class NativeSession {
  private tokens: NativeTokens | null = null;
  private generation = 0;
  private storageQueue: Promise<void> = Promise.resolve();
  private refreshInFlight: Promise<string> | null = null;
  private config: NativeIdentityConfig;
  private store: TokenStore;
  private refresh: (token: string) => Promise<TokenReply>;
  private now: () => number;
  constructor(config: NativeIdentityConfig, store: TokenStore, refresh: (token: string) => Promise<TokenReply>, now: () => number = Date.now) { this.config = config; this.store = store; this.refresh = refresh; this.now = now; }
  epoch() { return this.generation; }
  assertEpoch(epoch: number) { if (epoch !== this.generation) throw new Error('Native account changed. Discard this response.'); }
  private enqueue(work: () => Promise<void>) { const result = this.storageQueue.then(work); this.storageQueue = result.catch(() => {}); return result; }
  async clear() { this.generation++; this.tokens = null; this.refreshInFlight = null; await this.enqueue(() => this.store.remove()); }
  async restore() {
    const epoch = this.generation;
    const raw = await this.store.read(); this.assertEpoch(epoch);
    if (!raw) return;
    try {
      if (raw.length > 20000) throw new Error('Invalid native session');
      const value = JSON.parse(raw) as NativeTokens;
      if (value.binding !== identityBinding(this.config) || !secret(value.accessToken) || !(value.refreshToken == null || secret(value.refreshToken)) || typeof value.expiresAt !== 'number' || !Number.isSafeInteger(value.expiresAt) || value.expiresAt > this.now() + 900000) throw new Error('Invalid native session');
      this.tokens = { accessToken: value.accessToken, refreshToken: value.refreshToken, expiresAt: value.expiresAt, binding: value.binding };
    } catch { await this.clear(); }
  }
  async accept(reply: TokenReply, epoch: number, previousRefresh?: string) {
    const tokens = tokensFromReply(this.config, reply, this.now(), previousRefresh); this.assertEpoch(epoch);
    await this.enqueue(async () => { this.assertEpoch(epoch); await this.store.write(JSON.stringify(tokens)); });
    this.assertEpoch(epoch); this.tokens = tokens;
  }
  async bearer(): Promise<string> {
    if (!this.tokens) throw new Error('Sign in with your university account.');
    if (this.tokens.expiresAt > this.now() + 30000) return this.tokens.accessToken;
    if (this.refreshInFlight) return this.refreshInFlight;
    const refreshToken = this.tokens.refreshToken;
    if (!refreshToken) { await this.clear(); throw new Error('Your university sign-in has expired.'); }
    const epoch = this.generation;
    const operation = (async () => { try { const reply = await this.refresh(refreshToken); await this.accept(reply, epoch, refreshToken); return this.tokens!.accessToken; } catch { if (this.generation === epoch) await this.clear(); throw new Error('University sign-in could not be refreshed. Sign in again.'); } finally { if (this.generation === epoch) this.refreshInFlight = null; } })();
    this.refreshInFlight = operation; return operation;
  }
}

