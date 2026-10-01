// Error responses in OpenAI's shape, so the app's client maps proxy errors exactly like upstream ones:
// {"error":{"message","type","param","code"}} (docs/ARCHITECTURE.md §7.2 and §16).

/** Error code of each proxy-made error response, for the request log. */
export const errorCodes = new WeakMap();

/**
 * @param {number} status
 * @param {string} message  Safe, proxy-authored text. Never include a token, a key or body content.
 * @param {{type?: string, code?: string|null, param?: string|null, headers?: Record<string,string>}} [opts]
 */
export function apiError(status, message, opts = {}) {
  const { type = 'invalid_request_error', code = null, param = null, headers = {} } = opts;
  const h = new Headers({ 'content-type': 'application/json', 'cache-control': 'no-store' });
  for (const [k, v] of Object.entries(headers)) h.set(k, v);
  const response = new Response(JSON.stringify({ error: { message, type, param, code } }), { status, headers: h });
  errorCodes.set(response, code ?? type);
  return response;
}

export const Errors = {
  notFound: (method, path) =>
    apiError(404, `Invalid URL (${method} ${path}). This proxy serves only POST /v1/responses, ` +
      'POST /v1/audio/transcriptions, POST /v1/images/edits and GET /v1/models/{id}.'),

  // Exact body from the contract (ARCHITECTURE §16.1); core maps it to LlmError.Unauthorized.
  invalidToken: () => apiError(401, 'Invalid device token', { code: 'invalid_api_key' }),

  misconfigured: () => apiError(500, 'Proxy misconfigured', { type: 'server_error', code: 'proxy_misconfigured' }),

  rateLimited: (retryAfterSeconds) =>
    apiError(429, 'Too many requests from this device. Try again shortly.', {
      type: 'rate_limit_error',
      code: 'rate_limit_exceeded',
      headers: { 'retry-after': String(retryAfterSeconds) },
    }),

  tooLarge: (limitBytes) =>
    apiError(413, `Request body is larger than this route allows (${Math.floor(limitBytes / (1024 * 1024))} MB).`, {
      code: 'request_too_large',
    }),

  unsupportedMediaType: (expected) =>
    apiError(415, `Unsupported Content-Type. Expected ${expected}.`, { code: 'unsupported_content_type' }),

  invalidJson: () =>
    apiError(400, 'We could not parse the JSON body of your request. It must be a JSON object.', { code: 'invalid_json' }),

  invalidMultipart: (detail) =>
    apiError(400, `Could not parse multipart form: ${detail}`, { code: 'invalid_multipart' }),

  missingModel: () =>
    apiError(400, "Missing required parameter: 'model'.", { code: 'missing_required_parameter', param: 'model' }),

  invalidModelField: (detail) => apiError(400, detail, { code: 'invalid_value', param: 'model' }),

  // Core maps 403 model_not_allowed to LlmError.BadResponse (not Unauthorized).
  modelNotAllowed: (requested, upstream) =>
    apiError(403, requested === upstream
      ? `The model \`${requested}\` is not allowed through this proxy.`
      : `The model \`${requested}\` (mapped to \`${upstream}\`) is not allowed through this proxy.`, {
      code: 'model_not_allowed',
      param: 'model',
    }),

  toolNotAllowed: (type) =>
    apiError(400, `The tool type \`${type}\` is not allowed through this proxy.`, { code: 'tool_not_allowed', param: 'tools' }),

  invalidParam: (param, detail) => apiError(400, detail, { code: 'invalid_value', param }),

  upstreamUnreachable: () =>
    apiError(502, 'The proxy could not reach the OpenAI API.', { type: 'server_error', code: 'upstream_unreachable' }),

  // The upstream rejected the proxy's own key. Not the device's fault, and OpenAI's 401 body echoes a
  // masked version of the real key, so the body is replaced.
  upstreamAuth: () =>
    apiError(502, 'The proxy\'s OpenAI credentials were rejected. Ask whoever runs the proxy.', {
      type: 'server_error',
      code: 'upstream_auth_failed',
    }),

  upstreamTimeout: () =>
    apiError(504, 'The OpenAI API did not answer in time.', { type: 'server_error', code: 'upstream_timeout' }),

  internal: () => apiError(500, 'Internal proxy error', { type: 'server_error', code: 'proxy_error' }),
};
