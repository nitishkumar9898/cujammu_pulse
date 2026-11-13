import React, { useCallback, useContext, useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import type { PendingFacilityBooking, PendingFacilityBookingPage } from '../contracts';
import { Body, Button, Field, Heading, Panel, ThemeContext } from '../ui';

export function PendingBookings({ onSessionExpired }: { onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [page, setPage] = useState<PendingFacilityBookingPage | null>(null);
  const [pageIndex, setPageIndex] = useState(0);
  const [revision, setRevision] = useState(0);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');

  const load = useCallback(async (signal?: AbortSignal) => {
    try {
      const result = await api.pendingFacilityBookings(pageIndex, signal);
      if (!signal?.aborted) { setPage(result); setError(''); }
    } catch (failure) {
      if (!signal?.aborted) {
        if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
        else setError(failure instanceof Error ? failure.message : 'Pending booking requests could not be loaded.');
      }
    } finally { if (!signal?.aborted) setLoading(false); }
  }, [onSessionExpired, pageIndex]);

  useEffect(() => {
    const controller = new AbortController(); setLoading(true);
    void load(controller.signal);
    return () => controller.abort();
  }, [load, revision]);

  const decide = async (booking: PendingFacilityBooking, decision: 'APPROVE' | 'REJECT') => {
    if (busyId !== null) return;
    const reason = rejectionReason.trim();
    if (decision === 'REJECT' && !reason) { setError('Enter a short reason before rejecting this request.'); return; }
    setBusyId(booking.id); setError('');
    try {
      await api.decideFacilityBooking(booking.id, booking.version, decision, decision === 'REJECT' ? reason : '');
      setRejectionReason(''); setRevision(value => value + 1);
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
      else { setError(failure instanceof Error ? failure.message : 'The review could not be saved.'); setRevision(value => value + 1); }
    } finally { setBusyId(null); }
  };

  return <Panel>
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
      <View style={{ flex: 1 }}><Heading>Venue booking review</Heading><Body>Pending requests reserve their interval while awaiting review. Approval rechecks the conflict guard.</Body></View>
      <Button label="Refresh requests" secondary disabled={loading || busyId !== null} onPress={() => setRevision(value => value + 1)} />
    </View>
    {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
    {loading ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading pending venue bookings" />
      : !page || page.items.length === 0 ? <Body>No pending venue requests on this page.</Body>
      : page.items.map(booking => <View key={booking.id} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 16, gap: 10 }}>
        <Text style={{ color: colors.text, fontWeight: '700', fontSize: 17 }}>{booking.facilityName} · {booking.title}</Text>
        <Body>{booking.requesterName}{booking.requesterDepartment ? ` · ${booking.requesterDepartment}` : ''} · {campusTime(booking.startTime)} – {campusTime(booking.endTime)}</Body>
        <Body>{booking.requestedMics} microphones · {booking.requestedProjector ? 'Projector requested' : 'No projector requested'}</Body>
        <Field label="Rejection reason (required to reject)" value={rejectionReason} onChange={setRejectionReason} maxLength={300} disabled={busyId !== null} />
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 10 }}>
          <Button label={busyId === booking.id ? 'Saving…' : 'Approve'} disabled={busyId !== null} onPress={() => void decide(booking, 'APPROVE')} />
          <Button label={busyId === booking.id ? 'Saving…' : 'Reject'} secondary disabled={busyId !== null || !rejectionReason.trim()} onPress={() => void decide(booking, 'REJECT')} />
        </View>
      </View>)}
    {page && (pageIndex > 0 || page.hasNext) ? <View style={{ flexDirection: 'row', gap: 10 }}>
      <Button label="Previous" secondary disabled={pageIndex === 0 || loading || busyId !== null} onPress={() => { setPageIndex(value => value - 1); setRejectionReason(''); }} />
      <Button label="Next" secondary disabled={!page.hasNext || loading || busyId !== null} onPress={() => { setPageIndex(value => value + 1); setRejectionReason(''); }} />
    </View> : null}
  </Panel>;
}

function campusTime(value: string) { return `${value.replace('T', ' ').slice(0, 16)} IST`; }
