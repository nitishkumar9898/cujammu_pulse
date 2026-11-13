import React, { useEffect, useRef, useState } from 'react';
import { Text, View } from 'react-native';
import { Body, Button } from '../ui';
import { ApiError } from '../api';
import { newRequestKey } from '../request-key';
import { uploadProposalAttachment } from './proposal-attachment-upload';
import { pickProposalAttachment, type SelectedAttachment } from './proposal-attachment-picker';
import type { ProposalMediaKind } from './proposals';
import type { ProposalAttachmentReceipt } from './proposal-attachments';

export type ProposalAttachmentFormProps = {
  proposalId: number; revision: number; kind: ProposalMediaKind; disabled?: boolean;
  acquire(): boolean; release(): void; onReloadRequired(): void;
  onUploaded(receipt: ProposalAttachmentReceipt): void | Promise<void>; onSessionExpired(): void;
};
export function ProposalAttachmentForm(props: ProposalAttachmentFormProps) {
  const [selected, setSelected] = useState<SelectedAttachment | null>(null);
  const [sending, setSending] = useState(false); const [uncertain, setUncertain] = useState(false); const [error, setError] = useState('');
  const pending = useRef<{ selection: SelectedAttachment; key: string; revision: number } | null>(null);
  const locked = useRef(false); const inFlight = useRef(false); const alive = useRef(true); const request = useRef<AbortController | null>(null);
  const releaseRef = useRef(props.release); releaseRef.current = props.release;
  const clear = () => { const previous = pending.current; pending.current = null; try { previous?.selection.release(); } catch { if (alive.current) setError('The temporary image could not be removed from device cache. Contact support before leaving shared-device use.'); } finally { if (locked.current) { locked.current = false; releaseRef.current(); } } if (alive.current) { setSelected(null); setUncertain(false); } };
  useEffect(() => { alive.current = true; return () => { alive.current = false; request.current?.abort(); clear(); }; }, []);
  async function choose() {
    if (props.disabled || inFlight.current || locked.current || !props.acquire()) return;
    locked.current = true; inFlight.current = true; setSending(true); setError('');
    try {
      const key = newRequestKey();
      const selection = await pickProposalAttachment();
      if (!alive.current) { selection?.release(); return; }
      if (!selection) { clear(); return; }
      pending.current = { selection, key, revision: props.revision }; setSelected(selection);
    } catch { clear(); if (alive.current) setError('Image selection failed. Choose one PNG, JPEG or WebP image up to 5 MiB.'); }
    finally { inFlight.current = false; if (alive.current) setSending(false); }
  }
  async function send() {
    const item = pending.current; if (!item || inFlight.current || props.disabled) return;
    inFlight.current = true; setSending(true); setError(''); const controller = new AbortController(); request.current = controller;
    try {
      const receipt = await uploadProposalAttachment(props.proposalId, props.kind, item.revision, item.key, item.selection.file, controller.signal);
      if (!alive.current) return;
      try { await props.onUploaded(receipt); } catch { props.onReloadRequired(); }
      finally { clear(); }
    } catch (failure) {
      if (!alive.current) return;
      if (failure instanceof ApiError && failure.status === 401) { clear(); props.onSessionExpired(); }
      else if (failure instanceof ApiError && failure.status === 409) { clear(); props.onReloadRequired(); }
      else if (failure instanceof ApiError && [400, 403, 404, 413, 415].includes(failure.status)) { clear(); setError('The attachment was rejected. Reload your proposal and check the image requirements.'); props.onReloadRequired(); }
      else { setUncertain(true); setError('Upload was not confirmed. Keep this file unchanged and retry, or reload the proposal to check its saved state.'); }
    } finally { inFlight.current = false; if (alive.current) setSending(false); }
  }
  const label = props.kind === 'banner' ? 'banner' : 'certificate template';
  return <View style={{ gap: 8 }}>
    <Body>{label === 'banner' ? 'Proposal banner' : 'Certificate template'} · PNG, JPEG or WebP, up to 5 MiB.</Body>
    <Body>Attachments expire 30 days after the server accepts the upload. Repeated identical content may retain its original expiry. The confirmed availability date is shown after upload.</Body>
    {error ? <Text accessibilityRole="alert">{error}</Text> : null}
    {selected ? <><Body>Selected: {selected.name} · {Math.ceil(selected.file.size / 1024)} KiB</Body>
      <Button actionId="organizer.event-proposals.attachment.upload" label={sending ? 'Uploading attachment…' : uncertain ? `Retry ${label} upload` : `Upload ${label}`} disabled={sending || props.disabled} onPress={() => void send()} />
      {uncertain ? <Button label="Reload proposal after uncertain upload" secondary disabled={sending} onPress={() => { clear(); props.onReloadRequired(); }} /> : <Button label={`Cancel ${label} selection`} secondary disabled={sending} onPress={clear} />}
    </> : <Button label={`Choose ${label} image`} secondary disabled={props.disabled || sending} onPress={() => void choose()} />}
  </View>;
}
