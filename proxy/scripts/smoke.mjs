#!/usr/bin/env node
// Live smoke test of a running proxy (deployed or local). Costs well under US$0.001 per run.
//
//   PICNIC_TOKEN=pk_... node scripts/smoke.mjs https://picnic.cora.earth/v1
//   PICNIC_TOKEN=pk_... node scripts/smoke.mjs http://127.0.0.1:8787/v1 --audio ../path/recording.m4a
//
// The token is read from the environment (never from argv, which lands in shell history and `ps`).
// Checks: GET /healthz, GET /models/gpt-5.6-terra (free), one tiny POST /responses on gpt-6-luna, and
// optionally one POST /audio/transcriptions. Prints statuses, served models and request ids; never the token.

import { readFile } from 'node:fs/promises';
import { basename } from 'node:path';

const args = process.argv.slice(2);
let base = 'https://picnic.cora.earth/v1';
let audioPath = null;
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--audio') audioPath = args[++i];
  else base = args[i];
}
base = base.replace(/\/+$/, '');
const token = process.env.PICNIC_TOKEN;
if (!token) {
  console.error('Set PICNIC_TOKEN to a device token (or an OpenAI key when testing api.openai.com directly).');
  process.exit(2);
}
const auth = { authorization: `Bearer ${token}` };
let failures = 0;

async function step(name, fn) {
  const started = Date.now();
  try {
    const { response, detail } = await fn();
    const ok = response.ok;
    if (!ok) failures++;
    const requestId = response.headers.get('x-request-id') ?? '-';
    console.log(`${ok ? 'ok  ' : 'FAIL'} ${name}: HTTP ${response.status} in ${Date.now() - started} ms, request ${requestId}${detail ? `, ${detail}` : ''}`);
  } catch (err) {
    failures++;
    console.log(`FAIL ${name}: ${err.name}: ${err.message}`);
  }
}

async function errorDetail(response) {
  const body = await response.json().catch(() => null);
  return body?.error ? `${body.error.code ?? body.error.type}: ${body.error.message}` : null;
}

await step('healthz', async () => {
  const response = await fetch(new URL('/healthz', base));
  return { response, detail: JSON.stringify(await response.json().catch(() => null)) };
});

await step('GET /models/gpt-5.6-terra', async () => {
  const response = await fetch(`${base}/models/gpt-5.6-terra`, { headers: auth });
  if (!response.ok) return { response, detail: await errorDetail(response) };
  const body = await response.json();
  return { response, detail: `id ${body.id}` };
});

await step('POST /responses (gpt-6-luna)', async () => {
  const response = await fetch(`${base}/responses`, {
    method: 'POST',
    headers: { ...auth, 'content-type': 'application/json' },
    body: JSON.stringify({
      model: 'gpt-6-luna',
      reasoning: { effort: 'none' },
      max_output_tokens: 16,
      store: false,
      input: 'Reply with exactly one word: picnic',
    }),
  });
  if (!response.ok) return { response, detail: await errorDetail(response) };
  const body = await response.json();
  const message = (body.output ?? []).filter((o) => o.type === 'message' && o.phase !== 'commentary').at(-1);
  const text = message?.content?.find((c) => c.type === 'output_text')?.text;
  return { response, detail: `served by ${body.model}, status ${body.status}, text ${JSON.stringify(text)}` };
});

if (audioPath) {
  await step('POST /audio/transcriptions (gpt-transcribe)', async () => {
    const form = new FormData();
    form.append('model', 'gpt-transcribe');
    form.append('response_format', 'json');
    form.append('languages[]', 'en');
    form.append('file', new Blob([await readFile(audioPath)], { type: 'audio/mp4' }), basename(audioPath));
    const response = await fetch(`${base}/audio/transcriptions`, { method: 'POST', headers: auth, body: form });
    if (!response.ok) return { response, detail: await errorDetail(response) };
    const body = await response.json();
    return { response, detail: `text ${JSON.stringify(body.text?.slice(0, 80))}` };
  });
}

process.exit(failures ? 1 : 0);
