import React, { useContext, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { ThemeContext } from './ui';
import { groupRoutes, routeLabels, type Route } from './navigation';

export function CampusNavigation({ routes, active, onSelect, compact }: { routes: readonly Route[]; active: Route; onSelect: (route: Route) => void; compact: boolean }) {
  const colors = useContext(ThemeContext);
  const [expanded, setExpanded] = useState(false);
  const [query, setQuery] = useState('');
  const item = (route: Route) => <Pressable key={route} accessibilityRole="tab" accessibilityLabel={routeLabels[route]} aria-selected={active === route} accessibilityState={{ selected: active === route }} onPress={() => { onSelect(route); if (compact) { setQuery(''); setExpanded(false); } }}
    style={({ pressed }) => [styles.item, { backgroundColor: active === route ? colors.badge : pressed ? colors.badge : 'transparent', borderLeftColor: active === route ? colors.gold : 'transparent' }]}>
    <Text style={{ color: active === route ? colors.accent : colors.text, fontSize: 14, lineHeight: 20, fontWeight: active === route ? '700' : '500' }}>{routeLabels[route]}</Text>
  </Pressable>;
  if (compact) {
    const groups = groupRoutes(routes).map(group => ({
      ...group,
      routes: group.routes.filter(route => `${routeLabels[route]} ${group.title}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())),
    })).filter(group => group.routes.length > 0);
    return <View style={[styles.mobile, { backgroundColor: colors.surface, borderColor: colors.border }]}>
      <Pressable accessibilityRole="button" accessibilityLabel={`${expanded ? 'Close' : 'Open'} campus menu, ${routeLabels[active]}`} aria-expanded={expanded} accessibilityState={{ expanded }} onPress={() => setExpanded(value => !value)} style={styles.mobileHeader}>
        <View style={{ flex: 1, gap: 3 }}><Text style={{ color: colors.muted, fontSize: 10, fontWeight: '700', letterSpacing: 1.2 }}>CAMPUS NAVIGATION</Text><Text style={{ color: colors.text, fontSize: 16, fontWeight: '700' }}>{routeLabels[active]}</Text></View>
        <Text aria-hidden style={{ color: colors.accent, fontSize: 20, fontWeight: '700' }}>{expanded ? '−' : '+'}</Text>
      </Pressable>
      {expanded ? <>
        <TextInput accessibilityLabel="Filter campus pages" value={query} onChangeText={setQuery} placeholder="Find a page…" placeholderTextColor={colors.muted} returnKeyType="search" style={[styles.search, { backgroundColor: colors.background, borderColor: colors.border, color: colors.text }]} />
        <ScrollView style={styles.mobileList} contentContainerStyle={{ gap: 18 }} keyboardShouldPersistTaps="handled" nestedScrollEnabled>
          {groups.length ? groups.map(group => <View key={group.title} style={{ gap: 7 }}><Text style={[styles.groupTitle, { color: colors.muted }]}>{group.title.toUpperCase()}</Text><View style={styles.mobileRoutes}>{group.routes.map(route => <View key={route} style={styles.mobileRoute}>{item(route)}</View>)}</View></View>)
            : <Text accessibilityRole="alert" style={{ color: colors.muted, paddingVertical: 12 }}>No pages match “{query}”.</Text>}
        </ScrollView>
      </> : null}
    </View>;
  }
  return <View style={[styles.rail, { borderColor: colors.border, backgroundColor: colors.surface }]}>
    <View style={[styles.railHeader, { borderBottomColor: colors.border }]}><Text style={[styles.railTitle, { color: colors.accent }]}>CAMPUS SERVICES</Text><Text style={{ color: colors.muted, fontSize: 12, lineHeight: 18 }}>Your university workspace</Text></View>
    {groupRoutes(routes).map(group => <View key={group.title} style={{ gap: 3 }}><Text style={[styles.groupTitle, { color: colors.muted }]}>{group.title.toUpperCase()}</Text>{group.routes.map(item)}</View>)}
  </View>;
}
const styles = StyleSheet.create({
  rail: { width: 248, flexShrink: 0, borderWidth: 1, borderRadius: 14, padding: 12, gap: 18, alignSelf: 'flex-start' },
  railHeader: { paddingHorizontal: 10, paddingBottom: 12, borderBottomWidth: 1, gap: 3 },
  railTitle: { fontSize: 11, fontWeight: '800', letterSpacing: 1.2 },
  groupTitle: { fontSize: 10, fontWeight: '700', letterSpacing: 1.1, paddingHorizontal: 10, paddingTop: 3, paddingBottom: 4 },
  item: { minHeight: 44, paddingHorizontal: 12, paddingVertical: 10, borderRadius: 6, borderLeftWidth: 3, justifyContent: 'center' },
  mobile: { borderWidth: 1, borderRadius: 12, padding: 8, gap: 10 },
  mobileHeader: { minHeight: 56, paddingHorizontal: 12, paddingVertical: 8, borderRadius: 9, flexDirection: 'row', alignItems: 'center', gap: 12 },
  mobileList: { maxHeight: 420 },
  mobileRoutes: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  mobileRoute: { flexGrow: 1, flexBasis: '46%' },
  search: { minHeight: 46, borderRadius: 10, borderWidth: 1, paddingHorizontal: 14, fontSize: 16 },
});
