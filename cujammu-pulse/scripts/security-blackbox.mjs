import { request, chromium } from '@playwright/test';
import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';
import { performance } from 'node:perf_hooks';

// Intentionally loopback-only. No external targets or university records.
const origin = process.env.PULSE_SECURITY_ORIGIN ?? 'http://127.0.0.1:18081';
const url = new URL(origin);
assert.equal(url.hostname, '127.0.0.1');
assert.equal(url.protocol, 'http:');
const output = process.env.PULSE_SECURITY_OUTPUT
  ? resolve(process.env.PULSE_SECURITY_OUTPUT)
  : new URL('../../college-events/target/security-blackbox-2026-10-19.json', import.meta.url);
const credentialPath = process.env.PULSE_SECURITY_CREDENTIALS
  ? resolve(process.env.PULSE_SECURITY_CREDENTIALS)
  : new URL('../../college-events/target/security-review-2026-10-19/session-credentials.json', import.meta.url);
const credentials = JSON.parse((await readFile(credentialPath, 'utf8')).replace(/^\uFEFF/, ''));
assert.equal(new URL(credentials.url).origin, origin);
const api = await request.newContext({ baseURL: origin, maxRedirects: 0 });
const checks = [];
async function check(name, action) { await action(); checks.push({ name, passed: true }); }
let browser;
try {
  await check('API authentication rejects anonymous identity read without redirect', async () => {
    const response = await api.get('/api/v1/session');
    assert.equal(response.status(), 401); assert.equal(response.headers().location, undefined);
    assert.deepEqual(await response.json(), { error: 'AUTHENTICATION_REQUIRED' });
  });
  await check('headers deny framing, plugins, base injection and cross-origin forms', async () => {
    const response = await api.get('/app/'); const headers = response.headers();
    assert.equal(response.status(), 200); assert.equal(headers['x-frame-options'], 'DENY');
    assert.equal(headers['x-content-type-options'], 'nosniff');
    for (const directive of ["script-src 'self';", "object-src 'none'", "base-uri 'none'", "frame-ancestors 'none'", "form-action 'self'"]) assert.ok(headers['content-security-policy'].includes(directive));
    assert.ok(!headers['content-security-policy'].includes('unsafe-eval'));
  });
  await check('cross-origin write without CSRF cannot create account', async () => {
    const response = await api.post('/register', { headers: { Origin: 'https://attacker.example.invalid' }, form: { email: 'synthetic@example.invalid', role: 'ADMIN' } });
    // Anonymous invalid-CSRF failures may use the authentication entry point;
    // the authenticated mutation below must specifically fail with 403.
    assert.ok([401,403].includes(response.status()));
  });
  await check('session login uses CSRF and rejects privilege/header spoofing', async () => {
    const csrf = await (await api.get('/api/v1/session/csrf')).json();
    const response = await api.post('/login', { form: { username: 'student@example.invalid', password: credentials.password, _csrf: csrf.token, role: 'ADMIN' } });
    assert.equal(response.status(), 302);
    const location = response.headers().location ?? '';
    assert.ok(!location.includes('error=true'), `Synthetic student sign-in was rejected (${location || 'no redirect location'}).`);
    const sessionResponse = await api.get('/api/v1/session');
    const identity = await sessionResponse.json();
    assert.equal(sessionResponse.status(), 200, `Session lookup returned ${sessionResponse.status()} (${identity.error ?? 'no error code'}).`);
    assert.equal(identity.role, 'STUDENT', `Session role missing; response fields: ${Object.keys(identity).sort().join(',')}.`);
    assert.deepEqual(Object.keys(identity).sort(), ['id','name','role']);
    const denied = await api.get('/api/v1/cujammu/helpdesk/all', { headers: { 'X-Role': 'ADMIN', 'X-Forwarded-For': '127.0.0.1' } });
    assert.equal(denied.status(), 403);
  });
  await check('authenticated mutations reject missing CSRF and database console stays denied', async () => {
    assert.equal((await api.post('/api/notifications/read-all')).status(), 403);
    assert.equal((await api.get('/api/notifications/read-all')).status(), 405);
    assert.equal((await api.get('/h2-console/')).status(), 403);
  });
  await check('account throttle survives forged forwarding headers', async () => {
    const csrf = await (await api.get('/api/v1/session/csrf')).json();
    for (let i=0; i<11; i++) {
      const response = await api.post('/login', { headers: { 'X-Forwarded-For': `192.0.2.${i}` }, form: { username: i%2 ? ' SYNTHETIC-ATTACK@example.invalid ' : 'synthetic-attack@example.invalid', password: "' OR 1=1 --", _csrf: csrf.token } });
      assert.equal(response.status(), i<10 ? 302 : 429);
      if (i===10) assert.equal(response.headers()['retry-after'], '60');
    }
  });
  await check('peer quota prevents rotating accounts plus forged forwarding headers', async () => {
    const csrf = await (await api.get('/api/v1/session/csrf')).json();
    // Serial password hashing may exceed the 60s quota window. Exercise a bounded
    // burst within that window, retaining the real credential-verification path.
    const burstStarted = performance.now(); let next = 0; let limited = 0;
    await Promise.all(Array.from({ length: 8 }, async () => {
      while (next < 61) {
        const i = next++;
        const response = await api.post('/login', { headers: { 'X-Forwarded-For': `198.51.100.${i}` }, form: { username: `synthetic-rotating-${i}@example.invalid`, password:'SyntheticWrong123!', _csrf:csrf.token } });
        if (response.status()===429) { assert.equal(response.headers()['retry-after'],'60'); limited++; }
        else assert.equal(response.status(),302);
      }
    }));
    assert.ok(performance.now()-burstStarted < 60000, 'Burst exceeded the quota window; result is inconclusive.');
    assert.ok(limited > 0, 'Expected peer quota to reject rotating accounts.');
  });
  // Bounded read-only workload. No destructive stress, saturation, email or external model calls.
  const timings = []; let index = 0; const statuses = {};
  for (let i=0; i<8; i++) await api.get('/api/v1/cujammu/departments');
  const started = performance.now();
  await Promise.all(Array.from({ length: 8 }, async () => {
    while (index++ < 80) {
      const t = performance.now(); const response = await api.get('/api/v1/cujammu/departments');
      await response.body(); timings.push(performance.now()-t); statuses[response.status()] = (statuses[response.status()]??0)+1;
    }
  }));
  timings.sort((a,b)=>a-b); const elapsed = performance.now()-started;
  assert.equal(statuses[200], 80);
  const load = { scope:'synthetic loopback; one public catalogue read; not capacity certification', requests:80, concurrency:8, statuses,
    elapsedMs:Math.round(elapsed), requestsPerSecond:Math.round(80000/elapsed), p50Ms:Math.round(timings[39]), p95Ms:Math.round(timings[75]), p99Ms:Math.round(timings[79]),
    provisionalP95TargetMs:500, targetMet:timings[75]<=500 };
  browser = await chromium.launch({ channel:'msedge', headless:true });
  await check('real shared app boots under enforced strict CSP', async () => {
    const page = await browser.newPage(); const errors = []; const violations = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.addInitScript(() => { window.__cspViolations=[]; document.addEventListener('securitypolicyviolation', e => window.__cspViolations.push(e.violatedDirective)); });
    await page.goto(origin+'/app/');
    const skip = page.getByRole('button', { name:'Skip introduction', exact:true });
    await skip.waitFor(); await skip.click();
    await page.getByRole('button', { name:'Sign in', exact:true }).waitFor();
    violations.push(...await page.evaluate(() => window.__cspViolations));
    assert.deepEqual(errors, []); assert.deepEqual(violations, []); await page.close();
  });
  const report = { timestamp:new Date().toISOString(), checks, load, limitations:['No production target, distributed DoS, native device, provider delivery or Mongo replica-set claims.','CPU and memory must be measured on the server separately; runner memory is not server evidence.'] };
  await writeFile(output, JSON.stringify(report,null,2));
  console.log(JSON.stringify({ passed:checks.length, load, output:output.pathname }));
} finally { await api.dispose(); await browser?.close(); }
