/** Bound decompressed response bytes before JSON allocation; never trust Content-Length alone. */
export async function boundedJson(response: Response, maxBytes = 1_048_576): Promise<unknown> {
  if (!Number.isSafeInteger(maxBytes) || maxBytes < 1 || maxBytes > 1_048_576) throw new Error('Invalid response budget.');
  const declared = response.headers.get('content-length');
  if (declared !== null && (!/^\d+$/.test(declared) || Number(declared) > maxBytes)) {
    await response.body?.cancel().catch(() => {});
    throw new Error('The service response exceeds its permitted size.');
  }
  if (!response.body?.getReader) throw new Error('Bounded response streaming is unavailable.');
  const reader = response.body.getReader();
  let bytes = new Uint8Array(Math.min(maxBytes, 8192)); let total = 0;
  try {
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      const nextTotal = total + chunk.value.byteLength;
      if (nextTotal > maxBytes) throw new Error('The service response exceeds its permitted size.');
      if (nextTotal > bytes.length) {
        const grown = new Uint8Array(Math.min(maxBytes, Math.max(bytes.length * 2, nextTotal)));
        grown.set(bytes); bytes = grown;
      }
      bytes.set(chunk.value, total); total = nextTotal;
    }
    return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes.subarray(0,total)));
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
}
