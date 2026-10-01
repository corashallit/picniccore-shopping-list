// scripts/server.mjs (the self-hosting entry point) over real sockets, against a fake OpenAI.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFile, spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { connect } from 'node:net';
import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { hashOf, REAL_KEY, TOKEN } from './helpers.js';
import {
  createRateLimiter, DEFAULT_SETTINGS, EXIT_CONFIG, parseServerSettings, startServer,
} from '../scripts/server.mjs';

const run = promisify(execFile);
const script = (name) => fileURLToPath(new URL(`../scripts/${name}`, import.meta.url));
const auth = { authorization: `Bearer ${TOKEN}` };
const json = { ...auth, 'content-type': 'application/json' };
const SECRET_BODY = 'oat milk and a secret birthday cake';

/** A fake OpenAI. `behave(req, res, body)` answers; the default is a JSON 200 echoing the path. */
async function fakeUpstream(t, behave) {
  const received = [];
  const server = createServer((req, res) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      const entry = { url: req.url, headers: req.headers, body: Buffer.concat(chunks), closed: false };
      received.push(entry);
      res.on('close', () => { entry.closed = true; });
      if (behave) return behave(req, res, entry);
      res.writeHead(200, { 'content-type': 'application/json', 'x-request-id': 'req_fake' });
      res.end(JSON.stringify({ ok: true, path: req.url }));
    });
  });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  t.after(() => { server.closeAllConnections(); server.close(); });
  return { received, base: `http://127.0.0.1:${server.address().port}/v1` };
}

function proxyEnv(upstreamBase, extra = {}) {
  return {
    OPENAI_API_KEY: REAL_KEY,
    OPENAI_BASE_URL: upstreamBase,
    ACCESS_TOKEN_HASHES: `server-test:${hashOf(TOKEN)}`,
    PORT: '0',
    ...extra,
  };
}

async function start(t, env) {
  const logs = [];
  const started = await startServer({ env, log: (entry) => logs.push(entry) });
  assert.equal(started.ok, true, JSON.stringify(started.problems));
  t.after(() => { started.server.closeAllConnections(); started.server.close(); });
  return { ...started, logs };
}

/** Sends raw bytes and returns everything the server answers before closing. */
function rawRequest(url, text) {
  const { hostname, port } = new URL(url);
  return new Promise((resolve, reject) => {
    const socket = connect(Number(port), hostname, () => socket.end(text));
    let out = '';
    socket.on('data', (d) => { out += d; });
    socket.on('end', () => resolve(out));
    socket.on('error', reject);
  });
}

const until = async (predicate, ms = 2000) => {
  const deadline = Date.now() + ms;
  while (!predicate()) {
    if (Date.now() > deadline) throw new Error('timed out waiting');
    await new Promise((r) => setTimeout(r, 5));
  }
};

test('parseServerSettings: defaults, and range checks that name the setting but never echo values', () => {
  assert.deepEqual(parseServerSettings({}), { problems: [], settings: { ...DEFAULT_SETTINGS } });
  assert.equal(DEFAULT_SETTINGS.host, '127.0.0.1');
  assert.deepEqual(parseServerSettings({ HOST: '0.0.0.0', PORT: '9000', RATE_LIMIT_PER_MINUTE: '0' }).settings, {
    ...DEFAULT_SETTINGS, host: '0.0.0.0', port: 9000, rateLimitPerMinute: 0,
  });
  const bad = parseServerSettings({
    PORT: '70000', IDLE_TIMEOUT_SECONDS: 'soon', SHUTDOWN_GRACE_SECONDS: '-1', RATE_LIMIT_PER_MINUTE: '1.5', HOST: 'a b',
  });
  assert.equal(bad.problems.length, 5);
  assert.ok(bad.problems.every((p) => !/70000|soon|1\.5/.test(p)));
});

test('createRateLimiter: fixed window per key, then resets', async () => {
  let now = 1_000;
  const limiter = createRateLimiter({ limit: 2, periodMs: 60_000, now: () => now });
  const ok = async (key) => (await limiter.limit({ key })).success;
  assert.equal(await ok('a'), true);
  assert.equal(await ok('a'), true);
  assert.equal(await ok('a'), false);
  assert.equal(await ok('b'), true);
  now += 60_000;
  assert.equal(await ok('a'), true);
});

test('startServer refuses a broken configuration and listens on nothing', async () => {
  const missingKey = await startServer({ env: { ACCESS_TOKEN_HASHES: `x:${hashOf(TOKEN)}`, PORT: '0' }, log: () => {} });
  assert.equal(missingKey.ok, false);
  assert.ok(missingKey.problems.includes('OPENAI_API_KEY is not set'));

  const noTokens = await startServer({ env: { OPENAI_API_KEY: REAL_KEY, PORT: '0' }, log: () => {} });
  assert.equal(noTokens.ok, false);
  assert.match(noTokens.problems.join(), /no device tokens configured/);
  assert.ok(!noTokens.problems.join().includes(REAL_KEY));

  const timeouts = await startServer({
    env: proxyEnv('https://api.openai.com/v1', { IDLE_TIMEOUT_SECONDS: '120' }), log: () => {},
  });
  assert.deepEqual(timeouts, { ok: false, problems: ['IDLE_TIMEOUT_SECONDS must be longer than UPSTREAM_TIMEOUT_SECONDS'] });
});

test('serves the proxy: healthz, 401, JSON forwarding, header allow-list; logs hold no token, key or body', async (t) => {
  const upstream = await fakeUpstream(t);
  const { url, logs } = await start(t, proxyEnv(upstream.base, { HOST: '127.0.0.1' }));

  let res = await fetch(`${url}/healthz`);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { ok: true });
  res = await fetch(`${url}/healthz`, { method: 'HEAD' });
  assert.equal(res.status, 200);

  res = await fetch(`${url}/v1/models/gpt-5.6-terra`);
  assert.equal(res.status, 401);
  assert.equal((await res.json()).error.code, 'invalid_api_key');

  res = await fetch(`${url}/v1/responses`, {
    method: 'POST', headers: json, body: JSON.stringify({ model: 'gpt-6-luna', input: SECRET_BODY }),
  });
  assert.equal(res.status, 200);
  assert.equal(res.headers.get('x-request-id'), 'req_fake');
  assert.deepEqual(await res.json(), { ok: true, path: '/v1/responses' });
  assert.equal(upstream.received[0].headers.authorization, `Bearer ${REAL_KEY}`);
  assert.equal(JSON.parse(upstream.received[0].body).store, false);

  res = await fetch(`${url}/v1/nope`, { headers: auth });
  assert.equal(res.status, 404);
  // fetch refuses to send TRACE, and so does the WHATWG Request the Worker takes: answered by the server.
  const raw = await rawRequest(url, `TRACE /v1/models/gpt-5.6-terra HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer ${TOKEN}\r\nConnection: close\r\n\r\n`);
  assert.match(raw, /^HTTP\/1\.1 404 /);
  assert.match(raw, /"type":"invalid_request_error"/);
  assert.equal(upstream.received.length, 1);

  const text = JSON.stringify(logs);
  assert.ok(!text.includes(TOKEN) && !text.includes(REAL_KEY) && !text.includes(SECRET_BODY) && !text.includes(hashOf(TOKEN)));
  assert.equal(logs[0].event, 'listening');
  assert.ok(logs.some((l) => l.event === 'proxy' && l.token === 'server-test' && l.status === 200));
});

test('streams SSE events through as they arrive', async (t) => {
  let release;
  const released = new Promise((r) => { release = r; });
  const upstream = await fakeUpstream(t, async (req, res) => {
    res.writeHead(200, { 'content-type': 'text/event-stream', 'x-request-id': 'req_stream' });
    res.write('event: response.created\ndata: {"type":"response.created"}\n\n');
    await released; // nothing more until the test has seen the first event
    res.end('event: response.completed\ndata: {"type":"response.completed"}\n\n');
  });
  const { url } = await start(t, proxyEnv(upstream.base));

  const res = await fetch(`${url}/v1/responses`, {
    method: 'POST', headers: { ...json, accept: 'text/event-stream' },
    body: JSON.stringify({ model: 'gpt-6-luna', input: 'hi', stream: true }),
  });
  assert.equal(res.status, 200);
  assert.equal(res.headers.get('content-type'), 'text/event-stream');
  const reader = res.body.getReader();
  const first = new TextDecoder().decode((await reader.read()).value);
  assert.match(first, /response\.created/);
  assert.doesNotMatch(first, /response\.completed/);
  release();
  let rest = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    rest += new TextDecoder().decode(value);
  }
  assert.match(rest, /response\.completed/);
});

test('per-device rate limit (RATE_LIMIT_PER_MINUTE) answers 429 with Retry-After', async (t) => {
  const upstream = await fakeUpstream(t);
  const { url, logs } = await start(t, proxyEnv(upstream.base, { RATE_LIMIT_PER_MINUTE: '2' }));
  const statuses = [];
  for (let i = 0; i < 3; i++) {
    const res = await fetch(`${url}/v1/models/gpt-6-luna`, { headers: auth });
    statuses.push(res.status);
    if (res.status === 429) {
      assert.equal(res.headers.get('retry-after'), '60');
      assert.equal((await res.json()).error.code, 'rate_limit_exceeded');
    } else await res.body.cancel();
  }
  assert.deepEqual(statuses, [200, 200, 429]);
  assert.equal(upstream.received.length, 2);
  assert.ok(logs.some((l) => l.code === 'rate_limit_exceeded' && l.token === 'server-test'));

  const off = await start(t, proxyEnv(upstream.base, { RATE_LIMIT_PER_MINUTE: '0' }));
  for (let i = 0; i < 4; i++) {
    const res = await fetch(`${off.url}/v1/models/gpt-6-luna`, { headers: auth });
    assert.equal(res.status, 200);
    await res.body.cancel();
  }
});

test('a device that disconnects cancels the upstream call', async (t) => {
  const upstream = await fakeUpstream(t, () => {}); // never answers
  const { url } = await start(t, proxyEnv(upstream.base));
  const abort = new AbortController();
  const pending = fetch(`${url}/v1/responses`, {
    method: 'POST', headers: json, body: JSON.stringify({ model: 'gpt-6-luna', input: 'slow' }), signal: abort.signal,
  }).catch((err) => err);
  await until(() => upstream.received.length === 1);
  abort.abort();
  assert.equal((await pending).name, 'AbortError');
  await until(() => upstream.received[0].closed);
});

test('shutdown drains: refuses new connections, finishes in-flight requests, then stops', async (t) => {
  let release;
  const released = new Promise((r) => { release = r; });
  const upstream = await fakeUpstream(t, async (req, res) => {
    await released;
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end('{"slow":true}');
  });
  const { url, shutdown, stopped, logs } = await start(t, proxyEnv(upstream.base, { SHUTDOWN_GRACE_SECONDS: '10' }));

  const inflight = fetch(`${url}/v1/responses`, {
    method: 'POST', headers: json, body: JSON.stringify({ model: 'gpt-6-luna', input: 'slow' }),
  });
  await until(() => upstream.received.length === 1);
  const done = shutdown('SIGTERM');
  assert.equal((await fetch(`${url}/healthz`).catch((err) => err)).name, 'TypeError'); // connection refused
  release();
  const res = await inflight;
  assert.equal(res.status, 200);
  assert.equal(res.headers.get('connection'), 'close');
  assert.deepEqual(await res.json(), { slow: true });
  await done;
  await stopped;
  const events = logs.map((l) => l.event);
  assert.ok(events.indexOf('shutdown') < events.indexOf('stopped'));
  assert.equal(logs.find((l) => l.event === 'shutdown').inflight, 1);
  assert.ok(!events.includes('shutdown_timeout'));
});

test('shutdown gives up on in-flight requests after SHUTDOWN_GRACE_SECONDS and cancels them upstream', async (t) => {
  const upstream = await fakeUpstream(t, () => {}); // never answers
  const { url, shutdown, logs } = await start(t, proxyEnv(upstream.base, { SHUTDOWN_GRACE_SECONDS: '0.2' }));
  const pending = fetch(`${url}/v1/responses`, {
    method: 'POST', headers: json, body: JSON.stringify({ model: 'gpt-6-luna', input: 'stuck' }),
  }).catch((err) => err);
  await until(() => upstream.received.length === 1);
  const started = Date.now();
  await shutdown('SIGTERM');
  assert.ok(Date.now() - started >= 150);
  assert.equal((await pending).name, 'TypeError'); // socket closed under it
  assert.ok(logs.some((l) => l.event === 'shutdown_timeout'));
  await until(() => upstream.received[0].closed);
});

test('as a process: healthcheck script, SIGTERM exits 0; a broken config exits 78 without echoing values', async (t) => {
  const upstream = await fakeUpstream(t);
  const env = { PATH: process.env.PATH, ...proxyEnv(upstream.base) };
  const child = spawn(process.execPath, [script('server.mjs')], { env, stdio: ['ignore', 'pipe', 'pipe'] });
  t.after(() => child.kill('SIGKILL'));
  const lines = [];
  const exited = new Promise((resolve) => child.on('exit', (code, signal) => resolve({ code, signal })));
  const rl = createInterface({ input: child.stdout });
  const outputDone = new Promise((r) => rl.once('close', r));
  const listening = new Promise((resolve) => {
    rl.on('line', (line) => {
      lines.push(line);
      const entry = JSON.parse(line);
      if (entry.event === 'listening') resolve(entry);
    });
  });
  const { url } = await listening;
  const port = new URL(url).port;

  const check = await run(process.execPath, [script('healthcheck.mjs')], { env: { PATH: process.env.PATH, PORT: port } });
  assert.equal(check.stderr, '');
  const unauthorized = await fetch(`${url}/models/gpt-5.6-terra`);
  assert.equal(unauthorized.status, 401);

  child.kill('SIGTERM');
  assert.deepEqual(await exited, { code: 0, signal: null });
  await outputDone;
  const events = lines.map((l) => JSON.parse(l).event);
  assert.deepEqual(events.filter((e) => ['listening', 'shutdown', 'stopped'].includes(e)), ['listening', 'shutdown', 'stopped']);
  assert.ok(!lines.join('\n').includes(REAL_KEY));

  await assert.rejects(
    run(process.execPath, [script('healthcheck.mjs')], { env: { PATH: process.env.PATH, PORT: port } }),
    (err) => err.code === 1,
  );

  const broken = await run(process.execPath, [script('server.mjs')], {
    env: { PATH: process.env.PATH, OPENAI_API_KEY: REAL_KEY, ACCESS_TOKEN_HASHES: 'phone:not-a-hash', MODEL_MAP: '{oops', PORT: '0' },
  }).then(() => null, (err) => err);
  assert.equal(broken.code, EXIT_CONFIG);
  const problem = JSON.parse(broken.stdout.trim());
  assert.equal(problem.event, 'config_error');
  assert.ok(problem.problems.some((p) => p.startsWith('ACCESS_TOKEN_HASHES')));
  assert.ok(problem.problems.includes('MODEL_MAP is not valid JSON'));
  assert.ok(!broken.stdout.includes(REAL_KEY) && !broken.stdout.includes('not-a-hash') && !broken.stdout.includes('{oops'));
});
