import React, { createContext, useContext, useState } from 'react';
import { Image, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { resolveTheme } from './theme';

export const ThemeContext = createContext(resolveTheme('light', null));
export function UniversityMark({ size = 68 }: { size?: number }) {
  return <Image source={require('../assets/cujammu-logo.png')} accessibilityRole="image" accessibilityLabel="Central University of Jammu seal" resizeMode="contain" style={{ width: size, height: size }} />;
}
export function Button({ label, onPress, secondary = false, disabled = false, actionId }: { label: string; onPress: () => void; secondary?: boolean; disabled?: boolean; actionId?: string }) {
  const colors = useContext(ThemeContext);
  const [focused, setFocused] = useState(false);
  return <Pressable testID={actionId} accessibilityRole="button" aria-disabled={disabled} accessibilityState={{ disabled }} disabled={disabled} onPress={onPress}
    onFocus={() => setFocused(true)} onBlur={() => setFocused(false)}
    style={({ pressed }) => [uiStyles.button, { borderColor: focused ? colors.gold : secondary ? colors.border : colors.accent, backgroundColor: secondary ? colors.badge : colors.accent, opacity: disabled ? 0.5 : pressed ? 0.8 : 1 }]}>
    <Text style={{ color: secondary ? colors.text : colors.accentText, fontWeight: '700', fontSize: 15 }}>{label}</Text>
  </Pressable>;
}
export function Heading({ children }: { children: React.ReactNode }) {
  const colors = useContext(ThemeContext);
  return <Text accessibilityRole="header" style={[uiStyles.heading, { color: colors.text }]}>{children}</Text>;
}
export function Body({ children }: { children: React.ReactNode }) {
  const colors = useContext(ThemeContext);
  return <Text style={[uiStyles.body, { color: colors.muted }]}>{children}</Text>;
}
export function Panel({ children }: { children: React.ReactNode }) {
  const colors = useContext(ThemeContext);
  return <View style={[uiStyles.panel, { backgroundColor: colors.surface, borderColor: colors.border }]}>{children}</View>;
}
export function Field({ label, value, onChange, maxLength, multiline = false, disabled = false, keyboardType = 'default' }: { label: string; value: string; onChange: (value: string) => void; maxLength: number; multiline?: boolean; disabled?: boolean; keyboardType?: 'default' | 'number-pad' | 'url' }) {
  const colors = useContext(ThemeContext);
  return <View style={{ gap: 6 }}><Text style={{ color: colors.text, fontWeight: '600' }}>{label}</Text>
    <TextInput accessibilityLabel={label} value={value} onChangeText={onChange} maxLength={maxLength} multiline={multiline} editable={!disabled} keyboardType={keyboardType}
      style={{ minHeight: multiline ? 100 : 48, padding: 12, borderRadius: 10, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.background, color: colors.text, fontSize: 16, opacity: disabled ? 0.65 : 1 }} />
  </View>;
}
export function Choice({ label, checked, onChange, disabled = false }: { label: string; checked: boolean; onChange: (value: boolean) => void; disabled?: boolean }) {
  const colors = useContext(ThemeContext);
  return <Pressable accessibilityRole="checkbox" accessibilityLabel={label} aria-checked={checked} aria-disabled={disabled} accessibilityState={{ checked, disabled }} disabled={disabled} onPress={() => onChange(!checked)} style={[uiStyles.choice, { flexDirection: 'row', alignItems: 'center', gap: 12, borderColor: colors.border, backgroundColor: checked ? colors.badge : colors.surface, opacity: disabled ? 0.6 : 1 }]}>
    <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={{ width: 22, height: 22, borderRadius: 5, borderWidth: 2, borderColor: checked ? colors.accent : colors.muted, backgroundColor: checked ? colors.accent : colors.surface, alignItems: 'center', justifyContent: 'center' }}>
      {checked ? <View style={{ width: 6, height: 11, borderRightWidth: 2, borderBottomWidth: 2, borderColor: colors.accentText, transform: [{ rotate: '45deg' }], marginTop: -3 }} /> : null}
    </View>
    <Text style={{ color: colors.text, fontSize: 16, flex: 1 }}>{label}</Text>
  </Pressable>;
}
export const uiStyles = StyleSheet.create({
  panel: { borderWidth: 1, borderRadius: 15, padding: 20, gap: 16, shadowColor: '#102B3A', shadowOpacity: 0.035, shadowRadius: 10, shadowOffset: { width: 0, height: 3 }, elevation: 1 },
  heading: { fontSize: 24, lineHeight: 32, fontWeight: '700', letterSpacing: -0.35 },
  body: { fontSize: 16, lineHeight: 26 },
  row: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 10 },
  button: { minHeight: 48, paddingHorizontal: 18, paddingVertical: 11, borderRadius: 9, borderWidth: 1, justifyContent: 'center', alignItems: 'center' },
  choice: { minHeight: 48, padding: 12, borderRadius: 9, borderWidth: 1, justifyContent: 'center' },
});
