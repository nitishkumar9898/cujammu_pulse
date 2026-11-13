export type TeamEvent = { id: number; title: string; eventDate: string };
export type Team = { id: number; revision: number; name: string; minSize: number; maxSize: number; memberCount: number; locked: boolean; canManage: boolean; inviteCode: string | null; createdAt: string };
export type TeamMember = { id: number; name: string; role: string; joinedAt: string };
export type TeamHistoryItem = { event: TeamEvent; team: Team; canChangeRoster: boolean };
export type TeamHistory = { items: TeamHistoryItem[]; page: number; hasMore: boolean };
function record(value: unknown): Record<string, unknown> { if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid team response'); return value as Record<string, unknown>; }
function id(value: unknown): number { if (!Number.isSafeInteger(value) || (value as number) < 1) throw new Error('Invalid team response'); return value as number; }
function text(value: unknown, max: number): string { if (typeof value !== 'string' || !value.trim() || value.length > max) throw new Error('Invalid team response'); return value; }
function time(value: unknown): string { const result = text(value, 80); if (!/^\d{4}-\d\d-\d\dT/.test(result) || !Number.isFinite(new Date(result).getTime())) throw new Error('Invalid team response'); return result; }
function team(value: unknown): Team { const v = record(value); if (!Number.isSafeInteger(v.revision) || (v.revision as number) < 0 || !Number.isInteger(v.minSize) || !Number.isInteger(v.maxSize) || !Number.isInteger(v.memberCount) || (v.minSize as number) < 2 || (v.maxSize as number) < (v.minSize as number) || (v.memberCount as number) < 0 || typeof v.locked !== 'boolean' || typeof v.canManage !== 'boolean' || !(v.inviteCode === null || /^CUJ-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$/.test(String(v.inviteCode)))) throw new Error('Invalid team response'); return { id: id(v.id), revision: v.revision as number, name: text(v.name, 120), minSize: v.minSize as number, maxSize: v.maxSize as number, memberCount: v.memberCount as number, locked: v.locked, canManage: v.canManage, inviteCode: v.inviteCode as string | null, createdAt: time(v.createdAt) }; }
export function decodeEligibleEvents(value: unknown): TeamEvent[] { const root = record(value); if (!Array.isArray(root.items) || root.items.length > 100) throw new Error('Invalid team response'); return root.items.map(raw => { const v = record(raw); return { id: id(v.id), title: text(v.title, 200), eventDate: time(v.eventDate) }; }); }
export function decodeTeam(value: unknown): Team { return team(value); }
export function decodeTeams(value: unknown): Team[] { const root = record(value); if (!Array.isArray(root.items) || root.items.length > 100) throw new Error('Invalid team response'); return root.items.map(team); }
export function decodeRoster(value: unknown): { team: Team; members: TeamMember[] } { const root = record(value); if (!Array.isArray(root.members) || root.members.length > 50) throw new Error('Invalid team response'); return { team: team(root.team), members: root.members.map(raw => { const v = record(raw); return { id: id(v.id), name: text(v.name, 160), role: text(v.role, 20), joinedAt: time(v.joinedAt) }; }) }; }
export function decodeTeamHistory(value: unknown): TeamHistory {
  const root = record(value);
  if (!Array.isArray(root.items) || root.items.length > 20 || !Number.isSafeInteger(root.page) || (root.page as number) < 0 || typeof root.hasMore !== 'boolean') throw new Error('Invalid team response');
  return { page: root.page as number, hasMore: root.hasMore, items: root.items.map(raw => {
    const item = record(raw); const event = record(item.event); const ownTeam = team(item.team);
    if (typeof item.canChangeRoster !== 'boolean' || (item.canChangeRoster && (!ownTeam.canManage || ownTeam.locked))) throw new Error('Invalid team response');
    return { event: { id: id(event.id), title: text(event.title, 200), eventDate: time(event.eventDate) }, team: ownTeam, canChangeRoster: item.canChangeRoster };
  }) };
}
