import { test } from 'node:test';
import assert from 'node:assert/strict';
import { baseEnv, encoder, errorOf, harness, okhttpForm } from './helpers.js';

const MB = 1024 * 1024;

test('declared Content-Length over the route limit is rejected with 413 before reading', async () => {
  const h = harness();
  let pulled = 0;
  const stream = new ReadableStream({ pull(c) { pulled++; c.enqueue(new Uint8Array(1024)); } });
  const res = await h.send('/v1/responses', {
    body: stream, headers: { 'content-type': 'application/json', 'content-length': String(15 * MB + 1) },
  });
  assert.equal(res.status, 413);
  assert.equal((await errorOf(res)).code, 'request_too_large');
  assert.ok(pulled <= 1, `read ${pulled} chunks of an oversized body`);
  assert.equal(h.calls.length, 0);
});

test('streamed bodies without Content-Length are cut off once they pass the limit', async () => {
  const h = harness({ env: baseEnv({ MAX_JSON_MB: '1' }) });
  let sent = 0;
  const stream = new ReadableStream({
    pull(c) {
      if (sent > 50 * MB) return c.close();
      sent += 256 * 1024;
      c.enqueue(new Uint8Array(256 * 1024).fill(0x20));
    },
  });
  const res = await h.send('/v1/responses', { body: stream, headers: { 'content-type': 'application/json' } });
  assert.equal(res.status, 413);
  assert.ok(sent < 3 * MB, `buffered ${sent} bytes`);
});

test('default per-route limits: JSON 15 MB, transcriptions 26 MB, image edits 20 MB', async () => {
  const h = harness();
  // A ~14.9 MB JSON body (e.g. a large data-URL photo) is accepted.
  const big = JSON.stringify({ model: 'gpt-5.6-terra', input: 'x'.repeat(15 * MB - 100) });
  assert.ok(encoder.encode(big).length < 15 * MB);
  assert.equal((await h.send('/v1/responses', { body: big, headers: { 'content-type': 'application/json' } })).status, 200);

  const audio = new Uint8Array(25 * MB + 512 * 1024); // 25.5 MB file + form overhead < 26 MB
  const form = okhttpForm('b', [{ name: 'model', value: 'gpt-transcribe' }, { name: 'file', filename: 'a.m4a', value: audio }]);
  assert.equal((await h.send('/v1/audio/transcriptions', { body: form, headers: { 'content-type': 'multipart/form-data; boundary=b' } })).status, 200);

  const tooBigImage = okhttpForm('b', [
    { name: 'model', value: 'gpt-image-2.5-flare' },
    { name: 'image[]', filename: 'ref.png', value: new Uint8Array(20 * MB) },
  ]);
  const res = await h.send('/v1/images/edits', { body: tooBigImage, headers: { 'content-type': 'multipart/form-data; boundary=b' } });
  assert.equal(res.status, 413);
  assert.equal(h.calls.length, 2);
});
