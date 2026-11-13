import React, { useContext, useEffect, useRef, useState } from 'react';
import { Text, TextInput, View } from 'react-native';
import { api, ApiError } from '../api';
import type { Department } from '../contracts';
import type { StudentCandidate } from './student-identity';
import { Body, Button, Heading, Panel, ThemeContext } from '../ui';

export function StudentIdentityScreen({ onSessionExpired }: { onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [email, setEmail] = useState(''); const [candidate, setCandidate] = useState<StudentCandidate | null>(null);
  const [departments, setDepartments] = useState<Department[]>([]); const [departmentId, setDepartmentId] = useState<number | null>(null);
  const [departmentQuery, setDepartmentQuery] = useState('');
  const [identifier, setIdentifier] = useState(''); const [source, setSource] = useState(''); const [reason, setReason] = useState('');
  const [confirmed, setConfirmed] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [saved, setSaved] = useState(false);
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  const clearForm = () => { setCandidate(null); setIdentifier(''); setSource(''); setReason(''); setDepartmentId(null); setDepartmentQuery(''); setConfirmed(false); setSaved(false); };
  const fail = (e: unknown) => { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'The office request could not be completed.'); };
  const lookup = async () => {
    request.current?.abort(); const controller = new AbortController(); request.current = controller;
    clearForm(); setError(''); setBusy(true); setDepartments([]);
    try {
      const [student, directory] = await Promise.all([api.lookupStudent(email.trim(), controller.signal), api.departments(controller.signal)]);
      if (!controller.signal.aborted) { setCandidate(student); setDepartments(directory); }
    } catch (e) { if (!controller.signal.aborted) fail(e); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  };
  const verify = async () => {
    if (!candidate || candidate.bindingState !== 'UNVERIFIED' || !departmentId || !confirmed || busy) return;
    request.current?.abort(); const controller = new AbortController(); request.current = controller; setBusy(true); setError('');
    try {
      const result = await api.verifyStudent(candidate.studentId, { expectedCredentialVersion: candidate.credentialVersion, identifier: identifier.trim(), departmentId, officialSourceReference: source.trim(), reviewReason: reason.trim() }, controller.signal);
      if (!controller.signal.aborted) {
        setCandidate({ ...candidate, bindingState: 'VERIFIED', verifiedIdentifier: result.verifiedIdentifier, verifiedDepartmentId: departmentId, credentialVersion: result.credentialVersion });
        setSource(''); setReason(''); setIdentifier(''); setConfirmed(false); setSaved(true);
      }
    } catch (e) {
      if (!controller.signal.aborted) { clearForm(); fail(e); setError('Verification was not confirmed. Look up the account again to recover the server state before submitting another request.'); }
    } finally { if (!controller.signal.aborted) setBusy(false); }
  };
  const input = { minHeight: 48, borderWidth: 1, borderColor: colors.border, borderRadius: 10, padding: 12, color: colors.text, backgroundColor: colors.background };
  const matchingDepartments = departments.filter(department => `${department.name} ${department.code}`.toLowerCase().includes(departmentQuery.trim().toLowerCase()));
  return <View style={{ gap: 16 }}><Panel><Heading>Student identity verification</Heading>
    <Body>Use the official university source to verify an existing student account. Profile text does not establish identity or department authority. This records the first binding; existing identities need a separate reviewed correction process.</Body>
    <Body>Student account email</Body>
    <TextInput accessibilityLabel="Student account email" value={email} editable={!busy} autoCapitalize="none" keyboardType="email-address" maxLength={254} style={input} placeholder="Exact student account email" placeholderTextColor={colors.muted} onChangeText={value => { request.current?.abort(); setEmail(value); clearForm(); setError(''); }} />
    <Button label={busy ? 'Processing office request…' : 'Find student account'} actionId="admin.student-identity.lookup" disabled={busy || !email.trim()} onPress={() => void lookup()} />
    {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
    {saved ? <Text accessibilityRole="alert" style={{ color: colors.text }}>Student identity verified and saved.</Text> : null}
  </Panel>
  {candidate ? <Panel><Heading>{candidate.name}</Heading><Body>{candidate.email}</Body>
    {candidate.bindingState !== 'UNVERIFIED' ? <>
      <Body>{candidate.bindingState === 'VERIFIED' ? 'This account already has a verified binding.' : 'Legacy identity evidence needs office reconciliation before further changes.'}</Body>
      <Body>Verified identifier: {candidate.verifiedIdentifier ?? 'Not established'}</Body>
      <Body>Verified department: {departments.find(item => item.id === candidate.verifiedDepartmentId)?.name ?? 'Unavailable in the active directory'}</Body>
      <Body>Existing records cannot be overwritten from this screen.</Body>
    </> : <>
      <Body>Official roll or enrollment number</Body>
      <TextInput accessibilityLabel="Official roll or enrollment number" value={identifier} editable={!busy} autoCapitalize="none" maxLength={64} style={input} onChangeText={value => { setIdentifier(value); setConfirmed(false); }} />
      <Body>Select the department verified by the official source:</Body>
      <TextInput accessibilityLabel="Search active departments" value={departmentQuery} editable={!busy} maxLength={100} style={input} placeholder="Search by department name or code" placeholderTextColor={colors.muted} onChangeText={setDepartmentQuery} />
      {departmentId ? <Body>Selected department: {departments.find(department => department.id === departmentId)?.name}</Body> : null}
      {departments.length === 0 ? <Body>No active departments are available. The university registry must be reviewed before verification.</Body> : null}
      {departments.length > 0 && matchingDepartments.length === 0 ? <Body>No matching department. Check the name or code; free-text departments cannot be verified.</Body> : null}
      {matchingDepartments.length > 8 ? <Body>Showing 8 of {matchingDepartments.length} departments. Narrow the search to find the official department.</Body> : null}
      {matchingDepartments.slice(0, 8).map(department => <Button key={department.id} label={`${departmentId === department.id ? 'Selected: ' : ''}${department.name}`} secondary disabled={busy} onPress={() => { setDepartmentId(department.id); setConfirmed(false); }} />)}
      <Body>Official source reference</Body>
      <TextInput accessibilityLabel="Official source reference" value={source} editable={!busy} maxLength={120} style={input} placeholder="Approved record reference; do not paste documents" placeholderTextColor={colors.muted} onChangeText={value => { setSource(value); setConfirmed(false); }} />
      <Body>Verification review reason</Body>
      <TextInput accessibilityLabel="Verification review reason" value={reason} editable={!busy} maxLength={240} style={input} onChangeText={value => { setReason(value); setConfirmed(false); }} />
      <Button label={confirmed ? 'Review confirmed — change review confirmation' : 'I checked this account, identifier and department against the official source'} secondary disabled={busy} onPress={() => setConfirmed(value => !value)} />
      <Button label="Save verified student identity" actionId="admin.student-identity.verify" disabled={busy || !confirmed || !identifier.trim() || !source.trim() || !reason.trim() || !departmentId} onPress={() => void verify()} />
    </>}
  </Panel> : null}</View>;
}
