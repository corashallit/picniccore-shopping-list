# Picnic List: architecture

Status: v1 contract, 2026-10-01, revised for client amendments A1 (no model settings) and A2 (icon matching cascade). Package and applicationId base: `earth.cora.picnic`. App name (placeholder): "Picnic List".

This document is the contract between the people (or agents) building the app in parallel. The Kotlin stub files listed in §3.2 are **normative**: they compile today, and their signatures are the interfaces. Bodies marked `TODO("<owner>: …")` are for the owner to fill in. If this document and a stub disagree, the stub wins. Fix both, and tell the other owners.

**Changing a contract.** You may add to a contract freely: new optional parameters with defaults, new functions, new fields with defaults. Renaming or removing anything, or changing what an existing call means, needs a note to every consumer listed in §3.2. Persisted names (anything in `state.json`, icon keys, basket keys, aisle-id and name normalization) also need a schema migration (§6.4).

Research this rests on (not in the repo): the OpenAI brief (`research/openai_api.md`, live-verified), the Android build brief (`research/android_build.md`), the icon catalog (`research/icon_catalog.json`) and the art directions (`research/directions.md`, concept renders in `design/concepts/`).

---

## 1. What we're building

An Android grocery list for one friend of Cora's, in Ontario, Canada. It is sideloaded first; Play internal testing may come later, so the build stays Play-compliant (targetSdk 36, minimal permissions, no secrets in the APK).

1. **Aisle sorting.** The user keeps a free-text list of their store's aisles, one per line, in walking order. Every item is assigned to one aisle (or Unsorted), and the list is shown grouped by aisle in walking order.
2. **Free-form text.** Input such as "ingredients for pancakes from scratch" goes to a reasoning model with web search, which returns individual, already-categorized items. A plain list such as "milk, 2 lemons, eggs" takes the same path.
3. **Photo of a handwritten list.** The same model transcribes it into items.
4. **Voice.** A recording is transcribed and the transcript is shown. The transcript then goes through the same text pipeline.
5. **Theme.** A rich "picniccore × vintage 90s magical-girl" look. The art direction is still being chosen, so this round builds a clean **theme layer** with tasteful placeholder art. It must never look like Material defaults.
6. **Item icons.** Every item has a tiny icon from a bundled library of 235 catalog keys (the art pass supplies the images). Items without a fitting icon get a bespoke one **drawn live** with `gpt-image-2.5-flare` and cached by item name.
7. **Collapsible aisles with baskets.** A collapsed aisle header draws a little basket holding 2–5 overlapping mini icons of its items, plus a count badge. Each aisle has a basket kind (16 kinds), assigned by the fast model with a keyword fallback.

Units are metric (g, kg, mL, L), and Canadian specifics apply (4 L bagged milk).

### 1.1 Client amendments (override the original spec)

- **A1. No model settings.** There are no model-name fields, reasoning-effort choices, "Best/Budget" presets or web-search toggle anywhere a user can reach. Models are fixed defaults maintained by the developers in **one place**, core's `ModelDefaults` (§7.1). Settings keep only the connection (base URL + access token or own key, with "Test connection") and the "Draw new icons with AI" toggle. The proxy gets an optional `MODEL_MAP` so maintainers can upgrade models server-side without shipping an APK (§16.2).
- **A2. Icon matching cascade.** Every item that doesn't already carry an icon from the ingest call (typed items, the offline raw-lines fallback, renames) goes through: (1) a local match (normalized name against catalog keys, labels and aliases, plus learned icon overrides), then (2) an LLM decision through the `IconDecider` interface (gpt-6-luna today; OpenAI's Decisions API is the intended future implementation, §8.6), then (3) live generation of a bespoke icon. Each step falls through on failure or offline to the category fallback icon. The ingest call's own icon choice counts as step 2 for the items it returns (§8.2).

---

## 2. Repository layout and build

```
picniccore-shopping-list/
├─ settings.gradle.kts          root build: includeBuild("core"), include(":app")
├─ build.gradle.kts             plugin versions declared once (apply false); NO kotlin.jvm/android here
├─ gradle.properties            daemon/caching/config-cache (AGP 9 defaults not re-declared)
├─ gradle/libs.versions.toml    THE version catalog (shared with core/)
├─ gradlew, gradlew.bat, gradle/wrapper/   Gradle 9.8.0 wrapper (native 9.8.0 jar, pinned distribution sha256)
├─ local.properties             sdk.dir=/opt/android-sdk   (gitignored)
├─ core/                        STANDALONE Gradle build, plain Kotlin/JVM (bytecode 17 on JDK 21)
│  ├─ settings.gradle.kts       imports ../gradle/libs.versions.toml as "libs"
│  ├─ build.gradle.kts          kotlin.jvm + serialization + application (CLI); live tests gated
│  ├─ gradle.properties         used when building standalone (-p core)
│  └─ src/main/kotlin/earth/cora/picnic/core/{model,store,catalog,llm,assistant,cli}
│     src/main/resources/icon_catalog.json, prompts/*.txt
│     src/test/kotlin/…, src/test/resources/fixtures/…
├─ app/                         Android application (AGP 9.4.1 built-in Kotlin, Compose, Material3 heavily themed)
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

Toolchain: AGP 9.4.1 with built-in Kotlin. **Never** apply `org.jetbrains.kotlin.android` or kapt. Kotlin is 2.4.20 (compose and serialization plugins; `kotlin.jvm` is declared only inside core's separate build). Compose BOM is 2026.09.00, which requires `compileSdk 37`. SDK levels are `targetSdk 36` (Play requirement since 2026-08-31) and `minSdk 26`. The JDK is 21, with bytecode 17 (core: `options.release=17` + `-Xjdk-release=17`; no `jvmToolchain(17)`, there is no JDK 17). The rationale for every value is in `research/android_build.md`.

Commands (always `./gradlew`; the system gradle 8.14 cannot evaluate AGP 9, and never run `gradle wrapper` in the repo):

| What | Command |
|---|---|
| core build + tests (standalone) | `./gradlew -p core build` |
| core live tests (real API) | `OPENAI_API_KEY=… ./gradlew -p core test -Plive=true` |
| core CLI | `./gradlew -p core run --args="ingest 'ingredients for pancakes from scratch'"` |
| debug APK | `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/picnic-list-debug.apk` |
| sideload APK (what CI ships; not debuggable) | `./gradlew :app:assembleSideload` → `app/build/outputs/apk/sideload/picnic-list-sideload.apk` |
| app unit + screenshot tests | `./gradlew :app:testDebugUnitTest` / `:app:recordRoborazziDebug` / `:app:verifyRoborazziDebug` / `:app:compareRoborazziDebug` |
| lint (CI gate) | `./gradlew :app:lintDebug` |

Sandbox-only flags must **never** be committed. Use `-I <scratch>/robolectric-mirror.init.gradle` for Robolectric jar downloads. The Maven Central mirror lives in `~/.gradle/init.d`.

Verified on this skeleton (2026-10-01): `./gradlew -p core build` (9 contract tests green), `./gradlew :app:assembleDebug` (APK: compileSdk 37, targetSdk 36, permissions INTERNET + RECORD_AUDIO only, `icon_catalog.json` packaged), `:app:recordRoborazziDebug` (ThemeSampler previews rendered).

---

## 3. Ownership and interfaces

### 3.1 Files per implementer

| Owner | Owns (creates/edits) |
|---|---|
| **lead** | `docs/ARCHITECTURE.md`, `README.md`. Root Gradle files are handed to **app-shell** after the skeleton. |
| **core** | Everything under `core/` (sources, resources incl. `prompts/*.txt`, tests, `core/build.gradle.kts`, `core/settings.gradle.kts`, the CLI), plus `docs/PROMPTS.md`. |
| **app-shell** | Root `settings.gradle.kts`/`build.gradle.kts`/`gradle.properties`/`gradle/**`/`gradlew*`/`.gitignore`; `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/debug.keystore` (never change it); `app/src/main/AndroidManifest.xml`, `app/src/main/res/**` (except `values/strings_screens.xml`), `app/src/main/assets/**` (icons, style reference, fonts), the launcher icon.<br>Kotlin: `PicnicApp.kt`, `AppContainer.kt`, `MainActivity.kt`, `settings/**` (DataStore settings, Keystore token store), `platform/**` (captures, photo prep, voice recorder, TokenCipher), `icons/**` (IconRepository, IconImages, bespoke cache, decider batcher, live drawing queue), `ui/theme/**` (tokens, components, decor slots, ItemIcon, AisleBasket, list pieces, glyphs, placeholder art, previews).<br>Tests under `app/src/test/kotlin/earth/cora/picnic/{settings,platform,icons,ui/theme}/**` and the theme preview goldens.<br>Also `.github/workflows/android.yml` and `docs/BUILDING.md`. |
| **app-screens** | `ui/PicnicAppRoot.kt`, `ui/nav/**`, `ui/list/**`, `ui/add/**`, `ui/edit/**`, `ui/aisles/**`, `ui/settings/**`, `ui/common/**` (screens, ViewModels, UI-state classes, navigation), `res/values/strings_screens.xml`.<br>Tests under `app/src/test/kotlin/earth/cora/picnic/ui/{list,add,edit,aisles,settings,nav,common}/**`, including the screen screenshot tests and their goldens in `app/src/test/screenshots/`. |
| **proxy** | `proxy/**` and `docs/PROXY.md`. |

`gradle/libs.versions.toml` is shared **append-only**: any owner may add an entry. Changing a version is app-shell's call, and core must still build standalone afterwards. Nobody but core edits `core/build.gradle.kts`.

### 3.2 Contract files (normative stubs in the skeleton)

| Contract file | Provider | Consumers |
|---|---|---|
| `core/…/model/Model.kt` (domain model, `IconFit`, `IconOverride`, `ResolvedIcon`, `BasketKind`, `IconRef`, `SectionIds`) | core | app-shell, app-screens |
| `core/…/model/Names.kt` (`foldText`, `slugify`, `normalizeName`): implemented | core | all |
| `core/…/model/Aisles.kt` (`Aisles.parse`, `DEFAULT_TEXT`, `StoreProfile.aisles()`): implemented | core | all |
| `core/…/model/Grouping.kt` (`ListGrouping.group`, `AisleSection`, `GroupedList`) | core | app-screens |
| `core/…/model/StateOps.kt` (every state transition, `localIcon`, `itemsNeedingIconDecision`, `ingestContext`) | core | app-shell (icons), app-screens |
| `core/…/store/StateStore.kt` (`StateStore`, `JsonFileStateStore`, `InMemoryStateStore`) | core | app-shell (container), app-screens (tests) |
| `core/…/catalog/IconCatalog.kt` (`IconCatalog`, `BasketCatalog`, `match`, `displayRef`, `fallbackFor`) | core | app-shell, app-screens |
| `core/…/llm/ModelDefaults.kt` (`ModelDefaults`, `ModelSet`, model specs): **the one place for models** | core | core only (app never configures models) |
| `core/…/llm/LlmConfig.kt` (`LlmConfig` + `normalizeBaseUrl`, `LlmTimeouts`, `ReasoningEffort`, `ConfigSource`, `TokenSource`) | core | app-shell (settings, container), app-screens (settings UI validation) |
| `core/…/llm/LlmError.kt` | core | app-shell, app-screens |
| `core/…/llm/OpenAiClient.kt` | core | app-shell (container constructs it) |
| `core/…/assistant/GroceryAssistant.kt` (+ input/result types, `assignBasketsOrGuess`) | core | app-shell, app-screens |
| `core/…/assistant/IconDecider.kt` (`IconDecider`, `IconDecision`, `resolve`, `ResponsesIconDecider`) | core | app-shell (icons, container) |
| `core/…/assistant/{OpenAiGroceryAssistant,OfflineParser,AisleSync}.kt` | core | app-shell (container), app-screens |
| `app/…/AppContainer.kt`, `PicnicApp.kt` | app-shell | app-screens |
| `app/…/settings/AppSettings.kt` (`AppSettings`, `SettingsRepository`, `InMemorySettingsRepository`, `maskToken`), `settings/TokenStore.kt` | app-shell | app-screens |
| `app/…/icons/IconRepository.kt` (`IconRepository`, `IconImages`, `NoOpIconRepository`) | app-shell | app-screens, theme |
| `app/…/platform/{Captures,PhotoPreparer,VoiceRecorder (AudioRecorder),TokenCipher}.kt` | app-shell | app-screens |
| `app/…/ui/theme/*` (tokens, `PicnicTheme`, components, decor slots, `ItemIcon`, `AisleBasket`, `AisleHeader`, `AisleSectionTitle`, `AisleSectionRow`, `ItemRowCard`, `Glyph`) | app-shell | app-screens |
| `app/…/ui/PicnicAppRoot.kt` (`PicnicAppRoot(container)`) | app-screens | app-shell (MainActivity) |
| HTTP API surface: `/responses`, `/audio/transcriptions`, `/images/edits`, `/models/{id}`; `MODEL_MAP` | proxy | core |

### 3.3 Hand-off points between implementers (the seams that must line up)

| From → to | Seam | Rule |
|---|---|---|
| app-screens → core | `store.update { <StateOps> }` | All state changes go through StateOps inside `StateStore.update`, in `appScope`. |
| app-screens → core | `assistant.ingest/transcribe/testConnection`, `OfflineParser.parse`, `aisleSync.sync/sortItems` | Catch `LlmError` only; show `friendlyMessage`. |
| app-screens → app-shell | `icons.submit(items)` after every commit and every rename; `icons.redraw(name)`; `icons.forgetBespoke(name)` after `setItemIcon`; `icons.busy` for row sparkles | Never wait on icons. |
| app-shell → core | `iconDecider.decide`, `assistant.drawIcon`, `store.update { applyIconDecisions / attachCustomIcon }`, `itemsNeedingIconDecision()` | The icon cascade, §8. |
| app-shell → app-screens | `PicnicAppRoot(container)` inside `PicnicTheme` + `LocalIconImages`; theme components | Screens use only theme tokens/components (§11). |
| app-shell → core | `ConfigSource { settings.llmConfig() }`, `TokenSource { settings.token() }` | Read before every call; the token never leaves `token()`. |
| core → proxy | Base URL + bearer token; the four routes; model ids from `ModelDefaults` | The proxy may rewrite model ids (`MODEL_MAP`); the client absorbs parameter mismatches with its one-shot downgrade retry. |

---

## 4. Domain model (core, `earth.cora.picnic.core.model`)

All of these types are immutable `@Serializable` data classes. The JSON field names are persisted.

```kotlin
const val STATE_SCHEMA_VERSION = 1
enum class ItemSource { TYPED, AI_TEXT, PHOTO, VOICE }                         // persisted by name
enum class IconFit { EXACT /*"exact"*/, APPROXIMATE /*"approximate"*/, PENDING /*"pending"*/ }

data class GroceryItem(
    val id: String,                 // UUID
    val name: String,               // "All-purpose flour"
    val quantity: String? = null,   // "2", "1 kg", "4 L bag" (metric)
    val note: String? = null,
    val aisleId: String? = null,    // null = Unsorted; unknown id also renders as Unsorted
    val icon: String,               // valid catalog key; DEFAULT_ICON while PENDING
    val iconFit: IconFit = IconFit.EXACT,
    val customIconId: String? = null,   // bespoke icon drawn on this device
    val checked: Boolean = false,
    val addedAt: Long,              // epoch ms
    val source: ItemSource = ItemSource.TYPED,
    val manuallyPlaced: Boolean = false,
)
data class GroceryList(val id: String, val title: String, val createdAt: Long,
                       val items: List<GroceryItem> = emptyList(),      // insertion order
                       val collapsedAisles: Set<String> = emptySet())   // section ids, persisted per list
data class StoreProfile(val name: String = "My store", val aislesText: String = Aisles.DEFAULT_TEXT,
                        val aisleBaskets: Map<String, BasketKind> = emptyMap())
data class Correction(val name: String /* normalizeName */, val aisleId: String, val at: Long)
data class IconOverride(val name: String /* normalizeName */, val icon: String, val at: Long)
data class AppState(val version: Int = STATE_SCHEMA_VERSION, val lists: List<GroceryList>,
                    val currentListId: String, val store: StoreProfile = StoreProfile(),
                    val corrections: List<Correction> = emptyList(),       // oldest first, cap 200
                    val iconOverrides: List<IconOverride> = emptyList())   // oldest first, cap 200
data class Aisle(val id: String, val name: String, val position: Int)      // derived, 1-based position
data class ResolvedIcon(val icon: String, val fit: IconFit)               // not persisted
data class IconRef(val key: String, val customIconId: String? = null)
enum class BasketKind(val key: String) { WICKER_PICNIC, BREAD_BASKET, FRUIT_CRATE, BERRY_PUNNET,
    WIRE_BASKET, EGG_BASKET, MILK_CRATE, COOLER, ICE_BUCKET, BENTO_BOX, JAR_SHELF, CANVAS_TOTE,
    FLOWER_BUCKET, BOTTLE_CARRIER, CANDY_JAR, VANITY_CASE }                 // keys = catalog "baskets"
object SectionIds { UNSORTED = "_unsorted"; CHECKED = "_checked" }
const val UNSORTED_WIRE_ID = "unsorted"                                     // LLM enum literal only
```

There is a single store profile shared by all lists. "Unsorted" is represented only as `aisleId == null` (or an id that is not a current aisle). The literal `"unsorted"` exists only on the wire to the model.

`IconFit` meanings (the icon cascade, §8.2):

- **EXACT:** the catalog icon depicts this item (local match, learned override, ingest `"exact"`, or an IconDecider match).
- **APPROXIMATE:** only a near neighbour or category icon fits (ingest `"approximate"`, or IconDecider `"none"`). A candidate for a live-drawn bespoke icon.
- **PENDING:** no local match yet and the IconDecider has not answered (still running, or offline). `icon` is `IconCatalog.DEFAULT_ICON` (`paper_bag`). `IconRepository.resumePending()` retries these on the next process start and when a token is added.

### 4.1 Names and ids (implemented; persisted semantics)

- `foldText(s)` lowercases, folds accents (`œ→oe`, `æ→ae`, `ß→ss`), drops apostrophes, and reduces the result to `[a-z0-9]` runs separated by single spaces.
- `slugify(s)` = `foldText(s)` with spaces replaced by `-`. For example, "Aisle 2 - Baking & spices" → `aisle-2-baking-spices`.
- `normalizeName(name)` = `foldText(name)` plus a conservative singularization of the **last word only**, applied when the word is longer than 3 characters: `ies→y`; `oes→o`; words ending in `ss`, `us` or `is` are left alone; otherwise a trailing `s` is dropped. This is **the** key for dedupe, corrections, icon overrides, the IconDecider batch and the bespoke-icon cache.
- `Aisles.parse(text)`: one aisle per non-blank line. Lines starting with `#` are comments. A leading bullet (`- `, `* `, `• `, `1. `, `2) `) is stripped. `id = slugify(name)`; an empty slug becomes `aisle-<position>`, and the reserved slug `unsorted` becomes `unsorted-aisle`. Duplicates get `-2`, `-3` and so on, skipping ids already used. Positions are 1-based. The default text is the 14-aisle Ontario store used in the benchmark; its ids (`produce`, `bakery`, `deli-cheese`, …, `bulk-natural-foods`) match the research fixtures exactly.

### 4.2 Grouping (`ListGrouping.group(list, aisles, basketFor)`)

`group` returns a `GroupedList(sections, total, remaining)`. The rules:

- **Which section an item goes in:** its `aisleId` if that id is a current aisle, otherwise UNSORTED.
- **Section order:** UNSORTED first (only when it has unchecked items), then the aisles in walking order, then CHECKED last (only when something is checked). Unsorted goes first because those items need the user's attention.
- **Which aisle sections appear:** an aisle section is present only when it has at least one item. If all of its items are checked, the section is still present, with empty `items` and `cleared = true`.
- **Section contents:** UNSORTED and aisle sections list their **unchecked** items, oldest first. CHECKED lists every checked item, in aisle order and then oldest first. `checkedCount` is the number of the aisle's items now listed under CHECKED.
- **Basket and collapsed state:** `basket` = `basketFor(aisle)` for aisles and `BasketKind.DEFAULT` (wicker hamper) for the special sections. `collapsed` = whether the section id is in `list.collapsedAisles`. Every section starts expanded.

### 4.3 State operations (`StateOps.kt`, pure)

The app changes state **only** through these functions, always inside `store.update { … }`. They never do I/O, treat unknown ids as a no-op, and return the *same instance* when nothing changed.

| Function | Semantics |
|---|---|
| `StateOps.initialState(listId, now)` | One empty list "Groceries", default store |
| `currentList()`, `list(id)`, `findItem(itemId)`, `updateList(id) {}` | accessors |
| `addItems(listId, items)` | Append. Drop any item whose `normalizeName` matches an unchecked item on the list or an earlier item in the batch. Callers find the ids actually added with `findItem` on the returned state. |
| `toggleChecked`, `removeItem`, `restoreItem(listId, item, index)` | For undo, the caller captures the item and its index before calling `removeItem`. |
| `editItemDetails(listId, itemId, name, quantity, note, catalog)` | Name (blank = keep), quantity and note (blank = null). **If `normalizeName` changes**, the icon is re-resolved locally (cascade step 1): `customIconId = null`; `localIcon(newName)` → EXACT, else `DEFAULT_ICON` + PENDING. The caller then calls `icons.submit(listOf(updated))`. |
| `moveItemToAisle(listId, itemId, aisleId?, now)` | Sets `manuallyPlaced`. When `aisleId != null`, records or replaces a `Correction(normalizeName(name), aisleId, now)`. Capped at 200. |
| `setItemIcon(listId, itemId, key, now)` | icon = key, iconFit = EXACT, customIconId = null; records or replaces an `IconOverride(normalizeName(name), key, now)`. Capped at 200. Invalid key = no-op. The caller also calls `icons.forgetBespoke(name)`. |
| `clearChecked`, `setSectionCollapsed(listId, sectionId, collapsed)` | |
| `newList` (it becomes current), `switchList`, `renameList`, `deleteList` | `deleteList` never removes the last list; if the deleted list was current, a neighbouring list becomes current. |
| `setStoreName(name)` | Aisles screen |
| `setAislesText(text, guessBasket)` | Replaces the text. Prunes baskets for vanished ids and fills missing baskets with the heuristic. **Does not touch items.** |
| `setAisleBaskets(map)` | Merges the map, ignoring ids that are not current aisles. |
| `applyAisleAssignments(itemId → aisleId?)` | Applies to all lists. Skips items that were manually placed into an aisle that still exists. |
| `applyIconDecisions(normalizedName → ResolvedIcon)` | Applies to all lists, only to items still PENDING (a user choice made meanwhile wins). |
| `attachCustomIcon(normalizedName, customIconId, overwrite)` | Applies to all lists, to every item with that normalized name. Without `overwrite`, only items whose `customIconId` is null are changed. |
| `localIcon(name, catalog)` | Cascade step 1: newest `IconOverride` for the normalized name (if still a catalog key) ?: `catalog.match(name)?.key` ?: null. |
| `iconOverrideMap()` | Newest override per normalized name. |
| `itemsNeedingIconDecision()` | One `NamedItem(id = normalizedName, name = display name)` per distinct normalized name among PENDING items (all lists). |
| `itemsNeedingAisle()` | Items with a null or vanished aisle, excluding those the user deliberately moved to Unsorted. |
| `ingestContext(listId)` | Aisles, the newest valid corrections (≤ 60), the names of unchecked items, and `iconOverrideMap()`. |

---

## 5. Icon and basket catalog (core, `earth.cora.picnic.core.catalog`)

The catalog is `core/src/main/resources/icon_catalog.json`, a verbatim copy of `research/icon_catalog.json` (validated by `research/validate_icon_catalog.py`). It contains:

- 20 `categories` `{key,label,fallbackIcon}`; every fallback is a `<category>_generic` key
- 235 `icons` `{key,label,category,aliases[],drawHint}` (English and French aliases)
- 16 `baskets` `{key,label,drawHint,keywords[]}`

The joined keys come to about 2.2k characters, well inside the strict-schema enum budget (≤ 1000 enum values, ≤ 15k chars for an enum over 250 values).

```kotlin
class IconCatalog(val file: CatalogFile) {
  val keys: List<String>                       // ingest strict-schema enum, catalog order
  val genericKeys: Set<String>; fun isGeneric(key): Boolean      // the 20 *_generic fallbacks
  val decidableKeys: List<String>              // keys minus generics: the IconDecider enum (+ "none")
  operator fun get(key): IconEntry?; operator fun contains(key): Boolean
  fun categoryOf(iconKey): IconCategory?
  fun fallbackFor(iconKey): String             // "lemon" -> "fruit_generic"; generic -> itself; DEFAULT/unknown -> DEFAULT_ICON
  fun fallbackForCategory(categoryKey?): String
  fun sanitize(key: String?): String           // valid key or DEFAULT_ICON ("paper_bag")
  fun match(name: String): IconEntry?          // cascade step 1, catalog part (core implements)
  fun displayRef(item, busy: Boolean): IconRef // see §8.5 (implemented)
  val baskets: BasketCatalog
  companion object { const val DEFAULT_ICON = "paper_bag"; fun load(); fun parse(json); val default /* lazy singleton */ }
}
class BasketCatalog(entries) { fun entry(kind); fun guess(aisleName): BasketKind; fun guess(aisle) }
```

**`match(name)` contract** (precision over recall: when unsure, return null and let the IconDecider decide):

1. A leading quantity ("2 lemons", "500 g flour") is ignored.
2. **Exact:** `normalizeName(name)` equals `normalizeName` of an icon's key (underscores as spaces), label or any alias. "Lemons" → `lemon`, "Lait" → `milk_carton`, "Oat milk" → `plant_milk`.
3. **Head noun:** the longest key/label/alias (normalized, ≥ 3 characters) that is a whole-word **suffix** of the normalized name. "Meyer lemons" → `lemon`, "organic baby spinach" → `spinach`, "Lindt dark chocolate" → `chocolate_bar`. Ties go to catalog order.
4. Otherwise null.

`IconCatalog.load()` reads the classpath resource. AGP packages Java resources from the core jar into the APK, and they are on the classpath under Robolectric too (verified: `icon_catalog.json` is in the debug APK).

`BasketCatalog.guess` ports `basket_for` from `validate_icon_catalog.py`: fold the aisle name and pad it with spaces; find each keyword's earliest whole-word position; the earliest position wins, ties go to the longest keyword; no match returns `WICKER_PICNIC`. The 43 sample names in the validator become a core unit test. A core test asserts that `BasketKind.entries` keys equal the catalog's basket keys (`ContractTest`). To add a basket kind, add it to both places.

---

## 6. Persistence (core `store` + app)

### 6.1 Files on device

| Path | Owner | Contents | Backed up? |
|---|---|---|---|
| `filesDir/state.json` | core `JsonFileStateStore` | `AppState` (lists, store, corrections, icon overrides) | **Yes**: the only entry on the allow-list (`data_extraction_rules.xml`, `backup_rules.xml`) |
| `filesDir/datastore/settings.preferences_pb` | app-shell `DataStoreSettingsRepository` | `base_url`, `draw_icons`, `install_id`, `token_hint` | no |
| `noBackupFilesDir/token.bin` | app-shell `KeystoreTokenStore` | TokenCipher blob `base64(iv):base64(ct)` | no (and undecryptable elsewhere) |
| `filesDir/icons/custom/<customIconId>.webp`, `filesDir/icons/index.json` | app-shell `IconRepository` | bespoke icons (192 px) and the name index | no |
| `cacheDir/captures/*.jpg`, `cacheDir/voice/*.m4a` | app-shell platform | transient capture files; deleted after use | no |

After a backup restore, items may carry a `customIconId` whose file isn't there: `IconImages.load` then falls back to the bundled key (render order, §8.5), so nothing breaks.

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
- **Writing:** encode with `CoreJson.json`; write `state.json.tmp` in the same directory; `FileOutputStream.fd.sync()`; `Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)`. If the write fails, `update` throws `IOException`; memory and observers keep the previous state. The UI shows the snackbar "Couldn't save."
- **Unreadable JSON:** rename the file to `state.json.corrupt-<ms>` and start from `initial()`. Never crash on a bad file.
- **Missing file:** start from `initial()`; nothing is written until the first update.

### 6.3 Format (example)

```json
{
  "version": 1,
  "currentListId": "6f1c…",
  "lists": [{
    "id": "6f1c…", "title": "Groceries", "createdAt": 1759330000000,
    "items": [
      {"id": "a1…", "name": "Lemons", "quantity": "2", "aisleId": "produce", "icon": "lemon",
       "iconFit": "exact", "checked": false, "addedAt": 1759330100000, "source": "AI_TEXT", "manuallyPlaced": false},
      {"id": "b2…", "name": "Gochujang", "quantity": "500 g", "aisleId": "aisle-3-pasta-rice-international",
       "icon": "condiments_generic", "iconFit": "approximate", "customIconId": "c-1a2b3c4d5e-t2xk9q",
       "checked": false, "addedAt": 1759330100001, "source": "AI_TEXT", "manuallyPlaced": false},
      {"id": "c3…", "name": "Dish rack", "icon": "paper_bag", "iconFit": "pending",
       "checked": false, "addedAt": 1759330200000, "source": "TYPED", "manuallyPlaced": false}
    ],
    "collapsedAisles": ["bakery"]
  }],
  "store": {"name": "My store", "aislesText": "Produce\nBakery\n…", "aisleBaskets": {"produce": "fruit_crate"}},
  "corrections": [{"name": "oat milk", "aisleId": "dairy-eggs", "at": 1759330000000}],
  "iconOverrides": [{"name": "nooch", "icon": "spice_jar", "at": 1759330300000}]
}
```

`CoreJson.json` uses `ignoreUnknownKeys`, `encodeDefaults`, `explicitNulls = false` (null fields are omitted) and `coerceInputValues`. A test asserts that a minimal file (no `version`, no `iconOverrides`, no `iconFit`) decodes with defaults.

### 6.4 Versioning

`AppState.version` is the schema version, and a file with no `version` field counts as version 1. On load:

- If `version < STATE_SCHEMA_VERSION`, run `StateMigrations.migrate(raw JsonObject, from)` before decoding. It runs one `(JsonObject) -> JsonObject` step per version.
- If `version > current` (a downgrade), decode leniently. Copy the file to `state.json.v<version>.bak` before the first write.

Nothing has shipped yet, so v1 includes `IconFit.PENDING` and `iconOverrides`. From the first sideloaded APK on, to change the schema: bump `STATE_SCHEMA_VERSION`, add a migration step, add a fixture test that reads an old file. Adding a field with a default needs no migration; renaming, removing or changing the meaning of one does. Persisted names (field names, enum serial names, icon and basket keys, the slug and normalize rules) never change without a migration.

---

## 7. LLM layer (core `llm` + `assistant`)

### 7.1 Models: `ModelDefaults` is the one place (amendment A1)

`core/src/main/kotlin/earth/cora/picnic/core/llm/ModelDefaults.kt` is **the single source of truth** for models and their request details. There is no user setting, no persisted override and no preset list. Maintainers change a value there and ship an APK, or remap a model server-side with the proxy's `MODEL_MAP` (§16.2).

| Use | `ModelDefaults` | Request details |
|---|---|---|
| Ingest (text, photo, transcript) | `SMART` = `gpt-5.6-terra`, effort `low`, `max_output_tokens` 12000 | `web_search` for text and transcript input (`WEB_SEARCH`: `search_context_size` `low`, `user_location` CA / Ontario / America/Toronto); none for photos |
| recategorize, assignBaskets, IconDecider | `FAST` = `gpt-6-luna`, effort `none`, `max_output_tokens` 4000 | no tools |
| Transcription | `TRANSCRIBE` = `gpt-transcribe`, `languages[]=en`, prompt "A person dictating a grocery list in Ontario, Canada.", ≤ 100 keywords | |
| Live icons | `IMAGE` = `gpt-image-2.5-flare`, quality `low`, `1024x1024`, background `transparent`, `webp`, compression 80 | |

`ModelDefaults.models: ModelSet` bundles them. `OpenAiGroceryAssistant(client, catalog, models = ModelDefaults.models)` and `ResponsesIconDecider(client, catalog, model = ModelDefaults.FAST)` take them as defaulted parameters only so that tests and the developer CLI can vary them. App code never passes them.

**Connection config (the only user-editable part):**

```kotlin
data class LlmConfig(val baseUrl: String = DEFAULT_BASE_URL, val safetyIdentifier: String? = null) {
    companion object { const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
                       fun normalizeBaseUrl(input: String): String? }   // implemented
}
object LlmTimeouts { CONNECT 15 s; INGEST 120 s; FAST 45 s; TRANSCRIBE 60 s; IMAGE 120 s; TEST_CONNECTION 20 s }
fun interface ConfigSource { suspend fun config(): LlmConfig }
fun interface TokenSource { suspend fun token(): String? }
```

`normalizeBaseUrl` trims, drops a trailing `/`, and requires an absolute `https://` URL with a host and no query or fragment (`http://` only for `localhost`, `127.0.0.1`, `[::1]`: tests and local proxy development). Both the Settings UI and `OpenAiClient` use it. **The token is never inside `LlmConfig`.** `ConfigSource.config()` and `TokenSource.token()` are read before every call, so Settings changes apply immediately.

### 7.2 `OpenAiClient` (core-internal behaviour; the app only constructs it)

```kotlin
class OpenAiClient(config: ConfigSource, token: TokenSource) {
    suspend fun responses(body: JsonObject, timeout: Duration = LlmTimeouts.INGEST): ApiResult
    suspend fun transcription(model: TranscriptionModel, audio: ByteArray, fileName: String,
                              keywords: List<String>, timeout: Duration = LlmTimeouts.TRANSCRIBE): ApiResult
    suspend fun imageEdit(model: ImageModel, prompt: String, reference: ByteArray, referenceFileName: String,
                          timeout: Duration = LlmTimeouts.IMAGE): ImageResult
    suspend fun model(id: String, timeout: Duration = LlmTimeouts.TEST_CONNECTION): ApiResult
}
data class ApiResult(val json: JsonObject, val requestId: String?, val latencyMs: Long)
class ImageResult(val bytes: ByteArray, val requestId: String?, val latencyMs: Long)
```

- **Endpoints:** all are relative to `baseUrl`, and auth is `Authorization: Bearer <token>`. This works the same against api.openai.com and Cora's proxy (§16).
- **Not configured:** a missing or blank token, or a base URL that fails `normalizeBaseUrl`, throws `LlmError.NotConfigured` without sending a request.
- **Implementation:** OkHttp 5 stays an implementation detail of core (`implementation`, not `api`). Calls go through `enqueue` inside `suspendCancellableCoroutine`, and `invokeOnCancellation { call.cancel() }` cancels them. The per-call timeout is the OkHttp call timeout; connect timeout `LlmTimeouts.CONNECT`. Large JSON encode/decode runs on `Dispatchers.Default`. `http.newCall` runs on `Dispatchers.IO`, because the first call builds the lazy `OkHttpClient` (platform TLS, system CA store) and callers are often on the main thread.
- **Logging:** never log the token, the `Authorization` header or request bodies. Log the method, path, status, `x-request-id` and latency.
- **Retries:** up to 2 extra attempts on 429 rate limits (**not** `insufficient_quota` or the spend/usage-limit codes) and on 500/502/503/504 (**not** the proxy's `upstream_auth_failed` / `proxy_misconfigured`). Back off 1 s, then 2 s, with up to 0.5 s of jitter. Honour `Retry-After` up to 20 s. **`imageEdit` is never retried automatically:** a drawing is slow and billed, and its caller (the app's icon queue, §8.3) retries it at most once itself.
- **Model capabilities** (`ModelCapabilities`, internal, keyed by the model id being sent; with fixed models this mostly matters when the proxy remaps them):
  - `gpt-5.6*` and `gpt-6*`: send explicit prompt caching (`prompt_cache_options: {"mode":"explicit"}` plus `prompt_cache_breakpoint: {"mode":"explicit"}` on the last stable `input_text` part). Every other family gets **no** cache fields (gpt-5.5/5.4 answer 400 `invalid_parameter`).
  - `gpt-6.1-sol*` and `gpt-6-astra*` don't accept `none`: send `low`.
  - `gpt-5-mini|nano`: `minimal` instead of `none`, and no `web_search` with `minimal`.
  - **Never send `temperature`.** Always send `store: false`, and `safety_identifier` when `LlmConfig.safetyIdentifier` is set.
- **One-shot capability downgrades** on HTTP 400 from `/responses`, each tried at most once per call:
  - `param == "reasoning.effort"`: parse "Supported values are: …" and retry with the nearest supported value, going up first.
  - `param` is `prompt_cache_options` or `prompt_cache_breakpoint`: retry without the cache fields.
- **Error mapping** (`LlmError`; HTTP details in `research/openai_api.md` §2.8):
  - 401, and 403 other than `model_not_allowed` → `Unauthorized`. The server's message echoes a masked key, so drop it.
  - 429 with type `insufficient_quota` or a spend/usage-limit code (`credit_balance_exhausted`, `organization_spend_limit_exceeded`, `project_spend_limit_exceeded`, `organization_usage_limit_exceeded`) → `QuotaExceeded`. Any other 429, or 503 after retries → `RateLimited`.
  - 404 `model_not_found`, the proxy's 403 `model_not_allowed`, and other 4xx → `BadResponse(code)`. The friendly text for the two model codes is "This key or proxy can't use the AI model the app needs. Ask whoever set it up." (there is no model setting to point at).
  - Any other 404 (OpenAI's empty 404 for a path without `/v1`, the proxy's "Invalid URL" 404) → `BadResponse(code = ENDPOINT_NOT_FOUND)`: "Check the base URL in Settings", with **Open Settings**.
  - The proxy's own fatal codes, 502 `upstream_auth_failed` (its OpenAI key was rejected) and 500 `proxy_misconfigured` → `BadResponse(code)`, not retried: "The proxy can't reach OpenAI right now. Ask whoever runs it." Any other 5xx after retries → `Network`.
  - IOException → `Network` (including `UnknownServiceException` for cleartext). `SocketTimeoutException` or an exceeded call timeout → `Timeout`.
  - `image_generation_user_error` with `moderation_blocked` → `Refusal`; other image user errors → `BadResponse`; neither is retried.

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
data class IngestContext(aisles, corrections /*newest first*/, existingNames /*unchecked*/,
                         iconOverrides: Map<String, String> = emptyMap() /*normalized name -> key*/)
data class ProposedItem(name, quantity?, note?, aisleId? /*current id or null*/, icon /*valid*/, iconFit, aisleFromCorrection)
data class IngestResult(items, message, sources: List<WebSource>, skippedDuplicates, meta: CallMeta)
data class CallMeta(model?, requestId?, latencyMs, webSearches)
class IconDrawRequest(itemName, hint? = null, styleReference: ByteArray, styleReferenceFileName = "_style_reference.png")
class DrawnIcon(bytes, mimeType = "image/webp", meta); data class ConnectionCheck(model, meta)
fun ProposedItem.toGroceryItem(id, now, source): GroceryItem
suspend fun GroceryAssistant.assignBasketsOrGuess(aisles, guess): Map<String, BasketKind>   // never throws LlmError
```

Every operation is main-safe and cancellable, and throws only `LlmError` (plus `CancellationException`).

**ingest** makes one `POST /responses` call to `ModelDefaults.SMART` (body shape verified live, `research/openai_api.md` §2.1):

- **Fixed fields:** `store: false`, `max_output_tokens`, `reasoning.effort`, `safety_identifier`.
- **Output format:** `text.format` is a **strict** `json_schema` named `grocery_items`: `{items:[{name, quantity|null, note|null, aisle_id: enum(current aisle ids + "unsorted"), icon: enum(catalog keys), icon_fit: enum("exact","approximate")}], message}`.
- **Input (cache-friendly order):**
  1. A developer message with the stable RULES (`prompts/ingest_rules.txt`), ending in `prompt_cache_breakpoint` (only on models that take it).
  2. A developer message with the aisles in walking order (`id: name`), the learned corrections (`name -> aisle id`) and the names already on the list.
  3. The user message.
- **Text input:** `tools: [{type: "web_search", search_context_size, user_location}]`, `tool_choice: "auto"` and `include: ["web_search_call.action.sources"]` (from `ModelDefaults.WEB_SEARCH`; always on).
- **Image input:** `[input_text "Photo of a handwritten shopping list. Add its items.", input_image data:image/jpeg;base64,…, detail "high"]`, with **no tools**. The app sends a JPEG with long edge ≤ 1600 px at q≈85.
- **Transcript input:** `"Voice note transcript: <text>"`, with the same tools as text input.
- **Parsing:**
  - `status == "incomplete"` (any `incomplete_details.reason`) → `BadResponse`, even if a partial message exists. A response with **no message item at all** (seen live: only a reasoning item, 0 usage) → `BadResponse`. Never parse partial JSON.
  - Use the **last** `message` output item whose `phase` is not `"commentary"` (a missing phase counts as final). A `refusal` content part → `Refusal`. If its JSON doesn't parse, try earlier final messages. Never concatenate `output_text`.
  - Sources come from `web_search_call.action.sources[].url`, `action.url` (from `open_page`) and `url_citation` annotations, deduplicated. In `message`, markdown links `[t](u)` are reduced to `t`.
- **Post-processing:** trim names; a learned correction overrides `aisle_id`; `"unsorted"` or an unknown id becomes `null`; a learned icon override replaces `icon` with fit EXACT; an invalid icon becomes `DEFAULT_ICON` + APPROXIMATE; dedupe by `normalizeName` against `existingNames` and within the answer (duplicates go to `skippedDuplicates`).

**transcribe** makes one `POST /audio/transcriptions` (multipart) call: `model`, `response_format=json`, `languages[]` and `prompt` from `ModelDefaults.TRANSCRIBE`, `keywords[]` (at most 100, each one line with `<` and `>` stripped; from the list's names and the correction names), and `file=recording.m4a`. **The extension selects the decoder**; `.aac` and `.3gp` are rejected. Blank `text` means nothing was heard: return `Transcript("")` rather than an error.

**recategorize** uses `ModelDefaults.FAST` with no tools and the strict schema `{assignments:[{id: enum(i1..iN), aisle_id: enum(aisles + "unsorted")}]}`. Items with a matching correction are resolved locally and not sent. Batches hold at most 40 items. An id missing from the answer maps to `null`.

**assignBaskets** uses `ModelDefaults.FAST` with the strict schema `{baskets:[{aisle_id: enum(ids), basket: enum(16 kinds)}]}`. A missing aisle falls back to the heuristic inside `assignBasketsOrGuess`.

**drawIcon** (cascade step 3) makes one `POST /images/edits` (multipart) call: `model`, `prompt`, `image[]` = the style reference PNG, then `background`, `output_format`, `output_compression`, `quality`, `size` from `ModelDefaults.IMAGE`, and `n=1`. The prompt is `prompts/icon_prompt.txt` (from `research/openai_api.md` §6.1) with the item name substituted, plus the optional hint. It returns the decoded `data[0].b64_json` bytes; trimming and downscaling happen on the device (§8.4).

**testConnection** calls `GET /models/{ModelDefaults.SMART.id}`. It is free, and it checks the URL, the token and model access in one go.

### 7.4 `IconDecider` (cascade step 2, `assistant/IconDecider.kt`)

```kotlin
interface IconDecider { suspend fun decide(items: List<NamedItem>): Map<String, IconDecision> }
data class IconDecision(val icon: String? /* non-generic key or null = "none" */, val category: String?) {
    companion object { val NONE: IconDecision } }
fun IconDecision.resolve(catalog): ResolvedIcon   // match -> (icon, EXACT); none -> (category fallback ?: DEFAULT_ICON, APPROXIMATE)
class ResponsesIconDecider(client: OpenAiClient, catalog: IconCatalog, model: ResponsesModel = ModelDefaults.FAST) : IconDecider
```

- Returns a decision for **every** input id; an id the model skipped maps to `IconDecision.NONE`. Batches of ≤ 40 names; empty input sends nothing. Throws only `LlmError`.
- The caller (IconRepository) passes `NamedItem(id = normalizedName, name = displayName)`.
- Request (verified live 2026-10-01 with 12 names: 2.6 s, ~US$0.0004 cold, prefix cached on later calls):

```json
{"model":"gpt-6-luna","store":false,"max_output_tokens":4000,"reasoning":{"effort":"none"},
 "text":{"format":{"type":"json_schema","name":"icon_decisions","strict":true,"schema":{"type":"object",
   "properties":{"decisions":{"type":"array","items":{"type":"object","properties":{
     "id":{"type":"string","enum":["i1","…","iN"]},
     "icon":{"type":"string","enum":["<catalog.decidableKeys>","…","none"]},
     "category":{"type":"string","enum":["fruit","vegetables","…","garden"]}},
     "required":["id","icon","category"],"additionalProperties":false}}},
   "required":["decisions"],"additionalProperties":false}}},
 "input":[{"role":"developer","content":[{"type":"input_text","text":"<RULES + catalog grouped by category: key (label)>",
            "prompt_cache_breakpoint":{"mode":"explicit"}}]},
          {"role":"user","content":[{"type":"input_text","text":"Items:\ni1: Gochujang\ni2: Oat milk\n…"}]}],
 "prompt_cache_options":{"mode":"explicit"}}
```

- RULES (`prompts/icon_decider_rules.txt`, core tunes it): pick the key that depicts the item itself (same product or an obvious packaging of it); a related or same-category icon is **not** a match (gochujang is not `hot_sauce`); generic keys are never a match; `category` is always required. The probe answered: Gochujang → none/condiments, Oat milk → `plant_milk`, Kimchi → none/pantry, Spatula → none/household, Timbits → `donut`, Fish sauce → `soy_sauce` (too generous: tighten the rules).
- Client side, `resolve` treats an unknown or generic key as "none".

### 7.5 Prompts, CLI, tests

- **Prompts.** Prompt texts live in `core/src/main/resources/prompts/` (`ingest_rules.txt`, `recategorize_rules.txt`, `baskets_rules.txt`, `icon_decider_rules.txt`, `icon_prompt.txt`) and are documented in `docs/PROMPTS.md`, which core owns. Start `ingest_rules.txt` from `research/openai/examples/rules.txt`, and fix one line there: it says category icons are `cat_*`, but in the catalog they are `*_generic`. Lessons from the benchmark: examples leak verbatim, so never reuse eval inputs as examples; say "Never mention list items the request did not need".
- **CLI.** `earth.cora.picnic.core.cli.MainKt`, run with `./gradlew -p core run --args="<cmd> …"`. Commands: `ingest "<text>"`, `photo <jpg>`, `transcribe <m4a>`, `recat "<item>"…`, `baskets [aisles.txt]`, `icons "<item>"…` (IconDecider), `draw "<name>" <out.webp>`, `test`. It reads `OPENAI_API_KEY` and optional `PICNIC_BASE_URL` from the inherited environment. Developer-only flags `--smart=<model>` / `--fast=<model>` build a different `ModelSet` for prompt comparisons; the app has no equivalent. **Never** pass the key through `environment(...)` in Gradle: the configuration cache would write it to disk.
- **Tests.** Unit tests run on MockWebServer (`mockwebserver3`, with `http://127.0.0.1` base URLs) and fixtures in `core/src/test/resources/fixtures/`. Take trimmed real responses from `research/openai/out/bench/`. Cover: the duplicate-final-message case, `incomplete` with and without a message, a refusal, each error body, the effort and cache-field downgrades, decider "none" and skipped ids, corrections and icon overrides in post-processing. Live tests are `@Tag("live")` and also `assumeTrue(OPENAI_API_KEY)`; they run only with `-Plive=true`. They can use `research/openai/{handwriting,audio}`; copy only small fixtures into the repo.

---

## 8. Icons on device and the icon cascade (app-shell, `earth.cora.picnic.icons`)

### 8.1 Bundled library

- **Files:** `app/src/main/assets/icons/<key>.webp`, 192×192 transparent WebP, one per catalog key. The art pass produces them from sprite sheets (`research/openai_api.md` §6.4).
- **Style reference:** `assets/icons/_style_reference.png`, the sheet used for live drawing. Until the art pass, app-shell copies `research/openai/icons/_style_reference_512.png` (330 KB; it costs the same 1,024 input tokens as the 1024 px version).
- **Missing art:** if no asset exists, `IconImages.load` returns null and `ItemIcon` draws placeholder art. Build the bundled-key set once from `assets.list("icons")`.
- **Memory cache:** an `LruCache` of `ImageBitmap`s. Decode with `BitmapFactory` on `Dispatchers.IO`.

### 8.2 The cascade (amendment A2)

Every item ends with a catalog icon and an `IconFit`; `APPROXIMATE` items may also get a bespoke icon. Each step falls through on failure or offline; nothing ever blocks adding an item.

| Step | What | Where | On failure / offline |
|---|---|---|---|
| 1. Local | Learned `IconOverride` for the normalized name, else `IconCatalog.match(name)` | core, synchronous: `AppState.localIcon`, used by `OfflineParser` and `editItemDetails`; ingest applies overrides in post-processing | No match → `DEFAULT_ICON` + PENDING |
| 2. Decide | `IconDecider.decide` on PENDING names; ingest's own `icon`/`icon_fit` counts as step 2 for the items it returns | app-shell `IconRepository` (batcher) → core `ResponsesIconDecider` | `LlmError` (or no token yet) → items stay PENDING, showing the fallback; retried by `resumePending()` once per process start and whenever a token is added (§10.1) |
| 3. Draw | `assistant.drawIcon` for APPROXIMATE items when "Draw new icons with AI" is on | app-shell `IconRepository` (queue) → core `drawIcon` | Item keeps its APPROXIMATE catalog icon (the closest key, or its category's `*_generic`) |

Entry paths:

| Path | Step 1 | Step 2 | Step 3 |
|---|---|---|---|
| AI ingest (text, photo, voice) | override → EXACT | the model's `icon` + `icon_fit` | if APPROXIMATE |
| "Add as typed" and the raw-lines fallback (`OfflineParser`) | override / `match` → EXACT | IconDecider for the rest | if the decider said "none" |
| Rename in the edit sheet (`editItemDetails`) | override / `match` on the new name → EXACT | IconDecider for the rest | if "none" |
| User picks a catalog icon (`setItemIcon`) | records an override; `forgetBespoke(name)` | — | — |
| "Draw a new icon" (`redraw`) | — | — | always (forced, overwrites) |

Before steps 2 and 3, a cached bespoke icon for the normalized name is attached directly (reuse across items and lists).

### 8.3 `IconRepository` contract and flow

```kotlin
interface IconRepository {
    val busy: StateFlow<Set<String>>           // normalized names being decided or drawn (rows show the sparkle)
    fun isBundled(key: String): Boolean        // assets/icons/<key>.webp exists
    fun submit(items: List<GroceryItem>)       // after commits and renames: attach cache / decide / draw
    fun resumePending()                        // once per process start, and when a token is added: submit every PENDING item
    fun redraw(itemName: String)               // edit sheet "Draw a new icon" (forced; overwrites)
    fun forgetBespoke(itemName: String)        // the user picked a catalog icon for this name
}
interface IconImages {                          // provided to the theme via LocalIconImages
    fun cached(ref: IconRef): ImageBitmap?      // memory only, composition-safe
    suspend fun load(ref: IconRef): ImageBitmap? // custom -> bundled key -> bundled fallback -> null
}
class NoOpIconRepository(busyNames: Set<String> = emptySet()) : IconRepository   // previews/tests
```

Production implementation (app-shell): `AppIconRepository(app, store, settings, assistant, iconDecider, catalog, appScope, clock)`.

```
commit / rename ──► store.update{...} ──► icons.submit(items)       (returns immediately; work in appScope)
  for each item without customIconId:  key = normalizeName(name)
    index.byName[key]?            ──yes──► store.update{attachCustomIcon(key, id, overwrite=false)}; done
    iconFit == PENDING            ──────► decide queue += key; busy += key
    iconFit == APPROXIMATE && settings.drawIcons && key !in blocked && key !in busy && under daily cap
                                  ──────► draw queue += (key, name); busy += key
Decide batcher (single coroutine): debounce 300 ms, ≤ 40 keys per call
    iconDecider.decide(NamedItem(key, name)…) → store.update{applyIconDecisions(key -> decision.resolve(catalog))}
    busy -= keys; resolved APPROXIMATE keys continue to the draw rule above
    LlmError → busy -= keys (items stay PENDING; resumePending() retries next start or when a token is added)
Draw workers (2 coroutines on a Channel = max 2 concurrent):
    styleRef bytes (read once, cached) → assistant.drawIcon(IconDrawRequest(name, styleReference = …))
      RateLimited / Network / Timeout → wait 3 s, retry ONCE (imageEdit itself never retries: at most 2 requests per drawing)
      Refusal (moderation) → blocked += key (never auto-draw this name again)
      other → give up silently (the item keeps its APPROXIMATE catalog icon)
    decode → trim transparent bounds → fit in 176 px → centre on 192×192 transparent
    → WEBP_LOSSY q82 (API 30+) / WEBP (26-29) → filesDir/icons/custom/<id>.webp (tmp + rename)
    → index.byName[key] = id (index.json atomic write) → store.update{attachCustomIcon(key, id, overwrite = redraw)}
    → evict caches; busy -= key; delete the replaced file on a redraw
```

Rows read `busy` and show `IconCatalog.displayRef(item, busy = normalizeName(item.name) in busy)` with `drawing = busy` (sparkle).

### 8.4 Bespoke cache details

- **Icon id:** `customIconId = "c-" + sha1(key).take(10) + "-" + base36(epochSeconds)`. A redraw gets a new id, so a stale bitmap is never shown from cache.
- **Index file:** `index.json` = `{"version":1,"byName":{…},"blocked":[…],"day":"2026-10-01","drawnToday":3}`. `forgetBespoke(name)` removes `byName[key]` (the file stays while items reference it; orphaned files are swept at start).
- **Daily cap:** 40 automatic drawings per day protects against runaway cost. A redraw ignores the cap.
- **Settings toggle:** "Draw new icons with AI", on by default, with the cost note "Drawing a new icon takes about 10 s and costs about 1.5¢ (US)." It gates step 3 only; step 2 (≈ US$0.0002 per batch) always runs when a token is set.
- **Offline:** nothing is re-queued for drawing automatically later. The item shows its catalog icon, and the user can choose "Draw a new icon".

### 8.5 Display rule (core `IconCatalog.displayRef(item, busy)`, implemented)

1. A bespoke icon attached → `IconRef(item.icon, customIconId)`.
2. `busy` or `iconFit == PENDING` → `IconRef(fallbackFor(item.icon))` (the category fallback; `DEFAULT_ICON` for PENDING), and the row overlays the "drawing…" sparkle while busy.
3. Otherwise → `IconRef(item.icon)`.

`IconImages.load` then renders: custom file → bundled `<key>.webp` → bundled category fallback → null (placeholder art).

### 8.6 Decisions API seam (TODO, not implemented)

OpenAI's new Decisions API (`POST /v1/decisions`) is the intended future implementation of step 2. Today it is a limited preview with no public schema, and for the project key it answers **HTTP 403** `{"error":{"message":"Decision API is not enabled for this user.","type":"invalid_request_error","param":null,"code":null}}` (checked 2026-10-01). **Do not write a client against a guessed schema.**

The seam is ready:

- `IconDecider` (core) is the only interface the app uses for step 2; `AppContainer.iconDecider` is the only construction site.
- `ResponsesIconDecider` stays the implementation until the checklist below is done.

Switch-over checklist (owner: core, with proxy):

1. Confirm access (the 403 above goes away) and get the published request/response schema.
2. Add `OpenAiClient.decisions(body)` (`POST {base}/decisions`) and `DecisionsIconDecider(client, catalog)` in `core/…/assistant/`, mapping to `IconDecision` (icon or none, plus category).
3. MockWebServer tests from recorded real responses; a live comparison against `ResponsesIconDecider` on a fixed name set (accuracy, latency, cost), noted in `docs/PROMPTS.md`.
4. Proxy: add the `POST /v1/decisions` route with its model allow-list and `MODEL_MAP` handling.
5. Construct it in `AppContainer.iconDecider`, wrapped so a 403/404 from a key or proxy without access falls back to `ResponsesIconDecider`.

---

## 9. Settings and the token security model (app-shell, `earth.cora.picnic.settings`)

Amendment A1: the only settings are the **connection** (base URL + token, "Test connection") and **"Draw new icons with AI"**. There is no model, effort, web-search, theme or motion setting: theme follows the system dark mode and motion follows the system "remove animations" setting.

```kotlin
data class AppSettings(val baseUrl: String = LlmConfig.DEFAULT_BASE_URL, val drawIcons: Boolean = true,
                       val hasToken: Boolean = false, val tokenHint: String? = null) { val usesDefaultEndpoint: Boolean }
interface SettingsRepository {
    val settings: Flow<AppSettings>; suspend fun current(): AppSettings
    suspend fun setBaseUrl(url: String): Boolean   // blank -> default; invalid -> false, nothing stored
    suspend fun setDrawIcons(enabled: Boolean)
    suspend fun setToken(token: String?); suspend fun token(): String?
    suspend fun llmConfig(): LlmConfig             // baseUrl + safetyIdentifier, for core's ConfigSource
}
interface TokenStore { suspend fun read(): String?; suspend fun write(token: String?) }
fun maskToken(token: String): String              // "sk-…a1b2"
```

- **`DataStoreSettingsRepository`** (production, `androidx.datastore:datastore-preferences` 1.2.1): keys `base_url` (absent = default), `draw_icons` (absent = true), `token_hint`, `install_id` (a random UUID created on first run). `llmConfig().safetyIdentifier` = the first 32 hex characters of `sha256(install_id)`.
- **`InMemorySettingsRepository`** is for tests and previews (implemented).

**Token security model:**

- **Never in the repo or APK.** The user pastes either their own OpenAI key or a per-device proxy token from Cora (`pk_…`).
- **Encryption.** The token is encrypted with an AndroidKeyStore AES-256-GCM key (`platform/TokenCipher`). The Keystore picks the IV, and the stored form is `iv:ct`. The blob lives in `noBackupFilesDir/token.bin`, outside the backup allow-list; a restored blob can't be decrypted on another device anyway, because Keystore keys aren't backed up.
- **Unreadable token.** Only a permanent failure (`KeyPermanentlyInvalidatedException`, `AEADBadTagException`, a malformed blob) deletes `token.bin` and clears `token_hint` (`hasToken = false`, ask again). Any other Keystore failure (`KeyStoreException`, `UnrecoverableEntryException`, `ProviderException`: a busy or restarting keystore2, early boot) is `KeystoreUnavailableException` (an `IOException`): `TokenStore.read()` throws it and keeps the blob, and `token()` returns null for that one call (core reports `NotConfigured`) without touching the hint. `SecretCipher.decrypt` returns null only for the permanent cases.
- **One token per host.** A token is only ever sent to the host it was entered for. When Save (or "Use OpenAI directly") changes the base URL's host or port, the stored token is deleted **before** the new URL is stored, and the screen says "New address saved. Paste the key or token for it…" (`SettingsUiState.notice`). A token typed together with the new address is saved for it. A path change on the same host keeps the token.
- **Failed writes.** Every settings write runs through one wrapper that turns a DataStore `IOException` into `notice = "Couldn't save. Try again."` instead of a crash.
- **Where it can appear.** It is decrypted only inside `token()`, which `TokenSource` calls per request. It is never put in a data class, a `StateFlow`, a log line or an exception message. The Settings field is write-only: it shows `tokenHint` and is never pre-filled. `LlmError.Unauthorized` discards the server text, which echoes a masked key. There is no `HttpLoggingInterceptor` in any build type.
- **Tests.** The Keystore doesn't exist under Robolectric, so tests use `InMemoryTokenStore`.
- **Network.** Cleartext is blocked by default at targetSdk ≥ 28, and `normalizeBaseUrl` refuses `http://` except loopback.

---

## 10. App structure (app-shell + app-screens)

### 10.1 Application and DI

- `PicnicApp : Application` with `val container by lazy { AppContainer(this) }`. Its `onCreate` does nothing heavy, because Robolectric instantiates it in every test.
- **No main-thread I/O.** Touching a container property never does disk or network I/O on the caller's thread: the catalog is parsed in `startOnce()` before anything composes, the store/DataStore/token reads are `suspend` and switch to IO themselves, and `OpenAiClient` builds its `OkHttpClient` on `Dispatchers.IO`.
- `AppContainer` (app-shell completes the TODOs) provides:

  | Member | Notes |
  |---|---|
  | `appScope` | `SupervisorJob + Dispatchers.Default`. Work that outlives a screen: commits, aisle sync, the icon cascade. |
  | `clock()`, `newId()` | |
  | `catalog`, `baskets` | |
  | `store` | `JsonFileStateStore(filesDir/state.json)` |
  | `settings` | `DataStoreSettingsRepository(app, KeystoreTokenStore(app))` |
  | `configSource`, `tokenSource` | `{ settings.llmConfig() }`, `{ settings.token() }` |
  | `assistant` | `OpenAiGroceryAssistant(client, catalog)`; models come from `ModelDefaults` |
  | `iconDecider` | `ResponsesIconDecider(client, catalog)` (the Decisions API swap point, §8.6) |
  | `aisleSync` | |
  | `icons`, `iconImages` | `AppIconRepository`, `AssetIconImages` |
  | `photos` | `PhotoPreparer` |
  | `newVoiceRecorder()` | |
  | `ready: StateFlow<Boolean>` | True once `startOnce()` has parsed the icon catalog (≈105 KB JSON) and listed the bundled icons off the main thread |
  | `startOnce()` | Once per process (an `AtomicBoolean`; MainActivity calls it on every `onCreate`), in `appScope`: warm the catalog → `ready = true` → `icons.resumePending()` → `Captures.sweepStale`. It also starts the token watcher: each "no token → token" change (`onTokenAdded`) runs `icons.resumePending()` and `aisleSync.sortItems(...)` for TYPED items still needing an aisle, so items added before a token existed catch up without a cold start. |
- **ViewModels take their dependencies individually**, never the container. Their factories read the container:

  ```kotlin
  val Factory = viewModelFactory { initializer {
      val c = (this[APPLICATION_KEY] as PicnicApp).container
      ListViewModel(c.store, c.icons, c.settings, c.catalog, c.clock, c::newId, c.appScope) } }
  ```

  Tests then pass `InMemoryStateStore`, `InMemorySettingsRepository`, `NoOpIconRepository` and a fake `GroceryAssistant`.

### 10.2 Activity and navigation

`MainActivity` is owned by app-shell:

- Call `installSplashScreen()`, then `super.onCreate`, then `enableEdgeToEdge()` with bar styles that follow the system theme, then `container.startOnce()` (unconditionally: a process killed in the background restarts with a `savedInstanceState`, and must still resume the cascade), then `splash.setKeepOnScreenCondition { !container.ready.value }`, then `setContent`. Inside, nothing below `PicnicTheme` composes until `ready` is true, so no ViewModel factory parses the catalog on the main thread. Activity results (a camera capture after process death) wait in the `ActivityResultRegistry` until the launchers register.
- Inside `setContent`: `PicnicTheme(darkTheme = isSystemInDarkTheme(), reducedMotion = <system animator duration scale == 0>) { CompositionLocalProvider(LocalIconImages provides container.iconImages) { PicnicAppRoot(container) } }`.
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
class ListViewModel(store: StateStore, icons: IconRepository, settings: SettingsRepository, catalog: IconCatalog,
                    clock: () -> Long, newId: () -> String, appScope: CoroutineScope) : ViewModel() {
    val state: StateFlow<ListUiState>; val events: Flow<ListEvent>
    fun toggle(itemId); fun delete(itemId); fun undoDelete(item: GroceryItem, index: Int)
    fun toggleSection(sectionId); fun clearChecked(); fun newList(title); fun switchList(id)
    fun renameList(id, title); fun deleteList(id)
    fun openEdit(itemId); fun closeEdit()
    fun saveEdit(name, quantity, note)     // editItemDetails(..., catalog); icons.submit(updated) if renamed
    fun moveToAisle(itemId, aisleId: String?)  // moveItemToAisle: learned correction
    fun setIcon(itemId, key)               // setItemIcon: learned override; icons.forgetBespoke(name)
    fun redrawIcon(itemId)                 // icons.redraw(name)
}
data class ListUiState(val loading: Boolean = true, val listId: String = "", val title: String = "",
    val lists: List<ListSummary> = emptyList(), val sections: List<SectionUi> = emptyList(),
    val remaining: Int = 0, val total: Int = 0, val editing: EditItemUi? = null)
data class ListSummary(val id: String, val title: String, val remaining: Int, val current: Boolean)
data class SectionUi(val id: String, val kind: SectionKind, val title: String, val number: Int?,
    val aisleIndex: Int /* position-1, or -1 */, val basket: BasketKind, val collapsed: Boolean,
    val cleared: Boolean, val count: Int, val miniIcons: List<IconRef> /* first 5 displayRefs */, val items: List<ItemRowUi>)
data class ItemRowUi(val id: String, val name: String, val detail: String? /* "qty · note" */,
    val icon: IconRef /* displayRef */, val drawing: Boolean /* name in icons.busy */, val checked: Boolean)
data class EditItemUi(val item: GroceryItem, val aisles: List<Aisle>, val iconChoices: List<String> /* item's category first */,
    val canDraw: Boolean /* settings.hasToken */)
sealed interface ListEvent { data class Deleted(val item: GroceryItem, val index: Int) : ListEvent
                             data class Message(val text: String) : ListEvent }

// ui/add
enum class AddMode { WRITE, SNAP, SPEAK }
sealed interface AddPhase { Idle; Recording(elapsedMs, level: Float); Transcribing;
    Thinking(kind: ItemSource); Preview; Failed(message, retryable, canUsePlainLines, needsSettings) }
data class AddUiState(val listId: String, val mode: AddMode = WRITE, val text: String = "", val phase: AddPhase = Idle,
    val transcript: String? = null, val photo: Uri? = null, val preview: List<PreviewItemUi> = emptyList(),
    val message: String? = null, val sources: List<WebSource> = emptyList(), val skipped: List<String> = emptyList())
data class PreviewItemUi(val key: Int, val item: ProposedItem, val keep: Boolean, val aisleTitle: String, val icon: IconRef)
class AddViewModel(store, assistant, icons: IconRepository, aisleSync: AisleSync, catalog, photos: PhotoPreparer,
                   recorderFactory: () -> AudioRecorder, clock, newId, appScope,
                   savedState: SavedStateHandle /* createSavedStateHandle() in the factory */) : ViewModel
    // open(listId, mode), close(), attachList(listId), setMode, setText, submitText (AI), addTyped (no AI, commits
    // at once), onPhoto(uri), startRecording, stopRecording, cancel, toggleKeep(key), retry, usePlainLines, commit()

// ui/aisles
data class AislesUiState(val storeName: String, val text: String, val parsed: List<AislePreviewUi>,
    val dirty: Boolean, val syncing: Boolean, val lastReport: AisleSyncReport?)
data class AislePreviewUi(val aisle: Aisle, val basket: BasketKind)
class AislesViewModel(store, aisleSync, baskets, appScope)   // save(): setStoreName + setAislesText, then appScope.launch{aisleSync.sync()}

// ui/settings (amendment A1: connection + draw-icons only)
data class SettingsUiState(val baseUrlInput: String, val baseUrlError: String? = null, val usesDefaultEndpoint: Boolean,
    val tokenInput: String = "", val hasToken: Boolean, val tokenHint: String?, val drawIcons: Boolean,
    val test: ConnectionTestUi = ConnectionTestUi.Idle, val appVersion: String,
    val notice: String? = null /* failed write, or "paste the token for this new host" */)
sealed interface ConnectionTestUi { Idle; Running; Ok(latencyMs: Long); Failed(message: String) }
class SettingsViewModel(settings: SettingsRepository, assistant: GroceryAssistant, appVersion: String)
    // setBaseUrlInput, setTokenInput, saveConnection(), clearToken(), useOpenAiDirectly(), setDrawIcons(on), testConnection()
```

- **Settings screen content:** a "Connection" card (base URL field with the OpenAI default and a hint for Cora's proxy, write-only token field showing `tokenHint`, Save, Test connection with the result inline) and an "Icons" card ("Draw new icons with AI" switch plus the cost note). An "About" footer may show the app version and font licences. Nothing about models.
- **Lifecycle of saves.** Commits, deletes, edits and aisle syncs run in `appScope` (via the store), so closing a sheet or screen doesn't cancel a save. LLM calls the user is waiting for run in `viewModelScope`, so they are cancelled when the user dismisses the sheet.
- **Snackbars.** Swipe-to-delete emits `ListEvent.Deleted`. The screen shows "Removed <name>" with **Undo**, which calls `undoDelete(item, index)`.
- **Edit sheet:** rename, quantity and note use `saveEdit`; "Move to aisle" uses `moveToAisle` (learned correction); "Change icon" shows a grid of catalog keys, starting with the item's category, and calls `setIcon` (learned override); "Draw a new icon" calls `redrawIcon` (shown when `canDraw`).
- **List actions.** "Clear checked" calls `clearChecked`. New list, switch, rename and delete come from the top-bar switcher.

### 10.4 Data flow per input mode

The ingest context is taken when the request is sent (`state.ingestContext(listId)`). The target list is the list that was current when the sheet opened (`AddUiState.listId`).

**Process death.** `AddViewModel` keeps the draft text, the mode and the target list in its `SavedStateHandle`; `ListRoute` restores `showAdd` (`rememberSaveable`), so the sheet comes back with the draft. A camera result can reach the fresh VM during the first composition, before `ListRoute` knows the list: `onPhoto` then uses the current list (`store.current().currentListId`), and `ListRoute` calls the non-destructive `attachList(listId)` (sets the list only if none is known; never cancels work), not `open()`.

| Mode | Steps | Source | Fallback on `LlmError` |
|---|---|---|---|
| **Write, AI** (text, list, recipe URL) | `submitText` → `Thinking` → `assistant.ingest(Text(text), ctx)` → `Preview` (keep toggles; shows `message`, `skipped` and **Sources** chips opened with `LocalUriHandler`) → `commit` | AI_TEXT | `Failed(canUsePlainLines=true)` → `usePlainLines()` = `OfflineParser.parse(text, catalog, ctx)` → Preview → commit as TYPED |
| **Write, as typed** | `addTyped` → `OfflineParser.parse(text, catalog, ctx)` → commit at once (no preview) | TYPED | none needed (no network) |
| **Snap** (camera / picker) | `TakePicture` into a FileProvider Uri (`Captures.newCaptureUri`, pending Uri kept in `rememberSaveable`) or `PickVisualMedia(ImageOnly)` → `onPhoto(uri)` → `photos.jpegBytes(uri)` (IO) → `Thinking` → `ingest(Image(bytes), ctx)` → Preview → commit. Delete the capture file afterwards. | PHOTO | `Failed(retryable)`; no plain-lines option (nothing to parse) |
| **Speak** | Request `RECORD_AUDIO` at runtime → `AudioRecorder.start()` (`VoiceRecorder`: m4a, mono 16 kHz 32 kbps, max 5 min). Poll `level()` about 10×/s for the waveform. `stop()` (also on `ON_STOP`) → bytes → `Transcribing` → `assistant.transcribe(AudioClip(bytes), keywords)` → show the transcript (editable) → `Thinking` → `ingest(Transcript(text), ctx)` → Preview → commit | VOICE | Empty transcript → "Didn't catch that". Transcribe failure → retry with the bytes kept in memory. Ingest failure → plain lines from the transcript. |

**Commit** (shared by every path), in `appScope`:

1. `items = proposed.filter { keep }.map { it.toGroceryItem(newId(), clock(), source) }`.
2. `val after = store.update { it.addItems(listId, items) }`; `added = items.filter { after.findItem(it.id) != null }`.
3. `icons.submit(added)` (icon cascade steps 2–3, §8.3).
4. If any added TYPED item has `aisleId == null`: `aisleSync.sortItems(thoseIds)` (fast model places them; stays Unsorted offline).
5. Close the sheet and show a snackbar with the model's `message` (or "Added N items").

`NotConfigured` gives `Failed(needsSettings = true)`, which offers **Open Settings**.

**Aisle edit:**

1. Save runs `store.update { setStoreName(name).setAislesText(text, baskets::guess) }`. The heuristic baskets show immediately, and items in vanished aisles render as Unsorted.
2. `appScope.launch { aisleSync.sync() }` runs `assignBasketsOrGuess` → `setAisleBaskets`, then `itemsNeedingAisle` → `recategorize` → `applyAisleAssignments`.
3. The returned `AisleSyncReport` drives a snackbar, e.g. "Sorted 6 items · 2 left in Unsorted" or the error's friendly message.

---

## 11. Theme layer contract (app-shell provides, app-screens consumes, art pass replaces)

**Rule for screens:** use only `PicnicTheme.*` tokens, the theme components, the decor slots, `ItemIcon`/`AisleBasket`/`AisleHeader`/`AisleSectionTitle`/`AisleSectionRow`/`ItemRowCard` and `Glyph`.

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
data class PicnicShapes(card, row, chip, button, field, sheet /*top corners*/, badge, medallion,
    section: CornerBasedShape /*aisle section container*/, sectionInner: CornerBasedShape /*its row surface*/)
data class PicnicDimens(gutter=16, xxs..xxl, rowGap, sectionGap=14, sectionInset=8 /*tray wall*/, rule=1.5,
    rowMinHeight=64, touchTarget=48, itemIcon=40, miniIcon=20, basket=64, medallion=44, outline=2, hardShadow=DpOffset(0,3))
data class PicnicMotion(reduced, stepFps = 8, shortMillis = 150, mediumMillis = 280)
val LocalIconImages: ProvidableCompositionLocal<IconImages>   // default IconImages.None (placeholder art)
val LocalIconCatalog: ProvidableCompositionLocal<IconCatalog> // default IconCatalog.default
```

- **Palettes.** Two placeholder palettes: light "picnic at noon" and dark "picnic at dusk", chosen by the system dark mode. Both share an 8-colour aisle rotation (strawberry, sky, lemon, leaf, grape, tangerine, bubblegum, cocoa). An aisle's colour is `aisles[(position-1) % 8]`.
- **Aisle identity.** An aisle is identified by colour, pattern, number and name, never by colour alone.
- **Fonts.** These must be **bundled** TTF/OTF files in `res/font`, subsetted, with their OFL licences shown in-app. Robolectric can't render downloadable fonts. The skeleton uses system families as placeholders.

### 11.2 Components (`ui/theme/Components.kt`)

| Composable | Purpose |
|---|---|
| `PicnicSurface(modifier, shape, color, outlined, shadow, content: BoxScope.()->Unit)` | Every card or row container: fill, ink outline and a **hard offset shadow** (no blur elevation) |
| `PicnicButton(text, onClick, modifier, style: Primary/Secondary/Text, enabled, glyph?)` | Buttons, at least 48 dp tall. Any gloss must read as intentional cel shading clipped to the face and clear of the label (the placeholder Primary has a rim light along the top contour), never a loose highlight stroke. |
| `PicnicIconButton(glyph, contentDescription, onClick, modifier, enabled, emphasized)` | 48 dp round glyph button |
| `PicnicChip(text, selected, onClick, modifier, leading?)` | Aisle chips, Sources chips and filter chips |
| `PicnicTextField(value, onValueChange, modifier, placeholder, label, singleLine, minLines, maxLines, isError, supportingText, enabled, keyboardOptions, keyboardActions, visualTransformation, trailing?)` | All text input. For the token field, pass `PasswordVisualTransformation`. The visible `label` is the editable node's content description (TalkBack: "Amount, 2, edit box") and is cleared from the label `Text`; tests find fields by `hasContentDescription(label)`. |
| `PicnicTopBar(title, modifier, subtitle, navigation?, actions, bottom?)` | Title card. `bottom` takes `TripProgress` or tabs. Applies no insets itself. |
| `PicnicSwitch(checked, onCheckedChange, modifier, enabled)` | Settings toggles |
| `PicnicSnackbar(data, modifier)` | Use as `SnackbarHost(host) { PicnicSnackbar(it) }` |
| `SheetHandle(modifier)` | Use as `ModalBottomSheet(dragHandle = { SheetHandle() })` |
| `Glyph(glyph: PicnicGlyph, contentDescription, modifier, tint, size = 24.dp)` | UI glyphs: `Add, Close, Check, Back, More, Camera, Gallery, Mic, Stop, Send, Settings, Aisles, Lists, Edit, Delete, Undo, Refresh, Sparkle, Expand, Collapse, Link, Drag, Key`. The art pass draws them as vectors; the enum may grow. |

### 11.3 Decorative slots (`ui/theme/Decor.kt`)

These have no semantics unless a parameter says otherwise. Each direction's metaphor is listed for orientation (directions A / B / C in `research/directions.md` and `design/concepts/`).

| Slot | Screens place it… | Art pass draws (A / B / C) |
|---|---|---|
| `PicnicBackground(modifier, content)` | as the root of each screen | gingham / star tile / pinstripe |
| `SectionDivider(modifier, major)` | between regions | bunting / gold filigree / perforation |
| `AisleMedallion(number?, aisleIndex, modifier, size)` | in the expanded aisle header (the aisle header ornament) | numbered medallion with a pattern ring |
| `CheckMark(checked, modifier)` | in the row (the row provides the touch target); 48 dp footprint | an unmistakable box: bold ink outline, **no offset shadow**; checked = filled, heavy tick, a small flourish, stamped in (springy pop, instant with reduced motion). Placeholder: rounded square, lemon fill, strawberry twinkle. A / B / C may restyle it (star / halo / hanko), but the unchecked state must still read as an empty box at a glance in both palettes. |
| `TripProgress(done, total, modifier)` | in the top bar's `bottom` slot (progress metaphor) | episode ribbon / charm bracelet / stamp card |
| `EmptyState(title, body, modifier, action?)` | for an empty list or screen | sleepy bee on the hamper, etc. |
| `LoadingSparkle(modifier, label?)` | while the AI is thinking (the label is announced) | bee figure-8 / firefly / sparkle sweep |
| `DrawingSparkle(modifier)` | overlaid on an icon whose icon is being decided or drawn (`ItemIcon` does this) | small twinkle |

### 11.4 Icons, baskets and list pieces (`ItemIcon.kt`, `AisleBasket.kt`, `ListPieces.kt`)

```kotlin
@Composable fun ItemIcon(icon: IconRef, contentDescription: String?, modifier = Modifier,
                         size: Dp = PicnicTheme.dimens.itemIcon, drawing: Boolean = false)
@Composable fun AisleBasket(kind: BasketKind, miniIcons: List<IconRef>, count: Int, modifier = Modifier,
                            size: Dp = PicnicTheme.dimens.basket)
@Composable fun AisleHeader(title: String, number: Int?, aisleIndex: Int, basket: BasketKind, miniIcons: List<IconRef>,
                            count: Int, collapsed: Boolean, cleared: Boolean, onToggle: () -> Unit, modifier = Modifier,
                            rowsBelow: Boolean = false)
@Composable fun AisleSectionTitle(title: String, number: Int?, aisleIndex: Int, modifier = Modifier)
@Composable fun AisleSectionRow(aisleIndex: Int, first: Boolean, last: Boolean, modifier = Modifier,
                                content: @Composable () -> Unit)
@Composable fun ItemRowCard(name: String, detail: String?, icon: IconRef, checked: Boolean,
                            modifier = Modifier, drawing: Boolean = false)
```

**Aisle sections: an aisle owns its rows.** An aisle and its items read as **one container**, never as a stack of equal floating cards. The header is the container's top band, tinted with the aisle's `pastel`. The rows sit on a lighter inner surface (`colors.surface`, shape `shapes.sectionInner`) inside a tray of that same tint (`dimens.sectionInset` wall), divided by thin rules (`dimens.rule`). The last row closes the container with a rounded cap, the tray margin and the hard shadow. Collapsed or emptied sections are just the band with its basket.

The container is drawn **slice by slice, one slice per lazy-list item**, so every row keeps its own key (swipe state, placement animation) and a section can cross the viewport edge without one giant item:

```kotlin
item(key = "header:$id") { AisleHeader(…, rowsBelow = expanded && items.isNotEmpty()) }   // top band
itemsIndexed(items, key = { _, r -> r.id }) { i, r ->
    AisleSectionRow(aisleIndex, first = i == 0, last = i == items.lastIndex) {           // middle rows / bottom cap
        SwipeToDismissBox(…) { ItemRowCard(…, modifier = Modifier.combinedClickable(…)) }
    }
}
```

- Slices must touch: no `verticalArrangement` spacing in the list. Put `dimens.sectionGap` above each header (except the first) instead. Each slice draws its outline past its open edges and clips it to its own bounds, so adjacent slices join without a visible seam.
- **AisleSectionRow** draws the tray walls, the inner surface, a rule above every row but the `first`, and on the `last` row the cap. It clips `content` to the inner surface, so a swiped row slides out under the walls and the swipe background fills exactly that row's slot. Gestures, semantics and keys stay with the screen.
- **AisleSectionTitle** is a non-interactive band (small medallion and title, announced as a heading) for read-only groupings such as the Add sheet's preview. Follow it with `AisleSectionRow`s, in a plain `Column` if the group is short.
- If a later screen adds sticky headers, pin the header band only; rows keep drawing their own slice.

- **ItemIcon** uses `LocalIconImages.cached(ref)` first; if that's null, it calls `load(ref)` in `produceState`; if the result is still null, it draws placeholder art keyed by the icon's category. `drawing` overlays `DrawingSparkle`. Rows pass `contentDescription = null`, because the row text already names the item. Callers pass `IconCatalog.displayRef(item, busy)`.
- **AisleBasket** draws the container art for `kind` (one vector or bitmap per basket kind from the art pass; the catalog's `drawHint`s describe each). The first **2–5** of `miniIcons` peek out of the basket, overlapping (fewer if fewer are given, at most 5). A `count` badge shows when `count > 0`. It is decorative.
- **AisleHeader** is the single toggle target for its section. Collapsed: `AisleBasket` (basket, mini icons, count badge) beside the title. Expanded: `AisleMedallion` and the title. Cleared: a "cleared" stamp. It announces "`<title>`, `<count>` items" with the state description collapsed or expanded. Unsorted and the checked section use it with `number = null` and `aisleIndex = -1`. With `rowsBelow = true` it is the open top band of its section, otherwise a closed card.
- **ItemRowCard** holds the visual content of a row only, opaque on the section's inner surface. Wrap it in `AisleSectionRow`; it draws no card, outline or shadow of its own. The screen adds click (toggles checked), long-press (opens edit) and `SwipeToDismissBox` (delete with undo). A checked item shows the filled `CheckMark`, a strike-through and the softer `inkSoft` colour. State is never shown by colour alone.

### 11.5 Previews and accessibility rules

- **Previews (app-shell).** Every theme component gets an `@Preview` in light and dark. Roborazzi's preview scanner captures every `@Preview` under `earth.cora.picnic` automatically, at `w411dp-h891dp-night-xxhdpi` with SDK 36. `ThemeSampler.kt` is the first one (verified to render). Previews must render without the app container, through `IconImages.None` placeholder art and `IconCatalog.default`.
- **Accessibility.** Item names use `type.itemName`, never smaller than 16 sp. Font scale is supported up to 200 %. Touch targets are at least 48 dp. Respect `PicnicTheme.motion.reduced`: no decorative loops, and fades of 150 ms or less. Nothing flashes faster than 3 Hz.

---

## 12. Error handling summary

| Where | Error | User sees |
|---|---|---|
| ingest (text / transcript) | any `LlmError` | `Failed` with `friendlyMessage`, **Try again** if `retryable`, and **Add as plain lines** |
| ingest (photo) | any `LlmError` | `Failed` with **Try again** |
| any AI call | `NotConfigured` / `Unauthorized` | message and **Open Settings** |
| any AI call | `BadResponse(model_not_found / model_not_allowed)` | "This key or proxy can't use the AI model the app needs. Ask whoever set it up." |
| any AI call | `BadResponse(endpoint_not_found)` (a 404 other than `model_not_found`) | "That server address doesn't look like an OpenAI-compatible API. Check the base URL in Settings." and **Open Settings** |
| any AI call | `BadResponse(upstream_auth_failed / proxy_misconfigured)` (Cora's proxy, never retried) | "The proxy can't reach OpenAI right now. Ask whoever runs it." |
| transcribe | empty text | "Didn't catch that. Try again a little closer to the mic." |
| transcribe | `LlmError` | `Failed` with **Try again** (the recording is kept in memory while the sheet is open) |
| photo decode | `IOException` / `IllegalStateException` | "Couldn't open that photo." |
| aisle sync / sortItems | `report.error` | snackbar: friendly message plus "N items left in Unsorted" (sortItems: silent unless the user is on the list) |
| IconDecider (step 2) | any | nothing; items keep the fallback icon and stay PENDING until the next process start or a token is added |
| icon drawing (step 3) | any | nothing (a background enhancement); a refusal blocks the name |
| Test connection | any | inline: ✓ and latency, or the friendly message |
| settings write | `IOException` (DataStore) | inline notice "Couldn't save. Try again." |
| token read | `KeystoreUnavailableException` | that call gets `NotConfigured`; the token is kept (§9) |
| `StateStore.update` | `IOException` | snackbar "Couldn't save. Try again." |

Core's own log messages (`LlmError.message`) are safe to log. The UI shows `friendlyMessage` only.

---

## 13. Threading and cancellation

- **Main safety.** Every core `suspend` function is main-safe: HTTP runs on OkHttp's dispatcher, large JSON encoding/decoding on `Dispatchers.Default`, file I/O on the injected IO dispatcher.
- **Store.** `StateStore.update` is serialized by a `Mutex`. Transforms must be pure and fast: no suspension and no I/O inside them.
- **Scopes.** `viewModelScope` is for work tied to a screen: the LLM calls the user waits for, and polling the recording level. `appScope` is for commits, aisle sync, `sortItems` and the icon cascade (decider batcher and draw workers).
- **Cancellation.** Cancelling a coroutine cancels its OkHttp call. Closing the Add sheet cancels the in-flight ingest (`AddViewModel.cancel()`).
- **Bitmaps.** Decoding, the icon trim/downscale and JPEG/WebP encoding run on `Dispatchers.Default`/`IO`, never in composition. `IconImages.cached` touches memory only.
- **Recording.** It is foreground-only and stops on `Lifecycle.Event.ON_STOP`, except during a configuration change (`activity.isChangingConfigurations`): the recorder and meter live in the ViewModel and survive a rotation.

---

## 14. Screenshot testing

The tool is **Roborazzi 1.76.0** with Robolectric 4.17, native graphics, Robolectric **SDK pinned to 36** (`app/src/test/resources/robolectric.properties`; SDK 37 crashes Espresso's input injection) and `--add-opens java.base/jdk.internal.access`. The skeleton runs `recordRoborazziDebug` successfully. Paparazzi 2.0.0-alpha also works, but it is alpha; don't mix the two.

- **Goldens** live in `app/src/test/screenshots/`. Previews (app-shell) are named `<Class>.<Function>.<Preview_name>.png`. Explicit screen tests (app-screens) are named `<screen>_<state>_<light|dark>.png`.
- **Explicit screen tests.** Use `@RunWith(AndroidJUnit4::class)`, `@GraphicsMode(NATIVE)`, `@Config(qualifiers = "w411dp-h891dp-night-xxhdpi")` (or without `-night`) and `androidx.compose.ui.test.junit4.v2.createComposeRule`. Render the **stateless** `XxxScreen(state)`, then call `onRoot().captureRoboImage("src/test/screenshots/<name>.png")`.
- **Qualifier order.** `night` comes before the density. The other order throws.
- **States to cover:** List: empty; mixed sections with a collapsed basket, a cleared aisle, a drawing sparkle, a PENDING item and checked items. Add sheet: write, thinking, preview with sources, failed with plain lines, and speak with a transcript. Edit sheet (with the icon grid). Aisles editor. Settings: token set, default endpoint vs proxy, a failed connection test, and a new host asking for its token.
- **Commands.** Record locally with `recordRoborazziDebug`. CI runs `compareRoborazziDebug`, which doesn't fail, and uploads the diffs. Switch CI to `verifyRoborazziDebug` once the art settles.

---

## 15. CI and release (app-shell)

The workflow is `.github/workflows/android.yml`. Copy it verbatim from `research/android_build.md` §6, where it is proven and passes actionlint.

- **Triggers and environment:** push to any branch, tags `v*`, pull requests and `workflow_dispatch`. The runner is ubuntu-24.04 with JDK 21. setup-android installs `platforms;android-37.0 build-tools;36.0.0`. Gradle uses `cache-provider: basic`.
- **Steps:** (1) `./gradlew -p core test`; (2) `./gradlew :app:compareRoborazziDebug :app:lintDebug :app:assembleDebug :app:assembleSideload -Ppicnic.versionCode=<run_number> -Ppicnic.versionName=<ver>`; (3) upload the bare **sideload** APK as `picnic-list-<ver>.apk` (`archive: false`) and the reports.
- **Releases:** on a `v*` tag, a release job attaches the APK to a GitHub Release.
- **The installable APK is the `sideload` build type:** `initWith(debug)` (same code, not minified, no `debugImplementation` tooling) but `isDebuggable = false`, signed with the committed `app/debug.keystore`. A debuggable APK would let anyone with brief USB access `adb shell run-as` into the app's files and Keystore key (the stored token) or attach a debugger. Same applicationId and key, so it installs over earlier debug APKs and successive sideloads update in place. `assembleDebug` stays for development.
- **Signing:** debug and sideload builds are signed with the committed `app/debug.keystore`. Play will need an AAB signed with a separate upload key from CI secrets. Play rejects debuggable builds.
- **Secrets:** CI needs none. Live core tests are never run in CI.

---

## 16. Proxy contract (proxy owner; consumed by core)

Cora's Cloudflare Worker gives friends a token that is not an OpenAI key. To the app, it looks exactly like OpenAI: base URL `https://picnic.cora.earth/v1` and `Authorization: Bearer pk_<device token>`.

### 16.1 Routes and policy

| Route (passthrough to `https://api.openai.com/v1`) | Body | Notes |
|---|---|---|
| `POST /v1/responses` | JSON (`stream` false or true; pass SSE through unbuffered) | Model mapped, then checked against the allow-list. The proxy may force `store:false`, cap `max_output_tokens` at 16000 and strip `service_tier`. |
| `POST /v1/audio/transcriptions` | multipart, ≤ 26 MB | Map and check the `model` field. Re-send the form unchanged otherwise, keeping the filename (its extension matters). |
| `POST /v1/images/edits` | multipart (also accept JSON), ≤ 20 MB | Map and check the model. Cost policy: `n` ≤ `MAX_IMAGES_PER_REQUEST` (1), `quality` in `IMAGE_ALLOWED_QUALITIES` (`low,medium`), `size` in `IMAGE_ALLOWED_SIZES` (`1024x1024,auto`), ≤ `MAX_IMAGE_INPUTS` (2) reference images; a missing quality/size gets the list's first value unless `auto` is allowed. Otherwise 400 `invalid_value`. |
| `GET /v1/models/{id}` | none | Used by "Test connection"; `{id}` is mapped and checked like a body model |

- **Anything else:** 404, using OpenAI's error shape.
- **Auth:** device tokens are stored only as SHA-256 hashes, in KV (`TOKENS`: hash → `{name, created, disabled}`). A bad or disabled token gets **401** `{"error":{"message":"Invalid device token","type":"invalid_request_error","code":"invalid_api_key"}}`.
- **Allow-list:** `ALLOWED_MODELS` holds **upstream** model ids (after mapping). The defaults are the four `ModelDefaults` ids: `gpt-5.6-terra`, `gpt-6-luna`, `gpt-transcribe`, `gpt-image-2.5-flare`. A model outside it gets **403** `{"error":{…,"type":"invalid_request_error","code":"model_not_allowed"}}` (core maps it to `BadResponse`, not `Unauthorized`).
- **Rate limiting:** per token (Workers Rate Limiting binding) → **429** `{"error":{…,"type":"rate_limit_error","code":"rate_limit_exceeded"}}` plus `Retry-After`.
- **Headers:** the real key is the Worker secret `OPENAI_API_KEY`. The client's `Authorization` header is replaced and never forwarded or logged. Pass `x-request-id`, `retry-after`, `content-type` and `openai-processing-ms` back to the client.
- **Long requests:** they can take 20–40 s or more. Stream the upstream body and don't add a timeout shorter than 150 s.
- **Tooling:** a minting script prints a new token once and stores only its hash. Tests run locally with Node 22 (vitest or `node:test`, with a fake upstream `fetch`). `docs/PROXY.md` covers deploy (wrangler), secrets, KV, token management and `MODEL_MAP`.

The app sends `safety_identifier` (a hashed install id) on `/responses`, so OpenAI abuse signals can be attributed per device.

### 16.2 `MODEL_MAP` (amendment A1)

An optional Worker environment variable (plain var or secret) holding a JSON object of requested model id → upstream model id, for example `{"gpt-5.6-terra":"gpt-6.2-terra","gpt-6-luna":"gpt-6.1-luna"}`. It lets the maintainers upgrade the models every installed APK uses without shipping a new APK.

- **Where:** the `model` field of JSON bodies (`/responses`, JSON `/images/edits`), the `model` form field of multipart bodies (`/audio/transcriptions`, multipart `/images/edits`; re-encode the form with every other part byte-identical, filenames included), and the `{id}` of `GET /models/{id}`.
- **Order:** map **first**, then check `ALLOWED_MODELS` against the mapped id. Unmapped ids pass through unchanged and are checked as-is.
- **Single step:** no chaining (`a→b` and `b→c` does not turn `a` into `c`). Keys and values are exact, case-sensitive ids.
- **Bad config fails closed:** if `MODEL_MAP` is not a JSON object of string → string, every request gets **500** `{"error":{"message":"Proxy misconfigured","type":"server_error","code":"proxy_misconfigured"}}` and the Worker logs why (never the body or token).
- **Responses are not rewritten:** the upstream `model` field (the real model) reaches the app; the app only logs it.
- **Parameter compatibility:** the app picks request parameters for the model id it *sent* (§7.2). If a map target rejects one (e.g. a gpt-5.5 target rejects the prompt-cache fields; gpt-6.1-sol rejects effort `none`), the client's one-shot downgrade retry absorbs the 400 at the cost of one extra round trip. Prefer targets in the same family, and run the live smoke (`./gradlew -p core run --args="test"` with `PICNIC_BASE_URL` pointing at the proxy) after changing the map.

---

## 17. Privacy and Play compliance

- **Permissions:** `INTERNET` and `RECORD_AUDIO` (runtime, foreground only). There is **no `CAMERA` permission**, because the system camera intent is used. `<uses-feature>` marks camera and microphone as `required="false"`. The photo picker needs no permission. The `<queries>` entry for `IMAGE_CAPTURE` is present. Verified in the merged manifest: INTERNET, RECORD_AUDIO and androidx's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` only.
- **Data sent off the device:** text, photos and audio go to OpenAI, or to Cora's proxy, only when the user asks; item names go there in the background, including for "Add as typed": to the fast model for aisle placement and icon decisions, and to the image model for bespoke icons (when "Draw new icons with AI" is on). The Settings help text says the same. Requests use `store: false`. Nothing else leaves the device, and there are no analytics.
- **Backups:** only `state.json` is backed up.
- **Play:** a later Play release needs a privacy policy URL (because of `RECORD_AUDIO`) and a Data safety form that discloses the processing.
- **Security:** no secrets in the repo or APK, ever. `*.env`, `.env*` and `.dev.vars*` are gitignored.
- **Legal and IP:** OFL fonts are bundled with their licences. Art uses only generic genre motifs (see the IP rules in `research/directions.md`), with no real brand packaging.
