import React, { useCallback, useContext, useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import type { ReviewItem } from './contracts';
import { Body, Button, Choice, Field, Heading, Panel, ThemeContext } from '../ui';
import { AcademicPublisherForm } from './AcademicPublisherForm';
import { AcademicBatchReviewPanel } from './AcademicBatchReviewPanel';

export function AcademicReviewScreen({ onSessionExpired }: { onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [scopes, setScopes] = useState<string[]>([]); const [department, setDepartment] = useState('');
  const [items, setItems] = useState<ReviewItem[]>([]); const [page, setPage] = useState(0); const [hasNext, setHasNext] = useState(false);
  const [reason, setReason] = useState(''); const [busy, setBusy] = useState<number | null>(null);
  const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  const loadScopes = useCallback(async () => {
    setLoading(true);
    try { const values = await api.myAcademicReviewScopes(); setScopes(values); setDepartment(current => values.includes(current) ? current : values[0] ?? ''); setError(''); }
    catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Academic review access could not be loaded.'); }
    finally { setLoading(false); }
  }, [onSessionExpired]);
  useEffect(() => { void loadScopes(); }, [loadScopes]);
  useEffect(() => {
    if (!department) { setItems([]); return; }
    let active = true;
    setItems([]); setHasNext(false);
    void api.pendingScoreCards(department, page).then(result => { if (active) { setItems(result.items); setHasNext(result.hasNext); setError(''); } })
      .catch(failure => { if (!active) return; if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Pending results could not be loaded.'); });
    return () => { active = false; };
  }, [department, page, onSessionExpired]);
  const decide = async (item: ReviewItem, publish: boolean) => {
    if (busy !== null || (!publish && !reason.trim())) return;
    setBusy(item.id); setError('');
    try { await api.reviewScoreCard(item.id, publish, publish ? null : reason.trim()); setItems(current => current.filter(row => row.id !== item.id)); setReason(''); }
    catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Result review could not be saved.'); }
    finally { setBusy(null); }
  };
  return <View style={{ gap: 16 }}>
    <AcademicPublisherForm onSessionExpired={onSessionExpired} />
    <AcademicBatchReviewPanel onSessionExpired={onSessionExpired} />
    <Panel><Heading>Academic result review</Heading><Body>Review is limited to departments explicitly assigned by university administration. You cannot publish a result you submitted yourself.</Body>
      {loading ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading academic review access" /> : scopes.length === 0 ? <Body>No academic review authority is assigned to your account.</Body> : scopes.map(value => <Choice key={value} label={value} checked={department === value} onChange={() => { setDepartment(value); setPage(0); }} disabled={busy !== null} />)}
      {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
      {department && items.length === 0 && !error ? <Body>No results are waiting for review in this department.</Body> : null}
      {items.map(item => <View key={item.id} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 14, gap: 6 }}>
        <Text style={{ color: colors.text, fontWeight: '700' }}>{item.studentName} · {item.verifiedIdentifier}</Text>
        <Body>{item.department}{item.program ? ` · ${item.program}` : ''} · {item.semester} · {item.academicYear}</Body>
        <Body>SGPA {item.sgpa?.toFixed(2) ?? '—'} · CGPA {item.cgpa?.toFixed(2) ?? '—'} · {item.resultStatus}</Body>
        <Body>Version {item.version}{item.correctionReason ? ` · Correction: ${item.correctionReason}` : ' · Initial publication'}</Body>
        <Body>Submitted by {item.submittedBy}{item.submittedAt ? ` · ${new Date(item.submittedAt).toLocaleString()}` : ''}</Body>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          <Button actionId="academics.scorecards.review.publish" label="Approve and publish" disabled={busy !== null} onPress={() => void decide(item, true)} />
          <Button actionId="academics.scorecards.review.return" label="Return with reason" secondary disabled={busy !== null || !reason.trim()} onPress={() => void decide(item, false)} />
        </View>
      </View>)}
      {items.length > 0 ? <Field label="Reason required to return a result" value={reason} onChange={setReason} maxLength={500} multiline /> : null}
      {page > 0 || hasNext ? <View style={{ flexDirection: 'row', gap: 8 }}><Button secondary label="Previous" disabled={page === 0 || busy !== null} onPress={() => setPage(value => Math.max(0, value - 1))} /><Button secondary label="Next" disabled={!hasNext || busy !== null} onPress={() => setPage(value => value + 1)} /></View> : null}
    </Panel>
  </View>;
}
