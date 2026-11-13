import { test, expect, type Page } from '@playwright/test';
import { goToNavigation } from './helpers/navigation';
const candidate = { studentId: 7, name: 'Synthetic student', email: 'student@example.invalid', verifiedIdentifier: null, verifiedDepartmentId: null, bindingState: 'UNVERIFIED', credentialVersion: 4 };
async function setup(page: Page, role = 'ADMIN') {
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic office', role } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: {} }));
  await page.route('**/api/v1/cujammu/departments', route => route.fulfill({ json: [{ id: 3, code: 'SYN', name: 'Synthetic Engineering', building: 'Demo block' }] }));
  await page.goto('/app/'); await page.getByRole('button', { name: 'Skip introduction' }).click();
}
async function findStudent(page: Page) {
  await goToNavigation(page, 'Student verification');
  await page.getByRole('textbox', { name: 'Student account email', exact: true }).fill(candidate.email);
  await page.getByRole('button', { name: 'Find student account', exact: true }).click();
}
async function completeForm(page: Page) {
  await page.getByRole('textbox', { name: 'Official roll or enrollment number', exact: true }).fill('0000123');
  await page.getByRole('button', { name: 'Synthetic Engineering', exact: true }).click();
  await page.getByRole('textbox', { name: 'Official source reference', exact: true }).fill('Synthetic register A');
  await page.getByRole('textbox', { name: 'Verification review reason', exact: true }).fill('Checked synthetic source');
  await expect(page.getByRole('button', { name: 'Save verified student identity', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'I checked this account, identifier and department against the official source', exact: true }).click();
}
test('office looks up exact account, explicitly verifies and sees only confirmed receipt', async ({ page }) => {
  let command: unknown; let lookup: unknown;
  await page.route('**/api/v1/admin/students/lookup', route => { lookup = route.request().postDataJSON(); return route.fulfill({ json: candidate }); });
  await page.route('**/api/v1/admin/students/7/verified-identifier', route => {
    command = route.request().postDataJSON(); expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf');
    return route.fulfill({ json: { studentId: 7, verifiedIdentifier: '0000123', verifiedAt: '2035-04-02T10:00:00', credentialVersion: 5 } });
  });
  await setup(page); await findStudent(page); await completeForm(page);
  await page.getByRole('button', { name: 'Save verified student identity', exact: true }).click();
  await expect(page.getByText('Student identity verified and saved.', { exact: true })).toBeVisible();
  await expect(page.getByText('Verified identifier: 0000123', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Save verified student identity', exact: true })).toHaveCount(0);
  expect(lookup).toEqual({ email: candidate.email });
  expect(command).toEqual({ expectedCredentialVersion: 4, identifier: '0000123', departmentId: 3, officialSourceReference: 'Synthetic register A', reviewReason: 'Checked synthetic source' });
  await page.getByRole('textbox', { name: 'Student account email', exact: true }).fill('another@example.invalid');
  await expect(page.getByText('Verified identifier: 0000123', { exact: true })).toHaveCount(0);
});
test('uncertain save requires lookup recovery and never blindly repeats the write', async ({ page }) => {
  let writes = 0;
  await page.route('**/api/v1/admin/students/lookup', route => route.fulfill({ json: writes ? { ...candidate, bindingState: 'VERIFIED', verifiedIdentifier: '0000123', verifiedDepartmentId: 3, credentialVersion: 5 } : candidate }));
  await page.route('**/api/v1/admin/students/7/verified-identifier', route => { writes++; return route.fulfill({ status: 503, json: { error: 'UNKNOWN_OUTCOME' } }); });
  await setup(page); await findStudent(page); await completeForm(page);
  await page.getByRole('button', { name: 'Save verified student identity', exact: true }).click();
  await expect(page.getByText(/Verification was not confirmed/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Save verified student identity', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Find student account', exact: true }).click();
  await expect(page.getByText('This account already has a verified binding.', { exact: true })).toBeVisible(); expect(writes).toBe(1);
});
test('student does not receive office verification navigation', async ({ page }) => {
  await setup(page, 'STUDENT');
  const menu = page.getByRole('button', { name: /Open campus menu/ }); if (await menu.isVisible()) await menu.click();
  await expect(page.getByRole('tab', { name: 'Student verification', exact: true })).toHaveCount(0);
});
