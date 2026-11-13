import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { performance, monitorEventLoopDelay } from 'node:perf_hooks';
import { freemem, totalmem, loadavg } from 'node:os';

// Fixed bounded local workload: no credentials, mutations, external target or
// arbitrary route input. Keeps each catalogue below its normal per-peer quota.
const origin = new URL(process.env.PULSE_SECURITY_ORIGIN ?? 'http://127.0.0.1:18317');
assert.equal(origin.protocol, 'http:');
assert.equal(origin.hostname, '127.0.0.1');
assert.ok(origin.port && !origin.username && !origin.password && origin.pathname === '/' && !origin.search && !origin.hash);
const output = resolve(process.env.PULSE_SECURITY_OUTPUT ?? '../college-events/target/security-sustained-loopback.json');
const durationSeconds = Number(process.env.PULSE_SECURITY_DURATION_SECONDS ?? '60');
const provisionalP95TargetMs = 500;
assert.ok(Number.isInteger(durationSeconds) && durationSeconds >= 60 && durationSeconds <= 300);
const checks = []; const samples = []; const statuses = {}; const requestFailures = [];
const environmentSamples = [];
const eventLoop = monitorEventLoopDelay({ resolution: 20 });
eventLoop.enable();
const diagnosticStarted = performance.now();
const cpuStarted = process.cpuUsage();
function sampleEnvironment() {
  const memory = process.memoryUsage();
  environmentSamples.push({ elapsedMs: Math.round(performance.now() - diagnosticStarted), hostFreeMemoryBytes: freemem(), nodeRssBytes: memory.rss, nodeHeapUsedBytes: memory.heapUsed, nodeCpuMicroseconds: process.cpuUsage(cpuStarted), eventLoopMaxMs: Math.round(eventLoop.max / 1e6) });
}
sampleEnvironment();
const environmentTimer = setInterval(sampleEnvironment, 1000);
environmentTimer.unref();
let workloadStartedAt;
let workloadElapsedMs;
let workloadStart;
let phase = 'preflight';
async function read(path, options = {}) {
  const start = performance.now();
  const response = await fetch(new URL(path, origin), { ...options, redirect: 'manual', signal: AbortSignal.timeout(5000) });
  const body = await response.text();
  assert.ok(body.length < 1024 * 1024, 'Unexpected response size');
  return { response, body, ms: performance.now() - start };
}
async function check(name, action) { await action(); checks.push({ name, passed: true }); }
let failure;
try {
  await check('private and operational routes do not disclose data anonymously', async () => {
    for (const path of ['/api/v1/session', '/api/v1/cujammu/exams/scorecards/my', '/api/v1/cujammu/helpdesk/all', '/uploads/synthetic-private.pdf', '/actuator/env', '/actuator/heapdump']) {
      const { response } = await read(path);
      assert.ok([302, 401, 403, 404].includes(response.status), `${path}: unexpected status ${response.status}`);
      if (response.status === 302) assert.equal(new URL(response.headers.get('location'), origin).origin, origin.origin);
    }
  });
  await check('foreign origin receives no credentialed CORS allowance', async () => {
    for (const method of ['GET', 'OPTIONS']) {
      const { response } = await read('/api/v1/cujammu/departments', { method, headers: { Origin: 'https://attacker.example.invalid', 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'X-CSRF-TOKEN' } });
      assert.equal(response.headers.get('access-control-allow-origin'), null);
      assert.equal(response.headers.get('access-control-allow-credentials'), null);
    }
  });
  await check('method override cannot turn a denied write into a private read', async () => {
    const { response } = await read('/api/v1/session', { method: 'POST', headers: { 'X-HTTP-Method-Override': 'GET', Cookie: 'synthetic-probe=PRIVATE_SENTINEL' } });
    assert.ok([401, 403, 405].includes(response.status));
  });
  const paths = ['/api/v1/cujammu/departments', '/api/v1/cujammu/facilities', '/api/v1/cujammu/features'];
  for (const path of paths) assert.equal((await read(path)).response.status, 200);
  const started = performance.now();
  workloadStart = started;
  phase = 'workload';
  workloadStartedAt = new Date().toISOString();
  await Promise.all(Array.from({ length: 4 }, async (_, worker) => {
    let iteration = 0;
    while (performance.now() - started < durationSeconds * 1000) {
      const path = paths[(worker + iteration++) % paths.length];
      const requestStarted = performance.now();
      try {
        const result = await read(path);
        statuses[result.response.status] = (statuses[result.response.status] ?? 0) + 1;
        samples.push({ path, elapsedMs: Math.round(performance.now() - started), ms: Math.round(result.ms), status: result.response.status });
      } catch (error) {
        requestFailures.push({ path, worker, startedElapsedMs: Math.round(requestStarted - started), elapsedMs: Math.round(performance.now() - started), ms: Math.round(performance.now() - requestStarted), type: error.name });
      }
      const remainingMs = durationSeconds * 1000 - (performance.now() - started);
      if (remainingMs > 0) await new Promise(resolve => setTimeout(resolve, Math.min(1000, remainingMs)));
    }
  }));
  workloadElapsedMs = Math.round(performance.now() - started);
  assert.equal(requestFailures.length, 0, 'Workload requests failed; see requestFailures');
  assert.ok(samples.length >= 100, 'Insufficient completed requests');
  assert.equal(Object.keys(statuses).join(','), '200');
  const completedLatencies = samples.map(sample => sample.ms).sort((a, b) => a - b);
  assert.ok(completedLatencies[Math.ceil(completedLatencies.length * .95) - 1] <= provisionalP95TargetMs, 'Provisional p95 latency target exceeded');
} catch (error) { failure = { phase, type: error.name, message: error.message.slice(0, 250) }; }
finally {
  if (workloadStart !== undefined) workloadElapsedMs = Math.round(performance.now() - workloadStart);
  clearInterval(environmentTimer);
  sampleEnvironment();
  eventLoop.disable();
}
const sorted = samples.map(sample => sample.ms).sort((a, b) => a - b);
const percentile = p => sorted.length ? sorted[Math.ceil(sorted.length * p) - 1] : null;
const routeSummaries = [...new Set([...samples, ...requestFailures].map(sample => sample.path))].map(path => {
  const completed = samples.filter(sample => sample.path === path);
  const latencies = completed.map(sample => sample.ms).sort((a, b) => a - b);
  const routePercentile = p => latencies.length ? latencies[Math.ceil(latencies.length * p) - 1] : null;
  return { path, requests: completed.length, failedRequests: requestFailures.filter(sample => sample.path === path).length, statuses: completed.reduce((counts, sample) => { counts[sample.status] = (counts[sample.status] ?? 0) + 1; return counts; }, {}), p50Ms: routePercentile(.5), p95Ms: routePercentile(.95), p99Ms: routePercentile(.99), maxMs: latencies.at(-1) ?? null };
});
const report = { timestamp: new Date().toISOString(), origin: origin.origin, outcome: failure ? 'FAIL' : 'PASS', checks, failure,
  workload: { startedAt: workloadStartedAt, durationTargetSeconds: durationSeconds, elapsedMs: workloadElapsedMs, drainMs: workloadElapsedMs === undefined ? null : Math.max(0, workloadElapsedMs - durationSeconds * 1000), requestTimeoutMs: 5000, workers: 4, pacingMs: 1000, requests: samples.length, failedRequests: requestFailures.length, statuses, p50Ms: percentile(.5), p95Ms: percentile(.95), p99Ms: percentile(.99), provisionalP95TargetMs, targetMet: !failure && sorted.length > 0 && percentile(.95) <= provisionalP95TargetMs },
  diagnostics: { nodeVersion: process.version, hostTotalMemoryBytes: totalmem(), hostLoadAverage: loadavg(), nodeCpuMicroseconds: process.cpuUsage(cpuStarted), eventLoopDelayMs: { p50: Math.round(eventLoop.percentile(50) / 1e6), p95: Math.round(eventLoop.percentile(95) / 1e6), p99: Math.round(eventLoop.percentile(99) / 1e6), max: Math.round(eventLoop.max / 1e6) }, routeSummaries, environmentSamples },
  limitations: ['Three public catalogue routes on loopback/H2 only; not authenticated write load, campus capacity, distributed DoS or a soak/leak proof.', 'Node timers and abort callbacks can run late when the event loop or host is stalled; drain is measured rather than claimed as a hard real-time bound.', 'Host memory and Node event-loop samples identify correlations, not proof of JVM GC, database latency or a memory leak. Windows load average is not a CPU utilization metric.'], requestFailures, samples };
await writeFile(output, JSON.stringify(report, null, 2));
console.log(JSON.stringify({ outcome: report.outcome, checks: checks.length, failure, workload: report.workload }));
if (failure) process.exitCode = 1;
