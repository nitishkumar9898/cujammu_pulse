import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeFeedPage, decodeLikeResult, decodeFeedComment } from '../src/feed/contracts.ts';
import { actions, modules } from '../src/registry.ts';

test('feed decoder exposes bounded public projection without media URL', () => {
  const page = decodeFeedPage({ number: 0, totalPages: 1, last: true, content: [{ id: 1, content: 'Synthetic update', mediaType: 'NONE', category: 'GENERAL', winnerAnnouncement: false, relatedEventId: null, relatedEventTitle: null, winnerName: null, winningPrize: null, likesCount: 0, commentsCount: 0, pinned: false, createdAt: null, mediaUrl: 'private' }] });
  assert.equal(page.content[0]?.content, 'Synthetic update');
  assert.equal('mediaUrl' in page.content[0]!, false);
  assert.throws(() => decodeFeedPage({ number: 0, totalPages: 1, last: true, content: Array(31).fill({}) }));
});

test('feed module and read action are shared and feature-gated', () => {
  assert.equal(modules.find(item => item.id === 'feed')?.implemented, true);
  const action = actions.find(item => item.actionId === 'feed.posts.read');
  assert.equal(action?.featureKey, 'MODULE_SOCIAL_PULSE');
  assert.deepEqual(action?.clients, ['web', 'android']);
});
test('feed interaction responses are bounded', () => {
  assert.deepEqual(decodeLikeResult({ liked: true, likesCount: 2 }), { liked: true, likesCount: 2 });
  assert.equal(decodeFeedComment({ id: 1, content: 'Thanks', createdAt: null }).content, 'Thanks');
  assert.throws(() => decodeLikeResult({ liked: true, likesCount: -1 }));
});
test('feed comment actions are shared and feature-gated', () => {
  for (const id of ['feed.post.comments.read', 'feed.post.comment']) {
    const action = actions.find(item => item.actionId === id);
    assert.equal(action?.featureKey, 'MODULE_SOCIAL_PULSE');
    assert.deepEqual(action?.clients, ['web', 'android']);
  }
});
