import { goToNavigation, openNavigationMenu } from './helpers/navigation';
import { test, expect } from '@playwright/test';

test('student opens own team from overview and loses access after feature disable', async ({ page }) => {
  let enabled = true;
  let created = false;
  let managerReads = 0;
  const team = { id: 7, revision: 0, name: 'Synthetic Team', minSize: 2, maxSize: 5,
    memberCount: 1, locked: false, canManage: true, inviteCode: 'CUJ-ABCD23', createdAt: '2026-10-03T10:00:00' };
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Student', role: 'STUDENT' } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: true, MODULE_TEAMS: enabled } }));
  await page.route('**/api/v1/cujammu/teams/eligible-events', route => route.fulfill({ json: { items: [{ id: 2, title: 'Synthetic event', eventDate: '2030-01-01T10:00:00' }] } }));
  await page.route('**/api/v1/cujammu/events/2/teams', route => { managerReads++; return route.fulfill({ status: 403, json: {} }); });
  await page.route('**/api/v1/cujammu/teams/mine?page=0', route => route.fulfill({ json: { items: created ? [{ event: { id: 2, title: 'Synthetic event', eventDate: '2030-01-01T10:00:00' }, team, canChangeRoster: true }] : [], page: 0, hasMore: false } }));
  await page.route('**/api/v1/cujammu/events/2/teams/create', route => {
    expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf');
    expect(route.request().postDataJSON().name).toBe('Synthetic Team');
    created = true; return route.fulfill({ json: team });
  });
  await page.goto('/app/');
  await page.getByRole('button', { name: 'Skip introduction' }).click();
  await page.getByRole('button', { name: 'Open teams', exact: true }).click();
  await page.getByRole('textbox', { name: 'Team name', exact: true }).fill('Synthetic Team');
  await page.getByRole('button', { name: 'Create team', exact: true }).click();
  await expect(page.getByText('Invite code: CUJ-ABCD23')).toBeVisible();
  expect(managerReads).toBe(0);
  enabled = false;
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await openNavigationMenu(page);
  await expect(page.getByRole('tab', { name: 'Teams', exact: true })).toHaveCount(0);
  await expect(page.getByText('Invite code: CUJ-ABCD23')).toHaveCount(0);
});

test('closed-event team history and roster remain available with no joinable events', async ({ page }) => {
  const team = { id: 7, revision: 3, name: 'Past Team', minSize: 2, maxSize: 5, memberCount: 2, locked: true, canManage: true, inviteCode: 'CUJ-ABCD23', createdAt: '2026-01-01T10:00:00' };
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Student', role: 'STUDENT' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: true, MODULE_TEAMS: true } }));
  await page.route('**/api/v1/cujammu/teams/eligible-events', route => route.fulfill({ json: { items: [] } }));
  await page.route('**/api/v1/cujammu/teams/mine?page=*', route => {
    const second = new URL(route.request().url()).searchParams.get('page') === '1';
    return route.fulfill({ json: { items: [{ event: { id: second ? 3 : 2, title: second ? 'Older Event' : 'Closed Event', eventDate: '2026-01-02T10:00:00' }, team: { ...team, id: second ? 8 : 7, name: second ? 'Older Team' : 'Past Team' }, canChangeRoster: false }], page: second ? 1 : 0, hasMore: !second } });
  });
  await page.route('**/api/v1/cujammu/teams/7/members', route => route.fulfill({ json: { team, members: [{ id: 9, name: 'Synthetic Teammate', role: 'MEMBER', joinedAt: '2026-01-01T11:00:00' }] } }));
  await page.goto('/app/'); await page.getByRole('button', { name: 'Skip introduction' }).click();
  await page.getByRole('button', { name: 'Open teams', exact: true }).click();
  await expect(page.getByText('No confirmed event currently accepts team registrations.')).toBeVisible();
  await expect(page.getByText('Past Team', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Lock roster', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'View roster for Past Team' }).click();
  await expect(page.getByText('Synthetic Teammate · MEMBER', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Next teams', exact: true }).click();
  await expect(page.getByText('Older Team', { exact: true })).toBeVisible();
  await expect(page.getByText('Past Team', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Synthetic Teammate · MEMBER', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Previous teams', exact: true }).click();
  await expect(page.getByText('Past Team', { exact: true })).toBeVisible();
});

test('join binds the selected event and preserves history when server rejects a different-event code', async ({ page }) => {
  let command: unknown;
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Student', role: 'STUDENT' } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: true, MODULE_TEAMS: true } }));
  await page.route('**/api/v1/cujammu/teams/eligible-events', route => route.fulfill({ json: { items: [{ id: 2, title: 'First Event', eventDate: '2030-01-01T10:00:00' }, { id: 3, title: 'Second Event', eventDate: '2030-01-02T10:00:00' }] } }));
  await page.route('**/api/v1/cujammu/teams/mine?page=0', route => route.fulfill({ json: { items: [], page: 0, hasMore: false } }));
  await page.route('**/api/v1/cujammu/teams/join', route => { command = route.request().postDataJSON(); return route.fulfill({ status: 409, json: {} }); });
  await page.goto('/app/'); await page.getByRole('button', { name: 'Skip introduction' }).click();
  await page.getByRole('button', { name: 'Open teams', exact: true }).click();
  await page.getByRole('textbox', { name: 'Team invite code' }).fill('CUJ-ABCD23');
  await page.getByRole('button', { name: /Second Event/ }).click();
  await expect(page.getByRole('textbox', { name: 'Team invite code' })).toHaveValue('');
  await page.getByRole('textbox', { name: 'Team invite code' }).fill('CUJ-ABCD23');
  await page.getByRole('button', { name: 'Join team', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('This record changed or conflicts');
  expect(command).toEqual({ expectedEventId: 3, inviteCode: 'CUJ-ABCD23' });
  await expect(page.getByText('You have not joined any teams.')).toBeVisible();
});
