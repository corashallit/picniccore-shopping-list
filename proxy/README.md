# picnic-proxy

An OpenAI-compatible passthrough for Picnic List devices at `https://picnic.cora.earth/v1`. It runs self-hosted on Node 22+ (Docker compose or systemd, behind Caddy or nginx) or as a Cloudflare Worker. Each device gets its own revocable token, and the real OpenAI key stays on the proxy's host.

Full documentation (self-hosting, Cloudflare deploy, tokens, spend limits, `MODEL_MAP`, troubleshooting): [`docs/PROXY.md`](../docs/PROXY.md).

```bash
npm test                                   # node --test (Node 22+, no install needed)
node scripts/mint-token.mjs <device-name>  # new device token + its SHA-256 hash
node scripts/serve.mjs                     # development server on :8787 (reads .dev.vars)
sudo docker compose up -d --build          # self-hosted (see docs/PROXY.md §4 first)
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
| `scripts/` | `server.mjs` (production server for self-hosting), `serve.mjs` (development; Node/Bun/Deno), `mint-token.mjs`, `smoke.mjs`, `healthcheck.mjs` |
| `Dockerfile`, `compose.yaml` | Self-hosting with Docker (`.dockerignore` allow-lists what goes in the image) |
| `deploy/` | systemd unit, Caddy and nginx examples, env file template |
| `test/` | `node:test` suites with a mocked upstream; an OkHttp-made multipart fixture |
| `wrangler.jsonc` | Worker config, with placeholders (`<KV_NAMESPACE_ID>`, the custom domain) |
