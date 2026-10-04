package earth.cora.picnic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.ui.a11y.textOverflowed

/*
 * The "Next stop" interstitial card (docs/DESIGN_STICKER.md §10, D24; items 168–170). Owner: WP6.
 *
 * A holo prize card 236 dp wide: "NEXT" (DynaPuff 700 26 sp ballpoint, 6 dp white stroke, hs) and a 20 dp sparkle
 * sticker, then the next aisle's tab sticker (46 dp: a 30 dp number circle, the display name in DynaPuff 700 23 sp
 * BALLPOINT, its pattern on the right 32 dp at 45 %, kept clear of the name) and the store chip ("A3") when the aisle
 * has one. Nothing else.
 * Radius 14, 4 dp white border, hs2; three sparkle stickers twinkle at its corners, outside the text. The foil is
 * STATIC: no glint ever crosses the text during its hold (§9).
 *
 * Name fit: the name gets the card's inner width minus the number circle, the pattern's 36 dp and the chip (it never
 * runs over the pattern); it shrinks in 1 sp steps from 23 to 18 sp, then wraps to two lines (the card
 * grows). At large font scales (the same steps on the scaled sizes) a word can still be wider than the card: the card
 * then widens (up to the screen less 16 dp a side), drops the store chip under the tab when the two cannot share a line,
 * and only if two lines at 18 sp still do not hold the name keeps shrinking on two lines down to 14 sp; it ellipsizes
 * only beyond that, which no default aisle name needs at font scale 2.0. It never clips. Motion and semantics are the
 * host's (NextCardHost).
 */

/**
 * The fitted name: its size, its line count, and its lines as laid out ([text]: null when a word wider than the whole
 * room wraps inside itself) (tests: NextStopTest.defaultAislesFit, fontScale200).
 */
@Immutable
data class NextNameFit(val fontSizeSp: Float, val lines: Int, val overflowed: Boolean, val text: List<String>? = null)

/**
 * Fits [name] into [maxWidthPx] by the §10 rule: [fromSp] → [toSp] on one line, then two lines at [toSp], then two
 * lines shrinking to [floorSp] (large font scales only need it), then as many lines at [floorSp] as it takes (§0.4: an
 * aisle name wraps, it is never cut). It wraps between words into balanced lines, "&" kept with the word after it
 * ([tabLines]); only a word wider than the whole room breaks inside itself, which counts as overflowed.
 */
fun fitNextName(
    measurer: TextMeasurer,
    name: String,
    style: TextStyle,
    maxWidthPx: Int,
    fromSp: Float = 23f,
    toSp: Float = 18f,
    floorSp: Float = 14f,
): NextNameFit {
    fun at(sp: Float) = style.copy(fontSize = sp.sp, lineHeight = (sp * 1.2f).sp)
    var sp = fromSp
    while (sp >= toSp - 0.01f) {
        val width = measurer.measure(name, at(sp), softWrap = false, maxLines = 1).size.width
        if (width <= maxWidthPx) return NextNameFit(sp, 1, false, listOf(name))
        sp -= 1f
    }
    sp = toSp
    while (true) {
        val s = at(sp)
        val lines = tabLines(name, maxWidthPx) { measurer.measure(it, s, softWrap = false, maxLines = 1).size.width }
        if (lines != null && (lines.size <= 2 || sp <= floorSp + 0.01f)) return NextNameFit(sp, lines.size, false, lines)
        if (sp <= floorSp + 0.01f) {
            val wrapped = measurer.measure(keepWithNextWord(name), s, softWrap = true, constraints = Constraints(maxWidth = maxWidthPx))
            return NextNameFit(sp, wrapped.lineCount, true)
        }
        sp -= 1f
    }
}

/** The card's fixed width (§2.9 `nextCard`). */
val NextCardWidth: Dp = 236.dp

/** The fitted name size a NextStopCard chose (sp), for tests. */
val NextNameFontSize = androidx.compose.ui.semantics.SemanticsPropertyKey<Float>("NextNameFontSize")

/** The fitted name's line count, for tests. */
val NextNameLines = androidx.compose.ui.semantics.SemanticsPropertyKey<Int>("NextNameLines")

/**
 * The Next card for the stop at walking position [number] (null = "Everything else": the capsule in the circle),
 * [displayName], its store chip [storeAisle] and [look]. Decorative inside: the host gives the card its one button node.
 */
@Composable
fun NextStopCard(
    number: Int?,
    displayName: String,
    storeAisle: Int?,
    look: AisleLook,
    nextLabel: String,
    modifier: Modifier = Modifier,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val shapes = PicnicTheme.shapes
    val shadows = PicnicTheme.shadows
    val reduced = LocalReducedMotion.current
    val loop by rememberLoopTime(enabled = !reduced)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val nameStyle = t.nextAisle.copy(color = c.ballpoint)
    // The card plus room round it for the corner sparkles and the hs2 shadow. It is 236 dp wide, and wider only when the
    // name cannot sit on two lines at 18 sp beside the circle and the chip (a long name at a large font scale), up to the
    // screen less 16 dp a side.
    BoxWithConstraints(modifier) {
        val room = 8.dp
        val widest = if (constraints.hasBoundedWidth) (maxWidth - 32.dp).coerceAtLeast(NextCardWidth.coerceAtMost(maxWidth)) else NextCardWidth * 1.4f
        // The store chip as the kit lays it out (its glyph scales with its label), and the 6 dp before it.
        val chip = if (storeAisle != null) storeAisleChipWidth(storeAisle) + 6.dp else 0.dp
        val circle = nextCircleSize(density)
        val around = 4.dp * 2 + 12.dp * 2 + tabFixed(circle, look)
        val needed = remember(displayName, nameStyle, density) { with(density) { nameNeed(measurer, displayName, nameStyle).toDp() } }
        // The chip follows the tab; when the two cannot share a line even on the widest card, it drops under the tab.
        val chipBelow = storeAisle != null && around + chip + needed > widest
        val cardWidth = maxOf(NextCardWidth, around + (if (chipBelow) 0.dp else chip) + needed).coerceAtMost(widest)
        Box(Modifier.width(cardWidth + room * 2)) {
            Column(
                Modifier
                    .padding(room)
                    .width(cardWidth)
                    .hardShadow(shapes.nextCard, shadows, large = true)
                    .clip(shapes.nextCard)
                    .background(c.stickerWhite)
                    .padding(4.dp)
                    .clip(RoundedInner)
                    .drawBehind { drawRect(holoBrush(Offset(size.width * 0.5f, size.height * 0.42f))) }
                    .padding(horizontal = 12.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PuffyText(
                        nextLabel,
                        style = t.nextLabel.copy(color = c.ballpoint),
                        fill = PicnicTheme.colors.ballpoint,
                        stroke = 6.dp,
                        strokeColor = Color.White,
                        shadow = shadows.hs,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                    Spacer(Modifier.width(4.dp))
                    DecorSticker("star4", size = 20.dp)
                }
                NextTab(number, displayName, storeAisle, look, nameStyle, measurer, chipBelow, circle)
            }
            // Three sparkle stickers at the corners, twinkling, outside the text.
            val twinkle = if (reduced) Pose() else Twinkle.at(loop)
            val twinkle2 = if (reduced) Pose() else Twinkle.at(loop, 125)
            val twinkle3 = if (reduced) Pose() else Twinkle.at(loop, 250)
            DecorSticker("star4", Modifier.align(Alignment.TopStart).offset(2.dp, 2.dp).size(22.dp).graphicsLayer { scaleX = twinkle.scale; scaleY = twinkle.scale; rotationZ = twinkle.rotation }, size = 22.dp)
            DecorSticker("star", Modifier.align(Alignment.TopEnd).offset((-2).dp, 4.dp).size(20.dp).graphicsLayer { scaleX = twinkle2.scale; scaleY = twinkle2.scale; rotationZ = twinkle2.rotation }, size = 20.dp)
            DecorSticker("heart", Modifier.align(Alignment.BottomStart).offset(4.dp, (-2).dp).size(18.dp).graphicsLayer { scaleX = twinkle3.scale; scaleY = twinkle3.scale; rotationZ = twinkle3.rotation }, size = 18.dp)
        }
    }
}

private val RoundedInner = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)

/**
 * The tab's end padding: the pattern's 32 dp plus 4 dp, so the name (the text she reads while walking) never sits on
 * the 45 % pattern, as on the list's tabs (§4.10, §15 text on pattern); 12 dp on a plain tab (no pattern to clear).
 */
private fun tabEnd(look: AisleLook): Dp = if (look.pattern == TabPattern.Plain) 12.dp else 32.dp + 4.dp

/** The tab's parts round the name: border 3 + 3, start 6, the number [circle], the 8 dp gap, the end padding. */
private fun tabFixed(circle: Dp, look: AisleLook): Dp = 3.dp * 2 + 6.dp + circle + 8.dp + tabEnd(look)

/** The number circle's numeral: DynaPuff 17 sp (§10). */
private const val NUMERAL_SP: Float = 17f

/**
 * The numeral scales with the font, as tab numbers do (§2.7), up to [NUMERAL_MAX_DP]. The circle itself stays 30 dp,
 * because a circle grown for a fully scaled numeral takes the room the aisle name needs to stay at 18 sp on two lines at
 * font scale 2.0 (item 170), and the name is what she reads; the number repeats what TalkBack says. Within that cap the
 * numeral is fitted on its ink ([rememberNextNumeral]): it never comes nearer the ring than 0.75 dp (item 337).
 */
const val NUMERAL_MAX_DP: Float = 21f

/** The numeral's starting size in sp at [fontScale] (min(17 × fontScale, [NUMERAL_MAX_DP]) dp), before its ink fit. */
fun nextNumeralSp(fontScale: Float): Float = minOf(NUMERAL_SP * fontScale, NUMERAL_MAX_DP) / fontScale

/** The number circle: 30 dp at every font scale (see [NUMERAL_MAX_DP]). */
@Suppress("UNUSED_PARAMETER")
private fun nextCircleSize(density: androidx.compose.ui.unit.Density): Dp = 30.dp

/** The Next card's number circle, for tests (its numeral's clearance from the ring is read off the pixels). */
const val NEXT_NUMBER_TAG: String = "nextNumber"

/**
 * [number]'s numeral style in the Next card's [size] circle with its [ring] (§10, item 337): from [nextNumeralSp], set
 * 0.5 sp smaller each step until the kit's circle rule ([fitNumeral]: the numeral's text box inside the ring, and
 * [NUMBER_RING_CLEARANCE] between its ink and the ring) holds it in [size]. Worked out once per number, look and density.
 */
@Composable
private fun rememberNextNumeral(number: Int?, look: AisleLook, size: Dp, ring: Dp): TextStyle {
    val base = PicnicTheme.type.tabNumber.copy(color = look.family.trace)
    val paper = PicnicTheme.colors.stickerWhite
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val density = LocalDensity.current
    return remember(number, look, size, ring, base, paper, density, measurer) {
        val sp = nextNumeralSp(density.fontScale)
        val style = base.copy(fontSize = sp.sp, lineHeight = (sp * 1.1f).sp)
        if (number == null) return@remember style
        with(density) {
            val d = size.roundToPx()
            fitNumeral(measurer, number, style, look.family.trace, paper, d, d, ring.toPx()).style
        }
    }
}

/**
 * The narrowest room (px) in which [name] sits on at most two lines at 18 sp with no word broken, "&" kept with the
 * word after it.
 */
private fun nameNeed(measurer: TextMeasurer, name: String, style: TextStyle): Int {
    val s = style.copy(fontSize = 18.sp, lineHeight = (18f * 1.2f).sp)
    fun width(text: String) = measurer.measure(text, s, softWrap = false, maxLines = 1).size.width
    val one = width(name)
    val widestWord = keepWithNextWord(name).split(' ').filter { it.isNotEmpty() }.maxOf { width(it) }
    var lo = widestWord
    var hi = maxOf(one, widestWord)
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if ((tabLines(name, mid, ::width)?.size ?: 3) <= 2) hi = mid else lo = mid + 1
    }
    return lo + 1
}

/** The tab sticker on the card: 46 dp, a 30 dp number circle, the name (fitted), the pattern on the right 32 dp at 45 %. */
@Composable
private fun NextTab(number: Int?, displayName: String, storeAisle: Int?, look: AisleLook, nameStyle: TextStyle, measurer: TextMeasurer, chipBelow: Boolean, circle: Dp) {
    if (chipBelow && storeAisle != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NextTab(number, displayName, null, look, nameStyle, measurer, chipBelow = false, circle = circle)
            StoreAisleChip(storeAisle)
        }
        return
    }
    val c = PicnicTheme.colors
    val shapes = PicnicTheme.shapes
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // The chip is measured first; the tab takes the rest, and the name what the tab leaves it.
        Row(
            Modifier
                .weight(1f, fill = false)
                .heightIn(min = 46.dp)
                .hardShadow(shapes.tab, PicnicTheme.shadows)
                .clip(shapes.tab)
                .background(c.stickerWhite)
                .padding(3.dp)
                .clip(shapes.tab)
                .background(look.family.pastel)
                .tabPattern(look, width = 32.dp, alpha = 0.45f)
                .heightIn(min = 40.dp)
                .padding(start = 6.dp, end = tabEnd(look), top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NextNumberCircle(number, look, size = circle, ring = 2.dp)
            BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                val room = constraints.maxWidth
                val fit = remember(displayName, nameStyle, room) { fitNextName(measurer, displayName, nameStyle, room) }
                // Laid out line by line (balanced, "&" with the word after it, §0.4), so the tab hugs its widest line.
                Text(
                    fit.text?.joinToString("\n") ?: keepWithNextWord(displayName),
                    style = nameStyle.copy(fontSize = fit.fontSizeSp.sp, lineHeight = (fit.fontSizeSp * 1.2f).sp),
                    softWrap = fit.text == null,
                    modifier = Modifier.semantics {
                        this[NextNameFontSize] = fit.fontSizeSp
                        this[NextNameLines] = fit.lines
                        if (fit.overflowed) textOverflowed = true
                    },
                )
            }
        }
        // The store's own "A3" chip (§4.10): the theme's chip, which grows with a large font instead of clipping.
        if (storeAisle != null) StoreAisleChip(storeAisle)
    }
}

/**
 * The number circle at the Next card's size: [size] (30 dp, at every font scale), white with the kit's [ring] in the
 * trace, the DynaPuff numeral in the trace fitted on its ink ([rememberNextNumeral]) and placed as [NumberCircle] places
 * it; the capsule for "Everything else".
 */
@Composable
private fun NextNumberCircle(number: Int?, look: AisleLook, size: Dp, ring: Dp) {
    val numeral = rememberNextNumeral(number, look, size, ring)
    androidx.compose.ui.layout.Layout(
        content = {
            Box(Modifier.clip(CircleShape).background(PicnicTheme.colors.stickerWhite).ringStroke(ring, look.family.trace))
            if (number != null) {
                Text(number.toString(), style = numeral, maxLines = 1, softWrap = false)
            } else {
                CapsuleMark(look.family, Modifier.size(size * 0.6f))
            }
        },
        modifier = Modifier.testTag(NEXT_NUMBER_TAG).clearAndSetSemantics { },
    ) { measurables, _ ->
        val d = size.roundToPx()
        val circle = measurables[0].measure(Constraints.fixed(d, d))
        val mark = measurables[1].measure(Constraints())
        layout(d, d) {
            circle.place(0, 0)
            mark.place(numeralOffset(d, mark.width), numeralOffset(d, mark.height))
        }
    }
}
