import { goToNavigation, openNavigationMenu } from './helpers/navigation';
import { expect, test } from '@playwright/test';

test('sports UI records live score, then provisional and explicitly confirmed result with revision reason', async ({ page }) => {
  const writes: Array<Record<string, unknown>> = [];
  let fixture: Record<string, unknown> = {
    id: 21, eventId: 7, tournamentName: 'Synthetic Cup', roundNumber: 1, roundName: 'Final', matchNumber: 1,
    team1Id: 101, team1Name: 'CS', team2Id: 102, team2Name: 'EE', scoreTeam1: '0', scoreTeam2: '0',
    winnerId: null, winnerName: null, status: 'SCHEDULED', matchTime: '2030-01-01T10:00:00Z', venue: 'Synthetic Ground', revision: 0,
  };
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 4, name: 'Synthetic Organizer', role: 'ORGANIZER' } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_TOURNAMENTS: true } }));
  await page.route('**/api/v1/cujammu/events/operations/my-operator-events', route => route.fulfill({ json: { items: [{ id: 7, title: 'Synthetic Cup event', status: 'APPROVED', eventDate: '2030-01-01T10:00:00', operationsRevision: 1 }] } }));
  await page.route('**/api/v1/cujammu/events/7/teams', route => route.fulfill({ json: { items: [
    { id: 101, revision: 1, name: 'CS', minSize: 2, maxSize: 5, memberCount: 2, locked: true, canManage: false, inviteCode: null, createdAt: '2029-12-01T10:00:00' },
    { id: 102, revision: 1, name: 'EE', minSize: 2, maxSize: 5, memberCount: 2, locked: true, canManage: false, inviteCode: null, createdAt: '2029-12-01T10:00:00' },
  ] } }));
  await page.route('**/api/v1/cujammu/events/7/tournaments/fixtures', route => route.fulfill({ json: [fixture] }));
  await page.route('**/api/v1/cujammu/tournaments/fixtures/21/score', route => {
    expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf');
    const command = route.request().postDataJSON() as Record<string, unknown>;
    writes.push(command);
    fixture = { ...fixture, ...command, winnerName: command.winnerId === 101 ? 'CS' : command.winnerId === 102 ? 'EE' : null, revision: Number(fixture.revision) + 1 };
    return route.fulfill({ json: fixture });
  });

  await page.goto('/app/');
  await page.getByRole('button', { name: 'Skip introduction' }).click();
  await goToNavigation(page, 'Sports');
  await page.getByRole('checkbox', { name: /Synthetic Cup event/ }).check();
  await page.getByRole('button', { name: 'Load fixtures' }).click();
  await expect(page.getByRole('button', { name: 'Load fixtures' })).toBeEnabled();
  await page.getByRole('button', { name: 'Update score' }).click();
  await page.getByLabel('CS score').fill('2'); await page.getByLabel('EE score').fill('1');
  await page.getByRole('button', { name: 'Live score', exact: true }).last().click();
  await expect(page.getByRole('button', { name: 'Load fixtures' })).toBeEnabled();
  expect(writes[0]?.status).toBe('LIVE'); expect(writes[0]?.winnerId).toBeNull();

  await page.getByRole('button', { name: 'Update score' }).click();
  await page.getByLabel('CS score').fill('2'); await page.getByLabel('EE score').fill('1');
  await page.getByRole('button', { name: 'Provisional result', exact: true }).first().click();
  await page.getByRole('checkbox', { name: 'CS', exact: true }).click();
  await page.getByLabel('Change reason').fill('Synthetic referee review');
  await page.getByRole('button', { name: 'Provisional result', exact: true }).last().click();
  await expect(page.getByRole('button', { name: 'Load fixtures' })).toBeEnabled();
  expect(writes[1]?.status).toBe('PROVISIONAL_RESULT'); expect(writes[1]?.winnerId).toBe(101);

  await page.getByRole('button', { name: 'Update score' }).click();
  await page.getByRole('button', { name: 'Final result', exact: true }).first().click();
  await page.getByLabel('Change reason').fill('Synthetic final confirmation');
  await page.getByRole('button', { name: 'Final result', exact: true }).last().click();
  await expect(page.getByRole('button', { name: 'Load fixtures' })).toBeEnabled();
  expect(writes[2]?.status).toBe('FINAL'); expect(writes[2]?.winnerId).toBe(101);
  expect(writes[2]?.changeReason).toBe('Synthetic final confirmation');
  await expect(page.getByText('Confirmed winner: CS')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Update score' })).toHaveCount(0);
});

test('sports fixture creation selects managed event roster and preserves key after uncertain response', async ({ page }) => {
  const writes: Array<Record<string, unknown>> = [];
  let eventFetches = 0;
  let fixture: Record<string, unknown> | null = null;
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 4, name: 'Synthetic Organizer', role: 'ORGANIZER' } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_TOURNAMENTS: true } }));
  await page.route('**/api/v1/cujammu/events/operations/my-operator-events', route => {
    eventFetches += 1;
    if (eventFetches === 1) return route.fulfill({ status: 503, json: { message: 'Synthetic temporary error' } });
    return route.fulfill({ json: { items: [{ id: 7, title: 'Synthetic Cup event', status: 'APPROVED', eventDate: '2030-01-01T10:00:00', operationsRevision: 1 }] } });
  });
  await page.route('**/api/v1/cujammu/events/7/teams', route => route.fulfill({ json: { items: [
    { id: 101, revision: 1, name: 'CS', minSize: 2, maxSize: 5, memberCount: 2, locked: true, canManage: false, inviteCode: null, createdAt: '2029-12-01T10:00:00' },
    { id: 102, revision: 1, name: 'EE', minSize: 2, maxSize: 5, memberCount: 2, locked: true, canManage: false, inviteCode: null, createdAt: '2029-12-01T10:00:00' },
  ] } }));
  await page.route('**/api/v1/cujammu/events/7/tournaments/fixtures', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/cujammu/tournaments/fixtures', async route => {
    expect(route.request().method()).toBe('POST');
    expect(route.request().headers()['x-csrf-token']).toBe('synthetic-csrf');
    const command = route.request().postDataJSON() as Record<string, unknown>;
    writes.push(command);
    if (writes.length === 1) {
      fixture = { id: 22, eventId: 7, tournamentName: command.tournamentName, roundNumber: command.roundNumber, roundName: command.roundName,
        matchNumber: command.matchNumber, team1Id: 101, team1Name: 'CS', team2Id: 102, team2Name: 'EE', scoreTeam1: '0', scoreTeam2: '0',
        winnerId: null, winnerName: null, status: 'SCHEDULED', matchTime: command.matchTime, venue: command.venue, revision: 0 };
      return route.fulfill({ status: 503, json: { message: 'Synthetic uncertain response' } });
    }
    expect(command.requestKey).toBe(writes[0]?.requestKey);
    return route.fulfill({ json: fixture });
  });

  await page.goto('/app/');
  await page.getByRole('button', { name: 'Skip introduction' }).click();
  await goToNavigation(page, 'Sports');
  await expect(page.getByRole('button', { name: 'Retry loading managed events' })).toBeVisible();
  await page.getByRole('button', { name: 'Retry loading managed events' }).click();
  await expect(page.getByRole('checkbox', { name: /Synthetic Cup event/ })).toBeVisible();
  await page.getByRole('checkbox', { name: /Synthetic Cup event/ }).check();
  await page.getByRole('button', { name: 'Load fixtures' }).click();
  await expect(page.getByRole('button', { name: 'Create fixture' })).toBeVisible();
  await page.getByLabel('Tournament name').fill('Inter-department Cup');
  await page.getByLabel('Round name').fill('Final');
  await page.getByLabel('Campus-local match time').fill('2030-01-01T10:30');
  await page.getByRole('checkbox', { name: 'First team: CS', exact: true }).check();
  await page.getByRole('checkbox', { name: 'Second team: EE', exact: true }).check();
  await page.getByRole('button', { name: 'Create fixture' }).click();
  await expect(page.getByRole('alert')).toContainText('could not complete this request');
  await expect(page.getByText('The save outcome is uncertain.', { exact: false })).toBeVisible();
  await expect(page.getByRole('checkbox', { name: /Synthetic Cup event/ })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Load fixtures' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Retry same fixture request' })).toBeEnabled();
  expect(writes).toHaveLength(1);
  expect(writes[0]?.eventId).toBe(7); expect(writes[0]?.team1Id).toBe(101); expect(writes[0]?.team2Id).toBe(102);
  expect(writes[0]?.matchTime).toBe('2030-01-01T10:30:00');
  await page.getByRole('button', { name: 'Retry same fixture request' }).click();
  await expect(page.getByText('Inter-department Cup · Final · Match 1')).toBeVisible();
  expect(writes).toHaveLength(2);
});

test('definitive fixture rejection unlocks correction with a new request key', async ({ page }) => {
  const writes: Array<Record<string, unknown>> = [];
  await page.route('**/api/v1/session', route => route.fulfill({ json: { id: 4, name: 'Synthetic Organizer', role: 'ORGANIZER' } }));
  await page.route('**/api/v1/session/csrf', route => route.fulfill({ json: { token: 'synthetic-csrf', headerName: 'X-CSRF-TOKEN' } }));
  await page.route('**/api/v1/cujammu/features', route => route.fulfill({ json: { MODULE_TOURNAMENTS: true } }));
  await page.route('**/api/v1/cujammu/events/operations/my-operator-events', route => route.fulfill({ json: { items: [{ id: 7, title: 'Synthetic Cup event', status: 'APPROVED', eventDate: '2030-01-01T10:00:00', operationsRevision: 1 }] } }));
  await page.route('**/api/v1/cujammu/events/7/teams', route => route.fulfill({ json: { items: [
    { id: 101, revision: 1, name: 'CS', minSize: 2, maxSize: 5, memberCount: 2, locked: true, canManage: false, inviteCode: null, createdAt: '2029-12-01T10:00:00' },
    { id: 102, revision: 1, name: 'EE', minSize: 2, maxSize: 5, memberCount: 2, locked: true, canManage: false, inviteCode: null, createdAt: '2029-12-01T10:00:00' },
  ] } }));
  await page.route('**/api/v1/cujammu/events/7/tournaments/fixtures', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/cujammu/tournaments/fixtures', route => {
    const command = route.request().postDataJSON() as Record<string, unknown>;
    writes.push(command);
    if (writes.length === 1) return route.fulfill({ status: 400, json: { message: 'Synthetic definite rejection' } });
    return route.fulfill({ json: { id: 31, eventId: 7, tournamentName: command.tournamentName, roundNumber: 1, roundName: 'Final', matchNumber: 1,
      team1Id: 101, team1Name: 'CS', team2Id: 102, team2Name: 'EE', scoreTeam1: '0', scoreTeam2: '0', winnerId: null, winnerName: null,
      status: 'SCHEDULED', matchTime: command.matchTime, venue: null, revision: 0 } });
  });

  await page.goto('/app/');
  await page.getByRole('button', { name: 'Skip introduction' }).click();
  await goToNavigation(page, 'Sports');
  await page.getByRole('checkbox', { name: /Synthetic Cup event/ }).check();
  await page.getByRole('button', { name: 'Load fixtures' }).click();
  await page.getByLabel('Tournament name').fill('First attempt');
  await page.getByLabel('Round name').fill('Final');
  await page.getByLabel('Campus-local match time').fill('2030-01-01T10:30');
  await page.getByRole('checkbox', { name: 'First team: CS', exact: true }).check();
  await page.getByRole('checkbox', { name: 'Second team: EE', exact: true }).check();
  await page.getByRole('button', { name: 'Create fixture' }).click();
  await expect(page.getByLabel('Tournament name')).toBeEnabled();
  await page.getByLabel('Tournament name').fill('Corrected attempt');
  await expect(page.getByRole('button', { name: 'Create fixture' })).toBeEnabled();
  await page.getByRole('button', { name: 'Create fixture' }).click();
  await expect(page.getByText('Corrected attempt · Final · Match 1')).toBeVisible();
  expect(writes).toHaveLength(2);
  expect(writes[0]?.requestKey).not.toBe(writes[1]?.requestKey);
});
