import { chromium, expect } from '@playwright/test';
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Synthetic-only actual backend plus shared UI review workflow, loopback only.
const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const name = process.env.LIVE_DEMO_OUTPUT_NAME;
if (!name || !/^[a-z][a-z0-9-]{0,63}$/.test(name)) throw new Error('EXPLICIT_DEMO_OUTPUT_REQUIRED');
const output = path.join(project, 'college-events/target', name);
const base = new URL(process.env.LIVE_DEMO_URL || 'about:blank');
if (base.protocol !== 'http:' || base.hostname !== '127.0.0.1' || !base.port || base.username || base.password || base.search || base.hash) throw new Error('EXPLICIT_LOOPBACK_REQUIRED');
let browser; let credentials; let current = 'validate synthetic input';
const results = []; const startedAt = new Date().toISOString(); const contexts = [];
async function step(label, work) { current = label; await work(); results.push({ step: label, status: 'PASS' }); console.log(`PASS: ${label}`); }
try {
  credentials = JSON.parse((await readFile(path.join(output, 'session-credentials.json'), 'utf8')).replace(/^\uFEFF/, ''));
  if (credentials.mode !== 'SYNTHETIC_LOCAL_REVIEW' || new URL(credentials.url).origin !== base.origin || !Array.isArray(credentials.accounts) || credentials.accounts.some(a => typeof a !== 'string' || !a.endsWith('@example.invalid')) || typeof credentials.password !== 'string' || credentials.password.length < 20) throw new Error('SYNTHETIC_CREDENTIALS_REQUIRED');
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  async function login(account) {
    if (!credentials.accounts.includes(`${account}@example.invalid`)) throw new Error('SYNTHETIC_ACCOUNT_REQUIRED');
    const context = await browser.newContext({ baseURL: base.origin, viewport: { width: 1280, height: 900 } }); contexts.push(context);
    const page = await context.newPage(); page.setDefaultTimeout(15000);
    page.on('response', response => { const url = new URL(response.url()); if (url.origin === base.origin && url.pathname.startsWith('/api/v1/admin/event-proposals') && response.status() >= 400) throw new Error(`REVIEW_HTTP_${response.status()}`); });
    await page.goto('/app/sign-in', { waitUntil: 'domcontentloaded' });
    await page.locator('[name="username"]').fill(`${account}@example.invalid`); await page.locator('[name="password"]').fill(credentials.password);
    await Promise.all([page.waitForURL('**/app/**'), page.locator('form[action="/login"] button[type="submit"]').click()]);
    const session = await page.request.get('/api/v1/session');
    if (!session.ok() || (await session.json()).name !== `Synthetic ${account}`) throw new Error('SYNTHETIC_SESSION_NOT_VERIFIED');
    const skip = page.getByRole('button', { name: 'Skip introduction', exact: true });
    if (await skip.isVisible()) await skip.click();
    return page;
  }
  const faculty = await login('faculty'); const admin = await login('admin');
  const title = `Synthetic assigned review ${Date.now()}`; let eventId;
  await step('faculty submits a proposal through the legacy form and it appears in the shared queue', async () => {
    await faculty.goto('/organizer/events/create'); await faculty.locator('[name="title"]').fill(title);
    await faculty.locator('[name="description"]').fill('Synthetic programme details for assigned review verification.');
    const category = await faculty.locator('[name="categoryId"] option').evaluateAll(options => options.find(option => option.value)?.value);
    if (!category) throw new Error('SYNTHETIC_CATEGORY_REQUIRED'); await faculty.locator('[name="categoryId"]').selectOption(category);
    await faculty.locator('[name="venue"]').fill('Synthetic Review Hall');
    await faculty.locator('[name="eventDate"]').fill(new Date(Date.now() + 172800000).toISOString().slice(0, 16));
    await faculty.locator('[name="maxSeats"]').fill('18'); await faculty.locator('[name="fees"]').fill('25');
    await faculty.locator('[name="generateCertificate"][type="checkbox"]').check();
    await Promise.all([faculty.waitForURL('**/organizer/events'), faculty.locator('form[action="/organizer/events/create"] button[type="submit"]').click()]);
    await admin.getByRole('tab', { name: 'Event proposal review', exact: true }).click();
    const card = admin.getByRole('button', { name: `Review ${title}`, exact: true }); await expect(card).toBeVisible();
    const response = await admin.request.get('/api/v1/admin/event-proposals?page=0'); const queue = await response.json();
    const item = queue.items.find(value => value.title === title); if (!item || item.status !== 'PENDING' || item.canDecide || !item.canAssign || item.maxSeats !== 18 || item.fees !== 25 || item.targetDepartment !== 'ALL' || !item.generateCertificate) throw new Error('PENDING_PROPOSAL_NOT_PROJECTED');
    eventId = item.id;
  });
  await step('dispatcher assigns an active directory reviewer; audit and inbox persist', async () => {
    await admin.getByRole('button', { name: `Review ${title}`, exact: true }).click();
    await expect(admin.getByText('Capacity: 18 places', { exact: true })).toBeVisible(); await expect(admin.getByText('Fees: INR 25', { exact: true })).toBeVisible();
    const reviewer = admin.getByRole('button', { name: 'Select reviewer: Synthetic admin', exact: true }); await reviewer.click();
    await admin.getByRole('button', { name: 'Save reviewer assignment', exact: true }).click();
    await expect(admin.getByText('Reviewer assignment saved.', { exact: true })).toBeVisible();
    await expect(admin.getByText('Assigned reviewer: Synthetic admin', { exact: true })).toBeVisible();
    await expect(admin.getByText(/event stays private and no audience announcement is sent/)).toBeVisible();
    await expect(admin.getByRole('button', { name: 'Approve proposal', exact: true })).toBeDisabled();
    const after = await (await admin.request.get('/api/v1/admin/event-proposals?page=0')).json();
    const item = after.items.find(value => value.id === eventId);
    if (!item || !item.assignedReviewerActive || !item.canDecide || item.revision <= 0) throw new Error('ASSIGNMENT_NOT_AUTHORIZED_IN_PROJECTION');
  });
  await step('assigned reviewer approves privately; a separate administrator publishes', async () => {
    await admin.getByRole('button', { name: 'I reviewed the proposal and confirm approval', exact: true }).click();
    await admin.screenshot({ path: path.join(output, 'synthetic-assigned-review.png'), fullPage: true, animations: 'disabled' });
    await expect(admin.getByRole('button', { name: 'Approve proposal', exact: true })).toBeEnabled();
    await admin.getByRole('button', { name: 'Approve proposal', exact: true }).click();
    await expect(admin.getByText('Proposal approved. It remains private until a separate administrator publishes it.', { exact: true })).toBeVisible();
    const remaining = await (await admin.request.get('/api/v1/admin/event-proposals?page=0')).json();
    if (remaining.items.some(item => item.id === eventId)) throw new Error('APPROVED_PROPOSAL_STILL_PENDING');
    let catalog = await (await admin.request.get('/api/v1/cujammu/events/catalog')).json();
    if (catalog.some(item => item.id === eventId)) throw new Error('UNPUBLISHED_EVENT_IS_PUBLIC');
    await admin.getByRole('button', { name: 'Ready to publish', exact: true }).click();
    await admin.getByRole('button', { name: `Review ${title}`, exact: true }).click();
    const ready = await (await admin.request.get('/api/v1/admin/event-proposals/publication-queue?page=0')).json();
    const item = ready.items.find(value => value.id === eventId);
    if (!item || item.status !== 'APPROVED_PENDING_PUBLICATION' || !item.canPublish) throw new Error('PUBLICATION_REVIEW_NOT_AVAILABLE');
    await admin.getByRole('button', { name: 'I reviewed the visibility and confirm publication', exact: true }).click();
    await admin.getByRole('button', { name: 'Publish event', exact: true }).click();
    await expect(admin.getByText('Publication confirmed. The event is now public and its approved notices have been queued.', { exact: true })).toBeVisible();
    catalog = await (await admin.request.get('/api/v1/cujammu/events/catalog')).json();
    if (!catalog.some(item => item.id === eventId && item.title === title && item.status === 'APPROVED')) throw new Error('PUBLISHED_EVENT_NOT_PUBLIC');
  });
  await writeFile(path.join(output, 'assigned-review-ui-report.json'), JSON.stringify({ mode: 'SYNTHETIC_LOCAL_REVIEW', origin: base.origin, startedAt, finishedAt: new Date().toISOString(), outcome: 'PASS', scenarios: results }, null, 2));
} catch (error) {
  const sourceLocation = error instanceof Error ? error.stack?.match(/live-proposal-review-check\.mjs:\d+:\d+/)?.[0] : undefined;
  results.push({ step: current, status: 'FAIL', errorType: error instanceof Error ? error.name : 'UnknownError', sourceLocation });
  await writeFile(path.join(output, 'assigned-review-ui-report.json'), JSON.stringify({ mode: 'SYNTHETIC_LOCAL_REVIEW', startedAt, outcome: 'FAIL', scenarios: results }, null, 2));
  console.error(`Assigned review verification failed at ${current}; ${sourceLocation || 'no source frame'}. Credentials and response bodies withheld.`); process.exitCode = 1;
} finally { credentials = undefined; for (const context of contexts) await context.close().catch(() => {}); await browser?.close(); }
