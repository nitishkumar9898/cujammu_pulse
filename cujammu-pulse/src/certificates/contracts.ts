export type IssuedCertificate = { registrationId: number; eventId: number; eventTitle: string; certificateNumber: string; certificateType: 'POINT_PARTICIPATION'; issuedAt: string; downloadUrl: string };
export type CertificatePage = { items: IssuedCertificate[]; page: number; hasNext: boolean };
export type CertificateFinalization = { eventId: number; revision: number; status: 'FINALIZED' | 'FINALIZED_WITH_UNRESOLVED'; certificateType: 'POINT_PARTICIPATION'; policyVersion: number; issuedCount: number; ineligibleCount: number; unresolvedCount: number; finalizedAt: string };
function object(value: unknown): Record<string, unknown> { if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid certificate response'); return value as Record<string, unknown>; }
function number(value: unknown, minimum = 0): number { if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < minimum) throw new Error('Invalid certificate number'); return value; }
function text(value: unknown, max: number): string { if (typeof value !== 'string' || !value || value.length > max) throw new Error('Invalid certificate text'); return value; }
function time(value: unknown): string { const result = text(value, 80); if (!Number.isFinite(Date.parse(result))) throw new Error('Invalid certificate timestamp'); return result; }
export function decodeCertificates(value: unknown): CertificatePage {
  const v = object(value); if (!Array.isArray(v.items) || v.items.length > 20 || typeof v.hasNext !== 'boolean') throw new Error('Invalid certificate page');
  const items: IssuedCertificate[] = v.items.map(raw => { const item = object(raw); const registrationId = number(item.registrationId, 1); if (item.certificateType !== 'POINT_PARTICIPATION' || item.downloadUrl !== `/api/v1/cujammu/certificates/${registrationId}/download`) throw new Error('Invalid certificate download or evidence type'); return { registrationId, eventId: number(item.eventId, 1), eventTitle: text(item.eventTitle, 200), certificateNumber: text(item.certificateNumber, 100), certificateType: 'POINT_PARTICIPATION', issuedAt: time(item.issuedAt), downloadUrl: item.downloadUrl as string }; });
  if (new Set(items.map(item => item.registrationId)).size !== items.length) throw new Error('Duplicate issued certificate');
  const page = number(v.page); if (page > 10000) throw new Error('Invalid certificate page'); return { items, page, hasNext: v.hasNext };
}
export function decodeCertificateFinalization(value: unknown): CertificateFinalization {
  const v = object(value); if (v.certificateType !== 'POINT_PARTICIPATION' || !['FINALIZED', 'FINALIZED_WITH_UNRESOLVED'].includes(String(v.status))) throw new Error('Invalid certificate finalization');
  return { eventId: number(v.eventId, 1), revision: number(v.revision, 1), status: v.status as CertificateFinalization['status'], certificateType: 'POINT_PARTICIPATION', policyVersion: number(v.policyVersion, 1), issuedCount: number(v.issuedCount), ineligibleCount: number(v.ineligibleCount), unresolvedCount: number(v.unresolvedCount), finalizedAt: time(v.finalizedAt) };
}
