import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { ApiError } from '../api.ts';
import { isNativeApi, nativeAccess, nativeApiUrl } from '../identity/transport.ts';

const MAX_EXPORT_BYTES = 20 * 1024 * 1024;
const exportTypes = {
  csv: { mime: 'text/csv', extension: 'csv' },
  xlsx: { mime: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', extension: 'xlsx' },
} as const;
export type AttendanceExportKind = 'report-csv' | 'report-xlsx' | 'scan-log-csv';

export async function shareAttendanceExport(eventId: number, kind: AttendanceExportKind, signal?: AbortSignal) {
  if (!Number.isSafeInteger(eventId) || eventId < 1 || !isNativeApi()) throw new Error('Native private report delivery is unavailable.');
  if (!(await Sharing.isAvailableAsync())) throw new Error('Secure report sharing is unavailable on this device.');
  const access = nativeAccess(); const epoch = access.epoch();
  const controller = new AbortController(); const abort = () => controller.abort();
  signal?.addEventListener('abort', abort, { once: true }); if (signal?.aborted) abort();
  const timeout = setTimeout(abort, 60_000);
  let temporary: File | null = null;
  try {
    const resource = kind === 'report-xlsx' ? exportTypes.xlsx : exportTypes.csv;
    const suffix = kind === 'scan-log-csv' ? 'scan-log' : 'attendance';
    const path = kind === 'scan-log-csv' ? `/api/v1/cujammu/events/${eventId}/attendance/scan-log.csv` : `/api/v1/cujammu/events/${eventId}/attendance/report.${resource.extension}`;
    const response = await (access.fetcher ?? fetch)(nativeApiUrl(access.config, path), {
      method: 'GET', headers: { Accept: resource.mime, Authorization: `Bearer ${await access.bearer()}` },
      credentials: 'omit', redirect: 'error', cache: 'no-store', signal: controller.signal,
    });
    if (response.status === 401) { await access.clear(); throw new ApiError(401, 'Your session has ended. Sign in again.'); }
    if (!response.ok) throw new ApiError(response.status, 'The report could not be downloaded. Check access and try again.');
    const contentType = response.headers.get('content-type')?.split(';')[0]?.trim().toLowerCase();
    if (contentType !== resource.mime.toLowerCase()) throw new ApiError(502, 'The report format could not be verified.');
    const declared = response.headers.get('content-length');
    if (declared !== null && (!/^\d+$/.test(declared) || Number(declared) < 1 || Number(declared) > MAX_EXPORT_BYTES)) throw new ApiError(413, 'The report is too large to share securely.');
    if (!response.body) throw new ApiError(502, 'The report stream is unavailable.');
    const reader = response.body.getReader(); const chunks: Uint8Array[] = []; let total = 0;
    try {
      while (true) {
        const part = await reader.read(); if (part.done) break;
        total += part.value.byteLength; if (total > MAX_EXPORT_BYTES) throw new ApiError(413, 'The report is too large to share securely.');
        chunks.push(part.value);
      }
    } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
    if (!total) throw new ApiError(502, 'The report is empty.');
    access.assertEpoch(epoch);
    const bytes = new Uint8Array(total); let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
    const filename = `event-${eventId}-${suffix}-${Date.now()}.${resource.extension}`;
    temporary = new File(Paths.cache, filename); temporary.write(bytes);
    access.assertEpoch(epoch);
    await Sharing.shareAsync(temporary.uri, { mimeType: resource.mime, dialogTitle: 'Share attendance report', UTI: kind === 'report-xlsx' ? 'org.openxmlformats.spreadsheetml.sheet' : 'public.comma-separated-values-text' });
    access.assertEpoch(epoch);
  } finally {
    clearTimeout(timeout); signal?.removeEventListener('abort', abort);
    try { if (temporary?.exists) temporary.delete(); } catch { /* cache cleanup is best-effort; OS share targets may retain an explicit copy */ }
  }
}
