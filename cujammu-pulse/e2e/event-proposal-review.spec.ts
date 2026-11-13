import { test, expect, type Page } from '@playwright/test';
import { goToNavigation, openNavigationMenu } from './helpers/navigation';
const initial = { id: 7, title: 'Synthetic proposal', description: 'Synthetic proposal with complete review details.', venue: 'Synthetic Hall', eventDate: '2035-04-02T10:00:00', eventEndsAt: null, organizerName: 'Synthetic faculty', maxSeats: 30, fees: 25, targetDepartment: 'ALL', categoryName: 'Synthetic workshop', generateCertificate: true, hasBanner: false, hasCertificateTemplate: false, status: 'PENDING', revision: 4, assignedReviewerId: null as number | null, assignedReviewerName: null as string | null, assignedReviewerActive: false, canAssign: true, canDecide: false, canPublish: false };
const assigned = { ...initial, revision: 5, assignedReviewerId: 1, assignedReviewerName: 'Synthetic admin', assignedReviewerActive: true, canDecide: true };
async function setup(page: Page, state = { role: 'ADMIN', enabled: true }) {
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic admin', role: state.role } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: state.enabled } }));
  await page.route('**/api/v1/admin/event-proposals/reviewers?*', route => route.fulfill({ json: { items: [{ id: 1, name: 'Synthetic admin' }], page: 0, hasNext: false } }));
  await page.route('**/api/v1/organizer/event-proposals/categories', route => route.fulfill({ json: [{ id: 5, name: 'Synthetic workshop' }] }));
  await page.goto('/app/'); await page.getByRole('button', { name: 'Skip introduction', exact: true }).click();
}
async function select(page: Page) { await goToNavigation(page, 'Event proposal review'); await page.getByRole('button', { name: 'Review Synthetic proposal', exact: true }).click(); }
test('proposal attachments render through opaque proposal-ID routes without exposing stored keys', async ({ page }) => {
  const withAttachments = { ...assigned, hasBanner: true, hasCertificateTemplate: true };
  await page.route('**/api/v1/admin/event-proposals?*', route => route.fulfill({ json: { items: [withAttachments], page: 0, hasNext: false } }));
  let delivered = 0;
  await page.route('**/api/v1/admin/event-proposals/7/media/**', route => {
    delivered++;
    return route.fulfill({ status: 200, contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/TmoAAAAASUVORK5CYII=', 'base64') });
  });
  await setup(page); await select(page);
  const banner = page.getByAltText('Proposal banner preview', { exact: true });
  const template = page.getByAltText('Certificate template preview', { exact: true });
  await expect(banner).toBeVisible(); await expect(template).toBeVisible();
  await expect(banner).toHaveAttribute('src', '/api/v1/admin/event-proposals/7/media/banner');
  await expect(template).toHaveAttribute('src', '/api/v1/admin/event-proposals/7/media/certificate-template');
  await expect.poll(() => delivered).toBe(2);
});
test('proposal attachment failure does not carry over to the next selected proposal', async ({ page }) => {
  const first = { ...assigned, hasBanner: true };
  const second = { ...first, id: 8, title: 'Second proposal' };
  await page.route('**/api/v1/admin/event-proposals?*', route => route.fulfill({ json: { items: [first, second], page: 0, hasNext: false } }));
  await page.route('**/api/v1/admin/event-proposals/7/media/banner', route => route.fulfill({ status: 410 }));
  await page.route('**/api/v1/admin/event-proposals/8/media/banner', route => route.fulfill({ contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/TmoAAAAASUVORK5CYII=', 'base64') }));
  await setup(page); await select(page);
  await expect(page.getByRole('button', { name: 'Retry attachment preview' })).toBeVisible();
  await page.getByRole('button', { name: 'Review Second proposal', exact: true }).click();
  await expect(page.getByAltText('Proposal banner preview', { exact: true })).toHaveAttribute('src', '/api/v1/admin/event-proposals/8/media/banner');
  await expect(page.getByRole('button', { name: 'Retry attachment preview' })).toHaveCount(0);
});

test('approval stays private until a separately confirmed revision-bound publication', async ({ page }) => {
  let candidate = initial; let decided = false; let published = false; let writes = 0;
  await page.route('**/api/v1/admin/event-proposals?*', route => route.fulfill({ json: { items: decided ? [] : [candidate], page: 0, hasNext: false } }));
  await page.route('**/api/v1/admin/event-proposals/publication-queue?*', route => route.fulfill({ json: { items: published ? [] : [{ ...candidate, status: 'APPROVED_PENDING_PUBLICATION', revision: 6, canAssign: false, canDecide: false, canPublish: true }], page: 0, hasNext: false } }));
  await page.route('**/api/v1/admin/event-proposals/7/reviewer', route => {
    expect(route.request().postDataJSON()).toEqual({ expectedRevision: 4, reviewerId: 1 }); expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf'); candidate = assigned; return route.fulfill({ json: assigned });
  });
  await page.route('**/api/v1/admin/event-proposals/7/approve', route => { writes++; expect(route.request().postDataJSON()).toEqual({ expectedRevision: 5 }); decided = true; return route.fulfill({ status: 204 }); });
  await page.route('**/api/v1/admin/event-proposals/7/publish', route => { writes++; expect(route.request().postDataJSON()).toEqual({ expectedRevision: 6 }); published = true; return route.fulfill({ status: 204 }); });
  await setup(page); await select(page);
  await expect(page.getByText(initial.description, { exact: true })).toBeVisible(); await expect(page.getByRole('button', { name: 'Approve proposal', exact: true })).toHaveCount(0);
  await expect(page.getByText('Capacity: 30 places', { exact: true })).toBeVisible(); await expect(page.getByText('Fees: INR 25', { exact: true })).toBeVisible(); await expect(page.getByText('Target audience: All eligible students', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Select reviewer: Synthetic admin', exact: true }).click(); await page.getByRole('button', { name: 'Save reviewer assignment', exact: true }).click();
  await expect(page.getByText('Reviewer assignment saved.', { exact: true })).toBeVisible(); await expect(page.getByRole('button', { name: 'Approve proposal', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'I reviewed the proposal and confirm approval', exact: true }).click(); await page.getByRole('button', { name: 'Approve proposal', exact: true }).click();
  await expect(page.getByText('Proposal approved. It remains private until a separate administrator publishes it.', { exact: true })).toBeVisible(); await expect(page.getByRole('button', { name: 'Approve proposal', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Reload queue', exact: true }).click(); await expect(page.getByText('No pending proposals on this page.', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Ready to publish', exact: true }).click(); await page.getByRole('button', { name: 'Review Synthetic proposal', exact: true }).click();
  await page.getByRole('button', { name: 'I reviewed the visibility and confirm publication', exact: true }).click(); await page.getByRole('button', { name: 'Publish event', exact: true }).click();
  await expect(page.getByText('Publication confirmed. The event is now public and its approved notices have been queued.', { exact: true })).toBeVisible(); expect(writes).toBe(2);
});
test('assigned reviewer rejects with a bounded reason and does not auto-approve', async ({ page }) => {
  let command: unknown;
  await page.route('**/api/v1/admin/event-proposals?*', route => route.fulfill({ json: { items: [assigned], page: 0, hasNext: false } }));
  await page.route('**/api/v1/admin/event-proposals/7/reject', route => { command = route.request().postDataJSON(); return route.fulfill({ status: 204 }); });
  await setup(page); await select(page); await expect(page.getByRole('button', { name: 'Reject proposal', exact: true })).toBeDisabled();
  await page.getByRole('textbox', { name: 'Proposal rejection reason', exact: true }).fill('Clarify the synthetic programme.'); await page.getByRole('button', { name: 'Reject proposal', exact: true }).click();
  await expect(page.getByText('Proposal rejection confirmed. Reload the queue to continue.', { exact: true })).toBeVisible(); expect(command).toEqual({ expectedRevision: 5, reason: 'Clarify the synthetic programme.' });
});
test('mismatched assignment receipt clears private selection and requires authoritative reload', async ({ page }) => {
  let writes = 0;
  await page.route('**/api/v1/admin/event-proposals?*', route => route.fulfill({ json: { items: [writes ? { ...assigned, assignedReviewerId: 2, assignedReviewerName: 'Other reviewer', canDecide: false } : initial], page: 0, hasNext: false } }));
  await page.route('**/api/v1/admin/event-proposals/7/reviewer', route => { writes++; return route.fulfill({ json: { ...assigned, id: 8 } }); });
  await setup(page); await select(page); await page.getByRole('button', { name: 'Select reviewer: Synthetic admin', exact: true }).click(); await page.getByRole('button', { name: 'Save reviewer assignment', exact: true }).click();
  await expect(page.getByText(/The change was not confirmed/)).toBeVisible(); await expect(page.getByRole('button', { name: 'Save reviewer assignment', exact: true })).toHaveCount(0); await expect(page.getByText(initial.description, { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Reload queue', exact: true }).click(); await page.getByRole('button', { name: 'Review Synthetic proposal', exact: true }).click();
  await expect(page.getByText('Assigned reviewer: Other reviewer', { exact: true })).toBeVisible(); await expect(page.getByRole('button', { name: 'Approve proposal', exact: true })).toHaveCount(0); expect(writes).toBe(1);
});
test('stale decision cannot be blindly retried and forbidden reload removes proposal data', async ({ page }) => {
  let forbidden = false; let writes = 0;
  await page.route('**/api/v1/admin/event-proposals?*', route => forbidden ? route.fulfill({ status: 403 }) : route.fulfill({ json: { items: [assigned], page: 0, hasNext: false } }));
  await page.route('**/api/v1/admin/event-proposals/7/reject', route => { writes++; forbidden = true; return route.fulfill({ status: 409 }); });
  await setup(page); await select(page); await page.getByRole('textbox', { name: 'Proposal rejection reason', exact: true }).fill('Synthetic reviewed reason'); await page.getByRole('button', { name: 'Reject proposal', exact: true }).click();
  await expect(page.getByText(/The change was not confirmed/)).toBeVisible(); await expect(page.getByRole('textbox', { name: 'Proposal rejection reason', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Reload queue', exact: true }).click(); await expect(page.getByText(/Review access is unavailable/)).toBeVisible(); await expect(page.getByText(initial.description, { exact: true })).toHaveCount(0); expect(writes).toBe(1);
});
for (const revoked of ['feature', 'role']) test(`${revoked} removal clears the current review and removes its navigation`, async ({ page }) => {
  const state = { role: 'ADMIN', enabled: true };
  await page.route('**/api/v1/admin/event-proposals?*', route => route.fulfill({ json: { items: [assigned], page: 0, hasNext: false } }));
  await setup(page, state); await select(page); await expect(page.getByText(initial.description, { exact: true })).toBeVisible();
  if (revoked === 'feature') state.enabled = false; else state.role = 'FACULTY'; await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(page.getByText(initial.description, { exact: true })).toHaveCount(0); await openNavigationMenu(page); await expect(page.getByRole('tab', { name: 'Event proposal review', exact: true })).toHaveCount(0);
});
test('queue and reviewer paging select returned records without manual internal IDs', async ({ page }) => {
  let command: unknown;
  await page.route('**/api/v1/admin/event-proposals?*', route => {
    const currentPage = Number(new URL(route.request().url()).searchParams.get('page'));
    return route.fulfill({ json: { items: currentPage === 0 ? [] : [initial], page: currentPage, hasNext: currentPage === 0 } });
  });
  await setup(page);
  await page.route('**/api/v1/admin/event-proposals/reviewers?*', route => {
    const currentPage = Number(new URL(route.request().url()).searchParams.get('page'));
    return route.fulfill({ json: { items: [{ id: currentPage + 1, name: currentPage === 0 ? 'Synthetic admin' : 'Other reviewer' }], page: currentPage, hasNext: currentPage === 0 } });
  });
  await page.route('**/api/v1/admin/event-proposals/7/reviewer', route => { command = route.request().postDataJSON(); return route.fulfill({ json: { ...assigned, assignedReviewerId: 2, assignedReviewerName: 'Other reviewer', canDecide: false } }); });
  await goToNavigation(page, 'Event proposal review'); await page.getByRole('button', { name: 'Next proposals', exact: true }).click(); await page.getByRole('button', { name: 'Review Synthetic proposal', exact: true }).click();
  await page.getByRole('button', { name: 'Next reviewers', exact: true }).click(); await expect(page.getByRole('button', { name: 'Select reviewer: Synthetic admin', exact: true })).toHaveCount(0); await page.getByRole('button', { name: 'Select reviewer: Other reviewer', exact: true }).click(); await page.getByRole('button', { name: 'Save reviewer assignment', exact: true }).click();
  await expect(page.getByText('Assigned reviewer: Other reviewer', { exact: true })).toBeVisible(); expect(command).toEqual({ expectedRevision: 4, reviewerId: 2 });
  await page.getByRole('button', { name: 'Previous proposals', exact: true }).click(); await expect(page.getByText(initial.description, { exact: true })).toHaveCount(0);
});
test('organizer sees own private proposal status in the shared events screen', async ({ page }) => {
  await page.route('**/api/v1/cujammu/events/catalog', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/cujammu/events/registrations/mine', route => route.fulfill({ json: [] }));
  let ownerReads = 0;
  await page.route('**/api/v1/organizer/event-proposals?*', route => { ownerReads++; return route.fulfill({ json: { items: [{ id: 43, title: 'Owner synthetic proposal', venue: 'Synthetic hall', eventDate: '2035-04-02T10:00:00', status: 'REJECTED', revision: 8, rejectionReason: 'Please clarify the synthetic schedule.', submittedAt: '2035-01-01T10:00:00' }], page: 0, hasNext: false } }); });
  await setup(page, { role: 'ORGANIZER', enabled: true }); await goToNavigation(page, 'Events');
  await expect(page.getByText('Owner synthetic proposal · REJECTED', { exact: true })).toBeVisible();
  await expect(page.getByText('Reviewer note: Please clarify the synthetic schedule.', { exact: true })).toBeVisible();
  expect(ownerReads).toBeGreaterThan(0);
});
test('organizer edits and resubmits a rejected proposal using its current revision', async ({ page }) => {
  await page.route('**/api/v1/cujammu/events/catalog', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/cujammu/events/registrations/mine', route => route.fulfill({ json: [] }));
  const detail = { id: 43, title: 'Rejected synthetic proposal', description: 'Synthetic proposal details with enough words to edit.', categoryId: 5, venue: 'Synthetic hall', eventDate: '2035-04-02T10:00:00', eventEndsAt: null, maxSeats: 30, fees: 0, contactEmail: null, targetDepartment: 'ALL', generateCertificate: true, revision: 8, hasBanner: false, hasCertificateTemplate: false };
  let saved = false; let writes = 0;
  await page.route('**/api/v1/organizer/event-proposals?*', route => route.fulfill({ json: { items: [{ id: 43, title: saved ? 'Revised synthetic proposal' : detail.title, venue: 'Synthetic hall', eventDate: detail.eventDate, status: saved ? 'PENDING' : 'REJECTED', revision: saved ? 9 : 8, rejectionReason: saved ? null : 'Please clarify the synthetic schedule.', submittedAt: '2035-01-01T10:00:00' }], page: 0, hasNext: false } }));
  await page.route('**/api/v1/organizer/event-proposals/43', route => route.request().method() === 'GET' ? route.fulfill({ json: saved ? { ...detail, title: 'Revised synthetic proposal', revision: 9 } : detail }) : (() => { writes++; expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf'); expect(route.request().postDataJSON()).toMatchObject({ expectedRevision: 8, title: 'Revised synthetic proposal' }); expect(route.request().postDataJSON().organizerId).toBeUndefined(); saved = true; return route.fulfill({ json: { ...detail, title: 'Revised synthetic proposal', revision: 9 } }); })());
  await setup(page, { role: 'ORGANIZER', enabled: true }); await goToNavigation(page, 'Events');
  await page.getByRole('button', { name: 'Edit and resubmit', exact: true }).click(); await expect(page.getByRole('textbox', { name: 'Edit proposal title', exact: true })).toHaveValue(detail.title);
  await page.getByRole('textbox', { name: 'Edit proposal title', exact: true }).fill('Revised synthetic proposal'); await page.getByRole('button', { name: 'Save and resubmit', exact: true }).click();
  await expect(page.getByText('Proposal updated and returned to the administrator review queue.', { exact: true })).toBeVisible();
  await expect(page.getByText('Revised synthetic proposal · PENDING', { exact: true })).toBeVisible(); expect(writes).toBe(1);
});
test('proposal edit rejects impossible campus-local dates before sending an update', async ({ page }) => {
  await page.route('**/api/v1/cujammu/events/catalog', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/cujammu/events/registrations/mine', route => route.fulfill({ json: [] }));
  const detail = { id: 43, title: 'Rejected synthetic proposal', description: 'Synthetic proposal details with enough words to edit.', categoryId: 5, venue: 'Synthetic hall', eventDate: '2035-04-02T10:00:00', eventEndsAt: null, maxSeats: 30, fees: 0, contactEmail: null, targetDepartment: 'ALL', generateCertificate: true, revision: 8, hasBanner: false, hasCertificateTemplate: false };
  await page.route('**/api/v1/organizer/event-proposals?*', route => route.fulfill({ json: { items: [{ id: 43, title: detail.title, venue: detail.venue, eventDate: detail.eventDate, status: 'REJECTED', revision: 8, rejectionReason: 'Please revise.', submittedAt: '2035-01-01T10:00:00' }], page: 0, hasNext: false } }));
  let writes = 0;
  await page.route('**/api/v1/organizer/event-proposals/43', route => {
    if (route.request().method() === 'GET') return route.fulfill({ json: detail });
    writes++; return route.fulfill({ status: 204 });
  });
  await setup(page, { role: 'ORGANIZER', enabled: true }); await goToNavigation(page, 'Events'); await page.getByRole('button', { name: 'Edit and resubmit', exact: true }).click();
  await page.getByRole('textbox', { name: 'Edit proposal start date and time', exact: true }).fill('2035-02-31T10:00');
  await page.getByRole('button', { name: 'Save and resubmit', exact: true }).click();
  await expect(page.getByText('Check required fields, campus-local date/time, event interval, capacity and nonnegative fee.', { exact: true })).toBeVisible();
  expect(writes).toBe(0);
});
test('proposal attachment upload freezes competing writes and retries with the same key', async ({ page }) => {
  let releaseRefresh!: () => void;
  const refreshGate = new Promise<void>(resolve => { releaseRefresh = resolve; });
  let writes = 0; const keys: string[] = [];
  await page.route('**/api/v1/cujammu/events/catalog', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/cujammu/events/registrations/mine', route => route.fulfill({ json: [] }));
  const detail = { id: 43, title: 'Attachment proposal', description: 'Synthetic proposal details with enough words to edit.', categoryId: 5, venue: 'Synthetic hall', eventDate: '2035-04-02T10:00:00', eventEndsAt: null, maxSeats: 30, fees: 0, contactEmail: null, targetDepartment: 'ALL', generateCertificate: true, revision: 8, hasBanner: false, hasCertificateTemplate: false };
  await page.route('**/api/v1/organizer/event-proposals?*', async route => {
    if (writes === 2) await refreshGate;
    await route.fulfill({ json: { items: [{ ...detail, status: 'PENDING', rejectionReason: null, submittedAt: '2035-01-01T10:00:00' }], page: 0, hasNext: false } });
  });
  await page.route('**/api/v1/organizer/event-proposals/43', route => route.fulfill({ json: detail }));
  await page.route('**/api/v1/organizer/event-proposals/43/attachments/banner', route => {
    writes++; const body = route.request().postData() ?? '';
    expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf');
    const key = body.match(/name="requestKey"\r\n\r\n([^\r]+)/)?.[1]; expect(key).toBeTruthy(); keys.push(key!);
    if (writes === 1) return route.fulfill({ status: 503 });
    return route.fulfill({ json: { id: 43, revision: 9, kind: 'banner', replayed: true, uploadAcceptedAt: '2035-04-01T10:00:00Z', expiresAt: '2035-05-01T10:00:00Z' } });
  });
  await page.route('**/api/v1/organizer/event-proposals/43/media/banner', route => route.fulfill({ status: 410 }));
  await setup(page, { role: 'ORGANIZER', enabled: true }); await goToNavigation(page, 'Events');
  await page.getByRole('button', { name: 'Edit and resubmit', exact: true }).click();
  await page.getByRole('textbox', { name: 'Edit proposal title', exact: true }).fill('Unsaved draft stays');
  const chooserPromise = page.waitForEvent('filechooser');
  await page.getByRole('button', { name: 'Choose banner image', exact: true }).click();
  await (await chooserPromise).setFiles({ name: 'synthetic.png', mimeType: 'image/png', buffer: Buffer.from('synthetic-image') });
  await expect(page.getByRole('button', { name: 'Save and resubmit', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Choose certificate template image', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Upload banner', exact: true }).click();
  await page.getByRole('button', { name: 'Retry banner upload', exact: true }).click();
  await expect(page.getByText(/Attachment saved\. Available until/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Save and resubmit', exact: true })).toBeDisabled();
  releaseRefresh();
  await expect(page.getByRole('textbox', { name: 'Edit proposal title', exact: true })).toHaveValue('Unsaved draft stays');
  await expect(page.getByRole('button', { name: 'Save and resubmit', exact: true })).toBeEnabled();
  expect(writes).toBe(2); expect(keys[0]).toBe(keys[1]);
});

test('stale shared proposal edit requires an authoritative revision reload', async ({ page }) => {
  await page.route('**/api/v1/cujammu/events/catalog', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/cujammu/events/registrations/mine', route => route.fulfill({ json: [] }));
  const detail = { id: 43, title: 'Rejected synthetic proposal', description: 'Synthetic proposal details with enough words to edit.', categoryId: 5, venue: 'Synthetic hall', eventDate: '2035-04-02T10:00:00', eventEndsAt: null, maxSeats: 30, fees: 0, contactEmail: null, targetDepartment: 'ALL', generateCertificate: true, revision: 8, hasBanner: false, hasCertificateTemplate: false };
  let stale = false; let writes = 0;
  await page.route('**/api/v1/organizer/event-proposals?*', route => route.fulfill({ json: { items: [{ id: 43, title: detail.title, venue: detail.venue, eventDate: detail.eventDate, status: 'REJECTED', revision: stale ? 9 : 8, rejectionReason: 'Please revise.', submittedAt: '2035-01-01T10:00:00' }], page: 0, hasNext: false } }));
  await page.route('**/api/v1/organizer/event-proposals/43', route => {
    if (route.request().method() === 'GET') return route.fulfill({ json: { ...detail, revision: stale ? 9 : 8 } });
    writes++; stale = true; return route.fulfill({ status: 409 });
  });
  await setup(page, { role: 'ORGANIZER', enabled: true }); await goToNavigation(page, 'Events'); await page.getByRole('button', { name: 'Edit and resubmit', exact: true }).click();
  await page.getByRole('textbox', { name: 'Edit proposal title', exact: true }).fill('Attempted stale edit'); await page.getByRole('button', { name: 'Save and resubmit', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Reload proposal revision', exact: true })).toBeVisible(); await expect(page.getByRole('textbox', { name: 'Edit proposal title', exact: true })).toHaveAttribute('readonly', '');
  await page.getByRole('button', { name: 'Reload proposal revision', exact: true }).click(); await expect(page.getByRole('textbox', { name: 'Edit proposal title', exact: true })).toHaveValue(detail.title);
  await expect(page.getByText(/Current revision: 9\./)).toBeVisible(); expect(writes).toBe(1);
});
test('organizer submits a proposal and safely retries an uncertain response with the same key', async ({ page }) => {
  await page.route('**/api/v1/cujammu/events/catalog', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/cujammu/events/registrations/mine', route => route.fulfill({ json: [] }));
  let posts = 0; const requestKeys: string[] = [];
  await page.route('**/api/v1/organizer/event-proposals?*', route => route.fulfill({ json: { items: posts > 1 ? [{ id: 51, title: 'New synthetic event', venue: 'Synthetic hall', eventDate: '2035-04-02T10:00:00', status: 'PENDING', revision: 0, rejectionReason: null, submittedAt: '2035-01-01T10:00:00' }] : [], page: 0, hasNext: false } }));
  await page.route('**/api/v1/organizer/event-proposals', route => {
    posts++; const command = route.request().postDataJSON(); requestKeys.push(command.requestKey);
    expect(command.categoryId).toBe(5); expect(command.status).toBeUndefined(); expect(command.organizerId).toBeUndefined();
    return posts === 1 ? route.fulfill({ status: 503 }) : route.fulfill({ json: { id: 51, status: 'PENDING', revision: 0, replayed: true } });
  });
  await setup(page, { role: 'ORGANIZER', enabled: true }); await goToNavigation(page, 'Events');
  await page.getByRole('textbox', { name: 'Proposal title', exact: true }).fill('New synthetic event');
  await page.getByRole('textbox', { name: 'Proposal description', exact: true }).fill('A synthetic event proposal with complete review details.');
  await page.getByRole('button', { name: 'Synthetic workshop', exact: true }).click();
  await page.getByRole('textbox', { name: 'Proposal venue', exact: true }).fill('Synthetic hall');
  await page.getByRole('textbox', { name: 'Proposal start date and time', exact: true }).fill('2035-04-02T10:00');
  await page.getByRole('button', { name: 'Submit proposal for review', exact: true }).click();
  await expect(page.getByText(/Retry unchanged to recover the saved receipt/)).toBeVisible();
  await page.getByRole('button', { name: 'Retry proposal submission', exact: true }).click();
  await expect(page.getByText(/Submission confirmed from its saved receipt/)).toBeVisible();
  await expect(page.getByText('New synthetic event · PENDING', { exact: true })).toBeVisible();
  expect(posts).toBe(2); expect(requestKeys[0]).toMatch(/^[0-9a-f-]{36}$/i); expect(requestKeys[1]).toBe(requestKeys[0]);
});
test('proposal form rejects impossible calendar dates and accepts a valid leap-day value', async ({ page }) => {
  await page.route('**/api/v1/cujammu/events/catalog', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/cujammu/events/registrations/mine', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/organizer/event-proposals?*', route => route.fulfill({ json: { items: [], page: 0, hasNext: false } }));
  let posts = 0;
  await page.route('**/api/v1/organizer/event-proposals', route => { posts++; return route.fulfill({ json: { id: 52, status: 'PENDING', revision: 0, replayed: false } }); });
  await setup(page, { role: 'FACULTY', enabled: true }); await goToNavigation(page, 'Events');
  await page.getByRole('textbox', { name: 'Proposal title', exact: true }).fill('Leap day workshop');
  await page.getByRole('textbox', { name: 'Proposal description', exact: true }).fill('A synthetic event proposal with complete review details.');
  await page.getByRole('button', { name: 'Synthetic workshop', exact: true }).click();
  await page.getByRole('textbox', { name: 'Proposal venue', exact: true }).fill('Synthetic hall');
  const dateField = page.getByRole('textbox', { name: 'Proposal start date and time', exact: true });
  await dateField.fill('2036-02-31T10:00');
  await page.getByRole('button', { name: 'Submit proposal for review', exact: true }).click();
  await expect(page.getByText('Check the required fields, campus-local date/time, event interval, capacity and nonnegative fee.', { exact: true })).toBeVisible();
  expect(posts).toBe(0);
  await dateField.fill('2036-02-29T10:00'); await page.getByRole('button', { name: 'Submit proposal for review', exact: true }).click();
  await expect(page.getByText('Proposal 52 submitted for review.', { exact: true })).toBeVisible(); expect(posts).toBe(1);
});
test('non-administrator has no review entry point or private queue request', async ({ page }) => {
  let requests = 0; await page.route('**/api/v1/admin/event-proposals?*', route => { requests++; return route.fulfill({ status: 403 }); });
  await setup(page, { role: 'FACULTY', enabled: true }); await openNavigationMenu(page); await expect(page.getByRole('tab', { name: 'Event proposal review', exact: true })).toHaveCount(0); expect(requests).toBe(0);
});
