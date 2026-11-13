import test from 'node:test'; import assert from 'node:assert/strict'; import { decodeCalendar, decodeCalendarPage, decodeCalendarClashes } from '../src/calendar/contracts.ts'; import { actions } from '../src/registry.ts';
import { campusDate } from '../src/calendar/date.ts';
test('calendar projection is bounded and owner-safe',()=>{const value=decodeCalendar([{registrationId:1,eventId:2,title:'Hackathon',eventDate:'2026-10-04T10:00:00Z',venue:'Hall',status:'REGISTERED',email:'secret'}]);assert.equal('email' in value[0]!,false);assert.throws(()=>decodeCalendar([{registrationId:1,eventId:2,title:'x',eventDate:'bad',venue:'H',status:'REGISTERED'}]));});
test('calendar pages enforce the 50-item bound, page identity and continuation contract',()=>{const entry={registrationId:1,eventId:2,title:'Synthetic',eventDate:'2030-01-01T10:00:00',venue:'Hall',status:'REGISTERED'};assert.deepEqual(decodeCalendarPage({items:[entry],page:2,hasMore:true}),{items:[{...entry,eventEndsAt:null,eventStatus:null,lifecycleVersion:null}],page:2,hasMore:true});assert.throws(()=>decodeCalendarPage({items:Array(51).fill(entry),page:0,hasMore:true}));assert.throws(()=>decodeCalendarPage({items:[],page:-1,hasMore:false}));assert.throws(()=>decodeCalendarPage({items:[],page:0,hasMore:'yes'}));});
test('calendar action is shared and feature-gated',()=>{const a=actions.find(x=>x.actionId==='calendar.mine.read');assert.deepEqual(a?.clients,['web','android']);assert.equal(a?.featureKey,'MODULE_CALENDAR');});
test('feedback selector uses its own enabled module instead of requiring calendar access',()=>{const a=actions.find(x=>x.actionId==='insights.events.mine.read');assert.deepEqual(a?.clients,['web','android']);assert.equal(a?.featureKey,'MODULE_INSIGHTS');});

test('campus local dates have fixed IST semantics and preserve explicit offsets', () => {
  assert.equal(campusDate('2035-04-02T10:00:00').toISOString(), '2035-04-02T04:30:00.000Z');
  assert.equal(campusDate('2035-04-02T10:00:00Z').toISOString(), '2035-04-02T10:00:00.000Z');
});
test('clash decoder rejects false overlap assertions and strips private extra fields', () => {
  const entry = { registrationId:1,eventId:2,title:'Own event',eventDate:'2035-04-02T11:00:00',eventEndsAt:'2035-04-02T13:00:00',venue:'Hall',status:'REGISTERED',eventStatus:'APPROVED',lifecycleVersion:2,email:'private@example.invalid' };
  const value={eventId:3,startsAt:'2035-04-02T10:00:00',endsAt:'2035-04-02T12:00:00',lifecycleVersion:1,checkedAt:'2035-04-01T10:00:00Z',hasMore:false,items:[{entry,assessment:'OVERLAP'}]};
  assert.equal('email' in decodeCalendarClashes(value).items[0]!.entry, false);
  assert.throws(()=>decodeCalendarClashes({...value,endsAt:null}));
  assert.throws(()=>decodeCalendarClashes({...value,endsAt:'2035-04-02T11:00:00'}));
  assert.throws(()=>decodeCalendarClashes({...value,items:Array(51).fill(value.items[0])}));
  assert.throws(()=>decodeCalendarClashes({...value,items:[{entry:{...entry,eventDate:'2035-04-02'},assessment:'OVERLAP'}]}));
  assert.equal(decodeCalendarClashes({...value,endsAt:null,items:[{entry,assessment:'UNKNOWN_DURATION'}]}).items[0]!.assessment,'UNKNOWN_DURATION');
});
test('calendar lifecycle distinguishes cancelled event from still-saved registration', () => {
  const value=decodeCalendar([{registrationId:1,eventId:2,title:'Cancelled',eventDate:'2035-04-02T10:00:00',venue:'Hall',status:'REGISTERED',eventStatus:'CANCELLED',lifecycleVersion:3}]);
  assert.equal(value[0]!.eventStatus,'CANCELLED'); assert.equal(value[0]!.status,'REGISTERED'); assert.equal(value[0]!.lifecycleVersion,3);
  const action=actions.find(item=>item.actionId==='calendar.clashes.check');assert.deepEqual(action?.clients,['web','android']);assert.equal(action?.featureKey,'ACTION_CALENDAR_CLASH_CHECK');
});
