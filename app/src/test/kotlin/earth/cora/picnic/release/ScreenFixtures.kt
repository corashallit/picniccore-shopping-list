package earth.cora.picnic.release

import android.content.Context
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.about.AboutActions
import earth.cora.picnic.ui.about.AboutScreen
import earth.cora.picnic.ui.about.AboutUiState
import earth.cora.picnic.ui.about.Licences
import earth.cora.picnic.ui.about.SurpriseLabScreen
import earth.cora.picnic.ui.add.AddActions
import earth.cora.picnic.ui.add.AddFixtures
import earth.cora.picnic.ui.add.AddMode
import earth.cora.picnic.ui.add.AddOrigin
import earth.cora.picnic.ui.add.AddPhase
import earth.cora.picnic.ui.add.AddProblem
import earth.cora.picnic.ui.add.AddSheet
import earth.cora.picnic.ui.add.AddUiState
import earth.cora.picnic.ui.add.SnapPhoto
import earth.cora.picnic.ui.aisles.AislesActions
import earth.cora.picnic.ui.aisles.AislesScreen
import earth.cora.picnic.ui.aisles.MyStoreSamples
import earth.cora.picnic.ui.album.AlbumActions
import earth.cora.picnic.ui.album.AlbumSamples
import earth.cora.picnic.ui.album.AlbumScreen
import earth.cora.picnic.ui.edit.EditItemSheetContent
import earth.cora.picnic.ui.edit.EditPage
import earth.cora.picnic.ui.edit.EditSamples
import earth.cora.picnic.ui.eggs.EggRig
import earth.cora.picnic.ui.eggs.completedFixture
import earth.cora.picnic.ui.eggs.holdLcd
import earth.cora.picnic.ui.list.ListActions
import earth.cora.picnic.ui.list.ListFixture
import earth.cora.picnic.ui.list.ListRig
import earth.cora.picnic.ui.list.ListScreen
import earth.cora.picnic.ui.list.ListScreenState
import earth.cora.picnic.ui.list.ListTags
import earth.cora.picnic.ui.list.TripBookSheet
import earth.cora.picnic.ui.list.motion.CELEBRATION_TAG
import earth.cora.picnic.ui.list.motion.NEXT_CARD_TAG
import earth.cora.picnic.ui.pantry.PantryActions
import earth.cora.picnic.ui.pantry.PantryScreen
import earth.cora.picnic.ui.pantry.PantrySamples
import earth.cora.picnic.ui.settings.LocationPermission
import earth.cora.picnic.ui.settings.SettingsActions
import earth.cora.picnic.ui.settings.SettingsSamples
import earth.cora.picnic.ui.settings.SettingsScreen
import earth.cora.picnic.ui.theme.CelebrationTags
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.TOAST_TAG
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/*
 * The screen-fixture walker (docs/DESIGN_STICKER.md §21 WP10 "Provides": the screen-fixture walker used by the audits).
 * Every screen and its main states, built from the existing fixtures (Appendix A's "Hillcrest Market" Saturday shop:
 * ListFixture, SaturdayShop, the screens' samples) and the existing rigs (ListRig: the real List destination with its
 * view models; EggRig: the real list over the Surprise lab's sandbox with WP9's engine). Each fixture brings its screen
 * to the state on a held test clock (no wall clock, bounded frames only) and leaves it settled for the audit to look at.
 * A sheet is its own surface (§2.7), so the sheets are shown as the app draws them, over the screen they open from where
 * that screen is part of them, or alone.
 */

/**
 * How a fixture is shown: its theme variant, its font scale, and whether that scale is the platform's (the phone's own
 * non-linear scaling, set by the test method's `@Config(fontScale = …)`) or linear (a [Density] override, as the
 * `*_fs130` / `*_fs200` goldens render), and reduced motion.
 */
data class Combo(
    val variant: ThemeVariant = ThemeVariant.Day,
    val fontScale: Float = 1f,
    val linear: Boolean = false,
    val reduced: Boolean = false,
) {
    /** The linear scale a rig should apply itself, or null when the platform's configuration carries the scale. */
    val linearScale: Float? get() = if (linear) fontScale else null

    override fun toString(): String =
        "${variant.name.lowercase()} fs${(fontScale * 100).toInt()}${if (linear && fontScale != 1f) " linear" else ""}${if (reduced) " rm" else ""}"
}

/**
 * The surfaces of §2.7's family budget ("DynaPuff + Zen Maru + one of Klee One / Hachi Maru Pop"; a bottom sheet is its
 * own surface), each with the one third family it may use, or none.
 */
enum class Surface(val third: String?) {
    // §2.7: "The list uses Hachi Maru (doodles)". The Next card and the toasts are the list's.
    List(KitFonts.HACHI_MARU),

    // §2.7: "the Add sheet uses Klee One (transcripts) and no doodles".
    AddSheet(KitFonts.KLEE_ONE),
    EditSheet(null),
    NewTripSheet(null),
    TripBookSheet(null),
    SwitcherSheet(null),

    // §2.7: the aisles text is Klee One 16 / 24 sp.
    MyStore(KitFonts.KLEE_ONE),

    // §6.13: the pantry as text, on ruled lines like the aisles text.
    Pantry(KitFonts.KLEE_ONE),
    Settings(null),

    // §12.2: the About page hints "tap the sun?" in Hachi Maru.
    About(KitFonts.HACHI_MARU),

    // The lab shows the practice list (the real List screen, with its doodles) on its page.
    Lab(KitFonts.HACHI_MARU),
    Album(null),

    // The finale is a scene of its own over the list (§11).
    Celebration(null),
}

/**
 * One screen in one state: [run] shows it on [FixtureStage] and brings it to the state. [fontText]: it shows words set in
 * a font (false only where every word on screen is the dot-matrix lettering, vector art like the LCD's, §2.7).
 */
class Fixture(val name: String, val surface: Surface, val fontText: Boolean = true, val run: FixtureStage.() -> Unit) {
    override fun toString(): String = name
}

/** Where a fixture is shown: the test's compose rule on a held clock, at [combo]. */
class FixtureStage(val compose: ComposeContentTestRule, val combo: Combo) {
    private val cleanups = ArrayList<() -> Unit>()

    /** Density (px per dp) of the screen. */
    val density: Float get() = compose.density.density

    /** Shows [content] themed at [combo] (the only setContent of the test), on a clock held from the first frame. */
    fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val base = LocalDensity.current
            val d = if (combo.linear) Density(base.density, combo.fontScale) else base
            CompositionLocalProvider(LocalDensity provides d) {
                PicnicTheme(variant = combo.variant, reducedMotion = combo.reduced) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
        frames(2)
        checkScale()
    }

    /** The font scale the screen really runs at is [combo]'s (a platform scale needs the test method's @Config). */
    fun checkScale() {
        val seen = compose.density.fontScale
        if (!combo.linear) assertEquals("the phone's font scale (set the test method's @Config(fontScale))", combo.fontScale, seen, 0.001f)
    }

    /** Shows [rig]'s real List destination. */
    fun showList(rig: ListRig) {
        cleanups += { rig.scope.cancel() }
        show { rig.Content() }
    }

    /** One frame on the held clock: posted work runs, snapshot changes apply, then the frame. */
    fun frames(n: Int) = repeat(n) {
        compose.waitForIdle()
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
    }

    /** About [ms] of frames. */
    fun advance(ms: Long) = frames(((ms + 15) / 16).toInt())

    /** Frames until [done] or [maxMs]; whether [done] came true (bounded: never hangs). */
    fun framesUntil(maxMs: Long, done: () -> Boolean): Boolean {
        var t = 0L
        while (t <= maxMs) {
            if (done()) return true
            frames(1)
            t += 16
        }
        return done()
    }

    /** Lets entrances and stepped poses settle (the longest entrance, a sheet's 280 ms, and a toast's drop, well within). */
    fun settle() {
        advance(900)
        compose.waitForIdle()
    }

    fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Runs when the fixture is done with (a rig's coroutines). */
    fun close() {
        cleanups.forEach { it() }
        cleanups.clear()
    }
}

/** The walker's fixtures: every screen and its main states. */
object ScreenFixtures {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The List screen a sheet opens over (the stateless screen at the fixture's mid-trip), as the app layers them. */
    @Composable
    private fun OverList(ui: earth.cora.picnic.ui.list.ListUiState = ListFixture.ui(), sheet: @Composable () -> Unit) {
        ListScreen(ui, ListActions())
        sheet()
    }

    internal fun listRig(state: earth.cora.picnic.core.model.AppState = ListFixture.state(), now: Long = ListFixture.NOW) =
        ListRig(state, now = now).inSeason()

    /** The stateless List screen at [state] (for states the route opens from her gestures: a peek, the menu, a scroll). */
    private fun FixtureStage.listScreen(screen: ListScreenState, ui: earth.cora.picnic.ui.list.ListUiState = ListFixture.ui(), list: LazyListState? = null) =
        show { ListScreen(ui, ListActions(), screen = remember { screen }, listState = list?.let { remember { it } }) }

    private val addBase = AddUiState(listId = AddFixtures.LIST_ID)
    private const val PANCAKES = "ingredients for pancakes from scratch"

    private fun FixtureStage.addSheet(state: AddUiState) = show { OverList { AddSheet(state, AddActions(onEditPantry = {})) } }

    val all: List<Fixture> = listOf(
        // ---- The List (§6.1) ----
        Fixture("list_empty", Surface.List) { showList(listRig(ListFixture.empty())); settle() },
        Fixture("list_fresh", Surface.List) { showList(listRig(ListFixture.fresh())); settle() },
        Fixture("list_midtrip", Surface.List) { showList(listRig()); settle() },
        // Appendix A's long names: the app's 14 default aisles, one or two things in each ("Pasta, rice & international").
        Fixture("list_long_names", Surface.List) { showList(listRig(ListFixture.everyDefaultAisle())); settle() },
        Fixture("list_done", Surface.List) {
            showList(listRig(ListFixture.doneWithReceipt(), now = ListFixture.TRIP_START + 44 * ListFixture.MIN))
            settle()
        },
        // The open aisle's plate pinned while its rows scroll under it, the book collapsed with the bar (§6.1, §7.4).
        Fixture("list_pinned", Surface.List) {
            val s = ListScreenState()
            val l = LazyListState()
            listScreen(s, ListFixture.ui(ListFixture.longAisle()), l)
            compose.onNodeWithTag(ListTags.LIST).performScrollToKey("header:household")
            frames(2)
            compose.runOnIdle {
                s.scrolledDown = true
                runBlocking { l.scrollBy(1.5f * 56f * density) }
            }
            settle()
        },
        // Scrolled to the end: the end note on its pad patch (§6.1), the bar and the book collapsed.
        Fixture("list_end", Surface.List) {
            showList(listRig())
            settle()
            compose.onNodeWithTag(ListTags.LIST).performScrollToKey("end-note")
            frames(4)
            settle()
            assertTrue("the end note shows", exists(ListTags.END_NOTE))
        },
        // A folded basket held open (§5.3): the peek note.
        Fixture("list_peek", Surface.List) { listScreen(ListScreenState(peek = "produce")); settle() },
        Fixture("list_menu", Surface.List) { listScreen(ListScreenState(menuOpen = true)); settle() },
        // Scrolled down: the add bar folded into the FAB, the bar and the book collapsed (§6.1, item 98).
        Fixture("list_fab", Surface.List) {
            showList(listRig(ListFixture.longAisle()))
            settle()
            compose.onNodeWithTag(ListTags.LIST).performTouchInput { swipeUp(startY = centerY + 120 * density, endY = top + 10 * density, durationMillis = 500) }
            settle()
            assertTrue("the FAB shows", exists(earth.cora.picnic.ui.add.ADD_FAB_TAG))
        },
        // A row torn out (§8.6): the slot closed and the note "McIntosh apples torn out" + Undo, held.
        Fixture("list_tearoff_toast", Surface.List) {
            showList(listRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce")))
            settle()
            compose.onNodeWithTag(ListTags.row("apples")).performTouchInput {
                down(center)
                repeat(4) { moveBy(Offset(-20f * density, 0f)) }
                up()
            }
            assertTrue("the note dropped in", framesUntil(3000) { exists(TOAST_TAG) })
            advance(600)
        },
        // Baking's last item: the aisle done, the Next card up and holding (§10).
        Fixture("nextcard", Surface.List) {
            showList(listRig())
            settle()
            compose.onNodeWithTag(ListTags.LIST).performScrollToKey("baking-powder")
            frames(4)
            compose.onNodeWithTag(ListTags.row("baking-powder")).performClick()
            assertTrue("the Next card rises", framesUntil(3000) { exists(NEXT_CARD_TAG) })
            advance(800)
        },
        // The trip's last item: the celebration, at its still summary (§11, §0.4 Q7).
        Fixture("celebration_summary", Surface.Celebration) {
            showList(listRig(ListFixture.allBut(listOf("toothpaste"), open = "household")))
            settle()
            compose.onNodeWithTag(ListTags.LIST).performScrollToKey("toothpaste")
            frames(4)
            compose.onNodeWithTag(ListTags.row("toothpaste")).performClick()
            assertTrue("the celebration shows", framesUntil(2600) { exists(CELEBRATION_TAG) })
            assertTrue("the summary holds", framesUntil(16_000) { exists(CelebrationTags.CLOSING_LABEL) })
            advance(400)
        },
        // Egg 18: the receipt printed from the LCD of a finished trip (§13.2), on the real list over the lab's sandbox.
        Fixture("receipt", Surface.List) {
            val r = EggRig(compose, variant = combo.variant, reduced = combo.reduced, fontScale = combo.linearScale, fixture = completedFixture(41)).start()
            checkScale()
            r.holdLcd(r.timeouts.holdThresholdMs + 20)
            r.advance(2_000)
            assertTrue("the receipt printed", r.eggs.receipt != null)
        },

        // ---- The sheets (§4.16): each its own surface ----
        Fixture("add_write", Surface.AddSheet) { addSheet(addBase.copy(sheetOpen = true, mode = AddMode.WRITE)); settle() },
        Fixture("add_thinking", Surface.AddSheet) {
            addSheet(
                addBase.copy(
                    sheetOpen = true, mode = AddMode.WRITE, origin = AddOrigin.SHEET, text = PANCAKES, sentText = PANCAKES,
                    phase = AddPhase.Thinking(ItemSource.AI_TEXT),
                ),
            )
            settle()
        },
        Fixture("add_recipe", Surface.AddSheet) {
            addSheet(
                addBase.copy(
                    sheetOpen = true, mode = AddMode.WRITE, text = PANCAKES, sentText = PANCAKES, phase = AddPhase.Preview,
                    review = AddFixtures.review(3, AddFixtures.pancakes(true), ItemSource.AI_TEXT, PANCAKES),
                ),
            )
            settle()
        },
        Fixture("add_error", Surface.AddSheet) {
            addSheet(
                addBase.copy(
                    sheetOpen = true, mode = AddMode.WRITE, text = PANCAKES, sentText = PANCAKES,
                    phase = AddPhase.Failed(
                        AddProblem.AI, message = earth.cora.picnic.core.llm.LlmError.Network(null).friendlyMessage,
                        retryable = true, canUsePlainLines = true,
                    ),
                ),
            )
            settle()
        },
        Fixture("add_snap", Surface.AddSheet) {
            addSheet(
                addBase.copy(
                    sheetOpen = true, mode = AddMode.SNAP, phase = AddPhase.Preview, photo = SnapPhoto(AddFixtures.paperPhoto(), AddFixtures.NOW),
                    review = AddFixtures.review(4, AddFixtures.photo(), ItemSource.PHOTO, null, revealed = true),
                ),
            )
            settle()
        },
        Fixture("add_speak", Surface.AddSheet) {
            addSheet(
                addBase.copy(
                    sheetOpen = true, mode = AddMode.SPEAK, phase = AddPhase.Preview, transcript = AddFixtures.TRANSCRIPT,
                    review = AddFixtures.review(5, AddFixtures.voice(), ItemSource.VOICE, AddFixtures.TRANSCRIPT),
                ),
            )
            settle()
        },
        Fixture("add_speak_recording", Surface.AddSheet) {
            addSheet(
                addBase.copy(
                    sheetOpen = true, mode = AddMode.SPEAK,
                    phase = AddPhase.Recording(elapsedMs = 12_400, level = 0.72f, levels = listOf(0.2f, 0.45f, 0.8f, 0.55f, 0.3f, 0.65f, 0.95f, 0.7f)),
                ),
            )
            settle()
        },
        Fixture("edit", Surface.EditSheet) {
            show { OverList { EditItemSheetContent(EditSamples.milk(), { _, _, _ -> }, {}, {}, {}, {}, {}) } }
            settle()
        },
        Fixture("edit_stickers", Surface.EditSheet) {
            show { OverList { EditItemSheetContent(EditSamples.milk(), { _, _, _ -> }, {}, {}, {}, {}, {}, initialPage = EditPage.Icons) } }
            settle()
        },
        Fixture("new_trip", Surface.NewTripSheet) { listScreen(ListScreenState(newTripOpen = true)); settle() },
        Fixture("trip_book", Surface.TripBookSheet) { listScreen(ListScreenState(tripBookOpen = true)); settle() },
        // The finished trip's book with its receipt read at full size (§7.8, §13.2).
        // The receipt full size over its scrim: its words are dot-matrix (§13.2), drawn as dots, not in a font.
        Fixture("trip_book_receipt", Surface.TripBookSheet, fontText = false) {
            val ui = ListFixture.ui(ListFixture.doneWithReceipt(), now = ListFixture.TRIP_START + 44 * ListFixture.MIN)
            show { OverList(ui) { TripBookSheet(book = ui.tripBook, dateLabel = ui.book!!.dateLabel, onClose = {}, initialFullReceipt = true) } }
            settle()
        },
        Fixture("switcher", Surface.SwitcherSheet) { show { ListScreen(ListFixture.ui(), ListActions(), showLists = true) }; settle() },

        // ---- The other screens ----
        Fixture("mystore", Surface.MyStore) { show { AislesScreen(MyStoreSamples.default, AislesActions()) }; settle() },
        Fixture("mystore_long_names", Surface.MyStore) { show { AislesScreen(MyStoreSamples.longNames, AislesActions()) }; settle() },
        Fixture("mystore_editing", Surface.MyStore) { show { AislesScreen(MyStoreSamples.default.copy(editing = "bakery"), AislesActions()) }; settle() },
        Fixture("pantry", Surface.Pantry) { show { PantryScreen(PantrySamples.few, PantryActions()) }; settle() },
        Fixture("pantry_empty", Surface.Pantry) { show { PantryScreen(PantrySamples.state(), PantryActions()) }; settle() },
        Fixture("settings", Surface.Settings) {
            show { SettingsScreen(SettingsSamples.ok, SettingsActions(), location = LocationPermission.Denied) }
            settle()
        },
        Fixture("settings_key_failed", Surface.Settings) {
            show { SettingsScreen(SettingsSamples.failed, SettingsActions(), location = LocationPermission.Denied) }
            settle()
        },
        Fixture("about", Surface.About) {
            show { AboutScreen(AboutUiState("0.4.0", Licences.load(context), labVisible = true, canUnlock = false), AboutActions()) }
            settle()
        },
        Fixture("lab", Surface.Lab) { show { SurpriseLabScreen(onBack = {}) }; settle() },
        Fixture("album", Surface.Album) { show { AlbumScreen(AlbumSamples.state(AlbumSamples.mockup), AlbumActions()) }; settle() },
        Fixture("album_early", Surface.Album) { show { AlbumScreen(AlbumSamples.state(AlbumSamples.early), AlbumActions()) }; settle() },
    )

    /** Every fixture, for ParameterizedRobolectricTestRunner. */
    fun params(): List<Array<Any>> = all.map { arrayOf<Any>(it) }

    /** The fixtures of the screens §20 item 281 names: List, Add, Edit, My store, My pantry, Settings, Album, Next, summary, receipt. */
    fun named(vararg names: String): List<Fixture> = names.map { n -> all.single { it.name == n } }
}
