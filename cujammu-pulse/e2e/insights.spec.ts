import { goToNavigation, openNavigationMenu } from './helpers/navigation';
import { expect, test, type Page } from '@playwright/test';

async function openFeedback(page: Page, role = 'STUDENT') {
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Account', role } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_INSIGHTS: true } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'synthetic' } }));
  await page.route('**/api/v1/cujammu/insights/events/mine*', route => route.fulfill({ json: { items: [
    { registrationId: 11, eventId: 7, title: 'Synthetic completed event', eventDate: '2026-10-17T10:00:00', venue: 'Hall', status: 'REGISTERED' },
    { registrationId: 12, eventId: 8, title: 'Another synthetic event', eventDate: '2026-10-18T10:00:00', venue: 'Auditorium', status: 'REGISTERED' },
  ], page: 0, hasMore: false } }));
  await page.route('**/api/v1/cujammu/events/operations/mine', route => route.fulfill({ json: { items: [{ id: 7, title: 'Synthetic managed event', status: 'COMPLETED', eventDate: '2026-10-17T10:00:00', operationsRevision: 2 }] } }));
  await page.route('**/api/v1/cujammu/recovery/library/active', route => route.fulfill({ json: [] }));
  await page.goto('/app/');
  await page.getByRole('button', { name: 'Skip introduction' }).click();
  await goToNavigation(page, 'Feedback & insights');
}
const receipt = { id: 6, eventId: 7, rating: 5, comment: 'Synthetic feedback', createdAt: '2026-10-18T10:00:00' };

test('participant receives persisted receipt without requesting confidential summary; retry preserves payload', async ({ page }) => {
  let reads = 0;
  const submissions: unknown[] = [];
  await page.route('**/api/v1/cujammu/insights/events/7/feedback**', route => {
    if (route.request().method() === 'GET') { reads++; return route.fulfill({ status: 403, json: { message: 'Restricted' } }); }
    submissions.push(route.request().postDataJSON());
    return route.fulfill(submissions.length === 1 ? { status: 503, json: { message: 'Unconfirmed' } } : { json: receipt });
  });
  await openFeedback(page);
  await page.getByRole('radio', { name: /Synthetic completed event/ }).click();
  await page.getByRole('textbox', { name: 'Comment (optional)', exact: true }).fill('Synthetic feedback');
  await page.getByRole('button', { name: 'Submit feedback', exact: true }).click();
  await expect(page.getByRole('textbox', { name: 'Comment (optional)', exact: true })).not.toBeEditable();
  await page.getByRole('button', { name: 'Retry same feedback', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Your feedback is recorded', exact: true })).toBeVisible();
  expect(submissions).toEqual([{ rating: 5, comment: 'Synthetic feedback' }, { rating: 5, comment: 'Synthetic feedback' }]);
  expect(reads).toBe(0);
  await expect(page.getByRole('button', { name: 'Load event insights', exact: true })).toHaveCount(0);
});

test('own receipt can be recovered and switching event clears private content', async ({ page }) => {
  await page.route('**/api/v1/cujammu/insights/events/7/feedback/mine', route => route.fulfill({ json: receipt }));
  await page.route('**/api/v1/cujammu/insights/events/8/feedback/mine', route => route.fulfill({ status: 204 }));
  await openFeedback(page);
  const event = page.getByRole('radio', { name: /Synthetic completed event/ });
  await event.click();
  await page.getByRole('button', { name: 'Check my feedback', exact: true }).click();
  await expect(page.getByText('Synthetic feedback', { exact: true })).toBeVisible();
  await page.getByRole('radio', { name: /Another synthetic event/ }).click();
  await expect(page.getByText('Synthetic feedback', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Check my feedback', exact: true }).click();
  await expect(page.getByText('No saved response was found for your account on this event.', { exact: true })).toBeVisible();
});

test('manager summary remains explicitly requested and denial clears prior confidential responses', async ({ page }) => {
  let allowed = true;
  await page.route('**/api/v1/cujammu/insights/events/7/feedback', route => route.fulfill(allowed ? { json: { eventId: 7, averageRating: 5, totalResponses: 1, recent: [receipt] } } : { status: 403, json: { message: 'Access denied' } }));
  await openFeedback(page, 'FACULTY');
  await page.getByRole('radio', { name: /Synthetic managed event/ }).click();
  await page.getByRole('button', { name: 'Load event insights', exact: true }).click();
  await expect(page.getByText('Synthetic feedback', { exact: true })).toBeVisible();
  allowed = false;
  await page.getByRole('button', { name: 'Load event insights', exact: true }).click();
  await expect(page.getByText('Synthetic feedback', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('alert')).toBeVisible();
});
