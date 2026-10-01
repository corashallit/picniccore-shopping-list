import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { createServer } from 'node:http';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { hashOf, okhttpForm, REAL_KEY } from './helpers.js';
import { hashToken, mintToken } from '../scripts/mint-token.mjs';
import { parseDevVars, serveNode } from '../scripts/serve.mjs';

const run = promisify(execFile);
const script = (name) => fileURLToPath(new URL(`../scripts/${name}`, import.meta.url));

test('mint-token: 256-bit pk_ tokens, SHA-256 hex hashes, usable registration lines', async () => {
  const token = mintToken();
  assert.match(token, /^pk_[A-Za-z0-9_-]{43}$/);
  assert.notEqual(mintToken(), token);
  assert.equal(hashToken(token), hashOf(token));

  const { stdout } = await run(process.execPath, [script('mint-token.mjs'), 'sams-phone', '--json']);
  const out = JSON.parse(stdout);
  assert.equal(out.name, 'sams-phone');
  assert.equal(out.hash, hashOf(out.token));
  assert.equal(out.entry, `sams-phone:${out.hash}`);
  assert.equal(JSON.parse(out.kv).name, 'sams-phone');

  const human = await run(process.execPath, [script('mint-token.mjs'), 'kitchen']);
  assert.match(human.stdout, /wrangler kv key put --binding TOKENS --remote [0-9a-f]{64}/);
  assert.match(human.stdout, /ACCESS_TOKEN_HASHES/);

  await assert.rejects(run(process.execPath, [script('mint-token.mjs'), 'bad name!']));
});

test('mint-token --hash reads the token from stdin', async () => {
  const child = execFile(process.execPath, [script('mint-token.mjs'), '--hash']);
  let stdout = '';
  child.stdout.on('data', (d) => { stdout += d; });
  child.stdin.end('pk_example\n');
  await new Promise((resolve) => child.on('close', resolve));
  assert.equal(stdout.trim(), hashOf('pk_example'));
});

test('parseDevVars reads the wrangler .dev.vars format', () => {
  const vars = parseDevVars('# comment\nOPENAI_API_KEY=sk-x\nMODEL_MAP={"a":"b"}\nQUOTED="v w"\n\nBAD LINE\n');
  assert.deepEqual(vars, { OPENAI_API_KEY: 'sk-x', MODEL_MAP: '{"a":"b"}', QUOTED: 'v w' });
});

test('Node adapter: real sockets end to end against a fake OpenAI (JSON, multipart, streaming)', async (t) => {
  // A fake OpenAI that records what it receives.
  const received = [];
  const upstream = createServer((req, res) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      received.push({ url: req.url, method: req.method, headers: req.headers, body: Buffer.concat(chunks) });
      if (req.url === '/v1/responses' && JSON.parse(Buffer.concat(chunks)).stream) {
        res.writeHead(200, { 'content-type': 'text/event-stream', 'x-request-id': 'req_stream' });
        res.write('data: {"type":"response.created"}\n\n');
        setTimeout(() => res.end('data: {"type":"response.completed"}\n\n'), 50);
        return;
      }
      res.writeHead(200, { 'content-type': 'application/json', 'x-request-id': 'req_fake', 'openai-organization': 'org-x' });
      res.end(JSON.stringify({ ok: true, path: req.url }));
    });
  });
  await new Promise((r) => upstream.listen(0, '127.0.0.1', r));
  t.after(() => { upstream.closeAllConnections(); upstream.close(); });

  const token = mintToken();
  const env = {
    OPENAI_API_KEY: REAL_KEY,
    OPENAI_BASE_URL: `http://127.0.0.1:${upstream.address().port}/v1`,
    ACCESS_TOKEN_HASHES: `adapter-test:${hashToken(token)}`,
  };
  const server = await serveNode({ port: 0, host: '127.0.0.1', env });
  t.after(() => { server.closeAllConnections(); server.close(); });
  const base = `http://127.0.0.1:${server.address().port}`;
  const auth = { authorization: `Bearer ${token}` };

  assert.equal((await fetch(`${base}/healthz`)).status, 200);

  let res = await fetch(`${base}/v1/responses`, {
    method: 'POST', headers: { ...auth, 'content-type': 'application/json' },
    body: JSON.stringify({ model: 'gpt-6-luna', input: 'hi' }),
  });
  assert.equal(res.status, 200);
  assert.equal(res.headers.get('x-request-id'), 'req_fake');
  assert.equal(res.headers.get('openai-organization'), null);
  assert.deepEqual(await res.json(), { ok: true, path: '/v1/responses' });
  assert.equal(received[0].headers.authorization, `Bearer ${REAL_KEY}`);
  assert.equal(JSON.parse(received[0].body).store, false);

  const form = okhttpForm('adapter-b', [
    { name: 'model', value: 'gpt-transcribe' },
    { name: 'file', filename: 'recording.m4a', contentType: 'audio/mp4', value: new Uint8Array([1, 2, 3, 13, 10]) },
  ]);
  res = await fetch(`${base}/v1/audio/transcriptions`, {
    method: 'POST', headers: { ...auth, 'content-type': 'multipart/form-data; boundary=adapter-b' }, body: form,
  });
  assert.equal(res.status, 200);
  assert.deepEqual(new Uint8Array(received[1].body), form);

  res = await fetch(`${base}/v1/responses`, {
    method: 'POST', headers: { ...auth, 'content-type': 'application/json' },
    body: JSON.stringify({ model: 'gpt-6-luna', input: 'hi', stream: true }),
  });
  assert.equal(res.headers.get('content-type'), 'text/event-stream');
  const text = await res.text();
  assert.match(text, /response\.created[\s\S]*response\.completed/);

  res = await fetch(`${base}/v1/responses`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}' });
  assert.equal(res.status, 401);
  assert.equal(received.length, 3);
});
