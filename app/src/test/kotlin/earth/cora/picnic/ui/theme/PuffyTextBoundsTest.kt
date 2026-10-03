package earth.cora.picnic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.ui.a11y.TextOverflowed
import earth.cora.picnic.ui.list.motion.CelebrationContent
import earth.cora.picnic.ui.list.motion.CelebrationCopy
import earth.cora.picnic.ui.list.motion.CelebrationPresentation
import earth.cora.picnic.ui.list.motion.CelebrationTimeline
import earth.cora.picnic.ui.list.motion.MotionClock
import earth.cora.picnic.ui.list.motion.MotionFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Checklist 334, §2.7 "Puffy text never clips": every stroked title at its longest real text ("Saturday shop" and a
 * longer list name, "Kirakira Basket", "Everything else", "List complete!" and the celebration's banner, NEXT), in Day
 * and Glow at font scale 1.0 and 2.0, fitting and ellipsized:
 * - the drawn ink (stroke, shadow and fill, read back from the pixels on a plain ground, nothing clipping) lies inside
 *   the PuffyText's own box, so laying the box out is enough to show it whole;
 * - in every real caller (the binder top, the secondary screens' bar with and without a glyph subtitle, the About
 *   wordmark, the Next card, the celebration's title and banner) no ancestor's clip cuts into that box;
 * - the white (Glow #3A438A) stroke has no pinholes (the NEXT "XT" speck);
 * - an ellipsized title drops the space before its "…" and its box ends where its ink ends.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class PuffyTextBoundsTest {
    @get:Rule
    val compose = createComposeRule()

    private val screen by lazy { SwappableContent(compose) }

    /** One real title: what its caller passes PuffyText. */
    private class Title(
        val name: String,
        val text: AnnotatedString,
        val style: @Composable () -> TextStyle,
        val stroke: Dp,
        val strokeColor: @Composable () -> Color,
        val shadow: @Composable () -> DpOffset?,
        val minFontSize: TextUnit = TextUnit.Unspecified,
        val maxLines: Int = 1,
        val textAlign: TextAlign = TextAlign.Start,
        /** The width the caller gives it, and a narrow one that forces the ellipsis. */
        val width: Dp,
        val narrow: Dp,
    )

    private val titles = listOf(
        Title("app bar", AnnotatedString("Saturday shop"), { PicnicTheme.type.screenTitle.copy(color = PicnicTheme.colors.ink) }, 6.dp, { PicnicTheme.colors.puffyStroke }, { PicnicTheme.shadows.hs }, 20.sp, width = 240.dp, narrow = 110.dp),
        Title("long list name", AnnotatedString("Thanksgiving dinner at the cottage"), { PicnicTheme.type.screenTitle.copy(color = PicnicTheme.colors.ink) }, 6.dp, { PicnicTheme.colors.puffyStroke }, { PicnicTheme.shadows.hs }, 20.sp, width = 240.dp, narrow = 150.dp),
        Title(
            "wordmark",
            buildAnnotatedString {
                withStyle(SpanStyle(color = Families.strawberry.mid)) { append("Kirakira") }
                withStyle(SpanStyle(color = Families.ramune.mid)) { append(" Basket") }
            },
            { PicnicTheme.type.wordmarkLarge }, 7.dp, { Color.White }, { PicnicTheme.shadows.hs2 }, PicnicTheme_wordmarkMin, width = 300.dp, narrow = 140.dp,
        ),
        Title("tab", AnnotatedString("Everything else"), { PicnicTheme.type.aisleTab.copy(color = PicnicTheme.colors.ink) }, 6.dp, { PicnicTheme.colors.puffyStroke }, { PicnicTheme.shadows.hs }, width = 240.dp, narrow = 120.dp),
        Title("celebration", AnnotatedString("List complete!"), { PicnicTheme.type.celebrationTitle.copy(color = Families.strawberry.mid) }, 9.dp, { Color.White }, { PicnicTheme.shadows.hs2 }, 26.sp, maxLines = 2, textAlign = TextAlign.Center, width = 312.dp, narrow = 120.dp),
        Title("banner", AnnotatedString("Perfect shop!"), { PicnicTheme.type.cardTitle.copy(fontSize = 16.sp, lineHeight = 20.sp, color = Color.White) }, 4.dp, { Families.sakura.trace }, { null }, 13.sp, width = 256.dp, narrow = 80.dp),
        Title("next", AnnotatedString("NEXT"), { PicnicTheme.type.nextLabel.copy(color = PicnicTheme.colors.ballpoint) }, 6.dp, { Color.White }, { PicnicTheme.shadows.hs }, width = 200.dp, narrow = 60.dp),
    )

    @Composable
    private fun Draw(t: Title, width: Dp, tag: String, shadowColor: Color? = null) {
        Box(Modifier.width(width)) {
            PuffyText(
                t.text, t.style(), t.stroke, Modifier.testTag(tag), strokeColor = t.strokeColor(), shadow = t.shadow(),
                shadowColor = shadowColor ?: PicnicTheme.shadows.color,
                minFontSize = t.minFontSize, maxLines = t.maxLines, textAlign = t.textAlign,
            )
        }
    }

    private fun show(variant: ThemeVariant, fontScale: Float, content: @Composable () -> Unit) {
        screen.show {
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                PicnicTheme(variant) { content() }
            }
        }
    }

    /** A ground no title uses: anything else in the frame is ink. */
    private val ground = Color(0xFF00FF00)

    @Test
    fun inkStaysInsideTheBox() {
        for (variant in listOf(ThemeVariant.Day, ThemeVariant.Glow)) for (scale in listOf(1f, 2f)) for (narrow in listOf(false, true)) for (t in titles) {
            show(variant, scale) {
                Box(Modifier.background(ground).padding(24.dp).testTag("frame")) { Draw(t, if (narrow) t.narrow else t.width, "title") }
            }
            val what = "${t.name} ($variant, ×$scale, ${if (narrow) "narrow" else "its width"})"
            val frame = compose.onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
            val title = compose.onNode(SemanticsMatcher.keyIsDefined(PuffyFontSize), useUnmergedTree = true).fetchSemanticsNode()
            val box = title.boundsInRoot.translate(-frame.left, -frame.top)
            val img = compose.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
            val ink = inkBox(img) { !near(it, ground.toArgb(), 6) }
            requireNotNull(ink) { "$what drew nothing" }
            assertTrue("$what: ink $ink leaves its box $box on the left", ink.left >= box.left - 1f)
            assertTrue("$what: ink $ink leaves its box $box at the top", ink.top >= box.top - 1f)
            assertTrue("$what: ink $ink leaves its box $box on the right", ink.right <= box.right + 1f)
            assertTrue("$what: ink $ink leaves its box $box at the bottom", ink.bottom <= box.bottom + 1f)
            // A start-aligned line's box hugs its ink: no gap after the last glyph (an ellipsized title's box ends at
            // its "…"). Centred and wrapped titles take the width they are given.
            if (t.maxLines == 1 && t.textAlign == TextAlign.Start) assertTrue("$what: box $box runs past its ink $ink", box.right - ink.right <= 3f)
            if (narrow && t.name != "next") {
                val cut = title.config.getOrNull(TextOverflowed) == true
                assertTrue("$what: narrow enough to be cut", cut)
            }
        }
    }

    /** The titles in their real callers: nothing that holds them clips into their boxes, at 1.0 and 2.0. */
    @Test
    fun realCallersNeverClipTheirTitles() {
        val puffy = SemanticsMatcher.keyIsDefined(PuffyFontSize)
        val callers: List<Pair<String, @Composable () -> Unit>> = listOf(
            "binder top" to {
                Column {
                    BinderTopBar(
                        title = "Saturday shop", subtitle = "Hillcrest Market · 8 stops", lcd = LcdState(LcdMode.Count, count = 10, total = 24),
                        onMore = {}, onSunTap = {}, state = remember { AppBarState(false) },
                    )
                    BinderTopBar(
                        title = "Thanksgiving dinner at the cottage with everyone", subtitle = "Hillcrest Market · 14 stops",
                        lcd = LcdState(LcdMode.Count, count = 104, total = 128), onMore = {}, onSunTap = {}, state = remember { AppBarState(true) },
                    )
                }
            },
            "secondary bar" to {
                PicnicTopBar("My store & aisles", subtitle = "Hillcrest Market", navigation = { PicnicIconButton(PicnicGlyph.Back, "Back", {}) })
            },
            "secondary screen with a glyph subtitle" to {
                earth.cora.picnic.ui.common.SecondaryScreen("Sticker album", onBack = {}, subtitle = "★ 64 of 216 collected · 3 holo") {}
            },
            "wordmark" to { Column(Modifier.padding(16.dp)) { Wordmark("Kirakira Basket") } },
            "next card" to {
                Box(Modifier.size(360.dp, 400.dp)) {
                    NextStopCard(4, "Baking", storeAisle = 2, look = AisleLooks.of(4), nextLabel = "NEXT")
                }
            },
            "celebration" to {
                val clock = remember { MotionClock().also { it.now = 3000 } }
                val content = remember { CelebrationContent.from(MotionFixture.allBut(emptyList()), MotionFixture.LIST_ID, Album(), MotionFixture.catalog)!! }
                val plan = remember(content) { CelebrationTimeline.plan(content.listName, content.stats, false, total = content.total) }
                Box(Modifier.size(360.dp, 760.dp)) {
                    CelebrationScene(CelebrationPresentation(MotionFixture.LIST_ID, 0, content, plan, null, false, false), clock, CelebrationCopy.English)
                }
            },
        )
        for (variant in listOf(ThemeVariant.Day, ThemeVariant.Glow)) for (scale in listOf(1f, 2f)) for ((name, caller) in callers) {
            show(variant, scale) { Box(Modifier.fillMaxSize()) { caller() } }
            val nodes = compose.onAllNodes(puffy, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("$name ($variant, ×$scale) shows a puffy title", nodes.isNotEmpty())
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            for (n in nodes) {
                val what = "$name '${n.text()}' ($variant, ×$scale)"
                assertTrue("$what: an ancestor clips its box ${n.clippedAndWhole()}", n.isWhole())
                val (_, whole) = n.clippedAndWhole()
                assertTrue("$what: box $whole leaves the window $root", whole.left >= root.left - 0.5f && whole.right <= root.right + 0.5f)
            }
        }
    }

    /**
     * No pinholes or cracks in the stroke (the NEXT "XT" speck, where the hard shadow showed through a point between
     * two glyphs' halos, and the hairline slits where two halos nearly meet). Each title is drawn as its silhouette
     * (stroke and fill both white, the shadow pure blue, on pure green): read back at the stroke pass's own
     * half-coverage threshold, no uncovered region smaller than the pen is cut off from the open ground, or reached
     * only through a neck 2 px wide or less.
     */
    @Test
    fun strokeHasNoPinholes() {
        for (variant in listOf(ThemeVariant.Day, ThemeVariant.Glow)) for (scale in listOf(1f, 2f)) for (t in titles) {
            show(variant, scale) {
                Box(Modifier.background(ground).padding(12.dp)) {
                    Box(Modifier.width(t.width)) {
                        PuffyText(
                            AnnotatedString(t.text.text), t.style().copy(color = Color.White), t.stroke, Modifier.testTag("title"),
                            strokeColor = Color.White, shadow = t.shadow(), shadowColor = probeShadow,
                            minFontSize = t.minFontSize, maxLines = t.maxLines, textAlign = t.textAlign,
                        )
                    }
                }
            }
            val img = compose.onNodeWithTag("title").captureToImage().asAndroidBitmap()
            val w = img.width
            val h = img.height
            val px = IntArray(w * h)
            img.getPixels(px, 0, w, 0, 0, w, h)
            // The ground and the shadow have no red; white has all of it: red is the silhouette's coverage, read at the
            // stroke's own half threshold. The screen renders a little fuller than the mask PuffyText reads, so the
            // ground may stop a pixel or two short of where the mask let it reach: the check allows it 2 px more shore.
            val alpha = IntArray(w * h) { i -> if (((px[i] shr 16) and 0xFF) >= 128) 255 else 0 }
            val pen = kotlin.math.ceil(t.stroke.value * 3f).toInt()
            val holes = PuffyPinholes.holes(alpha, w, h, pen, shore = PuffyPinholes.SHORE + 2)?.count { it } ?: 0
            assertEquals("${t.name} ($variant, ×$scale): $holes px of ground or shadow in a pinhole or crack", 0, holes)
        }
    }

    /** The pinhole finder itself: an enclosed hole smaller than the pen fills, a larger one and an open one do not. */
    @Test
    fun pinholeFinder() {
        val w = 30
        val h = 20
        fun grid(vararg holes: IntArray): IntArray = IntArray(w * h) { 255 }.also { a -> for (r in holes) for (y in r[1] until r[3]) for (x in r[0] until r[2]) a[y * w + x] = 0 }
        // A 3 × 3 pinhole and a 10 × 10 counter, with a pen of 6 px.
        val a = grid(intArrayOf(4, 4, 7, 7), intArrayOf(15, 5, 25, 15))
        val holes = requireNotNull(PuffyPinholes.holes(a, w, h, pen = 6))
        assertTrue(holes[5 * w + 5])
        assertTrue("the pinhole's rim is filled too", holes[3 * w + 3] || holes[4 * w + 4])
        assertTrue("a counter wider than the pen stays open", !holes[10 * w + 20])
        // A notch open to the edge is no hole.
        assertNull(PuffyPinholes.holes(grid(intArrayOf(0, 8, 3, 11)), w, h, pen = 6))
        // A partly covered pixel (the stroke's antialiased edge) counts as covered from half up.
        val soft = grid(intArrayOf(10, 10, 12, 12)).also { it[10 * w + 10] = 200 }
        assertTrue(requireNotNull(PuffyPinholes.holes(soft, w, h, pen = 6))[10 * w + 11])
        // Open ground along the top; a hairline crack (1 px wide, 8 px deep) runs down from it: filled below its shore.
        val crack = grid(intArrayOf(0, 0, 30, 3), intArrayOf(14, 3, 15, 11))
        val c = requireNotNull(PuffyPinholes.holes(crack, w, h, pen = 6))
        for (y in 6 until 11) assertTrue("crack row $y", c[y * w + 14])
        assertTrue("the open ground stays open", !c[1 * w + 14])
        // A 5 px gap between two halos is open ground, all the way down.
        assertNull(PuffyPinholes.holes(grid(intArrayOf(0, 0, 30, 3), intArrayOf(12, 3, 17, 20)), w, h, pen = 6))
        // A shallow dip in the outline (2 px deep) stays as drawn.
        assertNull(PuffyPinholes.holes(grid(intArrayOf(0, 0, 30, 3), intArrayOf(14, 3, 16, 5)), w, h, pen = 6))
    }

    /** "Everything…", never "Everything …"; spans and their colour carry over to the "…". */
    @Test
    fun ellipsisDropsTheSpace() {
        assertEquals("Everything…", ellipsized(AnnotatedString("Everything else"), 11).text)
        assertEquals("Everything…", ellipsized(AnnotatedString("Everything else"), 10).text)
        assertEquals("Everythin…", ellipsized(AnnotatedString("Everything else"), 9).text)
        assertEquals("…", ellipsized(AnnotatedString("Everything else"), 0).text)
        val mark = buildAnnotatedString {
            withStyle(SpanStyle(color = Families.strawberry.mid)) { append("Kirakira") }
            withStyle(SpanStyle(color = Families.ramune.mid)) { append(" Basket") }
        }
        val cut = ellipsized(mark, 12)
        assertEquals("Kirakira Bas…", cut.text)
        assertEquals(Families.ramune.mid, cut.spanStyles.last { it.end == cut.length }.item.color)
    }

    private fun SemanticsNode.text(): String =
        config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { it.text } ?: "?"

    private companion object {
        val PicnicTheme_wordmarkMin: TextUnit = 24.sp
        val probeShadow = Color(0xFF0000FF)
    }
}
