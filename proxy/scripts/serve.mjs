#!/usr/bin/env node
// Runs the Worker outside Cloudflare: Node 22+, Bun or Deno. For local development; to run the proxy as a
// service (self-hosting), use scripts/server.mjs instead (docs/PROXY.md §4).
//
//   node scripts/serve.mjs                    # http://127.0.0.1:8787/v1
//   PORT=9000 HOST=0.0.0.0 node scripts/serve.mjs
//   bun scripts/serve.mjs
//   deno run --allow-net --allow-env --allow-read scripts/serve.mjs
//
// Configuration comes from proxy/.dev.vars (same format as wrangler) overlaid with the process
// environment. There is no KV or rate-limit binding here: use ACCESS_TOKEN_HASHES for device tokens.
// Put TLS (a reverse proxy) in front of it before exposing it beyond localhost.

import worker from '../src/worker.js';

const DEV_VARS_URL = new URL('../.dev.vars', import.meta.url);
const HOP_BY_HOP = new Set(['connection', 'keep-alive', 'transfer-encoding', 'upgrade', 'proxy-connection', 'te', 'trailer']);

/** Parses wrangler's .dev.vars format (KEY=VALUE lines, # comments, optional quotes). */
export function parseDevVars(text) {
  const vars = {};
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq <= 0) continue;
    const key = line.slice(0, eq).trim();
    let value = line.slice(eq + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    vars[key] = value;
  }
  return vars;
}

async function readDevVars() {
  try {
    const text = globalThis.Deno
      ? await globalThis.Deno.readTextFile(DEV_VARS_URL)
      : await (await import('node:fs/promises')).readFile(DEV_VARS_URL, 'utf8');
    return parseDevVars(text);
  } catch {
    return {};
  }
}

function processEnv() {
  if (globalThis.Deno) return globalThis.Deno.env.toObject();
  return { ...process.env };
}

const ctx = { waitUntil: (p) => Promise.resolve(p).catch(() => {}), passThroughOnException() {} };

/** Starts a Node http server around the Worker. Returns the server (tests close it). */
export async function serveNode({ port, host, env }) {
  const http = await import('node:http');
  const { Readable } = await import('node:stream');
  // The production server's body adapter: reads only what the Worker reads, and survives its cancel.
  const { bodyStream } = await import('./server.mjs');
  const server = http.createServer(async (req, res) => {
    const abort = new AbortController();
    res.on('close', () => {
      if (!res.writableFinished) abort.abort();
    });
    // A body the Worker stopped reading (a 413) is thrown away, so the socket doesn't stay paused.
    res.on('finish', () => {
      if (req.complete || req.destroyed) return;
      req.removeAllListeners('readable');
      req.resume();
    });
    try {
      const headers = new Headers();
      for (let i = 0; i < req.rawHeaders.length; i += 2) {
        const name = req.rawHeaders[i];
        if (!HOP_BY_HOP.has(name.toLowerCase())) headers.append(name, req.rawHeaders[i + 1]);
      }
      const hasBody = req.method !== 'GET' && req.method !== 'HEAD';
      const request = new Request(new URL(req.url ?? '/', `http://${host}:${port}`), {
        method: req.method,
        headers,
        body: hasBody ? bodyStream(req) : undefined,
        duplex: 'half',
        signal: abort.signal,
      });
      const response = await worker.fetch(request, env, ctx);
      const outHeaders = {};
      response.headers.forEach((value, name) => {
        outHeaders[name] = value;
      });
      if (response.statusText) res.writeHead(response.status, response.statusText, outHeaders);
      else res.writeHead(response.status, outHeaders);
      res.flushHeaders();
      if (!response.body || req.method === 'HEAD') {
        res.end();
        return;
      }
      const body = Readable.fromWeb(response.body);
      body.on('error', () => res.destroy());
      body.pipe(res);
    } catch {
      if (!res.headersSent) res.writeHead(500, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ error: { message: 'Internal proxy error', type: 'server_error', param: null, code: 'proxy_error' } }));
    }
  });
  // Long reasoning + web search calls can take minutes before the first byte.
  server.requestTimeout = 0;
  server.headersTimeout = 60_000;
  await new Promise((resolve) => server.listen(port, host, resolve));
  return server;
}

async function main() {
  const env = { ...(await readDevVars()), ...processEnv() };
  const port = Number(env.PORT ?? 8787);
  const host = env.HOST ?? '127.0.0.1';
  const fetch = (request) => worker.fetch(request, env, ctx);

  if (globalThis.Deno) {
    globalThis.Deno.serve({ port, hostname: host }, fetch);
  } else if (globalThis.Bun) {
    // Bun's default idleTimeout (10 s) would cut off slow reasoning calls; 255 s is its maximum.
    globalThis.Bun.serve({ port, hostname: host, fetch, idleTimeout: 255 });
  } else {
    await serveNode({ port, host, env });
  }
  console.log(JSON.stringify({ event: 'listening', url: `http://${host}:${port}/v1` }));
}

const isMain = globalThis.Deno
  ? import.meta.main
  : process.argv[1] && import.meta.url === (await import('node:url')).pathToFileURL(process.argv[1]).href;
if (isMain) await main();
