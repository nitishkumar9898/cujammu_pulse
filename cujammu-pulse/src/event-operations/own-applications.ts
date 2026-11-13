export type OwnVolunteerApplication = { id: number; revision: number; status: 'APPLIED' | 'ACCEPTED' | 'REJECTED' | 'REMOVED'; eventId: number; eventTitle: string; eventStatus: string; eventDate: string; appliedAt: string; decidedAt: string | null };
export type OwnVolunteerApplicationPage = { items: OwnVolunteerApplication[]; page: number; size: number; hasNext: boolean };
const integer = (value: unknown, min: number): number => { if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < min) throw new Error('Invalid application response'); return value; };
const text = (value: unknown, max: number): string => { if (typeof value !== 'string' || !value.trim() || value.length > max) throw new Error('Invalid application response'); return value; };
const date = (value: unknown): string => { const result = text(value, 40); if (!Number.isFinite(Date.parse(result))) throw new Error('Invalid application date'); return result; };
export function decodeOwnVolunteerApplications(value: unknown): OwnVolunteerApplicationPage {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid application page');
  const v = value as Record<string, unknown>;
  const page = integer(v.page, 0); const size = integer(v.size, 1);
  if (size > 50 || typeof v.hasNext !== 'boolean' || !Array.isArray(v.items) || v.items.length > size) throw new Error('Invalid application page');
  const items = v.items.map((raw): OwnVolunteerApplication => {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('Invalid application');
    const item = raw as Record<string, unknown>;
    if (!['APPLIED', 'ACCEPTED', 'REJECTED', 'REMOVED'].includes(String(item.status))) throw new Error('Unsupported application status');
    return { id: integer(item.id, 1), revision: integer(item.revision, 0), status: item.status as OwnVolunteerApplication['status'], eventId: integer(item.eventId, 1), eventTitle: text(item.eventTitle, 200), eventStatus: text(item.eventStatus, 80), eventDate: date(item.eventDate), appliedAt: date(item.appliedAt), decidedAt: item.decidedAt === null ? null : date(item.decidedAt) };
  });
  if (new Set(items.map(item => item.id)).size !== items.length) throw new Error('Duplicate application records');
  return { items, page, size, hasNext: v.hasNext };
}
