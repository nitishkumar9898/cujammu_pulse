import React, { useState, useContext, useEffect } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError, newRequestKey } from '../api';
import { Body, Button, Choice, Field, Heading, Panel, ThemeContext } from '../ui';
import type { EventSummary } from '../event-operations/contracts';
import type { Team } from '../teams/contracts';
import type { Fixture, FixtureScoreStatus } from './contracts';
import { allowedFixtureScoreStatuses, normalizeFixtureLocalDateTime, parsePositiveJavaInt } from './contracts';

const statusLabel: Record<FixtureScoreStatus, string> = {
  LIVE: 'Live score',
  SUSPENDED: 'Suspend match',
  RESUMED: 'Resume match',
  PROVISIONAL_RESULT: 'Provisional result',
  FINAL: 'Final result',
};

export function SportsScreen({ role, onSessionExpired }: { role: string; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [eventId, setEventId] = useState('');
  const [managedEvents, setManagedEvents] = useState<EventSummary[]>([]);
  const [managedEventsLoading, setManagedEventsLoading] = useState(false);
  const [managedEventsRetry, setManagedEventsRetry] = useState(0);
  const [teamRosterError, setTeamRosterError] = useState('');
  const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
  const [loadedEventId, setLoadedEventId] = useState<number | null>(null);
  const [teams, setTeams] = useState<Team[]>([]);
  const [fixtures, setFixtures] = useState<Fixture[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<Fixture | null>(null);
  const [score1, setScore1] = useState('');
  const [score2, setScore2] = useState('');
  const [draftStatus, setDraftStatus] = useState<FixtureScoreStatus>('LIVE');
  const [winnerId, setWinnerId] = useState<number | null>(null);
  const [changeReason, setChangeReason] = useState('');
  const [team1Id, setTeam1Id] = useState<number | null>(null);
  const [team2Id, setTeam2Id] = useState<number | null>(null);
  const [tournamentName, setTournamentName] = useState('');
  const [roundNumber, setRoundNumber] = useState('1');
  const [roundName, setRoundName] = useState('');
  const [matchNumber, setMatchNumber] = useState('1');
  const [matchTime, setMatchTime] = useState('');
  const [venue, setVenue] = useState('');
  const [fixtureRequestKey, setFixtureRequestKey] = useState<string | null>(null);
  const [fixtureRecoveryRequired, setFixtureRecoveryRequired] = useState(false);
  const canScore = ['ADMIN', 'FACULTY', 'ORGANIZER'].includes(role);
  const id = canScore ? selectedEventId : Number(eventId);
  const parsedRoundNumber = parsePositiveJavaInt(roundNumber);
  const parsedMatchNumber = parsePositiveJavaInt(matchNumber);
  const scoreStatuses = selected ? allowedFixtureScoreStatuses(selected.status) : [];
  const requiresWinner = draftStatus === 'PROVISIONAL_RESULT' || draftStatus === 'FINAL';

  useEffect(() => {
    if (!canScore) return;
    const controller = new AbortController();
    setManagedEventsLoading(true);
    api.myOperatorEvents(controller.signal).then(events => {
      if (!controller.signal.aborted) setManagedEvents(events);
    }).catch(failure => {
      if (!controller.signal.aborted) {
        if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
        else setError(failure instanceof Error ? failure.message : 'Managed events could not be loaded.');
      }
    }).finally(() => { if (!controller.signal.aborted) setManagedEventsLoading(false); });
    return () => controller.abort();
  }, [canScore, managedEventsRetry]);

  const load = async () => {
    if (fixtureRecoveryRequired) return;
    if (id == null || !Number.isSafeInteger(id) || id < 1) return;
    setLoading(true); setError(''); setTeamRosterError('');
    try {
      const nextFixtures = await api.tournamentFixtures(id);
      setFixtures(nextFixtures); setLoadedEventId(id); setTeams([]);
      if (canScore) {
        try { setTeams(await api.eventTeams(id)); }
        catch (failure) {
          if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
          else setTeamRosterError(failure instanceof Error ? failure.message : 'Team roster is unavailable.');
        }
      }
      setTeam1Id(null); setTeam2Id(null); setFixtureRequestKey(null); setFixtureRecoveryRequired(false);
    }
    catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Fixtures could not be loaded.'); }
    finally { setLoading(false); }
  };

  const createFixture = async () => {
    if (!id || loadedEventId !== id || !tournamentName.trim() || !roundName.trim() || !parsedRoundNumber || !parsedMatchNumber || !team1Id || !team2Id || team1Id === team2Id) return;
    const normalizedMatchTime = normalizeFixtureLocalDateTime(matchTime);
    if (!normalizedMatchTime) { setError('Enter a valid campus-local date and time in YYYY-MM-DDTHH:mm format.'); return; }
    const first = teams.find(team => team.id === team1Id), second = teams.find(team => team.id === team2Id);
    if (!first || !second || first.name.trim().toLowerCase() === second.name.trim().toLowerCase()) {
      setError('Select two distinct registered teams with different names.'); return;
    }
    const requestKey = fixtureRequestKey ?? newRequestKey();
    setFixtureRequestKey(requestKey); setLoading(true); setError('');
    try {
      const created = await api.createFixture({
        eventId: id, tournamentName: tournamentName.trim(), roundNumber: parsedRoundNumber, roundName: roundName.trim(),
        matchNumber: parsedMatchNumber, team1Id: first.id, team1Name: first.name, team2Id: second.id, team2Name: second.name,
        matchTime: normalizedMatchTime, venue: venue.trim() || null, requestKey,
      });
      setFixtures(current => [...current, created].sort((a, b) => a.roundNumber - b.roundNumber || a.matchNumber - b.matchNumber));
      setFixtureRequestKey(null); setFixtureRecoveryRequired(false);
    } catch (failure) {
      const uncertain = !(failure instanceof ApiError) || failure.status === 408 || failure.status === 429 || failure.status >= 500;
      setFixtureRecoveryRequired(uncertain);
      if (!uncertain) setFixtureRequestKey(null);
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
      else setError(failure instanceof Error ? failure.message : 'Fixture could not be saved. Retry without changing the form to recover a committed result.');
    } finally { setLoading(false); }
  };

  const openEditor = (fixture: Fixture) => {
    const allowed = allowedFixtureScoreStatuses(fixture.status);
    setSelected(fixture); setScore1(fixture.scoreTeam1); setScore2(fixture.scoreTeam2);
    setDraftStatus(allowed[0] ?? 'LIVE'); setWinnerId(fixture.winnerId); setChangeReason(''); setError('');
  };

  const updateScore = async () => {
    if (!selected || !score1.trim() || !score2.trim() || score1.length > 50 || score2.length > 50) return;
    if (requiresWinner && (!selected.team1Id || !selected.team2Id || !winnerId)) {
      setError('A result needs verified team records and an operator-selected winner.'); return;
    }
    if (selected.revision > 0 && !changeReason.trim()) {
      setError('Add a reason for this score correction before saving.'); return;
    }
    const winnerName = winnerId === selected.team1Id ? selected.team1Name : winnerId === selected.team2Id ? selected.team2Name : null;
    setLoading(true); setError('');
    try {
      const updated = await api.updateFixtureScore(selected.id, {
        scoreTeam1: score1.trim(), scoreTeam2: score2.trim(), status: draftStatus,
        winnerId: requiresWinner ? winnerId : null, winnerName: requiresWinner ? winnerName : null,
        expectedRevision: selected.revision, requestKey: newRequestKey(),
        changeReason: selected.revision > 0 ? changeReason.trim() : null,
      });
      setFixtures(current => current.map(item => item.id === updated.id ? updated : item));
      setSelected(null); setWinnerId(null); setChangeReason('');
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
      else setError(failure instanceof Error ? failure.message : 'Score update could not be saved. Refresh fixtures and retry.');
    } finally { setLoading(false); }
  };

  const selectedManagerEvent = id == null ? null : managedEvents.find(event => event.id === id) ?? null;
  const canManageSelected = canScore && selectedManagerEvent?.status === 'APPROVED';
  return <View style={{ gap: 14 }}>
    <Panel>
      <Heading>Sports & tournaments</Heading>
      <Body>View published fixtures and authorized score updates. Results stay provisional until an operator explicitly confirms them.</Body>
      {canScore ? <>
        <Body>Choose an event you manage. Fixtures and registered teams are loaded from the service.</Body>
        {managedEvents.map(event => <Choice key={event.id} label={`${event.title} · ${event.status} · ${new Date(event.eventDate).toLocaleDateString()}`} checked={selectedEventId === event.id} disabled={loading || fixtureRecoveryRequired} onChange={checked => {
          setSelectedEventId(checked ? event.id : null); setLoadedEventId(null); setFixtures([]); setTeams([]);
          setTeam1Id(null); setTeam2Id(null); setFixtureRequestKey(null);
        }} />)}
        {managedEventsLoading ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading managed events" /> : null}
        {!managedEventsLoading && managedEvents.length === 0 && error ? <Button label="Retry loading managed events" secondary onPress={() => { setError(''); setManagedEventsRetry(value => value + 1); }} /> : null}
        {!managedEventsLoading && managedEvents.length === 0 && !error ? <Body>No events are currently assigned to your account.</Body> : null}
        {selectedManagerEvent && selectedManagerEvent.status !== 'APPROVED' ? <Body>This event is closed. Fixture history is view-only.</Body> : null}
      </> : <Field label="Event ID" value={eventId} onChange={setEventId} maxLength={12} keyboardType="number-pad" />}
      <Button actionId="sports.fixtures.read" label={loading ? 'Loading…' : 'Load fixtures'} disabled={loading || fixtureRecoveryRequired || id == null || !Number.isSafeInteger(id) || id < 1} onPress={() => void load()} />
    </Panel>
    {error ? <Panel><Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text></Panel> : null}
    {fixtures.map(fixture => <Panel key={fixture.id}>
      <Text style={{ color: colors.gold, fontWeight: '700' }}>{fixture.tournamentName} · {fixture.roundName} · Match {fixture.matchNumber}</Text>
      <Text style={{ color: colors.text, fontSize: 17, fontWeight: '700' }}>{fixture.team1Name} {fixture.scoreTeam1} — {fixture.scoreTeam2} {fixture.team2Name}</Text>
      <Body>{fixture.status.replaceAll('_', ' ')} · {fixture.matchTime}{fixture.venue ? ` · ${fixture.venue}` : ''}</Body>
      {fixture.winnerName && (fixture.status === 'FINAL' || fixture.status === 'PROVISIONAL_RESULT') ? <Body>{fixture.status === 'FINAL' ? 'Confirmed winner' : 'Provisional winner'}: {fixture.winnerName}</Body> : null}
      {canManageSelected && loadedEventId === id && allowedFixtureScoreStatuses(fixture.status).length > 0 ? <Button actionId="sports.fixtures.score.update" label="Update score" secondary onPress={() => openEditor(fixture)} /> : null}
      {selected?.id === fixture.id ? <View style={{ gap: 8 }}>
        <Heading>Record match update</Heading>
        <Field label={`${fixture.team1Name} score`} value={score1} onChange={setScore1} maxLength={50} />
        <Field label={`${fixture.team2Name} score`} value={score2} onChange={setScore2} maxLength={50} />
        <Body>Choose the match state. A final result requires a separate explicit confirmation.</Body>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {scoreStatuses.map(status => <Button key={status} label={statusLabel[status]} secondary={draftStatus !== status} disabled={loading} onPress={() => setDraftStatus(status)} />)}
        </View>
        {requiresWinner ? <>
          <Body>Select the winner according to the approved rules for this sport.</Body>
          {fixture.team1Id && fixture.team2Id ? <>
            <Choice label={fixture.team1Name} checked={winnerId === fixture.team1Id} disabled={loading} onChange={checked => { if (checked) setWinnerId(fixture.team1Id); }} />
            <Choice label={fixture.team2Name} checked={winnerId === fixture.team2Id} disabled={loading} onChange={checked => { if (checked) setWinnerId(fixture.team2Id); }} />
          </> : <Body>Result confirmation is unavailable because verified team identifiers are missing.</Body>}
          <Body>Tie-break rules and automatic bracket advancement require an approved sport-specific policy.</Body>
        </> : null}
        {selected.revision > 0 ? <Field label="Change reason" value={changeReason} onChange={setChangeReason} maxLength={300} multiline /> : null}
        <Button actionId="sports.fixtures.score.update" label={statusLabel[draftStatus]} disabled={loading || !score1.trim() || !score2.trim() || (requiresWinner && (!winnerId || !fixture.team1Id || !fixture.team2Id)) || (selected.revision > 0 && !changeReason.trim())} onPress={() => void updateScore()} />
        <Button label="Discard score edit" secondary disabled={loading} onPress={() => setSelected(null)} />
      </View> : null}
    </Panel>)}
    {canManageSelected && loadedEventId === id ? <Panel>
      <Heading>Create fixture</Heading>
      <Body>Teams come from this event’s registered roster. The server verifies event and team identity again when saving.</Body>
      {fixtureRecoveryRequired ? <Body>The save outcome is uncertain. Retry this unchanged request to recover its result; editing could create a duplicate fixture.</Body> : null}
      <Field label="Tournament name" value={tournamentName} disabled={loading || fixtureRecoveryRequired} onChange={value => { setTournamentName(value); setFixtureRequestKey(null); }} maxLength={100} />
      <Field label="Round number" value={roundNumber} disabled={loading || fixtureRecoveryRequired} onChange={value => { setRoundNumber(value); setFixtureRequestKey(null); }} maxLength={3} keyboardType="number-pad" />
      <Field label="Round name" value={roundName} disabled={loading || fixtureRecoveryRequired} onChange={value => { setRoundName(value); setFixtureRequestKey(null); }} maxLength={50} />
      <Field label="Match number" value={matchNumber} disabled={loading || fixtureRecoveryRequired} onChange={value => { setMatchNumber(value); setFixtureRequestKey(null); }} maxLength={4} keyboardType="number-pad" />
      <Field label="Campus-local match time" value={matchTime} disabled={loading || fixtureRecoveryRequired} onChange={value => { setMatchTime(value); setFixtureRequestKey(null); }} maxLength={19} />
      <Field label="Court or venue" value={venue} disabled={loading || fixtureRecoveryRequired} onChange={value => { setVenue(value); setFixtureRequestKey(null); }} maxLength={100} />
      <Body>First team</Body>
      {teams.map(team => <Choice key={`first-${team.id}`} label={`First team: ${team.name}`} checked={team1Id === team.id} disabled={loading || fixtureRecoveryRequired || team2Id === team.id} onChange={checked => { setTeam1Id(checked ? team.id : null); setFixtureRequestKey(null); }} />)}
      <Body>Second team</Body>
      {teams.map(team => <Choice key={`second-${team.id}`} label={`Second team: ${team.name}`} checked={team2Id === team.id} disabled={loading || fixtureRecoveryRequired || team1Id === team.id} onChange={checked => { setTeam2Id(checked ? team.id : null); setFixtureRequestKey(null); }} />)}
      {teamRosterError ? <Body>{`Team roster could not be loaded: ${teamRosterError}`}</Body> : null}
      {!teamRosterError && teams.length === 0 ? <Body>No registered teams are available. Register teams before creating fixtures.</Body> : null}
      <Button actionId="sports.fixtures.create" label={loading ? 'Saving…' : fixtureRecoveryRequired ? 'Retry same fixture request' : 'Create fixture'} disabled={loading || !tournamentName.trim() || !roundName.trim() || !normalizeFixtureLocalDateTime(matchTime) || !parsedRoundNumber || !parsedMatchNumber || !team1Id || !team2Id || team1Id === team2Id || teams.length === 0} onPress={() => void createFixture()} />
    </Panel> : null}
    {loading ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading fixtures" /> : null}
  </View>;
}
