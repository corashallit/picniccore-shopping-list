// Test harness: the Worker handler with a mocked upstream fetch and a captured log.

import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { createHandler } from '../src/worker.js';

export const TOKEN = 'pk_TEST-device-token_0123456789abcdefghijklmnop';
export const OTHER_TOKEN = 'pk_other-device-token_zyxwvutsrqponmlkjihgfedcba';
export const REAL_KEY = 'sk-test-upstream-key-must-never-reach-the-device';
export const ORIGIN = 'https://picnic.cora.earth';
export const encoder = new TextEncoder();
export const decoder = new TextDecoder();

export const hashOf = (token) => createHash('sha256').update(token).digest('hex');

export function baseEnv(overrides = {}) {
  return { OPENAI_API_KEY: REAL_KEY, ACCESS_TOKEN_HASHES: `test-phone:${hashOf(TOKEN)}`, ...overrides };
}

/** An in-memory KV namespace (only `get` is used by the proxy). */
export function fakeKv(entries = {}) {
  const map = new Map(Object.entries(entries));
  const reads = [];
  return {
    reads,
    async get(key) {
      reads.push(key);
      return map.has(key) ? map.get(key) : null;
    },
  };
}

const defaultRespond = () => new Response(JSON.stringify({ ok: true, model: 'served-model' }), {
  status: 200,
  headers: {
    'content-type': 'application/json',
    'x-request-id': 'req_test_123',
    'openai-processing-ms': '42',
    'openai-organization': 'org-secret-name',
    'openai-project': 'proj_secret',
    'set-cookie': '__cf_bm=secret; path=/',
    'x-ratelimit-remaining-requests': '4999',
    'strict-transport-security': 'max-age=31536000',
  },
});

/**
 * @param {{env?: object, respond?: (call) => Response|Promise<Response>}} [opts]
 */
export function harness(opts = {}) {
  const env = opts.env ?? baseEnv();
  const respond = opts.respond ?? defaultRespond;
  const calls = [];
  const logs = [];
  const fetch = async (url, init = {}) => {
    let body = null;
    if (typeof init.body === 'string') body = encoder.encode(init.body);
    else if (init.body instanceof Uint8Array) body = init.body;
    const call = { url: String(url), method: init.method, headers: new Headers(init.headers), body, signal: init.signal, init };
    calls.push(call);
    return respond(call);
  };
  const handler = createHandler({ fetch, log: (entry) => logs.push(entry) });

  /** Sends a request to the handler. `body` may be a string, bytes or an object (sent as JSON). */
  const send = async (path, { method = 'POST', headers = {}, body, token = TOKEN } = {}) => {
    const h = new Headers(headers);
    if (token !== null && !h.has('authorization')) h.set('authorization', `Bearer ${token}`);
    let payload = body;
    if (body !== undefined && typeof body === 'object' && !(body instanceof Uint8Array) && !(body instanceof ReadableStream)) {
      payload = JSON.stringify(body);
      if (!h.has('content-type')) h.set('content-type', 'application/json');
    }
    const init = { method, headers: h };
    if (payload !== undefined) {
      init.body = payload;
      if (payload instanceof ReadableStream) init.duplex = 'half';
    }
    return handler(new Request(ORIGIN + path, init), env);
  };
  return { send, calls, logs, env, handler };
}

export async function errorOf(response) {
  const body = await response.json();
  return body.error;
}

export function jsonOf(call) {
  return JSON.parse(decoder.decode(call.body));
}

/** A multipart body in OkHttp 5's exact wire format (verified against okhttp-jvm 5.5.0). */
export function okhttpForm(boundary, parts) {
  const chunks = [];
  for (const p of parts) {
    let head = `--${boundary}\r\nContent-Disposition: form-data; name="${p.name}"`;
    if (p.filename !== undefined) head += `; filename="${p.filename}"`;
    head += '\r\n';
    if (p.contentType) head += `Content-Type: ${p.contentType}\r\n`;
    if (p.extraHeaders) head += p.extraHeaders.map((h) => `${h}\r\n`).join('');
    head += '\r\n';
    chunks.push(encoder.encode(head), typeof p.value === 'string' ? encoder.encode(p.value) : p.value, encoder.encode('\r\n'));
  }
  chunks.push(encoder.encode(`--${boundary}--\r\n`));
  return concat(chunks);
}

export function concat(chunks) {
  const total = chunks.reduce((n, c) => n + c.length, 0);
  const out = new Uint8Array(total);
  let o = 0;
  for (const c of chunks) {
    out.set(c, o);
    o += c.length;
  }
  return out;
}

export const OKHTTP_FIXTURE = new Uint8Array(readFileSync(new URL('./fixtures/okhttp-transcription.multipart', import.meta.url)));
export const OKHTTP_FIXTURE_BOUNDARY = '9d1c2b7e-5f4a-4c33-8a61-0e2f7b9c4d10';

/** Every captured log line as one string, for "never logged" assertions. */
export const logText = (logs) => logs.map((l) => JSON.stringify(l)).join('\n');
