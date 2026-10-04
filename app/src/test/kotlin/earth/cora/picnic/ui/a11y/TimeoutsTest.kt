package earth.cora.picnic.ui.a11y

import android.app.Application
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.platform.AndroidTimeouts
import earth.cora.picnic.platform.Timeouts
import earth.cora.picnic.platform.ladybugMs
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.list.ListFixture
import earth.cora.picnic.ui.list.ListRig
import earth.cora.picnic.ui.list.ListTags
import earth.cora.picnic.ui.list.RecordingProbe
import earth.cora.picnic.ui.list.advanceMs
import earth.cora.picnic.ui.list.boundsOf
import earth.cora.picnic.ui.list.exists
import earth.cora.picnic.ui.list.framesUntil
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.NEXT_CARD_TAG
import earth.cora.picnic.ui.list.show
import earth.cora.picnic.ui.theme.BookTags
import earth.cora.picnic.ui.theme.TOAST_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Checklist 290, on screen (docs/DESIGN_STICKER.md §9, §15; the service part is platform.TimeoutsTest): on a phone with
 * "Time to take action" at 10 s and "Touch & hold delay" at 1.5 s, read through the app's own [AndroidTimeouts] from a
 * shadowed AccessibilityManager, the List screen holds an action toast, the nudge, the Next card and the stamp name tag
 * for 10 s (their own holds are 2.6 to 6 s); the LCD and the basket peek wait 1.5 s for a hold; and egg 2's ladybug
 * sets off 560 ms after that, at 2.06 s.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class TimeoutsTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    /** The phone's own timing service, with "Time to take action" at 10 s and "Touch & hold delay" at 1.5 s. */
    private val phone: Timeouts by lazy {
        shadowOf(app.getSystemService(AccessibilityManager::class.java)).apply {
            setInteractiveUiTimeout(10_000)
            setNonInteractiveUiTimeout(10_000)
        }
        AndroidTimeouts(app) { 1500 }
    }

    /** Shows [rig] on a held clock. */
    private fun shown(rig: ListRig, probe: RecordingProbe? = null): ListRig {
        // Held from the start: a moment that opens with the screen (the nudge) is timed from its first frame.
        compose.mainClock.autoAdvance = false
        compose.show(rig, probe)
        compose.advanceMs(300)
        return rig
    }

    /** [tag] appears within [appearMs], is still up [atLeastMs] after it appeared, and is gone within [goneByMs]. */
    private fun holds(tag: String, what: String, appearMs: Long = 3000, atLeastMs: Long = 9_600, goneByMs: Long = 13_000, unmerged: Boolean = false) {
        assertTrue("$what shows", compose.framesUntil(appearMs) { compose.exists(tag, unmerged) })
        compose.advanceMs(atLeastMs)
        assertTrue("$what is still up ${atLeastMs / 1000.0} s on (it holds the phone's 10 s)", compose.exists(tag, unmerged))
        assertTrue("$what goes after its hold", compose.framesUntil(goneByMs - atLeastMs) { !compose.exists(tag, unmerged) })
    }

    @Test
    fun anActionToastHoldsTenSeconds() {
        val rig = shown(ListRig(timeouts = phone))
        compose.runOnIdle { rig.vm.delete("milk") }
        holds(TOAST_TAG, "the Undo toast")
    }

    @Test
    fun theNudgeHoldsTenSeconds() {
        shown(ListRig(now = ListFixture.NOW + 13 * ListFixture.HOUR, timeouts = phone))
        holds(TOAST_TAG, "the nudge")
    }

    @Test
    fun theNextCardHoldsTenSeconds() {
        shown(ListRig(timeouts = phone))
        // Baking's last item: the aisle is done and the card names the next stop.
        compose.onNodeWithTag(ListTags.row("baking-powder")).performClick()
        holds(NEXT_CARD_TAG, "the Next card", appearMs = 4000, goneByMs = 14_000)
    }

    @Test
    fun theStampNameTagHoldsTenSeconds() {
        shown(ListRig(timeouts = phone))
        val book = compose.boundsOf(BookTags.BOOK)
        val stamp = compose.boundsOf(BookTags.stamp("aisle-2-baking"), unmerged = true)
        compose.onNodeWithTag(BookTags.BOOK).performTouchInput { click(Offset(stamp.center.x - book.left, stamp.center.y - book.top)) }
        holds(BookTags.NAME_TAG, "the stamp's name tag", unmerged = true)
    }

    @Test
    fun theLcdWaitsForTouchAndHold() {
        val rig = shown(ListRig(timeouts = phone))
        val lcd = rig.engine.anchors.bounds(AnchorRegistry.LCD)!!
        compose.onRoot().performTouchInput { down(lcd.center) }
        compose.advanceMs(1350)
        assertEquals("no hold at 1.35 s", 0, rig.surprises.lcdHolds)
        compose.advanceMs(300)
        assertEquals("a hold by 1.65 s", 1, rig.surprises.lcdHolds)
        compose.onRoot().performTouchInput { up() }
        compose.advanceMs(200)
    }

    @Test
    fun thePeekAndTheLadybugWaitForTouchAndHold() {
        val probe = RecordingProbe()
        val rig = shown(ListRig(timeouts = phone), probe)
        val basket = probe["basket:produce"]
        val strip = compose.boundsOf(ListTags.strip("produce"))
        compose.onNodeWithTag(ListTags.strip("produce")).performTouchInput { down(Offset(basket.center.x - strip.left, basket.center.y - strip.top)) }
        compose.advanceMs(1350)
        assertFalse("no peek at 1.35 s", compose.exists(ListTags.PEEK))
        compose.advanceMs(300)
        assertTrue("the peek by 1.65 s", compose.exists(ListTags.PEEK))
        compose.advanceMs(300)
        assertTrue("no ladybug at 1.95 s: ${rig.surprises.basketHolds}", rig.surprises.basketHolds.isEmpty())
        compose.advanceMs(250)
        assertEquals("the ladybug sets off at 2.06 s, still held", listOf("produce" to phone.ladybugMs), rig.surprises.basketHolds)
        compose.onNodeWithTag(ListTags.strip("produce")).performTouchInput { up() }
        compose.advanceMs(200)
    }
}
