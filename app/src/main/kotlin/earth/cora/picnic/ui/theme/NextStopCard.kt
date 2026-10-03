package earth.cora.picnic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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

/** The fitted name: its size and its line count (tests: NextStopTest.defaultAislesFit, fontScale200). */
@Immutable
data class NextNameFit(val fontSizeSp: Float, val lines: Int, val overflowed: Boolean)

/**
 * Fits [name] into [maxWidthPx] by the §10 rule: [fromSp] → [toSp] on one line, then two lines at [toSp], then two
 * lines shrinking to [floorSp] (large font scales only need it), then an ellipsis.
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
    var sp = fromSp
    while (sp >= toSp - 0.01f) {
        val layout = measurer.measure(name, style.copy(fontSize = sp.sp, lineHeight = (sp * 1.2f).sp), softWrap = false, maxLines = 1, constraints = Constraints(maxWidth = maxWidthPx))
        if (!layout.hasVisualOverflow) return NextNameFit(sp, 1, false)
        sp -= 1f
    }
    sp = toSp
    while (true) {
        val two = measurer.measure(name, style.copy(fontSize = sp.sp, lineHeight = (sp * 1.2f).sp), softWrap = true, maxLines = 2, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = maxWidthPx))
        // A word wider than the line breaks inside itself: that is a clip too, so it counts as not fitting.
        val wordsWhole = (0 until two.lineCount).all { l -> two.getLineEnd(l, visibleEnd = true).let { e -> e >= name.length || name[e - 1] == ' ' || name.getOrNull(e) == ' ' } }
        if ((!two.hasVisualOverflow && wordsWhole) || sp <= floorSp + 0.01f) return NextNameFit(sp, two.lineCount.coerceAtMost(2), two.hasVisualOverflow || !wordsWhole)
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
        val chip = if (storeAisle != null) with(density) { measurer.measure("A$storeAisle", t.label.copy(fontSize = 13.sp)).size.width.toDp() } + CHIP_CHROME + 6.dp else 0.dp
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
 * The numeral scales with the font, as tab numbers do (§2.7), up to [NUMERAL_MAX_DP]: the largest at which two digits
 * ("14") sit inside the 30 dp circle's ring. The circle itself stays 30 dp, because a circle grown for a fully scaled
 * numeral takes the room the aisle name needs to stay at 18 sp on two lines at font scale 2.0 (item 170), and the name
 * is what she reads; the number repeats what TalkBack says.
 */
const val NUMERAL_MAX_DP: Float = 21f

/** The numeral's size in sp at [fontScale] (it renders at min(17 × fontScale, [NUMERAL_MAX_DP]) dp). */
fun nextNumeralSp(fontScale: Float): Float = minOf(NUMERAL_SP * fontScale, NUMERAL_MAX_DP) / fontScale

/** The number circle: 30 dp at every font scale (see [NUMERAL_MAX_DP]). */
@Suppress("UNUSED_PARAMETER")
private fun nextCircleSize(density: androidx.compose.ui.unit.Density): Dp = 30.dp

/** The store chip round its text: border 2 + 2, padding 7 + 7, the 13 dp basket glyph and its 5 dp gap. */
private val CHIP_CHROME: Dp = 2.dp * 2 + 7.dp * 2 + 13.dp + 5.dp

/** The narrowest room (px) in which [name] sits on at most two lines at 18 sp with no word broken. */
private fun nameNeed(measurer: TextMeasurer, name: String, style: TextStyle): Int {
    val s = style.copy(fontSize = 18.sp, lineHeight = (18f * 1.2f).sp)
    val one = measurer.measure(name, s, softWrap = false, maxLines = 1).size.width
    val widestWord = name.split(' ').filter { it.isNotEmpty() }.maxOf { measurer.measure(it, s, softWrap = false, maxLines = 1).size.width }
    var lo = widestWord
    var hi = maxOf(one, widestWord)
    while (lo < hi) {
        val mid = (lo + hi) / 2
        val l = measurer.measure(name, s, softWrap = true, maxLines = 3, constraints = Constraints(maxWidth = mid))
        if (l.lineCount <= 2) hi = mid else lo = mid + 1
    }
    return lo + 1
}

/** The tab sticker on the card: 46 dp, a 30 dp number circle, the name (fitted), the pattern on the right 32 dp at 45 %. */
@Composable
private fun NextTab(number: Int?, displayName: String, storeAisle: Int?, look: AisleLook, nameStyle: TextStyle, measurer: TextMeasurer, chipBelow: Boolean, circle: Dp) {
    if (chipBelow && storeAisle != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NextTab(number, displayName, null, look, nameStyle, measurer, chipBelow = false, circle = circle)
            NextStoreChip(storeAisle)
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
            NextNumberCircle(number, look, size = circle, ring = 2.dp, numeral = nextNumeralSp(LocalDensity.current.fontScale).sp)
            BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                val room = constraints.maxWidth
                val fit = remember(displayName, nameStyle, room) { fitNextName(measurer, displayName, nameStyle, room) }
                Text(
                    displayName,
                    style = nameStyle.copy(fontSize = fit.fontSizeSp.sp, lineHeight = (fit.fontSizeSp * 1.2f).sp),
                    maxLines = 2,
                    softWrap = fit.lines > 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics {
                        this[NextNameFontSize] = fit.fontSizeSp
                        this[NextNameLines] = fit.lines
                        if (fit.overflowed) textOverflowed = true
                    },
                )
            }
        }
        if (storeAisle != null) NextStoreChip(storeAisle)
    }
}

/**
 * The store-aisle chip on the card (§4.10's "A3": lemon pastel, basket glyph, label 13 sp ballpoint): 22 dp tall, and
 * taller when the font scale grows its text, so the label never clips. TalkBack: "store aisle 3".
 */
@Composable
private fun NextStoreChip(number: Int) {
    val label = androidx.compose.ui.res.stringResource(earth.cora.picnic.R.string.theme_store_aisle, number)
    val style = PicnicTheme.type.label.copy(color = PicnicTheme.colors.ballpoint, fontSize = 13.sp, lineHeight = 15.sp)
    val textHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    ChipFace(
        fill = Families.lemon.pastel,
        modifier = Modifier.clearAndSetSemantics { contentDescription = label },
        height = maxOf(22.dp, textHeight + 2.dp * 2 + 4.dp),
        border = 2.dp,
        horizontal = 7.dp,
    ) {
        Glyph(PicnicGlyph.Basket, null, tint = PicnicTheme.colors.ballpoint, size = 13.dp)
        Text("A$number", style = style, maxLines = 1)
    }
}

/**
 * The number circle at the Next card's size: [size] (30 dp), DynaPuff [numeral] sp in the trace, scaling with the font
 * up to what the circle holds ([nextNumeralSp]; the capsule for "Everything else").
 */
@Composable
private fun NextNumberCircle(number: Int?, look: AisleLook, size: Dp, ring: Dp, numeral: TextUnit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .border(ring, look.family.trace, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (number != null) {
            Text(number.toString(), style = PicnicTheme.type.tabNumber.copy(color = look.family.trace, fontSize = numeral, lineHeight = numeral * 1.1f), maxLines = 1)
        } else {
            CapsuleMark(look.family, Modifier.size(size * 0.6f))
        }
    }
}
