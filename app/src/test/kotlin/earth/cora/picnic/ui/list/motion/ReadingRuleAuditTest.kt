package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.test.junit4.v2.createComposeRule
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.platform.FakeTimeouts
import earth.cora.picnic.ui.theme.CelebrationTags
import earth.cora.picnic.ui.theme.TOAST_TAG
import earth.cora.picnic.ui.theme.ToastMessage
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.readHold
import earth.cora.picnic.ui.theme.words
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The reading rule's audit (docs/DESIGN_STICKER.md §9; item 167), table-driven over WP6's text-bearing moments (the
 * Next card, the celebration's beats and summary, and the toasts the overlay hosts and stacks with the card): each is
 * played on the test clock and measured by [StillnessProbe] from its settle (constant transform and alpha, where it is
 * really drawn too, and no glint, curl, shake, card or toast crossing its bounds) against readHold(its real words, its
 * floor), and against "Time to take action" when it carries an action.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ReadingRuleAuditTest(private val moment: Moment) {
    @get:Rule
    val compose = createComposeRule()

    /**
     * One §9 row: [copy] is what counts, [floor] its floor, [tags] the plates measured; [play] returns their settle.
     * [actionTimeout]: Android's "Time to take action" for the row (0 = its default, which leaves holds unchanged); a
     * moment with an action then holds at least that long.
     */
    class Moment(
        val name: String,
        val copy: String,
        val floor: Long,
        val tags: List<String>,
        val state: () -> AppState,
        val reduced: Boolean = false,
        val talkBack: Boolean = false,
        val factor: Long = 1,
        val actionTimeout: Long = 0,
        val play: (HarnessRig) -> Long,
    ) {
        override fun toString() = name
    }

    @Test
    fun holdsStill() {
        val rig = HarnessRig(compose, moment.state(), reduced = moment.reduced, talkBack = moment.talkBack, timeouts = FakeTimeouts(actionTimeoutMs = moment.actionTimeout)).start()
        val settle = moment.play(rig)
        val rule = maxOf(readHold(words(moment.copy), moment.floor) * moment.factor, moment.actionTimeout)
        rig.advanceTo(settle + rule + 64)
        for (tag in moment.tags) {
            var why = ""
            val still = rig.probe.stillFrom(tag, settle) { why = it }
            assertTrue("${moment.name} · $tag: still $still ms from its settle, rule $rule ms ($why)", still >= rule)
        }
    }

    companion object {
        private val cele = listOf(CelebrationTags.TITLE, CelebrationTags.CHIP, CelebrationTags.LCD, CelebrationTags.PERFECT, CelebrationTags.BANNER, CelebrationTags.BONUS)
        private val summary = cele + CelebrationTags.CLOSING_LABEL

        /** Runs the fixture's Baking powder to the Next card's settle. */
        private fun nextCard(rig: HarnessRig): Long {
            val t0 = rig.tap("baking-powder")
            rig.advanceTo(t0 + 620 + 1300 + 420)
            return rig.p.nextCard!!.settledAt
        }

        /** Runs the trip's last tap to the scene's start, then to [at] ms into it: returns the scene's t0 + [at]. */
        private fun scene(rig: HarnessRig, at: Long): Long {
            val t0 = rig.tap("toothpaste")
            val s = t0 + 620 + Beats.CELEBRATION
            rig.advanceTo(s + 16)
            return rig.p.celebration!!.t0 + at
        }

        private val lastOne = { MotionFixture.allBut(listOf("toothpaste"), open = "household") }

        private val add = ToastMessage("Added 5 items to Saturday shop ♡", kind = "add", actionLabel = "Undo", onAction = {})
        private val tear = ToastMessage("Oat cereal torn out", kind = "tear", actionLabel = "Undo", onAction = {})
        private val rain = ToastMessage("Rain on the way · bring the tote!", kind = "rain")
        private val nudge = ToastMessage("New shop? Start a fresh sticker book", kind = "nudge", actionLabel = "New trip", onAction = {})

        /**
         * Shows [message] and returns the moment it is still: the first frame from which the note, where it is really
         * drawn, stays where it rests (after its 220 ms drop).
         */
        private fun toast(rig: HarnessRig, message: ToastMessage): Long {
            rig.compose.runOnIdle { rig.toasts.show(message) }
            val shown = rig.now
            rig.advanceTo(shown + 1000)
            val frames = rig.probe.realFrames(TOAST_TAG).filter { it.time >= shown }
            val rest = frames.last().bounds
            return frames.first { f -> f.bounds == rest && frames.filter { it.time >= f.time }.all { it.bounds == rest } }.time
        }

        /** A toast, then Baking powder 1.5 s later: the Next card rises over the held toast (stacked, §4.15). */
        private fun toastThenCard(rig: HarnessRig, message: ToastMessage): Pair<Long, Long> {
            val settle = toast(rig, message)
            rig.advanceTo(settle + 500)
            return settle to nextCard(rig)
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun moments(): List<Array<Any>> = listOf(
            Moment("Next card, 5 Breakfast", "NEXT 5 Breakfast", PicnicMotion.floorCard, listOf(NEXT_CARD_TAG), { MotionFixture.state() }) { nextCard(it) },
            Moment("Next card, 7 Pasta, rice & international A3", "NEXT 7 Pasta, rice & international A3", PicnicMotion.floorCard, listOf(NEXT_CARD_TAG), { MotionFixture.pasta() }) { nextCard(it) },
            Moment("Next card with TalkBack (doubled)", "NEXT 5 Breakfast", PicnicMotion.floorCard, listOf(NEXT_CARD_TAG), { MotionFixture.state() }, talkBack = true, factor = 2) { nextCard(it) },
            Moment("Celebration title beat", "List complete! ♡ Saturday shop 24/24★ tap to skip", PicnicMotion.floorBeat, listOf(CelebrationTags.TITLE, CelebrationTags.CHIP, CelebrationTags.LCD), lastOne) { scene(it, 700) },
            Moment("Celebration PER-FECT!", "PER-FECT!", PicnicMotion.floorBeat, listOf(CelebrationTags.PERFECT), lastOne) { scene(it, 4730) },
            Moment("Celebration banner", "24 stickers · 41 min", PicnicMotion.floorBeat, listOf(CelebrationTags.BANNER), lastOne) { scene(it, 6530) },
            Moment("Celebration bonus", "bonus sticker!", PicnicMotion.floorBeat, listOf(CelebrationTags.BONUS), lastOne) { scene(it, 9430) },
            Moment("Celebration summary", "List complete! ♡ Saturday shop 24/24★ PER-FECT! 24 stickers · 41 min bonus sticker! Closing… tap to keep it", 4000, summary, lastOne) { scene(it, 12150) },
            Moment("Celebration summary, reduced motion", "List complete! ♡ Saturday shop 24/24★ PER-FECT! 24 stickers · 41 min bonus sticker! Closing… tap to keep it", 6000, summary, lastOne, reduced = true) { scene(it, 120) },
            Moment("Celebration summary, Time to take action 20 s", "List complete! ♡ Saturday shop 24/24★ PER-FECT! 24 stickers · 41 min bonus sticker! Closing… tap to keep it", 4000, summary, lastOne, actionTimeout = 20_000) { scene(it, 12150) },
            // The toasts the overlay hosts (§4.15), alone and stacked with the Next card.
            Moment("Sticky-note toast", "Added 5 items to Saturday shop ♡ Undo", PicnicMotion.floorToast, listOf(TOAST_TAG), { MotionFixture.state() }) { toast(it, add) },
            Moment("Tear-off undo toast", "Oat cereal torn out Undo", PicnicMotion.floorToast, listOf(TOAST_TAG), { MotionFixture.state() }) { toast(it, tear) },
            Moment("Rainy-day toast", "Rain on the way · bring the tote!", PicnicMotion.floorToast, listOf(TOAST_TAG), { MotionFixture.state() }) { toast(it, rain) },
            Moment("Nudge toast, Time to take action 10 s", "New shop? Start a fresh sticker book New trip", PicnicMotion.floorToast, listOf(TOAST_TAG), { MotionFixture.state() }, actionTimeout = 10_000) { toast(it, nudge) },
            Moment("Sticky-note toast under a Next card", "Added 5 items to Saturday shop ♡ Undo", PicnicMotion.floorToast, listOf(TOAST_TAG), { MotionFixture.state() }) { toastThenCard(it, add).first },
            Moment("Next card over a sticky-note toast", "NEXT 5 Breakfast", PicnicMotion.floorCard, listOf(NEXT_CARD_TAG), { MotionFixture.state() }) { toastThenCard(it, add).second },
            Moment("Next card over a rainy-day toast that leaves", "NEXT 5 Breakfast", PicnicMotion.floorCard, listOf(NEXT_CARD_TAG), { MotionFixture.state() }) { toastThenCard(it, rain).second },
            Moment("Tear-off toast over a held Next card", "Oat cereal torn out Undo", PicnicMotion.floorToast, listOf(TOAST_TAG), { MotionFixture.state() }) {
                nextCard(it)
                toast(it, tear)
            },
        ).map { arrayOf<Any>(it) }
    }
}
