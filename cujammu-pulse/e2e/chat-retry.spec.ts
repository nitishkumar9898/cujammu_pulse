import { goToNavigation, openNavigationMenu } from './helpers/navigation';
import { test, expect, type Page } from '@playwright/test';

async function prepare(page: Page) {
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Student', role: 'STUDENT' } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_COMMUNITY_CHAT: true } }));
  await page.route('**/api/v1/cujammu/chat/groups/page?page=0', route => route.fulfill({ json: { items: [1, 2].map(id => ({
    id, name: `Department ${id}`, type: 'PERMANENT_DEPARTMENT', nightSilenceEnabled: true, canPost: true, nightSilenceActive: false,
  })), page: 0, hasMore: false } }));
}
async function openChat(page: Page) {
  await page.goto('/app/'); await page.getByRole('button', { name: 'Skip introduction' }).click();
  await goToNavigation(page, 'Community chat');
  await expect(page.getByRole('textbox', { name: 'Message', exact: true })).toBeVisible();
}
const saved = { id: 51, senderName: 'Synthetic Student', content: 'One intended message', createdAt: '2026-10-03T10:00:00' };

test('uncertain send keeps its key across refresh and displays a committed retry only once', async ({ page }) => {
  await prepare(page);
  const posts: Array<{ content: string; requestKey: string }> = [];
  await page.route('**/api/v1/cujammu/chat/groups/1/messages', async route => {
    if (route.request().method() === 'POST') {
      posts.push(route.request().postDataJSON());
      if (posts.length === 1) return route.abort('failed'); // Commit happened but its response was lost.
      return route.fulfill({ json: saved });
    }
    return route.fulfill({ json: { items: posts.length ? [saved] : [] } });
  });
  await openChat(page);
  await page.getByRole('textbox', { name: 'Message', exact: true }).fill(saved.content);
  await page.getByRole('button', { name: 'Send message', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('could not be confirmed');
  expect(posts).toHaveLength(1);
  await page.getByRole('button', { name: 'Refresh groups', exact: true }).click();
  await expect(page.getByTestId(`chat-message-${saved.id}`)).toHaveCount(1);
  await expect(page.getByTestId(`chat-message-${saved.id}`).getByText(saved.content, { exact: true })).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Message', exact: true })).toHaveValue(saved.content);
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(page.getByRole('button', { name: 'Retry message', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Retry message', exact: true }).click();
  await expect(page.getByRole('textbox', { name: 'Message', exact: true })).toHaveValue('');
  expect(posts).toHaveLength(2);
  expect(posts[0]!.requestKey).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
  expect(posts[1]).toEqual(posts[0]);
  await expect(page.getByTestId(`chat-message-${saved.id}`)).toHaveCount(1);
  await expect(page.getByTestId(`chat-message-${saved.id}`).getByText(saved.content, { exact: true })).toBeVisible();
});

test('conflicts require an explicit draft edit and rate limits retain the key for user retry', async ({ page }) => {
  await prepare(page);
  const posts: Array<{ content: string; requestKey: string }> = [];
  await page.route('**/api/v1/cujammu/chat/groups/1/messages', route => {
    if (route.request().method() !== 'POST') return route.fulfill({ json: { items: [] } });
    posts.push(route.request().postDataJSON());
    if (posts.length === 1) return route.fulfill({ status: 409, json: {} });
    if (posts.length === 2) return route.fulfill({ status: 429, headers: { 'Retry-After': '60' }, json: { error: 'CHAT_RATE_LIMITED', retryAfterSeconds: 60 } });
    return route.fulfill({ json: { ...saved, content: posts.at(-1)!.content } });
  });
  await openChat(page);
  await page.getByRole('textbox', { name: 'Message', exact: true }).fill('First draft');
  await page.getByRole('button', { name: 'Send message', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Edit the draft');
  await expect(page.getByRole('button', { name: 'Send message', exact: true })).toBeDisabled();
  expect(posts).toHaveLength(1);
  await page.getByRole('textbox', { name: 'Message', exact: true }).fill('Changed draft');
  await page.getByRole('button', { name: 'Send message', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Wait 60 seconds');
  expect(posts).toHaveLength(2);
  expect(posts[1]!.requestKey).not.toEqual(posts[0]!.requestKey);
  await page.getByRole('button', { name: 'Retry message', exact: true }).click();
  await expect(page.getByRole('textbox', { name: 'Message', exact: true })).toHaveValue('');
  expect(posts).toHaveLength(3);
  expect(posts[2]).toEqual(posts[1]);
});

test('changing groups clears the uncertain draft and gives the new message a different key', async ({ page }) => {
  await prepare(page);
  const posts: Array<{ content: string; requestKey: string }> = [];
  await page.route('**/api/v1/cujammu/chat/groups/*/messages', route => {
    if (route.request().method() !== 'POST') return route.fulfill({ json: { items: [] } });
    posts.push(route.request().postDataJSON());
    if (posts.length === 1) return route.abort('failed');
    return route.fulfill({ json: saved });
  });
  await openChat(page);
  await page.getByRole('textbox', { name: 'Message', exact: true }).fill(saved.content);
  await page.getByRole('button', { name: 'Send message', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Retry message', exact: true })).toBeVisible();
  await page.getByRole('button', { name: /Department 2/ }).click();
  await expect(page.getByRole('textbox', { name: 'Message', exact: true })).toHaveValue('');
  await page.getByRole('textbox', { name: 'Message', exact: true }).fill(saved.content);
  await page.getByRole('button', { name: 'Send message', exact: true }).click();
  await expect(page.getByRole('textbox', { name: 'Message', exact: true })).toHaveValue('');
  expect(posts).toHaveLength(2);
  expect(posts[1]!.requestKey).not.toEqual(posts[0]!.requestKey);
});
