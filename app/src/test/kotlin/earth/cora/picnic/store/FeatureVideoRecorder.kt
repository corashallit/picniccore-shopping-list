package earth.cora.picnic.store

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.AppContainer
import earth.cora.picnic.MainActivity
import earth.cora.picnic.PicnicApp
import earth.cora.picnic.core.assistant.CallMeta
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.ProposedItem
import earth.cora.picnic.core.assistant.RecipeInfo
import earth.cora.picnic.core.assistant.SkipReason
import earth.cora.picnic.core.assistant.WebSource
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.PantryEntry
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.TripStats
import earth.cora.picnic.core.model.currentList
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.core.model.updateList
import earth.cora.picnic.ui.add.ADD_BAR_TAG
import earth.cora.picnic.ui.add.ADD_CAPSULE_TAG
import earth.cora.picnic.ui.add.ADD_TABS_TAG
import earth.cora.picnic.ui.add.RECIPE_CARD_TAG
import earth.cora.picnic.ui.add.THINKING_TAG
import earth.cora.picnic.ui.album.AlbumSamples
import earth.cora.picnic.ui.album.AlbumTags
import earth.cora.picnic.ui.common.SaturdayShop
import earth.cora.picnic.ui.list.ListTags
import earth.cora.picnic.ui.list.motion.BeatName
import earth.cora.picnic.ui.list.motion.CELEBRATION_TAG
import earth.cora.picnic.ui.list.motion.CelebrationTimeline
import earth.cora.picnic.ui.list.motion.NEXT_CARD_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The store's feature video, filmed in the real app: MainActivity with the real container (the JSON store, the album,
 * the screens, the motion engine and the eggs), driven through a storyboard one segment per test and written frame by
 * frame for design/store/make_feature_video.py to cut (VideoRig: the frame contract, the fakes, the camera). Each
 * segment seeds its own state, so the tests run in any order; the clock is held and stepped at 30 frames a second of
 * app time, so every frame is the app exactly at its time. No request leaves the test: the assistant's answers are
 * scripted (as the real one returns them after its post-processing) and every sticker is from the library.
 *
 * Skipped unless PICNIC_VIDEO_FRAMES names the frames' root folder (CI's testDebugUnitTest reports it skipped). Record:
 *
 *     PICNIC_VIDEO_FRAMES=/path/to/frames ./gradlew :app:testDebugUnitTest \
 *         --tests 'earth.cora.picnic.store.FeatureVideoRecorder' --rerun
 *
 * The forked test JVM sees the variable as Gradle's own environment has it; design/store/make_feature_video.py then cuts
 * the video from the folder.
 *
 * Segments (docs/DESIGN_STICKER.md section): write (§6.2 Send with a key, the review card, the commit), recipe (§4.17,
 * §4.18), shop (§7.6 check-off, §10 Next stop), celebrate (§11), album (§6.10), glow (§2.5, the phone in dark mode).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class FeatureVideoRecorder {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val setup = VideoSetup(::scene) { compose.mainClock }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    private val container: AppContainer get() = (compose.activity.application as PicnicApp).container

    private val state: AppState get() = runBlocking { container.store.current() }

    private fun scene(segment: String): Scene = when (segment) {
        "write" -> Scene { c -> c.store.update { it.withHillcrest() } }
        "recipe" -> Scene(VIDEO_MORNING + 4 * MIN) { c -> c.store.update { written(it.withHillcrest()) } }
        "shop" -> Scene { c -> c.store.update { SaturdayShop.state() } }
        "celebrate" -> Scene(SaturdayShop.TRIP_START + 41 * MIN) { c -> c.store.update { almostDone() } }
        "album" -> Scene { c ->
            c.store.update { SaturdayShop.state() }
            c.album.update { AlbumSamples.full }
        }
        "glow" -> Scene { c -> c.store.update { atProduce() } }
        else -> Scene()
    }

    /* ---------------- the segments ---------------- */

    /**
     * Write (§6.2): the empty list, "Start my list", her list typed into the add bar, Send. With a key the note goes to
     * the assistant (rule 3; the bar's capsule wobbles meanwhile); six items are more than a quick add, so the sheet
     * opens on the review (rule 5): the thinking box, then the card's aisle trays. "Stick all 6" puts them on the list
     * in their aisles, the first stop open, with the toast.
     */
    @Test
    fun write() = film("write", "day") { cam ->
        cam.roll(1_100)
        press(cam, node(hasText("Start my list")))
        cam.roll(450)
        cam.mark("typing_start")
        type(cam, "bananas, avocados, 2 bagels, milk, eggs, maple syrup")
        cam.roll(400)
        val answer = setup.fakes.assistant.answer(writeAnswer())
        press(cam, node(hasContentDescription("Send") and hasAnyAncestor(hasTestTag(ADD_BAR_TAG))))
        cam.mark("sent")
        cam.rollUntil("the add bar's capsule", maxMs = 500) { exists(ADD_CAPSULE_TAG) }
        cam.roll(1_400)
        answer.gate.complete(Unit)
        cam.rollUntil("the review", maxMs = 1_500) { exists(THINKING_TAG) }
        cam.mark("card_shown")
        cam.rollUntil("the card's stickers", maxMs = 6_000) { textShown("Stick all 6 on my list", substring = true) }
        cam.roll(1_800)
        press(cam, node(hasText("Stick all 6 on my list", substring = true)))
        cam.mark("stuck")
        cam.settle("the six rows saved") { state.currentList().items.size == 6 }
        cam.rollUntil("the sheet to go", maxMs = 2_000) { !exists(ADD_TABS_TAG) && row("Bananas") != null }
        cam.mark("rows_landed")
        cam.roll(2_600)
    }

    /**
     * Recipe (§4.17, §4.18): on that list, "pancakes from scratch". The thinking box ticks the answer's real steps (the
     * web search with its own query, 3 recipes compared, the pantry, the sort); the recipe card keeps what she needs
     * and says why the rest is off ("already on your list", "in your pantry", "optional · skipped"); "Stick all 4"
     * lands them in their aisles.
     */
    @Test
    fun recipe() = film("recipe", "day") { cam ->
        cam.roll(900)
        press(cam, field())
        cam.roll(300)
        cam.mark("typing_start")
        type(cam, "pancakes from scratch")
        cam.roll(400)
        val answer = setup.fakes.assistant.answer(pancakes())
        press(cam, node(hasContentDescription("Send") and hasAnyAncestor(hasTestTag(ADD_BAR_TAG))))
        cam.mark("sent")
        cam.rollUntil("the add bar's capsule", maxMs = 500) { exists(ADD_CAPSULE_TAG) }
        cam.roll(1_600)
        answer.gate.complete(Unit)
        cam.rollUntil("the review", maxMs = 1_500) { exists(THINKING_TAG) }
        cam.mark("card_shown")
        cam.rollUntil("the recipe card", maxMs = 4_000) { exists(RECIPE_CARD_TAG) }
        cam.mark("recipe_card")
        // The stickers fan out into the trays, and the steps can be read.
        cam.roll(1_700)
        // Up through the card, so its aisle trays and their reasons can be read.
        cam.mark("card_scroll")
        drag(cam, from = Offset(540f, 1_750f), distance = 1_700f, ms = 2_000)
        cam.roll(2_200)
        press(cam, node(hasText("Stick all 4 on my list", substring = true)))
        cam.mark("stuck")
        cam.settle("the four rows saved") { state.currentList().items.size == 10 }
        cam.rollUntil("the sheet to go", maxMs = 2_000) { !exists(ADD_TABS_TAG) }
        cam.mark("rows_landed")
        cam.roll(2_000)
    }

    /**
     * Shop (§7.6, §10): mid-trip at Hillcrest Market, Baking open. Baking powder: GOT IT!, its sticker hops into the
     * mixing bowl and its twin lands in the book; Baking is done, so the Next card points to Breakfast, which opens
     * behind it. Then maple syrup, and the next card.
     */
    @Test
    fun shop() = film("shop", "day") { cam ->
        cam.roll(900)
        tick(cam, "Baking powder")
        cam.mark("first_tick")
        cam.rollUntil("the Next card", maxMs = 4_000) { exists(NEXT_CARD_TAG) }
        cam.mark("next_card")
        cam.rollUntil("the Next card to go", maxMs = 8_000) { !exists(NEXT_CARD_TAG) }
        cam.roll(300)
        tick(cam, "Maple syrup")
        cam.mark("second_tick")
        cam.rollUntil("the second Next card", maxMs = 4_000) { exists(NEXT_CARD_TAG) }
        cam.mark("next_card_2")
        cam.roll(2_400)
    }

    /**
     * Celebrate (§11): everything in the basket but the toothpaste, 41 minutes into the trip. A scroll down past the
     * finished aisles to Household, the last tick, the last aisle folds, then the celebration: "List complete!",
     * PER-FECT! for a trip with nothing left behind, the banner, the bonus sticker and the summary with its closing
     * bar.
     */
    @Test
    fun celebrate() = film("celebrate", "day") { cam ->
        cam.roll(700)
        // Down the list to the last stop, past the aisles tied with their bows.
        drag(cam, from = Offset(540f, 1_700f), distance = 900f, ms = 1_100)
        cam.roll(500)
        tick(cam, "Toothpaste")
        cam.mark("last_tick")
        cam.rollUntil("the celebration", maxMs = 6_000) { exists(CELEBRATION_TAG) }
        cam.mark("celebration")
        // The scene's own storyboard (CelebrationTimeline, the same plan the scene runs) names the beats' frames.
        val list = state.currentList()
        val plan = CelebrationTimeline.plan(list.title, TripStats.of(list, stops = 8), petHatched = false)
        val start = cam.frame
        fun frameAt(ms: Long) = start + (ms * VIDEO_FPS / 1000).toInt()
        cam.mark("title", frameAt(plan.beat(BeatName.Title).at))
        cam.mark("perfect", frameAt(plan.beat(BeatName.Perfect).at))
        cam.mark("banner", frameAt(plan.beat(BeatName.Banner).at))
        cam.mark("bonus", frameAt(plan.beat(BeatName.Bonus).at))
        cam.mark("summary", frameAt(plan.summaryAt))
        // Until the summary has been still for a good read.
        cam.roll(plan.summaryAt + 2_400)
    }

    /**
     * Album (§6.10): her Sticker album with a healthy collection (136 of 216 stickers, holo ones, two filed trip
     * books), its Groceries pages scrolled slowly from the fruit to the vegetables.
     */
    @Test
    fun album() = film("album", "day", opening = {
        node(hasTestTag(ListTags.MORE)).performTouchInput { click(center) }
        settleFrames(400)
        node(hasText("Sticker album")).performTouchInput { click(center) }
        settleFrames(1_200) { exists(AlbumTags.LIST) }
    }) { cam ->
        cam.roll(1_000)
        cam.mark("scroll_start")
        drag(cam, from = Offset(600f, 1_900f), distance = 1_500f, ms = 2_200)
        cam.roll(1_400)
    }

    /**
     * Glow (§2.5): the phone is in dark mode, so the list is the Glow variant. At the first stop: the strawberries,
     * then the apples, and Produce is done.
     */
    @Test
    @Config(qualifiers = "+night")
    fun glow() = film("glow", "glow") { cam ->
        cam.roll(800)
        tick(cam, "Strawberries")
        cam.mark("first_tick")
        cam.roll(1_300)
        tick(cam, "McIntosh apples")
        cam.mark("second_tick")
        cam.rollUntil("the Next card", maxMs = 4_000) { exists(NEXT_CARD_TAG) }
        cam.mark("next_card")
        cam.roll(1_400)
    }

    /* ---------------- the script's verbs ---------------- */

    /**
     * Holds the clock, waits for the list, runs [opening] unfilmed (the clock still stepping), lets the screen settle,
     * then films [script] into the segment's folder and writes its segment.json.
     */
    private fun film(
        segment: String,
        theme: String,
        opening: () -> Unit = {},
        script: (FrameCamera) -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        everyMillisecond()
        settleFrames(20_000) { exists(ListTags.LIST) || exists(ListTags.EMPTY) }
        settleFrames(1_500)
        opening()
        settleFrames(600)
        val began = System.nanoTime()
        val cam = FrameCamera(compose, videoRoot!!, segment, theme)
        script(cam)
        val captureMs = cam.finish()
        val totalMs = (System.nanoTime() - began) / 1e6
        println("video: $segment ${cam.frame} frames, ${"%.0f".format(totalMs / cam.frame)} ms a frame (screen grab ${"%.0f".format(captureMs)} ms)")
    }

    /**
     * The Compose test clock delivers a frame every millisecond, so the frame the camera grabs is always the app at the
     * grab's own time (at the default 16 ms frame, the grab would show the last frame up to 15 ms before it).
     */
    private fun everyMillisecond() {
        val environment = compose.fieldValue("environment")!!
        val frameClock = environment.fieldValue("frameClock")!!
        val delay = frameClock.javaClass.getDeclaredField("frameDelayNanos").apply { isAccessible = true }
        delay.setLong(frameClock, 1_000_000L)
        check(delay.getLong(frameClock) == 1_000_000L) { "the test frame clock kept its own frame time" }
    }

    /** Runs the clock [ms] unfilmed (16 ms steps), then until [done] holds (at most 20 s more). */
    private fun settleFrames(ms: Long, done: () -> Boolean = { true }) {
        val until = compose.mainClock.currentTime + ms
        val guard = compose.mainClock.currentTime + 20_000 + ms
        while (compose.mainClock.currentTime < until || !done()) {
            check(compose.mainClock.currentTime < guard) { "the app never settled" }
            Snapshot.sendApplyNotifications()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(16, ignoreFrameDuration = true)
        }
        compose.waitForIdle()
    }

    /** Types [text] into the add bar's field one character at a time, at about 14 a second, pausing after commas. */
    private fun type(cam: FrameCamera, text: String) {
        var due = cam.elapsed
        for (c in text) {
            while (cam.elapsed < due) cam.shoot()
            field().performTextInput(c.toString())
            due += when (c) {
                ',' -> 170
                ' ' -> 50
                else -> 62
            }
        }
    }

    /** A finger on [node]: down, held [holdMs] (the kit's press shows), up. */
    private fun press(cam: FrameCamera, node: SemanticsNodeInteraction, holdMs: Long = 100) {
        val n = node.fetchSemanticsNode()
        val c = n.positionOnScreen + Offset(n.size.width / 2f, n.size.height / 2f)
        cam.tap(c.x, c.y)
        node.performTouchInput { down(center) }
        cam.roll(holdMs)
        node.performTouchInput { up() }
    }

    /** Ticks the row [name] (a press), then waits with the clock held for the store to have it checked. */
    private fun tick(cam: FrameCamera, name: String) {
        cam.settle("$name's row on screen") { onScreen(name) }
        press(cam, row(name)!!, holdMs = 90)
        cam.settle("$name checked") { state.currentList().items.single { it.name == name }.checked }
    }

    /**
     * A finger drags the screen's content up by [distance] px over [ms], starting at [from] (root px): one move a
     * frame, eased in and out, so the content glides and comes to rest before the finger lifts (no fling).
     */
    private fun drag(cam: FrameCamera, from: Offset, distance: Float, ms: Long) {
        val root = compose.onRoot()
        cam.tap(from.x, from.y)
        root.performTouchInput { down(from) }
        // Past the touch slop at once (the finger is already moving as it lands), so the content moves from the first
        // frame on instead of standing still and then jumping.
        val slop = android.view.ViewConfiguration.get(compose.activity).scaledTouchSlop.toFloat()
        root.performTouchInput { moveBy(Offset(0f, -slop)) }
        val frames = (ms * VIDEO_FPS / 1000).toInt().coerceAtLeast(2)
        var moved = 0f
        for (i in 1..frames) {
            cam.shoot()
            val u = i.toFloat() / frames
            val target = distance * u * u * (3 - 2 * u)
            root.performTouchInput { moveBy(Offset(0f, -(target - moved))) }
            moved = target
        }
        cam.shoot()
        root.performTouchInput { up() }
    }

    /** The row [name] is on screen, clear of the add bar. */
    private fun onScreen(name: String): Boolean {
        val r = row(name)?.fetchSemanticsNode()?.boundsInRoot ?: return false
        val bar = compose.onAllNodesWithTag(ADD_BAR_TAG, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top ?: Float.MAX_VALUE
        return r.top >= 0f && r.bottom <= bar
    }

    private fun field(): SemanticsNodeInteraction = node(hasSetTextAction() and hasAnyAncestor(hasTestTag(ADD_BAR_TAG)))

    private fun node(matcher: SemanticsMatcher): SemanticsNodeInteraction = compose.onNode(matcher, useUnmergedTree = false)

    private fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun textShown(text: String, substring: Boolean = false): Boolean =
        compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The list row whose spoken label starts with [name], or null while it is not on screen. */
    private fun row(name: String): SemanticsNodeInteraction? {
        val node = compose.onAllNodes(hasContentDescription(name, substring = true)).fetchSemanticsNodes().firstOrNull { n ->
            n.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox &&
                n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.startsWith(name) == true
        } ?: return null
        return compose.onNode(SemanticsMatcher("row ${node.id}") { it.id == node.id })
    }

    /* ---------------- the fixtures ---------------- */

    /** Hillcrest Market (Appendix A), the list called "Saturday shop". */
    private fun AppState.withHillcrest(): AppState =
        copy(store = StoreProfile(name = SaturdayShop.STORE, aislesText = SaturdayShop.AISLES)).updateList(currentListId) { it.copy(title = "Saturday shop") }

    /** The write segment's list once it is on: six rows in four aisles, and her pantry's staples. */
    private fun written(s: AppState): AppState {
        val at = VIDEO_MORNING
        val items = writeAnswer().items.mapIndexed { i, p ->
            GroceryItem(
                id = "w-$i", name = p.name, quantity = p.quantity, note = p.note, aisleId = p.aisleId, icon = p.icon, iconFit = p.iconFit,
                addedAt = at + i, source = ItemSource.AI_TEXT,
            )
        }
        val pantry = listOf("Salt", "Cinnamon", "Olive oil").map { PantryEntry(it, null, at - 30 * DAY) }
        return s.updateList(s.currentListId) { it.copy(items = items) }.copy(pantry = pantry)
    }

    /** The Saturday shop with everything in the basket but the toothpaste, checked over 40 minutes. */
    private fun almostDone(): AppState {
        val base = SaturdayShop.state()
        val rest = base.currentList().items.filter { !it.checked && it.name != "Toothpaste" }
        val checked = rest.foldIndexed(base) { i, s, item -> s.toggleChecked(SaturdayShop.LIST_ID, item.id, SaturdayShop.TRIP_START + (26 + i) * MIN) }
        // The open stop is the automatic one again: Household, where the toothpaste waits.
        return checked.setOpenAisle(SaturdayShop.LIST_ID, null)
    }

    /** The Saturday shop at its first stop: Produce open, the strawberries and the apples still to get. */
    private fun atProduce(): AppState = SaturdayShop.state().setOpenAisle(SaturdayShop.LIST_ID, "produce")

    private fun meta(webSearches: Int = 0, queries: List<String> = emptyList()) =
        CallMeta(model = null, requestId = null, latencyMs = 1_600, webSearches = webSearches, searchQueries = queries)

    private fun item(name: String, icon: String, aisle: String, quantity: String? = null, skip: SkipReason = SkipReason.NONE, named: Boolean = false, pantry: String? = null) =
        ProposedItem(name, quantity, null, aisle, icon, IconFit.EXACT, skipReason = skip, named = named, pantryEntry = pantry)

    /**
     * "bananas, avocados, 2 bagels, milk, eggs, maple syrup": each named line in its aisle, no amounts invented. No
     * berries or apples: egg 3 would send them hopping along their rows after the commit, a surprise for her own list.
     */
    private fun writeAnswer() = IngestResult(
        items = listOf(
            item("Bananas", "banana", "produce", named = true),
            item("Avocados", "avocado", "produce", named = true),
            item("Bagels", "bagel", "bakery", "2", named = true),
            item("Milk", "milk_carton", "dairy-eggs", named = true),
            item("Eggs", "eggs", "dairy-eggs", named = true),
            item("Maple syrup", "maple_syrup", "breakfast", named = true),
        ),
        message = "",
        sources = emptyList(),
        skippedDuplicates = emptyList(),
        meta = meta(),
    )

    /**
     * "pancakes from scratch" against the written list and her pantry: a recipe from the web, compared across three, with
     * brand-free source names.
     */
    private fun pancakes() = IngestResult(
        items = listOf(
            item("Blueberries", "blueberries", "produce", "170 g clamshell · 1 cup", SkipReason.OPTIONAL),
            item("All-purpose flour", "flour", "aisle-2-baking", "2.5 kg bag · 2 cups"),
            item("White sugar", "sugar", "aisle-2-baking", "2 kg bag · 2 tbsp"),
            item("Baking powder", "baking_soda", "aisle-2-baking", "225 g tin · 2 tsp"),
            item("Salt", "salt", "aisle-2-baking", "1 kg box · ½ tsp", SkipReason.IN_PANTRY, pantry = "Salt"),
            item("Pure vanilla extract", "vanilla", "aisle-2-baking", "43 mL bottle · 1 tsp", SkipReason.OPTIONAL),
            item("Milk", "milk_carton", "dairy-eggs", "2 L carton · 1½ cups", SkipReason.ALREADY_ON_LIST),
            item("Eggs", "eggs", "dairy-eggs", "1 dozen · 2", SkipReason.ALREADY_ON_LIST),
            item("Unsalted butter", "butter", "dairy-eggs", "454 g brick · 3 tbsp"),
            item("Maple syrup", "maple_syrup", "breakfast", "540 mL tin · to serve", SkipReason.ALREADY_ON_LIST),
        ),
        message = "",
        sources = listOf(
            WebSource("https://recipes.example/fluffy-pancakes", "Fluffy pancakes"),
            WebSource("https://kitchen.example/weekend-pancakes", "Weekend pancakes"),
            WebSource("https://baking.example/classic-pancakes", "Classic pancakes"),
        ),
        skippedDuplicates = listOf("Milk", "Eggs", "Maple syrup"),
        meta = meta(webSearches = 1, queries = listOf("fluffy pancakes from scratch recipe")),
        recipe = RecipeInfo("Pancakes from scratch", recipesCompared = 3, servings = 12),
    )

    private companion object {
        const val MIN = 60_000L
        const val DAY = 86_400_000L
    }
}

/** A private field's value, read by reflection (the Compose test environment's frame clock). */
private fun Any.fieldValue(name: String): Any? {
    var c: Class<*>? = javaClass
    while (c != null) {
        runCatching { return c.getDeclaredField(name).apply { isAccessible = true }.get(this) }
        c = c.superclass
    }
    error("no field $name on ${javaClass.name}")
}
