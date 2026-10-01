#!/usr/bin/env node
// Production server for self-hosting the proxy on Node 22+ (docs/PROXY.md §4), behind a TLS reverse proxy
// on the same machine (deploy/Caddyfile or deploy/nginx-picnic.conf). Docker: Dockerfile + compose.yaml;
// systemd without Docker: deploy/picnic-proxy.service.
//
//   node --env-file=/etc/picnic-proxy/picnic-proxy.env scripts/server.mjs
//
// How it differs from scripts/serve.mjs (local development):
//  - Settings come only from the process environment (systemd EnvironmentFile=, compose env_file:,
//    node --env-file). It never reads proxy/.dev.vars next to the code.
//  - A broken configuration stops it at startup with exit code 78 and the problems logged (setting names,
//    never values), instead of answering 500 to every request.
//  - An in-memory per-device rate limit (RATE_LIMIT_PER_MINUTE) stands in for the Workers binding.
//  - Socket timeouts fit slow reasoning calls, and SIGTERM/SIGINT let in-flight requests finish first.
//
// Server settings, in addition to the proxy's own (docs/PROXY.md §9):
//   HOST                    127.0.0.1  listen address (the Docker image sets 0.0.0.0 inside the container)
//   PORT                    8787
//   IDLE_TIMEOUT_SECONDS    300        socket inactivity limit; must be longer than UPSTREAM_TIMEOUT_SECONDS (180)
//   SHUTDOWN_GRACE_SECONDS  120        how long SIGTERM waits for in-flight requests (the app gives up at 120 s)
//   RATE_LIMIT_PER_MINUTE   60         requests per device per minute; 0 turns the limit off
//
// Logs: one JSON line per event on stdout (journald, docker logs). The proxy's request lines never contain
// bodies, tokens or the key, and this file only adds startup, shutdown and error-name lines.

import { createServer } from 'node:http';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { pathToFileURL } from 'node:url';
import { getConfig } from '../src/config.js';
import { createHandler } from '../src/worker.js';

/** EX_CONFIG from sysexits.h. The systemd unit sets RestartPreventExitStatus=78, so a bad config doesn't loop. */
export const EXIT_CONFIG = 78;

export const DEFAULT_SETTINGS = Object.freeze({
  host: '127.0.0.1',
  port: 8787,
  idleTimeoutSeconds: 300,
  shutdownGraceSeconds: 120,
  rateLimitPerMinute: 60,
});

const HEADERS_TIMEOUT_MS = 30_000; // complete request headers
const REQUEST_TIMEOUT_MS = 300_000; // complete request, body included (a 26 MB upload on a slow link)
// Longer than the reverse proxy keeps idle upstream connections (60 s in deploy/), so the proxy never
// sends a request on a connection Node is closing at the same moment (a classic source of stray 502s).
const KEEP_ALIVE_TIMEOUT_MS = 75_000;
// A request body the Worker didn't read to the end (a 401, 429, 404 or 415 on an upload, or a 413) is read
// and thrown away after the answer, up to this much: the device sees the answer instead of a reset, and the
// connection can carry its next request. That covers anything Caddy or nginx let through (27 MiB, deploy/).
// A body declared bigger than this gets "Connection: close" instead; one that keeps going is cut off here.
const DISCARD_LIMIT_BYTES = 27 * 1024 * 1024;
const HOP_BY_HOP = new Set(['connection', 'keep-alive', 'transfer-encoding', 'upgrade', 'proxy-connection', 'te', 'trailer']);
// Methods the WHATWG Request constructor refuses; everything else reaches the Worker (which 404s it).
const FORBIDDEN_METHODS = new Set(['CONNECT', 'TRACE', 'TRACK']);
const INTERNAL_ERROR = JSON.stringify({
  error: { message: 'Internal proxy error', type: 'server_error', param: null, code: 'proxy_error' },
});
const NOT_FOUND = JSON.stringify({
  error: { message: 'Invalid URL. This proxy serves only /v1 routes.', type: 'invalid_request_error', param: null, code: null },
});

const defaultLog = (entry) => console.log(JSON.stringify(entry));

/**
 * Parses the server's own settings (not the proxy's; src/config.js validates those).
 * @returns {{problems: string[], settings: typeof DEFAULT_SETTINGS}}
 */
export function parseServerSettings(env) {
  const problems = [];
  const present = (v) => v !== undefined && v !== null && String(v).trim() !== '';
  const num = (name, fallback, min, max, integer = false) => {
    if (!present(env[name])) return fallback;
    const n = Number(String(env[name]).trim());
    if (!Number.isFinite(n) || n < min || n > max || (integer && !Number.isInteger(n))) {
      problems.push(`${name} must be ${integer ? 'an integer' : 'a number'} between ${min} and ${max}`);
      return fallback;
    }
    return n;
  };
  const host = present(env.HOST) ? String(env.HOST).trim() : DEFAULT_SETTINGS.host;
  if (!/^[A-Za-z0-9.:-]{1,253}$/.test(host)) problems.push('HOST must be an IP address or host name');
  return {
    problems,
    settings: {
      host,
      port: num('PORT', DEFAULT_SETTINGS.port, 0, 65535, true),
      idleTimeoutSeconds: num('IDLE_TIMEOUT_SECONDS', DEFAULT_SETTINGS.idleTimeoutSeconds, 1, 3600),
      shutdownGraceSeconds: num('SHUTDOWN_GRACE_SECONDS', DEFAULT_SETTINGS.shutdownGraceSeconds, 0, 3600),
      rateLimitPerMinute: num('RATE_LIMIT_PER_MINUTE', DEFAULT_SETTINGS.rateLimitPerMinute, 0, 1_000_000, true),
    },
  };
}

/**
 * A fixed-window, in-memory limiter with the Workers Rate Limiting binding's interface. Keys are per device
 * (the proxy calls it only after the token checks out), so the map stays as small as the device list.
 */
export function createRateLimiter({ limit, periodMs = 60_000, now = () => Date.now() }) {
  const windows = new Map();
  let lastSweep = now();
  return {
    async limit({ key }) {
      const t = now();
      if (t - lastSweep >= periodMs) {
        for (const [k, w] of windows) if (t - w.start >= periodMs) windows.delete(k);
        lastSweep = t;
      }
      let w = windows.get(key);
      if (!w || t - w.start >= periodMs) {
        w = { start: t, count: 0 };
        windows.set(key, w);
      }
      w.count += 1;
      return { success: w.count <= limit };
    },
  };
}

/**
 * Values copied unchanged from deploy/picnic-proxy.env.example. They are well-formed, so the proxy would
 * start and then fail every request (upstream_auth_failed, invalid_api_key); refuse them at startup instead.
 */
export function templatePlaceholders(env) {
  const problems = [];
  if (typeof env.OPENAI_API_KEY === 'string' && env.OPENAI_API_KEY.includes('REPLACE_ME')) {
    problems.push('OPENAI_API_KEY is still the placeholder from picnic-proxy.env.example');
  }
  if (typeof env.ACCESS_TOKEN_HASHES === 'string' && /(^|[:,\s])0{64}(?=$|[,\s])/.test(env.ACCESS_TOKEN_HASHES)) {
    problems.push('ACCESS_TOKEN_HASHES still has the placeholder entry from picnic-proxy.env.example');
  }
  return problems;
}

const urlHost = (host) => (host.includes(':') ? `[${host}]` : host);

/**
 * Starts the server. Returns {ok:false, problems} for a broken configuration (nothing is listening then),
 * otherwise {ok:true, server, url, shutdown(signal), stopped}. `stopped` resolves once every connection
 * has closed after shutdown().
 * @param {{env?: Record<string, unknown>, log?: (entry: object) => void}} [opts]
 */
export async function startServer({ env = process.env, log = defaultLog } = {}) {
  const { problems, settings } = parseServerSettings(env);
  const workerEnv = { ...env };
  delete workerEnv.RATE_LIMITER;
  if (settings.rateLimitPerMinute > 0) workerEnv.RATE_LIMITER = createRateLimiter({ limit: settings.rateLimitPerMinute });
  problems.push(...templatePlaceholders(env));
  const config = getConfig(workerEnv);
  if (!config.ok) problems.push(...config.problems);
  else if (settings.idleTimeoutSeconds * 1000 <= config.config.upstreamTimeoutMs) {
    problems.push('IDLE_TIMEOUT_SECONDS must be longer than UPSTREAM_TIMEOUT_SECONDS');
  }
  if (problems.length) return { ok: false, problems };

  const handle = createHandler({ log });
  let draining = false;
  let inflight = 0;
  /** Requests whose answer is out but whose unread body is still being thrown away. */
  const discarding = new Set();

  const server = createServer({ headersTimeout: HEADERS_TIMEOUT_MS, requestTimeout: REQUEST_TIMEOUT_MS }, (req, res) => {
    inflight++;
    const abort = new AbortController();
    res.on('close', () => {
      inflight--;
      // The device went away before the answer was complete: cancel the upstream call too.
      if (!res.writableFinished) abort.abort();
      if (draining) setImmediate(() => server.closeIdleConnections());
    });
    // Runs after Node's own 'finish' handling, which already discards a body nobody started reading.
    res.on('finish', () => {
      if (!req.complete && !req.destroyed) discardUnreadBody(req);
    });
    serve(req, res, abort).catch(() => res.destroy());
  });

  /**
   * Reads and drops what is left of a request body after its answer went out (see DISCARD_LIMIT_BYTES).
   * Without this the socket stays paused mid-request: the next request on it is never answered, a close
   * from the other end goes unnoticed, and shutdown waits for it as if a request were still running.
   */
  function discardUnreadBody(req) {
    let budget = DISCARD_LIMIT_BYTES;
    discarding.add(req);
    req.removeAllListeners('readable'); // a body read the Worker abandoned (bodyStream) must not block flowing
    req.on('data', (chunk) => {
      budget -= chunk.length;
      if (budget < 0) req.destroy();
    });
    req.once('end', () => {
      if (draining) setImmediate(() => server.closeIdleConnections());
    });
    req.once('close', () => discarding.delete(req));
    req.resume();
  }
  // A socket with no traffic either way for this long is dropped. A non-streamed reasoning call is silent
  // until OpenAI answers, and the proxy gives up on OpenAI after UPSTREAM_TIMEOUT_SECONDS (504) first.
  server.timeout = settings.idleTimeoutSeconds * 1000;
  server.keepAliveTimeout = KEEP_ALIVE_TIMEOUT_MS;

  async function serve(req, res, abort) {
    const method = req.method ?? 'GET';
    if (FORBIDDEN_METHODS.has(method.toUpperCase())) {
      res.writeHead(404, { 'content-type': 'application/json', 'cache-control': 'no-store' });
      res.end(NOT_FOUND);
      return;
    }
    let response;
    try {
      response = await handle(toWebRequest(req, abort.signal), workerEnv);
    } catch (err) {
      // The Worker answers its own errors; this is only for a failure in the adapter itself.
      log({ event: 'adapter_error', error: err?.name ?? 'Error' });
      if (!res.headersSent && !res.destroyed) {
        res.writeHead(500, { 'content-type': 'application/json', 'cache-control': 'no-store' });
        res.end(INTERNAL_ERROR);
      }
      return;
    }
    if (res.destroyed) {
      await response.body?.cancel().catch(() => {});
      return;
    }
    const headers = {};
    response.headers.forEach((value, name) => {
      headers[name] = value;
    });
    // While draining, and when the device declared more body than is worth reading to the end, this is
    // the last answer on the connection.
    if (draining || (!req.complete && Number(req.headers['content-length']) > DISCARD_LIMIT_BYTES)) {
      headers.connection = 'close';
    }
    res.writeHead(response.status, headers);
    if (!response.body || method === 'HEAD') {
      await response.body?.cancel().catch(() => {});
      res.end();
      return;
    }
    // Status line and headers go out now; each body chunk (an SSE event) is written as it arrives.
    res.flushHeaders();
    try {
      await pipeline(Readable.fromWeb(response.body), res);
    } catch (err) {
      if (!abort.signal.aborted) log({ event: 'stream_error', error: err?.name ?? 'Error' });
      res.destroy();
    }
  }

  try {
    await new Promise((resolve, reject) => {
      server.once('error', reject);
      server.listen(settings.port, settings.host, () => {
        server.off('error', reject);
        resolve();
      });
    });
  } catch (err) {
    return { ok: false, problems: [`cannot listen on ${settings.host}:${settings.port} (${err?.code ?? err?.name ?? 'Error'})`] };
  }
  const url = `http://${urlHost(settings.host)}:${server.address().port}`;
  log({
    event: 'listening', url: `${url}/v1`, node: process.version, rateLimitPerMinute: settings.rateLimitPerMinute,
    idleTimeoutSeconds: settings.idleTimeoutSeconds, shutdownGraceSeconds: settings.shutdownGraceSeconds,
  });

  let resolveStopped;
  const stopped = new Promise((resolve) => {
    resolveStopped = resolve;
  });
  let shutdownStarted = 0;

  /** Stops accepting connections and lets in-flight requests finish, for up to SHUTDOWN_GRACE_SECONDS. */
  function shutdown(signal = 'shutdown') {
    if (shutdownStarted) {
      // A second signal: stop waiting for in-flight requests.
      log({ event: 'shutdown_now', signal, inflight });
      server.closeAllConnections();
      return stopped;
    }
    shutdownStarted = Date.now();
    draining = true;
    log({ event: 'shutdown', signal, inflight, graceSeconds: settings.shutdownGraceSeconds });
    // Their answers are out; only the rest of a rejected upload is still arriving.
    for (const req of discarding) req.destroy();
    const timer = setTimeout(() => {
      log({ event: 'shutdown_timeout', inflight });
      server.closeAllConnections();
    }, settings.shutdownGraceSeconds * 1000);
    server.close(() => {
      clearTimeout(timer);
      log({ event: 'stopped', ms: Date.now() - shutdownStarted });
      resolveStopped();
    });
    server.closeIdleConnections();
    return stopped;
  }

  return { ok: true, server, url, shutdown, stopped };
}

/** The Node request as a WHATWG Request for the Worker. Only the path matters to it; Host is not trusted. */
function toWebRequest(req, signal) {
  const headers = new Headers();
  for (let i = 0; i < req.rawHeaders.length; i += 2) {
    const name = req.rawHeaders[i];
    if (!HOP_BY_HOP.has(name.toLowerCase())) headers.append(name, req.rawHeaders[i + 1]);
  }
  let url;
  try {
    url = new URL(req.url ?? '/', 'http://localhost');
  } catch {
    url = new URL('/invalid', 'http://localhost');
  }
  const hasBody = req.method !== 'GET' && req.method !== 'HEAD';
  return new Request(url, {
    method: req.method,
    headers,
    body: hasBody ? bodyStream(req) : undefined,
    duplex: 'half',
    signal,
  });
}

/**
 * The request body as a web stream that reads from the socket only when the Worker reads it (not
 * Readable.toWeb, for two reasons):
 *  - A body the Worker never reads (401, 429, 404, 415, a 413 from Content-Length) stays untouched, so
 *    Node discards it after the answer, as for any plain Node handler.
 *  - Cancelling it (readBodyLimited does on a 413) only stops reading. Readable.toWeb's cancel destroys the
 *    request, and its 'data' handler then throws on the closed stream, which crashed the process.
 * Whatever is left unread is thrown away after the answer (discardUnreadBody).
 */
function bodyStream(req) {
  let waiting = null;
  const events = ['readable', 'end', 'error', 'close'];
  const stopWaiting = () => {
    if (!waiting) return;
    for (const event of events) req.off(event, waiting);
    waiting = null;
  };
  return new ReadableStream(
    {
      pull(controller) {
        return new Promise((resolve) => {
          const step = () => {
            const chunk = req.destroyed ? null : req.read();
            if (chunk !== null) {
              stopWaiting();
              controller.enqueue(new Uint8Array(chunk)); // a copy, detached from Node's buffer pool
            } else if (req.readableEnded) {
              stopWaiting();
              controller.close();
            } else if (req.destroyed) {
              stopWaiting();
              controller.error(req.errored ?? new Error('request body aborted'));
            } else {
              if (!waiting) {
                waiting = step;
                for (const event of events) req.on(event, step);
              }
              return;
            }
            resolve();
          };
          step();
        });
      },
      cancel() {
        stopWaiting();
      },
    },
    { highWaterMark: 0 },
  );
}

async function main() {
  if (Number(process.versions.node.split('.')[0]) < 22) {
    defaultLog({ event: 'config_error', problems: [`Node 22 or newer is required (this is ${process.version})`] });
    process.exit(EXIT_CONFIG);
  }
  const started = await startServer({ env: process.env });
  if (!started.ok) {
    defaultLog({ event: 'config_error', problems: started.problems });
    process.exit(started.problems.some((p) => p.startsWith('cannot listen')) ? 1 : EXIT_CONFIG);
  }
  for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => started.shutdown(signal));
  await started.stopped;
  process.exit(0);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await main();
