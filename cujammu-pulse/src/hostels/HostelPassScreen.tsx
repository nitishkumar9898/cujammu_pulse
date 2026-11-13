import React, { useCallback, useContext, useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import type { GatePass, Hostel, HostelCredential, PendingGatePass } from './contracts';
import { Body, Button, Choice, Field, Heading, Panel, ThemeContext } from '../ui';

export function HostelPassScreen({ role, onSessionExpired }: { role: string; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [hostels, setHostels] = useState<Hostel[]>([]); const [passes, setPasses] = useState<GatePass[]>([]);
  const [hostelId, setHostelId] = useState<number | null>(null); const [type, setType] = useState<'DAY_OUT' | 'NIGHT_OUT'>('DAY_OUT');
  const [destination, setDestination] = useState(''); const [reason, setReason] = useState(''); const [exit, setExit] = useState(''); const [returnAt, setReturnAt] = useState('');
  const [busy, setBusy] = useState(false); const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  const [credential, setCredential] = useState<HostelCredential | null>(null);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [hostelList, passList] = await Promise.all([api.hostels(), api.myGatePasses()]);
      setHostels(hostelList); setPasses(passList);
      setHostelId(current => hostelList.some(hostel => hostel.id === current) ? current : hostelList[0]?.id ?? null); setError('');
    } catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Hostel passes could not be loaded.'); }
    finally { setLoading(false); }
  }, [onSessionExpired]);
  useEffect(() => { void load(); }, [load]);
  const apply = async () => {
    if (!hostelId || !destination.trim() || !reason.trim() || !exit || !returnAt || busy) return;
    setBusy(true); setError('');
    try {
      const pass = await api.applyGatePass({ hostelId, passType: type, destination: destination.trim(), reason: reason.trim(), expectedExitTime: exit, expectedReturnTime: returnAt });
      setPasses(current => [pass, ...current.filter(item => item.id !== pass.id)]); setDestination(''); setReason('');
    } catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Gate-pass request could not be submitted.'); }
    finally { setBusy(false); }
  };
  const issueCredential = async (passId: number) => {
    if (busy) return;
    setBusy(true); setError('');
    try { setCredential(await api.issueHostelCredential(passId)); }
    catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Secure gate credential could not be issued.'); }
    finally { setBusy(false); }
  };
  return <View style={{ gap: 16 }}>
    <Panel><Heading>Hostel gate pass</Heading><Body>Submit a Day-Out or Night-Out request for your verified hostel. Approval and gate credential verification remain staff-controlled; no QR secret is shown in this client.</Body>
      {loading ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading hostel passes" /> : <><Body>Hostel</Body>
        {hostels.map(hostel => <Choice key={hostel.id} label={`${hostel.name}${hostel.curfewTime ? ` · Curfew ${hostel.curfewTime}` : ''}`} checked={hostelId === hostel.id} onChange={() => setHostelId(hostel.id)} disabled={busy} />)}
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}><Choice label="Day-Out" checked={type === 'DAY_OUT'} onChange={() => setType('DAY_OUT')} disabled={busy} /><Choice label="Night-Out" checked={type === 'NIGHT_OUT'} onChange={() => setType('NIGHT_OUT')} disabled={busy} /></View>
        <Field label="Destination" value={destination} onChange={setDestination} maxLength={255} /><Field label="Reason" value={reason} onChange={setReason} maxLength={2000} multiline /><Field label="Expected exit (YYYY-MM-DDTHH:mm)" value={exit} onChange={setExit} maxLength={32} /><Field label="Expected return (YYYY-MM-DDTHH:mm)" value={returnAt} onChange={setReturnAt} maxLength={32} />
        <Button actionId="hostel.pass.apply" label={busy ? 'Submitting…' : 'Apply for gate pass'} disabled={busy || !hostelId || !destination.trim() || !reason.trim() || !exit || !returnAt} onPress={() => void apply()} />
      </>}</Panel>
    {error ? <Panel><Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text><Button secondary label="Reload" onPress={() => void load()} /></Panel> : null}
    <Panel><Heading>Your gate-pass history</Heading>{passes.length === 0 ? <Body>No gate-pass requests yet.</Body> : passes.map(pass => <View key={pass.id} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 10, gap: 3 }}><Text style={{ color: colors.text, fontWeight: '700' }}>{pass.type.replace('_', ' ')} · {pass.status}</Text><Body>{pass.destination} · Exit {new Date(pass.expectedExit).toLocaleString()}</Body>{pass.actualReturn ? <Body>Returned {new Date(pass.actualReturn).toLocaleString()}</Body> : null}{pass.status === 'APPROVED' || pass.status === 'ACTIVE_OUT' ? <Button actionId="hostel.pass.credential" label={busy ? 'Issuing…' : 'Issue owner-bound gate credential'} disabled={busy} onPress={() => void issueCredential(pass.id)} /> : null}</View>)}</Panel>
    {credential ? <Panel><Heading>Gate credential ready</Heading><Body>Show this credential only at the hostel gate. The gate operator still needs your authenticated confirmation; the credential alone does not record departure or return.</Body><Text selectable style={{ color: colors.text, fontFamily: 'monospace' }}>{credential.credential}</Text><Body>{credential.action} · Expires {new Date(credential.expiresAt).toLocaleString()}</Body></Panel> : null}
    {['WARDEN', 'ADMIN'].includes(role) ? <HostelApproval hostels={hostels} onSessionExpired={onSessionExpired} /> : null}
  </View>;
}

function HostelApproval({ hostels, onSessionExpired }: { hostels: Hostel[]; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext); const [hostelId, setHostelId] = useState<number | null>(null); const [items, setItems] = useState<PendingGatePass[]>([]); const [reason, setReason] = useState(''); const [busy, setBusy] = useState<number | null>(null); const [error, setError] = useState('');
  useEffect(() => { setHostelId(current => hostels.some(hostel => hostel.id === current) ? current : hostels[0]?.id ?? null); }, [hostels]);
  useEffect(() => {
    if (!hostelId) { setItems([]); return; }
    let active = true;
    void api.pendingGatePasses(hostelId).then(value => { if (active) { setItems(value); setError(''); } }).catch(failure => { if (!active) return; if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Pending passes could not be loaded.'); });
    return () => { active = false; };
  }, [hostelId, onSessionExpired]);
  const decide = async (passId: number, approve: boolean) => {
    if (!approve && !reason.trim()) return;
    setBusy(passId);
    try { await api.decideGatePass(passId, approve, approve ? null : reason.trim()); setItems(current => current.filter(item => item.id !== passId)); setReason(''); }
    catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Pass decision could not be saved.'); }
    finally { setBusy(null); }
  };
  return <Panel><Heading>Pending gate-pass approvals</Heading>{hostels.map(hostel => <Choice key={hostel.id} label={hostel.name} checked={hostelId === hostel.id} onChange={() => setHostelId(hostel.id)} disabled={busy !== null} />)}{error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}{items.map(item => <View key={item.id} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 10, gap: 6 }}><Text style={{ color: colors.text, fontWeight: '700' }}>{item.studentName} · {item.enrollmentNo}</Text><Body>{item.type.replace('_', ' ')} · {item.destination}</Body><Body>{item.reason}</Body><View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}><Button actionId="hostel.pass.approve" label="Approve" disabled={busy !== null} onPress={() => void decide(item.id, true)} /><Button actionId="hostel.pass.reject" label="Reject" secondary disabled={busy !== null || !reason.trim()} onPress={() => void decide(item.id, false)} /></View></View>)}{items.length ? <Field label="Reason required for rejection" value={reason} onChange={setReason} maxLength={255} /> : <Body>No pending passes for this hostel.</Body>}</Panel>;
}
