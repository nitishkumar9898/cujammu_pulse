import { goToNavigation, openNavigationMenu } from './helpers/navigation';
import { expect, test } from '@playwright/test';

test('signed-out walkthrough, theme persistence and sign-in link', async ({ page }) => {
  await page.route('**/api/v1/session', route => route.fulfill({ status: 401, json: { code: 'UNAUTHENTICATED' } }));
  await page.goto('/app/');
  await expect(page.getByText('Your campus. Connected.')).toBeVisible();
  await page.getByRole('button', { name: 'Skip introduction' }).click();
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Appearance', exact: true }).click();
  await page.getByRole('radio', { name: 'Dark', exact: true }).click();
  await expect(page.getByRole('radio', { name: 'Dark', exact: true })).toHaveAttribute('aria-checked', 'true');
  await page.reload();
  await page.getByRole('button', { name: 'Appearance', exact: true }).click();
  await expect(page.getByRole('radio', { name: 'Dark', exact: true })).toHaveAttribute('aria-checked', 'true');
});

test('synthetic catalogue search stays focused and feature disable removes venue data', async ({ page }, testInfo) => {
  let venues = true;
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Student', role: 'STUDENT' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_VENUES: venues, MODULE_HOSTEL_GATEPASS: true } }));
  await page.route('**/api/v1/cujammu/departments', route => route.fulfill({ json: [{ id: 1, code: 'TEST', name: 'Synthetic Computing', building: 'Test Wing' }, { id: 2, code: 'TEST2', name: 'Synthetic Physics', building: '' }] }));
  await page.route('**/api/v1/cujammu/facilities', route => route.fulfill({ json: [{ id: 1, name: 'Synthetic Hall', facilityType: 'AUDITORIUM', location: 'Test Campus', capacity: 100 }] }));
  await page.goto('/app/');
  await page.getByRole('button', { name: 'Skip introduction' }).click();
  await expect(page.getByText('Welcome, Synthetic Student')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Open hostel passes', exact: true })).toBeVisible();
  await openNavigationMenu(page);
  await expect(page.getByRole('tab', { name: 'Hostel passes', exact: true })).toBeVisible();
  const brand = await page.getByLabel('CUJAMMU PULSE, Campus Services').boundingBox();
  expect(brand?.height).toBeLessThan(55);
  await page.screenshot({ path: testInfo.outputPath('overview.png'), fullPage: true });
  await page.getByRole('button', { name: 'View departments' }).click();
  const search = page.getByRole('textbox', { name: 'Search departments' });
  await search.pressSequentially('Computing');
  await expect(search).toBeFocused();
  await expect(page.getByText('Synthetic Computing', { exact: true })).toBeVisible();
  await expect(page.getByText('Synthetic Physics', { exact: true })).toHaveCount(0);
  await goToNavigation(page, 'Venues & grounds');
  await expect(page.getByText('Synthetic Hall', { exact: true })).toBeVisible();
  venues = false;
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await openNavigationMenu(page);
  await expect(page.getByRole('tab', { name: 'Venues & grounds' })).toHaveCount(0);
  await expect(page.getByText('Synthetic Hall', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Explore venues' })).toHaveCount(0);
});

test('expired session clears identity and catalogue instead of retaining stale content', async ({ page }) => {
  let expired = false;
  await page.route('**/api/v1/session', route => route.fulfill(expired ? { status: 401, json: {} } : { json: { id: 2, name: 'Synthetic Faculty', role: 'FACULTY' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: {} }));
  await page.goto('/app/');
  await page.getByRole('button', { name: 'Skip introduction' }).click();
  await expect(page.getByText('Welcome, Synthetic Faculty')).toBeVisible();
  expired = true;
  await page.getByRole('button', { name: 'Refresh services' }).click();
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
  await expect(page.getByText('Welcome, Synthetic Faculty')).toHaveCount(0);
  await openNavigationMenu(page);
  await expect(page.getByRole('tab', { name: 'Departments' })).toHaveCount(0);
});
