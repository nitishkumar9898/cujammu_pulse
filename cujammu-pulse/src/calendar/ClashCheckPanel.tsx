import React, { useContext, useEffect, useRef, useState } from 'react';
import { Text, View } from 'react-native';
import { api, ApiError } from '../api';
import { Body, Button, ThemeContext } from '../ui';
import type { CalendarClashCheck } from './contracts';
import { campusDateTime } from './date';

export function ClashCheckPanel({ eventId, onSessionExpired }: { eventId: number; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [result, setResult] = useState<CalendarClashCheck | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), [eventId]);
  const check = async () => {
    request.current?.abort();
    const controller = new AbortController(); request.current = controller;
    setBusy(true); setError(''); setResult(null);
    try {
      const response = await api.calendarClashes(eventId, controller.signal);
      if (!controller.signal.aborted) setResult(response);
    } catch (failure) {
      if (!controller.signal.aborted) {
        if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
        else setError(failure instanceof Error ? failure.message : 'Schedule check could not be completed.');
      }
    } finally { if (!controller.signal.aborted) setBusy(false); }
  };
  return <View style={{ gap: 8 }}>
    <Button actionId="calendar.clashes.check" label={busy ? 'Checking schedule…' : 'Check schedule clashes'} secondary disabled={busy} onPress={() => void check()} />
    {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
    {result ? <View accessibilityLabel="Personal schedule check" style={{ gap: 8, padding: 14, borderWidth: 1, borderColor: colors.border, borderRadius: 10 }}>
      <Text accessibilityRole="header" style={{ color: colors.text, fontWeight: '700' }}>Your confirmed event commitments</Text>
      <Body>Checked schedule: {campusDateTime(result.startsAt)}{result.endsAt ? ` to ${campusDateTime(result.endsAt)}` : ' · end time unknown'} (IST).</Body>
      {result.endsAt === null ? <Body>This event has no confirmed end time. A complete overlap check is not possible.</Body> : null}
      {result.items.length === 0 ? <Body>{result.endsAt === null ? 'No candidate confirmed events were returned. This event’s duration is still unknown.' : 'No overlap found among your known confirmed event intervals. Missing schedule data can still hide a clash.'}</Body> : null}
      {result.items.map(({ entry, assessment }) => <View key={entry.registrationId} style={{ gap: 4 }}>
        <Text style={{ color: assessment === 'OVERLAP' ? colors.error : colors.text, fontWeight: '600' }}>{assessment === 'OVERLAP' ? 'Overlapping event' : 'Possible clash — duration unknown'}: {entry.title}</Text>
        <Body>{campusDateTime(entry.eventDate)}{entry.eventEndsAt ? ` to ${campusDateTime(entry.eventEndsAt)}` : ' · end time unknown'} (IST) · {entry.venue}</Body>
      </View>)}
      {result.hasMore ? <Body>Only the first 50 candidate commitments are shown. More entries remain unchecked in this view.</Body> : null}
      <Body>Classes, exams, matches, duties and travel time are not included. This is advice only; it does not reserve a seat or change registration eligibility.</Body>
      <Body>Schedule revision {result.lifecycleVersion} · checked {new Date(result.checkedAt).toLocaleTimeString()}. Recheck after schedule changes.</Body>
      <Button label="Hide schedule check" secondary onPress={() => setResult(null)} />
    </View> : null}
  </View>;
}
