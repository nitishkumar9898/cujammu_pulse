import { expect, test } from '@playwright/test';

test('library UI hides unverified entry and exit actions while keeping private history reachable', async ({ page }) => {
  const writes: string[] = [];
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Student', role: 'STUDENT' } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: {
    MODULE_LIBRARY_TURNSTILE: true, ACTION_LIBRARY_ENTRY: true, ACTION_LIBRARY_EXIT: true,
  } }));
  await page.route('**/api/v1/cujammu/library/history**', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/cujammu/library/scan/**', route => { writes.push(route.request().url()); return route.fulfill({ status: 409, json: { code: 'LIBRARY_VERIFICATION_UNAVAILABLE' } }); });

  await page.goto('/app/');
  await page.getByRole('button', { name: 'Skip introduction' }).click();
  await page.getByRole('textbox', { name: 'Search campus services' }).fill('library');
  await page.getByRole('button', { name: 'Open smart library', exact: true }).click();
  await expect(page.getByText(/Entry and exit remain unavailable until this client can verify/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Scan entry QR' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Confirm exit' })).toHaveCount(0);
  await page.getByRole('button', { name: 'View my library history', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Smart library' })).toBeVisible();
  expect(writes).toEqual([]);
});
