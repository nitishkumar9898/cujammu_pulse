export type PollEvent = { id: number; title: string; eventDate: string; canManage: boolean };
export type PollView = { id: number; eventId: number; question: string; options: string[]; state: 'DRAFT' | 'OPEN' | 'CLOSED'; revision: number; opensAt: string | null; closesAt: string | null; canManage: boolean; canVote: boolean; myChoice: string | null; totalVotes: number; results: { option: string; votes: number; percentage: number }[] };
export type PollPage<T> = { items: T[]; page: number; hasMore: boolean };
export type CreatePoll = { question: string; options: string[]; durationMinutes: number; requestKey: string };
function record(v: unknown): Record<string, unknown> { if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error('Invalid poll response'); return v as Record<string, unknown>; }
function text(v: unknown, max: number): string { if (typeof v !== 'string' || !v.trim() || v.length > max) throw new Error('Invalid poll response'); return v; }
function integer(v: unknown, min = 0): number { if (!Number.isSafeInteger(v) || (v as number) < min) throw new Error('Invalid poll response'); return v as number; }
function bool(v: unknown): boolean { if (typeof v !== 'boolean') throw new Error('Invalid poll response'); return v; }
function date(v: unknown): string { const result = text(v, 80); if (!/^\d{4}-\d\d-\d\dT/.test(result) || !Number.isFinite(Date.parse(result))) throw new Error('Invalid poll response'); return result; }
function page<T extends { id: number }>(v: unknown, decode: (v: unknown) => T): PollPage<T> { const r = record(v); if (!Array.isArray(r.items) || r.items.length > 20) throw new Error('Invalid poll response'); const items = r.items.map(decode); if (new Set(items.map(i => i.id)).size !== items.length) throw new Error('Invalid poll response'); return { items, page: integer(r.page), hasMore: bool(r.hasMore) }; }
export function decodePollEvents(v: unknown): PollPage<PollEvent> { return page(v, raw => { const r = record(raw); return { id: integer(r.id, 1), title: text(r.title, 200), eventDate: date(r.eventDate), canManage: bool(r.canManage) }; }); }
export function decodePoll(v: unknown): PollView {
  const r = record(v);
  if (!Array.isArray(r.options) || r.options.length < 2 || r.options.length > 8 || !['DRAFT', 'OPEN', 'CLOSED'].includes(String(r.state))) throw new Error('Invalid poll response');
  const options = r.options.map(v => text(v, 50));
  if (options.some(v => v.includes(',')) || new Set(options.map(v => v.toLowerCase())).size !== options.length) throw new Error('Invalid poll response');
  if (r.myChoice !== null && (typeof r.myChoice !== 'string' || !options.includes(r.myChoice))) throw new Error('Invalid poll response');
  if (!Array.isArray(r.results) || r.results.length !== options.length) throw new Error('Invalid poll response');
  const results = r.results.map((raw, index) => { const result = record(raw); if (result.option !== options[index] || typeof result.percentage !== 'number' || !Number.isFinite(result.percentage) || result.percentage < 0 || result.percentage > 100) throw new Error('Invalid poll response'); return { option: options[index]!, votes: integer(result.votes), percentage: result.percentage }; });
  const totalVotes = integer(r.totalVotes);
  if (results.reduce((n, result) => n + result.votes, 0) !== totalVotes) throw new Error('Invalid poll response');
  // Server percentages are rounded to one decimal place.
  if (results.some(result => Math.abs(result.percentage - (totalVotes ? result.votes * 100 / totalVotes : 0)) > 0.051)) throw new Error('Invalid poll response');
  const opensAt = r.opensAt === null ? null : date(r.opensAt), closesAt = r.closesAt === null ? null : date(r.closesAt);
  if (r.state === 'DRAFT' ? opensAt !== null || closesAt !== null || r.canVote !== false : !opensAt || !closesAt || !/(Z|[+-]\d\d:\d\d)$/.test(opensAt) || !/(Z|[+-]\d\d:\d\d)$/.test(closesAt) || Date.parse(opensAt) > Date.parse(closesAt) || (r.state === 'OPEN' && Date.parse(opensAt) === Date.parse(closesAt))) throw new Error('Invalid poll response');
  if (r.canVote === true && (r.myChoice !== null || r.state !== 'OPEN')) throw new Error('Invalid poll response');
  return { id: integer(r.id, 1), eventId: integer(r.eventId, 1), question: text(r.question, 200), options, state: r.state as PollView['state'], revision: integer(r.revision), opensAt, closesAt, canManage: bool(r.canManage), canVote: bool(r.canVote), myChoice: r.myChoice as string | null, totalVotes, results };
}
export function decodePollPage(v: unknown): PollPage<PollView> { return page(v, decodePoll); }
