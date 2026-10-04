package earth.cora.picnic.ui.theme

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.ui.a11y.textOverflowed
import earth.cora.picnic.ui.theme.gen.LcdGlyph
import earth.cora.picnic.ui.theme.gen.LcdGlyphs
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer
import kotlin.math.ceil
import kotlin.math.min

/*
 * The pastel LCD toy (docs/DESIGN_STICKER.md §4.3, §13.1). Owner: WP4 (LcdGlyphsTest, LcdChipTest); WP6 and WP9
 * animate the pet sprite. All LCD text is drawn by [LcdText] as a vector 5 × 7 dot matrix (never a font), so it can
 * print any store name and never counts as a font family.
 */

/** What the LCD shows (§13.1 `LcdMode`). */
enum class LcdMode {
    /** "10/24★": [LcdState.count] of [LcdState.total], the star (a moon in Dusk and Glow). */
    Count,

    /** The pixel bee (egg 15) + the count; the star hides. */
    Pet,

    /** One word: FED 1.5 s, SNACK? 2.6 s, BONUS★, SYNC 1.5 s, PRINT★ 1.7 s ([LcdState.text]). */
    Text,

    /** Settings "Test connection": OK, OK., OK.. ticking every 0.6 s ([LcdState.text]). */
    Ok,

    /** The Speak timer "0:12" ([LcdState.text]). */
    Timer,

    /** The book's page number "2/3" ([LcdState.text]). */
    Page,
}

/**
 * The LCD's content. TalkBack reads the count ("10 of 24 in the basket") whatever the mode shows, since words are
 * decorative. [receiptReady] exposes the long-press as the custom action "Print the trip receipt" (only once the hold
 * prints the receipt, D21: the trip is complete or its finale has played, and with Easter eggs on; the pet stays a secret).
 */
@Immutable
data class LcdState(
    val mode: LcdMode = LcdMode.Count,
    val count: Int = 0,
    val total: Int = 0,
    val text: String = "",
    val receiptReady: Boolean = false,
    /** The pet's pose in [LcdMode.Pet] (WP9 animates it). */
    val petPose: PetPose = PetPose.Rest,
    /**
     * The pet lives in this LCD (egg 15) while a word other than its own stands in (`Text` mode: PRINT★, SNACK?,
     * BONUS★, SYNC): the bee steps aside for the word, and the chip keeps the bee's and the count's room.
     */
    val petLives: Boolean = false,
)

/** The pixel bee's sprite frames (egg 15, 9 × 7 px): bob 2 steps per second, a 2-frame chomp, a 4-frame dance. */
enum class PetPose { Rest, Bob, Chomp1, Chomp2, Dance1, Dance2, Dance3, Dance4 }

/* ------------------------------------------------------------------------------------------------
 * Glyphs.
 * ------------------------------------------------------------------------------------------------ */

/** The vector extras that are drawn as shapes, not dots (§13.1): the star, the full-moon disc and the heart. */
internal enum class LcdExtra { Star, Moon, Heart }

/** One printable cell: a dot glyph or a vector extra, and its width in columns. */
@Immutable
internal class LcdCell(val glyph: LcdGlyph?, val extra: LcdExtra?, val width: Int)

/**
 * The glyph table (§13.1): WP2's generated [LcdGlyphs] first, then these supplements for the punctuation the app can
 * print that the generated table does not carry yet (# % & ' ( ) , · " = *). Same 5 × 7 conventions.
 */
internal object LcdSupplement {
    private fun g(width: Int, vararg rows: Int) = LcdGlyph(width, rows)
    val glyphs: Map<Char, LcdGlyph> = mapOf(
        '#' to g(5, 0b01010, 0b01010, 0b11111, 0b01010, 0b11111, 0b01010, 0b01010),
        '%' to g(5, 0b11001, 0b11010, 0b00010, 0b00100, 0b01000, 0b01011, 0b10011),
        '&' to g(5, 0b01100, 0b10010, 0b10100, 0b01000, 0b10101, 0b10010, 0b01101),
        '\'' to g(1, 0b1, 0b1, 0b0, 0b0, 0b0, 0b0, 0b0),
        '"' to g(3, 0b101, 0b101, 0b000, 0b000, 0b000, 0b000, 0b000),
        '(' to g(3, 0b001, 0b010, 0b100, 0b100, 0b100, 0b010, 0b001),
        ')' to g(3, 0b100, 0b010, 0b001, 0b001, 0b001, 0b010, 0b100),
        ',' to g(2, 0b00, 0b00, 0b00, 0b00, 0b01, 0b01, 0b10),
        '·' to g(1, 0b0, 0b0, 0b0, 0b1, 0b0, 0b0, 0b0),
        '=' to g(5, 0b00000, 0b00000, 0b11111, 0b00000, 0b11111, 0b00000, 0b00000),
        '*' to g(5, 0b00000, 0b10101, 0b01110, 0b11111, 0b01110, 0b10101, 0b00000),
    )
}

/** Folds [text] to what the LCD prints: uppercase, accents folded (É → E, Ç → C), Œ → OE, Æ → AE, ß → SS. */
internal fun lcdFold(text: String): String {
    val sb = StringBuilder()
    for (ch in text) {
        when (ch) {
            'œ', 'Œ' -> sb.append("OE")
            'æ', 'Æ' -> sb.append("AE")
            'ß' -> sb.append("SS")
            '–', '—' -> sb.append('-')
            '‘', '’' -> sb.append('\'')
            '“', '”' -> sb.append('"')
            '•' -> sb.append('·')
            '…' -> sb.append("...")
            '×' -> sb.append('X')
            '☆' -> sb.append('★')
            '♥' -> sb.append('♡')
            else -> {
                val base = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
                sb.append(base.uppercase())
            }
        }
    }
    return sb.toString()
}

/** The cells [text] prints as; any character without a glyph prints as a space. */
internal fun lcdCells(text: String): List<LcdCell> = lcdFold(text).map { ch ->
    when (ch) {
        '★' -> LcdCell(null, LcdExtra.Star, 7)
        '●' -> LcdCell(null, LcdExtra.Moon, 7)
        '♡' -> LcdCell(null, LcdExtra.Heart, 7)
        else -> {
            val g = LcdGlyphs.glyph(ch) ?: LcdSupplement.glyphs[ch]
            if (g != null) LcdCell(g, null, g.width) else LcdCell(LcdGlyphs.glyph(' ') ?: LcdGlyph(2, IntArray(7)), null, 2)
        }
    }
}

/** True when every character of [text] has a glyph or an extra (LcdGlyphsTest: nothing the app prints is missing). */
fun lcdCanPrint(text: String): Boolean = lcdFold(text).all { ch ->
    ch == ' ' || ch == '★' || ch == '●' || ch == '♡' || LcdGlyphs.glyph(ch) != null || LcdSupplement.glyphs[ch] != null
}

/** The width of [text] in columns (glyph columns plus a 1-column gap between cells). */
internal fun lcdColumns(cells: List<LcdCell>): Int = if (cells.isEmpty()) 0 else cells.sumOf { it.width } + cells.size - 1

/* ------------------------------------------------------------------------------------------------
 * LcdText.
 * ------------------------------------------------------------------------------------------------ */

/**
 * A 5 × 7 dot-matrix line at [pitch] (app bar 1.9 dp, page number 1.2 dp, receipt 1.3 dp, settings 1.6 dp), scaled with
 * the font scale up to 1.6×. Each lit dot is 0.8 pitch; unlit dots show at 50 % [grid]. Lowercase prints as uppercase,
 * accents fold, anything else is a space. [moon] draws ★ as a full-moon disc (Dusk and Glow). [bold] doubles the dots
 * (the receipt's first and last lines). [starAlpha] dims only the ★ (or moon) cells' lit dots (the chip's blink): the
 * line keeps one layout, so the blink never moves a dot. Sets the TextOverflowed semantics key when it does not fit its
 * constraints.
 */
@Composable
fun LcdText(
    text: String,
    pitch: Dp,
    modifier: Modifier = Modifier,
    color: Color = PicnicTheme.colors.ballpoint,
    grid: Color? = PicnicTheme.colors.lcdGrid,
    moon: Boolean = false,
    bold: Boolean = false,
    starAlpha: Float = 1f,
) {
    val density = LocalDensity.current
    val scale = min(density.fontScale, 1.6f).coerceAtLeast(1f)
    val cells = remember(text) { lcdCells(text) }
    val columns = lcdColumns(cells)
    val p = with(density) { pitch.toPx() } * scale
    var overflowed by remember { mutableStateOf(false) }
    Layout(
        modifier = modifier
            .semantics {
                this.text = AnnotatedString(text)
                if (overflowed) textOverflowed = true
            }
            .drawBehind { drawLcdCells(cells, p, color, grid, moon, bold, starAlpha) },
        content = {},
    ) { _, constraints ->
        val w = ceil(columns * p).toInt()
        val h = ceil(LcdGlyphs.ROWS * p).toInt()
        overflowed = constraints.hasBoundedWidth && w > constraints.maxWidth
        layout(w.coerceAtMost(constraints.maxWidth), h.coerceIn(constraints.minHeight, constraints.maxHeight)) {}
    }
}

/** Draws [cells] at pitch [p] (px) from the top-left. */
internal fun DrawScope.drawLcdCells(
    cells: List<LcdCell>,
    p: Float,
    color: Color,
    grid: Color?,
    moon: Boolean,
    bold: Boolean,
    starAlpha: Float = 1f,
) {
    val dot = p * 0.8f
    val inset = (p - dot) / 2f
    val r = CornerRadius(dot * 0.18f)
    val total = lcdColumns(cells)
    if (grid != null) {
        val unlit = grid.copy(alpha = grid.alpha * 0.5f)
        for (col in 0 until total) for (row in 0 until LcdGlyphs.ROWS) {
            drawRoundRect(unlit, Offset(col * p + inset, row * p + inset), Size(dot, dot), r)
        }
    }
    var x = 0
    for (cell in cells) {
        val left = x * p
        val g = cell.glyph
        when {
            cell.extra != null -> {
                val blinks = cell.extra == LcdExtra.Star || cell.extra == LcdExtra.Moon
                drawExtra(cell.extra, left, p, if (blinks && starAlpha < 1f) color.copy(alpha = color.alpha * starAlpha) else color, moon)
            }
            g != null -> for (row in 0 until LcdGlyphs.ROWS) for (col in 0 until g.width) {
                if (g.lit(col, row)) {
                    val w = if (bold) dot + p * 0.45f else dot
                    drawRoundRect(color, Offset(left + col * p + inset, row * p + inset), Size(w, dot), r)
                }
            }
        }
        x += cell.width + 1
    }
}

private fun DrawScope.drawExtra(extra: LcdExtra, left: Float, p: Float, color: Color, moon: Boolean) {
    val c = Offset(left + 3.5f * p, 3.5f * p)
    when {
        extra == LcdExtra.Moon || (extra == LcdExtra.Star && moon) -> drawCircle(color, 3.1f * p, c)
        extra == LcdExtra.Star -> drawPath(starPath(c, 3.6f * p, 1.55f * p), color)
        else -> drawPath(heartPath(c, 6.4f * p), color)
    }
}

/* ------------------------------------------------------------------------------------------------
 * LcdChip.
 * ------------------------------------------------------------------------------------------------ */

/**
 * The app-bar LCD chip (§4.3): face `lcd`, 3 dp white border, radius 9, hs, padding 7 × 8 dp, a faint 1 dp grid at
 * 50 %. Digits tick on change (−2 → +1 → 0 dp, 2 steps, 160 ms); the star blinks to 12 % for 3 % of every 4 s (0.25 Hz)
 * and is a moon in Dusk and Glow (the count's star only: the star in a word, BONUS★ or PRINT★, stays a star). [onHold]
 * fires once the finger has stayed down on the chip for the hold threshold (`max(440 ms, longPressTimeout)`): the
 * receipt when the trip is complete, otherwise the pet (D21); a tap, a brush across it or a scroll that takes the
 * gesture does nothing. TalkBack reads the count label in every mode.
 */
@Composable
fun LcdChip(
    state: LcdState,
    modifier: Modifier = Modifier,
    onHold: (() -> Unit)? = null,
    pitch: Dp = 1.9.dp,
    /**
     * The words this chip can show in place of its count (the app bar: SYNC, and with Easter eggs on FED, SNACK?,
     * BONUS★ and PRINT★): its text keeps one slot as wide as the widest of them and the count, in every mode.
     */
    words: List<String> = emptyList(),
) {
    val c = PicnicTheme.colors
    val shapes = PicnicTheme.shapes
    val variant = PicnicTheme.variant
    val reduced = LocalReducedMotion.current
    val moon = lcdShowsMoon(variant, state)
    val shown = when (state.mode) {
        LcdMode.Count -> "${state.count}/${state.total}★"
        // The pet's own words (egg 15's "FED") show beside it; otherwise the count.
        LcdMode.Pet -> state.text.ifEmpty { "${state.count}/${state.total}" }
        else -> state.text
    }
    val label = pluralStringResource(R.plurals.theme_lcd_count, state.total, state.count, state.total)
    val printLabel = stringResource(R.string.theme_lcd_print_receipt)
    val threshold = LocalTimeouts.current.holdThresholdMs
    val hold by rememberUpdatedState(onHold)

    // Tick: −2 → +1 → 0 dp in 2 steps over 160 ms when the shown text changes (instant under reduced motion).
    var tickDy by remember { mutableStateOf(0f) }
    var last by remember { mutableStateOf(shown) }
    LaunchedEffect(shown) {
        if (shown != last) {
            last = shown
            if (!reduced) {
                withContext(FixedMotionScale) {
                    val offsets = PicnicMotion.lcdTickOffsetsDp
                    val step = PicnicMotion.lcdTick.durationMillis / (offsets.size - 1).toLong()
                    for (o in offsets.dropLast(1)) {
                        tickDy = o
                        delay(step)
                    }
                    tickDy = offsets.last()
                }
            }
        }
    }
    // The star's blink: dim for the last 120 ms of every 4 s. Timed by delays, not frames, so the chip recomposes twice
    // per blink and the frame clock idles between them (the LCD sits on the List screen for a whole shop). The
    // infinite-animation policy still decides whether it runs at all (tests: the lit star, unless the clock is held).
    var dim by remember { mutableStateOf(false) }
    LaunchedEffect(reduced, state.mode) {
        dim = false
        if (reduced || state.mode != LcdMode.Count) return@LaunchedEffect
        while (true) {
            withInfiniteAnimationFrameMillis { }
            delay(PicnicMotion.lcdBlinkPeriod - PicnicMotion.lcdBlinkOn)
            dim = true
            delay(PicnicMotion.lcdBlinkOn)
            dim = false
        }
    }
    val starAlpha = if (dim) PicnicMotion.lcdBlinkAlpha else 1f
    // The text's slot (§13.1, item 259): a word standing in for the count (an egg's PRINT★ or FED, a refresh's SYNC) is
    // never narrower than the count it replaces, and on the app bar ([words]) the slot is as wide as the widest word the
    // chip can show as well, in every mode, so the chip keeps one width whatever it shows and the binder top's words
    // beside it never re-flow. The bee (Pet mode) stands beside the slot, so FED and PRINT★ with the bee are as wide as
    // the bee and the count. The text is centred in its slot.
    val slot = remember(state.mode, state.count, state.total, state.text, state.petLives, words) { lcdSlot(state, words) }

    Box(
        modifier
            .then(if (onHold != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .clearAndSetSemantics {
                contentDescription = label
                if (onHold != null && state.receiptReady) {
                    customActions = listOf(CustomAccessibilityAction(printLabel) { hold?.invoke(); true })
                }
            }
            .then(
                if (onHold != null) {
                    Modifier.pointerInput(threshold) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (awaitLcdHold(down, threshold)) hold?.invoke()
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .hardShadow(shapes.lcd, PicnicTheme.shadows)
                .clip(shapes.lcd)
                .background(c.stickerWhite)
                .padding(3.dp)
                .clip(RoundedLcdInner)
                .background(c.lcd)
                .drawBehind { drawLcdGrid(c.lcdGrid) }
                .padding(horizontal = 8.dp, vertical = 7.dp),
        ) {
            Row(
                Modifier.lcdSlot(slot, pitch).offset { IntOffset(0, tickDy.dp.roundToPx()) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.mode == LcdMode.Pet) {
                    LcdPetSprite(state.petPose, Modifier.padding(end = pitch * PET_GAP), pitch = pitch)
                }
                // One line, one layout: the blink dims the star's dots in place and never re-lays the digits.
                LcdText(shown, pitch, moon = moon, starAlpha = starAlpha)
            }
        }
    }
}

/** Only the count's star becomes the moon in Dusk and Glow (§13.1); the star inside a word (BONUS★, PRINT★) stays a star. */
internal fun lcdShowsMoon(variant: ThemeVariant, state: LcdState): Boolean =
    (variant == ThemeVariant.Dusk || variant == ThemeVariant.Glow) &&
        (state.mode == LcdMode.Count || (state.mode == LcdMode.Pet && state.text.isEmpty()))

/**
 * The room the chip's content keeps (§13.1, item 259), in LCD columns: [alone], a text shown on its own; [besideBee], a
 * text shown beside the bee (null when the pet does not live here). The content is centred in the widest.
 */
@Immutable
internal data class LcdSlot(val alone: Int, val besideBee: Int?)

/**
 * The chip's room for [state] (§13.1, item 259). With [words] (the app bar: every word it can show in place of its
 * count), the widest of them and the count, in every mode, so the chip keeps one width whatever it shows; without, a
 * word or the bee standing in for the count is never narrower than the count. While the pet lives here (Pet mode, or
 * [LcdState.petLives] while another word stands in), the bee's room beside the count (or its own word, FED) is kept
 * too, and the star hides.
 */
internal fun lcdSlot(state: LcdState, words: List<String>): LcdSlot {
    val petHome = state.mode == LcdMode.Pet || state.petLives
    val count = if (state.total > 0) {
        lcdColumns(lcdCells(if (petHome) "${state.count}/${state.total}" else "${state.count}/${state.total}★"))
    } else {
        0
    }
    val wordsAlone = words.maxOfOrNull { lcdColumns(lcdCells(it)) } ?: 0
    return if (petHome) {
        val own = if (state.mode == LcdMode.Pet) lcdColumns(lcdCells(state.text)) else 0
        LcdSlot(alone = wordsAlone, besideBee = maxOf(count, own))
    } else {
        LcdSlot(alone = maxOf(wordsAlone, if (state.mode != LcdMode.Count || words.isNotEmpty()) count else 0), besideBee = null)
    }
}

/** The gap after the bee, in pitches. */
private const val PET_GAP = 1.5f

/**
 * Lays the chip's content out as wide as [slot] at least, centred: LcdText's own px (ceil(columns × pitch × scale)) and
 * the bee's (9 pitches, unscaled, and its gap).
 */
private fun Modifier.lcdSlot(slot: LcdSlot, pitch: Dp): Modifier = layout { measurable, constraints ->
    val scale = min(fontScale, 1.6f).coerceAtLeast(1f)
    val p = pitch.toPx()
    fun text(columns: Int): Int = if (columns <= 0) 0 else ceil(columns * p * scale).toInt()
    val bee = (pitch * 9).roundToPx() + (pitch * PET_GAP).roundToPx()
    val room = maxOf(text(slot.alone), slot.besideBee?.let { bee + text(it) } ?: 0)
    val content = measurable.measure(constraints.copy(minWidth = 0))
    val w = maxOf(content.width, room).coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(w, content.height) { content.place((w - content.width) / 2, 0) }
}

/**
 * Waits out the hold threshold after [down] (§4.3) without consuming anything. True only when the finger is still down
 * on the chip, within the touch slop of where it landed, and nothing else has taken the gesture when the threshold runs
 * out; false on a release, a cancellation, a brush off the chip or a move, which are all different from running out the
 * clock (eggs 15 and 18 never fire from a swipe across the LCD).
 */
private suspend fun AwaitPointerEventScope.awaitLcdHold(down: PointerInputChange, thresholdMs: Long): Boolean {
    val slop = viewConfiguration.touchSlop
    var ended = false
    withTimeoutOrNull(thresholdMs) {
        while (true) {
            val e = awaitPointerEvent()
            val change = e.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed || change.isConsumed || change.isOutOfBounds(size, extendedTouchPadding) ||
                (change.position - down.position).getDistance() > slop
            ) {
                ended = true
                return@withTimeoutOrNull
            }
            // A parent that takes the gesture consumes on the Main pass after this child: look again on the Final pass.
            val after = awaitPointerEvent(PointerEventPass.Final)
            if (after.changes.any { it.id == down.id && it.isConsumed }) {
                ended = true
                return@withTimeoutOrNull
            }
        }
    }
    return !ended
}

private val RoundedLcdInner = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)

/** The LCD's faint 1 dp grid at 50 % (every 3 dp). */
private fun DrawScope.drawLcdGrid(grid: Color) {
    val step = 3.dp.toPx()
    val w = 1.dp.toPx() * 0.5f
    val color = grid.copy(alpha = 0.5f)
    var x = step
    while (x < size.width) {
        drawRect(color, Offset(x, 0f), Size(w, size.height))
        x += step
    }
    var y = step
    while (y < size.height) {
        drawRect(color, Offset(0f, y), Size(size.width, w))
        y += step
    }
}

/* ------------------------------------------------------------------------------------------------
 * The pixel bee (egg 15).
 * ------------------------------------------------------------------------------------------------ */

/** The bee's 9 × 7 frames, top row first ('#' = lit). */
internal object PetFrames {
    private fun f(vararg rows: String): List<String> = rows.toList()
    private val rest = f(
        "..##.##..",
        "..##.##..",
        ".#######.",
        "#.#.#.###",
        ".#######.",
        "..#...#..",
        ".........",
    )
    private val bob = f(
        "..##.##..",
        ".#######.",
        "#.#.#.###",
        ".#######.",
        "..#...#..",
        ".........",
        ".........",
    )
    private val chomp1 = f(
        "..##.##..",
        "..##.##..",
        ".#######.",
        "#.#.#.##.",
        ".######.#",
        "..#...#..",
        ".........",
    )
    private val chomp2 = rest
    private val dance1 = f(
        ".##...##.",
        "..##.##..",
        ".#######.",
        "#.#.#.###",
        ".#######.",
        ".#.....#.",
        ".........",
    )
    private val dance2 = f(
        "..##.##..",
        "..##.##..",
        "#######..",
        ".#.#.###.",
        "#######..",
        ".#...#...",
        ".........",
    )
    private val dance3 = dance1
    private val dance4 = f(
        "..##.##..",
        "..##.##..",
        "..#######",
        "..#.#.###",
        "..#######",
        "...#...#.",
        ".........",
    )

    fun of(pose: PetPose): List<String> = when (pose) {
        PetPose.Rest -> rest
        PetPose.Bob -> bob
        PetPose.Chomp1 -> chomp1
        PetPose.Chomp2 -> chomp2
        PetPose.Dance1 -> dance1
        PetPose.Dance2 -> dance2
        PetPose.Dance3 -> dance3
        PetPose.Dance4 -> dance4
    }
}

/**
 * The pixel bee in the LCD (egg 15): a 9 × 7 dot sprite at the LCD's [pitch], in ballpoint dots. Drawn by WP4; WP6 (the
 * celebration dance) and WP9 (chomp, bob) pick the [pose]. Decorative.
 */
@Composable
fun LcdPetSprite(pose: PetPose, modifier: Modifier = Modifier, pitch: Dp = 1.9.dp, color: Color = PicnicTheme.colors.ballpoint) {
    val frame = PetFrames.of(pose)
    Canvas(modifier.size(pitch * 9, pitch * 7).clearAndSetSemantics { }) {
        val p = size.width / 9f
        val dot = p * 0.8f
        val inset = (p - dot) / 2f
        frame.forEachIndexed { row, line ->
            line.forEachIndexed { col, ch ->
                if (ch == '#') drawRoundRect(color, Offset(col * p + inset, row * p + inset), Size(dot, dot), CornerRadius(dot * 0.18f))
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * A loop clock for decorative loops.
 * ------------------------------------------------------------------------------------------------ */

/**
 * Milliseconds since this loop started, ticking every frame while [enabled]; 0 when off (reduced motion) and under
 * tests' infinite-animation policy (so goldens show the first pose). Decorative loops only: chain beats and holds use
 * the frame clock in their own coroutines (§8.8).
 */
@Composable
fun rememberLoopTime(enabled: Boolean = true): State<Long> {
    val time = remember { mutableLongStateOf(0L) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            time.longValue = 0L
            return@LaunchedEffect
        }
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) {
            withInfiniteAnimationFrameMillis { now -> time.longValue = now - start }
        }
    }
    return time
}
