export type ReadinessOwner = { userId: number; name: string; role: string };
export type ReadinessTask = {
  id: number; eventId: number; title: string; description: string; category: string; status: string;
  completionNote: string | null;
  ownerUserId: number; ownerName: string; ownerRole: string; createdByUserId: number;
  dueAt: string; revision: number; createdAt: string; updatedAt: string;
};

const object = (value: unknown): Record<string, unknown> => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid readiness task response');
  return value as Record<string, unknown>;
};
const text = (value: unknown, max: number) => {
  if (typeof value !== 'string' || value.length > max) throw new Error('Invalid readiness task response');
  return value;
};
const integer = (value: unknown) => {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) throw new Error('Invalid readiness task response');
  return value;
};
const timestamp = (value: unknown) => {
  const valueText = text(value, 40);
  if (!Number.isFinite(Date.parse(valueText))) throw new Error('Invalid readiness task timestamp');
  return valueText;
};

export function decodeReadinessOwners(value: unknown): ReadinessOwner[] {
  if (!Array.isArray(value) || value.length > 51) throw new Error('Invalid readiness task owners');
  return value.map(raw => {
    const owner = object(raw);
    return { userId: integer(owner.userId), name: text(owner.name, 120), role: text(owner.role, 24) };
  });
}

export function decodeReadinessTask(value: unknown): ReadinessTask {
  const task = object(value);
  return {
    id: integer(task.id), eventId: integer(task.eventId), title: text(task.title, 120),
    description: text(task.description, 500), category: text(task.category, 24), status: text(task.status, 20),
    completionNote: task.completionNote == null ? null : text(task.completionNote, 300),
    ownerUserId: integer(task.ownerUserId), ownerName: text(task.ownerName, 120), ownerRole: text(task.ownerRole, 24),
    createdByUserId: integer(task.createdByUserId), dueAt: timestamp(task.dueAt), revision: integer(task.revision),
    createdAt: timestamp(task.createdAt), updatedAt: timestamp(task.updatedAt),
  };
}

export function decodeReadinessTaskList(value: unknown): { eventId: number; items: ReadinessTask[]; truncated: boolean } {
  const list = object(value);
  if (!Array.isArray(list.items) || list.items.length > 100 || typeof list.truncated !== 'boolean') throw new Error('Invalid readiness task list');
  const items = list.items.map(decodeReadinessTask);
  const eventId = integer(list.eventId);
  if (items.some(item => item.eventId !== eventId)) throw new Error('Readiness task scope mismatch');
  return { eventId, items, truncated: list.truncated };
}
