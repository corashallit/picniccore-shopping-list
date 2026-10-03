package earth.cora.picnic.ui.list

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.list.motion.CELEBRATION_TAG
import earth.cora.picnic.ui.list.motion.MotionEvent
import earth.cora.picnic.ui.theme.BookRules
import earth.cora.picnic.ui.theme.CelebrationTags
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The finale on the real list (docs/DESIGN_STICKER.md §7.6, §11, §0.4 Q7; the list's halves of items 153 and 314):
 * CelebrationHost and the overlay are mounted on the List screen, the summary closes itself behind its washi-tape bar,
 * and 450 ms after it closes WP5's book turns its stamp page holo and presses PER-FECT! across the spine.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class CelebrationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bookPerfectAfterClose() {
        val rig = ListRig(ListFixture.allBut(listOf("toothpaste"), open = "household"))
        compose.show(rig)
        // Household, the last stop, is below the fold: bring its last row on screen.
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("toothpaste")
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.row("toothpaste")).performClick()
        // The scene starts at L + 1350 over the real list.
        assertTrue("the celebration shows", compose.framesUntil(2600) { compose.exists(CELEBRATION_TAG) })
        val book = rig.vm.state.value.book!!
        assertTrue("held: no PERFECT on the book while the scene plays", !BookRules.perfect(book, rig.engine.current.value.bookFx))
        // To the summary, with its closing bar and label (§0.4 Q7).
        assertTrue("the summary's closing label", compose.framesUntil(16_000) { compose.exists(CelebrationTags.CLOSING_LABEL, unmerged = true) })
        // A tap outside the summary card closes it at once.
        val card = compose.boundsOf(CelebrationTags.SUMMARY_CARD, unmerged = true)
        val tapped = rig.engine.time
        compose.onNodeWithTag(CELEBRATION_TAG).performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, (card.top / 2f).coerceAtLeast(10f))) }
        val frames = compose.sample(rig, 1200)
        assertTrue("closed", rig.engine.eventLog.any { it is MotionEvent.CelebrationClosed })
        val pressed = frames.firstTime { it.bookFx.perfectT0 != null }
        assertNotNull("PER-FECT! is pressed on the book", pressed)
        assertTrue("450 ms after the tap that closed it (${pressed!! - tapped})", pressed - tapped in 430L..520L)
        assertTrue(rig.engine.eventLog.contains(MotionEvent.BookPerfect))
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        // The finale is persisted and the book stays PERFECT: holo stamp page, the spine hanko.
        assertNotNull(rig.app.list(ListFixture.LIST_ID)!!.trip!!.finaleShownAt)
        assertTrue(rig.vm.state.value.book!!.perfect)
        assertTrue(BookRules.perfect(rig.vm.state.value.book!!, rig.engine.current.value.bookFx))
    }
}
