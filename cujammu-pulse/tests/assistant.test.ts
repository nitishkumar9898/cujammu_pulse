import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeAssistantReply } from '../src/assistant/contracts.ts';
import { actions, modules } from '../src/registry.ts';

test('assistant decoder accepts bounded safe reply and rejects oversized output', () => {
  assert.deepEqual(decodeAssistantReply({ reply: 'Use the Events screen.', safety: 'SAFE_GROUNDED_ASSISTANT' }).reply, 'Use the Events screen.');
  assert.throws(() => decodeAssistantReply({ reply: 'x'.repeat(4001) }));
});

test('assistant action is shared and feature-gated', () => {
  const action = actions.find(item => item.actionId === 'assistant.chat.send');
  assert.deepEqual(action?.clients, ['web', 'android']);
  assert.equal(action?.featureKey, 'MODULE_AI_CHATBOT');
  assert.equal(modules.find(item => item.id === 'assistant')?.implemented, true);
});
