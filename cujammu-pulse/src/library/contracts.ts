export type LibrarySession = { id: number; sessionType: 'STUDY' | 'BOOK_ISSUE_RETURN'; entryTime: string; exitTime?: string; durationSeconds?: number; durationFormatted?: string; status: string };
function object(value: unknown): Record<string, unknown> { if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid library response'); return value as Record<string, unknown>; }
function time(value: unknown): string { if (typeof value !== 'string' || !Number.isFinite(new Date(value).getTime())) throw new Error('Invalid library response'); return value; }
export function decodeLibrarySession(value: unknown): LibrarySession {
  const v = object(value);
  if (!Number.isSafeInteger(v.id) || (v.id as number) < 1 || !['STUDY', 'BOOK_ISSUE_RETURN'].includes(String(v.sessionType)) || typeof v.status !== 'string' || v.status.length > 32) throw new Error('Invalid library response');
  const result: LibrarySession = { id: v.id as number, sessionType: v.sessionType as LibrarySession['sessionType'], entryTime: time(v.entryTime), status: v.status as string };
  if (v.exitTime != null) result.exitTime = time(v.exitTime);
  if (v.durationSeconds != null && Number.isSafeInteger(v.durationSeconds) && (v.durationSeconds as number) >= 0) result.durationSeconds = v.durationSeconds as number;
  if (v.durationFormatted != null && typeof v.durationFormatted === 'string' && v.durationFormatted.length <= 40) result.durationFormatted = v.durationFormatted;
  return result;
}
export function decodeLibraryHistory(value: unknown): LibrarySession[] {
  if (!Array.isArray(value) || value.length > 50) throw new Error('Invalid library history response');
  return value.map(decodeLibrarySession);
}
