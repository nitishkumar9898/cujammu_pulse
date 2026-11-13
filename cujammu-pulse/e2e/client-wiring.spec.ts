import { goToNavigation, openNavigationMenu } from './helpers/navigation';
import { test, expect, type Page } from '@playwright/test';

async function session(page: Page, role = 'STUDENT') {
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 1, name: 'Synthetic Account', role } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
}
async function readinessTasks(page: Page, eventId: number) {
  await page.route(`**/api/v1/cujammu/readiness/events/${eventId}/tasks/owners`, route => route.fulfill({ json: [{ userId: 2, name: 'Synthetic Event Owner', role: 'FACULTY' }] }));
  await page.route(`**/api/v1/cujammu/readiness/events/${eventId}/tasks`, route => route.fulfill({ json: { eventId, items: [], truncated: false } }));
}
async function start(page: Page) { await page.goto('/app/'); await page.getByRole('button', { name: 'Skip introduction' }).click(); }

test('event catalog distinguishes empty and denied responses from loading', async ({ page }) => {
  await session(page);
  let denied = false;
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: true } }));
  await page.route('**/api/v1/cujammu/events/catalog', route => route.fulfill(denied ? { status: 403, json: {} } : { json: [] }));
  await page.route('**/api/v1/cujammu/events/registrations/mine', route => route.fulfill({ json: [] }));
  await start(page); await goToNavigation(page, 'Events');
  await expect(page.getByText('No events are available for your account.')).toBeVisible();
  await expect(page.getByLabel('Loading events')).toHaveCount(0);
  denied = true; await page.getByRole('button', { name: 'Refresh events', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('cannot access');
  await expect(page.getByLabel('Loading events')).toHaveCount(0);
});

test('registration withdrawal requires confirmation and uses owner registration ID with CSRF', async ({ page }) => {
  await session(page); let cancelled = false; let writes = 0;
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: true } }));
  await page.route('**/api/v1/cujammu/events/catalog', route => route.fulfill({ json: [{ id: 7, title: 'Synthetic Event', description: 'Synthetic event', venue: 'Hall', eventDate: '2030-01-01T10:00:00', maxSeats: 20, status: 'APPROVED', targetDepartment: 'All' }] }));
  await page.route('**/api/v1/cujammu/events/registrations/mine', route => route.fulfill({ json: [{ id: 9, eventId: 7, status: cancelled ? 'CANCELLED' : 'REGISTERED', registeredAt: null }] }));
  await page.route('**/api/v1/cujammu/events/registrations/9/cancel', route => { expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf'); writes++; cancelled = true; return route.fulfill({ status: 204 }); });
  await start(page); await goToNavigation(page, 'Events');
  await page.getByRole('button', { name: 'Withdraw registration', exact: true }).click(); expect(writes).toBe(0);
  await page.getByRole('button', { name: 'Keep registration', exact: true }).click(); expect(writes).toBe(0);
  await page.getByRole('button', { name: 'Withdraw registration', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm withdrawal', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Withdraw registration', exact: true })).toHaveCount(0); expect(writes).toBe(1);
  await expect(page.getByRole('button', { name: 'Cancelled', exact: true })).toBeDisabled();
});

test('registered student can display and immediately hide an opaque short-lived entry QR', async ({ page }) => {
  await session(page); let credentialRequests = 0;
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_EVENTS: true } }));
  await page.route('**/api/v1/cujammu/events/catalog', route => route.fulfill({ json: [{ id: 7, title: 'Synthetic Event', description: 'Synthetic event', venue: 'Hall', eventDate: '2030-01-01T10:00:00', maxSeats: 20, status: 'APPROVED', targetDepartment: 'All' }] }));
  await page.route('**/api/v1/cujammu/events/registrations/mine', route => route.fulfill({ json: [{ id: 9, eventId: 7, status: 'REGISTERED', registeredAt: null }] }));
  await page.route('**/api/v1/cujammu/events/7/registrations/9/credential', route => {
    expect(route.request().method()).toBe('POST'); expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf');
    expect(route.request().postDataJSON()).toEqual({ action: 'ENTRY' }); credentialRequests++;
    return route.fulfill({ json: { action: 'ENTRY', credential: 'synthetic-bearer-secret-never-rendered-as-text', expiresAt: new Date(Date.now() + 90_000).toISOString(), credentialVersion: 1 } });
  });
  await start(page); await goToNavigation(page, 'Events');
  await page.getByRole('button', { name: 'Show entry QR pass' }).click();
  await expect(page.getByRole('heading', { name: 'Synthetic Event entry pass' })).toBeVisible();
  await expect(page.getByRole('img', { name: 'Short-lived entry QR code' })).toBeVisible();
  await expect(page.getByText(/expires automatically in/)).toBeVisible();
  await expect(page.getByText('synthetic-bearer-secret-never-rendered-as-text', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Hide pass now' }).click();
  await expect(page.getByRole('img', { name: 'Short-lived entry QR code' })).toHaveCount(0);
  expect(credentialRequests).toBe(1);
});

test.describe('campus calendar rendering', () => {
test.use({ timezoneId: 'America/Los_Angeles' });
test('calendar groups only registered events, labels missing sources and refreshes without stale entries', async ({ page }) => {
  await session(page); let reads = 0;
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_CALENDAR: true, MODULE_EVENTS: true } }));
  await page.route('**/api/v1/cujammu/events/calendar/mine*', route => {
    reads++;
    const pageNumber = new URL(route.request().url()).searchParams.get('page');
    return route.fulfill({ json: pageNumber === '1' ? {
      items: [{ registrationId: 12, eventId: 3, title: 'More synthetic event', eventDate: '2030-04-04T11:00:00', venue: 'East Hall', status: 'REGISTERED' }], page: 1, hasMore: false,
    } : reads === 1 ? {
      items: [
      { registrationId: 11, eventId: 2, title: 'Later synthetic event', eventDate: '2030-04-03T14:00:00', venue: 'North Hall', status: 'WAITLISTED' },
      { registrationId: 10, eventId: 1, title: 'Earlier synthetic event', eventDate: '2030-04-02T09:30:00', venue: 'Central Hall', status: 'REGISTERED' },
      ], page: 0, hasMore: true,
    } : {
      items: [
      { registrationId: 10, eventId: 1, title: 'Earlier synthetic event', eventDate: '2030-04-02T09:30:00', venue: 'Central Hall', status: 'REGISTERED' },
      ], page: 0, hasMore: false,
    } });
  });
  await start(page); await page.getByRole('button', { name: 'View my calendar', exact: true }).click();
  await expect(page.getByText(/Event times use campus time \(IST\)/)).toBeVisible();
  await expect(page.getByText('approved class and exam timetables, selected matches, and assigned duties are not connected yet.')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Earlier synthetic event' })).toBeVisible();
  await expect(page.getByText(/9:30\s*AM/)).toBeVisible();
  await expect(page.getByText('Tuesday, April 2, 2030', { exact: true })).toBeVisible();
  await expect(page.getByText('Registration confirmed', { exact: true })).toBeVisible();
  await expect(page.getByText('Waitlisted', { exact: true })).toBeVisible();
  const headings = await page.getByRole('heading').allTextContents();
  expect(headings.indexOf('Earlier synthetic event')).toBeLessThan(headings.indexOf('Later synthetic event'));
  await page.getByRole('button', { name: 'Load more events', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'More synthetic event' })).toBeVisible();
  await page.getByRole('button', { name: 'Refresh calendar', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Later synthetic event' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'More synthetic event' })).toHaveCount(0);
  expect(reads).toBe(3);
});
});

test('volunteer tile opens own tasks, submits hours for review and clears on disable', async ({ page }) => {
  await session(page); let enabled = true; let submitted = false;
  const task = { id: 4, eventId: 7, revision: 0, title: 'Synthetic gate task', description: null, category: 'GENERAL', status: 'ASSIGNED', hoursLogged: 0, requestedHours: null, certificateAwarded: false, assignedUserName: 'Synthetic Account', completedAt: null, reportedShiftStart: null, reportedShiftEnd: null, completionEvidence: null, reviewReason: null, canAssign: false, canSubmit: true, canApprove: false };
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_VOLUNTEERS: enabled } }));
  await page.route('**/api/v1/cujammu/volunteers/mine**', route => route.fulfill({ json: [{ ...task, status: submitted ? 'COMPLETION_REQUESTED' : 'ASSIGNED', requestedHours: submitted ? 2.5 : null, canSubmit: !submitted }] }));
  await page.route('**/api/v1/cujammu/volunteers/tasks/4/complete', route => { expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf'); const body = route.request().postDataJSON(); expect(body).toMatchObject({ expectedRevision: 0, hoursLogged: 2.5, evidence: 'Synthetic shift evidence' }); expect(body.requestKey).toMatch(/^[0-9a-f-]{36}$/i); expect(Date.parse(body.shiftStart)).toBeLessThan(Date.parse(body.shiftEnd)); submitted = true; return route.fulfill({ json: { ...task, revision: 1, status: 'COMPLETION_REQUESTED', requestedHours: 2.5, reportedShiftStart: body.shiftStart, reportedShiftEnd: body.shiftEnd, completionEvidence: body.evidence, canSubmit: false } }); });
  await start(page); await page.getByRole('button', { name: 'Open volunteer hub', exact: true }).click();
  await expect(page.getByText('Synthetic gate task', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Submit shift: Synthetic gate task', exact: true }).click();
  await page.getByRole('textbox', { name: 'Shift start with timezone' }).fill('2026-10-17T09:00:00+05:30');
  await page.getByRole('textbox', { name: 'Shift end with timezone' }).fill('2026-10-17T12:00:00+05:30');
  await page.getByRole('textbox', { name: 'Hours completed (0.25 to 24)' }).fill('2.5');
  await page.getByRole('textbox', { name: 'Completion evidence' }).fill('Synthetic shift evidence');
  await page.getByRole('button', { name: 'Submit completion for review', exact: true }).click();
  await expect(page.getByText(/COMPLETION REQUESTED.*Approved hours 0/)).toBeVisible();
  await expect(page.getByText('Submitted hours 2.5')).toBeVisible();
  enabled = false; await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(page.getByText('Synthetic gate task', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Open volunteer hub', exact: true })).toHaveCount(0);
});

test('readiness tile selects only scoped approved events and hides for students', async ({ page }) => {
  await session(page, 'FACULTY');
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_READINESS: true, MODULE_EVENTS: true } }));
  await page.route('**/api/v1/cujammu/events/operations/mine', route => route.fulfill({ json: { items: [{ id: 7, title: 'Synthetic Managed Event', status: 'APPROVED', eventDate: '2030-01-01T10:00:00', operationsRevision: 0 }] } }));
  await readinessTasks(page, 7);
  await page.route('**/api/v1/cujammu/readiness/events/7', route => route.fulfill({ json: { eventId: 7, status: 'APPROVED', ready: false, checks: [{ key: 'venue', label: 'Venue', passed: false, detail: 'Pending review' }] } }));
  await start(page); await page.getByRole('button', { name: 'Open event readiness', exact: true }).click();
  await expect(page.getByText('Needs attention · Venue', { exact: true })).toBeVisible();
  await expect(page.getByText('Pending review', { exact: true })).toBeVisible();
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 2, name: 'Synthetic Student', role: 'STUDENT' } }));
  await page.reload(); await page.getByRole('button', { name: 'Skip introduction' }).click();
  await expect(page.getByRole('button', { name: 'Open event readiness', exact: true })).toHaveCount(0);
});

test('readiness recovers from load and refresh failures without discarding confirmed data', async ({ page }) => {
  await session(page, 'FACULTY');
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_READINESS: true, MODULE_EVENTS: true } }));
  await page.route('**/api/v1/cujammu/events/operations/mine', route => route.fulfill({ json: { items: [{ id: 7, title: 'Synthetic Managed Event', status: 'APPROVED', eventDate: '2030-01-01T10:00:00', operationsRevision: 0 }] } }));
  await readinessTasks(page, 7);
  let reads = 0;
  await page.route('**/api/v1/cujammu/readiness/events/7', route => {
    reads++;
    if (reads === 1 || reads === 3) return route.fulfill({ status: 503, json: {} });
    const detail = reads === 2 ? 'Pending review' : 'Venue scheduled';
    return route.fulfill({ json: { eventId: 7, status: 'APPROVED', ready: reads > 3, checks: [{ key: 'venue', label: 'Venue', passed: reads > 3, detail }] } });
  });
  await start(page);
  await page.getByRole('button', { name: 'Open event readiness', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveText('Readiness is unavailable. Retry to check again.');
  await page.getByRole('button', { name: 'Retry readiness', exact: true }).click();
  await expect(page.getByText('Needs attention · Venue', { exact: true })).toBeVisible();
  await expect(page.getByText('Pending review', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Refresh readiness', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveText('Refresh failed. Showing the last confirmed checklist.');
  await expect(page.getByText('Pending review', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Refresh readiness', exact: true }).click();
  await expect(page.getByText('Complete · Venue', { exact: true })).toBeVisible();
  await expect(page.getByText('Venue scheduled', { exact: true })).toBeVisible();
  expect(reads).toBe(4);
});

test('readiness event switch ignores an older response from the previous event', async ({ page }) => {
  await session(page, 'FACULTY');
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_READINESS: true, MODULE_EVENTS: true } }));
  await page.route('**/api/v1/cujammu/events/operations/mine', route => route.fulfill({ json: { items: [
    { id: 7, title: 'First Managed Event', status: 'APPROVED', eventDate: '2030-01-01T10:00:00', operationsRevision: 0 },
    { id: 8, title: 'Second Managed Event', status: 'APPROVED', eventDate: '2030-01-02T10:00:00', operationsRevision: 0 },
  ] } }));
  await readinessTasks(page, 7); await readinessTasks(page, 8);
  let releaseFirstResponse!: () => void;
  const firstResponse = new Promise<void>(resolve => { releaseFirstResponse = resolve; });
  const firstRequest = page.waitForRequest('**/api/v1/cujammu/readiness/events/7');
  await page.route('**/api/v1/cujammu/readiness/events/7', async route => {
    await firstResponse;
    await route.fulfill({ json: { eventId: 7, status: 'APPROVED', ready: false, checks: [{ key: 'venue', label: 'First event venue', passed: false, detail: 'Stale first event result' }] } }).catch(() => undefined);
  });
  await page.route('**/api/v1/cujammu/readiness/events/8', route => route.fulfill({ json: { eventId: 8, status: 'APPROVED', ready: true, checks: [{ key: 'venue', label: 'Second event venue', passed: true, detail: 'Confirmed second event result' }] } }));
  await start(page);
  await page.getByRole('button', { name: 'Open event readiness', exact: true }).click();
  await firstRequest;
  await page.getByRole('checkbox', { name: 'Second Managed Event', exact: true }).click();
  await expect(page.getByText('Confirmed second event result', { exact: true })).toBeVisible();
  releaseFirstResponse();
  await expect(page.getByText('Stale first event result', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Complete · Second event venue', { exact: true })).toBeVisible();
});
test('readiness tasks use scoped owner choices, stable create keys and revisioned updates', async ({ page }) => {
  await session(page, 'FACULTY');
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_READINESS: true, MODULE_EVENTS: true } }));
  await page.route('**/api/v1/cujammu/events/operations/mine', route => route.fulfill({ json: { items: [{ id: 7, title: 'Synthetic Managed Event', status: 'APPROVED', eventDate: '2030-01-01T10:00:00', operationsRevision: 0 }] } }));
  await page.route('**/api/v1/cujammu/readiness/events/7/tasks/owners', route => route.fulfill({ json: [{ userId: 2, name: 'Synthetic Event Owner', role: 'FACULTY' }] }));
  let created: any;
  await page.route('**/api/v1/cujammu/readiness/events/7/tasks', route => {
    if (route.request().method() === 'GET') return route.fulfill({ json: { eventId: 7, items: created ? [created] : [], truncated: false } });
    expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf');
    created = { id: 20, eventId: 7, title: 'Check accessible route', description: 'Confirm ramp access at the venue.', category: 'OPERATIONS', status: 'OPEN', ownerUserId: 2, ownerName: 'Synthetic Event Owner', ownerRole: 'FACULTY', createdByUserId: 1, dueAt: '2030-01-02T04:30:00Z', revision: 0, createdAt: '2030-01-01T00:00:00Z', updatedAt: '2030-01-01T00:00:00Z' };
    const body = route.request().postDataJSON();
    expect(body.ownerUserId).toBe(2); expect(body.requestKey).toMatch(/^[0-9a-f-]{36}$/); expect(body.dueAt).toBe('2030-01-02T04:30:00.000Z');
    return route.fulfill({ json: created });
  });
  await page.route('**/api/v1/cujammu/readiness/events/7/tasks/20', route => {
    expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf');
    expect(route.request().postDataJSON().expectedRevision).toBe(created.revision);
    created = { ...created, status: route.request().postDataJSON().status, completionNote: route.request().postDataJSON().completionNote || null, revision: created.revision + 1, updatedAt: '2030-01-01T00:01:00Z' };
    return route.fulfill({ json: created });
  });
  await page.route('**/api/v1/cujammu/readiness/events/7', route => route.fulfill({ json: { eventId: 7, status: 'APPROVED', ready: false, checks: [] } }));
  await start(page); await page.getByRole('button', { name: 'Open event readiness', exact: true }).click();
  await page.getByLabel('Task title').fill('Check accessible route');
  await page.getByLabel('Task details').fill('Confirm ramp access at the venue.');
  await page.getByLabel('Due time (YYYY-MM-DDTHH:mm, IST)').fill('2030-01-02T10:00');
  await page.getByRole('button', { name: 'Add readiness task', exact: true }).click();
  await expect(page.getByText('Check accessible route', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Mark in progress', exact: true }).click();
  await expect(page.getByText(/OPERATIONS · IN PROGRESS · Due/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Mark done', exact: true })).toBeDisabled();
  await page.getByLabel('Completion note for Check accessible route').fill('Ramp access confirmed by the venue team.');
  await page.getByRole('button', { name: 'Mark done', exact: true }).click();
  await expect(page.getByText('Completion note: Ramp access confirmed by the venue team.', { exact: true })).toBeVisible();
});

test('logout immediately clears private views and keeps sign-in blocked until failed logout is retried', async ({ page }) => {
  await session(page); let signedOut = false; let attempts = 0;
  await page.route('**/api/v1/session', route => route.fulfill(signedOut ? { status: 401, json: {} } : { json: { id: 1, name: 'Synthetic Private Account', role: 'STUDENT' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: {} }));
  await page.route('**/api/v1/cujammu/recovery/library/active', route => route.fulfill({ json: [] }));
  await page.route('**/logout', route => { attempts++; if (attempts === 1) return route.fulfill({ status: 503, json: {} }); signedOut = true; return route.fulfill({ status: 204 }); });
  await start(page); await expect(page.getByText('Welcome, Synthetic Private Account', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await expect(page.getByText('Welcome, Synthetic Private Account', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Retry sign out', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toHaveCount(0);
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(page.getByText('Welcome, Synthetic Private Account', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Retry sign out', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible(); expect(attempts).toBe(2);
});
