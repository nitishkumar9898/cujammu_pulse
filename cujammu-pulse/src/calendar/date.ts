export const CAMPUS_TIME_ZONE = 'Asia/Kolkata';

/** API LocalDateTime fields use campus time, independent of the viewer's device zone. */
export function campusDate(value: string): Date {
  const hasOffset = /(?:Z|[+-]\d{2}:\d{2})$/i.test(value);
  return new Date(hasOffset ? value : `${value}+05:30`);
}

export function campusDateTime(value: string): string {
  const date = campusDate(value);
  return Number.isNaN(date.getTime()) ? 'Time unavailable' : new Intl.DateTimeFormat(undefined, {
    timeZone: CAMPUS_TIME_ZONE, year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
  }).format(date);
}
