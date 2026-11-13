import { goToNavigation, openNavigationMenu } from './helpers/navigation';
import { expect, test, type Page } from '@playwright/test';
const entry = { id: 7, displayName: 'Synthetic Graduate', department: 'TEST', graduationYear: 2020, degree: 'MSc', currentCompany: null, currentDesignation: null, location: null, linkedInUrl: null, bio: null, willingToMentor: true, willingToRefer: false };
const profile = { ...entry, version: 1, verifiedAlumni: true, directoryVisible: false, showCareer: false, showLocation: false, showBio: false, showLinkedIn: false };
const job = { id: 1, version: 0, companyName: 'Synthetic Ltd', jobTitle: 'Engineer', jobType: 'FULL_TIME', location: null, description: 'Synthetic description', applicationLinkOrEmail: 'https://example.invalid/apply', targetDepartment: 'ALL', active: true, postedAt: '2026-10-02T12:00:00' };
async function setup(page: Page, role = 'STUDENT') {
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic User', role } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_ALUMNI: true } }));
  await page.route('**/api/v1/cujammu/alumni?page=*', route => route.fulfill({ json: { items: [entry], page: 0, hasNext: false } }));
  await page.goto('/app/');
  await page.getByRole('button', { name: 'Skip introduction' }).click();
  await page.getByRole('button', { name: 'Explore alumni' }).click();
}

test('alumni creates private profile from 204 with CSRF and explicit opt-in, then disabled module clears view', async ({ page }, testInfo) => {
  let saved: Record<string, unknown> | null = null;
  await page.route('**/api/v1/cujammu/alumni/profile', async route => {
    if (route.request().method() === 'GET') return route.fulfill({ status: 204 });
    expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf');
    saved = route.request().postDataJSON();
    return route.fulfill({ json: { ...profile, verifiedAlumni: false, degree: saved!.degree, graduationYear: saved!.graduationYear, directoryVisible: saved!.directoryVisible } });
  });
  await setup(page);
  await expect(page.getByText('Synthetic Graduate', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'My profile', exact: true }).click();
  await page.getByRole('textbox', { name: 'Degree (required)', exact: true }).fill('MSc');
  await page.getByRole('textbox', { name: 'Graduation year (required)', exact: true }).fill('2020');
  await expect(page.getByRole('checkbox', { name: 'List my verified profile in the directory' })).not.toBeChecked();
  await page.getByRole('button', { name: 'Save profile & privacy', exact: true }).click();
  await expect(page.getByText('Profile and visibility saved. Directory listing requires independent verification and your opt-in.')).toBeVisible();
  expect(saved).toMatchObject({ expectedVersion: 0, directoryVisible: false, degree: 'MSc', graduationYear: 2020 });
  expect(saved).not.toHaveProperty('verifiedAlumni');
  await page.screenshot({ path: testInfo.outputPath('alumni-profile.png'), fullPage: true });
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_ALUMNI: false } }));
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await openNavigationMenu(page);
  await expect(page.getByRole('tab', { name: 'Alumni', exact: true })).toHaveCount(0);
  await expect(page.getByText('My alumni profile', { exact: true })).toHaveCount(0);
});

test('verified alumni retries identical job request and confirms closure', async ({ page }) => {
  const submissions: Record<string, unknown>[] = []; let closed = false;
  await page.route('**/api/v1/cujammu/alumni/profile', route => route.fulfill({ json: profile }));
  await page.route('**/api/v1/cujammu/alumni/jobs/mine?page=*', route => route.fulfill({ json: { items: submissions.length >= 2 ? [{ ...job, active: !closed, version: closed ? 1 : 0 }] : [], page: 0, hasNext: false } }));
  await page.route('**/api/v1/cujammu/alumni/jobs', route => {
    submissions.push(route.request().postDataJSON());
    return route.fulfill(submissions.length === 1 ? { status: 503, json: {} } : { json: job });
  });
  await page.route('**/api/v1/cujammu/alumni/jobs/1/state', route => {
    expect(route.request().postDataJSON()).toEqual({ expectedVersion: 0, active: false }); closed = true;
    return route.fulfill({ json: { ...job, active: false, version: 1 } });
  });
  await setup(page);
  await page.getByRole('button', { name: 'My listings', exact: true }).click();
  await page.getByRole('textbox', { name: 'Company name (required)' }).fill('Synthetic Ltd');
  await page.getByRole('textbox', { name: 'Job title (required)' }).fill('Engineer');
  await page.getByRole('textbox', { name: 'Description (required)' }).fill('Synthetic description');
  await page.getByRole('textbox', { name: 'Application HTTPS link or email (required)' }).fill('https://example.invalid/apply');
  await page.getByRole('button', { name: 'Publish opportunity', exact: true }).click();
  await expect(page.getByText(/Result is uncertain/)).toBeVisible();
  await page.getByRole('button', { name: 'Retry same listing', exact: true }).click();
  await expect(page.getByText('Your job listing was confirmed by the server.')).toBeVisible();
  expect(submissions).toHaveLength(2); expect(submissions[0]).toEqual(submissions[1]);
  await page.getByRole('button', { name: 'Close listing', exact: true }).click();
  expect(closed).toBe(false);
  await page.getByRole('button', { name: 'Confirm change', exact: true }).click();
  await expect(page.getByText('Department: ALL · Closed')).toBeVisible();
  expect(closed).toBe(true);
});

test('admin verification requires review confirmation and displays authoritative rejection', async ({ page }) => {
  let verified = false; let attempts = 0;
  await page.route('**/api/v1/cujammu/alumni/review?page=*', route => route.fulfill({ json: { items: verified ? [] : [{ ...profile, verifiedAlumni: false }], page: 0, hasNext: false } }));
  await page.route('**/api/v1/cujammu/alumni/profiles/7/verification', route => {
    attempts++; expect(route.request().postDataJSON()).toEqual({ expectedVersion: 1, verified: true });
    if (attempts === 1) return route.fulfill({ status: 403, json: {} });
    verified = true; return route.fulfill({ json: profile });
  });
  await setup(page, 'ADMIN');
  await page.getByRole('button', { name: 'Verification review', exact: true }).click();
  await page.getByRole('button', { name: 'Review verification approval', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Confirm change', exact: true })).toBeDisabled();
  await page.getByRole('checkbox', { name: 'I reviewed the institutional evidence and have authority to decide' }).click();
  await page.getByRole('button', { name: 'Confirm change', exact: true }).click();
  await expect(page.getByText('Your account cannot access this information.')).toBeVisible();
  expect(verified).toBe(false);
  await page.getByRole('button', { name: 'Confirm change', exact: true }).click();
  await expect(page.getByText('No profiles are awaiting review on this page.')).toBeVisible();
  expect(verified).toBe(true);
});
