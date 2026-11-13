import { decodeDepartments, decodeFacilities, decodeFacilityBookingReceipt, decodeFacilitySuggestions, decodePendingFacilityBookings, decodeMyFacilityBookings, decodeFlags, decodeSession } from './contracts.ts';
import { decodeEvents, decodeEventSummaryPage, decodeOperations, decodeVolunteers, decodeOperatorCapabilities, decodeManualCheckInReceipt, decodeManualGrants, decodeScheduleChanges } from './event-operations/contracts.ts';
import { decodeNotifications } from './notifications/contracts.ts';
import { decodeEntry, decodeManagedTicket, decodeTicket, decodeTicketPage, type ManagedTicket, type Ticket, type TicketEntry, type TicketPage } from './helpdesk/contracts.ts';
import { decodeHostels, decodeHostelCredential, decodePass, decodePasses, decodePendingPasses, decodePendingScanIntents } from './hostels/contracts.ts';
import { decodeFeatureControl, decodeFeatureControls, decodeFeatureControlAudit } from './admin/feature-controls.ts';
import { decodeMediaStorageInventory } from './admin/media-storage.ts';
import { decodeStudentCandidate, decodeStudentVerification, type StudentVerification } from './admin/student-identity.ts';
import { decodeEventProposal, decodeProposalPage, decodePublicationPage, decodeReviewerPage, reviewInteger } from './admin/event-proposal-review.ts';
import { decodeLibrarySession, decodeLibraryHistory } from './library/contracts.ts';
import { decodeEvents as decodeCatalogEvents, decodeRegistration, decodeRegistrations } from './events/contracts.ts';
import { decodeEditableProposal, decodeOwnProposals, decodeProposalCategories, decodeProposalSubmissionReceipt, proposalMediaPath, type EditableProposal, type ProposalMediaKind, type SubmitProposalCommand, type UpdateProposalCommand } from './events/proposals.ts';
import { decodeAttendanceCredential, decodeAttendanceReceipt, decodePendingAttendance } from './attendance/contracts.ts';
import { decodeVolunteerTasks,decodeEligibleVolunteers,type TaskCreate,type TaskAssign,type TaskComplete,type TaskReview } from './volunteers/contracts.ts';
import { decodeLostFoundItem, decodeLostFoundPage, decodeFoundClaim, decodeFoundClaims, type FoundCategory, type ReportFoundCommand, type ClaimFoundCommand, type HandoverFoundCommand } from './lost-found/contracts.ts';
import { decodeFixtures } from './sports/contracts.ts';
import { decodeJudgingScores, decodeLeaderboard } from './judging/contracts.ts';
import { decodeFeedback, decodeFeedbackSummary } from './insights/contracts.ts';
import { decodeCalendar, decodeCalendarPage, decodeCalendarClashes } from './calendar/contracts.ts';
import { decodeDisruptions } from './disruptions/contracts.ts';
import { decodeReadiness } from './readiness/contracts.ts';
import { decodeReadinessOwners, decodeReadinessTask, decodeReadinessTaskList } from './readiness/task-contracts.ts';
import { decodeGroups, decodeGroupPage, decodeMessages, type ChatMessage } from './chat/contracts.ts';
import { decodeDepartmentChatGroups, decodeChatMembershipPage, decodeChatMembership, type ChatMembershipCommand } from './chat/membership-contracts.ts';
import { decodeEligibleEvents, decodeTeam, decodeTeams, decodeRoster, decodeTeamHistory } from './teams/contracts.ts';
import { decodePollEvents, decodePollPage, decodePoll, type CreatePoll } from './polls/contracts.ts';
import { decodeAssistantReply } from './assistant/contracts.ts';
import { decodeFeedComment, decodeFeedPage, decodeLikeResult } from './feed/contracts.ts';
import { isNativeApi, nativeAccess, nativeApiUrl } from './identity/transport.ts';
import { decodeCertificates, decodeCertificateFinalization } from './certificates/contracts.ts';
import { boundedJson } from './bounded-json.ts';
import { decodeRecoverableVisits, decodeSafeExit } from './library/recovery-contracts.ts';
import { newRequestKey } from './request-key.ts';
export { newRequestKey };

export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message: string) { super(message); this.status = status; }
}
/** Web BFF transport. Cookies stay in the browser; never copy them into JS storage. */
export async function requestJson(path: string, signal?: AbortSignal, command?: { method: 'POST' | 'PUT'; body: unknown; token: string; expectedStatus?: 204 }): Promise<unknown> {
  if (!path.startsWith('/api/v1/') || path.includes('://')) throw new Error('Invalid API path');
  const controller = new AbortController();
  const abort = () => controller.abort();
  if (signal?.aborted) controller.abort();
  signal?.addEventListener('abort', abort, { once: true });
  const timeout = setTimeout(abort, 12_000);
  try {
  const access = isNativeApi() ? nativeAccess() : null;
  const epoch = access?.epoch();
  let authorization: string | null = null;
  try { authorization = access ? await access.bearer() : null; }
  catch { throw new ApiError(401, 'Your session has ended. Sign in again.'); }
  if (access && epoch !== undefined) access.assertEpoch(epoch);
  const headers: Record<string, string> = { Accept: 'application/json', ...(command ? { 'Content-Type': 'application/json' } : {}) };
  if (authorization) headers.Authorization = `Bearer ${authorization}`;
  else if (command) headers['X-CSRF-TOKEN'] = command.token;
  const perform = access?.fetcher ?? fetch;
  const response = await perform(access ? nativeApiUrl(access.config, path) : path, { credentials: access ? 'omit' : 'same-origin', method: command?.method ?? 'GET', body: command ? JSON.stringify(command.body) : undefined, headers, cache: 'no-store', redirect: 'error', signal: controller.signal });
  if (access && epoch !== undefined) access.assertEpoch(epoch);
  if (response.status === 401) { if (access) await access.clear(); throw new ApiError(401, 'Your session has ended. Sign in again.'); }
  if (response.status === 403) throw new ApiError(403, 'Your account cannot access this information.');
  if (response.status === 404) throw new ApiError(404, 'This feature is currently unavailable.');
  if (response.status === 409) throw new ApiError(409, 'This record changed or conflicts with university policy. Reload the latest record before trying again.');
  if (response.status === 400) throw new ApiError(400, 'Check required fields and permitted links, then try again.');
  if (!response.ok) throw new ApiError(response.status, 'The campus service could not complete this request.');
  if (command?.expectedStatus && response.status !== command.expectedStatus) throw new ApiError(502, 'The service did not confirm the requested change. Reload the current record.');
  if (response.status === 204) return null;
  if (!response.headers.get('content-type')?.includes('application/json')) throw new ApiError(502, 'The service returned an unexpected response.');
  const value = await boundedJson(response);
  if (access && epoch !== undefined) access.assertEpoch(epoch);
  return value;
  } finally { clearTimeout(timeout); signal?.removeEventListener('abort', abort); }
}
import { decodeAcademicBatchDetail, decodeAcademicBatchPreview, decodeAcademicBatchQueue, decodeAcademicBatchReceipt, decodeReviewPage, decodeReviewScopes, decodeScoreCards } from './academics/contracts.ts';

export const api = {
  eventProposalQueue: async (page = 0, signal?: AbortSignal) => {
    reviewInteger(page, 0); const result = decodeProposalPage(await requestJson(`/api/v1/admin/event-proposals?page=${page}`, signal));
    if (result.page !== page) throw new Error('Proposal queue page does not match the request'); return result;
  },
  myEventProposals: async (page = 0, signal?: AbortSignal) => {
    reviewInteger(page, 0); const result = decodeOwnProposals(await requestJson(`/api/v1/organizer/event-proposals?page=${page}`, signal));
    if (result.page !== page) throw new Error('Proposal list page does not match the request'); return result;
  },
  eventProposalCategories: async (signal?: AbortSignal) => decodeProposalCategories(await requestJson('/api/v1/organizer/event-proposals/categories', signal)),
  eventProposalMediaPath: (audience: 'organizer' | 'admin', proposalId: number, kind: ProposalMediaKind) => proposalMediaPath(audience, proposalId, kind),
  editableEventProposal: async (eventId: number, signal?: AbortSignal) => {
    reviewInteger(eventId, 1); const proposal = decodeEditableProposal(await requestJson(`/api/v1/organizer/event-proposals/${eventId}`, signal));
    if (proposal.id !== eventId) throw new Error('Proposal edit details do not match the selected proposal'); return proposal;
  },
  submitEventProposal: async (command: SubmitProposalCommand, signal?: AbortSignal) => {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(command.requestKey)) throw new Error('A valid proposal request key is required');
    const receipt = decodeProposalSubmissionReceipt(await mutateJson('/api/v1/organizer/event-proposals', 'POST', command, signal));
    return receipt;
  },
  updateEventProposal: async (eventId: number, command: UpdateProposalCommand, signal?: AbortSignal): Promise<EditableProposal> => {
    reviewInteger(eventId, 1); reviewInteger(command.expectedRevision, 0);
    const { expectedRevision, ...fields } = command;
    const updated = decodeEditableProposal(await mutateJson(`/api/v1/organizer/event-proposals/${eventId}`, 'PUT', command, signal));
    if (updated.id !== eventId || updated.revision <= expectedRevision || updated.title !== fields.title || updated.description !== fields.description)
      throw new Error('Proposal update receipt does not match the submitted revision and values');
    return updated;
  },
  eventProposalPublicationQueue: async (page = 0, signal?: AbortSignal) => {
    reviewInteger(page, 0); const result = decodePublicationPage(await requestJson(`/api/v1/admin/event-proposals/publication-queue?page=${page}`, signal));
    if (result.page !== page) throw new Error('Publication queue page does not match the request'); return result;
  },
  eventProposalReviewers: async (page = 0, signal?: AbortSignal) => {
    reviewInteger(page, 0); const result = decodeReviewerPage(await requestJson(`/api/v1/admin/event-proposals/reviewers?page=${page}`, signal));
    if (result.page !== page) throw new Error('Reviewer directory page does not match the request'); return result;
  },
  assignEventProposalReviewer: async (eventId: number, command: { expectedRevision: number; reviewerId: number }, signal?: AbortSignal) => {
    reviewInteger(eventId); reviewInteger(command.expectedRevision, 0); reviewInteger(command.reviewerId);
    const result = decodeEventProposal(await mutateJson(`/api/v1/admin/event-proposals/${eventId}/reviewer`, 'PUT', command, signal));
    if (result.id !== eventId || result.assignedReviewerId !== command.reviewerId || !result.assignedReviewerActive || result.revision < command.expectedRevision) throw new Error('Reviewer assignment receipt does not match the request');
    return result;
  },
  decideEventProposal: async (eventId: number, expectedRevision: number, decision: 'approve' | 'reject', reason: string, signal?: AbortSignal) => {
    reviewInteger(eventId); reviewInteger(expectedRevision, 0);
    if (!['approve', 'reject'].includes(decision) || (decision === 'reject' && (!reason.trim() || reason.length > 500))) throw new Error('Invalid proposal review command');
    await mutateJson(`/api/v1/admin/event-proposals/${eventId}/${decision}`, 'POST', { expectedRevision, ...(decision === 'reject' ? { reason } : {}) }, signal, 204);
  },
  publishEventProposal: async (eventId: number, expectedRevision: number, signal?: AbortSignal) => {
    reviewInteger(eventId); reviewInteger(expectedRevision, 0);
    await mutateJson(`/api/v1/admin/event-proposals/${eventId}/publish`, 'POST', { expectedRevision }, signal, 204);
  },
  lookupStudent: async (email: string, signal?: AbortSignal) => {
    const result = decodeStudentCandidate(await mutateJson('/api/v1/admin/students/lookup', 'POST', { email }, signal));
    if (result.email !== email.trim().toLowerCase()) throw new Error('Student lookup does not match the requested account');
    return result;
  },
  verifyStudent: async (studentId: number, command: StudentVerification, signal?: AbortSignal) => {
    if (!Number.isSafeInteger(studentId) || studentId < 1) throw new Error('Invalid student account');
    const result = decodeStudentVerification(await mutateJson(`/api/v1/admin/students/${studentId}/verified-identifier`, 'PUT', command, signal));
    if (result.studentId !== studentId || result.verifiedIdentifier !== command.identifier.trim()) throw new Error('Verification receipt does not match this request');
    return result;
  },
  recoverableLibraryVisits: async (signal?: AbortSignal) => decodeRecoverableVisits(await requestJson('/api/v1/cujammu/recovery/library/active', signal)),
  safeExitLibraryVisit: async (visitId: number, command: { requestKey: string; expectedRevision: number; reasonCode: 'SERVICE_DISABLED' | 'SCANNER_UNAVAILABLE' | 'ACCESSIBILITY_SUPPORT' }, signal?: AbortSignal) => decodeSafeExit(await mutateJson(`/api/v1/cujammu/recovery/library/${visitId}/safe-exit`, 'POST', command, signal)),
  myCertificates: async (page = 0, signal?: AbortSignal) => decodeCertificates(await requestJson(`/api/v1/cujammu/certificates/own?page=${page}`, signal)),
  finalizeEventCertificates: async (eventId: number, command: { expectedOperationsRevision: number; requestKey: string; reason: string }, signal?: AbortSignal) => decodeCertificateFinalization(await mutateJson(`/api/v1/cujammu/events/${eventId}/certificates/finalize`, 'POST', command, signal)),
  configureEventEvidence: async (eventId: number, command: { expectedRevision: number; attendanceMode: 'SINGLE_QR' | 'DUAL_QR' | 'NO_QR'; singleQrPolicy: 'ENTRY_ONLY' | 'ENTRY_AND_ATTENDANCE'; certificateEvidencePolicy: 'UNREVIEWED' | 'POINT_ATTENDANCE' }, signal?: AbortSignal) => decodeOperations(await mutateJson(`/api/v1/cujammu/events/${eventId}/operations`, 'PUT', command, signal)),
  assistantChat: async (message: string, signal?: AbortSignal) => decodeAssistantReply(await mutateJson('/api/v1/cujammu/assistant/chat', 'POST', { message: message.slice(0, 2000) }, signal)),
  feed: async (page = 0, signal?: AbortSignal) => decodeFeedPage(await requestJson(`/api/v1/cujammu/feed?page=${page}&size=20`, signal)),
  toggleFeedLike: async (postId:number, signal?:AbortSignal) => decodeLikeResult(await mutateJson(`/api/v1/cujammu/feed/${postId}/like`, 'POST', {}, signal)),
  commentFeedPost: async (postId:number, content:string, signal?:AbortSignal) => decodeFeedComment(await mutateJson(`/api/v1/cujammu/feed/${postId}/comment`, 'POST', { content: content.slice(0, 2000) }, signal)),
  feedComments: async (postId:number, signal?:AbortSignal) => { const value = await requestJson(`/api/v1/cujammu/feed/${postId}/comments`, signal); if (!Array.isArray(value) || value.length > 50) throw new Error('Invalid feed comments'); return value.map(decodeFeedComment); },
  pollEvents: async (page = 0, signal?: AbortSignal) => decodePollEvents(await requestJson(`/api/v1/cujammu/polls/events?page=${page}`, signal)),
  eventPolls: async (eventId: number, page = 0, signal?: AbortSignal) => decodePollPage(await requestJson(`/api/v1/cujammu/events/${eventId}/polls?page=${page}`, signal)),
  createPoll: async (eventId: number, command: CreatePoll, signal?: AbortSignal) => decodePoll(await mutateJson(`/api/v1/cujammu/events/${eventId}/polls`, 'POST', command, signal)),
  publishPoll: async (id: number, expectedRevision: number, signal?: AbortSignal) => decodePoll(await mutateJson(`/api/v1/cujammu/polls/${id}/publish`, 'POST', { expectedRevision }, signal)),
  closePoll: async (id: number, expectedRevision: number, signal?: AbortSignal) => decodePoll(await mutateJson(`/api/v1/cujammu/polls/${id}/close`, 'POST', { expectedRevision }, signal)),
  votePoll: async (id: number, option: string, signal?: AbortSignal) => decodePoll(await mutateJson(`/api/v1/cujammu/polls/${id}/vote`, 'POST', { option }, signal)),
  pollResults: async (id: number, signal?: AbortSignal) => decodePoll(await requestJson(`/api/v1/cujammu/polls/${id}/results`, signal)),
  session: async (signal?: AbortSignal) => decodeSession(await requestJson('/api/v1/session', signal)),
  features: async (signal?: AbortSignal) => decodeFlags(await requestJson('/api/v1/cujammu/features', signal)),
  featureControls: async (signal?: AbortSignal) => decodeFeatureControls(await requestJson('/api/v1/cujammu/admin/feature-controls', signal)),
  featureControlAudit: async (page = 0, size = 25, signal?: AbortSignal) => decodeFeatureControlAudit(await requestJson(`/api/v1/cujammu/admin/feature-controls/audit?page=${page}&size=${size}`, signal)),
  mediaStorageInventory: async (signal?: AbortSignal) => decodeMediaStorageInventory(await requestJson('/api/v1/cujammu/admin/media/storage', signal)),
  libraryEntry: async (sessionType: 'STUDY' | 'BOOK_ISSUE_RETURN', signal?: AbortSignal) => decodeLibrarySession(await mutateJson('/api/v1/cujammu/library/scan/entry', 'POST', { sessionType }, signal)),
  libraryExit: async (sessionType: 'STUDY' | 'BOOK_ISSUE_RETURN', signal?: AbortSignal) => decodeLibrarySession(await mutateJson('/api/v1/cujammu/library/scan/exit', 'POST', { sessionType }, signal)),
  libraryActive: async (signal?: AbortSignal) => { const value = await requestJson('/api/v1/cujammu/library/active', signal); return value == null ? null : decodeLibrarySession(value); },
  libraryHistory: async (page = 0, size = 20, signal?: AbortSignal) => decodeLibraryHistory(await requestJson(`/api/v1/cujammu/library/history?page=${page}&size=${size}`, signal)),
  eventCatalog: async (signal?: AbortSignal) => decodeCatalogEvents(await requestJson('/api/v1/cujammu/events/catalog', signal)),
  registerForEvent: async (eventId: number, signal?: AbortSignal) => decodeRegistration(await mutateJson(`/api/v1/cujammu/events/${eventId}/registrations`, 'POST', {}, signal)),
  myRegistrations: async (signal?: AbortSignal) => decodeRegistrations(await requestJson('/api/v1/cujammu/events/registrations/mine', signal)),
  cancelEventRegistration: async (registrationId: number, signal?: AbortSignal) => { await mutateJson(`/api/v1/cujammu/events/registrations/${registrationId}/cancel`, 'POST', {}, signal); },
  issueAttendanceCredential: async (eventId: number, registrationId: number, action: 'ENTRY' | 'ATTENDANCE_CHECKPOINT', signal?: AbortSignal) => decodeAttendanceCredential(await mutateJson(`/api/v1/cujammu/events/${eventId}/registrations/${registrationId}/credential`, 'POST', { action }, signal)),
  createAttendanceScanIntent: async (eventId: number, credential: string, action: 'ENTRY' | 'ATTENDANCE_CHECKPOINT', requestKey: string, signal?: AbortSignal) => await mutateJson(`/api/v1/cujammu/events/${eventId}/attendance/scan-intents`, 'POST', { credential, action, requestKey }, signal),
  pendingAttendanceIntents: async (signal?: AbortSignal) => decodePendingAttendance(await requestJson('/api/v1/cujammu/events/attendance/scan-intents/pending', signal)),
  confirmAttendanceIntent: async (intentKey: string, challengeCode: string, requestKey: string, signal?: AbortSignal) => decodeAttendanceReceipt(await mutateJson(`/api/v1/cujammu/events/attendance/scan-intents/${encodeURIComponent(intentKey)}/confirm`, 'POST', { challengeCode, requestKey }, signal)),
  attendanceReportUrl: (eventId: number, format: 'csv' | 'xlsx') => {
    if (!Number.isSafeInteger(eventId) || eventId < 1 || !['csv', 'xlsx'].includes(format)) throw new Error('Invalid attendance report request');
    return `/api/v1/cujammu/events/${eventId}/attendance/report.${format}`;
  },
  attendanceScanLogUrl: (eventId: number) => {
    if (!Number.isSafeInteger(eventId) || eventId < 1) throw new Error('Invalid attendance report request');
    return `/api/v1/cujammu/events/${eventId}/attendance/scan-log.csv`;
  },
  myVolunteerTasks: async (page=0,signal?: AbortSignal) => decodeVolunteerTasks(await requestJson(`/api/v1/cujammu/volunteers/mine?page=${page}`, signal)),
  eventVolunteerTasks: async (eventId: number,page=0,signal?: AbortSignal) => decodeVolunteerTasks(await requestJson(`/api/v1/cujammu/volunteers/events/${eventId}/tasks?page=${page}`, signal)),
  eligibleVolunteers: async(eventId:number,page=0,signal?:AbortSignal)=>decodeEligibleVolunteers(await requestJson(`/api/v1/cujammu/volunteers/events/${eventId}/eligible?page=${page}`,signal)),
  createVolunteerTask: async (eventId: number, command:TaskCreate,signal?:AbortSignal) => decodeVolunteerTasks([await mutateJson(`/api/v1/cujammu/volunteers/events/${eventId}/tasks`, 'POST', command, signal)])[0]!,
  assignVolunteerTask: async(taskId:number,command:TaskAssign,signal?:AbortSignal)=>decodeVolunteerTasks([await mutateJson(`/api/v1/cujammu/volunteers/tasks/${taskId}/assign`,'PUT',command,signal)])[0]!,
  completeVolunteerTask: async (taskId: number, command:TaskComplete,signal?:AbortSignal) => decodeVolunteerTasks([await mutateJson(`/api/v1/cujammu/volunteers/tasks/${taskId}/complete`, 'POST', command, signal)])[0]!,
  approveVolunteerTask: async (taskId: number, command:TaskReview,signal?:AbortSignal) => decodeVolunteerTasks([await mutateJson(`/api/v1/cujammu/volunteers/tasks/${taskId}/approve`, 'POST', command, signal)])[0]!,
  lostFound: async (category?: FoundCategory, page = 0, signal?: AbortSignal) => decodeLostFoundPage(await requestJson(`/api/v1/cujammu/lost-and-found?page=${page}${category ? `&category=${encodeURIComponent(category)}` : ''}`, signal)),
  reportFoundItem: async (command: ReportFoundCommand, signal?: AbortSignal) => decodeLostFoundItem(await mutateJson('/api/v1/cujammu/lost-and-found', 'POST', command, signal)),
  claimLostFound: async (id: number, command: ClaimFoundCommand, signal?: AbortSignal) => decodeFoundClaim(await mutateJson(`/api/v1/cujammu/lost-and-found/${id}/claim`, 'POST', command, signal)),
  lostFoundClaims: async (id: number, page = 0, signal?: AbortSignal) => decodeFoundClaims(await requestJson(`/api/v1/cujammu/lost-and-found/${id}/claims?page=${page}`, signal)),
  handoverLostFound: async (id: number, command: HandoverFoundCommand, signal?: AbortSignal) => decodeLostFoundItem(await mutateJson(`/api/v1/cujammu/lost-and-found/${id}/hand-over`, 'POST', command, signal)),
  tournamentFixtures: async (eventId: number, signal?: AbortSignal) => decodeFixtures(await requestJson(`/api/v1/cujammu/events/${eventId}/tournaments/fixtures`, signal)),
  createFixture: async (command: Record<string, unknown>, signal?: AbortSignal) => decodeFixtures([await mutateJson('/api/v1/cujammu/tournaments/fixtures', 'POST', command, signal)])[0]!,
  updateFixtureScore: async (fixtureId: number, command: Record<string, unknown>, signal?: AbortSignal) => decodeFixtures([await mutateJson(`/api/v1/cujammu/tournaments/fixtures/${fixtureId}/score`, 'PUT', command, signal)])[0]!,
  judgingScores: async (eventId: number, round: number, signal?: AbortSignal) => decodeJudgingScores(await requestJson(`/api/v1/cujammu/events/${eventId}/judging/round/${round}/scores`, signal)),
  submitJudgingScore: async (eventId: number, command: Record<string, unknown>, signal?: AbortSignal) => decodeJudgingScores([await mutateJson(`/api/v1/cujammu/events/${eventId}/judging/score`, 'POST', command, signal)])[0]!,
  judgingLeaderboard: async (eventId: number, round: number, signal?: AbortSignal) => decodeLeaderboard(await requestJson(`/api/v1/cujammu/events/${eventId}/judging/round/${round}/leaderboard`, signal)),
  feedbackSummary: async (eventId:number, signal?:AbortSignal) => decodeFeedbackSummary(await requestJson(`/api/v1/cujammu/insights/events/${eventId}/feedback`, signal)),
  feedbackMine: async (eventId:number, signal?:AbortSignal) => { const value=await requestJson(`/api/v1/cujammu/insights/events/${eventId}/feedback/mine`,signal); return value===null?null:decodeFeedback(value); },
  submitFeedback: async (eventId:number, rating:number, comment:string, signal?:AbortSignal) => decodeFeedback(await mutateJson(`/api/v1/cujammu/insights/events/${eventId}/feedback`, 'POST', { rating, comment }, signal)),
  calendarPage: async (page:number, signal?:AbortSignal) => { if(!Number.isInteger(page)||page<0||page>10000) throw new Error('Invalid calendar page'); return decodeCalendarPage(await requestJson(`/api/v1/cujammu/events/calendar/mine?page=${page}`, signal)); },
  calendarClashes: async (eventId:number, signal?:AbortSignal) => {
    if (!Number.isSafeInteger(eventId) || eventId < 1) throw new Error('Invalid event');
    const result = decodeCalendarClashes(await requestJson(`/api/v1/cujammu/events/calendar/clashes/${eventId}`, signal));
    if (result.eventId !== eventId) throw new Error('Schedule response does not match the requested event');
    return result;
  },
  myCalendar: async (signal?:AbortSignal) => decodeCalendarPage(await requestJson('/api/v1/cujammu/events/calendar/mine?page=0', signal)).items,
  insightEvents: async (page:number, signal?:AbortSignal) => { if(!Number.isInteger(page)||page<0||page>10000) throw new Error('Invalid event page'); return decodeCalendarPage(await requestJson(`/api/v1/cujammu/insights/events/mine?page=${page}`, signal)); },
  myDisruptions: async (signal?:AbortSignal) => decodeDisruptions(await requestJson('/api/v1/cujammu/disruptions/mine', signal)),
  eventReadiness: async (eventId:number, signal?:AbortSignal) => decodeReadiness(await requestJson(`/api/v1/cujammu/readiness/events/${eventId}`, signal)),
  readinessTaskOwners: async (eventId: number, signal?: AbortSignal) => decodeReadinessOwners(await requestJson(`/api/v1/cujammu/readiness/events/${eventId}/tasks/owners`, signal)),
  readinessTasks: async (eventId: number, signal?: AbortSignal) => decodeReadinessTaskList(await requestJson(`/api/v1/cujammu/readiness/events/${eventId}/tasks`, signal)),
  createReadinessTask: async (eventId: number, command: { title: string; description: string; category: string; ownerUserId: number; dueAt: string; requestKey: string }, signal?: AbortSignal) => decodeReadinessTask(await mutateJson(`/api/v1/cujammu/readiness/events/${eventId}/tasks`, 'POST', command, signal)),
  updateReadinessTask: async (eventId: number, taskId: number, command: { expectedRevision: number; title: string; description: string; category: string; ownerUserId: number; dueAt: string; status: 'OPEN' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE'; completionNote: string }, signal?: AbortSignal) => decodeReadinessTask(await mutateJson(`/api/v1/cujammu/readiness/events/${eventId}/tasks/${taskId}`, 'PUT', command, signal)),
  setFeatureControl: async (key: string, enabled: boolean, expectedRevision: number, reason: string, signal?: AbortSignal) => decodeFeatureControl(await mutateJson(`/api/v1/cujammu/admin/feature-controls/${encodeURIComponent(key)}`, 'PUT', { enabled, expectedRevision, reason }, signal)),
  chatGroups: async (signal?: AbortSignal) => decodeGroups(await requestJson('/api/v1/cujammu/chat/groups', signal)),
  chatGroupPage: async (page = 0, signal?: AbortSignal) => decodeGroupPage(await requestJson(`/api/v1/cujammu/chat/groups/page?page=${page}`, signal)),
  departmentChatGroups: async (signal?: AbortSignal) => decodeDepartmentChatGroups(await requestJson('/api/v1/cujammu/chat/admin/department-groups', signal)),
  chatMemberships: async (groupId: number, page = 0, signal?: AbortSignal) => decodeChatMembershipPage(await requestJson(`/api/v1/cujammu/chat/admin/groups/${groupId}/members?page=${page}`, signal)),
  setChatMembership: async (groupId: number, userId: number, command: ChatMembershipCommand, signal?: AbortSignal) => decodeChatMembership(await mutateJson(`/api/v1/cujammu/chat/admin/groups/${groupId}/members/${userId}`, 'PUT', command, signal)),
  chatMessages: async (groupId: number, signal?: AbortSignal) => decodeMessages(await requestJson(`/api/v1/cujammu/chat/groups/${groupId}/messages`, signal)),
  postChatMessage: async (groupId: number, content: string, requestKey: string, signal?: AbortSignal): Promise<ChatMessage> => decodeMessages({ items: [await mutateJson(`/api/v1/cujammu/chat/groups/${groupId}/messages`, 'POST', { requestKey, content }, signal)] })[0]!,
  teamEligibleEvents: async (signal?: AbortSignal) => decodeEligibleEvents(await requestJson('/api/v1/cujammu/teams/eligible-events', signal)),
  teamHistory: async (page = 0, signal?: AbortSignal) => decodeTeamHistory(await requestJson(`/api/v1/cujammu/teams/mine?page=${page}`, signal)),
  eventTeams: async (eventId: number, signal?: AbortSignal) => decodeTeams(await requestJson(`/api/v1/cujammu/events/${eventId}/teams`, signal)),
  myEventTeams: async (eventId: number, signal?: AbortSignal) => decodeTeams(await requestJson(`/api/v1/cujammu/events/${eventId}/teams/mine`, signal)),
  teamRoster: async (teamId: number, signal?: AbortSignal) => decodeRoster(await requestJson(`/api/v1/cujammu/teams/${teamId}/members`, signal)),
  createTeam: async (eventId: number, name: string, minSize: number, maxSize: number, signal?: AbortSignal) => decodeTeam(await mutateJson(`/api/v1/cujammu/events/${eventId}/teams/create`, 'POST', { name, minSize, maxSize }, signal)),
  joinTeam: async (expectedEventId: number, inviteCode: string, signal?: AbortSignal) => decodeTeam(await mutateJson('/api/v1/cujammu/teams/join', 'POST', { expectedEventId, inviteCode }, signal)),
  lockTeam: async (teamId: number, expectedRevision: number, locked: boolean, signal?: AbortSignal) => decodeTeam(await mutateJson(`/api/v1/cujammu/teams/${teamId}/lock`, 'PUT', { expectedRevision, locked }, signal)),
  departments: async (signal?: AbortSignal) => decodeDepartments(await requestJson('/api/v1/cujammu/departments', signal)),
  facilities: async (signal?: AbortSignal) => decodeFacilities(await requestJson('/api/v1/cujammu/facilities', signal)),
  facilitySuggestions: async (facilityId: number, durationMinutes: number, days: number, signal?: AbortSignal) => decodeFacilitySuggestions(await requestJson(`/api/v1/cujammu/facilities/${facilityId}/availability-suggestions?durationMinutes=${durationMinutes}&days=${days}`, signal)),
  bookFacility: async (facilityId: number, command: { title: string; startTime: string; endTime: string; requestedMics: number; requestedProjector: boolean; requestKey: string }, signal?: AbortSignal) => decodeFacilityBookingReceipt(await mutateJson(`/api/v1/cujammu/facilities/${facilityId}/book`, 'POST', command, signal)),
  pendingFacilityBookings: async (page: number, signal?: AbortSignal) => decodePendingFacilityBookings(await requestJson(`/api/v1/cujammu/facilities/bookings/pending?page=${page}`, signal)),
  myFacilityBookings: async (page: number, signal?: AbortSignal) => decodeMyFacilityBookings(await requestJson(`/api/v1/cujammu/facilities/bookings/mine?page=${page}`, signal)),
  decideFacilityBooking: async (bookingId: number, expectedVersion: number, decision: 'APPROVE' | 'REJECT', reason: string, signal?: AbortSignal) => decodeFacilityBookingReceipt(await mutateJson(`/api/v1/cujammu/facilities/bookings/${bookingId}/decision`, 'PUT', { expectedVersion, decision, reason }, signal)),
  cancelFacilityBooking: async (bookingId: number, expectedVersion: number, reason: string, signal?: AbortSignal) => decodeFacilityBookingReceipt(await mutateJson(`/api/v1/cujammu/facilities/bookings/${bookingId}/cancel`, 'PUT', { expectedVersion, reason }, signal)),
  notifications: async (beforeId?: number, signal?: AbortSignal) => decodeNotifications(await requestJson(`/api/v1/cujammu/notifications${beforeId == null ? '' : `?beforeId=${beforeId}`}`, signal)),
  markNotificationRead: async (notificationId: number, signal?: AbortSignal) => mutateJson(`/api/v1/cujammu/notifications/${notificationId}/read`, 'POST', {}, signal),
  acknowledgeNotification: async (notificationId: number, signal?: AbortSignal) => mutateJson(`/api/v1/cujammu/notifications/${notificationId}/acknowledge`, 'POST', {}, signal),
  createHelpdeskTicket: async (command: { requestKey: string; subject: string; description: string; category: string; requestedPriority: string }, signal?: AbortSignal) => decodeTicket(await mutateJson('/api/v1/cujammu/helpdesk/tickets', 'POST', command, signal)),
  myHelpdeskTickets: async (page = 0, signal?: AbortSignal): Promise<TicketPage<Ticket>> => decodeTicketPage(await requestJson(`/api/v1/cujammu/helpdesk/my-tickets?page=${page}`, signal), decodeTicket) as TicketPage<Ticket>,
  assignedHelpdeskTickets: async (page = 0, signal?: AbortSignal): Promise<TicketPage<ManagedTicket>> => decodeTicketPage(await requestJson(`/api/v1/cujammu/helpdesk/assigned?page=${page}`, signal), decodeManagedTicket) as TicketPage<ManagedTicket>,
  allHelpdeskTickets: async (page = 0, signal?: AbortSignal): Promise<TicketPage<ManagedTicket>> => decodeTicketPage(await requestJson(`/api/v1/cujammu/helpdesk/all?page=${page}`, signal), decodeManagedTicket) as TicketPage<ManagedTicket>,
  helpdeskTicket: async (ticketId: string, signal?: AbortSignal) => decodeTicket(await requestJson(`/api/v1/cujammu/helpdesk/tickets/${ticketId}`, signal)),
  helpdeskThread: async (ticketId: string, page = 0, signal?: AbortSignal): Promise<TicketPage<TicketEntry>> => decodeTicketPage(await requestJson(`/api/v1/cujammu/helpdesk/tickets/${ticketId}/thread?page=${page}`, signal), decodeEntry) as TicketPage<TicketEntry>,
  replyHelpdeskTicket: async (ticketId: string, expectedVersion: number, requestKey: string, body: string, signal?: AbortSignal) => decodeTicket(await mutateJson(`/api/v1/cujammu/helpdesk/tickets/${ticketId}/replies`, 'POST', { expectedVersion, requestKey, body }, signal)),
  reopenHelpdeskTicket: async (ticketId: string, expectedVersion: number, requestKey: string, reason: string, signal?: AbortSignal) => decodeTicket(await mutateJson(`/api/v1/cujammu/helpdesk/tickets/${ticketId}/reopen`, 'POST', { expectedVersion, requestKey, reason }, signal)),
  escalateHelpdeskTicket: async (ticketId: string, expectedVersion: number, requestKey: string, reason: string, signal?: AbortSignal) => decodeTicket(await mutateJson(`/api/v1/cujammu/helpdesk/tickets/${ticketId}/escalate`, 'POST', { expectedVersion, requestKey, reason }, signal)),
  triageHelpdeskTicket: async (ticketId: string, command: { expectedVersion: number; requestKey: string; status: string; priority: string; assignedOffice: string; assignedStaffId: number | null; resolutionNotes: string | null }, signal?: AbortSignal) => decodeManagedTicket(await mutateJson(`/api/v1/cujammu/helpdesk/tickets/${ticketId}/triage`, 'PUT', command, signal)),
  hostels: async (signal?: AbortSignal) => decodeHostels(await requestJson('/api/v1/cujammu/hostels', signal)),
  myGatePasses: async (signal?: AbortSignal) => decodePasses(await requestJson('/api/v1/cujammu/hostels/gate-pass/my', signal)),
  applyGatePass: async (command: { hostelId: number; passType: 'DAY_OUT' | 'NIGHT_OUT'; destination: string; reason: string; expectedExitTime: string; expectedReturnTime: string }, signal?: AbortSignal) => decodePass(await mutateJson('/api/v1/cujammu/hostels/gate-pass/apply', 'POST', command, signal)),
  pendingGatePasses: async (hostelId: number, signal?: AbortSignal) => decodePendingPasses(await requestJson(`/api/v1/cujammu/hostels/gate-pass/pending?hostelId=${hostelId}`, signal)),
  decideGatePass: async (passId: number, approve: boolean, rejectionReason: string | null, signal?: AbortSignal) => mutateJson('/api/v1/cujammu/hostels/gate-pass/approve', 'POST', { passId, approve, rejectionReason }, signal),
  issueHostelCredential: async (passId: number, signal?: AbortSignal) => decodeHostelCredential(await mutateJson(`/api/v1/cujammu/hostels/gate-pass/${passId}/credential`, 'POST', {}, signal)),
  pendingHostelScanIntents: async (passId: number, signal?: AbortSignal) => decodePendingScanIntents(await requestJson(`/api/v1/cujammu/hostels/gate-pass/${passId}/scan-intents/pending`, signal)),
  myScoreCards: async (signal?: AbortSignal) => decodeScoreCards(await requestJson('/api/v1/cujammu/exams/scorecards/my', signal)),
  myAcademicReviewScopes: async (signal?: AbortSignal) => decodeReviewScopes(await requestJson('/api/v1/cujammu/exams/scorecards/review-scopes', signal)),
  myAcademicPublishScopes: async (signal?: AbortSignal) => decodeReviewScopes(await requestJson('/api/v1/cujammu/exams/scorecards/publish-scopes', signal)),
  submitScoreCard: async (command: { verifiedIdentifier: string; semester: string; academicYear: string; program: string | null; sgpa: number; cgpa: number; resultStatus: 'PASS' | 'PROMOTED' | 'REAPPEAR'; pdfUrl: null; requestKey: string; correctionReason: string | null }, signal?: AbortSignal) => mutateJson('/api/v1/cujammu/exams/scorecards/distribute', 'POST', command, signal),
  previewScoreCardBatch: async (department: string, csvContent: string, signal?: AbortSignal) => decodeAcademicBatchPreview(await mutateJson('/api/v1/cujammu/exams/scorecards/batch/preview', 'POST', { department, csvContent }, signal)),
  submitScoreCardBatch: async (department: string, csvContent: string, requestKey: string, signal?: AbortSignal) => decodeAcademicBatchReceipt(await mutateJson('/api/v1/cujammu/exams/scorecards/batch/submit', 'POST', { department, csvContent, requestKey }, signal)),
  pendingAcademicBatches: async (page = 0, signal?: AbortSignal) => decodeAcademicBatchQueue(await requestJson(`/api/v1/cujammu/exams/scorecards/batches/review?page=${page}`, signal)),
  academicBatchDetail: async (batchId: number, signal?: AbortSignal) => decodeAcademicBatchDetail(await requestJson(`/api/v1/cujammu/exams/scorecards/batches/${batchId}/review`, signal)),
  reviewAcademicBatch: async (batchId: number, expectedVersion: number, publish: boolean, reason: string | null, signal?: AbortSignal) => decodeAcademicBatchReceipt(await mutateJson(`/api/v1/cujammu/exams/scorecards/batches/${batchId}/review`, 'POST', { expectedVersion, publish, reason }, signal)),
  pendingScoreCards: async (department: string, page = 0, signal?: AbortSignal) => decodeReviewPage(await requestJson(`/api/v1/cujammu/exams/scorecards/review?department=${encodeURIComponent(department)}&page=${page}`, signal)),
  reviewScoreCard: async (id: number, publish: boolean, reason: string | null, signal?: AbortSignal) => mutateJson(`/api/v1/cujammu/exams/scorecards/${id}/review`, 'POST', { publish, reason }, signal),
  eventOperationsMine: async (signal?: AbortSignal) => decodeEvents(await requestJson('/api/v1/cujammu/events/operations/mine', signal)),
  attendanceExportEventsMine: async (page = 0, signal?: AbortSignal) => decodeEventSummaryPage(await requestJson(`/api/v1/cujammu/events/attendance-export-access/mine?page=${page}`, signal)),
  myOperatorEvents: async (signal?: AbortSignal) => decodeEvents(await requestJson('/api/v1/cujammu/events/operations/my-operator-events', signal)),
  volunteerOpportunities: async (signal?: AbortSignal) => decodeEvents(await requestJson('/api/v1/cujammu/events/operations/volunteer-opportunities', signal)),
  eventOperations: async (eventId: number, signal?: AbortSignal) => decodeOperations(await requestJson(`/api/v1/cujammu/events/${eventId}/operations`, signal)),
  eventScheduleHistory: async (eventId: number, signal?: AbortSignal) => decodeScheduleChanges(await requestJson(`/api/v1/cujammu/events/${eventId}/lifecycle/history`, signal)),
  eventRescheduleSuggestions: async (eventId: number, durationMinutes: number, days: number, signal?: AbortSignal) => decodeFacilitySuggestions(await requestJson(`/api/v1/cujammu/events/${eventId}/lifecycle/availability-suggestions?durationMinutes=${durationMinutes}&days=${days}`, signal)),
  cancelEvent: async (eventId: number, requestKeyOrSignal?: string | AbortSignal, expectedRevision?: number, signal?: AbortSignal) => {
    const requestKey = typeof requestKeyOrSignal === 'string' ? requestKeyOrSignal : newRequestKey();
    const actualSignal = typeof requestKeyOrSignal === 'string' ? signal : requestKeyOrSignal;
    const body: { requestKey: string; expectedRevision?: number } = { requestKey };
    if (expectedRevision !== undefined) body.expectedRevision = expectedRevision;
    return mutateJson(`/api/v1/cujammu/events/${eventId}/lifecycle/cancel`, 'POST', body, actualSignal);
  },
  postponeEvent: async (eventId: number, newDate: string, reason: string, requestKeyOrSignal?: string | AbortSignal, expectedRevision?: number, signal?: AbortSignal) => {
    const requestKey = typeof requestKeyOrSignal === 'string' ? requestKeyOrSignal : newRequestKey();
    const actualSignal = typeof requestKeyOrSignal === 'string' ? signal : requestKeyOrSignal;
    const body: { newDate: string; reason: string; requestKey: string; expectedRevision?: number } = { newDate, reason, requestKey };
    if (expectedRevision !== undefined) body.expectedRevision = expectedRevision;
    return mutateJson(`/api/v1/cujammu/events/${eventId}/lifecycle/postpone`, 'POST', body, actualSignal);
  },
  eventVolunteers: async (eventId: number, signal?: AbortSignal) => decodeVolunteers(await requestJson(`/api/v1/cujammu/events/${eventId}/volunteer-applications`, signal)),
  manualGrants: async (eventId: number, signal?: AbortSignal) => decodeManualGrants(await requestJson(`/api/v1/cujammu/events/${eventId}/manual-grants`, signal)),
  attendanceExportGrants: async (eventId: number, signal?: AbortSignal) => decodeManualGrants(await requestJson(`/api/v1/cujammu/events/${eventId}/attendance-export-grants`, signal)),
  operatorCapabilities: async (eventId: number, signal?: AbortSignal) => decodeOperatorCapabilities(await requestJson(`/api/v1/cujammu/events/${eventId}/my-operator-capabilities`, signal)),
  updateRecruitment: async (eventId: number, expectedRevision: number, open: boolean, maxApplicants: number, signal?: AbortSignal) => decodeOperations(await mutateJson(`/api/v1/cujammu/events/${eventId}/volunteer-recruitment`, 'PUT', { expectedRevision, open, maxApplicants }, signal)),
  updateManualWindow: async (eventId: number, expectedRevision: number, open: boolean, reason: string, signal?: AbortSignal) => decodeOperations(await mutateJson(`/api/v1/cujammu/events/${eventId}/manual-window`, 'PUT', { expectedRevision, open, reason }, signal)),
  updateScannerControl: async (eventId: number, expectedRevision: number, scope: 'OFF' | 'ALL' | 'SELECTED', actions: string[], opensAt: string | null, closesAt: string | null, signal?: AbortSignal) => decodeOperations(await mutateJson(`/api/v1/cujammu/events/${eventId}/scanner-control`, 'PUT', { expectedRevision, scope, actions, opensAt, closesAt }, signal)),
  grantScanner: async (eventId: number, userId: number, expectedRevision: number, actions: string[], signal?: AbortSignal) => mutateJson(`/api/v1/cujammu/events/${eventId}/scanner-grants/${userId}`, 'PUT', { expectedRevision, actions }, signal),
  revokeScanner: async (eventId: number, userId: number, expectedRevision: number, signal?: AbortSignal) => mutateJson(`/api/v1/cujammu/events/${eventId}/scanner-grants/${userId}/revoke`, 'PUT', { expectedRevision }, signal),
  grantManualEntry: async (eventId: number, userId: number, expectedRevision: number, expiresAt: string, signal?: AbortSignal) => mutateJson(`/api/v1/cujammu/events/${eventId}/manual-grants/${userId}`, 'PUT', { expectedRevision, actions: ['ENTRY'], reason: 'Organizer-authorized manual entry delegation', expiresAt }, signal),
  revokeManual: async (eventId: number, userId: number, expectedRevision: number, signal?: AbortSignal) => mutateJson(`/api/v1/cujammu/events/${eventId}/manual-grants/${userId}/revoke`, 'PUT', { expectedRevision }, signal),
  grantAttendanceExport: async (eventId: number, userId: number, expectedRevision: number, expiresAt: string, reason: string, signal?: AbortSignal) => mutateJson(`/api/v1/cujammu/events/${eventId}/attendance-export-grants/${userId}`, 'PUT', { expectedRevision, expiresAt, reason }, signal),
  revokeAttendanceExport: async (eventId: number, userId: number, expectedRevision: number, signal?: AbortSignal) => mutateJson(`/api/v1/cujammu/events/${eventId}/attendance-export-grants/${userId}/revoke`, 'PUT', { expectedRevision }, signal),
  manualCheckIn: async (eventId: number, verifiedIdentifier: string, reasonCode: string, idempotencyKey: string, signal?: AbortSignal) => decodeManualCheckInReceipt(await mutateJson(`/api/v1/cujammu/events/${eventId}/attendance/manual-entry`, 'POST', { verifiedIdentifier, reasonCode, idempotencyKey }, signal)),
  applyAsVolunteer: async (eventId: number, signal?: AbortSignal) => decodeVolunteers({ items: [await mutateJson(`/api/v1/cujammu/events/${eventId}/volunteer-applications`, 'POST', {}, signal)] })[0]!,
  decideVolunteer: async (eventId: number, membershipId: number, expectedRevision: number, decision: 'ACCEPT' | 'REJECT', signal?: AbortSignal) => decodeVolunteers({ items: [await mutateJson(`/api/v1/cujammu/events/${eventId}/volunteer-applications/${membershipId}/decision`, 'PUT', { expectedRevision, decision }, signal)] })[0]!,
};

export async function mutateJson(path: string, method: 'POST' | 'PUT', body: unknown, signal?: AbortSignal, expectedStatus?: 204) {
  if (isNativeApi()) return requestJson(path, signal, { method, body, token: '', expectedStatus });
  const value = await requestJson('/api/v1/session/csrf', signal);
  if (!value || typeof value !== 'object') throw new ApiError(502, 'Cannot verify this request.');
  const { token, headerName } = value as Record<string, unknown>;
  if (typeof token !== 'string' || !token || headerName !== 'X-CSRF-TOKEN') throw new ApiError(502, 'Cannot verify this request.');
  return requestJson(path, signal, { method, body, token, expectedStatus });
}

export async function signOut(): Promise<void> {
  if (isNativeApi()) { await nativeAccess().clear(); return; }
  const value = await requestJson('/api/v1/session/csrf');
  if (!value || typeof value !== 'object') throw new ApiError(502, 'Cannot verify sign-out request.');
  const { token, headerName } = value as Record<string, unknown>;
  if (typeof token !== 'string' || token.length < 1 || headerName !== 'X-CSRF-TOKEN') throw new ApiError(502, 'Cannot verify sign-out request.');
  const result = await fetch('/logout', { method: 'POST', credentials: 'same-origin', headers: { [headerName]: token }, redirect: 'follow' });
  if (!result.ok) throw new ApiError(result.status, 'Sign out could not be confirmed. Please retry.');
}
