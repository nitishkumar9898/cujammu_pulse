function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid helpdesk response');
  return value as Record<string, unknown>;
}
function text(value: unknown, max = 4000): string {
  if (typeof value !== 'string' || value.length > max) throw new Error('Invalid helpdesk response');
  return value;
}
function id(value: unknown): string {
  const valueText = text(value, 64);
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(valueText)) throw new Error('Invalid helpdesk ticket identifier');
  return valueText;
}
function timestamp(value: unknown): string | null {
  if (value == null) return null;
  const result = text(value, 80);
  if (!Number.isFinite(Date.parse(result))) throw new Error('Invalid helpdesk timestamp');
  return result;
}
export type Ticket = { id: string; version: number; ticketNumber: string; subject: string; description: string; category: string; priority: string; requestedPriority: string; status: string; assignedOffice: string | null; escalated: boolean; resolutionNotes: string | null; createdAt: string | null; resolvedAt: string | null; canManage: boolean; canReopen: boolean };
export type ManagedTicket = Ticket & { assignedStaffId: number | null };
export type TicketEntry = { id: string; kind: string; authorLabel: string; body: string; occurredAt: string | null };
export type TicketPage<T> = { items: T[]; page: number; hasNext: boolean };
export function decodeTicket(value: unknown): Ticket {
  const item = record(value);
  if (typeof item.version !== 'number' || !Number.isSafeInteger(item.version) || item.version < 0
    || typeof item.escalated !== 'boolean' || typeof item.canManage !== 'boolean' || typeof item.canReopen !== 'boolean') throw new Error('Invalid helpdesk ticket');
  for (const field of ['category', 'priority', 'requestedPriority', 'status'] as const) text(item[field], 32);
  return { id: id(item.id), version: item.version, ticketNumber: text(item.ticketNumber, 32), subject: text(item.subject, 150), description: text(item.description), category: text(item.category, 32), priority: text(item.priority, 32), requestedPriority: text(item.requestedPriority, 32), status: text(item.status, 32), assignedOffice: item.assignedOffice == null ? null : text(item.assignedOffice, 32), escalated: item.escalated, resolutionNotes: item.resolutionNotes == null ? null : text(item.resolutionNotes, 2000), createdAt: timestamp(item.createdAt), resolvedAt: timestamp(item.resolvedAt), canManage: item.canManage, canReopen: item.canReopen };
}
export function decodeManagedTicket(value: unknown): ManagedTicket {
  const ticket = decodeTicket(value); const item = record(value);
  if (!ticket.canManage || (item.assignedStaffId != null && (typeof item.assignedStaffId !== 'number' || !Number.isSafeInteger(item.assignedStaffId) || item.assignedStaffId < 1))) throw new Error('Invalid managed helpdesk ticket');
  return { ...ticket, assignedStaffId: item.assignedStaffId as number | null };
}
export function decodeTicketPage(value: unknown, decoder: (value: unknown) => Ticket | TicketEntry): TicketPage<Ticket | TicketEntry> {
  const root = record(value);
  if (typeof root.page !== 'number' || !Number.isSafeInteger(root.page) || root.page < 0 || typeof root.hasNext !== 'boolean' || !Array.isArray(root.items) || root.items.length > 24) throw new Error('Invalid helpdesk page');
  return { page: root.page, hasNext: root.hasNext, items: root.items.map(decoder) };
}
export function decodeEntry(value: unknown): TicketEntry {
  const item = record(value);
  return { id: id(item.id), kind: text(item.kind, 32), authorLabel: text(item.authorLabel, 64), body: text(item.body), occurredAt: timestamp(item.occurredAt) };
}
