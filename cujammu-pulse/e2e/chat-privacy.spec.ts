import { goToNavigation, openNavigationMenu } from './helpers/navigation';
import { test, expect } from '@playwright/test';

test('switching groups discards pending send results and revoked content', async ({ page }) => {
  const group = (id: number, name: string) => ({ id, name, type: 'EPHEMERAL_EVENT', nightSilenceEnabled: true, canPost: true, nightSilenceActive: false });
  const message = (id: number, content: string) => ({ id, senderName: 'Synthetic sender', content, createdAt: '2026-10-03T10:00:00' });
  let revoked = false;
  let releaseSend!: () => void;
  let sendStarted!: () => void;
  const sending = new Promise<void>(resolve => { sendStarted = resolve; });
  const pending = new Promise<void>(resolve => { releaseSend = resolve; });
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Student', role: 'STUDENT' } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: true, MODULE_COMMUNITY_CHAT: true } }));
  await page.route('**/api/v1/cujammu/chat/groups/page?page=0', route => route.fulfill({ json: { items: [group(1, 'Group Alpha'), group(2, 'Group Beta')], page: 0, hasMore: false } }));
  await page.route('**/api/v1/cujammu/chat/groups/1/messages', async route => {
    if (route.request().method() === 'POST') {
      sendStarted(); await pending;
      await route.fulfill({ json: message(3, 'Pending Alpha message') });
    } else await route.fulfill({ json: { items: [message(1, 'Private Alpha content')] } });
  });
  await page.route('**/api/v1/cujammu/chat/groups/2/messages', route => route.fulfill(revoked
    ? { status: 403, json: {} } : { json: { items: [message(2, 'Private Beta content')] } }));
  await page.goto('/app/'); await page.getByRole('button', { name: 'Skip introduction' }).click();
  await goToNavigation(page, 'Community chat');
  await expect(page.getByText('Private Alpha content', { exact: true })).toBeVisible();
  await page.getByRole('textbox', { name: 'Message', exact: true }).fill('Pending Alpha message');
  await page.getByRole('button', { name: 'Send message', exact: true }).click(); await sending;
  await page.getByRole('button', { name: /Group Beta/ }).click();
  await expect(page.getByText('Private Beta content', { exact: true })).toBeVisible();
  await expect(page.getByText('Private Alpha content', { exact: true })).toHaveCount(0);
  releaseSend();
  await expect(page.getByRole('button', { name: 'Send message', exact: true })).toBeVisible();
  await expect(page.getByText('Pending Alpha message', { exact: true })).toHaveCount(0);
  revoked = true;
  await page.getByRole('button', { name: 'Refresh groups', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByText('Private Beta content', { exact: true })).toHaveCount(0);
});

test('group paging clears private content and drafts while department chat works with events disabled', async ({ page }) => {
  const group = (id: number, name: string) => ({ id, name, type: 'PERMANENT_DEPARTMENT', nightSilenceEnabled: true, canPost: true, nightSilenceActive: false });
  let releasePage!: () => void;
  const nextPage = new Promise<void>(resolve => { releasePage = resolve; });
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Student', role: 'STUDENT' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: false, MODULE_COMMUNITY_CHAT: true } }));
  await page.route('**/api/v1/cujammu/chat/groups/page?page=*', async route => {
    const requestedPage = Number(new URL(route.request().url()).searchParams.get('page'));
    if (requestedPage === 1) await nextPage;
    await route.fulfill({ json: { items: [group(requestedPage + 1, requestedPage === 0 ? 'First department' : 'Next department')], page: requestedPage, hasMore: requestedPage === 0 } });
  });
  await page.route('**/api/v1/cujammu/chat/groups/*/messages', route => {
    const next = route.request().url().includes('/groups/2/');
    return route.fulfill({ json: { items: [{ id: next ? 2 : 1, senderName: 'Synthetic sender', content: next ? 'Second department content' : 'First department content', createdAt: '2026-10-03T10:00:00' }] } });
  });
  await page.goto('/app/'); await page.getByRole('button', { name: 'Skip introduction' }).click();
  await goToNavigation(page, 'Community chat');
  await expect(page.getByText('First department content', { exact: true })).toBeVisible();
  await page.getByRole('textbox', { name: 'Message', exact: true }).fill('Private unsent draft');
  await page.getByRole('button', { name: 'Next groups', exact: true }).click();
  await expect(page.getByText('First department content', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('textbox', { name: 'Message', exact: true })).toHaveCount(0);
  releasePage();
  await expect(page.getByText('Second department content', { exact: true })).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Message', exact: true })).toHaveValue('');
  await expect(page.getByRole('button', { name: 'Next groups', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Previous groups', exact: true }).click();
  await expect(page.getByText('First department content', { exact: true })).toBeVisible();
  await expect(page.getByText('Second department content', { exact: true })).toHaveCount(0);
});
