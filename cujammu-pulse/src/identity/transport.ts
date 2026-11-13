import type { NativeIdentityConfig } from './config.ts';
export type NativeAccess = { config: NativeIdentityConfig; fetcher?: typeof fetch; bearer(): Promise<string>; epoch(): number; assertEpoch(epoch: number): void; clear(): Promise<void> };
let mode: 'web' | 'native' = 'web'; let native: NativeAccess | null = null;
export function selectApiMode(value: 'web' | 'native') { mode = value; if (value === 'web') native = null; }
export function installNativeAccess(value: NativeAccess) { if (mode !== 'native') throw new Error('Native bearer transport cannot replace browser sessions.'); native = value; }
export function isNativeApi() { return mode === 'native'; }
export function nativeAccess(): NativeAccess { if (mode !== 'native' || !native) throw new Error('Secure native sign-in is not configured.'); return native; }
export function nativeApiUrl(config: NativeIdentityConfig, path: string): string {
  if (!path.startsWith('/api/v1/') || path.includes('://') || path.includes('\\') || path.includes('#') || path.includes('..') || /%(?:2e|2f|5c)/i.test(path)) throw new Error('Invalid native API path.');
  const url = new URL(path, config.apiOrigin); if (url.origin !== config.apiOrigin || !url.pathname.startsWith('/api/v1/')) throw new Error('Invalid native API origin.'); return url.toString();
}
