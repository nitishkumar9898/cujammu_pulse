import { chromium, expect } from '@playwright/test';
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Explicit synthetic-only mutation run. Never part of mocked/default acceptance.
const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const name = process.env.LIVE_DEMO_OUTPUT_NAME;
if (!name || !/^[a-z][a-z0-9-]{0,63}$/.test(name)) throw new Error('EXPLICIT_DEMO_OUTPUT_REQUIRED');
const output = path.join(project, 'college-events/target', name);
const base = new URL(process.env.LIVE_DEMO_URL || 'about:blank');
if (base.protocol !== 'http:' || base.hostname !== '127.0.0.1' || !base.port || base.username || base.password || base.search || base.hash) throw new Error('EXPLICIT_LOOPBACK_REQUIRED');
let browser; let credentials; let current = 'validate synthetic input';
const results = []; const startedAt = new Date().toISOString();
const step = async (label, work) => { current = label; await work(); results.push({ step: label, status: 'PASS' }); console.log(`PASS: ${label}`); };
try {
  credentials = JSON.parse((await readFile(path.join(output, 'session-credentials.json'), 'utf8')).replace(/^\uFEFF/, ''));
  if (credentials.mode !== 'SYNTHETIC_LOCAL_REVIEW' || new URL(credentials.url).origin !== base.origin || !Array.isArray(credentials.accounts) || credentials.accounts.some(a => typeof a !== 'string' || !a.endsWith('@example.invalid')) || typeof credentials.password !== 'string' || credentials.password.length < 20) throw new Error('SYNTHETIC_CREDENTIALS_REQUIRED');
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  async function login(account) {
    if (!credentials.accounts.includes(`${account}@example.invalid`)) throw new Error('SYNTHETIC_ACCOUNT_REQUIRED');
    const context = await browser.newContext({ baseURL: base.origin, viewport: { width: 1280, height: 900 } });
    const page = await context.newPage(); page.setDefaultTimeout(15000);
    await page.goto('/app/sign-in');
    await page.locator('[name="username"]').fill(`${account}@example.invalid`);
    await page.locator('[name="password"]').fill(credentials.password);
    await Promise.all([page.waitForURL('**/app/**'), page.locator('form[action="/login"] button[type="submit"]').click()]);
    const session = await page.request.get('/api/v1/session');
    if (!session.ok() || (await session.json()).name !== `Synthetic ${account}`) throw new Error('SESSION_NOT_VERIFIED');
    return page;
  }
  const faculty = await login('faculty'); const admin = await login('admin'); const other = await login('reviewer');
  let eventId; let originalRevision; let editRevision; let approvedRevision;
  const title = `Synthetic proposal ${Date.now()}`;
  await step('faculty creates a persisted pending proposal using the actual form', async () => {
    await faculty.goto('/organizer/events/create');
    await faculty.locator('[name="title"]').fill(title);
    await faculty.locator('[name="description"]').fill('Synthetic programme for local proposal boundary review only.');
    const category = await faculty.locator('[name="categoryId"] option').evaluateAll(options => options.find(option => option.value)?.value);
    if (!category) throw new Error('SYNTHETIC_CATEGORY_REQUIRED');
    await faculty.locator('[name="categoryId"]').selectOption(category);
    await faculty.locator('[name="venue"]').fill('Synthetic Proposal Hall');
    await faculty.locator('[name="eventDate"]').fill(new Date(Date.now() + 172800000).toISOString().slice(0, 16));
    await faculty.locator('[name="maxSeats"]').fill('20');
    await faculty.locator('[name="contactEmail"]').fill('faculty@example.invalid');
    await faculty.locator('[name="generateCertificate"][type="checkbox"]').uncheck();
    await Promise.all([faculty.waitForURL('**/organizer/events'), faculty.locator('form[action="/organizer/events/create"] button[type="submit"]').click()]);
    const card = faculty.locator('.card').filter({ has: faculty.getByText(title, { exact: true }) }).first();
    const href = await card.locator('a[href$="/edit"]').getAttribute('href');
    eventId = /^\/organizer\/events\/(\d+)\/edit$/.exec(href || '')?.[1];
    if (!eventId) throw new Error('CREATED_PROPOSAL_NOT_FOUND');
    await faculty.goto(href); originalRevision = await faculty.locator('[name="expectedLifecycleVersion"]').inputValue();
  });
  await step('another faculty account cannot read the private proposal edit form', async () => {
    const response = await other.goto(`/organizer/events/${eventId}/edit`);
    expect(response.status()).toBe(403); await expect(other.getByText(title, { exact: true })).toHaveCount(0);
  });
  await step('owner edits persist and invalidate an older review revision', async () => {
    await faculty.locator('[name="venue"]').fill('Synthetic Revised Hall');
    await Promise.all([faculty.waitForURL('**/organizer/events'), faculty.locator('form[action$="/edit"] button[type="submit"]').click()]);
    await admin.goto(`/admin/events/${eventId}`);
    const approval = admin.locator('form[action$="/approve"]');
    editRevision = await approval.locator('[name="expectedRevision"]').inputValue();
    expect(Number(editRevision)).toBeGreaterThan(Number(originalRevision));
    const csrf = await approval.locator('input[name="_csrf"]').first().inputValue();
    const stale = await admin.request.post(`/admin/events/${eventId}/approve`, { form: { _csrf: csrf, expectedRevision: originalRevision }, maxRedirects: 0 });
    expect(stale.status()).toBe(302);
    await admin.reload(); await expect(approval.locator('[name="expectedRevision"]')).toHaveValue(editRevision);
    await expect(admin.getByText('Synthetic Revised Hall', { exact: true })).toBeVisible();
  });
  await step('admin rejection works without inline JavaScript and owner can resubmit', async () => {
    await admin.locator('#proposal-rejection-reason').fill('Clarify the synthetic agenda.\nPlease resubmit for review.');
    await admin.screenshot({ path: path.join(output, 'synthetic-proposal-review.png'), fullPage: true, animations: 'disabled' });
    await admin.setViewportSize({ width: 390, height: 844 });
    await expect(admin.locator('#proposal-rejection-reason')).toBeVisible();
    expect(await admin.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
    await admin.screenshot({ path: path.join(output, 'synthetic-proposal-review-small-web.png'), fullPage: true, animations: 'disabled' });
    await admin.setViewportSize({ width: 1280, height: 900 });
    await Promise.all([admin.waitForURL('**/admin/events/pending'), admin.locator('form[action$="/reject"] button[type="submit"]').click()]);
    await faculty.goto(`/organizer/events/${eventId}/edit`);
    expect(Number(await faculty.locator('[name="expectedLifecycleVersion"]').inputValue())).toBeGreaterThan(Number(editRevision));
    await faculty.locator('[name="description"]').fill('Synthetic programme with clarified agenda for independent review.');
    await Promise.all([faculty.waitForURL('**/organizer/events'), faculty.locator('form[action$="/edit"] button[type="submit"]').click()]);
  });
  await step('fresh approval persists and reviewed events cannot reenter proposal editing', async () => {
    await admin.goto(`/admin/events/${eventId}`);
    const approval = admin.locator('form[action$="/approve"]');
    approvedRevision = await approval.locator('[name="expectedRevision"]').inputValue();
    expect(Number(approvedRevision)).toBeGreaterThan(Number(editRevision));
    await Promise.all([admin.waitForURL('**/admin/events/pending'), approval.locator('button[type="submit"]').click()]);
    await admin.goto(`/admin/events/${eventId}`);
    await expect(admin.locator('form[action$="/approve"]')).toHaveCount(0);
    await expect(admin.locator('form[action$="/reject"]')).toHaveCount(0);
    await expect(admin.getByText('APPROVED_PENDING_PUBLICATION', { exact: true })).toBeVisible();
    const response = await faculty.goto(`/organizer/events/${eventId}/edit`); expect(response.status()).toBe(409);
    await expect(faculty.locator('[name="expectedLifecycleVersion"]')).toHaveCount(0);
  });
  await writeFile(path.join(output, 'proposal-ui-report.json'), JSON.stringify({ mode: 'SYNTHETIC_LOCAL_REVIEW', origin: base.origin, startedAt, finishedAt: new Date().toISOString(), outcome: 'PASS', scenarios: results }, null, 2));
} catch (error) {
  const sourceLocation = error instanceof Error ? error.stack?.match(/live-proposal-check\.mjs:\d+:\d+/)?.[0] : undefined;
  results.push({ step: current, status: 'FAIL', errorType: error instanceof Error ? error.name : 'UnknownError', sourceLocation });
  await writeFile(path.join(output, 'proposal-ui-report.json'), JSON.stringify({ mode: 'SYNTHETIC_LOCAL_REVIEW', startedAt, outcome: 'FAIL', scenarios: results }, null, 2));
  console.error(`Proposal browser verification failed at: ${current}; ${sourceLocation || 'no source frame'}. Credentials and response bodies withheld.`); process.exitCode = 1;
} finally { credentials = undefined; await browser?.close(); }
