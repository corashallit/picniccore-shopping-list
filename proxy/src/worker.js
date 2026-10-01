// Picnic List proxy: an OpenAI-compatible passthrough for a Cloudflare Worker (also runs on Node, Deno
// and Bun through scripts/serve.mjs). Contract: docs/ARCHITECTURE.md §16; operations: docs/PROXY.md.
//
// Request flow: route -> config (fail closed) -> device token -> rate limit -> read + check body
// (size, MODEL_MAP then ALLOWED_MODELS, policy) -> forward with the real key -> stream the response back.
// Never logs request bodies, response bodies, device tokens or the OpenAI key.

import { authenticate } from './auth.js';
import { BodyTooLargeError, readBodyLimited } from './body.js';
import { getConfig, isModelId } from './config.js';
import { errorCodes, Errors } from './errors.js';
import {
  decodeText, MultipartError, multipartBoundary, parseMultipart, serializeMultipart, textPart,
} from './multipart.js';

const ROUTES = {
  '/v1/responses': { name: 'responses', upstreamPath: '/responses', accepts: 'json', limit: 'json' },
  '/v1/audio/transcriptions': {
    name: 'audio.transcriptions', upstreamPath: '/audio/transcriptions', accepts: 'multipart', limit: 'transcription',
  },
  '/v1/images/edits': { name: 'images.edits', upstreamPath: '/images/edits', accepts: 'json+multipart', limit: 'imageEdit' },
};
const MODELS_ROUTE = { name: 'models.retrieve' };
const MODELS_PATH = /^\/v1\/models\/([^/]+)$/;

// Upstream response headers passed back to the device. Everything else (OpenAI organization/project
// ids, cookies, CDN and transport headers) is dropped. Status and body are passed through unchanged.
const PASS_HEADERS = new Set([
  'content-type', 'cache-control', 'x-request-id', 'openai-processing-ms', 'openai-version', 'retry-after', 'retry-after-ms',
]);
const isPassHeader = (name) => PASS_HEADERS.has(name) || name.startsWith('x-ratelimit-');
const TOKEN_CHARS = /^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/;
const utf8 = new TextDecoder('utf-8', { fatal: true });

const defaultLog = (entry) => console.log(JSON.stringify(entry));

/**
 * @param {{fetch?: typeof fetch, log?: (entry: object) => void, now?: () => number}} [deps]
 *   Injected in tests; the Worker uses the globals.
 */
export function createHandler(deps = {}) {
  const fetchImpl = deps.fetch ?? ((input, init) => fetch(input, init));
  const log = deps.log ?? defaultLog;
  const now = deps.now ?? (() => Date.now());

  return async function handle(request, env = {}, _ctx = undefined) {
    const started = now();
    const method = request.method.toUpperCase();
    const pathname = new URL(request.url).pathname;
    try {
      if (pathname === '/healthz') return healthz(method, pathname, env, log);

      let route = null;
      let pathModel = null;
      if (method === 'POST') route = ROUTES[pathname] ?? null;
      else if (method === 'GET') {
        const m = MODELS_PATH.exec(pathname);
        if (m) {
          route = MODELS_ROUTE;
          pathModel = m[1];
        }
      }
      const reject = (response, extra = {}) => {
        log({
          event: 'reject', method, route: route?.name ?? null, status: response.status,
          code: errorCodes.get(response) ?? null, ms: now() - started, ...extra,
        });
        return response;
      };
      if (!route) return reject(Errors.notFound(method, pathname.slice(0, 100)), { path: pathname.slice(0, 100) });

      const configResult = getConfig(env);
      if (!configResult.ok) {
        log({ event: 'config_error', problems: configResult.problems });
        return reject(Errors.misconfigured());
      }
      const config = configResult.config;

      const auth = await authenticate(request, config);
      if (!auth.ok) return reject(Errors.invalidToken());
      const token = auth.label;

      if (config.rateLimiter) {
        let allowed = true;
        try {
          ({ success: allowed } = await config.rateLimiter.limit({ key: `token:${auth.hash}` }));
        } catch (err) {
          // Fail open: the OpenAI project spend limit is the hard cap, this is only a brake.
          log({ event: 'rate_limiter_error', error: err?.name ?? 'Error' });
        }
        if (!allowed) return reject(Errors.rateLimited(config.rateLimitRetryAfter), { token });
      }

      let prepared;
      try {
        prepared = route === MODELS_ROUTE
          ? prepareModelLookup(pathModel, config)
          : await prepareBody(request, route, config, auth);
      } catch (err) {
        if (err instanceof BodyTooLargeError) return reject(Errors.tooLarge(err.limit), { token });
        if (err instanceof MultipartError) return reject(Errors.invalidMultipart(err.message), { token });
        throw err;
      }
      if (prepared instanceof Response) return reject(prepared, { token });

      return await forward(request, prepared, config, { fetchImpl, log, now, started, method, route, token });
    } catch (err) {
      // Only the error's name: messages can quote body content (e.g. JSON parse errors).
      log({ event: 'error', method, path: pathname.slice(0, 100), error: err?.name ?? 'Error' });
      return Errors.internal();
    }
  };
}

function healthz(method, pathname, env, log) {
  if (method !== 'GET' && method !== 'HEAD') return Errors.notFound(method, pathname);
  const result = getConfig(env);
  if (!result.ok) log({ event: 'config_error', problems: result.problems });
  return new Response(method === 'HEAD' ? null : JSON.stringify({ ok: result.ok }), {
    status: result.ok ? 200 : 500,
    headers: { 'content-type': 'application/json', 'cache-control': 'no-store' },
  });
}

/** MODEL_MAP first (one step, exact ids), then the allow-list on the mapped (upstream) id. */
function mapModel(requested, config) {
  const upstream = config.modelMap.get(requested) ?? requested;
  if (!config.allowedModels.has(upstream)) return Errors.modelNotAllowed(requested, upstream);
  return { requested, upstream };
}

function prepareModelLookup(rawId, config) {
  let id;
  try {
    id = decodeURIComponent(rawId);
  } catch {
    return Errors.invalidModelField('Invalid model id.');
  }
  if (!isModelId(id)) return Errors.invalidModelField('Invalid model id.');
  const mapped = mapModel(id, config);
  if (mapped instanceof Response) return mapped;
  return {
    method: 'GET', upstreamPath: `/models/${encodeURIComponent(mapped.upstream)}`, body: undefined,
    contentType: undefined, model: id, upstreamModel: mapped.upstream, stream: false,
  };
}

async function prepareBody(request, route, config, auth) {
  const contentType = request.headers.get('content-type') ?? '';
  const isJson = contentType.split(';')[0].trim().toLowerCase() === 'application/json';
  const boundary = route.accepts === 'json' ? null : multipartBoundary(contentType);
  if (route.accepts === 'json' && !isJson) return Errors.unsupportedMediaType('application/json');
  if (route.accepts === 'multipart' && boundary === null) return Errors.unsupportedMediaType('multipart/form-data');
  if (route.accepts === 'json+multipart' && !isJson && boundary === null) {
    return Errors.unsupportedMediaType('multipart/form-data or application/json');
  }
  const bytes = await readBodyLimited(request, config.limits[route.limit]);
  return boundary !== null
    ? prepareMultipart(bytes, boundary, route, config)
    : prepareJson(bytes, route, config, auth);
}

function prepareJson(bytes, route, config, auth) {
  let body;
  try {
    body = JSON.parse(utf8.decode(bytes));
  } catch {
    return Errors.invalidJson();
  }
  if (body === null || typeof body !== 'object' || Array.isArray(body)) return Errors.invalidJson();

  // Exactly one field named "model" (case-variant duplicates could be read differently upstream).
  const modelKeys = Object.keys(body).filter((k) => k.trim().toLowerCase() === 'model');
  if (modelKeys.length === 0) return Errors.missingModel();
  if (modelKeys.length > 1 || modelKeys[0] !== 'model') return Errors.invalidModelField("Send exactly one 'model' field.");
  if (!isModelId(body.model)) return Errors.invalidModelField('Invalid model id.');
  const mapped = mapModel(body.model, config);
  if (mapped instanceof Response) return mapped;
  body.model = mapped.upstream;

  if (route.name === 'responses') {
    const problem = applyResponsesPolicy(body, config, auth);
    if (problem) return problem;
  } else if (route.name === 'images.edits') {
    const problem = checkImageCount(body.n, config) ?? applyImageJsonPolicy(body, config);
    if (problem) return problem;
  }
  // Re-serialized, so what was checked is exactly what is sent (duplicate JSON keys collapse here).
  return {
    method: 'POST', upstreamPath: route.upstreamPath, body: JSON.stringify(body), contentType: 'application/json',
    model: mapped.requested, upstreamModel: mapped.upstream, stream: body.stream === true,
  };
}

/** Responses policy (ARCHITECTURE §16.1): store:false, output cap, no paid service tiers, tool allow-list. */
function applyResponsesPolicy(body, config, auth) {
  if (body.tools !== undefined && body.tools !== null) {
    if (!Array.isArray(body.tools)) return Errors.invalidParam('tools', "'tools' must be an array.");
    for (const tool of body.tools) {
      // Hosted tools can carry their own model (image_generation) or cost (code_interpreter, file_search),
      // so they would bypass the model allow-list. Only listed tool types pass.
      const type = tool && typeof tool === 'object' && !Array.isArray(tool) ? tool.type : undefined;
      if (typeof type !== 'string' || !config.allowedTools.has(type)) {
        return Errors.toolNotAllowed(typeof type === 'string' ? type.replace(/[^A-Za-z0-9_.-]/g, '').slice(0, 64) : '?');
      }
    }
  }
  body.store = false;
  delete body.service_tier; // "priority"/"fast" bill at 2x; the project default tier applies
  const requestedMax = body.max_output_tokens;
  if (!(Number.isSafeInteger(requestedMax) && requestedMax <= config.maxOutputTokens)) {
    body.max_output_tokens = config.maxOutputTokens;
  }
  // Lets OpenAI attribute abuse signals per device when the app did not send its own id.
  if (typeof body.safety_identifier !== 'string' || body.safety_identifier === '') {
    body.safety_identifier = `picnic-${auth.hash.slice(0, 16)}`;
  }
  return null;
}

function checkImageCount(n, config) {
  if (n === undefined || n === null) return null;
  if (Number.isSafeInteger(n) && n >= 1 && n <= config.maxImagesPerRequest) return null;
  return Errors.invalidParam('n', `'n' must be between 1 and ${config.maxImagesPerRequest} through this proxy.`);
}

/**
 * Image edit cost policy (docs/PROXY.md): `quality` and `size` from IMAGE_ALLOWED_QUALITIES /
 * IMAGE_ALLOWED_SIZES, at most MAX_IMAGE_INPUTS reference images. A missing quality or size gets the
 * list's first value unless "auto" (the API's default) is allowed. Returns an error Response, or
 * {fill: [[field, value]...]} for the fields to add.
 */
function checkImageFields(quality, size, inputs, config) {
  const fill = [];
  for (const [param, value, allowed] of [['quality', quality, config.imageQualities], ['size', size, config.imageSizes]]) {
    if (value === undefined) {
      if (!allowed.includes('auto')) fill.push([param, allowed[0]]);
    } else if (typeof value !== 'string' || !allowed.includes(value.trim().toLowerCase())) {
      return Errors.invalidParam(param, `'${param}' must be one of ${allowed.join(', ')} through this proxy.`);
    }
  }
  if (inputs > config.maxImageInputs) {
    return Errors.invalidParam('image', `At most ${config.maxImageInputs} input images per edit through this proxy.`);
  }
  return { fill };
}

function applyImageJsonPolicy(body, config) {
  const count = (v) => (Array.isArray(v) ? v.length : v === undefined || v === null ? 0 : 1);
  const result = checkImageFields(
    body.quality ?? undefined, body.size ?? undefined, count(body.images) + count(body.image), config,
  );
  if (result instanceof Response) return result;
  for (const [field, value] of result.fill) body[field] = value;
  return null;
}

function prepareMultipart(bytes, boundary, route, config) {
  const parts = parseMultipart(bytes, boundary);
  const named = (name) => parts.filter((p) => p.name.trim().toLowerCase() === name);

  const modelParts = named('model');
  if (modelParts.length === 0) return Errors.missingModel();
  const modelPart = modelParts[0];
  if (modelParts.length > 1 || modelPart.name !== 'model' || modelPart.filename !== undefined) {
    return Errors.invalidModelField("Send exactly one plain 'model' field.");
  }
  const requested = decodeText(modelPart.body);
  if (!isModelId(requested)) return Errors.invalidModelField('Invalid model id.');
  const mapped = mapModel(requested, config);
  if (mapped instanceof Response) return mapped;

  const extraParts = [];
  if (route.name === 'images.edits') {
    // At most one plain (non-file) field each for n, quality and size.
    const single = (name) => {
      const found = named(name);
      if (found.length > 1 || (found.length === 1 && (found[0].name !== name || found[0].filename !== undefined))) {
        return Errors.invalidParam(name, `Send at most one plain '${name}' field.`);
      }
      return found.length === 1 ? decodeText(found[0].body) : undefined;
    };
    const n = single('n');
    if (n instanceof Response) return n;
    if (n !== undefined) {
      const problem = /^[0-9]{1,3}$/.test(n)
        ? checkImageCount(Number(n), config)
        : Errors.invalidParam('n', "'n' must be an integer.");
      if (problem) return problem;
    }
    const quality = single('quality');
    if (quality instanceof Response) return quality;
    const size = single('size');
    if (size instanceof Response) return size;
    const inputs = parts.filter((p) => /^image(\[[0-9]*\])?$/.test(p.name.trim().toLowerCase())).length;
    const result = checkImageFields(quality, size, inputs, config);
    if (result instanceof Response) return result;
    for (const [field, value] of result.fill) extraParts.push(textPart(field, value));
  }

  // Every part other than a remapped model field is forwarded byte-identical (filenames included).
  const outParts = (mapped.upstream === requested
    ? parts
    : parts.map((p) => (p === modelPart ? textPart('model', mapped.upstream) : p))).concat(extraParts);
  const quoted = TOKEN_CHARS.test(boundary) ? boundary : `"${boundary}"`;
  return {
    method: 'POST', upstreamPath: route.upstreamPath, body: serializeMultipart(outParts, boundary),
    contentType: `multipart/form-data; boundary=${quoted}`, model: requested, upstreamModel: mapped.upstream, stream: false,
  };
}

async function forward(request, prepared, config, ctx) {
  const { fetchImpl, log, now, started, method, route, token } = ctx;
  const headers = new Headers({ authorization: `Bearer ${config.apiKey}` });
  if (prepared.contentType) headers.set('content-type', prepared.contentType);
  const accept = request.headers.get('accept');
  if (accept) headers.set('accept', accept);

  // The timeout covers the wait for response headers only; a streamed body may take longer.
  const controller = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, config.upstreamTimeoutMs);
  try {
    request.signal?.addEventListener?.('abort', () => controller.abort(), { once: true });
  } catch {
    // Runtimes without request.signal support: the upstream call is cancelled with the invocation.
  }

  const base = {
    method, route: route.name, token, model: prepared.model,
    upstreamModel: prepared.upstreamModel !== prepared.model ? prepared.upstreamModel : undefined,
  };
  let upstream;
  try {
    upstream = await fetchImpl(config.upstreamBase + prepared.upstreamPath, {
      method: prepared.method, headers, body: prepared.body, signal: controller.signal, redirect: 'manual',
    });
  } catch (err) {
    const response = timedOut ? Errors.upstreamTimeout() : Errors.upstreamUnreachable();
    log({ event: 'upstream_error', ...base, status: response.status, error: timedOut ? 'timeout' : (err?.name ?? 'Error'), ms: now() - started });
    return response;
  } finally {
    clearTimeout(timer);
  }

  const requestId = upstream.headers.get('x-request-id') ?? undefined;
  if (upstream.status === 401) {
    await upstream.body?.cancel().catch(() => {});
    log({ event: 'upstream_auth_failed', ...base, status: 502, requestId, ms: now() - started });
    return Errors.upstreamAuth();
  }

  const out = new Headers();
  upstream.headers.forEach((value, name) => {
    if (isPassHeader(name.toLowerCase())) out.set(name, value);
  });
  log({ event: 'proxy', ...base, status: upstream.status, stream: prepared.stream || undefined, requestId, ms: now() - started });
  const noBody = [101, 204, 205, 304].includes(upstream.status);
  return new Response(noBody ? null : upstream.body, { status: upstream.status, statusText: upstream.statusText, headers: out });
}

const handle = createHandler();

export default {
  /** Cloudflare Workers module entry point. */
  fetch(request, env, ctx) {
    return handle(request, env, ctx);
  },
};
