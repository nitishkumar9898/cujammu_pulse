import { test, expect } from '@playwright/test';
import { goToNavigation } from './helpers/navigation';

test.use({ timezoneId: 'America/Los_Angeles' });
test('private clash advice is explicitly requested, refreshes, and clears when disabled', async ({ page }) => {
  let enabled=true, reads=0, writes=0;
  await page.route('**/api/v1/session', route=>route.fulfill({json:{id:1,name:'Synthetic',role:'STUDENT'}}));
  await page.route('**/api/v1/cujammu/features', route=>route.fulfill({json:{MODULE_EVENTS:true,MODULE_CALENDAR:true,ACTION_CALENDAR_CLASH_CHECK:enabled}}));
  await page.route('**/api/v1/cujammu/events/catalog', route=>route.fulfill({json:[{id:7,title:'Target event',description:'Synthetic event',venue:'Hall',eventDate:'2035-04-02T10:00:00',maxSeats:20,status:'APPROVED',targetDepartment:'ALL'}]}));
  await page.route('**/api/v1/cujammu/events/registrations/mine', route=>route.fulfill({json:[]}));
  await page.route('**/api/v1/cujammu/events/7/registrations', route=>{writes++;return route.fulfill({status:500});});
  await page.route('**/api/v1/cujammu/events/calendar/clashes/7', route=>{
    reads++;
    return route.fulfill({json:{eventId:7,startsAt:'2035-04-02T10:00:00',endsAt:'2035-04-02T12:00:00',lifecycleVersion:4,checkedAt:'2035-04-01T05:00:00Z',hasMore:false,items:reads===1?[{entry:{registrationId:5,eventId:8,title:'My existing seminar',eventDate:'2035-04-02T11:00:00',eventEndsAt:'2035-04-02T13:00:00',venue:'East Hall',status:'REGISTERED',eventStatus:'APPROVED',lifecycleVersion:2},assessment:'OVERLAP'}]:[]}});
  });
  await page.goto('/app/');await page.getByRole('button',{name:'Skip introduction'}).click();await goToNavigation(page,'Events');
  expect(reads).toBe(0);
  await page.getByRole('button',{name:'Check schedule clashes',exact:true}).click();
  await expect(page.getByText('Overlapping event: My existing seminar',{exact:true})).toBeVisible();
  await expect(page.getByText(/Checked schedule:.*10:00\s*AM/)).toBeVisible();
  await expect(page.getByText(/Classes, exams, matches, duties and travel time are not included/)).toBeVisible();
  await expect(page.getByRole('button',{name:'Register',exact:true})).toBeEnabled();expect(writes).toBe(0);
  await page.getByRole('button',{name:'Check schedule clashes',exact:true}).click();
  await expect(page.getByText(/No overlap found among your known confirmed event intervals/)).toBeVisible();
  await expect(page.getByText('Overlapping event: My existing seminar',{exact:true})).toHaveCount(0);
  enabled=false;await page.evaluate(()=>window.dispatchEvent(new Event('focus')));
  await expect(page.getByRole('button',{name:'Check schedule clashes',exact:true})).toHaveCount(0);
  await expect(page.getByText(/No overlap found among/)).toHaveCount(0);expect(reads).toBe(2);expect(writes).toBe(0);
});

test('agenda displays cancellation separately from retained registration and explicit unknown duration', async ({page})=>{
  await page.route('**/api/v1/session',route=>route.fulfill({json:{id:1,name:'Synthetic',role:'STUDENT'}}));
  await page.route('**/api/v1/cujammu/features',route=>route.fulfill({json:{MODULE_EVENTS:true,MODULE_CALENDAR:true}}));
  await page.route('**/api/v1/cujammu/events/calendar/mine*',route=>route.fulfill({json:{page:0,hasMore:false,items:[{registrationId:1,eventId:7,title:'Cancelled occurrence',eventDate:'2035-04-02T10:00:00',venue:'Hall',status:'REGISTERED',eventEndsAt:null,eventStatus:'CANCELLED',lifecycleVersion:5}]}}));
  await page.goto('/app/');await page.getByRole('button',{name:'Skip introduction'}).click();await goToNavigation(page,'Calendar');
  await expect(page.getByText('Event cancelled — this occurrence will not take place.',{exact:true})).toBeVisible();
  await expect(page.getByText('Registration confirmed',{exact:true})).toBeVisible();
  await expect(page.getByText('End time not supplied; duration is unknown.',{exact:true})).toBeVisible();
  await expect(page.getByText('Event source · revision 5. Refresh for schedule changes.',{exact:true})).toBeVisible();
});
