import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError, newRequestKey } from '../api';
import { Body, Button, Choice, Field, Heading, Panel } from '../ui';
import { foundCategories, type FoundCategory, type LostFoundItem, type FoundClaim, type ReportFoundCommand, type ClaimFoundCommand, type HandoverFoundCommand } from './contracts';

const categoryLabels: Record<FoundCategory, string> = { ELECTRONICS: 'Electronics', ID_CARDS_DOCS: 'ID cards and documents', BAGS_WALLETS: 'Bags and wallets', KEYS: 'Keys', CLOTHING: 'Clothing', OTHER: 'Other' };

export function LostFoundScreen({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [items, setItems] = useState<LostFoundItem[]>([]); const [page, setPage] = useState(0); const [hasNext, setHasNext] = useState(false); const [filter, setFilter] = useState<FoundCategory | undefined>();
  const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [revision, setRevision] = useState(0);
  const [title, setTitle] = useState(''); const [description, setDescription] = useState(''); const [location, setLocation] = useState(''); const [category, setCategory] = useState<FoundCategory>('OTHER');
  const [selected, setSelected] = useState<number | null>(null); const [reportBlocked, setReportBlocked] = useState(false); const reportAttempt = useRef<ReportFoundCommand | null>(null);
  const fail = useCallback((e: unknown) => { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Lost and found could not be loaded.'); }, [onSessionExpired]);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setItems([]); setSelected(null); setError('');
    void api.lostFound(filter, page, controller.signal).then(result => { if (!controller.signal.aborted) { if (result.page !== page) throw new Error('The item page changed. Refresh before continuing.'); setItems(result.items); setHasNext(result.hasNext); } }).catch(e => { if (!controller.signal.aborted) { setHasNext(false); fail(e); } }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [filter, page, revision, fail]);
  const edit = (setter: (value: string) => void, value: string) => { setter(value); reportAttempt.current = null; setReportBlocked(false); };
  const report = async () => {
    if (busy || reportBlocked || !title.trim() || !description.trim() || !location.trim()) return;
    const command = reportAttempt.current ?? { requestKey: newRequestKey(), title: title.trim(), category, description: description.trim(), locationFound: location.trim() }; reportAttempt.current = command;
    setBusy(true); setError('');
    try { await api.reportFoundItem(command); reportAttempt.current = null; setTitle(''); setDescription(''); setLocation(''); setFilter(undefined); setPage(0); setRevision(x => x + 1); }
    catch (e) { if (e instanceof ApiError && [400, 403, 409].includes(e.status)) setReportBlocked(true); fail(e); }
    finally { setBusy(false); }
  };
  return <View style={{ gap: 14 }}><Panel><Heading>Lost & Found</Heading><Body>Report found items without personal documents or public contact details. Private claims require evidence; submission does not complete a handover. Photo uploads are unavailable in this release.</Body>
    <Field label="Item title" value={title} onChange={v => edit(setTitle, v)} maxLength={120} disabled={busy} /><Body>Item category</Body>{foundCategories.map(key => <Choice key={key} label={`Report category: ${categoryLabels[key]}`} checked={category === key} disabled={busy} onChange={() => { setCategory(key); reportAttempt.current = null; setReportBlocked(false); }} />)}
    <Field label="Description" value={description} onChange={v => edit(setDescription, v)} maxLength={2000} multiline disabled={busy} /><Field label="Location found" value={location} onChange={v => edit(setLocation, v)} maxLength={150} disabled={busy} />
    <Button actionId="lost-found.item.report" label={busy ? 'Submitting…' : 'Report found item'} disabled={busy || reportBlocked || !title.trim() || !description.trim() || !location.trim()} onPress={() => void report()} />{reportBlocked ? <Body>The report was rejected. Review or edit its details before submitting again.</Body> : null}
  </Panel>{error ? <Text accessibilityRole="alert">{error}</Text> : null}<Panel><Heading>Published items</Heading><Body>Filter by category</Body><Choice label="All categories" checked={filter === undefined} disabled={busy} onChange={() => { setFilter(undefined); setPage(0); }} />{foundCategories.map(key => <Choice key={key} label={`Filter: ${categoryLabels[key]}`} checked={filter === key} disabled={busy} onChange={() => { setFilter(key); setPage(0); }} />)}
    <Button label="Refresh items" secondary disabled={busy || loading} onPress={() => setRevision(x => x + 1)} /><Body>Page {page + 1}</Body><Button label="Previous items" secondary disabled={busy || loading || page === 0} onPress={() => setPage(x => x - 1)} /><Button label="Next items" secondary disabled={busy || loading || !hasNext} onPress={() => setPage(x => x + 1)} />
    {loading ? <ActivityIndicator accessibilityLabel="Loading lost and found" /> : items.length === 0 ? <Body>No published items match this category.</Body> : items.map(item => <View key={item.id} style={{ gap: 8 }}><Heading>{item.title}</Heading><Body>{categoryLabels[item.category as FoundCategory] ?? item.category} · {item.status} · {item.locationFound}</Body><Body>{item.description}</Body><Button label={item.canReview ? `Review private claims: ${item.title}` : `View or submit my claim: ${item.title}`} secondary disabled={busy} onPress={() => setSelected(current => current === item.id ? null : item.id)} />
      {selected === item.id ? <PrivateClaims key={`${item.id}-${item.version}`} item={item} onSessionExpired={onSessionExpired} onSaved={saved => setItems(current => current.map(existing => existing.id === saved.id ? saved : existing))} /> : null}
    </View>)}
  </Panel></View>;
}

function PrivateClaims({ item, onSessionExpired, onSaved }: { item: LostFoundItem; onSessionExpired: () => void; onSaved: (item: LostFoundItem) => void }) {
  const [claims, setClaims] = useState<FoundClaim[]>([]); const [page, setPage] = useState(0); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [revision, setRevision] = useState(0);
  const [evidence, setEvidence] = useState(''); const [reason, setReason] = useState(''); const [confirmClaim, setConfirmClaim] = useState<number | null>(null); const [blocked, setBlocked] = useState(false);
  const claimAttempt = useRef<ClaimFoundCommand | null>(null); const handoverAttempt = useRef<HandoverFoundCommand | null>(null);
  const fail = useCallback((e: unknown) => { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else { if (e instanceof ApiError && [400, 403, 409].includes(e.status)) { setClaims([]); setBlocked(true); } setError(e instanceof Error ? e.message : 'Private claims could not be loaded.'); } }, [onSessionExpired]);
  useEffect(() => { const controller = new AbortController(); setClaims([]); setLoading(true); void api.lostFoundClaims(item.id, page, controller.signal).then(result => { if (!controller.signal.aborted) { if (result.some(claim => claim.itemId !== item.id)) throw new Error('Private claims do not match this item.'); setClaims(result); } }).catch(e => { if (!controller.signal.aborted) fail(e); }).finally(() => { if (!controller.signal.aborted) setLoading(false); }); return () => controller.abort(); }, [item.id, page, revision, fail]);
  const submit = async () => {
    if (busy || blocked || !evidence.trim()) return; const command = claimAttempt.current ?? { requestKey: newRequestKey(), expectedVersion: item.version, evidence: evidence.trim() }; claimAttempt.current = command; setBusy(true); setError('');
    try { await api.claimLostFound(item.id, command); claimAttempt.current = null; setEvidence(''); setPage(0); setRevision(x => x + 1); } catch (e) { fail(e); } finally { setBusy(false); }
  };
  const handover = async () => {
    if (busy || blocked || confirmClaim == null || !reason.trim()) return; const command = handoverAttempt.current ?? { requestKey: newRequestKey(), expectedVersion: item.version, claimId: confirmClaim, verificationReason: reason.trim() }; handoverAttempt.current = command; setBusy(true); setError('');
    try { const saved = await api.handoverLostFound(item.id, command); handoverAttempt.current = null; setClaims([]); setEvidence(''); setReason(''); setConfirmClaim(null); onSaved(saved); } catch (e) { fail(e); } finally { setBusy(false); }
  };
  return <Panel><Heading>{item.canReview ? 'Private claim review' : 'Your private claims'}</Heading><Body>Only your claims or claims you are authorized to review are returned. Do not enter passwords, identity-document numbers or unnecessary contact details.</Body>{error ? <Text accessibilityRole="alert">{error}</Text> : null}{blocked ? <Body>Access or item state changed. Refresh the item listing before continuing.</Body> : null}
    {!item.canReview && ['REPORTED', 'VERIFIED'].includes(item.status) ? <><Field label="Private ownership evidence" value={evidence} onChange={v => { setEvidence(v); claimAttempt.current = null; }} maxLength={2000} multiline disabled={busy || blocked} /><Button actionId="lost-found.item.claim" label="Submit private claim" disabled={busy || blocked || !evidence.trim()} onPress={() => void submit()} /></> : null}
    {loading ? <ActivityIndicator accessibilityLabel="Loading private claims" /> : claims.length === 0 ? <Body>No private claims are available on this page.</Body> : claims.map(claim => <View key={claim.id} style={{ gap: 6 }}><Body>Claim {claim.id} · {claim.status}</Body><Body>{claim.evidence}</Body>{item.canReview && claim.status === 'PENDING' ? <Button label={`Verify handover for claim ${claim.id}`} secondary disabled={busy || blocked} onPress={() => { setConfirmClaim(claim.id); setReason(''); handoverAttempt.current = null; }} /> : null}</View>)}
    <Button label="Previous claims" secondary disabled={loading || busy || page === 0} onPress={() => { setConfirmClaim(null); setPage(x => x - 1); }} /><Button label="Next claims" secondary disabled={loading || busy || claims.length < 20} onPress={() => { setConfirmClaim(null); setPage(x => x + 1); }} />
    {confirmClaim != null ? <View accessibilityRole="alert" style={{ gap: 8 }}><Body>Confirm handover for claim {confirmClaim} after privately verifying ownership. This closes the item to competing claims.</Body><Field label="Handover verification reason" value={reason} onChange={v => { setReason(v); handoverAttempt.current = null; }} maxLength={500} multiline disabled={busy || blocked} /><Button label="Keep reviewing claims" secondary disabled={busy} onPress={() => { setConfirmClaim(null); handoverAttempt.current = null; }} /><Button actionId="lost-found.item.handover" label="Confirm verified handover" disabled={busy || blocked || !reason.trim()} onPress={() => void handover()} /></View> : null}
  </Panel>;
}
