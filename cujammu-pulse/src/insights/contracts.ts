export type Feedback = { id: number; eventId: number; rating: number; comment: string | null; createdAt: string | null };
export type FeedbackSummary = { eventId: number; averageRating: number; totalResponses: number; recent: Feedback[] };

function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid feedback response');
  return value as Record<string, unknown>;
}
function integer(value: unknown, minimum: number, maximum = Number.MAX_SAFE_INTEGER): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < minimum || value > maximum) throw new Error('Invalid feedback number');
  return value;
}
function nullableText(value: unknown, maximum: number): string | null {
  if (value === null) return null;
  if (typeof value !== 'string' || value.length > maximum) throw new Error('Invalid feedback text');
  return value;
}
export function decodeFeedback(value: unknown): Feedback {
  const row = object(value);
  return { id: integer(row.id, 1), eventId: integer(row.eventId, 1), rating: integer(row.rating, 1, 5), comment: nullableText(row.comment, 2000), createdAt: nullableText(row.createdAt, 80) };
}
export function decodeFeedbackSummary(value: unknown): FeedbackSummary {
  const row = object(value);
  const eventId = integer(row.eventId, 1);
  const totalResponses = integer(row.totalResponses, 0);
  if (typeof row.averageRating !== 'number' || !Number.isFinite(row.averageRating) || row.averageRating < 0 || row.averageRating > 5 || !Array.isArray(row.recent) || row.recent.length > 50 || row.recent.length > totalResponses) throw new Error('Invalid feedback summary');
  const recent = row.recent.map(decodeFeedback);
  if (recent.some(item => item.eventId !== eventId) || new Set(recent.map(item => item.id)).size !== recent.length) throw new Error('Invalid feedback summary scope');
  return { eventId, averageRating: row.averageRating, totalResponses, recent };
}
