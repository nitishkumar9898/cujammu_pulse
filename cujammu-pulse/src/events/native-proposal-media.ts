import { ApiError } from '../api.ts';
import { isNativeApi, nativeAccess, nativeApiUrl } from '../identity/transport.ts';

const MAX_BYTES = 5 * 1024 * 1024;
const TIMEOUT_MS = 12_000;
const ALLOWED_TYPES = new Set(['image/png', 'image/jpeg', 'image/webp']);

function base64(bytes: Uint8Array): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  const parts: string[] = []; let result = '';
  for (let index = 0; index < bytes.length; index += 3) {
    const first = bytes[index]!;
    const hasSecond = index + 1 < bytes.length;
    const hasThird = index + 2 < bytes.length;
    const second = hasSecond ? bytes[index + 1]! : 0;
    const third = hasThird ? bytes[index + 2]! : 0;
    result += alphabet[first >> 2]!;
    result += alphabet[((first & 3) << 4) | (second >> 4)]!;
    result += hasSecond ? alphabet[((second & 15) << 2) | (third >> 6)]! : '=';
    result += hasThird ? alphabet[third & 63]! : '=';
    if (result.length >= 16_384) { parts.push(result); result = ''; }
  }
  if (result) parts.push(result);
  return parts.join('');
}

async function boundedBytes(response: Response): Promise<Uint8Array> {
  const reader = response.body?.getReader();
  if (!reader) throw new ApiError(502, 'The service returned an unsupported attachment.');
  const chunks: Uint8Array[] = []; let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > MAX_BYTES) { await reader.cancel(); throw new ApiError(502, 'The service returned an invalid attachment size.'); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(total); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return bytes;
}

async function cancelBody(response: Response): Promise<void> {
  try { await response.body?.cancel(); } catch { /* The transport may already have closed the stream. */ }
}

/** Fetch protected proposal media through the same pinned-origin bearer transport as API JSON.
 * The returned data URI lives only in component memory; it is never written to disk or logged.
 */
export async function loadNativeProposalMedia(path: string, signal?: AbortSignal): Promise<string> {
  if (!isNativeApi()) throw new Error('Native media delivery is unavailable.');
  const access = nativeAccess();
  const epoch = access.epoch();
  let token: string;
  try { token = await access.bearer(); }
  catch { throw new ApiError(401, 'Your session has ended. Sign in again.'); }
  access.assertEpoch(epoch);
  const controller = new AbortController();
  const abort = () => controller.abort();
  if (signal?.aborted) controller.abort(); else signal?.addEventListener('abort', abort, { once: true });
  const timeout = setTimeout(abort, TIMEOUT_MS);
  let response: Response | null = null;
  try {
    response = await (access.fetcher ?? fetch)(nativeApiUrl(access.config, path), {
      method: 'GET', credentials: 'omit', redirect: 'error', signal: controller.signal,
      headers: { Accept: 'image/png, image/jpeg, image/webp', Authorization: `Bearer ${token}` },
    });
    access.assertEpoch(epoch);
    if (response.status === 401) { await access.clear(); throw new ApiError(401, 'Your session has ended. Sign in again.'); }
    if (!response.ok) throw new ApiError(response.status, 'This attachment is unavailable, expired, or no longer authorized for this account.');
    const type = response.headers.get('content-type')?.split(';', 1)[0]?.trim().toLowerCase();
    const cacheDirectives = (response.headers.get('cache-control') ?? '').toLowerCase().split(',').map(value => value.trim().split('=', 1)[0]);
    const declaredLength = response.headers.get('content-length');
    if (!type || !ALLOWED_TYPES.has(type) || !cacheDirectives.includes('no-store') || !cacheDirectives.includes('private')
        || (declaredLength !== null && (!/^\d{1,8}$/.test(declaredLength) || Number(declaredLength) > MAX_BYTES))) {
      throw new ApiError(502, 'The service returned an unsupported attachment.');
    }
    const bytes = await boundedBytes(response);
    access.assertEpoch(epoch);
    if (!bytes.length || bytes.length > MAX_BYTES || (declaredLength !== null && bytes.length !== Number(declaredLength)))
      throw new ApiError(502, 'The service returned an invalid attachment size.');
    return `data:${type};base64,${base64(bytes)}`;
  } catch (error) {
    if (response) await cancelBody(response);
    if (signal?.aborted) throw error;
    if (controller.signal.aborted) throw new ApiError(408, 'Attachment preview timed out. Retry from the current proposal.');
    throw error;
  } finally { clearTimeout(timeout); signal?.removeEventListener('abort', abort); }
}
