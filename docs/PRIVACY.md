# Picnic List privacy policy

> **Draft for Cora.** Host it as a plain public web page, for example `https://cora.earth/picnic/privacy`. Put that URL in Play Console and link it from the app's Settings (docs/RELEASING.md §8).
>
> Before publishing:
> - Replace every **[FILL IN: …]**.
> - Pick one hosting option in "Cora's server".
> - Delete this box and the "Notes for Cora" section at the end.
>
> It describes the app as built on 2026-10-01. Re-check it whenever what the app sends changes. It is not legal advice.

**Effective date:** [FILL IN: date of publication]

Picnic List is a grocery-list app for Android, made by [FILL IN: your name as shown on Google Play] in Ontario, Canada ("I", "me" below). If you have a question about this policy or your data, write to **[FILL IN: contact email]**.

## The short version

- **Your lists live on your phone.** There's no Picnic List account, and I can't see your lists.
- **Nothing leaves your phone until you connect the app to an AI service.** You connect it either with your own OpenAI key, or with an access token I gave you for my server. Without one, the app works as a plain list.
- **Once you're connected,** what you add is sent to OpenAI so it can be understood, sorted into aisles and given a little icon: typed requests, photos of handwritten lists, voice notes and item names. It goes either directly or through my server.
- **No ads, no analytics, no tracking, no selling of data.** The app contains no advertising or analytics code.
- **My server keeps only short technical logs:** which feature was used and when. It never keeps the contents.

## What stays on your phone

| What | Why | Backed up by Android? |
|---|---|---|
| Your lists and items, your aisles and store name, the aisle corrections you've taught the app, and the icons you picked for items | That's the app | **Yes**, if you've turned on Android backup or move to a new phone. This file is the only part of the app that is backed up. Backups are handled by Google under your Google account. |
| Settings: the server address, the "Draw new icons with AI" switch, a random installation ID, and a masked hint of your key or token (like `sk-…a1b2`) | So the app remembers your setup | No |
| Your OpenAI key or access token | To connect | No. It is encrypted with a key kept in your phone's Android Keystore and can't be decrypted on any other device. The app never shows it again. |
| Icons drawn for your items, a count of today's drawings, and any item names the image service refused to draw | So each icon is drawn once, and a refused name isn't retried | No |
| A photo or voice note while it's being processed | To send it | No. It is deleted as soon as it has been read; leftovers from an interrupted session are removed within a day. |

To remove everything from your phone, including what the app has learned, **uninstall the app** or clear its storage (Android Settings → Apps → Picnic List → Storage → Clear storage). Deleting a list removes that list.

## What leaves your phone, and when

Only after you've added a key or token in Settings. From then on:

| When you… | What is sent |
|---|---|
| **Ask the AI** in "Write" (for example "ingredients for pancakes") | Your text. Also, to sort the results: your aisle names in walking order, the names of unchecked items on the list (to avoid duplicates), up to 60 of your aisle corrections, and the icons you've chosen for item names. |
| **Snap or pick a photo** of a handwritten list | The photo, re-saved as a JPEG of at most 1600 pixels, plus the same context as above. **Location tags and other photo metadata are not sent:** the app re-encodes the image without them. |
| **Record a voice note** | The recording (a short audio file), with a hint list of item names already on the list so they're spelled right. The transcript then goes on like typed text. |
| **Add items** (including "Add as typed"), **rename** them, or **change your aisles** | Item names and aisle names, so the app can sort items into aisles, choose a basket picture for each aisle and pick an icon for each item. This happens in the background. |
| Have **"Draw new icons with AI"** on (the default) | For an item with no fitting icon: its name and the app's built-in style sheet image, to draw a small icon. At most 40 automatic drawings a day. You can turn this off in Settings. |
| Tap **Test connection** | Nothing about you: it only asks the server whether the AI model is available. |

**With every request:** your key or access token, so the service knows the request is allowed.

**Requests to the language model** also carry a *safety identifier*. It is a one-way scrambled form of a random ID the app makes when it is first used. OpenAI uses it to spot abuse from one installation; it contains nothing about you, your phone or your Google account.

**Location.** When the AI looks something up on the web, the app tells it a **fixed** approximate location, "Ontario, Canada", so products and units fit. The app never reads your phone's location. If the AI used web pages, the app shows links to them; opening one opens your web browser.

**Every connection is encrypted** (HTTPS). The app refuses unencrypted connections.

## Who receives it

### OpenAI

The AI features are provided by OpenAI, a company based in the United States.

- **With your own key,** your phone talks to OpenAI directly under your own OpenAI account. OpenAI's own terms and privacy policy apply between you and OpenAI.
- **With my access token,** my server passes your requests to OpenAI under my account, and OpenAI processes them on my behalf.

According to OpenAI's published API data controls, as of the effective date:
- OpenAI **does not use API data to train its models**.
- The app asks OpenAI **not to store** the answers (`store: false`).
- OpenAI may keep requests, including text, photos and icon drawings, in **abuse-monitoring logs for up to 30 days** before deleting them, unless the law requires longer.
- Voice-transcription requests are not kept in those logs.
- OpenAI may process data in the United States and other countries.

### My server (only if you use an access token from me)

My server at `picnic.cora.earth`:
1. checks your access token and applies usage limits;
2. forwards your request to OpenAI;
3. passes the answer back.

**It doesn't store what you send.**

- **Tokens.** It keeps only a one-way hash of each token, with a short name I gave your device (like "sams-phone"). That lets me turn a single device's access off.
- **Log lines.** It writes one line per request with:
  - the time, and which feature was used (for example the transcription route);
  - the AI model and the result code, and how long it took;
  - OpenAI's request number, for troubleshooting;
  - your device's name.

  The log **never** has the text, photos, recordings or item names you send, your token, or my OpenAI key.
- **Safety identifier.** If a request has none, the server adds one made from the token's hash, for the same abuse-detection reason as above.

**Where it runs and what the host keeps.** [FILL IN: keep the one that applies and delete the others.]

- *Self-hosted with Caddy* (the setup in the proxy's `deploy/Caddyfile`):
  - It runs on a server [FILL IN: provider and country].
  - The web front end keeps **no access log**.
  - The proxy's log lines are kept for [FILL IN: number] days.
- *Self-hosted with nginx:*
  - It runs on a server [FILL IN: provider and country].
  - nginx's access log also records the **IP address** your request came from, the time, the address requested and the app's network library name. It is kept for [FILL IN: number] days. I use it only to keep the service running and secure.
- *On Cloudflare Workers:*
  - Cloudflare hosts it on my behalf.
  - Cloudflare keeps the log lines, plus request details it adds itself (such as country and network), for 3 days (7 days on a paid plan).

### Nobody else

- I don't sell, rent or share your data with advertisers, data brokers or anyone else.
- I don't use it for advertising or profiling.
- **Google** distributes the app through Google Play and handles Android backups; its own privacy policy covers that. Google Play may show me anonymous, combined statistics such as install counts and crash rates.

## Permissions the app asks for

- **Microphone:** asked only when you first tap the microphone. The app records only while you're on the voice screen, and stops when you leave the app.
- **Internet:** to reach the AI service.

The app has **no camera permission**: photos come from your camera app or Android's photo picker, and the app sees only the photo you choose. It doesn't ask for your location, contacts, files or anything else.

## How long things are kept

| Data | Kept |
|---|---|
| Lists, settings and drawn icons on your phone | Until you delete them or uninstall the app |
| Android backup of your lists | As long as Google keeps your device backup (your Google account settings) |
| Photos and voice notes on your phone | Deleted right after they're sent; leftovers within a day |
| My server's logs | [FILL IN: number] days (Cloudflare: 3 or 7 days) |
| Access-token hashes on my server | Until I revoke the token |
| OpenAI's abuse-monitoring logs | Up to 30 days (no logs for voice transcription) |

## Your choices and rights

- **Use the app without AI.** Don't add a key or token: nothing leaves your phone.
- **Turn off "Draw new icons with AI"** in Settings, or **remove your key or token** there.
- **Delete** items or lists in the app, or uninstall it to remove everything.
- **Ask me** what my server's logs hold about your device, to delete them, or to turn off your access token: write to [FILL IN: contact email]. I'll answer within 30 days. For anything OpenAI holds under *your own* key, contact OpenAI.
- If you're not satisfied with my answer, you can contact the [Office of the Privacy Commissioner of Canada](https://www.priv.gc.ca/).

## Security

- **Encrypted connections.** Everything sent travels over HTTPS.
- **Your key or token** is encrypted on the phone with an Android Keystore key that never leaves your phone, and it is never included in backups.
- **No built-in secrets.** The app contains no passwords or keys of its own.
- **Tokens on my server.** It stores only hashes of access tokens.

## Children

Picnic List is meant for adults. It isn't directed at children, and I don't knowingly collect anything from children. If you think a child has used it with my access token, write to me and I'll turn that token off and delete its logs.

## Changes to this policy

If what the app collects or sends changes, I'll update this page and its effective date before the change reaches the app, and mention it in the release notes.

## Contact

[FILL IN: your name], [FILL IN: contact email], Ontario, Canada.

---

## Notes for Cora (delete before publishing)

**What to fill in:**
- the effective date;
- your name as shown on Google Play;
- the contact email (it can be the Play listing's support address);
- the hosting option: provider, country and log retention.
  - On a systemd host, the proxy logs to the journal, so its retention is journald's (`MaxRetentionSec=` in `journald.conf`).
  - With Docker it is the container's log options (`docker compose logs`).
  - If you use nginx, set `access_log off;` for the proxy, or shorten the retention, and say so above.
  - On Cloudflare, the proxy's `wrangler.jsonc` turns Workers Logs on. Setting `"observability": { "logs": { "invocation_logs": false } }` would leave only the proxy's own lines.

**Where each statement comes from** (re-check these when the app changes):

| Statement | Source in the repository |
|---|---|
| On-device files and the backup allow-list | `app/src/main/res/xml/data_extraction_rules.xml`, `backup_rules.xml` (only `state.json`); docs/ARCHITECTURE.md §6.1 |
| Token encryption | `settings/TokenStore.kt` (`noBackupFilesDir/token.bin`), `platform/TokenCipher.kt` |
| Settings and the install ID; the safety identifier is a hash of it | `settings/DataStoreSettingsRepository.kt` (`llmConfig()`); core `OpenAiClient.responses()` adds `safety_identifier` to Responses calls only |
| What a request contains | core `StateOps.ingestContext()` (aisles, ≤ 60 corrections, unchecked names, icon overrides); `ui/add/AddViewModel.keywords()` (transcription hints); `assistant/OpenAiGroceryAssistant.kt`, `IconDecider.kt`; `icons/AppIconRepository.kt` (drawing, daily cap 40) |
| Photos lose their metadata; capture and voice files are deleted | `platform/Captures.kt` (`toJpeg`: decode, then `Bitmap.compress`; `sweepStale` after 24 h); `AddViewModel` deletes each file after reading it |
| Fixed web-search location | core `llm/ModelDefaults.kt` (`WEB_SEARCH`: CA / Ontario / America/Toronto) |
| `store: false`; no request bodies in logs | core `OpenAiClient.kt` (`LlmLog`: method, path, status, request id, latency); app `platform/PicnicLog.kt` |
| Proxy logs and token hashes | `proxy/src/worker.js` (log fields), docs/PROXY.md §10 |
| Permissions | `app/src/main/AndroidManifest.xml`. Merged manifest checked 2026-10-01: INTERNET, RECORD_AUDIO and androidx's internal receiver permission only; no AD_ID |
| OpenAI retention | [OpenAI: data controls in the OpenAI platform](https://developers.openai.com/api/docs/guides/your-data): no training on API data; abuse monitoring up to 30 days for `/v1/responses` and `/v1/images/edits`, none for `/v1/audio/transcriptions` |
| Cloudflare log retention | [Cloudflare Workers Logs](https://developers.cloudflare.com/workers/observability/logs/workers-logs/): 3 days on Free, 7 on Paid; invocation logs include request metadata |
