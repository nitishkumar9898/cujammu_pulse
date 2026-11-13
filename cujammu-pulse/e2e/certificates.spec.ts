import { goToNavigation, openNavigationMenu } from './helpers/navigation';
import { test, expect, type Page } from '@playwright/test';
async function setup(page: Page, role = 'STUDENT') {
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Account', role } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: true, MODULE_CERTIFICATES: true } }));
}
async function start(page: Page) { await page.goto('/app/'); await page.getByRole('button', { name: 'Skip introduction' }).click(); }
test('certificates display only issued service records and never query attendance for eligibility', async ({ page }) => {
  await setup(page); let registrationReads = 0;
  await page.route('**/api/v1/cujammu/events/registrations/mine', route => { registrationReads++; return route.fulfill({ json: [{ id: 2, eventId: 7, status: 'ATTENDED' }] }); });
  await page.route('**/api/v1/cujammu/certificates/own?*', route => route.fulfill({ json: { items: [], page: 0, hasNext: false } }));
  await start(page); await goToNavigation(page, 'Certificates');
  await expect(page.getByText('No issued certificates are available on this page.')).toBeVisible(); await expect(page.getByRole('button', { name: 'Download issued PDF', exact: true })).toHaveCount(0); expect(registrationReads).toBe(0);
});
test('completed managed event finalization requires review and preserves retry key plus unresolved evidence', async ({ page }) => {
  await setup(page, 'FACULTY'); const writes: unknown[] = [];
  const operations = { revision: 4, attendanceMode: 'DUAL_QR', singleQrPolicy: 'ENTRY_ONLY', certificateEvidencePolicy: 'POINT_ATTENDANCE', certificatePolicyVersion: 1, recruitmentOpen: false, maxVolunteerApplicants: 20, scannerMasterEnabled: false, scannerScope: 'OFF', scannerEpoch: 0, manualEpoch: 0, scannerOpensAt: null, scannerClosesAt: null, manualWindowOpen: false, manualOpensAt: null, manualClosesAt: null };
  await page.route('**/api/v1/cujammu/events/operations/mine', route => route.fulfill({ json: { items: [{ id: 7, title: 'Synthetic Completed Event', status: 'COMPLETED', eventDate: '2026-10-01T10:00:00', operationsRevision: 4 }] } }));
  await page.route('**/api/v1/cujammu/events/7/operations', route => route.fulfill({ json: operations }));
  await page.route('**/api/v1/cujammu/events/7/volunteer-applications', route => route.fulfill({ json: { items: [] } }));
  await page.route('**/api/v1/cujammu/events/7/manual-grants', route => route.fulfill({ json: { items: [] } }));
  await page.route('**/api/v1/cujammu/events/7/attendance-export-grants', route => route.fulfill({ json: { items: [] } }));
  await page.route('**/api/v1/cujammu/events/7/lifecycle/history', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/cujammu/events/7/certificates/finalize', route => { writes.push(route.request().postDataJSON()); expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf'); if (writes.length === 1) return route.fulfill({ status: 503, json: {} }); return route.fulfill({ json: { eventId: 7, revision: 1, status: 'FINALIZED_WITH_UNRESOLVED', certificateType: 'POINT_PARTICIPATION', policyVersion: 1, issuedCount: 1, ineligibleCount: 2, unresolvedCount: 3, finalizedAt: '2026-10-10T10:00:00Z' } }); });
  await start(page); await goToNavigation(page, 'Volunteers');
  await expect(page.getByRole('button', { name: 'Enable scanner for all accepted volunteers', exact: true })).toBeDisabled();
  await page.getByRole('textbox', { name: 'Certificate finalization review reason', exact: true }).fill('Reviewed owner-confirmed evidence');
  await page.getByRole('button', { name: 'Review certificate finalization', exact: true }).click(); expect(writes).toHaveLength(0);
  await page.getByRole('button', { name: 'Confirm reviewed finalization', exact: true }).click();
  await expect(page.getByText('The campus service could not complete this request.', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Confirm reviewed finalization', exact: true }).click();
  await expect(page.getByText('FINALIZED_WITH_UNRESOLVED: issued 1, ineligible 2, unresolved 3.', { exact: false })).toBeVisible(); expect(writes[1]).toEqual(writes[0]); expect(writes[0]).toMatchObject({ expectedOperationsRevision: 4, reason: 'Reviewed owner-confirmed evidence' });
});
