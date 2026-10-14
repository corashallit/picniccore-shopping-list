package earth.cora.picnic.store

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.toSize
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
import earth.cora.picnic.core.assistant.toGroceryItem
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.PantryEntry
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.TripStats
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.currentList
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.model.setFavourite
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.core.model.updateList
import earth.cora.picnic.ui.add.ADD_BAR_DOODLE_TAG
import earth.cora.picnic.ui.add.ADD_BAR_TAG
import earth.cora.picnic.ui.add.ADD_CAPSULE_TAG
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
import earth.cora.picnic.ui.theme.LETTERSET_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
 * The segments tell one Saturday shop, in the video's order (docs/DESIGN_STICKER.md section): write (§6.2 Send with a
 * key, the review card, the commit), recipe (§4.17, §4.18), shop (§7.6 check-off, §10 Next stop), glow (§2.5, the
 * phone in dark mode), celebrate (§11) and album (§6.10). The list the trip is shopped from is what write and recipe
 * put on it plus what she added after (24 items, 8 stops, [trip]), so its count only goes up from one segment to the
 * next: shop 12 → 13 of 24, glow 13 → 15, celebrate 23 → 24.
 *
 * Marks (segment.json "marks": the first frame that shows the beat). Each segment opens on its screen at rest, long
 * enough for the caption to be read before anything moves.
 * - typing_start, typing_end (write, recipe): the first character in the add field; the whole note in it.
 * - sent: the first frame after Send is let go (the capsule takes the Send button's place).
 * - card_shown: the review's thinking box is there (the sheet starts to rise with it).
 * - sheet_up: the first frame the sheet rests fully risen (later it only glides to new heights as the card arrives).
 * - recipe_card (recipe): the recipe card's first frame. card_scroll: the finger comes down to scroll the card.
 * - stuck: the first frame after "Stick all" is let go (the sheet starts down, still showing the card).
 * - sheet_gone, rows_landed: the first frame with nothing of the sheet left; the new rows are on the list.
 * - first_tick, second_tick, last_tick: the first frame after a row's check spot is let go (GOT IT! slams on).
 * - next_card: the Next stop card's first frame.
 * - scroll_start (celebrate, album): the finger comes down to scroll.
 * - celebration (celebrate): the celebration's first frame (its white flash); title, perfect, banner, bonus, summary:
 *   its beats, from the scene's own storyboard (CelebrationTimeline).
 *
 * Taps and drags (segment.json "taps", VideoRig's header): every press is on the spot the app answers at (a tick on
 * the row's check spot, where its hanko lands); a drag is logged with the eased path its finger takes. Regions:
 * add_field (write, recipe), the add field's box over the typing with an even margin round it; pantry_row_1 and
 * pantry_row_2 (recipe), the recipe card's two "in your pantry" rows (White sugar, Salt) once the card has come to
 * rest, inside their tray's border and under the rule above them.
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
        "shop" -> Scene(TRIP_START + 17 * MIN) { c -> c.store.update { trip(it, gotten = 12) } }
        "glow" -> Scene(TRIP_START + 20 * MIN) { c -> c.store.update { trip(it, gotten = 13) } }
        "celebrate" -> Scene(TRIP_START + 41 * MIN) { c -> c.store.update { trip(it, gotten = 23) } }
        "album" -> Scene { c ->
            c.store.update { SaturdayShop.state() }
            c.album.update { AlbumSamples.full }
        }
        else -> Scene()
    }

    /* ---------------- the segments ---------------- */

    /**
     * Write (§6.2): the empty list, "Start my list", her list typed into the add bar, Send. With a key the note goes to
     * the assistant (rule 3; the bar's capsule wobbles meanwhile); six items are more than a quick add, so the sheet
     * opens on the review (rule 5): the thinking box, then the card's aisle trays. "Stick all 6" puts them on the list
     * in their aisles, the first stop open, with the toast; the sheet slides away still showing the card.
     */
    @Test
    fun write() = film("write", "day") { cam ->
        val sheet = SheetWatch(cam)
        // "A brand-new sticker sheet!" at rest, long enough to be read.
        cam.roll(2_200)
        press(cam, node(hasText("Start my list")))
        cam.roll(450)
        followField(cam) {
            type(cam, "bananas, avocados, 2 bagels, milk, eggs, maple syrup")
            cam.roll(400)
        }
        val answer = setup.fakes.assistant.answer(writeAnswer())
        press(cam, node(hasContentDescription("Send") and hasAnyAncestor(hasTestTag(ADD_BAR_TAG))))
        cam.mark("sent")
        cam.rollUntil("the add bar's capsule", maxMs = 500) { exists(ADD_CAPSULE_TAG) }
        cam.roll(1_400)
        answer.gate.complete(Unit)
        cam.rollUntil("the review", maxMs = 1_500) { exists(THINKING_TAG) }
        cam.mark("card_shown")
        cam.rollUntil("the card's stickers", maxMs = 6_000) { textShown("Stick all 6 on my list", substring = true) }
        cam.roll(2_250)
        stick(cam, sheet, "Stick all 6 on my list", items = 6)
        cam.roll(2_600)
    }

    /**
     * Recipe (§4.17, §4.18): on that list, "pancakes for 12" (her own words, so they never match the field's example
     * line). The thinking box ticks the answer's real steps (the web search with its own query, 3 recipes compared, the
     * pantry, the sort); the recipe card keeps what she needs and says why the rest is off ("already on your list", "in
     * your pantry" twice, "optional · skipped"); "Stick all 3" lands them in their aisles.
     */
    @Test
    fun recipe() = film("recipe", "day") { cam ->
        val sheet = SheetWatch(cam)
        cam.roll(1_500)
        press(cam, field())
        cam.roll(400)
        followField(cam) {
            type(cam, "pancakes for 12")
            cam.roll(400)
        }
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
        // The stickers fan out into the trays, and the steps (about 30 words) can be read.
        cam.roll(3_100)
        // Up through the card, so its aisle trays and their reasons can be read (the finger lifts on the card, a little
        // under the sheet's top edge, never over the dimmed app bar).
        cam.mark("card_scroll")
        drag(cam, from = Offset(540f, 1_880f), distance = 1_450f, ms = 2_000)
        cam.roll(300)
        // The two rows the pantry left out, at rest: White sugar, then Salt. Each inside its tray's border (2 dp) and
        // under the rule above it (1 dp), so a closer look shows the row alone.
        val dp = compose.activity.resources.displayMetrics.density
        compose.onAllNodes(hasContentDescription("in your pantry", substring = true)).fetchSemanticsNodes()
            .map(::boundsOnScreen).sortedBy { it.top }
            .forEachIndexed { i, b -> cam.region("pantry_row_${i + 1}", b.left + 2 * dp, b.top + dp, b.right - 2 * dp, b.bottom) }
        // Still long enough to read the rows and their reasons up close.
        cam.roll(3_800)
        stick(cam, sheet, "Stick all 3 on my list", items = 9)
        cam.roll(2_000)
    }

    /**
     * Shop (§7.6, §10): the same list at Hillcrest Market, 12 of 24 in the basket and Baking open with the baking
     * powder still to get. Its check spot: GOT IT!, its sticker hops into the mixing bowl and its twin lands in the
     * book; Baking is done, so the tray folds into its strip and the Next card points to Breakfast, which opens behind
     * it. Filmed until the card has gone again.
     */
    @Test
    fun shop() = film("shop", "day") { cam ->
        cam.roll(2_400)
        tick(cam, "Baking powder")
        cam.mark("first_tick")
        cam.rollUntil("the Next card", maxMs = 4_000) { exists(NEXT_CARD_TAG) }
        cam.mark("next_card")
        cam.rollUntil("the Next card to go", maxMs = 8_000) { !exists(NEXT_CARD_TAG) }
        cam.roll(500)
    }

    /**
     * Glow (§2.5): the phone is in dark mode, so the list is the Glow variant; the same trip a stop on, Breakfast open.
     * The maple syrup, then the coffee beans, and Breakfast is done: its tray folds and the Next card rises (the cut can
     * end before it, the shop segment's beat).
     */
    @Test
    @Config(qualifiers = "+night")
    fun glow() = film("glow", "glow") { cam ->
        cam.roll(1_500)
        tick(cam, "Maple syrup")
        cam.mark("first_tick")
        cam.roll(1_300)
        tick(cam, "Coffee beans")
        cam.mark("second_tick")
        cam.rollUntil("the Next card", maxMs = 4_000) { exists(NEXT_CARD_TAG) }
        cam.mark("next_card")
        cam.roll(1_000)
    }

    /**
     * Celebrate (§11): the trip 41 minutes in, everything in the basket but the toothpaste. A scroll down past the
     * finished aisles to Household, the last tick, the last aisle folds, then the celebration: "List complete!",
     * PER-FECT! for a trip with nothing left behind, the banner, the bonus sticker and the summary with its closing
     * bar, filmed until the summary has been still for a good read.
     */
    @Test
    fun celebrate() = film("celebrate", "day") { cam ->
        cam.roll(1_500)
        // Down the list to the last stop, past the aisles tied with their bows.
        cam.mark("scroll_start")
        drag(cam, from = Offset(540f, 1_700f), distance = 900f, ms = 1_100)
        // At rest on Household long enough for the scene's caption to be read before the tick.
        cam.roll(1_700)
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
        // The summary still for 3.4 s, stopping short of the scene closing by itself.
        cam.roll(min(plan.summaryAt + 3_400, plan.closeAt - 200))
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
        cam.roll(1_500)
        cam.mark("scroll_start")
        // From low on the grid, so the finger lifts on the stickers, under the pinned tabs.
        drag(cam, from = Offset(600f, 2_050f), distance = 1_500f, ms = 2_200)
        cam.roll(1_800)
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
        val list = state.currentList()
        println(
            "video: $segment ${cam.frame} frames, ${"%.0f".format(totalMs / cam.frame)} ms a frame (screen grab " +
                "${"%.0f".format(captureMs)} ms), the list ends at ${list.items.count { it.checked }}/${list.items.size}",
        )
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

    /**
     * Types [text] into the add bar's field one key at a time at about 12 characters a second, as a thumb types: a
     * beat longer after a space, longer still after a comma. Marks typing_start and typing_end: a key typed before
     * frame n shows from frame n + 1 (the field recomposes as the clock steps on).
     */
    private fun type(cam: FrameCamera, text: String) {
        cam.mark("typing_start", cam.frame + 1)
        var due = cam.elapsed
        for (c in text) {
            while (cam.elapsed < due) cam.shoot()
            field().performTextInput(c.toString())
            due += when (c) {
                ',' -> 180
                ' ' -> 90
                else -> 70
            }
        }
        cam.mark("typing_end", cam.frame + 1)
    }

    /**
     * Films [script] while following the add field on every frame (it grows to a second line as she types), then names
     * the box it took on the screen, with an 8 dp margin, as the region add_field. The bar's "picnic day" doodle leans
     * over the field's top, so the box starts between the doodle and the typed text rather than cut either in half, and
     * gives up as much of its margin below, so the text sits in the middle of it; never more than leaves the underline
     * whole (the field's own padding under it is about 5 dp).
     */
    private fun followField(cam: FrameCamera, script: () -> Unit) {
        var box: Rect? = null
        val stop = cam.watch { box = union(box, boundsOnScreen(field().fetchSemanticsNode())) }
        script()
        stop()
        val b = checkNotNull(box) { "the add field was never filmed" }
        val dp = compose.activity.resources.displayMetrics.density
        val doodle = compose.onAllNodesWithTag(ADD_BAR_DOODLE_TAG, useUnmergedTree = true).fetchSemanticsNodes()
            .firstOrNull()?.let(::boundsOnScreen)
        // Its box is laid out level; turned 4°, its lowest ink (the heart at its left end) ends about 1 dp under it, and
        // two-line text starts about 2 dp lower still.
        val clear = doodle?.let { it.bottom + 2 * dp } ?: Float.NEGATIVE_INFINITY
        val top = max(b.top - 8 * dp, clear)
        val bottom = max(b.bottom + 8 * dp - (top - (b.top - 8 * dp)), b.bottom - 2 * dp)
        cam.region("add_field", b.left - 8 * dp, top, b.right + 8 * dp, bottom)
    }

    /** A finger on [node]: down on its centre, held [holdMs] (the kit's press shows), up. */
    private fun press(cam: FrameCamera, node: SemanticsNodeInteraction, holdMs: Long = 100) {
        val c = boundsOnScreen(node.fetchSemanticsNode()).center
        cam.tap(c.x, c.y)
        node.performTouchInput { down(center) }
        cam.roll(holdMs)
        node.performTouchInput { up() }
    }

    /**
     * Ticks the row [name] with a press on its check spot, where the GOT IT! hanko lands (the whole row is the toggle),
     * then waits with the clock held for the store to have it checked.
     */
    private fun tick(cam: FrameCamera, name: String) {
        cam.settle("$name's row on screen") { onScreen(name) }
        val id = state.currentList().items.single { it.name == name }.id
        press(cam, compose.onNode(hasTestTag(ListTags.check(id)), useUnmergedTree = true), holdMs = 90)
        cam.settle("$name checked") { state.currentList().items.single { it.name == name }.checked }
    }

    /**
     * Presses the review's [button] ("Stick all n on my list"): marks stuck, waits (clock held) for the list to hold
     * [items], films the sheet sliding away with the card, and marks sheet_up, sheet_gone and rows_landed.
     */
    private fun stick(cam: FrameCamera, sheet: SheetWatch, button: String, items: Int) {
        val resting = cam.frame
        press(cam, node(hasText(button, substring = true)))
        cam.mark("stuck")
        cam.settle("the rows saved") { state.currentList().items.size == items }
        cam.rollUntil("the sheet to go", maxMs = 2_000) { sheet.gone() }
        sheet.mark(resting)
        cam.mark("rows_landed")
    }

    /**
     * A finger drags the screen's content up by [distance] px over [ms], starting at [from] (screen px): one move a
     * frame along a smoothstep, so the content glides and comes to rest before the finger lifts (no fling). Logged as
     * a drag from where the finger is on its first frame to where it lifts.
     */
    private fun drag(cam: FrameCamera, from: Offset, distance: Float, ms: Long) {
        val root = compose.onRoot()
        root.performTouchInput { down(from) }
        // Past the touch slop at once (the finger is already moving as it lands), so the content moves from the first
        // frame on instead of standing still and then jumping.
        val slop = android.view.ViewConfiguration.get(compose.activity).scaledTouchSlop.toFloat()
        root.performTouchInput { moveBy(Offset(0f, -slop)) }
        val first = cam.frame
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
        // Frame first + k showed the finger smoothstep(k / frames) of the way; it lifts after first + frames.
        cam.drag(first, from.x, from.y - slop, from.x, from.y - slop - distance, until = cam.frame - 1)
        root.performTouchInput { up() }
    }

    /**
     * Follows the Add sheet on every frame from the next one on (where it is on the screen, or nothing while it is not
     * there), for sheet_up, the first frame it rests fully risen (its bottom edge at rest: a later height glide moves
     * only its top), and sheet_gone, the first frame with nothing of it on the screen.
     */
    private inner class SheetWatch(private val cam: FrameCamera) {
        private val bottoms = HashMap<Int, Float>()

        init {
            cam.watch { f -> sheetOnScreen()?.let { bottoms[f] = it.bottom } }
        }

        /** The sheet has been on the screen and nothing of it is left there now. */
        fun gone(): Boolean = bottoms.isNotEmpty() && sheetOnScreen().let { it == null || it.top >= FrameCamera.HEIGHT }

        /** Marks sheet_up, the sheet having rested up to frame [resting], and sheet_gone, the next frame filmed. */
        fun mark(resting: Int) {
            val rest = bottoms.getValue(resting)
            var up = resting
            while (bottoms[up - 1]?.let { abs(it - rest) < 0.5f } == true) up--
            cam.mark("sheet_up", up)
            cam.mark("sheet_gone")
        }

        private fun sheetOnScreen(): Rect? =
            compose.onAllNodesWithTag(LETTERSET_TAG, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let(::boundsOnScreen)
    }

    /** Where [n] is on the screen as drawn (a layer's slide included), not clipped to it. */
    private fun boundsOnScreen(n: SemanticsNode): Rect = Rect(n.positionOnScreen, n.size.toSize())

    private fun union(a: Rect?, b: Rect): Rect =
        a?.let { Rect(min(it.left, b.left), min(it.top, b.top), max(it.right, b.right), max(it.bottom, b.bottom)) } ?: b

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
        val pantry = listOf("Salt", "White sugar", "Cinnamon", "Olive oil").map { PantryEntry(it, null, at - 30 * DAY) }
        return s.updateList(s.currentListId) { it.copy(items = items) }.copy(pantry = pantry)
    }

    /**
     * The Saturday shop the rest of the video is shopped from: the six she wrote and the recipe's three (as their
     * commits wrote them), and 15 more of Appendix A's items she added after the recipe, 24 in Hillcrest Market's 8
     * stops. She set off at [TRIP_START] and gets them in walking order ([WAY_ROUND]); the first [gotten] are in the
     * basket. The coffee beans are a favourite; the open stop is the automatic one, the first with something left.
     */
    private fun trip(s: AppState, gotten: Int): AppState {
        val base = written(s.withHillcrest())
        val id = base.currentListId
        val recipe = pancakes().items.filter { it.skipReason == SkipReason.NONE }
            .mapIndexed { i, p -> p.toGroceryItem("r-$i", VIDEO_MORNING + 4 * MIN + 10_000 + i, ItemSource.AI_TEXT) }
        val more = ADDED_AFTER.mapIndexed { i, row -> SaturdayShop.item(row).copy(addedAt = VIDEO_MORNING + 6 * MIN + i * 20_000L) }
        val listed = (recipe + more).fold(base) { acc, item -> acc.addItems(id, listOf(item), item.addedAt).first }
        check(listed.currentList().items.size == 24) { "the trip's list holds ${listed.currentList().items.size} items" }
        val checked = WAY_ROUND.take(gotten).fold(listed) { acc, (item, minute) -> acc.toggleChecked(id, item, TRIP_START + minute * MIN) }
        return checked.setFavourite(normalizeName("Coffee beans"), true).setOpenAisle(id, null)
    }

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
     * "pancakes for 12" against the written list and her pantry (salt and white sugar are in it): a recipe from the web
     * for 12, compared across three, with brand-free source names.
     */
    private fun pancakes() = IngestResult(
        items = listOf(
            item("Blueberries", "blueberries", "produce", "170 g clamshell · 1 cup", SkipReason.OPTIONAL),
            item("All-purpose flour", "flour", "aisle-2-baking", "2.5 kg bag · 2 cups"),
            item("White sugar", "sugar", "aisle-2-baking", "2 kg bag · 2 tbsp", SkipReason.IN_PANTRY, pantry = "White sugar"),
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
        meta = meta(webSearches = 1, queries = listOf("fluffy pancakes recipe for 12")),
        recipe = RecipeInfo("Fluffy pancakes", recipesCompared = 3, servings = 12),
    )

    private companion object {
        const val MIN = 60_000L
        const val DAY = 86_400_000L

        /** 11:02, half an hour after she wrote the list: the first sticker of the trip. */
        const val TRIP_START = VIDEO_MORNING + 32 * MIN

        /**
         * Appendix A's items she added after the recipe: all but the ones already on her list (its bananas, flour,
         * baking powder, maple syrup, milk, eggs and butter), the sugar in her pantry and the sourdough (her bagels).
         */
        val ADDED_AFTER = listOf(
            "strawberries", "apples", "carrots", "broccoli", "croissants", "baguette", "ham", "peameal", "coffee",
            "peas", "ice-cream", "toilet-paper", "dish-soap", "cat-food", "toothpaste",
        )

        /**
         * Her way round Hillcrest Market: every item in walking order, with the minute after [TRIP_START] it went in the
         * basket (w- the six she wrote, r- the recipe's flour, baking powder and butter).
         */
        val WAY_ROUND: List<Pair<String, Long>> = listOf(
            "w-0" to 0L, "w-1" to 1L, "strawberries" to 2L, "apples" to 3L, "carrots" to 4L, "broccoli" to 5L, // Produce
            "w-2" to 7L, "croissants" to 8L, "baguette" to 9L, // Bakery
            "ham" to 11L, "peameal" to 12L, // Deli & meat
            "r-0" to 15L, "r-1" to 17L, // Baking
            "w-5" to 20L, "coffee" to 21L, // Breakfast
            "w-3" to 24L, "w-4" to 25L, "r-2" to 26L, // Dairy & eggs
            "peas" to 29L, "ice-cream" to 30L, // Frozen
            "toilet-paper" to 33L, "dish-soap" to 34L, "cat-food" to 36L, "toothpaste" to 41L, // Household
        )
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
