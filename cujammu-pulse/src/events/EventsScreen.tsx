import React, { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, AppState, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import { AttendanceQr } from '../attendance/AttendanceQr';
import type { AttendanceCredential } from '../attendance/contracts';
import type { EventCard, Registration } from './contracts';
import type { OwnProposalPage } from './proposals';
import { Body, Button, Heading, Panel, ThemeContext } from '../ui';
import { ClashCheckPanel } from '../calendar/ClashCheckPanel';
import { campusDateTime } from '../calendar/date';
import { ProposalSubmitForm } from './ProposalSubmitForm';
import { ProposalEditForm } from './ProposalEditForm';

export function EventsScreen({ onSessionExpired, canCheckClashes = false, role }: { onSessionExpired: () => void; canCheckClashes?: boolean; role?: string }) {
  const colors = useContext(ThemeContext);
  const [events, setEvents] = useState<EventCard[]>([]); const [mine, setMine] = useState<Registration[]>([]);
  const [proposals, setProposals] = useState<OwnProposalPage | null>(null); const [proposalPage, setProposalPage] = useState(0);
  const [editingProposalId, setEditingProposalId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true); const [busy, setBusy] = useState<number | null>(null);
  const [withdrawal, setWithdrawal] = useState<Registration | null>(null); const [error, setError] = useState('');
  const [pass, setPass] = useState<{ eventId: number; registrationId: number; eventTitle: string; credential: AttendanceCredential } | null>(null);
  const [passError, setPassError] = useState(''); const [secondsLeft, setSecondsLeft] = useState(0);
  const passRequestGeneration = useRef(0);
  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true); setError('');
    try { const [catalog, registrations, ownProposals] = await Promise.all([api.eventCatalog(signal), api.myRegistrations(signal), role === 'FACULTY' || role === 'ORGANIZER' ? api.myEventProposals(proposalPage, signal) : Promise.resolve(null)]); if (!signal?.aborted) { setEvents(catalog); setMine(registrations); setProposals(ownProposals); setPass(current => current && registrations.some(item => item.id === current.registrationId && item.eventId === current.eventId && item.status === 'REGISTERED') ? current : null); } }
    catch (e) { if (!signal?.aborted) { setEvents([]); setMine([]); setProposals(null); setPass(null); if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Events could not be loaded.'); } }
    finally { if (!signal?.aborted) setLoading(false); }
  }, [onSessionExpired, role, proposalPage]);
  useEffect(() => { const controller = new AbortController(); void load(controller.signal); return () => controller.abort(); }, [load]);
  useEffect(() => {
    if (!pass) { setSecondsLeft(0); return; }
    const update = () => {
      const remaining = Math.max(0, Math.ceil((Date.parse(pass.credential.expiresAt) - Date.now()) / 1000));
      setSecondsLeft(remaining);
      if (remaining === 0) setPass(null);
    };
    update(); const timer = setInterval(update, 1000); return () => clearInterval(timer);
  }, [pass]);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => { if (state !== 'active') { passRequestGeneration.current++; setPass(null); } });
    return () => subscription.remove();
  }, []);
  const act = async (eventId: number, work: () => Promise<unknown>) => { setBusy(eventId); setPass(null); passRequestGeneration.current++; setError(''); try { await work(); setWithdrawal(null); await load(); } catch (e) { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Registration could not be updated.'); } finally { setBusy(null); } };
  const showEntryPass = async (event: EventCard, registration: Registration) => {
    const generation = ++passRequestGeneration.current;
    setPass(null); setPassError(''); setBusy(event.id);
    try {
      const credential = await api.issueAttendanceCredential(event.id, registration.id, 'ENTRY');
      if (generation === passRequestGeneration.current && AppState.currentState === 'active' && Date.parse(credential.expiresAt) > Date.now()) setPass({ eventId: event.id, registrationId: registration.id, eventTitle: event.title, credential });
      else if (generation === passRequestGeneration.current) setPassError('The pass expired before it could be displayed. Request a fresh pass.');
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) onSessionExpired();
      else setPassError(e instanceof Error ? e.message : 'An entry pass is not available for this event.');
    } finally { setBusy(null); }
  };
  return <View style={{ gap: 14 }}><Panel><Heading>Campus events</Heading><Body>Approved events available for your account are shown here.</Body>{error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}<Button actionId="events.catalog.refresh" label="Refresh events" secondary disabled={loading || busy !== null} onPress={() => void load()} /></Panel>
    {role === 'FACULTY' || role === 'ORGANIZER' ? <ProposalSubmitForm onSubmitted={() => load()} onSessionExpired={onSessionExpired} /> : null}
    {role === 'FACULTY' || role === 'ORGANIZER' ? <Panel><Heading>My event proposals</Heading><Body>Private status view for proposals submitted by your university account.</Body><Button actionId="organizer.event-proposals.mine.read" label="Refresh proposal status" secondary disabled={loading} onPress={() => void load()} />
      {!proposals || proposals.items.length === 0 ? <Body>No event proposals are recorded for this account.</Body> : proposals.items.map(proposal => <View key={proposal.id} style={{ gap: 4, paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: colors.border }}><Body>{proposal.title} · {proposal.status.replaceAll('_', ' ')}</Body><Body>{proposal.venue} · {campusDateTime(proposal.eventDate)} (IST) · revision {proposal.revision}</Body>{proposal.status === 'REJECTED' && proposal.rejectionReason ? <Body>Reviewer note: {proposal.rejectionReason}</Body> : null}{['PENDING', 'REJECTED'].includes(proposal.status) ? <Button actionId="organizer.event-proposals.edit.read" label={editingProposalId === proposal.id ? 'Editing proposal' : 'Edit and resubmit'} secondary disabled={loading || editingProposalId !== null} onPress={() => setEditingProposalId(proposal.id)} /> : null}</View>)}
      {proposals ? <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}><Button label="Previous proposals" secondary disabled={loading || proposalPage === 0} onPress={() => setProposalPage(value => value - 1)} /><Button label="Next proposals" secondary disabled={loading || !proposals.hasNext} onPress={() => setProposalPage(value => value + 1)} /></View> : null}
    </Panel> : null}
    {editingProposalId !== null && (role === 'FACULTY' || role === 'ORGANIZER') ? <ProposalEditForm key={editingProposalId} proposalId={editingProposalId} onCancel={() => setEditingProposalId(null)} onSaved={() => load()} onSessionExpired={onSessionExpired} /> : null}
    {loading ? <ActivityIndicator accessibilityLabel="Loading events" color={colors.accent} /> : error && events.length === 0 ? null : events.length === 0 ? <Body>No events are available for your account.</Body> : events.map(event => {
      const registration = mine.find(item => item.eventId === event.id);
      const canWithdraw = registration && ['REGISTERED', 'WAITLISTED'].includes(registration.status);
      return <Panel key={event.id}><Heading>{event.title}</Heading><Body>{event.description}</Body><Body>{event.venue} · {campusDateTime(event.eventDate)} (IST)</Body><Body>{event.targetDepartment} · Seats {event.maxSeats}</Body>{registration ? <Body>Registration: {registration.status}</Body> : null}
        {canCheckClashes && !registration ? <ClashCheckPanel key={`${event.id}:${event.eventDate}:${event.venue}`} eventId={event.id} onSessionExpired={onSessionExpired} /> : null}
        <Button actionId="events.registration.create" label={registration ? registration.status === 'CANCELLED' ? 'Cancelled' : 'Registered' : busy === event.id ? 'Registering…' : 'Register'} disabled={!!registration || busy !== null} onPress={() => void act(event.id, () => api.registerForEvent(event.id))} />
        {registration?.status === 'REGISTERED' ? <Button actionId="attendance.credential.issue" label={busy === event.id ? 'Preparing pass…' : 'Show entry QR pass'} secondary disabled={busy !== null} onPress={() => void showEntryPass(event, registration)} /> : null}
        {canWithdraw ? <Button label="Withdraw registration" secondary disabled={busy !== null} onPress={() => setWithdrawal(registration)} /> : null}
        {withdrawal?.id === registration?.id && registration ? <View accessibilityRole="alert" style={{ gap: 8 }}><Body>Withdraw your registration for {event.title}? Your seat or waitlist place will be released.</Body><Button label="Keep registration" secondary disabled={busy !== null} onPress={() => setWithdrawal(null)} /><Button actionId="events.registration.cancel" label={busy === event.id ? 'Withdrawing…' : 'Confirm withdrawal'} disabled={busy !== null} onPress={() => void act(event.id, () => api.cancelEventRegistration(registration.id))} /></View> : null}
      </Panel>;
    })}
    {passError ? <Panel><Text accessibilityRole="alert" style={{ color: colors.error }}>{passError}</Text></Panel> : null}
    {pass ? <Panel><View accessibilityLabel="Temporary event entry pass" style={{ gap: 14 }}><Heading>{pass.eventTitle} entry pass</Heading><Body>Show this QR to the event scanner. It expires automatically in {secondsLeft} seconds. A screenshot or stale code will not remain valid.</Body><View style={{ alignItems: 'center', padding: 8 }}><AttendanceQr value={pass.credential.credential} label="Short-lived entry QR code" /></View><Body>Keep this screen open while the scanner verifies your pass. The server checks current event and registration eligibility.</Body><Button actionId="attendance.credential.hide" label="Hide pass now" secondary onPress={() => { passRequestGeneration.current++; setPass(null); }} /></View></Panel> : null}
  </View>;
}
