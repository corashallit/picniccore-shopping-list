# Kirakira Basket: architecture

Status: v1 contract, 2026-10-01, revised for client amendments A1 (no model settings), A2 (icon matching cascade) and A3 (baskets hold what's gotten); amended 2026-10-02 for the art pass: A4 (settings), A5 (trips and the in-store layout) and A6 (Cora's answers), plus every change listed in `docs/DESIGN_STICKER.md` §19.3, marked **Art pass** where it lands below; 2026-10-03: the contracts as part 1 of the art pass built them, then the additions part 2 made (WP6 motion, WP7 add flows; DESIGN_STICKER §19.2); 2026-10-04: part 3 joins WP9's eggs to WP5's List screen (§10.3); 2026-10-05: the paper decides the ink (`OnPaper`, §11.1–§11.2; DESIGN_STICKER §2.4.1, item 339). Package and applicationId base: `earth.cora.picnic` (unchanged). App name: **"Kirakira Basket"** (D20: the launcher label and the wordmark read `R.string.app_name`; it was "Picnic List" through v1, and the repository, the applicationId and the APK file names `picnic-list-*.apk` keep their old names).

This document is the contract between the people (or agents) building the app in parallel. The Kotlin stub files listed in §3.2 are **normative**: they compile today, and their signatures are the interfaces. Bodies marked `TODO("<owner>: …")` are for the owner to fill in. If this document and a stub disagree, the stub wins. Fix both, and tell the other owners.

**Changing a contract.** You may add to a contract freely: new optional parameters with defaults, new functions, new fields with defaults. Renaming or removing anything, or changing what an existing call means, needs a note to every consumer listed in §3.2. Persisted names (anything in `state.json` or `album.json`, icon keys, basket keys, aisle-id and name normalization) also need a schema migration (§6.4).

**The art pass (direction C, "Furoku Sticker Album").** `docs/DESIGN_STICKER.md` is the authoritative build spec for the art pass: §19 lists its data and contract changes, §20 the acceptance checklist, §21 the ten work packages (WP1–WP10) and their file ownership. Its §19.3 amendments are landed in this document (each marked **Art pass**, with its consumers). Phase 0 (§21.3 step 1) landed every new contract as a compiling stub: signatures are normative like the skeleton's (§3.2), bodies marked `TODO("WP<n>: …")` or `TODO(WP<n>)` are for that package, and v1 behaviour is kept wherever the current screens still call an op. Part 1 (WP1–WP4, WP10) has filled its stubs; where it changed or added a signature, the text below gives the one it built. Where this document and DESIGN_STICKER.md disagree, DESIGN_STICKER.md wins and this document is fixed.

Research this rests on (not in the repo): the OpenAI brief (`research/openai_api.md`, live-verified), the Android build brief (`research/android_build.md`), the icon catalog (`research/icon_catalog.json`) and the art directions (`research/directions.md`, concept renders in `design/concepts/`).

---

## 1. What we're building

An Android grocery list for one friend of Cora's, in Ontario, Canada. It is sideloaded to up to 20 authorized phones through a limited distribution account in Android Developer Console (docs/RELEASING.md); Google Play may come later, so the build stays Play-compliant (targetSdk 36, minimal permissions, no secrets in the APK).

1. **Aisle sorting.** The user keeps a free-text list of their store's aisles, one per line, in walking order. Every item is assigned to one aisle (or Unsorted), and the list is shown grouped by aisle in walking order.
2. **Free-form text.** Input such as "ingredients for pancakes from scratch" goes to a reasoning model with web search, which returns individual, already-categorized items. A plain list such as "milk, 2 lemons, eggs" takes the same path.
3. **Photo of a handwritten list.** The same model transcribes it into items.
4. **Voice.** A recording is transcribed and the transcript is shown. The transcript then goes through the same text pipeline.
5. **Theme.** A rich "picniccore × vintage 90s magical-girl" look. The art direction is still being chosen, so this round builds a clean **theme layer** with tasteful placeholder art. It must never look like Material defaults.
6. **Item icons.** Every item has a tiny icon from a bundled library of 235 catalog keys (the art pass supplies the images). Items without a fitting icon get a bespoke one **drawn live** with `gpt-image-2.5-flare` and cached by item name.
7. **Collapsible aisles with baskets.** A collapsed aisle header draws a little basket holding overlapping mini icons (up to 5) of what has **already been gotten** from that aisle, plus a badge counting what is still to get (amendment A3). Each aisle has a basket kind (16 kinds), assigned by the fast model with a keyword fallback.

Units are metric (g, kg, mL, L), and Canadian specifics apply (4 L bagged milk).

### 1.1 Client amendments (override the original spec)

- **A1. No model settings.** There are no model-name fields, reasoning-effort choices, "Best/Budget" presets or web-search toggle anywhere a user can reach. Models are fixed defaults maintained by the developers in **one place**, core's `ModelDefaults` (§7.1). Settings keep only the user's OpenAI key (with "Test connection") and the "Draw new icons with AI" toggle. Changing a model means editing `ModelDefaults` and shipping an APK.
- **A2. Icon matching cascade.** Every item that doesn't already carry an icon from the ingest call (typed items, the offline raw-lines fallback, renames) goes through: (1) a local match (normalized name against catalog keys, labels and aliases, plus learned icon overrides), then (2) an LLM decision through the `IconDecider` interface (gpt-6-luna today; OpenAI's Decisions API is the intended future implementation, §8.6), then (3) live generation of a bespoke icon. Each step falls through on failure or offline to the category fallback icon. The ingest call's own icon choice counts as step 2 for the items it returns (§8.2).
- **A3. The basket holds what's gotten (Cora).** A collapsed aisle's basket shows mini icons of the aisle's **checked** items only, in the order the "In the basket" section lists them (oldest first; check time isn't tracked). Items still to get never appear in a basket. Nothing ticked yet means an empty basket of that aisle's kind, which fills as items are ticked; a cleared aisle (everything ticked) keeps its "cleared" stamp with a full basket. The badge keeps counting what is still to get, and the header announces both ("Produce, 2 to get, 3 in the basket", collapsed). This holds wherever `AisleBasket` shows mini icons (§4.2, §10.3, §11.4). Basket kinds whose look comes from their contents (flower bucket, ice bucket, bottle carrier, candy jar) keep that built-in decoration when empty; Cora prefers the maximalist look to a bare container, so the badge, not the art, says how much is left. **Art pass:** baskets now fill in check-off order (`GroceryItem.checkedAt`, A5), and the GOT IT hanko lands on the basket lid when the aisle is done (DESIGN_STICKER §5).
- **A4. Surprises and About (settings, DESIGN_STICKER D3, §6.8).** Settings gains a **Surprises** card: Easter eggs (on), Sounds (off), Haptics (on), "Next stop" card (on), Celebration (on), Weather surprises (off), Use my area (off, A6), each a field of `AppSettings` (§9), and an **About & licences** page (the OFL texts, and the hidden Surprise lab, §6.9 there). Models, effort and the web-search toggle still never reach the UI: A1 stands. Consumers: `AppSettings` / `SettingsRepository` (WP8), every package that gates on a switch (WP6 Next card and celebration, WP9 eggs and weather, WP10 sounds and haptics).
- **A5. Trips and the in-store layout (DESIGN_STICKER D1, D2, D13, §7).** A **trip** opens at the first check-off on a list without one and closes on **New trip** or when its list is deleted; its sticker book is filed into the **album** (`album.json`) first. Trip completion is an **invariant** of the state that every item-touching operation reconciles (`TripRules.reconcile`), so a trip finished by a delete, a move or an undo is complete too. `GroceryList.lastClosedAt` anchors the "new" star between trips. **Checked rows stay in place** in their aisle (no "In the basket" section; "Clear checked" becomes New trip). **One open aisle** (`GroceryList.openAisle`: null automatic, "" all folded, else a section id). Schema **v2** with a real v1 → v2 step (§6.4). Consumers: ListViewModel, AddViewModel, AislesViewModel, AisleSync, the icon repository.
- **A6. Cora's answers of 2 October (DESIGN_STICKER §0.4).** The name is **Kirakira Basket**. "Use my area" is an opt-in that requests `ACCESS_COARSE_LOCATION` only, rounds the point to 0.1° before any use and feeds sunset and the forecast through `PlaceSource` (Toronto otherwise; §9, §16). There is a **pantry** (`AppState.pantry`, §4): every AI request carries it and the model leaves out worked-out items it covers, never named ones. The first check-off more than 12 h after a trip's last activity **starts a new book without asking** (`TripRules.startsNewBook`, `toggleCheckedStartingNewBook`, `TripFiling.toggle`; the old book is filed first and Undo restores it, `undoNewBook`). The celebration closes itself behind a visible progress bar (TalkBack: it waits for a tap). The mockup is a frozen reference: the spec wins. Consumers: WP3 (pantry, trips), WP5 (the auto new book), WP6 (celebration), WP7 (pantry rows), WP8 and WP9 (Use my area, the pantry screen), PRIVACY.md.

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
│  ├─ build.gradle.kts          compose, serialization, buildConfig, Roborazzi, signing
│  ├─ debug.keystore            COMMITTED (androiddebugkey / android / android); never regenerate
│  ├─ proguard-rules.pro
│  └─ src/main/{AndroidManifest.xml, kotlin/earth/cora/picnic/…, res/…, assets/icons/…}
│     src/test/{kotlin/…, resources/robolectric.properties (sdk=36), screenshots/ (goldens)}
├─ docs/                        ARCHITECTURE.md, BUILDING.md, OPENAI_KEY.md, PRIVACY.md, PROMPTS.md, RELEASING.md
├─ design/concepts/             art-direction concept renders (reference only)
├─ design/stickers/ fonts/ sounds/   Art pass: the sticker DSL + Android emitter (WP1, WP2), font and sound scripts (WP2)
├─ design/mockup/               Art pass: the r9 mockup generator, frozen as a visual reference (DESIGN_STICKER §18.4)
└─ .github/workflows/android.yml
```

History: the OpenAI proxy (`proxy/`, `docs/PROXY.md`, `.github/workflows/proxy.yml`) was removed; it was last present in commit `e31554e` (`git checkout e31554e -- proxy docs/PROXY.md .github/workflows/proxy.yml`).

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
| lint (CI gate) | `./gradlew :app:lintDebug` (also runs `:app:checkSounds`) |
| **Art pass:** sticker art in sync with the DSL | `./gradlew :app:checkStickers` (Python 3.11+ with `design/stickers/requirements.txt`; run locally after any change under `design/stickers/`) |
| **Art pass:** sound clips in sync with their recipes | `./gradlew :app:checkSounds` (Python 3 standard library only) |

Sandbox-only flags must **never** be committed. Use `-I <scratch>/robolectric-mirror.init.gradle` for Robolectric jar downloads. The Maven Central mirror lives in `~/.gradle/init.d`.

Verified on this skeleton (2026-10-01): `./gradlew -p core build` (9 contract tests green), `./gradlew :app:assembleDebug` (APK: compileSdk 37, targetSdk 36, permissions INTERNET + RECORD_AUDIO only, `icon_catalog.json` packaged), `:app:recordRoborazziDebug` (ThemeSampler previews rendered).

---

## 3. Ownership and interfaces

### 3.1 Files per implementer

**Art pass (§19.3): this table is superseded by `docs/DESIGN_STICKER.md` §21** for the art pass: ten packages (WP1–WP10) plus the **lead**, each with exclusive file ownership. The lead owns this document, DESIGN_STICKER.md, `README.md` and the wiring files every package touches (`AppContainer.kt`, `PicnicApp.kt`, `app/build.gradle.kts`'s check tasks, `settings.gradle.kts`, `res/values*/colors.xml`); packages send their hunks to the lead. Strings: one file per package (`res/values/strings_<pkg>.xml`: `strings_theme` WP4, `strings_list` WP5, `strings_motion` WP6, `strings_add` WP7, `strings_screens` WP8, `strings_eggs` WP9, `strings_a11y` WP10; `strings.xml` keeps `app_name` and `wordmark`, WP8). The table below returns once the pass is merged.

| Owner | Owns (creates/edits) |
|---|---|
| **lead** | `docs/ARCHITECTURE.md`, `README.md`. Root Gradle files are handed to **app-shell** after the skeleton. |
| **core** | Everything under `core/` (sources, resources incl. `prompts/*.txt`, tests, `core/build.gradle.kts`, `core/settings.gradle.kts`, the CLI), plus `docs/PROMPTS.md`. |
| **app-shell** | Root `settings.gradle.kts`/`build.gradle.kts`/`gradle.properties`/`gradle/**`/`gradlew*`/`.gitignore`; `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/debug.keystore` (never change it); `app/src/main/AndroidManifest.xml`, `app/src/main/res/**` (except `values/strings_screens.xml`), `app/src/main/assets/**` (icons, style reference, fonts), the launcher icon.<br>Kotlin: `PicnicApp.kt`, `AppContainer.kt`, `MainActivity.kt`, `settings/**` (DataStore settings, Keystore token store), `platform/**` (captures, photo prep, voice recorder, TokenCipher), `icons/**` (IconRepository, IconImages, bespoke cache, decider batcher, live drawing queue), `ui/theme/**` (tokens, components, decor slots, ItemIcon, AisleBasket, list pieces, glyphs, placeholder art, previews).<br>Tests under `app/src/test/kotlin/earth/cora/picnic/{settings,platform,icons,ui/theme}/**` and the theme preview goldens.<br>Also `.github/workflows/android.yml` and `docs/BUILDING.md`. |
| **app-screens** | `ui/PicnicAppRoot.kt`, `ui/nav/**`, `ui/list/**`, `ui/add/**`, `ui/edit/**`, `ui/aisles/**`, `ui/settings/**`, `ui/common/**` (screens, ViewModels, UI-state classes, navigation), `res/values/strings_screens.xml`.<br>Tests under `app/src/test/kotlin/earth/cora/picnic/ui/{list,add,edit,aisles,settings,nav,common}/**`, including the screen screenshot tests and their goldens in `app/src/test/screenshots/`. |

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
| `core/…/llm/LlmConfig.kt` (`LlmConfig` + `normalizeBaseUrl`, `LlmTimeouts`, `ReasoningEffort`, `ConfigSource`, `TokenSource`) | core | app-shell (settings, container) |
| `core/…/llm/LlmError.kt` | core | app-shell, app-screens |
| `core/…/llm/OpenAiClient.kt` | core | app-shell (container constructs it) |
| `core/…/assistant/GroceryAssistant.kt` (+ input/result types, `assignBasketsOrGuess`) | core | app-shell, app-screens |
| `core/…/assistant/IconDecider.kt` (`IconDecider`, `IconDecision`, `resolve`, `ResponsesIconDecider`) | core | app-shell (icons, container) |
| `core/…/assistant/{OpenAiGroceryAssistant,OfflineParser,AisleSync}.kt` | core | app-shell (container), app-screens |
| `app/…/AppContainer.kt`, `PicnicApp.kt` | app-shell | app-screens |
| `app/…/settings/AppSettings.kt` (`AppSettings`, `SettingsRepository`, `InMemorySettingsRepository`, `maskToken`), `settings/TokenStore.kt` | app-shell | app-screens |
| `app/…/icons/IconRepository.kt` (`IconRepository`, `NoOpIconRepository`; `IconImages` was replaced by `StickerArt` in the art pass) | app-shell | app-screens, theme |
| `app/…/platform/{Captures,PhotoPreparer,VoiceRecorder (AudioRecorder),TokenCipher}.kt` | app-shell | app-screens |
| `app/…/ui/theme/*` (tokens, `PicnicTheme`, components, decor slots, `ItemIcon`, `AisleBasket`, `AisleHeader`, `AisleSectionTitle`, `AisleSectionRow`, `ItemRowCard`, `Glyph`) | app-shell | app-screens |
| `app/…/ui/PicnicAppRoot.kt` (`PicnicAppRoot(container)`) | app-screens | app-shell (MainActivity) |

**Art pass contract files** (phase-0 stubs, DESIGN_STICKER §19; provider = the owning package):

| Contract file | Provider | Consumers |
|---|---|---|
| `core/…/model/Model.kt` (+ `Rarity`, `Trip`, `Receipt`, `NewTripMode`, `AisleStyle`, `PantryEntry`, `BasketKind.MIXING_BOWL`), `Grouping.kt` (in-place shape), `StateOps.kt` (`now` + reconcile, trip ops), `TripRules.kt`, `BookSlots.kt`, `TripStats.kt`, `Pantry.kt` (+ `PantryMatch`), `Metric.kt`, `Aisles.kt` (+ `displayName`, `storeAisleNumber`) | WP3 | WP5, WP6, WP7, WP8, WP9 |
| `core/…/store/AlbumStore.kt` (`Album`, `TripBook`, `AlbumStore` + `flush()`, `referencedCustomIconIds()`, `JsonFileAlbumStore`, `InMemoryAlbumStore`, `AlbumReconciler`), `TripFiling.kt` (the trip ops that write both files, §6.2) | WP3 | lead (container), WP1 (bespoke sweep), WP5, WP8, WP9 |
| `core/…/calendar/{Seasons,SunClock,Place}.kt` (`Season`, `PlaceSource`) | WP3 (Place: WP8 · WP9) | WP5, WP9 |
| `core/…/catalog/Personality.kt` | WP3 | WP1 (manifest), WP6 (`ToggleEvent`) |
| `app/…/ui/theme/{Theme,MotionSpec,Hanko,Lcd,PuffyText}.kt`, `ItemIcon.kt`, `Decor.kt` (`ThemeVariant`, `PicnicTheme(variant, reducedMotion)`, `LocalReducedMotion`, `PicnicMotion` tokens, `readHold`, `words`, `FixedMotionScale`, `CheckMark(checked, seed, stampText)`, `HankoText`, `ItemIcon` C parameters, `StickerFlag`, `LcdChip`, `LcdText`, `LcdPetSprite`, `LcdState`, `PuffyText`) | WP4 | every screen package, WP6, WP9 |
| `app/…/ui/list/motion/{Choreographer,ListPresentation,AnchorRegistry}.kt` (`ToggleEvent`, `ListChange`, `IdentityChoreographer`; part 2: `Choreographer.keepCelebration` / `onUserOpen`, `ToggleEvent.newBook`, `ChangeCause.NEW_BOOK_UNDO` with `ListChange.slot`, `SpotPhase.Appearing.inPlace`), `ListMotion.kt` (`FoldToggles`, `FoldFocusEffect`) | WP6 | WP5, WP9 |
| `app/…/ui/add/{AddUiState,AddViewModel,AddFlows,AddSheet}.kt` (part 2: `AddEvent`, `AddCommit`, `AddPhase.Failed(AddProblem)`, `AddViewModel.requireKey` / `needsKey`, `AddFlows` with `takePhoto` / `pickPhoto` / `recordVoice`, `AddActions.onOpenAppSettings`) | WP7 | WP5 (places the dock, the sheet and the toasts), WP9 (commit hooks) |
| `app/…/ui/eggs/{SurpriseEngine,ThemeVariantSource}.kt` (`NoOpSurpriseEngine`; part 3: the hooks with defaults listed in SurpriseEngine.kt's header, `AppSurpriseEngine(…, variant)`), `EggListHost.kt` (`EggListHost`, `EggListScope`), `EggOverlays.kt` (`EggAnchors`), `EggHooks.kt` (the list pieces) | WP9 | WP4 (MainActivity), WP5 (ListScreen, part 3), WP7 |
| `app/…/platform/{Feedback,Announcer,Timeouts}.kt` (`Cue`, no-op implementations), `app/…/ui/a11y/Semantics.kt` (`TextOverflowed`) | WP10 | WP4, WP5, WP6, WP7, WP9 |
| `app/…/icons/StickerArt.kt` (`StickerArt`, `StickerVector`, `LiveSticker`, `AppStickerArt`; replaces `IconImages`, §8) | WP1 | `ItemIcon` (through `LocalStickerArt`), `IconRepository`, the container, previews |
| `app/…/ui/theme/gen/*.kt`, generated by `design/stickers/build_android.py` (`StickerManifest`: key → drawable, two families, personality, face, source; `BasketManifest`, `DecorManifest`, `LcdGlyphs`, `PatternTiles`) | WP1 (stickers), WP2 (the rest) | WP4, WP5, WP6, WP9 |

### 3.3 Hand-off points between implementers (the seams that must line up)

| From → to | Seam | Rule |
|---|---|---|
| app-screens → core | `store.update { <StateOps> }` | All state changes go through StateOps inside `StateStore.update`, in `appScope`. |
| app-screens → core | `assistant.ingest/transcribe/testConnection`, `OfflineParser.parse`, `aisleSync.sync/sortItems` | Catch `LlmError` only; show `friendlyMessage`. |
| app-screens → app-shell | `icons.submit(items)` after every commit and every rename; `icons.redraw(name)`; `icons.forgetBespoke(name)` after `setItemIcon`; `icons.busy` for row sparkles | Never wait on icons. |
| app-shell → core | `iconDecider.decide`, `assistant.drawIcon`, `store.update { applyIconDecisions / attachCustomIcon }`, `itemsNeedingIconDecision()` | The icon cascade, §8. |
| app-shell → app-screens | `PicnicAppRoot(container)` inside `PicnicTheme` + `LocalStickerArt` and `LocalTimeouts` (art pass; v1: `LocalIconImages`); theme components | Screens use only theme tokens/components (§11). |
| app-screens → core (art pass) | `TripFiling.newTrip / deleteList / toggle / addItems / undoNewBook` | Anything that files a book writes `album.json` before `state.json` through `TripFiling`; a ViewModel never pairs StateOps with album writes itself. |
| app-shell → core | `ConfigSource { settings.llmConfig() }`, `TokenSource { settings.token() }` | Read before every call; the token never leaves `token()`. |

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

**Art pass (A5, A6; DESIGN_STICKER §7.2, §19.1).** All additive, with defaults, so a v1 file loads unchanged:

```kotlin
enum class Rarity { NONE, SHINY, JACKPOT }                                   // persisted by name
// GroceryItem (+) checkedAt: Long? = null (check-off time; basket order, shop time), bookSlot: Int? = null, rarity: Rarity = NONE
// GroceryList (+) openAisle: String? = null (D2), trip: Trip? = null, lastClosedAt: Long? = null   // collapsedAisles kept, ignored by C
// StoreProfile (+) aisleStyles: Map<String, AisleStyle> = emptyMap()
// AppState (+) favourites: Set<String> = emptySet() (normalized names), pantry: List<PantryEntry> = emptyList()
data class Trip(val id: String /* "t-<listId>-<startedAt>" */, val startedAt: Long, val storeName: String, val lastActivityAt: Long,
    val completedAt: Long? = null /* set iff ≥1 item and all checked */, val finaleShownAt: Long? = null,
    val shopMinutes: Int? = null /* frozen at the first completion */, val pet: Boolean = false, val receipt: Receipt? = null,
    val receiptSaved: Boolean = false, val napDismissed: Boolean = false,
    val bonus: BookSticker? = null /* the celebration's bonus sticker, set once per trip (setTripBonus), filed as TripBook.bonus */)
data class Receipt(val printedAt: Long, val dateLabel: String, val store: String, val stickers: Int, val baskets: Int, val minutes: Int?, val shiny: Int)
data class AisleStyle(val family: String? = null, val pattern: String? = null, val storeAisle: Int? = null)
data class PantryEntry(val name: String, val icon: IconRef? = null, val addedAt: Long)
enum class NewTripMode { CLEAR_GOTTEN, UNCHECK_ALL }                         // not persisted
enum class BasketKind { …, MIXING_BOWL }                                     // the 17th kind (D8)
```

`STATE_SCHEMA_VERSION` is **2**, with the real v1 → v2 step (§6.4, WP3); a v2 file without `pantry` loads with an empty pantry and needs no bump. `SectionIds.CHECKED` survives only for the v1 list screen's bridge until WP5 lays rows in place.

**Trips (A5, A6; `model/TripRules.kt`, `TripStats.kt`, `BookSlots.kt`, `TripBooks.kt`, all pure).** `TripRules.reconcile(list, now, storeName = "")` ends every operation that touches items and keeps the invariants: **a trip exists exactly while the list has a sticker** (a checked item): the last un-check or delete closes it without filing, and a sticker that comes back with no trip open (a restore, a revive) opens one with id `tripId(listId, earliest check-off)`; `completedAt` is set iff the list has ≥ 1 item and all are checked; `shopMinutes` is frozen at the first completion; `lastActivityAt` moves only on check and un-check, which set it themselves. Queries: `isComplete(list)`; `finaleOwed(list)` (complete, finale not shown); `finaleIsStale(list, now)` (owed, but completed more than `FINALE_GRACE_MILLIS` = 30 min ago: mark it shown instead of playing it); `newAnchor(list)` (the open trip's `startedAt`, else `lastClosedAt`; null = no "new" stars) and `isNew(list, item)`; `startsNewBook(list, now)` (last activity more than `IDLE_MILLIS` = 12 h ago); `shopIsOver(list, now)` (idle, or the finale shown more than `SHOP_OVER_MILLIS` = 30 min ago: an un-check, a revive or a tear-off then files the book first, DESIGN_STICKER §7.1). `TripStats` (`of`, `formatMinutes`, `lastSessionStart`, `shopMinutes`): sessions break at gaps over 45 min between check-offs, and shop minutes run from the first check-off of the last session to the completion, **or to the last check-off when the completion comes more than 45 min after it** (the last item deleted at home hours later). `BookSlots.assign` / `compact` keep the book's spots; `tripBook(listId, now)` is the book New trip or a delete would file (`bookSticker`, `stamps`, the look from `AisleRotation`).

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
- **"Also here" notes (Cora, 5 October; DESIGN_STICKER §0.4, §6.7).** A trailing `(also: toothpaste, floss)` on an aisle line is `Aisle.note` (`also` in any case, `also here:` too, the closing bracket optional) and is never part of the name or the id, so ids are unchanged by any note and a text without notes parses exactly as before. A note is kept to `Aisles.NOTE_MAX` (80) characters, cut after a whole word. A note lives in `aislesText` like the rest of the aisle (nothing new is persisted, no schema change); `setAisleNote` rewrites only the end of its line, and `renameAisle`, `moveAisle` and `commitAislesText` keep each note with its aisle. Only My store shows notes; `Prompts.contextBlock` sends each aisle with its note (§7.3).

### 4.2 Grouping (`ListGrouping.group(list, aisles, basketFor, styleFor)`)

**Art pass (replaces the CHECKED-section shape below; consumer: ListViewModel / ListUiState).** There is no CHECKED section: checked rows stay in their aisle (D1).

```kotlin
enum class SectionKind { AISLE, UNSORTED }
data class AisleSection(val id: String, val kind: SectionKind, val aisle: Aisle?, val position: Int /* walking position, 0 = UNSORTED */,
    val displayName: String, val storeAisle: Int?, val rows: List<GroceryItem> /* every item, addedAt order */,
    val gotten: List<GroceryItem> /* checked, checkedAt order */, val toGet: Int, val total: Int, val complete: Boolean, val basket: BasketKind)
data class GroupedList(val sections: List<AisleSection>, val total: Int, val checked: Int, val stops: Int,
    val currentSectionId: String?, val openSectionId: String?, val nowSectionId: String?)
```

- Sections: UNSORTED ("Everything else") first **whenever it has any item**, then every aisle with at least one item in walking order. `stops` = the number of sections.
- `rows` are every item, oldest first; `gotten` the checked ones in check-off order (`checkedAt`, falling back to `addedAt`): what the basket holds. `complete` = at least one item and nothing left to get.
- `displayName` is the name without its store prefix (`Aisle.displayName()`, D9); `storeAisle` is `AisleStyle.storeAisle`, else `storeAisleNumber(name)`. The number circle shows `position`.
- `currentSectionId`: the first aisle in walking order with something left; UNSORTED only when it is all that is left. `openSectionId`: `openAisle` null → current, "" → none, a stale id → current. `nowSectionId` (the "now" ring): the open section if it has items left, else the current one.

The v1 rules, kept for reference until WP5 replaces the screen (the phase-0 list bridge derives them from the shape above):

`group` returned a `GroupedList(sections, total, remaining)`. The rules:

- **Which section an item goes in:** its `aisleId` if that id is a current aisle, otherwise UNSORTED.
- **Section order:** UNSORTED first (only when it has unchecked items), then the aisles in walking order, then CHECKED last (only when something is checked). Unsorted goes first because those items need the user's attention.
- **Which aisle sections appear:** an aisle section is present only when it has at least one item. If all of its items are checked, the section is still present, with empty `items` and `cleared = true`.
- **Section contents:** UNSORTED and aisle sections list their **unchecked** items (still to get) in `items`, oldest first. CHECKED lists every checked item, in aisle order and then oldest first.
- **Gotten items per section:** `checkedItems` holds an UNSORTED or aisle section's **checked** items (already gotten), oldest first, the same order CHECKED lists them in (check time isn't tracked). They are what the section's basket holds (amendment A3) and are also listed under CHECKED. `checkedCount` = `checkedItems.size`. Both are empty/0 for CHECKED itself, whose `items` are the gotten ones.
- **Basket and collapsed state:** `basket` = `basketFor(aisle)` for aisles and `BasketKind.DEFAULT` (wicker hamper) for the special sections. `collapsed` = whether the section id is in `list.collapsedAisles`. Every section starts expanded.

### 4.3 State operations (`StateOps.kt`, pure)

The app changes state **only** through these functions, always inside `store.update { … }`. They never do I/O, treat unknown ids as a no-op, and return the *same instance* when nothing changed.

**Art pass (replaces; consumers: ListViewModel, AddViewModel, AislesViewModel, AisleSync).** Every operation that touches items takes `now` and ends with `TripRules.reconcile(list, now, storeName)`, which keeps the trip invariants (§4): `toggleChecked(listId, itemId, now)` (one atomic transition: checkedAt, the book slot, opening the trip, `lastActivityAt`), `addItems(listId, items, now): Pair<AppState, AddOutcome>` (matches incoming names against **all** items: an unchecked match is dropped, a checked match is un-checked; `AddOutcome(added, alreadyThere, revived)` lists item ids), `removeItem` / `restoreItem(…, now)`, `moveItemToAisle(…, now)`, `applyAisleAssignments(map, now)`, `setAislesText(text, guess, now)`. New: `commitAislesText` (line diff; renames keep their items), `renameAisle`, `moveAisle`, `setOpenAisle(listId, sectionId?)` (replaces `setSectionCollapsed`), `newTrip(listId, mode, now): Pair<AppState, TripBook?>` (replaces `clearChecked`; the book is written to the album **before** the state), `deleteList(listId, now): Pair<AppState, TripBook?>` (files a non-empty book first), `markFinaleShown`, `hatchPet`, `printReceipt`, `saveReceipt`, `dismissNap`, `setRarity`, `setFavourite`, `setAisleStyle`, `setAisleBasket`, `setTripBonus(listId, bonus)` (`Trip.bonus`; the first one stays), and the pantry ops (`model/Pantry.kt`: `pantryText`, `setPantryText` (splits on new lines, commas, semicolons and " and ", drops quantities, dedupes by `normalizeName`, keeps the icon and `addedAt` of unchanged names), `addToPantry`, `removeFromPantry`, `setPantryIcon`, `inPantry`). **The check-off tap is `toggleCheckedStartingNewBook(listId, itemId, now): Pair<AppState, AutoNewBook?>`** (A6): it is `toggleChecked` unless it checks an item on a trip `TripRules.startsNewBook` calls idle (then the old book is filed, `newTrip(CLEAR_GOTTEN)`, and the item is checked onto a fresh trip) or un-checks one on a trip whose shop is over (`TripRules.shopIsOver`: the book is filed with the sticker in it and the item comes back unchecked on the fresh list; `addItemsStartingNewBook` and `removeItemStartingNewBook` do the same for a revive and a tear-off), and `AutoNewBook(book, undo: NewBookUndo(listId, itemId, trip, cleared, lastClosedAt, openAisle))` carries what the toast's Undo needs. `undoNewBook(undo, now)` brings the old trip and its cleared items back and checks everything checked since into the old book. Screens call both through `TripFiling` (§6.2), which writes the album first. `ingestContext` also carries the first 200 pantry entries. Full signatures: DESIGN_STICKER §19.1. Phase 0 keeps v1 behaviour where noted in `StateOps.kt`; `clearChecked` and `setSectionCollapsed` stay, deprecated, until WP5's screen stops calling them.

| Function | Semantics |
|---|---|
| `StateOps.initialState(listId, now)` | One empty list "Groceries", default store |
| `currentList()`, `list(id)`, `findItem(itemId)`, `updateList(id) {}` | accessors |
| `addItems(listId, items)` | Append. Drop any item whose `normalizeName` matches an unchecked item on the list or an earlier item in the batch. Callers find the ids actually added with `findItem` on the returned state. |
| `toggleChecked`, `removeItem`, `restoreItem(listId, item, index)` | For undo, the caller captures the item and its index before calling `removeItem`. |
| `editItemDetails(listId, itemId, name, quantity, note, catalog)` | Name (blank = keep), quantity and note (blank = null). **If `normalizeName` changes**, the icon is re-resolved locally (cascade step 1): `customIconId = null`; `localIcon(newName)` → EXACT, else `DEFAULT_ICON` + PENDING. The caller then calls `icons.submit(listOf(updated))`. |
| `moveItemToAisle(listId, itemId, aisleId?, now)` | Sets `manuallyPlaced`. When `aisleId != null`, records or replaces a `Correction(normalizeName(name), aisleId, now)`. Capped at 200. |
| `moveItemToAisleWithUndo(…)` / `undoMove(undo, now)` | (5 October) The same move with a `MoveUndo` for its note's Undo, which puts back the old aisle (unless the item moved again since) and the correction the move replaced; the corrections exactly as before when nothing else touched them. |
| `setAisleNote(aisleId, note?)` | (5 October) The aisle's "Also here" note (blank removes it); only the end of its line changes: no id, item, basket, style or correction. |
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
| `ingestContext(listId)` | Aisles (each with its "Also here" note), the newest valid corrections (≤ 60), the names of unchecked items, and `iconOverrideMap()`. |

---

## 5. Icon and basket catalog (core, `earth.cora.picnic.core.catalog`)

The catalog is `core/src/main/resources/icon_catalog.json`, a verbatim copy of `research/icon_catalog.json` (validated by `research/validate_icon_catalog.py`). It contains:

- 20 `categories` `{key,label,fallbackIcon}`; every fallback is a `<category>_generic` key
- 235 `icons` `{key,label,category,aliases[],drawHint}` (English and French aliases)
- 16 `baskets` `{key,label,drawHint,keywords[]}`; **Art pass:** 17, with `mixing_bowl` (D8; its keyword tuning so the default store maps per DESIGN_STICKER §5.2 is WP3's `BasketCatalogTest`)
- **Art pass (WP3):** per icon optional `"tags"` (`roll`, `hop`, `picnic`, `birthday`, `frozen`) and `"personality"` (`berry`, `egg`, `bread`, `can`, `frozen`; core `Personality`), and a new `peameal_bacon` key (236 icons). `displayRef` returns the capsule sentinel `IconRef(IconCatalog.CAPSULE)` (`"_capsule"`) for PENDING and busy items (D5; display only). New queries: `keysTagged(tag)`, `tagsOf(key)`, `personalityOf(key)`, `collectibleKeys` (every key but the generics) and `collectKey(item)` (what a check-off collects: `"c:<customIconId>"` (`COLLECTED_CUSTOM_PREFIX`) for a live sticker, the key for an EXACT non-generic icon, else null until it resolves).

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
| `filesDir/state.json` | core `JsonFileStateStore` | `AppState` (lists, store, corrections, icon overrides; art pass: trips, favourites, pantry) | **Yes**: on the allow-list (`data_extraction_rules.xml`, `backup_rules.xml`) |
| `filesDir/album.json` | core `JsonFileAlbumStore` (**Art pass**, A5) | `Album`: collected stickers, filed trip books, sheets, perfect-week streak | **Yes**: the second allow-list entry (`BackupAllowListTest`) |
| `filesDir/datastore/settings.preferences_pb` | app-shell `DataStoreSettingsRepository` | `draw_icons`, `install_id`, `token_hint` (a `base_url` from older builds is removed on upgrade, §9) | no |
| `noBackupFilesDir/token.bin` | app-shell `KeystoreTokenStore` | TokenCipher blob `base64(iv):base64(ct)` | no (and undecryptable elsewhere) |
| `filesDir/icons/custom/<customIconId>.webp`, `<customIconId>.cut`, `filesDir/icons/index.json` | app-shell `IconRepository` | bespoke icons and the name index. **Art pass:** a v4 sticker is a 208 px lossless WebP plus its die-cut path (`.cut`); one without a `.cut` is a legacy 192 px icon (§8.4) | no |
| `cacheDir/captures/*.jpg`, `cacheDir/voice/*.m4a` | app-shell platform | transient capture files; deleted after use | no |

After a backup restore, items may carry a `customIconId` whose file isn't there: `ItemIcon` then draws the item's library sticker (§8.5), so nothing breaks.

### 6.2 `StateStore` contract

```kotlin
interface StateStore {
    val states: Flow<AppState>                                  // current (loads on first use), then each commit
    suspend fun current(): AppState
    suspend fun update(transform: (AppState) -> AppState): AppState   // Mutex-serialized; emit, then persist (a failed write emits the old state again and throws)
}
class JsonFileStateStore(file: File, initial: () -> AppState, ioDispatcher = Dispatchers.IO)
class InMemoryStateStore(initial: AppState)                     // tests/previews (implemented)
```

- **Loading:** lazy, on the first `states` collection or `current()`, on the IO dispatcher. Nothing is read in the constructor, so `AppContainer` stays cheap.
- **Writing:** encode with `CoreJson.json`; write `state.json.tmp` in the same directory; `FileOutputStream.fd.sync()`; `Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)`. The new state is emitted before the write, so a check-off's chain starts at her tap rather than after the fsync (§8.3 beats are measured from the tap). If the write fails, memory and observers go back to the previous state (emitted again) and `update` throws `IOException`. The UI shows the snackbar "Couldn't save."
- **Unreadable JSON:** rename the file to `state.json.corrupt-<ms>` and start from `initial()`. Never crash on a bad file.
- **Missing file:** start from `initial()`; nothing is written until the first update.

**Art pass (replaces; consumers: AppContainer, the backup rules).** `JsonFileStateStore` is refactored onto a generic `JsonFileStore<T>(file, serializer, version, migrations, repair)` (atomic write, migrations, repair), and `JsonFileAlbumStore` uses it for `album.json`:

```kotlin
interface AlbumStore { val album: Flow<Album>; suspend fun current(): Album; suspend fun update(transform: (Album) -> Album): Album
    suspend fun flush()                                    // writes a coalesced update now; throws IOException
    suspend fun referencedCustomIconIds(): Set<String> }   // filed books + "c:<id>" collected keys (§8.4)
suspend fun AlbumStore.file(book: TripBook): Album      // files and flushes: durable before state.json changes
class InMemoryAlbumStore(initial: Album = Album()) : AlbumStore     // tests, previews, the Surprise lab sandbox
// Album ops (pure): file(book) (idempotent by trip id; counts finished trips and the perfect streak), unfile(book, before),
// collect(item, tripId, catalog, now), addHolo, addPicnic, markSeen, unlockSheet. Album.tally holds "<tripId>|<itemId>|<key>"
// for each sticker counted on a trip still open, so collecting is idempotent; filing drops the trip's entries.
object AlbumReconciler { fun reconcile(state: AppState, album: Album, catalog: IconCatalog, now: Long): Album }
object TripFiling {   // store/TripFiling.kt: the trip operations that write both files, album first
    suspend fun newTrip(store, album, listId, mode, now): TripBook?
    suspend fun deleteList(store, album, listId, now): TripBook?
    suspend fun toggle(store, album, catalog, listId, itemId, now): Toggle   // Toggle(autoNewBook: AutoNewBook?, albumBefore: Album?)
    suspend fun addItems(store, album, listId, items, now, then): Add         // (item 315) an add after 12 h idle files the
        // book first (addItemsStartingNewBook); Add(state, outcome, newBook: Toggle?)
    suspend fun removeItem(store, album, listId, itemId, now): Tear           // the tear-off; a got row of a shop that is over
        // (TripRules.shopIsOver) files the book first (removeItemStartingNewBook); Tear(removed, newBook: Toggle?)
    suspend fun undoNewBook(store, album, toggle: Toggle, now): Boolean      // the state first (only while newBookStands),
        // then Album.unfile; false when a newer book stands
    fun launchAlbumSync(scope, store, album, catalog, clock): Job }         // AppContainer.startOnce launches it once
```

Album writes are coalesced (one per 500 ms burst). **New trip, deleting a list with a book and the automatic new book (A6) write the album first, then `state.json`**, through `TripFiling`; filing is idempotent by trip id. `launchAlbumSync` runs `AlbumReconciler` on every committed state: it collects check-offs (and stickers that resolve after theirs), skips trips already filed and drops stale tally entries, so a crash between the two files or inside the coalesce window is repaired on the next start. Filed books are capped at 200 (`Album.MAX_BOOKS`), newest first.

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

**Art pass:** schema **v2** (DESIGN_STICKER §7.2) is a real step: checked items get `checkedAt = addedAt` and book slots, a list with checked items gets a trip (`finaleShownAt` set, so no surprise finale on her first open), stored basket kinds that equal the v1 heuristic's guess are re-guessed once, and pre-v4 bespoke icons are marked legacy. `StateMigrationV2Test` has one fixture per case. The step has landed (WP3): `STATE_SCHEMA_VERSION` is 2. `JsonFileStateStore` and `JsonFileAlbumStore` share `JsonFileStore<T>` (atomic write, migrations, quarantine of an unreadable file, `.v<old>.bak` before the first write over another version).

Nothing has shipped yet, so v1 includes `IconFit.PENDING` and `iconOverrides`. From the first sideloaded APK on, to change the schema: bump `STATE_SCHEMA_VERSION`, add a migration step, add a fixture test that reads an old file. Adding a field with a default needs no migration; renaming, removing or changing the meaning of one does. Persisted names (field names, enum serial names, icon and basket keys, the slug and normalize rules) never change without a migration.

---

## 7. LLM layer (core `llm` + `assistant`)

### 7.1 Models: `ModelDefaults` is the one place (amendment A1)

`core/src/main/kotlin/earth/cora/picnic/core/llm/ModelDefaults.kt` is **the single source of truth** for models and their request details. There is no user setting, no persisted override and no preset list. Maintainers change a value there and ship an APK.

| Use | `ModelDefaults` | Request details |
|---|---|---|
| Ingest (text, photo, transcript) | `SMART` = `gpt-5.6-terra`, effort `low`, `max_output_tokens` 12000 | `web_search` for text and transcript input (`WEB_SEARCH`: `search_context_size` `low`, `user_location` CA / Ontario / America/Toronto); none for photos |
| recategorize, assignBaskets, IconDecider | `FAST` = `gpt-6-luna`, effort `none`, `max_output_tokens` 4000 | no tools |
| Transcription | `TRANSCRIBE` = `gpt-transcribe`, `languages[]=en`, prompt "A person dictating a grocery list in Ontario, Canada.", ≤ 100 keywords | |
| Live icons | `IMAGE` = `gpt-image-2.5-flare`, quality `low`, `1024x1024`, background `transparent`, `webp`, compression 80 | |

`ModelDefaults.models: ModelSet` bundles them. `OpenAiGroceryAssistant(client, catalog, models = ModelDefaults.models)` and `ResponsesIconDecider(client, catalog, model = ModelDefaults.FAST)` take them as defaulted parameters only so that tests and the developer CLI can vary them. App code never passes them.

**Connection config (not user-editable; the key comes from `TokenSource`):**

```kotlin
data class LlmConfig(val baseUrl: String = DEFAULT_BASE_URL, val safetyIdentifier: String? = null) {
    companion object { const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
                       fun normalizeBaseUrl(input: String): String? }   // implemented
}
object LlmTimeouts { CONNECT 15 s; INGEST 120 s; FAST 45 s; TRANSCRIBE 60 s; IMAGE 120 s; TEST_CONNECTION 20 s }
fun interface ConfigSource { suspend fun config(): LlmConfig }
fun interface TokenSource { suspend fun token(): String? }
```

`normalizeBaseUrl` trims, drops a trailing `/`, and requires an absolute `https://` URL with a host and no query or fragment (`http://` only for `localhost`, `127.0.0.1`, `[::1]`: tests and local development). `OpenAiClient` checks it before every call. **The app never changes `baseUrl`:** there is no address setting, so every request goes to `https://api.openai.com/v1`. The field stays for tests (MockWebServer) and the developer CLI's `PICNIC_BASE_URL`. **The token is never inside `LlmConfig`.** `ConfigSource.config()` and `TokenSource.token()` are read before every call, so Settings changes apply immediately.

### 7.2 `OpenAiClient` (core-internal behaviour; the app only constructs it)

```kotlin
class OpenAiClient(config: ConfigSource, token: TokenSource, retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
                   log: LlmLog = LlmLog.Default, isOnline: () -> Boolean = { true }) {   // isOnline: 5 October, offline
    suspend fun responses(body: JsonObject, timeout: Duration = LlmTimeouts.INGEST): ApiResult
    suspend fun transcription(model: TranscriptionModel, audio: ByteArray, fileName: String,
                              keywords: List<String>, timeout: Duration = LlmTimeouts.TRANSCRIBE): ApiResult
    suspend fun imageEdit(model: ImageModel, prompt: String, images: List<ImagePart>, mask: ImagePart? = null,
                          timeout: Duration = LlmTimeouts.IMAGE): ImageResult   // art pass (v4): replaced imageEdit(…, reference, …)
    suspend fun model(id: String, timeout: Duration = LlmTimeouts.TEST_CONNECTION): ApiResult
}
data class ApiResult(val json: JsonObject, val requestId: String?, val latencyMs: Long)
class ImagePart(val bytes: ByteArray, val fileName: String, val mimeType: String = OpenAiClient.imageMediaType(fileName))
class ImageResult(val bytes: ByteArray, val requestId: String?, val latencyMs: Long)
```

- **Endpoints:** all are relative to `baseUrl`, and auth is `Authorization: Bearer <token>`. The app always uses api.openai.com, with the user's OpenAI key as the bearer token.
- **Not configured:** a missing or blank token, or a base URL that fails `normalizeBaseUrl`, throws `LlmError.NotConfigured` without sending a request.
- **Offline** (DESIGN_STICKER §0.4, 5 October): `isOnline` (the app passes its `NetworkStatus`; the CLI and tests leave it true) is read before every call, after the key checks; false throws `LlmError.Offline` ("You’re offline right now. Try again once you’re back online.", retryable) with no request and no wait, so every caller (ingest, transcribe, recategorize, the IconDecider, drawings, the key test) fails at once in airplane mode.
- **Implementation:** OkHttp 5 stays an implementation detail of core (`implementation`, not `api`). Calls go through `enqueue` inside `suspendCancellableCoroutine`, and `invokeOnCancellation { call.cancel() }` cancels them. The per-call timeout is the OkHttp call timeout; connect timeout `LlmTimeouts.CONNECT`. Large JSON encode/decode runs on `Dispatchers.Default`. `http.newCall` runs on `Dispatchers.IO`, because the first call builds the lazy `OkHttpClient` (platform TLS, system CA store) and callers are often on the main thread.
- **Logging:** never log the token, the `Authorization` header or request bodies. Log the method, path, status, `x-request-id` and latency.
- **Retries:** up to 2 extra attempts on 429 rate limits (**not** `insufficient_quota` or the spend/usage-limit codes) and on 500/502/503/504. Back off 1 s, then 2 s, with up to 0.5 s of jitter. Honour `Retry-After` up to 20 s. **`imageEdit` is never retried automatically:** a drawing is slow and billed, and its caller (the app's icon queue, §8.3) retries it at most once itself.
- **Model capabilities** (`ModelCapabilities`, internal, keyed by the model id being sent; with fixed models this mostly matters when `ModelDefaults` moves to a new model or the developer CLI compares models):
  - `gpt-5.6*` and `gpt-6*`: send explicit prompt caching (`prompt_cache_options: {"mode":"explicit"}` plus `prompt_cache_breakpoint: {"mode":"explicit"}` on the last stable `input_text` part). Every other family gets **no** cache fields (gpt-5.5/5.4 answer 400 `invalid_parameter`).
  - `gpt-6.1-sol*` and `gpt-6-astra*` don't accept `none`: send `low`.
  - `gpt-5-mini|nano`: `minimal` instead of `none`, and no `web_search` with `minimal`.
  - **Never send `temperature`.** Always send `store: false`, and `safety_identifier` when `LlmConfig.safetyIdentifier` is set.
- **One-shot capability downgrades** on HTTP 400 from `/responses`, each tried at most once per call:
  - `param == "reasoning.effort"`: parse "Supported values are: …" and retry with the nearest supported value, going up first.
  - `param` is `prompt_cache_options` or `prompt_cache_breakpoint`: retry without the cache fields.
- **Error mapping** (`LlmError`; HTTP details in `research/openai_api.md` §2.8):
  - 401 and 403 → `Unauthorized`, except OpenAI's 403 `model_not_found` (below). The invalid-key message echoes a masked key, so drop it.
  - A 401/403 whose message says "insufficient permissions" (a restricted key without a permission: "… Missing scopes: api.model.read. …") → `Unauthorized(missingScopes)`. Only well-formed scope names are kept from the text, and the friendly message names them: "This OpenAI key is missing a permission the app needs (api.model.read). Check the key's permissions." (setup: docs/OPENAI_KEY.md).
  - 429 with type `insufficient_quota` or a spend/usage-limit code (`credit_balance_exhausted`, `organization_spend_limit_exceeded`, `project_spend_limit_exceeded`, `organization_usage_limit_exceeded`) → `QuotaExceeded`: "The OpenAI account behind this key is out of credit or over its monthly budget." Any other 429, or 503 after retries → `RateLimited`.
  - 404 `model_not_found`, and the 403 `model_not_found` OpenAI sends when the key's project has an allowed-models list without the model ("Project … does not have access to model …") → `BadResponse(code = "model_not_found")`: "This OpenAI key can't use one of the AI models the app needs. Check which models its project allows." (there is no model setting to point at). Other 4xx, including any other 404 → `BadResponse(code)`.
  - Any 5xx after retries → `Network`.
  - A transport failure **before the call got a connection** (DNS, refused, a connect timeout, a failed TLS handshake, also when the call timeout ran out while connecting) → `Network(beforeConnect = true)`: "Couldn't reach OpenAI. Check your connection?", never "That took too long". An OkHttp `EventListener` marks each attempt's `connectionAcquired` (a `ConnectionMark` request tag), so no exception text is parsed. A **connected** call whose call timeout or socket timeout runs out → `Timeout`; any other IOException → `Network` (including `UnknownServiceException` for cleartext). `LlmError.unreached` (Offline, or Network before a connection: nothing reached OpenAI) and `LlmError.transport` (Offline, Network, Timeout) name the two groups the app acts on.
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
                         iconOverrides: Map<String, String> = emptyMap() /*normalized name -> key*/,
                         appLanguage: String = "English", pantry: List<String> = emptyList() /*art pass: first 200, as written*/)
data class ProposedItem(name, quantity?, note?, aisleId? /*current id or null*/, icon /*valid*/, iconFit, aisleFromCorrection,
                        skipReason: SkipReason = NONE, named: Boolean = true, pantryEntry: String? = null)   // art pass
enum class SkipReason { NONE, ALREADY_ON_LIST, OPTIONAL, IN_PANTRY }                                        // art pass
data class IngestResult(items, message, sources: List<WebSource>, skippedDuplicates, meta: CallMeta,
                        lines: List<PhotoLine> = emptyList(), recipe: RecipeInfo? = null)                    // art pass
data class PhotoLine(text, crossedOut, correctionOf?, itemIndex?); data class RecipeInfo(title, recipesCompared?, servings?)
data class CallMeta(model?, requestId?, latencyMs, webSearches)
class IconDrawRequest(itemName, sheet: ByteArray, mask: ByteArray, subject: String, families: List<String>)
    // art pass (v4, WP1): replaced IconDrawRequest(itemName, hint?, styleReference); consumers AppIconRepository, LiveIconDrawer
class DrawnIcon(bytes, mimeType = "image/webp", meta); data class ConnectionCheck(model, meta)
fun ProposedItem.toGroceryItem(id, now, source): GroceryItem
suspend fun GroceryAssistant.assignBasketsOrGuess(aisles, guess): Map<String, BasketKind>   // never throws LlmError
```

Every operation is main-safe and cancellable, and throws only `LlmError` (plus `CancellationException`).

**Art pass (owner WP3; consumers WP7, the Add flows).** The ingest schema gains per item `skip_reason` (`none` / `already_on_list` / `optional` / `in_pantry`), `named` (she named it versus the model worked it out) and `pantry_entry` (the covering entry, "" unless `in_pantry`), and top-level `lines` (photos: `[{text, crossed_out, correction_of, item_index}]`, else empty) and `recipe` (`{title, recipes_compared, servings}` or null); client side these are `ProposedItem.skipReason` / `named` / `pantryEntry`, `IngestResult.lines` / `recipe`. `IngestContext.pantry` carries the first 200 pantry entries as written (`PANTRY_IN_CONTEXT`) and the context block prints them on the line "In the pantry (she keeps these at home): …" ("nothing yet" when empty). **`PantryMatch` backs the model up:** `covers(entry, itemName)` is `normalizeName` equality with singular/plural folding of the last word and nothing more ("flour" does not cover "almond flour"); `apply(items, pantry)` never skips a named item for the pantry and skips a worked-out item an entry covers as `in_pantry`, even when the model kept it. `AisleSync.resortAll()` re-files every item not placed by hand; `normalizeMetric(quantity)` (`model/Metric.kt`) turns lb, oz, fl oz, qt and gal into metric (cup, tbsp and tsp only as a recipe amount). `IconDecision` gains `look` and `families` (additive, WP1, §7.4).

**Aisle notes (Cora, 5 October; DESIGN_STICKER §0.4).** The context block writes each aisle with its "Also here" note (`- produce: Produce (also here: toothpaste)`; an aisle without one reads as before), so ingest, recategorize and AisleSync all see it; both rule files say a note beats where things usually go and a learned correction still wins (corrections are applied client-side first anyway). `assignBaskets` sends names only (a basket suits the aisle's main goods). PROMPTS.md §2.2, §3.

**ingest** makes one `POST /responses` call to `ModelDefaults.SMART` (body shape verified live, `research/openai_api.md` §2.1):

- **Fixed fields:** `store: false`, `max_output_tokens`, `reasoning.effort`, `safety_identifier`.
- **Output format:** `text.format` is a **strict** `json_schema` named `grocery_items`: `{items:[{name, quantity|null, note|null, aisle_id: enum(current aisle ids + "unsorted"), icon: enum(catalog keys), icon_fit: enum("exact","approximate")}], message}`. **Art pass:** each item adds `skip_reason`, `named` and `pantry_entry`, and the answer adds `lines` and `recipe` (above).
- **Input (cache-friendly order):**
  1. A developer message with the stable RULES (`prompts/ingest_rules.txt`), ending in `prompt_cache_breakpoint` (only on models that take it).
  2. A developer message with the aisles in walking order (`id: name`), the learned corrections (`name -> aisle id`), the names already on the list and (art pass) the pantry line.
  3. The user message.
- **Text input:** `tools: [{type: "web_search", search_context_size, user_location}]`, `tool_choice: "auto"` and `include: ["web_search_call.action.sources"]` (from `ModelDefaults.WEB_SEARCH`; always on).
- **Image input:** `[input_text "Photo of a shopping list or a recipe. Add what to buy.", input_image data:image/jpeg;base64,…, detail "high"]`, with **no tools**. The app sends a JPEG with long edge ≤ 1600 px at q≈85.
- **Transcript input:** `"Voice note transcript: <text>"`, with the same tools as text input.
- **Parsing:**
  - `status == "incomplete"` (any `incomplete_details.reason`) → `BadResponse`, even if a partial message exists. A response with **no message item at all** (seen live: only a reasoning item, 0 usage) → `BadResponse`. Never parse partial JSON.
  - Use the **last** `message` output item whose `phase` is not `"commentary"` (a missing phase counts as final). A `refusal` content part → `Refusal`. If its JSON doesn't parse, try earlier final messages. Never concatenate `output_text`.
  - Sources come from `web_search_call.action.sources[].url`, `action.url` (from `open_page`) and `url_citation` annotations, deduplicated. In `message`, markdown links `[t](u)` are reduced to `t`.
- **Post-processing:** trim names; a learned correction overrides `aisle_id`; `"unsorted"` or an unknown id becomes `null`; a learned icon override replaces `icon` with fit EXACT; an invalid icon becomes `DEFAULT_ICON` + APPROXIMATE; dedupe by `normalizeName` against `existingNames` and within the answer (duplicates go to `skippedDuplicates`); art pass: `PantryMatch.apply` with the request's pantry.

**transcribe** makes one `POST /audio/transcriptions` (multipart) call: `model`, `response_format=json`, `languages[]` and `prompt` from `ModelDefaults.TRANSCRIBE`, `keywords[]` (at most 100, each one line with `<` and `>` stripped; from the list's names and the correction names), and `file=recording.m4a`. **The extension selects the decoder**; `.aac` and `.3gp` are rejected. Blank `text` means nothing was heard: return `Transcript("")` rather than an error.

**recategorize** uses `ModelDefaults.FAST` with no tools and the strict schema `{assignments:[{id: enum(i1..iN), aisle_id: enum(aisles + "unsorted")}]}`. Items with a matching correction are resolved locally and not sent. Batches hold at most 40 items. An id missing from the answer maps to `null`.

**assignBaskets** uses `ModelDefaults.FAST` with the strict schema `{baskets:[{aisle_id: enum(ids), basket: enum(16 kinds)}]}`. A missing aisle falls back to the heuristic inside `assignBasketsOrGuess`.

**drawIcon** (cascade step 3; live stickers v4 since the art pass, DESIGN_STICKER §18.3) makes one `POST /images/edits` (multipart) call: `model`, `prompt`, `image[]` = the 1024² neighbour sheet (three library stickers and an empty fourth cell, §8.3), `mask` = the bundled mask that opens only that cell, then `background`, `output_format`, `output_compression`, `quality` (`low`), `size` from `ModelDefaults.IMAGE`, and `n=1`. The prompt is `prompts/icon_prompt.txt` with `{subject}` (the decider's `look`, or "one <name>, as it is sold in a Canadian grocery store"), `{colours}`, `{body}` and `{band}` from the request's two sticker families (`StickerPalette`). It returns the decoded `data[0].b64_json` bytes; everything else happens on the device (§8.3).

**testConnection** calls `GET /models/{ModelDefaults.SMART.id}`. It is free, and it checks the key and access to the model in one go. A restricted key needs only **List models: Read** for it (`api.model.read`), so a passing test doesn't prove the other two permissions (`docs/OPENAI_KEY.md`).

### 7.4 `IconDecider` (cascade step 2, `assistant/IconDecider.kt`)

```kotlin
interface IconDecider { suspend fun decide(items: List<NamedItem>): Map<String, IconDecision> }
data class IconDecision(val icon: String? /* non-generic key or null = "none" */, val category: String?,
    val look: String? = null /* art pass: with "none", what to draw */, val families: List<String> = emptyList() /* body first, ≤ 2 */) {
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
- **Art pass (WP1).** The schema adds `look` (one line: what the live drawing shows; "" for a match) and `body` / `accent` (enums of the ten sticker families), so every "none" carries the drawing's brief. `LiveIconDrawer(assistant, decider)` is the network half of a live sticker: `brief(key, name, known)` asks the decider once more only for a name that reached drawing without a brief (ingest's "approximate", a redraw, a legacy icon), and `draw(request): DrawOutcome` (`Drawn`, `Refused`, `Failed`) makes one `drawIcon` call, retried once after 3 s on RateLimited, Network or Timeout.

### 7.5 Prompts, CLI, tests

- **Prompts.** Prompt texts live in `core/src/main/resources/prompts/` (`ingest_rules.txt`, `recategorize_rules.txt`, `baskets_rules.txt`, `icon_decider_rules.txt`, `icon_prompt.txt`) and are documented in `docs/PROMPTS.md`, which core owns. Start `ingest_rules.txt` from `research/openai/examples/rules.txt`, and fix one line there: it says category icons are `cat_*`, but in the catalog they are `*_generic`. Lessons from the benchmark: examples leak verbatim, so never reuse eval inputs as examples; say "Never mention list items the request did not need".
- **CLI.** `earth.cora.picnic.core.cli.MainKt`, run with `./gradlew -p core run --args="<cmd> …"`. Commands: `ingest "<text>"`, `photo <jpg>`, `transcribe <m4a>`, `recat "<item>"…`, `baskets [aisles.txt]`, `icons "<item>"…` (IconDecider), `draw "<name>" <out.webp>`, `test`. It reads `OPENAI_API_KEY` and optional `PICNIC_BASE_URL` from the inherited environment. Developer-only flags `--smart=<model>` / `--fast=<model>` build a different `ModelSet` for prompt comparisons; the app has no equivalent. **Never** pass the key through `environment(...)` in Gradle: the configuration cache would write it to disk.
- **Tests.** Unit tests run on MockWebServer (`mockwebserver3`, with `http://127.0.0.1` base URLs) and fixtures in `core/src/test/resources/fixtures/`. Take trimmed real responses from `research/openai/out/bench/`. Cover: the duplicate-final-message case, `incomplete` with and without a message, a refusal, each error body, the effort and cache-field downgrades, decider "none" and skipped ids, corrections and icon overrides in post-processing. Live tests are `@Tag("live")` and also `assumeTrue(OPENAI_API_KEY)`; they run only with `-Plive=true`. They can use `research/openai/{handwriting,audio}`; copy only small fixtures into the repo.

---

## 8. Icons on device and the icon cascade (app-shell, `earth.cora.picnic.icons`)

### 8.1 Bundled library

**Art pass (§3.2 and §8.3, `IconImages` → `StickerArt`, replaces; consumers: `ItemIcon`, `IconRepository`, previews; owner WP1).** Library stickers become generated **vector drawables** `res/drawable/sk_i_<key>.xml` with named groups (`cut`, `fills`, `lines`, `gloss`, `dominant`, optional `face`) listed in the generated `StickerManifest`; live stickers are a 208 px bitmap plus a stored cut path (`.cut`), or legacy (pre-v4, no cut). One render path draws both:

```kotlin
interface StickerArt { fun vector(key: String): StickerVector?; fun cached(customIconId: String): LiveSticker?
    suspend fun load(customIconId: String): LiveSticker?; fun isBundled(key: String): Boolean /* from StickerManifest */
    object None : StickerArt }                                     // previews and tests: library vectors only
data class StickerVector(@DrawableRes val id: Int, val groups: Set<String>)
data class LiveSticker(val bitmap: ImageBitmap, val cut: Path? /* bitmap pixel space */, val legacy: Boolean)
class AppStickerArt(customDir: File, maxBytes: Int = 8 MB) : StickerArt { fun evict(customIconId: String) }
```

- **Library:** one generated `sk_i_<key>.xml` per catalog key (236), from the sticker DSL in `design/stickers/` (`build_android.py`; `:app:checkStickers` fails on drift or a lint error). `StickerManifest` lists each key's drawable, its two families, personality, face and source.
- **Live stickers:** `AppStickerArt` reads `filesDir/icons/custom/<id>.webp` and `<id>.cut` on `Dispatchers.IO` into a byte-bounded `LruCache` and remembers misses, so a row whose file is gone never hits the disk on every scroll. `evict(id)` drops one after a redraw deleted its file (the repository's `onIconReplaced`). A v4 sticker whose `.cut` can't be read still draws (its alpha is the silhouette); only its ghost loses the cut.
- **Rendering:** `ItemIcon` reads `StickerArt` through `LocalStickerArt` (§11.1, §11.4). Nothing renders from `assets/icons/` any more: the only asset left there is the live-drawing `mask.png` (`BundledIcons.mask()`).
- History: v1 bundled 192 px `assets/icons/<key>.webp` files behind `IconImages` / `AssetIconImages`; the art pass replaced both.

### 8.2 The cascade (amendment A2)

Every item ends with a catalog icon and an `IconFit`; `APPROXIMATE` items may also get a bespoke icon. Each step falls through on failure or offline; nothing ever blocks adding an item.

| Step | What | Where | On failure / offline |
|---|---|---|---|
| 1. Local | Learned `IconOverride` for the normalized name, else `IconCatalog.match(name)` | core, synchronous: `AppState.localIcon`, used by `OfflineParser` and `editItemDetails`; ingest applies overrides in post-processing | No match → `DEFAULT_ICON` + PENDING |
| 2. Decide | `IconDecider.decide` on PENDING names; ingest's own `icon`/`icon_fit` counts as step 2 for the items it returns | app-shell `IconRepository` (batcher) → core `ResponsesIconDecider` | `LlmError` (or no token yet) → items stay PENDING, showing the fallback; retried by `resumePending()` once per process start and whenever a token is added (§10.1) |
| 3. Draw | a live sticker (v4) for APPROXIMATE items when "Draw new icons with AI" is on | app-shell `IconRepository` (queue, neighbour sheet, post-processing) → core `LiveIconDrawer` → `drawIcon` | Item keeps its APPROXIMATE catalog icon (the closest key, or its category's `*_generic`) |

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
    fun isBundled(key: String): Boolean        // art pass: the key has library art (StickerManifest)
    fun submit(items: List<GroceryItem>)       // after commits and renames: attach cache / decide / draw
    fun resumePending()                        // once per process start, and when a token is added: submit every PENDING item;
                                               // art pass: also sweeps orphaned files and queues legacy redraws (§8.4)
    fun redraw(itemName: String)               // edit sheet "Draw a new icon" (forced; overwrites)
    fun forgetBespoke(itemName: String)        // the user picked a catalog icon for this name
}
class NoOpIconRepository(busyNames: Set<String> = emptySet()) : IconRepository   // previews/tests
```

Production implementation (app-shell): `AppIconRepository(app, store, settings, assistant, iconDecider, catalog, appScope, clock, bundled = BundledIcons(app), album: AlbumStore?, onIconReplaced: (String) -> Unit)`; the container passes its album (filed stickers keep their files) and `AppStickerArt::evict`.

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
    LlmError → busy -= keys (items stay PENDING; resumePending() retries next start, when a token is added, or at the catch-up)
Offline (online() false, NetworkStatus; 5 October): no decide call is queued (PENDING items wait), no drawing is queued and no
    daily slot is used: an APPROXIMATE name joins index.drawLater; resumePending() (cold start, refresh, the catch-up)
    submits every waiting name an item still wants (APPROXIMATE, no custom icon) and drops the others
Draw workers (2 coroutines on a Channel = max 2 concurrent), live stickers v4 (art pass, DESIGN_STICKER §18.3 step 5):
    brief = the decider's "none" (look + two families), else LiveIconDrawer.brief asks once more for this name
    sheet = SheetComposer: the approximate key and the two closest same-category library stickers, art only, on a
            1024² 2 × 2 sheet, with the bundled mask.png opening the empty 4th cell (no mask → drawing is off)
    LiveIconDrawer.draw → assistant.drawIcon(IconDrawRequest(name, sheet, mask, subject, families))
      RateLimited / Network / Timeout → wait 3 s, retry ONCE (imageEdit itself never retries: at most 2 requests per drawing)
      Refusal (moderation) → blocked += key (never auto-draw this name again)
      Offline / Network / Timeout after the retry → an item's drawing joins drawLater; the day's slot is refunded only
      when no attempt reached OpenAI (DrawOutcome.Failed.reached false: Offline, or Network before a connection), since
      a connected attempt may have been billed
      other (quota, bad answer) → give up (the item keeps its APPROXIMATE catalog icon; the slot stays used)
      a forced redraw ("Draw a new icon") that fails → IconRepository.drawFailures (DrawFailure: offline, refused or other),
      which the Edit sheet shows
    LiveStickerProcessor (IconPostProcessorV4, < 100 ms on Dispatchers.Default): cut the 4th cell, snap every pixel to
      the two families + white + lemon pastel, remove specks, size and base on the library grid, 4u white die-cut,
      cut path, procedural gloss, 2u trace
    → 208 px lossless WebP + <id>.cut → filesDir/icons/custom/ (tmp + rename) → index.byName[key] = id
    → store.update{attachCustomIcon(key, id, overwrite = redraw)} (a legacy redraw replaces the old id everywhere)
    → busy -= key; the replaced file is deleted only when no list, pantry entry or album.json references it,
      and onIconReplaced(old id) drops it from StickerArt's memory
```

Rows read `busy` and show `IconCatalog.displayRef(item, busy = normalizeName(item.name) in busy)` with `drawing = busy` (sparkle).

### 8.4 Bespoke cache details

- **Icon id:** `customIconId = "c-" + sha1(key).take(10) + "-" + base36(epochSeconds)`. A redraw gets a new id, so a stale bitmap is never shown from cache.
- **Index file:** `index.json` = `{"version":1,"byName":{…},"blocked":[…],"day":"2026-10-01","drawnToday":3}`. `forgetBespoke(name)` removes `byName[key]` (the file stays while items reference it; orphaned files are swept at start).
- **Art pass (live icons v4, replaces; consumers: AppIconRepository, LiveIconDrawer, BespokeIconCache).** Output is 208 px plus a `.cut` file. A redraw, and the start-up sweep (files older than 10 min that nothing points at), delete a replaced file only when **no list, pantry entry or `album.json`** references it (`AlbumStore.referencedCustomIconIds()`): filed books keep their stickers; if the album can't be read, nothing is deleted. **Legacy icons** (drawn before v4: 192 px, no `.cut`) render as they are and are redrawn in v4 in the background from `resumePending()`, oldest first, under the daily cap, only with a key set and drawing on.
- **Daily cap:** 40 automatic drawings per day protects against runaway cost. A redraw ignores the cap.
- **Settings toggle:** "Draw new icons with AI", on by default, with the cost note "Drawing a new icon takes about 10 s and costs about 1.5¢ (US)." It gates step 3 only; step 2 (≈ US$0.0002 per batch) always runs when a token is set.
- **Offline** (5 October): nothing is drawn or decided automatically while `NetworkStatus` says offline, and no daily slot is used. A name whose automatic drawing waits for a connection (asked offline, or failed on the way: Offline, Network or Timeout; the slot is refunded only when no attempt reached OpenAI) is kept in `index.json`'s `drawLater` (at most 200 names, oldest dropped first) and submitted again by `resumePending()` at the cold start, a refresh and the catch-up (§10.5), under the cap; it leaves the set once drawn, refused, given up on (quota, bad answer) or no longer wanted. Meanwhile the item shows its catalog icon, and "Draw a new icon" in the Edit sheet rests offline.

### 8.5 Display rule (core `IconCatalog.displayRef(item, busy)`, implemented)

1. A bespoke icon attached → `IconRef(item.icon, customIconId)`.
2. `busy`, `iconFit == PENDING`, or the `DEFAULT_ICON` kept because nothing matched (fit not EXACT; `IconCatalog.showsCapsule(icon, fit)`) → **art pass (D5):** the capsule sentinel `IconRef(IconCatalog.CAPSULE)`, tinted by its aisle (v1: the category fallback), and the row overlays the "drawing…" sparkle while busy.
3. Otherwise → `IconRef(item.icon)`: an item matched EXACT to `paper_bag` ("grocery bags") shows the kraft "Shopping bag" sticker.

The same `showsCapsule` rule picks the sticker a twin is filed with (`GroceryItem.bookIcon()`: filed trip books, the album's book thumbnails, the finale's stops and basket riders), the add flows' preview rows, and whether Change sticker opens on a category (an item still showing the capsule has none yet, and no cell wears the selection ring).

`ItemIcon` then draws (art pass): the live sticker from `StickerArt` for a `customIconId` (the library art while it loads, or if its file is gone) → the key's library sticker (`paper_bag` included) → its category's generic → the capsule (an unknown key, whose fallback is `DEFAULT_ICON`, draws the capsule, not the bag). v1 rendered through `IconImages.load` and placeholder art.

### 8.6 Decisions API seam (TODO, not implemented)

OpenAI's new Decisions API (`POST /v1/decisions`) is the intended future implementation of step 2. Today it is a limited preview with no public schema, and for the project key it answers **HTTP 403** `{"error":{"message":"Decision API is not enabled for this user.","type":"invalid_request_error","param":null,"code":null}}` (checked 2026-10-01). **Do not write a client against a guessed schema.**

The seam is ready:

- `IconDecider` (core) is the only interface the app uses for step 2; `AppContainer.iconDecider` is the only construction site.
- `ResponsesIconDecider` stays the implementation until the checklist below is done.

Switch-over checklist (owner: core):

1. Confirm access (the 403 above goes away) and get the published request/response schema.
2. Add `OpenAiClient.decisions(body)` (`POST {base}/decisions`) and `DecisionsIconDecider(client, catalog)` in `core/…/assistant/`, mapping to `IconDecision` (icon or none, plus category).
3. MockWebServer tests from recorded real responses; a live comparison against `ResponsesIconDecider` on a fixed name set (accuracy, latency, cost), noted in `docs/PROMPTS.md`.
4. Add the Decisions permission to the restricted-key list in `docs/OPENAI_KEY.md`.
5. Construct it in `AppContainer.iconDecider`, wrapped so a 403/404 from a key without access falls back to `ResponsesIconDecider`.

---

## 9. Settings and the key security model (app-shell, `earth.cora.picnic.settings`)

Amendment A1: the only settings are **"Your OpenAI key"** (paste, Save, Remove key, "Test connection") and **"Draw new icons with AI"**. There is no server-address, model, effort, web-search, theme or motion setting: requests always go to `LlmConfig.DEFAULT_BASE_URL` (`https://api.openai.com/v1`), theme follows the system dark mode and motion follows the system "remove animations" setting. Internally the key keeps its older name, "token" (`setToken`, `hasToken`, `tokenHint`, `TokenStore`, core's `TokenSource`).

```kotlin
data class AppSettings(val drawIcons: Boolean = true, val hasToken: Boolean = false, val tokenHint: String? = null)
interface SettingsRepository {
    val settings: Flow<AppSettings>; suspend fun current(): AppSettings
    suspend fun setDrawIcons(enabled: Boolean)
    suspend fun setToken(token: String?); suspend fun token(): String?
    suspend fun llmConfig(): LlmConfig             // DEFAULT_BASE_URL + safetyIdentifier, for core's ConfigSource
}
interface TokenStore { suspend fun read(): String?; suspend fun write(token: String?) }
fun maskToken(token: String): String              // "sk-…a1b2"
```

- **`DataStoreSettingsRepository`** (production, `androidx.datastore:datastore-preferences` 1.2.1): keys `draw_icons` (absent = true), `token_hint`, `install_id` (a random UUID created on first run). `llmConfig()` = the default base URL plus `safetyIdentifier` = the first 32 hex characters of `sha256(install_id)`.
- **Upgrade from builds with an address setting.** Older builds could store a `base_url`. Before the first read of the settings, the key or `setToken`, a one-time step removes it. If its host (host and port) isn't `api.openai.com`, it first deletes the stored key and its hint, so a key entered for another server is never sent to OpenAI; a key entered for OpenAI's own host is kept. If the key can't be deleted or the settings can't be read, `token()` returns null for that call and the step runs again next time. Tests: `DataStoreSettingsRepositoryTest`.
- **`InMemorySettingsRepository`** is for tests and previews (implemented).

**Key security model:**

- **Never in the repo or APK.** The user pastes an OpenAI API key: their own, or one Cora made for them (a dedicated project with a hard monthly budget and a restricted key, `docs/OPENAI_KEY.md`).
- **Encryption.** The key is encrypted with an AndroidKeyStore AES-256-GCM key (`platform/TokenCipher`). The Keystore picks the IV, and the stored form is `iv:ct`. The blob lives in `noBackupFilesDir/token.bin`, outside the backup allow-list; a restored blob can't be decrypted on another device anyway, because Keystore keys aren't backed up.
- **Unreadable token.** Only a permanent failure (`KeyPermanentlyInvalidatedException`, `AEADBadTagException`, a malformed blob) deletes `token.bin` and clears `token_hint` (`hasToken = false`, ask again). Any other Keystore failure (`KeyStoreException`, `UnrecoverableEntryException`, `ProviderException`: a busy or restarting keystore2, early boot) is `KeystoreUnavailableException` (an `IOException`): `TokenStore.read()` throws it and keeps the blob, and `token()` returns null for that one call (core reports `NotConfigured`) without touching the hint. `SecretCipher.decrypt` returns null only for the permanent cases.
- **What it protects against.** Other apps, backups and device transfers, and USB debugging of the sideload APK (not debuggable). Not against someone with the unlocked phone, who can use the AI features freely: the project's hard spend limit is the real cap (`docs/OPENAI_KEY.md`).
- **Failed writes.** Every settings write runs through one wrapper that turns a DataStore `IOException` into `notice = "Couldn't save. Try again."` instead of a crash. A key the Keystore refused (nothing stored) gets the same notice and stays in the field.
- **Where it can appear.** It is decrypted only inside `token()`, which `TokenSource` calls per request. It is never put in a data class, a `StateFlow`, a log line or an exception message. The Settings field is write-only: it shows `tokenHint` and is never pre-filled. `LlmError.Unauthorized` discards the server text, which echoes a masked key. There is no `HttpLoggingInterceptor` in any build type.
- **Tests.** The Keystore doesn't exist under Robolectric, so tests use `InMemoryTokenStore`.
- **Network.** Cleartext is blocked by default at targetSdk ≥ 28, the only host is api.openai.com, and `normalizeBaseUrl` refuses `http://` except loopback. **Art pass (one opt-in exception; consumers: PRIVACY.md, the Data safety form):** with **Weather surprises** on, the app also fetches Environment Canada's city forecast feed (`weather.gc.ca`, at most every 3 h, no identifier; DESIGN_STICKER §12.4). With **Use my area** on (A6), the only thing that leaves the device about location is the nearest forecast city in that same request (its feed is named by the city's own coordinates, `weather.gc.ca/rss/weather/<lat>_<lon>_e.xml`); the rounded point stays on the phone.

**Art pass (A4).** `AppSettings` gains the Surprises switches: `eggs = true`, `sounds = false`, `haptics = true`, `nextStopCard = true`, `celebration = true`, `weather = false`, `useMyArea = false` (A6), and the egg state `duskBinder = false`, `surpriseLab = false`. WP8 adds their DataStore keys (`eggs`, `sounds`, `haptics`, `next_stop_card`, `celebration`, `weather`, `use_my_area`, `dusk_binder`, `surprise_lab`, plus the weather cache keys) and setters. Egg 17's last parade day is egg state too: `ants_day` (her local date), read and written through `SettingsRepository.antsDay()` / `markAntsDay(day)`, so the parade marches at most once a day across processes (DESIGN_STICKER §0.4). The one-time move notes are device-local too (5 October): `moveNoteShown` / `move_note_shown` (the first move's fuller note) and `holdHintDone` / `hold_hint_done` (the hold hint), set through `markMoveNoteShown()` / `markHoldHintDone()`. Reduced motion still follows the system.

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
  | `icons`, `stickerArt` | `AppIconRepository` (with the album and `onIconReplaced = AppStickerArt::evict`), `AppStickerArt(filesDir/icons/custom)`; art pass: `stickerArt` replaced `iconImages` / `AssetIconImages` |
  | `photos` | `PhotoPreparer` |
  | **5 October:** `network` | `AndroidNetworkStatus.create(app)` (`NetworkStatus`, §10.5): `online` and `arrivals`; read by `OpenAiClient` (`isOnline`), `AppIconRepository`, the add flows, the list's refresh, Settings and `catchUp` |
  | **5 October:** `catchUp` | `CatchUp` (§10.5): resumes `icons`, runs `aisleSync.sortPending()`, `PantryIcons.decidePending` and `surprises.onBackOnline()`; its note reaches `ListViewModel` (`CatchUpNotes`) |
  | **5 October:** `keptCaptures` | `FileCaptureKeeper(noBackupFilesDir/kept)` (`CaptureKeeper`): photos and voice notes whose request failed, for `AddViewModel` |
  | `newVoiceRecorder()` | |
  | `ready: StateFlow<Boolean>` | True once `startOnce()` has parsed the icon catalog (≈110 KB JSON) off the main thread |
  | **Art pass:** `album` | `JsonFileAlbumStore(filesDir/album.json)` (WP3), kept in step with the state by `TripFiling.launchAlbumSync` (`startOnce`) |
  | **Art pass:** `feedback`, `announcer`, `timeouts` | WP10's services: `AndroidFeedback.create(app, settings.settings, appScope)` (sounds and haptics, each gated by its switch; built in `startOnce` before the first resume so its window tracker sees the activity), `AndroidAnnouncer` (polite, de-duplicated within 1 s), `AndroidTimeouts` ("Time to take action", "Touch & hold delay"). Tests use `FakeFeedback`, `FakeAnnouncer`, `FakeTimeouts`; previews the no-ops (`NoOpFeedback`, `NoOpAnnouncer`, `DefaultTimeouts`). |
  | **Art pass:** `places`, `surprises`, `themeVariants` | `PlaceSource` (Toronto until "Use my area" lands), `SurpriseEngine` (phase 0: `NoOpSurpriseEngine`), `ThemeVariantSource(settings, clock, SunClock, places)` |
  | `startOnce()` | Once per process (an `AtomicBoolean`; MainActivity calls it on every `onCreate`), in `appScope`: build `feedback` → warm the catalog → `ready = true` → `TripFiling.launchAlbumSync(appScope, store, album, catalog, clock)` (art pass) → `catchUp.start()` (5 October: `icons.resumePending()` always, then, online with a key, the rest of the catch-up; then one after each arrival of a connection) → `Captures.sweepStale` → `keptCaptures.sweep()` (kept captures older than 7 days). It also starts the token watcher: each "no token → token" change (`onTokenAdded`) runs `icons.resumePending()` and `aisleSync.sortItems(...)` for TYPED items still needing an aisle, so items added before a token existed catch up without a cold start. |
- **ViewModels take their dependencies individually**, never the container. Their factories read the container:

  ```kotlin
  val Factory = viewModelFactory { initializer {
      val c = (this[APPLICATION_KEY] as PicnicApp).container
      ListViewModel(c.store, c.album, c.icons, c.settings, c.catalog, c.clock, c::newId, c.appScope) } }
  ```

  Tests then pass `InMemoryStateStore`, `InMemoryAlbumStore`, `InMemorySettingsRepository`, `NoOpIconRepository` and a fake `GroceryAssistant`.

### 10.2 Activity and navigation

`MainActivity` is owned by app-shell:

- Call `installSplashScreen()`, then `super.onCreate`, then `enableEdgeToEdge()` with bar styles that follow the system theme, then `container.startOnce()` (unconditionally: a process killed in the background restarts with a `savedInstanceState`, and must still resume the cascade), then `splash.setKeepOnScreenCondition { !container.ready.value }`, then `setContent`. Inside, nothing below `PicnicTheme` composes until `ready` is true, so no ViewModel factory parses the catalog on the main thread. Activity results (a camera capture after process death) wait in the `ActivityResultRegistry` until the launchers register.
- Inside `setContent` (v1): `PicnicTheme(darkTheme = isSystemInDarkTheme(), reducedMotion = <system animator duration scale == 0>) { CompositionLocalProvider(LocalIconImages provides container.iconImages) { PicnicAppRoot(container) } }`.
- **Art pass (D12, owner WP4):** `PicnicTheme(variant = container.themeVariants.variant, reducedMotion) { CompositionLocalProvider(LocalStickerArt provides container.stickerArt, LocalTimeouts provides container.timeouts) { PicnicAppRoot(container) } }`, where reduced motion is on when the animator **or** the transition scale is 0 (read from `Settings.Global` at start and on every resume), and the status and navigation bar icon styles follow the resolved variant (dark icons on Day, Dusk and Golden; the celebration always composes in Day).
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
class ListViewModel(store: StateStore, album: AlbumStore /* art pass */, icons: IconRepository, settings: SettingsRepository,
                    catalog: IconCatalog, clock: () -> Long, newId: () -> String, appScope: CoroutineScope) : ViewModel() {
    val state: StateFlow<ListUiState>; val events: Flow<ListEvent>
    fun toggle(itemId); fun delete(itemId); fun undoDelete(item: GroceryItem, index: Int)
    fun toggleSection(sectionId); fun clearChecked(); fun newList(title); fun switchList(id)
    fun renameList(id, title); fun deleteList(id)  // art pass: TripFiling.deleteList, the book is filed first
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
    val cleared: Boolean, val count: Int /* listed: still to get; CHECKED: its items */,
    val gotten: Int /* AisleSection.checkedCount; 0 for CHECKED */,
    val inBasket: List<IconRef> /* displayRefs of the first 5 GOTTEN items (checkedItems; CHECKED: its items), never items to get */,
    val items: List<ItemRowUi>)
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

// ui/settings (amendment A1: the OpenAI key + draw-icons only)
data class SettingsUiState(val tokenInput: String = "", val hasToken: Boolean, val tokenHint: String?, val drawIcons: Boolean,
    val test: ConnectionTestUi = ConnectionTestUi.Idle, val tokenVisible: Boolean = false,
    val canSave: Boolean = false, val loading: Boolean = true, val notice: String? = null /* failed write */)
sealed interface ConnectionTestUi { Idle; Running; Ok(latencyMs: Long); Failed(message: String) }
class SettingsViewModel(settings: SettingsRepository, assistant: GroceryAssistant, places: PlaceSource, listTitle: Flow<String>)
    // setTokenInput, toggleTokenVisible(), saveConnection() (save, then test), clearToken(), setDrawIcons(on), testConnection()
```

**Art pass, part 2 (WP7; replaces the `ui/add` shapes above; consumers: WP5's List screen, WP9's commit hooks; DESIGN_STICKER §6.2, §19.2).** The add flows are one holder, `rememberAddFlows(vm): AddFlows` (the ViewModel, the platform launchers, the bar's fold and focus, `dockHeight`), placed by the List screen as the dock, the toasts (`AddEventsEffect`) and the sheet. `AddPhase.Failed(problem: AddProblem, message?, retryable, canUsePlainLines, needsSettings)` replaces `Failed(message, …)`: `AddProblem` (`AI`, `PHOTO_UNREADABLE`, `CAMERA_UNAVAILABLE`, `MIC_UNAVAILABLE`, `MIC_DENIED`, `TOO_SHORT`, `NOTHING_HEARD`, `SAVE_FAILED`) names the app's own copy, and an AI error carries core's friendly `message`. `AddEvent` is redesigned as the list's one-off events: `Committed(AddCommit)` (`added`, `alreadyThere`, `revived`, `aisleTitle`, `asTypedNoKey`; `canUndo`, `popDelayMillis(i)` for the 80 ms pops), `Undone(commitId)`, `SaveFailed`, `SortFailed(friendlyMessage, leftUnsorted)`, `FocusField`. Snap and Speak need the key before anything is captured: `AddViewModel.requireKey(mode, capture)` opens the sheet on that mode with "Add your OpenAI key in Settings first." and never runs `capture` without one (`needsKey()` says which), and `AddFlows.takePhoto()` / `pickPhoto()` / `recordVoice()` go through it. `AddActions.onOpenAppSettings` is "Open phone settings" after the microphone was refused for good.

**Art pass, part 3 (integration of WP5 and WP9; DESIGN_STICKER §12, §19.2).** The List screen sits in WP9's `EggListHost` (`ListScreen(…, surprises, feedback, imeVisible)`; `ListRoute` hands it `ListViewModel.surpriseEngine`): the host calls `onListShown` / `onListHidden`, runs the shake detector and the keyboard observer, scrolls each egg's scene into the list's room, and its `overlays` ride in MotionOverlay's `eggs` slot, above the bar's measured top (`dock(bar)` puts `addbar`, the bar's `text:` keep-out and `eggAddBar()` on the docked bar). The list's pieces read the eggs' state through `LocalListEggs` (the LCD's words and pet, the holo title, the sun, the pixel and gold stickers, the twinkle, HAPPY / B-DAY!, the basket's hello, the nap cat in its basket, the snack doodle beside the frozen tab, the ladybug along a strip, the roll home under a row's words, the page stickers' budget) and register the anchors the overlays look for (`list`, `strip:`, `tab:`, `line:`, and `text:` on every word). The add flows tell the engine about a commit once (`AddViewModel`); the list only pops its rows. While it rains the list's viewport ends above the umbrella's band, so no row lies under the canopy. The default `StaticDock` (previews, goldens, the lab) wears the eggs' tape when they run, the rain's included. The letter-set sheet's scroll viewport starts 16 dp under its perforated top, so scrolled content passes under the paper's edge and the washi handle instead of over them (§4.16; at rest nothing moves).

**Art pass, part 2 (WP6; consumers: WP5, WP9; DESIGN_STICKER §8, §19.2).** `Choreographer` adds `keepCelebration()` (§0.4 Q7: a tap on the summary keeps it open) and `onUserOpen(sectionId?)` (her own tap on a strip, or `null` for the open tray's chevron, wins at once over an aisle-done chain; WP5 calls it from those handlers only). `ToggleEvent.newBook` marks the check-off that started a new sticker book on its own (A6: the book turns to the fresh page); its Undo is a `ListChange` with cause `NEW_BOOK_UNDO`, empty aisle sets and `slot` (the spot the item takes again), shown with `SpotPhase.Appearing(t0, inPlace = true)` (a 120 ms fade, no bloom). `FoldToggles` counts her own fold and unfold taps per section and `FoldFocusEffect` moves focus after her fold (item 193).

- **Settings screen content:** a "Your OpenAI key" card (a short intro, a write-only key field with Show/Hide that shows `tokenHint` once saved, Save, Remove key, the note "Stored encrypted on this phone (Android Keystore) and sent only to OpenAI.", and Test connection with the result inline), an "Icons" card ("Draw new icons with AI" switch plus the cost note), and an "About your key" card (hard monthly budget, restricted key with its three permissions, a line saying whoever made the key has a step-by-step guide (that guide is `docs/OPENAI_KEY.md`; the repository is private, so the app names no path or link), and what is sent). The app version is not on Settings: it shows on About & licences (DESIGN_STICKER §6.9, `AboutUiState.appVersion`). Nothing about models or server addresses.
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
| **Snap** (camera / picker) | `TakePicture` into a FileProvider Uri (`Captures.newCaptureUri`, pending Uri kept in `rememberSaveable`) or `PickVisualMedia(ImageOnly)` → `onPhoto(uri)` → `photos.jpegBytes(uri)` (IO) → `Thinking` → `ingest(Image(bytes), ctx)` → Preview → commit. Delete the capture file afterwards. | PHOTO | `Failed(retryable)`; no plain-lines option (nothing to parse). The JPEG is kept (`CaptureKeeper`) until sent, replaced or deleted (§10.5) |
| **Speak** | Request `RECORD_AUDIO` at runtime → `AudioRecorder.start()` (`VoiceRecorder`: m4a, mono 16 kHz 32 kbps, max 5 min). Poll `level()` about 10×/s for the waveform. `stop()` (also on `ON_STOP`) → bytes → `Transcribing` → `assistant.transcribe(AudioClip(bytes), keywords)` → show the transcript (editable) → `Thinking` → `ingest(Transcript(text), ctx)` → Preview → commit | VOICE | Empty transcript → "Didn't catch that". Transcribe failure → retry with the bytes, kept (`CaptureKeeper`, §10.5). Ingest failure → plain lines from the transcript; the recording and its words are kept for Try again. |

**Preview layout.** Proposed items are grouped by aisle in section containers (`AisleSectionTitle` + `AisleSectionRow`s). The commit row (**Add N items**, **Start over**) is a footer pinned under the scrolling preview, behind a dashed edge, so the main action never needs a scroll. A skipped row fades and leaves its box empty; it is never struck through (§11.4).

**Commit** (shared by every path), in `appScope`:

1. `items = proposed.filter { keep }.map { it.toGroceryItem(newId(), clock(), source) }`.
2. `val after = store.update { it.addItems(listId, items) }`; `added = items.filter { after.findItem(it.id) != null }`.
3. `icons.submit(added)` (icon cascade steps 2–3, §8.3).
4. If any added TYPED item has `aisleId == null`: `aisleSync.sortItems(thoseIds)` (fast model places them; stays Unsorted offline).
5. Close the sheet and show a snackbar with the model's `message` (or "Added N items").

`NotConfigured` gives `Failed(needsSettings = true)`, which offers **Open Settings**.

**Offline** (5 October, DESIGN_STICKER §6.2 rule 2b): `AddViewModel(online = { network.online.value })`. The bar's Send with a key and no network commits the OfflineParser rows at once (`AddCommit.asTypedOffline`: "Added as typed · sorting waits until you’re back online"); so does a bar Send whose ingest fails `LlmError.unreached`. The sheet's Write Send, Snap and Speak check `online` before each request and fail with `LlmError.Offline` (`Failed(offline = true)`: Write keeps its "As typed" footer). A typed commit made offline skips `sortItems` (the catch-up sorts it) and says so in its one note; a background `sortItems` that never reached OpenAI sends `AddEvent.SortWaits`, not `SortFailed`. Either one while `online` is still true calls `requestCatchUp` (`CatchUp.request`, §10.5). An as-typed commit keeps its text in its `UndoRecord` (`draft`), and Undo puts it back in the field when the field is empty.

**Aisle edit:**

1. Save runs `store.update { setStoreName(name).setAislesText(text, baskets::guess) }`. The heuristic baskets show immediately, and items in vanished aisles render as Unsorted.
2. `appScope.launch { aisleSync.sync() }` runs `assignBasketsOrGuess` → `setAisleBaskets`, then `itemsNeedingAisle` → `recategorize` → `applyAisleAssignments`.
3. The returned `AisleSyncReport` drives a snackbar, e.g. "Sorted 6 items · 2 left in Unsorted" or the error's friendly message.

### 10.5 Offline and the catch-up (5 October)

DESIGN_STICKER §0.4 (Cora, 5 October). Four pieces, all app-side except core's `LlmError.Offline` and `AisleSync.sortPending`:

- **`NetworkStatus`** (`platform/NetworkStatus.kt`, needs `ACCESS_NETWORK_STATE`, a normal permission). `AndroidNetworkStatus` registers ConnectivityManager's default-network callback once per process and starts from the active network's capabilities. `online` is false only when there is **no** default network that offers `NET_CAPABILITY_INTERNET` (airplane mode, no signal); a network that exists but is not `VALIDATED` (a captive portal, or validation blocked) stays online and is tried, since validation is unreliable on some networks. `arrivals` counts each time a usable connection arrives: offline → online, another default network, or the same one newly validated (never the first report at registration). If the callback cannot be registered the app assumes online. Tests use `FakeNetworkStatus`; previews `NetworkStatus.AlwaysOnline`.
- **The Offline error** (§7.2): `OpenAiClient(isOnline = { network.online.value })` fails every call at once offline, and a call that never connected is `Network(beforeConnect = true)`, never `Timeout`.
- **`CatchUp`** (`platform/CatchUp.kt`): `start()` runs once at the cold start (`icons.resumePending()` always; online with a key also the sorting, the pantry stickers) and then after every arrival, debounced 3 s. Each run, online only: `surprises.onBackOnline()` (the forecast if due or failed; it needs no key), then with a key `icons.resumePending()`, `aisleSync.sortPending()` (every list's `itemsNeedingAisle()`, one recategorize call, manual placements and corrections exactly as `sortItems`; names the model already answered in this process are not asked again until the aisles change) and `PantryIcons.decidePending`. Runs take turns on one `Mutex` (never two at once), arrivals during a run fold into one more run after it (`conflate`), and runs after arrivals are at least 60 s apart. **Retries:** a phone can look online and still not reach OpenAI, and then no arrival comes; so `request()` (from `AddViewModel` when an unreached commit or `SortWaits` happens while `online`) and any run whose `sortPending` fails with a `transport` error schedule retries 1, 5 and 15 min later (`RETRY_BACKOFF_MS`), single-flight, through the same lock and 60 s gap, without the forecast; they stop as soon as a sort gets an answer (unless a `request()` came in while it sorted), when offline (the arrival takes over) or without a key, and after the third until the next arrival, cold start or `request()`. `sortPending` counts items as answered only once their answer is saved (a failed save leaves them eligible). When it sorted something, `note` holds a `CatchUpNote` until the list shows it as the refresh's note (`ListEvent.Refreshed`) and calls `shown`. `ListViewModel.refresh()` offline with a key asks nothing and sends `ListEvent.RefreshWaits` ("Sorting and new stickers wait until you’re back online"); `ListUiState.online` tells the screen, whose `refresh` then leaves the LCD alone (no SYNC).
- **The draw-later queue** (§8.3, §8.4): `BespokeIconCache.drawLater` in `index.json`, filled offline and by drawings that fail on the way (their slot refunded only when nothing reached OpenAI), drained by `resumePending()` under the daily cap.

**Kept captures** (`platform/KeptCaptures.kt`): `CaptureKeeper` keeps one photo (`photo-<keptAt>.jpg`, the prepared JPEG) and one voice note (`voice-<keptAt>.m4a`) in `noBackupFilesDir/kept` (never backed up), written atomically when a request fails with "Try again" offered. `AddViewModel` keeps the file names (and a voice note's words, when it was written down) in its `SavedStateHandle` with the draft, and falls back to `latest()` on a fresh start, so a kept capture survives closing the sheet, process death and a cold start. Opening Snap or Speak again (the bar's camera or cassette via `AddFlows.snapFromBar` / `speakFromBar`, the sheet's tab) shows it (`AddUiState.kept`) with its failure, or `AddProblem.KEPT_PHOTO` / `KEPT_VOICE` after a restart, Try again and "Delete photo" / "Delete voice note" (`discardKept`). It is deleted once its request succeeds, a new photo or recording replaces it, plain lines from it are stuck on the list, or she deletes it; `startOnce` and each `restoreKept` sweep any older than 7 days, and `read`, `latest` and `isKept` ignore an older or missing file, so a restored pointer to one is let go and `showKept` turns an expired one down at once. When the bytes of a kept capture can't be read after all, the bar's `snapFromBar` / `speakFromBar` pass the camera or recorder as `showKept`'s fallback, so the tap still opens them. Writes and deletes go through one ordered queue, so a delete asked during a write still ends deleted.

**Other screens.** Settings (`SettingsViewModel(network = …)`): a key test offline makes no request and shows `ConnectionTestUi.Unchecked(offline = true)`; a `transport` failure while online is `Unchecked(offline = false)`; both keep "Saved." with no error styling and a neutral line, and an unchecked key is tested again by itself when a connection arrives while Settings is open. The Edit sheet (`EditItemUi.offline`, `drawFailed`): "Draw a new sticker" rests offline with its reason; a failed redraw (`IconRepository.drawFailures`) is said in the sheet until she tries again or the sheet closes. Weather (`WeatherService`): a failed ask keeps the old forecast and is retried after 15 min (`weather_failed_at`), or at once by the catch-up (`check(retryFailed = true)`).

---

## 11. Theme layer contract (app-shell provides, app-screens consumes, art pass replaces)

**Rule for screens:** use only `PicnicTheme.*` tokens, the theme components, the decor slots, `ItemIcon`/`AisleBasket`/`AisleHeader`/`AisleSectionTitle`/`AisleSectionRow`/`ItemRowCard` and `Glyph`.

- Never use raw `Color(…)`, `MaterialTheme.colorScheme` or `material-icons`. The project does not depend on material-icons.
- Never hand-roll a card or button style.
- Material3 *building blocks* are fine (`Scaffold`, `ModalBottomSheet`, `SwipeToDismissBox`, `SnackbarHost`, `DropdownMenu`, `LazyColumn`). `PicnicTheme` maps a full `MaterialTheme` from the tokens, so none of them shows Material defaults.

The art pass then mostly edits `ui/theme/**` and assets, not screens.

**Art pass (§11 theme contract; consumers: every screen package, WP6, WP9).** The C theme adds **variants** and the C components (DESIGN_STICKER §2, §4, §19.2):

```kotlin
enum class ThemeVariant { Day, Glow, Dusk, Golden }
@Composable fun PicnicTheme(variant: ThemeVariant, reducedMotion: Boolean = LocalReducedMotion.current, content)   // v1 darkTheme form kept for now
val LocalReducedMotion: ProvidableCompositionLocal<Boolean>; val LocalThemeVariant: ProvidableCompositionLocal<ThemeVariant>
@Composable fun CheckMark(checked: Boolean, seed: String, stampText: HankoText = HankoText.GotIt, modifier = Modifier)
@Composable fun ItemIcon(icon, contentDescription, modifier, size, aisleIndex = -1, ghost = false, holo = false, pixel = false,
    flags: Set<StickerFlag> = emptySet(), drawing = false, lifted = false,
    jackpot = false /* gold capsule */, mono = false, face = false /* album, achievements, empty states */, shadow = true,
    glint = true /* part 2 (WP6): false keeps holo foil still on a text plate's hold */)
@Composable fun LcdChip(state: LcdState, modifier, onHold: (() -> Unit)? = null, pitch: Dp = 1.9.dp)
@Composable fun LcdText(text, pitch: Dp, modifier, color = ballpoint, grid: Color? = lcdGrid, moon = false, bold = false,
    starAlpha = 1f /* polish: dims only the ★ / moon cells, the chip's blink; a blink never moves a dot */)
@Composable fun LcdPetSprite(pose: PetPose, modifier)
@Composable fun PuffyText(text: String | AnnotatedString, style, [fill: Color, String only], stroke: Dp, modifier,
    strokeColor = colors.puffyStroke, shadow: DpOffset? = shadows.hs, shadowColor = shadows.color,
    minFontSize = TextUnit.Unspecified, maxLines = 1, textAlign = TextAlign.Start, heading = false, wrapLast = false,
    fillBrush: (() -> Brush)? = null /* egg 7 */)   // polish: an emoji draws its shadow and stroke in tinted layers
@Composable fun Wordmark(text: String, modifier, compact = false)   // only shrinks, then stacks; never ellipsized
@Composable fun PicnicText(text, style, modifier, color, textAlign, maxLines,
    overflow = TextOverflow.Clip, onTextLayout = null)   // part 2 (WP6): inline ♡ ★ vectors; a cut reports through onTextLayout
class ToastHostState { …; var paused: Boolean }          // part 2 (WP6): the celebration covers the toasts; their holds wait
```

**Large text (polish, WP4).** The theme's fixed-size pieces are minimums: `NumberCircle` (24 / 20 dp) grows with its numeral's text box and further, until its ring keeps 0.75 dp from its numeral's ink (measured on the glyphs, drawn once into a small bitmap: a two-digit numeral's corners reach past its box's circle; every numeral 1 to 24 clears it at 1.0, so the drawn size holds there), `ChipFace` (and with it every label, aisle and source chip) grows with its label, the store-aisle and rarity chips scale with their label's text box (face and the store chip's basket glyph alike, keeping their 1.0 proportions), and the pills' 52 / 48 dp faces grow with a taller label, so nothing clips at font scale 2.0; at 1.0 each keeps its drawn size. The store-aisle and rarity chips are never drawn under their 1.0 size, so their words never fall under 13 sp (`Modifier.fitChipToSlot`: above 1.0, in a slot narrower than the chip, the whole chip is drawn smaller to fit, down to its 1.0 size; narrower still, it runs on past the slot uncut, so such a caller gives it a line of its own); `storeAisleChipWidth(number)` is the store chip's laid-out width for a caller that plans round it (the Next card), and the tray header offers its chip only what `indexTabLeastWidth(position, displayName, look, size)` (the narrowest tab that shows the whole name at 16 sp, its number circle as drawn) leaves, never less than its 1.0 size, so the chip gives way before the aisle name is cut; when even its 1.0 size would cut the name (a long name at any scale; "Baking" from about 1.5 on a 360 dp phone), the chip leaves the header (the tab's heading still says "store aisle 2", and the folded strip's meta line shows it). The index tab's number circle is the kit's `NumberCircle` (it grows with its numeral, and the tab's fit counts the grown circle), but it gives way to the name: `NumberCircle(…, maxSize)` caps its growth at what leaves the whole name room at 16 sp (never under 24 dp), and `fitNumeral` then sets the numeral smaller, 0.5 sp at a time, until it keeps 0.75 dp from the ring. The Next card's number circle stays 30 dp at every scale (item 170) and fits its numeral on its ink instead (`fitNumeral`, from min(17 × fontScale, 21) dp), its ring drawn as the kit draws it. A ruled multi-line field's rule pitch is its text's line height: the `textStyle`'s own when it sets one, else `RULED_LINE_PITCH` (24 sp), in px following Compose's non-linear line-height rule (`lineHeightPx`), so rules sit under the lines at any scale. `PuffyText` fills the pinholes and hairline cracks its glyphs' halos leave (DESIGN_STICKER §2.7) and ellipsizes to the longest fitting start without the space before the "…". `Wordmark` never ellipsizes: it shrinks to one line, then stacks "Basket" under "Kirakira" in a box as wide as its longer word. A pill's or a chip's label wraps onto `PILL_LABEL_LINES` / `CHIP_LABEL_LINES` (2) balanced lines (`LineBreak.Heading`), its face growing round them with its one-line caps (`CappedStadium`) and a one-line label's room above and below (on the secondary, the label's ink keeps 3 dp from the inset ring); past 2 lines it ends in "…" and sets `TextOverflowed`, and no screen swaps in a short label for large text. `ChipFace(press, leadingHeight, focused)`: a chip shows its own pressed state (no ripple) and the pills' 2 dp focus ring (in the page's ink: ballpoint in Day, white in Glow); `PicnicChip` and `SourceChip` take an `interactionSource` and `PicnicChip` a `leadingHeight` (how tall its leading content stands). `PinholeWork { Inline, Background }` and `LocalPinholeWork` say where `PuffyText` works out its pinhole patch: the app root (`MainActivity`) provides `Background` (a worker); any other root or harness that leaves it out gets `Inline`, which scans on the main thread.

**Motion (`ui/theme/MotionSpec.kt`).** Every timing lives in `object PicnicMotion` (the §8.2 tokens: `pose`, `sparkle`, `layout`, `standard`, `sheetIn`/`sheetOut`, `toastIn`/`toastOut`, `hop`/`hopPersonality`, `cardIn`/`cardOut`, `glint`, `rmFade`, the reading floors), with `Stepped(n)` easing, `readHold(words, floor)` and `words(text)` (the reading rule, DESIGN_STICKER §9). Reduced motion is decided from **both** Android scales; animations never follow the system scale: chains, overlays, toasts, sheets and the celebration run under `FixedMotionScale` (a `MotionDurationScale` of 1) and measure holds on the frame clock (§8.8 there). The v1 `PicnicTheme.motion` (`ThemeMotion`: `reduced`, `stepFps`, `shortMillis`, `mediumMillis`) stays until WP4 moves its callers. `TripProgress` is removed (its slot becomes the sticker book, WP5) once the v1 list screen stops using it.

### 11.1 Tokens (`ui/theme/Tokens.kt`, `Theme.kt`)

```kotlin
@Composable fun PicnicTheme(darkTheme: Boolean = isSystemInDarkTheme(), reducedMotion: Boolean = false, content)
object PicnicTheme { colors: PicnicColors; type: PicnicTypography; shapes: PicnicShapes; dimens: PicnicDimens; motion: PicnicMotion }
data class PicnicColors(isDark, background, backgroundPattern, surface, surfaceRaised, surfaceSunken, hardShadow,
    outline, ink, inkSoft, inkFaint, primary, primaryShadow, onPrimary, secondary, onSecondary, highlight, onHighlight,
    link, error, onError, strike, scrim, aisles: List<AisleColors>,
    neutral: AisleColors /*Unsorted, In the basket*/, buttonGloss: Color? /*null = no gloss*/) {
    fun aisle(index: Int): AisleColors }  // index<0 = neutral
data class AisleColors(base, shadow, pastel, deep)        // cel base, hard shade, pale fill, deep text
data class PicnicTypography(wordmark, screenTitle, aisleTitle, aisleNumber, itemName /*>=16sp*/, body,
    detail /*>=13sp*/, label, caption, script /*decorative*/, handwriting, counter)
data class PicnicShapes(card, row, chip, button, field, sheet /*top corners*/, badge, medallion,
    section: CornerBasedShape /*aisle section container*/, sectionInner: CornerBasedShape /*its row surface*/)
data class PicnicDimens(gutter=16, xxs..xxl, rowGap, sectionGap=14, sectionInset=8 /*tray wall*/, rule=1.5,
    rowMinHeight=64, touchTarget=48, itemIcon=40, miniIcon=20, basket=64, medallion=44, outline=2, hardShadow=DpOffset(0,3))
data class PicnicMotion(reduced, stepFps = 8, shortMillis = 150, mediumMillis = 280)
val LocalStickerArt: ProvidableCompositionLocal<StickerArt>   // art pass (replaced LocalIconImages); default StickerArt.None
val LocalIconCatalog: ProvidableCompositionLocal<IconCatalog> // default IconCatalog.default
```

- **Palettes.** Two placeholder palettes: light "picnic at noon" and dark "picnic at dusk", chosen by the system dark mode. Both share an 8-colour aisle rotation (strawberry, sky, lemon, leaf, grape, tangerine, bubblegum, cocoa). An aisle's colour is `aisles[(position-1) % 8]`.
- **Aisle identity.** An aisle is identified by colour, pattern, number and name, never by colour alone.
- **Neutral sections.** Unsorted and In the basket use `colors.neutral` (what `aisle(-1)` returns): a low-chroma paper tone, never one of the rotation pastels or `surfaceSunken`, so they can't pass for an aisle. Its pastel stays light in both palettes, like the rotation's, so its tray reads the same way (light tray, row panel inside). `TokensContrastTest` checks it is low-chroma and at least ΔE 8 from every rotation pastel.
- **Paper decides the ink** (DESIGN_STICKER §2.4.1). Content takes its ink set from the paper under it, through `OnPaper` (§11.2): in Glow, dark paper (binder, page, backing) gives Glow's inks and light paper (white, pastels, notes, vinyl, LCD, receipt) Day's, and the shadow follows the same paper (Day's periwinkle on light paper in Glow, the night's on dark), while the variant never changes. Day, Dusk and Golden have no dark paper. Colours are read inside the paper's scope, never from a `PicnicTheme.colors` captured above it.
- **Fonts.** These must be **bundled** TTF/OTF files in `res/font`, subsetted, with their OFL licences shown in-app. Robolectric can't render downloadable fonts. The skeleton uses system families as placeholders.

### 11.2 Components (`ui/theme/Components.kt`)

| Composable | Purpose |
|---|---|
| `PicnicSurface(modifier, shape, color, outlined, shadow, content: BoxScope.()->Unit)` | Every card or row container: fill, ink outline and a **hard offset shadow** (no blur elevation) |
| `OnPaper(paper: Color?, content)` (`ui/theme/Paper.kt`, with `LocalPaperColor`, `paperColors`, `isLightPaper`, `paperFocusInk` and `accentInk`, a family's trace on light paper and mid on dark) | Provides the paper, its focus-ring ink and, in Glow, the ink set and shadows of light or dark paper (`GlowLightColors` and `paperShadows`, Day's periwinkle, on light paper; the soft-ink copy on aisle pastels and mid faces in every variant); never the variant. Floating layers re-root on the binder, then lay their own paper: the letter-set sheet with `OnPaper(null)`, the overflow menu and pop cards with `OnPaper(binder)`, which keeps their navy Glow card's white kiss-cut edge (`PicnicSurface` drops it only on the binder itself or the page). `PicnicSurface` and the letter-set sheet call it; an object's own shadow, border and fill stay outside it. |
| `PicnicButton(text, onClick, modifier, style: Primary/Secondary/Text, enabled, glyph?)` | Buttons, at least 48 dp tall. Gloss is a palette setting, `colors.buttonGloss` (null = none). Any gloss must read as an intentional cel highlight: a shape of its own on the face, clear of the outline and the label, never a loose stroke or a second top edge. The placeholder Primary draws a tapered lens centred under the top outline at noon (about half the face wide, capped at 96 dp) and none at dusk, where the pale face gives a highlight nothing to contrast with. At dusk Primary buttons also wear a 2.5 dp halo in the ground colour, so the pale face never melts into a pastel band behind it. |
| `PicnicIconButton(glyph, contentDescription, onClick, modifier, enabled, emphasized)` | 48 dp round glyph button |
| `PicnicChip(text, selected, onClick, modifier, leading?, family, leadingHeight, interactionSource?)` | Aisle chips, Sources chips and filter chips |
| `PicnicTextField(value, onValueChange, modifier, placeholder, label, singleLine, minLines, maxLines, isError, supportingText, enabled, keyboardOptions, keyboardActions, visualTransformation, trailing?, textStyle?, example?, interactionSource?)` | All text input. Multi-line, its rules sit one per line at the text's line height (`textStyle`'s own, else `RULED_LINE_PITCH`, 24 sp). For the token field, pass `PasswordVisualTransformation`. The visible `label` is the editable node's content description (TalkBack: "Amount, 2, edit box") and is cleared from the label `Text`; tests find fields by `hasContentDescription(label)`. |
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
                         size: Dp = PicnicTheme.dimens.itemIcon, drawing: Boolean = false, lifted: Boolean = false)
@Composable fun AisleBasket(kind: BasketKind, inBasket: List<IconRef> /* gotten items */, count: Int /* to get */,
                            modifier = Modifier, size: Dp = PicnicTheme.dimens.basket)
@Composable fun AisleHeader(title: String, number: Int?, aisleIndex: Int, basket: BasketKind, inBasket: List<IconRef>,
                            count: Int, gotten: Int? /* null = the checked section */, collapsed: Boolean, cleared: Boolean,
                            onToggle: () -> Unit, modifier = Modifier, rowsBelow: Boolean = false, pinned: Boolean = false)
@Composable fun AisleSectionTitle(title: String, number: Int?, aisleIndex: Int, modifier = Modifier,
                                  rowIcon: Dp = PicnicTheme.dimens.itemIcon)
@Composable fun AisleSectionRow(aisleIndex: Int, first: Boolean, last: Boolean, modifier = Modifier,
                                content: @Composable () -> Unit)
@Composable fun ItemRowCard(name: String, detail: String?, icon: IconRef, checked: Boolean,
                            modifier = Modifier, drawing: Boolean = false, pressed: Boolean = false)
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

- Slices must touch: no `verticalArrangement` spacing in the list. Put `dimens.sectionGap` between sections (the List screen uses a spacer item) instead. Each slice draws its outline past its open edges and clips it to its own bounds, so adjacent slices join without a visible seam.
- **AisleSectionRow** draws the tray walls, the inner surface, a rule above every row but the `first`, and on the `last` row the cap. It clips `content` to the inner surface, so a swiped row slides out under the walls and the swipe background fills exactly that row's slot. Gestures, semantics and keys stay with the screen.
- **Section lines** (the inner surface's edge and the rules between rows) are measured as drawn, alpha included: at least 2:1 on the row surface in both palettes. Placeholder: the aisle's `deep` at 50 % at noon, its `base` at 70 % at dusk, a 35 % outline on neutral sections (at most 3:1 there, so it never reads as a second border inside the container's ink outline). `TokensContrastTest` checks all three.
- **AisleSectionTitle** is a non-interactive band (small medallion and title, announced as a heading) for read-only groupings such as the Add sheet's preview. Follow it with `AisleSectionRow`s, in a plain `Column` if the group is short. Pass the rows' icon size as `rowIcon` (rows laid out like `ItemRowCard`: `d.s`, icon, `d.m`, name) so the medallion sits on the icon column and the title starts where the names do. `AisleHeader` lines up the same way with `ItemRowCard`.
- **Band motion.** When a header's `rowsBelow` changes, its bottom closure (edge, corners, shadow) cross-fades over `motion.shortMillis`, alongside the rows' fade, so a band never sits open over nothing (instant with reduced motion). Keep the header the same lazy item while it expands and collapses, or the animation restarts at its target.
- **Pinned bands.** On the List screen every section header is a `stickyHeader`. Once an open section's rows scroll under the top, its band stays pinned, drawn closed (`rowsBelow = false`) as a card over its own rows, so a long aisle is never a run of rows without a name. A one-pixel sticky marker after each section pushes the band off as the section ends (the list ignores zero-size items when choosing which header to pin). The list's viewport starts under the top bar (top inset as padding, not content padding), so the pinned band sits just below the bar.
- **Pinned bands are backed.** A pinned band (`AisleHeader(pinned = true)`) repaints the screen ground behind itself, from the top of the list's viewport down to its own bottom edge and across the full width, in line with the real ground (`PicnicBackground` provides its position; the gingham and dusk stars keep its grid). No part of a row scrolling underneath may show above or beside the pinned band, its transparent rounded corners included; rows appear only below it, under its hard shadow. The card itself looks the same. The backing relies on the list clipping to its viewport (a `LazyColumn` does). Golden: `list_pinned` (Aisle 2's rows mid-scroll under it).
- **Ticking off.** A just-ticked row stays in its aisle, ticked, for 600 ms so the check stamp plays in place, then fades out of the aisle and into the basket. Basket rows are keyed `basket:<id>`, so a row never slides its slice across the sections in between. This is presentation only: the state and its counts are unchanged.
- **Art pass (replaces the next rule; consumers: `ItemIcon`, `ListPieces`):** item stickers carry their own hard die-cut shadow in rows (C's sticker look, DESIGN_STICKER §3.3); everything else in a row (the checkbox spot, the hanko) still lies flat.
- **No offset shadows inside a section (v1).** Circles and boxes in a row (item stickers, the checkbox) lie flat. An offset drop reads as a smudge or crescent next to the checkbox, not as depth. Lift (hard offset shadow) is only for free-floating cards, buttons and stickers on the ground: `ItemIcon(lifted = true)` for the edit sheet's hero icon.

- **ItemIcon** (art pass, DESIGN_STICKER §3.3): library stickers are vectors (the key, else its category's generic, else the capsule); a `customIconId` draws the live sticker from `LocalStickerArt` (`cached(id)` first, then `load(id)` in `produceState`; the library art meanwhile, and the previous sticker stays until a new one is decoded). One render path for both: the hard shadow at render time from the theme, the sticker, its look (ghost, holo, pixel, mono, capsule), the corner flags. A v4 live sticker's shadow, white die-cut edge and ghost come from its stored cut path; a legacy one is drawn as it is, with no added shadow, and its ghost is the mono silhouette at 18 %. `drawing` overlays `BusySparkle`. Rows pass `contentDescription = null`, because the row text already names the item. Callers pass `IconCatalog.displayRef(item, busy)`. (v1: `LocalIconImages` bitmaps and placeholder art.)
- **AisleBasket** draws the container art for `kind` (one vector or bitmap per basket kind from the art pass; the catalog's `drawHint`s describe each). **The basket holds what has already been gotten** (amendment A3): `inBasket` are the icons of the aisle's checked items, and the first of them (at most 5) peek out of the basket, overlapping. Items still to get are never passed. An empty `inBasket` (nothing gotten yet, or the list's empty state) draws the basket of that kind with no item minis in it. Kinds whose look comes from their contents (flower bucket, ice bucket, bottle carrier, candy jar) keep that built-in decoration when empty (amendment A3: Cora prefers maximalism to a bare container). A `count` badge (what is still to get) shows when `count > 0`. It is decorative.
- **AisleHeader** is the single toggle target for its section. Collapsed: `AisleBasket` (basket kind, the gotten items in it, the to-get badge) beside the title. Expanded: `AisleMedallion` and the title (no basket). Cleared: a "cleared" stamp in the trailing slot, replacing the count and the chevron (nothing is left to show or hide), never laid over them; collapsed, its basket is full. It announces "`<title>`, `<count>` to get, `<gotten>` in the basket" (the checked section, `gotten = null`: "`<title>`, `<count>` items"), plus "All picked up!" when cleared, with the state description collapsed or expanded. Unsorted and the checked section use it with `number = null` and `aisleIndex = -1`. With `rowsBelow = true` it is the open top band of its section, otherwise a closed card. The halftone accent fades out before an open band's bottom edge.
- **ItemRowCard** holds the visual content of a row only, opaque on the section's inner surface. Wrap it in `AisleSectionRow`; it draws no card, outline or shadow of its own. The screen adds click (toggles checked), long-press (opens edit) and `SwipeToDismissBox` (delete with undo). A checked item shows the filled `CheckMark`, a strike-through and the softer `inkSoft` colour. State is never shown by colour alone. `pressed` (from the screen's interaction source) is the press feedback: the row takes its section's pastel wash at once (it has no card edge to move or shrink). `TokensContrastTest` keeps ink, inkSoft and the checkbox ring readable on it.
- **Strike-through means done** everywhere. Screens that let the user skip items (the Add preview) fade a skipped row and leave its box empty; they never strike it.

### 11.5 Previews and accessibility rules

- **Previews (app-shell).** Every theme component gets an `@Preview` in light and dark. Roborazzi's preview scanner captures every `@Preview` under `earth.cora.picnic` automatically, at `w411dp-h891dp-night-xxhdpi` with SDK 36. `ThemeSampler.kt` is the first one (verified to render). Previews must render without the app container, through `StickerArt.None` (library vectors only) and `IconCatalog.default`.
- **Accessibility.** Item names use `type.itemName`, never smaller than 16 sp. Font scale is supported up to 200 %. Touch targets are at least 48 dp. Respect `PicnicTheme.motion.reduced`: no decorative loops, and fades of 150 ms or less. Nothing flashes faster than 3 Hz.
- **Art pass (WP10).** `ui/a11y`: `spokenDetail`, `spokenItemLabel` and `spokenDuration` (units in words for TalkBack: "4 L bag" → "4 litre bag"), `Modifier.decorative()` (no TalkBack node; a test-visible `Decorative` key), `TextOverflowed` (set by `PuffyText` and `LcdText` when their text doesn't fit) and `isScreenReaderOn()`. `platform`: `Feedback.cue(Cue)` (a sound and haptic pair, §10.1), `RepeatingCue` (repeats a cue while the screen is resumed), `Announcer.polite`, `Timeouts.recommended(holdMs, controls)` and `holdThresholdMs`.

---

## 12. Error handling summary

| Where | Error | User sees |
|---|---|---|
| ingest (text / transcript) | any `LlmError` | `Failed` with `friendlyMessage`, **Try again** if `retryable`, and **Add as plain lines** |
| ingest (photo) | any `LlmError` | `Failed` with **Try again**; the photo is kept until sent, replaced or deleted (§10.5) |
| any AI call | `Offline` (no network, 5 October) | "You’re offline right now. Try again once you’re back online." at once, no request; the add bar's Send adds as typed instead (§10.4) |
| any AI call | `NotConfigured` | "Add your OpenAI key in Settings first." and **Open Settings** |
| any AI call | `Unauthorized` | "OpenAI didn't accept this key. Check it in Settings, or paste a new one." (or, with `missingScopes`, "This OpenAI key is missing a permission the app needs (…)") and **Open Settings** |
| any AI call | `BadResponse(model_not_found)` (404, or 403 from a project's allowed-models list) | "This OpenAI key can't use one of the AI models the app needs. Check which models its project allows." |
| any AI call | `QuotaExceeded` (credits, or the project's hard spend limit) | "The OpenAI account behind this key is out of credit or over its monthly budget." |
| transcribe | empty text | "Didn't catch that. Try again a little closer to the mic." |
| transcribe | `LlmError` | `Failed` with **Try again** (the recording is kept on the phone until sent, replaced or deleted, §10.5) |
| photo decode | `IOException` / `IllegalStateException` | "Couldn't open that photo." |
| aisle sync / sortItems | `report.error` | snackbar: friendly message plus "N items left in Unsorted" (sortItems: silent unless the user is on the list) |
| IconDecider (step 2) | any | nothing; items keep the fallback icon and stay PENDING until the next process start, a key is added or the catch-up |
| icon drawing (step 3) | any | nothing (a background enhancement); a refusal blocks the name; Offline / Network / Timeout wait in `drawLater`, with the slot refunded only when no attempt reached OpenAI; a redraw she asked for says so in the Edit sheet |
| Test connection | `Offline`, `Network`, `Timeout` | "Saved." stays; a neutral "Not checked yet: …" line ("Test it again once you’re back online"), tested again by itself when a connection arrives while Settings is open |
| Test connection | any other | inline: ✓ and latency, or the friendly message |
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
- **Bitmaps.** Decoding, the icon trim/downscale and JPEG/WebP encoding run on `Dispatchers.Default`/`IO`, never in composition. `StickerArt.cached` touches memory only.
- **Recording.** It is foreground-only and stops on `Lifecycle.Event.ON_STOP`, except during a configuration change (`activity.isChangingConfigurations`): the recorder and meter live in the ViewModel and survive a rotation.

---

## 14. Screenshot testing

The tool is **Roborazzi 1.76.0** with Robolectric 4.17, native graphics, Robolectric **SDK pinned to 36** (`app/src/test/resources/robolectric.properties`; SDK 37 crashes Espresso's input injection) and `--add-opens java.base/jdk.internal.access`. The skeleton runs `recordRoborazziDebug` successfully. Paparazzi 2.0.0-alpha also works, but it is alpha; don't mix the two.

- **Art pass:** fixture goldens (the Appendix A "Saturday shop" fixture of DESIGN_STICKER) render at `w360dp-h760dp-xxhdpi` (the mockup's phone width, so the book has its 8 columns), with selected `w411dp-h891dp` variants named `…_w411`; names and the required set follow DESIGN_STICKER §20 and its Appendix C manifest (a reference: no test reads it). Each package records only its own goldens.
- **Goldens** live in `app/src/test/screenshots/`. Previews (app-shell) are named `<Class>.<Function>.<Preview_name>.png`. Explicit screen tests (app-screens) are named `<screen>_<state>_<light|dark>.png`.
- **Explicit screen tests.** Use `@RunWith(AndroidJUnit4::class)`, `@GraphicsMode(NATIVE)`, `@Config(qualifiers = "w411dp-h891dp-night-xxhdpi")` (or without `-night`) and `androidx.compose.ui.test.junit4.v2.createComposeRule`. Render the **stateless** `XxxScreen(state)`, then call `onRoot().captureRoboImage("src/test/screenshots/<name>.png")`.
- **Qualifier order.** `night` comes before the density. The other order throws.
- **States to cover:** List: empty; mixed sections with collapsed baskets (some items gotten; nothing gotten, an empty basket; a cleared aisle, a full basket), a drawing sparkle, a PENDING item and checked items; rows scrolled under a pinned band (`list_pinned`). Add sheet: write, thinking, preview with sources, failed with plain lines, and speak with a transcript. Edit sheet (with the icon grid). Aisles editor. Settings: no key, a key typed and shown, a saved key with a passing test, a failed test (a missing permission), and the "About your key" card scrolled into view.
- **Commands.** Record locally with `recordRoborazziDebug`. CI runs `compareRoborazziDebug`, which doesn't fail, and uploads the diffs. It stays that way: moving CI to `verifyRoborazziDebug` (DESIGN_STICKER item 305) was retired on 4 October.

---

## 15. CI and release (app-shell)

The workflow is `.github/workflows/android.yml`. Copy it verbatim from `research/android_build.md` §6, where it is proven and passes actionlint.

- **Triggers and environment:** push to any branch, tags `v*`, pull requests and `workflow_dispatch`. The runner is ubuntu-24.04 with JDK 21. setup-android installs `platforms;android-37.0 build-tools;36.0.0`. Gradle uses `cache-provider: basic`.
- **Jobs:** `build` (no secrets): (1) `./gradlew -p core test`; (2) `./gradlew :app:compareRoborazziDebug :app:lintDebug :app:assembleDebug` (`lintDebug` depends on `:app:checkSounds`; `:app:checkStickers` needs Python packages a runner lacks, so it runs locally); (3) upload the reports. Then `apk`, on its own runner with the signing key: `:app:assembleSideload -Ppicnic.versionCode=<run_number> -Ppicnic.versionName=<ver>`, a check that the APK carries exactly the registered certificate, and the bare **sideload** APK uploaded as `picnic-list-<ver>.apk` (`archive: false`).
- **Releases:** on a `v*` tag, a release job attaches the APK to a GitHub Release.
- **The installable APK is the `sideload` build type:** `initWith(debug)` (same code, not minified, no `debugImplementation` tooling) but `isDebuggable = false`, signed with the app's private key. A debuggable APK would let anyone with brief USB access `adb shell run-as` into the app's files and Keystore key (the stored token) or attach a debugger. Successive sideloads update in place; debug APKs (debug key) don't mix with them. `assembleDebug` stays for development.
- **Signing:** sideload (and release) builds are signed with the private key registered for `earth.cora.picnic` in Android Developer Console, from the `PICNIC_SIGNING_*` settings (docs/RELEASING.md); without them, sideload falls back to the committed `app/debug.keystore`, which also signs debug builds and must never be registered.
- **Secrets:** only the `apk` job reads them: the signing keystore and its password (docs/RELEASING.md §3). Live core tests are never run in CI.

---

## 16. Privacy and Play compliance

- **Permissions:** `INTERNET` and `RECORD_AUDIO` (runtime, foreground only). **5 October:** plus `ACCESS_NETWORK_STATE` (a normal permission, granted at install, no prompt) for `NetworkStatus` (§10.5). **Art pass (A6):** plus `ACCESS_COARSE_LOCATION`, requested only when she turns on "Use my area" (after the rationale in DESIGN_STICKER §0.4); never fine or background location (`ManifestPermissionsTest`, owners WP8 · WP9). There is **no `CAMERA` permission**, because the system camera intent is used. `<uses-feature>` marks camera and microphone as `required="false"`. The photo picker needs no permission. The `<queries>` entry for `IMAGE_CAPTURE` is present. Verified in the merged manifest: INTERNET, RECORD_AUDIO and androidx's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` only.
- **Data sent off the device:** text, photos and audio go directly to OpenAI only when the user asks; item names go there in the background, including for "Add as typed": to the fast model for aisle placement and icon decisions, and to the image model for bespoke icons (when "Draw new icons with AI" is on). The Settings help text says the same. Requests use `store: false`. Nothing else leaves the device, and there are no analytics.
- **Backups:** only `state.json` is backed up. **Art pass:** `state.json` and `album.json`.
- **Art pass:** the pantry is stored on the phone and sent to OpenAI with each AI request (never without a key); the opt-in weather feed and the approximate area are as in §9. PRIVACY.md says so (WP8 · WP10).
- **Play:** a later Play release needs a privacy policy URL (because of `RECORD_AUDIO`) and a Data safety form that discloses the processing.
- **Security:** no secrets in the repo or APK, ever. `*.env` and `.env*` are gitignored.
- **Legal and IP:** OFL fonts are bundled with their licences. Art uses only generic genre motifs (see the IP rules in `research/directions.md`), with no real brand packaging.
