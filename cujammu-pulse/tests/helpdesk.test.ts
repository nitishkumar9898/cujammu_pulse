import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeManagedTicket, decodeTicket, decodeTicketPage } from '../src/helpdesk/contracts.ts';
import { actions, modules } from '../src/registry.ts';

const ticket = { id: '11111111-1111-4111-8111-111111111111', version: 0, ticketNumber: 'CUJ-TKT-203001-0001', subject: 'Synthetic support request', description: 'Synthetic test request', category: 'GENERAL', priority: 'MEDIUM', requestedPriority: 'MEDIUM', status: 'OPEN', assignedOffice: null, assignedStaffId: 17, escalated: false, resolutionNotes: null, createdAt: '2030-01-01T00:00:00Z', resolvedAt: null, canManage: false, canReopen: false };

test('requester ticket decoder strips staff identity and validates bounded public contract', () => {
  const decoded = decodeTicket(ticket);
  assert.equal(decoded.ticketNumber, 'CUJ-TKT-203001-0001');
  assert.equal('assignedStaffId' in decoded, false);
  assert.deepEqual(decodeTicketPage({ page: 0, hasNext: false, items: [ticket] }, decodeTicket).items, [decoded]);
  assert.throws(() => decodeTicket({ ...ticket, id: 'not-a-ticket' }));
});

test('requester helpdesk actions are feature-gated and shared by web and Android', () => {
  for (const actionId of ['helpdesk.ticket.create', 'helpdesk.ticket.reply', 'helpdesk.ticket.reopen', 'helpdesk.ticket.escalate', 'helpdesk.ticket.triage']) {
    assert.ok(actions.some(action => action.actionId === actionId && action.featureKey === 'MODULE_HELPDESK' && action.clients.includes('web') && action.clients.includes('android')));
  }
});

test('helpdesk module is available only through its runtime flag', () => {
  const module = modules.find(item => item.id === 'helpdesk');
  assert.equal(module?.flag, 'MODULE_HELPDESK');
  assert.equal(module?.implemented, true);
});

test('assigned staff identifier is accepted only in a server-authorized manager ticket', () => {
  assert.deepEqual(decodeManagedTicket({ ...ticket, canManage: true, assignedStaffId: 17 }).assignedStaffId, 17);
  assert.throws(() => decodeManagedTicket({ ...ticket, canManage: false, assignedStaffId: 17 }));
});
