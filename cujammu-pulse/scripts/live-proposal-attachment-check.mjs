import { chromium, expect } from '@playwright/test';
import { readFile, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Mutates generated synthetic loopback data only. No mocked API responses.
const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const name = process.env.LIVE_DEMO_OUTPUT_NAME;
if (!name || !/^[a-z][a-z0-9-]{0,63}$/.test(name)) throw new Error('EXPLICIT_DEMO_OUTPUT_REQUIRED');
const output = path.join(project, 'college-events/target', name);
const base = new URL(process.env.LIVE_DEMO_URL || 'about:blank');
if (base.protocol !== 'http:' || base.hostname !== '127.0.0.1' || !base.port || base.username || base.password || base.search || base.hash) throw new Error('EXPLICIT_LOOPBACK_REQUIRED');
let browser; let credentials; let current = 'validate synthetic input';
const scenarios = []; const startedAt = new Date().toISOString();
async function step(label, work) { current = label; await work(); scenarios.push({ step: label, status: 'PASS' }); console.log(`PASS: ${label}`); }
try {
  credentials = JSON.parse((await readFile(path.join(output, 'session-credentials.json'), 'utf8')).replace(/^\uFEFF/, ''));
  if (credentials.mode !== 'SYNTHETIC_LOCAL_REVIEW' || new URL(credentials.url).origin !== base.origin || !Array.isArray(credentials.accounts) || credentials.accounts.some(a => typeof a !== 'string' || !a.endsWith('@example.invalid')) || typeof credentials.password !== 'string' || credentials.password.length < 20) throw new Error('SYNTHETIC_CREDENTIALS_REQUIRED');
  browser = await chromium.launch({ channel: 'msedge', headless: true });
  async function login(account) {
    if (!credentials.accounts.includes(`${account}@example.invalid`)) throw new Error('SYNTHETIC_ACCOUNT_REQUIRED');
    const context = await browser.newContext({ baseURL: base.origin });
    const page = await context.newPage(); page.setDefaultTimeout(15000);
    await page.goto('/app/sign-in');
    await page.locator('[name="username"]').fill(`${account}@example.invalid`);
    await page.locator('[name="password"]').fill(credentials.password);
    await Promise.all([page.waitForURL('**/app/**'), page.locator('form[action="/login"] button[type="submit"]').click()]);
    const session = await page.request.get('/api/v1/session');
    if (!session.ok() || (await session.json()).name !== `Synthetic ${account}`) throw new Error('SESSION_NOT_VERIFIED');
    return page;
  }
  const owner = await login('faculty'); const other = await login('reviewer');
  // Observe actual browser fetch responses without relying on DevTools retaining
  // no-store response bodies. The original request/response is never replaced.
  await owner.addInitScript(() => {
    globalThis.__attachmentReceipts = {};
    globalThis.__attachmentKeys = {};
    const original = globalThis.fetch;
    globalThis.fetch = async (...args) => {
      const response = await original(...args);
      const url = new URL(response.url);
      if (url.origin === location.origin && /^\/api\/v1\/organizer\/event-proposals\/\d+\/attachments\/(banner|certificate-template)$/.test(url.pathname) && response.ok) {
        globalThis.__attachmentKeys[url.pathname] = args[1]?.body instanceof FormData ? args[1].body.get('requestKey') : null;
        void response.clone().json().then(value => { globalThis.__attachmentReceipts[url.pathname] = value; }).catch(() => {});
      }
      return response;
    };
  });
  async function observedReceipt(kind) {
    const resource = `/api/v1/organizer/event-proposals/${id}/attachments/${kind}`;
    await expect.poll(() => owner.evaluate(key => !!globalThis.__attachmentReceipts[key], resource)).toBe(true);
    return owner.evaluate(key => globalThis.__attachmentReceipts[key], resource);
  }
  const csrfResponse = await owner.request.get('/api/v1/session/csrf');
  expect(csrfResponse.ok()).toBe(true); const csrf = await csrfResponse.json();
  const headers = { [csrf.headerName]: csrf.token };
  let id; let revision; let receipt; let key;
  const title = `Synthetic attachment ${Date.now()}`;
  const image = Buffer.from(await owner.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 16; canvas.height = 16;
    const ctx = canvas.getContext('2d'); ctx.fillStyle = '#183b56'; ctx.fillRect(0, 0, 16, 16);
    return canvas.toDataURL('image/png').split(',')[1];
  }), 'base64');
  await step('create a persisted synthetic proposal through the real API', async () => {
    const categories = await (await owner.request.get('/api/v1/organizer/event-proposals/categories')).json();
    const response = await owner.request.post('/api/v1/organizer/event-proposals', { headers, data: {
      requestKey: randomUUID(), title, description: 'Synthetic attachment integration verification only.', categoryId: categories[0].id,
      venue: 'Synthetic hall', eventDate: new Date(Date.now() + 172800000).toISOString().slice(0, 19), eventEndsAt: null,
      maxSeats: 20, fees: 0, contactEmail: null, targetDepartment: 'ALL', generateCertificate: true,
    } });
    expect(response.ok()).toBe(true); id = (await response.json()).id;
    const detail = await (await owner.request.get(`/api/v1/organizer/event-proposals/${id}`)).json(); revision = detail.revision;
  });
  await step('shared UI uploads image and preserves the unsaved draft', async () => {
    await owner.goto('/app/');
    const skip = owner.getByRole('button', { name: 'Skip introduction', exact: true }); if (await skip.isVisible()) await skip.click();
    await owner.getByRole('tab', { name: 'Events', exact: true }).click();
    await owner.getByText(`${title} · PENDING`, { exact: true }).locator('..').getByRole('button', { name: 'Edit and resubmit', exact: true }).click();
    await owner.getByRole('textbox', { name: 'Edit proposal title', exact: true }).fill(`${title} unsaved`);
    const chooser = owner.waitForEvent('filechooser'); await owner.getByRole('button', { name: 'Choose banner image', exact: true }).click();
    await (await chooser).setFiles({ name: 'synthetic.png', mimeType: 'image/png', buffer: image });
    const responsePromise = owner.waitForResponse(response => new URL(response.url()).pathname === `/api/v1/organizer/event-proposals/${id}/attachments/banner` && response.request().method() === 'POST');
    await owner.getByRole('button', { name: 'Upload banner', exact: true }).click();
    const response = await responsePromise; expect(response.ok()).toBe(true);
    expect(response.headers()['content-type']).toContain('application/json');
    receipt = await observedReceipt('banner');
    expect(receipt.id).toBe(id); expect(receipt.kind).toBe('banner'); expect(receipt.replayed).toBe(false);
    expect(Object.keys(receipt).sort()).toEqual(['expiresAt', 'id', 'kind', 'replayed', 'revision', 'uploadAcceptedAt']);
    expect(Number.isSafeInteger(receipt.revision)).toBe(true); expect(receipt.revision).toBeGreaterThan(revision);
    key = await owner.evaluate(resource => globalThis.__attachmentKeys[resource], `/api/v1/organizer/event-proposals/${id}/attachments/banner`);
    expect(key).toMatch(/^[0-9a-f-]{36}$/i);
    await expect(owner.getByText(/Attachment saved\. Available until/)).toBeVisible();
    await expect(owner.getByRole('textbox', { name: 'Edit proposal title', exact: true })).toHaveValue(`${title} unsaved`);
    const detail = await (await owner.request.get(`/api/v1/organizer/event-proposals/${id}`)).json();
    expect(detail.hasBanner).toBe(true); expect(detail.title).toBe(title); expect(detail.revision).toBe(receipt.revision);
    expect(Date.parse(receipt.expiresAt) - Date.parse(receipt.uploadAcceptedAt)).toBe(30 * 86400000);
  });
  await step('owner image access, wrong-role denial and replay retain the accepted expiry', async () => {
    const mediaPath = `/api/v1/organizer/event-proposals/${id}/media/banner`;
    const media = await owner.request.get(mediaPath); expect(media.ok()).toBe(true);
    expect(media.headers()['content-type']).toMatch(/^image\//); expect(media.headers()['cache-control']).toContain('no-store');
    expect((await media.body()).length).toBeGreaterThan(0);
    expect([403, 404]).toContain((await other.request.get(mediaPath)).status());
    const replay = await owner.request.post(`/api/v1/organizer/event-proposals/${id}/attachments/banner`, { headers,
      multipart: { expectedRevision: String(revision), requestKey: key, file: { name: 'attachment.png', mimeType: 'image/png', buffer: image } } });
    expect(replay.ok()).toBe(true); expect(await replay.json()).toEqual({ ...receipt, replayed: true });
    const replacement = await owner.request.post(`/api/v1/organizer/event-proposals/${id}/attachments/banner`, { headers,
      multipart: { expectedRevision: String(receipt.revision), requestKey: randomUUID(), file: { name: 'attachment.png', mimeType: 'image/png', buffer: image } } });
    expect(replacement.status()).toBe(409);
    expect((await (await owner.request.get(`/api/v1/organizer/event-proposals/${id}`)).json()).revision).toBe(receipt.revision);
  });
  await step('shared certificate-template upload preserves the banner and updates its own preview', async () => {
    const chooser = owner.waitForEvent('filechooser');
    await owner.getByRole('button', { name: 'Choose certificate template image', exact: true }).click();
    await (await chooser).setFiles({ name: 'synthetic-template.png', mimeType: 'image/png', buffer: image });
    const responsePromise = owner.waitForResponse(response => new URL(response.url()).pathname === `/api/v1/organizer/event-proposals/${id}/attachments/certificate-template` && response.request().method() === 'POST');
    await owner.getByRole('button', { name: 'Upload certificate template', exact: true }).click();
    const response = await responsePromise; expect(response.ok()).toBe(true); const template = await observedReceipt('certificate-template');
    expect(template.id).toBe(id); expect(template.kind).toBe('certificate-template'); expect(template.replayed).toBe(false);
    expect(template.revision).toBeGreaterThan(receipt.revision);
    await expect(owner.getByRole('button', { name: 'Save and resubmit', exact: true })).toBeEnabled();
    await expect(owner.getByRole('img', { name: 'Current certificate template preview', exact: true }).first()).toBeVisible();
    await expect(owner.getByRole('textbox', { name: 'Edit proposal title', exact: true })).toHaveValue(`${title} unsaved`);
    const detail = await (await owner.request.get(`/api/v1/organizer/event-proposals/${id}`)).json();
    expect(detail.hasBanner).toBe(true); expect(detail.hasCertificateTemplate).toBe(true); expect(detail.revision).toBe(template.revision);
    const media = await owner.request.get(`/api/v1/organizer/event-proposals/${id}/media/certificate-template`);
    expect(media.ok()).toBe(true); expect(media.headers()['cache-control']).toContain('no-store');
    expect((await media.body()).length).toBeGreaterThan(0);
  });
  await writeFile(path.join(output, 'proposal-attachment-ui-report.json'), JSON.stringify({ mode: 'SYNTHETIC_LOCAL_REVIEW', startedAt, finishedAt: new Date().toISOString(), outcome: 'PASS', scenarios }, null, 2));
} catch (error) {
  const sourceLocation = error instanceof Error ? error.stack?.match(/live-proposal-attachment-check\.mjs:\d+:\d+/)?.[0] : undefined;
  scenarios.push({ step: current, status: 'FAIL', errorType: error instanceof Error ? error.name : 'UnknownError', sourceLocation });
  await writeFile(path.join(output, 'proposal-attachment-ui-report.json'), JSON.stringify({ mode: 'SYNTHETIC_LOCAL_REVIEW', startedAt, outcome: 'FAIL', scenarios }, null, 2));
  console.error(`Attachment integration failed at: ${current}; ${sourceLocation || 'no source frame'}. Credentials and response bodies withheld.`); process.exitCode = 1;
} finally { credentials = undefined; await browser?.close(); }
