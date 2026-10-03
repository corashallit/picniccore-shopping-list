package earth.cora.picnic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.hypot

/**
 * WP4 polish (§4.6, §4.7, §4.8, §4.3, §15): the fixed-size pieces grow with the font instead of clipping it, at font
 * scale 1.3 and 2.0 (and 3.0, past Android's largest, to prove the pills grow); at 1.0 every one keeps its drawn size.
 * The ruled field's rules follow its lines at any scale; the LCD star's blink never moves a dot.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class LargeTextComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    private val screen by lazy { SwappableContent(compose) }
    private val px = 3f

    private fun show(fontScale: Float, content: @Composable () -> Unit) {
        screen.show {
            CompositionLocalProvider(LocalDensity provides Density(px, fontScale)) {
                PicnicTheme(ThemeVariant.Day) {
                    Box(Modifier.background(Color.White).padding(12.dp)) { content() }
                }
            }
        }
    }

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    /** Every text node under [root]: its layout fits its box (no lines or height lost) and no ancestor clips it. */
    private fun assertTextWhole(root: SemanticsNode, what: String) {
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap { walk(it) }
        val texts = walk(root).filter { it.config.getOrNull(SemanticsProperties.Text) != null && it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }
        assertTrue("$what: has text", texts.isNotEmpty())
        for (t in texts) {
            val results = mutableListOf<TextLayoutResult>()
            t.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val r = results.single()
            assertFalse("$what '${r.layoutInput.text}': lines cut (${r.size} for ${r.multiParagraph.height})", r.didOverflowHeight || r.multiParagraph.height > r.size.height + 0.5f)
            for (line in 0 until r.lineCount) {
                assertFalse("$what '${r.layoutInput.text}': line $line ellipsized", r.isLineEllipsized(line))
                assertTrue("$what '${r.layoutInput.text}': line $line cut on the right (${r.getLineRight(line)} in ${r.size})", r.getLineRight(line) <= r.size.width + 0.5f)
            }
            assertTrue("$what '${r.layoutInput.text}': clipped by an ancestor ${t.clippedAndWhole()}", t.isWhole())
        }
    }

    /**
     * The aisle number circle (§2.7, §4.10, §4.7): 24 / 20 dp at 1.0, and from 1.3 up it grows so its numeral, which
     * scales with the font, is never cut: the numeral's ink inside the ring is exactly the ink of the same numeral drawn
     * on its own (nothing lost under the ring or outside the circle).
     */
    @Test
    fun numberCircleGrowsWithItsNumeral() {
        val cases = listOf(4 to 24, 12 to 24, 24 to 24, 8 to 20, 24 to 20, 7 to 28)
        for (scale in listOf(1f, 1.3f, 2f)) for ((n, size) in cases) {
            val look = AisleLooks.of(n)
            show(scale) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.testTag("circle")) { NumberCircle(n, look, size = size.dp) }
                    val style = if (size <= 20) PicnicTheme.type.chipNumber else PicnicTheme.type.tabNumber
                    Box(Modifier.testTag("bare").background(Color.White)) { Text("$n", style = style.copy(color = look.family.trace), maxLines = 1) }
                }
            }
            val what = "$n in $size dp at $scale"
            val circle = node("circle").boundsInRoot
            if (scale == 1f) {
                assertEquals("$what: drawn size kept", size * px, circle.width, 0.5f)
                assertEquals("$what: drawn size kept", size * px, circle.height, 0.5f)
            }
            assertEquals("$what: still a circle", circle.width, circle.height, 0.5f)
            val trace = look.family.trace.toArgb()
            val isInk: (Int) -> Boolean = { near(it, trace, 90) }
            val img = compose.onNodeWithTag("circle", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            // Only the inside of the ring: the 2 dp ring is trace-coloured too.
            val r = img.width / 2f
            val inner = r - 2f * px - 1f
            val inside = android.graphics.Bitmap.createBitmap(img.width, img.height, android.graphics.Bitmap.Config.ARGB_8888)
            for (y in 0 until img.height) for (x in 0 until img.width) {
                val keep = hypot(x + 0.5f - r, y + 0.5f - img.height / 2f) <= inner
                inside.setPixel(x, y, if (keep) img.getPixel(x, y) else Color.White.toArgb())
            }
            val inCircle = inkBox(inside, isInk = isInk)
            val bare = inkBox(compose.onNodeWithTag("bare", useUnmergedTree = true).captureToImage().asAndroidBitmap(), isInk = isInk)
            assertNotNull(what, inCircle)
            assertNotNull(what, bare)
            assertTrue("$what: numeral width kept inside the ring (${bare!!.width()} vs ${inCircle!!.width()})", abs(bare.width() - inCircle.width()) <= 1)
            assertTrue("$what: numeral height kept inside the ring (${bare.height()} vs ${inCircle.height()})", abs(bare.height() - inCircle.height()) <= 1)
        }
    }

    /**
     * Chips (§4.7) and pills (§4.6): at 1.0 the label chip's face is 32 dp, the store chip 22 dp and the primary pill
     * 52 dp; at larger scales they grow and no label is cut (by its own box or a clip), up to 3.0.
     */
    @Test
    fun chipsAndPillsGrowInsteadOfClipping() {
        for (scale in listOf(1f, 1.3f, 2f, 3f)) {
            show(scale) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.testTag("chip")) { PicnicChip("Favourites", selected = true, onClick = {}) }
                    Box(Modifier.testTag("face")) { ChipFace(fill = Families.ramune.pastel) { Text("Favourites", style = PicnicTheme.type.label, maxLines = 1) } }
                    Box(Modifier.testTag("aisle")) { AisleChip(3, "Baking", AisleLooks.of(3), selected = false, onClick = {}) }
                    Box(Modifier.testTag("source")) { SourceChip("pancake recipe", onClick = {}) }
                    Box(Modifier.testTag("storeChip")) { StoreAisleChip(12) }
                    Box(Modifier.testTag("rarity")) { RarityChip(jackpot = true) }
                    // The two chips' words drawn on their own, to compare their ink with.
                    Box(Modifier.testTag("storeBare").background(Color.White)) {
                        Text("A12", style = PicnicTheme.type.label.copy(color = PicnicTheme.colors.ballpoint, fontSize = 13.sp, lineHeight = 15.sp), maxLines = 1)
                    }
                    Box(Modifier.testTag("rarityBare").background(Color.White)) {
                        Text("jackpot!", style = PicnicTheme.type.counter.copy(color = PicnicTheme.colors.ballpoint, fontSize = 13.sp, lineHeight = 15.sp), maxLines = 1)
                    }
                    Box(Modifier.testTag("secondary")) { PicnicButton("Try again", onClick = {}, style = PicnicButtonStyle.Secondary, glyph = PicnicGlyph.Refresh) }
                    Box(Modifier.testTag("disabled")) { PicnicButton("Reading", onClick = {}, enabled = false, busy = true) }
                    Box(Modifier.testTag("primary")) { PicnicButton("Stick all 5", onClick = {}, leadingStar = true) }
                }
            }
            for (tag in listOf("chip", "face", "aisle", "source", "secondary", "disabled")) assertTextWhole(node(tag), "$tag at $scale")
            // The primary's puffy label: its whole box inside the face, no clip into it.
            val puffy = compose.onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(PuffyFontSize), useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("primary label clipped at $scale ${puffy.clippedAndWhole()}", puffy.isWhole())
            val faces = compose.onAllNodes(androidx.compose.ui.test.hasTestTag(FACE_TAG), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
            val face = faces.first { it.contains(puffy.boundsInRoot.center) }
            assertTrue("primary label inside its face at $scale", puffy.boundsInRoot.top >= face.top - 0.5f && puffy.boundsInRoot.bottom <= face.bottom + 0.5f)
            // The store chip and the rarity chip hide their words from TalkBack: their letters are read off the pixels
            // instead, and must be exactly the letters of the same words drawn on their own (none cut by the face).
            val store = node("storeChip").boundsInRoot
            val rarity = node("rarity").boundsInRoot
            val dark: (Int) -> Boolean = { argb ->
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                (0.299 * r + 0.587 * g + 0.114 * b) < 110
            }
            fun inkOf(tag: String, skipLeftDp: Float): android.graphics.Rect? {
                val img = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
                val skip = (skipLeftDp * px).toInt()
                return inkBox(img, android.graphics.Rect(skip, 0, img.width, img.height), dark)
            }
            for ((chip, bare, skip) in listOf(Triple("storeChip", "storeBare", 2f + 7f + 13f + 2.5f), Triple("rarity", "rarityBare", 0f))) {
                val inChip = requireNotNull(inkOf(chip, skip)) { "$chip at $scale: no letters" }
                val alone = requireNotNull(inkOf(bare, 0f)) { "$bare at $scale: no letters" }
                assertTrue("$chip at $scale: letters cut (${inChip.width()}×${inChip.height()} for ${alone.width()}×${alone.height()})", inChip.width() >= alone.width() - 1 && inChip.height() >= alone.height() - 1)
            }
            if (scale == 1f) {
                assertEquals("label chip face 32 dp", 32f * px, node("face").boundsInRoot.height, 0.5f)
                assertEquals("store chip 22 dp", 22f * px, store.height, 0.5f)
                assertEquals("rarity chip 22 dp", 22f * px, rarity.height, 0.5f)
                assertEquals("primary face 52 dp (46 inside its border)", 46f * px, face.height, 0.5f)
            }
        }
    }

    /**
     * §4.8: a multi-line ruled field draws one rule per text line. At 1.0, 1.3 and 2.0 every rule sits at a line's
     * bottom edge (never through a line's letters) and every line has its rule.
     */
    @Test
    fun ruledFieldRulesFollowTheLines() {
        val note = "pancakes\nmaple syrup\nberries\nflour"
        for (scale in listOf(1f, 1.3f, 2f)) {
            show(scale) { PicnicTextField(note, {}, minLines = 4, modifier = Modifier.width(300.dp)) }
            val field = compose.onNode(hasSetTextAction()).fetchSemanticsNode()
            val results = mutableListOf<TextLayoutResult>()
            field.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val layout = results.single()
            assertEquals(4, layout.lineCount)
            val img = compose.onRoot().captureToImage().asAndroidBitmap()
            val rule = DayColors.rule.toArgb()
            val left = field.boundsInRoot.left.toInt() + 4
            val right = field.boundsInRoot.right.toInt() - 4
            // Rows where the rule colour runs across most of the field (letters interrupt it a little).
            val rows = (field.boundsInRoot.top.toInt() until field.boundsInRoot.bottom.toInt()).filter { y ->
                (left until right).count { x -> near(img.getPixel(x, y), rule, 16) } > (right - left) * 0.6f
            }
            assertTrue("rules drawn at $scale", rows.isNotEmpty())
            val top = field.boundsInRoot.top
            for (i in 0 until layout.lineCount) {
                val lineTop = top + layout.getLineTop(i)
                val lineBottom = top + layout.getLineBottom(i)
                assertTrue("line $i at $scale has its rule at its bottom ($lineBottom; rules $rows)", rows.any { it >= lineBottom - 1.5f * px - 1 && it <= lineBottom + 1 })
                assertTrue("a rule cuts line $i at $scale ($lineTop..$lineBottom; rules $rows)", rows.none { it > lineTop + 1 && it < lineBottom - 1.5f * px - 1 })
            }
        }
    }

    /**
     * §4.3: the LCD star blinks to 12 % without moving a dot. The line keeps one layout: dimmed or not, the text is the
     * same size and every digit's pixels are the same; only the star's cell changes.
     */
    @Test
    fun lcdStarBlinkNeverMovesADot() {
        for (alpha in listOf(1f, PicnicMotion.lcdBlinkAlpha)) {
            show(1f) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.testTag("lit")) { LcdText("10/24★", 1.9.dp) }
                    Box(Modifier.testTag("dim")) { LcdText("10/24★", 1.9.dp, starAlpha = alpha) }
                }
            }
            val lit = compose.onNodeWithTag("lit", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            val dim = compose.onNodeWithTag("dim", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            assertEquals(lit.width, dim.width)
            assertEquals(lit.height, dim.height)
            // The star is the last 7 columns of the 5-column glyphs + gaps: everything left of it is identical.
            val p = 1.9f * px
            val starLeft = (lit.width - 7 * p).toInt() - 1
            for (y in 0 until lit.height) for (x in 0 until starLeft) {
                assertTrue("digit dot moved at ($x, $y) with the star at $alpha", near(lit.getPixel(x, y), dim.getPixel(x, y), 8))
            }
            val changed = (0 until lit.height).sumOf { y -> (starLeft until lit.width).count { x -> !near(lit.getPixel(x, y), dim.getPixel(x, y), 8) } }
            if (alpha < 1f) assertTrue("the star dims", changed > 0) else assertEquals(0, changed)
        }
        // The chip itself keeps its size through a blink (its digits are one LcdText with the star). The test clock
        // runs infinite loops (the blink) only while it is driven by hand, so it is held before the chip is shown.
        compose.mainClock.autoAdvance = false
        show(1f) { Box(Modifier.testTag("chip")) { LcdChip(LcdState(LcdMode.Count, count = 10, total = 24)) } }
        repeat(3) {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeByFrame()
        }
        val before = node("chip").boundsInRoot
        val restImage = compose.onNodeWithTag("chip", useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val frames = ((PicnicMotion.lcdBlinkPeriod - PicnicMotion.lcdBlinkOn / 2) / 16L).toInt()
        repeat(frames) {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeByFrame()
        }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        val during = node("chip").boundsInRoot
        val blinkImage = compose.onNodeWithTag("chip", useUnmergedTree = true).captureToImage().asAndroidBitmap()
        assertTrue("the blink frame was reached", !restImage.sameAs(blinkImage))
        assertTrue("the chip moved or resized in its blink: $before vs $during", abs(before.width - during.width) < 0.5f && abs(before.left - during.left) < 0.5f)
        compose.mainClock.autoAdvance = true
    }
}
