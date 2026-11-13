import React, { useEffect, useRef, useState } from 'react';
import * as Crypto from 'expo-crypto';
import { TextInput, View, Text } from 'react-native';
import { api, ApiError } from '../api';
import { Body, Button, Heading, Panel, ThemeContext } from '../ui';
import { useContext } from 'react';
import { isCampusLocalDateTime, type ProposalCategory, type SubmitProposalCommand } from './proposals';

type Draft = { title: string; description: string; categoryId: number | null; venue: string; eventDate: string; eventEndsAt: string; maxSeats: string; fees: string; contactEmail: string; targetDepartment: string; generateCertificate: boolean };
const emptyDraft: Draft = { title: '', description: '', categoryId: null, venue: '', eventDate: '', eventEndsAt: '', maxSeats: '30', fees: '0', contactEmail: '', targetDepartment: 'ALL', generateCertificate: true };

export function ProposalSubmitForm({ onSubmitted, onSessionExpired }: { onSubmitted: () => Promise<void>; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext); const [draft, setDraft] = useState(emptyDraft); const [categories, setCategories] = useState<ProposalCategory[]>([]);
  const [loading, setLoading] = useState(true); const [saving, setSaving] = useState(false); const [uncertain, setUncertain] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState('');
  const pending = useRef<{ fingerprint: string; requestKey: string } | null>(null);
  useEffect(() => { const controller = new AbortController(); void api.eventProposalCategories(controller.signal).then(setCategories).catch(failure => { if (!controller.signal.aborted) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Proposal categories could not be loaded.'); } }).finally(() => { if (!controller.signal.aborted) setLoading(false); }); return () => controller.abort(); }, [onSessionExpired]);
  const update = <K extends keyof Draft>(key: K, value: Draft[K]) => { setDraft(current => ({ ...current, [key]: value })); setNotice(''); };
  const style = { minHeight: 46, borderWidth: 1, borderColor: colors.border, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 10, color: colors.text, backgroundColor: colors.background };
  const submit = async () => {
    if (saving || loading || !draft.categoryId || !isCampusLocalDateTime(draft.eventDate) || (draft.eventEndsAt && (!isCampusLocalDateTime(draft.eventEndsAt) || draft.eventEndsAt <= draft.eventDate)) || !Number.isInteger(Number(draft.maxSeats)) || Number(draft.maxSeats) < 1 || Number(draft.maxSeats) > 10000 || !Number.isFinite(Number(draft.fees)) || Number(draft.fees) < 0) { setError('Check the required fields, campus-local date/time, event interval, capacity and nonnegative fee.'); return; }
    const commandBase = { title: draft.title, description: draft.description, categoryId: draft.categoryId, venue: draft.venue, eventDate: `${draft.eventDate}:00`, eventEndsAt: draft.eventEndsAt ? `${draft.eventEndsAt}:00` : null, maxSeats: Number(draft.maxSeats), fees: Number(draft.fees), contactEmail: draft.contactEmail.trim() || null, targetDepartment: draft.targetDepartment.trim() || 'ALL', generateCertificate: draft.generateCertificate };
    const fingerprint = JSON.stringify(commandBase); const requestKey = pending.current?.fingerprint === fingerprint ? pending.current.requestKey : Crypto.randomUUID();
    pending.current = { fingerprint, requestKey }; const command: SubmitProposalCommand = { ...commandBase, requestKey };
    setSaving(true); setError(''); setNotice('');
    try { const receipt = await api.submitEventProposal(command); pending.current = null; setUncertain(false); setNotice(receipt.replayed ? `Submission confirmed from its saved receipt (proposal ${receipt.id}, ${receipt.status.replaceAll('_', ' ')}).` : `Proposal ${receipt.id} submitted for review.`); setDraft(emptyDraft); void onSubmitted().catch(() => setError('Submission is confirmed, but the event list could not refresh. Use Refresh events to reload it.')); }
    catch (failure) { const isUncertain = !(failure instanceof ApiError) || failure.status === 0 || failure.status >= 500; if (isUncertain) setUncertain(true); if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? isUncertain ? `${failure.message} Retry unchanged to recover the saved receipt.` : failure.message : 'Proposal submission could not be confirmed. Retry unchanged to recover safely.'); }
    finally { setSaving(false); }
  };
  const editable = !saving && !uncertain;
  return <Panel><Heading>Submit an event proposal</Heading><Body>Send an event for assigned administrator review. Approval and public publication are separate decisions.</Body>
    {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}{notice ? <Text accessibilityRole="status" style={{ color: colors.text }}>{notice}</Text> : null}
    {uncertain ? <Body>The server response is uncertain. Form fields are locked so retry uses the original request key and data.</Body> : null}
    <Body>Event title *</Body><TextInput accessibilityLabel="Proposal title" maxLength={200} value={draft.title} editable={editable} onChangeText={value => update('title', value)} style={style} />
    <Body>Description *</Body><TextInput accessibilityLabel="Proposal description" multiline maxLength={10000} value={draft.description} editable={editable} onChangeText={value => update('description', value)} style={[style, { minHeight: 110, textAlignVertical: 'top' }]} />
    <Body>Category *</Body>{loading ? <Body>Loading approved categories…</Body> : categories.length === 0 ? <Body>No categories are available; contact the event office.</Body> : <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>{categories.map(category => <Button key={category.id} label={draft.categoryId === category.id ? `Selected: ${category.name}` : category.name} secondary disabled={!editable} onPress={() => update('categoryId', category.id)} />)}</View>}
    <Body>Venue *</Body><TextInput accessibilityLabel="Proposal venue" maxLength={200} value={draft.venue} editable={editable} onChangeText={value => update('venue', value)} style={style} />
    <Body>Start time * (campus local time, YYYY-MM-DDTHH:mm)</Body><TextInput accessibilityLabel="Proposal start date and time" placeholder="2035-04-02T10:00" value={draft.eventDate} editable={editable} onChangeText={value => update('eventDate', value)} style={style} />
    <Body>End time (optional; same format)</Body><TextInput accessibilityLabel="Proposal end date and time" placeholder="2035-04-02T12:00" value={draft.eventEndsAt} editable={editable} onChangeText={value => update('eventEndsAt', value)} style={style} />
    <Body>Maximum seats *</Body><TextInput accessibilityLabel="Proposal capacity" keyboardType="number-pad" value={draft.maxSeats} editable={editable} onChangeText={value => update('maxSeats', value)} style={style} />
    <Body>Fee (INR; use 0 for no fee)</Body><TextInput accessibilityLabel="Proposal fee" keyboardType="decimal-pad" value={draft.fees} editable={editable} onChangeText={value => update('fees', value)} style={style} />
    <Body>Target department (ALL or approved department code)</Body><TextInput accessibilityLabel="Proposal target department" maxLength={160} value={draft.targetDepartment} editable={editable} onChangeText={value => update('targetDepartment', value)} style={style} />
    <Body>Contact email (optional)</Body><TextInput accessibilityLabel="Proposal contact email" keyboardType="email-address" autoCapitalize="none" maxLength={254} value={draft.contactEmail} editable={editable} onChangeText={value => update('contactEmail', value)} style={style} />
    <Button label={draft.generateCertificate ? 'Certificate requested — toggle off' : 'No certificate requested — toggle on'} secondary disabled={!editable} onPress={() => update('generateCertificate', !draft.generateCertificate)} />
    <Body>Banner and certificate template uploads are not supported in this shared form yet. Submit only the proposal details available here.</Body>
    <Button actionId="organizer.event-proposals.submit" label={saving ? 'Submitting proposal…' : uncertain ? 'Retry proposal submission' : 'Submit proposal for review'} disabled={saving || loading || categories.length === 0} onPress={() => void submit()} />
  </Panel>;
}
