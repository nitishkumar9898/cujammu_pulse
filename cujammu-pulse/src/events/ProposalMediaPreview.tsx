import React, { useEffect, useState } from 'react';
import { Image, Platform, Text, View } from 'react-native';
import { Body, Button } from '../ui';
import { loadNativeProposalMedia } from './native-proposal-media';
import { ApiError } from '../api';

type PreviewProps = { uri: string; label: string; onSessionExpired: () => void };

export function ProposalMediaPreview(props: PreviewProps) {
  return <AttachmentPreview key={props.uri} {...props} />;
}

function AttachmentPreview({ uri, label, onSessionExpired }: PreviewProps) {
  const [failed, setFailed] = useState(false);
  const [nativeUri, setNativeUri] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (Platform.OS === 'web') return;
    const controller = new AbortController(); let active = true;
    setFailed(false); setNativeUri(null);
    void loadNativeProposalMedia(uri, controller.signal).then(value => { if (active) setNativeUri(value); }).catch(error => {
      if (!active || controller.signal.aborted) return;
      setNativeUri(null); setFailed(true);
      if (error instanceof ApiError && error.status === 401) onSessionExpired();
    });
    return () => { active = false; controller.abort(); };
  }, [uri, onSessionExpired, attempt]);
  if (Platform.OS !== 'web' && !nativeUri && !failed) return <Body>Loading {label.toLowerCase()}…</Body>;
  const source = Platform.OS === 'web' ? uri : nativeUri;
  return <View style={{ gap: 8 }}>
    <Body>{label}</Body>
    {failed ? <><Text accessibilityRole="alert">This attachment is unavailable, expired, or no longer authorized for this account.</Text><Button label="Retry attachment preview" secondary onPress={() => { setFailed(false); setAttempt(value => value + 1); }} /></> :
      <Image key={attempt} source={{ uri: source! }} accessibilityRole="image" accessibilityLabel={label} resizeMode="contain"
        onError={() => setFailed(true)} style={{ width: '100%', maxWidth: 680, height: 320, alignSelf: 'center', backgroundColor: '#EEF1F3' }} />}
  </View>;
}
