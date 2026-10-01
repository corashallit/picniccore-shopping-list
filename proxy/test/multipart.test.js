import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  baseEnv, concat, decoder, encoder, errorOf, harness, jsonOf, OKHTTP_FIXTURE, OKHTTP_FIXTURE_BOUNDARY, okhttpForm, REAL_KEY,
} from './helpers.js';
import { MultipartError, multipartBoundary, parseMultipart, serializeMultipart } from '../src/multipart.js';

const B = 'okhttp-boundary-1234';
const ct = (b = B) => `multipart/form-data; boundary=${b}`;
const audio = new Uint8Array([0, 1, 2, 0x0d, 0x0a, 0x2d, 0x2d, 0xff, 0x0d, 0x0a]);

function transcriptionParts(model = 'gpt-transcribe', extra = []) {
  return [
    { name: 'model', value: model },
    { name: 'response_format', value: 'json' },
    { name: 'languages[]', value: 'en' },
    { name: 'keywords[]', value: "za'atar" },
    ...extra,
    { name: 'file', filename: 'recording.m4a', contentType: 'audio/mp4', value: audio },
  ];
}

const sendForm = (h, path, body, contentType = ct()) =>
  h.send(path, { body, headers: { 'content-type': contentType } });

test('the real OkHttp 5 body passes through byte-identical when the model is not remapped', async () => {
  const h = harness();
  const res = await sendForm(h, '/v1/audio/transcriptions', OKHTTP_FIXTURE, ct(OKHTTP_FIXTURE_BOUNDARY));
  assert.equal(res.status, 200);
  const call = h.calls[0];
  assert.equal(call.url, 'https://api.openai.com/v1/audio/transcriptions');
  assert.equal(call.headers.get('authorization'), `Bearer ${REAL_KEY}`);
  assert.equal(call.headers.get('content-type'), ct(OKHTTP_FIXTURE_BOUNDARY));
  assert.deepEqual(call.body, OKHTTP_FIXTURE);
});

test('the fixture parses into 7 parts, and the fake part inside the audio bytes stays inside it', () => {
  const parts = parseMultipart(OKHTTP_FIXTURE, OKHTTP_FIXTURE_BOUNDARY);
  assert.deepEqual(parts.map((p) => p.name), ['model', 'response_format', 'languages[]', 'prompt', 'keywords[]', 'keywords[]', 'file']);
  assert.equal(parts.filter((p) => p.name === 'model').length, 1);
  assert.equal(decoder.decode(parts[0].body), 'gpt-transcribe');
  const file = parts.at(-1);
  assert.equal(file.filename, 'recording.m4a');
  assert.match(new TextDecoder('latin1').decode(file.body), /gpt-6-astra/);
  assert.deepEqual(serializeMultipart(parts, OKHTTP_FIXTURE_BOUNDARY), OKHTTP_FIXTURE);
});

test('MODEL_MAP rewrites only the model field; every other part stays byte-identical', async () => {
  const env = baseEnv({ MODEL_MAP: '{"gpt-transcribe":"gpt-transcribe-2"}', ALLOWED_MODELS: 'gpt-transcribe-2' });
  const h = harness({ env });
  const res = await sendForm(h, '/v1/audio/transcriptions', OKHTTP_FIXTURE, ct(OKHTTP_FIXTURE_BOUNDARY));
  assert.equal(res.status, 200);
  const sent = h.calls[0].body;
  const before = parseMultipart(OKHTTP_FIXTURE, OKHTTP_FIXTURE_BOUNDARY);
  const after = parseMultipart(sent, OKHTTP_FIXTURE_BOUNDARY);
  assert.equal(after.length, before.length);
  assert.equal(decoder.decode(after[0].body), 'gpt-transcribe-2');
  for (let i = 1; i < before.length; i++) {
    assert.deepEqual(after[i].headerBytes, before[i].headerBytes);
    assert.deepEqual(after[i].body, before[i].body);
  }
  assert.equal(after.at(-1).filename, 'recording.m4a');
  // The only difference in the whole body is the model value.
  const expected = decoder.decode(OKHTTP_FIXTURE).replace('\r\n\r\ngpt-transcribe\r\n', '\r\n\r\ngpt-transcribe-2\r\n');
  assert.equal(decoder.decode(sent), expected);
});

test('multipart model allow-list: disallowed, missing, duplicate, case-variant or file-typed model fields', async () => {
  const h = harness();
  const cases = [
    [transcriptionParts('gpt-4o-transcribe'), 403, 'model_not_allowed'],
    [transcriptionParts().slice(1), 400, 'missing_required_parameter'],
    [transcriptionParts('gpt-transcribe', [{ name: 'model', value: 'gpt-4o-transcribe' }]), 400, 'invalid_value'],
    [transcriptionParts('gpt-transcribe', [{ name: 'Model', value: 'whisper-1' }]), 400, 'invalid_value'],
    [[{ name: 'model', filename: 'm.txt', value: 'gpt-transcribe' }, ...transcriptionParts().slice(1)], 400, 'invalid_value'],
    [transcriptionParts('gpt transcribe'), 400, 'invalid_value'],
  ];
  for (const [parts, status, code] of cases) {
    const res = await sendForm(h, '/v1/audio/transcriptions', okhttpForm(B, parts));
    assert.equal(res.status, status, JSON.stringify(parts.map((p) => [p.name, typeof p.value === 'string' ? p.value : 'bytes'])));
    assert.equal((await errorOf(res)).code, code);
  }
  assert.equal(h.calls.length, 0);
});

test('malformed or ambiguous multipart bodies are rejected with 400 invalid_multipart', async () => {
  const h = harness();
  const good = decoder.decode(okhttpForm(B, transcriptionParts().slice(0, 3)));
  const bodies = {
    'bare LF line endings': good.replaceAll('\r\n', '\n'),
    'missing closing boundary': good.replace(`--${B}--\r\n`, ''),
    'no boundary in body': 'model=gpt-transcribe',
    'part without headers': `--${B}\r\n\r\ngpt-transcribe\r\n--${B}--\r\n`,
    'folded header': `--${B}\r\nContent-Disposition: form-data;\r\n name="model"\r\n\r\ngpt-transcribe\r\n--${B}--\r\n`,
    'duplicate name parameter': `--${B}\r\nContent-Disposition: form-data; name="file"; name="model"\r\n\r\ngpt-transcribe\r\n--${B}--\r\n`,
    'RFC 5987 name': `--${B}\r\nContent-Disposition: form-data; name*=UTF-8''model\r\n\r\ngpt-transcribe\r\n--${B}--\r\n`,
    'escaped quote': `--${B}\r\nContent-Disposition: form-data; name="mo\\"del"\r\n\r\nx\r\n--${B}--\r\n`,
    'duplicate Content-Disposition': `--${B}\r\nContent-Disposition: form-data; name="model"\r\nContent-Disposition: form-data; name="x"\r\n\r\ngpt-transcribe\r\n--${B}--\r\n`,
    'not form-data': `--${B}\r\nContent-Disposition: attachment; name="model"\r\n\r\ngpt-transcribe\r\n--${B}--\r\n`,
    'wrong part Content-Length': `--${B}\r\nContent-Disposition: form-data; name="model"\r\nContent-Length: 3\r\n\r\ngpt-transcribe\r\n--${B}--\r\n`,
    'transport padding after boundary': `--${B}  \r\nContent-Disposition: form-data; name="model"\r\n\r\ngpt-transcribe\r\n--${B}--\r\n`,
  };
  for (const [what, body] of Object.entries(bodies)) {
    const res = await sendForm(h, '/v1/audio/transcriptions', body);
    assert.equal(res.status, 400, what);
    assert.equal((await errorOf(res)).code, 'invalid_multipart', what);
  }
  for (const badType of ['multipart/form-data', 'multipart/form-data; boundary=', 'multipart/form-data; boundary=a; boundary=b']) {
    const res = await sendForm(h, '/v1/audio/transcriptions', good, badType);
    assert.equal(res.status, 400, badType);
  }
  for (const wrongType of ['application/json', 'application/x-www-form-urlencoded', 'multipart/mixed; boundary=x']) {
    const res = await sendForm(h, '/v1/audio/transcriptions', good, wrongType);
    assert.equal(res.status, 415, wrongType);
  }
  assert.equal(h.calls.length, 0);
});

test('a correct per-part Content-Length is accepted; preamble and epilogue are dropped', async () => {
  const h = harness();
  const body = `preamble text\r\n--${B}\r\nContent-Disposition: form-data; name="model"\r\nContent-Length: 14\r\n\r\ngpt-transcribe\r\n`
    + `--${B}\r\nContent-Disposition: form-data; name="file"; filename="a.m4a"\r\n\r\nabc\r\n--${B}--\r\nepilogue`;
  const res = await sendForm(h, '/v1/audio/transcriptions', body);
  assert.equal(res.status, 200);
  const sent = decoder.decode(h.calls[0].body);
  assert.ok(sent.startsWith(`--${B}\r\n`));
  assert.ok(sent.endsWith(`--${B}--\r\n`));
  assert.ok(!sent.includes('preamble') && !sent.includes('epilogue'));
  assert.ok(sent.includes('Content-Length: 14\r\n'));
});

test('quoted boundaries and browser/undici FormData bodies work', async () => {
  const h = harness();
  const quoted = 'weird boundary:(1)';
  const body = okhttpForm(quoted, transcriptionParts());
  let res = await sendForm(h, '/v1/audio/transcriptions', body, `multipart/form-data; boundary="${quoted}"`);
  assert.equal(res.status, 200);
  assert.equal(h.calls[0].headers.get('content-type'), `multipart/form-data; boundary="${quoted}"`);

  const form = new FormData();
  form.append('model', 'gpt-transcribe');
  form.append('file', new Blob([audio], { type: 'audio/mp4' }), 'recording.m4a');
  const encoded = new Request('https://x/', { method: 'POST', body: form });
  res = await h.send('/v1/audio/transcriptions', {
    body: new Uint8Array(await encoded.arrayBuffer()), headers: { 'content-type': encoded.headers.get('content-type') },
  });
  assert.equal(res.status, 200);
  const parts = parseMultipart(h.calls[1].body, multipartBoundary(h.calls[1].headers.get('content-type')));
  assert.equal(parts.at(-1).filename, 'recording.m4a');
  assert.deepEqual(parts.at(-1).body, audio);
});

test('images/edits: multipart (core form) and JSON both work; model mapped and n capped', async () => {
  const env = baseEnv({ MODEL_MAP: '{"gpt-image-2.5-flare":"gpt-image-3-flare"}', ALLOWED_MODELS: 'gpt-image-3-flare' });
  const h = harness({ env });
  const png = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3]);
  const imageParts = (n = '1') => [
    { name: 'model', value: 'gpt-image-2.5-flare' },
    { name: 'prompt', value: 'Draw ONE new sticker icon of gochujang' },
    { name: 'image[]', filename: '_style_reference.png', contentType: 'image/png', value: png },
    { name: 'background', value: 'transparent' },
    { name: 'output_format', value: 'webp' },
    { name: 'quality', value: 'low' },
    { name: 'size', value: '1024x1024' },
    { name: 'n', value: n },
  ];
  let res = await sendForm(h, '/v1/images/edits', okhttpForm(B, imageParts()));
  assert.equal(res.status, 200);
  assert.equal(h.calls[0].url, 'https://api.openai.com/v1/images/edits');
  const parts = parseMultipart(h.calls[0].body, B);
  assert.equal(decoder.decode(parts[0].body), 'gpt-image-3-flare');
  assert.deepEqual(parts[2].body, png);
  assert.equal(parts[2].filename, '_style_reference.png');

  for (const n of ['2', '10', 'one', '1.0']) {
    res = await sendForm(h, '/v1/images/edits', okhttpForm(B, imageParts(n)));
    assert.equal(res.status, 400, `n=${n}`);
    assert.equal((await errorOf(res)).param, 'n');
  }
  res = await sendForm(h, '/v1/images/edits', okhttpForm(B, [...imageParts(), { name: 'n', value: '1' }]));
  assert.equal(res.status, 400);

  // JSON form (data-URL images); no Responses-only fields are added.
  res = await h.send('/v1/images/edits', {
    body: { model: 'gpt-image-2.5-flare', prompt: 'x', images: [{ image_url: 'data:image/png;base64,AAAA' }], n: 1 },
  });
  assert.equal(res.status, 200);
  const sent = jsonOf(h.calls.at(-1));
  assert.equal(sent.model, 'gpt-image-3-flare');
  assert.ok(!('store' in sent) && !('max_output_tokens' in sent) && !('safety_identifier' in sent));
  res = await h.send('/v1/images/edits', { body: { model: 'gpt-image-2.5-flare', prompt: 'x', n: 4 } });
  assert.equal(res.status, 400);

  // The image route checks the model too.
  res = await h.send('/v1/images/edits', { body: { model: 'gpt-image-2', prompt: 'x' } });
  assert.equal(res.status, 403);
});

test('images/edits cost policy: quality, size and reference count are capped; missing quality defaults to low', async () => {
  const h = harness();
  const png = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 1, 2, 3]);
  const form = (fields, images = 1) => [
    { name: 'model', value: 'gpt-image-2.5-flare' },
    { name: 'prompt', value: 'Draw ONE new sticker icon of gochujang' },
    ...Array.from({ length: images }, (_, i) => ({ name: 'image[]', filename: `ref${i}.png`, contentType: 'image/png', value: png })),
    ...Object.entries(fields).map(([name, value]) => ({ name, value })),
  ];
  // What the app sends passes unchanged.
  let res = await sendForm(h, '/v1/images/edits', okhttpForm(B, form({ quality: 'low', size: '1024x1024', n: '1' })));
  assert.equal(res.status, 200);
  assert.equal(parseMultipart(h.calls.at(-1).body, B).length, 6);

  for (const [fields, param] of [
    [{ quality: 'high' }, 'quality'], [{ quality: 'max' }, 'quality'], [{ quality: 'auto' }, 'quality'],
    [{ size: '4096x4096' }, 'size'], [{ size: '1536x1024' }, 'size'], [{ quality: 'low', size: '1024x1024' }, 'image'],
  ]) {
    res = await sendForm(h, '/v1/images/edits', okhttpForm(B, form(fields, param === 'image' ? 3 : 1)));
    assert.equal(res.status, 400, JSON.stringify(fields));
    assert.equal((await errorOf(res)).param, param);
  }
  res = await sendForm(h, '/v1/images/edits', okhttpForm(B, [...form({ quality: 'low' }), { name: 'quality', value: 'low' }]));
  assert.equal(res.status, 400);
  const before = h.calls.length;
  // Two references are fine; no quality gets "low" added, size stays the API's auto.
  res = await sendForm(h, '/v1/images/edits', okhttpForm(B, form({}, 2)));
  assert.equal(res.status, 200);
  assert.equal(h.calls.length, before + 1);
  const sent = parseMultipart(h.calls.at(-1).body, B);
  assert.deepEqual(sent.filter((p) => p.name === 'quality').map((p) => decoder.decode(p.body)), ['low']);
  assert.equal(sent.filter((p) => p.name === 'size').length, 0);

  // JSON: the same rules.
  res = await h.send('/v1/images/edits', { body: { model: 'gpt-image-2.5-flare', prompt: 'x', quality: 'high' } });
  assert.equal(res.status, 400);
  res = await h.send('/v1/images/edits', {
    body: { model: 'gpt-image-2.5-flare', prompt: 'x', images: Array(3).fill({ image_url: 'data:image/png;base64,AAAA' }) },
  });
  assert.equal(res.status, 400);
  res = await h.send('/v1/images/edits', { body: { model: 'gpt-image-2.5-flare', prompt: 'x', size: 'auto' } });
  assert.equal(res.status, 200);
  assert.equal(jsonOf(h.calls.at(-1)).quality, 'low');

  // Operators can widen it.
  const wide = harness({ env: baseEnv({ IMAGE_ALLOWED_QUALITIES: 'medium,high', IMAGE_ALLOWED_SIZES: '1024x1024,1536x1024', MAX_IMAGE_INPUTS: '3' }) });
  res = await sendForm(wide, '/v1/images/edits', okhttpForm(B, form({ quality: 'high', size: '1536x1024' }, 3)));
  assert.equal(res.status, 200);
  res = await sendForm(wide, '/v1/images/edits', okhttpForm(B, form({})));
  const filled = parseMultipart(wide.calls.at(-1).body, B);
  assert.deepEqual(filled.slice(-2).map((p) => [p.name, decoder.decode(p.body)]), [['quality', 'medium'], ['size', '1024x1024']]);
  const broken = harness({ env: baseEnv({ IMAGE_ALLOWED_SIZES: 'huge' }) });
  assert.equal((await sendForm(broken, '/v1/images/edits', okhttpForm(B, form({})))).status, 500);
});

test('parser unit checks', () => {
  assert.equal(multipartBoundary('application/json'), null);
  assert.equal(multipartBoundary(''), null);
  assert.equal(multipartBoundary('Multipart/Form-Data; Boundary=abc'), 'abc');
  assert.equal(multipartBoundary('multipart/form-data; charset=utf-8; boundary="a b"'), 'a b');
  assert.throws(() => multipartBoundary('multipart/form-data; boundary=' + 'x'.repeat(71)), MultipartError);
  assert.throws(() => parseMultipart(encoder.encode(`--${B}--\r\n`), B), /no parts/);
  const body = okhttpForm(B, [{ name: 'a', value: '' }, { name: 'b', value: new Uint8Array(0) }]);
  const parts = parseMultipart(body, B);
  assert.deepEqual(parts.map((p) => [p.name, p.body.length]), [['a', 0], ['b', 0]]);
  assert.deepEqual(serializeMultipart(parts, B), body);
  assert.deepEqual(concat([encoder.encode('a'), encoder.encode('b')]), encoder.encode('ab'));
});
