import { ApiError, requestJson } from '../api.ts';
import { boundedJson } from '../bounded-json.ts';
import { isNativeApi, nativeAccess, nativeApiUrl } from '../identity/transport.ts';
import type { ProposalMediaKind } from './proposals.ts';
import { decodeProposalAttachmentReceipt } from './proposal-attachments.ts';

export async function uploadProposalAttachment(id: number, kind: ProposalMediaKind, expectedRevision: number, requestKey: string, file: Blob, signal?: AbortSignal) {
  if (!Number.isSafeInteger(id) || id < 1 || !Number.isSafeInteger(expectedRevision) || expectedRevision < 0
      || !['banner', 'certificate-template'].includes(kind) || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(requestKey)
      || !file || file.size < 1 || file.size > 5 * 1024 * 1024 || !['image/png', 'image/jpeg', 'image/webp'].includes(file.type))
    throw new Error('Choose a PNG, JPEG or WebP image up to 5 MiB and reload the current proposal revision.');
  const controller = new AbortController(); const abort = () => controller.abort();
  signal?.addEventListener('abort', abort, { once: true }); if (signal?.aborted) abort();
  const timeout = setTimeout(abort, 30_000);
  try {
    const access = isNativeApi() ? nativeAccess() : null; const epoch = access?.epoch();
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (access) { headers.Authorization = `Bearer ${await access.bearer()}`; access.assertEpoch(epoch!); }
    else {
      const csrf = await requestJson('/api/v1/session/csrf', controller.signal) as Record<string, unknown>;
      if (!csrf || typeof csrf.token !== 'string' || !csrf.token || csrf.headerName !== 'X-CSRF-TOKEN') throw new ApiError(502, 'Cannot verify upload request.');
      headers['X-CSRF-TOKEN'] = csrf.token;
    }
    const form = new FormData();
    form.append('file', file, `attachment.${file.type === 'image/png' ? 'png' : file.type === 'image/jpeg' ? 'jpg' : 'webp'}`);
    form.append('expectedRevision', String(expectedRevision)); form.append('requestKey', requestKey);
    const path = `/api/v1/organizer/event-proposals/${id}/attachments/${kind}`;
    const response = await (access?.fetcher ?? fetch)(access ? nativeApiUrl(access.config, path) : path, {
      method: 'POST', headers, body: form, credentials: access ? 'omit' : 'same-origin', redirect: 'error', signal: controller.signal,
    });
    try {
      if (access) access.assertEpoch(epoch!);
      if (response.status === 401) { if (access) await access.clear(); throw new ApiError(401, 'Your session has ended. Sign in again.'); }
      if (!response.ok) throw new ApiError(response.status, 'Upload could not be confirmed. Reload the proposal or retry the unchanged file with the same request key.');
      if (!response.headers.get('content-type')?.includes('application/json')) throw new ApiError(502, 'Unexpected upload receipt.');
      const receipt = decodeProposalAttachmentReceipt(await boundedJson(response));
      if (access) access.assertEpoch(epoch!);
      if (receipt.id !== id || receipt.kind !== kind || receipt.revision <= expectedRevision) throw new ApiError(502, 'Upload receipt does not match this proposal.');
      return receipt;
    } catch (error) { try { await response.body?.cancel(); } catch {} throw error; }
  } finally { clearTimeout(timeout); signal?.removeEventListener('abort', abort); }
}
