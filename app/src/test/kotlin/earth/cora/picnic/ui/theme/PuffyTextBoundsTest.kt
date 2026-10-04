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
 * - in every real caller (the List screen's own title, open and with its bar collapsed, the binder top, the secondary
 *   screens' bar with and without a glyph subtitle, the About wordmark, the Next card, the celebration's title and
 *   banner) no ancestor's clip cuts into that box;
 * - a colour glyph (an emoji in a list's name) casts no full-colour ghost and wears its halo;
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

    private fun show(variant: ThemeVariant, fontScale: Float, density: Float = 3f, content: @Composable () -> Unit) {
        screen.show {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
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

    /**
     * The titles in their real callers: nothing that holds them clips into their boxes, at 1.0 and 2.0. The wordmark is
     * the app's name: it stacks rather than ellipsize, at any scale.
     */
    @Test
    fun realCallersNeverClipTheirTitles() {
        val puffy = SemanticsMatcher.keyIsDefined(PuffyFontSize)
        val collapsed = earth.cora.picnic.ui.list.ListScreenState().also { it.scrolledDown = true }
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
            // The shipping list title (ListScreen's TopStack): its own row beside the sun, egg 7's fill brush, the size
            // refitting in half-sp steps as the bar collapses. Open, and collapsed by a scroll.
            "list" to {
                earth.cora.picnic.ui.list.ListScreen(
                    earth.cora.picnic.ui.list.ListFixture.ui(MotionFixture.state("Thanksgiving dinner at the cottage with everyone")),
                    earth.cora.picnic.ui.list.ListActions(),
                )
            },
            "list, bar collapsed" to {
                earth.cora.picnic.ui.list.ListScreen(
                    earth.cora.picnic.ui.list.ListFixture.ui(MotionFixture.state("Thanksgiving dinner at the cottage with everyone")),
                    earth.cora.picnic.ui.list.ListActions(),
                    screen = collapsed,
                    // Scrolled off the top, so the bar stays collapsed (at the top it comes back).
                    listState = remember { androidx.compose.foundation.lazy.LazyListState(firstVisibleItemIndex = 3) },
                )
            },
            "secondary bar" to {
                PicnicTopBar("My store & aisles", subtitle = "Hillcrest Market", navigation = { PicnicIconButton(PicnicGlyph.Back, "Back", {}) })
            },
            "secondary screen with a glyph subtitle" to {
                earth.cora.picnic.ui.common.SecondaryScreen("Sticker album", onBack = {}, subtitle = "★ 64 of 216 collected · 3 holo") {}
            },
            "wordmark" to { Column(Modifier.padding(16.dp)) { Wordmark("Kirakira Basket") } },
            // About on a 320 dp phone: 288 dp between its margins.
            "wordmark" to { Column(Modifier.padding(16.dp).width(288.dp)) { Wordmark("Kirakira Basket") } },
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
                if (name == "wordmark") assertTrue("$what: the wordmark is ellipsized", !n.config.contains(earth.cora.picnic.ui.a11y.TextOverflowed))
            }
        }
        assertTrue("the list's bar stayed collapsed for its case", collapsed.scrolledDown)
        // The wordmark only ever shrinks as its box narrows: one line shrinking, then stacked at the size it reached
        // (never a 40 sp stack at 232 dp after 25 sp on one line at 248 dp), never ellipsized, never clipped.
        for (scale in listOf(1f, 1.3f, 2f)) for (compact in listOf(false, true)) {
            var last = Float.MAX_VALUE
            for (w in listOf(360, 328, 288, 260, 248, 232, 216)) {
                show(ThemeVariant.Day, scale) { Box(Modifier.width(w.dp)) { Wordmark("Kirakira Basket", compact = compact) } }
                val n = compose.onNode(puffy, useUnmergedTree = true).fetchSemanticsNode()
                val what = "wordmark${if (compact) " (compact)" else ""} in $w dp at ×$scale"
                val size = n.config[PuffyFontSize]
                assertTrue("$what: $size sp, bigger than the $last sp it had in a wider box", size <= last + 0.01f)
                assertTrue("$what: ellipsized", !n.config.contains(earth.cora.picnic.ui.a11y.TextOverflowed))
                assertTrue("$what: clipped ${n.clippedAndWhole()}", n.isWhole())
                last = size
            }
        }
    }

    /**
     * No pinholes or cracks in the stroke (the NEXT "XT" speck, where the hard shadow showed through a point between
     * two glyphs' halos, and the hairline slits where two halos nearly meet), at 2.625, 3, 3.5 and 4 px per dp (the
     * rule is in dp, so every phone closes the same cracks), font scale 1.0 and 2.0, fitting and ellipsized. Each title
     * is drawn as its silhouette (stroke and fill both white, the shadow pure blue, on pure green) and judged three ways:
     * - by the finder's own rule, read back at the stroke pass's half-coverage threshold: no uncovered region smaller
     *   than the pen is cut off from the open ground, or reached only through a crack of about 1 dp;
     * - by two oracles that know nothing of the finder, read off the pixels alone: no region of ground or shadow is
     *   sealed inside the white, except a glyph's own counter (found by drawing the fill alone) bigger than a pinhole;
     *   and no hairline: no run of ground or shadow under 0.5 dp between white on both sides, 0.75 dp long or more
     *   (a single antialiased point where two arcs meet is no crack).
     */
    @Test
    fun strokeHasNoPinholes() {
        val cases = listOf(2.625f, 3f, 3.5f, 4f).map { d -> ThemeVariant.Day to d } + listOf(ThemeVariant.Glow to 3f)
        for ((variant, d) in cases) for (scale in listOf(1f, 2f)) for (narrow in listOf(false, true)) for (t in titles) {
            fun silhouette(strokeAndShadow: Boolean): android.graphics.Bitmap {
                show(variant, scale, d) {
                    Box(Modifier.background(ground).padding(12.dp)) {
                        Box(Modifier.width(if (narrow) t.narrow else t.width)) {
                            PuffyText(
                                AnnotatedString(t.text.text), t.style().copy(color = Color.White), t.stroke, Modifier.testTag("title"),
                                strokeColor = if (strokeAndShadow) Color.White else Color.Transparent, shadow = t.shadow(),
                                shadowColor = if (strokeAndShadow) probeShadow else Color.Transparent,
                                minFontSize = t.minFontSize, maxLines = t.maxLines, textAlign = t.textAlign,
                            )
                        }
                    }
                }
                return compose.onNodeWithTag("title").captureToImage().asAndroidBitmap()
            }
            val what = "${t.name} ($variant, ${d}x, ×$scale${if (narrow) ", narrow" else ""})"
            val img = silhouette(true)
            val w = img.width
            val h = img.height
            val px = IntArray(w * h).also { img.getPixels(it, 0, w, 0, 0, w, h) }
            val fillImg = silhouette(false)
            assertTrue("$what: the fill-only pass draws the same box", fillImg.width == w && fillImg.height == h)
            val fillOnly = IntArray(w * h).also { fillImg.getPixels(it, 0, w, 0, 0, w, h) }
            // The ground and the shadow have no red; white has all of it: red is the silhouette's coverage, read at the
            // stroke's own half threshold. The capture blends edges in linear light, so a half-covered pixel reads 188
            // (the sRGB of 50 %), not 128; anything fainter is ground. A pixel or so of antialiasing may still differ
            // from the mask PuffyText reads, so the finder's check allows the ground 2 px more shore.
            fun white(i: Int) = ((px[i] shr 16) and 0xFF) >= HALF_COVERED_RED
            val alpha = IntArray(w * h) { i -> if (white(i)) 255 else 0 }
            val pen = kotlin.math.ceil(t.stroke.value * d).toInt()
            val holes = PuffyPinholes.holes(alpha, w, h, pen, PuffyPinholes.neckPx(d), PuffyPinholes.shorePx(d) + 2)?.count { it } ?: 0
            assertEquals("$what: $holes px of ground or shadow in a pinhole or crack (the finder's rule)", 0, holes)
            // Oracle 1: sealed regions (8 neighbours: a diagonal leak is no seal).
            val counters = PuffyPinholes.enclosed(BooleanArray(w * h) { ((fillOnly[it] shr 16) and 0xFF) < HALF_COVERED_RED }, w, h)
            val reached = BooleanArray(w * h)
            val queue = IntArray(w * h)
            var head = 0
            var tail = 0
            fun flood(i: Int, mark: BooleanArray) {
                if (!white(i) && !mark[i]) {
                    mark[i] = true
                    queue[tail++] = i
                }
            }
            fun spread(mark: BooleanArray, visit: (Int) -> Unit) {
                while (head < tail) {
                    val i = queue[head++]
                    visit(i)
                    val x = i % w
                    val y = i / w
                    for (dy in -1..1) for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until w && ny in 0 until h && !reached[ny * w + nx]) flood(ny * w + nx, mark)
                    }
                }
            }
            for (x in 0 until w) { flood(x, reached); flood((h - 1) * w + x, reached) }
            for (y in 0 until h) { flood(y * w, reached); flood(y * w + w - 1, reached) }
            spread(reached) {}
            val seen = BooleanArray(w * h)
            for (start in 0 until w * h) {
                if (white(start) || reached[start] || seen[start]) continue
                head = 0
                tail = 0
                flood(start, seen)
                var inCounter = false
                spread(seen) { if (counters[it]) inCounter = true }
                assertTrue("$what: $tail px of ground or shadow sealed in the white (${if (inCounter) "a counter no bigger than a pinhole" else "between glyphs"}) at ${start % w}, ${start / w}", inCounter && tail > pen * pen / 2)
            }
            // Oracle 2: hairlines.
            val thin = maxOf(1, (0.5f * d).toInt())
            val narrowRun = BooleanArray(w * h)
            for (y in 0 until h) {
                var x = 0
                while (x < w) {
                    if (white(y * w + x)) { x++; continue }
                    var end = x
                    while (end < w && !white(y * w + end)) end++
                    if (x > 0 && end < w && end - x <= thin) for (k in x until end) narrowRun[y * w + k] = true
                    x = end
                }
            }
            for (x in 0 until w) {
                var y = 0
                while (y < h) {
                    if (white(y * w + x)) { y++; continue }
                    var end = y
                    while (end < h && !white(end * w + x)) end++
                    if (y > 0 && end < h && end - y <= thin) for (k in y until end) narrowRun[k * w + x] = true
                    y = end
                }
            }
            val long = kotlin.math.ceil(0.75f * d).toInt()
            val grouped = BooleanArray(w * h)
            for (start in 0 until w * h) {
                if (!narrowRun[start] || grouped[start]) continue
                val members = ArrayDeque(listOf(start))
                grouped[start] = true
                var minX = w; var maxX = 0; var minY = h; var maxY = 0
                while (members.isNotEmpty()) {
                    val i = members.removeFirst()
                    val x = i % w
                    val y = i / w
                    minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
                    for (dy in -1..1) for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until w && ny in 0 until h && narrowRun[ny * w + nx] && !grouped[ny * w + nx]) {
                            grouped[ny * w + nx] = true
                            members.addLast(ny * w + nx)
                        }
                    }
                }
                val length = maxOf(maxX - minX + 1, maxY - minY + 1)
                assertTrue("$what: a hairline crack ${length} px long at $minX..$maxX, $minY..$maxY", length < long)
            }
        }
    }

    /**
     * The patch is worked out once per title, size and density ([PuffyPatchCache]): showing NEXT again scans nothing.
     * In the app ([PinholeWork.Background]) the scan runs off the main thread, and the patch it brings is the one the
     * inline scan draws: the same pixels once it lands (item 308: no title costs a frame when it appears).
     */
    @Test
    fun pinholePatchIsCachedAndScannedOffTheMainThread() {
        val next = titles.single { it.name == "next" }
        PuffyPatchCache.clear()
        val start = PuffyPinholes.scans
        show(ThemeVariant.Day, 1f) { Box(Modifier.background(ground).padding(12.dp).testTag("frame")) { Draw(next, next.width, "title") } }
        assertEquals("the first NEXT is scanned once", start + 1, PuffyPinholes.scans)
        assertTrue("inline, the scan runs in composition (the main thread)", PuffyPinholes.lastScanOnMain)
        assertTrue("NEXT's \"XT\" has a pinhole to close", PuffyPinholes.lastScanFilled > 0)
        val inline = compose.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        show(ThemeVariant.Day, 1f) { Box(Modifier.background(ground).padding(12.dp)) { Draw(next, next.width, "again") } }
        show(ThemeVariant.Day, 1f) { Box(Modifier.background(ground).padding(12.dp).testTag("frame")) { Draw(next, next.width, "title") } }
        assertEquals("NEXT seen again scans nothing", start + 1, PuffyPinholes.scans)
        PuffyPatchCache.clear()
        show(ThemeVariant.Day, 1f) {
            CompositionLocalProvider(LocalPinholeWork provides PinholeWork.Background) {
                Box(Modifier.background(ground).padding(12.dp).testTag("frame")) { Draw(next, next.width, "title") }
            }
        }
        compose.waitUntil(5_000) { PuffyPinholes.scans == start + 2 }
        assertTrue("in the app, the scan runs off the main thread", !PuffyPinholes.lastScanOnMain)
        compose.waitUntil(5_000) { compose.onNodeWithTag("frame").captureToImage().asAndroidBitmap().sameAs(inline) }
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
        // The 10 × 10 counter with a pen of 6 stays open inside a glyph, but the same hole between two glyphs (closed only
        // by their halos) is a stroke artefact and fills, up to 4 pens across.
        val big = grid(intArrayOf(15, 5, 25, 15))
        assertNull(PuffyPinholes.holes(big, w, h, pen = 6, inGlyph = BooleanArray(w * h) { it % w in 15 until 25 && it / w in 5 until 15 }))
        assertTrue(requireNotNull(PuffyPinholes.holes(big, w, h, pen = 6, inGlyph = BooleanArray(w * h)))[10 * w + 20])
        assertNull("wider than 4 pens: left as drawn", PuffyPinholes.holes(big, w, h, pen = 2, inGlyph = BooleanArray(w * h)))
        // The box's edge is open air: a sliver between the edge and a halo nearer than the neck is no hole.
        val sliver = grid(intArrayOf(0, 4, 2, 16))
        assertNull(PuffyPinholes.holes(sliver, w, h, pen = 6, neck = 2, shore = 3))
    }

    /**
     * A colour glyph casts no ghost (§2.7, item 334): an emoji in a list's name ("Picnic 🍓 party") is a bitmap that the
     * passes' Stroke and colours never reach, so the shadow pass used to draw a second, full-colour strawberry offset by
     * hs, peeking out of the first, and the stroke left the emoji with no die-cut halo. Drawn with the stroke pure white,
     * the shadow pure blue on a pure green ground, at 1.0 and 2.0, Day and Glow: no strawberry red or leaf green lies
     * outside the emoji's own box in the fill's layout (only the fill draws it in colour), the strawberry is there in
     * colour, and the ground never shows within the halo's reach of its left edge.
     */
    @Test
    @Config(qualifiers = "w900dp-h400dp-xxhdpi")
    fun colourGlyphsCastNoGhost() {
        val text = "Picnic \uD83C\uDF53 party"
        val index = text.indexOf("\uD83C\uDF53")
        fun isRed(c: Int) = ((c shr 16) and 0xFF) > 150 && ((c shr 8) and 0xFF) < 110 && (c and 0xFF) < 110
        fun isLeaf(c: Int): Boolean {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            // Leaf greens are yellow-greens, more red than blue; the ground's pure green and its blends with the white
            // stroke and the blue shadow never are (they carry at least as much blue as red).
            return g in 100..210 && r >= 40 && r < g - 30 && b < g - 40 && b < r - 10
        }
        for (variant in listOf(ThemeVariant.Day, ThemeVariant.Glow)) for (scale in listOf(1f, 2f)) {
            var fit: PuffyFit? = null
            show(variant, scale) {
                val style = PicnicTheme.type.screenTitle.copy(color = PicnicTheme.colors.ink)
                val hs = PicnicTheme.shadows.hs
                val measurer = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 0)
                val d = LocalDensity.current
                Box(Modifier.background(ground).padding(24.dp).testTag("frame")) {
                    Box(Modifier.width(800.dp)) {
                        PuffyText(text, style, fill = style.color, stroke = 6.dp, modifier = Modifier.testTag("title"), strokeColor = Color.White, shadow = hs, shadowColor = probeShadow)
                    }
                }
                fit = with(d) {
                    fitPuffy(
                        measurer, AnnotatedString(text), style, 6.dp.toPx(), hs.x.toPx(), hs.y.toPx(), TextUnit.Unspecified, 1,
                        TextAlign.Start, 800.dp.roundToPx(), Color.White, probeShadow,
                    )
                }
            }
            val what = "\"$text\" ($variant, ×$scale)"
            val f = requireNotNull(fit)
            assertTrue("$what: the strawberry counts as a colour glyph", f.colourGlyphs)
            assertTrue("$what: fits whole", !f.overflowed)
            val frame = compose.onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
            val box = compose.onNode(SemanticsMatcher.keyIsDefined(PuffyFontSize), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.translate(-frame.left, -frame.top)
            val glyph = f.fill.getBoundingBox(index).translate(box.left + f.half, box.top + f.half)
            val img = compose.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
            val px = IntArray(img.width * img.height)
            img.getPixels(px, 0, img.width, 0, 0, img.width, img.height)
            var inside = 0
            val ghost = mutableListOf<String>()
            for (y in 0 until img.height) for (x in 0 until img.width) {
                val c = px[y * img.width + x]
                if (!isRed(c) && !isLeaf(c)) continue
                val within = x + 0.5f >= glyph.left - 1f && x + 0.5f <= glyph.right + 1f && y + 0.5f >= glyph.top - 1f && y + 0.5f <= glyph.bottom + 1f
                if (within) inside++ else if (ghost.size < 8) ghost += "($x, $y) #${Integer.toHexString(c)}"
            }
            assertTrue("$what: the strawberry is drawn in colour in its box $glyph ($inside px)", inside > 20)
            assertTrue("$what: strawberry colours outside its box $glyph: $ghost", ghost.isEmpty())
            // Its halo: on the row through the strawberry's middle, the ground never shows within the halo's reach
            // (half the stroke, less a px of anti-aliasing) to the left of its first red pixel.
            val midY = glyph.center.y.toInt()
            val firstRed = (glyph.left.toInt().coerceAtLeast(0)..glyph.right.toInt().coerceAtMost(img.width - 1)).firstOrNull { isRed(px[midY * img.width + it]) || isLeaf(px[midY * img.width + it]) }
            requireNotNull(firstRed) { "$what: no strawberry on its middle row" }
            val reach = (f.half - 1f).toInt()
            for (x in (firstRed - reach) until firstRed) {
                assertTrue("$what: the ground shows at ($x, $midY), ${firstRed - x} px from the strawberry's edge: no halo", !near(px[midY * img.width + x], ground.toArgb(), 40))
            }
        }
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

        /** A half-covered white edge on a red-free ground, as the capture blends it (linear light: sRGB of 50 %). */
        const val HALF_COVERED_RED = 188
    }
}
