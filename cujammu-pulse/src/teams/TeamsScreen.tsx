import React, { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, Text, TextInput, View } from 'react-native';
import { api, ApiError } from '../api';
import type { TeamEvent, TeamHistory, TeamMember } from './contracts';
import { Body, Button, Field, Heading, Panel, ThemeContext } from '../ui';

export function TeamsScreen({ onSessionExpired }: { onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [events, setEvents] = useState<TeamEvent[]>([]);
  const [selected, setSelected] = useState<TeamEvent | null>(null);
  const [history, setHistory] = useState<TeamHistory | null>(null);
  const [roster, setRoster] = useState<{ teamId: number; members: TeamMember[] } | null>(null);
  const [name, setName] = useState(''); const [code, setCode] = useState('');
  const [loading, setLoading] = useState(true); const [historyLoading, setHistoryLoading] = useState(true);
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const controller = useRef<AbortController | null>(null);
  const fail = useCallback((e: unknown) => {
    if (e instanceof ApiError && e.status === 401) onSessionExpired();
    else setError(e instanceof Error ? e.message : 'Team registration is unavailable.');
  }, [onSessionExpired]);

  const loadEvents = useCallback(async (signal: AbortSignal) => {
    setLoading(true);
    try {
      const eligible = await api.teamEligibleEvents(signal);
      if (!signal.aborted) { setEvents(eligible); setSelected(current => eligible.find(event => event.id === current?.id) ?? eligible[0] ?? null); }
    } catch (e) { if (!signal.aborted) { setEvents([]); setSelected(null); fail(e); } }
    finally { if (!signal.aborted) setLoading(false); }
  }, [fail]);
  const loadHistory = useCallback(async (page: number, signal: AbortSignal) => {
    setHistoryLoading(true); setHistory(null); setRoster(null);
    try { const result = await api.teamHistory(page, signal); if (!signal.aborted) setHistory(result); }
    catch (e) { if (!signal.aborted) fail(e); }
    finally { if (!signal.aborted) setHistoryLoading(false); }
  }, [fail]);
  const refresh = useCallback(async () => {
    controller.current?.abort(); const current = new AbortController(); controller.current = current; setError('');
    await Promise.all([loadEvents(current.signal), loadHistory(0, current.signal)]);
  }, [loadEvents, loadHistory]);
  useEffect(() => { void refresh(); return () => controller.current?.abort(); }, [refresh]);
  const work = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError('');
    try { await fn(); await refresh(); } catch (e) { fail(e); } finally { setBusy(false); }
  };
  const historyPage = async (page: number) => {
    setBusy(true); setError(''); controller.current?.abort();
    const current = new AbortController(); controller.current = current;
    try { await loadHistory(page, current.signal); } finally { setBusy(false); }
  };
  const showRoster = async (teamId: number) => {
    setBusy(true); setError(''); setRoster(null);
    const signal = controller.current?.signal;
    try { const result = await api.teamRoster(teamId, signal); if (!signal?.aborted) setRoster({ teamId, members: result.members }); }
    catch (e) { if (!signal?.aborted) fail(e); } finally { setBusy(false); }
  };
  return <View style={{ gap: 18 }}>
    <Panel><Heading>Team registrations</Heading><Body>Create or join one team for an event you are confirmed to attend. Your existing teams remain here after registration closes.</Body>
      <Button label="Refresh teams" secondary disabled={loading || historyLoading || busy} actionId="teams.events.refresh" onPress={() => void refresh()} />
      {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
    </Panel>
    {loading ? <ActivityIndicator accessibilityLabel="Loading eligible team events" color={colors.accent} /> : events.length === 0 ? <Panel><Body>No confirmed event currently accepts team registrations.</Body></Panel> : <>
      <Panel><Heading>Choose event</Heading>{events.map(event => <Pressable key={event.id} accessibilityRole="button" accessibilityState={{ selected: selected?.id === event.id }} disabled={busy} onPress={() => { setSelected(event); setCode(''); }} style={{ padding: 12, borderRadius: 10, borderWidth: 1, borderColor: selected?.id === event.id ? colors.accent : colors.border }}>
        <Text style={{ color: colors.text, fontWeight: '700' }}>{event.title}</Text><Body>{new Date(event.eventDate).toLocaleString()}</Body>
      </Pressable>)}</Panel>
      <Panel><Heading>Create team</Heading><Field label="Team name" value={name} onChange={setName} maxLength={120} />
        <Button label={busy ? 'Saving…' : 'Create team'} disabled={busy || !selected || !name.trim()} actionId="teams.create" onPress={() => void work(() => api.createTeam(selected!.id, name.trim(), 2, 5))} />
      </Panel>
      <Panel><Heading>Join with invite code</Heading><Body>Join a team for {selected?.title}. Codes from other events are rejected.</Body>
        <TextInput accessibilityLabel="Team invite code" value={code} onChangeText={value => setCode(value.toUpperCase())} maxLength={10} autoCapitalize="characters" placeholder="CUJ-XXXXXX" placeholderTextColor={colors.muted} style={{ minHeight: 48, borderWidth: 1, borderColor: colors.border, borderRadius: 10, padding: 12, color: colors.text, backgroundColor: colors.background }} />
        <Button label={busy ? 'Joining…' : 'Join team'} disabled={busy || !selected || !/^CUJ-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$/.test(code)} actionId="teams.join" onPress={() => void work(() => api.joinTeam(selected!.id, code))} />
      </Panel>
    </>}
    <Panel><Heading>My teams and history</Heading>
      {historyLoading ? <ActivityIndicator accessibilityLabel="Loading my teams" color={colors.accent} /> : history ? <>
        {history.items.length ? history.items.map(({ team, event, canChangeRoster }) => <View key={team.id} style={{ gap: 6, borderBottomWidth: 1, borderColor: colors.border, paddingBottom: 12 }}>
          <Text style={{ color: colors.text, fontWeight: '700' }}>{team.name}</Text><Body>{event.title} · {new Date(event.eventDate).toLocaleString()}</Body>
          <Body>{team.memberCount}/{team.maxSize} members · {team.locked ? 'Roster locked' : 'Roster open'}</Body>
          {team.canManage && team.inviteCode ? <Body>Invite code: {team.inviteCode}</Body> : null}
          {canChangeRoster ? <Button label="Lock roster" secondary disabled={busy} actionId="teams.lock" onPress={() => void work(() => api.lockTeam(team.id, team.revision, true))} /> : <Body>Roster changes are unavailable.</Body>}
          <Button label={`View roster for ${team.name}`} secondary disabled={busy} actionId="teams.roster" onPress={() => void showRoster(team.id)} />
          {roster?.teamId === team.id ? roster.members.map(member => <Body key={member.id}>{member.name} · {member.role}</Body>) : null}
        </View>) : <Body>You have not joined any teams.</Body>}
        <Body>History page {history.page + 1}</Body>
        <Button label="Previous teams" secondary disabled={busy || history.page === 0} actionId="teams.history.previous" onPress={() => void historyPage(history.page - 1)} />
        <Button label="Next teams" secondary disabled={busy || !history.hasMore} actionId="teams.history.next" onPress={() => void historyPage(history.page + 1)} />
      </> : <Body>Your team history could not be loaded. Refresh to retry.</Body>}
    </Panel>
  </View>;
}
