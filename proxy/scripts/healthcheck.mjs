#!/usr/bin/env node
// Exits 0 when the proxy on this machine answers GET /healthz with 200 (running, configuration valid),
// otherwise 1. The Docker image's HEALTHCHECK runs it; it also suits a monitoring check on the host:
//
//   node scripts/healthcheck.mjs              # http://127.0.0.1:$PORT/healthz (PORT defaults to 8787)
//
// HOST is used when it names a specific address; a wildcard (0.0.0.0, ::) is checked through loopback.

const env = process.env;
const port = env.PORT && env.PORT.trim() !== '' ? env.PORT.trim() : '8787';
const host = !env.HOST || ['0.0.0.0', '::', ''].includes(env.HOST.trim()) ? '127.0.0.1' : env.HOST.trim();
const url = `http://${host.includes(':') ? `[${host}]` : host}:${port}/healthz`;

try {
  const response = await fetch(url, { signal: AbortSignal.timeout(4000) });
  await response.body?.cancel();
  if (!response.ok) console.error(`healthz: HTTP ${response.status}`);
  process.exit(response.ok ? 0 : 1);
} catch (err) {
  console.error(`healthz: ${err?.cause?.code ?? err?.name ?? 'error'}`);
  process.exit(1);
}
