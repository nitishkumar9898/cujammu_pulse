import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { decodeDirectory, decodeJob, decodePage, decodeProfile, profileCommand, safeLink } from '../src/alumni/contracts.ts';
import { alumniApi } from '../src/alumni/api.ts';

const entry = { id: 1, displayName: 'Synthetic Alumni', department: 'TEST', graduationYear: 2020, degree: 'MSc', currentCompany: null, currentDesignation: null, location: null, linkedInUrl: null, bio: null, willingToMentor: true, willingToRefer: false };
const profile = { ...entry, version: 2, verifiedAlumni: true, directoryVisible: false, showCareer: false, showLocation: false, showBio: false, showLinkedIn: false };
const job = { id: 1, version: 0, companyName: 'Synthetic Ltd', jobTitle: 'Engineer', jobType: 'FULL_TIME', location: null, description: 'Synthetic description', applicationLinkOrEmail: 'https://example.invalid/apply', targetDepartment: 'ALL', active: true, postedAt: '2026-10-02T12:00:00' };
test('alumni directory privacy decoder preserves nulls and strips contact/identity entity', () => {
  assert.deepEqual(decodeDirectory({ ...entry, user: { email: 'test@example.invalid', password: 'synthetic' }, enrollment: 'TEST01', phone: 'private' }), entry);
});
test('profile command cannot self-verify, change identity or silently opt in', () => {
  const command = profileCommand(decodeProfile({ ...profile, role: 'ADMIN' }));
  assert.equal(command.expectedVersion, 2); assert.equal(command.directoryVisible, false);
  assert.ok(!('verifiedAlumni' in command)); assert.ok(!('role' in command)); assert.ok(!('displayName' in command));
  assert.equal(profileCommand(null).directoryVisible, false);
});
test('external alumni links reject script, credential and unapproved LinkedIn hosts', () => {
  for (const value of ['javascript:alert(1)', 'http://example.invalid', 'https://user:pass@example.invalid', 'mailto:a@example.invalid?body=private']) assert.equal(safeLink(value), null);
  assert.equal(safeLink('https://linkedin.com.attacker.invalid/a', true), null);
  assert.equal(safeLink('https://www.linkedin.com/in/test', true), 'https://www.linkedin.com/in/test');
});
test('alumni pages validate bounded shape and do not treat strings as verified', () => {
  assert.throws(() => decodePage({ items: Array(25).fill(entry), page: 0, hasNext: true }, decodeDirectory));
  assert.throws(() => decodeProfile({ ...profile, verifiedAlumni: 'true' }));
});
test('client decoder fields match actual backend Java response records', () => {
  const source = readFileSync(new URL('../../college-events/src/main/java/com/college/events/dto/AlumniDtos.java', import.meta.url), 'utf8');
  for (const [name, decoded] of [['DirectoryEntry', decodeDirectory(entry)], ['Profile', decodeProfile(profile)], ['Job', decodeJob(job)], ['PageResult', decodePage({ items: [], page: 0, hasNext: false }, decodeJob)]] as const) {
    const record = source.match(new RegExp(`record ${name}(?:<[^>]+>)?\\(([^)]*)\\)`));
    assert.ok(record, `Java ${name} exists`);
    const fields = record[1]!.split(',').map(field => field.trim().split(/\s+/).at(-1)).sort();
    assert.deepEqual(Object.keys(decoded).sort(), fields, `Java ${name} field contract`);
  }
});
test('missing profile 204 is normal and backend version/CSRF fields are sent exactly', async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async () => new Response(null, { status: 204 });
    assert.equal(await alumniApi.profile(), null);
    let sent: unknown;
    globalThis.fetch = async (input, init) => {
      if (input === '/api/v1/session/csrf') return Response.json({ token: 'synthetic', headerName: 'X-CSRF-TOKEN' });
      assert.equal(input, '/api/v1/cujammu/alumni/profiles/1/verification'); assert.equal(init?.method, 'PUT');
      assert.equal((init?.headers as Record<string, string>)['X-CSRF-TOKEN'], 'synthetic');
      sent = JSON.parse(init?.body as string); return Response.json(profile);
    };
    await alumniApi.verify(1, 2, true);
    assert.deepEqual(sent, { expectedVersion: 2, verified: true });
  } finally { globalThis.fetch = original; }
});
