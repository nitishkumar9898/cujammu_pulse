import { goToNavigation, openNavigationMenu } from './helpers/navigation';
import { test, expect, type Page } from '@playwright/test';
const openPoll = { id: 51, eventId: 1, question: 'Best presentation?', options: ['Alpha', 'Beta'], state: 'OPEN', revision: 2, opensAt: '2026-10-03T10:00:00Z', closesAt: '2026-10-03T11:00:00Z', canManage: false, canVote: true, myChoice: null as string | null, totalVotes: 0, results: [{ option: 'Alpha', votes: 0, percentage: 0 }, { option: 'Beta', votes: 0, percentage: 0 }] };
async function prepare(page: Page, manager = false) {
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic account', role: manager ? 'FACULTY' : 'STUDENT' } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: true, MODULE_POLLS: true } }));
  await page.route('**/api/v1/cujammu/polls/events?page=0', route => route.fulfill({ json: { items: [1, 2].map(id => ({ id, title: `Event ${id}`, eventDate: '2026-10-03T10:00:00', canManage: manager })), page: 0, hasMore: false } }));
}
async function open(page: Page) { await page.goto('/app/'); await page.getByRole('button', { name: 'Skip introduction' }).click(); await goToNavigation(page, 'Audience polls'); }
test('vote requires explicit confirmation, shows immutable receipt and no manager controls', async ({ page }) => {
  await prepare(page); let votes = 0;
  await page.route('**/api/v1/cujammu/events/1/polls?page=0', route => route.fulfill({ json: { items: [openPoll], page: 0, hasMore: false } }));
  await page.route('**/api/v1/cujammu/polls/51/vote', route => { votes++; expect(route.request().postDataJSON()).toEqual({ option: 'Alpha' }); return route.fulfill({ json: { ...openPoll, canVote: false, myChoice: 'Alpha', totalVotes: 1, results: [{ option: 'Alpha', votes: 1, percentage: 100 }, { option: 'Beta', votes: 0, percentage: 0 }] } }); });
  await open(page); await page.getByRole('button', { name: 'Choose Alpha', exact: true }).click(); expect(votes).toBe(0);
  await page.getByRole('button', { name: 'Confirm vote: Alpha', exact: true }).click();
  await expect(page.getByText('Your confirmed choice: Alpha. This choice cannot be changed.', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Choose Beta', exact: true })).toHaveCount(0); expect(votes).toBe(1);
  await expect(page.getByRole('button', { name: 'Create draft', exact: true })).toHaveCount(0);
  await expect(page.getByText('Alpha: 1 votes · 100.0%', { exact: true })).toBeVisible();
});
test('manager retry retains its UUID and publishes/closes using returned revisions', async ({ page }) => {
  await prepare(page, true); const posts: { requestKey: string; question: string }[] = []; let created = false;
  const draft = { ...openPoll, state: 'DRAFT', revision: 0, canManage: true, canVote: false, opensAt: null, closesAt: null };
  await page.route('**/api/v1/cujammu/events/1/polls?page=0', route => route.fulfill({ json: { items: created ? [draft] : [], page: 0, hasMore: false } }));
  await page.route('**/api/v1/cujammu/events/1/polls', route => { posts.push(route.request().postDataJSON()); created = true; if (posts.length === 1) return route.abort('failed'); return route.fulfill({ json: draft }); });
  await page.route('**/api/v1/cujammu/polls/51/publish', route => { expect(route.request().postDataJSON()).toEqual({ expectedRevision: 0 }); return route.fulfill({ json: { ...openPoll, canManage: true, revision: 1 } }); });
  await page.route('**/api/v1/cujammu/polls/51/close', route => { expect(route.request().postDataJSON()).toEqual({ expectedRevision: 1 }); return route.fulfill({ json: { ...openPoll, state: 'CLOSED', canManage: true, canVote: false, revision: 2 } }); });
  await open(page); await page.getByRole('textbox', { name: 'Poll question', exact: true }).fill('Best presentation?'); await page.getByRole('textbox', { name: 'Options (one per line)', exact: true }).fill('Alpha\nBeta');
  await page.getByRole('button', { name: 'Create draft', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('could not be confirmed');
  await page.getByRole('button', { name: 'Refresh poll events', exact: true }).click(); await expect(page.getByRole('textbox', { name: 'Poll question', exact: true })).toHaveValue('Best presentation?');
  await page.getByRole('button', { name: 'Retry poll draft', exact: true }).click(); await expect(page.getByRole('textbox', { name: 'Poll question', exact: true })).toHaveValue('');
  expect(posts).toHaveLength(2); expect(posts[1]).toEqual(posts[0]);
  await page.getByRole('button', { name: 'Publish poll', exact: true }).click(); await page.getByRole('button', { name: 'Close poll now', exact: true }).click();
  await expect(page.getByTestId('poll-51')).toContainText('CLOSED');
});
test('event switch clears draft and vote choice; denial clears private poll content', async ({ page }) => {
  await prepare(page, true); let denied = false;
  await page.route('**/api/v1/cujammu/events/*/polls?page=0', route => { if (denied) return route.fulfill({ status: 403, json: {} }); const eventId = route.request().url().includes('/events/2/') ? 2 : 1; return route.fulfill({ json: { items: [{ ...openPoll, eventId, id: eventId === 1 ? 51 : 52, question: `Private question ${eventId}` }], page: 0, hasMore: false } }); });
  await open(page); await page.getByRole('textbox', { name: 'Poll question', exact: true }).fill('Unsaved draft'); await page.getByRole('button', { name: 'Choose Alpha', exact: true }).click();
  await page.getByRole('button', { name: 'Select Event 2', exact: true }).click(); await expect(page.getByRole('textbox', { name: 'Poll question', exact: true })).toHaveValue(''); await expect(page.getByRole('button', { name: 'Confirm vote: Alpha', exact: true })).toHaveCount(0);
  await expect(page.getByText('Private question 1', { exact: true })).toHaveCount(0); denied = true;
  await page.getByRole('button', { name: 'Refresh polls', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('cannot access');
  await expect(page.getByText('Private question 2', { exact: true })).toHaveCount(0); await expect(page.getByRole('textbox', { name: 'Poll question', exact: true })).toHaveCount(0);
});
test('runtime event dependency disables the polls tab and clears its screen', async ({ page }) => {
  await prepare(page); await page.route('**/api/v1/cujammu/events/1/polls?page=0', route => route.fulfill({ json: { items: [openPoll], page: 0, hasMore: false } }));
  await open(page); await expect(page.getByTestId('poll-51')).toBeVisible();
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: false, MODULE_POLLS: true } }));
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await openNavigationMenu(page);
  await expect(page.getByRole('tab', { name: 'Audience polls', exact: true })).toHaveCount(0); await expect(page.getByTestId('poll-51')).toHaveCount(0);
});
test('lost vote receipt requires refresh and then displays the original committed choice', async ({ page }) => {
  await prepare(page); let posts = 0;
  await page.route('**/api/v1/cujammu/events/1/polls?page=0', route => route.fulfill({ json: { items: [openPoll], page: 0, hasMore: false } }));
  await page.route('**/api/v1/cujammu/polls/51/vote', route => { posts++; return route.abort('failed'); });
  await page.route('**/api/v1/cujammu/polls/51/results', route => route.fulfill({ json: { ...openPoll, myChoice: 'Alpha', canVote: false, totalVotes: 1, results: [{ option: 'Alpha', votes: 1, percentage: 100 }, { option: 'Beta', votes: 0, percentage: 0 }] } }));
  await open(page); await page.getByRole('button', { name: 'Choose Alpha', exact: true }).click(); await page.getByRole('button', { name: 'Confirm vote: Alpha', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('may already be recorded'); await expect(page.getByRole('button', { name: 'Choose Beta', exact: true })).toBeDisabled();
  await expect(page.getByText('Your confirmed choice: Alpha. This choice cannot be changed.', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Refresh result', exact: true }).click(); await expect(page.getByText('Your confirmed choice: Alpha. This choice cannot be changed.', { exact: true })).toBeVisible(); expect(posts).toBe(1);
});
test('conflicting creation blocks unchanged draft and an edit creates a fresh request key', async ({ page }) => {
  await prepare(page, true); const posts: { requestKey: string; question: string }[] = [];
  await page.route('**/api/v1/cujammu/events/1/polls?page=0', route => route.fulfill({ json: { items: [], page: 0, hasMore: false } }));
  await page.route('**/api/v1/cujammu/events/1/polls', route => { posts.push(route.request().postDataJSON()); return route.fulfill({ status: 409, json: {} }); });
  await open(page); await page.getByRole('textbox', { name: 'Poll question', exact: true }).fill('First question'); await page.getByRole('textbox', { name: 'Options (one per line)', exact: true }).fill('Alpha\nBeta');
  await page.getByRole('button', { name: 'Create draft', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('Edit the draft'); await expect(page.getByRole('button', { name: 'Create draft', exact: true })).toBeDisabled();
  await page.getByRole('textbox', { name: 'Poll question', exact: true }).fill('Changed question'); await page.getByRole('button', { name: 'Create draft', exact: true }).click(); await expect(page.getByRole('button', { name: 'Create draft', exact: true })).toBeDisabled();
  expect(posts).toHaveLength(2); expect(posts[0]!.requestKey).not.toEqual(posts[1]!.requestKey);
});
