export type OwnProposal = { id: number; title: string; venue: string; eventDate: string; status: string; revision: number; rejectionReason: string | null; submittedAt: string | null };
export type OwnProposalPage = { items: OwnProposal[]; page: number; hasNext: boolean };
export type SubmitProposalCommand = { requestKey: string; title: string; description: string; categoryId: number; venue: string; eventDate: string; eventEndsAt: string | null; maxSeats: number; fees: number; contactEmail: string | null; targetDepartment: string; generateCertificate: boolean };
export type SubmitProposalReceipt = { id: number; status: string; revision: number; replayed: boolean };
export type ProposalCategory = { id: number; name: string };
export type EditableProposal = { id: number; title: string; description: string; categoryId: number; venue: string; eventDate: string; eventEndsAt: string | null; maxSeats: number; fees: number; contactEmail: string | null; targetDepartment: string; generateCertificate: boolean; revision: number; hasBanner: boolean; hasCertificateTemplate: boolean };
export type UpdateProposalCommand = Omit<SubmitProposalCommand, 'requestKey'> & { expectedRevision: number };
export type ProposalMediaKind = 'banner' | 'certificate-template';
export function proposalMediaPath(audience: 'organizer' | 'admin', proposalId: number, kind: ProposalMediaKind): string {
  if (!Number.isSafeInteger(proposalId) || proposalId < 1 || !['banner', 'certificate-template'].includes(kind))
    throw new Error('Proposal media request could not be verified.');
  return `/api/v1/${audience}/event-proposals/${proposalId}/media/${kind}`;
}
/** Validates the campus-local form value without interpreting it in the device timezone. */
export function isCampusLocalDateTime(value: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value);
  if (!match) return false;
  const year = Number(match[1]); const month = Number(match[2]); const day = Number(match[3]);
  const hour = Number(match[4]); const minute = Number(match[5]);
  if (month < 1 || month > 12 || hour > 23 || minute > 59) return false;
  const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1]!;
  return day >= 1 && day <= daysInMonth;
}
function record(value: unknown): Record<string, unknown> { if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Proposal status could not be verified.'); return value as Record<string, unknown>; }
function integer(value: unknown, minimum = 0): number { if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < minimum) throw new Error('Proposal status could not be verified.'); return value; }
function date(value: unknown, nullable = false): string | null { if (nullable && value === null) return null; if (typeof value !== 'string' || value.length > 50 || !Number.isFinite(Date.parse(value))) throw new Error('Proposal status could not be verified.'); return value; }
export function decodeOwnProposals(value: unknown): OwnProposalPage {
  const page = record(value); if (!Array.isArray(page.items) || page.items.length > 20 || typeof page.hasNext !== 'boolean') throw new Error('Proposal status could not be verified.');
  const items = page.items.map(input => { const item = record(input); const statuses = ['PENDING', 'REJECTED', 'APPROVED_PENDING_PUBLICATION', 'APPROVED', 'COMPLETED', 'CANCELLED'];
    if (typeof item.title !== 'string' || item.title.length > 200 || typeof item.venue !== 'string' || item.venue.length > 200 || typeof item.status !== 'string' || !statuses.includes(item.status) || (item.rejectionReason !== null && (typeof item.rejectionReason !== 'string' || item.rejectionReason.length > 500))) throw new Error('Proposal status could not be verified.');
    return { id: integer(item.id, 1), title: item.title, venue: item.venue, eventDate: date(item.eventDate)!, status: item.status, revision: integer(item.revision), rejectionReason: item.rejectionReason as string | null, submittedAt: date(item.submittedAt, true) };
  });
  if (new Set(items.map(item => item.id)).size !== items.length) throw new Error('Proposal status could not be verified.');
  return { items, page: integer(page.page), hasNext: page.hasNext };
}
export function decodeProposalSubmissionReceipt(value: unknown): SubmitProposalReceipt {
  const receipt = record(value); const statuses = ['PENDING', 'REJECTED', 'APPROVED_PENDING_PUBLICATION', 'APPROVED', 'COMPLETED', 'CANCELLED'];
  if (typeof receipt.status !== 'string' || !statuses.includes(receipt.status) || typeof receipt.replayed !== 'boolean') throw new Error('Proposal submission receipt could not be verified.');
  return { id: integer(receipt.id, 1), status: receipt.status, revision: integer(receipt.revision), replayed: receipt.replayed };
}
export function decodeProposalCategories(value: unknown): ProposalCategory[] {
  if (!Array.isArray(value) || value.length > 100) throw new Error('Proposal categories could not be verified.');
  const categories = value.map(input => { const category = record(input); if (typeof category.name !== 'string' || !category.name.trim() || category.name.length > 120) throw new Error('Proposal categories could not be verified.'); return { id: integer(category.id, 1), name: category.name }; });
  if (new Set(categories.map(category => category.id)).size !== categories.length) throw new Error('Proposal categories could not be verified.');
  return categories;
}
export function decodeEditableProposal(value: unknown): EditableProposal {
  const item = record(value); const statusFields = ['title', 'description', 'venue', 'targetDepartment'];
  if (statusFields.some(key => typeof item[key] !== 'string') || typeof item.contactEmail !== 'string' && item.contactEmail !== null ||
      typeof item.generateCertificate !== 'boolean' || typeof item.hasBanner !== 'boolean' || typeof item.hasCertificateTemplate !== 'boolean') throw new Error('Proposal edit details could not be verified.');
  const eventDate = date(item.eventDate)!; const eventEndsAt = date(item.eventEndsAt, true);
  if (eventEndsAt && Date.parse(eventEndsAt) <= Date.parse(eventDate)) throw new Error('Proposal edit details could not be verified.');
  if ((item.title as string).length < 3 || (item.title as string).length > 200 || (item.description as string).length < 20 || (item.description as string).length > 10000 || (item.venue as string).length > 200 || (item.targetDepartment as string).length > 160 || (item.contactEmail !== null && (item.contactEmail as string).length > 254)) throw new Error('Proposal edit details could not be verified.');
  if (typeof item.fees !== 'number' || !Number.isFinite(item.fees) || item.fees < 0 || typeof item.maxSeats !== 'number' || !Number.isSafeInteger(item.maxSeats) || item.maxSeats < 1 || item.maxSeats > 10000)
    throw new Error('Proposal edit details could not be verified.');
  return { id: integer(item.id, 1), revision: integer(item.revision), title: item.title as string,
    description: item.description as string, categoryId: integer(item.categoryId, 1), venue: item.venue as string, eventDate,
    eventEndsAt, maxSeats: item.maxSeats, fees: item.fees, contactEmail: item.contactEmail as string | null,
    targetDepartment: item.targetDepartment as string, generateCertificate: item.generateCertificate,
    hasBanner: item.hasBanner, hasCertificateTemplate: item.hasCertificateTemplate };
}
