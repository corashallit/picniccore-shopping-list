package earth.cora.picnic.ui.list

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
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

    operator fun get(key: String): Rect = bounds[key] ?: error("no piece \"$key\" laid out (have ${bounds.keys.sorted()})")
}

/** Shows [rig]'s List destination (with [probe] when given) and waits for it to settle. */
fun ComposeContentTestRule.show(rig: ListRig, probe: RecordingProbe? = null, variant: ThemeVariant = ThemeVariant.Day) {
    setContent {
        CompositionLocalProvider(LocalPieceProbe provides probe) { rig.Themed(variant) }
    }
    waitForIdle()
}

/** A node whose test tag starts with [prefix]. */
fun hasTagPrefix(prefix: String): SemanticsMatcher =
    SemanticsMatcher("test tag starts with $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }

/** The fixture list's items in [state]. */
fun AppState.items() = list(ListFixture.LIST_ID)!!.items

/** Whether [id] is checked in [state]. */
fun AppState.isChecked(id: String) = items().first { it.id == id }.checked
