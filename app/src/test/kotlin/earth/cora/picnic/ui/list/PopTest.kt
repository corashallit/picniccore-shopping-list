package earth.cora.picnic.ui.list

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.TOAST_TAG
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A commit's pops play once (docs/DESIGN_STICKER.md §6.2 rule 7 and "Commit", §4.11; items 213 and 87): the row's
 * sticker pops and its text band flashes the highlighter once, at the commit. Folding the aisle and opening it again,
 * or scrolling the row away and back, never plays it again; a row in a folded aisle, first shown long after the commit,
 * never flashes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class PopTest {
    @get:Rule
    val compose = createComposeRule()

    private var band = 0

    /**
     * Fewer band-coloured pixels than this over a row is no flash (stray anti-aliased edges, the lemon ring of a tray
     * still unfolding); the band behind a row's words is tens of thousands.
     */
    private val FLASH = 1000

    private fun show(rig: ListRig) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            band = PicnicTheme.colors.pressedBand.toArgb()
            rig.Themed()
        }
        compose.frames(10)
    }

    /**
     * Highlighter pixels over [itemId]'s row (0 when the row is not on screen), leaving out the commit's sticky note
     * (lemon paper too) where it overlaps the row.
     */
    private fun flashPixels(itemId: String): Int {
        if (!compose.exists(ListTags.row(itemId))) return 0
        val r: Rect = compose.boundsOf(ListTags.row(itemId))
        val note: Rect? = if (compose.exists(TOAST_TAG)) compose.boundsOf(TOAST_TAG).inflate(12 * compose.density.density) else null
        val bmp: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        var n = 0
        for (y in r.top.toInt().coerceAtLeast(0) until r.bottom.toInt().coerceAtMost(bmp.height)) {
            for (x in r.left.toInt().coerceAtLeast(0) until r.right.toInt().coerceAtMost(bmp.width)) {
                if (note != null && note.contains(androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat()))) continue
                if (near(bmp.getPixel(x, y), band)) n++
            }
        }
        return n
    }

    private fun near(a: Int, b: Int, tol: Int = 6): Boolean =
        abs(((a shr 16) and 0xff) - ((b shr 16) and 0xff)) <= tol &&
            abs(((a shr 8) and 0xff) - ((b shr 8) and 0xff)) <= tol &&
            abs((a and 0xff) - (b and 0xff)) <= tol

    /** A duplicate add (as typed) of [name]: its row pops (rule 7). */
    private fun addAgain(rig: ListRig, name: String) {
        compose.runOnIdle {
            rig.addVm.setText(name)
            rig.addVm.addAsTyped()
        }
    }

    @Test
    fun aPopPlaysOnce() {
        val rig = ListRig()
        show(rig)
        val still = flashPixels("baking-powder")
        assertTrue("before the pop, no band ($still)", still < FLASH)
        addAgain(rig, "Baking powder")
        compose.frames(3)
        assertTrue("the band flashes at the commit", flashPixels("baking-powder") > 10 * FLASH)
        // (the band behind "Baking powder" and its detail line)
        compose.advanceMs(1200)
        assertTrue("then it is gone", flashPixels("baking-powder") < FLASH)
        // Fold Baking, open it again: the row comes back without a flash.
        compose.runOnIdle { rig.vm.foldAll() }
        compose.advanceMs(800)
        compose.runOnIdle { rig.vm.openSection("aisle-2-baking") }
        compose.frames(3)
        assertTrue(compose.exists(ListTags.row("baking-powder")))
        val back = flashPixels("baking-powder")
        assertTrue("no flash when the row comes back ($back)", back < FLASH)
        compose.advanceMs(300)
        assertTrue(flashPixels("baking-powder") < FLASH)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun aRowInAFoldedAisleNeverFlashesLater() {
        val rig = ListRig()
        show(rig)
        // Frozen is folded: Frozen peas is not on screen when its duplicate pops.
        assertTrue(!compose.exists(ListTags.row("peas")))
        addAgain(rig, "Frozen peas")
        compose.advanceMs(3000)
        compose.runOnIdle { rig.vm.openSection("frozen") }
        var most = 0
        repeat(30) {
            compose.frames(1)
            most = maxOf(most, flashPixels("peas"))
        }
        assertTrue(compose.exists(ListTags.row("peas")))
        assertTrue("opened later, the row never flashes ($most)", most < FLASH)
        compose.mainClock.autoAdvance = true
    }
}
