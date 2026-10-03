package earth.cora.picnic.ui.list.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.ui.theme.AisleLooks
import earth.cora.picnic.ui.theme.PicnicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The basket's to-get badge at large font scales (docs/DESIGN_STICKER.md §4.12, §2.7, §15; item 107): the 13 sp numeral
 * follows the font up to BADGE_MAX_SCALE (the count is also in the strip's meta line, which grows all the way), and the
 * badge grows from its 22 dp circle into a pill round it, so the numeral never runs over the trace ring. Its bottom-left
 * corner stays on the flag slot's, so it grows up and to the right, never inward over the basket art. MotionBasket is
 * the basket WP5 draws on strips and tray headers.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class BasketBadgeTest {
    @get:Rule
    val compose = createComposeRule()

    private val counts = listOf(3, 12, 100)

    private fun show(fontScale: Float): Map<Int, IntSize> {
        val numerals = HashMap<Int, IntSize>()
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                PicnicTheme {
                    // The numeral as the badge sets it, measured on its own.
                    val measurer = rememberTextMeasurer()
                    val sp = 13f * minOf(fontScale, BADGE_MAX_SCALE) / fontScale
                    val style = PicnicTheme.type.badgeNumeral.copy(fontSize = sp.sp, lineHeight = (sp * 1.15f).sp)
                    for (n in counts) numerals[n] = measurer.measure(if (n > 99) "99+" else n.toString(), style).size
                    Row(
                        Modifier.testTag("badges").background(PicnicTheme.colors.binder).padding(start = 10.dp, end = 30.dp, top = 6.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                    ) {
                        counts.forEachIndexed { i, n ->
                            Box(Modifier.testTag("basket_$n")) {
                                MotionBasket(
                                    kind = BasketKind.MIXING_BOWL,
                                    look = AisleLooks.of(i + 4),
                                    gotten = emptyList(),
                                    toGet = n,
                                    complete = false,
                                    presentation = null,
                                    clock = MotionClock(),
                                    size = DpSize(80.dp, 64.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        return numerals
    }

    private fun check(fontScale: Float, numerals: Map<Int, IntSize>) {
        val d = compose.density.density
        val badges = compose.onAllNodesWithTag(BADGE_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }
        assertEquals(counts.size, badges.size)
        counts.forEachIndexed { i, n ->
            val badge = badges[i]
            val basket = compose.onNodeWithTag("basket_$n", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val numeral = numerals.getValue(n)
            // The white face inside the 2 dp ring holds the numeral, centred.
            val face = Rect(badge.left + 2 * d, badge.top + 2 * d, badge.right - 2 * d, badge.bottom - 2 * d)
            assertTrue("fs $fontScale, $n: numeral ${numeral.width} px wide in a ${face.width} px face", numeral.width <= face.width + 0.5f)
            assertTrue("fs $fontScale, $n: numeral ${numeral.height} px tall in a ${face.height} px face", numeral.height <= face.height + 0.5f)
            assertTrue("fs $fontScale, $n: at least the 22 dp circle", badge.height >= 22 * d - 0.5f && badge.width >= badge.height - 0.5f)
            // On the flag slot: the 22 dp slot's left edge 10 dp inside the basket box's right edge (its right edge 12 dp
            // outside), its bottom 30 dp down (8 dp + 22 dp, a folded strip); a bigger badge grows up and right from there.
            assertEquals("fs $fontScale, $n: left edge (never further over the art)", basket.right - 10 * d, badge.left, 1f)
            assertEquals("fs $fontScale, $n: bottom", basket.top + 30 * d, badge.bottom, 1f)
        }
    }

    @Test
    fun badgeHoldsItsNumeralAtFontScale100() {
        val numerals = show(1f)
        check(1f, numerals)
        // At 1.0 the one- and two-digit badges are the 22 dp circle.
        val d = compose.density.density
        val badges = compose.onAllNodesWithTag(BADGE_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }
        for (b in badges.take(2)) {
            assertEquals(22 * d, b.width, 1f)
            assertEquals(22 * d, b.height, 1f)
        }
    }

    /** Golden basket_badge_fs200: 3, 12 and 99+ at font scale 2.0, each numeral inside its pill. */
    @Test
    fun badgeHoldsItsNumeralAtFontScale200() {
        val numerals = show(2f)
        check(2f, numerals)
        compose.onNodeWithTag("badges").captureRoboImage("$SCREENSHOTS/basket_badge_fs200.png")
    }
}
