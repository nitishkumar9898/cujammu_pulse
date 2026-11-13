import test from 'node:test';
import assert from 'node:assert/strict';
import { boundedJson } from '../src/bounded-json.ts';

test('response budget accepts exact boundary and rejects excess without trusting declared length', async () => {
  assert.equal(await boundedJson(new Response('"1234"'), 6), '1234');
  await assert.rejects(boundedJson(new Response('"12345"'), 6), /permitted size/);
  await assert.rejects(boundedJson(new Response('"12345"', { headers: { 'content-length': '1' } }), 6), /permitted size/);
});
test('oversized or malformed length cancels before consuming data', async () => {
  for (const length of ['100', '-1', 'abc']) {
    let cancelled = false;
    const body = new ReadableStream({ cancel() { cancelled = true; } });
    await assert.rejects(boundedJson(new Response(body, { headers: { 'content-length': length } }), 6));
    assert.equal(cancelled, true);
  }
});
test('chunked oversized response cancels stream; malformed JSON and UTF-8 fail closed', async () => {
  let cancelled = false;
  const body = new ReadableStream<Uint8Array>({
    start(c) { c.enqueue(new Uint8Array(4)); c.enqueue(new Uint8Array(4)); },
    cancel() { cancelled = true; },
  });
  await assert.rejects(boundedJson(new Response(body), 6), /permitted size/);
  assert.equal(cancelled, true);
  await assert.rejects(boundedJson(new Response('{')));
  await assert.rejects(boundedJson(new Response(new Uint8Array([0xff]))));
});
