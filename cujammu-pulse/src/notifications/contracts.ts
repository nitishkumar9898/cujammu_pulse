function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid notification response');
  return value as Record<string, unknown>;
}

export type NotificationItem = { id: number; message: string; read: boolean; acknowledged: boolean; createdAt: string | null };
export type NotificationPage = { nextBeforeId: number | null; items: NotificationItem[] };

export function decodeNotifications(value: unknown): NotificationPage {
  const root = record(value);
  if ((root.nextBeforeId != null && (typeof root.nextBeforeId !== 'number' || !Number.isSafeInteger(root.nextBeforeId) || root.nextBeforeId < 1))
      || !Array.isArray(root.items) || root.items.length > 20) throw new Error('Invalid notification list');
  const items = root.items.map((value: unknown) => {
    const item = record(value);
    if (typeof item.id !== 'number' || !Number.isSafeInteger(item.id) || item.id < 1
        || typeof item.message !== 'string' || item.message.length > 500
        || typeof item.read !== 'boolean'
        || typeof item.acknowledged !== 'boolean'
        || (item.createdAt != null && typeof item.createdAt !== 'string')) {
      throw new Error('Invalid notification item');
    }
    return { id: item.id, message: item.message, read: item.read, acknowledged: item.acknowledged, createdAt: item.createdAt as string | null };
  });
  return { nextBeforeId: root.nextBeforeId as number | null, items };
}
