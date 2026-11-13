import React, { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import type { NotificationItem } from './contracts';
import { Body, Button, Heading, Panel, ThemeContext } from '../ui';

export function NotificationsScreen({ onSessionExpired }: { onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [errorScope, setErrorScope] = useState<'initial' | 'more' | 'command'>('initial');
  const [hasLoaded, setHasLoaded] = useState(false);
  const [announcement, setAnnouncement] = useState('');
  // Synchronous guard also blocks two presses before React commits disabled controls.
  const actionPending = useRef(false);
  const sessionExpired = useRef(onSessionExpired);
  sessionExpired.current = onSessionExpired;
  const [revision, setRevision] = useState(0);
  const [nextBeforeId, setNextBeforeId] = useState<number | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const hasNext = nextBeforeId !== null;

  const load = useCallback(async (signal?: AbortSignal) => {
    try {
      const current = await api.notifications(undefined, signal);
      if (!signal?.aborted) { setItems(current.items); setNextBeforeId(current.nextBeforeId); setError(''); setHasLoaded(true); }
    } catch (failure) {
      if (!signal?.aborted) {
        if (failure instanceof ApiError && failure.status === 401) sessionExpired.current();
        else { setErrorScope('initial'); setError(failure instanceof Error ? failure.message : 'Notifications could not be loaded.'); }
      }
    } finally { if (!signal?.aborted) setLoading(false); }
  }, []);

  const loadMore = async () => {
    if (nextBeforeId == null || loadingMore || loading || actionPending.current) return;
    actionPending.current = true;
    setLoadingMore(true); setError(''); setAnnouncement('');
    try {
      const next = await api.notifications(nextBeforeId);
      setItems(current => [...current, ...next.items.filter(item => !current.some(existing => existing.id === item.id))]);
      setNextBeforeId(next.nextBeforeId);
      setAnnouncement('Older notifications loaded.');
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
      else { setErrorScope('more'); setError(failure instanceof Error ? failure.message : 'More notifications could not be loaded.'); }
    } finally { actionPending.current = false; setLoadingMore(false); }
  };

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    void load(controller.signal);
    return () => controller.abort();
  }, [load, revision]);

  const markRead = async (id: number) => {
    if (loading || actionPending.current) return;
    actionPending.current = true;
    setBusyId(id); setError(''); setAnnouncement('');
    try {
      await api.markNotificationRead(id);
      setItems(current => current.map(item => item.id === id ? { ...item, read: true } : item));
      setAnnouncement('Notification marked read.');
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
      else { setErrorScope('command'); setError(failure instanceof Error ? failure.message : 'The notification could not be updated.'); }
    } finally { actionPending.current = false; setBusyId(null); }
  };

  const acknowledge = async (id: number) => {
    if (loading || actionPending.current) return;
    actionPending.current = true;
    setBusyId(id); setError(''); setAnnouncement('');
    try {
      await api.acknowledgeNotification(id);
      setItems(current => current.map(item => item.id === id
        ? { ...item, read: true, acknowledged: true } : item));
      setAnnouncement('Notification acknowledged.');
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
      else { setErrorScope('command'); setError(failure instanceof Error ? failure.message : 'The notification could not be acknowledged.'); }
    } finally { actionPending.current = false; setBusyId(null); }
  };

  const refresh = () => {
    if (loading || actionPending.current) return;
    setLoading(true); setError(''); setAnnouncement('');
    setRevision(value => value + 1);
  };

  return <View style={{ gap: 16 }}>
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
      <View style={{ flex: 1 }}><Heading>Notifications</Heading><Body>Private updates for your account. Open this screen again or refresh to check for new notices.</Body></View>
      <Button label="Refresh" secondary disabled={loading || loadingMore || busyId !== null} onPress={refresh} />
    </View>
    <Text role="status" accessibilityLiveRegion="polite" style={{ color: colors.muted }}>{announcement || (loadingMore ? 'Loading older notifications…' : busyId !== null ? 'Saving notification…' : '')}</Text>
    {error ? <Panel><Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text>{errorScope !== 'command' ? <Button label="Retry" disabled={loading || loadingMore || busyId !== null} onPress={errorScope === 'more' ? () => void loadMore() : refresh} /> : null}</Panel> : null}
    {loading ? <Panel><ActivityIndicator color={colors.accent} accessibilityLabel="Loading notifications" /><Body>Loading your notifications…</Body></Panel>
      : items.length === 0 && hasLoaded && !error ? <Panel><Heading>You’re all caught up</Heading><Body>Important updates for your account will appear here.</Body></Panel>
      : items.map(item => <Panel key={item.id}>
        <View style={{ flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
          <View style={{ flex: 1, gap: 8 }}>
            <Text style={{ color: colors.text, fontSize: 16, lineHeight: 24, fontWeight: item.read ? '400' : '700' }}>{item.message}</Text>
            <Text style={{ color: colors.muted, fontSize: 13 }}>{item.createdAt ? new Date(item.createdAt).toLocaleString() : 'Time unavailable'} · {item.read ? 'Read' : 'Unread'}</Text>
          </View>
          {!item.read ? <Button label={busyId === item.id ? 'Saving…' : 'Mark read'} secondary disabled={loadingMore || busyId !== null} onPress={() => void markRead(item.id)} /> : null}
        </View>
        {!item.acknowledged ? <Button label="Acknowledge" secondary disabled={loadingMore || busyId !== null} onPress={() => void acknowledge(item.id)} /> : <Text style={{ color: colors.muted }}>Acknowledged</Text>}
      </Panel>)}
    {!loading && hasNext ? <Button label={loadingMore ? 'Loading…' : 'Load older notifications'} secondary disabled={loadingMore || busyId !== null} onPress={() => void loadMore()} /> : null}
  </View>;
}
