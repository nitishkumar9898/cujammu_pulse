import React, { useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import type { CalendarEntry } from './contracts';
import { Body, Button, Heading, Panel, ThemeContext } from '../ui';
import { CAMPUS_TIME_ZONE, campusDate } from './date';

function dayLabel(value: string): string {
  const date = campusDate(value);
  return Number.isNaN(date.getTime()) ? 'Date unavailable' : new Intl.DateTimeFormat(undefined, {
    timeZone: CAMPUS_TIME_ZONE, weekday: 'long', month: 'long', day: 'numeric', year: 'numeric',
  }).format(date);
}

function timeLabel(value: string): string {
  const date = campusDate(value);
  return Number.isNaN(date.getTime()) ? 'Time unavailable' : new Intl.DateTimeFormat(undefined, {
    timeZone: CAMPUS_TIME_ZONE, hour: 'numeric', minute: '2-digit',
  }).format(date);
}

function statusLabel(value: string): string {
  const labels: Record<string, string> = {
    REGISTERED: 'Registration confirmed', ATTENDED: 'Attendance recorded',
    WAITLISTED: 'Waitlisted', CANCELLED: 'Registration cancelled',
  };
  return labels[value] ?? `Status: ${value.toLowerCase().replaceAll('_', ' ')}`;
}

export function CalendarScreen({ onSessionExpired }: { onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [items, setItems] = useState<CalendarEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError('');
    try {
      const result = await api.calendarPage(0, signal);
      if (!signal?.aborted) { setItems(result.items); setPage(result.page); setHasMore(result.hasMore); }
    } catch (cause) {
      if (!signal?.aborted) {
        if (cause instanceof ApiError && cause.status === 401) onSessionExpired();
        else setError(cause instanceof Error ? cause.message : 'Calendar could not be loaded.');
      }
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [onSessionExpired]);

  const loadMore = useCallback(async () => {
    if (loading || loadingMore || !hasMore) return;
    setLoadingMore(true);
    setError('');
    try {
      const result = await api.calendarPage(page + 1);
      setItems(current => {
        const byId = new Map(current.map(item => [item.registrationId, item]));
        result.items.forEach(item => byId.set(item.registrationId, item));
        return [...byId.values()];
      });
      setPage(result.page);
      setHasMore(result.hasMore);
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) onSessionExpired();
      else setError(cause instanceof Error ? cause.message : 'More calendar entries could not be loaded.');
    } finally { setLoadingMore(false); }
  }, [hasMore, loading, loadingMore, onSessionExpired, page]);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  const sortedItems = useMemo(() => [...items].sort((a, b) =>
    campusDate(a.eventDate).getTime() - campusDate(b.eventDate).getTime() || a.registrationId - b.registrationId), [items]);

  const groups = useMemo(() => {
    const byDay = new Map<string, CalendarEntry[]>();
    for (const item of sortedItems) {
      const date = campusDate(item.eventDate);
      const key = Number.isNaN(date.getTime()) ? 'unknown' : new Intl.DateTimeFormat('en-CA', {
        timeZone: CAMPUS_TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit',
      }).format(date);
      byDay.set(key, [...(byDay.get(key) ?? []), item]);
    }
    return [...byDay.entries()].map(([key, entries]) => ({
      key,
      label: key === 'unknown' ? 'Date unavailable' : dayLabel(entries[0]!.eventDate),
      entries,
    }));
  }, [sortedItems]);

  return <View style={{ gap: 16 }}>
    <Panel>
      <Heading>My campus calendar</Heading>
      <Body>Your registered campus events, arranged by date.</Body>
      <Body>Event times use campus time (IST). Calendar coverage is limited: approved class and exam timetables, selected matches, and assigned duties are not connected yet.</Body>
      {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
      <Button label={loading ? 'Refreshing calendar…' : 'Refresh calendar'} secondary disabled={loading || loadingMore} onPress={() => void load()} />
    </Panel>

    {loading && items.length === 0 ? <ActivityIndicator accessibilityLabel="Loading calendar" color={colors.accent} /> : null}
    {!loading && items.length === 0 && !error ? <Panel>
      <Heading>No registered events yet</Heading>
      <Body>When you register for a campus event, its date and venue will appear here.</Body>
    </Panel> : null}
    {!loading && items.length === 0 && error ? <Body>Your calendar could not be refreshed. Try again when your connection is available.</Body> : null}

    {groups.map(group => <View key={group.key} style={{ gap: 10 }} accessibilityRole="list">
      <Text accessibilityRole="header" style={{ color: colors.text, fontSize: 17, fontWeight: '700', paddingHorizontal: 2 }}>{group.label}</Text>
      {group.entries.map(item => <Panel key={item.registrationId}>
        <Heading>{item.title}</Heading>
        <Body>{timeLabel(item.eventDate)} · {item.venue}</Body>
        <Body>{item.eventEndsAt ? `Ends ${dayLabel(item.eventEndsAt)} at ${timeLabel(item.eventEndsAt)} (IST)` : 'End time not supplied; duration is unknown.'}</Body>
        <Text style={{ color: item.eventStatus === 'CANCELLED' ? colors.error : colors.muted, fontWeight: '600' }}>
          {item.eventStatus === 'CANCELLED' ? 'Event cancelled — this occurrence will not take place.'
            : item.eventStatus === 'PENDING' ? 'Event awaiting approval — this schedule is not confirmed.'
            : item.eventStatus === 'REJECTED' ? 'Event not approved — this schedule is not confirmed.'
            : item.eventStatus === 'COMPLETED' ? 'Event completed'
            : item.eventStatus === 'APPROVED' ? 'Current approved schedule' : 'Event lifecycle status unavailable in this response.'}
        </Text>
        {item.lifecycleVersion !== null ? <Body>Event source · revision {item.lifecycleVersion}. Refresh for schedule changes.</Body> : null}
        <Text style={{ color: colors.muted, fontSize: 14, fontWeight: '600' }}>{statusLabel(item.status)}</Text>
      </Panel>)}
    </View>)}
    {hasMore ? <Panel>
      <Body>Showing {items.length} registered events. More entries are available.</Body>
      <Button label={loadingMore ? 'Loading more events…' : 'Load more events'} disabled={loading || loadingMore} onPress={() => void loadMore()} />
    </Panel> : null}
  </View>;
}
