/// <reference types="node" />
import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';

// Executes the real legacy template scripts with synthetic responses. This does
// not replace server-rendered Thymeleaf/authentication integration coverage.
const template = (name: string) => readFileSync(new URL(`../../college-events/src/main/resources/templates/${name}.html`, import.meta.url), 'utf8');
const script = (name: string) => [...template(name).matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(match => match[1]).filter(Boolean).at(-1)!;
const payload = '<img src=x onerror="window.__xss=1"><svg onload="window.__xss=1"></svg>';

test('legacy scanner treats camera labels and identifiers as data', async ({ page }) => {
  await page.goto('/app/');
  await page.setContent('<span id="scanStatusText"></span><div class="scanner-status-dot"></div><select id="cameraSelect"></select><button id="switchCamBtn" class="d-none"></button><input id="galleryInput" type="file"><canvas id="confettiCanvas"></canvas>');
  const hostileId = `camera\" autofocus onfocus=\"window.__xss=1\">${payload}`;
  await page.evaluate(({ id, label }) => {
    (window as any).Html5Qrcode = { getCameras: async () => [{ id, label }, { id: 'second', label: 'Second camera' }] };
  }, { id: hostileId, label: `</option></select>${payload}` });
  await page.addScriptTag({ content: script('organizer/scan-qr') });
  // Isolate device startup while executing the actual enumeration/rendering code.
  await page.evaluate('startCamera = value => { window.__cameraId = value; }; initScanner();');
  await expect(page.locator('#cameraSelect option')).toHaveCount(2);
  await expect(page.locator('#cameraSelect option').first()).toHaveText(`</option></select>${payload}`);
  await expect(page.locator('#cameraSelect')).toHaveValue(hostileId);
  await expect(page.locator('img, svg, [autofocus], [onfocus]')).toHaveCount(0);
  expect(await page.evaluate('window.__cameraId')).toBe(hostileId);
  expect(await page.evaluate('window.__xss')).toBeUndefined();
});

test('legacy feed comments render hostile author and content as literal text', async ({ page }) => {
  await page.goto('/app/');
  await page.setContent('<div id="comments-list-1"></div>');
  await page.route('**/api/v1/cujammu/feed/1/comments', route => route.fulfill({ json: [{ author: { name: payload }, content: payload }] }));
  await page.addScriptTag({ content: script('feed/index') });
  await page.evaluate('loadComments(1)');
  await expect(page.locator('#comments-list-1')).toHaveText(`${payload}: ${payload}`);
  await expect(page.locator('#comments-list-1 img, #comments-list-1 svg')).toHaveCount(0);
  expect(await page.evaluate('window.__xss')).toBeUndefined();
});

test('legacy feed share keeps untrusted values out of executable handler source', async ({ page }) => {
  const source = template('feed/index');
  expect(source).toContain('th:attr="data-author=${post.author.name},data-content=${post.content}"');
  expect(source).not.toContain('th:onclick="\'sharePost(');
  await page.goto('/app/');
  await page.setContent('<button id="share" onclick="sharePost(this.dataset.author, this.dataset.content)">Share</button>');
  await page.addScriptTag({ content: script('feed/index') });
  const hostile = `');window.__xss=1;//${payload}`;
  await page.evaluate(value => {
    const button = document.getElementById('share')!;
    button.dataset.author = value;
    button.dataset.content = value;
    Object.defineProperty(navigator, 'share', { configurable: true, value: (data: unknown) => { (window as any).__shared = data; return Promise.resolve(); } });
  }, hostile);
  await page.locator('#share').click();
  expect(await page.evaluate('(window.__shared).text')).toBe(`${hostile} on CUJAMMU-PULSE:\n${hostile}`);
  expect(await page.evaluate('window.__xss')).toBeUndefined();
});

test('legacy sports commentary renders timeline and body as literal text', async ({ page }) => {
  await page.goto('/app/');
  await page.setContent('<div id="comm-container-1" class="d-none"><div id="comm-list-1"></div></div>');
  await page.route('**/api/v1/cujammu/sports/matches/1', route => route.fulfill({ json: { commentary: [{ timelineMark: payload, commentaryText: payload, scoreEvent: 'SIX' }] } }));
  await page.addScriptTag({ content: script('sports/index') });
  await page.evaluate('loadCommentary(1)');
  await expect(page.locator('#comm-list-1')).toHaveText(payload + payload);
  await expect(page.locator('#comm-list-1 img, #comm-list-1 svg')).toHaveCount(0);
  await expect(page.locator('.comm-mark')).toHaveClass(/bg-primary/);
  expect(await page.evaluate('window.__xss')).toBeUndefined();
});

test('legacy sports errors never interpolate exception text into markup', async ({ page }) => {
  await page.goto('/app/');
  await page.setContent('<div id="comm-container-1" class="d-none"><div id="comm-list-1"></div></div>');
  await page.addScriptTag({ content: script('sports/index') });
  await page.evaluate(value => { window.fetch = async () => { throw new Error(value); }; }, payload);
  await page.evaluate('loadCommentary(1)');
  await expect(page.locator('#comm-list-1')).toHaveText('Failed to load commentary. Please try again.');
  await expect(page.locator('#comm-list-1 img, #comm-list-1 svg')).toHaveCount(0);
});

for (const success of [true, false]) {
  test(`legacy OTP resend ${success ? 'success' : 'failure'} renders message as literal text`, async ({ page }) => {
    await page.goto('/app/');
    await page.setContent('<form id="otpForm"><input class="otp-input"><input id="otpHidden"><button id="verifyBtn"></button></form><span id="countdown"></span><button id="resendBtn"></button><div id="resendMsg"></div>');
    await page.route('**/register/resend-otp', route => route.fulfill({ json: { success, message: payload, remainingSeconds: 120 } }));
    await page.addScriptTag({ content: script('auth/verify-otp') });
    await page.evaluate('resendOtp()');
    await expect(page.locator('#resendMsg')).toHaveText(payload);
    await expect(page.locator('#resendMsg img, #resendMsg svg')).toHaveCount(0);
    expect(await page.evaluate('window.__xss')).toBeUndefined();
  });
}
