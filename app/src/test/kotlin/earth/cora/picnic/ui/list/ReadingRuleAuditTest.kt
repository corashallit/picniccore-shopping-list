package earth.cora.picnic.ui.list

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
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
import earth.cora.picnic.ui.list.motion.LocalMotionProbe
import earth.cora.picnic.ui.list.motion.NEXT_CARD_TAG
import earth.cora.picnic.ui.list.motion.StillnessProbe
import earth.cora.picnic.ui.list.motion.TOAST_SLOT_TAG
import earth.cora.picnic.ui.theme.LocalPieceProbe
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
 * name tag, the automatic new book's note, the New trip note, the tear-off note and the nudge each hold still for at
 * least readHold(their real words, their floor), on the frame clock, measured by WP6's [StillnessProbe] from their
 * settle on the real list: where each is really drawn (its node's bounds through every motion layer) stays put, and no
 * overlay the list reports (the Next card, sampled where it is really drawn, and every glint, curl or shake the motion
 * pieces report themselves) crosses it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class ReadingRuleAuditTest {
    @get:Rule
    val compose = createComposeRule()

    private val dp get() = compose.density.density

    /** The real list under a [StillnessProbe], sampled frame by frame on the held clock. */
    private inner class Still(val rig: ListRig) {
        val probe = StillnessProbe()
        val pieces = RecordingProbe()

        /**
         * Shows the list. [held]: the clock is held from the very first frame (a moment that starts as the list opens,
         * the nudge); otherwise the list settles first, as she would see it before she touches it.
         */
        fun show(held: Boolean = false) {
            if (held) compose.mainClock.autoAdvance = false
            compose.setContent {
                CompositionLocalProvider(LocalMotionProbe provides probe, LocalPieceProbe provides pieces) { rig.Themed() }
            }
            if (!held) compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            frames(1)
        }

        /** Runs [ms] of frames, sampling each. */
        fun run(ms: Long) = frames(((ms + 15) / 16).toInt())

        private fun frames(n: Int) = repeat(n) {
            compose.frames(1)
            sample()
        }

        /**
         * Where the toast's note, the Next card's face and the stamp name tag are really drawn now (root px, through every
         * layer), reported as the texts' real geometry and as overlays to one another.
         */
        private fun sample() {
            val t = compose.mainClock.currentTime
            compose.onAllNodesWithTag(NEXT_CARD_TAG, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { n ->
                val face = n.boundsInRoot.deflate(8f * dp)
                probe.real(NEXT_CARD_TAG, t, face)
                probe.overlay(NEXT_CARD_TAG, t, face)
            }
            toastNote()?.let { note ->
                probe.text(TOAST_TAG, t, note)
                probe.real(TOAST_TAG, t, note)
                probe.overlay(TOAST_TAG, t, note)
            }
            pieces.bounds.entries.firstOrNull { it.key.startsWith("nameTag:") }?.value?.let { tag ->
                probe.text(NAME_TAG, t, tag)
                probe.real(NAME_TAG, t, tag)
                probe.overlay(NAME_TAG, t, tag)
            }
        }

        /** The toast's note as drawn: its live-region body (inside the note's motion layer) grown by the note's frame. */
        private fun toastNote(): Rect? {
            val body = compose.onAllNodes(
                hasAnyAncestor(hasTestTag(TOAST_TAG)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().firstOrNull()?.boundsInRoot ?: return null
            if (body.width <= 0f || body.height <= 0f) return null
            val note = Rect(body.left - 17f * dp, body.top - 29f * dp, body.right + 13f * dp, body.bottom + 11f * dp)
            val slot = compose.onAllNodesWithTag(TOAST_SLOT_TAG, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot ?: return note
            return if (note.overlaps(slot)) note.intersect(slot) else null
        }

        /** The first frame from which [tag] rests where it rests [window] ms after it first showed (its settle). */
        fun settle(tag: String, window: Long = 1000): Long {
            val frames = probe.realFrames(tag)
            assertTrue("$tag never showed", frames.isNotEmpty())
            val first = frames.first().time
            val early = frames.filter { it.time <= first + window }
            val rest = early.last().bounds
            return early.first { f -> early.filter { it.time >= f.time }.all { it.bounds == rest } }.time
        }

        /** [tag] stayed still at least [rule] ms from its settle, as [StillnessProbe] measures it. */
        fun assertHolds(tag: String, rule: Long, what: String) {
            val from = settle(tag)
            var why = ""
            val still = probe.stillFrom(tag, from) { why = it }
            assertTrue("$what held still $still ms from its settle, rule $rule ms ($why)", still >= rule)
        }
    }

    /** "4 · Baking · 1 left": max(2600, readHold(4)) = 2600 ms. */
    @Test
    fun stampNameTag() {
        val still = Still(ListRig())
        still.show()
        val book = compose.boundsOf(BookTags.BOOK)
        val left = still.pieces["bookLeft"]
        val (cx, cy) = stampCentre(3, BookLayout.of(book.width / dp, 8, 24), top = 19f)
        compose.onNodeWithTag(BookTags.BOOK).performTouchInput { click(Offset(left.left - book.left + cx * dp, left.top - book.top + cy * dp)) }
        val rule = readHold(words("4 · Baking · 1 left"), PicnicMotion.floorCard)
        still.run(rule + 1600)
        still.assertHolds(NAME_TAG, rule, "the stamp tag")
        compose.mainClock.autoAdvance = true
    }

    /**
     * "New sticker book for today ✦ your last shop is in the album" + Undo: 12 words, 4800 ms, while the check-off's
     * chain plays (the hop, the twin in the fresh book): nothing moves the note or crosses it.
     */
    @Test
    fun newBookNote() {
        val still = Still(ListRig(now = ListFixture.NOW + 13 * ListFixture.HOUR))
        still.show()
        compose.onNodeWithTag(ListTags.row("baking-powder")).performClick()
        val rule = readHold(words("New sticker book for today ✦ your last shop is in the album Undo"), PicnicMotion.floorToast)
        still.run(600)
        compose.onNodeWithText("New sticker book for today", substring = true, useUnmergedTree = true).assertExists()
        still.run(rule + 1000)
        still.assertHolds(TOAST_TAG, rule, "the new book's note")
        compose.mainClock.autoAdvance = true
    }

    /** "New trip · a fresh sticker book ♡": the toast floor, 4000 ms. */
    @Test
    fun newTripNote() {
        val still = Still(ListRig())
        still.show()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("More options").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New trip").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Clear what I got").performScrollTo().performClick()
        val rule = readHold(words("New trip · a fresh sticker book ♡"), PicnicMotion.floorToast)
        still.run(rule + 1600)
        still.assertHolds(TOAST_TAG, rule, "the New trip note")
        compose.mainClock.autoAdvance = true
    }

    /** "Toothpaste torn out" + Undo: 4000 ms. */
    @Test
    fun tearOffNote() {
        val still = Still(ListRig())
        still.show()
        compose.runOnIdle { still.rig.vm.delete("toothpaste") }
        val rule = readHold(words("Toothpaste torn out Undo"), PicnicMotion.floorToast)
        still.run(rule + 1600)
        still.assertHolds(TOAST_TAG, rule, "the tear-off note")
        compose.mainClock.autoAdvance = true
    }

    /**
     * The nudge, "New shop? Start a fresh sticker book" + New trip: it carries an action, so it holds at least
     * max(6000 ms, readHold) (§6.1, §9), as it really draws, from its settle.
     */
    @Test
    fun nudgeNote() {
        val still = Still(ListRig(now = ListFixture.NOW + 13 * ListFixture.HOUR))
        still.show(held = true)
        val rule = maxOf(6000L, readHold(words("New shop? Start a fresh sticker book New trip"), PicnicMotion.floorToast))
        still.run(rule + 1600)
        still.assertHolds(TOAST_TAG, rule, "the nudge")
        compose.mainClock.autoAdvance = true
    }

    private companion object {
        /** The stamp name tag's key in the probe. */
        const val NAME_TAG = "list_stamp_name_tag"
    }
}
