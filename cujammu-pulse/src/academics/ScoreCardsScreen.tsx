import React, { useCallback, useContext, useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import type { ScoreCard } from './contracts';
import { Body, Button, Heading, Panel, ThemeContext } from '../ui';

export function ScoreCardsScreen({ onSessionExpired }: { onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [cards, setCards] = useState<ScoreCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const load = useCallback(async () => {
    setLoading(true);
    try { setCards(await api.myScoreCards()); setError(''); }
    catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Scorecards could not be loaded.'); }
    finally { setLoading(false); }
  }, [onSessionExpired]);
  useEffect(() => { void load(); }, [load]);
  return <View style={{ gap: 16 }}>
    <Panel><Heading>My scorecards</Heading><Body>Only scorecards published for your verified university identity appear here. Official PDF copies are unavailable until the university’s private document service is connected.</Body>
      {loading ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading scorecards" /> : error ? <><Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text><Button secondary label="Retry" onPress={() => void load()} /></> : cards.length === 0 ? <Body>No published scorecards are available for your account.</Body> : cards.map(card => <View key={card.id} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 14, gap: 5 }}>
        <Text style={{ color: colors.text, fontSize: 17, fontWeight: '700' }}>{card.semester} · {card.academicYear}</Text>
        <Body>{card.department}{card.program ? ` · ${card.program}` : ''}</Body>
        <Body>Published version {card.version}</Body>
        <Body>SGPA {card.sgpa == null ? '—' : card.sgpa.toFixed(2)} · CGPA {card.cgpa == null ? '—' : card.cgpa.toFixed(2)}</Body>
        <Text style={{ color: card.resultStatus === 'REAPPEAR' ? colors.error : colors.accent, fontWeight: '700' }}>{card.resultStatus}</Text>
        {card.publishedAt ? <Body>Published {new Date(card.publishedAt).toLocaleString()}</Body> : null}
      </View>)}
    </Panel>
  </View>;
}
