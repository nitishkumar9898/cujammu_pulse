export const foundCategories = ['ELECTRONICS', 'ID_CARDS_DOCS', 'BAGS_WALLETS', 'KEYS', 'CLOTHING', 'OTHER'] as const;
export type FoundCategory = typeof foundCategories[number];
export type LostFoundItem = { id: number; version: number; title: string; category: string; description: string; locationFound: string; status: string; reportedAt: string | null; canReview: boolean };
export type LostFoundPage = { items: LostFoundItem[]; page: number; hasNext: boolean };
export type FoundClaim = { id: number; itemId: number; status: string; evidence: string; submittedAt: string | null };
export type ReportFoundCommand = { requestKey: string; title: string; category: FoundCategory; description: string; locationFound: string };
export type ClaimFoundCommand = { requestKey: string; expectedVersion: number; evidence: string };
export type HandoverFoundCommand = { requestKey: string; expectedVersion: number; claimId: number; verificationReason: string };
function obj(v: unknown): Record<string, unknown> { if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error('Invalid lost and found response'); return v as Record<string, unknown>; }
function number(v: unknown, minimum: number): number { if (typeof v !== 'number' || !Number.isSafeInteger(v) || v < minimum) throw new Error('Invalid lost and found identifier'); return v; }
function text(v: unknown, max: number): string { if (typeof v !== 'string' || v.length > max) throw new Error('Invalid lost and found text'); return v; }
function date(v: unknown): string | null { if (v == null) return null; const s = text(v, 80); if (!Number.isFinite(Date.parse(s))) throw new Error('Invalid lost and found time'); return s; }
export function decodeLostFoundItem(v: unknown): LostFoundItem {
  const x = obj(v); if (typeof x.canReview !== 'boolean') throw new Error('Invalid found-item review capability');
  return { id: number(x.id, 1), version: number(x.version, 0), title: text(x.title, 120), category: text(x.category, 50), description: text(x.description, 2000), locationFound: text(x.locationFound, 150), status: text(x.status, 40), reportedAt: date(x.reportedAt), canReview: x.canReview };
}
export function decodeLostFoundItems(v: unknown): LostFoundItem[] { if (!Array.isArray(v) || v.length > 20) throw new Error('Invalid found-item page'); const items = v.map(decodeLostFoundItem); if (new Set(items.map(item => item.id)).size !== items.length) throw new Error('Duplicate found item'); return items; }
export function decodeLostFoundPage(v: unknown): LostFoundPage { const x = obj(v); const page = number(x.page, 0); if (page > 10000 || typeof x.hasNext !== 'boolean') throw new Error('Invalid found-item pagination'); return { items: decodeLostFoundItems(x.items), page, hasNext: x.hasNext }; }
export function decodeFoundClaim(v: unknown): FoundClaim { const x = obj(v); return { id: number(x.id, 1), itemId: number(x.itemId, 1), status: text(x.status, 40), evidence: text(x.evidence, 2000), submittedAt: date(x.submittedAt) }; }
export function decodeFoundClaims(v: unknown): FoundClaim[] { if (!Array.isArray(v) || v.length > 20) throw new Error('Invalid private claims page'); const items = v.map(decodeFoundClaim); if (new Set(items.map(item => item.id)).size !== items.length) throw new Error('Duplicate found-item claim'); return items; }
