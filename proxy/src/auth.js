// Device-token authentication. The proxy never stores tokens, only their SHA-256 hashes:
//  - ACCESS_TOKEN_HASHES (secret): "<hash>" or "<name>:<hash>" entries, compared in constant time;
//  - TOKENS (KV, optional): key = hash, value = {"name","created","disabled"}.
// A KV lookup by hash leaks nothing useful through timing: an attacker would need a SHA-256 preimage of a
// 256-bit random token to learn anything from it.

const encoder = new TextEncoder();
const MAX_TOKEN_LENGTH = 256;

/** @returns {Promise<Uint8Array>} */
export async function sha256(text) {
  return new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(text)));
}

export function toHex(bytes) {
  let out = '';
  for (const b of bytes) out += b.toString(16).padStart(2, '0');
  return out;
}

/** Constant-time equality of two byte arrays of the same length (no early exit). */
export function timingSafeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

/** The bearer token from the Authorization header, or null. */
export function bearerToken(request) {
  const header = request.headers.get('authorization');
  if (!header) return null;
  const m = /^Bearer[ ]+([\x21-\x7e]+)[ ]*$/i.exec(header);
  if (!m || m[1].length > MAX_TOKEN_LENGTH) return null;
  return m[1];
}

/**
 * @param {Request} request
 * @param {import('./config.js').Config} config
 * @returns {Promise<{ok: true, hash: string, label: string} | {ok: false}>}
 *   `label` is safe to log: the token's configured name, else the first 8 hex chars of its hash.
 */
export async function authenticate(request, config) {
  const token = bearerToken(request);
  if (token === null) return { ok: false };
  const digest = await sha256(token);
  const hash = toHex(digest);

  // Scan every configured hash without stopping at a match.
  let match = null;
  for (const entry of config.tokenHashes) {
    const equal = timingSafeEqual(digest, entry.bytes);
    if (equal && match === null) match = entry;
  }
  if (match) return { ok: true, hash, label: match.label ?? hash.slice(0, 8) };

  if (config.tokensKv) {
    const raw = await config.tokensKv.get(hash);
    if (raw !== null && raw !== undefined) {
      let record = null;
      try {
        record = typeof raw === 'string' ? JSON.parse(raw) : raw;
      } catch {
        record = null; // not a JSON record: treat as unknown (fail closed)
      }
      if (record && typeof record === 'object' && !Array.isArray(record) && record.disabled !== true) {
        return { ok: true, hash, label: safeLabel(record.name) ?? hash.slice(0, 8) };
      }
    }
  }
  return { ok: false };
}

function safeLabel(name) {
  if (typeof name !== 'string') return null;
  const cleaned = name.replace(/[^A-Za-z0-9._ -]/g, '').trim().slice(0, 48);
  return cleaned || null;
}
