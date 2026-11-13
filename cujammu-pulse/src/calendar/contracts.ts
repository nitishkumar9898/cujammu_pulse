import { campusDate } from './date.ts';
export type CalendarEntry = { registrationId: number; eventId: number; title: string; eventDate: string; venue: string; status: string; eventEndsAt: string | null; eventStatus: string | null; lifecycleVersion: number | null };
export type CalendarPage = { items: CalendarEntry[]; page: number; hasMore: boolean };
export type CalendarClashCheck = { eventId: number; startsAt: string; endsAt: string | null; lifecycleVersion: number; checkedAt: string; items: { entry: CalendarEntry; assessment: 'OVERLAP' | 'UNKNOWN_DURATION' }[]; hasMore: boolean };

function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid calendar response');
  return value as Record<string, unknown>;
}

function entries(value: unknown): CalendarEntry[] {
  if (!Array.isArray(value) || value.length > 50) throw new Error('Invalid calendar response');
  return value.map(raw => {
    const entry = object(raw);
    if (typeof entry.registrationId !== 'number' || !Number.isSafeInteger(entry.registrationId) || entry.registrationId < 1
      || typeof entry.eventId !== 'number' || !Number.isSafeInteger(entry.eventId) || entry.eventId < 1
      || typeof entry.title !== 'string' || entry.title.length > 200
      || typeof entry.eventDate !== 'string' || !Number.isFinite(campusDate(entry.eventDate).getTime())
      || typeof entry.venue !== 'string' || entry.venue.length > 200
      || typeof entry.status !== 'string' || entry.status.length > 40) throw new Error('Invalid calendar entry');
    const eventEndsAt = entry.eventEndsAt ?? null;
    const eventStatus = entry.eventStatus ?? null;
    const lifecycleVersion = entry.lifecycleVersion ?? null;
    if (eventEndsAt !== null && (typeof eventEndsAt !== 'string' || !Number.isFinite(campusDate(eventEndsAt).getTime()) || campusDate(eventEndsAt) <= campusDate(entry.eventDate))
      || eventStatus !== null && (typeof eventStatus !== 'string' || !['PENDING', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED'].includes(eventStatus))
      || lifecycleVersion !== null && (typeof lifecycleVersion !== 'number' || !Number.isSafeInteger(lifecycleVersion) || lifecycleVersion < 0)) throw new Error('Invalid calendar schedule');
    return { registrationId: entry.registrationId, eventId: entry.eventId, title: entry.title,
      eventDate: entry.eventDate, venue: entry.venue, status: entry.status, eventEndsAt, eventStatus, lifecycleVersion };
  });
}

export function decodeCalendarPage(value: unknown): CalendarPage {
  const page = object(value);
  if (typeof page.page !== 'number' || !Number.isInteger(page.page) || page.page < 0 || page.page > 10000
    || typeof page.hasMore !== 'boolean') throw new Error('Invalid calendar page');
  return { items: entries(page.items), page: page.page, hasMore: page.hasMore };
}

/** Backwards-compatible bounded projection for consumers that only need page zero. */
export const decodeCalendar = (value: unknown): CalendarEntry[] =>
  Array.isArray(value) ? entries(value) : decodeCalendarPage(value).items;

export function decodeCalendarClashes(value: unknown): CalendarClashCheck {
  const result = object(value);
  if (typeof result.eventId !== 'number' || !Number.isSafeInteger(result.eventId) || result.eventId < 1
    || typeof result.startsAt !== 'string' || !Number.isFinite(campusDate(result.startsAt).getTime())
    || result.endsAt !== null && (typeof result.endsAt !== 'string' || !Number.isFinite(campusDate(result.endsAt).getTime()) || campusDate(result.endsAt) <= campusDate(result.startsAt))
    || typeof result.lifecycleVersion !== 'number' || !Number.isSafeInteger(result.lifecycleVersion) || result.lifecycleVersion < 0
    || typeof result.checkedAt !== 'string' || !Number.isFinite(Date.parse(result.checkedAt))
    || typeof result.hasMore !== 'boolean' || !Array.isArray(result.items) || result.items.length > 50) throw new Error('Invalid clash check');
  const items = result.items.map(raw => {
    const item = object(raw);
    const entry = entries([item.entry])[0]!;
    if (item.assessment !== 'OVERLAP' && item.assessment !== 'UNKNOWN_DURATION') throw new Error('Invalid clash assessment');
    if (item.assessment === 'OVERLAP' && (result.endsAt === null || entry.eventEndsAt === null
      || campusDate(entry.eventDate) >= campusDate(result.endsAt as string) || campusDate(entry.eventEndsAt) <= campusDate(result.startsAt as string))) throw new Error('Invalid overlap');
    return { entry, assessment: item.assessment } as CalendarClashCheck['items'][number];
  });
  return { eventId: result.eventId, startsAt: result.startsAt, endsAt: result.endsAt as string | null,
    lifecycleVersion: result.lifecycleVersion, checkedAt: result.checkedAt, items, hasMore: result.hasMore };
}
