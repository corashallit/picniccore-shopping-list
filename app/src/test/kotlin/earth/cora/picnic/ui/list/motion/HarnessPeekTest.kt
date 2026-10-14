package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Development aid: renders harness frames to a folder for a look (not goldens). Runs only with WP6_PEEK=<dir>.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class HarnessPeekTest {
    @get:Rule
    val compose = createComposeRule()

    private val dir: String? = System.getenv("WP6_PEEK")?.takeIf { File(it).isDirectory }

    @Test
    fun checkOffChain() {
        assumeTrue(dir != null)
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        rig.peek(File(dir, "harness_rest.png"))
        val t0 = rig.tap("baking-powder")
        for (t in listOf(0L, 100L, 240L, 400L, 500L, 640L, 960L, 1100L, 1250L)) {
            rig.advanceTo(t0 + t)
            rig.peek(File(dir, "chain_$t.png"))
        }
        for (t in listOf(1300L, 1500L, 1800L, 2100L, 2600L)) {
            rig.advanceTo(t0 + 620 + t)
            rig.peek(File(dir, "aisle_L+$t.png"))
        }
    }

    @Test
    fun celebration() {
        assumeTrue(dir != null)
        val rig = HarnessRig(compose, MotionFixture.allBut(listOf("toothpaste"), open = "household")).start()
        val t0 = rig.tap("toothpaste")
        val start = t0 + 620 + 1350
        for (t in listOf(100L, 700L, 2400L, 3300L, 4600L, 5000L, 6600L, 9500L, 11500L, 12200L, 15000L)) {
            rig.advanceTo(start + t)
            rig.peek(File(dir, "cele_$t.png"))
        }
    }

    /** The celebration's entrance, every frame from just before the scene to 0.64 s, over a Day and a Glow list. */
    @Test
    fun entranceDay() = entrance(ThemeVariant.Day)

    @Test
    fun entranceGlow() = entrance(ThemeVariant.Glow)

    private fun entrance(variant: ThemeVariant) {
        assumeTrue(dir != null)
        val rig = HarnessRig(compose, MotionFixture.allBut(listOf("toothpaste"), open = "household"), variant = variant).start()
        // Toothpaste's row on screen, so the iris opens from the checkbox she tapped.
        compose.onNodeWithTag(HarnessTags.LIST).performScrollToIndex(rig.model.grouped().sections.indexOfFirst { it.id == "household" })
        rig.advance(300)
        val t0 = rig.tap("toothpaste")
        val start = t0 + 620 + Beats.CELEBRATION
        rig.advanceTo(start - 48)
        while (rig.now <= start + 640) {
            rig.peek(File(dir, "entrance_${variant.suffix}_${rig.now - start}.png"))
            rig.frame()
        }
    }
}
