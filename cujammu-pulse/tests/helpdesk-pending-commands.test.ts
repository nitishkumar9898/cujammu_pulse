import test from 'node:test';
import assert from 'node:assert/strict';
import { clearPendingHelpdeskCommands, pendingHelpdeskCommands } from '../src/helpdesk/pending-commands.ts';

test('pending helpdesk commands survive route remounts in memory but are account-scoped', () => {
  const first = pendingHelpdeskCommands('synthetic-account-a');
  first.create = { requestKey: 'synthetic-key', subject: 'Private subject', description: 'Private body', category: 'GENERAL', requestedPriority: 'MEDIUM' };
  assert.equal(pendingHelpdeskCommands('synthetic-account-a'), first);
  assert.equal(pendingHelpdeskCommands('synthetic-account-a').create?.description, 'Private body');
  assert.notEqual(pendingHelpdeskCommands('synthetic-account-b'), first);
  assert.equal(pendingHelpdeskCommands('synthetic-account-b').create, null);
  pendingHelpdeskCommands('synthetic-account-b').action = { kind: 'reply', ticketId: 'ticket-b', version: 0, requestKey: 'key-b', value: 'private reply' };
  clearPendingHelpdeskCommands('synthetic-account-a');
  assert.equal(pendingHelpdeskCommands('synthetic-account-a').create, null);
  assert.equal(pendingHelpdeskCommands('synthetic-account-b').action?.value, 'private reply');
  clearPendingHelpdeskCommands();
  assert.equal(pendingHelpdeskCommands('synthetic-account-a').create, null);
});
