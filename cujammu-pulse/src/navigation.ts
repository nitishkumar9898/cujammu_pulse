/** Presentation groups only. App supplies the current authorized route inventory. */
export const routeLabels = {
  'student-identity': 'Student verification',
  'event-proposal-review': 'Event proposal review',
  home: 'Overview', departments: 'Departments', venues: 'Venues & grounds', library: 'Library', events: 'Events', attendance: 'Attendance', calendar: 'Calendar', disruptions: 'Campus notices', alumni: 'Alumni', 'event-operations': 'Volunteers', teams: 'Teams', polls: 'Audience polls', chat: 'Community chat', 'chat-access': 'Chat access', notifications: 'Notifications', assistant: 'Campus assistant', insights: 'Feedback & insights', certificates: 'Certificates', feed: 'Campus Pulse', lostfound: 'Lost & Found', sports: 'Sports', judging: 'Judging', operations: 'Operations', helpdesk: 'Helpdesk', hostels: 'Hostel passes', academics: 'Academic records', 'feature-controls': 'Feature controls', settings: 'Appearance', volunteers: 'Volunteer tasks', readiness: 'Event readiness',
} as const;
export type Route = keyof typeof routeLabels;
export const navigationGroups = [
  { title: 'My campus', routes: ['home', 'calendar', 'notifications', 'disruptions'] },
  { title: 'Learning & services', routes: ['departments', 'library', 'academics', 'certificates', 'venues', 'hostels'] },
  { title: 'Events & participation', routes: ['events', 'attendance', 'event-operations', 'volunteers', 'readiness', 'teams', 'sports', 'judging', 'polls'] },
  { title: 'Community & support', routes: ['feed', 'chat', 'alumni', 'lostfound', 'helpdesk', 'insights', 'assistant'] },
  { title: 'Administration', routes: ['operations', 'feature-controls', 'chat-access', 'student-identity', 'event-proposal-review'] },
  { title: 'Preferences', routes: ['settings'] },
] as const;
export function groupRoutes(allowed: readonly Route[]) {
  const permitted = new Set(allowed);
  return navigationGroups.map(group => ({ title: group.title, routes: group.routes.filter(route => permitted.has(route)) })).filter(group => group.routes.length > 0);
}
export function routeGroup(route: Route): string {
  return navigationGroups.find(group => (group.routes as readonly string[]).includes(route))?.title ?? 'My campus';
}
export function matchesService(search: string, label: string, description: string): boolean {
  const terms = search.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
  const text = `${label} ${description}`.toLocaleLowerCase();
  return terms.every(term => text.includes(term));
}
