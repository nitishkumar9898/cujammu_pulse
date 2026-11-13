import React, { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, Text, TextInput, View } from 'react-native';
import * as Crypto from 'expo-crypto';
import { api, ApiError } from '../api';
import type { ChatGroup, ChatMessage } from './contracts';
import { Body, Button, Heading, Panel, ThemeContext, uiStyles } from '../ui';

export function ChatScreen({ onSessionExpired }: { onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [groups, setGroups] = useState<ChatGroup[]>([]);
  const [selected, setSelected] = useState<ChatGroup | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [sendOutcome, setSendOutcome] = useState<'ready' | 'uncertain' | 'rejected' | 'limited'>('ready');
  const pendingAttempt = useRef<{ groupId: number; content: string; requestKey: string } | null>(null);
  const activeGroup = useRef<number | null>(null);
  const requestVersion = useRef(0);
  const directoryRequest = useRef<AbortController | null>(null);
  const sendRequest = useRef<AbortController | null>(null);
  const clearThread = () => { requestVersion.current++; setMessages([]); setDraft(''); pendingAttempt.current = null; setSendOutcome('ready'); };
  const selectGroup = (group: ChatGroup | null) => {
    if (group && activeGroup.current === group.id) return;
    activeGroup.current = group?.id ?? null; clearThread(); setError(''); setSelected(group);
  };
  const fail = (e: unknown) => {
    if (e instanceof ApiError && [401, 403, 404].includes(e.status)) clearThread();
    if (e instanceof ApiError && e.status === 401) { setGroups([]); selectGroup(null); onSessionExpired(); }
    else setError(e instanceof Error ? e.message : 'Chat is unavailable.');
  };
  const loadGroups = useCallback(async (targetPage = 0, preserveAttempt = true) => {
    directoryRequest.current?.abort();
    const controller = new AbortController(); directoryRequest.current = controller;
    const preferred = activeGroup.current;
    setLoading(true); setError(''); setGroups([]); setSelected(null); setHasMore(false);
    requestVersion.current++; setMessages([]);
    if (!preserveAttempt) { clearThread(); activeGroup.current = null; }
    try {
      const result = await api.chatGroupPage(targetPage, controller.signal);
      if (controller.signal.aborted) return;
      if (result.page !== targetPage) throw new Error('The group page changed. Refresh groups before continuing.');
      setGroups(result.items); setPage(result.page); setHasMore(result.hasMore);
      const group = result.items.find(item => item.id === preferred) ?? result.items[0] ?? null;
      if (!group || group.id !== preferred) clearThread();
      activeGroup.current = group?.id ?? null; setSelected(group);
    } catch (e) { if (!controller.signal.aborted) { setGroups([]); setSelected(null); fail(e); } }
    finally { if (!controller.signal.aborted) setLoading(false); }
  }, [onSessionExpired]);
  useEffect(() => {
    void loadGroups();
    return () => { directoryRequest.current?.abort(); sendRequest.current?.abort(); requestVersion.current++; };
  }, [loadGroups]);
  const loadMessages = useCallback(async (signal?: AbortSignal) => {
    const groupId = selected?.id;
    const version = ++requestVersion.current; setMessages([]);
    if (!groupId) return;
    try {
      const result = await api.chatMessages(groupId, signal);
      if (!signal?.aborted && version === requestVersion.current && activeGroup.current === groupId) setMessages(result);
    } catch (e) { if (!signal?.aborted && version === requestVersion.current) { setMessages([]); fail(e); } }
  }, [selected]);
  useEffect(() => { const controller = new AbortController(); void loadMessages(controller.signal); return () => { controller.abort(); requestVersion.current++; }; }, [loadMessages]);
  const send = async () => {
    if (!selected?.canPost || !draft.trim() || busy || sendRequest.current || sendOutcome === 'rejected') return;
    const groupId = selected.id, version = ++requestVersion.current;
    const controller = new AbortController(); sendRequest.current = controller;
    setBusy(true); setError('');
    try {
      const content = draft.trim();
      const previous = pendingAttempt.current;
      const attempt = previous?.groupId === groupId && previous.content === content
        ? previous : { groupId, content, requestKey: Crypto.randomUUID() };
      pendingAttempt.current = attempt;
      const saved = await api.postChatMessage(groupId, content, attempt.requestKey, controller.signal);
      if (!controller.signal.aborted && activeGroup.current === groupId && version === requestVersion.current) {
        setMessages(current => (current.some(message => message.id === saved.id)
          ? current.map(message => message.id === saved.id ? saved : message)
          : [...current, saved]).slice(-100)); setDraft('');
        pendingAttempt.current = null; setSendOutcome('ready');
      }
    } catch (e) {
      if (!controller.signal.aborted && activeGroup.current === groupId && version === requestVersion.current) {
        if (e instanceof ApiError && [401, 403, 404].includes(e.status)) fail(e);
        else if (e instanceof ApiError && e.status === 429) {
          setSendOutcome('limited'); setError('Chat send limit reached. Wait 60 seconds before retrying this message.');
        } else if (e instanceof ApiError && [400, 409].includes(e.status)) {
          setSendOutcome('rejected'); setError(`${e.message} Edit the draft before starting a new send attempt.`);
        } else {
          setSendOutcome('uncertain'); setError('The send could not be confirmed. Refresh groups to check the conversation, or retry this unchanged message safely.');
        }
      }
    } finally { if (sendRequest.current === controller) sendRequest.current = null; if (!controller.signal.aborted) setBusy(false); }
  };
  const editDraft = (value: string) => {
    if (value.trim() !== pendingAttempt.current?.content) { pendingAttempt.current = null; setSendOutcome('ready'); setError(''); }
    setDraft(value);
  };
  return <View style={{ gap: 18 }}>
    <Panel>
      <Heading>Community & event chat</Heading>
      <Body>Your authorized groups are shown here. Department access is granted after university verification. Attachments and automatic group cleanup are not yet available.</Body>
      {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
      <Button label="Refresh groups" secondary disabled={loading || busy} actionId="chat.groups.refresh" onPress={() => void loadGroups(page)} />
      <Body>Groups page {page + 1}</Body>
      <View style={uiStyles.row}>
        <Button label="Previous groups" secondary disabled={loading || busy || page === 0} actionId="chat.groups.previous" onPress={() => void loadGroups(page - 1, false)} />
        <Button label="Next groups" secondary disabled={loading || busy || !hasMore} actionId="chat.groups.next" onPress={() => void loadGroups(page + 1, false)} />
      </View>
    </Panel>
    {loading ? <ActivityIndicator accessibilityLabel="Loading chat groups" color={colors.accent} /> : groups.length === 0 ? <Panel><Body>No authorized chat groups on this page.</Body></Panel> : <>
      <Panel>
        <Heading>Your groups</Heading>
        {groups.map(group => <Pressable key={group.id} accessibilityRole="button" accessibilityState={{ selected: selected?.id === group.id }} onPress={() => selectGroup(group)} style={{ padding: 12, borderWidth: 1, borderColor: selected?.id === group.id ? colors.accent : colors.border, borderRadius: 10, gap: 4 }}>
          <Text style={{ color: colors.text, fontWeight: '700' }}>{group.name}</Text><Body>{group.type} · {group.nightSilenceActive ? 'Night Silence active' : group.canPost ? 'Posting available' : 'Posting restricted'}</Body>
        </Pressable>)}
      </Panel>
      {selected ? <Panel>
        <Heading>{selected.name}</Heading>
        {messages.length ? messages.map(message => <View key={message.id} testID={`chat-message-${message.id}`} style={{ gap: 3, borderBottomWidth: 1, borderColor: colors.border, paddingBottom: 10 }}>
          <Text style={{ color: colors.text, fontWeight: '700' }}>{message.senderName}</Text><Text style={{ color: colors.text }}>{message.content}</Text><Body>{new Date(message.createdAt).toLocaleString()}</Body>
        </View>) : <Body>No messages yet.</Body>}
        {sendOutcome === 'uncertain' ? <Body>This draft may already have been sent. Retry uses the same request identifier. Editing the draft or changing groups starts a new message.</Body> : null}
        <TextInput accessibilityLabel="Message" value={draft} onChangeText={editDraft} maxLength={2000} multiline editable={selected.canPost && !busy} placeholder={selected.canPost ? 'Write a message' : 'Posting is currently restricted'} placeholderTextColor={colors.muted} style={{ minHeight: 84, borderWidth: 1, borderColor: colors.border, borderRadius: 10, padding: 12, color: colors.text, backgroundColor: colors.background }} />
        <Button label={busy ? 'Sending…' : ['uncertain', 'limited'].includes(sendOutcome) ? 'Retry message' : 'Send message'} disabled={!selected.canPost || busy || !draft.trim() || sendOutcome === 'rejected'} actionId="chat.message.post" onPress={() => void send()} />
      </Panel> : null}
    </>}
  </View>;
}
