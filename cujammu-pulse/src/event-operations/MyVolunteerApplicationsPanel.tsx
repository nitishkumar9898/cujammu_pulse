import React, { useContext, useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { ApiError, requestJson } from '../api';
import { Body, Button, Heading, Panel, ThemeContext } from '../ui';
import { decodeOwnVolunteerApplications, type OwnVolunteerApplicationPage } from './own-applications';

export function MyVolunteerApplicationsPanel({ revision, onSessionExpired }: { revision: number; onSessionExpired(): void }) {
  const colors = useContext(ThemeContext);
  const [page, setPage] = useState(0); const [refresh, setRefresh] = useState(0);
  const [result, setResult] = useState<OwnVolunteerApplicationPage | null>(null);
  const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController(); setResult(null); setError(''); setLoading(true);
    void requestJson(`/api/v1/cujammu/events/operations/my-volunteer-applications?page=${page}&size=20`, controller.signal)
      .then(value => {
        if (controller.signal.aborted) return;
        const decoded = decodeOwnVolunteerApplications(value);
        if (decoded.page !== page || decoded.size !== 20) throw new Error('Application page does not match the request.');
        setResult(decoded);
      }).catch(failure => {
        if (controller.signal.aborted) return;
        if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
        else setError('Your application status could not be loaded. Refresh to check the saved state.');
      }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [page, refresh, revision, onSessionExpired]);
  return <Panel><Heading>My volunteer applications</Heading>
    <Body>Your saved applications remain visible after recruitment closes. Acceptance does not automatically grant scanner, manual attendance or report access.</Body>
    <Button label="Refresh my applications" actionId="events.volunteer.applications.mine" secondary disabled={loading} onPress={() => setRefresh(value => value + 1)} />
    {loading ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading my volunteer applications" /> : null}
    {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
    {result?.items.length === 0 ? <Body>No applications on this page.</Body> : null}
    {result?.items.map(item => <View key={item.id} style={{ gap: 6, paddingVertical: 10 }}>
      <Heading>{item.eventTitle}</Heading><Body>Application status: {item.status === 'APPLIED' ? 'Awaiting review' : item.status === 'ACCEPTED' ? 'Accepted' : item.status === 'REMOVED' ? 'Removed' : 'Rejected'}</Body>
      <Body>Event status: {item.eventStatus.replaceAll('_', ' ')}</Body>
    </View>)}
    <View style={{ flexDirection: 'row', gap: 8 }}>
      <Button label="Previous application page" secondary disabled={loading || page === 0} onPress={() => setPage(value => value - 1)} />
      <Button label="Next application page" secondary disabled={loading || !result?.hasNext} onPress={() => setPage(value => value + 1)} />
    </View>
  </Panel>;
}
