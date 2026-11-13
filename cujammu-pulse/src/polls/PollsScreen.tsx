import React, { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import * as Crypto from 'expo-crypto';
import { api, ApiError } from '../api';
import { Body, Button, Field, Heading, Panel, ThemeContext, uiStyles } from '../ui';
import type { CreatePoll, PollEvent, PollView } from './contracts';

export function PollsScreen({ onSessionExpired }: { onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [events, setEvents] = useState<PollEvent[]>([]), [selected, setSelected] = useState<PollEvent | null>(null);
  const [polls, setPolls] = useState<PollView[]>([]), [eventPage, setEventPage] = useState(0), [eventMore, setEventMore] = useState(false);
  const [pollPage, setPollPage] = useState(0), [pollMore, setPollMore] = useState(false);
  const [loading, setLoading] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [question, setQuestion] = useState(''), [optionText, setOptionText] = useState(''), [duration, setDuration] = useState('30');
  const [uncertain, setUncertain] = useState(false);
  const [rejectedDraft, setRejectedDraft] = useState<string | null>(null);
  const pending = useRef<{ eventId: number; fingerprint: string; command: CreatePoll } | null>(null);
  const directory = useRef<AbortController | null>(null), pollRequest = useRef<AbortController | null>(null), mutation = useRef<AbortController | null>(null);
  const activeEvent = useRef<number | null>(null), generation = useRef(0);
  const resetDraft = () => { setQuestion(''); setOptionText(''); setDuration('30'); pending.current = null; setUncertain(false); setRejectedDraft(null); };
  const clear = () => { generation.current++; pollRequest.current?.abort(); activeEvent.current = null; setSelected(null); setPolls([]); setPollMore(false); resetDraft(); };
  const fail = (e: unknown) => {
    if (e instanceof ApiError && [401, 403, 404].includes(e.status)) { clear(); setEvents([]); setEventMore(false); setLoading(false); }
    if (e instanceof ApiError && e.status === 401) onSessionExpired();
    else setError(e instanceof Error ? e.message : 'Polls could not be loaded.');
  };
  const loadPolls = async (eventId: number, target = 0) => {
    pollRequest.current?.abort(); const controller = new AbortController(); pollRequest.current = controller;
    const version = ++generation.current; setLoading(true); setPolls([]); setPollMore(false); setError('');
    try {
      const result = await api.eventPolls(eventId, target, controller.signal);
      if (controller.signal.aborted || version !== generation.current || activeEvent.current !== eventId) return;
      if (result.page !== target || result.items.some(poll => poll.eventId !== eventId)) throw new Error('Poll scope changed. Refresh events to continue.');
      setPolls(result.items); setPollPage(target); setPollMore(result.hasMore);
    } catch (e) { if (!controller.signal.aborted && version === generation.current) fail(e); }
    finally { if (!controller.signal.aborted && version === generation.current) setLoading(false); }
  };
  const chooseEvent = (event: PollEvent) => {
    if (busy || event.id === activeEvent.current) return;
    clear(); setError(''); activeEvent.current = event.id; setSelected(event); setPollPage(0); void loadPolls(event.id);
  };
  const loadEvents = useCallback(async (target = 0, preserve = true) => {
    directory.current?.abort(); pollRequest.current?.abort(); generation.current++;
    const controller = new AbortController(); directory.current = controller;
    const preferred = preserve ? activeEvent.current : null;
    if (!preserve) clear();
    setLoading(true); setError(''); setEvents([]); setPolls([]); setSelected(null); setEventMore(false);
    try {
      const result = await api.pollEvents(target, controller.signal);
      if (controller.signal.aborted) return;
      if (result.page !== target) throw new Error('The events page changed. Refresh before continuing.');
      setEvents(result.items); setEventPage(target); setEventMore(result.hasMore);
      const event = result.items.find(item => item.id === preferred) ?? result.items[0] ?? null;
      if (event?.id !== preferred || !event?.canManage) resetDraft();
      activeEvent.current = event?.id ?? null; setSelected(event);
      if (event) await loadPolls(event.id);
    } catch (e) { if (!controller.signal.aborted) fail(e); }
    finally { if (!controller.signal.aborted) setLoading(false); }
  }, [onSessionExpired]);
  useEffect(() => { void loadEvents(); return () => { directory.current?.abort(); pollRequest.current?.abort(); mutation.current?.abort(); generation.current++; }; }, [loadEvents]);

  const act = async (operation: (signal: AbortSignal) => Promise<PollView>, creating = false, voting = false): Promise<boolean> => {
    if (!selected || busy || mutation.current) return false;
    const eventId = selected.id, version = generation.current, controller = new AbortController(); mutation.current = controller;
    setBusy(true); setError('');
    try {
      const receipt = await operation(controller.signal);
      if (controller.signal.aborted || version !== generation.current || activeEvent.current !== eventId) return false;
      if (receipt.eventId !== eventId) throw new Error('Poll scope changed. Refresh events to continue.');
      if (creating) { resetDraft(); await loadPolls(eventId); }
      else setPolls(current => current.map(poll => poll.id === receipt.id ? receipt : poll));
      return true;
    } catch (e) {
      if (!controller.signal.aborted && version === generation.current) {
        if (creating && (!(e instanceof ApiError) || e.status >= 500)) { setUncertain(true); setError('Creation could not be confirmed. Refresh polls or retry the unchanged draft with the same request identifier.'); }
        else if (voting && (!(e instanceof ApiError) || e.status >= 500)) setError('Your vote may already be recorded. Refresh the result or polls to check your original receipt before choosing again.');
        else { fail(e); if (e instanceof ApiError && e.status === 409) { if (creating) setRejectedDraft(pending.current?.fingerprint ?? null); setPolls([]); setError(creating ? 'This draft conflicts with an earlier request. Edit the draft before creating a new request.' : 'The poll changed or your vote was already recorded. Refresh polls to see the current receipt before continuing.'); } }
      }
      return false;
    } finally { if (mutation.current === controller) mutation.current = null; if (!controller.signal.aborted) setBusy(false); }
  };
  const options = optionText.split('\n').map(v => v.trim()).filter(Boolean);
  const validDraft = rejectedDraft !== JSON.stringify({ question: question.trim(), options, durationMinutes: Number(duration) }) && question.trim().length > 0 && options.length >= 2 && options.length <= 8 && options.every(v => v.length <= 50 && !v.includes(',')) && new Set(options.map(v => v.toLowerCase())).size === options.length && /^\d+$/.test(duration) && Number(duration) >= 1 && Number(duration) <= 1440;
  const create = () => {
    if (!selected?.canManage || !validDraft) return;
    const payload = { question: question.trim(), options, durationMinutes: Number(duration) }, fingerprint = JSON.stringify(payload);
    if (pending.current?.eventId !== selected.id || pending.current.fingerprint !== fingerprint) pending.current = { eventId: selected.id, fingerprint, command: { ...payload, requestKey: Crypto.randomUUID() } };
    const attempt = pending.current; void act(signal => api.createPoll(attempt.eventId, attempt.command, signal), true);
  };
  const edit = (setter: (value: string) => void) => (value: string) => { setter(value); setUncertain(false); setError(''); };
  return <View style={{ gap: 18 }}>
    <Panel><Heading>Audience polls</Heading><Body>One confirmed choice per eligible university account. Results are snapshots; use Refresh polls for updated totals. Realtime results and result finalization are not yet available.</Body>
      {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
      <Button label="Refresh poll events" actionId="polls.events.refresh" disabled={busy || loading} secondary onPress={() => void loadEvents(eventPage)} />
      <Body>Events page {eventPage + 1}</Body><View style={uiStyles.row}><Button label="Previous poll events" actionId="polls.events.previous" disabled={busy || loading || !eventPage} secondary onPress={() => void loadEvents(eventPage - 1, false)} /><Button label="Next poll events" actionId="polls.events.next" disabled={busy || loading || !eventMore} secondary onPress={() => void loadEvents(eventPage + 1, false)} /></View>
      {events.map(event => <Button key={event.id} label={`Select ${event.title}`} actionId="polls.events.select" secondary disabled={busy || loading || selected?.id === event.id} onPress={() => chooseEvent(event)} />)}
      {!loading && !events.length ? <Body>No eligible events on this page.</Body> : null}
    </Panel>
    {loading ? <ActivityIndicator accessibilityLabel="Loading polls" color={colors.accent} /> : null}
    {selected ? <><Panel><Heading>{selected.title}</Heading><Body>Polls page {pollPage + 1}</Body><View style={uiStyles.row}>
      <Button label="Refresh polls" actionId="polls.refresh" disabled={loading || busy} secondary onPress={() => void loadPolls(selected.id, pollPage)} />
      <Button label="Previous polls" actionId="polls.previous" disabled={loading || busy || !pollPage} secondary onPress={() => void loadPolls(selected.id, pollPage - 1)} />
      <Button label="Next polls" actionId="polls.next" disabled={loading || busy || !pollMore} secondary onPress={() => void loadPolls(selected.id, pollPage + 1)} />
    </View>{!loading && !polls.length ? <Body>No polls on this page.</Body> : null}</Panel>
    {!loading && selected.canManage ? <Panel><Heading>Create a poll draft</Heading><Field label="Poll question" value={question} onChange={edit(setQuestion)} maxLength={200} disabled={busy} /><Field label="Options (one per line)" value={optionText} onChange={edit(setOptionText)} maxLength={408} multiline disabled={busy} /><Body>Use 2–8 distinct options, up to 50 characters each, without commas.</Body><Field label="Voting duration (minutes)" value={duration} onChange={edit(setDuration)} maxLength={4} keyboardType="number-pad" disabled={busy} /><Body>The voting window starts when you publish. Duration: 1–1440 minutes.</Body><Button label={uncertain ? 'Retry poll draft' : 'Create draft'} actionId="polls.create" disabled={busy || !validDraft} onPress={create} /></Panel> : null}
    {polls.map(poll => <PollCard key={`${poll.id}-${poll.revision}-${poll.myChoice}`} poll={poll} busy={busy || loading} act={act} />)}</> : null}
  </View>;
}

function PollCard({ poll, busy, act }: { poll: PollView; busy: boolean; act: (operation: (signal: AbortSignal) => Promise<PollView>, creating?: boolean, voting?: boolean) => Promise<boolean> }) {
  const colors = useContext(ThemeContext), [choice, setChoice] = useState<string | null>(null), [needsReceipt, setNeedsReceipt] = useState(false);
  return <Panel><View testID={`poll-${poll.id}`} style={{ gap: 14 }}><Heading>{poll.question}</Heading><Body>{poll.state} · {poll.totalVotes} confirmed votes</Body>
    {poll.closesAt ? <Body>Voting closes: {new Date(poll.closesAt).toLocaleString()}</Body> : null}
    {poll.results.map(result => <View key={result.option} style={{ gap: 5 }}><Text style={{ color: colors.text }}>{result.option}: {result.votes} votes · {result.percentage.toFixed(1)}%</Text><View style={{ height: 8, borderRadius: 4, backgroundColor: colors.badge }}><View style={{ height: 8, borderRadius: 4, width: `${result.percentage}%`, backgroundColor: colors.accent }} /></View></View>)}
    {poll.myChoice ? <Body>Your confirmed choice: {poll.myChoice}. This choice cannot be changed.</Body> : null}
    {needsReceipt ? <Body>Check the server receipt before making another choice.</Body> : null}
    {poll.canVote && !poll.myChoice ? <><Body>Select a choice, then confirm. A confirmed vote cannot be changed.</Body>{poll.options.map(option => <Button key={option} label={`Choose ${option}`} actionId="polls.vote.select" secondary disabled={busy || needsReceipt} onPress={() => setChoice(option)} />)}{choice ? <><Body>Confirm your permanent choice: {choice}</Body><Button label={`Confirm vote: ${choice}`} actionId="polls.vote.confirm" disabled={busy || needsReceipt} onPress={() => { setNeedsReceipt(true); void act(signal => api.votePoll(poll.id, choice, signal), false, true).then(ok => { if (ok) { setNeedsReceipt(false); setChoice(null); } }); }} /></> : null}</> : null}
    <Button label="Refresh result" actionId="polls.results" secondary disabled={busy} onPress={() => { void act(signal => api.pollResults(poll.id, signal)).then(ok => { if (ok) { setNeedsReceipt(false); setChoice(null); } }); }} />
    {poll.canManage && poll.state === 'DRAFT' ? <Button label="Publish poll" actionId="polls.publish" disabled={busy} onPress={() => void act(signal => api.publishPoll(poll.id, poll.revision, signal))} /> : null}
    {poll.canManage && poll.state === 'OPEN' ? <Button label="Close poll now" actionId="polls.close" disabled={busy} onPress={() => void act(signal => api.closePoll(poll.id, poll.revision, signal))} /> : null}
  </View></Panel>;
}
