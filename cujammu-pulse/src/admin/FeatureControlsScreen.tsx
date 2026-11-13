import React, { useCallback, useContext, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, Text, TextInput, View } from 'react-native';
import { api, ApiError } from '../api';
import type { FeatureControl, FeatureControlAudit } from './feature-controls';
import type { MediaStorageInventory } from './media-storage';
import { Body, Button, Heading, Panel, ThemeContext } from '../ui';

export function FeatureControlsScreen({ onSessionExpired, onChanged }: { onSessionExpired: () => void; onChanged: () => Promise<void> }) {
  const colors = useContext(ThemeContext);
  const [items, setItems] = useState<FeatureControl[]>([]);
  const [auditItems, setAuditItems] = useState<FeatureControlAudit[]>([]);
  const [mediaInventory, setMediaInventory] = useState<MediaStorageInventory | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [reason, setReason] = useState('Operational change reviewed by university administrator');
  const [query, setQuery] = useState('');
  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true); setError('');
    try { setItems(await api.featureControls(signal)); }
    catch (e) { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Feature controls could not be loaded.'); }
    finally { if (!signal?.aborted) setLoading(false); }
  }, [onSessionExpired]);
  const loadAudit = useCallback(async () => {
    try { setAuditItems((await api.featureControlAudit(0, 25)).items); }
    catch (e) { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Feature audit could not be loaded.'); }
  }, [onSessionExpired]);
  const loadMediaInventory = useCallback(async () => {
    try { setMediaInventory(await api.mediaStorageInventory()); }
    catch (e) { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Media inventory could not be loaded.'); }
  }, [onSessionExpired]);
  useEffect(() => { const controller = new AbortController(); void load(controller.signal); return () => controller.abort(); }, [load]);
  const toggle = async (control: FeatureControl) => {
    if (control.locked || !reason.trim()) return;
    setBusy(control.key); setError('');
    try {
      const saved = await api.setFeatureControl(control.key, !control.requestedEnabled, control.revision, reason.trim());
      setItems(current => current.map(item => item.key === saved.key ? saved : item));
      await onChanged();
    } catch (e) { if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'The feature state could not be changed. Refresh and retry.'); }
    finally { setBusy(null); }
  };
  const filtered = items.filter(item => `${item.name} ${item.key} ${item.category}`.toLowerCase().includes(query.trim().toLowerCase()));
  return <View style={{ gap: 18 }}><Panel><Heading>Module & feature controls</Heading>
    <Body>Changes take effect only after the server commits them. A switch never grants a role or bypasses resource authorization. Required safety controls are locked.</Body>
    <TextInput accessibilityLabel="Search feature controls" value={query} onChangeText={setQuery} maxLength={100} placeholder="Search modules or functions" placeholderTextColor={colors.muted} style={{ minHeight: 48, borderWidth: 1, borderColor: colors.border, borderRadius: 10, padding: 12, color: colors.text, backgroundColor: colors.background }} />
    <TextInput accessibilityLabel="Reason for feature change" value={reason} onChangeText={setReason} maxLength={300} placeholder="Reason required for audit" placeholderTextColor={colors.muted} style={{ minHeight: 72, borderWidth: 1, borderColor: colors.border, borderRadius: 10, padding: 12, color: colors.text, backgroundColor: colors.background }} multiline />
    <Button label="Refresh controls" secondary disabled={loading || !!busy} actionId="admin.feature-controls.refresh" onPress={() => void load()} />
    <Button label="Load recent toggle audit" secondary disabled={loading || !!busy} actionId="admin.feature-controls.audit.read" onPress={() => void loadAudit()} />
    <Button label="Inspect media storage" secondary disabled={loading || !!busy} actionId="admin.media-storage.read" onPress={() => void loadMediaInventory()} />
    {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
  </Panel>
  {loading ? <ActivityIndicator accessibilityLabel="Loading feature controls" color={colors.accent} /> : filtered.map(control => <Panel key={control.key}><View style={{ gap: 6 }}><Text style={{ color: colors.text, fontSize: 17, fontWeight: '700' }}>{control.name}</Text><Body>{control.category} · {control.key}</Body><Body>{control.description}</Body><Body>{control.locked ? 'Required safety control · Always enabled' : control.effectiveEnabled ? 'Effective state: Enabled' : 'Effective state: Disabled'}{control.updatedAt ? ` · Updated ${new Date(control.updatedAt).toLocaleString()}` : ''}</Body></View>
    <Pressable accessibilityRole="switch" accessibilityState={{ checked: control.requestedEnabled, disabled: control.locked || !!busy || !reason.trim() }} aria-checked={control.requestedEnabled} aria-disabled={control.locked || !!busy || !reason.trim()} disabled={control.locked || !!busy || !reason.trim()} onPress={() => void toggle(control)} style={{ minHeight: 48, borderRadius: 12, padding: 13, alignItems: 'center', justifyContent: 'center', backgroundColor: control.requestedEnabled ? colors.accent : colors.badge, opacity: control.locked || !!busy || !reason.trim() ? 0.55 : 1 }}><Text style={{ color: control.requestedEnabled ? colors.accentText : colors.text, fontWeight: '700' }}>{busy === control.key ? 'Saving…' : control.locked ? 'Locked safety control' : control.requestedEnabled ? 'Disable feature' : 'Enable feature'}</Text></Pressable>
  </Panel>)}
  {!loading && filtered.length === 0 ? <Panel><Body>No matching control is available.</Body></Panel> : null}
  {auditItems.length ? <Panel><Heading>Recent control changes</Heading>{auditItems.map(item => <View key={item.id} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 8, gap: 3 }}><Body>{item.featureKey} · {item.enabled ? 'Enabled' : 'Disabled'} · {item.role}</Body><Body>{item.reason || 'No reason recorded'} · {item.timestamp ? new Date(item.timestamp).toLocaleString() : 'Unknown time'}</Body></View>)}</Panel> : null}
  {mediaInventory ? <Panel><Heading>Media storage inventory</Heading><Body>Measured {new Date(mediaInventory.measuredAt).toLocaleString()}</Body><Body>Tracked: {mediaInventory.trackedObjects} objects · {mediaInventory.trackedBytes} bytes</Body><Body>Active: {mediaInventory.activeObjects} · Expired: {mediaInventory.expiredObjects} · Held: {mediaInventory.heldObjects}</Body><Body>Failed purge: {mediaInventory.purgeFailedObjects} · Orphans: {mediaInventory.orphanObjects} ({mediaInventory.orphanBytes} bytes)</Body><Body>Orphans are review-only and are never deleted from this screen.</Body></Panel> : null}
  </View>;
}
