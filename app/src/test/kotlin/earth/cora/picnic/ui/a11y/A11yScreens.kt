package earth.cora.picnic.ui.a11y

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.platform.FakeTimeouts
import earth.cora.picnic.ui.about.AboutActions
import earth.cora.picnic.ui.about.AboutScreen
import earth.cora.picnic.ui.about.AboutUiState
import earth.cora.picnic.ui.about.Licences
import earth.cora.picnic.ui.about.SurpriseLabScreen
import earth.cora.picnic.ui.add.AddFixtures
import earth.cora.picnic.ui.add.AddMode
import earth.cora.picnic.ui.add.AddOrigin
import earth.cora.picnic.ui.aisles.AislesActions
import earth.cora.picnic.ui.aisles.AislesScreen
import earth.cora.picnic.ui.aisles.MyStoreSamples
import earth.cora.picnic.ui.album.AlbumActions
import earth.cora.picnic.ui.album.AlbumSamples
import earth.cora.picnic.ui.album.AlbumScreen
import earth.cora.picnic.ui.eggs.EggTags
import earth.cora.picnic.ui.eggs.PracticeListScreen
import earth.cora.picnic.ui.eggs.SandboxClock
import earth.cora.picnic.ui.eggs.SurpriseLabSandbox
import earth.cora.picnic.ui.eggs.completedFixture
import earth.cora.picnic.ui.list.ListFixture
import earth.cora.picnic.ui.list.ListRig
import earth.cora.picnic.ui.list.ListTags
import earth.cora.picnic.ui.list.RecordingProbe
import earth.cora.picnic.ui.list.TripBookTags
import earth.cora.picnic.ui.list.NewTripTags
import earth.cora.picnic.ui.list.motion.CELEBRATION_TAG
import earth.cora.picnic.ui.list.motion.NEXT_CARD_TAG
import earth.cora.picnic.ui.pantry.PantryActions
import earth.cora.picnic.ui.pantry.PantryScreen
import earth.cora.picnic.ui.pantry.PantrySamples
import earth.cora.picnic.ui.settings.LocationPermission
import earth.cora.picnic.ui.settings.SettingsActions
import earth.cora.picnic.ui.settings.SettingsSamples
import earth.cora.picnic.ui.settings.SettingsScreen
import earth.cora.picnic.ui.theme.BookTags
import earth.cora.picnic.ui.theme.CelebrationTags
import earth.cora.picnic.ui.theme.LocalPieceProbe
import earth.cora.picnic.ui.theme.LocalTimeouts
import earth.cora.picnic.ui.theme.TOAST_TAG

/*
 * Every screen of the app and its main states (docs/DESIGN_STICKER.md §6, §7.5, §21 WP10), as the audits walk them.
 * The List and the sheets over it run the real List destination (ListRig: ListViewModel, WP7's AddViewModel, WP6's
 * engine) on Appendix A's "Saturday shop" at Hillcrest Market, and reach each state the way she would (a tap, a hold,
 * a swipe, a send); the secondary screens render their stateless screens with the samples their own tests use; the
 * receipt prints on the Surprise lab's practice list (the real List screen over the sandbox's real egg engine).
 */
object A11yScreens {
    private fun exists(compose: ComposeContentTestRule, tag: String): Boolean =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** A stage on the real List destination over [rig], reaching its state with [reach]. */
    private fun list(
        rig: ListRig,
        probe: RecordingProbe? = null,
        reduced: Boolean = false,
        leave: StageScope.() -> Unit = {},
        reach: StageScope.(ListRig) -> Unit = {},
    ): Stage =
        Stage(reducedMotion = reduced, dispose = { rig.addVm.cancel(); disposeScope(rig.scope)() }, reach = { reach(rig) }, leave = leave) {
            CompositionLocalProvider(LocalPieceProbe provides probe) { rig.Content() }
        }

    private fun StageScope.tap(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    private fun StageScope.menu(item: String) {
        tap(ListTags.MORE)
        compose.onNodeWithText(item).performClick()
        compose.waitForIdle()
    }

    /** The List (§6.1) in its states, with the sticker book collapsed and open. */
    val list: List<ScreenFixture> = listOf(
        ScreenFixture("list", "empty") { list(ListRig(ListFixture.empty()).inSeason()) },
        ScreenFixture("list", "fresh") { list(ListRig(ListFixture.fresh()).inSeason()) },
        ScreenFixture("list", "mid-trip") { list(ListRig().inSeason()) },
        ScreenFixture("list", "done") { list(ListRig(ListFixture.done()).inSeason()) },
        ScreenFixture("list", "default-aisles") { list(ListRig(ListFixture.everyDefaultAisle()).inSeason()) },
        // Household open and taller than the viewport: its plate pinned while its rows scroll under it (§4.10, R6).
        ScreenFixture("list", "pinned") {
            list(ListRig(ListFixture.longAisle()).inSeason()) {
                compose.onNodeWithTag(ListTags.LIST).performScrollToKey("header:household")
                compose.waitForIdle()
                compose.onNodeWithTag(ListTags.LIST).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 150f * compose.density.density) }
                compose.waitForIdle()
            }
        },
        // Scrolled down by her thumb: the bar folds into the FAB, the app bar and the book collapse (§6.1, D11).
        ScreenFixture("list", "fab") {
            list(ListRig(ListFixture.longAisle()).inSeason()) {
                val dp = compose.density.density
                compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeUp(startY = centerY + 120 * dp, endY = top + 10 * dp, durationMillis = 500) }
                compose.waitForIdle()
            }
        },
        // A folded strip's basket held: the peek note (§4.23).
        ScreenFixture("list", "peek") {
            val probe = RecordingProbe()
            list(ListRig().inSeason(), probe, leave = { compose.onRoot().performTouchInput { cancel() } }) {
                val basket = probe["basket:produce"]
                val strip = compose.onNodeWithTag(ListTags.strip("produce")).fetchSemanticsNode().boundsInRoot
                compose.mainClock.autoAdvance = false
                compose.onNodeWithTag(ListTags.strip("produce")).performTouchInput { down(Offset(basket.center.x - strip.left, basket.center.y - strip.top)) }
                framesUntil(1500) { exists(compose, ListTags.PEEK) }
            }
        },
        // McIntosh apples torn off: the halves gone, the slot closed, "McIntosh apples torn out" + Undo (§8.6).
        ScreenFixture("list", "tear-off-toast") {
            list(ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce")).inSeason()) {
                compose.onNodeWithTag(ListTags.LIST).performScrollToKey("apples")
                compose.waitForIdle()
                compose.mainClock.autoAdvance = false
                compose.onNodeWithTag(ListTags.row("apples")).performTouchInput {
                    down(center)
                    repeat(4) { moveBy(Offset(-20f * density, 0f)) }
                    up()
                }
                framesUntil(3000) { exists(compose, TOAST_TAG) }
                frames(20)
            }
        },
        ScreenFixture("list", "menu") { list(ListRig().inSeason()) { tap(ListTags.MORE) } },
        // Baking powder checked: Baking done, the Next card on its hold (§10).
        ScreenFixture("list", "next-card") {
            list(ListRig().inSeason()) {
                compose.onNodeWithTag(ListTags.LIST).performScrollToKey("baking-powder")
                compose.waitForIdle()
                compose.mainClock.autoAdvance = false
                compose.onNodeWithTag(ListTags.row("baking-powder")).performClick()
                framesUntil(4000) { exists(compose, NEXT_CARD_TAG) }
                frames(30)
            }
        },
        // The last thing checked: the celebration's summary (§11; reduced motion reaches it in 3 s, the same summary).
        ScreenFixture("list", "celebration-summary") {
            list(ListRig(ListFixture.allBut(listOf("toothpaste"), open = "household")), reduced = true) {
                compose.onNodeWithTag(ListTags.LIST).performScrollToKey("toothpaste")
                compose.waitForIdle()
                compose.mainClock.autoAdvance = false
                compose.onNodeWithTag(ListTags.row("toothpaste")).performClick()
                framesUntil(9000) { exists(compose, CelebrationTags.SUMMARY_CARD) }
                frames(10)
            }
        },
    )

    /** The sheets over the List (§4.16, §6.2 to §6.6). */
    val sheets: List<ScreenFixture> = listOf(
        ScreenFixture("sheet", "trip-book") { list(ListRig().inSeason()) { tap(BookTags.BOOK) } },
        ScreenFixture("sheet", "new-trip") { list(ListRig().inSeason()) { menu("New trip") } },
        ScreenFixture("sheet", "switch-list") { list(ListRig().inSeason()) { menu("Switch list…") } },
        ScreenFixture("sheet", "edit") { list(ListRig().inSeason()) { r -> compose.runOnIdle { r.vm.openEdit("milk") } } },
        ScreenFixture("sheet", "add-write") { list(ListRig().inSeason()) { r -> compose.runOnIdle { r.addVm.openSheet(AddMode.WRITE) } } },
        ScreenFixture("sheet", "add-snap") { list(ListRig().inSeason()) { r -> compose.runOnIdle { r.addVm.openSheet(AddMode.SNAP) } } },
        ScreenFixture("sheet", "add-speak") { list(ListRig().inSeason()) { r -> compose.runOnIdle { r.addVm.openSheet(AddMode.SPEAK) } } },
        // A photographed paper list, read: the polaroid and its popped stickers (§4.19).
        ScreenFixture("sheet", "add-snap-read") {
            list(ListRig().inSeason()) { r ->
                r.assistant.ingest = { _, _ -> AddFixtures.photo() }
                compose.runOnIdle { r.addVm.onPhoto(android.net.Uri.parse("content://a11y/photo.jpg")) }
            }
        },
        // Recording: the cassette, the equaliser and Stop (§4.20).
        ScreenFixture("sheet", "add-speak-recording") {
            list(ListRig().inSeason()) { r -> compose.runOnIdle { r.addVm.startRecording() } }
        },
        // "pancakes from scratch" answered with a recipe: the recipe card with kept and skipped rows (§4.17).
        ScreenFixture("sheet", "add-recipe") {
            list(ListRig().inSeason()) { r ->
                r.assistant.ingest = { _, _ -> AddFixtures.pancakes() }
                compose.runOnIdle {
                    r.addVm.openSheet(AddMode.WRITE)
                    r.addVm.setText("pancakes from scratch")
                    r.addVm.send(AddOrigin.SHEET)
                }
            }
        },
    )

    /** The receipt (egg 18, §13.2): printed from the LCD of a finished trip, waiting for her tap. */
    val receipt: ScreenFixture = ScreenFixture("list", "receipt") { compose ->
        val sandbox = SurpriseLabSandbox(
            fixture = completedFixture(),
            clock = SandboxClock(earth.cora.picnic.ui.eggs.PracticeFixture.NOW) { compose.mainClock.currentTime },
            timeouts = FakeTimeouts(),
            minuteTicks = false,
        )
        Stage(reducedMotion = true, dispose = { sandbox.close() }, reach = {
            compose.mainClock.autoAdvance = false
            frames(2)
            compose.runOnIdle { sandbox.fire(18) }
            framesUntil(4000) { exists(compose, EggTags.RECEIPT) }
            frames(30)
        }) {
            CompositionLocalProvider(LocalTimeouts provides sandbox.timeouts) { PracticeListScreen(sandbox) }
        }
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun screen(group: String, name: String, content: @androidx.compose.runtime.Composable () -> Unit) =
        ScreenFixture(group, name) { Stage(content = content) }

    /** The other screens (§6.7 to §6.10, §6.13), as their own tests render them. */
    val screens: List<ScreenFixture> = listOf(
        screen("mystore", "default") { AislesScreen(MyStoreSamples.default, AislesActions()) },
        screen("mystore", "editing") { AislesScreen(MyStoreSamples.default.copy(editing = "bakery"), AislesActions()) },
        screen("pantry", "few") { PantryScreen(PantrySamples.few, PantryActions()) },
        screen("pantry", "empty") { PantryScreen(PantrySamples.state(), PantryActions()) },
        screen("pantry", "popcard") { PantryScreen(PantrySamples.few, PantryActions(), initialPopCard = normalizeName("Salt")) },
        screen("settings", "key-set") { SettingsScreen(SettingsSamples.ok, SettingsActions(), location = LocationPermission.Denied) },
        screen("settings", "no-key") { SettingsScreen(SettingsSamples.typed, SettingsActions(), location = LocationPermission.Denied) },
        screen("about", "default") {
            AboutScreen(remember { AboutUiState("0.4.0", Licences.load(context), labVisible = true, canUnlock = false) }, AboutActions())
        },
        screen("lab", "default") { SurpriseLabScreen(onBack = {}) },
        screen("album", "default") { AlbumScreen(AlbumSamples.state(AlbumSamples.mockup), AlbumActions()) },
        screen("album", "book") { AlbumScreen(AlbumSamples.state(AlbumSamples.mockup), AlbumActions(), initialBook = AlbumSamples.perfectBook.id) },
    )

    /** Every fixture. */
    val all: List<ScreenFixture> get() = list + sheets + receipt + screens

    /** Unused tags kept referenced so a rename breaks the build here first. */
    @Suppress("unused")
    private val tags = listOf(TripBookTags.SHEET, NewTripTags.SHEET, CELEBRATION_TAG)
}
