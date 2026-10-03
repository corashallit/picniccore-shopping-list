package earth.cora.picnic.ui.list

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.snapshots.Snapshot
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.list
import earth.cora.picnic.ui.theme.LocalPieceProbe
import earth.cora.picnic.ui.theme.PieceProbe
import earth.cora.picnic.ui.theme.ThemeVariant

/** Collects where the list's pieces were laid out (ListPieces' probe hook): the latest bounds and every one seen. */
class RecordingProbe : PieceProbe {
    val bounds = HashMap<String, Rect>()
    val fonts = HashMap<String, Float>()
    val history = HashMap<String, MutableList<Rect>>()

    override fun report(key: String, bounds: Rect, fontSp: Float?) {
        this.bounds[key] = bounds
        if (fontSp != null) fonts[key] = fontSp
        history.getOrPut(key) { mutableListOf() } += bounds
    }

    override fun gone(key: String) {
        bounds.remove(key)
    }

    /** The keys laid out now that start with [prefix]. */
    fun keys(prefix: String): List<String> = bounds.keys.filter { it.startsWith(prefix) }.sorted()

    operator fun get(key: String): Rect = bounds[key] ?: error("no piece \"$key\" laid out (have ${bounds.keys.sorted()})")
}

/** Shows [rig]'s List destination (with [probe] when given, at [fontScale] when given) and waits for it to settle. */
fun ComposeContentTestRule.show(
    rig: ListRig,
    probe: RecordingProbe? = null,
    variant: ThemeVariant = ThemeVariant.Day,
    fontScale: Float? = null,
    reducedMotion: Boolean = false,
) {
    setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalPieceProbe provides probe,
            LocalDensity provides Density(base.density, fontScale ?: base.fontScale),
        ) { rig.Themed(variant, reducedMotion) }
    }
    waitForIdle()
}

/**
 * Runs [n] frames on the held clock (mainClock.autoAdvance = false): before each, the main looper's posted work runs
 * (the view models' coroutines on Dispatchers.Main; with the clock held this never advances time) and snapshot changes
 * are applied.
 */
fun ComposeContentTestRule.frames(n: Int) = repeat(n) {
    waitForIdle()
    Snapshot.sendApplyNotifications()
    mainClock.advanceTimeByFrame()
}

/** Runs frames on the held clock for about [ms] milliseconds. */
fun ComposeContentTestRule.advanceMs(ms: Long) = frames(((ms + 15) / 16).toInt())

/** Runs held frames until [done] is true or [maxMs] has passed; returns whether [done] came true (never hangs). */
fun ComposeContentTestRule.framesUntil(maxMs: Long, done: () -> Boolean): Boolean {
    var t = 0L
    while (t <= maxMs) {
        if (done()) return true
        frames(1)
        t += 16
    }
    return done()
}

/** The bounds in the root of the node tagged [tag]. */
fun ComposeContentTestRule.boundsOf(tag: String, unmerged: Boolean = false): Rect =
    onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().boundsInRoot

/** Whether a node tagged [tag] is in the tree. */
fun ComposeContentTestRule.exists(tag: String, unmerged: Boolean = false): Boolean =
    onAllNodesWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()

/** A node whose test tag starts with [prefix]. */
fun hasTagPrefix(prefix: String): SemanticsMatcher =
    SemanticsMatcher("test tag starts with $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }

/** The fixture list's items in [state]. */
fun AppState.items() = list(ListFixture.LIST_ID)!!.items

/** Whether [id] is checked in [state]. */
fun AppState.isChecked(id: String) = items().first { it.id == id }.checked
