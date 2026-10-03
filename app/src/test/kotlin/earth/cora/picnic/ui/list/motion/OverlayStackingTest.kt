package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.theme.TOAST_TAG
import earth.cora.picnic.ui.theme.ToastMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The toast and the Next card never overlap (docs/DESIGN_STICKER.md §4.15, §10; item 175): whichever arrives second sits
 * 8 dp above the first, and the first never moves while it holds. Bounds at w360dp.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class OverlayStackingTest {
    @get:Rule
    val compose = createComposeRule()

    /** The card's face: its box less the 8 dp of room round it. */
    private fun HarnessRig.cardFace(): DpRect = bounds(NEXT_CARD_TAG).let { DpRect(it.left + 8.dp, it.top + 8.dp, it.right - 8.dp, it.bottom - 8.dp) }

    @Test
    fun toastFirstThenCard() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        compose.runOnIdle { rig.toasts.show(ToastMessage("Added 5 items to Saturday shop ♡")) }
        rig.advance(300)
        val toast0 = rig.bounds(TOAST_TAG)
        val t0 = rig.tap("baking-powder")
        val L = t0 + 620
        rig.advanceTo(L + 1300 + 400)
        assertNotNull(rig.p.nextCard)
        assertNotNull(rig.toasts.current)
        val toast = rig.bounds(TOAST_TAG)
        val card = rig.cardFace()
        assertEquals("the toast never moved", toast0, toast)
        assertTrue("card $card sits 8 dp above toast $toast", card.bottom + 8.dp <= toast.top + 0.5.dp)
        assertTrue("and close above it", card.bottom + 8.dp >= toast.top - 0.5.dp)
    }

    @Test
    fun cardFirstThenToast() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        val t0 = rig.tap("baking-powder")
        val L = t0 + 620
        rig.advanceTo(L + 1300 + 400)
        val card0 = rig.cardFace()
        compose.runOnIdle { rig.toasts.show(ToastMessage("Toothpaste torn out", kind = "tear", actionLabel = "Undo", onAction = {})) }
        rig.advance(300)
        val toast = rig.bounds(TOAST_TAG)
        val card = rig.cardFace()
        assertEquals("the card never moved", card0, card)
        assertTrue("toast $toast sits 8 dp above card $card", toast.bottom + 8.dp <= card.top + 0.5.dp)
        assertTrue("and close above it", toast.bottom + 8.dp >= card.top - 0.5.dp)
        // While both show, at every frame they stay apart.
        rig.sampleTo(L + 1700 + 2000) {
            if (rig.p.nextCard != null && rig.toasts.current != null) {
                val c = rig.cardFace()
                val t = rig.bounds(TOAST_TAG)
                assertTrue("overlap at $it: $t vs $c", t.bottom <= c.top || c.bottom <= t.top)
            }
        }
    }
}
