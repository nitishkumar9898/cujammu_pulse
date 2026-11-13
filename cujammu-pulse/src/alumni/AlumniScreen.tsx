import React, { useContext, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Linking, Platform, Text, View } from 'react-native';
import { ApiError } from '../api';
import { Body, Button, Choice, Field, Heading, Panel, ThemeContext, uiStyles } from '../ui';
import { alumniApi } from './api';
import { profileCommand, type DirectoryEntry, type Job, type JobUpdate, type Page, type Profile, type ProfileUpdate } from './contracts';

type Tab = 'directory' | 'mentors' | 'profile' | 'jobs' | 'mine' | 'review';
type Confirmation = { kind: 'job'; value: Job; active: boolean } | { kind: 'verify'; value: Profile; verified: boolean };
const titles: Record<Tab, string> = { directory: 'Directory', mentors: 'Mentors', profile: 'My profile', jobs: 'Opportunities', mine: 'My listings', review: 'Verification review' };
const emptyPage: Page<never> = { items: [], page: 0, hasNext: false };

function AlumniCard({ value }: { value: DirectoryEntry }) {
  return <Panel><Heading>{value.displayName}</Heading><Body>{value.department} · {value.degree} · Class of {value.graduationYear}</Body>
    {value.currentCompany || value.currentDesignation ? <Body>{[value.currentDesignation, value.currentCompany].filter(Boolean).join(' · ')}</Body> : null}
    {value.location ? <Body>{value.location}</Body> : null}{value.bio ? <Body>{value.bio}</Body> : null}
    <Body>{[value.willingToMentor && 'Open to mentoring', value.willingToRefer && 'Open to referrals'].filter(Boolean).join(' · ') || 'Alumni member'}</Body>
    {value.linkedInUrl ? <><Body>{value.linkedInUrl}</Body><Button actionId="alumni.profile.openLinkedIn" label="Open LinkedIn profile" secondary onPress={() => void Linking.openURL(value.linkedInUrl!)} /></> : null}
  </Panel>;
}

export function AlumniScreen({ role, onSessionExpired }: { role: string; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [tab, setTab] = useState<Tab>('directory');
  const [page, setPage] = useState(0);
  const [departmentDraft, setDepartmentDraft] = useState('');
  const [department, setDepartment] = useState('');
  const [revision, setRevision] = useState(0);
  const [directory, setDirectory] = useState<Page<DirectoryEntry>>(emptyPage);
  const [jobs, setJobs] = useState<Page<Job>>(emptyPage);
  const [reviews, setReviews] = useState<Page<Profile>>(emptyPage);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [reviewAcknowledged, setReviewAcknowledged] = useState(false);
  const [profileId, setProfileId] = useState('');
  const request = useRef<AbortController | null>(null);
  const mutationBusy = useRef(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; request.current?.abort(); }; }, []);
  const fail = (failure: unknown) => {
    if (!mounted.current) return;
    if (failure instanceof ApiError && failure.status === 401) { onSessionExpired(); return; }
    setError(failure instanceof ApiError ? failure.message : 'The request could not be confirmed. Reload to check the latest state.');
  };
  useEffect(() => {
    const controller = new AbortController();
    setStatus('loading'); setError(''); setDirectory(emptyPage); setJobs(emptyPage); setReviews(emptyPage); setProfile(null); setConfirmation(null); setReviewAcknowledged(false);
    const load = async () => {
      try {
        if (tab === 'directory' || tab === 'mentors') { const result = await alumniApi.directory(page, department, tab === 'mentors', controller.signal); if (!controller.signal.aborted) setDirectory(result); }
        if (tab === 'profile') { const result = await alumniApi.profile(controller.signal); if (!controller.signal.aborted) setProfile(result); }
        if (tab === 'jobs' || tab === 'mine') {
          const result = await alumniApi.jobs(page, department, tab === 'mine', controller.signal);
          const own = tab === 'mine' ? await alumniApi.profile(controller.signal) : null;
          if (!controller.signal.aborted) { setJobs(result); setProfile(own); }
        }
        if (tab === 'review' && role === 'ADMIN') { const result = await alumniApi.review(page, controller.signal); if (!controller.signal.aborted) setReviews(result); }
        if (!controller.signal.aborted) setStatus('ready');
      } catch (failure) { if (!controller.signal.aborted) { fail(failure); setStatus('error'); } }
    };
    void load(); return () => controller.abort();
  }, [tab, page, department, revision, role]);

  const navigate = (next: Tab) => { if (busy) return; setTab(next); setPage(0); setNotice(''); setError(''); };
  const perform = async <T,>(work: (signal: AbortSignal) => Promise<T>): Promise<T> => {
    if (mutationBusy.current) throw new Error('Request already in progress');
    mutationBusy.current = true;
    const controller = new AbortController(); request.current = controller; setBusy(true); setError(''); setNotice('');
    try { return await work(controller.signal); }
    catch (failure) { if (!controller.signal.aborted) fail(failure); throw failure; }
    finally { mutationBusy.current = false; if (mounted.current) setBusy(false); }
  };
  const confirm = async () => {
    if (!confirmation || (confirmation.kind === 'verify' && !reviewAcknowledged)) return;
    try {
      if (confirmation.kind === 'job') await perform(signal => alumniApi.jobState(confirmation.value.id, confirmation.value.version, confirmation.active, signal));
      else await perform(signal => alumniApi.verify(confirmation.value.id, confirmation.value.version, confirmation.verified, signal));
      if (mounted.current) { setNotice('The updated status was confirmed by the server.'); setRevision(value => value + 1); }
    } catch { /* Error displayed without inventing a successful transition. */ }
  };
  const openReview = async () => {
    const id = Number(profileId);
    if (!Number.isSafeInteger(id) || id < 1) { setError('Enter a valid profile record number.'); return; }
    try { const value = await perform(signal => alumniApi.privateProfile(id, signal)); if (mounted.current) { setReviews({ items: [value], page: 0, hasNext: false }); setConfirmation(null); } } catch { }
  };
  const result = tab === 'directory' || tab === 'mentors' ? directory : tab === 'review' ? reviews : jobs;
  const pageable = tab !== 'profile';
  return <View style={{ gap: 20 }}>
    <Panel><Heading>Alumni connections</Heading><Body>Connect through verified, opt-in profiles. You control which details appear in the directory.</Body>
      <View style={uiStyles.row}>{(['directory', 'mentors', 'profile', 'jobs', 'mine', ...(role === 'ADMIN' ? ['review'] : [])] as Tab[]).map(value => <Button key={value} actionId={`alumni.navigate.${value}`} label={titles[value]} secondary={tab !== value} disabled={busy} onPress={() => navigate(value)} />)}</View>
    </Panel>
    {notice ? <Text accessibilityLiveRegion="polite" aria-live="polite" style={{ color: colors.text }}>{notice}</Text> : null}
    {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{error}</Text> : null}
    {(tab === 'directory' || tab === 'mentors' || tab === 'jobs') ? <Panel><Field label="Department filter (blank for all)" value={departmentDraft} onChange={setDepartmentDraft} maxLength={80} disabled={busy} /><View style={uiStyles.row}><Button actionId="alumni.filter.apply" label="Apply department filter" disabled={busy} onPress={() => { setDepartment(departmentDraft); setPage(0); }} /><Button actionId="alumni.filter.clear" label="Clear filter" secondary disabled={busy} onPress={() => { setDepartmentDraft(''); setDepartment(''); setPage(0); }} /></View></Panel> : null}
    {status === 'loading' ? <ActivityIndicator accessibilityLabel="Loading alumni records" color={colors.accent} /> : status === 'error' ? <Button actionId="alumni.retry" label="Reload alumni records" disabled={busy} onPress={() => setRevision(value => value + 1)} /> : <>
      {tab === 'directory' || tab === 'mentors' ? directory.items.length ? directory.items.map(value => <AlumniCard key={value.id} value={value} />) : <Panel><Body>No visible {tab === 'mentors' ? 'mentors' : 'alumni profiles'} match this filter.</Body></Panel> : null}
      {tab === 'profile' ? <ProfileEditor key={profile?.version ?? 'new'} profile={profile} busy={busy} save={async command => {
        const saved = await perform(signal => alumniApi.saveProfile(command, signal));
        if (mounted.current) { setProfile(saved); setNotice('Profile and visibility saved. Directory listing requires independent verification and your opt-in.'); }
      }} reload={() => setRevision(value => value + 1)} /> : null}
      {tab === 'jobs' || tab === 'mine' ? <>
        {jobs.items.length === 0 ? <Panel><Body>{tab === 'mine' ? 'You have no listings on this page.' : 'No current opportunities match this filter.'}</Body></Panel> : jobs.items.map(value => <Panel key={value.id}><Heading>{value.jobTitle}</Heading><Body>{value.companyName} · {value.jobType.replaceAll('_', ' ')}{value.location ? ` · ${value.location}` : ''}</Body><Body>{value.description}</Body><Body>Department: {value.targetDepartment} · {value.active ? 'Active' : 'Closed'}</Body>
          {value.applicationLinkOrEmail ? <><Body>Application destination: {value.applicationLinkOrEmail}</Body><Button actionId="alumni.job.openApplication" label="Open application destination" secondary onPress={() => void Linking.openURL(value.applicationLinkOrEmail!)} /></> : null}
          {tab === 'mine' ? <Button actionId="alumni.job.reviewState" label={value.active ? 'Close listing' : 'Reopen listing'} disabled={busy || (!value.active && !profile?.verifiedAlumni)} secondary onPress={() => setConfirmation({ kind: 'job', value, active: !value.active })} /> : null}
        </Panel>)}
        {tab === 'mine' ? profile?.verifiedAlumni ? <JobEditor busy={busy} post={async command => {
          await perform(signal => alumniApi.postJob(command, signal));
          if (mounted.current) { setNotice('Your job listing was confirmed by the server.'); setRevision(value => value + 1); }
        }} /> : <Panel><Body>Job posting requires an independently verified alumni profile. Create or review your profile to begin verification.</Body></Panel> : null}
      </> : null}
      {tab === 'review' && role === 'ADMIN' ? <>
        <Panel><Heading>Independent verification</Heading><Body>Review the university's evidence before changing verification. The server blocks verification of your own profile. Removing verification also removes the profile and its listings from public discovery.</Body><Field label="Profile record number" value={profileId} onChange={setProfileId} keyboardType="number-pad" maxLength={16} /><Button actionId="alumni.review.lookup" label="Open profile for review" disabled={busy} onPress={() => void openReview()} /></Panel>
        {reviews.items.length === 0 ? <Panel><Body>No profiles are awaiting review on this page.</Body></Panel> : reviews.items.map(value => <View key={value.id} style={{ gap: 10 }}><AlumniCard value={value} /><Panel><Body>Record {value.id} · Revision {value.version} · {value.verifiedAlumni ? 'Verified' : 'Awaiting verification'} · Directory {value.directoryVisible ? 'opt-in' : 'hidden'}</Body><Button actionId="alumni.verification.review" label={value.verifiedAlumni ? 'Review verification removal' : 'Review verification approval'} disabled={busy} onPress={() => { setConfirmation({ kind: 'verify', value, verified: !value.verifiedAlumni }); setReviewAcknowledged(false); }} /></Panel></View>)}
      </> : null}
      {confirmation ? <Panel><Heading>Confirm {confirmation.kind === 'job' ? 'listing change' : 'verification change'}</Heading><Body>{confirmation.kind === 'job' ? `${confirmation.active ? 'Reopen' : 'Close'} ${confirmation.value.jobTitle}?` : `${confirmation.verified ? 'Verify' : 'Remove verification for'} ${confirmation.value.displayName}?`}</Body>
        {confirmation.kind === 'verify' ? <Choice label="I reviewed the institutional evidence and have authority to decide" checked={reviewAcknowledged} onChange={setReviewAcknowledged} disabled={busy} /> : null}
        <View style={uiStyles.row}><Button actionId="alumni.change.confirm" label={busy ? 'Saving...' : 'Confirm change'} disabled={busy || (confirmation.kind === 'verify' && !reviewAcknowledged)} onPress={() => void confirm()} /><Button actionId="alumni.change.cancel" label="Keep current state" secondary disabled={busy} onPress={() => setConfirmation(null)} /></View>
      </Panel> : null}
      {pageable ? <View style={uiStyles.row}><Button actionId="alumni.page.previous" label="Previous page" secondary disabled={busy || page === 0} onPress={() => setPage(value => value - 1)} /><Body>Page {result.page + 1}</Body><Button actionId="alumni.page.next" label="Next page" secondary disabled={busy || !result.hasNext} onPress={() => setPage(value => value + 1)} /></View> : null}
      <Button actionId="alumni.reload" label="Reload latest records" secondary disabled={busy} onPress={() => setRevision(value => value + 1)} />
    </>}
  </View>;
}

function ProfileEditor({ profile, busy, save, reload }: { profile: Profile | null; busy: boolean; save: (command: ProfileUpdate) => Promise<void>; reload: () => void }) {
  const [draft, setDraft] = useState(() => profileCommand(profile));
  const [year, setYear] = useState(profile ? String(profile.graduationYear) : '');
  const [error, setError] = useState('');
  const set = <K extends keyof ProfileUpdate>(key: K, value: ProfileUpdate[K]) => setDraft(current => ({ ...current, [key]: value }));
  const submit = async () => {
    const graduationYear = Number(year);
    if (!draft.degree.trim() || !/^\d{4}$/.test(year) || graduationYear < 1900 || graduationYear > 2200) { setError('Degree and a valid four-digit graduation year are required.'); return; }
    setError(''); try { await save({ ...draft, graduationYear }); } catch { }
  };
  return <Panel><Heading>My alumni profile</Heading><Body>{profile?.verifiedAlumni ? 'Verified. Degree and graduation year corrections require university review.' : 'Unverified. Saving does not grant alumni privileges. An independent university reviewer verifies your profile.'}</Body><Body>Unsaved changes stay on this screen only. Reload or leaving this section discards them.</Body>
    {profile ? <Body>{profile.displayName} · {profile.department}</Body> : null}
    <Field label="Degree (required)" value={draft.degree} onChange={value => set('degree', value)} maxLength={80} disabled={busy || profile?.verifiedAlumni} />
    <Field label="Graduation year (required)" value={year} onChange={setYear} maxLength={4} keyboardType="number-pad" disabled={busy || profile?.verifiedAlumni} />
    <Field label="Company" value={draft.company} onChange={value => set('company', value)} maxLength={120} disabled={busy} />
    <Field label="Designation" value={draft.designation} onChange={value => set('designation', value)} maxLength={100} disabled={busy} />
    <Field label="Location" value={draft.location} onChange={value => set('location', value)} maxLength={100} disabled={busy} />
    <Field label="LinkedIn HTTPS profile" value={draft.linkedin} onChange={value => set('linkedin', value)} maxLength={250} keyboardType="url" disabled={busy} />
    <Field label="Biography" value={draft.bio} onChange={value => set('bio', value)} maxLength={2000} multiline disabled={busy} />
    <Heading>Directory privacy</Heading><Body>Only verified profiles with directory opt-in appear. Hidden details remain available to you and authorized university reviewers.</Body>
    {([['directoryVisible', 'List my verified profile in the directory'], ['showCareer', 'Show company and designation'], ['showLocation', 'Show location'], ['showBio', 'Show biography'], ['showLinkedIn', 'Show LinkedIn profile'], ['mentor', 'I am willing to mentor'], ['refer', 'I am willing to refer']] as const).map(([key, label]) => <Choice key={key} label={label} checked={draft[key]} onChange={value => set(key, value)} disabled={busy} />)}
    {error ? <Body>{error}</Body> : null}<View style={uiStyles.row}><Button actionId="alumni.profile.save" label={busy ? 'Saving...' : 'Save profile & privacy'} disabled={busy} onPress={() => void submit()} /><Button actionId="alumni.profile.reload" label="Discard changes & reload" secondary disabled={busy} onPress={reload} /></View>
  </Panel>;
}

function JobEditor({ busy, post }: { busy: boolean; post: (command: JobUpdate) => Promise<void> }) {
  const [draft, setDraft] = useState<Omit<JobUpdate, 'requestKey'>>({ companyName: '', jobTitle: '', jobType: 'FULL_TIME', location: '', description: '', linkOrEmail: '', targetDepartment: 'ALL' });
  const [pending, setPending] = useState<JobUpdate | null>(null);
  const pendingRequest = useRef<JobUpdate | null>(null);
  const [error, setError] = useState('');
  const set = (key: keyof typeof draft, value: string) => setDraft(current => ({ ...current, [key]: value }));
  const submit = async () => {
    if (Object.entries(draft).some(([key, value]) => key !== 'location' && !value.trim())) { setError('Fill all required job fields.'); return; }
    if (Platform.OS !== 'web' || !globalThis.crypto?.randomUUID) { setError('Secure request creation is unavailable on this device.'); return; }
    const command = pendingRequest.current ?? { ...draft, requestKey: globalThis.crypto.randomUUID() };
    pendingRequest.current = command; setPending(command); setError('');
    try { await post(command); }
    catch (failure) {
      if (failure instanceof ApiError && (failure.status === 400 || failure.status === 403 || failure.status === 404)) { pendingRequest.current = null; setPending(null); }
      else setError('Result is uncertain. Retry sends the identical request safely. Before starting a different listing, reload My listings to check whether this one was saved.');
    }
  };
  return <Panel><Heading>Post an opportunity</Heading><Body>Publish a clear description and a permitted HTTPS application destination or contact email. No contact or application is sent by this platform.</Body>
    <Field label="Company name (required)" value={draft.companyName} onChange={value => set('companyName', value)} maxLength={120} disabled={busy || !!pending} />
    <Field label="Job title (required)" value={draft.jobTitle} onChange={value => set('jobTitle', value)} maxLength={120} disabled={busy || !!pending} />
    <View style={uiStyles.row}>{['FULL_TIME', 'INTERNSHIP', 'REMOTE', 'HYBRID'].map(value => <Button key={value} actionId={`alumni.job.type.${value}`} label={value.replaceAll('_', ' ')} secondary={draft.jobType !== value} disabled={busy || !!pending} onPress={() => set('jobType', value)} />)}</View>
    <Field label="Job location" value={draft.location} onChange={value => set('location', value)} maxLength={100} disabled={busy || !!pending} />
    <Field label="Description (required)" value={draft.description} onChange={value => set('description', value)} maxLength={4000} multiline disabled={busy || !!pending} />
    <Field label="Application HTTPS link or email (required)" value={draft.linkOrEmail} onChange={value => set('linkOrEmail', value)} maxLength={250} disabled={busy || !!pending} />
    <Field label="Target department (ALL or department name)" value={draft.targetDepartment} onChange={value => set('targetDepartment', value)} maxLength={80} disabled={busy || !!pending} />
    {error ? <Body>{error}</Body> : null}<Button actionId="alumni.job.publish" label={busy ? 'Publishing...' : pending ? 'Retry same listing' : 'Publish opportunity'} disabled={busy} onPress={() => void submit()} />
  </Panel>;
}
