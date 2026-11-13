import { goToNavigation } from './helpers/navigation';
import { test, expect } from '@playwright/test';

test('delegated faculty can discover assigned event reports without event-operations access', async ({ page }) => {
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 51, name: 'Synthetic Delegate', role: 'FACULTY' } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: true } }));
  await page.route('**/api/v1/cujammu/events/operations/mine', route => route.fulfill({ json: { items: [] } }));
  await page.route('**/api/v1/cujammu/events/attendance-export-access/mine*', route => route.fulfill({ json: { page: 0, hasNext: false, items: [
    { id: 71, title: 'Synthetic Delegated Event', status: 'COMPLETED', eventDate: '2026-10-01T10:00:00', operationsRevision: 6 },
  ] } }));
  await page.route('**/api/v1/cujammu/events/71/attendance/report.csv', route => route.fulfill({ headers: { 'content-type': 'text/csv', 'cache-control': 'no-store', 'content-disposition': 'attachment; filename=attendance.csv' }, body: 'synthetic report' }));
  await page.goto('/app/');
  await page.getByRole('button', { name: 'Skip introduction' }).click();
  await goToNavigation(page, 'Volunteers');

  await expect(page.getByText('Delegated attendance reports')).toBeVisible();
  await expect(page.getByText('Synthetic Delegated Event', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Download attendance CSV', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Open recruitment', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Enable scanner for all accepted volunteers', exact: true })).toHaveCount(0);
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Download attendance CSV', exact: true }).click();
  expect((await download).suggestedFilename()).toBe('report.csv');
});
