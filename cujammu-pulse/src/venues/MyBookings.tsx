import React, { useCallback, useContext, useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import type { MyFacilityBookingPage } from '../contracts';
import { Body, Button, Field, Heading, Panel, ThemeContext } from '../ui';

export function MyBookings({ onSessionExpired }: { onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [page, setPage] = useState<MyFacilityBookingPage | null>(null);
  const [pageIndex, setPageIndex] = useState(0);
  const [revision, setRevision] = useState(0);
  const [reasons, setReasons] = useState<Record<number, string>>({});
  const [busyId, setBusyId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const load = useCallback(async (signal?: AbortSignal) => {
    try {
      const result = await api.myFacilityBookings(pageIndex, signal);
      if (!signal?.aborted) { setPage(result); setError(''); }
    } catch (failure) {
      if (!signal?.aborted) {
        if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
        else setError(failure instanceof Error ? failure.message : 'Your venue requests could not be loaded.');
      }
    } finally { if (!signal?.aborted) setLoading(false); }
  }, [onSessionExpired, pageIndex]);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true);
    void load(controller.signal);
    return () => controller.abort();
  }, [load, revision]);

  const cancel = async (id: number, version: number) => {
    const reason = (reasons[id] ?? '').trim();
    if (!reason || busyId !== null) return;
    setBusyId(id); setError('');
    try {
      await api.cancelFacilityBooking(id, version, reason);
      setReasons(current => { const next = { ...current }; delete next[id]; return next; });
      setRevision(current => current + 1);
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
      else { setError(failure instanceof Error ? failure.message : 'Cancellation could not be confirmed.'); setRevision(current => current + 1); }
    } finally { setBusyId(null); }
  };

  return <Panel>
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
      <View style={{ flex: 1 }}><Heading>My venue requests</Heading><Body>Private status history for your account. Only pending requests can be cancelled here.</Body></View>
      <Button label="Refresh my requests" secondary disabled={loading || busyId !== null} onPress={() => setRevision(value => value + 1)} />
    </View>
    {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
    {loading ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading your venue requests" />
      : !page || page.items.length === 0 ? <Body>{pageIndex === 0 ? 'You have no venue requests yet.' : 'No more requests on this page.'}</Body>
      : page.items.map(item => <View key={item.id} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 14, gap: 8 }}>
        <Text style={{ color: colors.text, fontWeight: '700' }}>{item.facilityName} · {item.title}</Text>
        <Body>{campusTime(item.startTime)} – {campusTime(item.endTime)} · {humanStatus(item.status)} · Request {item.id}</Body>
        {item.status === 'PENDING' ? <>
          <Field label="Reason to cancel pending request" value={reasons[item.id] ?? ''}
            onChange={value => setReasons(current => ({ ...current, [item.id]: value }))} maxLength={300} disabled={busyId !== null} />
          <Button label={busyId === item.id ? 'Cancelling…' : 'Cancel pending request'} secondary
            disabled={busyId !== null || !(reasons[item.id] ?? '').trim()} onPress={() => void cancel(item.id, item.version)} />
        </> : null}
      </View>)}
    {page && (pageIndex > 0 || page.hasNext) ? <View style={{ flexDirection: 'row', gap: 10 }}>
      <Button label="Previous" secondary disabled={pageIndex === 0 || loading || busyId !== null} onPress={() => setPageIndex(value => value - 1)} />
      <Button label="Next" secondary disabled={!page.hasNext || loading || busyId !== null} onPress={() => setPageIndex(value => value + 1)} />
    </View> : null}
  </Panel>;
}

function campusTime(value: string) { return `${value.replace('T', ' ').slice(0, 16)} IST`; }
function humanStatus(value: string) { return value.toLowerCase().replaceAll('_', ' '); }
