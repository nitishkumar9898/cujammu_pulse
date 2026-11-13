import test from 'node:test';
import assert from 'node:assert/strict';
import { groupRoutes, matchesService, navigationGroups, routeLabels, type Route } from '../src/navigation.ts';
import { themes } from '../src/theme.ts';

test('navigation groups never resurrect routes removed by session or runtime controls', () => {
  const signedOut = groupRoutes(['home', 'settings']);
  assert.deepEqual(signedOut.flatMap(group => group.routes), ['home', 'settings']);
  assert.equal(signedOut.some(group => group.title === 'Administration'), false);
  const scoped = groupRoutes(['home', 'events', 'calendar', 'volunteers']);
  assert.deepEqual(new Set(scoped.flatMap(group => group.routes)), new Set(['home', 'events', 'calendar', 'volunteers']));
});

test('every shared route belongs to exactly one navigation group', () => {
  const routes = navigationGroups.flatMap(group => [...group.routes]);
  assert.equal(new Set(routes).size, routes.length);
  assert.deepEqual([...routes].sort(), Object.keys(routeLabels).sort());
  assert.equal(groupRoutes(Object.keys(routeLabels) as Route[]).flatMap(group => group.routes).length, routes.length);
});

test('service search is case insensitive and matches all terms across name and description', () => {
  assert.equal(matchesService('  LIBRARY history ', 'Smart library', 'Review your active visit and history'), true);
  assert.equal(matchesService('library unavailable', 'Smart library', 'Review history'), false);
  assert.equal(matchesService(' ', 'Departments', 'Academic directory'), true);
});

function luminance(hex: string) {
  const values = [1, 3, 5].map(offset => parseInt(hex.slice(offset, offset + 2), 16) / 255).map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
  return values[0]! * 0.2126 + values[1]! * 0.7152 + values[2]! * 0.0722;
}
test('both appearances preserve AA normal-text contrast for shared semantic pairs', () => {
  for (const palette of Object.values(themes)) {
    for (const [foreground, background] of [[palette.text, palette.surface], [palette.muted, palette.surface], [palette.muted, palette.background], [palette.accentText, palette.accent], [palette.gold, palette.surface], [palette.heroText, palette.hero], [palette.mastheadText, palette.masthead]]) {
      const values = [luminance(foreground!), luminance(background!)].sort((a, b) => a - b);
      assert.ok((values[1]! + 0.05) / (values[0]! + 0.05) >= 4.5, `${foreground} on ${background}`);
    }
  }
});
