# Picnic List proxy

Cora's proxy lets friends use Picnic List without an OpenAI account. It answers at `https://picnic.cora.earth/v1` and looks exactly like the OpenAI API to the app. It runs either on Cora's own server, as a small Node service behind Caddy or nginx (§4), or as a Cloudflare Worker (§3). Each device gets its own revocable token, and the real OpenAI key never leaves the proxy's host.

- Code: `proxy/` (plain modern JavaScript, no build step; Node 22+ for tests and tooling).
- Contract with the app: `docs/ARCHITECTURE.md` §16.
- Runs in three places:
  - self-hosted on Node 22+ (`proxy/scripts/server.mjs`, with Docker or systemd; §4);
  - on Cloudflare Workers (§3);
  - for local development, on Node, Bun or Deno (`proxy/scripts/serve.mjs`; §12).

```
Picnic List app ──HTTPS── Bearer pk_<device token> ──► picnic.cora.earth/v1 (the proxy)
   check the token (SHA-256 hash in KV or ACCESS_TOKEN_HASHES) · rate limit · size limits
   MODEL_MAP, then ALLOWED_MODELS · store:false · output cap · tool allow-list
                    ──HTTPS── Bearer sk-proj-<real key> ──► api.openai.com/v1
   ◄── status + body passed through unchanged (streamed); only safe headers are copied
```

---

## 1. Why a proxy, and why a key must never be baked into an APK

An APK is a zip file. Anyone who has it can unpack it with `apktool` or `jadx` in a minute, and every string in it, including an "obfuscated" or "encrypted" key and the code that decrypts it, comes back out. A key shipped in an APK leaks the moment one copy of the APK is shared. Then:

- **Unlimited spend.** The holder can call every model on the account at any volume, including models far more expensive than the app uses.
- **Access to project data.** Depending on the key's permissions, that can include files, vector stores, stored responses and fine-tunes.
- **No clean revocation.** Rotating the key breaks every installed copy until a new APK reaches every device.
- **Play policy.** Shipping credentials in an app is exactly what Play's security review flags.

The proxy turns this around. Each device holds a **device token** that only works against this proxy, for four routes and four models, under a rate limit, and can be revoked on its own in seconds. The real key lives only on the proxy's host: in an env file that only root can read on Cora's server, or in a Cloudflare secret. Rotating it takes a minute and changes nothing on the devices.

The repo and the APK contain **no** secrets. The app stores whatever the user pastes (a device token or their own key) encrypted with an Android Keystore key (`docs/ARCHITECTURE.md` §9).

---

## 2. What the proxy does

### 2.1 Routes

Everything else gets **404** in OpenAI's error shape. Query strings are ignored and are never forwarded.

| Route | Body | Forwarded to | Size limit (default) |
|---|---|---|---|
| `POST /v1/responses` | JSON (`stream` true or false) | `/responses` | 15 MB (`MAX_JSON_MB`) |
| `POST /v1/audio/transcriptions` | multipart | `/audio/transcriptions` | 26 MB (`MAX_TRANSCRIPTION_MB`) |
| `POST /v1/images/edits` | multipart or JSON | `/images/edits` | 20 MB (`MAX_IMAGE_EDIT_MB`) |
| `GET /v1/models/{id}` | none | `/models/{mapped id}` | |
| `GET /healthz` | none, no auth | not forwarded; `{"ok":true}`, or **500** `{"ok":false}` when the config is broken | |

The app's "Test connection" button calls `GET /v1/models/gpt-5.6-terra`. That call is free, and it checks the URL, the token and model access in one go.

### 2.2 Checks, in order

1. **Route.** Unknown route → 404.
2. **Config.** Any malformed setting (bad `MODEL_MAP`, bad hash list, no key, no tokens configured) → **500** `proxy_misconfigured` for every request. The reason is logged, never the values. A self-hosted proxy refuses to start instead (§4.5).
3. **Device token.** `Authorization: Bearer <token>`. The proxy hashes the token with SHA-256 and compares the hash in constant time against `ACCESS_TOKEN_HASHES`, then looks it up in the `TOKENS` KV namespace. Unknown, malformed or disabled → **401** `invalid_api_key`.
4. **Rate limit** (per device: the optional Workers binding, or `RATE_LIMIT_PER_MINUTE` when self-hosted) → **429** `rate_limit_exceeded` with `Retry-After`.
5. **Body.**
   - The content type must fit the route; otherwise **415**.
   - The size must be within the route's limit; otherwise **413**. A declared `Content-Length` is checked before reading, and a streamed body is cut off as soon as it passes the limit.
   - The body must parse; otherwise **400** (`invalid_json`, `invalid_multipart`).
6. **Model.** There must be exactly one `model` field: a JSON key, a multipart form field, or the `{id}` in the path. `MODEL_MAP` is applied first. The resulting **upstream** id must then be in `ALLOWED_MODELS`; otherwise **403** `model_not_allowed`. The app maps this to "This key or proxy can't use the AI model the app needs."
7. **Policy** (`/responses` only):
   - `store` is forced to `false`.
   - `service_tier` is removed, so nobody can opt into the 2× priority tier.
   - `max_output_tokens` is capped at `MAX_OUTPUT_TOKENS` (16000), and set to the cap when it is missing.
   - Every entry in `tools` must have a type listed in `ALLOWED_TOOLS` (default `web_search`); otherwise **400** `tool_not_allowed`. Hosted tools such as `image_generation`, `code_interpreter`, `file_search` and `mcp` carry their own model or cost, so they would otherwise bypass the model allow-list.
   - `safety_identifier` is set to `picnic-<first 16 hex chars of the token hash>` when the app didn't send one, so OpenAI can attribute abuse signals to a device.
   - On `/images/edits` (the cost policy for live icon drawing; the app always sends `quality=low`, `size=1024x1024`, one reference image and `n=1`):
     - `n` must not exceed `MAX_IMAGES_PER_REQUEST` (1);
     - `quality` must be in `IMAGE_ALLOWED_QUALITIES` (`low,medium`) and `size` in `IMAGE_ALLOWED_SIZES` (`1024x1024,auto`); a request without one gets the list's first value added, unless the list allows `auto` (the API's own default);
     - at most `MAX_IMAGE_INPUTS` (2) reference images (`image[]`/`image` form parts, or the JSON `images` array).

     Anything else is **400** `invalid_value` with `param` set. Without these checks a leaked device token could ask for high-quality, large drawings with many references, many times the app's ~1.5¢ per icon.
8. **Forward** with `Authorization: Bearer <OPENAI_API_KEY>`.
   - Only `content-type` and `accept` are copied from the client. Its `Authorization`, cookies, `OpenAI-Organization`/`OpenAI-Project` and everything else are dropped.
   - Redirects are not followed.
   - Waiting for response headers times out after 180 s (`UPSTREAM_TIMEOUT_SECONDS`) → **504**. A streamed body may take longer. A network failure → **502** `upstream_unreachable`.
9. **Respond.** The upstream status and body are passed through **unchanged and streamed** (SSE included), including OpenAI's own errors (400s, 404 `model_not_found`, 429 quota/spend-limit, 5xx), so the app classifies them exactly as it would against api.openai.com.
   - **Headers:** only `content-type`, `cache-control`, `x-request-id`, `openai-processing-ms`, `openai-version`, `retry-after`, `retry-after-ms` and `x-ratelimit-*` are copied back. This drops the organization and project ids, cookies and CDN headers.
   - **Upstream 401:** the one exception. A 401 from OpenAI means the proxy's own key is wrong, not the device's token, and OpenAI's message echoes a masked copy of the real key. It becomes **502** `upstream_auth_failed`.

### 2.3 Multipart handling

The transcription and image routes carry the model inside a multipart form. The proxy parses the form with a deliberately strict parser and re-serializes it, so that **the bytes OpenAI receives are exactly what the proxy checked**:

- **Every part other than `model` is byte-identical**, with headers and filenames unchanged. This matters: the transcription endpoint chooses the audio decoder from the file extension (`recording.m4a`).
- **Only a remapped `model` value changes.**
- **Ambiguous forms are rejected with 400** instead of being guessed at, because a different parser upstream might read them differently: bare-LF line endings, duplicate or case-variant `model` fields, a `model` part with a filename, folded headers, `name*=` parameters, backslash escapes, or a per-part `Content-Length` that doesn't match.
- **Tested formats:** OkHttp 5 (the app), curl `-F`, and browser/undici `FormData`.

JSON bodies are re-serialized after checking for the same reason. Duplicate keys collapse to the value that was checked.

---

## 3. Deploy on Cloudflare (`picnic.cora.earth`)

This is the alternative to self-hosting (§4). Do the OpenAI side (§3.1) either way.

**Prerequisites:**

- A Cloudflare account, with the `cora.earth` zone active on Cloudflare (its nameservers point to Cloudflare). Custom Domains need the zone on Cloudflare.
- Node 22+ on your machine.

### 3.1 OpenAI side (do this first, for either host)

1. In the OpenAI dashboard, create a **dedicated project**, e.g. "Picnic List". Everything the proxy spends is then billed, limited and visible separately.
2. **Set the real spend cap.** Go to Project settings → **Limits** → **Spend** → *Edit spend limit*, enter a monthly amount (e.g. US$10) and turn on **Enforce a hard limit**.
   - When the limit is reached, OpenAI answers 429 `project_spend_limit_exceeded`. The proxy passes it through, and the app shows its quota message.
   - Add spend alerts below the limit (e.g. 50 % and 80 %).
   - Enforcement isn't instantaneous, so recorded spend can slightly exceed the limit.
   - For scale: one active user costs about US$1.40 per month at the current models (`research/openai_api.md` §0).
3. **Optional hardening in the same project:**
   - Restrict the project's allowed models to the four in §6.
   - Allow only the default service tier.
   - Set an **expiry** on the key and rotate it before it expires (§5.4).
4. **Create a project API key**, preferably a service-account key with **restricted** permissions. The permission names are as of 2026-10; check them when you create the key:
   - List models: Read
   - Model capabilities: Request
   - Responses API: Write
   - Everything else: None.

   Keep the key only long enough to paste it into step 3.2.4 (Cloudflare) or the env file (§4.3).

### 3.2 Cloudflare side

From `proxy/`:

1. **Install and log in.**

   ```bash
   cd proxy
   npm install                     # wrangler (dev dependency only; the Worker has no dependencies)
   npx wrangler login
   ```

2. **Create the token store.**

   ```bash
   npx wrangler kv namespace create TOKENS
   ```

   Paste the printed `id` into `wrangler.jsonc` in place of `<KV_NAMESPACE_ID>`. If you'd rather not use KV, delete the `kv_namespaces` block and use `ACCESS_TOKEN_HASHES` instead (§5.3).

3. **Check `wrangler.jsonc`.**
   - `routes` holds `picnic.cora.earth` with `custom_domain: true`.
   - `vars.ALLOWED_MODELS` lists the four app models.
   - `ratelimits` has a `namespace_id` that is unique in your account (any integer).
   - Observability is on.

4. **Store the real key as a secret.** It is entered at the prompt and is never written to a file:

   ```bash
   npx wrangler secret put OPENAI_API_KEY
   ```

5. **Deploy.**

   ```bash
   npx wrangler deploy
   ```

   On the first deploy, Cloudflare creates the `picnic.cora.earth` DNS record and its certificate. Give the certificate a minute or two.

6. **Verify.**

   ```bash
   curl https://picnic.cora.earth/healthz          # {"ok":true}
   node scripts/mint-token.mjs smoke-test          # §5.1; register it as printed
   PICNIC_TOKEN=pk_... node scripts/smoke.mjs https://picnic.cora.earth/v1
   ```

   `smoke.mjs` checks `healthz`, `GET /models/gpt-5.6-terra` (free) and one tiny `gpt-6-luna` Responses call, costing well under US$0.001. Add `--audio path/to/recording.m4a` to also try a transcription. Revoke the smoke-test token afterwards (§5.2).

**Plan limits.**

- The **Workers Free** plan allows 100,000 requests a day and 10 ms of CPU per request. The proxy's own CPU per request, measured with the real code:

  | Request | CPU |
  |---|---|
  | Text ingest | about 1 ms |
  | Photo ingest (450 KB JPEG as a data URL) | about 5 ms |
  | Icon drawing (330 KB reference) | about 1 ms |
  | 5-minute voice note (1.2 MB) | about 2 ms |

  The app's traffic therefore fits the Free plan. A near-limit 25 MB upload needs about 40 ms of CPU, so if you expect those, use **Workers Paid** (5 min CPU) and optionally raise `limits.cpu_ms`.
- Waiting on OpenAI doesn't count as CPU time.
- Cloudflare's edge accepts request bodies of at least 100 MB on every plan.
- Every config change is a `npx wrangler deploy`, or a `wrangler secret put`, which deploys immediately.

---

## 4. Self-hosting on cora.earth

The same proxy code also runs as a plain Node service on Cora's own server. The app can't tell the difference: the base URL stays `https://picnic.cora.earth/v1`, and device tokens keep working.

```
Picnic List app ──HTTPS──► Caddy or nginx on the server (TLS on :443; passes on only /v1/* and /healthz)
                 ──HTTP───► 127.0.0.1:8787  node scripts/server.mjs   (Docker compose or systemd)
                 ──HTTPS──► api.openai.com/v1
```

How it differs from the Worker:

- **Entry point:** `proxy/scripts/server.mjs`, the production server (Node 22+).
  - It reads its settings only from its environment.
  - It refuses to start with a broken configuration (exit code 78, the problems logged).
  - On `SIGTERM` it lets in-flight requests finish.
  - Its timeouts are sized for slow reasoning calls.

  `scripts/serve.mjs` remains the development server (§12).
- **Device tokens** live in `ACCESS_TOKEN_HASHES` in an env file that only root can read. There is no KV: a change means editing the file and restarting, and it takes effect at once.
- **Rate limit:** `RATE_LIMIT_PER_MINUTE` per device (default 60), kept in memory. It is exact, because there is only one process, but a restart resets it.
- **Logs:** the same JSON lines, in `docker compose logs` or `journalctl -u picnic-proxy`.
- **Files:**
  - `proxy/Dockerfile` and `proxy/compose.yaml`;
  - in `proxy/deploy/`: `picnic-proxy.service` (systemd), `Caddyfile`, `nginx-picnic.conf` and `picnic-proxy.env.example`.

### 4.1 What you need

- **A Linux server** with a public address and ports 80 and 443 open.
- **A DNS record** for `picnic.cora.earth` (A, plus AAAA if the server has IPv6) pointing at the server.
  - If `cora.earth` uses Cloudflare DNS, set the record to **DNS only** (grey cloud).
  - A proxied (orange-cloud) record cuts every request off after 100 seconds (Cloudflare error 524), and a slow reasoning call can take longer than that. It also gets in the way of Caddy's certificate.
- **Docker or Node:** either Docker Engine with the compose plugin (recommended), or Node 22+ at `/usr/bin/node` for systemd.
  - Debian 12 and Ubuntu 24.04 ship an older Node, so install 22 from NodeSource, for example.
  - Node 22 is supported until April 2027 (§4.9).
- **A TLS front end:** Caddy (recommended, because it gets its certificate itself) or nginx with certbot.
- **The OpenAI side from §3.1:** a dedicated project, a **hard monthly spend limit**, and a restricted project key. Hosting the proxy yourself changes nothing there: the project's spend limit is still the real cap.

### 4.2 Get the code

Only `proxy/` is needed. A shallow, sparse clone leaves out the Android app:

```bash
sudo git clone --depth 1 --filter=blob:none --sparse \
  https://github.com/corashallit/picniccore-shopping-list.git /opt/picnic-proxy
sudo git -C /opt/picnic-proxy sparse-checkout set proxy
cd /opt/picnic-proxy/proxy
```

- There is nothing to install. The proxy has no runtime dependencies (`npm install` only fetches wrangler, which is for Cloudflare).
- The checkout stays owned by root, and the service only reads it.
- If the repository is private, clone it with a read-only deploy key. To run a branch other than the default, add `--branch <name>`.

### 4.3 Device tokens and the env file

All settings, including the OpenAI key, go in one env file **outside** the checkout, readable only by root.

- systemd and Docker compose both read the file as root before they start the proxy, so the proxy's own user can't read it.
- Because it is outside the checkout, `git pull` never touches it.

1. **Create the file** from the template:

   ```bash
   sudo install -d -m 0700 /etc/picnic-proxy
   sudo install -m 0600 deploy/picnic-proxy.env.example /etc/picnic-proxy/picnic-proxy.env
   ```

2. **Mint one token per device** (§5 explains tokens). With Node on the host:

   ```bash
   node scripts/mint-token.mjs sams-phone
   ```

   Without Node, build the image and run the script inside it:

   ```bash
   sudo docker compose build
   sudo docker run --rm picnic-proxy:local node scripts/mint-token.mjs sams-phone
   ```

   - Each run prints a token once. The token goes to the device; don't keep a copy.
   - It also prints a `sams-phone:<sha256 hex>` entry. The entry goes in the env file.
   - Also mint a `smoke-test` token, for §4.7.

3. **Fill in the file** with `sudoedit /etc/picnic-proxy/picnic-proxy.env`, so the key never lands in your shell history:

   ```bash
   OPENAI_API_KEY=sk-proj-…
   ACCESS_TOKEN_HASHES=sams-phone:3f2a…,kitchen-tablet:9b1c…,smoke-test:77de…
   ```

   - Write one `KEY=value` per line, with no spaces around `=`. A line starting with `#` is a comment.
   - Put a value that contains quotes or braces in single quotes, for example `MODEL_MAP='{"gpt-5.6-terra":"gpt-6.2-terra"}'`. systemd, Docker compose and `node --env-file` all read it the same way.
   - Any setting from §9 can go here. Leave out `HOST` and `PORT`: the systemd unit and `compose.yaml` already set them.

### 4.4 Run it with Docker compose

```bash
cd /opt/picnic-proxy/proxy
sudo docker compose up -d --build
sudo docker compose ps                     # STATUS shows "(healthy)" within about 30 s
curl http://127.0.0.1:8787/healthz         # {"ok":true}
sudo docker compose logs -f                # one JSON line per request
```

`compose.yaml` publishes the port on `127.0.0.1` only, and runs the container:

- as uid 1000, with a read-only root filesystem, no Linux capabilities and `no-new-privileges`;
- with at most 256 MB of memory and 64 processes;
- with `restart: unless-stopped`, so it comes back after a crash or a reboot;
- with 130 s to drain when it stops, so an update doesn't cut off a slow reasoning call;
- with rotated logs (5 files of 10 MB).

The image is the official `node:22-alpine` image, pinned by digest, plus the proxy's few files. Nothing is installed in it. Its `HEALTHCHECK` calls `/healthz`.

### 4.5 Or run it with systemd, without Docker

```bash
node --version                             # v22 or newer, at /usr/bin/node
sudo install -m 0644 deploy/picnic-proxy.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now picnic-proxy
systemctl status picnic-proxy
curl http://127.0.0.1:8787/healthz         # {"ok":true}
journalctl -u picnic-proxy -f
```

The unit runs `node scripts/server.mjs` from `/opt/picnic-proxy/proxy`. For other paths, adjust `WorkingDirectory=` and `ExecStart=`. It runs the proxy:

- as a throwaway unprivileged user (`DynamicUser=yes`) with no capabilities;
- with a read-only system (`ProtectSystem=strict`), no access to `/home`, and a private `/tmp` and `/dev`;
- with IP and local sockets only, and a system-call filter;
- with `MemoryMax=256M`, `Restart=on-failure`, and 130 s to drain when it stops.

`systemd-analyze security picnic-proxy` rates the unit 1.2 ("OK"). What's left is inherent to a network service running V8.

**A broken configuration**, such as a missing key, a malformed hash, bad `MODEL_MAP` JSON or a placeholder left over from the template, stops the proxy at startup, with either Docker or systemd. It exits with code 78 and writes one log line that names the problems but never the values:

```json
{"event":"config_error","problems":["MODEL_MAP is not valid JSON"]}
```

- systemd then stops trying (`RestartPreventExitStatus=78`).
- Docker keeps restarting it, so look at `docker compose logs`.

### 4.6 Put Caddy or nginx in front

The proxy speaks plain HTTP on loopback. The reverse proxy adds TLS and passes on only `/v1/*` and `/healthz`.

- **Caddy** (recommended, because it gets and renews the certificate itself):
  1. Copy `deploy/Caddyfile` to `/etc/caddy/Caddyfile`, or paste its two blocks into your existing one.
  2. Run `sudo systemctl reload caddy`.
- **nginx:**
  1. Get a certificate: `sudo certbot certonly --webroot -w /var/www/html -d picnic.cora.earth`.
  2. Copy `deploy/nginx-picnic.conf` to `/etc/nginx/conf.d/`.
  3. Run `sudo nginx -t && sudo systemctl reload nginx`.

Both examples:

- **Stream in both directions.** SSE answers pass through event by event, with no response buffering. Uploads go straight to the proxy, so nginx doesn't write photos or audio to its temporary files.
- **Cap request bodies at 27 MiB,** just above the proxy's largest route limit (26 MiB). The proxy then rejects oversized uploads itself, with the OpenAI-shaped 413 the app understands.
- **Keep idle upstream connections for 60 s.** That is shorter than the proxy's own 75 s, so they never reuse a connection that Node is closing.
- **Never cut off a slow call.** Each layer waits at least as long as the one in front of it:

| Layer | Limit | Setting |
|---|---|---|
| App, per call | 120 s for ingest and icon drawing, 60 s for transcription, 45 s for the quick calls, 20 s for Test connection | `LlmTimeouts` in core; the app gives up first |
| Proxy → OpenAI | 180 s to the response headers, then 504 | `UPSTREAM_TIMEOUT_SECONDS` |
| Proxy socket | 300 s with no data | `IDLE_TIMEOUT_SECONDS` |
| Caddy or nginx → proxy | 300 s with no data | `read_timeout` / `proxy_read_timeout` |
| Shutdown drain | 120 s; compose and systemd wait 130 s | `SHUTDOWN_GRACE_SECONDS` |

**A path instead of a subdomain.** Both files include a commented-out variant that serves the proxy at `https://cora.earth/picnic/v1`. The subdomain is simpler, though:

- the URL matches the Cloudflare one;
- `smoke.mjs` checks `/healthz` at the root of the host.

### 4.7 Test it

```bash
curl https://picnic.cora.earth/healthz          # {"ok":true}
read -rs PICNIC_TOKEN && export PICNIC_TOKEN    # paste the smoke-test token (it isn't echoed)
node scripts/smoke.mjs https://picnic.cora.earth/v1
```

Without Node on the host, run the same script in the container:

```bash
sudo --preserve-env=PICNIC_TOKEN docker compose exec -e PICNIC_TOKEN picnic-proxy \
  node scripts/smoke.mjs https://picnic.cora.earth/v1
```

`smoke.mjs` checks three things:

- `healthz`;
- `GET /models/gpt-5.6-terra`, which is free;
- one tiny `gpt-6-luna` call, which costs well under US$0.001.

With Node on the host, `--audio recording.m4a` also tries a transcription.

Then test it from the app:

1. Open Settings → Connection.
2. Set the base URL to `https://picnic.cora.earth/v1` and paste a device token.
3. Tap **Save**, then **Test connection**.

Finally, remove the `smoke-test` entry (§4.8).

### 4.8 Tokens, the OpenAI key and `MODEL_MAP`

Every change is an edit of `/etc/picnic-proxy/picnic-proxy.env` (with `sudoedit`), followed by a restart:

```bash
sudo docker compose up -d --force-recreate     # Docker, from /opt/picnic-proxy/proxy ("restart" keeps the old settings)
sudo systemctl restart picnic-proxy            # systemd
```

Restarts are graceful: the old process finishes its in-flight requests first, for up to 120 s. A request that arrives in the second or two between the old process and the new one gets a 502 from Caddy or nginx, and the app retries it.

- **Add a device:** mint a token (§4.3), add its entry to `ACCESS_TOKEN_HASHES`, and restart.
- **Revoke a device:** delete its entry and restart. The next request with that token gets 401. To disable a device for a while, turn its entry into a `#` comment line instead.
- **Replace a device's token** (a lost phone or a leaked token): mint a new one under the same name, replace the entry, restart, and hand out the new token.
- **Find a token's entry:** `node scripts/mint-token.mjs --hash < token.txt` prints the token's hash. Every log line also names the device (`"token":"sams-phone"`).
- **Rotate the OpenAI key:** no device notices this.
  1. Create a new project key (§3.1).
  2. Replace `OPENAI_API_KEY` and restart.
  3. Run `smoke.mjs`.
  4. Revoke the old key in the OpenAI dashboard.
- **Upgrade a model for every installed app** (§7):
  1. Add the target to `ALLOWED_MODELS` and the mapping to `MODEL_MAP`:

     ```bash
     MODEL_MAP='{"gpt-5.6-terra":"gpt-6.2-terra"}'
     ALLOWED_MODELS=gpt-6.2-terra,gpt-6-luna,gpt-transcribe,gpt-image-2.5-flare
     ```

  2. Restart, then run the checks in §7.2.

  To roll back, remove both lines and restart.
- **Watch the spend:** the OpenAI project's **hard monthly spend limit** (§3.1 step 2) is the only real cap. `RATE_LIMIT_PER_MINUTE` and the model allow-list are brakes (§6). To count one device's requests today:

  ```bash
  journalctl -u picnic-proxy --since today | grep '"event":"proxy"' | grep -c '"token":"sams-phone"'
  ```

  With Docker, use `sudo docker compose logs --since 24h` in place of `journalctl`.

### 4.9 Updating

```bash
sudo git -C /opt/picnic-proxy pull --ff-only
cd /opt/picnic-proxy/proxy
sudo docker compose up -d --build              # Docker
sudo systemctl restart picnic-proxy            # systemd
curl https://picnic.cora.earth/healthz
```

- **Changed config files:** if `deploy/picnic-proxy.service` changed, copy it again and run `sudo systemctl daemon-reload` before the restart. Do the same for your Caddy or nginx file.
- **Tests:** `npm test` (Node 22+) runs the proxy's tests on the server too, with nothing to install.
- **Node updates:** the `Dockerfile` pins Node by digest, so a rebuild never changes Node by surprise.
  - To take a Node security release, update the tag and digest on its `FROM` line (the comment above it says how). Dependabot's `docker` ecosystem can open those pull requests for you.
  - With systemd, Node updates with the OS packages.
- **Node 22 end of life (April 2027):** move to the next LTS before then. Change the `FROM` line or the system package, run `npm test`, and restart.

### 4.10 Moving between Cloudflare and self-hosting

Both hosts read the same token hashes, so devices keep their tokens and their base URL.

1. **Copy the tokens.** List the Worker's tokens with `npx wrangler kv key list --binding TOKENS --remote`; the keys are the hashes. `npx wrangler kv key get --binding TOKENS --remote <hash>` shows a token's name. Add each one to `ACCESS_TOKEN_HASHES` as `name:hash`.
2. **Test on the server.** Start the self-hosted proxy and check it on the server itself: `curl http://127.0.0.1:8787/healthz`, then `smoke.mjs` against `http://127.0.0.1:8787/v1`.
3. **Switch the domain.**
   1. Remove the Worker's custom domain (Workers → picnic-proxy → Settings → Domains & Routes).
   2. Create the DNS-only record (§4.1).
   3. Reload Caddy or nginx so it gets the certificate.
4. **Test the public URL.** Run `smoke.mjs` against `https://picnic.cora.earth/v1`.

Going back works the same way in reverse. Either way, give the new host a fresh OpenAI key, and revoke the old one once the switch is done.

---

## 5. Device tokens

A device token looks like `pk_` followed by 43 base64url characters (256 random bits). The proxy only ever stores `sha256(token)` as hex.

The commands below are for the Cloudflare Worker. When self-hosting, tokens live in the env file instead (§4.3, §4.8).

### 5.1 Mint and hand out

```bash
node scripts/mint-token.mjs sams-phone
```

This prints the token **once**, its hash, and the two ways to register it. Nothing is written to disk.

1. Register the hash. KV is recommended:

   ```bash
   npx wrangler kv key put --binding TOKENS --remote <hash> '{"name":"sams-phone","created":"2026-10-01T…"}'
   ```

   The `name` appears in the logs.
2. Send the token to the device owner over a private channel. In the app they open Settings → Connection, set the base URL to `https://picnic.cora.earth/v1`, paste the token as the access token, tap **Save** and then **Test connection**.
3. Forget the token. If it's lost, mint a new one.

`node scripts/mint-token.mjs --hash < token.txt` prints the hash of an existing token, for example to find its KV entry.

### 5.2 Revoke or disable

- **Disable (reversible):**

  ```bash
  npx wrangler kv key put --binding TOKENS --remote <hash> '{"name":"sams-phone","disabled":true}'
  ```

- **Delete:**

  ```bash
  npx wrangler kv key delete --binding TOKENS --remote <hash>
  ```

KV is eventually consistent, so a change can take up to about a minute to reach every Cloudflare location. Revocation does not depend on the device: the next request with that token gets 401.

### 5.3 Without KV: `ACCESS_TOKEN_HASHES`

A Worker secret holding comma-separated entries. Each entry is `<name>:<sha256 hex>` or just the hex hash. Example: `sams-phone:3f2a…,kitchen-tablet:9b1c…`.

```bash
npx wrangler secret put ACCESS_TOKEN_HASHES     # paste the whole list
```

- Secrets can't be read back, so keep the list (hashes only, never tokens) somewhere safe, and re-enter the whole list to add or remove a device.
- Entries are compared in constant time.
- A malformed entry fails the whole proxy closed (500), so a typo is noticed at once: run `curl …/healthz` after each change.
- A secret holds at most 5 KB, which is roughly 50 devices. Use KV beyond a handful.

Both methods can be used together. The hash list is checked first, then KV.

### 5.4 Rotating the real OpenAI key

No device notices this.

1. Create a new project key (§3.1).
2. Run `npx wrangler secret put OPENAI_API_KEY`.
3. Check with `healthz` and `smoke.mjs`.
4. Revoke the old key in the OpenAI dashboard.

If the proxy is ever deployed with a revoked or wrong key, the app gets 502 `upstream_auth_failed` and the log line `upstream_auth_failed` appears.

---

## 6. Spend control

| Layer | What it limits | Where |
|---|---|---|
| **OpenAI project hard spend limit** | Total monthly spend. **This is the real cap**; everything else is a brake. | OpenAI dashboard (§3.1) |
| Model allow-list | Only `gpt-5.6-terra`, `gpt-6-luna`, `gpt-transcribe`, `gpt-image-2.5-flare` | `ALLOWED_MODELS` |
| Per-call caps | `max_output_tokens` ≤ 16000; no priority tier; only the `web_search` tool; one image per edit | proxy policy (§2.2) |
| Per-device rate limit | 60 requests a minute per device (default) | `ratelimits` binding (Cloudflare) or `RATE_LIMIT_PER_MINUTE` (self-hosted) |
| Revocation | Cuts off one device | §5.2 |

On Cloudflare, the rate limit uses the Workers Rate Limiting binding:

- It counts **per Cloudflare location** and is eventually consistent, so treat it as a brake against a runaway loop or a leaked token, not as accounting.
- `period` must be 10 or 60 seconds.
- If the binding itself errors, the proxy **fails open** and logs `rate_limiter_error`, because the spend limit still holds.
- When self-hosted, it is a fixed one-minute window per device in the proxy's memory. That is exact, because there is one process, but a restart resets it. `RATE_LIMIT_PER_MINUTE=0` turns it off.
- Normal app use is far below 60 a minute. A bulk add of 40 typed items triggers 1 icon-decider call and at most about 12 drawings a minute.

Monthly spend per device shows in OpenAI's usage page, grouped by project. Requests per device show in the proxy's logs (`token` field).

---

## 7. `MODEL_MAP`: upgrading models without shipping an APK

The app's models are fixed in core's `ModelDefaults` (`gpt-5.6-terra`, `gpt-6-luna`, `gpt-transcribe`, `gpt-image-2.5-flare`). Users can't change them, by design (client amendment A1). `MODEL_MAP` lets the maintainers swap what those ids mean, for every installed APK at once.

```jsonc
// wrangler.jsonc → vars (preferred: versioned and reviewable)
"MODEL_MAP": { "gpt-5.6-terra": "gpt-6.2-terra" },
"ALLOWED_MODELS": "gpt-6.2-terra,gpt-6-luna,gpt-transcribe,gpt-image-2.5-flare"
```

`MODEL_MAP` can also be a secret holding the same JSON as a string: `npx wrangler secret put MODEL_MAP`. When self-hosting, it goes in the env file, in single quotes (§4.8).

### 7.1 Rules (ARCHITECTURE §16.2)

- **Where it applies:**
  - the `model` key of JSON bodies (`/responses`, JSON `/images/edits`);
  - the `model` form field of multipart bodies (`/audio/transcriptions`, multipart `/images/edits`), where every other part stays byte-identical;
  - the `{id}` of `GET /models/{id}`.
- **Map first, then check.** `ALLOWED_MODELS` holds **upstream** ids, so it must contain each map target. A requested id that is mapped is not itself required to be in the list.
- **One step, exact ids.** `a→b` plus `b→c` does not turn `a` into `c`. Keys and values are case-sensitive model ids.
- **Unmapped ids pass through unchanged** and are checked as they are.
- **Fail closed.** A value that isn't a JSON object of model-id strings gives **500** `proxy_misconfigured` on every request, and `healthz` reports `{"ok":false}`.
- **Responses are not rewritten.** The app sees the real upstream `model` in each response (it only logs it).

### 7.2 Rolling out a model change

1. Check that the target accepts the app's request parameters. The app picks parameters for the id it *sends*:
   - **Explicit prompt caching** is sent for `gpt-5.6*`/`gpt-6*` ids; gpt-5.5/5.4 reject it.
   - **Reasoning effort:** `none` on the fast model, `low` on the smart one.

   If the target rejects one of these, the app's one-shot downgrade retry absorbs the 400 at the cost of one extra round trip. Prefer targets in the same family.
2. Add the target to `ALLOWED_MODELS`, add the `MODEL_MAP` entry, and deploy: run `npx wrangler deploy`, or, when self-hosting, edit the env file and restart.
3. Run the app's own requests through the proxy:

   ```bash
   cd .. && PICNIC_BASE_URL=https://picnic.cora.earth/v1 OPENAI_API_KEY=pk_<a device token> \
     ./gradlew -p core run --args="test"
   ```

   `ingest "…"`, `transcribe <m4a>`, `icons "…"` and `draw "…" out.webp` work the same way. Also run `node scripts/smoke.mjs`.
4. To roll back, remove the entry and deploy (or restart) again.

Verified live (2026-10-01): with `{"gpt-6-luna":"gpt-5.4-nano","gpt-transcribe":"gpt-4o-mini-transcribe"}`:

- `GET /v1/models/gpt-6-luna` returned `gpt-5.4-nano`;
- a `gpt-6-luna` Responses call was served by `gpt-5.4-nano-2026-03-17`;
- a multipart `gpt-transcribe` upload was served by `gpt-4o-mini-transcribe`.

---

## 8. Pointing the app at it, and the own-key alternative

**Cora's proxy.** In Settings → Connection:

1. Set the base URL to `https://picnic.cora.earth/v1`.
2. Paste the device token (`pk_…`) as the access token.
3. Tap **Save**, then **Test connection**.

The token is stored encrypted on the device (Android Keystore, AES-GCM) and is only shown as a hint like `pk_…a1b2`. Nothing else changes: every feature, including live icon drawing (when "Draw new icons with AI" is on), goes through the proxy.

**Own key (no proxy).** A friend who prefers to pay OpenAI directly:

1. Leaves the base URL at the default, `https://api.openai.com/v1`.
2. Pastes their own OpenAI key.
3. Ideally uses a key from a dedicated project with a hard spend limit and model restrictions, set up as in §3.1.

The key is stored the same encrypted way and is sent only to OpenAI. Expect about US$1–2 a month for one active user.

**A non-production proxy.** The app talks HTTPS only: Android blocks cleartext at targetSdk 36 and the app ships no network-security exception. Core's `normalizeBaseUrl` accepts `http://` only for loopback, and that exists for JVM tests and the core CLI. To try a proxy change with the app, run a second Worker or a second self-hosted instance on another subdomain (e.g. `picnic-staging.cora.earth`). To try it without the app, point the core CLI at a local `serve.mjs` / `wrangler dev` (§12).

---

## 9. Configuration reference

| Name | Kind | Default | Meaning |
|---|---|---|---|
| `OPENAI_API_KEY` | **secret**, required | — | The real key (a restricted project key) |
| `ACCESS_TOKEN_HASHES` | secret | — | `name:sha256hex` entries, comma or newline separated (§5.3) |
| `TOKENS` | KV binding (Cloudflare only) | — | Device tokens: key = hash, value = `{"name","created","disabled"}` |
| `MODEL_MAP` | var or secret (JSON object or JSON string) | `{}` | Requested id → upstream id (§7) |
| `ALLOWED_MODELS` | var (comma list or JSON array) | the four `ModelDefaults` ids | Upstream ids allowed after mapping |
| `ALLOWED_TOOLS` | var | `web_search` | Tool types allowed in `/responses` (empty = no tools) |
| `MAX_OUTPUT_TOKENS` | var | `16000` | Cap and default for `max_output_tokens` |
| `MAX_JSON_MB` / `MAX_TRANSCRIPTION_MB` / `MAX_IMAGE_EDIT_MB` | var | `15` / `26` / `20` | Request body limits per route (MiB) |
| `MAX_IMAGES_PER_REQUEST` | var | `1` | Upper bound for `n` on `/images/edits` |
| `IMAGE_ALLOWED_QUALITIES` | var (comma list) | `low,medium` | Allowed `quality` on `/images/edits`; the first is filled in when a request has none (unless `auto` is listed) |
| `IMAGE_ALLOWED_SIZES` | var (comma list) | `1024x1024,auto` | Allowed `size` on `/images/edits`; same default rule |
| `MAX_IMAGE_INPUTS` | var | `2` | Reference images per `/images/edits` request |
| `UPSTREAM_TIMEOUT_SECONDS` | var | `180` | Wait for OpenAI's response headers (keep ≥ 150) |
| `RATE_LIMITER` | rate limiting binding (Cloudflare only) | — | Per-device limit; omit the block to disable |
| `RATE_LIMIT_RETRY_AFTER` | var | `60` | `Retry-After` seconds on a rate-limited request |
| `OPENAI_BASE_URL` | var | `https://api.openai.com/v1` | Upstream base (https; http only for localhost) |

At least one of `ACCESS_TOKEN_HASHES` and `TOKENS` must be configured.

Self-hosted only (`scripts/server.mjs`, §4):

| Name | Default | Meaning |
|---|---|---|
| `HOST` | `127.0.0.1` | Listen address. The Docker image uses `0.0.0.0` inside the container, and compose publishes it on `127.0.0.1` only. |
| `PORT` | `8787` | Listen port |
| `RATE_LIMIT_PER_MINUTE` | `60` | Requests per device per minute, kept in memory; `0` turns the limit off |
| `IDLE_TIMEOUT_SECONDS` | `300` | Drop a connection after this long with no data either way. It must be longer than `UPSTREAM_TIMEOUT_SECONDS`. |
| `SHUTDOWN_GRACE_SECONDS` | `120` | On `SIGTERM`, how long in-flight requests get to finish before their connections are closed |

`wrangler.jsonc` deliberately has **no** `secrets.required` block. With it, `wrangler dev` loads only the listed names from `.dev.vars` and silently drops `ACCESS_TOKEN_HASHES` and `MODEL_MAP`. A missing `OPENAI_API_KEY` is still caught: it fails closed (500 and `healthz` `{"ok":false}`).

---

## 10. Errors and troubleshooting

Every error the proxy makes uses OpenAI's shape, `{"error":{"message","type","param","code"}}`, so the app treats it like an upstream error.

| HTTP | `code` | Cause | What to do |
|---|---|---|---|
| 401 | `invalid_api_key` | Missing, unknown or disabled device token | Mint and register a token (§5). After a KV change, wait up to a minute. |
| 403 | `model_not_allowed` | The model (after `MODEL_MAP`) isn't in `ALLOWED_MODELS` | Add the upstream id. Usually it's a map target that was forgotten. |
| 400 | `invalid_json`, `invalid_multipart`, `missing_required_parameter`, `invalid_value`, `tool_not_allowed` | A malformed or disallowed request | The message says which part. The app never sends these. |
| 413 / 415 | `request_too_large` / `unsupported_content_type` | Body too big, or the wrong content type for the route | |
| 429 | `rate_limit_exceeded` (type `rate_limit_error`) | The per-device rate limit | Wait for `Retry-After`. If real use hits it, raise `simple.limit` (Cloudflare) or `RATE_LIMIT_PER_MINUTE` (self-hosted). |
| 429 | `project_spend_limit_exceeded`, `insufficient_quota`, … | **OpenAI's** spend limit or credit, passed through | Raise the project limit or add credit (§3.1). The app shows its quota message. |
| 500 | `proxy_misconfigured` | Broken config | The log line `config_error` lists the problems; `healthz` is `{"ok":false}`. A self-hosted proxy doesn't start at all (§4.5). The app does **not** retry it and says "The proxy can't reach OpenAI right now. Ask whoever runs it." |
| 502 | `upstream_auth_failed` | OpenAI rejected the **proxy's** key | Rotate `OPENAI_API_KEY` (§5.4). Not retried by the app; same message as above. |
| 502 / 504 | `upstream_unreachable` / `upstream_timeout` | Network trouble, or no response headers within 180 s | Transient. The app retries 502/503/504 (except the two codes above). |
| 502 / 504 from Caddy or nginx (not JSON) | — | The self-hosted proxy isn't running, or is restarting | Run `sudo docker compose ps` or `systemctl status picnic-proxy`, then read its logs. The app retries these. |
| 404 | `null` | A route the proxy doesn't serve | Check that the base URL ends in `/v1`. The app maps any 404 other than `model_not_found` to "Check the base URL in Settings" with an **Open Settings** button. |

---

## 11. Logs and privacy

The proxy writes one JSON line per request. On Cloudflare the lines go to Workers Logs (`observability` in `wrangler.jsonc`). Self-hosted, they go to standard output, so read them with `docker compose logs` or `journalctl -u picnic-proxy`. For example:

```json
{"event":"proxy","method":"POST","route":"responses","token":"sams-phone","model":"gpt-5.6-terra","status":200,"requestId":"req_…","ms":2127}
{"event":"reject","method":"POST","route":"responses","status":403,"code":"model_not_allowed","ms":3,"token":"sams-phone"}
```

- **What's logged:** the route, status, latency to response headers, the device name (or the first 8 hex characters of its hash), the requested model and the mapped `upstreamModel` when they differ, OpenAI's `x-request-id` (for support tickets), and the error code.
- **Never logged:** request or response bodies (item names, photos, audio), device tokens, full hashes, the OpenAI key, or exception messages (a JSON parse error can quote the body, so only the error's name is logged).
- **Sent upstream:**
  - **Every request:** the body after the policy changes in §2.2, plus `content-type`, `accept` and the proxy's own `Authorization`.
  - **Responses calls:** a per-device `safety_identifier`.
  - **Added by Cloudflare** (Worker only): a `cf-worker` header naming the zone.
- **Stored by OpenAI:** `store:false` is forced on every Responses call.
- **Self-hosted:**
  - `server.mjs` adds its own lines: `listening`, `config_error`, `shutdown`, `stopped` and, for a stream that breaks, `stream_error` with only the error's name.
  - Caddy writes no access log unless you turn one on, and it redacts `Authorization` when you do.
  - nginx's default access log has no `Authorization` header, and the app sends no query strings.

---

## 12. Local development and tests

```bash
cd proxy
npm test                                  # node --test, no install needed (Node 22+)

cp .dev.vars.example .dev.vars            # fill in OPENAI_API_KEY and ACCESS_TOKEN_HASHES (gitignored)
node scripts/serve.mjs                    # http://127.0.0.1:8787/v1   (Node; PORT / HOST env override)
bun scripts/serve.mjs                     # same on Bun
deno run --allow-net --allow-env --allow-read scripts/serve.mjs   # same on Deno
npx wrangler dev                          # the real Workers runtime (workerd), with local KV + rate limiter
```

- `serve.mjs` reads `.dev.vars` and then the process environment, which overrides it. It has no KV or rate-limit binding, so use `ACCESS_TOKEN_HASHES`.
- `serve.mjs` is for development. To run the proxy as a service, use `scripts/server.mjs` (§4).
- Behind an outbound HTTP proxy, Node's `fetch` needs `NODE_USE_ENV_PROXY=1` (Node ≥ 22.21).
- **Tests** (`proxy/test/`, 56 cases, mocked upstream `fetch`):
  - routes, healthz and 404s;
  - auth through hashes and KV (active, disabled, malformed) and the exact 401 body;
  - fail-closed config;
  - the model allow-list for JSON, multipart and path ids;
  - `MODEL_MAP` rules (map before check, single step, case-sensitive, multipart byte-identity);
  - duplicate and case-variant `model` smuggling;
  - `store:false`, the output cap, `service_tier` stripping, the tool allow-list and `safety_identifier`;
  - client header stripping and the response header allow-list;
  - size limits (declared and streamed);
  - pass-through of status and body, SSE streaming before upstream completion, upstream 401 → 502, timeouts;
  - the rate limiter, including failing open;
  - "never logged" checks;
  - the mint script;
  - the Node adapter over real sockets;
  - the production server, `server.mjs`, over real sockets:
    - settings validation, and refusing a broken config or the env template's placeholders (exit 78, no values logged);
    - the in-memory rate limit;
    - SSE pass-through, and cancelling the upstream call when the device disconnects;
    - graceful and forced shutdown;
    - the healthcheck script.
- **Fixture:** `test/fixtures/okhttp-transcription.multipart` is a body produced by the app's HTTP client, OkHttp 5.5.0. Its audio bytes contain a fake `model` part that must stay inside the file.
- **Live end-to-end (2026-10-01).** These ran against the real API, both through `serve.mjs` on Node and through `wrangler dev` (workerd):
  - `smoke.mjs` with `--audio`;
  - an SSE stream;
  - a curl `-F` image edit;
  - core's CLI (`test`, `transcribe`, `ingest`, `icons`, `draw`) with `PICNIC_BASE_URL` pointing at the proxy;
  - a `MODEL_MAP` run (§7);
  - KV tokens and the rate limiter under workerd.

  Total spend was a few cents.
- **Self-hosting kit (2026-10-01).** Checked without a Docker daemon or systemd as PID 1:
  - **The image.** The pinned `node:22.23.3-alpine3.24` filesystem, with the files laid out as the `Dockerfile` does, ran as uid 1000 on a read-only root:
    - `/healthz` answered 200, and a request without a token got 401;
    - the `HEALTHCHECK` command exited 0;
    - writes failed with `EROFS`;
    - `SIGTERM` ended in `stopped`.
  - **The reverse proxies.** `deploy/Caddyfile` (Caddy 2.11) and `deploy/nginx-picnic.conf` (nginx 1.30) were run in front of `server.mjs` and a fake OpenAI:
    - other paths stopped at the reverse proxy with 404;
    - SSE arrived event by event;
    - a 5 MiB upload arrived byte-identical;
    - 26.5 MiB got the proxy's JSON 413, and 28 MiB got the reverse proxy's 413;
    - a call silent for 130 s completed.
  - **The real API.** `smoke.mjs` passed through Caddy and `server.mjs` to the real API.
  - **Static checks:**
    - `hadolint` reports no findings;
    - `docker compose config` validates `compose.yaml`;
    - `systemd-analyze verify` passes, and `systemd-analyze security` rates the unit 1.2.

---

## 13. Future: the Decisions API route

`docs/ARCHITECTURE.md` §8.6 plans to move the icon decision (cascade step 2) to OpenAI's Decisions API (`POST /v1/decisions`) once it leaves limited preview and publishes a schema. Today it answers 403 "Decision API is not enabled for this user". The proxy part of that switch-over is step 4 of the §8.6 checklist:

1. Add `'/v1/decisions'` to `ROUTES` in `proxy/src/worker.js` (JSON, `limit: 'json'`).
2. Apply `MODEL_MAP` and `ALLOWED_MODELS` to its `model` field, the same way as `/responses`. Decide whether `store:false` and an output cap apply, from the published schema.
3. Add the decisions model to `ALLOWED_MODELS` and add tests next to the `/responses` ones.

Until then, `/v1/decisions` gets a 404 from the proxy. The app falls back to `ResponsesIconDecider` anyway.
