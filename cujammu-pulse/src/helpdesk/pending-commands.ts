import type { api } from '../api';

export type PendingCreateCommand = Parameters<typeof api.createHelpdeskTicket>[0];
export type PendingOwnerCommand = {
  kind: 'reply' | 'reopen' | 'escalate';
  ticketId: string;
  version: number;
  requestKey: string;
  value: string;
};
export type PendingTriageCommand = {
  ticketId: string;
  command: Parameters<typeof api.triageHelpdeskTicket>[1];
};
export type PendingHelpdeskCommands = {
  create: PendingCreateCommand | null;
  action: PendingOwnerCommand | null;
  triage: PendingTriageCommand | null;
};

const pendingByAccount = new Map<string, PendingHelpdeskCommands>();

/** Holds uncertain private commands only in process memory, isolated per signed-in account. */
export function pendingHelpdeskCommands(accountId: string): PendingHelpdeskCommands {
  if (!accountId) throw new Error('Helpdesk account scope is required.');
  let pending = pendingByAccount.get(accountId);
  if (!pending) {
    pending = { create: null, action: null, triage: null };
    pendingByAccount.set(accountId, pending);
  }
  return pending;
}

/** Drops every private pending payload when the authenticated session is cleared. */
export function clearPendingHelpdeskCommands(accountId?: string): void {
  if (accountId) pendingByAccount.delete(accountId);
  else pendingByAccount.clear();
}
