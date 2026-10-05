package earth.cora.picnic.ui.add

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import earth.cora.picnic.ui.a11y.Decorative
import earth.cora.picnic.ui.a11y.focusRingIndication
import earth.cora.picnic.ui.a11y.textOverflowed
import earth.cora.picnic.ui.theme.DayColors
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.StickerSpot
import earth.cora.picnic.ui.theme.hardShadow
import earth.cora.picnic.ui.theme.rememberPressFraction
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * Small pieces the add flows share (docs/DESIGN_STICKER.md §4.4, §4.6, §4.7, §4.11, §4.17, §15; ARCHITECTURE §11: no
 * Material defaults). Every control here draws its own pressed state and passes `indication = null` (or the focus ring
 * alone, `focusRingIndication`, §15), so no Material ripple ever shows in the bar or the sheet (AddNoRippleTest scans
 * this package). Owner: WP7.
 */

/**
 * The hanko red of the add flows' stickers (the cassette's stop and record stickers, the REC dots). They are stickers
 * with a white die-cut ring, and stickers keep their colours in every variant (§2.2), so this is the Day `hanko` in Glow
 * too, as the GOT IT stamp's paper is.
 */
val StickerHanko: Color = DayColors.hanko

/**
 * The pressed state of a row, a cell or a tab (§4.4, §4.11): a highlighter band behind the text area only (left −4,
 * right −2, top and bottom 3 dp outside it), rounded like a marker stroke (radii 3, 10, 4, 12 from the top left) and
 * rotated −0.6°, in `pressedBand` (Glow `#3A438A`). The text never moves.
 */
fun Modifier.pressedBand(pressed: Boolean, color: Color): Modifier = drawBehind {
    if (!pressed) return@drawBehind
    val left = -4.dp.toPx()
    val top = -3.dp.toPx()
    val right = size.width + 2.dp.toPx()
    val bottom = size.height + 3.dp.toPx()
    val band = Path().apply {
        addRoundRect(
            RoundRect(
                left, top, right, bottom,
                topLeftCornerRadius = CornerRadius(3.dp.toPx()),
                topRightCornerRadius = CornerRadius(10.dp.toPx()),
                bottomRightCornerRadius = CornerRadius(4.dp.toPx()),
                bottomLeftCornerRadius = CornerRadius(12.dp.toPx()),
            ),
        )
    }
    rotate(-0.6f, Offset((left + right) / 2f, (top + bottom) / 2f)) { drawPath(band, color) }
}

/**
 * A kiss-cut label chip that is a button (§4.7: the "Choose a photo" chip, the source chips): a ≥32 dp pastel face in a
 * 48 dp target, 3 dp white border, hs; Zen Maru 700 13.5 sp ballpoint with a leading glyph. The face grows with the text
 * and the label wraps instead of clipping (§15). Pressed: the face drops onto its shadow (§4.6 "pressed flat").
 */
@Composable
internal fun AddChip(
    text: String,
    glyph: PicnicGlyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = Families.ramune.pastel,
    glyphSize: Dp = 16.dp,
) {
    val c = PicnicTheme.colors
    val shadows = PicnicTheme.shadows
    val shape = PicnicTheme.shapes.pill
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPressFraction(pressed)
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .clickable(interaction, indication = focusRingIndication(shape), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = 32.dp)
                .hardShadow(shape, shadows.color, shadows.hs)
                .offset(shadows.hs.x * press, shadows.hs.y * press)
                .clip(shape)
                .background(c.stickerWhite)
                .padding(PicnicTheme.dimens.border)
                .clip(shape)
                .background(fill)
                .padding(horizontal = 11.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Glyph(glyph, null, tint = c.ballpoint, size = glyphSize)
            Text(text, style = PicnicTheme.type.label.copy(color = c.ballpoint))
        }
    }
}

/**
 * The keep spot's picture (§4.17): the 30 dp dashed spot in a 48 dp box; kept = a 30 dp lemon star pressed on in the
 * book's magic-appear poses (0.3 → 1.3 → 0.92 → 1, 320 ms; a fade under reduced motion). Not a control of its own:
 * the row or cell around it is the one Role.Switch, so a tap anywhere on it toggles and no second node is announced.
 * The tray cells use a small one on the sticker's corner ([box], [spot], [star]).
 */
@Composable
internal fun KeepSpot(checked: Boolean, modifier: Modifier = Modifier, box: Dp = 48.dp, spot: Dp = 30.dp, star: Dp = 30.dp) {
    val reduced = LocalReducedMotion.current
    val pop = remember { Animatable(1f) }
    var first by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(checked) {
        // The star that is already on when the row first shows does not pop.
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (!checked) return@LaunchedEffect
        withContext(FixedMotionScale) {
            if (reduced) {
                pop.snapTo(0f)
                pop.animateTo(1f, tween(PicnicMotion.rmFade.durationMillis))
            } else {
                for (s in KEEP_POSES) {
                    pop.snapTo(s)
                    delay(KEEP_POSE_MS)
                }
                pop.snapTo(1f)
            }
        }
    }
    Box(modifier.size(box).clearAndSetSemantics { this[Decorative] = Unit; testTag = KEEP_SPOT_TAG }, contentAlignment = Alignment.Center) {
        StickerSpot(size = spot)
        if (checked) {
            DecorSticker(
                "star",
                Modifier.graphicsLayer {
                    if (reduced) alpha = pop.value else { scaleX = pop.value; scaleY = pop.value }
                },
                size = star,
            )
        }
    }
}

/** Test tag of every keep spot (the recipe card's rows, the Snap and Heard trays' cells). */
const val KEEP_SPOT_TAG: String = "add_keep_spot"

/** The keep star's appear poses (§4.17: the book's magic-appear, 320 ms in four steps). */
private val KEEP_POSES = listOf(0.3f, 1.3f, 0.92f)
private const val KEEP_POSE_MS = 320L / 4

/**
 * Decorative text (doodles, the polaroid's caption): hidden from TalkBack, but tagged [tag] for the tests, and it still
 * reports [earth.cora.picnic.ui.a11y.TextOverflowed] when its text does not fit, so the clipped-text checks see it.
 */
internal fun Modifier.decorativeText(tag: String, overflowed: Boolean): Modifier = clearAndSetSemantics {
    this[Decorative] = Unit
    testTag = tag
    if (overflowed) textOverflowed = true
}

/**
 * Words drawn under a node that speaks for them (a row's or a line's own label, a placeholder TalkBack never reads):
 * each Text reports its layout here ([layout] with its own key), and [clearedWith] puts
 * [earth.cora.picnic.ui.a11y.TextOverflowed] on the node when any of them lost text, so the clipped-text checks
 * (§15: nothing clips at 200 %) still see words that TalkBack does not.
 */
@Stable
internal class LostText {
    private val lost = mutableStateMapOf<Int, Boolean>()

    val any: Boolean get() = lost.values.any { it }

    fun layout(key: Int): (TextLayoutResult) -> Unit = { r -> lost[key] = r.lostText() }
}

@Composable
internal fun rememberLostText(): LostText = remember { LostText() }

/** Clears the words' own semantics and sets [extra] (the label TalkBack hears) plus TextOverflowed when [words] lost text. */
internal fun Modifier.clearedWith(words: LostText, extra: SemanticsPropertyReceiver.() -> Unit = {}): Modifier = clearAndSetSemantics {
    extra()
    if (words.any) textOverflowed = true
}

/** True when [r] lost text: a line cut by maxLines or by the box's height, or an ellipsis. */
internal fun TextLayoutResult.lostText(): Boolean =
    didOverflowHeight || multiParagraph.didExceedMaxLines || (0 until lineCount).any { isLineEllipsized(it) }

/**
 * Draws [content] at font scale 1 (decorative print on a fixed-size object: the polaroid's caption, a number badge).
 * TalkBack never reads it, and the object it sits on does not grow with the text, so it keeps its drawn size.
 */
@Composable
internal fun FixedFontScale(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, 1f), content = content)
}
