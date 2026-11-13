export type EventProposal = {
  id: number; title: string; description: string; venue: string; eventDate: string; eventEndsAt: string | null;
  organizerName: string; status: 'PENDING' | 'APPROVED_PENDING_PUBLICATION'; revision: number; assignedReviewerId: number | null;
  assignedReviewerName: string | null; assignedReviewerActive: boolean; canAssign: boolean; canDecide: boolean;
  canPublish: boolean; maxSeats: number; fees: number; targetDepartment: string; categoryName: string | null; generateCertificate: boolean; hasBanner: boolean; hasCertificateTemplate: boolean;
};
export type Reviewer = { id: number; name: string };
export type ReviewPage<T> = { items: T[]; page: number; hasNext: boolean };
const invalid = () => new Error('The proposal review response could not be verified. Reload the queue.');
function record(value: unknown): Record<string, unknown> { if (!value || typeof value !== 'object' || Array.isArray(value)) throw invalid(); return value as Record<string, unknown>; }
function text(value: unknown, max: number): string { if (typeof value !== 'string' || value.length > max) throw invalid(); return value; }
export function reviewInteger(value: unknown, minimum = 1): number { if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < minimum) throw invalid(); return value; }
function bool(value: unknown): boolean { if (typeof value !== 'boolean') throw invalid(); return value; }
function date(value: unknown): string { const result = text(value, 50); if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?$/.test(result) || !Number.isFinite(Date.parse(result))) throw invalid(); return result; }
export function decodeEventProposal(value: unknown, expectedStatus: EventProposal['status'] = 'PENDING'): EventProposal {
  const v = record(value); if (v.status !== expectedStatus) throw invalid();
  if (typeof v.fees !== 'number' || !Number.isFinite(v.fees) || v.fees < 0) throw invalid();
  const result: EventProposal = { id: reviewInteger(v.id), title: text(v.title, 200), description: text(v.description, 10000), venue: text(v.venue, 200), eventDate: date(v.eventDate), eventEndsAt: v.eventEndsAt === null ? null : date(v.eventEndsAt), organizerName: text(v.organizerName, 500), status: expectedStatus, revision: reviewInteger(v.revision, 0), assignedReviewerId: v.assignedReviewerId === null ? null : reviewInteger(v.assignedReviewerId), assignedReviewerName: v.assignedReviewerName === null ? null : text(v.assignedReviewerName, 500), assignedReviewerActive: bool(v.assignedReviewerActive), canAssign: bool(v.canAssign), canDecide: bool(v.canDecide), canPublish: bool(v.canPublish), maxSeats: reviewInteger(v.maxSeats), fees: v.fees, targetDepartment: text(v.targetDepartment, 200), categoryName: v.categoryName === null ? null : text(v.categoryName, 500), generateCertificate: bool(v.generateCertificate), hasBanner: bool(v.hasBanner), hasCertificateTemplate: bool(v.hasCertificateTemplate) };
  if (result.assignedReviewerId === null && (result.assignedReviewerName !== null || result.assignedReviewerActive || result.canDecide)) throw invalid();
  if (result.assignedReviewerActive && !result.assignedReviewerName?.trim()) throw invalid();
  if (result.canDecide && !result.assignedReviewerActive) throw invalid();
  if (expectedStatus === 'PENDING' && result.canPublish) throw invalid();
  if (expectedStatus === 'APPROVED_PENDING_PUBLICATION' && (result.canAssign || result.canDecide)) throw invalid();
  return result;
}
function page<T extends { id: number }>(value: unknown, decode: (value: unknown) => T): ReviewPage<T> {
  const v = record(value); if (!Array.isArray(v.items) || v.items.length > 20) throw invalid();
  const items = v.items.map(item => decode(item)); if (new Set(items.map(item => item.id)).size !== items.length) throw invalid();
  return { items, page: reviewInteger(v.page, 0), hasNext: bool(v.hasNext) };
}
export const decodeProposalPage = (value: unknown) => page(value, decodeEventProposal);
export const decodePublicationPage = (value: unknown) => page(value, value => decodeEventProposal(value, 'APPROVED_PENDING_PUBLICATION'));
export const decodeReviewerPage = (value: unknown) => page(value, value => { const v = record(value); const name = text(v.name, 500); if (!name.trim()) throw invalid(); return { id: reviewInteger(v.id), name }; });
