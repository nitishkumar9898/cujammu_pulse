import React, { useContext, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError, newRequestKey } from '../api';
import type { Facility, FacilityBookingReceipt, FacilitySlot, FacilitySuggestions } from '../contracts';
import { Body, Button, Choice, Field, Panel, ThemeContext } from '../ui';

export function AvailabilityCard({ facility, canBook, onSessionExpired }: { facility: Facility; canBook: boolean; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [duration, setDuration] = useState('60');
  const [days, setDays] = useState('14');
  const [result, setResult] = useState<FacilitySuggestions | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [selectedSlot, setSelectedSlot] = useState<FacilitySlot | null>(null);
  const [title, setTitle] = useState('');
  const [mics, setMics] = useState('0');
  const [projector, setProjector] = useState(false);
  const [bookingAttempt, setBookingAttempt] = useState<{ fingerprint: string; requestKey: string } | null>(null);
  const [receipt, setReceipt] = useState<FacilityBookingReceipt | null>(null);
  const [cancellationReason, setCancellationReason] = useState('');

  const search = async () => {
    if (busy) return;
    const durationMinutes = Number(duration);
    const searchDays = Number(days);
    if (!Number.isInteger(durationMinutes) || durationMinutes < 15 || durationMinutes > 720
        || !Number.isInteger(searchDays) || searchDays < 1 || searchDays > 30) {
      setError('Choose a duration from 15 to 720 minutes and a search range from 1 to 30 days.');
      setResult(null);
      return;
    }
    setBusy(true); setError(''); setResult(null);
    try { setResult(await api.facilitySuggestions(facility.id, durationMinutes, searchDays)); setReceipt(null); setSelectedSlot(null); setBookingAttempt(null); }
    catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
      else setError(failure instanceof Error ? failure.message : 'Suggestions are unavailable. Try again.');
    } finally { setBusy(false); }
  };

  const book = async () => {
    if (!selectedSlot || !canBook || busy) return;
    const requestedMics = Number(mics);
    if (!title.trim() || title.trim().length > 200 || !Number.isInteger(requestedMics) || requestedMics < 0 || requestedMics > 100) {
      setError('Enter a short booking title and a microphone count from 0 to 100.'); return;
    }
    const command = { title: title.trim(), startTime: selectedSlot.startTime, endTime: selectedSlot.endTime, requestedMics, requestedProjector: projector };
    const fingerprint = JSON.stringify(command);
    const requestKey = bookingAttempt?.fingerprint === fingerprint ? bookingAttempt.requestKey : newRequestKey();
    setBookingAttempt({ fingerprint, requestKey }); setBusy(true); setError('');
    try {
      const saved = await api.bookFacility(facility.id, { ...command, requestKey });
      setReceipt(saved); setSelectedSlot(null); setBookingAttempt(null); setResult(null);
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
      else setError(failure instanceof Error ? failure.message : 'The booking request could not be completed. Retry unchanged details safely.');
    } finally { setBusy(false); }
  };

  const cancelPending = async () => {
    if (!receipt || receipt.status !== 'PENDING' || !cancellationReason.trim() || busy) return;
    setBusy(true); setError('');
    try { setReceipt(await api.cancelFacilityBooking(receipt.id, receipt.version, cancellationReason.trim())); }
    catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
      else setError(failure instanceof Error ? failure.message : 'Cancellation could not be confirmed. Retry with the same reason.');
    } finally { setBusy(false); }
  };

  return <Panel>
    <View style={{ gap: 6 }}>
      <Text style={{ color: colors.text, fontWeight: '700', fontSize: 18 }}>{facility.name}</Text>
      <Body>{[facility.facilityType.replaceAll('_', ' '), facility.location, facility.capacity == null ? 'Capacity not listed' : `Capacity ${facility.capacity}`].filter(Boolean).join(' · ')}</Body>
    </View>
    <Body>Find tentative reservation-free times. Suggestions are advisory and do not reserve this facility.</Body>
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12 }}>
      <View style={{ flexGrow: 1, flexBasis: 180 }}><Field label="Required duration (minutes)" value={duration} onChange={setDuration} maxLength={3} keyboardType="number-pad" disabled={busy} /></View>
      <View style={{ flexGrow: 1, flexBasis: 180 }}><Field label="Search next (days)" value={days} onChange={setDays} maxLength={2} keyboardType="number-pad" disabled={busy} /></View>
    </View>
    <Button label={busy ? 'Checking reservations…' : 'Find available-time suggestions'} disabled={busy} onPress={() => void search()} />
    {busy ? <ActivityIndicator color={colors.accent} accessibilityLabel="Checking reservation conflicts" /> : null}
    {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
    {result ? <View style={{ gap: 10 }}>
      <Text style={{ color: colors.text, fontWeight: '700' }}>{result.items.length ? 'Tentative times' : 'No reservation-free time found in this range'}</Text>
      <Body>Campus local time (Asia/Kolkata). Checked known active facility bookings only. These results can become unavailable and require rechecking when you book.</Body>
      {result.items.map((slot, index) => <View key={`${slot.startTime}-${index}`} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 10 }}>
        <Text style={{ color: colors.text, paddingVertical: 8 }}>{formatCampusTime(slot.startTime)} – {formatCampusTime(slot.endTime)}</Text>
        {canBook ? <Button label="Choose this time" secondary disabled={busy} onPress={() => { setSelectedSlot(slot); setBookingAttempt(null); setError(''); setReceipt(null); }} /> : null}
      </View>)}
      <Body>Not checked: {result.unknownConstraints.join(', ')}.</Body>
    </View> : null}
    {receipt ? <View style={{ gap: 8 }}><Text accessibilityLiveRegion="polite" style={{ color: colors.text, fontWeight: '700' }}>{receipt.status === 'PENDING' ? 'Request submitted · awaiting administrator review' : receipt.status === 'CONFIRMED' ? 'Reservation approved' : receipt.status === 'CANCELLED' ? 'Pending request cancelled' : 'Request not approved'}</Text><Body>Reference {receipt.id} · {formatCampusTime(receipt.startTime)} – {formatCampusTime(receipt.endTime)}</Body>
      {receipt.status === 'PENDING' ? <><Field label="Reason to cancel this pending request" value={cancellationReason} onChange={setCancellationReason} maxLength={300} disabled={busy} /><Button label={busy ? 'Cancelling…' : 'Cancel pending request'} secondary disabled={busy || !cancellationReason.trim()} onPress={() => void cancelPending()} /></> : null}</View> : null}
    {selectedSlot && canBook ? <View style={{ gap: 12 }}>
      <Text style={{ color: colors.text, fontWeight: '700' }}>Request this tentative time</Text>
      <Body>{formatCampusTime(selectedSlot.startTime)} – {formatCampusTime(selectedSlot.endTime)}. The request reserves this interval while pending; an administrator rechecks availability before approval.</Body>
      <Field label="Booking title" value={title} onChange={setTitle} maxLength={200} disabled={busy} />
      <Field label="Microphones requested" value={mics} onChange={setMics} maxLength={3} keyboardType="number-pad" disabled={busy} />
      <Choice label="Projector requested" checked={projector} onChange={setProjector} disabled={busy} />
      <Button label={busy ? 'Submitting…' : 'Submit for administrator review'} disabled={busy} onPress={() => void book()} />
    </View> : null}
    {!canBook ? <Body>Venue booking requests are available to authorized administrators, faculty and organizers.</Body> : null}
  </Panel>;
}

function formatCampusTime(value: string): string {
  return `${value.replace('T', ' ').slice(0, 16)} IST`;
}
