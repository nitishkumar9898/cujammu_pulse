import { goToNavigation, openNavigationMenu } from './helpers/navigation';
import { test, expect } from '@playwright/test';

test('admin grants and revokes versioned department membership while chat is disabled', async ({ page }) => {
  let enabled = true;
  let member: { userId: number; name: string; active: boolean; revision: number } | null = null;
  const commands: unknown[] = [];
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Admin', role: 'ADMIN' } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_COMMUNITY_CHAT: enabled } }));
  await page.route('**/api/v1/cujammu/chat/admin/department-groups', route => route.fulfill({ json: { items: [{ id: 3, name: 'Synthetic department group', department: 'Synthetic department' }] } }));
  await page.route('**/api/v1/cujammu/chat/admin/groups/3/members?page=0', route => route.fulfill({ json: { items: member ? [member] : [], page: 0, hasMore: false } }));
  await page.route('**/api/v1/cujammu/chat/admin/groups/3/members/22', route => {
    expect(route.request().method()).toBe('PUT');
    expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf');
    const body = route.request().postDataJSON(); commands.push(body);
    expect(body.expectedRevision).toBe(member?.revision ?? null);
    member = { userId: 22, name: 'Synthetic Student', active: body.active, revision: member ? member.revision + 1 : 0 };
    return route.fulfill({ json: member });
  });
  await page.goto('/app/'); await page.getByRole('button', { name: 'Skip introduction' }).click();
  await goToNavigation(page, 'Chat access');
  await page.getByRole('textbox', { name: 'Verified user ID', exact: true }).fill('22');
  await page.getByRole('textbox', { name: 'Verification reference', exact: true }).fill('REGISTRY-TEST-001');
  await page.getByRole('textbox', { name: 'Membership change reason', exact: true }).fill('Verified synthetic source');
  await page.getByRole('button', { name: 'Grant chat access', exact: true }).click();
  await expect(page.getByText('Department chat access granted.', { exact: true })).toBeVisible();
  expect(commands).toHaveLength(1);
  enabled = false; await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await openNavigationMenu(page);
  await expect(page.getByRole('tab', { name: 'Community chat', exact: true })).toHaveCount(0);
  await expect(page.getByRole('tab', { name: 'Chat access', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Select member 22', exact: true }).click();
  await page.getByRole('textbox', { name: 'Membership change reason', exact: true }).fill('Verified removal request');
  await page.getByRole('button', { name: 'Revoke chat access', exact: true }).click();
  await expect(page.getByText('Department chat access revoked.', { exact: true })).toBeVisible();
  expect(commands).toEqual([
    { expectedRevision: null, active: true, sourceReference: 'REGISTRY-TEST-001', reason: 'Verified synthetic source' },
    { expectedRevision: 0, active: false, sourceReference: '', reason: 'Verified removal request' },
  ]);
});

test('student never sees membership management navigation', async ({ page }) => {
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 22, name: 'Synthetic Student', role: 'STUDENT' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_COMMUNITY_CHAT: false } }));
  await page.goto('/app/'); await page.getByRole('button', { name: 'Skip introduction' }).click();
  await expect(page.getByText('Welcome, Synthetic Student', { exact: true })).toBeVisible();
  await openNavigationMenu(page);
  await expect(page.getByRole('tab', { name: 'Chat access', exact: true })).toHaveCount(0);
});

test('small-screen campus navigation opens a searchable, grouped page menu', async ({ page }, testInfo) => {
  test.skip((page.viewportSize()?.width ?? 1280) >= 1000, 'The grouped menu is the compact navigation pattern.');
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 22, name: 'Synthetic Student', role: 'STUDENT' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: true, MODULE_CALENDAR: true } }));
  await page.goto('/app/');
  await page.getByRole('button', { name: 'Skip introduction' }).click();

  const menu = page.getByRole('button', { name: 'Open campus menu, Overview', exact: true });
  await expect(menu).toBeVisible();
  await menu.click();
  await expect(page.getByRole('button', { name: 'Close campus menu, Overview', exact: true })).toHaveAttribute('aria-expanded', 'true');
  await page.screenshot({ path: testInfo.outputPath('campus-menu-expanded.png'), fullPage: true });
  await page.getByRole('textbox', { name: 'Filter campus pages' }).fill('event');
  await expect(page.getByRole('tab', { name: 'Events', exact: true })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Calendar', exact: true })).toHaveCount(0);
  await goToNavigation(page, 'Events');
  await expect(page.getByRole('button', { name: 'Open campus menu, Events', exact: true })).toBeVisible();
});
