import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  baseEnv, errorOf, fakeKv, harness, hashOf, logText, OTHER_TOKEN, REAL_KEY, TOKEN,
} from './helpers.js';
import { bearerToken, timingSafeEqual } from '../src/auth.js';
import { parseConfig } from '../src/config.js';

const responsesBody = { model: 'gpt-6-luna', input: 'hi' };

test('healthz answers without auth and reports config health', async () => {
  const ok = harness();
  const res = await ok.send('/healthz', { method: 'GET', token: null });
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { ok: true });

  const broken = harness({ env: baseEnv({ MODEL_MAP: '{not json' }) });
  const bad = await broken.send('/healthz', { method: 'GET', token: null });
  assert.equal(bad.status, 500);
  assert.deepEqual(await bad.json(), { ok: false });
  assert.equal(ok.calls.length + broken.calls.length, 0);
});

test('unknown routes and methods get 404 in OpenAI error shape, without reaching upstream', async () => {
  const h = harness();
  const cases = [
    ['POST', '/v1/chat/completions'], ['GET', '/v1/responses'], ['POST', '/v1/models/gpt-6-luna'],
    ['GET', '/v1/models'], ['POST', '/v1/files'], ['POST', '/v1/responses/'], ['POST', '/responses'],
    ['DELETE', '/v1/responses'], ['POST', '/v1/images/generations'], ['POST', '/v1/batches'],
    ['POST', '/v1/%72esponses'], ['OPTIONS', '/v1/responses'], ['POST', '/v1/decisions'],
  ];
  for (const [method, path] of cases) {
    const res = await h.send(path, { method, body: method === 'POST' ? responsesBody : undefined });
    assert.equal(res.status, 404, `${method} ${path}`);
    const err = await errorOf(res);
    assert.equal(err.type, 'invalid_request_error');
    assert.match(err.message, /Invalid URL/);
    assert.ok('param' in err && 'code' in err);
  }
  assert.equal(h.calls.length, 0);
});

test('missing, malformed or unknown device tokens get the contract 401 body', async () => {
  const h = harness();
  const attempts = [
    { token: null },
    { token: OTHER_TOKEN },
    { token: REAL_KEY },
    { token: null, headers: { authorization: `Basic ${TOKEN}` } },
    { token: null, headers: { authorization: 'Bearer' } },
    { token: null, headers: { authorization: `Bearer ${TOKEN} extra` } },
    { token: null, headers: { authorization: `Bearer ${'x'.repeat(300)}` } },
    { token: null, headers: { 'x-api-key': TOKEN } },
  ];
  for (const a of attempts) {
    const res = await h.send('/v1/responses', { body: responsesBody, ...a });
    assert.equal(res.status, 401);
    assert.deepEqual(await res.json(), {
      error: { message: 'Invalid device token', type: 'invalid_request_error', param: null, code: 'invalid_api_key' },
    });
  }
  assert.equal(h.calls.length, 0);
});

test('a valid token from ACCESS_TOKEN_HASHES is accepted; scheme is case-insensitive', async () => {
  const h = harness({
    env: baseEnv({ ACCESS_TOKEN_HASHES: `${hashOf(OTHER_TOKEN)}, phone:${hashOf(TOKEN).toUpperCase()}` }),
  });
  assert.equal((await h.send('/v1/responses', { body: responsesBody })).status, 200);
  assert.equal((await h.send('/v1/responses', { body: responsesBody, token: OTHER_TOKEN })).status, 200);
  assert.equal(
    (await h.send('/v1/responses', { body: responsesBody, token: null, headers: { authorization: `bearer ${TOKEN}` } })).status,
    200,
  );
  // The log identifies devices by name, or by hash prefix when unnamed.
  const tokens = h.logs.filter((l) => l.event === 'proxy').map((l) => l.token);
  assert.deepEqual(tokens, ['phone', hashOf(OTHER_TOKEN).slice(0, 8), 'phone']);
});

test('KV tokens: active records pass, disabled or malformed records fail closed', async () => {
  const kv = fakeKv({
    [hashOf(TOKEN)]: JSON.stringify({ name: 'kitchen-tablet', created: '2026-10-01', disabled: false }),
    [hashOf(OTHER_TOKEN)]: JSON.stringify({ name: 'old-phone', disabled: true }),
    [hashOf('pk_garbage')]: 'not json',
  });
  const h = harness({ env: { OPENAI_API_KEY: REAL_KEY, TOKENS: kv } });
  assert.equal((await h.send('/v1/responses', { body: responsesBody })).status, 200);
  assert.equal((await h.send('/v1/responses', { body: responsesBody, token: OTHER_TOKEN })).status, 401);
  assert.equal((await h.send('/v1/responses', { body: responsesBody, token: 'pk_garbage' })).status, 401);
  assert.equal((await h.send('/v1/responses', { body: responsesBody, token: 'pk_unknown' })).status, 401);
  assert.equal(h.logs.find((l) => l.event === 'proxy').token, 'kitchen-tablet');
  // KV is looked up by hash, never by the raw token.
  assert.ok(kv.reads.every((k) => /^[0-9a-f]{64}$/.test(k)));
  assert.ok(!kv.reads.includes(TOKEN));
});

test('misconfiguration fails closed with 500 proxy_misconfigured before anything else', async () => {
  const broken = [
    { ACCESS_TOKEN_HASHES: hashOf(TOKEN) }, // no OPENAI_API_KEY
    baseEnv({ ACCESS_TOKEN_HASHES: undefined }), // no device tokens at all
    baseEnv({ MODEL_MAP: '{"gpt-6-luna":' }),
    baseEnv({ MODEL_MAP: '["gpt-6-luna"]' }),
    baseEnv({ MODEL_MAP: '{"gpt-6-luna": 42}' }),
    baseEnv({ MODEL_MAP: '{"gpt-6-luna": ""}' }),
    baseEnv({ MODEL_MAP: '"gpt-6-luna"' }),
    baseEnv({ ACCESS_TOKEN_HASHES: 'phone:not-a-hash' }),
    baseEnv({ ALLOWED_MODELS: 'gpt-6-luna, bad model id' }),
    baseEnv({ OPENAI_BASE_URL: 'http://api.openai.com/v1' }),
    baseEnv({ MAX_OUTPUT_TOKENS: 'lots' }),
    baseEnv({ TOKENS: 'not a binding' }),
  ];
  for (const env of broken) {
    const h = harness({ env });
    const res = await h.send('/v1/responses', { body: responsesBody });
    assert.equal(res.status, 500, JSON.stringify(Object.keys(env)));
    assert.deepEqual(await res.json(), {
      error: { message: 'Proxy misconfigured', type: 'server_error', param: null, code: 'proxy_misconfigured' },
    });
    assert.equal(h.calls.length, 0);
    const configLog = h.logs.find((l) => l.event === 'config_error');
    assert.ok(configLog?.problems.length > 0);
    assert.ok(!logText(h.logs).includes(REAL_KEY));
  }
});

test('config accepts JSON-valued vars (wrangler.jsonc objects and arrays)', () => {
  const r = parseConfig(baseEnv({ MODEL_MAP: { 'gpt-6-luna': 'gpt-6.1-luna' }, ALLOWED_MODELS: ['gpt-6.1-luna'] }));
  assert.equal(r.ok, true);
  assert.equal(r.config.modelMap.get('gpt-6-luna'), 'gpt-6.1-luna');
  assert.deepEqual([...r.config.allowedModels], ['gpt-6.1-luna']);
  const empty = parseConfig(baseEnv({ MODEL_MAP: {} }));
  assert.equal(empty.ok, true);
  assert.equal(empty.config.modelMap.size, 0);
  const defaults = parseConfig(baseEnv());
  assert.deepEqual([...defaults.config.allowedModels], ['gpt-5.6-terra', 'gpt-6-luna', 'gpt-transcribe', 'gpt-image-2.5-flare']);
});

test('per-device rate limit: 429 rate_limit_error with Retry-After, keyed by token hash', async () => {
  const keys = [];
  let allow = true;
  const RATE_LIMITER = { async limit({ key }) { keys.push(key); return { success: allow }; } };
  const h = harness({ env: baseEnv({ RATE_LIMITER, RATE_LIMIT_RETRY_AFTER: '10' }) });
  assert.equal((await h.send('/v1/responses', { body: responsesBody })).status, 200);
  allow = false;
  const res = await h.send('/v1/responses', { body: responsesBody });
  assert.equal(res.status, 429);
  assert.equal(res.headers.get('retry-after'), '10');
  const err = await errorOf(res);
  assert.equal(err.type, 'rate_limit_error');
  assert.equal(err.code, 'rate_limit_exceeded');
  assert.equal(h.calls.length, 1);
  assert.deepEqual(keys, [`token:${hashOf(TOKEN)}`, `token:${hashOf(TOKEN)}`]);
  // Unauthenticated requests never consume a device's budget.
  await h.send('/v1/responses', { body: responsesBody, token: OTHER_TOKEN });
  assert.equal(keys.length, 2);
});

test('a failing rate limiter binding fails open (the OpenAI spend limit is the hard cap)', async () => {
  const RATE_LIMITER = { async limit() { throw new Error('binding unavailable'); } };
  const h = harness({ env: baseEnv({ RATE_LIMITER }) });
  assert.equal((await h.send('/v1/responses', { body: responsesBody })).status, 200);
  assert.ok(h.logs.some((l) => l.event === 'rate_limiter_error'));
});

test('helpers: constant-time compare and bearer parsing', () => {
  assert.equal(timingSafeEqual(new Uint8Array([1, 2, 3]), new Uint8Array([1, 2, 3])), true);
  assert.equal(timingSafeEqual(new Uint8Array([1, 2, 3]), new Uint8Array([1, 2, 4])), false);
  assert.equal(timingSafeEqual(new Uint8Array([1, 2]), new Uint8Array([1, 2, 3])), false);
  const req = (v) => new Request('https://x/', { headers: v === undefined ? {} : { authorization: v } });
  assert.equal(bearerToken(req(`Bearer ${TOKEN}`)), TOKEN);
  assert.equal(bearerToken(req(`BEARER   ${TOKEN}`)), TOKEN);
  assert.equal(bearerToken(req(undefined)), null);
  assert.equal(bearerToken(req('Bearer a b')), null);
  assert.equal(bearerToken(req('Token abc')), null);
});

test('logs never contain the device token, the real key or body content', async () => {
  const h = harness({
    respond: () => new Response('{"secret_output":"pancake recipe"}', { status: 200, headers: { 'content-type': 'application/json' } }),
  });
  await h.send('/v1/responses', { body: { model: 'gpt-6-luna', input: 'SECRET-INPUT-TEXT' } });
  await h.send('/v1/responses', { body: { model: 'gpt-6-astra', input: 'SECRET-INPUT-TEXT' } });
  await h.send('/v1/responses', { body: '{"model": SECRET-INPUT-TEXT', headers: { 'content-type': 'application/json' } });
  await h.send('/v1/responses', { body: responsesBody, token: OTHER_TOKEN });
  const text = logText(h.logs);
  for (const secret of [TOKEN, OTHER_TOKEN, REAL_KEY, 'SECRET-INPUT-TEXT', 'pancake recipe', hashOf(TOKEN)]) {
    assert.ok(!text.includes(secret), `log leaked ${secret.slice(0, 12)}…`);
  }
  assert.deepEqual(h.logs.map((l) => l.event), ['proxy', 'reject', 'reject', 'reject']);
  assert.deepEqual(h.logs.map((l) => l.status), [200, 403, 400, 401]);
});
