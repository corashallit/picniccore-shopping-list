package earth.cora.picnic.assets

import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Checklist items 18 and 20 (WP2, docs/DESIGN_STICKER.md §2.7): `res/font` holds exactly the seven bundled faces with
 * the right weights (DynaPuff as static 600 / 700 instances, not the variable font), Android loads each one, and
 * neither DotGothic16 (the LCD is vector dots) nor Nanum Pen Script (a mockup prop) ships. The files are built by
 * design/fonts/build_fonts.py.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontFilesTest {
    private val fontDir = File("src/main/res/font")

    @Test
    fun filesAndWeights() {
        val files = fontDir.listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(EXPECTED.keys.sorted(), files)
        for ((name, want) in EXPECTED) {
            val font = TrueTypeFile(File(fontDir, name))
            val (family, weight) = want
            assertEquals("$name OS/2 weight", weight, font.weightClass)
            assertTrue("$name is ${font.familyName}, expected $family", font.familyName.startsWith(family))
            assertFalse("$name must be a static instance", "fvar" in font.tableTags)
        }
    }

    @Test
    fun noExtraFonts() {
        val shipped = File("src/main").walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf", "ttc", "woff", "woff2") }
            .toList()
        assertEquals(shipped.map { it.path }.toString(), EXPECTED.size, shipped.size)
        for (file in shipped) {
            val family = TrueTypeFile(file).familyName.lowercase()
            val name = file.name.lowercase()
            assertFalse(file.path, "dotgothic" in name || "nanum" in name || "dotgothic" in family || "nanum" in family)
        }
    }

    @Test
    fun androidLoadsEveryFace() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (id in listOf(
            R.font.dynapuff_semibold, R.font.dynapuff_bold, R.font.zen_maru_gothic_medium, R.font.zen_maru_gothic_bold,
            R.font.zen_maru_gothic_black, R.font.klee_one_semibold, R.font.hachi_maru_pop_regular,
        )) {
            assertNotNull(context.resources.getResourceEntryName(id), ResourcesCompat.getFont(context, id))
        }
    }

    companion object {
        /** file -> (family name prefix, OS/2 weight), §2.7 */
        val EXPECTED: Map<String, Pair<String, Int>> = mapOf(
            "dynapuff_semibold.ttf" to ("DynaPuff" to 600),
            "dynapuff_bold.ttf" to ("DynaPuff" to 700),
            "zen_maru_gothic_medium.ttf" to ("Zen Maru Gothic" to 500),
            "zen_maru_gothic_bold.ttf" to ("Zen Maru Gothic" to 700),
            "zen_maru_gothic_black.ttf" to ("Zen Maru Gothic" to 900),
            "klee_one_semibold.ttf" to ("Klee One" to 600),
            "hachi_maru_pop_regular.ttf" to ("Hachi Maru Pop" to 400),
        )
    }
}
