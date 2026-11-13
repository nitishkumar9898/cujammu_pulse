/** Allowlist decoders reject contract drift instead of rendering raw server entities. */
export type Session = { id: number; name: string; role: string };
export type Department = { id: number; code: string; name: string; building: string };
export type Facility = { id: number; name: string; facilityType: string; location: string; capacity: number | null };
export type FacilitySlot = { startTime: string; endTime: string };
export type FacilitySuggestions = { facilityId: number; facilityName: string; durationMinutes: number; searchedFrom: string; searchedUntil: string; computedAt: string; status: 'ADVISORY_ONLY'; checkedConstraints: string[]; unknownConstraints: string[]; items: FacilitySlot[] };
export type FacilityBookingReceipt = { id: number; facilityId: number; version: number; status: 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED'; startTime: string; endTime: string };
export type PendingFacilityBooking = { id: number; version: number; facilityId: number; facilityName: string; title: string; requesterName: string; requesterDepartment: string; startTime: string; endTime: string; requestedMics: number; requestedProjector: boolean };
export type PendingFacilityBookingPage = { items: PendingFacilityBooking[]; page: number; hasNext: boolean };
export type MyFacilityBooking = { id: number; version: number; facilityId: number; facilityName: string; title: string; status: 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED' | 'PREEMPTED'; startTime: string; endTime: string };
export type MyFacilityBookingPage = { items: MyFacilityBooking[]; page: number; hasNext: boolean };
export type FeatureFlags = Readonly<Record<string, boolean>>;

function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid API response');
  return value as Record<string, unknown>;
}
function text(value: unknown): string {
  if (typeof value !== 'string' || value.length > 500) throw new Error('Invalid API response');
  return value;
}
function id(value: unknown): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 1) throw new Error('Invalid API response');
  return value;
}
export function decodeSession(value: unknown): Session {
  const v = record(value);
  return { id: id(v.id), name: text(v.name), role: text(v.role) };
}
export function decodeFlags(value: unknown): FeatureFlags {
  const v = record(value);
  if (Object.keys(v).length > 500) throw new Error('Invalid feature configuration');
  const output: Record<string, boolean> = Object.create(null);
  for (const [key, enabled] of Object.entries(v)) {
    if (!/^[A-Z][A-Z0-9_]{1,100}$/.test(key) || typeof enabled !== 'boolean') throw new Error('Invalid feature configuration');
    output[key] = enabled;
  }
  return output;
}
function list(value: unknown): unknown[] {
  if (!Array.isArray(value) || value.length > 1000) throw new Error('Invalid catalogue response');
  return value;
}
export function decodeDepartments(value: unknown): Department[] {
  return list(value).map(value => {
    const v = record(value);
    return { id: id(v.id), code: text(v.code), name: text(v.name), building: v.building == null ? '' : text(v.building) };
  });
}
export function decodeFacilities(value: unknown): Facility[] {
  return list(value).map(value => {
    const v = record(value);
    if (v.capacity != null && (typeof v.capacity !== 'number' || !Number.isSafeInteger(v.capacity) || v.capacity < 0)) throw new Error('Invalid capacity');
    return { id: id(v.id), name: text(v.name), facilityType: text(v.facilityType), location: v.location == null ? '' : text(v.location), capacity: v.capacity as number | null ?? null };
  });
}

export function decodeFacilitySuggestions(value: unknown): FacilitySuggestions {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid availability response');
  const v = value as Record<string, unknown>;
  if (!Number.isSafeInteger(v.facilityId) || (v.facilityId as number) < 1
      || typeof v.facilityName !== 'string' || !Number.isInteger(v.durationMinutes)
      || (v.durationMinutes as number) < 15 || (v.durationMinutes as number) > 720
      || v.status !== 'ADVISORY_ONLY' || !Array.isArray(v.items) || v.items.length > 20
      || !Array.isArray(v.checkedConstraints) || !Array.isArray(v.unknownConstraints)
      || v.checkedConstraints.length > 10 || v.unknownConstraints.length > 10
      || ![v.searchedFrom, v.searchedUntil, v.computedAt].every(validLocalDateTime)) {
    throw new Error('Invalid availability response');
  }
  const items = v.items.map((item: unknown) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) throw new Error('Invalid suggested slot');
    const slot = item as Record<string, unknown>;
    if (!validLocalDateTime(slot.startTime) || !validLocalDateTime(slot.endTime)
        || new Date(slot.startTime).getTime() >= new Date(slot.endTime).getTime()) throw new Error('Invalid suggested slot');
    return { startTime: slot.startTime, endTime: slot.endTime };
  });
  const strings = (items: unknown[]) => items.map(item => {
    if (typeof item !== 'string' || item.length > 120) throw new Error('Invalid availability constraint');
    return item;
  });
  return { facilityId: v.facilityId as number, facilityName: v.facilityName, durationMinutes: v.durationMinutes as number,
    searchedFrom: v.searchedFrom as string, searchedUntil: v.searchedUntil as string, computedAt: v.computedAt as string,
    status: 'ADVISORY_ONLY', checkedConstraints: strings(v.checkedConstraints), unknownConstraints: strings(v.unknownConstraints), items };
}

export function decodeFacilityBookingReceipt(value: unknown): FacilityBookingReceipt {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid booking response');
  const v = value as Record<string, unknown>;
    if (!Number.isSafeInteger(v.id) || (v.id as number) < 1 || !Number.isSafeInteger(v.facilityId)
        || (v.facilityId as number) < 1 || !Number.isSafeInteger(v.version) || (v.version as number) < 0
        || !['PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED'].includes(String(v.status))
      || !validLocalDateTime(v.startTime) || !validLocalDateTime(v.endTime)
      || new Date(v.startTime).getTime() >= new Date(v.endTime).getTime()) throw new Error('Invalid booking response');
  return { id: v.id as number, facilityId: v.facilityId as number, version: v.version as number, status: v.status as FacilityBookingReceipt['status'], startTime: v.startTime, endTime: v.endTime };
}

export function decodePendingFacilityBookings(value: unknown): PendingFacilityBookingPage {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid pending booking page');
  const root = value as Record<string, unknown>;
  if (!Array.isArray(root.items) || root.items.length > 50 || !Number.isSafeInteger(root.page)
      || (root.page as number) < 0 || typeof root.hasNext !== 'boolean') throw new Error('Invalid pending booking page');
  const items = root.items.map((item: unknown) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) throw new Error('Invalid pending booking');
    const v = item as Record<string, unknown>;
    if (![v.id, v.version, v.facilityId].every(Number.isSafeInteger)
        || (v.id as number) < 1 || (v.version as number) < 0 || (v.facilityId as number) < 1
        || !['facilityName', 'title', 'requesterName', 'requesterDepartment'].every(key => typeof v[key] === 'string' && (v[key] as string).length <= 200)
        || !validLocalDateTime(v.startTime) || !validLocalDateTime(v.endTime)
        || new Date(v.startTime).getTime() >= new Date(v.endTime).getTime()
        || typeof v.requestedMics !== 'number' || !Number.isInteger(v.requestedMics) || v.requestedMics < 0 || v.requestedMics > 100
        || typeof v.requestedProjector !== 'boolean') throw new Error('Invalid pending booking');
    return { id: v.id as number, version: v.version as number, facilityId: v.facilityId as number,
      facilityName: v.facilityName as string, title: v.title as string, requesterName: v.requesterName as string,
      requesterDepartment: v.requesterDepartment as string, startTime: v.startTime, endTime: v.endTime,
      requestedMics: v.requestedMics, requestedProjector: v.requestedProjector };
  });
  return { items, page: root.page as number, hasNext: root.hasNext };
}

export function decodeMyFacilityBookings(value: unknown): MyFacilityBookingPage {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid booking history');
  const root = value as Record<string, unknown>;
  if (!Array.isArray(root.items) || root.items.length > 30 || !Number.isSafeInteger(root.page)
      || (root.page as number) < 0 || typeof root.hasNext !== 'boolean') throw new Error('Invalid booking history');
  const items = root.items.map((item: unknown) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) throw new Error('Invalid booking history item');
    const v = item as Record<string, unknown>;
    if (![v.id, v.version, v.facilityId].every(Number.isSafeInteger) || (v.id as number) < 1
        || (v.version as number) < 0 || (v.facilityId as number) < 1
        || !['facilityName', 'title'].every(key => typeof v[key] === 'string' && (v[key] as string).length <= 200)
        || !['PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'PREEMPTED'].includes(String(v.status))
        || !validLocalDateTime(v.startTime) || !validLocalDateTime(v.endTime)
        || new Date(v.startTime).getTime() >= new Date(v.endTime).getTime()) throw new Error('Invalid booking history item');
    return { id: v.id as number, version: v.version as number, facilityId: v.facilityId as number,
      facilityName: v.facilityName as string, title: v.title as string, status: v.status as MyFacilityBooking['status'],
      startTime: v.startTime as string, endTime: v.endTime as string };
  });
  return { items, page: root.page as number, hasNext: root.hasNext };
}

function validLocalDateTime(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?$/.test(value)
    && Number.isFinite(new Date(value).getTime());
}
