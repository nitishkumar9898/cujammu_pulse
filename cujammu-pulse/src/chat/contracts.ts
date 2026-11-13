export type ChatGroup = { id: number; name: string; type: string; nightSilenceEnabled: boolean; canPost: boolean; nightSilenceActive: boolean };
export type ChatMessage = { id: number; senderName: string; content: string; createdAt: string };
export type ChatGroupPage = { items: ChatGroup[]; page: number; hasMore: boolean };
function record(value: unknown): Record<string, unknown> { if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid chat response'); return value as Record<string, unknown>; }
function text(value: unknown, max: number): string { if (typeof value !== 'string' || !value.trim() || value.length > max) throw new Error('Invalid chat response'); return value; }
function id(value: unknown): number { if (!Number.isSafeInteger(value) || (value as number) < 1) throw new Error('Invalid chat response'); return value as number; }
function timestamp(value: unknown): string { const result = text(value, 80); if (!/^\d{4}-\d\d-\d\dT/.test(result) || !Number.isFinite(new Date(result).getTime())) throw new Error('Invalid chat response'); return result; }
export function decodeGroups(value: unknown): ChatGroup[] { const root = record(value); if (!Array.isArray(root.items) || root.items.length > 100) throw new Error('Invalid chat response'); return root.items.map(raw => { const v = record(raw); if (typeof v.nightSilenceEnabled !== 'boolean' || typeof v.canPost !== 'boolean' || typeof v.nightSilenceActive !== 'boolean') throw new Error('Invalid chat response'); return { id: id(v.id), name: text(v.name, 200), type: text(v.type, 40), nightSilenceEnabled: v.nightSilenceEnabled, canPost: v.canPost, nightSilenceActive: v.nightSilenceActive }; }); }
export function decodeMessages(value: unknown): ChatMessage[] { const root = record(value); if (!Array.isArray(root.items) || root.items.length > 100) throw new Error('Invalid chat response'); return root.items.map(raw => { const v = record(raw); return { id: id(v.id), senderName: text(v.senderName, 160), content: text(v.content, 2000), createdAt: timestamp(v.createdAt) }; }); }
export function decodeGroupPage(value: unknown): ChatGroupPage {
  const root = record(value);
  if (!Array.isArray(root.items) || root.items.length > 20 || !Number.isSafeInteger(root.page) || (root.page as number) < 0 || typeof root.hasMore !== 'boolean') throw new Error('Invalid chat response');
  const items = decodeGroups(root);
  if (new Set(items.map(group => group.id)).size !== items.length) throw new Error('Invalid chat response');
  return { items, page: root.page as number, hasMore: root.hasMore };
}
