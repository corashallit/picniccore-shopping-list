package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.platform.NoOpFeedback
import earth.cora.picnic.platform.ShakeEffect
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.MotionChoreographer
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import earth.cora.picnic.ui.theme.ToastHostState
import kotlin.math.abs

/*
 * The eggs' hand-off to a list screen (docs/DESIGN_STICKER.md §12, §19.2; §21 WP9 "overlay composables mounted in WP6's
 * overlay host"). WP5's ListScreen wraps its content in [EggListHost], as the Surprise lab's practice list does, so every
 * egg plays on her list exactly as it plays in the lab: the same code runs in both. The per-piece checklist (each anchor
 * key, each modifier and composable, and where it goes) is in SurpriseEngine.kt's header. Owner: WP9.
 */

/**
 * What [EggListHost] hands its content (the list screen): the eggs' state, the room the list keeps (measured from the
 * add bar), whether the add bar's tape moves along, the background wash and the overlay to mount in MotionOverlay's
 * `eggs` slot.
 */
@Stable
class EggListScope internal constructor(
    /** The engine (its hooks: onSunTap, onStampTap, onLcdHold, onBasketHold, interceptText, onCommitted, …). */
    val engine: SurpriseEngine,
    /** The list's anchor registry (MotionChoreographer.anchors). */
    val anchors: AnchorRegistry,
) {
    /** What the eggs show now. */
    var eggs: SurpriseState by mutableStateOf(SurpriseState.Off)
        internal set

    internal var density: Density = Density(1f)
    internal var barSize: IntSize by mutableStateOf(IntSize.Zero)

    /** The docked add bar's window bounds as last laid out (snapshot state: the umbrella follows it); null while gone. */
    internal var barBounds: Rect? by mutableStateOf(null)

    /** The docked add bar has folded into the FAB (it slides away, then leaves): the umbrella steps off with it. */
    internal var barFolded: Boolean by mutableStateOf(false)

    /** Moves whenever the list lays out anew: a reveal waiting for its row (eggs 6 and 13) looks again then. */
    internal var listLayouts: Int by mutableIntStateOf(0)
    internal val room = ListRoom()
    internal val rollHome = RollHome()
    internal val ladybugHome = LadybugHome(anchors)
    internal var overlay: @Composable BoxScope.() -> Unit = {}

    /**
     * The add bar, measured and followed: put it on the docked add bar (WP7's AddBar or AddDock), so the list's end
     * room, the toasts' inset and the umbrella's tape follow its real height and width at every font scale, and the
     * umbrella stands on the bar wherever it is (risen with the keyboard, grown with large text). [folded]: the bar has
     * folded into the FAB (the umbrella goes with it, and pops back when the bar returns).
     */
    fun Modifier.eggAddBar(folded: Boolean = false): Modifier = onSizeChanged { barSize = it } then EggBarElement(this@EggListScope, folded)

    /** The add bar's height as measured ([eggAddBar]; 84 dp, the bar at font scale 1, until it is). */
    val barHeight: Dp get() = if (barSize.height > 0) with(density) { barSize.height.toDp() } else DEFAULT_BAR

    /** Egg 16: the umbrella stands on the docked bar's shoulder (it rains and the bar is out, not folded into the FAB). */
    val umbrellaShown: Boolean get() = eggs.umbrella && !barFolded

    /** The bar the umbrella stands on (window px), or null when it does not show. */
    internal val umbrellaBar: Rect? get() = if (umbrellaShown) barBounds else null

    /** Egg 16: while the umbrella stands on the bar's shoulder, the list and the toasts keep clear of its canopy. */
    val umbrellaRoom: Dp get() = if (umbrellaShown) UMBRELLA_LIST_ROOM else 0.dp

    /**
     * The list's bottom content padding (§6.1): the add bar's height + 26 dp, + the umbrella's room while it rains, so the
     * last strip can always scroll wholly above the bar.
     */
    fun listBottomPadding(bar: Dp = barHeight): Dp = bar + LIST_END_GAP + umbrellaRoom

    /** MotionOverlay's `bottomInset` (the toasts sit 20 dp above it): the bar's height, + the umbrella's room. */
    fun overlayInset(bar: Dp = barHeight): Dp = bar + umbrellaRoom

    /**
     * Egg 18: while a printed receipt waits and hangs down into the list's room (large text, a short book), the list's
     * viewport starts this much lower, under the paper, so no row can scroll beneath it (an inset of the viewport, not
     * of its content). It eases in and out ([ListRoom.shown]).
     */
    val listTopExtra: Dp get() = with(density) { room.shown.toDp() }

    /** Egg 18: a printed receipt waits for its tap: the binder top and the book stay open under it (§13.2). */
    val receiptWaits: Boolean get() = eggs.receipt != null

    /**
     * The add bar's `tapeAfterDoodle` (§12.2 egg 16): while the umbrella stands at the bar's left end, the raindrop washi
     * moves along, after the doodle as the bar lays it out (none when the bar has no room for it there).
     */
    val tapeAfterDoodle: Boolean get() = eggs.umbrella

    /** MotionOverlay's `eggs` slot: every egg overlay (the nap cat and snack doodle are the list's own, in its content). */
    val overlays: @Composable BoxScope.() -> Unit get() = overlay

    /** Egg 1's wash: place it FIRST inside the list's PicnicBackground, beneath everything else. */
    @Composable
    fun BoxScope.Wash() = DuskWash(eggs)

    companion object {
        /** The bar at font scale 1 (§6.2), until it is measured. */
        val DEFAULT_BAR: Dp = 84.dp

        /** The room after the bar's top that the list's last entry keeps (§6.1). */
        val LIST_END_GAP: Dp = 26.dp
    }
}

/**
 * Mounts the eggs on a list screen (§12, §19.2). Wrap the list screen's content in it; [content] places the pieces the
 * [EggListScope] hands it. It does, for every list that shows [listId]:
 * - `onListShown` / `onListHidden` as the list comes and goes (the screen, a list switch, the app stopping and
 *   starting): SNACK? on entry, the forecast check, the receipt's auto-tuck, the bonus stage ending, the ants on open;
 * - the shake detector (egg 4) and the keyboard's visibility (egg 17 never marches while it is open; [imeVisible] is the
 *   test hook, the phone reads WindowInsets.isImeVisible);
 * - [LocalRollHome] (egg 3 rolls home along its own line, beneath the row's words), [LocalLadybugHome] (egg 2 walks
 *   along its strip, drawn by the strip) and [LocalListRoom] (egg 18);
 * - the scrolls that bring a scene into the list's room, the viewport down to the add bar's top, at any font scale: the
 *   rolling item's aisle and then its row (egg 3), the basket the nap cat sleeps in (egg 8), the frozen aisle's tab
 *   (egg 5), the top while a printed receipt needs room (egg 18). [indexOfSection] gives a section's first lazy index;
 *   an aisle scrolled to sits [peek] under the list's top.
 * The overlay ([EggListScope.overlays]) shows the eggs' toasts in [toasts], forwards [motion]'s events (the pet chomps)
 * and plays [feedback]'s cues; [onOpenAlbum] is the back pocket toast's "Look" (the album at its back pocket, §12.2).
 * [listPlacesDecor] false draws the nap
 * cat and the snack doodle in the overlay instead of the list's content.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EggListHost(
    engine: SurpriseEngine,
    listId: String,
    anchors: AnchorRegistry,
    listState: LazyListState,
    indexOfSection: (String) -> Int,
    toasts: ToastHostState? = null,
    motion: MotionChoreographer? = null,
    feedback: Feedback = NoOpFeedback,
    onOpenAlbum: () -> Unit = {},
    peek: Dp = 4.dp,
    listPlacesDecor: Boolean = true,
    imeVisible: Boolean = WindowInsets.isImeVisible,
    /**
     * The Surprise lab's practice list brings the nap cat's basket and the frozen aisle into view as their scenes start
     * (§6.9); her own list never scrolls by itself for them (§12.1: an egg never moves her from where she is). Egg 3's
     * scroll to the rolling item's aisle is the spec's own and runs on every list.
     */
    sceneScrolls: Boolean = false,
    content: @Composable EggListScope.() -> Unit,
) {
    val eggs by engine.state.collectAsState()
    val scope = remember(engine, anchors) { EggListScope(engine, anchors) }
    val density = LocalDensity.current
    scope.eggs = eggs
    scope.density = density
    val openAlbum by rememberUpdatedState(onOpenAlbum)
    scope.overlay = remember(engine, anchors, toasts, motion, feedback, listPlacesDecor) {
        {
            EggOverlays(
                engine, anchors, toasts, motion, feedback, onOpenAlbum = { openAlbum() }, listPlacesDecor = listPlacesDecor,
                // Read as snapshot state: the umbrella follows the bar as it slides, rises with the keyboard or grows.
                addBar = { scope.umbrellaBar },
                listLayouts = { scope.listLayouts },
            )
        }
    }
    LifecycleStartEffect(engine, listId) {
        engine.onListShown(listId)
        onStopOrDispose { engine.onListHidden() }
    }
    // Egg 4 never runs under reduced motion (§12.3), so the accelerometer is not even listened to then.
    ShakeEffect(enabled = eggs.enabled && !LocalReducedMotion.current) { engine.onShake() }
    LaunchedEffect(engine, imeVisible) { engine.onImeVisible(imeVisible) }
    LaunchedEffect(listState) { snapshotFlow { listState.layoutInfo }.collect { scope.listLayouts++ } }

    val index by rememberUpdatedState(indexOfSection)
    val peekPx = with(density) { peek.roundToPx() }
    val gapPx = with(density) { ROOM_GAP.toPx() }
    // A toast resting above the bar covers the list's room too: a scene lands clear of it.
    val toastTop: () -> Float? = { toasts?.takeIf { it.current != null }?.restingTop }
    // Egg 3: the list scrolls to the rolling item's aisle (§12.2), then brings its row (or its folded strip) wholly above
    // the bar; once the roll has settled it goes back to where she was (she is mid-shop in another aisle), unless she
    // scrolled meanwhile. The way back outlives the roll's own state (cleared as it settles), so it runs in [hostScope].
    val rolling = eggs.rolling
    val hostScope = rememberCoroutineScope()
    val rollBack = remember { arrayOfNulls<Job>(1) }
    LaunchedEffect(rolling?.id) {
        val r = rolling ?: return@LaunchedEffect
        val i = index(r.sectionId)
        if (i < 0) return@LaunchedEffect
        rollBack[0]?.cancel()
        val startedAt = withFrameMillis { it }
        val backIndex = listState.firstVisibleItemIndex
        val backOffset = listState.firstVisibleItemScrollOffset
        bringIntoRoom(listState, anchors, i, peekPx, gapPx, plateIndex = i, scrollFirst = { true }, cover = toastTop) {
            anchors.bounds(EggAnchors.line(r.itemId))?.let { it to true } ?: anchors.bounds(EggAnchors.strip(r.sectionId))?.let { it to false }
        }
        val leftIndex = listState.firstVisibleItemIndex
        val leftOffset = listState.firstVisibleItemScrollOffset
        if (leftIndex == backIndex && leftOffset == backOffset) return@LaunchedEffect
        val wait = RollingMotion.DURATION + ROLL_BACK_PAUSE_MS - (withFrameMillis { it } - startedAt)
        rollBack[0] = hostScope.launch {
            // Any scroll while the roll plays is hers (the egg's own is over): she has moved on, so the list stays.
            val scrolled = withTimeoutOrNull(wait.coerceAtLeast(0L)) { snapshotFlow { listState.isScrollInProgress }.first { it } } != null
            val still = listState.firstVisibleItemIndex == leftIndex && abs(listState.firstVisibleItemScrollOffset - leftOffset) <= gapPx
            if (!scrolled && still) listState.animateScrollToItem(backIndex, backOffset)
        }
    }
    // The receipt's room under the paper eases in and out (instant under reduced motion).
    val reduced = LocalReducedMotion.current
    LaunchedEffect(scope.room) {
        androidx.compose.runtime.snapshotFlow { scope.room.receiptShift }.collect { target ->
            if (reduced || scope.room.shownAnim.value == target) {
                scope.room.shownAnim.snapTo(target)
            } else {
                // In step with the feed as the paper prints; back over 220 ms once the poof is over (§13.2).
                val spec = if (target < scope.room.shownAnim.value) {
                    tween<Float>(ROOM_RELEASE_MS, easing = PicnicMotion.standard.easing)
                } else {
                    // Steady steps, each done before the paper's next one.
                    tween(ROOM_EASE_MS, easing = androidx.compose.animation.core.LinearEasing)
                }
                withContext(FixedMotionScale) { scope.room.shownAnim.animateTo(target, spec) }
            }
        }
    }
    // Egg 8: the basket the cat sleeps in, with its strip, wholly above the bar (two strips before it in view, room
    // allowing): in the Surprise lab only (sceneScrolls).
    LaunchedEffect(eggs.napSectionId, sceneScrolls) {
        if (!sceneScrolls) return@LaunchedEffect
        val nap = eggs.napSectionId ?: return@LaunchedEffect
        val i = index(nap)
        if (i < 0) return@LaunchedEffect
        bringIntoRoom(listState, anchors, (i - CONTEXT_STRIPS).coerceAtLeast(0), if (i >= CONTEXT_STRIPS) peekPx else 0, gapPx, plateIndex = -1, cover = toastTop) {
            (anchors.bounds(EggAnchors.strip(nap)) ?: anchors.bounds(AnchorRegistry.basketKey(nap)))?.let { it to false }
        }
    }
    // Egg 5: the frozen aisle, where the snack star and doodle sit by the tab: in the Surprise lab only (sceneScrolls).
    LaunchedEffect(eggs.snackSectionId, sceneScrolls) {
        if (!sceneScrolls) return@LaunchedEffect
        val snack = eggs.snackSectionId ?: return@LaunchedEffect
        val i = index(snack)
        if (i < 0) return@LaunchedEffect
        bringIntoRoom(listState, anchors, (i - CONTEXT_STRIPS).coerceAtLeast(0), if (i >= CONTEXT_STRIPS) peekPx else 0, gapPx, plateIndex = -1, cover = toastTop) {
            (anchors.bounds(EggAnchors.strip(snack)) ?: anchors.bounds(EggAnchors.tab(snack)))?.let { it to false }
        }
    }
    // The Surprise lab's scenes on a row (the cake's check-off, the shiny pull, the jackpot): its row, wholly above the
    // add bar, its aisle first when the row is not on screen (§6.9, at any font scale).
    val focus = LocalSceneFocus.current()
    LaunchedEffect(focus) {
        val f = focus ?: return@LaunchedEffect
        val i = index(f.sectionId)
        if (i < 0) return@LaunchedEffect
        bringIntoRoom(listState, anchors, i, peekPx, gapPx, plateIndex = i, cover = toastTop) {
            f.itemId?.let { id -> anchors.bounds(EggAnchors.line(id))?.let { it to true } }
                ?: anchors.bounds(EggAnchors.strip(f.sectionId))?.let { it to false }
        }
    }

    CompositionLocalProvider(LocalRollHome provides scope.rollHome, LocalLadybugHome provides scope.ladybugHome, LocalListRoom provides scope.room) {
        scope.content()
    }
}

/**
 * The docked add bar's place, as snapshot state ([EggListScope.barBounds]): recorded on every placement, forgotten when
 * the bar leaves composition (folded into the FAB), with whether it is folding ([EggListScope.barFolded]).
 */
private class EggBarElement(private val scope: EggListScope, private val folded: Boolean) : ModifierNodeElement<EggBarNode>() {
    override fun create(): EggBarNode = EggBarNode(scope, folded)

    override fun update(node: EggBarNode) = node.update(scope, folded)

    override fun equals(other: Any?): Boolean = other is EggBarElement && other.scope === scope && other.folded == folded

    override fun hashCode(): Int = System.identityHashCode(scope) * 31 + folded.hashCode()
}

private class EggBarNode(private var scope: EggListScope, private var folded: Boolean) : Modifier.Node(), GlobalPositionAwareModifierNode {
    override fun onAttach() {
        scope.barFolded = folded
    }

    fun update(scope: EggListScope, folded: Boolean) {
        this.scope = scope
        this.folded = folded
        if (isAttached) scope.barFolded = folded
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        scope.barBounds = coordinates.boundsInWindow()
    }

    override fun onDetach() {
        scope.barBounds = null
    }
}

/** The receipt's room eases in and out over this long (ms). */
/** Each step of the receipt's room eases in within one step of its feed (121 ms). */
private const val ROOM_EASE_MS = 100

/** The list takes back the receipt's room over 220 ms once the poof is over. */
private const val ROOM_RELEASE_MS = 220

/** A strip scene (the nap, the snack) keeps this many entries before it in view when the list scrolls to it. */
private const val CONTEXT_STRIPS = 2

/** Egg 3: the list goes back to where she was this long after the roll has settled. */
private const val ROLL_BACK_PAUSE_MS = 300L

/** The list's room ends this far above the add bar's top (the scenes are brought in clear of the bar's fade). */
private val ROOM_GAP: Dp = 6.dp

/**
 * Brings a scene wholly into the list's room: the list's viewport (`list` anchor) down to the add bar's top (`addbar`
 * anchor), less [gapPx], at any font scale. [target] reads the scene's window bounds after layout, and whether it is a
 * row of the open tray whose header is the lazy item [plateIndex] (the header pins over the rows as the plate, so the
 * room starts under it). A target out of the room (or not composed, or [scrollFirst]) first brings the lazy item
 * [scrollTo] to the top,
 * [scrollOffsetPx] under it (the aisle, or a couple of strips before a strip, for context); a target still out of the
 * room then moves the least that brings it in: its bottom to the room's bottom, or its top to the room's top (a target
 * taller than the room is aligned to its top). Bounded: two scrolls at most.
 */
@OptIn(ExperimentalFoundationApi::class)
internal suspend fun bringIntoRoom(
    listState: LazyListState,
    anchors: AnchorRegistry,
    scrollTo: Int,
    scrollOffsetPx: Int,
    gapPx: Float,
    plateIndex: Int,
    /** True (read after layout): scroll to [scrollTo] first even when the target is already in the room. */
    scrollFirst: () -> Boolean = { false },
    /** The window y of what covers the list above the bar (a toast resting there), or null. */
    cover: () -> Float? = { null },
    target: () -> Pair<Rect, Boolean>?,
) {
    fun room(underPlate: Boolean): Rect? {
        val list = anchors.bounds(EggAnchors.LIST) ?: return null
        val bar = anchors.bounds(AnchorRegistry.ADD_BAR)
        val bottom = minOf(list.bottom, bar?.top ?: list.bottom, cover() ?: list.bottom) - gapPx
        val info = listState.layoutInfo
        val plate = if (underPlate) info.visibleItemsInfo.firstOrNull { it.index == plateIndex } else null
        val top = if (plate == null) list.top else maxOf(list.top, list.top + info.beforeContentPadding + plate.offset + plate.size)
        return Rect(list.left, top, list.right, bottom)
    }
    fun Rect.within(r: Rect): Boolean = top >= r.top - 1f && bottom <= r.bottom + 1f
    // The frame the change lands on is laid out first (an aisle opening, a row arriving).
    withFrameNanos { }
    withFrameNanos { }
    val first = target()
    val r0 = room(first?.second == true) ?: return
    if (first != null && first.first.within(r0) && !scrollFirst()) return
    listState.animateScrollToItem(scrollTo, scrollOffsetPx)
    withFrameNanos { }
    withFrameNanos { }
    val (t, underPlate) = target() ?: return
    val r = room(underPlate) ?: return
    if (t.within(r)) return
    val delta = when {
        t.height > r.height -> t.top - r.top
        t.bottom > r.bottom -> t.bottom - r.bottom
        else -> t.top - r.top
    }
    if (abs(delta) > 1f) listState.animateScrollBy(delta)
}
