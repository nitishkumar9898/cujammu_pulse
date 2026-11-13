import React, { useContext, useState } from 'react';
import { Text, View } from 'react-native';
import { api, ApiError } from '../api';
import type { LibrarySession } from './contracts';
import type { FeatureFlags } from '../contracts';
import { Body, Button, Heading, Panel, ThemeContext } from '../ui';

export function LibraryScreen({ flags, onSessionExpired }: { flags: FeatureFlags; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [history, setHistory] = useState<LibrarySession[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const loadHistory = async () => { try { setHistory(await api.libraryHistory()); } catch (e) { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Library history could not be loaded.'); } };
  const workflowReady = flags.ACTION_LIBRARY_ENTRY === true && flags.ACTION_LIBRARY_EXIT === true;
  return <View style={{ gap: 14 }}><Panel><Heading>Smart library</Heading>
    <Body>{workflowReady
      ? 'Entry and exit remain unavailable until this client can verify the facility QR challenge and physical scan.'
      : 'The library turnstile is temporarily disabled. Entry and exit are unavailable.'}</Body>
    {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
    <Button label={busy ? 'Loading…' : 'View my library history'} secondary disabled={busy} actionId="library.history.read"
      onPress={async () => { setBusy(true); setError(''); try { setHistory(await api.libraryHistory()); } catch (e) { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Library history could not be loaded.'); } finally { setBusy(false); } }} />
  </Panel>{history.length ? <Panel><Heading>My library history</Heading>{history.map(item => <View key={item.id} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 8 }}><Body>{item.sessionType} · {item.status}</Body><Body>{new Date(item.entryTime).toLocaleString()} · {item.durationFormatted ?? 'Active'}</Body></View>)}</Panel> : null}</View>;
}
