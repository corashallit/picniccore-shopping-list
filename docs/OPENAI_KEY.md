# Setting up a key for a friend

Picnic List talks to OpenAI directly from the phone. Whoever uses it pastes an OpenAI API key into
**Settings → Your OpenAI key**, and that is the only way to connect it. The key is never in the
repository or the APK.

This page is for the person who makes that key, usually Cora making one for a friend. It is written so
that a lost phone or a leaked key costs little and reaches nothing else.

Checked against OpenAI's documentation on **2026-10-01** (sources at the end). OpenAI renames dashboard
labels from time to time. Where this page quotes a label, it is OpenAI's wording on that date.

## Why the extra steps

OpenAI's own advice is: "Don't share it with others or expose it in any client-side code such as
browsers or apps." Picnic List keeps the key on the phone because there is no server in between, so
this page limits the damage instead:

- **A project of its own** keeps the key away from everything else in the OpenAI account.
- **A monthly budget with a hard limit** caps what the key can ever cost in a month.
- **A restricted key** can only call what the app calls.
- **One key per person** means you can revoke one phone without touching the others.

## What the app calls, and the permissions that covers

Every request goes to `https://api.openai.com/v1` with the key as a bearer token (core
`OpenAiClient`). These are all the endpoints the app uses:

| Endpoint | When the app calls it | Model | Restricted-key permission | Scope OpenAI checks |
|---|---|---|---|---|
| `GET /v1/models/gpt-5.6-terra` | **Test connection** (free) | none | **List models: Read** | `api.model.read` |
| `POST /v1/responses` | "Sort it out" (text, photo, voice transcript), with web search for typed text and voice transcripts (not photos) | `gpt-5.6-terra` | **Responses API: Write**, plus **Model capabilities: Request** | `api.responses.write`, `api.model.request` (`model.request`) |
| `POST /v1/responses` | Background: sorting items into aisles, picking basket pictures and icons | `gpt-6-luna` | the same two | the same |
| `POST /v1/audio/transcriptions` | A voice note | `gpt-transcribe` | **Model capabilities: Request** | `api.model.request` |
| `POST /v1/images/edits` | "Draw new icons with AI" | `gpt-image-2.5-flare` | **Model capabilities: Request** | `api.model.request` |

So a restricted key needs exactly three permissions: **List models: Read**, **Model capabilities:
Request** and **Responses API: Write**. Set everything else to **None**: the app uses no files,
assistants, threads, vector stores, batches, fine-tuning, evals, prompts or webhooks, and it sends
`store: false`, so it never reads a response back.

The mapping comes from OpenAI's permissions page. Its table describes "List models" as "List models
this organization has access to", "Model capabilities" as "Make requests to chat completions, audio,
embeddings, and images", and "Responses API" as "Create responses". Its scope list gives
`api.model.read` and `model.read` for `/v1/models`, and `api.model.request` and `model.request` for
`/v1/audio`, `/v1/images` and `/v1/responses` among others. It also says: "if requesting /v1/models, the
API key must have api.model.read assigned to it".

**What Test connection proves.** It calls only the first row. "Connected" means the key is valid and has
**List models: Read**. The other two permissions are first used by the first "Sort it out", so try one
item after setting up. If a permission is missing, the app names the permission to turn on, for example
"This key can’t do everything the app needs. Ask the person who made it to allow Model capabilities
(Request)." (OpenAI answers HTTP 401 "You have insufficient permissions for this operation. Missing scopes:
…"; the scope ids go to the app's log only.)

| Scope OpenAI reports | Permission to add |
|---|---|
| `api.model.read`, `model.read` | List models: Read |
| `api.model.request`, `model.request` | Model capabilities: Request |
| `api.responses.write` (or `api.responses.read`) | Responses API: Write. If a key with Write still reports `api.responses.read` missing, the dialog can't grant both on one row: make the key with **Permissions: All** inside this dedicated project instead; the project's hard budget still caps it. |

## Step by step

### 1. Open the API platform

Sign in at [platform.openai.com](https://platform.openai.com). That's the API dashboard, not ChatGPT: a
ChatGPT subscription doesn't pay for API calls. The organization needs API billing (credits or a
payment method) under [Billing](https://platform.openai.com/settings/organization/billing). When prepaid
credits run out, requests fail with `credit_balance_exhausted`.

### 2. Make a project for this person

Create a new project in the dashboard, for example **Picnic List – Sam**, one per person. Its keys, its
usage and its limits are separate from your other projects.

### 3. Give it a monthly budget with a hard limit

In the project's settings, open **Limits**. Under **Spend**, choose **Edit spend limit**, enter the
**Monthly spend limit**, turn on **Enforce a hard limit**, and **Save**.

Be precise about what each control does:

- **A hard limit exists, but only with the switch on.** With **Enforce a hard limit** on, requests fail
  once the project's tracked spend reaches the amount: HTTP 429 with code
  `project_spend_limit_exceeded`. The app then says "The OpenAI account behind this key is out of credit
  or over its monthly budget." Without the switch, nothing stops the spending.
- **Spend alerts only notify.** OpenAI: "Spend alerts do not enforce a cap." Add one anyway, for example
  at half the limit, so you hear about heavy use before the app stops working.
- **It's not instantaneous.** OpenAI: "Enforcement is not instantaneous, so recorded spend can slightly
  exceed the configured amount."
- **It resets with the next monthly cycle.** Raising or removing the limit lets requests through again once
  the change has propagated (not instantly).
- An **organization** hard limit (organization settings → Limits) caps every project together. It makes a
  good backstop, but it stops your other projects too when it trips.

**How much?** Rough figures from the app's own measurements (docs/PROMPTS.md): a "Sort it out" costs
about US$0.006, or about US$0.03 when it searches the web for a recipe. A drawn icon costs about
US$0.015, and the app draws at most 40 automatically a day. Background sorting uses the small model and
costs a fraction of a cent. Normal use stays well under US$2 a month. **US$5 a month** is a sensible
cap. Raise it if the app ever says the budget is used up.

### 4. Optional: allow only the app's models

OpenAI lets a project keep a list of allowed models ("project model permissions": an allow list or a
deny list). If your project settings offer it, allow exactly the four models the app uses:

`gpt-5.6-terra`, `gpt-6-luna`, `gpt-transcribe`, `gpt-image-2.5-flare`

These come from core's `ModelDefaults`. If an app update changes one of them, update the list too.
Otherwise the app says "This OpenAI key can't use one of the AI models the app needs. Check which models
its project allows." (OpenAI answers HTTP 403 "Project … does not have access to model …").

Also check **web search**. If your organization allows hosted tools only for selected projects, this
project needs web search: "Sort it out" uses it for recipes and product lookups.

If you can't find either setting, skip this step. The budget limit is what caps the cost.

### 5. Create a restricted key in that project

On the **API keys** page, create a new secret key:

- **Name:** the person and device, for example `sam-phone`.
- **Project:** the project from step 2.
- **Permissions:** **Restricted**, then:
  - **List models: Read**
  - **Model capabilities: Request.** Set the **Model capabilities** row itself to Request. Don't pick
    only some of the endpoints under it. In early 2026, a group that showed "Mixed" didn't grant
    `model.request`, so `/v1/responses` failed with "Missing scopes: model.request". OpenAI staff said
    on the developer forum that a fix shipped on 2026-05-10, but setting the whole row avoids the
    problem either way.
  - **Responses API: Write**
  - everything else: **None**
- **Expiration:** if the dialog offers one, set it. OpenAI "strongly recommend[s] setting an expiration
  date when you create a project API key" and rotating keys regularly. When it expires, make a new key
  and paste it into the app (see "Rotating or revoking" below).

Copy the key when it's shown. OpenAI shows the full secret only once.

A restricted key with **Model capabilities: Request** can still call chat completions, embeddings and
moderations, because they share that permission. The budget is what bounds that.

### 6. Hand it over, then test

- Pass it on so it doesn't stay in a chat or email history, for example in person, or through a
  password manager's sharing feature.
- On the phone: **Settings → Your OpenAI key**, paste it and tap **Save**. The app runs **Test
  connection** right away. "Connected · … ms" means the key works and can read the model (step 5's
  first permission).
- Then add one item with **Sort it out**. That uses the other two permissions.

### 7. One key per person

Never put the same key on two phones. With one key per person you can revoke one person's key without
touching anyone else's, and the usage page shows who used what.

## Rotating or revoking

- **Revoke:** delete the key on the API keys page. OpenAI: "Revocations of an API key take effect within
  a few seconds." The phone's next request is refused. The app says "OpenAI didn't accept this key.
  Check it in Settings, or paste a new one." and offers **Open Settings**.
- **Rotate:** create a new key (step 5) and paste it into the app: **Settings → Your OpenAI key**, paste,
  **Save**. A new key replaces the stored one. Then delete the old key. The app never fetches keys
  itself, so pasting a new key is the only way to change it.
- **Expired key:** same as revoked: paste a new one.
- **Changed permissions on an existing key:** OpenAI: "Most updates that affect authentication results
  of an API key propagate within 15 minutes, but can potentially take longer."
- **Remove key** in the app's Settings deletes the key from the phone. It doesn't revoke it at OpenAI.

## What the phone protects, and what it doesn't

The app encrypts the key with an AES-256-GCM key that lives in the phone's **Android Keystore**
(`platform/TokenCipher.kt`). The encrypted copy sits in the app's no-backup folder
(`noBackupFilesDir/token.bin`).

**It protects the key from:**
- other apps: Android keeps each app's files private, and only Picnic List can use its Keystore key;
- backups and device-to-device transfers: the file is outside the backup allow-list, and a copied file
  couldn't be decrypted on another phone anyway;
- someone looking at the screen: Settings never shows the key again, only a hint like `sk-…a1b2`;
- someone with a USB cable: the APK friends install is not debuggable, so `adb shell run-as` can't open
  the app's files.

**It does not protect the key from someone who has the unlocked phone.** They can use every AI feature
as much as they like, and someone with enough skill (a rooted phone, for example) can get the key out.
The key also travels to OpenAI, over HTTPS, with every request.

That's why the budget's hard limit matters most. It is the only thing that caps the cost of a lost
phone or a leaked key. The restricted permissions narrow what a leaked key can do: no files, no
assistants, nothing in your other projects.

## When the app shows a message

| The app says | Usually means | Fix |
|---|---|---|
| "Add your OpenAI key in Settings first." | No key on the phone | Paste a key in Settings. |
| "OpenAI didn't accept this key. Check it in Settings, or paste a new one." | Wrong, revoked or expired key (401), or an unsupported country (403) | Make a new key (step 5) and paste it. |
| "This OpenAI key is missing a permission the app needs (…)." | The restricted key lacks a scope | Add the permission from the scope table above, or make a new key. |
| "This OpenAI key can't use one of the AI models the app needs. Check which models its project allows." | The project's allowed models exclude one the app uses | Add it (step 4). |
| "The OpenAI account behind this key is out of credit or over its monthly budget." | The hard limit was reached, or the credits ran out | Wait for the next month, raise the limit (step 3), or add credits. |
| "The service is busy right now. Try again in a moment." | Rate limits | Wait. Retries happen automatically first. |

## Sources (checked 2026-10-01)

- OpenAI, [Spend limits](https://developers.openai.com/api/docs/guides/spend-limits): project and
  organization **Monthly spend limit**, **Enforce a hard limit**, the 429 codes, "not instantaneous",
  spend alerts that only notify, the monthly reset.
- OpenAI, [Manage permissions in the OpenAI platform](https://developers.openai.com/api/docs/guides/rbac):
  the permission table (List models, Model capabilities, Responses API) and the scope names.
- OpenAI, [Error codes](https://developers.openai.com/api/docs/guides/error-codes): 401 for "an API key
  that does not have the required permissions for the endpoint", `project_spend_limit_exceeded`,
  `credit_balance_exhausted`.
- OpenAI, [API overview: Authentication](https://developers.openai.com/api/reference/overview#authentication):
  "Don't share it with others or expose it in any client-side code such as browsers or apps",
  revocation within seconds, other key updates within 15 minutes.
- OpenAI, [Production best practices](https://developers.openai.com/api/docs/guides/production-best-practices):
  key expiration and rotation, separate projects with their own spend limits.
- OpenAI, [Admin APIs](https://developers.openai.com/api/docs/guides/admin-apis) and
  [Model, tool, and data controls](https://developers.openai.com/api/docs/guides/terraform/project-controls):
  project model permissions (`allow_list` / `deny_list`) and hosted-tool permissions such as web search.
- OpenAI Developer Community, ["Missing scopes: model.request on restricted API key"](https://community.openai.com/t/missing-scopes-model-request-on-restricted-api-key/1371602)
  (January to May 2026): the "Mixed" pitfall, the exact error text, and OpenAI staff's 2026-05-10 reply
  that a fix had shipped.
- OpenAI Developer Community, ["Restricted API keys are broken for the Responses API"](https://community.openai.com/t/restricted-api-keys-are-broken-for-the-responses-api/1292912)
  (2025-06-19, one report, no reply): the dialog offers one choice per row (None, Read or Write), and a key
  with only Responses API: Write was refused for missing `api.responses.read`.
- The app: core `llm/OpenAiClient.kt` (endpoints), `assistant/OpenAiGroceryAssistant.kt`
  (`testConnection` = `GET /models/{SMART}`), `llm/ModelDefaults.kt` (models), `llm/ApiErrors.kt` (error
  mapping); app `settings/` and `platform/TokenCipher.kt` (storage).

Live check on 2026-10-01: the call Test connection makes, `GET /v1/models/gpt-5.6-terra` with a real
project key, answered HTTP 200 in 0.86 s with `{"id":"gpt-5.6-terra","object":"model",…}`. It isn't
billed.
