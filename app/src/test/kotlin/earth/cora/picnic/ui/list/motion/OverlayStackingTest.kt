package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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

    /** The card's face where it rests: its slot less the 8 dp of room round it. */
    private fun HarnessRig.cardFace(): DpRect = bounds(NEXT_CARD_SLOT_TAG).let { DpRect(it.left + 8.dp, it.top + 8.dp, it.right - 8.dp, it.bottom - 8.dp) }

    /** The card's face where it is drawn now (root px), through its motion layer. */
    private fun HarnessRig.cardDrawn(): Rect? =
        compose.onAllNodesWithTag(NEXT_CARD_TAG, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.deflate(8f * compose.density.density)

    @Test
    fun toastFirstThenCard() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        compose.runOnIdle { rig.toasts.show(ToastMessage("Added 5 items to Saturday shop ♡")) }
        val shown = rig.now
        rig.advance(300)
        val toast0 = rig.bounds(TOAST_TAG)
        val t0 = rig.tap("baking-powder")
        val L = t0 + 620
        rig.advanceTo(L + 1300 - 16)
        // From before the card rises until it has gone (the toast lifts off on the way, under the card), on every frame:
        // the card's face and the toast's note, where they are really drawn, never meet (§4.15).
        var both = 0
        var toastAtRise: DpRect? = null
        val frames = rig.sampleTo(L + 1300 + 400 + 2600 + 400) { now ->
            if (now in (L + 1300)..(L + 1700) && rig.toasts.current != null) toastAtRise = rig.bounds(TOAST_TAG)
            val card = rig.cardDrawn() ?: return@sampleTo
            val note = rig.toastNote() ?: return@sampleTo
            both++
            assertTrue("overlap at ${now - L}: card $card, toast $note", !card.overlaps(note))
            assertEquals("the engine and the test clock agree", now, rig.engine.time)
        }
        assertTrue("sampled the card over the toast: $both frames", both > 60)
        val card = frames.mapNotNull { it.second.nextCard }.first()
        val pose = nextCardPose(card, card.t0 + 60, stacked = true)
        assertTrue("the stacked card fades in place: $pose", pose.dyFraction == 0f && pose.dyDp == 0f && pose.alpha in 0.01f..0.99f)
        assertEquals("the toast never moved for the card", toast0, toastAtRise)
        // The toast held still for its whole hold while the card rose over it.
        var why = ""
        val still = rig.probe.stillFrom(TOAST_TAG, shown + 260) { why = it }
        assertTrue("the toast is still $still ms ($why)", still >= 4000 - 60)
        // And the card held still for its whole hold while the toast lifted off under it.
        val cardStill = rig.probe.stillFrom(NEXT_CARD_TAG, card.settledAt) { why = it }
        assertTrue("the card is still $cardStill ms of ${card.holdMs} ($why)", cardStill >= card.holdMs)
    }

    @Test
    fun toastFirstThenCardRestsEightAbove() {
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

    /**
     * A toast under the card leaves while the card holds, and a queued toast then shows: it arrives second, so it sits
     * 8 dp above the card and is drawn whole (only a toast below the card is clipped to it).
     */
    @Test
    fun queuedToastOverAStackedCard() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        compose.runOnIdle {
            rig.toasts.show(ToastMessage("Rain on the way · bring the tote!", kind = "rain"))
            rig.toasts.show(ToastMessage("Added 5 items to Saturday shop ♡", kind = "add"))
        }
        rig.advance(1500)
        val t0 = rig.tap("baking-powder")
        val L = t0 + 620
        rig.advanceTo(L + 1300 + 400)
        val card = rig.p.nextCard!!
        // The first toast's hold ends during the card's; the queued one shows next, over the card.
        rig.advanceTo(card.settledAt + 1500)
        assertEquals("Added 5 items to Saturday shop ♡", rig.toasts.current?.text)
        val face = rig.cardFace()
        val toast = rig.bounds(TOAST_TAG)
        assertTrue("toast $toast sits above the card $face", toast.bottom + 8.dp <= face.top + 0.5.dp)
        val note = rig.toastNote()
        assertNotNull("the toast over the card is drawn", note)
        val d = compose.density.density
        assertEquals("drawn whole, not clipped", (toast.bottom.value - toast.top.value), (note!!.bottom - note.top) / d, 30f)
    }

    /** The toast leaves (lifts 40 dp) while the card held over it holds: the lift never crosses the card's face. */
    @Test
    fun toastLeavesUnderAHeldCard() {
        val rig = HarnessRig(compose, MotionFixture.state()).start()
        compose.runOnIdle { rig.toasts.show(ToastMessage("Rain on the way · bring the tote!", kind = "rain")) }
        rig.advance(300)
        val shown = rig.now - 300
        val t0 = rig.tap("baking-powder")
        val L = t0 + 620
        rig.advanceTo(L + 1300 + 400)
        val card = rig.p.nextCard!!
        // The toast's 4 s hold ends during the card's; the card stays still through the toast's lift-off.
        assertTrue(shown + 220 + 4000 + 260 < card.settledAt + card.holdMs)
        rig.advanceTo(card.settledAt + card.holdMs - 16)
        var why = ""
        val still = rig.probe.stillFrom(NEXT_CARD_TAG, card.settledAt) { why = it }
        assertTrue("the card held still $still ms of ${card.holdMs} ($why)", still >= card.holdMs - 32)
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

    /**
     * §4.15 ("An Undo toast is never cut short"), §11: she tears off the trip's last unchecked item (out of stock): the
     * trip completes and the celebration covers the screen 1.35 s later, over her "… torn out · Undo" toast. The toast's
     * hold waits under the scene; once the scene has gone it is still there, with its Undo, and holds its full hold.
     */
    @Test
    fun undoToastOutlivesTheCelebration() {
        val rig = HarnessRig(compose, MotionFixture.allBut(listOf("toothpaste"), open = "household")).start()
        var change: ListChange? = null
        var undone = false
        compose.runOnIdle {
            change = rig.model.delete("toothpaste")
            rig.toasts.show(ToastMessage("Toothpaste torn out", kind = "tear", actionLabel = "Undo", onAction = { undone = true }))
        }
        rig.frame()
        assertTrue("the tear-off completed the trip", change!!.tripCompleted)
        val toast = rig.toasts.current!!
        val d0 = rig.now
        rig.advanceTo(d0 + Beats.CELEBRATION + 16)
        assertNotNull("the scene covers the toast", rig.p.celebration)
        assertTrue("held under the scene", rig.toasts.paused)
        val limit = rig.now + 30_000
        while (rig.p.celebration != null && rig.now < limit) rig.frame()
        assertTrue("the scene closed by itself", rig.p.celebration == null)
        val gone = rig.now
        assertEquals("her toast is still there", toast.id, rig.toasts.current?.id)
        assertEquals("with its Undo", "Undo", rig.toasts.current?.actionLabel)
        assertTrue(!rig.toasts.paused)
        // Its full hold from the moment the scene has gone, then it lifts off.
        rig.advanceTo(gone + 4000 - 32)
        assertEquals("holds its whole hold", toast.id, rig.toasts.current?.id)
        var why = ""
        val still = rig.probe.stillFrom(earth.cora.picnic.ui.theme.TOAST_TAG, gone + 16) { why = it }
        assertTrue("still $still ms after the scene ($why)", still >= 4000 - 80)
        rig.advanceTo(gone + 4000 + 400)
        assertEquals("then it leaves", null, rig.toasts.current)
        assertTrue("never acted on", !undone)
    }
}
