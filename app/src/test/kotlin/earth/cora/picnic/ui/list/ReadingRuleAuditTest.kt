package earth.cora.picnic.ui.list

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.BookLayout
import earth.cora.picnic.ui.theme.BookTags
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.TOAST_TAG
import earth.cora.picnic.ui.theme.readHold
import earth.cora.picnic.ui.theme.stampCentre
import earth.cora.picnic.ui.theme.words
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The reading rule for the list's own text moments (docs/DESIGN_STICKER.md §9; the list's half of item 167): the stamp
 * name tag, the automatic new book's note, the New trip note, the tear-off note and the nudge each hold still (constant
 * bounds, where they are really drawn) for at least readHold(their real words, their floor), on the frame clock.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class ReadingRuleAuditTest {
    @get:Rule
    val compose = createComposeRule()

    private val dp get() = compose.density.density

    /** The longest run (ms) over which [bounds] stayed the same, non-null, sampled each frame for [ms]. */
    private fun stillFor(rig: ListRig, ms: Long, bounds: () -> Rect?): Long {
        var best = 0L
        var runStart: Long? = null
        var last: Rect? = null
        compose.sample(rig, ms) { f ->
            val b = bounds()
            if (b != null && b == last) {
                best = maxOf(best, f.clock - runStart!!)
            } else {
                runStart = f.clock
            }
            last = b
        }
        return best
    }

    private fun toastBounds(): Rect? = if (compose.exists(TOAST_TAG)) compose.boundsOf(TOAST_TAG) else null

    /** "4 · Baking · 1 left": max(2600, readHold(4)) = 2600 ms. */
    @Test
    fun stampNameTag() {
        val rig = ListRig()
        val probe = RecordingProbe()
        compose.show(rig, probe)
        val book = compose.boundsOf(BookTags.BOOK)
        val left = probe["bookLeft"]
        val (cx, cy) = stampCentre(3, BookLayout.of(book.width / dp, 8, 24), top = 19f)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(BookTags.BOOK).performTouchInput { click(Offset(left.left - book.left + cx * dp, left.top - book.top + cy * dp)) }
        val text = "4 · Baking · 1 left"
        val still = stillFor(rig, 3600) { probe.bounds["nameTag:$text"] }
        val rule = readHold(words(text), PicnicMotion.floorCard)
        assertTrue("the stamp tag held still $still ms, rule $rule ms", still >= rule)
        compose.mainClock.autoAdvance = true
    }

    /** "New sticker book for today ✦ your last shop is in the album" + Undo: 12 words, 4800 ms. */
    @Test
    fun newBookNote() {
        val rig = ListRig(now = ListFixture.NOW + 13 * ListFixture.HOUR)
        compose.show(rig)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.row("baking-powder")).performClick()
        val still = stillFor(rig, 6500, ::toastBounds)
        val rule = readHold(words("New sticker book for today ✦ your last shop is in the album Undo"), PicnicMotion.floorToast)
        assertTrue("the new book's note held still $still ms, rule $rule ms", still >= rule)
        compose.mainClock.autoAdvance = true
    }

    /** "New trip · a fresh sticker book ♡": the toast floor, 4000 ms. */
    @Test
    fun newTripNote() {
        val rig = ListRig()
        compose.show(rig)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New trip").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Clear what I got").performScrollTo().performClick()
        val still = stillFor(rig, 5500, ::toastBounds)
        val rule = readHold(words("New trip · a fresh sticker book ♡"), PicnicMotion.floorToast)
        assertTrue("the New trip note held still $still ms, rule $rule ms", still >= rule)
        compose.mainClock.autoAdvance = true
    }

    /** "Toothpaste torn out" + Undo: 4000 ms. */
    @Test
    fun tearOffNote() {
        val rig = ListRig()
        compose.show(rig)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { rig.vm.delete("toothpaste") }
        val still = stillFor(rig, 5500, ::toastBounds)
        val rule = readHold(words("Toothpaste torn out Undo"), PicnicMotion.floorToast)
        assertTrue("the tear-off note held still $still ms, rule $rule ms", still >= rule)
        compose.mainClock.autoAdvance = true
    }

    /**
     * The nudge, "New shop? Start a fresh sticker book" + New trip: it carries an action, so it holds at least
     * max(6000 ms, readHold) (§6.1, §9), as it really draws, from the moment it shows.
     */
    @Test
    fun nudgeNote() {
        val rig = ListRig(now = ListFixture.NOW + 13 * ListFixture.HOUR)
        compose.mainClock.autoAdvance = false
        compose.setContent { rig.Themed() }
        val still = stillFor(rig, 7500, ::toastBounds)
        val rule = maxOf(6000L, readHold(words("New shop? Start a fresh sticker book New trip"), PicnicMotion.floorToast))
        assertTrue("the nudge held still $still ms, rule $rule ms", still >= rule)
        compose.mainClock.autoAdvance = true
    }
}
