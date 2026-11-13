import React, { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';
import { api, ApiError, newRequestKey } from '../api';
import type { ManagedTicket, Ticket, TicketEntry } from './contracts';
import { pendingHelpdeskCommands } from './pending-commands';
import { Body, Button, Choice, Field, Heading, Panel, ThemeContext } from '../ui';

const categories = ['EVENT_REGISTRATION', 'LIBRARY_PASS', 'HOSTEL_LEAVE', 'CERTIFICATE_ISSUE', 'TECH_SUPPORT', 'GENERAL'];
const priorities = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

export function HelpdeskScreen({ accountId, role, onSessionExpired }: { accountId: string; role: string; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const pendingCommands = pendingHelpdeskCommands(accountId);
  const [tickets, setTickets] = useState<Ticket[]>([]); const [selected, setSelected] = useState<Ticket | null>(null);
  const [entries, setEntries] = useState<TicketEntry[]>([]); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [subject, setSubject] = useState(pendingCommands.create?.subject ?? ''); const [description, setDescription] = useState(pendingCommands.create?.description ?? ''); const [category, setCategory] = useState(pendingCommands.create?.category ?? 'GENERAL'); const [priority, setPriority] = useState(pendingCommands.create?.requestedPriority ?? 'MEDIUM');
  const [reply, setReply] = useState(pendingCommands.action?.kind === 'reply' ? pendingCommands.action.value : ''); const [reason, setReason] = useState(pendingCommands.action && pendingCommands.action.kind !== 'reply' ? pendingCommands.action.value : '');
  useEffect(() => {
    const create = pendingCommands.create;
    if (create) { setSubject(create.subject); setDescription(create.description); setCategory(create.category); setPriority(create.requestedPriority); }
    const action = pendingCommands.action;
    if (action?.kind === 'reply') setReply(action.value);
    else if (action) setReason(action.value);
  }, [accountId, pendingCommands]);
  const loadTickets = useCallback(async (signal?: AbortSignal) => {
    try { const page = await api.myHelpdeskTickets(0, signal); if (!signal?.aborted) { setTickets(page.items); setSelected(current => page.items.find(ticket => ticket.id === current?.id) ?? page.items[0] ?? null); setError(''); } }
    catch (failure) { if (!signal?.aborted) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Helpdesk tickets could not be loaded.'); } }
    finally { if (!signal?.aborted) setLoading(false); }
  }, [onSessionExpired]);
  useEffect(() => { const controller = new AbortController(); void loadTickets(controller.signal); return () => controller.abort(); }, [loadTickets]);
  useEffect(() => { if (!selected) { setEntries([]); return; } const controller = new AbortController(); api.helpdeskThread(selected.id, 0, controller.signal).then(page => { if (!controller.signal.aborted) setEntries(page.items); }).catch(failure => { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : 'Ticket history could not be loaded.'); }); return () => controller.abort(); }, [selected?.id, selected?.version]);
  const act = async (work: () => Promise<Ticket>) => { setBusy(true); setError(''); try { const updated = await work(); setTickets(current => current.map(ticket => ticket.id === updated.id ? updated : ticket)); setSelected(updated); } catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'The ticket could not be updated.'); } finally { setBusy(false); } };
  const create = async () => {
    if (!pendingCommands.create && (!subject.trim() || !description.trim())) return;
    const command = pendingCommands.create ?? { requestKey: newRequestKey(), subject: subject.trim(), description: description.trim(), category, requestedPriority: priority };
    pendingCommands.create = command;
    await act(async () => {
      const saved = await api.createHelpdeskTicket(command);
      pendingCommands.create = null; setSubject(''); setDescription('');
      setTickets(current => [saved, ...current.filter(ticket => ticket.id !== saved.id)]); return saved;
    });
  };
  const ownerAction = async (kind: 'reply' | 'reopen' | 'escalate') => {
    if (!selected) return;
    const previous = pendingCommands.action;
    if (previous && (previous.kind !== kind || previous.ticketId !== selected.id)) return;
    const value = kind === 'reply' ? reply.trim() : reason.trim();
    if (!previous && !value) return;
    const command = previous ?? { kind, ticketId: selected.id, version: selected.version, requestKey: newRequestKey(), value };
    pendingCommands.action = command;
    await act(async () => {
      const saved = command.kind === 'reply'
        ? await api.replyHelpdeskTicket(command.ticketId, command.version, command.requestKey, command.value)
        : command.kind === 'reopen'
          ? await api.reopenHelpdeskTicket(command.ticketId, command.version, command.requestKey, command.value)
          : await api.escalateHelpdeskTicket(command.ticketId, command.version, command.requestKey, command.value);
      pendingCommands.action = null;
      if (command.kind === 'reply' && reply.trim() === command.value) setReply('');
      if (command.kind !== 'reply' && reason.trim() === command.value) setReason('');
      return saved;
    });
  };
  return <View style={{ gap: 16 }}>
    <Panel><Heading>24/7 Helpdesk</Heading><Body>Submit a private support request. “24/7” means requests can be filed anytime; response coverage depends on the assigned university office.</Body>
      <Field label="Subject" value={subject} onChange={setSubject} maxLength={150} disabled={busy || !!pendingCommands.create} /><Field label="Describe the issue" value={description} onChange={setDescription} maxLength={4000} multiline disabled={busy || !!pendingCommands.create} />
      <Body>Category</Body><View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>{categories.map(value => <Choice key={value} label={value.replaceAll('_', ' ')} checked={category === value} onChange={() => setCategory(value)} disabled={busy || !!pendingCommands.create} />)}</View>
      <Body>Requested priority</Body><View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>{priorities.map(value => <Choice key={value} label={value} checked={priority === value} onChange={() => setPriority(value)} disabled={busy || !!pendingCommands.create} />)}</View>
      <Button actionId="helpdesk.ticket.create" label={busy ? 'Submitting…' : pendingCommands.create ? 'Retry support request' : 'Submit support request'} disabled={busy || (!pendingCommands.create && (!subject.trim() || !description.trim()))} onPress={() => void create()} />
    </Panel>
    {error ? <Panel><Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text><Button label="Reload tickets" secondary disabled={busy} onPress={() => { setLoading(true); void loadTickets(); }} /></Panel> : null}
    <Panel><Heading>Your support tickets</Heading>{loading ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading helpdesk tickets" /> : tickets.length === 0 ? <Body>No support requests yet.</Body> : tickets.map(ticket => <Pressable key={ticket.id} accessibilityRole="button" accessibilityState={{ selected: selected?.id === ticket.id }} onPress={() => setSelected(ticket)} style={{ gap: 4, borderTopWidth: 1, borderColor: colors.border, paddingTop: 10 }}><Text style={{ color: colors.text, fontWeight: '700' }}>{ticket.ticketNumber} · {ticket.subject}</Text><Body>{ticket.status} · {ticket.priority}{ticket.escalated ? ' · Escalated' : ''}</Body></Pressable>)}</Panel>
    {selected ? <Panel><Heading>{selected.ticketNumber}</Heading><Body>{selected.description}</Body>{selected.resolutionNotes ? <Body>Resolution: {selected.resolutionNotes}</Body> : null}<Heading>Conversation</Heading>{entries.map(entry => <View key={entry.id} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 8 }}><Text style={{ color: colors.text, fontWeight: '700' }}>{entry.authorLabel} · {entry.kind}</Text><Body>{entry.body}</Body></View>)}
      {['OPEN', 'IN_PROGRESS'].includes(selected.status) ? <><Field label="Reply" value={reply} onChange={setReply} maxLength={4000} multiline /><Button actionId="helpdesk.ticket.reply" label={busy ? 'Saving…' : 'Send reply'} disabled={busy || !reply.trim()} onPress={() => void ownerAction('reply')} /><Field label="Reason for escalation" value={reason} onChange={setReason} maxLength={2000} multiline /><Button actionId="helpdesk.ticket.escalate" label={selected.escalated ? 'Escalation pending review' : 'Escalate ticket'} secondary disabled={busy || selected.escalated || !reason.trim()} onPress={() => void ownerAction('escalate')} /></> : null}
      {selected.canReopen ? <><Field label="Reason to reopen" value={reason} onChange={setReason} maxLength={2000} multiline /><Button actionId="helpdesk.ticket.reopen" label="Reopen ticket" secondary disabled={busy || !reason.trim()} onPress={() => void ownerAction('reopen')} /></> : null}
    </Panel> : null}
    {['STAFF', 'ADMIN'].includes(role) ? <HelpdeskTriage accountId={accountId} admin={role === 'ADMIN'} onSessionExpired={onSessionExpired} /> : null}
  </View>;
}

function HelpdeskTriage({ accountId, admin, onSessionExpired }: { accountId: string; admin: boolean; onSessionExpired: () => void }) {
  const pendingCommands = pendingHelpdeskCommands(accountId);
  const colors = useContext(ThemeContext); const [tickets, setTickets] = useState<ManagedTicket[]>([]); const [selected, setSelected] = useState<ManagedTicket | null>(null); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [office, setOffice] = useState('TECHNICAL'); const [priority, setPriority] = useState('MEDIUM'); const [notes, setNotes] = useState('');
  const load = useCallback(async () => { setLoading(true); try { const page = admin ? await api.allHelpdeskTickets() : await api.assignedHelpdeskTickets(); setTickets(page.items); setSelected(current => page.items.find(ticket => ticket.id === current?.id) ?? page.items[0] ?? null); setError(''); } catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Triage queue could not be loaded.'); } finally { setLoading(false); } }, [admin, onSessionExpired]);
  useEffect(() => { void load(); }, [load]);
  useEffect(() => { if (selected) { setOffice(selected.assignedOffice ?? 'TECHNICAL'); setPriority(selected.priority); setNotes(selected.resolutionNotes ?? ''); } }, [selected?.id, selected?.version]);
  const advance = async () => { if (!selected) return; const status = selected.status === 'OPEN' ? 'IN_PROGRESS' : selected.status === 'IN_PROGRESS' ? 'RESOLVED' : selected.status === 'RESOLVED' ? 'CLOSED' : null; if (!status || (['RESOLVED', 'CLOSED'].includes(status) && !notes.trim())) return; const pending = pendingCommands.triage ?? { ticketId: selected.id, command: { requestKey: newRequestKey(), expectedVersion: selected.version, status, priority, assignedOffice: office, assignedStaffId: selected.assignedStaffId, resolutionNotes: notes.trim() || null } }; pendingCommands.triage = pending; setBusy(true); setError(''); try { const saved = await api.triageHelpdeskTicket(pending.ticketId, pending.command); pendingCommands.triage = null; setTickets(current => current.map(ticket => ticket.id === saved.id ? saved : ticket)); setSelected(saved); } catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Triage update could not be saved.'); } finally { setBusy(false); } };
  return <Panel><Heading>{admin ? 'All support tickets' : 'Assigned support tickets'}</Heading><Body>Only server-authorized managers can change ticket state. Reassigning staff is intentionally not available in this client slice.</Body>{error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}{loading ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading triage queue" /> : tickets.length === 0 ? <Body>No tickets are assigned to this queue.</Body> : tickets.map(ticket => <Pressable key={ticket.id} accessibilityRole="button" accessibilityState={{ selected: selected?.id === ticket.id }} onPress={() => setSelected(ticket)} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 8 }}><Text style={{ color: colors.text, fontWeight: '700' }}>{ticket.ticketNumber} · {ticket.subject}</Text><Body>{ticket.status} · {ticket.priority} · {ticket.assignedOffice ?? 'Unassigned office'}</Body></Pressable>)}{selected ? <View style={{ gap: 10 }}><Heading>Update {selected.ticketNumber}</Heading><Field label="Assigned office" value={office} onChange={setOffice} maxLength={32} /><Field label="Priority" value={priority} onChange={setPriority} maxLength={32} /><Field label="Resolution notes" value={notes} onChange={setNotes} maxLength={2000} multiline /><Button actionId="helpdesk.ticket.triage" label={busy ? 'Saving…' : selected.status === 'OPEN' ? 'Start work' : selected.status === 'IN_PROGRESS' ? 'Resolve ticket' : selected.status === 'RESOLVED' ? 'Close ticket' : 'Ticket closed'} disabled={busy || selected.status === 'CLOSED' || (['IN_PROGRESS', 'RESOLVED'].includes(selected.status) && !notes.trim())} onPress={() => void advance()} /></View> : null}</Panel>;
}
