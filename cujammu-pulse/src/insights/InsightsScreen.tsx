import React, { useContext, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import { Body, Button, Field, Heading, Panel, ThemeContext } from '../ui';
import type { Feedback, FeedbackSummary } from './contracts';
import type { CalendarEntry } from '../calendar/contracts';
import type { EventSummary } from '../event-operations/contracts';

export function InsightsScreen({ role, onSessionExpired }: { role: string; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [calendarEvents, setCalendarEvents] = useState<CalendarEntry[]>([]);
  const [managedEvents, setManagedEvents] = useState<EventSummary[]>([]);
  const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
  const [summary, setSummary] = useState<FeedbackSummary | null>(null);
  const [receipt, setReceipt] = useState<Feedback | null>(null);
  const [checked, setChecked] = useState(false);
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [uncertain, setUncertain] = useState(false);
  const controller = useRef<AbortController | null>(null);
  const id = selectedEventId;
  const validId = id !== null && Number.isSafeInteger(id) && id > 0;
  const canReview = ['ADMIN', 'FACULTY', 'ORGANIZER'].includes(role);
  useEffect(() => {
    const pending = new AbortController();
    void api.insightEvents(0,pending.signal).then(result=>setCalendarEvents(result.items)).catch(() => { if (!pending.signal.aborted) setCalendarEvents([]); });
    if (canReview) void api.eventOperationsMine(pending.signal).then(setManagedEvents).catch(() => { if (!pending.signal.aborted) setManagedEvents([]); });
    return () => { pending.abort(); controller.current?.abort(); };
  }, [canReview]);

  const selectEvent = (value: number) => {
    controller.current?.abort(); setBusy(false); setSelectedEventId(value); setReceipt(null); setSummary(null);
    setChecked(false); setRating(5); setComment(''); setError(''); setUncertain(false);
  };
  const run = async (operation: 'mine' | 'summary' | 'submit') => {
    if (!validId || id === null || busy) return;
    controller.current?.abort(); const pending = new AbortController(); controller.current = pending;
    setBusy(true); setError('');
    if (operation === 'summary') setSummary(null);
    try {
      if (operation === 'summary') {
        const result = await api.feedbackSummary(id, pending.signal);
        if (!pending.signal.aborted) { if (result.eventId !== id) throw new Error('The response did not match this event.'); setSummary(result); }
      } else {
        const result = operation === 'mine' ? await api.feedbackMine(id, pending.signal) : await api.submitFeedback(id, rating, comment.trim(), pending.signal);
        if (!pending.signal.aborted) {
          if (result && result.eventId !== id) throw new Error('The response did not match this event.');
          setReceipt(result); setChecked(true); setUncertain(false);
          if (result) { setComment(''); setRating(result.rating); }
        }
      }
    } catch (failure) {
      if (pending.signal.aborted) return;
      if (failure instanceof ApiError && failure.status === 401) { setReceipt(null); setSummary(null); setComment(''); onSessionExpired(); }
      else { if (operation === 'submit') setUncertain(true); setError(failure instanceof Error ? failure.message : 'Feedback could not be loaded. Try again.'); }
    } finally { if (!pending.signal.aborted) setBusy(false); }
  };
  return <View style={{ gap: 20 }}>
    <Panel><Heading>Feedback & insights</Heading><Body>Share feedback after an event has been completed. Feedback is linked to your account and may be reviewed by authorized event staff; it is not anonymous.</Body><Text style={{ color: colors.text, fontWeight: '700' }}>Choose one of your registered events</Text>{calendarEvents.map(event=><Pressable key={`registered-${event.eventId}`} accessibilityRole="radio" accessibilityState={{ checked: selectedEventId===event.eventId, disabled: busy }} onPress={()=>selectEvent(event.eventId)} disabled={busy} style={{ padding: 12, borderWidth: 1, borderColor: selectedEventId===event.eventId?colors.accent:colors.border, borderRadius: 10 }}><Text style={{ color: colors.text, fontWeight: '700' }}>{event.title}</Text><Text style={{ color: colors.muted }}>{event.status} · {new Date(event.eventDate).toLocaleDateString()}</Text></Pressable>)}{canReview?<><Text style={{ color: colors.text, fontWeight: '700', marginTop: 8 }}>Events you manage</Text>{managedEvents.map(event=><Pressable key={`managed-${event.id}`} accessibilityRole="radio" accessibilityState={{ checked: selectedEventId===event.id, disabled: busy }} onPress={()=>selectEvent(event.id)} disabled={busy} style={{ padding: 12, borderWidth: 1, borderColor: selectedEventId===event.id?colors.accent:colors.border, borderRadius: 10 }}><Text style={{ color: colors.text, fontWeight: '700' }}>{event.title}</Text><Text style={{ color: colors.muted }}>{event.status} · {new Date(event.eventDate).toLocaleDateString()}</Text></Pressable>)}</>:null}{calendarEvents.length===0&&(!canReview||managedEvents.length===0)?<Body>No registered or managed events are available to select.</Body>:null}<Body>New feedback requires a verified enrollment and confirmed registration. Event access is checked again by the service.</Body><Button actionId="insights.feedback.mine" label={busy ? 'Loading…' : 'Check my feedback'} disabled={busy || !validId} onPress={() => void run('mine')} /></Panel>
    {error ? <Panel><Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text></Panel> : null}
    {receipt ? <Panel><Heading>Your feedback is recorded</Heading><Text accessibilityLiveRegion="polite" style={{ color: colors.text }}>Receipt {receipt.id}</Text><Body>Rating: {receipt.rating} / 5</Body>{receipt.comment ? <Body>{receipt.comment}</Body> : null}<Body>Your response is saved. Changes require the university's reviewed correction process.</Body></Panel> : <Panel><Heading>Submit feedback</Heading><Body>{checked ? 'No saved response was found for your account on this event.' : 'You can check for an existing response before submitting.'}</Body><Text style={{ color: colors.text, fontWeight: '700' }}>Your rating</Text><View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>{[1, 2, 3, 4, 5].map(value => <Pressable key={value} accessibilityRole="radio" aria-checked={rating === value} accessibilityState={{ checked: rating === value, disabled: busy || uncertain }} disabled={busy || uncertain} onPress={() => setRating(value)} style={{ padding: 14, minWidth: 48, minHeight: 48, borderRadius: 8, borderWidth: 1, borderColor: colors.border, backgroundColor: rating === value ? colors.accent : colors.surface }}><Text style={{ color: rating === value ? colors.accentText : colors.text }}>{value} / 5</Text></Pressable>)}</View><Field label="Comment (optional)" value={comment} onChange={setComment} maxLength={2000} multiline disabled={busy || uncertain} /><Body>Do not include sensitive personal information about yourself or others.</Body>{uncertain ? <Body>Submission was not confirmed. Check your saved response, or retry this unchanged response. An identical retry returns the same receipt.</Body> : null}<Button actionId="insights.feedback.submit" label={busy ? 'Submitting…' : uncertain ? 'Retry same feedback' : 'Submit feedback'} disabled={busy || !validId} onPress={() => void run('submit')} /></Panel>}
    {canReview ? <Panel><Heading>Confidential event review</Heading><Body>Only authorized managers of this event can review responses. This view is not a published or participant-facing aggregate.</Body><Button actionId="insights.feedback.read" label="Load event insights" disabled={busy || !validId || !managedEvents.some(event=>event.id===id)} onPress={() => void run('summary')} />{summary ? <><Heading>Summary for selected event</Heading><Body>{summary.totalResponses === 0 ? 'No responses have been submitted.' : `Average rating: ${summary.averageRating.toFixed(1)} / 5 · Responses: ${summary.totalResponses}`}</Body><Body>Showing the most recent {summary.recent.length} confidential responses.</Body>{summary.recent.map(item => <View key={item.id} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 12, gap: 6 }}><Text style={{ color: colors.text, fontWeight: '700' }}>Rating: {item.rating} / 5</Text>{item.comment ? <Body>{item.comment}</Body> : null}</View>)}</> : null}</Panel> : null}
    {busy ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading feedback" /> : null}
  </View>;
}
