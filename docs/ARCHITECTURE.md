# Picnic List: architecture

Status: v1 contract, 2026-10-01. Package and applicationId base: `earth.cora.picnic`. App name (placeholder): "Picnic List".

This document is the contract between the people (or agents) building the app in parallel. The Kotlin stub files listed in §3 are **normative**: they compile today, and their signatures are the interfaces. Bodies marked `TODO("<owner>: …")` are for the owner to fill in. If this document and a stub disagree, the stub wins. Fix both, and tell the other owners.

**Changing a contract.** You may add to a contract freely: new optional parameters with defaults, new functions, new fields with defaults. Renaming or removing anything, or changing what an existing call means, needs a note to every consumer listed in §3.2. Persisted names (anything in `state.json`, icon keys, basket keys, aisle-id and name normalization) also need a schema migration (§6.4).

Research this rests on (not in the repo): the OpenAI brief (`research/openai_api.md`, live-verified), the Android build brief (`research/android_build.md`), the icon catalog (`research/icon_catalog.json`) and the art directions (`research/directions.md`).

---

## 1. What we're building

An Android grocery list for one friend of Cora's, in Ontario, Canada. It is sideloaded first; Play internal testing may come later, so the build stays Play-compliant.

1. **Aisle sorting.** The user keeps a free-text list of their store's aisles, one per line, in walking order. Every item is assigned to one aisle (or Unsorted), and the list is shown grouped by aisle in walking order.
2. **Free-form text.** Input such as "ingredients for pancakes from scratch" goes to a reasoning model with web search, which returns individual, already-categorized items. A plain list such as "milk, 2 lemons, eggs" takes the same path.
3. **Photo of a handwritten list.** The same model transcribes it into items.
4. **Voice.** A recording is transcribed and the transcript is shown. The transcript then goes through the same text pipeline.
5. **Theme.** A rich "picniccore × vintage 90s magical-girl" look. The art direction is still being chosen, so this round builds a clean **theme layer** with tasteful placeholder art. It must never look like Material defaults.
6. **Item icons.** Every item has a tiny icon. A bundled library of about 235 keys exists, and the art pass will supply the images. When an item's best catalog icon is only `approximate`, a bespoke icon can be **drawn live** with `gpt-image-2.5-flare` and cached by item name.
7. **Collapsible aisles with baskets.** A collapsed aisle header draws a little basket holding 2–5 overlapping mini icons of its items, plus a count badge. Each aisle has a basket kind (16 kinds), assigned by the fast model with a keyword fallback.

Units are metric (g, kg, mL, L), and Canadian specifics apply (4 L bagged milk).

---

## 2. Repository layout and build

```
picniccore-shopping-list/
├─ settings.gradle.kts          root build: includeBuild("core"), include(":app")
├─ build.gradle.kts             plugin versions declared once (apply false); NO kotlin.jvm/android here
├─ gradle.properties            daemon/caching/config-cache (AGP 9 defaults not re-declared)
├─ gradle/libs.versions.toml    THE version catalog (shared with core/)
├─ gradlew, gradlew.bat, gradle/wrapper/   Gradle 9.8.0 wrapper (native jar, pinned sha256)
├─ local.properties             sdk.dir=/opt/android-sdk   (gitignored)
├─ core/                        STANDALONE Gradle build, plain Kotlin/JVM (bytecode 17 on JDK 21)
│  ├─ settings.gradle.kts       imports ../gradle/libs.versions.toml as "libs"
│  ├─ build.gradle.kts          kotlin.jvm + serialization + application (CLI); live tests gated
│  ├─ gradle.properties
│  └─ src/main/kotlin/earth/cora/picnic/core/{model,store,catalog,llm,assistant,cli}
│     src/main/resources/icon_catalog.json, prompts/*.txt
│     src/test/kotlin/…, src/test/resources/fixtures/…
├─ app/                         Android application (AGP 9.4.1 built-in Kotlin, Compose, Material3 themed)
│  ├─ build.gradle.kts          compose, serialization, buildConfig, Roborazzi, debug signing
│  ├─ debug.keystore            COMMITTED (androiddebugkey / android / android); never regenerate
│  ├─ proguard-rules.pro
│  └─ src/main/{AndroidManifest.xml, kotlin/earth/cora/picnic/…, res/…, assets/icons/…}
│     src/test/{kotlin/…, resources/robolectric.properties (sdk=36), screenshots/ (goldens)}
├─ proxy/                       Cloudflare Worker: OpenAI-compatible passthrough (not a Gradle project)
├─ docs/                        ARCHITECTURE.md, BUILDING.md, PROXY.md, PROMPTS.md
├─ design/concepts/             art-direction concept renders (reference only)
└─ .github/workflows/android.yml
```

Toolchain: AGP 9.4.1 with built-in Kotlin. **Never** apply `org.jetbrains.kotlin.android` or kapt. Kotlin is 2.4.20 (compose and serialization plugins). Compose BOM is 2026.09.00, which requires `compileSdk 37`. SDK levels are `targetSdk 36` (Play requirement since 2026-08-31) and `minSdk 26`. The JDK is 21, with bytecode 17. The rationale for every value is in `research/android_build.md`.

Commands (all through `./gradlew`; the system gradle 8.14 cannot evaluate AGP 9):

| What | Command |
|---|---|
| core build + tests (standalone) | `./gradlew -p core build` |
| core live tests (real API) | `OPENAI_API_KEY=… ./gradlew -p core test -Plive=true` |
| core CLI | `./gradlew -p core run --args="ingest 'ingredients for pancakes from scratch'"` |
| debug APK | `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/picnic-list-debug.apk` |
| app unit + screenshot tests | `./gradlew :app:testDebugUnitTest` / `:app:recordRoborazziDebug` / `:app:verifyRoborazziDebug` / `:app:compareRoborazziDebug` |
| lint (CI gate) | `./gradlew :app:lintDebug` |

Sandbox-only flags must **never** be committed. Use `-I <scratch>/robolectric-mirror.init.gradle` for Robolectric jar downloads. The Maven Central mirror lives in `~/.gradle/init.d`.

---

## 3. Ownership and interfaces

### 3.1 Files per implementer

| Owner | Owns (creates/edits) |
|---|---|
| **lead** | `docs/ARCHITECTURE.md`. Root Gradle files are handed to **app-shell** after the skeleton. |
| **core** | Everything under `core/` (sources, resources, tests, `core/build.gradle.kts`, the CLI), plus `docs/PROMPTS.md`. |
| **app-shell** | `app/build.gradle.kts`, `app/proguard-rules.pro`, root `settings.gradle.kts`/`build.gradle.kts`/`gradle.properties`/`gradle/**`/`.gitignore`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/**` (except `values/strings_screens.xml`), `app/src/main/assets/**`.<br>Kotlin: `PicnicApp.kt`, `AppContainer.kt`, `MainActivity.kt`, `settings/**`, `platform/**`, `icons/**`, `ui/theme/**`.<br>Tests under `app/src/test/kotlin/earth/cora/picnic/{settings,platform,icons,ui/theme}/**`.<br>Also the launcher icon, `.github/workflows/android.yml` and `docs/BUILDING.md`. |
| **app-screens** | `ui/PicnicAppRoot.kt`, `ui/nav/**`, `ui/list/**`, `ui/add/**`, `ui/edit/**`, `ui/aisles/**`, `ui/settings/**`, `ui/common/**`, `res/values/strings_screens.xml`.<br>Tests under `app/src/test/kotlin/earth/cora/picnic/ui/{list,add,edit,aisles,settings,nav,common}/**`, including the screen screenshot tests and their goldens in `app/src/test/screenshots/`. |
| **proxy** | `proxy/**` and `docs/PROXY.md`. |

`gradle/libs.versions.toml` is shared **append-only**: any owner may add an entry. Changing a version is app-shell's call, and core must still build standalone afterwards. Nobody else edits `core/build.gradle.kts`.

### 3.2 Contract files (normative stubs in the skeleton)

| Contract file | Provider | Consumers |
|---|---|---|
| `core/…/model/Model.kt` (domain model, `BasketKind`, `IconRef`, `SectionIds`) | core | app-shell, app-screens |
| `core/…/model/Names.kt` (`foldText`, `slugify`, `normalizeName`): implemented | core | all |
| `core/…/model/Aisles.kt` (`Aisles.parse`, `DEFAULT_TEXT`, `StoreProfile.aisles()`): implemented | core | all |
| `core/…/model/Grouping.kt` (`ListGrouping.group`, `AisleSection`, `GroupedList`) | core | app-screens |
| `core/…/model/StateOps.kt` (every state transition) | core | app-shell (icons), app-screens |
| `core/…/store/StateStore.kt` (`StateStore`, `JsonFileStateStore`, `InMemoryStateStore`) | core | app-shell (container), app-screens (tests) |
| `core/…/catalog/IconCatalog.kt` (`IconCatalog`, `BasketCatalog`, `displayRef`) | core | app-shell, app-screens |
| `core/…/llm/LlmConfig.kt` (`LlmConfig`, `LlmDefaults`, `ReasoningEffort`, `ConfigSource`, `TokenSource`) | core | app-shell (settings), app-screens (settings UI) |
| `core/…/llm/LlmError.kt` | core | app-shell, app-screens |
| `core/…/assistant/GroceryAssistant.kt` (+ input/result types, `assignBasketsOrGuess`) | core | app-shell, app-screens |
| `core/…/assistant/{OpenAiGroceryAssistant,OfflineParser,AisleSync}.kt`, `llm/OpenAiClient.kt` | core | app-shell (container), app-screens |
| `app/…/AppContainer.kt`, `PicnicApp.kt` | app-shell | app-screens |
| `app/…/settings/AppSettings.kt` (`AppSettings`, `SettingsRepository`, `InMemorySettingsRepository`, `maskToken`), `settings/TokenStore.kt` | app-shell | app-screens |
| `app/…/icons/IconRepository.kt` (`IconRepository`, `IconImages`, `NoOpIconRepository`) | app-shell | app-screens, theme |
| `app/…/platform/{Captures,PhotoPreparer,VoiceRecorder (AudioRecorder),TokenCipher}.kt` | app-shell | app-screens |
| `app/…/ui/theme/*` (tokens, `PicnicTheme`, components, decor slots, `ItemIcon`, `AisleBasket`, `AisleHeader`, `ItemRowCard`, `Glyph`) | app-shell | app-screens |
| `app/…/ui/PicnicAppRoot.kt` (`PicnicAppRoot(container)`) | app-screens | app-shell (MainActivity) |
| HTTP API surface: `/responses`, `/audio/transcriptions`, `/images/edits`, `/models/{id}` | proxy | core |

---

## 4. Domain model (core, `earth.cora.picnic.core.model`)

All of these types are immutable `@Serializable` data classes. The JSON field names are persisted.

```kotlin
const val STATE_SCHEMA_VERSION = 1
enum class ItemSource { TYPED, AI_TEXT, PHOTO, VOICE }             // persisted by name
enum class IconFit { EXACT /*"exact"*/, APPROXIMATE /*"approximate"*/ }

data class GroceryItem(
    val id: String,                 // UUID
    val name: String,               // "All-purpose flour"
    val quantity: String? = null,   // "2", "1 kg", "4 L bag" (metric)
    val note: String? = null,
    val aisleId: String? = null,    // null = Unsorted; unknown id also renders as Unsorted
    val icon: String,               // valid catalog key
    val iconFit: IconFit = IconFit.EXACT,
    val customIconId: String? = null,
    val checked: Boolean = false,
    val addedAt: Long,              // epoch ms
    val source: ItemSource = ItemSource.TYPED,
    val manuallyPlaced: Boolean = false,
)
data class GroceryList(val id: String, val title: String, val createdAt: Long,
                       val items: List<GroceryItem> = emptyList(),      // insertion order
                       val collapsedAisles: Set<String> = emptySet())   // section ids
data class StoreProfile(val name: String = "My store", val aislesText: String = Aisles.DEFAULT_TEXT,
                        val aisleBaskets: Map<String, BasketKind> = emptyMap())
data class Correction(val name: String /* normalizeName */, val aisleId: String, val at: Long)
data class AppState(val version: Int = STATE_SCHEMA_VERSION, val lists: List<GroceryList>,
                    val currentListId: String, val store: StoreProfile = StoreProfile(),
                    val corrections: List<Correction> = emptyList())     // oldest first, cap 200
data class Aisle(val id: String, val name: String, val position: Int)  // derived, 1-based position
data class IconRef(val key: String, val customIconId: String? = null)
enum class BasketKind(val key: String) { WICKER_PICNIC, BREAD_BASKET, FRUIT_CRATE, BERRY_PUNNET,
    WIRE_BASKET, EGG_BASKET, MILK_CRATE, COOLER, ICE_BUCKET, BENTO_BOX, JAR_SHELF, CANVAS_TOTE,
    FLOWER_BUCKET, BOTTLE_CARRIER, CANDY_JAR, VANITY_CASE }             // keys = catalog "baskets"
object SectionIds { UNSORTED = "_unsorted"; CHECKED = "_checked" }
const val UNSORTED_WIRE_ID = "unsorted"                                 // LLM enum literal only
```

There is a single store profile shared by all lists. "Unsorted" is represented only as `aisleId == null` (or an id that is not a current aisle). The literal `"unsorted"` exists only on the wire to the model.

### 4.1 Names and ids (implemented; persisted semantics)

- `foldText(s)` lowercases, folds accents (`œ→oe`, `æ→ae`, `ß→ss`), drops apostrophes, and reduces the result to `[a-z0-9]` runs separated by single spaces.
- `slugify(s)` = `foldText(s)` with spaces replaced by `-`. For example, "Aisle 2 - Baking & spices" → `aisle-2-baking-spices`.
- `normalizeName(name)` = `foldText(name)` plus a conservative singularization of the **last word only**, applied when the word is longer than 3 characters:
  - `ies→y`
  - `oes→o`
  - words ending in `ss`, `us` or `is` are left alone
  - otherwise a trailing `s` is dropped

  This is the key for dedupe, corrections and the bespoke-icon cache.
- `Aisles.parse(text)`: one aisle per non-blank line.
  - Lines starting with `#` are comments.
  - A leading bullet (`- `, `* `, `• `, `1. `, `2) `) is stripped.
  - `id = slugify(name)`. An empty slug becomes `aisle-<position>`, and the reserved slug `unsorted` becomes `unsorted-aisle`.
  - Duplicates get `-2`, `-3` and so on, skipping ids already used.
  - Positions are 1-based.

  The default text is the 14-aisle Ontario store used in the benchmark. Its ids (`produce`, `bakery`, `deli-cheese`, …, `bulk-natural-foods`) match the research fixtures exactly.

### 4.2 Grouping (`ListGrouping.group(list, aisles, basketFor)`)

`group` returns a `GroupedList(sections, total, remaining)`. The rules:

- **Which section an item goes in:** its `aisleId` if that id is a current aisle, otherwise UNSORTED.
- **Section order:** UNSORTED first (only when it has unchecked items), then the aisles in walking order, then CHECKED last (only when something is checked). Unsorted goes first because those items need the user's attention.
- **Which aisle sections appear:** an aisle section is present only when it has at least one item. If all of its items are checked, the section is still present, with empty `items` and `cleared = true`.
- **Section contents:**
  - UNSORTED and aisle sections list their **unchecked** items, oldest first.
  - CHECKED lists every checked item, in aisle order and then oldest first.
  - `checkedCount` is the number of the aisle's items now listed under CHECKED.
- **Basket and collapsed state:** `basket` = `basketFor(aisle)` for aisles and `BasketKind.DEFAULT` (wicker hamper) for the special sections. `collapsed` = whether the section id is in `list.collapsedAisles`. Every section starts expanded.

### 4.3 State operations (`StateOps.kt`, pure)

The app changes state **only** through these functions, always inside `store.update { … }`. They never do I/O, treat unknown ids as a no-op, and return the *same instance* when nothing changed.

| Function | Semantics |
|---|---|
| `StateOps.initialState(listId, now)` | One empty list "Groceries", default store |
| `currentList()`, `list(id)`, `updateList(id) {}` | accessors |
| `addItems(listId, items)` | Append. Drop any item whose `normalizeName` matches an unchecked item on the list or an earlier item in the batch. |
| `toggleChecked`, `removeItem`, `restoreItem(listId, item, index)` | For undo, the caller captures the item and its index before calling `removeItem`. |
| `editItem(listId, itemId) { … }` | Rename, quantity, note |
| `moveItemToAisle(listId, itemId, aisleId?, now)` | Sets `manuallyPlaced`. When `aisleId != null`, records or replaces a `Correction(normalizeName(name), aisleId, now)`. The list is capped at 200. |
| `setItemIcon(listId, itemId, key)` | icon = key, iconFit = EXACT, customIconId = null |
| `clearChecked`, `setSectionCollapsed(listId, sectionId, collapsed)` | |
| `newList` (it becomes current), `switchList`, `renameList`, `deleteList` | `deleteList` never removes the last list; if the deleted list was current, a neighbouring list becomes current. |
| `setAislesText(text, guessBasket)` | Replaces the text. Prunes baskets for vanished ids and fills missing baskets with the heuristic. **Does not touch items.** |
| `setAisleBaskets(map)` | Merges the map, ignoring ids that are not current aisles. |
| `applyAisleAssignments(itemId → aisleId?)` | Applies to all lists. Skips items that were manually placed into an aisle that still exists. |
| `attachCustomIcon(normalizedName, customIconId, overwrite)` | Applies to all lists, to every item with that normalized name. Without `overwrite`, only items whose `customIconId` is null are changed. |
| `itemsNeedingAisle()` | Items with a null or vanished aisle, excluding those the user deliberately moved to Unsorted. |
| `ingestContext(listId)` | Aisles, plus the newest valid corrections (≤ 60), plus the names of unchecked items. |

---

## 5. Icon and basket catalog (core, `earth.cora.picnic.core.catalog`)

The catalog is `core/src/main/resources/icon_catalog.json`, a verbatim copy of `research/icon_catalog.json` (validated by `research/validate_icon_catalog.py`). It contains:

- 20 `categories` `{key,label,fallbackIcon}`
- 235 `icons` `{key,label,category,aliases[],drawHint}`
- 16 `baskets` `{key,label,drawHint,keywords[]}`

The joined keys come to about 2.4k characters, well inside the strict-schema enum budget.

```kotlin
class IconCatalog(val file: CatalogFile) {
  val keys: List<String>                       // strict-schema enum, catalog order
  operator fun get(key): IconEntry?; operator fun contains(key): Boolean
  fun categoryOf(iconKey): IconCategory?
  fun fallbackFor(iconKey): String             // "lemon" -> "fruit_generic"; unknown -> DEFAULT_ICON
  fun sanitize(key: String?): String           // valid key or DEFAULT_ICON ("paper_bag")
  fun match(name: String): IconEntry?          // offline alias lookup (core implements)
  fun displayRef(item, drawing): IconRef       // custom ?: (drawing ? category fallback : item.icon)
  val baskets: BasketCatalog
  companion object { fun load(); fun parse(json); val default /* lazy singleton */ }
}
class BasketCatalog(entries) { fun entry(kind); fun guess(aisleName): BasketKind; fun guess(aisle) }
```

`IconCatalog.load()` reads the classpath resource. AGP packages Java resources from the core jar into the APK, and they are on the classpath under Robolectric too. The loading is already verified: `icon_catalog.json` is in the debug APK.

`BasketCatalog.guess` ports `basket_for` from `validate_icon_catalog.py`:

1. Fold the aisle name and pad it with spaces.
2. Find each keyword's earliest whole-word position.
3. The earliest position wins; ties go to the longest keyword.
4. If nothing matches, return `WICKER_PICNIC`.

The 43 sample names in the validator become a core unit test.

A core test asserts that `BasketKind.entries` keys equal the catalog's basket keys (already in `ContractTest`). To add a basket kind, add it to both places.

---

## 6. Persistence (core `store` + app)

### 6.1 Files on device

| Path | Owner | Contents | Backed up? |
|---|---|---|---|
| `filesDir/state.json` | core `JsonFileStateStore` | `AppState` | **Yes**: the only item on the allow-list (`data_extraction_rules.xml`, `backup_rules.xml`) |
| `filesDir/datastore/settings.preferences_pb` | app-shell | non-secret settings | no |
| `noBackupFilesDir/token.bin` | app-shell `KeystoreTokenStore` | TokenCipher blob `base64(iv):base64(ct)` | no (and undecryptable elsewhere) |
| `filesDir/icons/custom/<customIconId>.webp`, `filesDir/icons/index.json` | app-shell `IconRepository` | bespoke icons (192 px) and the name index | no |
| `cacheDir/captures/*.jpg`, `cacheDir/voice/*.m4a` | app-shell platform | transient capture files; deleted after use | no |

### 6.2 `StateStore` contract

```kotlin
interface StateStore {
    val states: Flow<AppState>                                  // current (loads on first use), then each commit
    suspend fun current(): AppState
    suspend fun update(transform: (AppState) -> AppState): AppState   // Mutex-serialized; persist, then emit
}
class JsonFileStateStore(file: File, initial: () -> AppState, ioDispatcher = Dispatchers.IO)
class InMemoryStateStore(initial: AppState)                     // tests/previews (implemented)
```

- **Loading:** lazy, on the first `states` collection or `current()`, on the IO dispatcher. Nothing is read in the constructor, so `AppContainer` stays cheap.
- **Writing:**
  1. Encode with `CoreJson.json`.
  2. Write `state.json.tmp` in the same directory.
  3. Call `FileOutputStream.fd.sync()`.
  4. `Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)`.

  If the write fails, `update` throws `IOException`; memory and observers keep the previous state. The UI shows the snackbar "Couldn't save."
- **Unreadable JSON:** rename the file to `state.json.corrupt-<ms>` and start from `initial()`. Never crash on a bad file.
- **Missing file:** start from `initial()`; nothing is written until the first update.

### 6.3 Format (example)

```json
{
  "version": 1,
  "currentListId": "6f1c…",
  "lists": [{
    "id": "6f1c…", "title": "Groceries", "createdAt": 1759330000000,
    "items": [{"id": "a1…", "name": "Lemons", "quantity": "2", "aisleId": "produce", "icon": "lemon",
               "iconFit": "exact", "checked": false, "addedAt": 1759330100000, "source": "AI_TEXT",
               "manuallyPlaced": false}],
    "collapsedAisles": ["bakery"]
  }],
  "store": {"name": "My store", "aislesText": "Produce\nBakery\n…", "aisleBaskets": {"produce": "fruit_crate"}},
  "corrections": [{"name": "oat milk", "aisleId": "dairy-eggs", "at": 1759330000000}]
}
```

`CoreJson.json` uses `ignoreUnknownKeys`, `encodeDefaults`, `explicitNulls = false` (null fields are omitted) and `coerceInputValues`.

### 6.4 Versioning

`AppState.version` is the schema version, and a file with no `version` field counts as version 1. On load:

- If `version < STATE_SCHEMA_VERSION`, run `StateMigrations.migrate(raw JsonObject, from)` before decoding. It runs one `(JsonObject) -> JsonObject` step per version.
- If `version > current` (a downgrade), decode leniently. Copy the file to `state.json.v<version>.bak` before the first write.

To change the schema:

1. Bump `STATE_SCHEMA_VERSION`.
2. Add a migration step.
3. Add a fixture test that reads an old file.

Persisted names (field names, enum serial names, icon and basket keys, the slug and normalize rules) never change without a migration.

---

## 7. LLM layer (core `llm` + `assistant`)

### 7.1 Configuration

`LlmDefaults` is **the single source of truth** for defaults, which come from the live benchmark:

| Setting | Default |
|---|---|
| smart model | `gpt-5.6-terra` @ `low` |
| fast model | `gpt-6-luna` @ `none` |
| transcription | `gpt-transcribe` |
| image | `gpt-image-2.5-flare`, quality `low`, size 1024² |
| web search | on, `search_context_size: "low"`, `user_location` CA / Ontario / America/Toronto |
| base URL | `https://api.openai.com/v1` |

Timeouts: connect 15 s, ingest 120 s, fast calls 45 s, transcription 60 s, image 120 s, connection test 20 s.

`SMART_PRESETS` provides suggestion chips in Settings: "Best" (`gpt-6.1-sol` @ low) and "Budget" (`gpt-6-luna` @ low).

`LlmConfig` holds the non-secret values. **The token is never inside it.** `ConfigSource.config()` and `TokenSource.token()` are read before every call, so Settings changes apply immediately. The app persists only overrides (§9), so an updated default reaches existing installs.

### 7.2 `OpenAiClient` (core-internal behaviour; the app only constructs it)

- **Endpoints:** all are relative to `baseUrl`, and auth is `Authorization: Bearer <token>`. This works the same against api.openai.com and Cora's proxy (§16).
- **Not configured:** a missing or blank token, or a non-`https://` base URL, throws `LlmError.NotConfigured` without sending a request.
- **Implementation:** OkHttp 5 stays an implementation detail of core (the dependency is `implementation`, not `api`). Calls go through `enqueue` inside `suspendCancellableCoroutine`, and `invokeOnCancellation { call.cancel() }` cancels them. The per-call timeout is the OkHttp call timeout.
- **Logging:** never log the token, the `Authorization` header or request bodies. Log the method, path, status, `x-request-id` and latency.
- **Retries:** up to 2 extra attempts on 429 rate limits (**not** `insufficient_quota` or the spend/usage-limit codes) and on 500/502/503/504. Back off 1 s, then 2 s, with up to 0.5 s of jitter. Honour `Retry-After` up to 20 s.
- **One-shot capability downgrades** on HTTP 400, each tried once:
  - `param == "reasoning.effort"`: parse "Supported values are: …" and retry with the nearest supported value, going up first.
  - `param` is `prompt_cache_options` or `prompt_cache_breakpoint`: retry without the cache fields.
- **Capability rules by model prefix** (`ModelCapabilities`, internal):
  - `gpt-5.6*` and `gpt-6*` get explicit prompt-cache fields.
  - `gpt-6.1-sol*` and `gpt-6-astra*` don't accept `none`, so use `low`.
  - `gpt-5-mini|nano` use `minimal` instead of `none`, and get no `web_search` with `minimal`.
  - **Never send `temperature`.**
- **Error mapping** (`LlmError`; HTTP details in `research/openai_api.md` §2.8):
  - 401/403 → `Unauthorized`. The server's message echoes a masked key, so drop it.
  - 429 with type `insufficient_quota` or a spend/usage-limit code → `QuotaExceeded`. Any other 429, or 503 after retries → `RateLimited`.
  - 404 `model_not_found` and other 4xx → `BadResponse(code)`.
  - IOException → `Network`. `SocketTimeoutException` or an exceeded call timeout → `Timeout`.
  - `status: "incomplete"` or unparsable output → `BadResponse`. A refusal content part, or `moderation_blocked` → `Refusal`.

### 7.3 `GroceryAssistant` operations

```kotlin
interface GroceryAssistant {
    suspend fun ingest(input: IngestInput, context: IngestContext): IngestResult
    suspend fun transcribe(clip: AudioClip, keywords: List<String> = emptyList()): Transcript
    suspend fun recategorize(items: List<NamedItem>, aisles: List<Aisle>, corrections: List<Correction>): Map<String, String?>
    suspend fun assignBaskets(aisles: List<Aisle>): Map<String, BasketKind>
    suspend fun drawIcon(request: IconDrawRequest): DrawnIcon
    suspend fun testConnection(): ConnectionCheck
}
sealed interface IngestInput { Text(text); Image(bytes, mimeType = "image/jpeg"); Transcript(text) }
data class IngestContext(aisles, corrections /*newest first*/, existingNames /*unchecked*/)
data class ProposedItem(name, quantity?, note?, aisleId? /*current id or null*/, icon /*valid*/, iconFit, aisleFromCorrection)
data class IngestResult(items, message, sources: List<WebSource>, skippedDuplicates, meta: CallMeta)
fun ProposedItem.toGroceryItem(id, now, source): GroceryItem
suspend fun GroceryAssistant.assignBasketsOrGuess(aisles, guess): Map<String, BasketKind>   // never throws LlmError
```

Every operation is main-safe and cancellable, and throws only `LlmError` (plus `CancellationException`).

**ingest** makes one `POST /responses` call to the smart model:

- **Fixed fields:** `store: false`, `max_output_tokens: 12000`, `reasoning.effort`.
- **Output format:** `text.format` is a **strict** `json_schema` named `grocery_items`, of the form `{items:[{name, quantity|null, note|null, aisle_id: enum(current aisle ids + "unsorted"), icon: enum(catalog keys), icon_fit: enum("exact","approximate")}], message}`.
- **Input (cache-friendly order):**
  1. A developer message with the stable RULES, ending in `prompt_cache_breakpoint` (only on models that support it).
  2. A developer message with the aisles in walking order (`id: name`), the learned corrections (`name -> aisle id`) and the names already on the list.
  3. The user message.
- **Text input:** `tools: [{type: "web_search", search_context_size, user_location}]`, `tool_choice: "auto"` and `include: ["web_search_call.action.sources"]`. These are added only if `LlmConfig.webSearch` is on.
- **Image input:** `[input_text "Photo of a handwritten shopping list. Add its items.", input_image data:image/jpeg;base64,…, detail "high"]`, with **no tools**. The app sends a JPEG with long edge ≤ 1600 px at q≈85.
- **Transcript input:** `"Voice note transcript: <text>"`, with the same tools as text input.
- **Parsing:**
  - Use the **last** `message` output item whose `phase` is not `"commentary"`. A missing phase counts as final.
  - A `refusal` part becomes `Refusal`.
  - If the JSON doesn't parse, try earlier final messages. Never concatenate `output_text`.
  - Sources come from `web_search_call.action.sources[].url`, `action.url` (from `open_page`) and `url_citation` annotations, deduplicated.
  - In `message`, markdown links `[t](u)` are reduced to `t`.
- **Post-processing:**
  - Trim the names.
  - A learned correction overrides `aisle_id`.
  - `"unsorted"` or an unknown id becomes `null`.
  - An invalid icon becomes `DEFAULT_ICON` and `APPROXIMATE`.
  - Dedupe by `normalizeName` against `existingNames` and within the answer; duplicates go to `skippedDuplicates`.

**transcribe** makes one `POST /audio/transcriptions` (multipart) call with these fields:

- `model`
- `response_format=json`
- `languages[]=en`
- `prompt="A person dictating a grocery list in Ontario, Canada."`
- `keywords[]`: at most 100, each one line, with `<` and `>` stripped. They come from the list's names and the correction names.
- `file=recording.m4a`. **The extension selects the decoder.** `.aac` and `.3gp` are rejected.

Blank `text` means nothing was heard; return `Transcript("")` rather than an error.

**recategorize** uses the fast model with no tools and the strict schema `{assignments:[{id: enum(i1..iN), aisle_id: enum(aisles + "unsorted")}]}`.

- Items with a matching correction are resolved locally and not sent.
- Batches hold at most 40 items.
- An id missing from the answer maps to `null`.

**assignBaskets** uses the fast model with the strict schema `{baskets:[{aisle_id: enum(ids), basket: enum(16 kinds)}]}`. A missing aisle falls back to the heuristic inside `assignBasketsOrGuess`.

**drawIcon** makes one `POST /images/edits` (multipart) call:

- Fields: `model`, `prompt`, `image[]=` the style reference PNG, `background=transparent`, `output_format=webp`, `output_compression=80`, `quality` (from config), `size=1024x1024`, `n=1`.
- The prompt is the one in `research/openai_api.md` §6.1, with the item name substituted, plus an optional hint.
- It returns the decoded `data[0].b64_json` bytes. Downscaling happens on the device (§8).

**testConnection** calls `GET /models/{smartModel}`. It is free, and it checks the URL, the token and model access in one go.

### 7.4 Prompts, CLI, tests

- **Prompts.** Prompt texts live in `core/src/main/resources/prompts/` (`ingest_rules.txt`, `recategorize_rules.txt`, `baskets_rules.txt`, `icon_prompt.txt`) and are documented in `docs/PROMPTS.md`, which core owns.
  - Start `ingest_rules.txt` from `research/openai/examples/rules.txt`. Fix one line there: it says category icons are `cat_*`, but in the catalog they are `*_generic`.
  - Lesson from the benchmark: examples leak verbatim, so never reuse eval inputs as examples.
- **CLI.** `earth.cora.picnic.core.cli.MainKt`, run with `./gradlew -p core run --args="<cmd> …"`. Commands: `ingest "<text>"`, `photo <jpg>`, `transcribe <m4a>`, `recat "<item>"…`, `baskets [aisles.txt]`, `icon "<name>" <out.webp>`, `test`. It reads `OPENAI_API_KEY` and optional `PICNIC_BASE_URL`/`PICNIC_SMART_MODEL`/… from the inherited environment. **Never** pass the key through `environment(...)` in Gradle: configuration-cache would write it to disk.
- **Tests.**
  - Unit tests run on MockWebServer (`mockwebserver3`) with fixtures in `core/src/test/resources/fixtures/`. Take trimmed real responses from `research/openai/out/bench/`. Cover the duplicate-final-message case, an incomplete response, a refusal, the error bodies and the effort downgrade.
  - Live tests are `@Tag("live")` and also `assumeTrue(OPENAI_API_KEY)`. They run only with `-Plive=true`. They can use `research/openai/{handwriting,audio}`; copy only small fixtures into the repo.

---

## 8. Icons on device (app-shell, `earth.cora.picnic.icons`)

```kotlin
interface IconRepository {
    val drawing: StateFlow<Set<String>>        // normalized names queued or drawing
    fun isBundled(key: String): Boolean        // assets/icons/<key>.webp exists
    fun onItemsAdded(items: List<GroceryItem>) // attach cached bespoke icons / enqueue approximate ones
    fun redraw(itemName: String)               // edit sheet "Draw a new icon" (forced; overwrites)
}
interface IconImages {                          // provided to the theme via LocalIconImages
    fun cached(ref: IconRef): ImageBitmap?      // memory only, composition-safe
    suspend fun load(ref: IconRef): ImageBitmap? // custom -> bundled key -> bundled fallback -> null
}
```

### 8.1 Bundled library

- **Files:** `app/src/main/assets/icons/<key>.webp`, 192×192 transparent WebP, one per catalog key. The art pass produces them from sprite sheets (`research/openai_api.md` §6.4).
- **Style reference:** `assets/icons/_style_reference.png`, the sheet used for live drawing. Until the art pass, app-shell copies `research/openai/icons/_style_reference_512.png`. That file is 330 KB, and it costs the same 1,024 input tokens as the 1024 px version.
- **Missing art:** if no asset exists, `IconImages.load` returns null and `ItemIcon` draws placeholder art. Build the bundled-key set once from `assets.list("icons")`.
- **Memory cache:** an `LruCache` of `ImageBitmap`s. Decode with `BitmapFactory` on `Dispatchers.IO`.

### 8.2 Live drawing flow

```
AddViewModel.commit ──► store.update{addItems}  ──► icons.onItemsAdded(addedItems)
                                                     │ for each item without customIconId:
                                                     │   key = normalizeName(name)
                                                     │   index.byName[key]? ──yes──► store.update{attachCustomIcon(key,id,false)}
                                                     │   iconFit==APPROXIMATE && settings.drawIcons
                                                     │     && key !in blocked && key !in drawing && under daily cap
                                                     │     ──► enqueue(key, name); drawing += key
Row shows IconCatalog.displayRef(item, drawing=key in drawing) → category fallback + DrawingSparkle
Worker (2 concurrent, appScope):
  styleRef bytes (cached) → assistant.drawIcon(IconDrawRequest(name, styleReference=…))
    retryable LlmError (RateLimited/Network/Timeout) → wait 3 s, retry ONCE
    Refusal (moderation) → blocked += key (never auto-draw this name again)
    other → give up silently (item keeps its approximate catalog icon)
  decode → trim transparent bounds → fit in 176 px → centre on 192×192 transparent
  → WEBP_LOSSY q82 (API 30+) / WEBP (26-29) → filesDir/icons/custom/<id>.webp (tmp+rename)
  → index.byName[key] = id (index.json atomic write) → store.update{attachCustomIcon(key, id, overwrite = redraw)}
  → evict caches; drawing -= key
```

- **Queueing:** the queue is a `Channel` with two worker coroutines, which gives "max 2 concurrent". The same key is never queued twice. Adding items never waits on the queue.
- **Icon id:** `customIconId = "c-" + sha1(key).take(10) + "-" + base36(epochSeconds)`. A redraw gets a new id, so the old bitmap is never shown from cache. Delete the old file after attaching the new one.
- **Index file:** `index.json` = `{"version":1,"byName":{…},"blocked":[…],"day":"2026-10-01","drawnToday":3}`.
- **Daily cap:** 40 automatic drawings per day protects against runaway cost. A redraw ignores the cap.
- **Settings toggle:** "Draw new icons with AI", on by default, with the cost note: "Drawing a new icon takes about 10 s and costs about 1.5¢ (US)."
- **Offline:** nothing is re-queued automatically later. The item shows its approximate catalog icon, and the user can choose "Draw a new icon".
- **Display rule** (core `IconCatalog.displayRef`): a bespoke icon if one is attached; otherwise, while drawing, the category fallback with a sparkle; otherwise the item's own key.

---

## 9. Settings and the token security model (app-shell, `earth.cora.picnic.settings`)

```kotlin
data class AppSettings(llm: LlmConfig = LlmConfig(), drawIcons = true, themeMode = SYSTEM,
                       reduceMotion = false, hasToken = false, tokenHint: String? = null)
interface SettingsRepository {
    val settings: Flow<AppSettings>; suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)   // persists only non-default values
    suspend fun setToken(token: String?); suspend fun token(): String?; suspend fun resetLlmToDefaults()
}
interface TokenStore { suspend fun read(): String?; suspend fun write(token: String?) }
```

`SettingsRepository` has a production implementation and a test one:

- **`DataStoreSettingsRepository`** (production, `androidx.datastore:datastore-preferences` 1.2.1):
  - **Keys:** one preference per field (`base_url`, `smart_model`, `smart_effort`, `fast_model`, `fast_effort`, `transcribe_model`, `image_model`, `image_quality`, `web_search`, `search_context_size`, `draw_icons`, `theme_mode`, `reduce_motion`), plus `token_hint` and `install_id`.
  - **Defaults:** an absent key means the default (`LlmDefaults`). Writing a value equal to the default removes the key.
  - **Base URL validation:** trimmed, without a trailing `/`, and it must start with `https://`. The settings UI validates this; core enforces it again.
  - **Safety identifier:** `install_id` is a random UUID created on first run. `llm.safetyIdentifier` = the first 32 hex characters of `sha256(install_id)`, filled in by `current()` and `settings`.
- **`InMemorySettingsRepository`** is for tests and previews (already implemented).

**Token security model:**

- **Never in the repo or APK.** The user pastes either their own OpenAI key or a per-device proxy token from Cora (`pk_…`).
- **Encryption.** The token is encrypted with an AndroidKeyStore AES-256-GCM key (`platform/TokenCipher`). The Keystore picks the IV, and the stored form is `iv:ct`. The blob lives in `noBackupFilesDir/token.bin`, outside the backup allow-list, and a restored blob can't be decrypted on another device anyway.
- **Unreadable token.** On `KeyPermanentlyInvalidatedException` or `AEADBadTagException`, treat the token as unset (`hasToken = false`) and ask the user for it again.
- **Where it can appear.** It is decrypted only inside `token()`, which `TokenSource` calls per request. It is never put in a data class, a `StateFlow`, a log line or an exception message.
  - The Settings field is write-only. It shows `tokenHint` (from `maskToken`, e.g. `sk-…a1b2`) and is never pre-filled.
  - `LlmError.Unauthorized` discards the server text, which echoes a masked key.
  - There is no `HttpLoggingInterceptor` in any build type.
- **Tests.** The Keystore doesn't exist under Robolectric, so tests use `InMemoryTokenStore`.
- **Network.** Cleartext is blocked by default at targetSdk ≥ 28. `UnknownServiceException` (cleartext) maps to `NotConfigured("https required")`.

---

## 10. App structure (app-shell + app-screens)

### 10.1 Application and DI

- `PicnicApp : Application` with `val container by lazy { AppContainer(this) }`. Its `onCreate` does nothing heavy, because Robolectric instantiates it in every test.
- `AppContainer` (app-shell completes the TODOs) provides:

  | Member | Notes |
  |---|---|
  | `appScope` | `SupervisorJob + Dispatchers.Default`. Work that outlives a screen: commits, aisle sync, the icon queue. |
  | `clock()`, `newId()` | |
  | `catalog`, `baskets` | |
  | `store` | `JsonFileStateStore(filesDir/state.json)` |
  | `settings` | `DataStoreSettingsRepository` |
  | `configSource`, `tokenSource` | |
  | `assistant` | `OpenAiGroceryAssistant` |
  | `aisleSync` | |
  | `icons`, `iconImages` | |
  | `photos` | `PhotoPreparer` |
  | `newVoiceRecorder()` | |
- **ViewModels take their dependencies individually** (store, assistant, settings, icons, catalog, clock, newId, appScope), never the container. Their factories read the container:

  ```kotlin
  val Factory = viewModelFactory { initializer {
      val c = (this[APPLICATION_KEY] as PicnicApp).container
      ListViewModel(c.store, c.icons, c.catalog, c.clock, c::newId) } }
  ```

  Tests then pass `InMemoryStateStore`, `InMemorySettingsRepository`, `NoOpIconRepository` and a fake `GroceryAssistant`.

### 10.2 Activity and navigation

`MainActivity` is owned by app-shell:

- Call `installSplashScreen()` (optional), then `super.onCreate`, then `enableEdgeToEdge()` with bar styles that follow the effective theme, then `setContent`.
- Inside `setContent`, wrap the UI as `PicnicTheme(darkTheme = themeMode resolved, reducedMotion = settings.reduceMotion || system animator scale == 0) { CompositionLocalProvider(LocalIconImages provides container.iconImages) { PicnicAppRoot(container) } }`.
- Never lock orientation.

`PicnicAppRoot` is owned by app-screens:

- **Navigation:** a navigation-compose 2.10 type-safe `NavHost` with `@Serializable` routes `ListRoute` (start), `AislesRoute` and `SettingsRoute`.
- **Sheets:** the Add sheet and the Edit sheet are `ModalBottomSheet`s owned by the List screen, not routes. The list switcher is a sheet or menu on the list's top bar.
- **Back:** only `BackHandler`/`PredictiveBackHandler` and navigation's built-in back. `onBackPressed` is not called at targetSdk 36.
- **Insets:** every screen consumes window insets (`Scaffold` `contentWindowInsets`, `safeDrawingPadding`, `imePadding` on text fields). Decorative full-bleed art (`PicnicBackground`) may draw under the system bars; interactive content may not.

### 10.3 ViewModels and UI state (app-screens)

The names below are binding. Implementers may add fields. Every screen is split into a stateful `XxxRoute(vm)` and a stateless `XxxScreen(state, onAction…)`. Screenshot tests render the stateless one with hand-built states.

```kotlin
// ui/list
class ListViewModel(store, icons: IconRepository, catalog, clock, newId) : ViewModel {
    val state: StateFlow<ListUiState>; val events: Flow<ListEvent>
    fun toggle(itemId); fun delete(itemId); fun undoDelete(item: GroceryItem, index: Int)
    fun toggleSection(sectionId); fun clearChecked(); fun newList(title); fun switchList(id)
    fun renameList(id, title); fun deleteList(id)
    fun openEdit(itemId); fun closeEdit(); fun saveEdit(name, quantity, note)
    fun moveToAisle(itemId, aisleId: String?); fun setIcon(itemId, key); fun redrawIcon(itemId)
}
data class ListUiState(val loading: Boolean = true, val listId: String = "", val title: String = "",
    val lists: List<ListSummary> = emptyList(), val sections: List<SectionUi> = emptyList(),
    val remaining: Int = 0, val total: Int = 0, val editing: EditItemUi? = null)
data class ListSummary(val id: String, val title: String, val remaining: Int, val current: Boolean)
data class SectionUi(val id: String, val kind: SectionKind, val title: String, val number: Int?,
    val aisleIndex: Int /* position-1, or -1 */, val basket: BasketKind, val collapsed: Boolean,
    val cleared: Boolean, val count: Int, val miniIcons: List<IconRef> /* first 5 */, val items: List<ItemRowUi>)
data class ItemRowUi(val id: String, val name: String, val detail: String? /* "qty · note" */,
    val icon: IconRef /* displayRef */, val drawing: Boolean, val checked: Boolean)
data class EditItemUi(val item: GroceryItem, val aisles: List<Aisle>, val iconChoices: List<String>)
sealed interface ListEvent { data class Deleted(val item: GroceryItem, val index: Int) : ListEvent
                             data class Message(val text: String) : ListEvent }

// ui/add
enum class AddMode { WRITE, SNAP, SPEAK }
sealed interface AddPhase { Idle; Recording(elapsedMs, level: Float); Transcribing;
    Thinking(kind: ItemSource); Preview; Failed(message, retryable, canUsePlainLines, needsSettings) }
data class AddUiState(val mode: AddMode = WRITE, val text: String = "", val phase: AddPhase = Idle,
    val transcript: String? = null, val photo: Uri? = null, val preview: List<PreviewItemUi> = emptyList(),
    val message: String? = null, val sources: List<WebSource> = emptyList(), val skipped: List<String> = emptyList())
data class PreviewItemUi(val key: Int, val item: ProposedItem, val keep: Boolean, val aisleTitle: String, val icon: IconRef)
class AddViewModel(store, assistant, icons, catalog, photos: PhotoPreparer, recorderFactory: () -> AudioRecorder,
                   clock, newId, appScope) : ViewModel
    // setMode, setText, submitText, onPhoto(uri), startRecording, stopRecording, cancel,
    // toggleKeep(key), retry, usePlainLines, commit(listId)

// ui/aisles
data class AislesUiState(val storeName: String, val text: String, val parsed: List<AislePreviewUi>,
    val dirty: Boolean, val syncing: Boolean, val lastReport: AisleSyncReport?)
data class AislePreviewUi(val aisle: Aisle, val basket: BasketKind)
class AislesViewModel(store, aisleSync, baskets, appScope)   // save(): setAislesText + appScope.launch{aisleSync.sync()}

// ui/settings
data class SettingsUiState(val settings: AppSettings, val tokenInput: String = "", val test: ConnectionTestUi = Idle)
sealed interface ConnectionTestUi { Idle; Running; Ok(model, latencyMs); Failed(message) }
class SettingsViewModel(settings: SettingsRepository, assistant)
```

- **Lifecycle of saves.** Commits, deletes and aisle syncs run in `appScope` (via the store), so closing a sheet or screen doesn't cancel a save. LLM calls the user is waiting for run in `viewModelScope`, so they are cancelled when the user dismisses the sheet.
- **Snackbars.** Swipe-to-delete emits `ListEvent.Deleted`. The screen shows a snackbar "Removed <name>" with **Undo**, which calls `undoDelete(item, index)`.
- **Edit sheet.**
  - Rename, quantity and note use `editItem`.
  - Moving to an aisle uses `moveItemToAisle` and records a learned correction.
  - Changing the icon offers a grid of catalog keys, starting with the item's category, and calls `setItemIcon`.
  - "Draw a new icon" calls `icons.redraw(name)`.
- **List actions.** "Clear checked" calls `clearChecked`. New list, switch, rename and delete come from the top-bar switcher.

### 10.4 Data flow per input mode

The ingest context is taken when the request is sent. The target list is the list that was current when the sheet opened.

| Mode | Steps | Source | Fallback on `LlmError` |
|---|---|---|---|
| **Write** (text, list, recipe URL) | `submitText` → `Thinking` → `assistant.ingest(Text(text), state.ingestContext(listId))` → `Preview` (keep toggles; shows `message`, `skipped` and **Sources** chips, opened with `LocalUriHandler`) → `commit` | AI_TEXT | `Failed(canUsePlainLines=true)` → `usePlainLines()` = `OfflineParser.parse(text, …)` → Preview → commit as TYPED (Unsorted unless a correction matches) |
| **Snap** (camera / picker) | `TakePicture` into a FileProvider Uri (`Captures.newCaptureUri`, pending Uri kept in `rememberSaveable`) or `PickVisualMedia(ImageOnly)` → `onPhoto(uri)` → `photos.jpegBytes(uri)` (IO) → `Thinking` → `ingest(Image(bytes))` → Preview → commit. Delete the capture file afterwards. | PHOTO | `Failed(retryable)`; no plain-lines option (nothing to parse) |
| **Speak** | Request `RECORD_AUDIO` at runtime → `AudioRecorder.start()` (`VoiceRecorder`: m4a, mono 16 kHz 32 kbps, max 5 min). Poll `level()` about 10×/s for the waveform. `stop()` (also on `ON_STOP`) → bytes → `Transcribing` → `assistant.transcribe(AudioClip(bytes), keywords)` → show the transcript, which is editable → `Thinking` → `ingest(Transcript(text))` → Preview → commit. | VOICE | Empty transcript → "Didn't catch that". Transcribe failure → retry using the bytes kept in memory. Ingest failure → plain lines from the transcript. |

**Commit:**

1. `items = preview.filter { keep }.map { toGroceryItem(newId(), clock(), source) }`.
2. In `appScope`, run `store.update { addItems(listId, items) }`.
3. Take the ids that were actually added and call `icons.onItemsAdded(added)`.
4. Close the sheet and show a snackbar with the model's `message`.

`NotConfigured` gives `Failed(needsSettings = true)`, which offers **Open Settings**.

**Aisle edit:**

1. Save runs `store.update { setAislesText(text, baskets::guess) }`. The heuristic baskets show immediately, and items in vanished aisles render as Unsorted.
2. `appScope.launch { aisleSync.sync() }` runs:
   1. `assignBasketsOrGuess` → `setAisleBaskets`.
   2. `itemsNeedingAisle` → `recategorize` → `applyAisleAssignments`.
3. The returned `AisleSyncReport` drives a snackbar, e.g. "Sorted 6 items · 2 left in Unsorted" or the error's friendly message.

---

## 11. Theme layer contract (app-shell provides, app-screens consumes, art pass replaces)

**Rule for screens:** use only `PicnicTheme.*` tokens, the theme components, the decor slots, `ItemIcon`/`AisleBasket`/`AisleHeader`/`ItemRowCard` and `Glyph`.

- Never use raw `Color(…)`, `MaterialTheme.colorScheme` or `material-icons`. The project does not depend on material-icons.
- Never hand-roll a card or button style.
- Material3 *building blocks* are fine (`Scaffold`, `ModalBottomSheet`, `SwipeToDismissBox`, `SnackbarHost`, `DropdownMenu`, `LazyColumn`). `PicnicTheme` maps a full `MaterialTheme` from the tokens, so none of them shows Material defaults.

The art pass then mostly edits `ui/theme/**` and assets, not screens.

### 11.1 Tokens (`ui/theme/Tokens.kt`, `Theme.kt`)

```kotlin
@Composable fun PicnicTheme(darkTheme: Boolean = isSystemInDarkTheme(), reducedMotion: Boolean = false, content)
object PicnicTheme { colors: PicnicColors; type: PicnicTypography; shapes: PicnicShapes; dimens: PicnicDimens; motion: PicnicMotion }
data class PicnicColors(isDark, background, backgroundPattern, surface, surfaceRaised, surfaceSunken, hardShadow,
    outline, ink, inkSoft, inkFaint, primary, primaryShadow, onPrimary, secondary, onSecondary, highlight, onHighlight,
    link, error, onError, strike, scrim, aisles: List<AisleColors>) { fun aisle(index: Int): AisleColors }  // index<0 = neutral
data class AisleColors(base, shadow, pastel, deep)        // cel base, hard shade, pale fill, deep text
data class PicnicTypography(wordmark, screenTitle, aisleTitle, aisleNumber, itemName /*>=16sp*/, body,
    detail /*>=13sp*/, label, caption, script /*decorative*/, handwriting, counter)
data class PicnicShapes(card, row, chip, button, field, sheet /*top corners*/, badge, medallion)
data class PicnicDimens(gutter=16, xxs..xxl, rowGap, sectionGap, rowMinHeight=64, touchTarget=48, itemIcon=40,
    miniIcon=20, basket=64, medallion=44, outline=2, hardShadow=DpOffset(0,3))
data class PicnicMotion(reduced, stepFps = 8, shortMillis = 150, mediumMillis = 280)
val LocalIconImages: ProvidableCompositionLocal<IconImages>   // default IconImages.None (placeholder art)
val LocalIconCatalog: ProvidableCompositionLocal<IconCatalog> // default IconCatalog.default
```

- **Palettes.** There are two placeholder palettes: light "picnic at noon" and dark "picnic at dusk". Both share an 8-colour aisle rotation (strawberry, sky, lemon, leaf, grape, tangerine, bubblegum, cocoa). An aisle's colour is `aisles[(position-1) % 8]`.
- **Aisle identity.** An aisle is identified by colour, pattern, number and name, never by colour alone.
- **Fonts.** These must be **bundled** TTF/OTF files in `res/font`, subsetted, with their OFL licences shown in-app. Robolectric can't render downloadable fonts. The skeleton uses system families as placeholders.

### 11.2 Components (`ui/theme/Components.kt`)

| Composable | Purpose |
|---|---|
| `PicnicSurface(modifier, shape, color, outlined, shadow, content: BoxScope.()->Unit)` | Every card or row container: fill, ink outline and a **hard offset shadow** (no blur elevation) |
| `PicnicButton(text, onClick, modifier, style: Primary/Secondary/Text, enabled, glyph?)` | Buttons, at least 48 dp tall |
| `PicnicIconButton(glyph, contentDescription, onClick, modifier, enabled, emphasized)` | 48 dp round glyph button |
| `PicnicChip(text, selected, onClick, modifier, leading?)` | Aisle chips and filter chips |
| `PicnicTextField(value, onValueChange, modifier, placeholder, label, singleLine, minLines, maxLines, isError, supportingText, enabled, keyboardOptions, keyboardActions, visualTransformation, trailing?)` | All text input. For the token field, pass `PasswordVisualTransformation`. |
| `PicnicTopBar(title, modifier, subtitle, navigation?, actions, bottom?)` | Title card. `bottom` takes `TripProgress` or tabs. Applies no insets itself. |
| `PicnicSwitch(checked, onCheckedChange, modifier, enabled)` | Settings toggles |
| `PicnicSnackbar(data, modifier)` | Use as `SnackbarHost(host) { PicnicSnackbar(it) }` |
| `SheetHandle(modifier)` | Use as `ModalBottomSheet(dragHandle = { SheetHandle() })` |
| `Glyph(glyph: PicnicGlyph, contentDescription, modifier, tint, size = 24.dp)` | UI glyphs: `Add, Close, Check, Back, More, Camera, Gallery, Mic, Stop, Send, Settings, Aisles, Lists, Edit, Delete, Undo, Refresh, Sparkle, Expand, Collapse, Link, Drag, Key`. The art pass draws them as vectors; the enum may grow. |

### 11.3 Decorative slots (`ui/theme/Decor.kt`)

These have no semantics unless a parameter says otherwise. Each direction's metaphor is listed for orientation (directions A / B / C in `research/directions.md`).

| Slot | Screens place it… | Art pass draws (A / B / C) |
|---|---|---|
| `PicnicBackground(modifier, content)` | as the root of each screen | gingham / star tile / pinstripe |
| `SectionDivider(modifier, major)` | between regions | bunting / gold filigree / perforation |
| `AisleMedallion(number?, aisleIndex, modifier, size)` | in the expanded aisle header (the aisle header ornament) | numbered medallion with a pattern ring |
| `CheckMark(checked, modifier)` | in the row (the row provides the touch target) | star / halo / hanko stamp |
| `TripProgress(done, total, modifier)` | in the top bar's `bottom` slot (progress metaphor) | episode ribbon / charm bracelet / stamp card |
| `EmptyState(title, body, modifier, action?)` | for an empty list or screen | sleepy bee on the hamper, etc. |
| `LoadingSparkle(modifier, label?)` | while the AI is thinking (the label is announced) | bee figure-8 / firefly / sparkle sweep |
| `DrawingSparkle(modifier)` | overlaid on an icon whose bespoke version is being drawn (`ItemIcon` does this) | small twinkle |

### 11.4 Icons, baskets and list pieces (`ItemIcon.kt`, `AisleBasket.kt`, `ListPieces.kt`)

```kotlin
@Composable fun ItemIcon(icon: IconRef, contentDescription: String?, modifier = Modifier,
                         size: Dp = PicnicTheme.dimens.itemIcon, drawing: Boolean = false)
@Composable fun AisleBasket(kind: BasketKind, miniIcons: List<IconRef>, count: Int, modifier = Modifier,
                            size: Dp = PicnicTheme.dimens.basket)
@Composable fun AisleHeader(title: String, number: Int?, aisleIndex: Int, basket: BasketKind, miniIcons: List<IconRef>,
                            count: Int, collapsed: Boolean, cleared: Boolean, onToggle: () -> Unit, modifier = Modifier)
@Composable fun ItemRowCard(name: String, detail: String?, icon: IconRef, checked: Boolean,
                            modifier = Modifier, drawing: Boolean = false)
```

**ItemIcon**

- Uses `LocalIconImages.cached(ref)` first. If that's null, it calls `load(ref)` in `produceState`. If the result is still null, it draws placeholder art keyed by the icon's category.
- `drawing` overlays `DrawingSparkle`.
- Rows pass `contentDescription = null`, because the row text already names the item.

**AisleBasket**

- Draws the container art for `kind`. The art pass will provide one vector or bitmap per basket kind, and the keys' `drawHint`s describe each.
- The first **2–5** of `miniIcons` peek out of the basket, overlapping: fewer if fewer are given, at most 5.
- A `count` badge shows when `count > 0`.
- It is decorative.

**AisleHeader**

- It is the single toggle target for its section.
- **Collapsed:** shows `AisleBasket` (basket, mini icons, count badge) beside the title.
- **Expanded:** shows `AisleMedallion` and the title.
- **Cleared:** shows a "cleared" stamp.
- **Accessibility:** it announces "`<title>`, `<count>` items" with the state description collapsed or expanded.
- Unsorted and the checked section use it with `number = null` and `aisleIndex = -1`.

**ItemRowCard**

- Holds the visual content of a row only. The screen adds click (toggles checked), long-press (opens edit) and `SwipeToDismissBox` (delete with undo).
- A checked item shows the mark, a strike-through and the softer `inkSoft` colour. State is never shown by colour alone.

### 11.5 Previews and accessibility rules

**Previews (app-shell)**

- Every theme component gets an `@Preview` in light and dark. Roborazzi's preview scanner captures every `@Preview` under `earth.cora.picnic` automatically, at `w411dp-h891dp-night-xxhdpi` with SDK 36. `ThemeSampler.kt` exists as the first one.
- Previews must render without the app container. That works through `IconImages.None` placeholder art and `IconCatalog.default`.

**Accessibility**

- Item names use `type.itemName`, never smaller than 16 sp.
- Font scale is supported up to 200 %.
- Touch targets are at least 48 dp.
- Respect `PicnicTheme.motion.reduced`: no decorative loops, and fades of 150 ms or less.
- Nothing flashes faster than 3 Hz.

---

## 12. Error handling summary

| Where | Error | User sees |
|---|---|---|
| ingest (text / transcript) | any `LlmError` | `Failed` with `friendlyMessage`, **Try again** if `retryable`, and **Add as plain lines** |
| ingest (photo) | any `LlmError` | `Failed` with **Try again** |
| any AI call | `NotConfigured` / `Unauthorized` | message and **Open Settings** |
| transcribe | empty text | "Didn't catch that. Try again a little closer to the mic." |
| transcribe | `LlmError` | `Failed` with **Try again** (the recording is kept in memory while the sheet is open) |
| photo decode | `IOException` / `IllegalStateException` | "Couldn't open that photo." |
| aisle sync | `report.error` | snackbar: friendly message plus "N items left in Unsorted" |
| icon drawing | any | nothing (it is a background enhancement). A refusal blocks the name. |
| Test connection | any | inline: ✓ model id and latency, or the friendly message |
| `StateStore.update` | `IOException` | snackbar "Couldn't save. Try again." |

Core's own log messages (`LlmError.message`) are safe to log. The UI shows `friendlyMessage` only.

---

## 13. Threading and cancellation

- **Main safety.** Every core `suspend` function is main-safe:
  - HTTP runs on OkHttp's dispatcher.
  - JSON encoding and decoding of large bodies happens on `Dispatchers.Default`.
  - File I/O runs on the injected IO dispatcher.
- **Store.** `StateStore.update` is serialized by a `Mutex`. Transforms must be pure and fast: no suspension and no I/O inside them.
- **Scopes.**
  - `viewModelScope` is for work tied to a screen: the LLM calls the user waits for, and polling the recording level.
  - `appScope` is for commits, aisle sync and the icon workers.
- **Cancellation.** Cancelling a coroutine cancels its OkHttp call. Closing the Add sheet cancels the in-flight ingest (`AddViewModel.cancel()`).
- **Bitmaps.** Decoding, the icon downscale and JPEG encoding run on `Dispatchers.Default`/`IO`, never in composition. `IconImages.cached` touches memory only.
- **Recording.** It is foreground-only and stops on `Lifecycle.Event.ON_STOP`.

---

## 14. Screenshot testing

The tool is **Roborazzi 1.76.0** with Robolectric 4.17, native graphics, Robolectric **SDK pinned to 36** (`app/src/test/resources/robolectric.properties`) and `--add-opens java.base/jdk.internal.access`. The skeleton runs `recordRoborazziDebug` successfully. Paparazzi 2.0.0-alpha also works, but it is alpha; don't mix the two.

- **Goldens.** They live in `app/src/test/screenshots/`.
  - Previews (app-shell) are named `<Class>.<Function>.<Preview_name>.png`.
  - Explicit screen tests (app-screens) are named `<screen>_<state>_<light|dark>.png`.
- **Explicit screen tests.** Use `@RunWith(AndroidJUnit4::class)`, `@GraphicsMode(NATIVE)`, `@Config(qualifiers = "w411dp-h891dp-night-xxhdpi")` (or without `-night`) and `androidx.compose.ui.test.junit4.v2.createComposeRule`. Render the **stateless** `XxxScreen(state)`, then call `onRoot().captureRoboImage("src/test/screenshots/<name>.png")`.
- **Qualifier order.** `night` comes before the density. The other order throws.
- **States to cover:**
  - List: empty; mixed sections with a collapsed basket, a cleared aisle, a drawing sparkle and checked items.
  - Add sheet: write, thinking, preview with sources, failed with plain lines, and speak with a transcript.
  - Edit sheet.
  - Aisles editor.
  - Settings: token set, and a connection test that failed.
- **Commands.** Record locally with `recordRoborazziDebug`. CI runs `compareRoborazziDebug`, which doesn't fail, and uploads the diffs. Switch CI to `verifyRoborazziDebug` once the art settles.

---

## 15. CI and release (app-shell)

The workflow is `.github/workflows/android.yml`. Copy it verbatim from `research/android_build.md` §6, where it is proven and passes actionlint.

- **Triggers and environment:** push to any branch, tags `v*`, pull requests and `workflow_dispatch`. The runner is ubuntu-24.04 with JDK 21. setup-android installs `platforms;android-37.0 build-tools;36.0.0`. Gradle uses `cache-provider: basic`.
- **Steps:**
  1. `./gradlew -p core test`
  2. `./gradlew :app:compareRoborazziDebug :app:lintDebug :app:assembleDebug -Ppicnic.versionCode=<run_number> -Ppicnic.versionName=<ver>`
  3. Upload the bare APK (`archive: false`) and the reports.
- **Releases:** on a `v*` tag, a release job attaches the APK to a GitHub Release.
- **Signing:** debug builds are signed with the committed `app/debug.keystore`, so successive sideloads update in place. Play will need an AAB signed with a separate upload key from CI secrets. Play rejects debuggable builds.

---

## 16. Proxy contract (proxy owner; consumed by core)

Cora's Cloudflare Worker gives friends a token that is not an OpenAI key. To the app, it looks exactly like OpenAI: base URL `https://picnic.cora.earth/v1` and `Authorization: Bearer pk_<device token>`.

| Route (passthrough to `https://api.openai.com/v1`) | Body | Notes |
|---|---|---|
| `POST /v1/responses` | JSON (`stream` false or true; pass SSE through unbuffered) | Model checked against the allow-list. The proxy may force `store:false`, cap `max_output_tokens` at 16000 and strip `service_tier`. |
| `POST /v1/audio/transcriptions` | multipart, ≤ 26 MB | Check the `model` field. Re-send the form unchanged, keeping the filename (its extension matters). |
| `POST /v1/images/edits` | multipart (also accept JSON), ≤ 20 MB | Model allow-list |
| `GET /v1/models/{id}` | none | Used by "Test connection" |

Policy:

- **Anything else:** 404, using OpenAI's error shape.
- **Auth:**
  - Device tokens are stored only as SHA-256 hashes, in KV (`TOKENS`: hash → `{name, created, disabled}`).
  - A bad or disabled token gets **401** `{"error":{"message":"Invalid device token","type":"invalid_request_error","code":"invalid_api_key"}}`.
  - A model outside `ALLOWED_MODELS` gets **403** `{"error":{…,"code":"model_not_allowed"}}`.
  - Per-token rate limiting (Workers Rate Limiting binding) gets **429** `{"error":{…,"type":"rate_limit_error","code":"rate_limit_exceeded"}}` plus `Retry-After`.
- **Headers:** the real key is the Worker secret `OPENAI_API_KEY`. The client's `Authorization` header is replaced and never forwarded or logged. Pass `x-request-id`, `retry-after`, `content-type` and `openai-processing-ms` back to the client.
- **Long requests:** they can take 20–40 s or more. Stream the upstream body and don't add a timeout shorter than 150 s.
- **Tooling:** a minting script prints a new token once and stores only its hash. Tests run locally with Node 22 (vitest or `node:test`, with a fake upstream `fetch`). `docs/PROXY.md` covers deploy (wrangler), secrets, KV and token management.

The app sends `safety_identifier`, a hashed install id, so OpenAI abuse signals can be attributed per device.

---

## 17. Privacy and Play compliance

- **Permissions:** `INTERNET` and `RECORD_AUDIO` (runtime, foreground only). There is **no `CAMERA` permission**, because the system camera intent is used. `<uses-feature>` marks camera and microphone as `required="false"`. The photo picker needs no permission. The `<queries>` entry for `IMAGE_CAPTURE` is present.
- **Data sent off the device:** text, photos and audio go to OpenAI, or to Cora's proxy, only when the user asks. Requests use `store: false`. Nothing else leaves the device, and there are no analytics.
- **Backups:** only `state.json` is backed up.
- **Play:** a later Play release needs a privacy policy URL (because of `RECORD_AUDIO`) and a Data safety form that discloses the processing.
- **Security:** no secrets in the repo or APK, ever. `*.env` and `.dev.vars*` are gitignored.
- **Legal and IP:** OFL fonts are bundled with their licences. Art uses only generic genre motifs (see the IP rules in `research/directions.md`), with no real brand packaging.
