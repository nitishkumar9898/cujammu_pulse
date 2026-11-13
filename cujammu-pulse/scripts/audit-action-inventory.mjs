import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { modules, actions, foundationRequirements } from '../src/registry.ts';
import { navigationGroups, routeLabels } from '../src/navigation.ts';

// Static delivery inventory only. A registry label never certifies runtime/native behavior.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = path.join(root, 'target/action-inventory');
const issues = [];
function duplicates(values, label) {
  const seen = new Set();
  for (const value of values) {
    if (seen.has(value)) issues.push(`Duplicate ${label}: ${value}`);
    seen.add(value);
  }
}
duplicates(modules.map(item => item.id), 'module');
duplicates(actions.map(item => item.actionId), 'action');
const groupedRoutes = navigationGroups.flatMap(group => group.routes);
duplicates(groupedRoutes, 'navigation route');
for (const route of Object.keys(routeLabels)) if (!groupedRoutes.includes(route)) issues.push(`Ungrouped route: ${route}`);
for (const route of groupedRoutes) if (!(route in routeLabels)) issues.push(`Unknown route: ${route}`);
const expected = Array.from({ length: 30 }, (_, index) => `CF-${String(index + 1).padStart(2, '0')}`).filter(id => id !== 'CF-12');
const covered = new Set([...foundationRequirements, ...modules.flatMap(item => item.requirements)]);
for (const id of expected) if (!covered.has(id)) issues.push(`Missing requirement reference: ${id}`);
for (const id of covered) if (!expected.includes(id)) issues.push(`Unexpected requirement reference: ${id}`);
for (const action of actions) {
  if (!action.scenarios.length) issues.push(`Missing scenario reference: ${action.actionId}`);
  if (!action.clients.includes('web') || !action.clients.includes('android')) issues.push(`Missing declared client: ${action.actionId}`);
}
const statusCounts = {};
for (const action of actions) statusCounts[action.status] = (statusCounts[action.status] ?? 0) + 1;
const sources = {};
for (const file of ['src/registry.ts', 'src/navigation.ts']) sources[file] = createHash('sha256').update(await readFile(path.join(root, file))).digest('hex');
const result = {
  schemaVersion: 1, generatedAt: new Date().toISOString(), kind: 'STATIC_DELIVERY_INVENTORY',
  scope: 'Only registered actions and navigation; not all source functions/buttons or runtime acceptance.',
  evidenceLimits: 'Declared implemented/client/scenario metadata is not proof that an action works on deployed web or Android. Consult requirement matrix, actual test reports and release gates.',
  sources, summary: { modules: modules.length, navigationGroups: navigationGroups.length, routes: Object.keys(routeLabels).length, actions: actions.length, activeRequirementReferences: expected.length, statusCounts },
  structuralIssues: issues, modules, navigationGroups, actions,
};
const cell = value => String(value ?? '').replaceAll('|', '\\|').replaceAll('\n', ' ');
const lines = [
  '# Registered action inventory', '', `Generated: ${result.generatedAt}`, '', result.scope, '', result.evidenceLimits, '',
  `${modules.length} modules; ${navigationGroups.length} navigation groups; ${Object.keys(routeLabels).length} routes; ${actions.length} registered actions.`, '',
  `Declared statuses: ${Object.entries(statusCounts).map(([key, count]) => `${key}: ${count}`).join('; ')}.`, '',
  `Structural issues: ${issues.length}. These checks do not validate business behavior.`, '',
  '| Module | Runtime flag | Requirement references |', '|---|---|---|',
  ...modules.map(item => `| ${cell(item.label)} | ${cell(item.flag)} | ${cell(item.requirements.join(', '))} |`), '',
  '| Navigation group | Routes |', '|---|---|',
  ...navigationGroups.map(group => `| ${cell(group.title)} | ${cell(group.routes.join(', '))} |`), '',
  '| Action | Screen | Operation | Runtime flag | Declared status | Scenario references |', '|---|---|---|---|---|---|',
  ...actions.map(item => `| ${cell(item.actionId)} | ${cell(item.screenId)} | ${cell(item.operationId ?? 'UI-local action')} | ${cell(item.featureKey ?? 'Not specified in registry')} | ${cell(item.status)} | ${cell(item.scenarios.join(', '))} |`), '',
  ...(issues.length ? ['## Structural issues', '', ...issues.map(issue => `- ${issue}`), ''] : []),
];
await mkdir(output, { recursive: true });
await writeFile(path.join(output, 'inventory.json'), JSON.stringify(result, null, 2));
await writeFile(path.join(output, 'inventory.md'), lines.join('\n'));
console.log(JSON.stringify({ ...result.summary, structuralIssues: issues, output }, null, 2));
if (issues.length) process.exitCode = 1;
