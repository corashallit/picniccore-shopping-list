package earth.cora.picnic.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import earth.cora.picnic.ui.theme.gen.DecorManifest

/**
 * Toolbar glyphs (docs/DESIGN_STICKER.md §3.4): 24 dp, ballpoint 2u line only, round caps, no border, no gloss. Drawn
 * from WP2's generated `sk_g_<name>` drawables ([DecorManifest.glyphs], tinted as a whole) where they exist, otherwise
 * by the theme as vector strokes on the same 24-unit grid. CONTRACT: screens use these names; the enum may grow.
 * [key] is the spec's (and the drawable's) name; [placeholder] is a plain-text label for logs (never drawn).
 */
enum class PicnicGlyph(val placeholder: String, val key: String) {
    Add("+", "add"), Close("×", "close"), Check("✓", "check"), Back("←", "back"), More("⋯", "more"),
    Camera("◉", "camera"), Gallery("▣", "gallery"), Mic("●", "mic"), Stop("■", "stop"), Send("➤", "send"),
    Settings("⚙", "settings"), Aisles("≡", "aisles"), Lists("☰", "lists"), Edit("✎", "edit"), Delete("⌫", "delete"),
    Undo("↶", "undo"), Refresh("↻", "refresh"), Sparkle("✦", "sparkle"), Expand("▾", "expand"), Collapse("▴", "collapse"),
    Link("↗", "link"), Drag("⋮", "drag"), Key("⚿", "key"),
    Eye("eye", "eye"), Web("web", "web"), Search("search", "search"), Sort("sort", "sort"),
    Scissors("scissors", "scissors"), Album("album", "album"), Trip("trip", "trip"), Cassette("cassette", "cassette"),
    Envelope("envelope", "envelope"), Polaroid("polaroid", "polaroid"), TornPaper("torn", "torn"), Jar("jar", "jar"),
    Heart("heart", "heart"), Paste("paste", "paste"), Basket("basket", "basket"),
}

/** Draws a UI glyph at [size] (default 24 dp) in [tint] (default the page ink). Decorative when [contentDescription] is null. */
@Composable
fun Glyph(
    glyph: PicnicGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = PicnicTheme.colors.ink,
    size: Dp = 24.dp,
) {
    val semantics = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    val drawable = DecorManifest.glyphs[glyph.key]
    val art = drawable?.let { rememberVectorArt(it) }
    Canvas(modifier.then(semantics).size(size)) {
        if (art != null) drawArt(art, paint = solid(tint)) else inGrid(24f) { drawGlyph(glyph, tint) }
    }
}

private const val LINE = 2.2f

private fun DrawScope.stroke(tint: Color, a: Offset, b: Offset) = inkLine(tint, LINE, a, b)

private fun DrawScope.stroke(tint: Color, a: Offset, b: Offset, c: Offset) = inkLine(tint, LINE, a, b, c)

private fun DrawScope.stroke(tint: Color, a: Offset, b: Offset, c: Offset, d: Offset) = inkLine(tint, LINE, a, b, c, d)

private fun DrawScope.stroke(tint: Color, a: Offset, b: Offset, c: Offset, d: Offset, e: Offset) = inkLine(tint, LINE, a, b, c, d, e)

private fun o(x: Float, y: Float) = Offset(x, y)

/** Draws [glyph] in a 24 x 24 grid. Shared with the theme's own decorations (chevrons, ticks). */
internal fun DrawScope.drawGlyph(glyph: PicnicGlyph, tint: Color) {
    val line = Stroke(width = LINE, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (glyph) {
        PicnicGlyph.Add -> {
            stroke(tint, o(12f, 5f), o(12f, 19f))
            stroke(tint, o(5f, 12f), o(19f, 12f))
        }
        PicnicGlyph.Close -> {
            stroke(tint, o(6.5f, 6.5f), o(17.5f, 17.5f))
            stroke(tint, o(17.5f, 6.5f), o(6.5f, 17.5f))
        }
        PicnicGlyph.Check -> stroke(tint, o(5f, 12.5f), o(10f, 17.5f), o(19f, 7f))
        PicnicGlyph.Back -> {
            stroke(tint, o(19f, 12f), o(5.5f, 12f))
            stroke(tint, o(11f, 6f), o(5f, 12f), o(11f, 18f))
        }
        PicnicGlyph.More -> listOf(6f, 12f, 18f).forEach { drawCircle(tint, 1.9f, o(it, 12f)) }
        PicnicGlyph.Camera -> {
            drawPath(
                path {
                    moveTo(3.5f, 9.5f); quadraticTo(3.5f, 7f, 6f, 7f); lineTo(8f, 7f); lineTo(9.5f, 4.5f)
                    lineTo(14.5f, 4.5f); lineTo(16f, 7f); lineTo(18f, 7f); quadraticTo(20.5f, 7f, 20.5f, 9.5f)
                    lineTo(20.5f, 16.5f); quadraticTo(20.5f, 19f, 18f, 19f); lineTo(6f, 19f)
                    quadraticTo(3.5f, 19f, 3.5f, 16.5f); close()
                },
                tint, style = line,
            )
            drawCircle(tint, 3.4f, o(12f, 12.8f), style = line)
        }
        PicnicGlyph.Gallery -> {
            drawRoundRect(tint, o(3.5f, 4.5f), Size(17f, 15f), CornerRadius(3f), style = line)
            stroke(tint, o(4f, 17f), o(9f, 11.5f), o(13.5f, 16f), o(16f, 13.5f), o(20f, 17.5f))
            drawCircle(tint, 1.7f, o(15.5f, 8.8f))
        }
        PicnicGlyph.Mic -> {
            drawRoundRect(tint, o(9f, 3f), Size(6f, 11.5f), CornerRadius(3f), style = line)
            drawArc(tint, 0f, 180f, false, o(6f, 6f), Size(12f, 11.5f), style = line)
            stroke(tint, o(12f, 17.5f), o(12f, 21f))
            stroke(tint, o(8.5f, 21f), o(15.5f, 21f))
        }
        PicnicGlyph.Stop -> drawRoundRect(tint, o(6f, 6f), Size(12f, 12f), CornerRadius(2.5f))
        PicnicGlyph.Send -> {
            drawPath(path { moveTo(4f, 11.5f); lineTo(20f, 4f); lineTo(14f, 20f); lineTo(11f, 13f); close() }, tint, style = line)
            stroke(tint, o(11f, 13f), o(20f, 4f))
        }
        PicnicGlyph.Settings -> {
            drawCircle(tint, 6.2f, o(12f, 12f), style = line)
            drawCircle(tint, 2.4f, o(12f, 12f), style = line)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val c = kotlin.math.cos(a).toFloat()
                val s = kotlin.math.sin(a).toFloat()
                stroke(tint, o(12f + 6.6f * c, 12f + 6.6f * s), o(12f + 9.4f * c, 12f + 9.4f * s))
            }
        }
        PicnicGlyph.Aisles -> listOf(7f, 12f, 17f).forEach { y ->
            drawCircle(tint, 1.5f, o(5f, y))
            stroke(tint, o(9f, y), o(20f, y))
        }
        PicnicGlyph.Lists -> {
            drawRoundRect(tint, o(5f, 4.5f), Size(14f, 16.5f), CornerRadius(2.5f), style = line)
            drawRoundRect(tint, o(9f, 2.5f), Size(6f, 3.5f), CornerRadius(1.5f))
            stroke(tint, o(8.5f, 10.5f), o(15.5f, 10.5f))
            stroke(tint, o(8.5f, 14f), o(15.5f, 14f))
            stroke(tint, o(8.5f, 17.5f), o(12.5f, 17.5f))
        }
        PicnicGlyph.Edit -> {
            drawPath(path { moveTo(4.5f, 19.5f); lineTo(5.5f, 15f); lineTo(15.5f, 5f); lineTo(19f, 8.5f); lineTo(9f, 18.5f); close() }, tint, style = line)
            stroke(tint, o(13.5f, 7f), o(17f, 10.5f))
        }
        PicnicGlyph.Delete -> {
            stroke(tint, o(4f, 6.5f), o(20f, 6.5f))
            stroke(tint, o(9.5f, 6.5f), o(10f, 4f), o(14f, 4f), o(14.5f, 6.5f))
            stroke(tint, o(6f, 6.5f), o(7f, 20f), o(17f, 20f), o(18f, 6.5f))
            stroke(tint, o(10f, 10f), o(10f, 16.5f))
            stroke(tint, o(14f, 10f), o(14f, 16.5f))
        }
        PicnicGlyph.Undo -> {
            drawPath(
                path { moveTo(5.5f, 9f); lineTo(14f, 9f); cubicTo(20.5f, 9f, 20.5f, 19f, 14f, 19f); lineTo(8f, 19f) },
                tint, style = line,
            )
            stroke(tint, o(9f, 5f), o(5f, 9f), o(9f, 13f))
        }
        PicnicGlyph.Refresh -> {
            drawArc(tint, -60f, 290f, false, o(5f, 5f), Size(14f, 14f), style = line)
            stroke(tint, o(15.2f, 2.8f), o(15.6f, 6.6f), o(19.4f, 6.2f))
        }
        PicnicGlyph.Sparkle -> drawPath(sparklePath(o(12f, 12f), 9.5f), tint)
        PicnicGlyph.Expand -> stroke(tint, o(6f, 9f), o(12f, 15f), o(18f, 9f))
        PicnicGlyph.Collapse -> stroke(tint, o(6f, 15f), o(12f, 9f), o(18f, 15f))
        PicnicGlyph.Link -> {
            stroke(tint, o(13.5f, 4.5f), o(19.5f, 4.5f), o(19.5f, 10.5f))
            stroke(tint, o(19.5f, 4.5f), o(11f, 13f))
            stroke(tint, o(17.5f, 14f), o(17.5f, 19.5f), o(4.5f, 19.5f), o(4.5f, 6.5f), o(10f, 6.5f))
        }
        PicnicGlyph.Drag -> listOf(9f, 15f).forEach { x -> listOf(6f, 12f, 18f).forEach { y -> drawCircle(tint, 1.7f, o(x, y)) } }
        PicnicGlyph.Key -> {
            drawCircle(tint, 4f, o(8f, 12f), style = line)
            stroke(tint, o(12f, 12f), o(20.5f, 12f))
            stroke(tint, o(17f, 12f), o(17f, 15.5f))
            stroke(tint, o(20f, 12f), o(20f, 15f))
        }
        PicnicGlyph.Eye -> {
            drawPath(path { moveTo(3f, 12f); quadraticTo(12f, 3.5f, 21f, 12f); quadraticTo(12f, 20.5f, 3f, 12f); close() }, tint, style = line)
            drawCircle(tint, 3f, o(12f, 12f), style = line)
        }
        PicnicGlyph.Web -> {
            drawCircle(tint, 8.5f, o(12f, 12f), style = line)
            stroke(tint, o(3.5f, 12f), o(20.5f, 12f))
            drawOval(tint, o(8f, 3.5f), Size(8f, 17f), style = line)
        }
        PicnicGlyph.Search -> {
            drawCircle(tint, 6f, o(10.5f, 10.5f), style = line)
            stroke(tint, o(15f, 15f), o(20f, 20f))
        }
        PicnicGlyph.Sort -> {
            stroke(tint, o(4f, 7f), o(16f, 7f))
            stroke(tint, o(4f, 12f), o(13f, 12f))
            stroke(tint, o(4f, 17f), o(10f, 17f))
            stroke(tint, o(18.5f, 9f), o(18.5f, 19f))
            stroke(tint, o(16f, 16.5f), o(18.5f, 19f), o(21f, 16.5f))
        }
        PicnicGlyph.Scissors -> {
            drawCircle(tint, 2.8f, o(6.5f, 7f), style = line)
            drawCircle(tint, 2.8f, o(6.5f, 17f), style = line)
            stroke(tint, o(8.8f, 8.6f), o(20f, 16.5f))
            stroke(tint, o(8.8f, 15.4f), o(20f, 7.5f))
        }
        PicnicGlyph.Album -> {
            drawRoundRect(tint, o(5f, 3.5f), Size(14.5f, 17f), CornerRadius(2.5f), style = line)
            stroke(tint, o(8.5f, 3.5f), o(8.5f, 20.5f))
            drawPath(starPath(o(14f, 10.5f), 3.2f), tint)
        }
        PicnicGlyph.Trip -> {
            drawRoundRect(tint, o(3.5f, 5f), Size(17f, 14f), CornerRadius(2.5f), style = line)
            stroke(tint, o(12f, 5f), o(12f, 19f))
            drawCircle(tint, 1.8f, o(7.5f, 10.5f), style = line)
            drawCircle(tint, 1.8f, o(16.5f, 10.5f), style = line)
        }
        PicnicGlyph.Cassette -> {
            drawRoundRect(tint, o(3f, 6f), Size(18f, 12.5f), CornerRadius(2.5f), style = line)
            drawCircle(tint, 1.8f, o(9f, 11.5f), style = line)
            drawCircle(tint, 1.8f, o(15f, 11.5f), style = line)
            stroke(tint, o(7f, 18.5f), o(8.5f, 15.5f), o(15.5f, 15.5f), o(17f, 18.5f))
        }
        PicnicGlyph.Envelope -> {
            drawRoundRect(tint, o(3f, 6f), Size(18f, 13f), CornerRadius(2f), style = line)
            stroke(tint, o(3.5f, 7f), o(12f, 13.5f), o(20.5f, 7f))
            drawPath(heartPath(o(12f, 14.6f), 4.4f), tint)
        }
        PicnicGlyph.Polaroid -> {
            drawRoundRect(tint, o(3.5f, 4.5f), Size(17f, 15f), CornerRadius(2.5f), style = line)
            drawCircle(tint, 3.6f, o(12f, 12.2f), style = line)
            drawRoundRect(tint, o(14.5f, 6.8f), Size(3.5f, 2f), CornerRadius(1f))
            stroke(tint, o(6f, 7.5f), o(8.5f, 7.5f))
        }
        PicnicGlyph.TornPaper -> {
            drawPath(
                path {
                    moveTo(5f, 3.5f); lineTo(19f, 3.5f); lineTo(19f, 17f); lineTo(16.5f, 19.5f); lineTo(14f, 17f)
                    lineTo(11.5f, 19.5f); lineTo(9f, 17f); lineTo(6.5f, 19.5f); lineTo(5f, 18f); close()
                },
                tint, style = line,
            )
            stroke(tint, o(8f, 8f), o(16f, 8f))
            stroke(tint, o(8f, 12f), o(13.5f, 12f))
        }
        PicnicGlyph.Jar -> {
            drawRoundRect(tint, o(7f, 3f), Size(10f, 3.5f), CornerRadius(1.2f), style = line)
            drawRoundRect(tint, o(5.5f, 6.5f), Size(13f, 14f), CornerRadius(3.5f), style = line)
            stroke(tint, o(8.5f, 12f), o(15.5f, 12f))
        }
        PicnicGlyph.Heart -> drawPath(heartPath(o(12f, 12.5f), 16f), tint, style = line)
        PicnicGlyph.Paste -> {
            drawRoundRect(tint, o(5f, 4.5f), Size(14f, 16.5f), CornerRadius(2.5f), style = line)
            drawRoundRect(tint, o(9f, 2.5f), Size(6f, 3.8f), CornerRadius(1.5f), style = line)
            stroke(tint, o(8.5f, 11f), o(15.5f, 11f))
            stroke(tint, o(8.5f, 15f), o(13f, 15f))
        }
        PicnicGlyph.Basket -> {
            drawArc(tint, 180f, 180f, false, o(7f, 4f), Size(10f, 10f), style = line)
            drawPath(path { moveTo(3.5f, 9.5f); lineTo(20.5f, 9.5f); lineTo(18.5f, 19f); lineTo(5.5f, 19f); close() }, tint, style = line)
            stroke(tint, o(9.5f, 12.5f), o(10f, 16.5f))
            stroke(tint, o(14.5f, 12.5f), o(14f, 16.5f))
        }
    }
}
