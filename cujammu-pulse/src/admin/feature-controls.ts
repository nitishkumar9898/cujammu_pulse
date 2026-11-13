export type FeatureControl = {
  key: string; name: string; description: string; category: string;
  requestedEnabled: boolean; effectiveEnabled: boolean; locked: boolean;
  revision: number; updatedAt: string | null; updatedBy: string | null;
};
export type FeatureControlAudit = { id: number; role: string; featureKey: string; enabled: boolean; reason: string; timestamp: string | null };

function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid feature control response');
  return value as Record<string, unknown>;
}
function text(value: unknown, max = 300): string {
  if (typeof value !== 'string' || !value.trim() || value.length > max) throw new Error('Invalid feature control response');
  return value;
}
function optionalText(value: unknown, max = 300): string | null {
  return value == null ? null : text(value, max);
}
function timestamp(value: unknown): string | null {
  if (value == null) return null;
  const date = text(value, 80);
  if (!/^\d{4}-\d\d-\d\dT/.test(date) || !Number.isFinite(new Date(date).getTime())) throw new Error('Invalid feature control response');
  return date;
}
function item(value: unknown): FeatureControl {
  const v = object(value);
  if (!/^[A-Z][A-Z0-9_]{1,100}$/.test(text(v.key, 101)) || typeof v.requestedEnabled !== 'boolean'
    || typeof v.effectiveEnabled !== 'boolean' || typeof v.locked !== 'boolean'
    || !Number.isSafeInteger(v.revision) || (v.revision as number) < 0) throw new Error('Invalid feature control response');
  return { key: v.key as string, name: text(v.name), description: text(v.description, 500), category: text(v.category, 80),
    requestedEnabled: v.requestedEnabled, effectiveEnabled: v.effectiveEnabled, locked: v.locked,
    revision: v.revision as number, updatedAt: timestamp(v.updatedAt), updatedBy: optionalText(v.updatedBy, 150) };
}

export function decodeFeatureControls(value: unknown): FeatureControl[] {
  const root = object(value);
  if (!Array.isArray(root.items) || root.items.length > 200) throw new Error('Invalid feature control response');
  return root.items.map(item);
}
export function decodeFeatureControl(value: unknown): FeatureControl { return item(value); }

export function decodeFeatureControlAudit(value: unknown): { items: FeatureControlAudit[]; page: number; hasNext: boolean } {
  const root = object(value);
  if (!Array.isArray(root.items) || root.items.length > 100 || !Number.isSafeInteger(root.page) || (root.page as number) < 0 || typeof root.hasNext !== 'boolean') throw new Error('Invalid feature audit response');
  const items = root.items.map(raw => {
    const v = object(raw);
    if (!Number.isSafeInteger(v.id) || (v.id as number) < 1 || !/^[A-Z][A-Z0-9_]{1,100}$/.test(text(v.featureKey, 101)) || typeof v.enabled !== 'boolean') throw new Error('Invalid feature audit response');
    return { id: v.id as number, role: text(v.role, 40), featureKey: v.featureKey as string, enabled: v.enabled, reason: typeof v.reason === 'string' ? v.reason.slice(0, 300) : '', timestamp: timestamp(v.timestamp) };
  });
  return { items, page: root.page as number, hasNext: root.hasNext };
}
