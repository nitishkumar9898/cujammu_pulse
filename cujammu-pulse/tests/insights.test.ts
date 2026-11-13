import test from 'node:test'; import assert from 'node:assert/strict'; import { decodeFeedback, decodeFeedbackSummary } from '../src/insights/contracts.ts'; import { actions, modules } from '../src/registry.ts';
test('feedback summary is bounded and rating-safe',()=>{const v=decodeFeedbackSummary({eventId:2,averageRating:4.5,totalResponses:1,recent:[{id:1,eventId:2,rating:5,comment:'Good',createdAt:null,private:'x'}]});assert.equal(v.recent[0]?.rating,5);assert.throws(()=>decodeFeedbackSummary({eventId:2,averageRating:6,totalResponses:0,recent:[]}));});
test('insights actions are shared and feature-gated',()=>{for(const id of ['insights.feedback.read','insights.feedback.submit']){const a=actions.find(x=>x.actionId===id);assert.deepEqual(a?.clients,['web','android']);assert.equal(a?.featureKey,'MODULE_INSIGHTS');}});
test('insights module has an interactive universal screen',()=>assert.equal(modules.find(x=>x.id==='insights')?.implemented,true));

test('feedback decoder denies malformed IDs, fractional ratings and unbounded comments', () => {
  const receipt = { id: 1, eventId: 2, rating: 5, comment: null, createdAt: null };
  assert.equal(decodeFeedback(receipt).comment, null);
  for (const invalid of [{ id: 0 }, { eventId: -1 }, { rating: 3.5 }, { rating: NaN }, { comment: 'x'.repeat(2001) }]) assert.throws(() => decodeFeedback({ ...receipt, ...invalid }));
  assert.deepEqual(Object.keys(decodeFeedback({ ...receipt, userId: 99 })), ['id', 'eventId', 'rating', 'comment', 'createdAt']);
});

test('summary rejects cross-event responses, duplicate records and invalid counts', () => {
  const row = { id: 1, eventId: 2, rating: 5, comment: null, createdAt: null };
  const summary = { eventId: 2, averageRating: 5, totalResponses: 1, recent: [row] };
  for (const invalid of [{ averageRating: NaN }, { totalResponses: -1 }, { totalResponses: 0 }, { recent: [{ ...row, eventId: 3 }] }, { totalResponses: 2, recent: [row, row] }]) assert.throws(() => decodeFeedbackSummary({ ...summary, ...invalid }));
});
