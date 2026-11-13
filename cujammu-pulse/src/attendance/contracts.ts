export type AttendanceCredential = { action: 'ENTRY' | 'ATTENDANCE_CHECKPOINT'; credential: string; expiresAt: string; credentialVersion: number };
export type PendingAttendanceIntent = { intentKey: string; eventTitle: string; action: 'ENTRY' | 'ATTENDANCE_CHECKPOINT'; expiresAt: string; status: string };
export type AttendanceReceipt = { evidenceId: number; action: string; status: string; occurredAt: string };
function object(v: unknown): Record<string, unknown> { if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error('Invalid attendance response'); return v as Record<string, unknown>; }
function bounded(v: unknown, max: number): v is string { return typeof v === 'string' && v.length > 0 && v.length <= max; }
function validUtcTimestamp(value: unknown): value is string {
  if (typeof value !== 'string' || value.length > 80 || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(value)) return false;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) && new Date(parsed).toISOString().startsWith(value.slice(0, 19));
}
function credential(v: unknown): AttendanceCredential { const x=object(v); const version=x.credentialVersion; const expiresAt=x.expiresAt; if((x.action!=='ENTRY'&&x.action!=='ATTENDANCE_CHECKPOINT')||!bounded(x.credential,512)||!validUtcTimestamp(expiresAt)||typeof version!=='number'||!Number.isSafeInteger(version)||version<1) throw new Error('Invalid attendance credential'); return { action:x.action, credential:x.credential, expiresAt, credentialVersion:version }; }
function pending(v: unknown): PendingAttendanceIntent { const x=object(v); if(!bounded(x.intentKey,80)||!bounded(x.eventTitle,200)||(x.action!=='ENTRY'&&x.action!=='ATTENDANCE_CHECKPOINT')||!bounded(x.expiresAt,80)||!bounded(x.status,40)) throw new Error('Invalid pending attendance intent'); return { intentKey:x.intentKey,eventTitle:x.eventTitle,action:x.action,expiresAt:x.expiresAt,status:x.status}; }
function receipt(v: unknown): AttendanceReceipt { const x=object(v); const id=x.evidenceId; if(typeof id!=='number'||!Number.isSafeInteger(id)||id<1||!bounded(x.action,40)||!bounded(x.status,40)||!bounded(x.occurredAt,80)) throw new Error('Invalid attendance receipt'); return { evidenceId:id,action:x.action,status:x.status,occurredAt:x.occurredAt}; }
export const decodeAttendanceCredential=(v:unknown)=>credential(v);
export const decodePendingAttendance=(v:unknown):PendingAttendanceIntent[]=>{const x=object(v).items; if(!Array.isArray(x)||x.length>100) throw new Error('Invalid pending attendance response'); return x.map(pending);};
export const decodeAttendanceReceipt=(v:unknown)=>receipt(v);
