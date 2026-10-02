package earth.cora.picnic.assets

import earth.cora.picnic.R
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checklist item 275 (WP2, docs/DESIGN_STICKER.md §14.2): the ten clips are 22.05 kHz, 16-bit mono PCM WAVs as long as
 * their recipes (±5 ms), peaking at −6 dBFS, starting with a fade-in (no click) and dying away to near silence; there
 * are no others. Rendered by design/sounds/make_sounds.py; `:app:checkSounds` keeps them in sync with the script.
 */
@RunWith(RobolectricTestRunner::class)
class SoundAssetsTest {
    private val raw = File("src/main/res/raw")

    @Test
    fun exactlyTheTenClips() {
        val clips = raw.listFiles().orEmpty().map { it.name }.filter { it.startsWith("sk_") }.sorted()
        assertEquals(LENGTH_MS.keys.map { "sk_$it.wav" }.sorted(), clips)
        // and each is a raw resource the player can load by name
        assertEquals(10, listOf(R.raw.sk_thunk, R.raw.sk_tk, R.raw.sk_pff, R.raw.sk_clunk, R.raw.sk_beep, R.raw.sk_pon,
            R.raw.sk_kira, R.raw.sk_peel, R.raw.sk_jingle, R.raw.sk_print).toSet().size)
    }

    @Test
    fun clipsMatchTheirRecipes() {
        for ((name, ms) in LENGTH_MS) {
            val wav = Wav(File(raw, "sk_$name.wav"))
            assertEquals("$name rate", 22050, wav.rate)
            assertEquals("$name channels", 1, wav.channels)
            assertEquals("$name bits", 16, wav.bits)
            val lengthMs = wav.samples.size * 1000.0 / wav.rate
            assertTrue("$name is $lengthMs ms, recipe $ms ms", abs(lengthMs - ms) <= 5.0)
            val peak = wav.samples.maxOf { abs(it.toInt()) } / 32768.0
            val dbfs = 20 * log10(peak)
            assertTrue("$name peaks at $dbfs dBFS", abs(dbfs - -6.02) <= 0.1)
            // fade-in: silent start, and the first millisecond stays well under the peak
            assertTrue("$name starts with a click", abs(wav.samples.first().toInt()) <= 64)
            val firstMs = wav.samples.take(22).maxOf { abs(it.toInt()) } / 32768.0
            assertTrue("$name first ms reaches $firstMs", firstMs < peak * 0.5)
            // exponential decay: the last millisecond is near silence
            val lastMs = wav.samples.takeLast(22).maxOf { abs(it.toInt()) } / 32768.0
            assertTrue("$name ends at $lastMs", lastMs < peak * 0.05)
        }
    }

    @Test
    fun soundsStaySmall() {
        val total = LENGTH_MS.keys.sumOf { File(raw, "sk_$it.wav").length() }
        assertTrue("sounds total $total bytes", total in 100_000..160_000)
    }

    private class Wav(file: File) {
        val rate: Int
        val channels: Int
        val bits: Int
        val samples: ShortArray

        init {
            val b = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            check(String(file.readBytes(), 0, 4, Charsets.US_ASCII) == "RIFF") { "${file.name} is not RIFF" }
            var at = 12
            var fmt: Triple<Int, Int, Int>? = null
            var data: ShortArray? = null
            while (at + 8 <= b.limit()) {
                val id = String(ByteArray(4) { b.get(at + it) }, Charsets.US_ASCII)
                val size = b.getInt(at + 4)
                if (id == "fmt ") {
                    check(b.getShort(at + 8).toInt() == 1) { "${file.name} is not PCM" }
                    fmt = Triple(b.getShort(at + 10).toInt(), b.getInt(at + 12), b.getShort(at + 22).toInt())
                } else if (id == "data") {
                    data = ShortArray(size / 2) { b.getShort(at + 8 + 2 * it) }
                }
                at += 8 + size + (size and 1)
            }
            val (c, r, bps) = checkNotNull(fmt) { "${file.name} has no fmt chunk" }
            channels = c
            rate = r
            bits = bps
            samples = checkNotNull(data) { "${file.name} has no data chunk" }
        }
    }

    companion object {
        /** §14.2: each clip lasts until its last tone ends (start + duration) */
        val LENGTH_MS: Map<String, Int> = mapOf(
            "thunk" to 120, "tk" to 30, "pff" to 90, "clunk" to 140, "beep" to 50,
            "pon" to 190, "kira" to 160, "peel" to 50, "jingle" to 650, "print" to 1105,
        )
    }
}
