// Parses and validates the Worker's environment once per env object. Any malformed value fails closed:
// every request then gets 500 proxy_misconfigured and the problems are logged (never values of secrets).

/** The four ModelDefaults ids in core (core/…/llm/ModelDefaults.kt). Upstream ids, i.e. after MODEL_MAP. */
export const DEFAULT_ALLOWED_MODELS = Object.freeze(['gpt-5.6-terra', 'gpt-6-luna', 'gpt-transcribe', 'gpt-image-2.5-flare']);
/** The app only uses the hosted web_search tool (ingest of text and transcripts). */
export const DEFAULT_ALLOWED_TOOLS = Object.freeze(['web_search']);
export const DEFAULT_UPSTREAM = 'https://api.openai.com/v1';
/**
 * Image edit cost policy. The app always sends quality=low, size=1024x1024 and one reference image;
 * the first entry of each list is filled in when a request leaves the field out (unless "auto" is allowed).
 */
export const DEFAULT_IMAGE_QUALITIES = Object.freeze(['low', 'medium']);
export const DEFAULT_IMAGE_SIZES = Object.freeze(['1024x1024', 'auto']);

const MB = 1024 * 1024;
const DEFAULTS = Object.freeze({
  maxOutputTokens: 16000,
  maxJsonMb: 15,
  maxTranscriptionMb: 26,
  maxImageEditMb: 20,
  maxImagesPerRequest: 1,
  maxImageInputs: 2,
  upstreamTimeoutSeconds: 180, // to response headers; the contract forbids anything under 150 s
  rateLimitRetryAfter: 60,
});

const MODEL_ID = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
const TOOL_TYPE = /^[a-z0-9_]{1,64}$/;
const HEX_HASH = /^[0-9a-f]{64}$/i;
const LABEL = /^[A-Za-z0-9._-]{1,48}$/;

export function isModelId(value) {
  return typeof value === 'string' && MODEL_ID.test(value);
}

/** @type {WeakMap<object, ReturnType<typeof parseConfig>>} */
const cache = new WeakMap();

/**
 * @returns {{ok: true, config: Config} | {ok: false, problems: string[]}}
 */
export function getConfig(env) {
  if (env === null || typeof env !== 'object') return parseConfig({});
  let result = cache.get(env);
  if (!result) {
    result = parseConfig(env);
    cache.set(env, result);
  }
  return result;
}

/**
 * @typedef {object} Config
 * @property {string} apiKey
 * @property {string} upstreamBase
 * @property {Set<string>} allowedModels
 * @property {Map<string,string>} modelMap
 * @property {Set<string>} allowedTools
 * @property {number} maxOutputTokens
 * @property {{json: number, transcription: number, imageEdit: number}} limits  bytes
 * @property {number} maxImagesPerRequest  output images (`n`)
 * @property {string[]} imageQualities  allowed `quality` values; [0] is the default
 * @property {string[]} imageSizes  allowed `size` values; [0] is the default
 * @property {number} maxImageInputs  input (reference) images per image edit
 * @property {number} upstreamTimeoutMs
 * @property {number} rateLimitRetryAfter
 * @property {{label: string|null, bytes: Uint8Array}[]} tokenHashes
 * @property {{get(key: string): Promise<string|null>}|null} tokensKv
 * @property {{limit(opts: {key: string}): Promise<{success: boolean}>}|null} rateLimiter
 */
export function parseConfig(env) {
  const problems = [];

  const apiKey = typeof env.OPENAI_API_KEY === 'string' ? env.OPENAI_API_KEY.trim() : '';
  if (!apiKey) problems.push('OPENAI_API_KEY is not set');
  else if (/[\s]/.test(apiKey)) problems.push('OPENAI_API_KEY contains whitespace');

  let upstreamBase = DEFAULT_UPSTREAM;
  if (present(env.OPENAI_BASE_URL)) {
    upstreamBase = String(env.OPENAI_BASE_URL).trim().replace(/\/+$/, '');
    if (!validUpstream(upstreamBase)) problems.push('OPENAI_BASE_URL must be an https URL (http only for localhost)');
  }

  const allowedModels = new Set();
  if (present(env.ALLOWED_MODELS)) {
    for (const id of list(env.ALLOWED_MODELS)) {
      if (isModelId(id)) allowedModels.add(id);
      else problems.push('ALLOWED_MODELS has an entry that is not a model id');
    }
    if (allowedModels.size === 0) problems.push('ALLOWED_MODELS is set but empty');
  } else {
    DEFAULT_ALLOWED_MODELS.forEach((id) => allowedModels.add(id));
  }

  const modelMap = new Map();
  if (present(env.MODEL_MAP)) {
    const parsed = parseModelMap(env.MODEL_MAP);
    if (parsed.ok) parsed.map.forEach((v, k) => modelMap.set(k, v));
    else problems.push(`MODEL_MAP ${parsed.problem}`);
  }

  const allowedTools = new Set();
  if (env.ALLOWED_TOOLS !== undefined && env.ALLOWED_TOOLS !== null) {
    // An explicitly empty ALLOWED_TOOLS means "no tools at all".
    for (const t of list(env.ALLOWED_TOOLS)) {
      if (TOOL_TYPE.test(t)) allowedTools.add(t);
      else problems.push('ALLOWED_TOOLS has an entry that is not a tool type');
    }
  } else {
    DEFAULT_ALLOWED_TOOLS.forEach((t) => allowedTools.add(t));
  }

  const num = (name, fallback, min, max) => {
    const raw = env[name];
    if (!present(raw)) return fallback;
    const n = Number(raw);
    if (!Number.isFinite(n) || n < min || n > max) {
      problems.push(`${name} must be a number between ${min} and ${max}`);
      return fallback;
    }
    return n;
  };
  const maxOutputTokens = Math.floor(num('MAX_OUTPUT_TOKENS', DEFAULTS.maxOutputTokens, 1, 1_000_000));
  const limits = {
    json: Math.floor(num('MAX_JSON_MB', DEFAULTS.maxJsonMb, 0.001, 100) * MB),
    transcription: Math.floor(num('MAX_TRANSCRIPTION_MB', DEFAULTS.maxTranscriptionMb, 0.001, 100) * MB),
    imageEdit: Math.floor(num('MAX_IMAGE_EDIT_MB', DEFAULTS.maxImageEditMb, 0.001, 100) * MB),
  };
  const maxImagesPerRequest = Math.floor(num('MAX_IMAGES_PER_REQUEST', DEFAULTS.maxImagesPerRequest, 1, 10));
  const maxImageInputs = Math.floor(num('MAX_IMAGE_INPUTS', DEFAULTS.maxImageInputs, 1, 16));
  const choices = (name, fallback, pattern) => {
    if (!present(env[name])) return [...fallback];
    const values = [...new Set(list(env[name]).map((v) => v.toLowerCase()))];
    if (values.length === 0 || !values.every((v) => pattern.test(v))) {
      problems.push(`${name} must be a comma-separated list of values like ${fallback.join(',')}`);
      return [...fallback];
    }
    return values;
  };
  const imageQualities = choices('IMAGE_ALLOWED_QUALITIES', DEFAULT_IMAGE_QUALITIES, /^[a-z]{1,16}$/);
  const imageSizes = choices('IMAGE_ALLOWED_SIZES', DEFAULT_IMAGE_SIZES, /^(auto|[1-9][0-9]{1,4}x[1-9][0-9]{1,4})$/);
  const upstreamTimeoutMs = Math.round(num('UPSTREAM_TIMEOUT_SECONDS', DEFAULTS.upstreamTimeoutSeconds, 0.01, 900) * 1000);
  const rateLimitRetryAfter = Math.ceil(num('RATE_LIMIT_RETRY_AFTER', DEFAULTS.rateLimitRetryAfter, 1, 3600));

  const tokenHashes = [];
  if (present(env.ACCESS_TOKEN_HASHES)) {
    for (const entry of list(env.ACCESS_TOKEN_HASHES)) {
      const parsed = parseHashEntry(entry);
      if (parsed) tokenHashes.push(parsed);
      else problems.push('ACCESS_TOKEN_HASHES has an entry that is not "<sha256 hex>" or "<name>:<sha256 hex>"');
    }
  }
  const tokensKv = isKv(env.TOKENS) ? env.TOKENS : null;
  if (env.TOKENS !== undefined && !tokensKv) problems.push('TOKENS is set but is not a KV namespace binding');
  if (tokenHashes.length === 0 && !tokensKv) {
    problems.push('no device tokens configured (set ACCESS_TOKEN_HASHES or bind the TOKENS KV namespace)');
  }

  const rateLimiter = env.RATE_LIMITER && typeof env.RATE_LIMITER.limit === 'function' ? env.RATE_LIMITER : null;
  if (env.RATE_LIMITER !== undefined && !rateLimiter) problems.push('RATE_LIMITER is set but is not a rate limiting binding');

  if (problems.length) return { ok: false, problems: [...new Set(problems)] };
  return {
    ok: true,
    config: {
      apiKey, upstreamBase, allowedModels, modelMap, allowedTools, maxOutputTokens, limits,
      maxImagesPerRequest, imageQualities, imageSizes, maxImageInputs,
      upstreamTimeoutMs, rateLimitRetryAfter, tokenHashes, tokensKv, rateLimiter,
    },
  };
}

/**
 * MODEL_MAP is a JSON object of requested id -> upstream id. Accepts a JSON string (secret or plain var)
 * or an already-parsed object (a JSON-valued var in wrangler.jsonc).
 */
export function parseModelMap(raw) {
  let value = raw;
  if (typeof raw === 'string') {
    try {
      value = JSON.parse(raw);
    } catch {
      return { ok: false, problem: 'is not valid JSON' };
    }
  }
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    return { ok: false, problem: 'is not a JSON object' };
  }
  const map = new Map();
  for (const [k, v] of Object.entries(value)) {
    if (!isModelId(k) || !isModelId(v)) return { ok: false, problem: 'must map model id strings to model id strings' };
    map.set(k, v);
  }
  return { ok: true, map };
}

function parseHashEntry(entry) {
  const i = entry.lastIndexOf(':');
  const label = i >= 0 ? entry.slice(0, i) : null;
  const hex = i >= 0 ? entry.slice(i + 1) : entry;
  if (!HEX_HASH.test(hex)) return null;
  if (label !== null && !LABEL.test(label)) return null;
  return { label, bytes: hexToBytes(hex) };
}

export function hexToBytes(hex) {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  return out;
}

function present(v) {
  if (v === undefined || v === null) return false;
  if (typeof v === 'string') return v.trim() !== '';
  return true;
}

/** Comma- or newline-separated string, or an array (JSON-valued var). Entries are trimmed. */
function list(v) {
  const items = Array.isArray(v) ? v.map(String) : String(v).split(/[,\r\n]+/);
  return items.map((x) => x.trim()).filter(Boolean);
}

function validUpstream(url) {
  try {
    const u = new URL(url);
    if (u.search || u.hash) return false;
    if (u.protocol === 'https:') return true;
    return u.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(u.hostname);
  } catch {
    return false;
  }
}

function isKv(v) {
  return !!v && typeof v === 'object' && typeof v.get === 'function';
}
