import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, AppState, Platform, Pressable, ScrollView, StatusBar, StyleSheet, Text, TextInput, useColorScheme, useWindowDimensions, View } from 'react-native';
import type { ScrollViewInstance } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { api, ApiError, signOut } from './api';
import type { Department, Facility, FeatureFlags, Session } from './contracts';
import { canOpenModule, visibleModules } from './registry';
import { resolveTheme, type ThemePreference } from './theme';
import { ThemeContext, Button, Heading, Body, Panel, UniversityMark } from './ui';
import { AlumniScreen } from './alumni/AlumniScreen';
import { EventOperationsScreen } from './event-operations/EventOperationsScreen';
import { NotificationsScreen } from './notifications/NotificationsScreen';
import { AvailabilityCard } from './venues/AvailabilityCard';
import { PendingBookings } from './venues/PendingBookings';
import { MyBookings } from './venues/MyBookings';
import { HelpdeskScreen } from './helpdesk/HelpdeskScreen';
import { clearPendingHelpdeskCommands } from './helpdesk/pending-commands';
import { HostelPassScreen } from './hostels/HostelPassScreen';
import { ScoreCardsScreen } from './academics/ScoreCardsScreen';
import { AcademicReviewScreen } from './academics/AcademicReviewScreen';
import { FeatureControlsScreen } from './admin/FeatureControlsScreen';
import { StudentIdentityScreen } from './admin/StudentIdentityScreen';
import { EventProposalReviewScreen } from './admin/EventProposalReviewScreen';
import { LibraryScreen } from './library/LibraryScreen';
import { EventsScreen } from './events/EventsScreen';
import { AttendanceScreen } from './attendance/AttendanceScreen';
import { CalendarScreen } from './calendar/CalendarScreen';
import { DisruptionsScreen } from './disruptions/DisruptionsScreen';
import { ChatScreen } from './chat/ChatScreen';
import { ChatMembershipPanel } from './chat/ChatMembershipPanel';
import { TeamsScreen } from './teams/TeamsScreen';
import { PollsScreen } from './polls/PollsScreen';
import { AssistantScreen } from './assistant/AssistantScreen';
import { InsightsScreen } from './insights/InsightsScreen';
import { CertificatesScreen } from './certificates/CertificatesScreen';
import { FeedScreen } from './feed/FeedScreen';
import { OperationsScreen } from './operations/OperationsScreen';
import { LostFoundScreen } from './lost-found/LostFoundScreen';
import { SportsScreen } from './sports/SportsScreen';
import { JudgingScreen } from './judging/JudgingScreen';
import { VolunteerHubScreen } from './volunteers/VolunteerHubScreen';
import { ReadinessHubScreen } from './readiness/ReadinessHubScreen';
import { selectApiMode } from './identity/transport';
import { ActiveSessionRecovery } from './library/ActiveSessionRecovery';
import { CampusNavigation } from './CampusNavigation';
import { routeLabels, routeGroup, matchesService, type Route } from './navigation';

selectApiMode(Platform.OS === 'web' ? 'web' : 'native');

type LoadState = 'loading' | 'ready' | 'error' | 'signed-out' | 'native-pending';
const intro = [
  ['Your campus. Connected.', 'A shared home for university events, learning spaces and campus services.'],
  ['Your account, your information.', 'Use your verified university account. Access is limited to your assigned role and records.'],
  ['Campus services in one place.', 'Discover departments and venues. Other services appear as their verified workflows become available.'],
  ['Permissions when you need them.', 'Camera and notification permissions will be requested only by the workflows that need them.'],
];

export default function App() { return <SafeAreaProvider><CampusApp /></SafeAreaProvider>; }

function CampusApp() {
  const systemTheme = useColorScheme();
  const { width } = useWindowDimensions();
  const [preference, setPreference] = useState<ThemePreference>('system');
  const [preferenceReady, setPreferenceReady] = useState(false);
  const [preferenceError, setPreferenceError] = useState('');
  const colors = resolveTheme(preference, systemTheme);
  const [walkthrough, setWalkthrough] = useState(0);
  const [route, setRoute] = useState<Route>('home');
  const [state, setState] = useState<LoadState>('loading');
  const [session, setSession] = useState<Session | null>(null);
  const [flags, setFlags] = useState<FeatureFlags>({});
  const [error, setError] = useState('');
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [signOutError, setSignOutError] = useState('');
  const [logoutNeedsRetry, setLogoutNeedsRetry] = useState(false);
  const [nativeSigningIn, setNativeSigningIn] = useState(false);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [facilities, setFacilities] = useState<Facility[]>([]);
  const [catalogueState, setCatalogueState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [catalogueError, setCatalogueError] = useState('');
  const [query, setQuery] = useState('');
  const [serviceQuery, setServiceQuery] = useState('');
  const [catalogueRetry, setCatalogueRetry] = useState(0);
  const refreshController = useRef<AbortController | null>(null);
  const contentController = useRef<AbortController | null>(null);
  const logoutInProgress = useRef(false);
  const pageScroll = useRef<ScrollViewInstance | null>(null);

  useEffect(() => {
    pageScroll.current?.scrollTo({ y: 0, animated: false });
    if (Platform.OS === 'web') document.title = `${routeLabels[route]} · CUJAMMU-PULSE`;
  }, [route]);

  useEffect(() => {
    let active = true;
    AsyncStorage.getItem('pulse.theme').then(value => {
      if (active && (value === 'light' || value === 'dark' || value === 'system')) setPreference(value);
    }).catch(() => { if (active) setPreferenceError('Theme preference could not be loaded.'); })
      .finally(() => { if (active) setPreferenceReady(true); });
    return () => { active = false; };
  }, []);

  const clearPrivateViews = useCallback(() => {
    contentController.current?.abort();
    clearPendingHelpdeskCommands();
    setServiceQuery('');
    setSession(null); setFlags({}); setDepartments([]); setFacilities([]); setUpdatedAt(null); setRoute('home');
  }, []);
  const membershipSessionExpired = useCallback(() => { clearPrivateViews(); setState('signed-out'); }, [clearPrivateViews]);
  const refresh = useCallback(async () => {
    if (logoutInProgress.current) return;
    refreshController.current?.abort();
    const controller = new AbortController(); refreshController.current = controller;
    setRefreshing(true);
    try {
      if (Platform.OS !== 'web') {
        const identity = await import('./identity/native');
        if (!await identity.initializeNativeIdentity()) { clearPrivateViews(); setState('native-pending'); return; }
      }
      const account = await api.session(controller.signal);
      const manifest = await api.features(controller.signal);
      if (controller.signal.aborted) return;
      setSession(account); setFlags(manifest); setUpdatedAt(new Date()); setState('ready'); setError('');
      if (account.role !== 'ADMIN' || manifest.MODULE_EVENTS !== true) setRoute(current => current === 'event-proposal-review' ? 'home' : current);
      if (manifest.MODULE_HELPDESK !== true) clearPendingHelpdeskCommands(String(account.id));
      if (!canOpenModule('venues', manifest)) {
        contentController.current?.abort(); setFacilities([]); setRoute(current => current === 'venues' ? 'home' : current);
      }
      if (!canOpenModule('alumni', manifest)) setRoute(current => current === 'alumni' ? 'home' : current);
      if (manifest.MODULE_EVENTS !== true) setRoute(current => current === 'event-operations' ? 'home' : current);
      if (manifest.MODULE_NOTIFICATIONS !== true) setRoute(current => current === 'notifications' ? 'home' : current);
      if (manifest.MODULE_AI_CHATBOT !== true) setRoute(current => current === 'assistant' ? 'home' : current);
      if (manifest.MODULE_CALENDAR !== true) setRoute(current => current === 'calendar' ? 'home' : current);
      if (manifest.MODULE_DISRUPTIONS !== true) setRoute(current => current === 'disruptions' ? 'home' : current);
      if (manifest.MODULE_INSIGHTS !== true) setRoute(current => current === 'insights' ? 'home' : current);
      if (manifest.MODULE_CERTIFICATES !== true) setRoute(current => current === 'certificates' ? 'home' : current);
      if (manifest.MODULE_SOCIAL_PULSE !== true) setRoute(current => current === 'feed' ? 'home' : current);
      if (manifest.MODULE_OPERATIONS !== true) setRoute(current => current === 'operations' ? 'home' : current);
      if (manifest.MODULE_LOST_FOUND !== true) setRoute(current => current === 'lostfound' ? 'home' : current);
      if (manifest.MODULE_TOURNAMENTS !== true) setRoute(current => current === 'sports' ? 'home' : current);
      if (manifest.MODULE_JUDGING !== true) setRoute(current => current === 'judging' ? 'home' : current);
      if (manifest.MODULE_VOLUNTEERS !== true) setRoute(current => current === 'volunteers' ? 'home' : current);
      if (manifest.MODULE_READINESS !== true || manifest.MODULE_EVENTS !== true) setRoute(current => current === 'readiness' ? 'home' : current);
      if (manifest.MODULE_HELPDESK !== true) setRoute(current => current === 'helpdesk' ? 'home' : current);
      if (manifest.MODULE_HOSTEL_GATEPASS !== true) setRoute(current => current === 'hostels' ? 'home' : current);
      if (manifest.MODULE_COE_EXAMS !== true) setRoute(current => current === 'academics' ? 'home' : current);
      if (manifest.MODULE_POLLS !== true || manifest.MODULE_EVENTS !== true) setRoute(current => current === 'polls' ? 'home' : current);
      if (manifest.MODULE_TEAMS !== true) setRoute(current => current === 'teams' ? 'home' : current);
      if (manifest.MODULE_COMMUNITY_CHAT !== true) setRoute(current => current === 'chat' ? 'home' : current);
    } catch (failure) {
      if (controller.signal.aborted) return;
      clearPrivateViews();
      setState(failure instanceof ApiError && failure.status === 401 ? 'signed-out' : 'error');
      setError(failure instanceof ApiError ? failure.message : 'Cannot connect to campus services. Check your connection and retry.');
    } finally { if (!controller.signal.aborted) setRefreshing(false); }
  }, [clearPrivateViews]);

  useEffect(() => {
    void refresh();
    const interval = setInterval(() => { if (AppState.currentState === 'active') void refresh(); }, 30_000);
    const subscription = AppState.addEventListener('change', next => { if (next === 'active') void refresh(); });
    const focus = () => { void refresh(); };
    if (Platform.OS === 'web') window.addEventListener('focus', focus);
    return () => { clearInterval(interval); subscription.remove(); refreshController.current?.abort(); if (Platform.OS === 'web') window.removeEventListener('focus', focus); };
  }, [refresh]);

  useEffect(() => {
    contentController.current?.abort(); setQuery('');
    if (state !== 'ready' || (route !== 'departments' && route !== 'venues')) return;
    if (route === 'venues' && !canOpenModule('venues', flags)) return;
    const controller = new AbortController(); contentController.current = controller;
    setDepartments([]); setFacilities([]); setCatalogueState('loading');
    const load = async () => {
      try {
        if (route === 'departments') {
          const data = await api.departments(controller.signal);
          if (!controller.signal.aborted) setDepartments(data);
        } else {
          const data = await api.facilities(controller.signal);
          if (!controller.signal.aborted) setFacilities(data);
        }
        if (!controller.signal.aborted) setCatalogueState('ready');
      } catch (failure) {
        if (controller.signal.aborted) return;
        if (failure instanceof ApiError && failure.status === 401) { clearPrivateViews(); setState('signed-out'); return; }
        setCatalogueState('error'); setCatalogueError(failure instanceof ApiError ? failure.message : 'The catalogue could not be loaded. Please retry.');
      }
    };
    void load();
    return () => controller.abort();
  }, [route, state, catalogueRetry, flags.MODULE_VENUES, clearPrivateViews]);

  const selectTheme = async (value: ThemePreference) => {
    setPreference(value); setPreferenceError('');
    try { await AsyncStorage.setItem('pulse.theme', value); }
    catch { setPreferenceError('Theme changed for this session; saving failed.'); }
  };
  const logout = async () => {
    logoutInProgress.current = true; refreshController.current?.abort(); setSigningOut(true); setSignOutError('');
    clearPrivateViews(); setState('signed-out');
    try { await signOut(); setLogoutNeedsRetry(false); logoutInProgress.current = false; }
    catch { setLogoutNeedsRetry(true); setSignOutError('Sign out could not be confirmed. Your private views have been cleared. Retry sign out before signing in again.'); }
    finally { setSigningOut(false); }
  };
  const nativeSignIn = async () => {
    if (logoutNeedsRetry || signingOut) return;
    clearPrivateViews(); setNativeSigningIn(true); setError('');
    try { const identity = await import('./identity/native'); await identity.signInNative(); await refresh(); }
    catch { setState('signed-out'); setError('University sign-in could not be completed. Retry or contact the university identity team.'); }
    finally { setNativeSigningIn(false); }
  };
  const visible = visibleModules(flags);
  const moduleRoute = (id: string): Route => ({ teams: 'teams', alumni: 'alumni', notifications: 'notifications', hostels: 'hostels', academics: 'academics', assistant: 'assistant', insights: 'insights', certificates: 'certificates', feed: 'feed', lostfound: 'lostfound', sports: 'sports', judging: 'judging', operations: 'operations', chat: 'chat', polls: 'polls', calendar: 'calendar', disruptions: 'disruptions', library: 'library', events: 'events', attendance: 'attendance', helpdesk: 'helpdesk', venues: 'venues', volunteers: 'volunteers', readiness: 'readiness' } as Record<string, Route>)[id] ?? 'home';
  const catalogue = route === 'departments' ? departments : facilities;
  const filtered = catalogue.filter(item => item.name.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()));
  const navigationRoutes = [...(state === "ready" && session?.role === "ADMIN" && flags.MODULE_EVENTS === true ? ["event-proposal-review"] : []), 'home', ...(state === 'ready' ? ['departments'] : []), ...(state === 'ready' && canOpenModule('venues', flags) ? ['venues'] : []), ...(state === 'ready' && flags.MODULE_LIBRARY_TURNSTILE === true && session ? ['library'] : []), ...(state === 'ready' && flags.MODULE_EVENTS === true && session ? ['events', 'attendance'] : []), ...(state === 'ready' && canOpenModule('alumni', flags) ? ['alumni'] : []), ...(state === 'ready' && flags.MODULE_EVENTS === true && session && ['ADMIN', 'FACULTY', 'ORGANIZER', 'STUDENT'].includes(session.role) ? ['event-operations'] : []), ...(state === 'ready' && flags.MODULE_POLLS === true && flags.MODULE_EVENTS === true && session ? ['polls'] : []), ...(state === 'ready' && flags.MODULE_TEAMS === true && session ? ['teams'] : []), ...(state === 'ready' && flags.MODULE_COMMUNITY_CHAT === true && session ? ['chat'] : []), ...(state === 'ready' && flags.MODULE_NOTIFICATIONS === true && session ? ['notifications'] : []), ...(state === 'ready' && flags.MODULE_AI_CHATBOT === true && session ? ['assistant'] : []), ...(state === 'ready' && flags.MODULE_INSIGHTS === true && session ? ['insights'] : []), ...(state === 'ready' && flags.MODULE_CERTIFICATES === true && session ? ['certificates'] : []), ...(state === 'ready' && flags.MODULE_SOCIAL_PULSE === true && session ? ['feed'] : []), ...(state === 'ready' && flags.MODULE_LOST_FOUND === true && session ? ['lostfound'] : []), ...(state === 'ready' && flags.MODULE_TOURNAMENTS === true && session ? ['sports'] : []), ...(state === 'ready' && flags.MODULE_JUDGING === true && session ? ['judging'] : []), ...(state === 'ready' && flags.MODULE_HELPDESK === true && session ? ['helpdesk'] : []), ...(state === 'ready' && flags.MODULE_HOSTEL_GATEPASS === true && session ? ['hostels'] : []), ...(state === 'ready' && flags.MODULE_COE_EXAMS === true && session && ['STUDENT', 'STAFF', 'FACULTY', 'ORGANIZER'].includes(session.role) ? ['academics'] : []), ...(state === 'ready' && session?.role === 'ADMIN' && flags.MODULE_OPERATIONS === true ? ['operations'] : []), ...(state === 'ready' && session?.role === 'ADMIN' ? ['feature-controls', 'chat-access', 'student-identity'] : []), ...(state === 'ready' && flags.MODULE_CALENDAR === true ? ['calendar'] : []), ...(state === 'ready' && flags.MODULE_DISRUPTIONS === true ? ['disruptions'] : []), ...(state === 'ready' && flags.MODULE_VOLUNTEERS === true ? ['volunteers'] : []), ...(state === 'ready' && flags.MODULE_READINESS === true && flags.MODULE_EVENTS === true && session && ['ADMIN', 'FACULTY', 'ORGANIZER'].includes(session.role) ? ['readiness'] : []), 'settings'] as Route[];
  const matchingServices = visible.filter(module => module.implemented && navigationRoutes.includes(moduleRoute(module.id)) && matchesService(serviceQuery, module.label, moduleDescription(module.id)));
  const showDepartments = matchesService(serviceQuery, 'Departments', 'Explore the university academic directory');
  return (
    <ThemeContext.Provider value={colors}>
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }}>
      <StatusBar barStyle={colors.background === '#0B1422' ? 'light-content' : 'dark-content'} />
      <View style={styles.utilityBar}><Text style={{ color: colors.mastheadText, fontSize: 11, fontWeight: '600', letterSpacing: 0.7 }}>जम्मू केंद्रीय विश्वविद्यालय  ·  CENTRAL UNIVERSITY OF JAMMU</Text><Text style={{ color: colors.highlight, fontSize: 10, fontWeight: '700', letterSpacing: 1.2 }}>CAMPUS SERVICES</Text></View>
      <ScrollView ref={pageScroll} contentContainerStyle={[styles.page, { paddingHorizontal: width < 600 ? 18 : width < 900 ? 28 : 40 }]} keyboardShouldPersistTaps="handled">
        <View style={[styles.header, { borderBottomColor: colors.border, backgroundColor: colors.surface }]}>
          <View style={[styles.brandGroup, { minWidth: width < 600 ? '100%' : 300 }]}>
            <UniversityMark size={width < 600 ? 58 : 72} />
            <View style={{ flex: 1, gap: 2 }}>
              <Text style={[styles.hindiName, { color: colors.muted, fontSize: width < 600 ? 12 : 14 }]}>जम्मू केंद्रीय विश्वविद्यालय</Text>
              <Text style={[styles.brand, { color: colors.text, fontSize: width < 600 ? 17 : 20 }]}>Central University of Jammu</Text>
              <Text accessibilityLabel="CUJAMMU PULSE, Campus Services" style={[styles.brandProduct, { color: colors.accent }]}>CUJAMMU <Text style={{ color: colors.gold }}>PULSE</Text><Text style={{ color: colors.muted }}>  /  CAMPUS SERVICES</Text></Text>
            </View>
          </View>
          <View style={styles.headerActions}>
            <Button label="Appearance" secondary onPress={() => { setWalkthrough(intro.length); setRoute('settings'); }} />
            {session ? <Button label={signingOut ? 'Signing out…' : 'Sign out'} secondary disabled={signingOut} onPress={() => void logout()} /> : null}
          </View>
        </View>
        {signOutError ? <Panel><Text accessibilityRole="alert" style={{ color: colors.error }}>{signOutError}</Text><Button label={signingOut ? 'Signing out…' : 'Retry sign out'} disabled={signingOut} onPress={() => void logout()} /></Panel> : null}

        {walkthrough < intro.length ? <Panel>
          <View style={styles.introMeta}><Text style={{ color: colors.accent, fontWeight: '800', letterSpacing: 1 }}>YOUR FIRST VISIT</Text><Text style={{ color: colors.muted, fontWeight: '600' }}>STEP {walkthrough + 1} OF {intro.length}</Text></View>
          <View style={styles.introProgress}>{intro.map((_, index) => <View key={index} style={{ height: 4, flex: 1, borderRadius: 2, backgroundColor: index <= walkthrough ? colors.accent : colors.border }} />)}</View>
          <Heading>{intro[walkthrough]?.[0]}</Heading><Body>{intro[walkthrough]?.[1]}</Body>
          <View style={styles.row}><Button label={walkthrough === intro.length - 1 ? 'Get Started' : 'Next'} onPress={() => setWalkthrough(value => value + 1)} /><Button label="Skip introduction" secondary onPress={() => setWalkthrough(intro.length)} /></View>
        </Panel> : <>
          <View style={[styles.workspace, { flexDirection: width >= 1000 ? 'row' : 'column' }]}>
          <CampusNavigation routes={navigationRoutes} active={route} onSelect={setRoute} compact={width < 1000} />
          <View style={styles.content}>
          <View style={styles.routeContext}><Text style={{ color: colors.muted, fontSize: 12 }}>{routeGroup(route)} / {routeLabels[route]}</Text>{session ? <Text style={{ color: colors.muted, fontSize: 12 }}>{session.role.replaceAll('_', ' ').toLocaleLowerCase()} workspace</Text> : null}</View>

          {route === 'settings' ? <Panel><Heading>Make yourself at home</Heading><Body>Choose a look for this device. Auto follows your system appearance.</Body><View style={styles.row}>{(['light', 'dark', 'system'] as ThemePreference[]).map(option => <Pressable key={option} accessibilityRole="radio" aria-checked={preference === option} aria-disabled={!preferenceReady} accessibilityState={{ checked: preference === option, disabled: !preferenceReady }} disabled={!preferenceReady} onPress={() => void selectTheme(option)} style={[styles.button, { backgroundColor: preference === option ? colors.accent : colors.badge }]}><Text style={{ color: preference === option ? colors.accentText : colors.text }}>{option === 'system' ? 'Auto (System)' : option === 'light' ? 'Light' : 'Dark'}</Text></Pressable>)}</View>{preferenceError ? <Body>{preferenceError}</Body> : null}</Panel>
          : state === 'loading' ? <Panel><ActivityIndicator color={colors.accent} accessibilityLabel="Checking campus session" /><Body>Checking your session…</Body></Panel>
          : state === 'native-pending' ? <Panel><Heading>Secure sign-in is being connected</Heading><Body>This Android build does not yet have university sign-in configured. Your account and campus records will become available after the secure identity connection is verified.</Body><Body>Theme and introduction are available on this device.</Body></Panel>
          : state !== 'ready' ? <Panel><Heading>{state === 'signed-out' ? 'Welcome to your campus' : 'Connection unavailable'}</Heading><Body>{state === 'signed-out' ? 'Sign in with your verified university account to access your campus services.' : error}</Body><View style={styles.row}>{state === 'signed-out' && !logoutNeedsRetry && !signingOut && Platform.OS === 'web' ? <Button label="Sign in" onPress={() => window.location.assign('/app/sign-in')} /> : null}{state === 'signed-out' && !logoutNeedsRetry && !signingOut && Platform.OS !== 'web' ? <Button label={nativeSigningIn ? 'Signing in…' : 'Sign in'} disabled={nativeSigningIn} onPress={() => void nativeSignIn()} /> : null}<Button label={refreshing ? 'Checking…' : 'Check connection'} disabled={refreshing} secondary onPress={() => void refresh()} /></View></Panel>
          : route === 'alumni' && session && canOpenModule('alumni', flags) ? <AlumniScreen key={session.id} role={session.role} onSessionExpired={membershipSessionExpired} />
          : route === 'notifications' && session && flags.MODULE_NOTIFICATIONS === true ? <NotificationsScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'assistant' && session && flags.MODULE_AI_CHATBOT === true ? <AssistantScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'insights' && session && flags.MODULE_INSIGHTS === true ? <InsightsScreen role={session.role} key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'certificates' && session && flags.MODULE_CERTIFICATES === true ? <CertificatesScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'feed' && session && flags.MODULE_SOCIAL_PULSE === true ? <FeedScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'lostfound' && session && flags.MODULE_LOST_FOUND === true ? <LostFoundScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'sports' && session && flags.MODULE_TOURNAMENTS === true ? <SportsScreen key={session.id} role={session.role} onSessionExpired={membershipSessionExpired} />
          : route === 'judging' && session && flags.MODULE_JUDGING === true ? <JudgingScreen key={session.id} role={session.role} onSessionExpired={membershipSessionExpired} />
          : route === 'volunteers' && session && flags.MODULE_VOLUNTEERS === true ? <VolunteerHubScreen key={session.id} role={session.role} onSessionExpired={membershipSessionExpired} />
          : route === 'readiness' && session && flags.MODULE_READINESS === true && flags.MODULE_EVENTS === true ? <ReadinessHubScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'operations' && session?.role === 'ADMIN' && flags.MODULE_OPERATIONS === true ? <OperationsScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'helpdesk' && session && flags.MODULE_HELPDESK === true ? <HelpdeskScreen key={session.id} accountId={String(session.id)} role={session.role} onSessionExpired={membershipSessionExpired} />
          : route === 'hostels' && session && flags.MODULE_HOSTEL_GATEPASS === true ? <HostelPassScreen key={session.id} role={session.role} onSessionExpired={membershipSessionExpired} />
          : route === 'academics' && session && flags.MODULE_COE_EXAMS === true && session.role === 'STUDENT' ? <ScoreCardsScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'academics' && session && flags.MODULE_COE_EXAMS === true && ['STAFF', 'FACULTY', 'ORGANIZER'].includes(session.role) ? <AcademicReviewScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'events' && session && flags.MODULE_EVENTS === true ? <EventsScreen key={session.id} role={session.role} onSessionExpired={membershipSessionExpired} canCheckClashes={flags.MODULE_CALENDAR === true && flags.ACTION_CALENDAR_CLASH_CHECK === true} />
          : route === 'attendance' && session && flags.MODULE_EVENTS === true ? <AttendanceScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'calendar' && session && flags.MODULE_CALENDAR === true ? <CalendarScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'disruptions' && session && flags.MODULE_DISRUPTIONS === true ? <DisruptionsScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'library' && session && flags.MODULE_LIBRARY_TURNSTILE === true ? <LibraryScreen key={session.id} flags={flags} onSessionExpired={membershipSessionExpired} />
          : route === 'feature-controls' && session?.role === 'ADMIN' ? <FeatureControlsScreen key={session.id} onSessionExpired={membershipSessionExpired} onChanged={refresh} />
          : route === 'student-identity' && session?.role === 'ADMIN' ? <StudentIdentityScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'event-proposal-review' && session?.role === 'ADMIN' && flags.MODULE_EVENTS === true ? <EventProposalReviewScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'chat-access' && session?.role === 'ADMIN' ? <ChatMembershipPanel grantsEnabled={flags.MODULE_COMMUNITY_CHAT === true} key={session.id} onChanged={() => {}} onSessionExpired={membershipSessionExpired} />
          : route === 'chat' && session && flags.MODULE_COMMUNITY_CHAT === true ? <ChatScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'polls' && session && flags.MODULE_POLLS === true && flags.MODULE_EVENTS === true ? <PollsScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'teams' && session && flags.MODULE_TEAMS === true ? <TeamsScreen key={session.id} onSessionExpired={membershipSessionExpired} />
          : route === 'event-operations' && session && flags.MODULE_EVENTS === true && ['ADMIN', 'FACULTY', 'ORGANIZER', 'STUDENT'].includes(session.role) ? <EventOperationsScreen key={session.id} session={session} flags={flags} onSessionExpired={membershipSessionExpired} />
          : route === 'home' ? <>
            {session ? <ActiveSessionRecovery key={session.id} onSessionExpired={membershipSessionExpired} /> : null}
            <View style={[styles.hero, { backgroundColor: colors.hero, padding: width < 600 ? 22 : 32, borderColor: colors.border }]}>
              <View style={styles.heroInstitution}><UniversityMark size={width < 600 ? 48 : 56} /><View style={{ gap: 2 }}><Text style={{ color: colors.accent, fontSize: 10, fontWeight: '800', letterSpacing: 1.2 }}>CENTRAL UNIVERSITY OF JAMMU</Text><Text style={{ color: colors.muted, fontSize: 13 }}>Campus services, together</Text></View></View>
              <Text accessibilityRole="header" style={[styles.heroTitle, { color: colors.heroText, fontSize: width < 600 ? 29 : 38 }]}>{session?.name ? `Welcome, ${session.name}` : 'Welcome to your campus'}</Text>
              <Text style={{ color: colors.muted, fontSize: 16, lineHeight: 27, maxWidth: 620 }}>Find university events, learning spaces and services from one clear starting point.</Text>
              <View style={styles.row}>{navigationRoutes.includes('events') ? <Button label="Explore events" onPress={() => setRoute('events')} secondary /> : null}{navigationRoutes.includes('calendar') ? <Button label="View my calendar" onPress={() => setRoute('calendar')} secondary /> : null}</View>
            </View>
            <View style={styles.sectionHeading}><View style={{ gap: 4, flex: 1 }}><Heading>Explore campus</Heading><Body>Services available to your account.</Body></View><Button label={refreshing ? 'Refreshing…' : 'Refresh services'} secondary disabled={refreshing} onPress={() => void refresh()} /></View>
            <TextInput accessibilityLabel="Search campus services" value={serviceQuery} onChangeText={setServiceQuery} placeholder="Search services, events or departments" placeholderTextColor={colors.muted} style={[styles.search, { backgroundColor: colors.surface, color: colors.text, borderColor: colors.border }]} />
            <View style={styles.grid}>
              {showDepartments ? <View style={[styles.tile, { backgroundColor: colors.surface, borderColor: colors.border }]}><Text style={{ color: colors.gold, fontWeight: '700', fontSize: 10, letterSpacing: 1 }}>LEARNING & SERVICES</Text><Heading>Departments</Heading><Body>Explore the university's current academic directory.</Body><Button label="View departments" onPress={() => setRoute('departments')} /></View> : null}
              {matchingServices.map(module => <View key={module.id} style={[styles.tile, { backgroundColor: colors.surface, borderColor: colors.border }]}><Text style={{ color: colors.gold, fontWeight: '700', fontSize: 10, letterSpacing: 1 }}>{routeGroup(moduleRoute(module.id)).toUpperCase()}</Text><Heading>{module.label}</Heading><Body>{moduleDescription(module.id)}</Body><Button label={module.id === 'teams' ? 'Open teams' : module.id === 'alumni' ? 'Explore alumni' : module.id === 'notifications' ? 'Open notifications' : module.id === 'hostels' ? 'Open hostel passes' : module.id === 'academics' ? 'Open scorecards' : module.id === 'venues' ? 'Explore venues' : `Open ${module.label.toLocaleLowerCase()}`} onPress={() => setRoute(moduleRoute(module.id))} /></View>)}
            </View>
            {!showDepartments && matchingServices.length === 0 ? <Panel><Text accessibilityRole="header" style={{ color: colors.text, fontWeight: '700', fontSize: 18 }}>No matching services</Text><Body>Try a different name or clear your search to browse available services.</Body><Button label="Clear service search" secondary onPress={() => setServiceQuery('')} /></Panel> : null}
            {visible.some(module => !module.implemented) ? <Panel><Heading>Other campus services</Heading><Body>These services are enabled by the university but their workflows are unavailable in this client release.</Body><View style={styles.row}>{visible.filter(module => !module.implemented).map(module => <Text key={module.id} style={[styles.pill, { backgroundColor: colors.badge, color: colors.muted }]}>{module.label} · Unavailable</Text>)}</View></Panel> : null}
          </> : <Panel><Heading>{route === 'departments' ? 'Academic departments' : 'Venues & grounds'}</Heading><Body>{route === 'departments' ? 'Current directory supplied by the campus service.' : 'Check advisory time suggestions based on known bookings. Suggestions do not reserve a space or confirm its operational readiness.'}</Body>
            <TextInput accessibilityLabel={route === 'departments' ? 'Search departments' : 'Search venues'} value={query} onChangeText={setQuery} placeholder="Search by name" placeholderTextColor={colors.muted} style={[styles.search, { color: colors.text, borderColor: colors.border, backgroundColor: colors.background }]} />
            {route === 'venues' && session ? <MyBookings key={session.id} onSessionExpired={membershipSessionExpired} /> : null}
            {route === 'venues' && session?.role === 'ADMIN' ? <PendingBookings onSessionExpired={membershipSessionExpired} /> : null}
            {catalogueState === 'loading' ? <ActivityIndicator color={colors.accent} accessibilityLabel="Loading catalogue" /> : catalogueState === 'error' ? <><Body>{catalogueError}</Body><Button label="Retry catalogue" onPress={() => setCatalogueRetry(value => value + 1)} /></> : filtered.length === 0 ? <Body>{query ? 'No matching results.' : 'No records are available.'}</Body> : filtered.map(item => 'code' in item
              ? <View key={item.id} style={[styles.catalogueRow, { borderColor: colors.border }]}><Text style={{ color: colors.text, fontWeight: '700', fontSize: 17 }}>{item.name}</Text><Body>{[item.code, item.building].filter(Boolean).join(' · ')}</Body></View>
              : <AvailabilityCard key={item.id} facility={item} canBook={!!session && ['ADMIN', 'FACULTY', 'ORGANIZER'].includes(session.role)} onSessionExpired={membershipSessionExpired} />)}
          </Panel>}
          </View></View>
        </>}
        <View style={[styles.footer, { borderColor: colors.border }]}><Text style={{ color: colors.text, fontSize: 13, lineHeight: 20, fontWeight: '700' }}>CUJAMMU PULSE  ·  Central University of Jammu</Text><Text style={{ color: colors.muted, fontSize: 12 }}>A campus workspace for students, faculty and staff{updatedAt ? `  ·  Services checked ${updatedAt.toLocaleTimeString()}` : ''}</Text></View>
      </ScrollView>
    </SafeAreaView>
    </ThemeContext.Provider>
  );
}

function moduleDescription(id: string): string {
  const descriptions: Record<string, string> = {
    events: 'Discover approved events and manage your registration.', venues: 'Review available-time suggestions and request a reservation.',
    library: 'Review your active library visit and purpose-matched history.', attendance: 'Review pending attendance confirmations.',
    hostels: 'Request a Day-Out or Night-Out pass and review its status.', academics: 'Review scorecards published for your verified identity.',
    chat: 'Read and send messages in your authorized campus groups.', teams: 'Create or join a team and review your roster.',
    judging: 'Review judging scores and authorized event results.', volunteers: 'Review assigned tasks and submit completion hours for review.',
    lostfound: 'Report found items and start a private claim.', helpdesk: 'Submit a support request and follow its progress.',
    sports: 'Review published fixtures and authorized score updates.', polls: 'Review eligible polls and submit your vote.',
    notifications: 'Read private account updates and acknowledge notices.', calendar: 'Review your registered campus events.',
    readiness: 'Review the available readiness checks for events you manage.', insights: 'Submit event feedback and review permitted summaries.',
    disruptions: 'Review schedule-change notices for your registered events.', alumni: 'Discover alumni and manage your profile visibility.',
    feed: 'Read published campus posts and join the conversation.', certificates: 'Review your registration certificate availability.',
    assistant: 'Ask questions using the available campus assistant.', operations: 'Inspect available operational health information.',
  };
  return descriptions[id] ?? 'Review the services available to your account.';
}

const styles = StyleSheet.create({
  utilityBar: { minHeight: 34, paddingHorizontal: 24, paddingVertical: 6, flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 4, backgroundColor: '#123149' },
  page: { width: '100%', maxWidth: 1480, alignSelf: 'center', paddingTop: 0, paddingBottom: 32, gap: 22 },
  workspace: { gap: 24, alignItems: 'stretch' },
  content: { flex: 1, minWidth: 0, gap: 22 },
  routeContext: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', gap: 8, paddingBottom: 2 },
  header: { minHeight: 106, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 14, paddingHorizontal: 4, paddingVertical: 14, borderBottomWidth: 1 },
  brandGroup: { flexDirection: 'row', alignItems: 'center', gap: 12, flexGrow: 1 },
  hindiName: { lineHeight: 19, fontWeight: '600' },
  brand: { fontWeight: '700', letterSpacing: -0.35, lineHeight: 25 },
  brandProduct: { fontSize: 10, fontWeight: '800', letterSpacing: 1.05, marginTop: 2 },
  headerActions: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 8 },
  button: { minHeight: 42, paddingHorizontal: 14, paddingVertical: 10, borderWidth: 1, borderColor: '#9AAEBB', borderRadius: 8, justifyContent: 'center', alignItems: 'center' },
  panel: { borderWidth: 1, borderRadius: 15, padding: 20, gap: 16 },
  heading: { fontSize: 24, lineHeight: 32, fontWeight: '700' },
  body: { fontSize: 16, lineHeight: 26 },
  row: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 10 },
  sectionHeading: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 14 },
  introMeta: { flexDirection: 'row', justifyContent: 'space-between', flexWrap: 'wrap', gap: 6 },
  introProgress: { flexDirection: 'row', gap: 6 },
  hero: { minHeight: 252, borderWidth: 1, borderRadius: 13, gap: 14, justifyContent: 'center' },
  heroInstitution: { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 2 },
  heroTitle: { lineHeight: 46, fontWeight: '700', letterSpacing: -0.75 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 14 },
  tile: { flexGrow: 1, flexBasis: 260, borderWidth: 1, borderLeftWidth: 3, borderLeftColor: '#B98B36', borderRadius: 10, padding: 18, gap: 12 },
  pill: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 7, fontSize: 13 },
  search: { minHeight: 52, borderWidth: 1, borderRadius: 9, paddingHorizontal: 16, fontSize: 16 },
  catalogueRow: { paddingVertical: 18, borderBottomWidth: 1, gap: 8 },
  footer: { borderTopWidth: 1, paddingTop: 16, gap: 4 },
});
