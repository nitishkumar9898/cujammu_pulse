import { expect, test, type Page } from '@playwright/test';
import { goToNavigation } from './helpers/navigation';

const ticketId = '00000000-0000-4000-8000-000000000015';
function ticket(version = 0) {
  return { id: ticketId, version, ticketNumber: 'CUJ-TKT-203001-00001', subject: 'Synthetic support request', description: 'Synthetic private details', category: 'GENERAL', priority: 'MEDIUM', requestedPriority: 'MEDIUM', status: 'OPEN', assignedOffice: null, escalated: false, resolutionNotes: null, createdAt: null, resolvedAt: null, canManage: false, canReopen: false };
}
async function openHelpdesk(page: Page) {
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Student', role: 'STUDENT' } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_HELPDESK: true, MODULE_EVENTS: true } }));
  await page.goto('/app/');
  await page.getByRole('button', { name: 'Skip introduction' }).click();
  await goToNavigation(page, 'Helpdesk');
}

test('uncertain helpdesk creation retries the exact command with its original request key', async ({ page }) => {
  const commands: unknown[] = [];
  await page.route('**/api/v1/cujammu/helpdesk/my-tickets?page=0', route => route.fulfill({ json: { items: [], page: 0, hasNext: false } }));
  await page.route('**/api/v1/cujammu/helpdesk/tickets', async route => {
    expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf');
    commands.push(route.request().postDataJSON());
    if (commands.length === 1) return route.abort();
    return route.fulfill({ json: ticket() });
  });
  await openHelpdesk(page);
  await page.getByRole('textbox', { name: 'Subject', exact: true }).fill('Synthetic request');
  await page.getByRole('textbox', { name: 'Describe the issue', exact: true }).fill('Synthetic description');
  await page.getByRole('button', { name: 'Submit support request', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Subject', exact: true })).not.toBeEditable();
  await goToNavigation(page, 'Events');
  await goToNavigation(page, 'Helpdesk');
  await expect(page.getByRole('textbox', { name: 'Subject', exact: true })).toHaveValue('Synthetic request');
  await page.getByRole('button', { name: 'Retry support request', exact: true }).click();
  await expect(page.getByText('CUJ-TKT-203001-00001', { exact: true })).toBeVisible();
  expect(commands).toHaveLength(2);
  expect(commands[1]).toEqual(commands[0]);
  expect((commands[0] as { requestKey: string }).requestKey).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
});

test('uncertain helpdesk reply retries its immutable command and preserves a newer draft', async ({ page }) => {
  const replies: unknown[] = [];
  await page.route('**/api/v1/cujammu/helpdesk/my-tickets?page=0', route => route.fulfill({ json: { items: [ticket()], page: 0, hasNext: false } }));
  await page.route(`**/api/v1/cujammu/helpdesk/tickets/${ticketId}/thread?page=0`, route => route.fulfill({ json: { items: [], page: 0, hasNext: false } }));
  await page.route(`**/api/v1/cujammu/helpdesk/tickets/${ticketId}/replies`, async route => {
    expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf');
    replies.push(route.request().postDataJSON());
    if (replies.length === 1) return route.abort();
    return route.fulfill({ json: ticket(1) });
  });
  await openHelpdesk(page);
  const reply = page.getByRole('textbox', { name: 'Reply', exact: true });
  await reply.fill('Original reply');
  await page.getByRole('button', { name: 'Send reply', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await reply.fill('New unsent draft');
  await page.getByRole('button', { name: 'Send reply', exact: true }).click();
  await expect(reply).toHaveValue('New unsent draft');
  expect(replies).toHaveLength(2);
  expect(replies[1]).toEqual(replies[0]);
  expect(replies[0]).toMatchObject({ expectedVersion: 0, body: 'Original reply' });
});
