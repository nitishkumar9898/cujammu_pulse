import React, { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import { Body, Button, Field, Heading, Panel, ThemeContext, uiStyles } from '../ui';
import { parseMembershipUserId, type ChatMembership, type ChatMembershipPage, type DepartmentChatGroup } from './membership-contracts';

/** Render only in the administrator workspace; all authority is rechecked by the API. */
export function ChatMembershipPanel({ onSessionExpired, onChanged, grantsEnabled = true }: { onSessionExpired: () => void; onChanged: () => void; grantsEnabled?: boolean }) {
  const colors = useContext(ThemeContext);
  const [groups, setGroups] = useState<DepartmentChatGroup[]>([]);
  const [groupId, setGroupId] = useState<number | null>(null);
  const [members, setMembers] = useState<ChatMembershipPage | null>(null);
  const [selected, setSelected] = useState<ChatMembership | null>(null);
  const [userId, setUserId] = useState('');
  const [sourceReference, setSourceReference] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const request = useRef<AbortController | null>(null);
  const mutation = useRef<AbortController | null>(null);
  const alive = useRef(true);
  const clearForm = () => { setSelected(null); setUserId(''); setSourceReference(''); setReason(''); };
  const fail = useCallback((failure: unknown) => {
    if (failure instanceof ApiError && [401, 403, 404].includes(failure.status)) {
      setMembers(null); setGroups([]); setGroupId(null); clearForm();
    }
    if (failure instanceof ApiError && failure.status === 401) onSessionExpired();
    else setError(failure instanceof Error ? failure.message : 'Membership management is unavailable.');
  }, [onSessionExpired]);
  const loadGroups = useCallback(async () => {
    request.current?.abort();
    const controller = new AbortController(); request.current = controller;
    setLoading(true); setError(''); setNotice(''); setGroups([]); setGroupId(null); setMembers(null); clearForm();
    try {
      const result = await api.departmentChatGroups(controller.signal);
      if (!controller.signal.aborted) { setGroups(result); setGroupId(result[0]?.id ?? null); }
    } catch (failure) { if (!controller.signal.aborted) fail(failure); }
    finally { if (!controller.signal.aborted) setLoading(false); }
  }, [fail]);
  const loadMembers = useCallback(async (id: number, page: number) => {
    request.current?.abort();
    const controller = new AbortController(); request.current = controller;
    setLoading(true); setError(''); setMembers(null); clearForm();
    try {
      const result = await api.chatMemberships(id, page, controller.signal);
      if (!controller.signal.aborted) {
        if (result.page !== page) throw new Error('The membership page changed. Refresh the group.');
        setMembers(result);
      }
    } catch (failure) { if (!controller.signal.aborted) fail(failure); }
    finally { if (!controller.signal.aborted) setLoading(false); }
  }, [fail]);
  useEffect(() => {
    alive.current = true; void loadGroups();
    return () => { alive.current = false; request.current?.abort(); mutation.current?.abort(); };
  }, [loadGroups]);
  useEffect(() => { if (groupId != null) void loadMembers(groupId, 0); }, [groupId, loadMembers]);
  const chooseGroup = (id: number) => {
    request.current?.abort(); setMembers(null); clearForm(); setNotice(''); setError('');
    if (id === groupId) void loadMembers(id, 0); else setGroupId(id);
  };
  const selectMember = (member: ChatMembership) => {
    setSelected(member); setUserId(String(member.userId)); setReason(''); setSourceReference(''); setNotice(''); setError('');
  };
  const validReason = reason.trim().length >= 3 && reason.length <= 500;
  const validReference = sourceReference.trim().length >= 3 && sourceReference.length <= 120 && !/[\r\n]/.test(sourceReference);
  const submit = async (active: boolean) => {
    const subject = parseMembershipUserId(userId);
    if (groupId == null || subject == null || busy || loading || !validReason || (active && (!validReference || !grantsEnabled)) || (!active && !selected?.active)) return;
    const controller = new AbortController(); mutation.current = controller;
    const targetGroup = groupId;
    setBusy(true); setError(''); setNotice('');
    try {
      const receipt = await api.setChatMembership(targetGroup, subject, {
        expectedRevision: selected?.revision ?? null, active, sourceReference: sourceReference.trim(), reason: reason.trim(),
      }, controller.signal);
      if (controller.signal.aborted) return;
      if (receipt.userId !== subject || receipt.active !== active) throw new Error('The membership result could not be confirmed. Refresh before trying again.');
      await loadMembers(targetGroup, members?.page ?? 0);
      if (alive.current) { setNotice(active ? 'Department chat access granted.' : 'Department chat access revoked.'); onChanged(); }
    } catch (failure) {
      if (!controller.signal.aborted) {
        if (failure instanceof ApiError && failure.status === 409) {
          await loadMembers(targetGroup, members?.page ?? 0);
          if (alive.current) setError('Membership changed. Select the latest member record and review the change again.');
        } else fail(failure);
      }
    } finally { if (alive.current) setBusy(false); }
  };
  return <Panel>
    <Heading>Department chat access</Heading>
    <Body>Grant access only after university verification. This grants access to this chat group; it does not change the person's institutional department. Self-grants are prohibited.</Body>
    {!grantsEnabled ? <Body>Community chat is disabled. Membership review and revocation remain available; new grants are unavailable.</Body> : null}
    <Button label="Refresh department groups" secondary disabled={busy || loading} actionId="chat.membership.groups.refresh" onPress={() => void loadGroups()} />
    {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
    {notice ? <Text accessibilityLiveRegion="polite" style={{ color: colors.text }}>{notice}</Text> : null}
    {groups.map(group => <Pressable key={group.id} accessibilityRole="button" accessibilityState={{ selected: groupId === group.id, disabled: busy }} disabled={busy} onPress={() => chooseGroup(group.id)} style={{ padding: 12, borderWidth: 1, borderColor: groupId === group.id ? colors.accent : colors.border, borderRadius: 10 }}>
      <Text style={{ color: colors.text, fontWeight: '700' }}>{group.name}</Text><Body>{group.department}</Body>
    </Pressable>)}
    {loading ? <ActivityIndicator accessibilityLabel="Loading department memberships" color={colors.accent} /> : groups.length === 0 ? <Body>No department chat groups are available.</Body> : null}
    {members ? <>
      <Heading>Verified chat memberships</Heading>
      <Body>Page {members.page + 1}. Select a current record to revoke or re-grant access.</Body>
      {members.items.length === 0 ? <Body>No membership records on this page.</Body> : members.items.map(member => <Pressable key={member.userId} accessibilityRole="button" accessibilityLabel={`Select member ${member.userId}`} accessibilityState={{ selected: selected?.userId === member.userId, disabled: busy }} disabled={busy} onPress={() => selectMember(member)} style={{ padding: 12, borderWidth: 1, borderColor: selected?.userId === member.userId ? colors.accent : colors.border, borderRadius: 10 }}>
        <Text style={{ color: colors.text }}>{member.name} · User {member.userId} · {member.active ? 'Active' : 'Revoked'}</Text>
      </Pressable>)}
      <View style={uiStyles.row}>
        <Button label="Previous memberships" actionId="chat.membership.previous" secondary disabled={busy || loading || members.page === 0} onPress={() => groupId != null && void loadMembers(groupId, members.page - 1)} />
        <Button label="Next memberships" actionId="chat.membership.next" secondary disabled={busy || loading || !members.hasMore} onPress={() => groupId != null && void loadMembers(groupId, members.page + 1)} />
      </View>
      <Button label="New membership" actionId="chat.membership.new" secondary disabled={busy || loading} onPress={() => { clearForm(); setNotice(''); }} />
      <Field label="Verified user ID" value={userId} onChange={setUserId} maxLength={16} keyboardType="number-pad" disabled={busy || selected != null} />
      {selected ? <Body>Editing {selected.name}, revision {selected.revision}. Use New membership for a different user.</Body> : <Body>Enter the account ID from the approved university verification process. Existing records must be selected from the list.</Body>}
      <Field label="Verification reference" value={sourceReference} onChange={setSourceReference} maxLength={120} disabled={busy} />
      <Body>Use an internal nonpersonal reference (3–120 characters). Do not paste identity documents or personal evidence. Required for grants.</Body>
      <Field label="Membership change reason" value={reason} onChange={setReason} maxLength={500} multiline disabled={busy} />
      <View style={uiStyles.row}>
        <Button label="Grant chat access" actionId="chat.membership.grant" disabled={!grantsEnabled || busy || loading || parseMembershipUserId(userId) == null || !validReference || !validReason || selected?.active === true} onPress={() => void submit(true)} />
        <Button label="Revoke chat access" actionId="chat.membership.revoke" secondary disabled={busy || loading || !selected?.active || !validReason} onPress={() => void submit(false)} />
      </View>
    </> : null}
  </Panel>;
}
