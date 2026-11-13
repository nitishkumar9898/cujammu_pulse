/** Generates RFC 4122 v4 idempotency keys using the platform CSPRNG. */
let platformUuidGenerator: (() => string) | null = null;

export function configureRequestKeyGenerator(generator: () => string): void {
  platformUuidGenerator = generator;
}

export function newRequestKey(): string {
  if (platformUuidGenerator) {
    const generated = platformUuidGenerator();
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(generated)) {
      throw new Error('Secure request-key generation returned an invalid UUID.');
    }
    return generated;
  }
  const cryptoApi = globalThis.crypto;
  if (!cryptoApi?.getRandomValues) throw new Error('Secure request-key generation is unavailable.');
  const bytes = cryptoApi.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6]! & 0x0f) | 0x40;
  bytes[8] = (bytes[8]! & 0x3f) | 0x80;
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
