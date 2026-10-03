package earth.cora.picnic.ui.list.motion

import androidx.compose.ui.test.junit4.v2.createComposeRule
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.ui.theme.CelebrationTags
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
 * The reading rule's audit (docs/DESIGN_STICKER.md §9; item 167), table-driven over WP6's text-bearing moments: each is
 * played on the test clock and measured by [StillnessProbe] from its settle (constant transform and alpha, no glint,
 * curl, shake or card crossing its bounds) against readHold(its real words, its floor).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ReadingRuleAuditTest(private val moment: Moment) {
    @get:Rule
    val compose = createComposeRule()

    /** One §9 row: [copy] is what counts, [floor] its floor, [tags] the plates measured; [play] returns their settle. */
    class Moment(
        val name: String,
        val copy: String,
        val floor: Long,
        val tags: List<String>,
        val state: () -> AppState,
        val reduced: Boolean = false,
        val talkBack: Boolean = false,
        val factor: Long = 1,
        val play: (HarnessRig) -> Long,
    ) {
        override fun toString() = name
    }

    @Test
    fun holdsStill() {
        val rig = HarnessRig(compose, moment.state(), reduced = moment.reduced, talkBack = moment.talkBack).start()
        val settle = moment.play(rig)
        val rule = readHold(words(moment.copy), moment.floor) * moment.factor
        rig.advanceTo(settle + rule + 64)
        for (tag in moment.tags) {
            var why = ""
            val still = rig.probe.stillFrom(tag, settle) { why = it }
            assertTrue("${moment.name} · $tag: still $still ms from its settle, rule $rule ms ($why)", still >= rule)
        }
    }

    companion object {
        private val cele = listOf(CelebrationTags.TITLE, CelebrationTags.CHIP, CelebrationTags.LCD, CelebrationTags.PERFECT, CelebrationTags.BANNER, CelebrationTags.BONUS)

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

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun moments(): List<Array<Any>> = listOf(
            Moment("Next card, 5 Breakfast", "NEXT 5 Breakfast", PicnicMotion.floorCard, listOf(NEXT_CARD_TAG), { MotionFixture.state() }) { nextCard(it) },
            Moment("Next card, 7 Pasta, rice & international A3", "NEXT 7 Pasta, rice & international A3", PicnicMotion.floorCard, listOf(NEXT_CARD_TAG), { MotionFixture.pasta() }) { nextCard(it) },
            Moment("Next card with TalkBack (doubled)", "NEXT 5 Breakfast", PicnicMotion.floorCard, listOf(NEXT_CARD_TAG), { MotionFixture.state() }, talkBack = true, factor = 2) { nextCard(it) },
            Moment("Celebration title beat", "List complete! ♡ Saturday shop 24/24★ tap to skip", PicnicMotion.floorBeat, listOf(CelebrationTags.TITLE, CelebrationTags.CHIP, CelebrationTags.LCD), lastOne) { scene(it, 700) },
            Moment("Celebration PER-FECT!", "PER-FECT!", PicnicMotion.floorBeat, listOf(CelebrationTags.PERFECT), lastOne) { scene(it, 4730) },
            Moment("Celebration banner", "24 stickers · 26 min", PicnicMotion.floorBeat, listOf(CelebrationTags.BANNER), lastOne) { scene(it, 6530) },
            Moment("Celebration bonus", "bonus sticker!", PicnicMotion.floorBeat, listOf(CelebrationTags.BONUS), lastOne) { scene(it, 9430) },
            Moment("Celebration summary", "List complete! ♡ Saturday shop 24/24★ PER-FECT! 24 stickers · 26 min bonus sticker!", 4000, cele, lastOne) { scene(it, 12150) },
            Moment("Celebration summary, reduced motion", "List complete! ♡ Saturday shop 24/24★ PER-FECT! 24 stickers · 26 min bonus sticker!", 6000, cele, lastOne, reduced = true) { scene(it, 120) },
        ).map { arrayOf<Any>(it) }
    }
}
