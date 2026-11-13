export type AssistantReply = { reply: string; safety: string };

export function decodeAssistantReply(value: unknown): AssistantReply {
  if (!value || typeof value !== 'object') throw new Error('Invalid assistant response');
  const raw = value as Record<string, unknown>;
  if (typeof raw.reply !== 'string' || raw.reply.trim().length === 0 || raw.reply.length > 4000) throw new Error('Invalid assistant response');
  const safety = typeof raw.safety === 'string' && raw.safety.length <= 80 ? raw.safety : 'SAFE_GROUNDED_ASSISTANT';
  return { reply: raw.reply, safety };
}
