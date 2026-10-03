package earth.cora.picnic.ui.add

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No Material defaults in the add flows (docs/DESIGN_STICKER.md §0.2 polish, ARCHITECTURE §11): inside PicnicTheme the
 * default indication is Material's ripple, so every clickable, toggleable and selectable in WP7's package passes
 * `indication = null` and draws the spec's own pressed state (the highlighter band, or pressed flat). A source scan:
 * each such call must name its indication.
 */
class AddNoRippleTest {
    private val dir = File("src/main/kotlin/earth/cora/picnic/ui/add")

    @Test
    fun everyControlDrawsItsOwnPressedState() {
        assertTrue("sources at ${dir.absolutePath}", dir.isDirectory)
        val offenders = mutableListOf<String>()
        val call = Regex("""\.(clickable|toggleable|selectable|combinedClickable|triStateToggleable)\(""")
        for (file in dir.listFiles { f -> f.extension == "kt" }.orEmpty()) {
            val text = file.readText()
            for (m in call.findAll(text)) {
                val args = argumentsAt(text, m.range.last)
                if (!Regex("""indication\s*=\s*null""").containsMatchIn(args)) {
                    val line = text.substring(0, m.range.first).count { it == '\n' } + 1
                    offenders += "${file.name}:$line ${m.value}"
                }
            }
        }
        assertTrue("controls with the default (Material ripple) indication: $offenders", offenders.isEmpty())
    }

    /** The text between the "(" at [open] and its matching ")". */
    private fun argumentsAt(text: String, open: Int): String {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return text.substring(open + 1, i)
            }
        }
        return text.substring(open + 1)
    }
}
