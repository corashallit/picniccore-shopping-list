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

/**
 * UI glyphs (not item icons). The app does not depend on material-icons: every glyph is drawn by
 * the theme layer as vector strokes on a 24-unit grid (round caps, 2.2-unit line). CONTRACT: screens
 * use these names; the art pass may redraw them and the enum may grow.
 * [placeholder] is a plain-text label kept for debugging/logs (it is never drawn).
 */
enum class PicnicGlyph(val placeholder: String) {
    Add("+"), Close("×"), Check("✓"), Back("←"), More("⋯"),
    Camera("◉"), Gallery("▣"), Mic("●"), Stop("■"), Send("➤"),
    Settings("⚙"), Aisles("≡"), Lists("☰"), Edit("✎"), Delete("⌫"),
    Undo("↶"), Refresh("↻"), Sparkle("✦"), Expand("▾"), Collapse("▴"),
    Link("↗"), Drag("⋮"), Key("⚿"),
}

/** Draws a UI glyph at [size] (default 24 dp) in [tint] (default ink). Decorative when [contentDescription] is null. */
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
    Canvas(modifier.then(semantics).size(size)) {
        inGrid(24f) { drawGlyph(glyph, tint) }
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
    }
}
