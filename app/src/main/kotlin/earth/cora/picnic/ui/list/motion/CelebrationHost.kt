package earth.cora.picnic.ui.list.motion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import earth.cora.picnic.R
import earth.cora.picnic.ui.theme.CelebrationScene
import earth.cora.picnic.ui.theme.ForceDayBars
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant

/*
 * The celebration host (docs/DESIGN_STICKER.md §6.1 item 5, §11.3, §0.4 Q7): above everything but the system bars,
 * always in Day tokens (the bars switch to dark icons while it shows). Taps:
 * - before the summary, one tap anywhere closes it (§11.3);
 * - during the summary, a tap on the summary card (the pad card holding the stamp card and the banner, with the washi
 *   progress bar along its bottom edge) keeps it open: the bar goes and a Done button closes it; a tap anywhere else
 *   (the rays, the shelf, the picnic, the album corner) closes it at once; Back closes it at any moment.
 * TalkBack: the scene is one node, "Celebration", double-tap to close; no countdown, it waits (§11.3). Owner: WP6.
 */

/** The celebration host's test tag. */
const val CELEBRATION_TAG: String = "picnic_celebration"

@Composable
fun CelebrationHost(engine: MotionChoreographer, presentation: ListPresentation, modifier: Modifier = Modifier) {
    val cele = presentation.celebration ?: return
    val copy = rememberCelebrationCopy()
    val label = stringResource(R.string.motion_celebration)
    val closeLabel = stringResource(R.string.motion_celebration_close)
    var card by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val current by rememberUpdatedState(cele)
    BackHandler(enabled = cele.closingT0 == null) { engine.closeCelebration() }
    PicnicTheme(variant = ThemeVariant.Day) {
        ForceDayBars()
        Box(
            modifier
                .fillMaxSize()
                .testTag(CELEBRATION_TAG)
                .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
                .clearAndSetSemantics {
                    contentDescription = label
                    onClick(label = closeLabel) {
                        engine.closeCelebration()
                        true
                    }
                }
                .pointerInput(cele.t0) {
                    detectTapGestures { pos ->
                        val c = current
                        val summaryAt = c.t0 + (c.plan?.summaryAt ?: 0L)
                        val inSummary = engine.clock.now >= summaryAt
                        val onCard = card?.contains(pos + origin) == true
                        when {
                            !inSummary || c.talkBack -> engine.closeCelebration()
                            c.keptAt == null && onCard -> engine.keepCelebration()
                            c.keptAt != null && onCard -> Unit
                            else -> engine.closeCelebration()
                        }
                    }
                },
        ) {
            CelebrationScene(
                cele,
                engine.clock,
                copy,
                onSummaryCard = { card = it },
                onDone = { engine.closeCelebration() },
            )
        }
    }
}

/** The scene's copy from strings_motion.xml (the timeline counts the words of exactly these). */
@Composable
fun rememberCelebrationCopy(): CelebrationCopy {
    val res = LocalResources.current
    val title = stringResource(R.string.motion_list_complete)
    val skip = stringResource(R.string.motion_tap_to_skip)
    val perfect = stringResource(R.string.motion_perfect)
    val bonus = stringResource(R.string.motion_bonus_sticker)
    val closing = stringResource(R.string.motion_closing)
    val done = stringResource(R.string.motion_done)
    return remember(res, title, skip, perfect, bonus, closing, done) {
        CelebrationCopy(
            title = title,
            skip = skip,
            perfect = perfect,
            bonus = bonus,
            banner = { n, m -> res.getQuantityString(R.plurals.motion_banner, n, n, m) },
            closing = closing,
            done = done,
        )
    }
}
