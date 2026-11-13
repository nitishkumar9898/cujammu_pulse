export type MediaStorageInventory = {
  trackedObjects: number; trackedBytes: number; activeObjects: number; expiredObjects: number;
  heldObjects: number; purgeFailedObjects: number; orphanObjects: number; orphanBytes: number; measuredAt: string;
};

function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid media inventory response');
  return value as Record<string, unknown>;
}
function count(value: unknown): number {
  if (!Number.isSafeInteger(value) || (value as number) < 0) throw new Error('Invalid media inventory response');
  return value as number;
}
export function decodeMediaStorageInventory(value: unknown): MediaStorageInventory {
  const v = object(value);
  const measuredAt = v.measuredAt;
  if (typeof measuredAt !== 'string' || !/^\d{4}-\d\d-\d\dT/.test(measuredAt) || !Number.isFinite(new Date(measuredAt).getTime())) throw new Error('Invalid media inventory response');
  return {
    trackedObjects: count(v.trackedObjects), trackedBytes: count(v.trackedBytes), activeObjects: count(v.activeObjects),
    expiredObjects: count(v.expiredObjects), heldObjects: count(v.heldObjects), purgeFailedObjects: count(v.purgeFailedObjects),
    orphanObjects: count(v.orphanObjects), orphanBytes: count(v.orphanBytes), measuredAt
  };
}
