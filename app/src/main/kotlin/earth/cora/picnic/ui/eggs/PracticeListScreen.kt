package earth.cora.picnic.ui.eggs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import earth.cora.picnic.R
import earth.cora.picnic.core.model.NewTripMode
import earth.cora.picnic.ui.add.AddDock
import earth.cora.picnic.ui.add.AddEventsEffect
import earth.cora.picnic.ui.add.AddRules
import earth.cora.picnic.ui.add.AddSheetHost
import earth.cora.picnic.ui.add.rememberAddFlows
import earth.cora.picnic.ui.add.toast
import earth.cora.picnic.platform.AddSources
import earth.cora.picnic.ui.list.ListActions
import earth.cora.picnic.ui.list.ListScreen
import earth.cora.picnic.ui.list.TearToasts
import earth.cora.picnic.ui.list.buildListUiState
import earth.cora.picnic.ui.list.motion.FinaleKeeper
import earth.cora.picnic.ui.list.rememberListScreenState
import earth.cora.picnic.ui.theme.ToastMessage

/*
 * The Surprise lab's practice list (docs/DESIGN_STICKER.md §6.9: "rendered by the real List screen"): WP5's ListScreen
 * over the sandbox. Its state is built by the list's own mapping (buildListUiState, with the eggs' hankos, pixel
 * stickers, holo stamps and season) from the sandbox's store; its taps go to the practice model (the stand-in for
 * ListViewModel: check-offs, opens, tear-offs, New trip) and to the sandbox's SurpriseEngine; its chains play on the
 * sandbox's MotionChoreographer and its sticky notes in the sandbox's toast host; its add bar is WP7's add flows over the
 * sandbox (an offline reader: no network, no cost). The eggs mount through the screen's own EggListHost, so every egg
 * plays here exactly as it plays on her list. Owner: WP9.
 */

/**
 * A scene the lab asks the list to bring into its room (the cake's row before its check-off, the shiny row, the jackpot
 * row): EggListHost scrolls it wholly above the add bar. Null outside the lab.
 */
val LocalSceneFocus = staticCompositionLocalOf<() -> PracticeModel.Focus?> { { null } }

/**
 * WP5's real List screen drawing [sandbox] (§6.9). [talkBack], [imeVisible] and [listState] are test hooks (null: the
 * phone's own).
 * The overflow menu's other screens (My store, My pantry, the album, Settings, the list switcher) are her own and never
 * the sandbox's, so in the lab they say so in a sticky note instead of leaving it.
 */
@Composable
fun PracticeListScreen(
    sandbox: SurpriseLabSandbox,
    modifier: Modifier = Modifier,
    talkBack: Boolean? = null,
    imeVisible: Boolean? = null,
    /** The list's scroll (tests drive it as her thumb would); null: the list keeps its own. */
    listState: androidx.compose.foundation.lazy.LazyListState? = null,
    /**
     * The lab brings the nap cat's basket and the frozen aisle into view as their scenes start (§6.9); false runs the
     * list as her own list runs (ListRoute: no scroll of its own for them; a test hook).
     */
    sceneScrolls: Boolean = true,
) {
    val model = sandbox.model
    val engine = sandbox.engine
    val catalog = model.catalog
    val app by sandbox.store.states.collectAsState()
    val eggs by engine.state.collectAsState()
    val settings by sandbox.settings.settings.collectAsState(initial = earth.cora.picnic.settings.AppSettings(eggs = true))
    val album by sandbox.album.album.collectAsState(initial = null)
    SideEffect { model.albumNow = album }
    // The Next card's and TalkBack's name for "Everything else".
    val everythingElse = stringResource(R.string.egg_practice_everything_else)
    SideEffect { model.everythingElse = everythingElse }
    val motion = model.motion
    // The finale's state (finaleShownAt, the bonus sticker) on the practice copy, on the sandbox's clock.
    LaunchedEffect(motion) {
        val keeper = FinaleKeeper(model.store, model.album, model::now)
        motion.events.collect { keeper.handle(it) }
    }
    val resources = LocalResources.current
    val addVm = remember(sandbox) { sandbox.addViewModel(resources::getString) }
    val flows = rememberAddFlows(addVm, sources = NO_CAPTURE)
    val addState by addVm.state.collectAsState()
    val screen = rememberListScreenState()
    val toasts = model.toasts
    // The add bar's commits: the rows pop, the sticky note with its Undo (WP7's words).
    AddEventsEffect(flows, toasts, onCommitted = { commit -> screen.pop(commit) })
    // The lab's own adds (eggs 3, 6, 9, 17): the same note and pops.
    LaunchedEffect(model, resources) {
        model.commits.collect { commit ->
            screen.pop(commit)
            toasts.show(commit.toast(resources) { model.undo(commit) })
        }
    }
    val tears = remember(model, resources) { TearToasts(resources) { all -> all.asReversed().forEach { model.restore(it.item, it.index) } } }
    val notHere = stringResource(R.string.egg_lab_not_here)
    val ui = remember(app, eggs, settings) {
        buildListUiState(
            state = app,
            busy = emptySet(),
            hasToken = false,
            editingId = null,
            catalog = catalog,
            now = sandbox.clock.now(),
            eggs = eggs.enabled,
            season = eggs.season.takeIf { eggs.enabled },
            bonusStage = eggs.pixelStickers,
            perfectWeek = eggs.enabled && eggs.perfectWeek,
            hankoFor = { eggs.hankoFor(it, catalog) },
        ).copy(nextStopCard = settings.nextStopCard, celebration = settings.celebration)
    }
    val note by rememberUpdatedState(notHere)
    val actions = remember(sandbox, flows, tears) {
        val elsewhere = { toasts.show(ToastMessage(note, kind = "lab")) }
        ListActions(
            onToggle = { id -> model.toggle(id) },
            onDelete = { id -> model.delete(id)?.let { toasts.show(tears.toast(it)) } },
            onOpenSection = { id -> model.open(id) },
            onFoldAll = { model.open("") },
            onStartList = flows::focusField,
            onNewTrip = { mode: NewTripMode -> sandbox.newTrip(mode) },
            onOpenAisles = elsewhere,
            onOpenPantry = elsewhere,
            onOpenAlbum = elsewhere,
            onOpenSettings = elsewhere,
            onOpenLists = elsewhere,
            onSunTap = engine::onSunTap,
            onMoonTap = engine::onMoonTap,
            onStampTap = engine::onStampTap,
            onLcdHold = engine::onLcdHold,
            onBasketHold = engine::onBasketHold,
            onCue = sandbox.feedback::cue,
        )
    }
    CompositionLocalProvider(LocalSceneFocus provides { model.focus }) {
        ListScreen(
            state = ui,
            actions = actions,
            modifier = modifier,
            engine = motion,
            toasts = toasts,
            screen = screen,
            listState = listState,
            dockHeight = flows.dockHeight,
            collapse = flows.collapse,
            dock = { bar ->
                AddDock(
                    flows, addState,
                    tape = AddRules.addBarTape(eggs),
                    // Egg 16: the umbrella stands at the bar's left end; the washi moves along after the doodle.
                    tapeAfterDoodle = eggs.umbrella,
                    // The practice copy has no camera or microphone: Snap, Choose a photo and Speak are off here.
                    captureEnabled = false,
                    modifier = bar,
                )
            },
            surprises = engine,
            feedback = sandbox.feedback,
            imeVisible = imeVisible,
            talkBackOverride = talkBack,
            sceneScrolls = sceneScrolls,
        )
    }
    // The Write sheet (a longer note opens its review here, on the sandbox).
    AddSheetHost(flows, addState, onOpenSettings = {}, onEditPantry = null)
}

/** The practice add bar's capture sources: never used (its Snap and Speak are off). */
private val NO_CAPTURE = AddSources(takePhoto = {}, pickPhoto = {}, recordVoice = {})
