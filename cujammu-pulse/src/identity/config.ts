export type NativeIdentityConfig = { issuer: string; clientId: string; apiOrigin: string; apiAudience: string; scopes: string[]; redirectUri: string };
export const nativeRedirectUri = 'in.ac.cujammu.pulse://oauth/callback';
export function secureUrl(value: string, originOnly = false): URL {
  const url = new URL(value);
  if (value.length > 1000 || url.protocol !== 'https:' || url.username || url.password || url.hash || url.search || (originOnly && url.pathname !== '/')) throw new Error('Native identity requires an approved HTTPS configuration.');
  if (url.hostname === 'localhost' || url.hostname.endsWith('.invalid') || url.hostname === '127.0.0.1' || url.hostname === '[::1]') throw new Error('Native identity requires a reachable approved host.');
  return url;
}
export function nativeIdentityConfig(env: Record<string, string | undefined>): NativeIdentityConfig | null {
  const keys = ['EXPO_PUBLIC_NATIVE_OIDC_ISSUER', 'EXPO_PUBLIC_NATIVE_OIDC_CLIENT_ID', 'EXPO_PUBLIC_NATIVE_API_ORIGIN', 'EXPO_PUBLIC_NATIVE_API_AUDIENCE', 'EXPO_PUBLIC_NATIVE_OIDC_SCOPES'];
  if (keys.every(key => !env[key])) return null;
  if (keys.some(key => !env[key]?.trim())) throw new Error('Native university sign-in configuration is incomplete.');
  const issuer = env.EXPO_PUBLIC_NATIVE_OIDC_ISSUER!; const apiOrigin = secureUrl(env.EXPO_PUBLIC_NATIVE_API_ORIGIN!, true).origin; secureUrl(issuer);
  const clientId = env.EXPO_PUBLIC_NATIVE_OIDC_CLIENT_ID!; const apiAudience = env.EXPO_PUBLIC_NATIVE_API_AUDIENCE!;
  if (!/^[A-Za-z0-9._:-]{1,200}$/.test(clientId) || !/^[A-Za-z0-9._:/-]{1,200}$/.test(apiAudience)) throw new Error('Native identity client or audience is invalid.');
  const scopes = env.EXPO_PUBLIC_NATIVE_OIDC_SCOPES!.trim().split(/\s+/);
  if (!scopes.includes('openid') || scopes.length > 20 || new Set(scopes).size !== scopes.length || scopes.some(scope => !/^[A-Za-z0-9._:/-]{1,100}$/.test(scope))) throw new Error('Native identity scopes are invalid.');
  return { issuer, clientId, apiOrigin, apiAudience, scopes, redirectUri: nativeRedirectUri };
}
export type ApprovedDiscovery = { authorizationEndpoint: string; tokenEndpoint: string; revocationEndpoint?: string };
export function validateDiscovery(config: NativeIdentityConfig, raw: unknown): ApprovedDiscovery {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('Identity discovery is invalid.');
  const value = raw as Record<string, unknown>;
  if (value.issuer !== config.issuer || !Array.isArray(value.code_challenge_methods_supported) || !value.code_challenge_methods_supported.includes('S256') || !Array.isArray(value.response_types_supported) || !value.response_types_supported.includes('code') || !Array.isArray(value.token_endpoint_auth_methods_supported) || !value.token_endpoint_auth_methods_supported.includes('none')) throw new Error('Identity provider does not support the approved public-client PKCE contract.');
  const endpoint = (raw: unknown) => { if (typeof raw !== 'string' || secureUrl(raw).origin !== secureUrl(config.issuer).origin) throw new Error('Identity endpoint is outside the approved issuer origin.'); return raw; };
  return { authorizationEndpoint: endpoint(value.authorization_endpoint), tokenEndpoint: endpoint(value.token_endpoint), ...(value.revocation_endpoint ? { revocationEndpoint: endpoint(value.revocation_endpoint) } : {}) };
}

