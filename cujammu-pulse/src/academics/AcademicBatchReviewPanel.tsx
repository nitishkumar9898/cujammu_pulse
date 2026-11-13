import React, { useCallback, useContext, useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import type { AcademicBatchDetail, AcademicBatchQueueItem } from './contracts';
import { Body, Button, Field, Heading, Panel, ThemeContext } from '../ui';

export function AcademicBatchReviewPanel({ onSessionExpired }: { onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [items, setItems] = useState<AcademicBatchQueueItem[]>([]); const [page, setPage] = useState(0); const [hasNext, setHasNext] = useState(false);
  const [detail, setDetail] = useState<AcademicBatchDetail | null>(null); const [reason, setReason] = useState('');
  const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const load = useCallback(async () => {
    setLoading(true);
    try { const result = await api.pendingAcademicBatches(page); setItems(result.items); setHasNext(result.hasNext); setError(''); }
    catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Batch review queue could not be loaded.'); }
    finally { setLoading(false); }
  }, [onSessionExpired, page]);
  useEffect(() => { void load(); }, [load]);
  const open = async (batchId: number) => {
    setBusy(true); setError('');
    try { setDetail(await api.academicBatchDetail(batchId)); setReason(''); }
    catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Batch details could not be loaded.'); }
    finally { setBusy(false); }
  };
  const decide = async (publish: boolean) => {
    if (!detail || busy || (!publish && !reason.trim())) return;
    setBusy(true); setError('');
    try { await api.reviewAcademicBatch(detail.batch.batchId, detail.batch.version, publish, publish ? null : reason.trim()); setDetail(null); setReason(''); await load(); }
    catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Batch decision could not be saved. Reload before retrying.'); }
    finally { setBusy(false); }
  };
  return <Panel><Heading>Academic batch review</Heading>
    <Body>Each batch is reviewed as one transaction. You cannot review a batch you submitted. A decision applies to every row, and each row is locked from individual review.</Body>
    {loading ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading academic batches" /> : items.length === 0 ? <Body>No batches are awaiting review in your assigned departments.</Body> : items.map(item => <View key={item.batchId} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 12, gap: 6 }}>
      <Body>Batch {item.batchId} · {item.department} · {item.rowCount} records</Body>
      <Body>Submitted by {item.submittedBy}{item.submittedAt ? ` · ${new Date(item.submittedAt).toLocaleString()}` : ''}</Body>
      <Button actionId="academics.scorecards.batches.detail" secondary label={detail?.batch.batchId === item.batchId ? 'Batch opened below' : 'Review complete batch'} disabled={busy} onPress={() => void open(item.batchId)} />
    </View>)}
    {detail ? <View style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 16, gap: 10 }}>
      <Heading>Batch {detail.batch.batchId} · {detail.rows.length} rows</Heading>
      {detail.rows.map(row => <View key={row.id} style={{ gap: 4, paddingBottom: 8 }}>
        <Body>{row.studentName} · {row.verifiedIdentifier}</Body>
        <Body>{row.semester} · {row.academicYear} · {row.program ?? 'Program not set'}</Body>
        <Body>SGPA {row.sgpa?.toFixed(2) ?? '—'} · CGPA {row.cgpa?.toFixed(2) ?? '—'} · {row.resultStatus}</Body>
        <Body>Version {row.version}{row.correctionReason ? ` · Correction: ${row.correctionReason}` : ''}</Body>
      </View>)}
      <Field label="Reason required to return the complete batch" value={reason} onChange={setReason} maxLength={500} multiline disabled={busy} />
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        <Button actionId="academics.scorecards.batches.publish" label="Approve and publish all rows" disabled={busy} onPress={() => void decide(true)} />
        <Button actionId="academics.scorecards.batches.return" secondary label="Return all rows" disabled={busy || !reason.trim()} onPress={() => void decide(false)} />
        <Button secondary label="Close batch details" disabled={busy} onPress={() => setDetail(null)} />
      </View>
    </View> : null}
    {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
    {!detail && (page > 0 || hasNext) ? <View style={{ flexDirection: 'row', gap: 8 }}>
      <Button secondary label="Previous" disabled={page === 0 || busy} onPress={() => setPage(value => Math.max(0, value - 1))} />
      <Button secondary label="Next" disabled={!hasNext || busy} onPress={() => setPage(value => value + 1)} />
    </View> : null}
    <Button actionId="academics.scorecards.batches.queue" secondary label="Refresh batch queue" disabled={busy || loading} onPress={() => void load()} />
  </Panel>;
}
