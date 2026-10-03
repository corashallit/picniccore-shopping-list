package earth.cora.picnic.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.theme.gen.DecorManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

/**
 * Rule R3 (§4.6; checklist 62, 63): every button face has exactly one gloss crescent and one dot, inside the face, ≥4 dp
 * from the label and off the border; a left-starting label moves the gloss to the right cap; the only other white on a
 * face is its own label or glyph; the gloss is opaque white and never blurred.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ButtonGlossTest {
    @get:Rule
    val compose = createComposeRule()

    private val density = Density(3f)
    private val clearance = 4f * 3f

    // ---- draw ops (unit) ----

    @Test
    fun allowedWhiteOnly() {
        for (variant in ThemeVariant.entries) {
            val c = colorsFor(variant)
            for (kind in ButtonKind.entries) for (enabled in listOf(true, false)) for (star in listOf(false, true)) {
                val ops = faceOps(kind, enabled, c, leadingStar = star)
                val crescents = ops.filterIsInstance<FaceOp.Crescent>()
                val dots = ops.filterIsInstance<FaceOp.Dot>()
                if (enabled) {
                    assertEquals("$kind crescents", 1, crescents.size)
                    assertEquals("$kind dots", 1, dots.size)
                } else {
                    // Disabled pills: no gloss at all (round buttons fade to 50 % with their gloss instead).
                    assertEquals(0, crescents.size + dots.size)
                }
                // The only white ON a face: one crescent, one dot and the face's own label or glyph (the primary's white
                // label, the FAB's white plus, a Glow round button's white glyph). The face's own fill may be white
                // (secondary pills, Day round buttons): it is the face, not on it. Nothing else (no border, lens or ring).
                val whites = ops.filter { it.color == Color.White }
                for (w in whites) {
                    val ok = w is FaceOp.Fill || w is FaceOp.Crescent || w is FaceOp.Dot || w is FaceOp.Label || w is FaceOp.GlyphInk
                    assertTrue("$variant $kind enabled=$enabled: unexpected white ${w::class.simpleName}", ok)
                }
                assertEquals(1, ops.count { it is FaceOp.Label || it is FaceOp.GlyphInk })
                assertTrue(ops.none { it is FaceOp.Ring && it.color == Color.White })
                // The face is drawn first, the gloss right after it (under the label).
                assertTrue(ops.first() is FaceOp.Fill)
            }
        }
    }

    @Test
    fun opaqueWhite() {
        for (kind in ButtonKind.entries) {
            for (op in faceOps(kind, true, DayColors)) {
                assertEquals("never blurred", 0f, op.blur)
                if (op is FaceOp.Crescent || op is FaceOp.Dot) assertEquals("opaque white", Color.White, op.color)
            }
        }
    }

    // ---- geometry (pure) ----

    private fun insidePill(p: Offset, face: Size): Boolean {
        val r = face.height / 2f
        val cx = p.x.coerceIn(r, face.width - r)
        return hypot(p.x - cx, p.y - r) <= r + 0.01f
    }

    private fun assertPillGloss(face: Size, label: Rect, name: String): GlossGeometry {
        val g = ButtonGloss.of(FaceKind.Pill, face, label, density)
        for (p in g.outline) assertTrue("$name crescent point $p outside the face", insidePill(p, face))
        for (a in 0 until 360 step 15) {
            val rad = Math.toRadians(a.toDouble())
            val p = Offset(g.dotCenter.x + g.dotRadius * kotlin.math.cos(rad).toFloat(), g.dotCenter.y + g.dotRadius * kotlin.math.sin(rad).toFloat())
            assertTrue("$name dot outside the face", insidePill(p, face))
        }
        assertTrue("$name gloss within 4 dp of the label: ${g.distanceTo(label) / 3f} dp (face $face, label $label, dot ${g.dotCenter}, bounds ${g.bounds})", g.distanceTo(label) >= clearance)
        return g
    }

    @Test
    fun pillGeometryForShortLongAndLeftLabels() {
        val px = 3f
        val face = Size(300f * px, 46f * px)
        val band = 18f * px
        val mid = face.height / 2f
        // Short and long centred labels start after the 27 dp start padding: the gloss stays in the left cap.
        val short = Rect(120f * px, mid - band / 2, 180f * px, mid + band / 2)
        val long = Rect(27f * px, mid - band / 2, 279f * px, mid + band / 2)
        assertFalse(assertPillGloss(face, short, "short").mirrored)
        assertFalse(assertPillGloss(face, long, "long").mirrored)
        // A label starting at the left edge moves the gloss to the right cap, still clear of it.
        val left = Rect(6f * px, mid - band / 2, 200f * px, mid + band / 2)
        assertTrue(assertPillGloss(face, left, "left").mirrored)
    }

    // ---- geometry on the real buttons (Robolectric) ----

    private fun faceRects(): List<Rect> = compose.onAllNodesWithTag(FACE_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }

    private val screen by lazy { SwappableContent(compose) }

    private fun show(content: @Composable () -> Unit) {
        screen.show { PicnicTheme(variant = ThemeVariant.Day) { Column(Modifier.fillMaxWidth()) { content() } } }
    }

    @Test
    fun geometry() {
        val labels = listOf("Go", "Stick all 5 on my list", "Keep everything, uncheck it all")
        for (primary in listOf(true, false)) for (text in labels) {
            show {
                PicnicButton(
                    text, {}, style = if (primary) PicnicButtonStyle.Primary else PicnicButtonStyle.Secondary,
                    leadingStar = primary, glyph = if (primary) null else PicnicGlyph.Refresh,
                )
            }
            val face = faceRects().single()
            val textBounds = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot()
            val d = 3f
            // The label row: the leading star or glyph (18 dp + 6 dp gap) and the text, its ink band.
            val rowLeft = textBounds.left.value * d - 24f * d
            val row = Rect(rowLeft - face.left, textBounds.top.value * d - face.top, textBounds.right.value * d - face.left, textBounds.bottom.value * d - face.top)
            val fontPx = (if (primary) 18f else 16f) * d
            val g = assertPillGloss(face.size, labelInkBand(row, fontPx), "${if (primary) "primary" else "secondary"} '$text'")
            assertFalse(g.mirrored)
        }
    }

    /**
     * R3 at large text (§4.6, §15): at font scale 1.3 and 2.0 the label's ink band reaches the gloss's height, so the
     * label starts past the dot; the gloss stays in the left cap, inside the face and ≥ 4 dp from the label.
     */
    @Test
    fun geometryAtLargeText() {
        val labels = listOf("Go", "Stick all 2", "Try again", "Plain lines")
        for (scale in listOf(1.3f, 2f)) for (primary in listOf(true, false)) for (text in labels) {
            show {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides Density(3f, scale),
                ) {
                    PicnicButton(
                        text, {}, style = if (primary) PicnicButtonStyle.Primary else PicnicButtonStyle.Secondary,
                        leadingStar = primary, glyph = if (primary) null else PicnicGlyph.Refresh,
                    )
                }
            }
            val face = faceRects().single()
            val textBounds = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot()
            val d = 3f
            val rowLeft = textBounds.left.value * d - 24f * d
            val row = Rect(rowLeft - face.left, textBounds.top.value * d - face.top, textBounds.right.value * d - face.left, textBounds.bottom.value * d - face.top)
            // The label's real size: Android scales large text non-linearly, as the button does.
            val fontPx = with(Density(3f, scale)) { (if (primary) 18 else 16).sp.toPx() }
            val g = assertPillGloss(face.size, labelInkBand(row, fontPx), "${if (primary) "primary" else "secondary"} '$text' at $scale")
            assertFalse("$text at $scale: the gloss stays in the left cap", g.mirrored)
        }
    }

    @Test
    fun roundFacesKeepTheirGlyphClear() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        for ((glyph, faceDp) in listOf(PicnicGlyph.More to 34f, PicnicGlyph.Back to 34f, PicnicGlyph.Add to 34f)) {
            val face = Size(faceDp * 3f, faceDp * 3f)
            val g = ButtonGloss.of(FaceKind.Circle, face, null, density)
            val art = VectorArtCache.get(resources, DecorManifest.glyphs.getValue(glyph.key))
            val scale = 24f * 3f / art.viewport.width
            val offset = (face.width - 24f * 3f) / 2f
            for (p in art.paths) for ((contour, _) in DashSpec.contoursOf(p.path)) {
                val b = contour.getBounds()
                val half = p.strokeWidth / 2f
                val ink = Rect(offset + (b.left - half) * scale, offset + (b.top - half) * scale, offset + (b.right + half) * scale, offset + (b.bottom + half) * scale)
                assertTrue("$glyph: gloss within 4 dp of its ink (${g.distanceTo(ink) / 3f} dp)", g.distanceTo(ink) >= clearance)
            }
            val r = face.width / 2f
            for (p in g.outline) assertTrue("$glyph crescent inside the face", hypot(p.x - r, p.y - r) <= r)
            assertTrue(hypot(g.dotCenter.x - r, g.dotCenter.y - r) + g.dotRadius <= r)
        }
        // The add-bar faces (46 dp inside their border) and their hand-drawn 24 dp glyphs (ink inside 3..21).
        val face = Size(46f * 3f, 46f * 3f)
        val g = ButtonGloss.of(FaceKind.Circle, face, null, density)
        val ink = Rect((11f + 3f) * 3f, (11f + 3f) * 3f, (11f + 21f) * 3f, (11f + 21f) * 3f)
        assertTrue(g.distanceTo(ink) >= clearance)
        // The FAB's crimson-stroked plus.
        val fab = Size(64f * 3f, 64f * 3f)
        val fg = ButtonGloss.of(FaceKind.Circle, fab, null, density)
        val c = fab.width / 2f
        val arm = fab.width * FAB_PLUS_ARM
        val w = fabPlusWidth(fab.width, 3f) / 2f
        for (bar in listOf(Rect(c - arm - w, c - w, c + arm + w, c + w), Rect(c - w, c - arm - w, c + w, c + arm + w))) {
            assertTrue("FAB plus within 4 dp of the gloss", fg.distanceTo(bar) >= clearance)
        }
    }
}
