import React, { useCallback, useContext, useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError, newRequestKey } from '../api';
import type { AcademicBatchPreview, AcademicBatchReceipt } from './contracts';
import { Body, Button, Choice, Field, Heading, Panel, ThemeContext } from '../ui';

type ResultStatus = 'PASS' | 'PROMOTED' | 'REAPPEAR';

export function AcademicPublisherForm({ onSessionExpired }: { onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [scopes, setScopes] = useState<string[]>([]); const [department, setDepartment] = useState('');
  const [identifier, setIdentifier] = useState(''); const [semester, setSemester] = useState(''); const [year, setYear] = useState('');
  const [program, setProgram] = useState(''); const [sgpa, setSgpa] = useState(''); const [cgpa, setCgpa] = useState('');
  const [status, setStatus] = useState<ResultStatus>('PASS'); const [correctionReason, setCorrectionReason] = useState('');
  const [pendingKey, setPendingKey] = useState(newRequestKey); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [loading, setLoading] = useState(true);
  const [csvContent, setCsvContent] = useState(''); const [batchPreview, setBatchPreview] = useState<AcademicBatchPreview | null>(null); const [previewBusy, setPreviewBusy] = useState(false);
  const [batchReceipt, setBatchReceipt] = useState<AcademicBatchReceipt | null>(null); const [batchBusy, setBatchBusy] = useState(false); const [batchRequestKey, setBatchRequestKey] = useState(newRequestKey);
  const load = useCallback(async () => {
    setLoading(true);
    try { const values = await api.myAcademicPublishScopes(); setScopes(values); setDepartment(current => values.includes(current) ? current : values[0] ?? ''); setError(''); }
    catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Academic publishing access could not be loaded.'); }
    finally { setLoading(false); }
  }, [onSessionExpired]);
  useEffect(() => { void load(); }, [load]);
  const update = (setter: (value: string) => void) => (value: string) => { setter(value); setPendingKey(newRequestKey()); setNotice(''); };
  const submit = async () => {
    const parsedSgpa = Number(sgpa); const parsedCgpa = Number(cgpa);
    if (busy || !department || !identifier.trim() || !semester.trim() || !year.trim() || !Number.isFinite(parsedSgpa) || !Number.isFinite(parsedCgpa) || parsedSgpa < 0 || parsedSgpa > 10 || parsedCgpa < 0 || parsedCgpa > 10) {
      setError('Enter the verified student identifier, term, year and valid SGPA/CGPA from 0 to 10.'); return;
    }
    setBusy(true); setError(''); setNotice('');
    try {
      const receipt = await api.submitScoreCard({ verifiedIdentifier: identifier.trim(), semester: semester.trim(), academicYear: year.trim(), program: program.trim() || null, sgpa: parsedSgpa, cgpa: parsedCgpa, resultStatus: status, pdfUrl: null, requestKey: pendingKey, correctionReason: correctionReason.trim() || null });
      setNotice(`Submission ${String((receipt as { status?: unknown })?.status ?? 'received')} · version ${String((receipt as { version?: unknown })?.version ?? 'pending')}. A different reviewer must approve it before the student can see it.`);
      setPendingKey(newRequestKey()); setCorrectionReason('');
    } catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Scorecard submission failed. Keep the form unchanged and retry safely.'); }
    finally { setBusy(false); }
  };
  const previewBatch = async () => {
    if (previewBusy || !department || !csvContent.trim()) return;
    setPreviewBusy(true); setError(''); setBatchPreview(null);
    try { setBatchPreview(await api.previewScoreCardBatch(department, csvContent)); }
    catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Batch preview failed.'); }
    finally { setPreviewBusy(false); }
  };
  const submitBatch = async () => {
    if (batchBusy || !batchPreview || batchPreview.rejectedRows > 0 || batchPreview.department !== department) return;
    setBatchBusy(true); setError('');
    try {
      const receipt = await api.submitScoreCardBatch(department, csvContent, batchRequestKey);
      setBatchReceipt(receipt); setCsvContent(''); setBatchPreview(null); setBatchRequestKey(newRequestKey());
    } catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Batch submission failed. Keep the CSV unchanged and retry safely.'); }
    finally { setBatchBusy(false); }
  };
  const visibleBatchRows = batchPreview?.rows.filter((row, index) => row.issue !== null || index < 25) ?? [];
  return <Panel><Heading>Submit or correct an official result</Heading>
    <Body>Only departments assigned by university administration are available. The server verifies the student identity and derives their department. A correction to an existing result needs a reason and creates a new reviewable version; the previously published result remains visible until approval. PDF upload is unavailable until private managed storage is connected.</Body>
    {loading ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading academic publishing access" /> : scopes.length === 0 ? <Body>No academic publishing authority is assigned to your account.</Body> : <>
      {scopes.map(value => <Choice key={value} label={`Department: ${value}`} checked={department === value} onChange={() => { setDepartment(value); setPendingKey(newRequestKey()); setBatchRequestKey(newRequestKey()); setBatchPreview(null); setBatchReceipt(null); }} disabled={busy || previewBusy || batchBusy} />)}
      <Field label="Verified roll/enrollment number" value={identifier} onChange={update(setIdentifier)} maxLength={64} disabled={busy} />
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12 }}>
        <Field label="Semester" value={semester} onChange={update(setSemester)} maxLength={20} disabled={busy} />
        <Field label="Academic year" value={year} onChange={update(setYear)} maxLength={20} disabled={busy} />
        <Field label="Program (optional)" value={program} onChange={update(setProgram)} maxLength={120} disabled={busy} />
        <Field label="SGPA (0–10)" value={sgpa} onChange={update(setSgpa)} maxLength={5} keyboardType="number-pad" disabled={busy} />
        <Field label="CGPA (0–10)" value={cgpa} onChange={update(setCgpa)} maxLength={5} keyboardType="number-pad" disabled={busy} />
      </View>
      {(['PASS', 'PROMOTED', 'REAPPEAR'] as const).map(value => <Choice key={value} label={`Result: ${value}`} checked={status === value} onChange={() => { setStatus(value); setPendingKey(newRequestKey()); }} disabled={busy} />)}
      <Field label="Correction reason (required for a reissue)" value={correctionReason} onChange={update(setCorrectionReason)} maxLength={500} multiline disabled={busy} />
      {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
      {notice ? <Text accessibilityLiveRegion="polite" style={{ color: colors.accent }}>{notice}</Text> : null}
      <Button actionId="academics.scorecards.submit" label={busy ? 'Submitting…' : 'Submit for independent review'} disabled={busy} onPress={() => void submit()} />
      <View style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 16, gap: 12 }}>
        <Heading>Batch CSV preflight</Heading>
        <Body>Paste a CSV with these exact headers: verifiedIdentifier,semester,academicYear,program,sgpa,cgpa,resultStatus,correctionReason. Preview validates up to 250 rows and does not import or publish them.</Body>
        <Field label="CSV content (maximum 500 KB)" value={csvContent} onChange={value => { setCsvContent(value); setBatchPreview(null); setBatchReceipt(null); setBatchRequestKey(newRequestKey()); }} maxLength={500000} multiline disabled={previewBusy || batchBusy} />
        <Button actionId="academics.scorecards.batch.preview" label={previewBusy ? 'Checking rows…' : 'Preview batch'} secondary disabled={previewBusy || !csvContent.trim()} onPress={() => void previewBatch()} />
        {batchPreview ? <>
          <Body>{batchPreview.department}: {batchPreview.matchedRows} identity matches, {batchPreview.rejectedRows} rows need correction. No rows were saved.</Body>
          {visibleBatchRows.map(row => <Body key={row.rowNumber}>CSV row {row.rowNumber}{row.verifiedIdentifier ? ` · ${row.verifiedIdentifier}` : ''}: {row.issue ?? 'Identity and fields matched'}</Body>)}
          {visibleBatchRows.length < batchPreview.rows.length ? <Body>Showing all rows that need correction and the first 25 matching rows.</Body> : null}
          {batchPreview.rejectedRows === 0 ? <Button actionId="academics.scorecards.batch.submit" label={batchBusy ? 'Submitting batch…' : `Submit ${batchPreview.totalRows} rows for review`} disabled={batchBusy || previewBusy} onPress={() => void submitBatch()} /> : null}
        </> : null}
        {batchReceipt ? <Text accessibilityLiveRegion="polite" style={{ color: colors.accent }}>Batch {batchReceipt.batchId} submitted: {batchReceipt.rowCount} result records are pending independent review. Students cannot see them until approval.</Text> : null}
      </View>
    </>}
  </Panel>;
}
