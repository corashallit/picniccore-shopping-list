package earth.cora.picnic.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The list's paper pieces (docs/DESIGN_STICKER.md §4.10, §4.11, rules R1 and R6): the index tab with its fitted name,
 * the open tray drawn slice by slice (one slice per lazy item, so every row keeps its own key) with the current aisle's
 * lemon ring and offset shadow running unbroken from the band (pinned or not) down to the end cap, the folded strip's
 * face, the pressed row's highlighter band and the checked row's hanko strike. Motion (the chains) is laid on top by
 * the list screen from WP6's presentation. Owner: WP5 (TabLayoutTest, TrayContainmentTest, PinnedBackingProbeTest).
 */

/* ------------------------------------------------------------------------------------------------
 * The index tab (§4.10).
 * ------------------------------------------------------------------------------------------------ */

/** The tab name as fitted: its size, and whether it wraps onto more lines at that size (TabLayoutTest). */
@Immutable
data class TabFit(val fontSizeSp: Float, val wraps: Boolean)

/** The baseline of an [IndexTab]'s name's first line (not its numeral's), as an alignment line its parent can read. */
val TabNameBaseline: HorizontalAlignmentLine = HorizontalAlignmentLine(::minOf)

/**
 * An aisle name as it is laid out where it may wrap (§4.10, §0.4): "&" and "+" are held to the word after them (a
 * no-break space), so a line never ends in one ("Meat / & seafood", "Pasta, rice / & international").
 */
fun keepWithNextWord(name: String): String = name.replace(" & ", " & ").replace(" + ", " + ")

/**
 * [name]'s lines in [room] px (§4.10, §0.4): the name itself when [width] says it fits on one line; else broken between
 * words into the fewest lines, balanced (the widest line as narrow as it can be, a longer first line on a tie), "&"
 * and "+" kept with the word after them ([keepWithNextWord]; without [glue], words only). Null when a word on its own
 * (with its "&") is wider than [room].
 */
fun tabLines(name: String, room: Int, glue: Boolean = true, width: (String) -> Int): List<String>? {
    if (width(name) <= room) return listOf(name)
    val words = (if (glue) keepWithNextWord(name) else name).split(' ').filter { it.isNotEmpty() }
    if (words.size < 2) return null
    val n = words.size
    val widths = HashMap<Int, Int>()
    fun w(i: Int, j: Int): Int = widths.getOrPut(i * (n + 1) + j) { width(words.subList(i, j).joinToString(" ")) }
    if ((0 until n).any { w(it, it + 1) > room }) return null
    // From word i to the end: the fewest lines, then the narrowest widest line, and where its first line ends.
    val lines = IntArray(n + 1)
    val widest = IntArray(n + 1)
    val next = IntArray(n + 1)
    for (i in n - 1 downTo 0) {
        lines[i] = Int.MAX_VALUE
        var j = i + 1
        while (j <= n && w(i, j) <= room) {
            val l = 1 + lines[j]
            val m = maxOf(w(i, j), widest[j])
            if (l < lines[i] || (l == lines[i] && m <= widest[i])) {
                lines[i] = l
                widest[i] = m
                next[i] = j
            }
            j++
        }
    }
    val out = ArrayList<String>()
    var i = 0
    while (i < n) {
        out += words.subList(i, next[i]).joinToString(" ")
        i = next[i]
    }
    return out
}

/** The fitted tab label's size (sp), for tests. */
val TabNameFontSize: SemanticsPropertyKey<Float> = SemanticsPropertyKey("TabNameFontSize")

/** The tab label's line count, for tests. */
val TabNameLines: SemanticsPropertyKey<Int> = SemanticsPropertyKey("TabNameLines")

/**
 * Fits [name] on one line in [maxWidthPx] (§4.10): the style's 20 sp if it fits, else 1 sp smaller each step down to
 * [toSp] (16 sp; under a large font, the name's 1.0 size, [tabNameFloorSp]), else [toSp], where it wraps. Sizes are the
 * style's sp, scaled by the font scale like every other text.
 */
fun fitTabName(measurer: TextMeasurer, name: String, style: TextStyle, maxWidthPx: Int, fromSp: Float = 20f, toSp: Float = 16f): TabFit {
    fun fits(sp: Float) = measurer.measure(name, style.copy(fontSize = sp.sp, lineHeight = (sp * 1.2f).sp), softWrap = false, maxLines = 1).size.width <= maxWidthPx
    var sp = fromSp
    while (sp >= toSp - 0.01f) {
        if (fits(sp)) return TabFit(sp, false)
        sp -= 1f
    }
    // A floor between the steps (a large font's 1.0 size) is tried as it is.
    if (sp + 1f > toSp + 0.01f && fits(toSp)) return TabFit(toSp, false)
    return TabFit(toSp, true)
}

/**
 * The size (sp) a tab name shrinks to before it wraps (§4.10): 16 sp; under a large font, on down to the size that
 * draws 16 sp at 1.0 (16 dp), so a name on one line at 1.0 is on one line at 1.3 and 2.0 too (item 281), and no name is
 * ever drawn under its 1.0 size (§2.7, as the store-aisle chip, item 337). Only a name that wraps at 1.0 wraps under a
 * large font, at the same size.
 */
fun tabNameFloorSp(density: androidx.compose.ui.unit.Density): Float = with(density) { 16.dp.toSp().value }.coerceAtMost(16f)

/**
 * A test hook for the list's layout rules (items 76, 79, 92): when provided, list pieces report where they were laid
 * out (in the root) under a key, with the tab's fitted size. Null in the app, where it costs nothing.
 */
fun interface PieceProbe {
    fun report(key: String, bounds: Rect, fontSp: Float?)

    /** The element reported under [key] left the screen (a name tag lifted off, a page turned). */
    fun gone(key: String) = Unit

    /** A text reported under [key] was laid out (for the large-text rules: no cut line, no break inside a word). */
    fun text(key: String, layout: TextLayoutResult) = Unit
}

val LocalPieceProbe = androidx.compose.runtime.staticCompositionLocalOf<PieceProbe?> { null }

/** Reports this element's bounds to [probe] under [key] each time it is placed, and when it leaves (no-op without a probe). */
fun Modifier.probe(probe: PieceProbe?, key: String?, fontSp: Float? = null): Modifier =
    if (probe == null || key == null) this else this then ProbeElement(probe, key, fontSp)

private data class ProbeElement(val probe: PieceProbe, val key: String, val fontSp: Float?) : ModifierNodeElement<ProbeNode>() {
    override fun create() = ProbeNode(probe, key, fontSp)

    override fun update(node: ProbeNode) {
        if (node.key != key) node.probe.gone(node.key)
        node.probe = probe
        node.key = key
        node.fontSp = fontSp
    }
}

private class ProbeNode(var probe: PieceProbe, var key: String, var fontSp: Float?) : Modifier.Node(), GlobalPositionAwareModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) = probe.report(key, coordinates.boundsInRoot(), fontSp)

    override fun onDetach() = probe.gone(key)
}

/**
 * The tab's height (§2.9): 38 dp in a tray header, 32 dp on a folded strip; both with the 24 dp number circle and its
 * 14 sp numeral (§4.10, item 75). Both are minimums: under a large font the circle grows with its numeral (item 337).
 */
enum class TabSize(val height: Dp, val circle: Dp) {
    Tray(38.dp, 24.dp),
    Strip(32.dp, 24.dp),
}

/**
 * An aisle's index tab (§4.10): the aisle pastel, a 3 dp white border, radius 12 / 4, hs; the number circle (the
 * walking [position]; the capsule for "Everything else") and the [displayName] (DynaPuff 700, fitted 20 → 16 sp, then
 * wrapped onto more lines at 16 sp, between words, balanced, "&" kept with the word after it; never cut), the pattern
 * on the right 26 dp only (12 dp where a word would not fit beside 26, or where that saves a third line), never under
 * the name. [ballpoint]: the open tray's and the pinned plate's label (the strongest text on the list); otherwise the
 * aisle trace. The tab is at most [maxWidth] wide, and as tall as its lines.
 * Decorative: its strip or tray carries the full name for TalkBack.
 */
@Composable
fun IndexTab(
    position: Int,
    displayName: String,
    look: AisleLook,
    size: TabSize,
    ballpoint: Boolean,
    maxWidth: Dp = Dp.Infinity,
    modifier: Modifier = Modifier,
    probeKey: String? = null,
    /** On the tab's words only (its name, its numeral's circle): the eggs' `text:` keep-outs (§12.1), not its sticker edge. */
    nameModifier: Modifier = Modifier,
    numberModifier: Modifier = Modifier,
) {
    // The room is the smaller of [maxWidth] and what the parent offers (a strip's text column, a band's tab slot).
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val offered = if (constraints.hasBoundedWidth) this.maxWidth else Dp.Infinity
        TabFace(position, displayName, look, size, ballpoint, minOf(maxWidth, offered), probeKey, nameModifier, numberModifier)
    }
}

@Composable
private fun TabFace(
    position: Int,
    displayName: String,
    look: AisleLook,
    size: TabSize,
    ballpoint: Boolean,
    maxWidth: Dp,
    probeKey: String?,
    nameModifier: Modifier = Modifier,
    numberModifier: Modifier = Modifier,
) {
    val probe = LocalPieceProbe.current
    val c = PicnicTheme.colors
    val shapes = PicnicTheme.shapes
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val base = PicnicTheme.type.aisleTab.copy(color = if (ballpoint) c.ballpoint else look.family.trace)
    // The number circle grows with a large font (the kit's NumberCircle, §2.7), but gives way to the name: it takes only
    // what leaves the whole name room on one line at 16 sp, down to its drawn 24 dp (its numeral then set to clear the
    // ring). The name is what she reads (§4.10); the number repeats it.
    val number = position.takeIf { it > 0 }
    val grown = rememberNumberCircleSize(number, look, size.circle)
    val nameLeast = rememberTabNameLeast(displayName, density)
    val circle = if (maxWidth == Dp.Infinity) grown else (maxWidth - tabChrome(look, 0.dp) - nameLeast).coerceIn(size.circle, grown.coerceAtLeast(size.circle))
    // What the name may take: the tab's width less its border, padding, circle and gap.
    val fixed = tabChrome(look, circle)
    val room = if (maxWidth == Dp.Infinity) Int.MAX_VALUE else with(density) { (maxWidth - fixed).roundToPx().coerceAtLeast(1) }
    val floor = tabNameFloorSp(density)
    val fit = remember(displayName, base, room, floor) { fitTabName(measurer, displayName, base, room, toSp = floor) }
    val style = base.copy(fontSize = fit.fontSizeSp.sp, lineHeight = (fit.fontSizeSp * 1.2f).sp)
    // Too long for one line even at 16 sp: it wraps between words into balanced lines, "&" with the word after it
    // (§4.10, §0.4: "Meat / & seafood"). Laid out line by line, so the tab hugs its widest line. A word wider than the
    // whole room on its own (rare) wraps inside itself instead, so no letter is lost.
    // A word too wide for the room on its own ("& international" beside a strip's basket), or a name that would take a
    // third line ("Canned goods / & soups"), gets the 14 dp the pattern end gives up when it narrows from 26 to 12 dp.
    val narrowRoom = if (room == Int.MAX_VALUE) room else room + with(density) { (PATTERN_END - PATTERN_END_NARROW).roundToPx() }
    val wrapped = remember(displayName, style, room, fit, look.pattern) {
        fun lines(r: Int, glue: Boolean = true) = tabLines(displayName, r, glue) { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width }
        val canNarrow = look.pattern != TabPattern.Plain
        val wide = if (fit.wraps) lines(room) else listOf(displayName)
        val narrower = if (canNarrow && (wide == null || wide.size > 2)) lines(narrowRoom) else null
        when {
            narrower != null && (wide == null || narrower.size < wide.size) -> narrower to true
            wide != null -> wide to false
            // A word with its "&" wider than the whole tab ("& international" at a large font on a narrow phone): the
            // "&" ends a line rather than a word breaking.
            canNarrow -> lines(narrowRoom, glue = false) to true
            else -> lines(room, glue = false) to false
        }
    }
    val lines = wrapped.first
    val narrow = wrapped.second || (lines == null && look.pattern != TabPattern.Plain && room != Int.MAX_VALUE)
    val patternWidth = if (narrow) PATTERN_END_NARROW else PATTERN_END
    val lineCount = lines?.size ?: 0
    Row(
        Modifier
            .probe(probe, probeKey, fit.fontSizeSp)
            .widthIn(max = maxWidth)
            .heightIn(min = size.height)
            .clearAndSetSemantics {
                this[TabNameFontSize] = fit.fontSizeSp
                this[TabNameLines] = lineCount
            }
            .hardShadow(shapes.tab, PicnicTheme.shadows)
            .clip(shapes.tab)
            .background(c.stickerWhite)
            .padding(3.dp)
            .clip(shapes.tab)
            .background(look.family.pastel)
            .tabPattern(look, width = patternWidth, alpha = 0.5f)
            .heightIn(min = size.height - 6.dp)
            .padding(start = 6.dp, end = tabEnd(look) - (PATTERN_END - patternWidth), top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TabNumber(number, look, size, maxSize = if (circle >= grown) Dp.Infinity else circle, numberModifier)
        Text(
            lines?.joinToString("\n") ?: displayName,
            style = if (lines == null) style.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading) else style,
            softWrap = lines == null,
            modifier = nameModifier.layout { m, c ->
                // The first line's baseline, for what sits beside the tab on it (egg 5's "snack?", §4.10 doodle).
                val p = m.measure(c)
                layout(p.width, p.height, mapOf(TabNameBaseline to p[FirstBaseline])) { p.place(0, 0) }
            },
        )
    }
}

/** An index tab's width round its name: border 3 + 3, start 6, the number [circle] as drawn, the 6 dp gap and [tabEnd]. */
private fun tabChrome(look: AisleLook, circle: Dp): Dp = 3.dp * 2 + 6.dp + circle + 6.dp + tabEnd(look)

/**
 * The narrowest an [IndexTab] for [position] can be and still show its whole [displayName] on one line (at 16 sp), its
 * number circle as drawn here (it grows with a large font): for a row that gives way to the name before it wraps (the
 * tray header's store chip, §4.10).
 */
@Composable
fun indexTabLeastWidth(position: Int, displayName: String, look: AisleLook, size: TabSize): Dp {
    val density = LocalDensity.current
    val circle = rememberNumberCircleSize(position.takeIf { it > 0 }, look, size.circle)
    return rememberTabNameLeast(displayName, density) + tabChrome(look, circle)
}

/**
 * The narrowest an [IndexTab] can be and still hold [displayName] on at most two lines, between words, at its smallest
 * size (16 sp; a large font's 16 dp), "&" kept with the word after it, at its 24 dp circle. The tray header's store chip
 * stays beside a name that fits it this way, and gives way to one that would need a third line (§4.10).
 */
@Composable
fun indexTabTwoLineWidth(displayName: String, look: AisleLook, size: TabSize): Dp {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val base = PicnicTheme.type.aisleTab
    val name = remember(displayName, base, density) {
        val sp = tabNameFloorSp(density)
        val style = base.copy(fontSize = sp.sp, lineHeight = (sp * 1.2f).sp)
        fun width(text: String) = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
        val words = keepWithNextWord(displayName).split(' ').filter { it.isNotEmpty() }
        val two = (1 until words.size).minOfOrNull { k -> maxOf(width(words.subList(0, k).joinToString(" ")), width(words.subList(k, words.size).joinToString(" "))) }
        val px = minOf(width(displayName), two ?: Int.MAX_VALUE)
        with(density) { (px + 2).toDp() }
    }
    return name + tabChrome(look, size.circle)
}

/**
 * The room [displayName] needs whole on one line at 16 sp here, two px over its width, so the tab's own fit (name
 * width <= its room, rounded to px) takes it whole.
 */
@Composable
private fun rememberTabNameLeast(displayName: String, density: androidx.compose.ui.unit.Density): Dp {
    val measurer = rememberTextMeasurer(cacheSize = 2)
    val base = PicnicTheme.type.aisleTab
    return remember(displayName, base, density) {
        val name = measurer.measure(displayName, base.copy(fontSize = 16.sp, lineHeight = (16f * 1.2f).sp), softWrap = false, maxLines = 1).size.width
        with(density) { (name + 2).toDp() }
    }
}

/** The tab's pattern end (§4.10): 26 dp, narrowed to 12 dp where a word would not fit beside 26 or a third line would. */
private val PATTERN_END: Dp = 26.dp
private val PATTERN_END_NARROW: Dp = 12.dp

/** The tab's right padding: the 26 dp pattern strip plus 6 dp; a plain tab ("Everything else") closes at 12 dp. */
private fun tabEnd(look: AisleLook): Dp = if (look.pattern == TabPattern.Plain) 12.dp else PATTERN_END + 6.dp

/**
 * The tab's number circle (§2.7): the kit's [NumberCircle] at [TabSize.circle] (24 dp white, a 2 dp trace ring, DynaPuff
 * 14 sp in the trace; the capsule for "Everything else"). Under a large font the numeral scales with the name beside it
 * and the circle grows until its ring clears the numeral's ink by 0.75 dp (item 337), up to [maxSize] where the name
 * needs the room; the tab grows round it.
 */
@Composable
private fun TabNumber(number: Int?, look: AisleLook, size: TabSize, maxSize: Dp, modifier: Modifier = Modifier) {
    NumberCircle(number, look, modifier, size = size.circle, maxSize = maxSize)
}

/* ------------------------------------------------------------------------------------------------
 * The open tray, slice by slice (§4.10, R1, R6, §0.4 "the lemon ring never breaks").
 * ------------------------------------------------------------------------------------------------ */

/** The tray's measures (dp), shared by every slice so they line up to the pixel. */
object TrayMetrics {
    /** The current aisle's lemon ring outside the white border. */
    val ring: Dp = 3.dp

    /** The white kiss-cut border. */
    val border: Dp = 3.dp

    /** The pastel wall between the border and the ruled panel (padding 0 5 18 dp). */
    val side: Dp = 5.dp

    /** The pastel below the panel, holding the 15 dp patterned end cap. */
    val bottom: Dp = 18.dp
    val endCap: Dp = 15.dp

    /** The header band inside the border. */
    val band: Dp = 50.dp

    /** The current aisle's offset shadow (§4.10: 2.6, 3.6). */
    val shadow: DpOffset = DpOffset(2.6.dp, 3.6.dp)

    /** Any other open tray's hard shadow (hs, §2.10). */
    val hs: DpOffset = DpOffset(1.6.dp, 2.2.dp)

    val outerRadius: Dp = 12.dp
    val innerRadius: Dp = 9.dp
    val panelRadius: Dp = 8.dp

    /**
     * From the slice's side to the panel: border + wall. The tray's white border lines up with the folded strips'
     * edges; the current aisle's ring and its shadow sit outside it, in the list's 8 dp gutter (as in the mockup).
     */
    val panelInset: Dp get() = border + side

    /** The header slice's height: ring + border + band (the pinned plate: 53 dp + the 3 dp ring). */
    val headerHeight: Dp get() = ring + border + band

    /** The cap slice's height: the pastel bottom, the border, the ring and room for the shadow. */
    val capHeight: Dp get() = bottom + border + ring + shadow.y
}

/** Which slice of the tray a lazy item draws. */
enum class TrayPart { Header, Row, Cap }

/**
 * Paints one slice of the open tray behind this item: the hard offset shadow, the 3 dp lemon ring ([ring] colour, the
 * Day lemon mid; Glow's glow-star mint), the white border, the pastel wall, and for rows the white ruled panel (its
 * 1.5 dp inner ring in the aisle mid at 60 %, a 1 dp rule above every row but the [first], rounded at the [first] and
 * [last] rows) or for the cap the patterned end cap. Every slice draws the whole tray's outline extended past its own
 * open edges and clipped to its own top and bottom only, so slices join with no seam and the ring and shadow run
 * unbroken from the band (pinned or not) to the cap. [pinned]: the band is the pinned plate, backed by the binder
 * ground from the window's top to its bottom edge across the full width (R6), so no row shows above or beside it.
 */
fun Modifier.traySlice(
    part: TrayPart,
    look: AisleLook,
    /** The current aisle's ring (lemon mid; Glow's glow-star mint), or null for an open tray that is not current. */
    ring: Color?,
    shadow: Color,
    pad: Color,
    rule: Color,
    first: Boolean = false,
    last: Boolean = false,
    patternTile: androidx.compose.ui.graphics.ShaderBrush? = null,
): Modifier = drawBehind { drawTraySlice(part, look, ring, shadow, pad, rule, first, last, patternTile) }

/**
 * The slice's tray outline (the ring's outer edge) in local px: it starts at the header slice's top and ends above the
 * cap slice's shadow room, and runs past every other slice's open edges.
 */
private fun DrawScope.trayOutline(part: TrayPart): Rect {
    val far = size.height + 4000f
    val ring = TrayMetrics.ring.toPx()
    val top = if (part == TrayPart.Header) 0f else -far
    val bottom = if (part == TrayPart.Cap) size.height - TrayMetrics.shadow.y.toPx() else size.height + far
    return Rect(-ring, top, size.width + ring, bottom)
}

internal fun DrawScope.drawTraySlice(
    part: TrayPart,
    look: AisleLook,
    ring: Color?,
    shadow: Color,
    pad: Color,
    rule: Color,
    first: Boolean,
    last: Boolean,
    patternTile: androidx.compose.ui.graphics.ShaderBrush?,
) {
    val outer = trayOutline(part)
    val ringPx = TrayMetrics.ring.toPx()
    val borderPx = TrayMetrics.border.toPx()
    val sidePx = TrayMetrics.side.toPx()
    val rOuter = TrayMetrics.outerRadius.toPx() + ringPx
    val rWhite = TrayMetrics.outerRadius.toPx()
    val rInner = TrayMetrics.innerRadius.toPx()
    val white = Rect(outer.left + ringPx, outer.top + ringPx, outer.right - ringPx, outer.bottom - ringPx)
    val pastel = Rect(white.left + borderPx, white.top + borderPx, white.right - borderPx, white.bottom - borderPx)
    // Clip vertically to the slice only: the shadow and ring may run past the sides into the list's gutter.
    clipRect(left = -size.width, top = 0f, right = size.width * 2f, bottom = size.height) {
        if (ring != null) {
            // The current aisle: the ring outside the white border and the (2.6, 3.6) offset shadow under it.
            val sx = TrayMetrics.shadow.x.toPx()
            val sy = TrayMetrics.shadow.y.toPx()
            drawRoundRect(shadow, Offset(outer.left + sx, outer.top + sy), outer.size, CornerRadius(rOuter))
            drawRoundRect(ring, outer.topLeft, outer.size, CornerRadius(rOuter))
        } else {
            // Any other open tray: the plain hard shadow (hs) under its white border, no ring.
            drawRoundRect(shadow, Offset(white.left + TrayMetrics.hs.x.toPx(), white.top + TrayMetrics.hs.y.toPx()), white.size, CornerRadius(rWhite))
        }
        drawRoundRect(Color.White, white.topLeft, white.size, CornerRadius(rWhite))
        drawRoundRect(look.family.pastel, pastel.topLeft, pastel.size, CornerRadius(rInner))
        when (part) {
            TrayPart.Header -> Unit
            TrayPart.Row -> {
                val panel = Rect(pastel.left + sidePx, if (first) 0f else -size.height - 40f, pastel.right - sidePx, if (last) size.height else size.height * 2f + 40f)
                val r = TrayMetrics.panelRadius.toPx()
                val shape = RoundRect(panel, CornerRadius(r))
                val path = Path().apply { addRoundRect(shape) }
                drawPath(path, pad)
                // The panel's inner ring: 1.5 dp in the aisle mid at 60 %, just inside its edge.
                val w = 1.5.dp.toPx()
                val inset = RoundRect(panel.left + w / 2f, panel.top + w / 2f, panel.right - w / 2f, panel.bottom - w / 2f, CornerRadius(r - w / 2f))
                drawPath(Path().apply { addRoundRect(inset) }, look.family.mid.copy(alpha = 0.6f), style = Stroke(w))
                if (!first) {
                    val rw = 1.dp.toPx()
                    drawRect(rule, Offset(panel.left + w, 0f), Size(panel.width - w * 2, rw))
                }
            }
            TrayPart.Cap -> {
                val capTop = 0f + (TrayMetrics.bottom - TrayMetrics.endCap).toPx() / 2f
                val capRect = Rect(pastel.left, capTop, pastel.right, pastel.bottom)
                if (patternTile != null) {
                    val clip = Path().apply { addRoundRect(RoundRect(pastel, CornerRadius(rInner))) }
                    clipPath(clip) {
                        drawRect(
                            patternTile, capRect.topLeft, capRect.size, alpha = 0.55f,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(look.family.mid, androidx.compose.ui.graphics.BlendMode.SrcIn),
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * The folded strip's face (§4.10).
 * ------------------------------------------------------------------------------------------------ */

/** A folded strip's face: white (Glow: the navy page), radius 6, hs, the full width. */
@Composable
fun Modifier.stripFace(): Modifier {
    val c = PicnicTheme.colors
    val shape = PicnicTheme.shapes.strip
    return this
        .hardShadow(shape, PicnicTheme.shadows)
        .clip(shape)
        .background(c.pad)
}

/* ------------------------------------------------------------------------------------------------
 * The row's pressed band and the checked strike (§4.11).
 * ------------------------------------------------------------------------------------------------ */

/**
 * The pressed row's highlighter band behind the TEXT area only (§4.11): left −4, right −2, top / bottom 3 dp past the
 * text, rounded like a marker stroke (3 10 4 12), rotated −0.6°; Glow's band is #3A438A.
 */
fun Modifier.highlighterBand(visible: Boolean, color: Color, alpha: () -> Float = { 1f }): Modifier = if (!visible) this else drawBehind {
    val a = alpha()
    if (a <= 0f) return@drawBehind
    val l = -4.dp.toPx()
    val t = -3.dp.toPx()
    val r = size.width + 2.dp.toPx()
    val b = size.height + 3.dp.toPx()
    val shape = RoundRect(
        left = l, top = t, right = r, bottom = b,
        topLeftCornerRadius = CornerRadius(3.dp.toPx()),
        topRightCornerRadius = CornerRadius(10.dp.toPx()),
        bottomRightCornerRadius = CornerRadius(4.dp.toPx()),
        bottomLeftCornerRadius = CornerRadius(12.dp.toPx()),
    )
    rotate(-0.6f) { drawPath(Path().apply { addRoundRect(shape) }, color.copy(alpha = color.alpha * a)) }
}

/**
 * An item name with the checked row's strike (§4.11): a 2 dp hanko line through every line of the laid-out text, from
 * its left to its right ink edge (custom-drawn from the text layout, not LineThrough), so a wrapped name is struck on
 * each line.
 */
@Composable
fun StruckText(text: String, style: TextStyle, struck: Boolean, strike: Color, modifier: Modifier = Modifier) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            // The strike is drawn under the glyphs (§4.11: custom-drawn under the text layout, not LineThrough).
            val l = layout
            if (!struck || l == null) {
                drawContent()
                return@drawWithContent
            }
            val w = 2.dp.toPx()
            for (i in 0 until l.lineCount) {
                val top = l.getLineTop(i)
                val base = l.getLineBaseline(i)
                val y = top + (base - top) * 0.66f
                val x0 = l.getLineLeft(i)
                val x1 = l.getLineRight(i)
                if (x1 - x0 > 1f) drawLine(strike, Offset(x0 - 1.dp.toPx(), y), Offset(x1 + 1.dp.toPx(), y), w, cap = StrokeCap.Round)
            }
            drawContent()
        },
    )
}

/* ------------------------------------------------------------------------------------------------
 * The chevron (§4.10).
 * ------------------------------------------------------------------------------------------------ */

/** The strip's and tray's chevron face: a 30 dp pad circle, 2 dp periwinkle-pastel ring, hs, the chevron (down = expand). */
@Composable
fun ChevronFace(up: Boolean, modifier: Modifier = Modifier, rotation: Float = if (up) 180f else 0f) {
    val c = PicnicTheme.colors
    val shadows = PicnicTheme.shadows
    Canvas(modifier.size(34.dp)) {
        val r = 15.dp.toPx()
        val center = Offset(size.width / 2f - 1.dp.toPx(), size.height / 2f - 1.dp.toPx())
        drawCircle(shadows.color, r, center + Offset(shadows.hs.x.toPx(), shadows.hs.y.toPx()))
        drawCircle(c.pad, r, center)
        drawCircle(Families.periwinkle.pastel, r - 1.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        rotate(rotation, center) {
            val w = 5.5.dp.toPx()
            val h = 3.2.dp.toPx()
            val p = Path().apply {
                moveTo(center.x - w, center.y - h / 1.4f)
                lineTo(center.x, center.y + h)
                lineTo(center.x + w, center.y - h / 1.4f)
            }
            drawPath(p, c.onPad, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

/** Places [content] in a [maxWidth]-aware box (helper for tab fitting in rows). */
@Composable
internal fun WithMaxWidth(modifier: Modifier = Modifier, content: @Composable (Dp) -> Unit) {
    BoxWithConstraints(modifier) { content(maxWidth) }
}
