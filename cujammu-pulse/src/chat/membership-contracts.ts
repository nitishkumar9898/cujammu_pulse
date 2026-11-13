export type DepartmentChatGroup = { id: number; name: string; department: string };
export type ChatMembership = { userId: number; name: string; active: boolean; revision: number };
export type ChatMembershipPage = { items: ChatMembership[]; page: number; hasMore: boolean };
export type ChatMembershipCommand = { expectedRevision: number | null; active: boolean; sourceReference: string; reason: string };

function invalid(): never { throw new Error('Invalid chat membership response'); }
function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return invalid();
  return value as Record<string, unknown>;
}
function integer(value: unknown, min: number): number {
  if (!Number.isSafeInteger(value) || (value as number) < min) return invalid();
  return value as number;
}
function text(value: unknown, max: number): string {
  if (typeof value !== 'string' || !value.trim() || value.length > max) return invalid();
  return value;
}
function list(value: unknown, max: number): unknown[] {
  if (!Array.isArray(value) || value.length > max) return invalid();
  return value;
}
function unique<T>(items: T[], key: (item: T) => number): T[] {
  if (new Set(items.map(key)).size !== items.length) return invalid();
  return items;
}
export function decodeDepartmentChatGroups(value: unknown): DepartmentChatGroup[] {
  return unique(list(record(value).items, 100).map(raw => {
    const v = record(raw);
    return { id: integer(v.id, 1), name: text(v.name, 200), department: text(v.department, 160) };
  }), item => item.id);
}
export function decodeChatMembership(value: unknown): ChatMembership {
  const v = record(value);
  if (typeof v.active !== 'boolean') return invalid();
  return { userId: integer(v.userId, 1), name: text(v.name, 160), active: v.active, revision: integer(v.revision, 0) };
}
export function decodeChatMembershipPage(value: unknown): ChatMembershipPage {
  const v = record(value);
  if (typeof v.hasMore !== 'boolean') return invalid();
  return { items: unique(list(v.items, 50).map(decodeChatMembership), item => item.userId), page: integer(v.page, 0), hasMore: v.hasMore };
}

/** Reject ambiguous IDs instead of converting a rounded or exponential value. */
export function parseMembershipUserId(value: string): number | null {
  if (!/^[1-9]\d{0,15}$/.test(value.trim())) return null;
  const result = Number(value.trim());
  return Number.isSafeInteger(result) ? result : null;
}
