import { chromium, expect } from '@playwright/test';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

// Deliberately outside e2e/: normal mocked acceptance tests never silently skip or execute this live mutation run.
const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const outputName = process.env.LIVE_DEMO_OUTPUT_NAME ?? 'professor-demo';
if (!/^[a-z][a-z0-9-]{0,63}$/.test(outputName)) throw new Error('INVALID_DEMO_OUTPUT_NAME');
const output = path.join(project, 'college-events/target', outputName);
let browser; let password = ''; let currentStep = 'validate isolated demo inputs';
const results = []; const started = new Date().toISOString();
const contexts = [];
const httpFailures = [];
async function step(name, work) { currentStep = name; await work(); results.push({ step: name, status: 'PASS' }); console.log(`PASS: ${name}`); }
try {
  const explicitUrl = process.env.LIVE_DEMO_URL || process.argv[2];
  if (!explicitUrl) throw new Error('EXPLICIT_LOOPBACK_URL_REQUIRED');
  const base = new URL(explicitUrl);
  if (base.protocol !== 'http:' || base.hostname !== '127.0.0.1' || base.username || base.password || base.search || base.hash || !base.port) throw new Error('ISOLATED_LOOPBACK_ONLY');
  const credentials = JSON.parse((await readFile(path.join(output, 'session-credentials.json'), 'utf8')).replace(/^\uFEFF/, ''));
  if (credentials.mode !== 'SYNTHETIC_LOCAL_REVIEW' || new URL(credentials.url).origin !== base.origin || !Array.isArray(credentials.accounts) || credentials.accounts.some(value => typeof value !== 'string' || !value.endsWith('@example.invalid')) || typeof credentials.password !== 'string' || credentials.password.length < 20) throw new Error('ISOLATED_CREDENTIALS_REQUIRED');
  password = credentials.password;
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  const pages = {};
  async function readyApp(page) {
    const skip = page.getByRole('button', { name: 'Skip introduction', exact: true });
    const events = page.getByRole('tab', { name: 'Events', exact: true });
    await expect(skip.or(events).first()).toBeVisible({ timeout: 15000 });
    if (await skip.isVisible()) await skip.click();
    await expect(events).toBeVisible({ timeout: 15000 });
  }
  async function login(label) {
    const callerStep = currentStep;
    const context = await browser.newContext({ baseURL: base.origin, viewport: { width: 1280, height: 900 } }); contexts.push(context);
    const page = await context.newPage(); page.setDefaultTimeout(15000);
    page.on('response', response => { const url = new URL(response.url()); if (url.origin === base.origin && url.pathname.startsWith('/api/') && response.status() >= 400) httpFailures.push({ step: currentStep, method: response.request().method(), path: url.pathname, status: response.status() }); });
    currentStep = `synthetic ${label} login: load form`;
    await page.goto('/app/sign-in', { waitUntil: 'domcontentloaded' });
    currentStep = `synthetic ${label} login: enter form`;
    await page.locator('input[name="username"]').fill(`${label}@example.invalid`);
    await page.locator('input[name="password"]').fill(password);
    currentStep = `synthetic ${label} login: submit and navigate`;
    await Promise.all([page.waitForURL('**/app/**'), page.locator('form[action="/login"] button[type="submit"]').click()]);
    currentStep = `synthetic ${label} login: verify persisted session`;
    const session = await page.request.get('/api/v1/session'); if (!session.ok() || (await session.json()).name !== `Synthetic ${label}`) throw new Error('SYNTHETIC_SESSION_NOT_VERIFIED');
    currentStep = `synthetic ${label} login: shared application walkthrough`;
    await readyApp(page);
    currentStep = callerStep;
    pages[label] = page; return page;
  }
  async function reloadApp(page, tab) { await page.goto('/app/'); await readyApp(page); if (tab) await page.getByRole('tab', { name: tab, exact: true }).click(); }
  async function getJson(page, url) { const response = await page.request.get(url); if (!response.ok()) throw new Error(`READ_HTTP_${response.status()}`); return response.json(); }
  async function forbiddenPost(page, url, body) { const csrf = await getJson(page, '/api/v1/session/csrf'); const response = await page.request.post(url, { headers: { [csrf.headerName]: csrf.token }, data: body }); if (response.status() !== 403) throw new Error(`EXPECTED_DENY_HTTP_${response.status()}`); }
  const tag = Date.now().toString(36); let eventId; let registrationId; let foundId; let claimId; let scoreId;
  await step('actual synthetic student browser login', async () => { await login('student'); });
  await step('unverified library scan actions stay disabled even for an administrator', async () => {
    const page = await login('admin');
    const controls = await getJson(page, '/api/v1/cujammu/admin/feature-controls');
    const entry = controls.items.find(item => item.key === 'ACTION_LIBRARY_ENTRY');
    if (!entry || entry.effectiveEnabled !== false) throw new Error('LIBRARY_ENTRY_NOT_FAIL_CLOSED');
    const csrf = await getJson(page, '/api/v1/session/csrf');
    const response = await page.request.put('/api/v1/cujammu/admin/feature-controls/ACTION_LIBRARY_ENTRY', {
      headers: { [csrf.headerName]: csrf.token },
      data: { enabled: true, expectedRevision: entry.revision, reason: 'Synthetic compatibility check; no change expected.' },
    });
    if (response.status() !== 400) throw new Error(`LIBRARY_ENTRY_ENABLE_NOT_REJECTED_${response.status()}`);
    const after = await getJson(page, '/api/v1/cujammu/admin/feature-controls');
    if (after.items.find(item => item.key === 'ACTION_LIBRARY_ENTRY')?.effectiveEnabled !== false) throw new Error('LIBRARY_ENTRY_ENABLED_AFTER_REJECTION');
  });
  await step('office verifies an unbound synthetic student and authoritative lookup survives reload', async () => {
    const page = pages.admin; await page.getByRole('tab', { name: 'Student verification', exact: true }).click();
    await page.getByRole('textbox', { name: 'Student account email', exact: true }).fill('applicant@example.invalid');
    await page.getByRole('button', { name: 'Find student account', exact: true }).click();
    await page.getByRole('textbox', { name: 'Official roll or enrollment number', exact: true }).fill('000003');
    await page.getByRole('textbox', { name: 'Search active departments', exact: true }).fill('Synthetic Engineering');
    await page.getByRole('button', { name: 'Synthetic Engineering', exact: true }).click();
    await page.getByRole('textbox', { name: 'Official source reference', exact: true }).fill('Synthetic review register only');
    await page.getByRole('textbox', { name: 'Verification review reason', exact: true }).fill('Synthetic office workflow test; no real university identity');
    await page.getByRole('button', { name: 'I checked this account, identifier and department against the official source', exact: true }).click();
    await page.getByRole('button', { name: 'Save verified student identity', exact: true }).click();
    await expect(page.getByText('Student identity verified and saved.', { exact: true })).toBeVisible();
    await reloadApp(page, 'Student verification');
    await page.getByRole('textbox', { name: 'Student account email', exact: true }).fill('applicant@example.invalid');
    await page.getByRole('button', { name: 'Find student account', exact: true }).click();
    await expect(page.getByText('Verified identifier: 000003', { exact: true })).toBeVisible();
    await expect(page.getByText('Verified department: Synthetic Engineering', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Save verified student identity', exact: true })).toHaveCount(0);
    await page.screenshot({ path: path.join(output, 'synthetic-student-verification.png'), fullPage: true });
    await forbiddenPost(pages.student, '/api/v1/admin/students/lookup', { email: 'applicant@example.invalid' });
  });
  await step('event registration persists across browser reload', async () => {
    const page = pages.student; await page.getByRole('tab', { name: 'Events', exact: true }).click();
    const events = await getJson(page, '/api/v1/cujammu/events/catalog'); const event = events.find(item => item.title === 'Synthetic professor review workshop'); if (!event) throw new Error('SYNTHETIC_EVENT_MISSING'); eventId = event.id;
    const before = await getJson(page, '/api/v1/cujammu/events/registrations/mine');
    await page.getByRole('button', { name: 'Check schedule clashes', exact: true }).click();
    await expect(page.getByText('This event has no confirmed end time. A complete overlap check is not possible.', { exact: true })).toBeVisible();
    const advice = await getJson(page, `/api/v1/cujammu/events/calendar/clashes/${eventId}`);
    if (advice.eventId !== eventId || advice.endsAt !== null || advice.items.length !== 0 || !Number.isSafeInteger(advice.lifecycleVersion)) throw new Error('SYNTHETIC_CLASH_ADVICE_INVALID');
    const after = await getJson(page, '/api/v1/cujammu/events/registrations/mine');
    if (JSON.stringify(before) !== JSON.stringify(after)) throw new Error('CLASH_CHECK_MUTATED_REGISTRATION');
    await page.getByRole('button', { name: 'Register', exact: true }).click(); await expect(page.getByRole('button', { name: 'Registered', exact: true })).toBeDisabled();
    await reloadApp(page, 'Events'); const rows = await getJson(page, '/api/v1/cujammu/events/registrations/mine'); const own = rows.find(item => item.eventId === eventId); if (own?.status !== 'REGISTERED') throw new Error('PERSISTED_REGISTRATION_NOT_VERIFIED'); registrationId = own.id;
    await expect(page.getByRole('button', { name: 'Withdraw registration', exact: true })).toBeVisible();
  });
  await step('verified student receives the private office notice in the actual inbox', async () => {
    const page = await login('applicant'); await page.getByRole('tab', { name: 'Notifications', exact: true }).click();
    await expect(page.getByText('Your university student identifier and department have been verified. Contact the university office if this is unexpected.', { exact: true })).toBeVisible();
  });
  await step('actual calendar exposes lifecycle and unknown duration without inferring attendance', async () => {
    const page = pages.student; await page.getByRole('tab', { name: 'Calendar', exact: true }).click();
    await expect(page.getByText('Current approved schedule', { exact: true })).toBeVisible();
    await expect(page.getByText('End time not supplied; duration is unknown.', { exact: true })).toBeVisible();
    await expect(page.getByText('Registration confirmed', { exact: true })).toBeVisible();
    await reloadApp(page, 'Events');
  });
  await step('confirmed registration withdrawal persists after reload', async () => {
    const page = pages.student; await page.getByRole('button', { name: 'Withdraw registration', exact: true }).click(); await page.getByRole('button', { name: 'Confirm withdrawal', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Cancelled', exact: true })).toBeDisabled(); await reloadApp(page, 'Events');
    const own = (await getJson(page, '/api/v1/cujammu/events/registrations/mine')).find(item => item.id === registrationId); if (own?.status !== 'CANCELLED') throw new Error('WITHDRAWAL_NOT_PERSISTED');
  });
  const title = `Synthetic found keys ${tag}`; const evidence = `Synthetic demonstration identifying mark ${tag}; no real property or identity data.`;
  await step('finder reports categorized synthetic property through actual UI', async () => {
    const page = await login('finder'); await page.getByRole('tab', { name: 'Lost & Found', exact: true }).click();
    await page.getByRole('textbox', { name: 'Item title', exact: true }).fill(title); await page.getByRole('checkbox', { name: /Report category: Keys$/ }).click();
    await page.getByRole('textbox', { name: 'Description', exact: true }).fill('Synthetic review example. No real property was found.'); await page.getByRole('textbox', { name: 'Location found', exact: true }).fill('Synthetic Demo Hall');
    await page.getByRole('button', { name: 'Report found item', exact: true }).click(); await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
    const item = (await getJson(page, '/api/v1/cujammu/lost-and-found?page=0')).items.find(item => item.title === title); if (!item || !item.canReview || item.category !== 'KEYS') throw new Error('FOUND_REPORT_NOT_PERSISTED'); foundId = item.id;
  });
  await step('student submits private evidence without final handover', async () => {
    const page = pages.student; await page.getByRole('tab', { name: 'Lost & Found', exact: true }).click(); await page.getByRole('button', { name: `View or submit my claim: ${title}`, exact: true }).click();
    await page.getByRole('textbox', { name: 'Private ownership evidence', exact: true }).fill(evidence); await page.getByRole('button', { name: 'Submit private claim', exact: true }).click();
    await expect(page.getByText(/^Claim \d+ · PENDING$/)).toBeVisible();
    await expect(page.getByRole('textbox', { name: 'Private ownership evidence', exact: true })).toHaveValue('');
    const claims = await getJson(page, `/api/v1/cujammu/lost-and-found/${foundId}/claims?page=0`); const own = claims.find(item => item.evidence === evidence); if (!own || own.status !== 'PENDING') throw new Error('PRIVATE_CLAIM_NOT_PENDING'); claimId = own.id;
    if ((await getJson(page, '/api/v1/cujammu/lost-and-found?page=0')).items.find(item => item.id === foundId)?.status === 'HANDED_OVER') throw new Error('CLAIM_MUST_NOT_AUTO_HANDOVER');
  });
  await step('unrelated faculty cannot read private claim or perform handover', async () => {
    const page = await login('faculty'); if ((await getJson(page, `/api/v1/cujammu/lost-and-found/${foundId}/claims?page=0`)).length !== 0) throw new Error('PRIVATE_CLAIM_DISCLOSURE');
    const found = (await getJson(page, '/api/v1/cujammu/lost-and-found?page=0')).items.find(item => item.id === foundId);
    await forbiddenPost(page, `/api/v1/cujammu/lost-and-found/${foundId}/hand-over`, { requestKey: crypto.randomUUID(), expectedVersion: found.version, claimId, verificationReason: 'Synthetic unauthorized negative test' });
  });
  await step('finder confirms a synthetic reviewed handover and persistence', async () => {
    const page = pages.finder; await reloadApp(page, 'Lost & Found'); await page.getByRole('button', { name: `Review private claims: ${title}`, exact: true }).click();
    await page.getByRole('button', { name: `Verify handover for claim ${claimId}`, exact: true }).click(); await page.getByRole('textbox', { name: 'Handover verification reason', exact: true }).fill('Synthetic simulated review; no real property was handed over.');
    await page.getByRole('button', { name: 'Confirm verified handover', exact: true }).click();
    await expect(page.getByText('Keys · HANDED_OVER · Synthetic Demo Hall', { exact: true })).toBeVisible();
    const item = (await getJson(page, '/api/v1/cujammu/lost-and-found?page=0')).items.find(item => item.id === foundId); if (item?.status !== 'HANDED_OVER') throw new Error('HANDOVER_NOT_PERSISTED');
  });
  const semester = `DEMO-${tag}`;
  await step('faculty submits numeric synthetic scorecard for independent review', async () => {
    const page = pages.faculty; await reloadApp(page, 'Academic records');
    for (const [name, value] of [['Verified roll/enrollment number', '000001'], ['Semester', semester], ['Academic year', '2026-27'], ['SGPA (0–10)', '8.25'], ['CGPA (0–10)', '8.1']]) await page.getByRole('textbox', { name, exact: true }).fill(value);
    await page.getByRole('button', { name: 'Submit for independent review', exact: true }).click(); await expect(page.getByText(/Submission PENDING_REVIEW/)).toBeVisible();
    const reviewer = await login('reviewer'); const queue = await getJson(reviewer, '/api/v1/cujammu/exams/scorecards/review?department=Synthetic%20Engineering&page=0'); const row = queue.items.find(item => item.semester === semester); if (!row || row.verifiedIdentifier !== '000001') throw new Error('ACADEMIC_REVIEW_ITEM_MISSING'); scoreId = row.id;
    await forbiddenPost(page, `/api/v1/cujammu/exams/scorecards/${scoreId}/review`, { publish: true, reason: null });
    const own = await getJson(pages.student, '/api/v1/cujammu/exams/scorecards/my'); if (own.some(item => item.semester === semester)) throw new Error('UNREVIEWED_RESULT_VISIBLE');
  });
  await step('independent reviewer publishes and student sees persisted own numeric result', async () => {
    const reviewer = pages.reviewer; await reviewer.getByRole('tab', { name: 'Academic records', exact: true }).click(); await expect(reviewer.getByText(semester, { exact: false })).toBeVisible();
    await reviewer.getByRole('button', { name: 'Approve and publish', exact: true }).click(); await expect(reviewer.getByRole('button', { name: 'Approve and publish', exact: true })).toHaveCount(0);
    const student = pages.student; await reloadApp(student, 'Academic records'); await expect(student.getByText(`${semester} · 2026-27`, { exact: true })).toBeVisible();
    const record = (await getJson(student, '/api/v1/cujammu/exams/scorecards/my')).find(item => item.semester === semester); if (!record || record.sgpa !== 8.25 || record.cgpa !== 8.1 || record.version !== 1) throw new Error('PUBLISHED_RESULT_NOT_VERIFIED');
    await student.screenshot({ path: path.join(output, 'synthetic-approved-scorecard.png'), fullPage: true });
  });
  await mkdir(output, { recursive: true }); await writeFile(path.join(output, 'live-ui-report.json'), JSON.stringify({ mode: 'SYNTHETIC_LOCAL_REVIEW', origin: base.origin, startedAt: started, finishedAt: new Date().toISOString(), outcome: 'PASS', scenarios: results }, null, 2));
  console.log(`Live browser/backend verification: ${results.length} scenarios passed. Only synthetic records were used.`);
} catch (error) {
  // Avoid printing Playwright fill call logs or captured credential/token values.
  const safeCode = error instanceof Error && /^[A-Z][A-Z0-9_]{1,90}$/.test(error.message) ? error.message : undefined;
  results.push({ step: currentStep, status: 'FAIL', errorType: error instanceof Error ? error.name : 'UnknownError', ...(safeCode ? { diagnosticCode: safeCode } : {}) });
  await mkdir(output, { recursive: true }).catch(() => {});
  await writeFile(path.join(output, 'live-ui-report.json'), JSON.stringify({ mode: 'SYNTHETIC_LOCAL_REVIEW', startedAt: started, finishedAt: new Date().toISOString(), outcome: 'FAIL', scenarios: results, httpFailures }, null, 2)).catch(() => {});
  console.error(`Live verification failed at: ${currentStep}${safeCode ? ` (${safeCode})` : ''}. No credentials or response bodies were printed.`); process.exitCode = 1;
} finally { password = ''; for (const context of contexts) await context.close().catch(() => {}); await browser?.close(); }
