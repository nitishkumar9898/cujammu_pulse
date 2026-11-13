import { expect, test, type Page } from '@playwright/test';
import { goToNavigation } from './helpers/navigation';

const item = (id: number) => ({ id, message: `Synthetic notice ${id}`, read: false, acknowledged: false, createdAt: '2026-10-19T00:00:00Z' });
async function start(page: Page) {
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Account', role: 'STUDENT' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_NOTIFICATIONS: true } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.goto('/app/');
  await page.getByRole('button', { name: 'Skip introduction' }).click();
  await goToNavigation(page, 'Notifications');
}

test('failed initial inbox is an alert; keyboard retry shows empty only after success', async ({ page }) => {
  let calls = 0;
  await page.route('**/api/v1/cujammu/notifications', route => route.fulfill(++calls === 1 ? { status: 503, json: {} } : { json: { items: [], nextBeforeId: null } }));
  await start(page);
  await expect(page.getByRole('alert')).toContainText('could not complete');
  await expect(page.getByRole('heading', { name: 'You’re all caught up' })).toHaveCount(0);
  const retry = page.getByRole('button', { name: 'Retry', exact: true });
  await retry.focus(); await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'You’re all caught up' })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(calls).toBe(2);
});

test('older-page retry preserves cursor and items; pending fetch excludes refresh and commands', async ({ page }) => {
  let firstCalls = 0; let olderCalls = 0; let sessionChecks = 0;
  let release!: () => void;
  const blocked = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/v1/cujammu/notifications*', async route => {
    const before = new URL(route.request().url()).searchParams.get('beforeId');
    if (before === null) { firstCalls++; return route.fulfill({ json: { items: [item(20)], nextBeforeId: 20 } }); }
    expect(before).toBe('20'); olderCalls++;
    if (olderCalls === 1) return route.fulfill({ status: 503, json: {} });
    await blocked;
    return route.fulfill({ json: { items: [item(20), item(19)], nextBeforeId: null } });
  });
  await start(page);
  await page.route('**/api/v1/session', route => { sessionChecks++; return route.fulfill({ json: { id: 1, name: 'Synthetic Account', role: 'STUDENT' } }); });
  await page.getByRole('button', { name: 'Load older notifications' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await page.getByRole('button', { name: 'Retry', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Refresh', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Mark read', exact: true })).toBeDisabled();
  await expect(page.getByRole('status')).toContainText('Loading older');
  // A parent session refresh must not silently restart the inbox while pagination is pending.
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect.poll(() => sessionChecks).toBeGreaterThan(0);
  release();
  await expect(page.getByText('Synthetic notice 19', { exact: true })).toBeVisible();
  await expect(page.getByText('Synthetic notice 20', { exact: true })).toHaveCount(1);
  await expect(page.getByRole('status')).toContainText('Older notifications loaded.');
  expect(firstCalls).toBe(1); expect(olderCalls).toBe(2);
});

test('failed read does not mutate local state; explicit retry announces confirmed result', async ({ page }) => {
  let writes = 0;
  await page.route('**/api/v1/cujammu/notifications', route => route.fulfill({ json: { items: [item(20)], nextBeforeId: null } }));
  await page.route('**/api/v1/cujammu/notifications/20/read', route => {
    expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf');
    return route.fulfill(++writes === 1 ? { status: 503, json: {} } : { status: 204 });
  });
  await start(page);
  await page.getByRole('button', { name: 'Mark read', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByText(/· Unread/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Retry', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Mark read', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Notification marked read.');
  await expect(page.getByText(/· Read/)).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(writes).toBe(2);
});
