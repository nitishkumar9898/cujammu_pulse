function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid event operations response');
  return value as Record<string, unknown>;
}
function positive(value: unknown, zero = false): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < (zero ? 0 : 1)) throw new Error('Invalid event operations number');
  return value;
}
function string(value: unknown): string {
  if (typeof value !== 'string' || value.length > 500) throw new Error('Invalid event operations text');
  return value;
}
function timestamp(value: unknown): string | null {
  if (value == null) return null;
  const result = string(value);
  if (!Number.isFinite(Date.parse(result))) throw new Error('Invalid event operations time');
  return result;
}
export type EventSummary = { id: number; title: string; status: string; eventDate: string; operationsRevision: number };
export type EventSummaryPage = { items: EventSummary[]; page: number; hasNext: boolean };
export type ScheduleChange = { id: number; changeNumber: number; changeType: 'CANCELLED' | 'RESCHEDULED'; previousDate: string | null; replacementDate: string | null; reason: string; actorRole: string; recordedAt: string };
export type Operations = { revision: number; attendanceMode: 'SINGLE_QR' | 'DUAL_QR' | 'NO_QR'; singleQrPolicy: string; certificateEvidencePolicy: 'UNREVIEWED' | 'POINT_ATTENDANCE'; certificatePolicyVersion: number; recruitmentOpen: boolean; maxVolunteerApplicants: number; scannerMasterEnabled: boolean; scannerScope: string; scannerEpoch: number; manualEpoch: number; scannerOpensAt: string | null; scannerClosesAt: string | null; manualWindowOpen: boolean; manualOpensAt: string | null; manualClosesAt: string | null };
export type Volunteer = { id: number; userId: number; revision: number; status: string; identifier: string; displayName: string; department: string | null; appliedAt: string; decidedAt: string | null };
export type OperatorCapabilities = { qrScannerEnabled: boolean; manualAttendanceEnabled: boolean; qrActions: string[]; manualActions: string[]; scannerEpoch: number };
export type ManualCheckInReceipt = { evidenceId: number; action: string; identifier: string; displayName: string; status: string; occurredAt: string };
export type ManualGrant = { userId: number; grantType: string; actions: string[]; enabled: boolean; epoch: number; expiresAt: string | null };
function items(value: unknown): unknown[] {
  const list = object(value).items;
  if (!Array.isArray(list) || list.length > 500) throw new Error('Invalid event operations list');
  return list;
}
export function decodeEvents(value: unknown): EventSummary[] {
  return items(value).map(entry => {
    const v = object(entry);
    if (typeof v.operationsRevision !== 'number' || !Number.isSafeInteger(v.operationsRevision) || v.operationsRevision < -1) throw new Error('Invalid event operations revision');
    return { id: positive(v.id), title: string(v.title), status: string(v.status), eventDate: timestamp(v.eventDate) ?? (() => { throw new Error('Missing event date'); })(), operationsRevision: v.operationsRevision };
  });
}
export function decodeEventSummaryPage(value: unknown): EventSummaryPage {
  const v = object(value);
  if (typeof v.page !== 'number' || !Number.isSafeInteger(v.page) || v.page < 0 || typeof v.hasNext !== 'boolean') throw new Error('Invalid event page');
  return { items: decodeEvents({ items: v.items }), page: v.page, hasNext: v.hasNext };
}
export function decodeScheduleChanges(value: unknown): ScheduleChange[] {
  if (!Array.isArray(value) || value.length > 100) throw new Error('Invalid lifecycle history');
  return value.map(entry => {
    const v = object(entry);
    const changeType = string(v.changeType);
    if (changeType !== 'CANCELLED' && changeType !== 'RESCHEDULED') throw new Error('Invalid lifecycle change type');
    return { id: positive(v.id), changeNumber: positive(v.changeNumber), changeType, previousDate: timestamp(v.previousDate), replacementDate: timestamp(v.replacementDate), reason: string(v.reason), actorRole: string(v.actorRole), recordedAt: timestamp(v.recordedAt) ?? (() => { throw new Error('Missing lifecycle change time'); })() };
  });
}
export function decodeOperations(value: unknown): Operations {
  const v = object(value);
  const mode = string(v.attendanceMode);
  if (mode !== 'SINGLE_QR' && mode !== 'DUAL_QR' && mode !== 'NO_QR') throw new Error('Invalid attendance mode');
  if (typeof v.recruitmentOpen !== 'boolean' || typeof v.scannerMasterEnabled !== 'boolean' || typeof v.manualWindowOpen !== 'boolean') throw new Error('Invalid event operations state');
  return { revision: positive(v.revision, true), attendanceMode: mode, singleQrPolicy: string(v.singleQrPolicy), certificateEvidencePolicy: certificatePolicy(v.certificateEvidencePolicy), certificatePolicyVersion: positive(v.certificatePolicyVersion, true), recruitmentOpen: v.recruitmentOpen,
    maxVolunteerApplicants: positive(v.maxVolunteerApplicants), scannerMasterEnabled: v.scannerMasterEnabled, scannerScope: string(v.scannerScope),
    scannerEpoch: positive(v.scannerEpoch, true), manualEpoch: positive(v.manualEpoch, true), scannerOpensAt: timestamp(v.scannerOpensAt), scannerClosesAt: timestamp(v.scannerClosesAt),
    manualWindowOpen: v.manualWindowOpen, manualOpensAt: timestamp(v.manualOpensAt), manualClosesAt: timestamp(v.manualClosesAt) };
}
export function decodeVolunteers(value: unknown): Volunteer[] {
  return items(value).map(entry => {
    const v = object(entry);
    return { id: positive(v.id), userId: positive(v.userId), revision: positive(v.revision, true), status: string(v.status), identifier: string(v.identifier),
      displayName: string(v.displayName), department: v.department == null ? null : string(v.department), appliedAt: timestamp(v.appliedAt) ?? (() => { throw new Error('Missing application date'); })(), decidedAt: timestamp(v.decidedAt) };
  });
}
export function decodeOperatorCapabilities(value: unknown): OperatorCapabilities {
  const v = object(value);
  if (typeof v.qrScannerEnabled !== 'boolean' || typeof v.manualAttendanceEnabled !== 'boolean' || !Array.isArray(v.qrActions) || !Array.isArray(v.manualActions)) throw new Error('Invalid operator capabilities');
  const actions = (items: unknown[]) => { if (items.length > 4 || items.some(item => typeof item !== 'string' || !['ENTRY', 'ATTENDANCE_CHECKPOINT', 'EXIT'].includes(item))) throw new Error('Invalid operator actions'); return items as string[]; };
  return { qrScannerEnabled: v.qrScannerEnabled, manualAttendanceEnabled: v.manualAttendanceEnabled, qrActions: actions(v.qrActions), manualActions: actions(v.manualActions), scannerEpoch: positive(v.scannerEpoch, true) };
}
export function decodeManualCheckInReceipt(value: unknown): ManualCheckInReceipt {
  const v = object(value);
  const action = string(v.action), status = string(v.status);
  if (action !== 'ENTRY' || !['RECORDED', 'DUPLICATE'].includes(status)) throw new Error('Invalid manual check-in receipt');
  return { evidenceId: positive(v.evidenceId), action, identifier: string(v.identifier), displayName: string(v.displayName), status, occurredAt: timestamp(v.occurredAt) ?? (() => { throw new Error('Missing check-in time'); })() };
}
export function decodeManualGrants(value: unknown): ManualGrant[] {
  return items(value).map(entry => {
    const v = object(entry);
    if (typeof v.enabled !== 'boolean' || !Array.isArray(v.actions) || v.actions.length > 4 || v.actions.some(action => typeof action !== 'string')) throw new Error('Invalid manual grant');
    return { userId: positive(v.userId), grantType: string(v.grantType), actions: v.actions as string[], enabled: v.enabled, epoch: positive(v.epoch, true), expiresAt: timestamp(v.expiresAt) };
  });
}

function certificatePolicy(value: unknown): 'UNREVIEWED' | 'POINT_ATTENDANCE' { if (value !== 'UNREVIEWED' && value !== 'POINT_ATTENDANCE') throw new Error('Invalid certificate evidence policy'); return value; }
