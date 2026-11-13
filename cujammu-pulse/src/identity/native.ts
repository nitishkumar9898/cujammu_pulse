import * as AuthSession from 'expo-auth-session';
import * as SecureStore from 'expo-secure-store';
import { fetch as expoFetch } from 'expo/fetch';
import { nativeIdentityConfig, validateDiscovery, type ApprovedDiscovery } from './config';
import { NativeSession, type TokenReply } from './session';
import { installNativeAccess } from './transport';
import { identityJson, requestNativeTokens } from './http';

const storageKey = 'pulse.native.session.v1';
const storageOptions = { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY };
const fetcher = expoFetch as unknown as typeof fetch;
let session: NativeSession | null = null;
let discovery: ApprovedDiscovery | null = null;
let initialized: Promise<boolean> | null = null;
let signingIn = false;
// Expo only inlines literal EXPO_PUBLIC property references. These contain public deployment configuration, never secrets.
const configuration = () => nativeIdentityConfig({
  EXPO_PUBLIC_NATIVE_OIDC_ISSUER: process.env.EXPO_PUBLIC_NATIVE_OIDC_ISSUER,
  EXPO_PUBLIC_NATIVE_OIDC_CLIENT_ID: process.env.EXPO_PUBLIC_NATIVE_OIDC_CLIENT_ID,
  EXPO_PUBLIC_NATIVE_API_ORIGIN: process.env.EXPO_PUBLIC_NATIVE_API_ORIGIN,
  EXPO_PUBLIC_NATIVE_API_AUDIENCE: process.env.EXPO_PUBLIC_NATIVE_API_AUDIENCE,
  EXPO_PUBLIC_NATIVE_OIDC_SCOPES: process.env.EXPO_PUBLIC_NATIVE_OIDC_SCOPES,
});
async function approvedDiscovery(): Promise<ApprovedDiscovery> {
  const config = configuration(); if (!config) throw new Error('Native university identity is not configured.');
  if (discovery) return discovery;
  discovery = validateDiscovery(config, await identityJson(`${config.issuer.replace(/\/$/, '')}/.well-known/openid-configuration`, { headers: { Accept: 'application/json' } }, fetcher)); return discovery;
}
export async function initializeNativeIdentity(): Promise<boolean> {
  if (initialized) return initialized;
  const config = configuration(); if (!config) return false;
  initialized = (async () => {
    if (!await SecureStore.isAvailableAsync()) throw new Error('Protected credential storage is unavailable.');
    session = new NativeSession(config, {
      read: () => SecureStore.getItemAsync(storageKey, storageOptions),
      write: value => SecureStore.setItemAsync(storageKey, value, storageOptions),
      remove: () => SecureStore.deleteItemAsync(storageKey, storageOptions),
    }, async refreshToken => requestNativeTokens(config, await approvedDiscovery(), { refreshToken }, fetcher));
    installNativeAccess({ config, fetcher, bearer: () => session!.bearer(), epoch: () => session!.epoch(), assertEpoch: epoch => session!.assertEpoch(epoch), clear: () => session!.clear() });
    await session.restore(); return true;
  })();
  try { return await initialized; } catch { initialized = null; throw new Error('Secure native identity could not be initialized.'); }
}
export async function signInNative(): Promise<void> {
  if (signingIn) throw new Error('A university sign-in request is already active.');
  signingIn = true;
  try {
    if (!await initializeNativeIdentity() || !session) throw new Error('Native university identity is not configured.');
    const config = configuration()!; await session.clear(); const epoch = session.epoch();
    const metadata = await approvedDiscovery(); session.assertEpoch(epoch);
    const request = new AuthSession.AuthRequest({ clientId: config.clientId, redirectUri: config.redirectUri, scopes: config.scopes, responseType: AuthSession.ResponseType.Code, usePKCE: true, codeChallengeMethod: AuthSession.CodeChallengeMethod.S256, prompt: AuthSession.Prompt.Login });
    const result = await request.promptAsync(metadata); session.assertEpoch(epoch);
    if (result.type !== 'success') throw new Error('University sign-in was not completed.');
    if (!result.params.code || result.params.code.length > 2048 || result.params.state !== request.state || !request.codeVerifier || (result.params.iss != null && result.params.iss !== config.issuer)) throw new Error('University sign-in response could not be verified.');
    const token = await requestNativeTokens(config, metadata, { code: result.params.code, verifier: request.codeVerifier }, fetcher);
    await session.accept(token as TokenReply, epoch);
  } catch { throw new Error('University sign-in could not be completed. Retry or contact the university identity team.'); }
  finally { signingIn = false; }
}
