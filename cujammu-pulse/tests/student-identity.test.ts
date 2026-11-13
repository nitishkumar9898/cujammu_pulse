import assert from 'node:assert/strict';
import test from 'node:test';
import { decodeStudentCandidate, decodeStudentVerification } from '../src/admin/student-identity.ts';
import { api } from '../src/api.ts';
const candidate = { studentId: 7, name: 'Synthetic', email: 'student@example.invalid', verifiedIdentifier: null, verifiedDepartmentId: null, bindingState: 'UNVERIFIED', credentialVersion: 0 };
test('student verification projection excludes profile claims and secret source fields', () => {
  assert.deepEqual(decodeStudentCandidate({ ...candidate, enrollmentNo: 'SELF-ASSERTED', department: 'Unverified', password: 'private', enrollmentIdentitySourceReference: 'private' }), candidate);
  const verified = { ...candidate, bindingState: 'VERIFIED', verifiedIdentifier: '0000123', verifiedDepartmentId: 2 };
  assert.equal(decodeStudentCandidate(verified).verifiedIdentifier, '0000123');
});
test('student verification rejects inconsistent binding state, IDs and oversized values', () => {
  for (const value of [{ ...candidate, bindingState: 'VERIFIED' }, { ...candidate, verifiedDepartmentId: 2 }, { ...candidate, studentId: 0 }, { ...candidate, credentialVersion: -1 }, { ...candidate, email: 'x'.repeat(255) }, { ...candidate, bindingState: 'APPROVED' }]) assert.throws(() => decodeStudentCandidate(value));
  assert.equal(decodeStudentCandidate({ ...candidate, bindingState: 'REVIEW_REQUIRED', verifiedIdentifier: 'legacy' }).bindingState, 'REVIEW_REQUIRED');
});
test('verification receipt remains bounded and preserves identifier text', () => {
  assert.deepEqual(decodeStudentVerification({ studentId: 7, verifiedIdentifier: '0000123', verifiedAt: '2035-04-02T10:00:00', credentialVersion: 1, password: 'private' }), { studentId: 7, verifiedIdentifier: '0000123', verifiedAt: '2035-04-02T10:00:00', credentialVersion: 1 });
  assert.throws(() => decodeStudentVerification({ studentId: 7, verifiedIdentifier: 'valid', verifiedAt: 'not-a-date', credentialVersion: 1 }));
});
test('office transport rejects another account lookup or mismatched save receipt without retries', async () => {
  const original = globalThis.fetch; let calls = 0;
  globalThis.fetch = async (input, init) => {
    if (String(input) === '/api/v1/session/csrf') return Response.json({ token: 'synthetic', headerName: 'X-CSRF-TOKEN' });
    calls++; assert.equal((init?.headers as Record<string,string>)['X-CSRF-TOKEN'], 'synthetic');
    return Response.json(String(input).endsWith('/lookup') ? { ...candidate, email: 'other@example.invalid' } : { studentId: 8, verifiedIdentifier: '0000123', verifiedAt: '2035-04-02T10:00:00', credentialVersion: 5 });
  };
  try {
    await assert.rejects(api.lookupStudent(candidate.email), /does not match/);
    await assert.rejects(api.verifyStudent(7, { expectedCredentialVersion: 4, identifier: '0000123', departmentId: 3, officialSourceReference: 'Synthetic', reviewReason: 'Synthetic' }), /does not match/);
    assert.equal(calls, 2);
  } finally { globalThis.fetch = original; }
});
