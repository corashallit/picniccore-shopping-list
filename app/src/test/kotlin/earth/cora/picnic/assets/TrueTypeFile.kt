package earth.cora.picnic.assets

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Just enough of the TrueType format for the font checks (WP2, docs/DESIGN_STICKER.md §2.7): the table directory,
 * `OS/2.usWeightClass`, the `name` table's family, and the code points the `cmap` maps (formats 4 and 12).
 */
class TrueTypeFile(val file: File) {
    private val buf: ByteBuffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.BIG_ENDIAN)
    private val tables: Map<String, Pair<Int, Int>> = run {
        val n = buf.getShort(4).toInt() and 0xFFFF
        (0 until n).associate { i ->
            val rec = 12 + 16 * i
            val tag = String(ByteArray(4) { buf.get(rec + it) }, Charsets.US_ASCII)
            tag to (buf.getInt(rec + 8) to buf.getInt(rec + 12))
        }
    }

    val tableTags: Set<String> get() = tables.keys

    val weightClass: Int
        get() = buf.getShort(table("OS/2") + 4).toInt() and 0xFFFF

    /** The font's own family name (name ID 1, Windows Unicode), e.g. "Zen Maru Gothic". */
    val familyName: String
        get() {
            val base = table("name")
            val count = u16(base + 2)
            val strings = base + u16(base + 4)
            for (i in 0 until count) {
                val r = base + 6 + 12 * i
                if (u16(r) == 3 && u16(r + 6) == 1) {
                    val len = u16(r + 8)
                    val off = strings + u16(r + 10)
                    return String(ByteArray(len) { buf.get(off + it) }, Charsets.UTF_16BE)
                }
            }
            error("no family name in ${file.name}")
        }

    /** Every code point the cmap maps to a glyph other than .notdef. */
    val codePoints: Set<Int> by lazy {
        val cmap = table("cmap")
        val n = u16(cmap + 2)
        val subtables = (0 until n).map { i ->
            val r = cmap + 4 + 8 * i
            Triple(u16(r), u16(r + 2), cmap + buf.getInt(r + 4))
        }
        val pick = subtables.firstOrNull { it.first == 3 && it.second == 10 }
            ?: subtables.firstOrNull { it.first == 3 && it.second == 1 }
            ?: subtables.first()
        readSubtable(pick.third)
    }

    private fun readSubtable(at: Int): Set<Int> {
        val out = HashSet<Int>()
        when (val format = u16(at)) {
            4 -> {
                val segX2 = u16(at + 6)
                val ends = at + 14
                val starts = ends + segX2 + 2
                val deltas = starts + segX2
                val ranges = deltas + segX2
                for (s in 0 until segX2 / 2) {
                    val end = u16(ends + 2 * s)
                    val start = u16(starts + 2 * s)
                    val delta = buf.getShort(deltas + 2 * s).toInt()
                    val rangeAt = ranges + 2 * s
                    val range = u16(rangeAt)
                    for (c in start..end) {
                        if (c == 0xFFFF) continue
                        val glyph = if (range == 0) {
                            (c + delta) and 0xFFFF
                        } else {
                            val g = u16(rangeAt + range + 2 * (c - start))
                            if (g == 0) 0 else (g + delta) and 0xFFFF
                        }
                        if (glyph != 0) out += c
                    }
                }
            }
            12 -> {
                val groups = buf.getInt(at + 12)
                for (g in 0 until groups) {
                    val r = at + 16 + 12 * g
                    val start = buf.getInt(r)
                    val end = buf.getInt(r + 4)
                    val first = buf.getInt(r + 8)
                    for (c in start..end) if (first + (c - start) != 0) out += c
                }
            }
            else -> error("cmap format $format in ${file.name}")
        }
        return out
    }

    private fun table(tag: String): Int = tables[tag]?.first ?: error("no $tag table in ${file.name}")

    private fun u16(at: Int): Int = buf.getShort(at).toInt() and 0xFFFF
}
