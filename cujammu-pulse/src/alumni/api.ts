import { mutateJson, requestJson } from '../api.ts';
import { decodeDirectory, decodeJob, decodePage, decodeProfile, type JobUpdate, type ProfileUpdate } from './contracts.ts';
const base = '/api/v1/cujammu/alumni';
function pageQuery(page: number, department = '') {
  if (!Number.isInteger(page) || page < 0 || page > 10000 || department.length > 80) throw new Error('Invalid search');
  return `?page=${page}${department.trim() ? `&department=${encodeURIComponent(department.trim())}` : ''}`;
}
export const alumniApi = {
  directory: async (page: number, department: string, mentors: boolean, signal?: AbortSignal) => decodePage(await requestJson(`${base}${mentors ? '/mentors' : ''}${pageQuery(page, department)}`, signal), decodeDirectory),
  profile: async (signal?: AbortSignal) => { const value = await requestJson(`${base}/profile`, signal); return value == null ? null : decodeProfile(value); },
  saveProfile: async (command: ProfileUpdate, signal?: AbortSignal) => decodeProfile(await mutateJson(`${base}/profile`, 'POST', command, signal)),
  review: async (page: number, signal?: AbortSignal) => decodePage(await requestJson(`${base}/review${pageQuery(page)}`, signal), decodeProfile),
  privateProfile: async (id: number, signal?: AbortSignal) => { if (!Number.isSafeInteger(id) || id < 1) throw new Error('Invalid profile ID'); return decodeProfile(await requestJson(`${base}/profiles/${id}`, signal)); },
  verify: async (id: number, expectedVersion: number, verified: boolean, signal?: AbortSignal) => decodeProfile(await mutateJson(`${base}/profiles/${id}/verification`, 'PUT', { expectedVersion, verified }, signal)),
  jobs: async (page: number, department: string, mine: boolean, signal?: AbortSignal) => decodePage(await requestJson(`${base}/jobs${mine ? '/mine' : ''}${pageQuery(page, mine ? '' : department)}`, signal), decodeJob),
  postJob: async (command: JobUpdate, signal?: AbortSignal) => decodeJob(await mutateJson(`${base}/jobs`, 'POST', command, signal)),
  jobState: async (id: number, expectedVersion: number, active: boolean, signal?: AbortSignal) => decodeJob(await mutateJson(`${base}/jobs/${id}/state`, 'PUT', { expectedVersion, active }, signal)),
};
