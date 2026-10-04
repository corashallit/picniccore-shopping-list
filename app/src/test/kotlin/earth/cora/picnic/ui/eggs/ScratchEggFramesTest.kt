package earth.cora.picnic.ui.eggs

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Scratch: dumps frames for a visual check (not committed). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ScratchEggFramesTest {
    @get:Rule
    val compose = createComposeRule()
    private val out = File("/tmp/claude-0/-home-user-picniccore-shopping-list/0abd3c0d-8466-5393-83a4-67568bc936fe/scratchpad/frames").apply { mkdirs() }

    private fun save(name: String) {
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun m100() = midnight(1f)
    @Test fun m130() = midnight(1.3f)
    @Test fun m200() = midnight(2f)

    private fun midnight(scale: Float) {
        val r = EggRig(compose, startAt = torontoMs(java.time.LocalTime.of(23, 30)), fontScale = scale)
        r.start()
        r.advance(1_500)
        val word = compose.onAllNodesWithTag(EggTags.SNACK, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot to it.layoutInfo.isPlaced }
        val star = compose.onAllNodesWithTag(EggTags.SNACK_STAR, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        File(out, "mid_$scale.txt").writeText("scale $scale tab=${r.anchor(EggAnchors.tab("frozen"))} name=${r.anchor(EggAnchors.TEXT_PREFIX + "tab:frozen")} basket=${r.anchor(AnchorRegistry.basketKey("frozen"))} strip=${r.anchor(EggAnchors.strip("frozen"))} word=$word star=$star\n")
        save("mid_$scale")
    }
}
