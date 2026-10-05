package earth.cora.picnic.icons

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Replays the live end-to-end run (`icons/live/<item>/`: the raw gpt-image-2.5-flare answers to the app's own
 * neighbour sheets, with the decider's brief; [LiveStickerEndToEndTest] recorded them) through the device processor,
 * offline. Each must land on the library's grid and palette, and match the sticker committed next to it (a change to
 * the post-processing shows up here as a diff to review; `PICNIC_V4_DUMP=<dir>` writes the new results).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveStickerFixturesTest {
    private val items = listOf("dragon_fruit", "gochujang", "mochi")
    private val px = IconPostProcessorV4.OUT_PX
    private val ou = px / IconPostProcessorV4.BOX_U

    private fun resource(path: String): File =
        File(requireNotNull(javaClass.classLoader!!.getResource(path)) { "missing test resource $path" }.toURI())

    private fun brief(item: String): JsonObject = Json.parseToJsonElement(resource("icons/live/$item/brief.json").readText()).jsonObject

    @Test
    fun theLiveAnswersBecomeLibraryGradeStickers() {
        // Process (and dump) all three first, so a re-recording gets every result even when one fails below.
        val results = items.associateWith { item ->
            val b = brief(item)
            val families = b.getValue("families").jsonArray.map { it.jsonPrimitive.content }
            val target = b.getValue("target_u2").jsonPrimitive.double
            BitmapLiveStickerProcessor().process(resource("icons/live/$item/raw.webp").readBytes(), families, target)
                .also { dump(item, it) }
        }
        for (item in items) {
            val b = brief(item)
            val families = b.getValue("families").jsonArray.map { it.jsonPrimitive.content }
            val target = b.getValue("target_u2").jsonPrimitive.double
            val sticker = results.getValue(item)
            val s = sticker.stats!!

            // Grid (§3.1): base on y 42, centred on x 24, at most 39u, the neighbours' mean area.
            assertEquals("$item base", 42.0, s.artBottomU, 0.3)
            assertEquals("$item centre", 24.0, (s.artLeftU + s.artRightU) / 2, 0.3)
            assertTrue("$item side", maxOf(s.artRightU - s.artLeftU, s.artBottomU - s.artTopU) <= 39.6)
            assertEquals("$item area", target, s.areaU2, target * 0.08)
            assertTrue("$item outline ${s.outlineU}", s.outlineU in 1.85..2.6)
            assertTrue("$item gloss", s.glossLengthU >= IconPostProcessorV4.GLOSS_MIN_U)

            val argb = decode(sticker.webp)
            // Palette (§3.1): every opaque pixel is one of the item's families' tones, white or lemon pastel, or an
            // edge blend of two of them.
            val pal = IconPostProcessorV4.palette(families).colors.map { rgb(it) }
            var opaque = 0
            var off = 0
            for (p in argb) {
                if ((p ushr 24) < 250) continue
                opaque++
                val c = rgb(p)
                if (pal.any { it.contentEquals(c) }) continue
                var best = Double.MAX_VALUE
                for (i in pal.indices) for (j in i + 1 until pal.size) best = minOf(best, segmentDistance(c, pal[i], pal[j]))
                if (best > 10.0) off++
            }
            assertTrue("$item: $off of $opaque off-palette", off <= opaque / 200)
            // The silhouette is outlined in trace: just inside the art's edge, the pixels are trace tones.
            assertTracedEdge(item, argb, families, s)

            // The committed result: the same sticker (a post-processing change must be reviewed and re-recorded).
            val committed = decode(resource("icons/live/$item/sticker.webp").readBytes())
            var differ = 0
            var counted = 0
            for (i in argb.indices) {
                val a = argb[i]
                val c = committed[i]
                if ((a ushr 24) < 128 && (c ushr 24) < 128) continue
                counted++
                if (kotlin.math.abs((a ushr 24) - (c ushr 24)) > 8 || rgb(a).zip(rgb(c)).any { (x, y) -> kotlin.math.abs(x - y) > 8 }) differ++
            }
            assertTrue("$item: $differ of $counted pixels differ from the committed sticker", differ * 100 <= counted)
            assertEquals("$item cut", resource("icons/live/$item/sticker.cut").readText(), sticker.cut)
        }
    }

    /**
     * Walking in from the die-cut along each row, the art starts with its colour trace: 3 px (0.75u) in, past the
     * anti-aliased seam, the pixel is a trace or a blend of two traces (where two families' outlines meet). Near-level
     * edges (a row grazing a top or bottom) may land deeper, hence 93 %.
     */
    private fun assertTracedEdge(item: String, argb: IntArray, families: List<String>, s: IconPostProcessorV4.Stats) {
        val traces = IconPostProcessorV4.palette(families).let { p -> p.colors.filterIndexed { i, _ -> p.tones[i] == IconPostProcessorV4.Tone.TRACE } }.map { rgb(it) }
        var rows = 0
        var traced = 0
        val y0 = ((s.artTopU + 2 + 3) * ou).toInt()
        val y1 = ((s.artBottomU + 2 - 3) * ou).toInt()
        for (y in y0 until y1) {
            for (dir in listOf(1, -1)) {
                var x = if (dir == 1) 0 else px - 1
                // Skip the transparent margin and the white die-cut.
                while (x in 0 until px && ((argb[y * px + x] ushr 24) < 250 || rgb(argb[y * px + x]).all { it >= 245 })) x += dir
                if (x !in 0 until px) continue
                val c = rgb(argb[y * px + (x + 3 * dir).coerceIn(0, px - 1)])
                rows++
                val near = traces.any { t -> segmentDistance(c, t, t) <= 40 } ||
                    traces.indices.any { i -> (i + 1 until traces.size).any { j -> segmentDistance(c, traces[i], traces[j]) <= 25 } }
                if (near) traced++
            }
        }
        assertTrue("$item: ${traced * 100 / rows} % of the silhouette edge is trace", traced * 100 >= rows * 93)
    }

    private fun decode(bytes: ByteArray): IntArray {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!
        return IntArray(px * px).also { bmp.getPixels(it, 0, px, 0, 0, px, px) }
    }

    private fun rgb(p: Int) = intArrayOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)

    private fun segmentDistance(c: IntArray, a: IntArray, b: IntArray): Double {
        val ab = DoubleArray(3) { (b[it] - a[it]).toDouble() }
        val ac = DoubleArray(3) { (c[it] - a[it]).toDouble() }
        val len2 = ab.sumOf { it * it }
        val t = if (len2 == 0.0) 0.0 else ((0 until 3).sumOf { ab[it] * ac[it] } / len2).coerceIn(0.0, 1.0)
        return kotlin.math.sqrt((0 until 3).sumOf { (ac[it] - t * ab[it]).let { d -> d * d } })
    }

    private fun dump(item: String, sticker: ProcessedSticker) {
        val dir = System.getenv("PICNIC_V4_DUMP")?.let(::File) ?: return
        File(dir, "live_$item").apply { mkdirs() }.let {
            File(it, "sticker.webp").writeBytes(sticker.webp)
            File(it, "sticker.cut").writeText(sticker.cut)
            File(it, "stats.txt").writeText(sticker.stats.toString())
        }
    }
}
