package earth.cora.picnic.icons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Live stickers v4 post-processing (docs/DESIGN_STICKER.md §18.3 step 5, checklist item 45) on the six raw answers the
 * mockup's v4 run got from gpt-image-2.5-flare (`icons/ai4/`, the fourth cell of each, with their prompts), plus
 * synthetic answers for the edge cases.
 */
class IconPostProcessorV4Test {
    private class Fixture(val key: String, val families: List<String>, val targetU2: Double, val post4: JsonObject, val sheet: IntArray)

    private val mockup: JsonObject by lazy {
        Json.parseToJsonElement(resource("icons/ai4/mockup.json").readText()).jsonObject
    }

    private val fixtures: List<Fixture> by lazy {
        mockup.entries.map { (key, value) ->
            val o = value.jsonObject
            Fixture(
                key = key,
                families = o.getValue("families").jsonArray.map { it.jsonPrimitive.content },
                targetU2 = o.getValue("target_u2").jsonPrimitive.double,
                post4 = o.getValue("post4").jsonObject,
                sheet = sheetWithCell(ImageIO.read(resource("icons/ai4/cell_$key.png"))),
            )
        }
    }

    private fun resource(path: String): File =
        File(requireNotNull(javaClass.classLoader!!.getResource(path)) { "missing test resource $path" }.toURI())

    /** The raw answer as the device gets it: 1024 x 1024 with the new art in the bottom-right cell. */
    private fun sheetWithCell(cell: BufferedImage): IntArray {
        require(cell.width == 512 && cell.height == 512)
        val sheet = IntArray(1024 * 1024)
        for (y in 0 until 512) for (x in 0 until 512) sheet[(y + 512) * 1024 + x + 512] = cell.getRGB(x, y)
        return sheet
    }

    private fun process(f: Fixture) = IconPostProcessorV4.process(f.sheet, 1024, 1024, f.families, f.targetU2)

    private val px = IconPostProcessorV4.OUT_PX
    private val ou = px / IconPostProcessorV4.BOX_U

    @Test
    fun theSixMockupAnswersBecomeStickersOnTheLibraryGrid() {
        assertEquals(setOf("dragonfruit", "bokchoy", "kimchi", "pierogi", "bagel", "oatmilk"), mockup.keys)
        for (f in fixtures) {
            val r = process(f)
            val s = r.stats
            dump(f.key, r)
            assertEquals(px * px, r.argb.size)
            // Size and base (§3.1): at most 39u, base on y 42, centred on x 24.
            val side = maxOf(s.artRightU - s.artLeftU, s.artBottomU - s.artTopU)
            assertTrue("${f.key}: art side $side u", side <= 39.6)
            assertEquals("${f.key}: base", 42.0, s.artBottomU, 0.3)
            assertEquals("${f.key}: centre", 24.0, (s.artLeftU + s.artRightU) / 2, 0.3)
            // The same size and outline as post4 got from the same answer.
            val post4Scale = f.post4.getValue("scale").jsonPrimitive.double
            assertEquals("${f.key}: scale", post4Scale, s.scale, post4Scale * 0.06)
            val post4Area = f.post4.getValue("area_u2").jsonPrimitive.double
            assertEquals("${f.key}: area", post4Area, s.areaU2, post4Area * 0.08)
            assertTrue("${f.key}: outline ${s.outlineU}", s.outlineU in 1.85..2.6)
            // The procedural gloss: one stroke of at least 10u.
            assertTrue("${f.key}: gloss ${s.glossLengthU}", s.glossLengthU >= IconPostProcessorV4.GLOSS_MIN_U)
            assertPaletteOnly(f, r)
            assertWhiteDieCut(f, r)
        }
    }

    /** Every pixel is a palette colour or an edge blend of two; most are exactly one (flat fills). */
    private fun assertPaletteOnly(f: Fixture, r: IconPostProcessorV4.Result) {
        val pal = IconPostProcessorV4.palette(f.families).colors.map { rgb(it) }
        var opaque = 0
        var exact = 0
        var off = 0
        for (p in r.argb) {
            if ((p ushr 24) < 250) continue
            opaque++
            val c = rgb(p)
            if (pal.any { it.contentEquals(c) }) { exact++; continue }
            var best = Double.MAX_VALUE
            for (i in pal.indices) for (j in i + 1 until pal.size) best = minOf(best, segmentDistance(c, pal[i], pal[j]))
            if (best > 10.0) off++
        }
        assertTrue("${f.key}: ${off * 1000 / opaque} per mille off-palette", off <= opaque / 200)
        assertTrue("${f.key}: only ${exact * 100 / opaque} % exact palette pixels", exact >= opaque * 6 / 10)
    }

    /** Around the art there is only the white die-cut, 4u wide, and transparency beyond it: no baked shadow. */
    private fun assertWhiteDieCut(f: Fixture, r: IconPostProcessorV4.Result) {
        val s = r.stats
        // The cut path's box is the art's box grown by 4u.
        val loops = parsePath(r.cutPath)
        assertTrue("${f.key}: a cut path", loops.isNotEmpty())
        val xs = loops.flatten().map { it.first }
        val ys = loops.flatten().map { it.second }
        assertEquals("${f.key}: cut left", (s.artLeftU - 4 + 2) * ou, xs.min(), 0.6 * ou)
        assertEquals("${f.key}: cut right", (s.artRightU + 4 + 2) * ou, xs.max(), 0.6 * ou)
        assertEquals("${f.key}: cut top", (s.artTopU - 4 + 2) * ou, ys.min(), 0.6 * ou)
        assertEquals("${f.key}: cut bottom", (s.artBottomU + 4 + 2) * ou, ys.max(), 0.6 * ou)
        for (y in 0 until px) for (x in 0 until px) {
            val p = r.argb[y * px + x]
            val a = p ushr 24
            val ux = (x + 0.5) / ou - 2
            val uy = (y + 0.5) / ou - 2
            val outside = ux < s.artLeftU - 0.3 || ux > s.artRightU + 0.3 || uy < s.artTopU - 0.3 || uy > s.artBottomU + 0.3
            if (outside && a > 0) {
                val c = rgb(p)
                assertTrue("${f.key}: ($x, $y) ${c.toList()} in the border is not white", c.all { it >= 250 })
            }
            val far = ux < s.artLeftU - 4.8 || ux > s.artRightU + 4.8 || uy < s.artTopU - 4.8 || uy > s.artBottomU + 4.8
            if (far) assertEquals("${f.key}: ($x, $y) beyond the cut", 0, a)
        }
    }

    @Test
    fun eachStickerTakesUnder100Ms() {
        // Warm up the JIT (the phone runs ahead-of-time compiled code), then the best of five per sticker.
        repeat(3) { fixtures.forEach { process(it) } }
        val runs = fixtures.map { f -> List(5) { process(f).stats } }
        val times = runs.map { r -> r.minOf { it.millis } }
        println("v4 post-processing ms (best of 5): " + fixtures.zip(times).joinToString { (f, t) -> "${f.key} %.1f".format(t) })
        runs.forEachIndexed { i, r -> println("  ${fixtures[i].key}: ${r.minBy { it.millis }.steps}") }
        times.forEachIndexed { i, t -> assertTrue("${fixtures[i].key} took $t ms", t < 100.0) }
    }

    @Test
    fun theModelsOwnGlossAndSpecksAreRemovedAndTheTraceGrowsTo2u() {
        // A melon disc with a thin (1u) trace, five 1u mid specks and a thin white stroke of "model gloss" on the right.
        val pxu = 512 / 52.0
        val sheet = IntArray(1024 * 1024)
        val pastel = 0xFFD3F4C5.toInt()
        val mid = 0xFF8EDB76.toInt()
        val traceC = 0xFF2E8239.toInt()
        val cx = 256.0
        val cy = 270.0
        val radius = 15 * pxu
        val specks = listOf(-6.0 to -4.0, 3.0 to 6.0, 7.0 to -7.0, -2.0 to 8.0, 0.0 to 0.0)
        for (y in 0 until 512) for (x in 0 until 512) {
            val d = hypot(x - cx, y - cy)
            val p = when {
                d > radius -> 0
                d > radius - 1.0 * pxu -> traceC
                specks.any { (sx, sy) -> hypot(x - (cx + sx * pxu), y - (cy + sy * pxu)) < 0.5 * pxu } -> mid
                x - cx in 8 * pxu..9.2 * pxu && y - cy in -6 * pxu..6 * pxu -> 0xFFFFFFFF.toInt()
                else -> pastel
            }
            sheet[(y + 512) * 1024 + x + 512] = p
        }
        val r = IconPostProcessorV4.process(sheet, 1024, 1024, listOf("melon"), targetAreaU2 = 700.0)
        val s = r.stats
        assertTrue("specks ${s.specksRemoved}", s.specksRemoved >= 5)
        assertEquals(1, s.whiteSpecksRemoved)
        assertEquals(2.0, s.outlineU, 0.35)
        assertEquals(700.0, s.areaU2, 700 * 0.08)
        // No mid left (the specks were the only mid), and no white where the model's stroke was (8–9.2u right of centre).
        val midRgb = rgb(mid)
        assertFalse(r.argb.any { (it ushr 24) == 255 && rgb(it).contentEquals(midRgb) })
        val centreX = (s.artLeftU + s.artRightU) / 2
        val centreY = (s.artTopU + s.artBottomU) / 2
        val rad = (s.artRightU - s.artLeftU) / 2
        var whiteRight = 0
        var whiteUpperLeft = 0
        for (y in 0 until px) for (x in 0 until px) {
            val ux = (x + 0.5) / ou - 2
            val uy = (y + 0.5) / ou - 2
            if (hypot(ux - centreX, uy - centreY) > rad - 2.5) continue // inside the trace only
            if (!rgb(r.argb[y * px + x]).all { it >= 250 }) continue
            if (ux - centreX in 7.0..10.5 && kotlin.math.abs(uy - centreY) < 6) whiteRight++
            if (ux < centreX && uy < centreY) whiteUpperLeft++
        }
        assertEquals("the model's gloss is gone", 0, whiteRight)
        // The procedural gloss: a 2.6u stroke of at least 10u on the upper left (≥ 26u² ≈ 400 px at 4 px per u).
        assertTrue("procedural gloss pixels $whiteUpperLeft", whiteUpperLeft > 250)
        assertTrue(s.glossLengthU >= 10.0)
    }

    /** A synthetic answer in the fourth cell: [paint] gives each cell pixel's ARGB (0 = transparent). */
    private fun synthetic(paint: (x: Int, y: Int) -> Int): IntArray {
        val sheet = IntArray(1024 * 1024)
        for (y in 0 until 512) for (x in 0 until 512) sheet[(y + 512) * 1024 + x + 512] = paint(x, y)
        return sheet
    }

    @Test
    fun aFillLeftOpenToTheOutsideIsRimmedInItsOwnTrace() {
        // A melon-mid disc the model drew without any outline: the silhouette must still end in melon trace (§3.1).
        val pxu = 512 / 52.0
        val sheet = synthetic { x, y -> if (hypot(x - 256.0, y - 260.0) <= 15 * pxu) 0xFF8EDB76.toInt() else 0 }
        val r = IconPostProcessorV4.process(sheet, 1024, 1024, listOf("melon"), targetAreaU2 = 700.0)
        assertTrue("rimmed ${r.stats.rimmedU2}", r.stats.rimmedU2 > 100.0)
        assertEquals(2.0, r.stats.outlineU, 0.4)
        val s = r.stats
        val cx = (s.artLeftU + s.artRightU) / 2
        val cy = (s.artTopU + s.artBottomU) / 2
        val rad = (s.artRightU - s.artLeftU) / 2
        val trace = rgb(0xFF2E8239.toInt())
        var ring = 0
        var traced = 0
        for (y in 0 until px) for (x in 0 until px) {
            val d = hypot((x + 0.5) / ou - 2 - cx, (y + 0.5) / ou - 2 - cy)
            if (d !in rad - 1.4..rad - 0.6) continue // 1u inside the silhouette edge
            ring++
            if (rgb(r.argb[y * px + x]).contentEquals(trace)) traced++
        }
        assertTrue("$traced of $ring rim pixels are melon trace", traced * 100 >= ring * 97)
    }

    @Test
    fun raggedBoundariesBetweenTwoFillsAreSmoothed() {
        // A traced disc, pastel on top and mid below, the boundary dithered over 1u the way a gradient snaps.
        val pxu = 512 / 52.0
        val rnd = java.util.Random(7)
        val sheet = synthetic { x, y ->
            val d = hypot(x - 256.0, y - 260.0)
            val split = 260 + (rnd.nextDouble() - 0.5) * pxu
            when {
                d > 15 * pxu -> 0
                d > 13 * pxu -> 0xFFC3264F.toInt()
                y < split -> 0xFFFFC4D1.toInt()
                else -> 0xFFFF6F8E.toInt()
            }
        }
        val r = IconPostProcessorV4.process(sheet, 1024, 1024, listOf("strawberry"), targetAreaU2 = 700.0)
        val pastel = rgb(0xFFFFC4D1.toInt())
        val mid = rgb(0xFFFF6F8E.toInt())
        var columns = 0
        var clean = 0
        val s = r.stats
        val x0 = ((s.artLeftU + 2 + 6) * ou).toInt()
        val x1 = ((s.artRightU + 2 - 6) * ou).toInt()
        for (x in x0 until x1) {
            var last = 0
            var changes = 0
            for (y in 0 until px) {
                val c = rgb(r.argb[y * px + x])
                val now = if (c.contentEquals(pastel)) 1 else if (c.contentEquals(mid)) 2 else 0
                if (now != 0) {
                    if (last != 0 && now != last) changes++
                    last = now
                }
            }
            columns++
            if (changes <= 1) clean++
        }
        assertTrue("$clean of $columns columns cross the boundary once", clean * 100 >= columns * 95)
    }

    @Test
    fun anOpaqueAnswerIsKeyedOnItsFlatGrey() {
        val f = fixtures.first { it.key == "kimchi" }
        val flattened = IntArray(f.sheet.size) { i ->
            val p = f.sheet[i]
            val a = (p ushr 24) / 255.0
            fun ch(shift: Int) = (((p shr shift) and 0xFF) * a + 127 * (1 - a)).toInt()
            (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        val keyed = IconPostProcessorV4.process(flattened, 1024, 1024, f.families, f.targetU2)
        val cut = process(f)
        assertEquals(cut.stats.areaU2, keyed.stats.areaU2, cut.stats.areaU2 * 0.05)
        assertEquals(cut.stats.scale, keyed.stats.scale, cut.stats.scale * 0.05)
    }

    @Test
    fun anEmptyCellIsUnusable() {
        try {
            IconPostProcessorV4.process(IntArray(1024 * 1024), 1024, 1024, listOf("lemon"))
            fail("expected UnusableDrawing")
        } catch (e: IconPostProcessorV4.UnusableDrawing) {
            // expected
        }
    }

    @Test
    fun thePaletteIsTheFamiliesThenWhiteAndLemonPastel() {
        val pal = IconPostProcessorV4.palette(listOf("Sakura", "melon", "cocoa", "neon"))
        assertEquals(listOf("sakura", "melon"), pal.families)
        assertEquals(
            listOf(0xFFD6E8, 0xFF9FC8, 0xB02E6B, 0xE67BAA, 0xD3F4C5, 0x8EDB76, 0x2E8239, 0x6FBF62, 0xFFFFFF, 0xFFF3A6),
            pal.colors.toList(),
        )
        // Nothing known: the default family.
        assertEquals(listOf("periwinkle"), IconPostProcessorV4.palette(listOf("neon")).families)
    }

    /* ---------------- helpers ---------------- */

    private fun rgb(p: Int) = intArrayOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)

    private fun segmentDistance(c: IntArray, a: IntArray, b: IntArray): Double {
        val ab = DoubleArray(3) { (b[it] - a[it]).toDouble() }
        val ac = DoubleArray(3) { (c[it] - a[it]).toDouble() }
        val len2 = ab.sumOf { it * it }
        val t = if (len2 == 0.0) 0.0 else ((0 until 3).sumOf { ab[it] * ac[it] } / len2).coerceIn(0.0, 1.0)
        return sqrt((0 until 3).sumOf { (ac[it] - t * ab[it]).let { d -> d * d } })
    }

    /** "M x y L x y … Z" loops. */
    private fun parsePath(d: String): List<List<Pair<Double, Double>>> =
        d.split('Z').filter { it.isNotBlank() }.map { loop ->
            loop.trim().removePrefix("M").split('L').map { pt ->
                val (x, y) = pt.trim().split(' ').map(String::toDouble)
                x to y
            }
        }

    /** PICNIC_V4_DUMP=<dir>: write each result as a PNG for a visual check. */
    private fun dump(key: String, r: IconPostProcessorV4.Result) {
        val dir = System.getenv("PICNIC_V4_DUMP")?.let(::File) ?: return
        dir.mkdirs()
        val img = BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, px, px, r.argb, 0, px)
        ImageIO.write(img, "png", File(dir, "v4_$key.png"))
        File(dir, "v4_$key.cut").writeText(r.cutPath)
        File(dir, "v4_$key.json").writeText(r.stats.toString())
    }
}
