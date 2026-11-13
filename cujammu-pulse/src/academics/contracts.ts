type ScoreCard = { id: number; studentName: string; department: string; program: string | null; semester: string; academicYear: string; sgpa: number | null; cgpa: number | null; resultStatus: 'PASS' | 'PROMOTED' | 'REAPPEAR'; publishedAt: string | null; version: number };
function obj(value: unknown): Record<string, unknown> { if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid academic response'); return value as Record<string, unknown>; }
function label(value: unknown, optional = false, maxLength = 200): string | null { if (value == null && optional) return null; if (typeof value !== 'string' || value.length > maxLength) throw new Error('Invalid academic response'); return value; }
function grade(value: unknown): number | null { if (value == null) return null; if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 10) throw new Error('Invalid grade value'); return value; }
export function decodeScoreCards(value: unknown): ScoreCard[] {
  if (!Array.isArray(value) || value.length > 100) throw new Error('Invalid scorecard list');
  return value.map(item => {
    const v = obj(item);
    if (!Number.isSafeInteger(v.id) || (v.id as number) < 1 || !['PASS', 'PROMOTED', 'REAPPEAR'].includes(String(v.resultStatus))) throw new Error('Invalid scorecard');
    const publishedAt = label(v.publishedAt, true);
    if (publishedAt && !Number.isFinite(Date.parse(publishedAt))) throw new Error('Invalid publication time');
    if (!Number.isSafeInteger(v.version) || (v.version as number) < 1) throw new Error('Invalid scorecard version');
    return { id: v.id as number, studentName: label(v.studentName)!, department: label(v.department)!, program: label(v.program, true), semester: label(v.semester)!, academicYear: label(v.academicYear)!, sgpa: grade(v.sgpa), cgpa: grade(v.cgpa), resultStatus: v.resultStatus as ScoreCard['resultStatus'], publishedAt, version: v.version as number };
  });
}
export type ReviewItem = { id: number; studentName: string; verifiedIdentifier: string; department: string; program: string | null; semester: string; academicYear: string; sgpa: number | null; cgpa: number | null; resultStatus: ScoreCard['resultStatus']; submittedBy: string; submittedAt: string | null; version: number; correctionReason: string | null };
export type ReviewPage = { items: ReviewItem[]; page: number; hasNext: boolean };
export type AcademicBatchPreview = { department: string; totalRows: number; matchedRows: number; rejectedRows: number; persisted: false; rows: { rowNumber: number; verifiedIdentifier: string | null; identityMatched: boolean; issue: string | null }[] };
export type AcademicBatchReceipt = { batchId: number; status: 'PENDING_REVIEW' | 'PUBLISHED' | 'REJECTED'; rowCount: number; version: number };
export type AcademicBatchQueueItem = { batchId: number; department: string; submittedBy: string; rowCount: number; status: 'PENDING_REVIEW'; submittedAt: string | null; version: number };
export type AcademicBatchQueue = { items: AcademicBatchQueueItem[]; page: number; hasNext: boolean };
export type AcademicBatchDetail = { batch: AcademicBatchQueueItem; rows: ReviewItem[] };
export function decodeReviewScopes(value: unknown): string[] {
  if (!Array.isArray(value) || value.length > 100) throw new Error('Invalid academic authority response');
  return value.map(item => { if (typeof item !== 'string' || item.length < 1 || item.length > 120) throw new Error('Invalid academic authority response'); return item; });
}
export function decodeReviewPage(value: unknown): ReviewPage {
  const v = obj(value);
  if (!Array.isArray(v.items) || v.items.length > 50 || !Number.isSafeInteger(v.page) || (v.page as number) < 0 || typeof v.hasNext !== 'boolean') throw new Error('Invalid academic review page');
  const items = v.items.map(item => {
    const row = obj(item);
    if (!Number.isSafeInteger(row.id) || (row.id as number) < 1 || !['PASS', 'PROMOTED', 'REAPPEAR'].includes(String(row.resultStatus))) throw new Error('Invalid academic review item');
    const submittedAt = label(row.submittedAt, true);
    if (submittedAt && !Number.isFinite(Date.parse(submittedAt))) throw new Error('Invalid academic review time');
    if (!Number.isSafeInteger(row.version) || (row.version as number) < 1) throw new Error('Invalid academic review version');
    return { id: row.id as number, studentName: label(row.studentName)!, verifiedIdentifier: label(row.verifiedIdentifier)!, department: label(row.department)!, program: label(row.program, true), semester: label(row.semester)!, academicYear: label(row.academicYear)!, sgpa: grade(row.sgpa), cgpa: grade(row.cgpa), resultStatus: row.resultStatus as ScoreCard['resultStatus'], submittedBy: label(row.submittedBy, false, 255)!, submittedAt, version: row.version as number, correctionReason: label(row.correctionReason, true, 500) };
  });
  return { items, page: v.page as number, hasNext: v.hasNext };
}
export function decodeAcademicBatchPreview(value: unknown): AcademicBatchPreview {
  const v = obj(value);
  if (!Number.isSafeInteger(v.totalRows) || (v.totalRows as number) < 1 || (v.totalRows as number) > 250
      || !Number.isSafeInteger(v.matchedRows) || !Number.isSafeInteger(v.rejectedRows)
      || (v.matchedRows as number) + (v.rejectedRows as number) !== (v.totalRows as number)
      || v.persisted !== false || !Array.isArray(v.rows) || v.rows.length !== v.totalRows) throw new Error('Invalid academic batch preview');
  const rows = v.rows.map(item => {
    const row = obj(item);
    if (!Number.isSafeInteger(row.rowNumber) || (row.rowNumber as number) < 2 || typeof row.identityMatched !== 'boolean') throw new Error('Invalid academic batch preview row');
    const verifiedIdentifier = label(row.verifiedIdentifier, true); const issue = label(row.issue, true);
    return { rowNumber: row.rowNumber as number, verifiedIdentifier, identityMatched: row.identityMatched, issue };
  });
  return { department: label(v.department)!, totalRows: v.totalRows as number, matchedRows: v.matchedRows as number, rejectedRows: v.rejectedRows as number, persisted: false, rows };
}
export function decodeAcademicBatchReceipt(value: unknown): AcademicBatchReceipt {
  const v = obj(value);
  if (!Number.isSafeInteger(v.batchId) || (v.batchId as number) < 1 || !['PENDING_REVIEW', 'PUBLISHED', 'REJECTED'].includes(String(v.status))
      || !Number.isSafeInteger(v.rowCount) || (v.rowCount as number) < 1 || (v.rowCount as number) > 250
      || !Number.isSafeInteger(v.version) || (v.version as number) < 0)
    throw new Error('Invalid academic batch receipt');
  return { batchId: v.batchId as number, status: v.status as AcademicBatchReceipt['status'], rowCount: v.rowCount as number, version: v.version as number };
}
export function decodeAcademicBatchQueue(value: unknown): AcademicBatchQueue {
  const v = obj(value);
  if (!Array.isArray(v.items) || v.items.length > 25 || !Number.isSafeInteger(v.page) || (v.page as number) < 0 || typeof v.hasNext !== 'boolean') throw new Error('Invalid academic batch queue');
  const items = v.items.map(item => {
    const row = obj(item); const submittedAt = label(row.submittedAt, true);
    if (!Number.isSafeInteger(row.batchId) || (row.batchId as number) < 1 || row.status !== 'PENDING_REVIEW'
        || !Number.isSafeInteger(row.rowCount) || (row.rowCount as number) < 1 || (row.rowCount as number) > 250
        || !Number.isSafeInteger(row.version) || (row.version as number) < 0 || (submittedAt && !Number.isFinite(Date.parse(submittedAt)))) throw new Error('Invalid academic batch queue item');
    return { batchId: row.batchId as number, department: label(row.department)!, submittedBy: label(row.submittedBy, false, 255)!, rowCount: row.rowCount as number, status: 'PENDING_REVIEW' as const, submittedAt, version: row.version as number };
  });
  return { items, page: v.page as number, hasNext: v.hasNext };
}
export function decodeAcademicBatchDetail(value: unknown): AcademicBatchDetail {
  const v = obj(value); const batch = decodeAcademicBatchQueue({ items: [v.batch], page: 0, hasNext: false }).items[0]!;
  if (!Array.isArray(v.rows) || v.rows.length !== batch.rowCount) throw new Error('Invalid academic batch detail');
  const rows = decodeReviewPage({ items: v.rows, page: 0, hasNext: false }).items;
  return { batch, rows };
}
export type { ScoreCard };
