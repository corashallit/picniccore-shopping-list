package earth.cora.picnic.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.model.TripStats
import earth.cora.picnic.ui.theme.LcdText
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.hardShadow
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

/*
 * The trip receipt (egg 18, docs/DESIGN_STICKER.md §13.2): its 21-column lines and the thermal-paper sticker they print
 * on. Owner: WP8 (ui/common); the album's Trip book sheet shows a saved one, WP9's print overlay can draw the same paper
 * from the same lines (ReceiptTest, ReceiptLinesTest).
 */

/**
 * One printed line of the receipt. [text] is its 21-column form (as the spec writes it, and as tests read it); the
 * sticker draws a [Leader] justified (its value flush right, dots between) and a [Rule] as a dotted dash line across the
 * paper, because the LCD's glyphs are proportional and 21 characters do not always make 21 equal columns.
 */
@Immutable
sealed interface ReceiptLine {
    val text: String

    /** Words: the wordmark and "THANK YOU ♪" are [bold] (double dots) and [centred], like a till's header and footer. */
    data class Words(override val text: String, val bold: Boolean = false, val centred: Boolean = false) : ReceiptLine

    /** "STICKERS ......... 24". */
    data class Leader(val label: String, val value: String) : ReceiptLine {
        override val text: String get() = ReceiptLines.leader(label, value)
    }

    /** The dashed rule. */
    data object Rule : ReceiptLine {
        override val text: String get() = ReceiptLines.RULE
    }
}

/** The receipt's lines (§13.2), pure: 21 columns of the LCD alphabet, values from the trip. */
object ReceiptLines {
    /** Columns per line: the receipt is always exactly this wide. */
    const val COLUMNS: Int = 21

    /** The dashed rule: the LCD's "-" printed 21 times. */
    val RULE: String = "-".repeat(COLUMNS)

    const val FOOTER: String = "THANK YOU ♪"

    /** Store-name words that never stand for the store (§13.2). Compared folded (uppercase, no accents). */
    private val STOPWORDS = setOf("MY", "THE", "LA", "LE", "LES", "STORE", "MARKET", "MAGASIN", "EPICERIE", "GROCERY")

    private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.US)
    private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.US)

    /**
     * Every line, top to bottom: the wordmark ("KIRAKIRA ★ BASKET"), the date and the store word ("SAT 3 OCT ·
     * HILLCREST"), a rule, STICKERS, BASKETS, TIME ("41 MIN"; "--" for a migrated trip), SHINY, a rule, "THANK YOU ♪".
     */
    fun lines(
        appName: String,
        date: LocalDate,
        storeName: String,
        listTitle: String,
        stickers: Int,
        baskets: Int,
        minutes: Int?,
        shiny: Int,
    ): List<String> = layout(appName, date, storeName, listTitle, stickers, baskets, minutes, shiny).map { it.text }

    /** The same lines, as the sticker prints them ([ReceiptSticker]). */
    fun layout(
        appName: String,
        date: LocalDate,
        storeName: String,
        listTitle: String,
        stickers: Int,
        baskets: Int,
        minutes: Int?,
        shiny: Int,
    ): List<ReceiptLine> = listOf(
        ReceiptLine.Words(header(appName), bold = true, centred = true),
        ReceiptLine.Words(dateLine(date, storeWord(storeName, listTitle))),
        ReceiptLine.Rule,
        ReceiptLine.Leader("STICKERS", stickers.toString()),
        ReceiptLine.Leader("BASKETS", baskets.toString()),
        ReceiptLine.Leader("TIME", time(minutes)),
        ReceiptLine.Leader("SHINY", shiny.toString()),
        ReceiptLine.Rule,
        ReceiptLine.Words(FOOTER, bold = true, centred = true),
    )

    /** "Kirakira Basket" -> "KIRAKIRA ★ BASKET" (the app's name, uppercase, a star between its words). */
    fun header(appName: String): String = fold(appName).split(' ').filter { it.isNotEmpty() }.joinToString(" ★ ")

    /**
     * The store word (§13.2): the first word of the store name that is not a stopword; with none (the default "My
     * store"), the first word of the list title; null when both are empty. As written (folded when printed).
     */
    fun storeWord(storeName: String, listTitle: String): String? {
        fun words(text: String) = text.split(Regex("\\s+")).map { w -> w.trim { !it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
        return words(storeName).firstOrNull { fold(it) !in STOPWORDS } ?: words(listTitle).firstOrNull()
    }

    /**
     * Line 2, fitted to 21 columns: "SAT 3 OCT · HILLCREST". A word longer than its budget (21 − date − 3) drops the
     * weekday first ("26 SEP · HILLCREST"), then is cut to the new budget.
     */
    fun dateLine(date: LocalDate, word: String?): String {
        val day = "${date.dayOfMonth} ${fold(MONTH.format(date))}"
        val full = "${fold(WEEKDAY.format(date))} $day"
        val w = word?.let(::fold)?.takeIf { it.isNotEmpty() } ?: return full
        if (full.length + 3 + w.length <= COLUMNS) return "$full · $w"
        return "$day · ${w.take(COLUMNS - day.length - 3)}"
    }

    /** "STICKERS ......... 24": the label, dot leaders and the value, 21 columns. */
    fun leader(label: String, value: String): String {
        val dots = (COLUMNS - label.length - value.length - 2).coerceAtLeast(1)
        return "$label ${".".repeat(dots)} $value"
    }

    /** "41 MIN", "1 H 5 MIN"; "--" without a shop time. */
    fun time(minutes: Int?): String = if (minutes == null) "--" else fold(TripStats.formatMinutes(minutes))

    /**
     * [text] in the LCD's alphabet, one character per column: uppercase, accents folded (É -> E, Ç -> C), ligatures
     * spelled out (Œ -> OE, Æ -> AE).
     */
    fun fold(text: String): String {
        val upper = text.uppercase(Locale.ROOT).replace("Œ", "OE").replace("Æ", "AE")
        return Normalizer.normalize(upper, Normalizer.Form.NFD).replace(COMBINING, "")
    }

    private val COMBINING = Regex("\\p{Mn}+")
}

/** The receipt's pitch (§13.1): 1.3 dp a dot, times the LCD's font-scale factor (up to 1.6×, inside [LcdText]). */
val ReceiptPitch: Dp = 1.3.dp

/**
 * The receipt sticker (§13.2): pastel thermal paper (#FFF9E8) with a 3 dp white border and a hard shadow, rotated 3°,
 * its bottom edge torn in a 4 dp zig-zag; [lines] printed in ballpoint dots at [pitch] (× the LCD's font-scale factor,
 * up to 1.6×): words by [LcdText] (the first and last bold and centred), leaders justified with their dots on the
 * baseline, rules as dotted dashes across the paper. 21 × 6 × pitch + 16 dp wide, so the 21 columns always fit. No
 * semantics of its own beyond the texts: the caller reads it as one sentence.
 */
@Composable
fun ReceiptSticker(lines: List<ReceiptLine>, modifier: Modifier = Modifier, pitch: Dp = ReceiptPitch) {
    val c = PicnicTheme.colors
    val scale = min(LocalDensity.current.fontScale, 1.6f).coerceAtLeast(1f)
    // One dot-matrix cell as drawn (LcdText applies the same font-scale factor to its pitch).
    val cell = pitch * scale
    val width = cell * (ReceiptLines.COLUMNS * 6f) + 16.dp
    val ink = c.ballpoint
    Column(
        modifier
            .rotate(3f)
            .width(width)
            .hardShadow(ReceiptShape, PicnicTheme.shadows)
            .background(c.stickerWhite, ReceiptShape)
            .padding(3.dp)
            .background(c.receiptPaper, ReceiptShape)
            .padding(start = 5.dp, end = 5.dp, top = 8.dp, bottom = 8.dp + ReceiptShape.TOOTH_DEPTH),
        verticalArrangement = Arrangement.spacedBy(cell * 3f),
    ) {
        lines.forEach { line ->
            when (line) {
                is ReceiptLine.Words -> LcdText(
                    line.text,
                    pitch = pitch,
                    color = ink,
                    // Thermal paper shows no unlit dots.
                    grid = null,
                    bold = line.bold,
                    modifier = if (line.centred) Modifier.align(Alignment.CenterHorizontally) else Modifier,
                )
                is ReceiptLine.Leader -> Row(Modifier.fillMaxWidth()) {
                    LcdText(line.label, pitch = pitch, color = ink, grid = null)
                    DotRow(ink, Modifier.weight(1f).height(cell * 7f).padding(horizontal = cell * 3f), row = 6, lit = { it % 2 == 0 })
                    LcdText(line.value, pitch = pitch, color = ink, grid = null)
                }
                ReceiptLine.Rule -> DotRow(ink, Modifier.fillMaxWidth().height(cell * 7f), row = 3, lit = { it % 4 != 3 })
            }
        }
    }
}

/** One row of the dot matrix across the width: dot columns where [lit] says, on [row] (0 = top, 6 = baseline). */
@Composable
private fun DotRow(color: Color, modifier: Modifier, row: Int, lit: (column: Int) -> Boolean) {
    Canvas(modifier) {
        val p = size.height / 7f
        val dot = p * 0.8f
        val inset = (p - dot) / 2f
        val columns = (size.width / p).toInt()
        val r = CornerRadius(dot * 0.18f)
        for (col in 0 until columns) {
            if (lit(col)) drawRoundRect(color, Offset(col * p + inset, row * p + inset), Size(dot, dot), r)
        }
    }
}

/** The receipt's outline: small rounded top corners, the bottom torn in a zig-zag (teeth 8 dp wide, 4 dp deep). */
object ReceiptShape : Shape {
    val TOOTH_DEPTH: Dp = 4.dp
    private val TOOTH_WIDTH: Dp = 8.dp
    private val CORNER: Dp = 3.dp

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val depth = with(density) { TOOTH_DEPTH.toPx() }
        val tooth = with(density) { TOOTH_WIDTH.toPx() }
        val r = with(density) { CORNER.toPx() }
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, r)
            quadraticTo(0f, 0f, r, 0f)
            lineTo(w - r, 0f)
            quadraticTo(w, 0f, w, r)
            lineTo(w, h - depth)
            // The torn foot, right to left: down to a point, up to the edge line, every half tooth.
            val teeth = (w / tooth).toInt().coerceAtLeast(1)
            val step = w / teeth
            for (i in teeth - 1 downTo 0) {
                lineTo(i * step + step / 2f, h)
                lineTo(i * step, h - depth)
            }
            close()
        }
        return Outline.Generic(path)
    }
}
