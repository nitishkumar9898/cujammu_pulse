export type DirectoryEntry = {
  id: number; displayName: string; department: string; graduationYear: number; degree: string;
  currentCompany: string | null; currentDesignation: string | null; location: string | null;
  linkedInUrl: string | null; bio: string | null; willingToMentor: boolean; willingToRefer: boolean;
};
export type Profile = DirectoryEntry & { version: number; verifiedAlumni: boolean; directoryVisible: boolean; showCareer: boolean; showLocation: boolean; showBio: boolean; showLinkedIn: boolean };
export type Job = { id: number; version: number; companyName: string; jobTitle: string; jobType: string; location: string | null; description: string; applicationLinkOrEmail: string | null; targetDepartment: string; active: boolean; postedAt: string | null };
export type Page<T> = { items: T[]; page: number; hasNext: boolean };
export type ProfileUpdate = { expectedVersion: number; degree: string; graduationYear: number; company: string; designation: string; location: string; linkedin: string; bio: string; mentor: boolean; refer: boolean; directoryVisible: boolean; showCareer: boolean; showLocation: boolean; showBio: boolean; showLinkedIn: boolean };
export type JobUpdate = { requestKey: string; companyName: string; jobTitle: string; jobType: string; location: string; description: string; linkOrEmail: string; targetDepartment: string };
function obj(value: unknown): Record<string, unknown> { if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid alumni response'); return value as Record<string, unknown>; }
function str(value: unknown, limit = 4000): string { if (typeof value !== 'string' || value.length > limit) throw new Error('Invalid alumni field'); return value; }
function optional(value: unknown): string | null { return value == null ? null : str(value); }
function integer(value: unknown, min = 0): number { if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < min) throw new Error('Invalid alumni number'); return value; }
function bool(value: unknown): boolean { if (typeof value !== 'boolean') throw new Error('Invalid alumni status'); return value; }
export function safeLink(value: unknown, linkedin = false): string | null {
  if (value == null || value === '') return null;
  const text = str(value, 250);
  if (!linkedin && /^mailto:[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$/.test(text)) return text;
  try {
    const url = new URL(text);
    if (url.protocol !== 'https:' || url.username || url.password || (linkedin && !['linkedin.com', 'www.linkedin.com'].includes(url.hostname))) return null;
    return url.href;
  } catch { return null; }
}
export function decodeDirectory(value: unknown): DirectoryEntry {
  const v = obj(value);
  return { id: integer(v.id, 1), displayName: str(v.displayName, 500), department: str(v.department, 80), graduationYear: integer(v.graduationYear), degree: str(v.degree, 80), currentCompany: optional(v.currentCompany), currentDesignation: optional(v.currentDesignation), location: optional(v.location), linkedInUrl: safeLink(v.linkedInUrl, true), bio: optional(v.bio), willingToMentor: bool(v.willingToMentor), willingToRefer: bool(v.willingToRefer) };
}
export function decodeProfile(value: unknown): Profile {
  const v = obj(value);
  return { ...decodeDirectory(v), version: integer(v.version), verifiedAlumni: bool(v.verifiedAlumni), directoryVisible: bool(v.directoryVisible), showCareer: bool(v.showCareer), showLocation: bool(v.showLocation), showBio: bool(v.showBio), showLinkedIn: bool(v.showLinkedIn) };
}
export function decodeJob(value: unknown): Job {
  const v = obj(value);
  return { id: integer(v.id, 1), version: integer(v.version), companyName: str(v.companyName, 120), jobTitle: str(v.jobTitle, 120), jobType: str(v.jobType, 40), location: optional(v.location), description: str(v.description), applicationLinkOrEmail: safeLink(v.applicationLinkOrEmail), targetDepartment: str(v.targetDepartment, 80), active: bool(v.active), postedAt: optional(v.postedAt) };
}
export function decodePage<T>(value: unknown, decode: (value: unknown) => T): Page<T> {
  const v = obj(value);
  if (!Array.isArray(v.items) || v.items.length > 24) throw new Error('Invalid alumni page');
  return { items: v.items.map(decode), page: integer(v.page), hasNext: bool(v.hasNext) };
}
export function profileCommand(profile: Profile | null): ProfileUpdate {
  return { expectedVersion: profile?.version ?? 0, degree: profile?.degree ?? '', graduationYear: profile?.graduationYear ?? 0, company: profile?.currentCompany ?? '', designation: profile?.currentDesignation ?? '', location: profile?.location ?? '', linkedin: profile?.linkedInUrl ?? '', bio: profile?.bio ?? '', mentor: profile?.willingToMentor ?? false, refer: profile?.willingToRefer ?? false, directoryVisible: profile?.directoryVisible ?? false, showCareer: profile?.showCareer ?? false, showLocation: profile?.showLocation ?? false, showBio: profile?.showBio ?? false, showLinkedIn: profile?.showLinkedIn ?? false };
}
