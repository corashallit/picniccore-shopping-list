package earth.cora.picnic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot as layoutBoundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.displayName
import earth.cora.picnic.ui.a11y.TextOverflowed
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

/**
 * WP4 polish (§4.6, §4.7, §4.8, §4.3, §15): the fixed-size pieces grow with the font instead of clipping it, at font
 * scale 1.3 and 2.0 (and 3.0, past Android's largest, to prove the pills grow); at 1.0 every one keeps its drawn size.
 * The ruled field's rules follow its lines at any scale; the LCD star's blink never moves a dot.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
open class LargeTextComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    private val screen by lazy { SwappableContent(compose) }
    private val px = 3f

    /**
     * The density the pieces are shown at for [fontScale]: Compose's linear one here; [LargeTextPlatformScaleTest] runs
     * every test again under Android's own non-linear font scaling (what a phone at 130 % or 200 % uses).
     */
    protected open fun densityAt(density: Float, fontScale: Float): Density = Density(density, fontScale)

    private fun show(fontScale: Float, density: Float = px, content: @Composable () -> Unit) {
        screen.show {
            CompositionLocalProvider(LocalDensity provides densityAt(density, fontScale)) {
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
     * The aisle number circle (§2.7, §4.10, §4.7, item 337): 20 / 24 / 26 dp at 1.0, and it grows until its ring clears
     * its numeral, which scales with the font. For every numeral 1 to 24, at font scale 1.0, 1.15, 1.3, 1.5 and 2.0 and
     * at 2.625 and 3 px per dp, no numeral ink lies within 0.75 dp of the ring's inner edge: read off the pixels by their
     * distance from the centre, so a glyph's corner reaching the ring's curve counts (a bounding box inside the ring would
     * miss it). Half a px is allowed for anti-aliasing. At 1.0 every circle keeps its drawn size.
     */
    @Test
    @Config(qualifiers = "w360dp-h1600dp-xxhdpi")
    fun numberCircleGrowsWithItsNumeral() {
        val failures = mutableListOf<String>()
        var checked = 0
        for (density in listOf(3f, 2.625f)) for (scale in listOf(1f, 1.15f, 1.3f, 1.5f, 2f)) for (size in listOf(20, 24, 26)) {
            show(scale, density) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in (1..24).chunked(6)) {
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (n in row) Box(Modifier.testTag("circle$n")) { NumberCircle(n, AisleLooks.of(n), size = size.dp) }
                        }
                    }
                }
            }
            val img = compose.onRoot().captureToImage().asAndroidBitmap()
            val drawn = Density(density).run { size.dp.roundToPx() }
            for (n in 1..24) {
                val what = "$n in $size dp at $scale, ${density}x"
                val b = node("circle$n").boundsInRoot
                if (scale == 1f) {
                    if (abs(b.width - drawn) > 0.5f || abs(b.height - drawn) > 0.5f) failures += "$what: drawn size not kept (${b.width} px for $drawn)"
                }
                if (abs(b.width - b.height) > 0.5f) failures += "$what: not a circle (${b.width} × ${b.height})"
                val gap = numeralRingGap(img, b, density, AisleLooks.of(n).family.trace.toArgb())
                if (gap == null) failures += "$what: no numeral ink"
                else if (gap < 0.75f * density - 0.5f) failures += "$what: ink ${"%.2f".format(gap / density)} dp from the ring"
                checked++
            }
        }
        assertEquals(24 * 5 * 3 * 2, checked)
        assertTrue("number circles: ${failures.joinToString("\n")}", failures.isEmpty())
    }

    /**
     * The index tab's number circle is the kit's (§2.7 "the circle grows when a large font needs it", §4.10, item 337):
     * on strip and tray tabs, for every numeral 1 to 24 at 1.0, 1.3 and 2.0 and at 2.625 and 3 px per dp, its ink keeps
     * 0.75 dp from the ring (read off the pixels as [numberCircleGrowsWithItsNumeral] does). At 1.0 the circle is its
     * drawn 24 dp; at 2.0 it has grown beside the name, which keeps its whole word.
     */
    @Test
    @Config(qualifiers = "w360dp-h1600dp-xxhdpi")
    fun tabNumbersGrowWithTheirName() {
        val bounds = HashMap<Int, androidx.compose.ui.geometry.Rect>()
        val failures = mutableListOf<String>()
        var checked = 0
        for (density in listOf(3f, 2.625f)) for (scale in listOf(1f, 1.3f, 2f)) for (size in TabSize.entries) {
            bounds.clear()
            show(scale, density) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (n in 1..24) Box(Modifier.testTag("tab$n")) {
                        IndexTab(n, "Tea", AisleLooks.of(n), size, ballpoint = size == TabSize.Tray, numberModifier = Modifier.onGloballyPositioned { bounds[n] = it.layoutBoundsInRoot() })
                    }
                }
            }
            val img = compose.onRoot().captureToImage().asAndroidBitmap()
            val drawn = Density(density).run { 24.dp.roundToPx() }
            for (n in 1..24) {
                val what = "$n on a $size tab at $scale, ${density}x"
                val b = bounds.getValue(n)
                if (scale == 1f && (abs(b.width - drawn) > 0.5f || abs(b.height - drawn) > 0.5f)) failures += "$what: drawn size not kept (${b.width} px for $drawn)"
                if (scale == 2f && b.width <= drawn + 0.5f) failures += "$what: the circle never grew (${b.width} px)"
                if (abs(b.width - b.height) > 0.5f) failures += "$what: not a circle (${b.width} × ${b.height})"
                val gap = numeralRingGap(img, b, density, AisleLooks.of(n).family.trace.toArgb())
                if (gap == null) failures += "$what: no numeral ink"
                else if (gap < 0.75f * density - 0.5f) failures += "$what: ink ${"%.2f".format(gap / density)} dp from the ring"
                if (walkAll(node("tab$n")).any { it.config.getOrNull(TextOverflowed) == true }) failures += "$what: \"Tea\" cut"
                checked++
            }
        }
        assertEquals(24 * 3 * 2 * TabSize.entries.size, checked)
        assertTrue("tab number circles: ${failures.joinToString("\n")}", failures.isEmpty())
    }

    /**
     * The tab's circle gives way to its name (§4.10: the name is what she reads, the number repeats it). "Produce" on
     * strip tabs from 150 to 340 dp wide, at 1.3 and 2.0, 2.625 and 3 px per dp, numerals 1, 20 and 24: the circle is
     * never under its 24 dp and grows (never shrinks) as the tab widens; it holds back from its full growth only while
     * the whole name needs the room, so the name is cut only where even a 24 dp circle leaves it too little; and however
     * far it holds back, the numeral's ink keeps 0.75 dp from the ring (read off the pixels). The widest tab draws the
     * circle at its full growth.
     */
    @Test
    @Config(qualifiers = "w360dp-h1600dp-xxhdpi")
    fun tabCircleGivesWayToTheName() {
        val widths = (150..340 step 10).toList()
        val bounds = HashMap<Int, androidx.compose.ui.geometry.Rect>()
        val failures = mutableListOf<String>()
        for (density in listOf(3f, 2.625f)) for (scale in listOf(1.3f, 2f)) for (n in listOf(1, 20, 24)) {
            bounds.clear()
            var full = 0f
            show(scale, density) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (w in widths) Box(Modifier.testTag("w$w")) {
                        IndexTab(n, "Produce", AisleLooks.of(n), TabSize.Strip, ballpoint = false, maxWidth = w.dp, numberModifier = Modifier.onGloballyPositioned { bounds[w] = it.layoutBoundsInRoot() })
                    }
                    Box(Modifier.testTag("alone")) { NumberCircle(n, AisleLooks.of(n), Modifier.onGloballyPositioned { full = it.size.width.toFloat() }, size = 24.dp) }
                }
            }
            val img = compose.onRoot().captureToImage().asAndroidBitmap()
            val least = Density(density).run { 24.dp.roundToPx() }
            var last = 0f
            for (w in widths) {
                val what = "$n in a $w dp tab at $scale, ${density}x"
                val b = bounds.getValue(w)
                val cut = walkAll(node("w$w")).any { it.config.getOrNull(TextOverflowed) == true }
                if (b.width < least - 0.5f) failures += "$what: circle ${b.width} px, under 24 dp"
                if (b.width < last - 0.5f) failures += "$what: circle shrank as the tab widened (${b.width} < $last px)"
                if (cut && b.width > least + 0.5f) failures += "$what: \"Produce\" cut beside a ${b.width} px circle that could give way"
                if (b.width > full + 0.5f) failures += "$what: circle ${b.width} px, past its full growth $full"
                val gap = numeralRingGap(img, b, density, AisleLooks.of(n).family.trace.toArgb())
                if (gap == null) failures += "$what: no numeral ink"
                else if (gap < 0.75f * density - 0.5f) failures += "$what: ink ${"%.2f".format(gap / density)} dp from the ring"
                last = b.width
            }
            if (abs(last - full) > 0.5f) failures += "$n at $scale, ${density}x: the widest tab's circle $last px is not its full $full px"
        }
        assertTrue("tab circles giving way: ${failures.joinToString("\n")}", failures.isEmpty())
    }

    /**
     * Chips (§4.7) and pills (§4.6): at 1.0 the label chip's face is 32 dp, the store chip 22 dp and the primary pill
     * 52 dp; at larger scales they grow and no label is cut (by its own box or a clip), up to 3.0. The two small chips
     * keep their label's place as they grow: the gap under the letters keeps its share of the gap over them, and the
     * store chip's basket glyph keeps its size beside its label (§4.7: the chip keeps its 1.0 proportions). A chip in a
     * slot too narrow for it: [smallChipsNeverDrawTheirWordUnder13sp].
     */
    @Test
    @Config(qualifiers = "w360dp-h1600dp-xxhdpi")
    fun chipsAndPillsGrowInsteadOfClipping() {
        val placeAtOne = mutableMapOf<String, Float>()
        var bareAtOne = 0f
        var glyphShareAtOne = 0f
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
            // The store chip's glyph scales as its label's text box grew from 1.0 (the bare "A12" box is that text box).
            val bareBox = node("storeBare").boundsInRoot.height
            if (scale == 1f) bareAtOne = bareBox
            val glyphDp = 13f * bareBox / bareAtOne
            for ((chip, bare, skip) in listOf(Triple("storeChip", "storeBare", 2f + 7f + glyphDp + 2.5f), Triple("rarity", "rarityBare", 0f))) {
                val inChip = requireNotNull(inkOf(chip, skip)) { "$chip at $scale: no letters" }
                val alone = requireNotNull(inkOf(bare, 0f)) { "$bare at $scale: no letters" }
                assertTrue("$chip at $scale: letters cut (${inChip.width()}×${inChip.height()} for ${alone.width()}×${alone.height()})", inChip.width() >= alone.width() - 1 && inChip.height() >= alone.height() - 1)
                // ...and they keep their place as the chip grows: never sinking onto the bottom border.
                val below = node(chip).boundsInRoot.height - inChip.bottom
                val place = below / inChip.top
                if (scale == 1f) placeAtOne[chip] = place
                assertTrue("$chip at $scale: letters sink (gap under / over ${"%.2f".format(place)}, at 1.0 ${"%.2f".format(placeAtOne[chip])})", place >= placeAtOne.getValue(chip) * 0.85f)
            }
            // The basket glyph keeps its share of the chip beside its label: its ink as tall, against the label's, as at 1.0.
            val storeImg = compose.onNodeWithTag("storeChip", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            val glyphInk = requireNotNull(inkBox(storeImg, android.graphics.Rect(((2f + 7f - 1f) * px).toInt(), 0, ((2f + 7f + glyphDp + 1f) * px).toInt(), storeImg.height), dark)) { "store glyph at $scale: no ink" }
            val labelInk = requireNotNull(inkOf("storeChip", 2f + 7f + glyphDp + 2.5f))
            val glyphShare = glyphInk.height().toFloat() / labelInk.height()
            if (scale == 1f) glyphShareAtOne = glyphShare
            assertEquals("store glyph beside its label at $scale (glyph ink ${glyphInk.height()} px, label ${labelInk.height()} px)", glyphShareAtOne, glyphShare, glyphShareAtOne * 0.12f)
            if (scale == 1f) {
                assertEquals("label chip face 32 dp", 32f * px, node("face").boundsInRoot.height, 0.5f)
                assertEquals("store chip 22 dp", 22f * px, store.height, 0.5f)
                assertEquals("rarity chip 22 dp", 22f * px, rarity.height, 0.5f)
                assertEquals("primary face 52 dp (46 inside its border)", 46f * px, face.height, 0.5f)
            }
        }
        longRealLabelsStayWhole()
    }

    /**
     * The spec's longest real labels in the letter-set sheet's 308 dp column (a 360 dp phone) and a 328 dp one, at 1.0,
     * 1.15 (Android's "Large"), 1.3 and 2.0: the New trip sheet's pills, the Edit sheet's "Draw a new sticker", the Add
     * sheet's "Stick all 12 on my list", a disabled "Reading… 6 of 9" and every default aisle in an aisle chip. Each
     * wraps rather than loses a word: every line whole, no "…", no TextOverflowed, nothing clipped, inside its face. A
     * label too long even for 2 lines ends in "…" and says so (TextOverflowed), never silently.
     */
    private fun longRealLabelsStayWhole() {
        val pills = listOf(
            Triple("Clear what I got", PicnicButtonStyle.Primary, null),
            Triple("Keep everything, uncheck it all", PicnicButtonStyle.Secondary, null),
            Triple("Draw a new sticker", PicnicButtonStyle.Secondary, PicnicGlyph.Sparkle),
            Triple("Stick all 12 on my list", PicnicButtonStyle.Primary, null),
        )
        val aisles = Aisles.parse(Aisles.DEFAULT_TEXT)
        for (column in listOf(308, 328)) for (scale in listOf(1f, 1.15f, 1.3f, 2f)) {
            show(scale) {
                Column(Modifier.width(column.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pills.forEachIndexed { i, (label, style, glyph) ->
                        Box(Modifier.testTag("pill$i")) {
                            PicnicButton(label, onClick = {}, style = style, glyph = glyph, leadingStar = label.startsWith("Stick"))
                        }
                    }
                    Box(Modifier.testTag("pillWide")) {
                        PicnicButton("Keep everything, uncheck it all", onClick = {}, style = PicnicButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
                    }
                    Box(Modifier.testTag("pillDisabled")) { PicnicButton("Reading… 6 of 9", onClick = {}, enabled = false, busy = true) }
                    aisles.forEach { a ->
                        Box(Modifier.testTag("aisle${a.position}")) { AisleChip(a.position, a.displayName(), AisleLooks.of(a.position), selected = a.position == 3, onClick = {}) }
                    }
                    Box(Modifier.testTag("aisleElse")) { AisleChip(null, "Everything else", AisleLooks.everythingElse, selected = false, onClick = {}) }
                    Box(Modifier.testTag("tooLong")) {
                        SourceChip("Hillcrest Market's weeknight pancakes with maple syrup, berries and a pot of orange pekoe tea for four", onClick = {})
                    }
                }
            }
            val tags = pills.indices.map { "pill$it" } + listOf("pillWide", "pillDisabled", "aisleElse") + aisles.map { "aisle${it.position}" }
            for (tag in tags) {
                val root = node(tag)
                val texts = walkTexts(root)
                val at = "$scale in $column dp"
                if (texts.isNotEmpty()) assertTextWhole(root, "$tag at $at")
                for (n in walkAll(root)) assertTrue("$tag at $at: TextOverflowed", n.config.getOrNull(TextOverflowed) != true)
                // Balanced breaks: a wrapped pill label is two even lines, never a long line and a stub ("…, uncheck / it all").
                if (tag in listOf("pill1", "pill2", "pillWide")) for (t in texts) {
                    val results = mutableListOf<TextLayoutResult>()
                    t.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                    val r = results.single()
                    if (r.lineCount == 2) {
                        val widths = (0 until 2).map { r.getLineRight(it) - r.getLineLeft(it) }
                        val lines = (0 until 2).joinToString(" / ") { r.layoutInput.text.text.substring(r.getLineStart(it), r.getLineEnd(it, visibleEnd = true)) }
                        assertTrue("$tag at $at: lopsided lines '$lines' (${widths.map { (it / px).toInt() }} dp)", widths.min() >= widths.max() * 0.5f)
                    }
                }
                // A puffy label (the candy pill): whole, unclipped and inside its face.
                for (n in walkAll(root).filter { it.config.getOrNull(PuffyFontSize) != null }) {
                    assertTrue("$tag at $at: puffy label clipped ${n.clippedAndWhole()}", n.isWhole())
                    assertTrue("$tag at $at: puffy label shrank below 15 sp", n.config[PuffyFontSize] >= 15f - 0.01f)
                }
                val faces = compose.onAllNodes(androidx.compose.ui.test.hasTestTag(FACE_TAG), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
                val box = root.boundsInRoot
                val face = faces.firstOrNull { box.contains(it.center) }
                if (face != null) {
                    for (t in walkAll(root).filter { it.config.getOrNull(SemanticsProperties.Text) != null }) {
                        val b = t.boundsInRoot
                        assertTrue("$tag at $at: label $b leaves its face $face", b.top >= face.top - 0.5f && b.bottom <= face.bottom + 0.5f && b.left >= face.left - 0.5f && b.right <= face.right + 0.5f)
                    }
                }
            }
            // At 2.0 the longest labels need their second line: the pill and the chip grew round it.
            if (scale == 2f) {
                val keep = walkTexts(node("pill1")).single()
                val results = mutableListOf<TextLayoutResult>()
                keep.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                assertEquals("'Keep everything, uncheck it all' wraps at 2.0", 2, results.single().lineCount)
            }
            // Past 2 lines: "…" and TextOverflowed, never a silent cut.
            val long = walkAll(node("tooLong")).filter { it.config.getOrNull(SemanticsProperties.Text) != null }
            assertTrue("the too-long source chip reports its cut", walkAll(node("tooLong")).any { it.config.getOrNull(TextOverflowed) == true })
            val results = mutableListOf<TextLayoutResult>()
            long.single().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            assertTrue("the too-long source chip ends in an ellipsis at $scale", (0 until results.single().lineCount).any { results.single().isLineEllipsized(it) })
        }
    }

    /**
     * §4.6 "Long labels", item 337: a wrapped pill keeps the room a one-line label has above and below it (never flush
     * with the face's edge), and on the secondary its first and last lines' ink keeps at least 3 dp from the inset ring
     * (read off the pixels: the highest and lowest ballpoint ink against the ring's inner edges, 4 dp inside the face).
     * Every real secondary label in the letter-set sheet's 308 dp column, a 328 dp one and a crowded 240 dp one, at 1.15,
     * 1.3 and 2.0; the candy and disabled pills keep their one-line room too.
     */
    @Test
    @Config(qualifiers = "w360dp-h1600dp-xxhdpi")
    fun wrappedPillsKeepTheirRoom() {
        val secondaries = listOf(
            "Keep everything, uncheck it all" to null,
            "Draw a new sticker" to PicnicGlyph.Sparkle,
            "Add as plain lines" to PicnicGlyph.Edit,
            "Open phone settings" to PicnicGlyph.Settings,
            "Try again" to PicnicGlyph.Refresh,
        )
        val ink = DayColors.ballpoint.toArgb()
        val ring = Families.periwinkle.trace.toArgb()
        val faces = { compose.onAllNodes(androidx.compose.ui.test.hasTestTag(FACE_TAG), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot } }
        fun faceOf(tag: String) = node(tag).boundsInRoot.let { box -> faces().first { box.contains(it.center) } }
        fun labelOf(tag: String) = walkAll(node(tag)).first { it.config.getOrNull(SemanticsProperties.Text) != null }.boundsInRoot
        var wrappedSecondaries = 0
        for (scale in listOf(1.15f, 1.3f, 2f)) for (column in listOf(308, 328, 240)) {
            val at = "$scale in $column dp"
            show(scale) {
                Column(Modifier.width(column.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    secondaries.forEachIndexed { i, (label, glyph) ->
                        Box(Modifier.testTag("s$i")) { PicnicButton(label, {}, style = PicnicButtonStyle.Secondary, glyph = glyph) }
                    }
                    Box(Modifier.testTag("sWide")) { PicnicButton("Keep everything, uncheck it all", {}, style = PicnicButtonStyle.Secondary, modifier = Modifier.fillMaxWidth()) }
                    Box(Modifier.testTag("candy")) { PicnicButton("Stick all 12 on my list", {}, leadingStar = true) }
                    Box(Modifier.testTag("disabled")) { PicnicButton("Keep everything, uncheck it all", {}, style = PicnicButtonStyle.Secondary, enabled = false) }
                    // One-line labels in each style: the room a wrapped one keeps.
                    Box(Modifier.testTag("candyOne")) { PicnicButton("Go", {}) }
                    Box(Modifier.testTag("secondaryOne")) { PicnicButton("Go", {}, style = PicnicButtonStyle.Secondary) }
                }
            }
            val img = compose.onRoot().captureToImage().asAndroidBitmap()
            fun room(tag: String): Float = faceOf(tag).height.let { h -> (h - labelOf(tag).height) / 2f }
            val candyRoom = room("candyOne")
            val secondaryRoom = room("secondaryOne")
            fun wrapped(tag: String, oneLine: String) = labelOf(tag).height > labelOf(oneLine).height * 1.4f
            fun assertRoom(tag: String, roomPx: Float) {
                val face = faceOf(tag)
                val label = labelOf(tag)
                assertTrue("$tag at $at: label ${label.top - face.top} px under the face's top, one line keeps $roomPx", label.top - face.top >= roomPx - 1f)
                assertTrue("$tag at $at: label ${face.bottom - label.bottom} px over the face's bottom, one line keeps $roomPx", face.bottom - label.bottom >= roomPx - 1f)
            }
            for (tag in secondaries.indices.map { "s$it" } + "sWide") {
                if (!wrapped(tag, "secondaryOne")) continue
                wrappedSecondaries++
                assertRoom(tag, secondaryRoom)
                // The label's ink rows (ballpoint, at least half-way from white, nearer the ink than the ring's colour).
                val face = faceOf(tag)
                val rows = (face.top.toInt() until face.bottom.toInt()).filter { y ->
                    (face.left.toInt() until face.right.toInt()).count { x -> isInkNotRing(img.getPixel(x, y), ink, ring) } >= 2
                }
                assertTrue("$tag at $at: no ink", rows.isNotEmpty())
                val ringTop = face.top + 4f * px
                val ringBottom = face.bottom - 4f * px
                val above = rows.first() - ringTop
                val below = ringBottom - (rows.last() + 1)
                assertTrue("$tag at $at: ink ${"%.2f".format(above / px)} dp under the ring", above >= 3f * px - 1f)
                assertTrue("$tag at $at: ink ${"%.2f".format(below / px)} dp over the ring", below >= 3f * px - 1f)
            }
            if (wrapped("candy", "candyOne")) assertRoom("candy", candyRoom)
            if (wrapped("disabled", "secondaryOne")) assertRoom("disabled", secondaryRoom)
        }
        assertTrue("wrapped secondary pills checked ($wrappedSecondaries)", wrappedSecondaries >= 15)
    }

    /** A pixel of a label's ink: at least half-way from white to [ink], and nearer [ink] than the ring's [ring] colour. */
    private fun isInkNotRing(argb: Int, ink: Int, ring: Int): Boolean {
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        fun lum(c: Int) = (ch(c, 16) + ch(c, 8) + ch(c, 0)) / 3f
        fun d2(a: Int, b: Int) = listOf(16, 8, 0).sumOf { s -> (ch(a, s) - ch(b, s)).let { it * it } }
        return lum(argb) <= (255f + lum(ink)) / 2f && d2(argb, ink) < d2(argb, ring)
    }

    private fun walkAll(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap { walkAll(it) }

    private fun walkTexts(n: SemanticsNode): List<SemanticsNode> =
        walkAll(n).filter { it.config.getOrNull(SemanticsProperties.Text) != null && it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }

    /**
     * §4.8: a multi-line ruled field draws one rule per text line. At 1.0, 1.3 and 2.0 every rule sits at a line's
     * bottom edge (never through a line's letters) and every line has its rule.
     */
    @Test
    fun ruledFieldRulesFollowTheLines() {
        val note = "pancakes\nmaple syrup\nberries\nflour"
        for (scale in listOf(1f, 1.3f, 2f)) {
            show(scale) { PicnicTextField(note, {}, minLines = 4, modifier = Modifier.width(300.dp)) }
            assertRulesFollowLines(4, "default field at $scale")
        }
    }

    /**
     * §4.8 with the caller's own line height: a photo transcript (Klee One 15 sp on 22 sp lines, §2.7) keeps its 22 sp
     * lines in a ruled field, and the rules follow them at 1.0, 1.3 and 2.0 (not the default 24 sp pitch).
     */
    @Test
    fun ruledFieldFollowsTheCallersLineHeight() {
        val note = "two lemons\nfeta, 300 g\noat milk\neggs"
        for (scale in listOf(1f, 1.3f, 2f)) {
            show(scale) { PicnicTextField(note, {}, minLines = 4, textStyle = PicnicTheme.type.transcript, modifier = Modifier.width(300.dp)) }
            val layout = assertRulesFollowLines(4, "transcript field at $scale")
            val pitch = layout.getLineBottom(2) - layout.getLineBottom(1)
            val expected = densityAt(px, scale).lineHeightPx(22.sp, 15.sp)
            assertEquals("transcript lines at $scale are its own 22 sp apart", expected, pitch, 1f)
            val default = densityAt(px, scale).lineHeightPx(RULED_LINE_PITCH, 15.sp)
            assertTrue("not the default 24 sp pitch at $scale ($pitch px, 24 sp is $default px)", abs(pitch - default) > 1f)
        }
    }

    /** The field on screen draws a rule at each of its [lines] lines' bottom edge, and none through a line's letters. */
    private fun assertRulesFollowLines(lines: Int, what: String): TextLayoutResult {
        val field = compose.onNode(hasSetTextAction()).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        field.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.single()
        assertEquals(what, lines, layout.lineCount)
        val img = compose.onRoot().captureToImage().asAndroidBitmap()
        val rule = DayColors.rule.toArgb()
        val left = field.boundsInRoot.left.toInt() + 4
        val right = field.boundsInRoot.right.toInt() - 4
        // Rows where the rule colour runs across most of the field (letters interrupt it a little).
        val rows = (field.boundsInRoot.top.toInt() until field.boundsInRoot.bottom.toInt()).filter { y ->
            (left until right).count { x -> near(img.getPixel(x, y), rule, 16) } > (right - left) * 0.6f
        }
        assertTrue("$what: rules drawn", rows.isNotEmpty())
        val top = field.boundsInRoot.top
        for (i in 0 until layout.lineCount) {
            val lineTop = top + layout.getLineTop(i)
            val lineBottom = top + layout.getLineBottom(i)
            assertTrue("$what: line $i has its rule at its bottom ($lineBottom; rules $rows)", rows.any { it >= lineBottom - 1.5f * px - 1 && it <= lineBottom + 1 })
            assertTrue("$what: a rule cuts line $i ($lineTop..$lineBottom; rules $rows)", rows.none { it > lineTop + 1 && it < lineBottom - 1.5f * px - 1 })
        }
        return layout
    }

    /**
     * §2.7's 13 sp floor and §4.7 (item 337): the store-aisle and rarity chips are never drawn smaller than at font scale
     * 1.0, so their words are never under 13 sp. Each chip in slots from 40 to 160 dp at 1.0, 1.3 and 2.0, read off
     * the pixels against the same chip laid out freely at 1.0 and at the scale under test: at 1.0 a chip never shrinks
     * (its ink is the free chip's); above 1.0 a chip in a slot narrower than it is drawn smaller to fit (filling its
     * slot), never smaller than its 1.0 self, and in a slot narrower even than that it keeps its 1.0 size, running past
     * the slot's end with its whole word. The rarity chip's word is also checked against "jackpot!" set at 13 sp at 1.0.
     */
    @Test
    @Config(qualifiers = "w360dp-h1600dp-xxhdpi")
    fun smallChipsNeverDrawTheirWordUnder13sp() {
        val slots = listOf(40, 56, 72, 96, 128, 160)
        val dark: (Int) -> Boolean = { argb ->
            (0.299 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.114 * (argb and 0xFF)) < 110
        }
        val chips: List<Pair<String, @Composable () -> Unit>> = listOf("rarity" to { RarityChip(jackpot = true) }, "store" to { StoreAisleChip(12) })
        var shrunk = 0
        var floored = 0
        for (scale in listOf(1f, 1.3f, 2f)) {
            show(scale) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // References, each alone on its row (ink is read to the row's end): the free chips at 1.0 and
                    // here, and the word at 13 sp at 1.0.
                    CompositionLocalProvider(LocalDensity provides densityAt(px, 1f)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for ((name, chip) in chips) Box(Modifier.testTag("${name}AtOne")) { chip() }
                            Box(Modifier.testTag("wordAtOne").background(Color.White)) {
                                Text("jackpot!", style = PicnicTheme.type.counter.copy(color = PicnicTheme.colors.ballpoint, fontSize = 13.sp, lineHeight = 15.sp), maxLines = 1)
                            }
                        }
                    }
                    for ((name, chip) in chips) Box(Modifier.testTag("${name}Free")) { chip() }
                    for ((name, chip) in chips) for (slot in slots) {
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) { Box(Modifier.width(slot.dp).testTag("$name$slot")) { chip() } }
                    }
                }
            }
            val img = compose.onRoot().captureToImage().asAndroidBitmap()
            // A chip's ink from its slot's start to the screen's edge (a chip held at its 1.0 size runs past its slot).
            fun ink(tag: String): android.graphics.Rect {
                val b = node(tag).boundsInRoot
                return requireNotNull(inkBox(img, android.graphics.Rect(b.left.toInt(), b.top.toInt(), img.width, b.bottom.toInt()), dark)) { "$tag at $scale: no ink" }
            }
            val word = ink("wordAtOne")
            // The word alone: for the store chip, the ink after the gap that follows its basket glyph (the glyph and
            // the gap are part of the chip, not of its 13 sp word).
            fun wordInk(name: String, r: android.graphics.Rect): android.graphics.Rect {
                if (name != "store") return r
                val cols = (r.left until r.right).map { x -> (r.top until r.bottom).any { y -> dark(img.getPixel(x, y)) } }
                var x = cols.indexOfFirst { it }
                while (x < cols.size && cols[x]) x++
                var gap = 0
                while (x < cols.size && !cols[x]) { x++; gap++ }
                assertTrue("store chip: a gap after its glyph ($gap px)", gap >= 6)
                return requireNotNull(inkBox(img, android.graphics.Rect(r.left + x, r.top, r.right, r.bottom), dark))
            }
            for ((name, _) in chips) {
                val atOne = ink("${name}AtOne")
                val free = ink("${name}Free")
                val freeWidth = node("${name}Free").boundsInRoot.width
                // The least a chip is drawn at: its 13 sp word's size at 1.0 over its size here.
                val floor = with(densityAt(px, scale)) { 13f * px / 13.sp.toPx() }
                fun shapeOf(r: android.graphics.Rect) = r.width().toFloat() / r.height()
                for (slot in slots) {
                    val what = "$name in $slot dp at $scale"
                    val drawn = ink("$name$slot")
                    val box = node("$name$slot").boundsInRoot
                    // Its word's letters as tall as at 1.0 (a px for anti-aliasing: the size itself); as wide within
                    // 5 %, since a word set at 16.9 sp and drawn at 13/16.9 of that size advances a little differently
                    // from one set at 13 sp ("A12": 57 px for 59).
                    val drawnWord = wordInk(name, drawn)
                    val wordAtOne = wordInk(name, atOne)
                    assertTrue("$what: its word drawn under its 1.0 size (ink ${drawnWord.width()}×${drawnWord.height()}, at 1.0 ${wordAtOne.width()}×${wordAtOne.height()})", drawnWord.width() >= wordAtOne.width() * 0.95f && drawnWord.height() >= wordAtOne.height() - 1)
                    // The whole chip, never a cut one: its ink keeps the shape of the free chip (or of its 1.0 self).
                    val off = minOf(abs(shapeOf(drawn) - shapeOf(free)) / shapeOf(free), abs(shapeOf(drawn) - shapeOf(atOne)) / shapeOf(atOne))
                    assertTrue("$what: its ink is not the whole chip's shape (${"%.2f".format(shapeOf(drawn))} for ${"%.2f".format(shapeOf(free))})", off <= 0.06f)
                    if (name == "rarity") assertTrue("$what: 'jackpot!' under 13 sp (ink ${drawn.height()} px tall, 13 sp at 1.0 is ${word.height()} px)", drawn.height() >= word.height() - 1)
                    val slotPx = slot * px
                    when {
                        freeWidth <= slotPx + 0.5f -> assertEquals("$what: fits, drawn at its full size", free.height().toFloat(), drawn.height().toFloat(), 1f)
                        scale == 1f -> assertEquals("$what: at 1.0 a chip never shrinks", free.height().toFloat(), drawn.height().toFloat(), 1f)
                        freeWidth * floor > slotPx + 0.5f -> {
                            assertEquals("$what: held at its 1.0 size", wordAtOne.height().toFloat(), drawnWord.height().toFloat(), 1f)
                            floored++
                        }
                        else -> {
                            assertTrue("$what: drawn smaller to fit its slot (ink ${drawn.height()} px tall, free ${free.height()} px)", drawn.height() < free.height() - 1)
                            assertTrue("$what: inside its slot (ink to ${drawn.right}, slot to ${box.right})", drawn.right <= box.right + 1)
                            assertEquals("$what: fills its slot", slotPx, box.width, 1f)
                            shrunk++
                        }
                    }
                }
            }
        }
        assertTrue("some chips were drawn smaller ($shrunk) and some held at their 1.0 size ($floored)", shrunk >= 4 && floored >= 2)
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
