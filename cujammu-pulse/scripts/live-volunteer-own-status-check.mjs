import { chromium, expect } from '@playwright/test';
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const name = process.env.LIVE_DEMO_OUTPUT_NAME;
if (!name || !/^[a-z][a-z0-9-]{0,63}$/.test(name)) throw new Error('EXPLICIT_DEMO_OUTPUT_REQUIRED');
const output = path.join(project, 'college-events/target', name);
const base = new URL(process.env.LIVE_DEMO_URL || 'about:blank');
if (base.protocol !== 'http:' || base.hostname !== '127.0.0.1' || !base.port || base.username || base.password || base.search || base.hash) throw new Error('EXPLICIT_LOOPBACK_REQUIRED');
let browser; let credentials; let current = 'synthetic input validation'; const scenarios = [];
const startedAt = new Date().toISOString();
async function step(label, work) { current = label; await work(); scenarios.push({ step: label, status: 'PASS' }); console.log(`PASS: ${label}`); }
try {
  credentials = JSON.parse((await readFile(path.join(output, 'session-credentials.json'), 'utf8')).replace(/^\uFEFF/, ''));
  if (credentials.mode !== 'SYNTHETIC_LOCAL_REVIEW' || new URL(credentials.url).origin !== base.origin || !Array.isArray(credentials.accounts) || credentials.accounts.some(a => typeof a !== 'string' || !a.endsWith('@example.invalid')) || typeof credentials.password !== 'string' || credentials.password.length < 20) throw new Error('SYNTHETIC_CREDENTIALS_REQUIRED');
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  async function login(account) {
    if (!credentials.accounts.includes(`${account}@example.invalid`)) throw new Error('SYNTHETIC_ACCOUNT_REQUIRED');
    const context = await browser.newContext({ baseURL: base.origin }); const page = await context.newPage(); page.setDefaultTimeout(15000);
    await page.goto('/app/sign-in'); await page.locator('[name="username"]').fill(`${account}@example.invalid`); await page.locator('[name="password"]').fill(credentials.password);
    await Promise.all([page.waitForURL('**/app/**'), page.locator('form[action="/login"] button[type="submit"]').click()]);
    const session = await page.request.get('/api/v1/session'); if (!session.ok() || (await session.json()).name !== `Synthetic ${account}`) throw new Error('SESSION_NOT_VERIFIED');
    const skip = page.getByRole('button', { name: 'Skip introduction', exact: true }); if (await skip.isVisible()) await skip.click(); return page;
  }
  const student = await login('student'); const faculty = await login('faculty'); const other = await login('finder');
  const directory = '/api/v1/cujammu/events/operations/my-volunteer-applications?page=0&size=20';
  let membership;
  await step('student applies through shared UI and sees persisted awaiting-review status', async () => {
    await student.getByRole('tab', { name: 'Volunteers', exact: true }).click();
    await student.getByRole('button', { name: 'Apply to volunteer', exact: true }).click();
    await expect(student.getByText('Application status: Awaiting review', { exact: true })).toBeVisible();
    const response = await student.request.get(directory); expect(response.ok()).toBe(true); expect(response.headers()['cache-control']).toContain('no-store');
    const result = await response.json(); expect(result.items.length).toBe(1); membership = result.items[0]; expect(membership.status).toBe('APPLIED');
    expect(Object.keys(membership).sort()).toEqual(['appliedAt', 'decidedAt', 'eventDate', 'eventId', 'eventStatus', 'eventTitle', 'id', 'revision', 'status']);
  });
  await step('another student cannot obtain the application by supplying its owner ID', async () => {
    const session = await (await student.request.get('/api/v1/session')).json();
    const response = await other.request.get(`${directory}&userId=${session.id}`); expect(response.ok()).toBe(true);
    expect((await response.json()).items).toEqual([]);
  });
  await step('accepted status remains discoverable after faculty closes recruitment', async () => {
    const csrf = await (await faculty.request.get('/api/v1/session/csrf')).json(); const headers = { [csrf.headerName]: csrf.token };
    const decision = await faculty.request.put(`/api/v1/cujammu/events/${membership.eventId}/volunteer-applications/${membership.id}/decision`, { headers, data: { expectedRevision: membership.revision, decision: 'ACCEPT' } });
    expect(decision.ok()).toBe(true);
    const operation = await (await faculty.request.get(`/api/v1/cujammu/events/${membership.eventId}/operations`)).json();
    const close = await faculty.request.put(`/api/v1/cujammu/events/${membership.eventId}/volunteer-recruitment`, { headers, data: { expectedRevision: operation.revision, open: false, maxApplicants: operation.maxVolunteerApplicants } });
    expect(close.ok()).toBe(true); expect((await close.json()).recruitmentOpen).toBe(false);
    const openEvents = await (await student.request.get('/api/v1/cujammu/events/operations/volunteer-opportunities')).json();
    expect(openEvents.items.some(event => event.id === membership.eventId)).toBe(false);
    const saved = await (await student.request.get(directory)).json();
    expect(saved.items[0].id).toBe(membership.id); expect(saved.items[0].status).toBe('ACCEPTED');
    await student.getByRole('button', { name: 'Refresh my applications', exact: true }).click();
    await expect(student.getByText('Application status: Accepted', { exact: true })).toBeVisible();
  });
  await writeFile(path.join(output, 'volunteer-own-status-ui-report.json'), JSON.stringify({ mode: 'SYNTHETIC_LOCAL_REVIEW', startedAt, finishedAt: new Date().toISOString(), outcome: 'PASS', scenarios }, null, 2));
} catch (error) {
  const sourceLocation = error instanceof Error ? error.stack?.match(/live-volunteer-own-status-check\.mjs:\d+:\d+/)?.[0] : undefined;
  scenarios.push({ step: current, status: 'FAIL', errorType: error instanceof Error ? error.name : 'UnknownError', sourceLocation });
  await writeFile(path.join(output, 'volunteer-own-status-ui-report.json'), JSON.stringify({ mode: 'SYNTHETIC_LOCAL_REVIEW', startedAt, outcome: 'FAIL', scenarios }, null, 2));
  console.error(`Volunteer status verification failed at ${current}; ${sourceLocation || 'no source frame'}. Response bodies and credentials withheld.`); process.exitCode = 1;
} finally { credentials = undefined; await browser?.close(); }
