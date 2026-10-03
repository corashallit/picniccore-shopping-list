package earth.cora.picnic.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.ListPresentation
import earth.cora.picnic.ui.list.motion.MotionClock
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant

/*
 * Helpers for WP5's component goldens (tabs, strips, trays, rows, baskets, the book): a piece rendered on the binder
 * ground with room for its shadows, captured by node so the golden is the piece, not a mostly empty phone.
 */

/** The captured node's tag. */
const val PIECE_TAG: String = "wp5_piece"

/**
 * Renders [content] on the binder ground in [variant] at [fontScale] (padding [pad] all round), lets stepped motion
 * settle for [settleMillis] on a frozen clock, and records `src/test/screenshots/[name].png` from the piece's node.
 */
fun ComposeContentTestRule.recordPiece(
    name: String,
    variant: ThemeVariant = ThemeVariant.Day,
    fontScale: Float? = null,
    pad: Dp = 12.dp,
    width: Dp? = null,
    settleMillis: Long = 800,
    before: ComposeContentTestRule.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale ?: base.fontScale)) {
            PicnicTheme(variant = variant) {
                Box(
                    Modifier
                        .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
                        .wrapContentHeight()
                        .testTag(PIECE_TAG),
                ) {
                    PicnicBackground(Modifier.matchParentSize()) {}
                    Box(Modifier.padding(pad)) { content() }
                }
            }
        }
    }
    waitForIdle()
    before()
    waitForIdle()
    mainClock.autoAdvance = false
    if (settleMillis > 0) mainClock.advanceTimeBy(settleMillis)
    waitForIdle()
    onNodeWithTag(PIECE_TAG).captureRoboImage("src/test/screenshots/$name.png")
}

/** No-op row actions for still pieces. */
internal val StillRowActions = RowActions(onToggle = {}, onDelete = {}, onEdit = {})

/** One open tray (band, rows, cap) as the list draws it, at rest, [current] or not. */
@Composable
internal fun TrayOf(section: SectionUi, current: Boolean = true, pinned: Boolean = false) {
    val clock = remember { MotionClock() }
    val anchors = remember { AnchorRegistry() }
    val focus = remember { FocusRequester() }
    Column {
        TrayHeader(section, null, clock, anchors, pinned = pinned, chevronFocus = focus, onFold = {}, current = current)
        section.rows.forEachIndexed { i, r ->
            TrayRow(r, section, first = i == 0, last = i == section.rows.lastIndex, presentation = ListPresentation.Identity, clock = clock, anchors = anchors, actions = StillRowActions, current = current)
        }
        TrayCap(section, current = current)
    }
}

/** Folded strips for [sections], 7 dp apart, at rest. */
@Composable
internal fun StripsOf(sections: List<SectionUi>, peeked: String? = null) {
    val clock = remember { MotionClock() }
    val anchors = remember { AnchorRegistry() }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        for (s in sections) {
            FoldedStrip(
                section = s,
                basket = null,
                aisle = null,
                clock = clock,
                anchors = anchors,
                focus = remember(s.id) { FocusRequester() },
                onOpen = {},
                onPeek = { _, _ -> },
                peeked = peeked == s.id,
            )
        }
    }
}
