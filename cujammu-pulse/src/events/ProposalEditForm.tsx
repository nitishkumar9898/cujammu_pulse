import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Text, TextInput, View } from 'react-native';
import { api, ApiError } from '../api';
import { Body, Button, Heading, Panel, ThemeContext } from '../ui';
import { ProposalMediaPreview } from './ProposalMediaPreview';
import { ProposalAttachmentForm } from './ProposalAttachmentForm';
import type { ProposalMediaKind } from './proposals';
import type { ProposalAttachmentReceipt } from './proposal-attachments';
import { useContext } from 'react';
import { isCampusLocalDateTime, type EditableProposal, type ProposalCategory, type UpdateProposalCommand } from './proposals';

type Draft = { title: string; description: string; categoryId: number | null; venue: string; eventDate: string; eventEndsAt: string; maxSeats: string; fees: string; contactEmail: string; targetDepartment: string; generateCertificate: boolean };
const localInput = (value: string | null) => value ? value.slice(0, 16) : '';

export function ProposalEditForm({ proposalId, onCancel, onSaved, onSessionExpired }: { proposalId: number; onCancel: () => void; onSaved: () => Promise<void>; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext); const [proposal, setProposal] = useState<EditableProposal | null>(null); const [categories, setCategories] = useState<ProposalCategory[]>([]);
  const lease = useRef<ProposalMediaKind | 'text' | null>(null);
  const loadRequest = useRef<AbortController | null>(null);
  const [attachmentOwner, setAttachmentOwner] = useState<ProposalMediaKind | null>(null);
  const acquireAttachment = (kind: ProposalMediaKind) => {
    if (lease.current !== null) return false;
    lease.current = kind; setAttachmentOwner(kind); return true;
  };
  const releaseAttachment = (kind: ProposalMediaKind) => {
    if (lease.current === kind) { lease.current = null; setAttachmentOwner(null); }
  };
  const uploaded = async (receipt: ProposalAttachmentReceipt) => {
    setProposal(current => current ? { ...current, revision: receipt.revision, hasBanner: current.hasBanner || receipt.kind === 'banner', hasCertificateTemplate: current.hasCertificateTemplate || receipt.kind === 'certificate-template' } : current);
    setNotice(`Attachment saved. Available until ${receipt.expiresAt}. Administrator review is required again.`);
    try { await onSaved(); } catch { setError('Attachment saved, but the proposal list could not refresh. Reload the list.'); }
  };
  const [draft, setDraft] = useState<Draft | null>(null); const [loading, setLoading] = useState(true); const [saving, setSaving] = useState(false); const [uncertain, setUncertain] = useState(false); const [reloadRequired, setReloadRequired] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState('');
  const load = useCallback(async () => {
    loadRequest.current?.abort();
    const controller = new AbortController(); loadRequest.current = controller;
    setLoading(true); setError('');
    try {
      const [details, choices] = await Promise.all([api.editableEventProposal(proposalId, controller.signal), api.eventProposalCategories(controller.signal)]);
      if (controller.signal.aborted) return;
      setProposal(details); setCategories(choices); setDraft({ title: details.title, description: details.description, categoryId: details.categoryId, venue: details.venue, eventDate: localInput(details.eventDate), eventEndsAt: localInput(details.eventEndsAt), maxSeats: String(details.maxSeats), fees: String(details.fees), contactEmail: details.contactEmail ?? '', targetDepartment: details.targetDepartment, generateCertificate: details.generateCertificate }); setUncertain(false); setReloadRequired(false); setNotice('');
    } catch (failure) { if (controller.signal.aborted) return; if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Proposal could not be loaded for editing.'); }
    finally { if (!controller.signal.aborted) setLoading(false); }
  }, [onSessionExpired, proposalId]);
  useEffect(() => { void load(); return () => loadRequest.current?.abort(); }, [load]);
  const update = <K extends keyof Draft>(key: K, value: Draft[K]) => { setDraft(current => current ? { ...current, [key]: value } : current); setNotice(''); };
  const style = { minHeight: 46, borderWidth: 1, borderColor: colors.border, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 10, color: colors.text, backgroundColor: colors.background };
  const submit = async () => {
    if (lease.current !== null) return;
    if (!proposal || !draft || saving || !draft.categoryId || !isCampusLocalDateTime(draft.eventDate) || (draft.eventEndsAt && (!isCampusLocalDateTime(draft.eventEndsAt) || draft.eventEndsAt <= draft.eventDate)) || !Number.isInteger(Number(draft.maxSeats)) || Number(draft.maxSeats) < 1 || Number(draft.maxSeats) > 10000 || !Number.isFinite(Number(draft.fees)) || Number(draft.fees) < 0) { setError('Check required fields, campus-local date/time, event interval, capacity and nonnegative fee.'); return; }
    const command: UpdateProposalCommand = { expectedRevision: proposal.revision, title: draft.title, description: draft.description, categoryId: draft.categoryId, venue: draft.venue, eventDate: `${draft.eventDate}:00`, eventEndsAt: draft.eventEndsAt ? `${draft.eventEndsAt}:00` : null, maxSeats: Number(draft.maxSeats), fees: Number(draft.fees), contactEmail: draft.contactEmail.trim() || null, targetDepartment: draft.targetDepartment.trim() || 'ALL', generateCertificate: draft.generateCertificate };
    lease.current = 'text'; setSaving(true); setError(''); setNotice('');
    try { const updated = await api.updateEventProposal(proposal.id, command); setProposal(updated); setNotice('Proposal updated and returned to the administrator review queue.'); await onSaved(); }
    catch (failure) { if (!(failure instanceof ApiError) || failure.status === 0 || failure.status >= 500) setUncertain(true); if (failure instanceof ApiError && failure.status === 409) setReloadRequired(true); if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Proposal update could not be confirmed. Reload the proposal before continuing.'); }
    finally { lease.current = null; setSaving(false); }
  };
  const editable = !!draft && !loading && !saving && !uncertain && !reloadRequired && attachmentOwner === null;
  if (loading) return <Panel><Heading>Edit event proposal</Heading><Body>Loading the current revision…</Body></Panel>;
  return <Panel><Heading>Edit event proposal</Heading><Body>Update your proposal and submit it again for administrator review. Current revision: {proposal?.revision ?? 'unavailable'}.</Body>
    {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}{notice ? <Text accessibilityRole="status" style={{ color: colors.text }}>{notice}</Text> : null}
    {uncertain ? <Body>The update response was uncertain. Reload the saved proposal to see its authoritative revision before editing again.</Body> : reloadRequired ? <Body>This proposal changed after you opened it. Reload the current revision before making another edit.</Body> : null}
    {draft ? <>
      <Body>Event title *</Body><TextInput accessibilityLabel="Edit proposal title" maxLength={200} value={draft.title} editable={editable} onChangeText={value => update('title', value)} style={style} />
      <Body>Description *</Body><TextInput accessibilityLabel="Edit proposal description" multiline maxLength={10000} value={draft.description} editable={editable} onChangeText={value => update('description', value)} style={[style, { minHeight: 110, textAlignVertical: 'top' }]} />
      <Body>Category *</Body><View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>{categories.map(category => <Button key={category.id} label={draft.categoryId === category.id ? `Selected: ${category.name}` : category.name} secondary disabled={!editable} onPress={() => update('categoryId', category.id)} />)}</View>
      <Body>Venue *</Body><TextInput accessibilityLabel="Edit proposal venue" maxLength={200} value={draft.venue} editable={editable} onChangeText={value => update('venue', value)} style={style} />
      <Body>Start time * (campus local time, YYYY-MM-DDTHH:mm)</Body><TextInput accessibilityLabel="Edit proposal start date and time" placeholder="2035-04-02T10:00" value={draft.eventDate} editable={editable} onChangeText={value => update('eventDate', value)} style={style} />
      <Body>End time (optional; same format)</Body><TextInput accessibilityLabel="Edit proposal end date and time" placeholder="2035-04-02T12:00" value={draft.eventEndsAt} editable={editable} onChangeText={value => update('eventEndsAt', value)} style={style} />
      <Body>Maximum seats *</Body><TextInput accessibilityLabel="Edit proposal capacity" keyboardType="number-pad" value={draft.maxSeats} editable={editable} onChangeText={value => update('maxSeats', value)} style={style} />
      <Body>Fee (INR; use 0 for no fee)</Body><TextInput accessibilityLabel="Edit proposal fee" keyboardType="decimal-pad" value={draft.fees} editable={editable} onChangeText={value => update('fees', value)} style={style} />
      <Body>Target department (ALL or approved department code)</Body><TextInput accessibilityLabel="Edit proposal target department" maxLength={160} value={draft.targetDepartment} editable={editable} onChangeText={value => update('targetDepartment', value)} style={style} />
      <Body>Contact email (optional)</Body><TextInput accessibilityLabel="Edit proposal contact email" keyboardType="email-address" autoCapitalize="none" maxLength={254} value={draft.contactEmail} editable={editable} onChangeText={value => update('contactEmail', value)} style={style} />
      <Button label={draft.generateCertificate ? 'Certificate requested — toggle off' : 'No certificate requested — toggle on'} secondary disabled={!editable} onPress={() => update('generateCertificate', !draft.generateCertificate)} />
      {proposal?.hasBanner ? <ProposalMediaPreview key={`banner:${proposal.revision}`} uri={api.eventProposalMediaPath('organizer', proposal.id, 'banner')} label="Current banner preview" onSessionExpired={onSessionExpired} /> : null}
      {proposal?.hasCertificateTemplate ? <ProposalMediaPreview key={`template:${proposal.revision}`} uri={api.eventProposalMediaPath('organizer', proposal.id, 'certificate-template')} label="Current certificate template preview" onSessionExpired={onSessionExpired} /> : null}
      {proposal ? (['banner', 'certificate-template'] as const).map(kind => <ProposalAttachmentForm key={`${proposal.id}:${kind}`} proposalId={proposal.id} revision={proposal.revision} kind={kind}
        disabled={loading || saving || uncertain || reloadRequired || (attachmentOwner !== null && attachmentOwner !== kind)}
        acquire={() => acquireAttachment(kind)} release={() => releaseAttachment(kind)} onUploaded={uploaded}
        onSessionExpired={onSessionExpired} onReloadRequired={() => setReloadRequired(true)} />) : null}
    </> : null}
    <Button actionId="organizer.event-proposals.update" label={saving ? 'Saving proposal…' : 'Save and resubmit'} disabled={!editable || categories.length === 0} onPress={() => void submit()} />
    {uncertain || reloadRequired ? <Button actionId="organizer.event-proposals.edit.read" label="Reload proposal revision" secondary disabled={loading || saving} onPress={() => void load()} /> : null}
    <Button label="Close editor" secondary disabled={saving || attachmentOwner !== null} onPress={onCancel} />
  </Panel>;
}
