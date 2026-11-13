export type ThemePreference = 'system' | 'light' | 'dark';
export const themes = {
  light: { background: '#F2F4F6', surface: '#FFFFFF', text: '#142D40', muted: '#435A6C', accent: '#173E59', accentText: '#FFFFFF', gold: '#80540D', border: '#D5DEE5', badge: '#E9EFF4', error: '#9B1C31', masthead: '#123149', mastheadText: '#FFFFFF', highlight: '#F4CC74', hero: '#EEEAE1', heroText: '#142D40' },
  dark: { background: '#101A24', surface: '#172532', text: '#F2F5F8', muted: '#C0CCD5', accent: '#C8DCE8', accentText: '#102B3A', gold: '#F4CC74', border: '#3A4A57', badge: '#243544', error: '#FFB3BC', masthead: '#0C2639', mastheadText: '#FFFFFF', highlight: '#F4CC74', hero: '#1B303E', heroText: '#F2F5F8' },
};
export function resolveTheme(preference: ThemePreference, system: string | null | undefined) {
  return themes[preference === 'system' ? system === 'dark' ? 'dark' : 'light' : preference];
}
