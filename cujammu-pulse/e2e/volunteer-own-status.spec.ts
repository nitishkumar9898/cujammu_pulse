import { test, expect } from '@playwright/test';
import { goToNavigation } from './helpers/navigation';

test('student can page own historical applications and clears stale status on denied refresh', async ({ page }) => {
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 41, name: 'Synthetic Student', role: 'STUDENT' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: true } }));
  await page.route('**/api/v1/cujammu/events/operations/volunteer-opportunities', route => route.fulfill({ json: { items: [] } }));
  await page.route('**/api/v1/cujammu/events/operations/my-operator-events', route => route.fulfill({ json: { items: [] } }));
  let denied = false;
  await page.route('**/api/v1/cujammu/events/operations/my-volunteer-applications?*', route => {
    if (denied) return route.fulfill({ status: 403 });
    const index = Number(new URL(route.request().url()).searchParams.get('page'));
    return route.fulfill({ json: { items: [{ id: index + 1, revision: 2, status: index === 0 ? 'ACCEPTED' : 'REJECTED', eventId: index + 10,
      eventTitle: index === 0 ? 'Completed synthetic event' : 'Cancelled synthetic event', eventStatus: index === 0 ? 'COMPLETED' : 'CANCELLED',
      eventDate: '2035-01-01T10:00:00', appliedAt: '2034-12-01T10:00:00', decidedAt: '2034-12-02T10:00:00' }], page: index, size: 20, hasNext: index === 0 } });
  });
  await page.goto('/app/'); await page.getByRole('button', { name: 'Skip introduction', exact: true }).click();
  await goToNavigation(page, 'Volunteers');
  await expect(page.getByText('Application status: Accepted', { exact: true })).toBeVisible();
  await expect(page.getByText('Completed synthetic event', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Apply to volunteer', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Next application page', exact: true }).click();
  await expect(page.getByText('Application status: Rejected', { exact: true })).toBeVisible();
  await expect(page.getByText('Completed synthetic event', { exact: true })).toHaveCount(0);
  denied = true; await page.getByRole('button', { name: 'Refresh my applications', exact: true }).click();
  await expect(page.getByText('Your application status could not be loaded. Refresh to check the saved state.', { exact: true })).toBeVisible();
  await expect(page.getByText('Cancelled synthetic event', { exact: true })).toHaveCount(0);
});
