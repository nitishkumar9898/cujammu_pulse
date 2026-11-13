import { test, expect, type Page } from '@playwright/test';
const item = { id: 1, version: 2, title: 'Synthetic Wallet', category: 'BAGS_WALLETS', description: 'Found near library', locationFound: 'Library', status: 'REPORTED', reportedAt: '2026-10-04T10:00:00Z', canReview: false };
const claim = { id: 4, itemId: 1, status: 'PENDING', evidence: 'Synthetic identifying mark', submittedAt: '2026-10-04T10:00:00Z' };
async function setup(page: Page) {
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Account', role: 'STUDENT' } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_LOST_FOUND: true } }));
}
async function start(page: Page) { await page.goto('/app/'); await page.getByRole('button', { name: 'Skip introduction' }).click(); await page.getByRole('button', { name: 'Open lost & found', exact: true }).click(); }

test('same-account session refresh preserves selected private claim and drafts; expiry clears them', async ({ page }) => {
  await setup(page); let expired = false; let checks = 0; let listingReads = 0;
  await page.route('**/api/v1/session', route => { checks++; return route.fulfill(expired ? { status: 401, json: {} } : { json: { id: 1, name: 'Synthetic Account', role: 'STUDENT' } }); });
  await page.route('**/api/v1/cujammu/lost-and-found?*', route => { listingReads++; return route.fulfill({ json: { items: [item], page: 0, hasNext: false } }); });
  await page.route('**/api/v1/cujammu/lost-and-found/1/claims?*', route => route.fulfill({ json: [claim] }));
  await start(page);
  await page.getByRole('textbox', { name: 'Item title', exact: true }).fill('Synthetic draft title');
  await page.getByRole('button', { name: 'View or submit my claim: Synthetic Wallet', exact: true }).click();
  await expect(page.getByText(claim.evidence, { exact: true })).toBeVisible();
  const evidence = page.getByRole('textbox', { name: 'Private ownership evidence', exact: true });
  await evidence.fill('Synthetic unfinished ownership evidence');
  const previousChecks = checks;
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect.poll(() => checks).toBeGreaterThan(previousChecks);
  await expect(evidence).toHaveValue('Synthetic unfinished ownership evidence');
  await expect(page.getByRole('textbox', { name: 'Item title', exact: true })).toHaveValue('Synthetic draft title');
  await expect(page.getByText(claim.evidence, { exact: true })).toBeVisible();
  expect(listingReads).toBe(1);
  expired = true;
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
  await expect(evidence).toHaveCount(0);
  await expect(page.getByText(claim.evidence, { exact: true })).toHaveCount(0);
});

test('private claim retries reuse key, submit evidence without identity fields and clear on category change', async ({ page }) => {
  await setup(page); let submitted = false; const commands: unknown[] = [];
  await page.route('**/api/v1/cujammu/lost-and-found?*', route => route.fulfill({ json: { items: route.request().url().includes('category=KEYS') ? [] : [item], page: 0, hasNext: false } }));
  await page.route('**/api/v1/cujammu/lost-and-found/1/claims?*', route => route.fulfill({ json: submitted ? [claim] : [] }));
  await page.route('**/api/v1/cujammu/lost-and-found/1/claim', route => { commands.push(route.request().postDataJSON()); expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf'); if (commands.length === 1) return route.fulfill({ status: 503, json: {} }); submitted = true; return route.fulfill({ json: claim }); });
  await start(page); await page.getByRole('button', { name: 'View or submit my claim: Synthetic Wallet', exact: true }).click();
  await expect(page.getByRole('textbox', { name: 'Your name', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Verify handover for claim 4', exact: true })).toHaveCount(0);
  await page.getByRole('textbox', { name: 'Private ownership evidence', exact: true }).fill(claim.evidence);
  await page.getByRole('button', { name: 'Submit private claim', exact: true }).click();
  await expect(page.getByText('The campus service could not complete this request.', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Submit private claim', exact: true }).click();
  await expect(page.getByText('Claim 4 · PENDING')).toBeVisible(); expect(commands).toHaveLength(2); expect(commands[1]).toEqual(commands[0]);
  expect(commands[0]).toMatchObject({ expectedVersion: 2, evidence: claim.evidence }); expect(Object.keys(commands[0] as object).sort()).toEqual(['evidence', 'expectedVersion', 'requestKey']);
  await page.getByRole('checkbox', { name: /Filter: Keys$/ }).click();
  await expect(page.getByText(claim.evidence, { exact: true })).toHaveCount(0);
  await expect(page.getByRole('textbox', { name: 'Private ownership evidence', exact: true })).toHaveCount(0);
});

test('finder verifies ownership before a confirmed versioned handover and retries unchanged command', async ({ page }) => {
  await setup(page); let handedOver = false; const commands: unknown[] = [];
  await page.route('**/api/v1/cujammu/lost-and-found?*', route => route.fulfill({ json: { items: [{ ...item, canReview: !handedOver, status: handedOver ? 'HANDED_OVER' : 'REPORTED' }], page: 0, hasNext: false } }));
  await page.route('**/api/v1/cujammu/lost-and-found/1/claims?*', route => route.fulfill({ json: [{ ...claim, status: handedOver ? 'HANDED_OVER' : 'PENDING' }] }));
  await page.route('**/api/v1/cujammu/lost-and-found/1/hand-over', route => { commands.push(route.request().postDataJSON()); expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf'); if (commands.length === 1) return route.fulfill({ status: 503, json: {} }); handedOver = true; return route.fulfill({ json: { ...item, version: 3, status: 'HANDED_OVER', canReview: false } }); });
  await start(page); await page.getByRole('button', { name: 'Review private claims: Synthetic Wallet', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Submit private claim', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Verify handover for claim 4', exact: true }).click(); expect(commands).toHaveLength(0);
  await expect(page.getByRole('button', { name: 'Confirm verified handover', exact: true })).toBeDisabled();
  await page.getByRole('textbox', { name: 'Handover verification reason', exact: true }).fill('Verified identifying mark in person');
  await page.getByRole('button', { name: 'Confirm verified handover', exact: true }).click();
  await expect(page.getByText('The campus service could not complete this request.', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Confirm verified handover', exact: true }).click();
  await expect(page.getByText('Bags and wallets · HANDED_OVER · Library')).toBeVisible();
  expect(commands).toHaveLength(2); expect(commands[1]).toEqual(commands[0]); expect(commands[0]).toMatchObject({ expectedVersion: 2, claimId: 4, verificationReason: 'Verified identifying mark in person' });
  await expect(page.getByRole('button', { name: 'Confirm verified handover', exact: true })).toHaveCount(0);
});

test('found-item report uses selected category and stable retry; listing pages clear old items', async ({ page }) => {
  await setup(page); const commands: unknown[] = [];
  await page.route('**/api/v1/cujammu/lost-and-found?*', route => { const next = new URL(route.request().url()).searchParams.get('page') === '1'; return route.fulfill({ json: { items: next ? [] : [item], page: next ? 1 : 0, hasNext: !next } }); });
  await page.route('**/api/v1/cujammu/lost-and-found', route => { commands.push(route.request().postDataJSON()); if (commands.length === 1) return route.fulfill({ status: 503, json: {} }); return route.fulfill({ json: { ...item, id: 2, title: 'Synthetic Keys', category: 'KEYS' } }); });
  await start(page);
  await page.getByRole('textbox', { name: 'Item title', exact: true }).fill('Synthetic Keys');
  await page.getByRole('checkbox', { name: /Report category: Keys$/ }).click();
  await page.getByRole('textbox', { name: 'Description', exact: true }).fill('Synthetic found keys');
  await page.getByRole('textbox', { name: 'Location found', exact: true }).fill('Library');
  await page.getByRole('button', { name: 'Report found item', exact: true }).click();
  await expect(page.getByText('The campus service could not complete this request.', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Report found item', exact: true }).click();
  await expect(page.getByRole('textbox', { name: 'Item title', exact: true })).toHaveValue('');
  expect(commands).toHaveLength(2); expect(commands[1]).toEqual(commands[0]); expect(commands[0]).toMatchObject({ category: 'KEYS' });
  await page.getByRole('button', { name: 'Next items', exact: true }).click();
  await expect(page.getByText('Page 2', { exact: true })).toBeVisible();
  await expect(page.getByText('Synthetic Wallet', { exact: true })).toHaveCount(0);
});
