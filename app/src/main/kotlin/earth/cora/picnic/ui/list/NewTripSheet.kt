package earth.cora.picnic.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.list.motion.BookPresentation
import earth.cora.picnic.ui.list.motion.MotionClock
import earth.cora.picnic.ui.theme.BookLayout
import earth.cora.picnic.ui.theme.LetterSetSheet
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.StickerBook
import kotlin.math.roundToInt

/*
 * The New trip sheet (docs/DESIGN_STICKER.md §6.5, §0.4 Q2): "Start a new trip?", the closing book drawn small (its
 * stickers, PERFECT if earned), "Today's sticker book goes into your album.", primary "Clear what I got", secondary
 * "Keep everything, uncheck it all", text button "Not yet". It never opens by itself: the first check-off after 12 h
 * starts a new book on its own instead (§0.4 Q9). Owner: WP5 (NewTripTest).
 */

object NewTripTags {
    const val SHEET: String = "picnic_new_trip_sheet"
    const val BOOK: String = "picnic_new_trip_book"
}

/** Lays the content out at its own size and draws it at [scale] from its top centre, taking only the scaled room. */
private fun Modifier.drawnSmall(scale: Float): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0))
    val w = (p.width * scale).roundToInt()
    val h = (p.height * scale).roundToInt()
    layout(w, h) {
        p.placeWithLayer((w - p.width) / 2, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
    }
}

@Composable
fun NewTripSheet(
    visible: Boolean,
    book: BookUi?,
    onClear: () -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    Box(modifier.fillMaxSize()) {
        LetterSetSheet(visible = visible, onDismissRequest = onDismiss, modifier = Modifier.testTag(NewTripTags.SHEET)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.list_new_trip_title),
                    style = t.cardTitle.copy(color = c.ink),
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                if (book != null) {
                    // The closing book drawn small (§6.5): the very book on the list (laid out at the list's width, with
                    // its pages and its stickers where she saw them), scaled down to fit, still and read-only.
                    val clock = remember { MotionClock() }
                    val listBook = BookLayout.widthFor(LocalConfiguration.current.screenWidthDp.toFloat()).dp
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp).decorative(), contentAlignment = Alignment.Center) {
                        val scale = (maxWidth / listBook).coerceAtMost(1f)
                        // Its date tag keeps its own size, so the date is never drawn under 13 sp (§2.7).
                        androidx.compose.runtime.CompositionLocalProvider(earth.cora.picnic.ui.theme.LocalBookDrawnScale provides scale) {
                            StickerBook(
                                book, BookPresentation.Identity, clock, width = listBook,
                                modifier = Modifier.drawnSmall(scale).testTag(NewTripTags.BOOK),
                                readOnly = true,
                            )
                        }
                    }
                }
                Text(stringResource(R.string.list_new_trip_body), style = t.body.copy(color = c.ink), modifier = Modifier.fillMaxWidth())
                PicnicButton(stringResource(R.string.list_new_trip_clear), onClick = onClear, leadingStar = true)
                PicnicButton(stringResource(R.string.list_new_trip_keep), onClick = onKeep, style = PicnicButtonStyle.Secondary)
                PicnicButton(stringResource(R.string.list_new_trip_not_yet), onClick = onDismiss, style = PicnicButtonStyle.Text)
            }
        }
    }
}
