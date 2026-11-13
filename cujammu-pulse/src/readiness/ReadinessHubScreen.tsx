import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import type { EventSummary } from '../event-operations/contracts';
import { Body, Choice, Heading, Panel } from '../ui';
import { ReadinessScreen } from './ReadinessScreen';

export function ReadinessHubScreen({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [events, setEvents] = useState<EventSummary[]>([]); const [eventId, setEventId] = useState<number | null>(null); const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  useEffect(() => { const controller = new AbortController(); void api.eventOperationsMine(controller.signal).then(items => { if (!controller.signal.aborted) { setEvents(items); setEventId(items[0]?.id ?? null); } }).catch(e => { if (!controller.signal.aborted) { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Approved events could not be loaded.'); } }).finally(() => { if (!controller.signal.aborted) setLoading(false); }); return () => controller.abort(); }, [onSessionExpired]);
  return <View style={{ gap: 14 }}><Panel><Heading>Event readiness</Heading><Body>Review available checks and accountable tasks for events you manage. Volunteer roster counts are informational; approved staffing targets, referee coverage, shift availability and accessibility evidence are not configured, so this summary cannot certify readiness.</Body>{error ? <Text accessibilityRole="alert">{error}</Text> : null}{loading ? <ActivityIndicator accessibilityLabel="Loading approved events" /> : events.length === 0 ? <Body>No approved events are available to review.</Body> : events.map(event => <Choice key={event.id} label={event.title} checked={eventId === event.id} onChange={() => setEventId(event.id)} />)}</Panel>{eventId ? <ReadinessScreen key={eventId} eventId={eventId} onSessionExpired={onSessionExpired} /> : null}</View>;
}
