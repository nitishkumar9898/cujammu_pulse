import React, { useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError } from '../api.ts';
import { Body, Button, Field, Heading, Panel } from '../ui.tsx';
import { ThemeContext } from '../ui.tsx';
import { useContext } from 'react';

type Message = { role: 'user' | 'assistant'; text: string };

export function AssistantScreen({ onSessionExpired }: { onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const send = async () => {
    const message = input.trim();
    if (!message || busy || message.length > 2000) return;
    setInput(''); setError(''); setBusy(true);
    setMessages(current => [...current.slice(-18), { role: 'user', text: message }]);
    try {
      const result = await api.assistantChat(message);
      setMessages(current => [...current.slice(-19), { role: 'assistant', text: result.reply }]);
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) { onSessionExpired(); return; }
      setError(failure instanceof Error ? failure.message : 'Assistant is temporarily unavailable.');
    } finally { setBusy(false); }
  };
  return <Panel>
    <Heading>Campus assistant</Heading>
    <Body>Ask about published campus services and workflows. The assistant cannot approve requests, change records, access private marksheets, or perform privileged actions.</Body>
    <View style={{ gap: 10 }} accessibilityLiveRegion="polite">
      {messages.map((item, index) => <View key={`${item.role}-${index}`} style={{ alignSelf: item.role === 'user' ? 'flex-end' : 'flex-start', maxWidth: '92%', padding: 12, borderRadius: 14, backgroundColor: item.role === 'user' ? colors.accent : colors.badge }}><Text style={{ color: item.role === 'user' ? colors.accentText : colors.text }}>{item.text}</Text></View>)}
    </View>
    <Field label="Message" value={input} onChange={setInput} maxLength={2000} multiline disabled={busy} />
    {error ? <Body>{error}</Body> : null}
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Button label={busy ? 'Thinking…' : 'Send'} actionId="assistant.chat.send" disabled={busy || input.trim().length === 0} onPress={() => void send()} />{busy ? <ActivityIndicator color={colors.accent} accessibilityLabel="Assistant is responding" /> : null}</View>
  </Panel>;
}
