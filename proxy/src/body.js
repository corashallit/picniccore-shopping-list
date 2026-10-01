// Reads a request body up to a byte limit without buffering more than the limit.

export class BodyTooLargeError extends Error {
  constructor(limit) {
    super('request body too large');
    this.name = 'BodyTooLargeError';
    this.limit = limit;
  }
}

/** @returns {Promise<Uint8Array>} */
export async function readBodyLimited(request, limit) {
  const declared = request.headers.get('content-length');
  if (declared !== null && /^[0-9]+$/.test(declared.trim()) && Number(declared) > limit) {
    throw new BodyTooLargeError(limit);
  }
  if (!request.body) return new Uint8Array(0);

  const reader = request.body.getReader();
  const chunks = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > limit) {
      await reader.cancel().catch(() => {});
      throw new BodyTooLargeError(limit);
    }
    chunks.push(value);
  }
  if (chunks.length === 1) return chunks[0];
  const out = new Uint8Array(total);
  let offset = 0;
  for (const c of chunks) {
    out.set(c, offset);
    offset += c.byteLength;
  }
  return out;
}
