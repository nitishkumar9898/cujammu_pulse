import React, { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Platform, Pressable, Text, TextInput, View } from 'react-native';
import { api, ApiError, newRequestKey } from '../api';
import type { FeatureFlags, Session } from '../contracts';
import { Body, Button, Field, Heading, Panel, ThemeContext } from '../ui';
import type { FacilitySuggestions } from '../contracts';
import type { EventSummary, ManualGrant, Operations, ScheduleChange, Volunteer } from './contracts';
import { ReadinessScreen } from '../readiness/ReadinessScreen';
import { CertificatePolicyPanel } from '../certificates/CertificatePolicyPanel';
import { MyVolunteerApplicationsPanel } from './MyVolunteerApplicationsPanel';
import { shareAttendanceExport, type AttendanceExportKind } from './native-attendance-export';

const manualReasons = [['PHONE_UNAVAILABLE', 'Phone unavailable'], ['QR_UNAVAILABLE', 'QR unavailable'], ['ACCESSIBILITY_SUPPORT', 'Accessibility support'], ['DEVICE_FAILURE', 'Device failure'], ['OTHER', 'Other approved reason']] as const;
const localDateTimeAfter = (milliseconds: number) => { const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).formatToParts(new Date(Date.now() + milliseconds)); const value = Object.fromEntries(parts.map(part => [part.type, part.value])); return `${value.year}-${value.month}-${value.day}T${value.hour}:${value.minute}:${value.second}`; };

export function EventOperationsScreen({ session, flags, onSessionExpired }: { session: Session; flags: FeatureFlags; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const manager = ['ADMIN', 'FACULTY', 'ORGANIZER'].includes(session.role);
  const [events, setEvents] = useState<EventSummary[]>([]);
  const [exportEvents, setExportEvents] = useState<EventSummary[]>([]);
  const [exportEventsError, setExportEventsError] = useState('');
  const [exportEventsPage, setExportEventsPage] = useState(0);
  const [exportEventsHasNext, setExportEventsHasNext] = useState(false);
  const [exportEventsLoading, setExportEventsLoading] = useState(false);
  const [operatorEvents, setOperatorEvents] = useState<EventSummary[]>([]);
  const [selected, setSelected] = useState<EventSummary | null>(null);
  const [operations, setOperations] = useState<Operations | null>(null);
  const [volunteers, setVolunteers] = useState<Volunteer[]>([]);
  const [manualGrants, setManualGrants] = useState<ManualGrant[]>([]);
  const [exportGrants, setExportGrants] = useState<ManualGrant[]>([]);
  const [exportGrantError, setExportGrantError] = useState('');
  const [exportDelegateId, setExportDelegateId] = useState('');
  const [scheduleHistory, setScheduleHistory] = useState<ScheduleChange[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const [replacementDate, setReplacementDate] = useState('');
  const [postponeReason, setPostponeReason] = useState('');
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [rescheduleDuration, setRescheduleDuration] = useState('60');
  const [rescheduleSuggestions, setRescheduleSuggestions] = useState<FacilitySuggestions | null>(null);
  const [lifecycleRequestKey, setLifecycleRequestKey] = useState<string | null>(null);
  const lifecycleRevision = scheduleHistory.reduce((current, change) => Math.max(current, change.changeNumber), 0);
  const eventMutable = selected?.status === 'APPROVED';
  const delegatedExportEvents = exportEvents.filter(event => !events.some(owned => owned.id === event.id));

  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true); setError('');
    try {
      if (manager) {
        const data = await api.eventOperationsMine(signal); if (signal?.aborted) return; setEvents(data); setOperatorEvents([]);
        setSelected(current => data.find(item => item.id === current?.id) ?? data[0] ?? null);
      } else {
        const [opportunities, assigned] = await Promise.all([api.volunteerOpportunities(signal), api.myOperatorEvents(signal)]);
        if (signal?.aborted) return;
        setEvents(opportunities); setOperatorEvents(assigned);
        setSelected(current => opportunities.find(item => item.id === current?.id) ?? opportunities[0] ?? null);
      }
    } catch (e) {
      if (signal?.aborted) return;
      setEvents([]); setOperatorEvents([]); setSelected(null);
      if (e instanceof ApiError && e.status === 401) onSessionExpired();
      else setError(e instanceof Error ? e.message : 'Could not load event operations.');
    } finally { if (!signal?.aborted) setLoading(false); }
  }, [manager, onSessionExpired, revision]);

  useEffect(() => { const controller = new AbortController(); void load(controller.signal); return () => controller.abort(); }, [load]);
  useEffect(() => {
    if (!manager || flags.MODULE_EVENTS !== true) { setExportEvents([]); setExportEventsError(''); setExportEventsHasNext(false); setExportEventsPage(0); return; }
    const controller = new AbortController();
    setExportEventsError(''); setExportEventsPage(0); setExportEventsLoading(true);
    api.attendanceExportEventsMine(0, controller.signal)
      .then(result => { if (!controller.signal.aborted) { setExportEvents(result.items); setExportEventsPage(result.page); setExportEventsHasNext(result.hasNext); } })
      .catch(e => { if (!controller.signal.aborted) { setExportEvents([]); setExportEventsError(e instanceof Error ? e.message : 'Delegated report access could not be loaded.'); } })
      .finally(() => { if (!controller.signal.aborted) setExportEventsLoading(false); });
    return () => controller.abort();
  }, [manager, flags.MODULE_EVENTS, revision]);
  useEffect(() => {
    if (!selected || !manager) { setOperations(null); setVolunteers([]); setManualGrants([]); setExportGrants([]); setExportGrantError(''); setScheduleHistory([]); return; }
    const controller = new AbortController();
    setOperations(null); setVolunteers([]); setManualGrants([]); setExportGrants([]); setExportGrantError(''); setScheduleHistory([]);
    Promise.all([api.eventOperations(selected.id, controller.signal), api.eventVolunteers(selected.id, controller.signal), api.manualGrants(selected.id, controller.signal), api.eventScheduleHistory(selected.id, controller.signal)])
      .then(([ops, roster, grants, history]) => { if (!controller.signal.aborted) { setOperations(ops); setVolunteers(roster); setManualGrants(grants); setScheduleHistory(history); } })
      .catch(e => { if (!controller.signal.aborted) setError(e instanceof Error ? e.message : 'Could not load this event roster.'); });
    api.attendanceExportGrants(selected.id, controller.signal)
      .then(exportAccess => { if (!controller.signal.aborted) setExportGrants(exportAccess); })
      .catch(e => { if (!controller.signal.aborted) setExportGrantError(e instanceof Error ? e.message : 'Attendance export grants could not be loaded.'); });
    return () => controller.abort();
  }, [selected?.id, manager, revision]);

  const act = async (work: () => Promise<unknown>, allowCompleted = false) => {
    if (!eventMutable && !(allowCompleted && selected?.status === 'COMPLETED')) return;
    setBusy(true); setError('');
    try { await work(); setRevision(value => value + 1); }
    catch (e) { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'The request could not be completed.'); }
    finally { setBusy(false); }
  };

  const loadMoreExportEvents = async () => {
    if (exportEventsLoading || !exportEventsHasNext) return;
    setExportEventsLoading(true); setExportEventsError('');
    try {
      const result = await api.attendanceExportEventsMine(exportEventsPage + 1);
      setExportEvents(current => [...current, ...result.items.filter(item => !current.some(existing => existing.id === item.id))]);
      setExportEventsPage(result.page); setExportEventsHasNext(result.hasNext);
    } catch (e) { setExportEventsError(e instanceof Error ? e.message : 'More delegated reports could not be loaded.'); }
    finally { setExportEventsLoading(false); }
  };

  return <View style={{ gap: 18 }}>
    {session.role === 'STUDENT' && flags.MODULE_EVENTS === true ? <MyVolunteerApplicationsPanel key={session.id} revision={revision} onSessionExpired={onSessionExpired} /> : null}
    <Panel><Heading>{manager ? 'Volunteer recruitment' : 'Volunteer opportunities'}</Heading>
      <Body>{manager ? 'Manage recruitment and reasoned manual entry for your approved events. QR attendance remains locked until owner-confirmed proof is available.' : 'Apply using your verified university identity. Your enrollment number is verified privately by the university.'}</Body>
      <Button label="Refresh events" secondary disabled={loading || busy} actionId="events.operations.refresh" onPress={() => setRevision(x => x + 1)} />
      {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
      {loading ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading volunteer events" /> : events.length === 0 ? <Body>{manager ? 'No approved events are available to manage.' : 'There are no open volunteer opportunities right now.'}</Body> : null}
      <View style={{ gap: 10 }}>{events.map(event => <Pressable key={event.id} accessibilityRole="button" accessibilityState={{ selected: selected?.id === event.id }} onPress={() => setSelected(event)} style={{ padding: 14, borderRadius: 12, borderWidth: 1, borderColor: selected?.id === event.id ? colors.accent : colors.border, backgroundColor: colors.surface }}>
        <Text style={{ color: colors.text, fontWeight: '700', fontSize: 17 }}>{event.title}</Text><Text style={{ color: colors.muted }}>{event.status} · {new Date(event.eventDate).toLocaleString()}</Text>
        {!manager ? <Button label="Apply to volunteer" disabled={busy || !eventMutable} actionId="events.volunteer.apply" onPress={() => void act(() => api.applyAsVolunteer(event.id))} /> : null}
      </Pressable>)}</View>
    </Panel>
    {manager && flags.MODULE_EVENTS === true && (delegatedExportEvents.length > 0 || exportEventsError) ? <Panel>
      <Heading>Delegated attendance reports</Heading>
      {exportEventsError ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{exportEventsError}</Text> : null}
      {delegatedExportEvents.map(event => <View key={event.id} style={{ gap: 8, borderTopWidth: 1, borderColor: colors.border, paddingTop: 10 }}>
        <Body>{event.title} · {event.status} · {new Date(event.eventDate).toLocaleString()}</Body>
        <AttendanceReportsPanel event={event} onSessionExpired={onSessionExpired} />
      </View>)}
      {exportEventsHasNext ? <Button label={exportEventsLoading ? 'Loading reports…' : 'Load more delegated reports'} secondary disabled={exportEventsLoading} onPress={() => void loadMoreExportEvents()} /> : null}
    </Panel> : null}
    {manager && selected ? <Panel><Heading>{selected.title}</Heading>
      <Panel><Heading>Schedule change history</Heading>{scheduleHistory.length === 0 ? <Body>No effective cancellation or reschedule changes recorded.</Body> : scheduleHistory.map(change => <View key={change.id} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 10, gap: 4 }}><Text style={{ color: colors.text, fontWeight: '700' }}>#{change.changeNumber} · {change.changeType}</Text><Body>{change.previousDate ? new Date(change.previousDate).toLocaleString() : 'No previous date'} → {change.replacementDate ? new Date(change.replacementDate).toLocaleString() : 'Cancelled'}</Body><Body>{change.reason} · {change.actorRole} · {new Date(change.recordedAt).toLocaleString()}</Body></View>)}</Panel>
      {operations ? <>
        {selected.status === 'APPROVED' ? <>
          <Body>Suggestions are advisory and never reserve a slot. On confirmation, the server rechecks availability and atomically moves the event’s single linked venue reservation. Events without exactly one matching reservation need venue-office reconciliation before rescheduling.</Body>
          <Field label="Expected event duration (minutes)" value={rescheduleDuration} onChange={setRescheduleDuration} keyboardType="number-pad" maxLength={3} />
          <Button label={busy ? 'Checking…' : 'Suggest next available times'} secondary disabled={busy || !eventMutable || !/^\d{1,3}$/.test(rescheduleDuration) || Number(rescheduleDuration) < 15 || Number(rescheduleDuration) > 720} actionId="events.lifecycle.availability.suggest" onPress={() => void act(async () => { const result = await api.eventRescheduleSuggestions(selected.id, Number(rescheduleDuration), 14); setRescheduleSuggestions(result); })} />
          {rescheduleSuggestions ? <View style={{ gap: 8 }}>
            <Body>{rescheduleSuggestions.facilityName} · advisory gaps for {rescheduleSuggestions.durationMinutes} minutes. Search range ends {new Date(rescheduleSuggestions.searchedUntil).toLocaleString()}.</Body>
            {rescheduleSuggestions.unknownConstraints.map(item => <Body key={item}>Not checked: {item}</Body>)}
            {rescheduleSuggestions.items.length ? rescheduleSuggestions.items.map(slot => <Button key={slot.startTime} label={`Use suggested start: ${new Date(slot.startTime).toLocaleString()}`} secondary disabled={busy || !eventMutable} onPress={() => setReplacementDate(slot.startTime)} />) : <Body>No advisory gaps were found in this search range.</Body>}
          </View> : null}
          <TextInput accessibilityLabel="Replacement date and time in campus local time" value={replacementDate} onChangeText={setReplacementDate} placeholder="YYYY-MM-DDTHH:mm (campus local time)" placeholderTextColor={colors.muted} autoCapitalize="none" style={{ minHeight: 48, borderWidth: 1, borderColor: colors.border, borderRadius: 10, padding: 12, color: colors.text, backgroundColor: colors.background }} />
          {flags.ACTION_EVENT_POSTPONE === true ? <><TextInput accessibilityLabel="Reason for rescheduling" value={postponeReason} onChangeText={setPostponeReason} maxLength={300} placeholder="Reason for rescheduling" placeholderTextColor={colors.muted} style={{ minHeight: 48, borderWidth: 1, borderColor: colors.border, borderRadius: 10, padding: 12, color: colors.text, backgroundColor: colors.background }} />
          <Button label="Reschedule and notify registrants" secondary disabled={busy || !eventMutable || !replacementDate.trim() || !postponeReason.trim()} actionId="events.lifecycle.postpone" onPress={() => void act(async () => { const key = lifecycleRequestKey ?? newRequestKey(); setLifecycleRequestKey(key); await api.postponeEvent(selected.id, replacementDate.trim(), postponeReason.trim(), key, lifecycleRevision); setLifecycleRequestKey(null); })} /></> : null}
          {flags.ACTION_EVENT_CANCEL === true ? <Button label="Cancel event and notify registrants" secondary disabled={busy || !eventMutable} actionId="events.lifecycle.cancel" onPress={() => setConfirmCancel(true)} /> : null}
          {confirmCancel ? <View accessibilityRole="alert" style={{ gap: 8, borderWidth: 1, borderColor: colors.error, borderRadius: 10, padding: 12 }}>
            <Body>Cancel {selected.title}? Registered participants will be notified. This action cannot be undone here.</Body>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}><Button label="Keep event" secondary disabled={busy || !eventMutable} onPress={() => setConfirmCancel(false)} /><Button label={busy ? 'Cancelling…' : 'Confirm cancellation'} disabled={busy || !eventMutable} onPress={() => void act(async () => { const key = lifecycleRequestKey ?? newRequestKey(); setLifecycleRequestKey(key); await api.cancelEvent(selected.id, key, lifecycleRevision); setLifecycleRequestKey(null); setConfirmCancel(false); })} /></View>
          </View> : null}
        </> : null}
        <Body>Recruitment: {operations.recruitmentOpen ? 'Open' : 'Closed'} · Capacity {operations.maxVolunteerApplicants}. QR policy: {operations.attendanceMode}. Scanner: {operations.scannerMasterEnabled ? `${operations.scannerScope} (enabled)` : 'Off'}.</Body>
        {operations.revision >= 0 ? <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}><Button label="Enable scanner for all accepted volunteers" secondary disabled={busy || !eventMutable || operations.attendanceMode === 'NO_QR'} actionId="events.operator.scanner.control" onPress={() => void act(() => api.updateScannerControl(selected.id, operations.revision, 'ALL', ['ENTRY', 'ATTENDANCE_CHECKPOINT'], null, null))} /><Button label="Enable selected-volunteer scanner" secondary disabled={busy || !eventMutable || operations.attendanceMode === 'NO_QR'} actionId="events.operator.scanner.control" onPress={() => void act(() => api.updateScannerControl(selected.id, operations.revision, 'SELECTED', ['ENTRY', 'ATTENDANCE_CHECKPOINT'], null, null))} /><Button label="Disable scanner" secondary disabled={busy || !eventMutable || !operations.scannerMasterEnabled} actionId="events.operator.scanner.control" onPress={() => void act(() => api.updateScannerControl(selected.id, operations.revision, 'OFF', [], null, null))} /></View> : null}
        {operations.revision < 0 ? <Body>This approved event needs its operations workspace initialized by an administrator before controls can be changed.</Body> : <Button label={operations.recruitmentOpen ? 'Close recruitment' : 'Open recruitment'} disabled={busy || !eventMutable} actionId="events.volunteer.recruitment.update" onPress={() => void act(() => api.updateRecruitment(selected.id, operations.revision, !operations.recruitmentOpen, operations.maxVolunteerApplicants))} />}
        <Heading>Applications ({volunteers.length})</Heading>
        {operations && selected.status === 'APPROVED' ? <View style={{ gap: 8, borderTopWidth: 1, borderColor: colors.border, paddingTop: 12 }}>
          <Heading>Independent report access</Heading><Body>Grant or revoke event-scoped attendance export access for an enabled faculty or organizer account. This does not grant scanner or manual check-in access. Access expires automatically after two hours.</Body>
          {exportGrantError ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{exportGrantError}</Text> : null}
          <TextInput accessibilityLabel="Faculty or organizer account ID for attendance export" value={exportDelegateId} onChangeText={setExportDelegateId} keyboardType="number-pad" placeholder="Faculty or organizer account ID" placeholderTextColor={colors.muted} style={{ minHeight: 48, borderWidth: 1, borderColor: colors.border, borderRadius: 10, padding: 12, color: colors.text, backgroundColor: colors.background }} />
          <Button label="Grant attendance report access" secondary disabled={busy || !eventMutable || !/^\d{1,12}$/.test(exportDelegateId)} actionId="attendance.report.access.grant" onPress={() => void act(() => api.grantAttendanceExport(selected.id, Number(exportDelegateId), operations.revision, localDateTimeAfter(2 * 60 * 60 * 1000), 'Temporary event-scoped attendance report access'))} />
          {exportGrants.filter(grant => grant.enabled).map(grant => <View key={grant.userId} style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}><Body>Account {grant.userId} · expires {grant.expiresAt ? new Date(grant.expiresAt).toLocaleString() : 'unknown'}</Body><Button label="Revoke" secondary disabled={busy || !['APPROVED', 'COMPLETED'].includes(selected.status)} actionId="attendance.report.access.revoke" onPress={() => void act(() => api.revokeAttendanceExport(selected.id, grant.userId, operations.revision), true)} /></View>)}
        </View> : null}
        {volunteers.length === 0 ? <Body>No volunteer applications yet.</Body> : volunteers.map(person => <View key={person.id} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 12, gap: 8 }}>
          <Text style={{ color: colors.text, fontWeight: '700' }}>{person.displayName} · {person.identifier}</Text><Body>{person.status}</Body>
          {person.status === 'APPLIED' ? <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}><Button label="Accept" disabled={busy || !eventMutable} actionId="events.volunteer.application.decide" onPress={() => void act(() => api.decideVolunteer(selected.id, person.id, person.revision, 'ACCEPT'))} /><Button label="Reject" secondary disabled={busy || !eventMutable} actionId="events.volunteer.application.decide" onPress={() => void act(() => api.decideVolunteer(selected.id, person.id, person.revision, 'REJECT'))} /></View> : null}
          {person.status === 'ACCEPTED' && operations?.scannerMasterEnabled && operations.scannerScope === 'SELECTED' ? <Button label="Grant QR scanner access" secondary disabled={busy || !eventMutable} actionId="events.operator.scanner.grant" onPress={() => void act(() => api.grantScanner(selected.id, person.userId, operations.revision, ['ENTRY', 'ATTENDANCE_CHECKPOINT']))} /> : null}
          {person.status === 'ACCEPTED' && operations?.manualWindowOpen && operations.manualClosesAt ? (() => {
            const grant = manualGrants.find(item => item.userId === person.userId);
            const effective = grant?.enabled === true && grant.actions.includes('ENTRY') && (!grant.expiresAt || Date.parse(grant.expiresAt) > Date.now());
            return <Button label={effective ? 'Revoke manual entry access' : 'Grant manual entry access'} secondary disabled={busy || !eventMutable} actionId={effective ? 'events.operator.manual.revoke' : 'events.operator.manual.grant'} onPress={() => void act(() => effective ? api.revokeManual(selected.id, person.userId, operations.revision) : api.grantManualEntry(selected.id, person.userId, operations.revision, operations.manualClosesAt!))} />;
          })() : null}
        </View>)}
      </> : <ActivityIndicator color={colors.accent} accessibilityLabel="Loading event roster" />}
      <Body>QR scanning uses a short-lived owner-confirmation intent. Manual check-in below is a separate supervised fallback and never unlocks certificates. Reports remain server-generated and scoped to authorized operators.</Body>
    </Panel> : null}
    {manager && selected && eventMutable ? <ManualAttendancePanel event={selected} manager onSessionExpired={onSessionExpired} /> : null}
    {manager && selected && ['APPROVED', 'COMPLETED'].includes(selected.status) && flags.MODULE_EVENTS === true ? <AttendanceReportsPanel event={selected} onSessionExpired={onSessionExpired} /> : null}
    {manager && selected && operations && flags.MODULE_CERTIFICATES === true ? <CertificatePolicyPanel key={selected.id} event={selected} operations={operations} onChanged={() => setRevision(x => x + 1)} onSessionExpired={onSessionExpired} /> : null}
    {manager && selected && eventMutable && flags.MODULE_READINESS === true ? <ReadinessScreen eventId={selected.id} onSessionExpired={onSessionExpired} /> : null}
    {!manager && operatorEvents.length > 0 ? <Panel><Heading>Your approved volunteer assignments</Heading>
      <Body>Manual check-in is available only when the organizer has opened the window and granted your account the ENTRY action.</Body>
      {operatorEvents.map(event => <ManualAttendancePanel key={event.id} event={event} onSessionExpired={onSessionExpired} />)}
    </Panel> : null}
  </View>;
}

function AttendanceReportsPanel({ event, onSessionExpired }: { event: EventSummary; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [busyKind, setBusyKind] = useState<AttendanceExportKind | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const openDownload = (url: string) => {
    if (Platform.OS !== 'web' || !url.startsWith('/api/v1/')) return;
    const link = document.createElement('a');
    link.href = url;
    link.download = '';
    link.rel = 'noreferrer';
    document.body.appendChild(link);
    link.click();
    link.remove();
  };
  const shareNative = async (kind: AttendanceExportKind) => {
    if (busyKind) return;
    setBusyKind(kind); setError(''); setNotice('');
    try {
      await shareAttendanceExport(event.id, kind);
      setNotice('Report opened in the device share sheet. Any copy you share is managed by the receiving app.');
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) onSessionExpired();
      else setError(cause instanceof Error ? cause.message : 'The report could not be shared.');
    } finally { setBusyKind(null); }
  };
  return <Panel><Heading>Attendance reports</Heading>
    <Body>Exports are current accepted-evidence snapshots sorted by verified identifier. While attendance is still open, they are provisional; later reviewed corrections may change a future export. CSV values should be imported as text to preserve leading zeros.</Body>
    {Platform.OS === 'web' ? <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
      <Button label="Download attendance CSV" actionId="attendance.report.csv.download" onPress={() => openDownload(api.attendanceReportUrl(event.id, 'csv'))} />
      <Button label="Download color-coded attendance XLSX" secondary actionId="attendance.report.xlsx.download" onPress={() => openDownload(api.attendanceReportUrl(event.id, 'xlsx'))} />
      <Button label="Download accepted scan log CSV" secondary actionId="attendance.report.scan-log.download" onPress={() => openDownload(api.attendanceScanLogUrl(event.id))} />
    </View> : <View style={{ gap: 8 }}>
      <Body>Reports are fetched from the authenticated university API, held temporarily in app cache, then opened in Android’s share sheet. A recipient app may retain a copy after sharing.</Body>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        <Button label={busyKind === 'report-csv' ? 'Preparing…' : 'Share attendance CSV'} disabled={busyKind !== null} actionId="attendance.report.csv.download" onPress={() => void shareNative('report-csv')} />
        <Button label={busyKind === 'report-xlsx' ? 'Preparing…' : 'Share color-coded attendance XLSX'} secondary disabled={busyKind !== null} actionId="attendance.report.xlsx.download" onPress={() => void shareNative('report-xlsx')} />
        <Button label={busyKind === 'scan-log-csv' ? 'Preparing…' : 'Share accepted scan log CSV'} secondary disabled={busyKind !== null} actionId="attendance.report.scan-log.download" onPress={() => void shareNative('scan-log-csv')} />
      </View>
      {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
      {notice ? <Text accessibilityRole="status" style={{ color: colors.muted }}>{notice}</Text> : null}
    </View>}
  </Panel>;
}

function ManualAttendancePanel({ event, manager = false, onSessionExpired }: { event: EventSummary; manager?: boolean; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [operations, setOperations] = useState<Operations | null>(null);
  const [capability, setCapability] = useState<{ manualAttendanceEnabled: boolean; manualActions: string[] } | null>(null);
  const [identifier, setIdentifier] = useState('');
  const [reason, setReason] = useState('PHONE_UNAVAILABLE');
  const [receipt, setReceipt] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  const pendingManual = useRef<{ eventId: number; identifier: string; reason: string; key: string } | null>(null);
  const load = useCallback(async (signal?: AbortSignal) => {
    try {
      const [ops, caps] = await Promise.all([api.eventOperations(event.id, signal), api.operatorCapabilities(event.id, signal)]);
      if (!signal?.aborted) { setOperations(ops); setCapability(caps); setError(''); }
    } catch (e) {
      if (!signal?.aborted) { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Manual attendance controls are unavailable.'); }
    }
  }, [event.id, onSessionExpired]);
  useEffect(() => { const controller = new AbortController(); void load(controller.signal); return () => controller.abort(); }, [load, revision]);
  const toggleWindow = async () => {
    if (!operations) return;
    setBusy(true); setError('');
    try { await api.updateManualWindow(event.id, operations.revision, !operations.manualWindowOpen, 'Authorized event attendance fallback'); setRevision(x => x + 1); }
    catch (e) { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Could not change the manual attendance window.'); }
    finally { setBusy(false); }
  };
  const submit = async () => {
    setBusy(true); setError(''); setReceipt('');
    let pending = pendingManual.current;
    if (!pending || pending.eventId !== event.id || pending.identifier !== identifier || pending.reason !== reason) {
      const key = newRequestKey();
      pending = { eventId: event.id, identifier, reason, key };
      pendingManual.current = pending;
    }
    try { const saved = await api.manualCheckIn(event.id, identifier, reason, pending.key); pendingManual.current = null; setReceipt(`${saved.status}: ${saved.displayName} (${saved.identifier}) · ${new Date(saved.occurredAt).toLocaleTimeString()}`); setRevision(x => x + 1); }
    catch (e) { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Manual check-in was not recorded.'); }
    finally { setBusy(false); }
  };
  const manualOn = capability?.manualAttendanceEnabled === true && capability.manualActions.includes('ENTRY');
  return <Panel><Heading>{event.title} · Manual entry</Heading>
    <Body>Check the participant’s official university ID in person. This records one check-in against a confirmed registration; it does not issue a certificate or prove duration.</Body>
    {manager && operations ? <><Body>Manual delegates receive a saved account notice when access is granted or revoked. External push and email delivery are unavailable in this release.</Body><Button label={operations.manualWindowOpen ? 'Close manual attendance window' : 'Open 2-hour manual attendance window'} secondary disabled={busy} actionId="events.attendance.manual-window.toggle" onPress={() => void toggleWindow()} /></> : null}
    <TextInput accessibilityLabel="Verified roll or enrollment number" value={identifier} onChangeText={setIdentifier} autoCapitalize="characters" autoCorrect={false} maxLength={64} placeholder="Verified roll / enrollment number" placeholderTextColor={colors.muted} style={{ minHeight: 48, borderWidth: 1, borderColor: colors.border, borderRadius: 10, padding: 12, color: colors.text, backgroundColor: colors.background }} />
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>{manualReasons.map(([value, label]) => <Pressable key={value} accessibilityRole="radio" aria-checked={reason === value} accessibilityState={{ checked: reason === value }} onPress={() => setReason(value)} style={{ padding: 10, borderRadius: 9, backgroundColor: reason === value ? colors.badge : colors.surface, borderWidth: 1, borderColor: colors.border }}><Text style={{ color: colors.text }}>{label}</Text></Pressable>)}</View>
    <Button label={busy ? 'Saving…' : 'Verify and record check-in'} disabled={busy || !manualOn || identifier.length < 1} actionId="events.attendance.manual-entry" onPress={() => void submit()} />
    {!manualOn ? <Body>Manual entry is locked until an authorized event manager opens the window and, for volunteers, grants ENTRY access.</Body> : null}
    {receipt ? <Text accessibilityLiveRegion="polite" style={{ color: colors.text }}>{receipt}</Text> : null}
    {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
  </Panel>;
}
