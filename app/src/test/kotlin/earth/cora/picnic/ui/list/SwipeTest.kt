package earth.cora.picnic.ui.list

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.Families
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Swipes on a row (docs/DESIGN_STICKER.md §4.11, §8.6, D10; item 117): right past 56 dp checks with the full chain, left
 * past 56 dp tears the row off; each shows its own hint while the finger is down; shorter swipes snap back; the
 * TalkBack actions stay.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class SwipeTest {
    @get:Rule
    val compose = createComposeRule()

    private val dp get() = compose.density.density

    /** The colour under the row's left (or right) edge, inside the tray's panel. */
    private fun bandColour(id: String, left: Boolean): Int {
        val slot = compose.boundsOf(ListTags.rowSlot(id))
        val x = if (left) slot.left + 16 * dp else slot.right - 14 * dp
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        return bmp.getPixel(x.toInt(), (slot.top + 6 * dp).toInt())
    }

    private fun near(a: Int, b: Int): Boolean =
        listOf(16, 8, 0).all { s -> abs(((a shr s) and 0xFF) - ((b shr s) and 0xFF)) <= 4 }

    @Test
    fun rightChecksLeftTears() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        compose.show(rig)
        val row = compose.onNodeWithTag(ListTags.row("strawberries"))
        val lemon = Families.lemon.pastel.toArgb()

        // A short right swipe (40 dp) shows the lemon GOT IT band while held, then snaps back without checking.
        row.performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(20 * dp, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(20 * dp, 0f))
        }
        compose.waitForIdle()
        assertTrue("the right swipe's hint is the lemon band", near(bandColour("strawberries", left = true), lemon))
        row.performTouchInput { up() }
        compose.waitForIdle()
        assertFalse("40 dp is short of 56 dp", rig.app.isChecked("strawberries"))

        // Past 56 dp: checked, with the chain (the hanko, then the hop to the basket).
        row.performTouchInput {
            down(center)
            repeat(4) { moveBy(androidx.compose.ui.geometry.Offset(20 * dp, 0f)) }
            up()
        }
        compose.waitForIdle()
        assertTrue("80 dp right checks it", rig.app.isChecked("strawberries"))
        assertTrue("the chain played: the sticker flew to Produce's basket", rig.engine.flightLog.any { it.itemId == "strawberries" && !it.back })

        // The TalkBack actions are still there (gestures are never the only way, §15).
        val labels = compose.onNodeWithTag(ListTags.row("strawberries")).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
        assertEquals(listOf("Uncheck", "Delete", "Edit"), labels)

        // A short left swipe shows the backing band with the scissors, then snaps back.
        val apples = compose.onNodeWithTag(ListTags.row("apples"))
        apples.performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-20 * dp, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(-20 * dp, 0f))
        }
        compose.waitForIdle()
        val backing = earth.cora.picnic.ui.theme.DayColors.backing.toArgb()
        assertTrue("the left swipe's hint is the backing band", near(bandColour("apples", left = false), backing))
        apples.performTouchInput { up() }
        compose.waitForIdle()
        assertTrue("40 dp left keeps the row", rig.app.items().any { it.id == "apples" })

        // Past 56 dp left: torn off, with the Undo note (the clock held, so the note is read while it holds).
        compose.mainClock.autoAdvance = false
        apples.performTouchInput {
            down(center)
            repeat(4) { moveBy(androidx.compose.ui.geometry.Offset(-20 * dp, 0f)) }
            up()
        }
        compose.advanceMs(1200)
        assertFalse("80 dp left tears it off", rig.app.items().any { it.id == "apples" })
        compose.onNodeWithText("McIntosh apples torn out", useUnmergedTree = true).assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

    }

    /**
     * A gotten row has nothing to check (D10, item 117): a right swipe does not move it, shows no GOT IT hint, and
     * leaves it checked; a left swipe still tears it off.
     */
    @Test
    fun rightOnCheckedRowKeepsIt() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, "produce"))
        compose.show(rig)
        assertTrue(rig.app.isChecked("bananas"))
        val row = compose.onNodeWithTag(ListTags.row("bananas"))
        val before = compose.boundsOf(ListTags.row("bananas"))
        val lemon = Families.lemon.pastel.toArgb()
        row.performTouchInput {
            down(center)
            repeat(4) { moveBy(androidx.compose.ui.geometry.Offset(20 * dp, 0f)) }
        }
        compose.waitForIdle()
        assertFalse("no GOT IT band on a gotten row", near(bandColour("bananas", left = true), lemon))
        assertEquals("the row stays put", before.left, compose.boundsOf(ListTags.row("bananas")).left, 0.5f)
        row.performTouchInput { up() }
        compose.waitForIdle()
        assertTrue("still checked after 80 dp right", rig.app.isChecked("bananas"))
        assertTrue("no un-check chain played", rig.engine.flightLog.none { it.itemId == "bananas" })
    }
}
