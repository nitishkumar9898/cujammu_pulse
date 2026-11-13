export type StudentCandidate = { studentId: number; name: string; email: string; verifiedIdentifier: string | null; verifiedDepartmentId: number | null; bindingState: 'UNVERIFIED' | 'VERIFIED' | 'REVIEW_REQUIRED'; credentialVersion: number };
export type StudentVerification = { expectedCredentialVersion: number; identifier: string; departmentId: number; officialSourceReference: string; reviewReason: string };
function record(value: unknown): Record<string, unknown> { if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid verification response'); return value as Record<string, unknown>; }
function text(value: unknown, max: number): string { if (typeof value !== 'string' || value.length > max) throw new Error('Invalid verification response'); return value; }
function integer(value: unknown, min = 1): number { if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < min) throw new Error('Invalid verification response'); return value; }
export function decodeStudentCandidate(value: unknown): StudentCandidate {
  const v = record(value);
  if (!['UNVERIFIED', 'VERIFIED', 'REVIEW_REQUIRED'].includes(String(v.bindingState))) throw new Error('Invalid verification state');
  const result = { studentId: integer(v.studentId), name: text(v.name, 500), email: text(v.email, 254), verifiedIdentifier: v.verifiedIdentifier === null ? null : text(v.verifiedIdentifier, 64), verifiedDepartmentId: v.verifiedDepartmentId === null ? null : integer(v.verifiedDepartmentId), bindingState: v.bindingState as StudentCandidate['bindingState'], credentialVersion: integer(v.credentialVersion, 0) };
  if (result.bindingState === 'UNVERIFIED' && (result.verifiedIdentifier !== null || result.verifiedDepartmentId !== null)) throw new Error('Inconsistent verification state');
  if (result.bindingState === 'VERIFIED' && (!result.verifiedIdentifier?.trim() || result.verifiedDepartmentId === null)) throw new Error('Inconsistent verification state');
  return result;
}
export function decodeStudentVerification(value: unknown) {
  const v = record(value); const verifiedAt = text(v.verifiedAt, 80);
  if (!verifiedAt || !Number.isFinite(Date.parse(verifiedAt))) throw new Error('Invalid verification time');
  return { studentId: integer(v.studentId), verifiedIdentifier: text(v.verifiedIdentifier, 64), verifiedAt, credentialVersion: integer(v.credentialVersion, 0) };
}
