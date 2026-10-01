import { test } from 'node:test';
import assert from 'node:assert/strict';
import { setTimeout as sleep } from 'node:timers/promises';
import {
  baseEnv, decoder, encoder, errorOf, harness, hashOf, jsonOf, REAL_KEY, TOKEN,
} from './helpers.js';

const ingestLike = {
  model: 'gpt-5.6-terra',
  store: true,
  reasoning: { effort: 'low' },
  max_output_tokens: 12000,
  service_tier: 'priority',
  tools: [{ type: 'web_search', search_context_size: 'low' }],
  input: [{ role: 'user', content: [{ type: 'input_text', text: 'ingredients for pancakes' }] }],
  safety_identifier: 'abc123',
};

test('forwards /v1/responses to upstream /responses with the real key and only safe headers', async () => {
  const h = harness();
  const res = await h.send('/v1/responses?model=gpt-6-astra&api-key=x', {
    body: ingestLike,
    headers: {
      accept: 'application/json',
      cookie: 'session=abc',
      'openai-organization': 'org-attacker',
      'openai-project': 'proj_attacker',
      'x-forwarded-for': '203.0.113.9',
      'user-agent': 'okhttp/5.5.0',
      'x-stainless-os': 'Android',
    },
  });
  assert.equal(res.status, 200);
  assert.equal(h.calls.length, 1);
  const call = h.calls[0];
  assert.equal(call.url, 'https://api.openai.com/v1/responses'); // query string dropped
  assert.equal(call.method, 'POST');
  assert.equal(call.init.redirect, 'manual');
  const sent = Object.fromEntries(call.headers);
  assert.deepEqual(Object.keys(sent).sort(), ['accept', 'authorization', 'content-type']);
  assert.equal(sent.authorization, `Bearer ${REAL_KEY}`);
  assert.equal(sent['content-type'], 'application/json');
  assert.ok(!JSON.stringify(sent).includes(TOKEN));
});

test('responses policy: store:false forced, service_tier stripped, tools and other fields preserved', async () => {
  const h = harness();
  await h.send('/v1/responses', { body: ingestLike });
  const sent = jsonOf(h.calls[0]);
  assert.equal(sent.store, false);
  assert.ok(!('service_tier' in sent));
  assert.equal(sent.max_output_tokens, 12000);
  assert.equal(sent.safety_identifier, 'abc123');
  assert.deepEqual(sent.tools, ingestLike.tools);
  assert.deepEqual(sent.input, ingestLike.input);
  assert.deepEqual(sent.reasoning, ingestLike.reasoning);

  // store:false is added when absent too.
  await h.send('/v1/responses', { body: { model: 'gpt-6-luna', input: 'x' } });
  assert.equal(jsonOf(h.calls[1]).store, false);
});

test('max_output_tokens is capped (default 16000) and filled in when missing or invalid', async () => {
  const h = harness();
  const cases = [[undefined, 16000], [50000, 16000], [16000, 16000], [100, 100], ['100000', 16000], [1.5, 16000], [null, 16000]];
  for (const [given, expected] of cases) {
    const body = { model: 'gpt-6-luna', input: 'x' };
    if (given !== undefined) body.max_output_tokens = given;
    await h.send('/v1/responses', { body });
    assert.equal(jsonOf(h.calls.at(-1)).max_output_tokens, expected, `given ${given}`);
  }
  const custom = harness({ env: baseEnv({ MAX_OUTPUT_TOKENS: '4000' }) });
  await custom.send('/v1/responses', { body: { model: 'gpt-6-luna', input: 'x', max_output_tokens: 12000 } });
  assert.equal(jsonOf(custom.calls[0]).max_output_tokens, 4000);
});

test('a per-device safety_identifier is added when the client sends none', async () => {
  const h = harness();
  await h.send('/v1/responses', { body: { model: 'gpt-6-luna', input: 'x' } });
  assert.equal(jsonOf(h.calls[0]).safety_identifier, `picnic-${hashOf(TOKEN).slice(0, 16)}`);
});

test('model allow-list: defaults are the four ModelDefaults ids; anything else is 403 model_not_allowed', async () => {
  const h = harness();
  for (const model of ['gpt-5.6-terra', 'gpt-6-luna']) {
    assert.equal((await h.send('/v1/responses', { body: { model, input: 'x' } })).status, 200);
  }
  for (const model of ['gpt-6-astra', 'gpt-5.6-terra-2026-09-01', 'GPT-6-LUNA', 'gpt-6.1-sol']) {
    const res = await h.send('/v1/responses', { body: { model, input: 'x' } });
    assert.equal(res.status, 403, model);
    const err = await errorOf(res);
    assert.equal(err.code, 'model_not_allowed');
    assert.equal(err.type, 'invalid_request_error');
    assert.equal(err.param, 'model');
  }
  assert.equal(h.calls.length, 2);
});

test('model field must be present, a valid id, and not duplicated under another case', async () => {
  const h = harness();
  const cases = [
    [{ input: 'x' }, 400, 'missing_required_parameter'],
    [{ model: 42, input: 'x' }, 400, 'invalid_value'],
    [{ model: '', input: 'x' }, 400, 'invalid_value'],
    [{ model: 'gpt-6-luna\n', input: 'x' }, 400, 'invalid_value'],
    [{ model: 'gpt-6-luna', Model: 'gpt-6-astra', input: 'x' }, 400, 'invalid_value'],
    [{ MODEL: 'gpt-6-luna', input: 'x' }, 400, 'invalid_value'],
  ];
  for (const [body, status, code] of cases) {
    const res = await h.send('/v1/responses', { body });
    assert.equal(res.status, status, JSON.stringify(body));
    assert.equal((await errorOf(res)).code, code);
  }
  assert.equal(h.calls.length, 0);
});

test('duplicate JSON keys cannot smuggle a second model: the body is re-serialized after checking', async () => {
  const h = harness();
  const raw = '{"model":"gpt-6-astra","input":"x","model":"gpt-6-luna"}';
  const res = await h.send('/v1/responses', { body: raw, headers: { 'content-type': 'application/json' } });
  assert.equal(res.status, 200);
  const sentText = decoder.decode(h.calls[0].body);
  assert.equal((sentText.match(/"model"/g) ?? []).length, 1);
  assert.equal(JSON.parse(sentText).model, 'gpt-6-luna');
});

test('bad JSON and wrong content types are rejected before upstream', async () => {
  const h = harness();
  const cases = [
    [{ body: 'not json', headers: { 'content-type': 'application/json' } }, 400, 'invalid_json'],
    [{ body: '[1,2]', headers: { 'content-type': 'application/json' } }, 400, 'invalid_json'],
    [{ body: 'null', headers: { 'content-type': 'application/json' } }, 400, 'invalid_json'],
    [{ body: new Uint8Array([0x7b, 0xff, 0x7d]), headers: { 'content-type': 'application/json' } }, 400, 'invalid_json'],
    [{ body: '{"model":"gpt-6-luna"}', headers: { 'content-type': 'text/plain' } }, 415, 'unsupported_content_type'],
    [{ body: '{"model":"gpt-6-luna"}', headers: {} }, 415, 'unsupported_content_type'],
    [{ body: 'x', headers: { 'content-type': 'multipart/form-data; boundary=b' } }, 415, 'unsupported_content_type'],
  ];
  for (const [opts, status, code] of cases) {
    const res = await h.send('/v1/responses', opts);
    assert.equal(res.status, status);
    assert.equal((await errorOf(res)).code, code);
  }
  // application/json with a charset parameter is fine.
  const ok = await h.send('/v1/responses', {
    body: '{"model":"gpt-6-luna","input":"x"}', headers: { 'content-type': 'application/json; charset=utf-8' },
  });
  assert.equal(ok.status, 200);
  assert.equal(h.calls.length, 1);
});

test('tool allow-list: web_search passes; hosted tools that bring their own model or cost do not', async () => {
  const h = harness();
  assert.equal((await h.send('/v1/responses', { body: { model: 'gpt-6-luna', input: 'x', tools: [] } })).status, 200);
  for (const tools of [
    [{ type: 'image_generation', model: 'gpt-image-2' }],
    [{ type: 'web_search' }, { type: 'code_interpreter', container: { type: 'auto' } }],
    [{ type: 'mcp', server_url: 'https://example.com' }],
    [{ type: 'file_search' }],
    [{ type: 'function', name: 'f', parameters: {} }],
    ['web_search'],
    { type: 'web_search' },
  ]) {
    const res = await h.send('/v1/responses', { body: { model: 'gpt-6-luna', input: 'x', tools } });
    assert.equal(res.status, 400, JSON.stringify(tools));
    const err = await errorOf(res);
    assert.ok(['tool_not_allowed', 'invalid_value'].includes(err.code));
    assert.equal(err.param, 'tools');
  }
  const custom = harness({ env: baseEnv({ ALLOWED_TOOLS: 'web_search,function' }) });
  const fn = await custom.send('/v1/responses', {
    body: { model: 'gpt-6-luna', input: 'x', tools: [{ type: 'function', name: 'f', parameters: {} }] },
  });
  assert.equal(fn.status, 200);
});

test('MODEL_MAP: applied before the allow-list, one step only, exact ids', async () => {
  const env = baseEnv({
    MODEL_MAP: JSON.stringify({ 'gpt-5.6-terra': 'gpt-6.2-terra', 'gpt-6.2-terra': 'gpt-7-terra', 'gpt-6-luna': 'gpt-6-astra' }),
    ALLOWED_MODELS: 'gpt-6.2-terra,gpt-6-luna,gpt-7-terra',
  });
  const h = harness({ env });

  // Mapped to an allowed upstream id: forwarded with the mapped model.
  let res = await h.send('/v1/responses', { body: { model: 'gpt-5.6-terra', input: 'x' } });
  assert.equal(res.status, 200);
  assert.equal(jsonOf(h.calls.at(-1)).model, 'gpt-6.2-terra'); // not chained to gpt-7-terra

  // The allow-list holds upstream ids: gpt-6-luna is listed, but it maps to a non-listed id.
  res = await h.send('/v1/responses', { body: { model: 'gpt-6-luna', input: 'x' } });
  assert.equal(res.status, 403);
  assert.match((await errorOf(res)).message, /gpt-6-luna.*mapped to.*gpt-6-astra/);

  // Unmapped ids pass through unchanged and are checked as-is; keys are case-sensitive.
  res = await h.send('/v1/responses', { body: { model: 'gpt-7-terra', input: 'x' } });
  assert.equal(res.status, 200);
  assert.equal(jsonOf(h.calls.at(-1)).model, 'gpt-7-terra');
  res = await h.send('/v1/responses', { body: { model: 'GPT-5.6-terra', input: 'x' } });
  assert.equal(res.status, 403);

  // The log records both ids; the response body is never rewritten.
  const mappedLog = h.logs.find((l) => l.event === 'proxy');
  assert.equal(mappedLog.model, 'gpt-5.6-terra');
  assert.equal(mappedLog.upstreamModel, 'gpt-6.2-terra');
});

test('GET /v1/models/{id}: mapped and allow-listed like a body model', async () => {
  const env = baseEnv({ MODEL_MAP: '{"gpt-5.6-terra":"gpt-6.2-terra"}', ALLOWED_MODELS: 'gpt-6.2-terra,gpt-6-luna' });
  const h = harness({ env });
  let res = await h.send('/v1/models/gpt-5.6-terra', { method: 'GET' });
  assert.equal(res.status, 200);
  assert.equal(h.calls[0].url, 'https://api.openai.com/v1/models/gpt-6.2-terra');
  assert.equal(h.calls[0].method, 'GET');
  assert.equal(h.calls[0].body, null);
  assert.equal(h.calls[0].headers.get('authorization'), `Bearer ${REAL_KEY}`);

  res = await h.send('/v1/models/gpt-6-astra', { method: 'GET' });
  assert.equal(res.status, 403);
  assert.equal((await errorOf(res)).code, 'model_not_allowed');
  for (const bad of ['gpt%2F..%2Ffiles', '%E0%A4%A', 'a%20b']) {
    res = await h.send(`/v1/models/${bad}`, { method: 'GET' });
    assert.equal(res.status, 400, bad);
  }
  res = await h.send('/v1/models/gpt-6-luna', { method: 'GET', token: null });
  assert.equal(res.status, 401);
  assert.equal(h.calls.length, 1);
});

test('upstream responses pass through unchanged: status, body bytes and safe headers', async () => {
  const errorBody = '{"error":{"message":"Unsupported value: \'reasoning.effort\'","type":"invalid_request_error","param":"reasoning.effort","code":"unsupported_value"}}';
  const h = harness({
    respond: () => new Response(errorBody, {
      status: 400,
      headers: {
        'content-type': 'application/json', 'x-request-id': 'req_abc', 'openai-organization': 'org-secret',
        'openai-project': 'proj_secret', 'set-cookie': 'a=b', 'x-ratelimit-remaining-tokens': '1999000',
        'openai-processing-ms': '12', 'retry-after': '3', server: 'cloudflare',
      },
    }),
  });
  const res = await h.send('/v1/responses', { body: { model: 'gpt-6-luna', input: 'x' } });
  assert.equal(res.status, 400);
  assert.equal(await res.text(), errorBody);
  assert.equal(res.headers.get('x-request-id'), 'req_abc');
  assert.equal(res.headers.get('x-ratelimit-remaining-tokens'), '1999000');
  assert.equal(res.headers.get('openai-processing-ms'), '12');
  assert.equal(res.headers.get('retry-after'), '3');
  for (const dropped of ['openai-organization', 'openai-project', 'set-cookie', 'server']) {
    assert.equal(res.headers.get(dropped), null, dropped);
  }
});

test('upstream 429 quota and 5xx errors are passed through for the client to classify', async () => {
  for (const [status, body] of [
    [429, '{"error":{"message":"quota","type":"insufficient_quota","param":null,"code":"project_spend_limit_exceeded"}}'],
    [503, '{"error":{"message":"overloaded","type":"service_unavailable_error","param":null,"code":"server_is_overloaded"}}'],
    [404, '{"error":{"message":"The model `x` does not exist","type":"invalid_request_error","param":null,"code":"model_not_found"}}'],
  ]) {
    const h = harness({ respond: () => new Response(body, { status, headers: { 'content-type': 'application/json' } }) });
    const res = await h.send('/v1/responses', { body: { model: 'gpt-6-luna', input: 'x' } });
    assert.equal(res.status, status);
    assert.equal(await res.text(), body);
  }
});

test('an upstream 401 (bad proxy key) becomes 502 upstream_auth_failed and hides the masked key', async () => {
  const h = harness({
    respond: () => new Response('{"error":{"message":"Incorrect API key provided: sk-proj-****0000","type":"invalid_request_error","param":null,"code":"invalid_api_key"}}', {
      status: 401, headers: { 'content-type': 'application/json' },
    }),
  });
  const res = await h.send('/v1/responses', { body: { model: 'gpt-6-luna', input: 'x' } });
  assert.equal(res.status, 502);
  const text = await res.text();
  assert.ok(!text.includes('sk-proj'));
  assert.equal(JSON.parse(text).error.code, 'upstream_auth_failed');
});

test('streaming: SSE chunks reach the client before the upstream stream finishes', async () => {
  let release;
  const gate = new Promise((r) => { release = r; });
  const h = harness({
    respond: () => new Response(new ReadableStream({
      async start(controller) {
        controller.enqueue(encoder.encode('event: response.created\ndata: {"type":"response.created"}\n\n'));
        await gate;
        controller.enqueue(encoder.encode('event: response.completed\ndata: {"type":"response.completed"}\n\n'));
        controller.close();
      },
    }), { status: 200, headers: { 'content-type': 'text/event-stream; charset=utf-8', 'x-request-id': 'req_s' } }),
  });
  const res = await h.send('/v1/responses', { body: { model: 'gpt-6-luna', input: 'x', stream: true } });
  assert.equal(res.status, 200);
  assert.equal(res.headers.get('content-type'), 'text/event-stream; charset=utf-8');
  assert.equal(jsonOf(h.calls[0]).stream, true);
  const reader = res.body.getReader();
  const first = await Promise.race([reader.read(), sleep(1000).then(() => 'timeout')]);
  assert.notEqual(first, 'timeout', 'first SSE chunk was buffered');
  assert.match(decoder.decode(first.value), /response\.created/);
  release();
  let rest = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    rest += decoder.decode(value);
  }
  assert.match(rest, /response\.completed/);
  assert.equal(h.logs.find((l) => l.event === 'proxy').stream, true);
});

test('network failure gives 502, a slow upstream gives 504 (timeout covers headers only)', async () => {
  const failing = harness({ respond: () => { throw new TypeError('fetch failed'); } });
  let res = await failing.send('/v1/responses', { body: { model: 'gpt-6-luna', input: 'x' } });
  assert.equal(res.status, 502);
  assert.equal((await errorOf(res)).code, 'upstream_unreachable');

  const slow = harness({
    env: baseEnv({ UPSTREAM_TIMEOUT_SECONDS: '0.05' }),
    respond: (call) => new Promise((_, reject) => {
      call.signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
    }),
  });
  res = await slow.send('/v1/responses', { body: { model: 'gpt-6-luna', input: 'x' } });
  assert.equal(res.status, 504);
  assert.equal((await errorOf(res)).code, 'upstream_timeout');
  assert.equal(slow.logs.at(-1).error, 'timeout');
});

test('OPENAI_BASE_URL redirects upstream calls (e.g. a regional or test endpoint)', async () => {
  const h = harness({ env: baseEnv({ OPENAI_BASE_URL: 'http://127.0.0.1:9/v1/' }) });
  await h.send('/v1/responses', { body: { model: 'gpt-6-luna', input: 'x' } });
  assert.equal(h.calls[0].url, 'http://127.0.0.1:9/v1/responses');
});
