# picnic-proxy

An OpenAI-compatible passthrough for Picnic List devices. It runs as a Cloudflare Worker (`https://picnic.cora.earth/v1`) and also on Node, Bun or Deno. Each device gets its own revocable token, and the real OpenAI key stays in a Worker secret.

Full documentation (deploy, tokens, spend limits, `MODEL_MAP`, troubleshooting): [`docs/PROXY.md`](../docs/PROXY.md).

```bash
npm test                                   # node --test (Node 22+, no install needed)
node scripts/mint-token.mjs <device-name>  # new device token + its SHA-256 hash
node scripts/serve.mjs                     # local server on :8787 (reads .dev.vars)
PICNIC_TOKEN=pk_... node scripts/smoke.mjs https://picnic.cora.earth/v1
npm install && npx wrangler deploy         # deploy (see docs/PROXY.md §3 first)
```

| Path | What |
|---|---|
| `src/worker.js` | Fetch handler: routing, policy, forwarding |
| `src/config.js` | Env parsing and validation (fails closed) |
| `src/auth.js` | Device-token hashing, constant-time compare, KV lookup |
| `src/multipart.js` | Strict multipart parser and serializer |
| `src/body.js`, `src/errors.js` | Size-limited body reader; OpenAI-shaped errors |
| `scripts/` | `mint-token.mjs`, `serve.mjs` (Node/Bun/Deno), `smoke.mjs` |
| `test/` | `node:test` suites with a mocked upstream; an OkHttp-made multipart fixture |
| `wrangler.jsonc` | Worker config, with placeholders (`<KV_NAMESPACE_ID>`, the custom domain) |
