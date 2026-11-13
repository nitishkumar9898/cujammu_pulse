import React, { useContext, useEffect, useRef, useState } from 'react';
import { Text, TextInput, View } from 'react-native';
import { api, ApiError } from '../api';
import { Body, Button, Heading, Panel, ThemeContext } from '../ui';
import { ProposalMediaPreview } from '../events/ProposalMediaPreview';
import type { EventProposal, Reviewer, ReviewPage } from './event-proposal-review';

export function EventProposalReviewScreen({ onSessionExpired }: { onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [queue, setQueue] = useState<ReviewPage<EventProposal> | null>(null);
  const [queueMode, setQueueMode] = useState<'review' | 'publication'>('review');
  const [directory, setDirectory] = useState<ReviewPage<Reviewer> | null>(null);
  const [selected, setSelected] = useState<EventProposal | null>(null);
  const [reviewer, setReviewer] = useState<Reviewer | null>(null);
  const [reason, setReason] = useState(''); const [confirmed, setConfirmed] = useState(false);
  const [loading, setLoading] = useState(false); const [directoryLoading, setDirectoryLoading] = useState(false); const [saving, setSaving] = useState(false);
  const [error, setError] = useState(''); const [notice, setNotice] = useState('');
  const queueRequest = useRef<AbortController | null>(null); const directoryRequest = useRef<AbortController | null>(null); const mutationRequest = useRef<AbortController | null>(null); const mutationLock = useRef(false);
  const busy = loading || directoryLoading || saving;
  const clearSelection = () => { setSelected(null); setReviewer(null); setDirectory(null); setReason(''); setConfirmed(false); directoryRequest.current?.abort(); setDirectoryLoading(false); };
  const clearPrivate = () => { clearSelection(); setQueue(null); setNotice(''); };
  const fail = (failure: unknown, uncertain = false) => {
    if (failure instanceof ApiError && failure.status === 401) { clearPrivate(); onSessionExpired(); return; }
    if (failure instanceof ApiError && failure.status === 403) { clearPrivate(); setError('Review access is unavailable. Reload the queue after your access is restored.'); return; }
    if (uncertain) { clearPrivate(); setError('The change was not confirmed. Reload the queue to recover the current server state before submitting another request.'); }
    else setError(failure instanceof ApiError ? failure.message : 'The review data could not be verified. Reload the queue.');
  };
  async function loadQueue(page = 0, mode = queueMode) {
    if (mutationLock.current) return;
    queueRequest.current?.abort(); const controller = new AbortController(); queueRequest.current = controller;
    clearPrivate(); setError(''); setLoading(true);
    try { const result = mode === 'publication' ? await api.eventProposalPublicationQueue(page, controller.signal) : await api.eventProposalQueue(page, controller.signal); if (!controller.signal.aborted) { setQueue(result); setQueueMode(mode); } }
    catch (failure) { if (!controller.signal.aborted) fail(failure); }
    finally { if (!controller.signal.aborted) setLoading(false); }
  }
  async function loadReviewers(page = 0) {
    if (mutationLock.current) return;
    directoryRequest.current?.abort(); const controller = new AbortController(); directoryRequest.current = controller;
    setDirectory(null); setReviewer(null); setError(''); setDirectoryLoading(true);
    try { const result = await api.eventProposalReviewers(page, controller.signal); if (!controller.signal.aborted) setDirectory(result); }
    catch (failure) { if (!controller.signal.aborted) { clearSelection(); fail(failure); } }
    finally { if (!controller.signal.aborted) setDirectoryLoading(false); }
  }
  useEffect(() => { void loadQueue(); return () => { queueRequest.current?.abort(); directoryRequest.current?.abort(); mutationRequest.current?.abort(); }; }, []);
  function choose(item: EventProposal) { if (busy || mutationLock.current) return; clearSelection(); setSelected(item); setNotice(''); setError(''); if (item.canAssign) void loadReviewers(); }
  async function mutate(kind: 'assign' | 'approve' | 'reject' | 'publish') {
    if (!selected || busy || mutationLock.current || (kind === 'assign' ? !selected.canAssign || !reviewer : kind === 'publish' ? !selected.canPublish : !selected.canDecide) || ((kind === 'approve' || kind === 'publish') && !confirmed) || (kind === 'reject' && !reason.trim())) return;
    mutationLock.current = true; setSaving(true); setError(''); setNotice('');
    const controller = new AbortController(); mutationRequest.current = controller;
    try {
      if (kind === 'assign') {
        const result = await api.assignEventProposalReviewer(selected.id, { expectedRevision: selected.revision, reviewerId: reviewer!.id }, controller.signal);
        if (!controller.signal.aborted) { setSelected(result); setQueue(value => value ? { ...value, items: value.items.map(item => item.id === result.id ? result : item) } : value); setReviewer(null); setConfirmed(false); setReason(''); setNotice('Reviewer assignment saved.'); }
      } else if (kind === 'publish') {
        await api.publishEventProposal(selected.id, selected.revision, controller.signal);
        if (!controller.signal.aborted) { clearPrivate(); setNotice('Publication confirmed. The event is now public and its approved notices have been queued.'); }
      } else {
        await api.decideEventProposal(selected.id, selected.revision, kind, reason.trim(), controller.signal);
        if (!controller.signal.aborted) { clearPrivate(); setNotice(kind === 'approve' ? 'Proposal approved. It remains private until a separate administrator publishes it.' : 'Proposal rejection confirmed. Reload the queue to continue.'); }
      }
    } catch (failure) { if (!controller.signal.aborted) fail(failure, true); }
    finally { mutationLock.current = false; if (!controller.signal.aborted) setSaving(false); }
  }
  const input = { minHeight: 100, borderWidth: 1, borderColor: colors.border, borderRadius: 10, padding: 12, color: colors.text, backgroundColor: colors.background };
  return <View style={{ gap: 16 }}>
    <Panel><Heading>Event proposal review</Heading><Body>Assign an active administrator, review the submitted details, and record a decision. Only the assigned reviewer can decide; organizers cannot review their own proposal.</Body>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        <Button label="Review proposals" secondary disabled={busy || queueMode === 'review'} onPress={() => void loadQueue(0, 'review')} />
        <Button label="Ready to publish" secondary disabled={busy || queueMode === 'publication'} onPress={() => void loadQueue(0, 'publication')} />
        <Button label={loading ? 'Loading queue…' : 'Reload queue'} actionId={queueMode === 'publication' ? 'admin.event-proposals.publication.read' : 'admin.event-proposals.read'} disabled={busy} onPress={() => void loadQueue(queue?.page ?? 0)} />
      </View>
      {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
      {notice ? <Text accessibilityRole="alert" style={{ color: colors.text }}>{notice}</Text> : null}
      {queue ? <><Body>{queueMode === 'publication' ? 'Approved, private proposals ready for publication' : 'Pending proposals'} · page {queue.page + 1}</Body>{queue.items.length === 0 ? <Body>{queueMode === 'publication' ? 'No approved proposals are waiting for publication.' : 'No pending proposals on this page.'}</Body> : null}
        {queue.items.map(item => <Button key={item.id} label={`Review ${item.title}`} secondary disabled={busy} onPress={() => choose(item)} />)}
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}><Button label="Previous proposals" secondary actionId="admin.event-proposals.read" disabled={busy || queue.page === 0} onPress={() => void loadQueue(queue.page - 1)} /><Button label="Next proposals" secondary actionId="admin.event-proposals.read" disabled={busy || !queue.hasNext} onPress={() => void loadQueue(queue.page + 1)} /></View>
      </> : null}
    </Panel>
    {selected ? <Panel><Heading>{selected.title}</Heading><Body>Submitted by {selected.organizerName}</Body><Body>{selected.description}</Body><Body>Venue: {selected.venue}</Body><Body>Starts: {selected.eventDate.replace('T', ' ')} · campus time</Body><Body>{selected.eventEndsAt ? `Ends: ${selected.eventEndsAt.replace('T', ' ')} · campus time` : 'End time not supplied'}</Body>
      <Body>Category: {selected.categoryName ?? 'Not supplied'}</Body><Body>Capacity: {selected.maxSeats} places</Body><Body>Fees: {selected.fees === 0 ? 'No fee' : `INR ${selected.fees.toLocaleString('en-IN')}`}</Body><Body>Target audience: {selected.targetDepartment === 'ALL' ? 'All eligible students' : selected.targetDepartment || 'Not configured'}</Body>
      <Body>Certificates requested: {selected.generateCertificate ? 'Yes' : 'No'}</Body><Body>Banner attachment recorded: {selected.hasBanner ? 'Yes' : 'No'} · certificate template recorded: {selected.hasCertificateTemplate ? 'Yes' : 'No'}</Body>
      {selected.hasBanner ? <ProposalMediaPreview uri={api.eventProposalMediaPath('admin', selected.id, 'banner')} label="Proposal banner preview" onSessionExpired={onSessionExpired} /> : null}
      {selected.hasCertificateTemplate ? <ProposalMediaPreview uri={api.eventProposalMediaPath('admin', selected.id, 'certificate-template')} label="Certificate template preview" onSessionExpired={onSessionExpired} /> : null}
      <Body>{selected.assignedReviewerId === null ? 'No reviewer assigned.' : `Assigned reviewer: ${selected.assignedReviewerName ?? 'Unavailable account'}${selected.assignedReviewerActive ? '' : ' (inactive — reassign before review)'}`}</Body>
      {selected.canAssign ? <><Heading>Reviewer assignment</Heading><Body>Select an active administrator from the directory.</Body>
        {directoryLoading ? <Body>Loading reviewers…</Body> : null}
        {directory ? <><Body>Reviewers · page {directory.page + 1}</Body>{directory.items.length === 0 ? <Body>No active reviewers on this page.</Body> : null}
          {directory.items.map(person => <Button key={person.id} label={`${reviewer?.id === person.id ? 'Selected reviewer: ' : 'Select reviewer: '}${person.name}`} secondary disabled={busy} onPress={() => setReviewer(person)} />)}
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}><Button label="Previous reviewers" secondary actionId="admin.event-proposals.reviewers" disabled={busy || directory.page === 0} onPress={() => void loadReviewers(directory.page - 1)} /><Button label="Next reviewers" secondary actionId="admin.event-proposals.reviewers" disabled={busy || !directory.hasNext} onPress={() => void loadReviewers(directory.page + 1)} /></View>
        </> : null}
        <Button label="Save reviewer assignment" actionId="admin.event-proposals.assign" disabled={busy || !reviewer} onPress={() => void mutate('assign')} />
      </> : <Body>Reviewer assignment is not available for this proposal.</Body>}
      {selected.canDecide ? <><Heading>Review decision</Heading><Body>Approval records the review decision. The event stays private and no audience announcement is sent until a separate administrator publication decision. Check the complete proposal before confirming.</Body>
        <Button label={confirmed ? 'Approval review confirmed — change confirmation' : 'I reviewed the proposal and confirm approval'} secondary disabled={busy} onPress={() => setConfirmed(value => !value)} />
        <Button label="Approve proposal" actionId="admin.event-proposals.approve" disabled={busy || !confirmed} onPress={() => void mutate('approve')} />
        <Body>Rejection reason</Body><TextInput accessibilityLabel="Proposal rejection reason" value={reason} editable={!busy} multiline maxLength={500} style={input} onChangeText={value => { setReason(value); setConfirmed(false); }} />
        <Button label="Reject proposal" actionId="admin.event-proposals.reject" disabled={busy || !reason.trim()} onPress={() => void mutate('reject')} />
      </> : selected.canPublish ? <><Heading>Publish approved event</Heading><Body>Publishing makes the event publicly discoverable, creates its operations workspace, and queues the approved organizer and audience notices. Confirm the visibility and target audience first.</Body>
        <Button label={confirmed ? 'Publication review confirmed — change confirmation' : 'I reviewed the visibility and confirm publication'} secondary disabled={busy} onPress={() => setConfirmed(value => !value)} />
        <Button label="Publish event" actionId="admin.event-proposals.publish" disabled={busy || !confirmed} onPress={() => void mutate('publish')} />
      </> : <Body>{selected.status === 'APPROVED_PENDING_PUBLICATION' ? 'Publication is unavailable to the current administrator.' : 'Decisions are available only to the active assigned reviewer for an eligible proposal.'}</Body>}
    </Panel> : null}
  </View>;
}
