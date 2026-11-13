import React, { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { api, ApiError } from '../api';
import { Body, Button, Field, Heading, Panel, Choice, ThemeContext } from '../ui';
import { newRequestKey } from '../request-key';
import type { Readiness } from './contracts';
import type { ReadinessOwner, ReadinessTask } from './task-contracts';

const categories = ['APPROVAL', 'ACCESSIBILITY', 'SAFETY', 'STAFFING', 'RESOURCE', 'OPERATIONS', 'OTHER'] as const;
const statuses = ['OPEN', 'IN_PROGRESS', 'BLOCKED', 'DONE'] as const;
function campusInstant(value: string): string {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) throw new Error('Enter the due time as YYYY-MM-DDTHH:mm in campus local time.');
  const date = new Date(`${value}:00+05:30`);
  if (!Number.isFinite(date.getTime()) || new Date(date.getTime() + 330 * 60_000).toISOString().slice(0, 16) !== value)
    throw new Error('Enter a valid campus local date and time.');
  return date.toISOString();
}
function campusDate(value: string): string {
  return new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) + ' IST';
}

export function ReadinessScreen({ eventId, onSessionExpired }: { eventId: number; onSessionExpired: () => void }) {
  const colors = useContext(ThemeContext);
  const [value, setValue] = useState<Readiness | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const requestId = useRef(0);
  const activeRequest = useRef<AbortController | null>(null);
  const pendingCreateKey = useRef<string | null>(null);
  const [owners, setOwners] = useState<ReadinessOwner[]>([]);
  const [tasks, setTasks] = useState<ReadinessTask[]>([]);
  const [tasksLoading, setTasksLoading] = useState(true);
  const [taskError, setTaskError] = useState('');
  const [savingTask, setSavingTask] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState<typeof categories[number]>('OPERATIONS');
  const [ownerUserId, setOwnerUserId] = useState<number | null>(null);
  const [dueLocal, setDueLocal] = useState('');
  const [completionNotes, setCompletionNotes] = useState<Record<number, string>>({});
  const currentValue = value?.eventId === eventId ? value : null;

  const load = useCallback(async () => {
    activeRequest.current?.abort();
    const controller = new AbortController();
    activeRequest.current = controller;
    const id = ++requestId.current;
    setLoading(true);
    setError(false);
    try {
      const next = await api.eventReadiness(eventId, controller.signal);
      if (id === requestId.current && !controller.signal.aborted) setValue(next);
    } catch (cause) {
      if (controller.signal.aborted || id !== requestId.current) return;
      if (cause instanceof ApiError && cause.status === 401) onSessionExpired();
      else setError(true);
    } finally {
      if (id === requestId.current) {
        activeRequest.current = null;
        setLoading(false);
      }
    }
  }, [eventId, onSessionExpired]);

  const loadTasks = useCallback(async () => {
    setTasksLoading(true);
    try {
      const [nextOwners, nextTasks] = await Promise.all([api.readinessTaskOwners(eventId), api.readinessTasks(eventId)]);
      setOwners(nextOwners);
      setTasks(nextTasks.items);
      setOwnerUserId(current => current != null && nextOwners.some(owner => owner.userId === current) ? current : nextOwners[0]?.userId ?? null);
      setTaskError('');
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) onSessionExpired();
      else setTaskError(cause instanceof Error ? cause.message : 'Readiness tasks could not be loaded.');
    } finally { setTasksLoading(false); }
  }, [eventId, onSessionExpired]);

  useEffect(() => {
    void load();
    return () => {
      requestId.current++;
      activeRequest.current?.abort();
      activeRequest.current = null;
    };
  }, [load]);

  useEffect(() => { void loadTasks(); }, [loadTasks]);

  const createTask = useCallback(async () => {
    setSavingTask(true);
    try {
      pendingCreateKey.current ??= newRequestKey();
      const saved = await api.createReadinessTask(eventId, {
        title, description, category, ownerUserId: ownerUserId!, dueAt: campusInstant(dueLocal), requestKey: pendingCreateKey.current,
      });
      setTasks(current => [saved, ...current.filter(task => task.id !== saved.id)].sort((a, b) => a.dueAt.localeCompare(b.dueAt)));
      pendingCreateKey.current = null;
      setTitle(''); setDescription(''); setTaskError('');
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) onSessionExpired();
      else setTaskError(cause instanceof Error ? cause.message : 'The task could not be saved.');
    } finally { setSavingTask(false); }
  }, [category, description, dueLocal, eventId, onSessionExpired, ownerUserId, title]);

  const updateStatus = useCallback(async (task: ReadinessTask, status: typeof statuses[number]) => {
    setSavingTask(true);
    try {
      const updated = await api.updateReadinessTask(eventId, task.id, {
        expectedRevision: task.revision, title: task.title, description: task.description, category: task.category,
        ownerUserId: task.ownerUserId, dueAt: task.dueAt, status,
        completionNote: status === 'DONE' ? completionNotes[task.id]?.trim() ?? '' : '',
      });
      setTasks(current => current.map(item => item.id === updated.id ? updated : item));
      if (status !== 'DONE') setCompletionNotes(current => ({ ...current, [task.id]: '' }));
      setTaskError('');
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) onSessionExpired();
      else setTaskError(cause instanceof Error ? cause.message : 'The task update failed. Refresh and retry.');
    } finally { setSavingTask(false); }
  }, [completionNotes, eventId, onSessionExpired]);

  return <View style={{ gap: 14 }}>
    <Panel>
      <Heading>Event readiness</Heading>
      {error ? <Text accessibilityRole="alert" style={{ color: colors.error }}>
        {currentValue ? 'Refresh failed. Showing the last confirmed checklist.' : 'Readiness is unavailable. Retry to check again.'}
      </Text> : null}
      {currentValue ? <>
        <Body>Status: {currentValue.ready ? 'Ready for review' : 'Action required'}</Body>
        <View accessibilityRole="list" style={{ gap: 10 }}>
          {currentValue.checks.map(check => <View key={check.key} accessible accessibilityLabel={`${check.passed ? 'Complete' : 'Needs attention'}: ${check.label}. ${check.detail}`} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 10, gap: 4 }}>
            <Text style={{ color: colors.text, fontWeight: '700' }}>{check.passed ? 'Complete' : 'Needs attention'} · {check.label}</Text>
            <Body>{check.detail}</Body>
          </View>)}
        </View>
      </> : loading ? <View accessibilityLiveRegion="polite" style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
        <ActivityIndicator accessibilityLabel="Loading event readiness" />
        <Body>Loading the latest checklist…</Body>
      </View> : null}
      <Button
        label={loading ? 'Refreshing readiness…' : currentValue ? 'Refresh readiness' : 'Retry readiness'}
        secondary
        disabled={loading}
        actionId="readiness.event.read.retry"
        onPress={() => void load()}
      />
    </Panel>
    <Panel>
      <Heading>Accountable readiness tasks</Heading>
      <Body>Assign an event readiness action to the event organizer or an enabled administrator. Due times use campus local time (IST). Task completion is recorded separately and does not itself certify the event ready.</Body>
      {taskError ? <Text accessibilityRole="alert" style={{ color: colors.error }}>{taskError}</Text> : null}
      <Field label="Task title" value={title} onChange={setTitle} maxLength={120} />
      <Field label="Task details" value={description} onChange={setDescription} maxLength={500} multiline />
      <Field label="Due time (YYYY-MM-DDTHH:mm, IST)" value={dueLocal} onChange={setDueLocal} maxLength={16} />
      <Body>Category</Body>
      <View style={{ gap: 8 }}>{categories.map(option => <Choice key={option} label={option.replaceAll('_', ' ')} checked={category === option} onChange={() => setCategory(option)} />)}</View>
      <Body>Owner</Body>
      {owners.length === 0 ? <Body>No eligible task owner is available.</Body> : owners.map(owner => <Choice key={owner.userId} label={`${owner.name} · ${owner.role}`} checked={ownerUserId === owner.userId} onChange={() => setOwnerUserId(owner.userId)} />)}
      <Button label={savingTask ? 'Saving task…' : 'Add readiness task'} actionId="readiness.tasks.create" disabled={savingTask || !title.trim() || !description.trim() || !dueLocal || ownerUserId == null} onPress={() => void createTask()} />
      {tasksLoading ? <ActivityIndicator accessibilityLabel="Loading readiness tasks" /> : tasks.length === 0 ? <Body>No readiness tasks have been assigned.</Body> : tasks.map(task => <View key={task.id} style={{ borderTopWidth: 1, borderColor: colors.border, paddingTop: 12, gap: 7 }}>
        <Heading>{task.title}</Heading><Body>{task.description}</Body>
        <Body>{task.category.replaceAll('_', ' ')} · {task.status.replaceAll('_', ' ')} · Due {campusDate(task.dueAt)}</Body>
        <Body>Owner: {task.ownerName} ({task.ownerRole})</Body>
        {task.completionNote ? <Body>Completion note: {task.completionNote}</Body> : null}
        {task.status !== 'DONE' ? <Field label={`Completion note for ${task.title}`} value={completionNotes[task.id] ?? ''} onChange={value => setCompletionNotes(current => ({ ...current, [task.id]: value }))} maxLength={300} multiline /> : null}
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>{statuses.filter(status => status !== task.status).map(status => <Button key={status} label={`Mark ${status.replaceAll('_', ' ').toLowerCase()}`} secondary disabled={savingTask || status === 'DONE' && !completionNotes[task.id]?.trim()} actionId="readiness.tasks.update" onPress={() => void updateStatus(task, status)} />)}</View>
      </View>)}
      {tasks.length >= 100 ? <Body>Showing the first 100 tasks. Contact the event office to review the full task register.</Body> : null}
      <Button label="Refresh readiness tasks" secondary disabled={tasksLoading || savingTask} actionId="readiness.tasks.read" onPress={() => void loadTasks()} />
    </Panel>
  </View>;
}
