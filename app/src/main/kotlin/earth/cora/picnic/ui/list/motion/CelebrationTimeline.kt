package earth.cora.picnic.ui.list.motion

import androidx.compose.runtime.Immutable
import earth.cora.picnic.core.model.TripStats
import earth.cora.picnic.core.model.TripStatsValue
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.readHold
import earth.cora.picnic.ui.theme.words

/*
 * The celebration's storyboard, computed from the real words (docs/DESIGN_STICKER.md §9, §11.2, D25). One text beat at
 * a time: each starts at the previous text's SETTLE + readHold(the words newly on screen with it), counted from the
 * real list name and stats, and no glint, curl or shake falls inside any hold. For the fixture ("Saturday shop",
 * 24 stickers, 41 min): title settled 0.70, PERFECT 4.30 (still from 4.73), banner 6.23, bonus 8.93, curl 11.23,
 * summary 12.15–16.95 s. Pure; Owner: WP6 (CelebrationTimelineTest).
 */

/** The storyboard's moments (§11.2). */
enum class BeatName {
    Flash, Iris, BurstStar, Title, SkipChip, Hamper, Kira, RibbonDraw, Ride, Spread, StampCard, Flip, CardGlint,
    Perfect, Shake, TitleGlint, Banner, Confetti, PetDance, CapsuleWobble, Bonus, CapsulePop, Curl, SkipChipOut,
    PocketKira, PocketMagic, Summary, Close,
}

/**
 * One beat: it starts at [at] and its movement ends at [endsAt] (ms from the scene's start). A text beat also has its
 * [settle] (the moment its text is still), its [hold] (fully still time from the settle, §9), the [words] that count
 * and the [text] they were counted from.
 */
@Immutable
data class Beat(
    val name: BeatName,
    val at: Long,
    val endsAt: Long = at,
    val settle: Long? = null,
    val hold: Long = 0,
    val words: Int = 0,
    val text: String? = null,
)

/** The copy the scene shows (strings_motion.xml); the timeline counts the words of exactly these. */
@Immutable
data class CelebrationCopy(
    val title: String = "List complete!",
    val skip: String = "tap to skip",
    val perfect: String = "PER-FECT!",
    val bonus: String = "bonus sticker!",
    /** "24 stickers · 41 min" / "1 sticker · 1 h 5 min". */
    val banner: (stickers: Int, minutes: String) -> String = { n, m -> "$n ${if (n == 1) "sticker" else "stickers"} · $m" },
    /** The washi progress bar's label (§0.4 Q7). */
    val closing: String = "Closing… tap to keep it",
    val done: String = "Done",
) {
    companion object {
        val English: CelebrationCopy = CelebrationCopy()
    }
}

/** The storyboard of one celebration, every time in ms from its start (§11.2). */
@Immutable
data class CelebrationPlan(
    val beats: List<Beat>,
    /** The chip "♡ <list name>", the LCD "24/24★" and the banner text as shown. */
    val listChip: String,
    val lcd: String,
    val banner: String,
    /** The summary's still hold: max(4000, readHold(all its words)); max(6000, …) under reduced motion (§11.4). */
    val summaryHold: Long,
    val summaryWords: Int,
    val reduced: Boolean,
) {
    fun beat(name: BeatName): Beat = beats.first { it.name == name }

    fun beatOrNull(name: BeatName): Beat? = beats.firstOrNull { it.name == name }

    /** When the summary is still from. */
    val summaryAt: Long get() = beat(BeatName.Summary).at

    /** When the scene closes by itself (the end of the summary hold; never with TalkBack, §11.3). */
    val closeAt: Long get() = beat(BeatName.Close).at

    /** The text beats in order, with their settle and hold. */
    val textBeats: List<Beat> get() = beats.filter { it.settle != null }
}

object CelebrationTimeline {
    /** The title drops in 600 ms (cubic-bezier(.3, 1.4, .5, 1)) from 0.10 s: settled S1 = 0.70 s. */
    const val TITLE_AT: Long = 100
    const val TITLE_IN: Long = 600
    const val PERFECT_SLAM: Long = 260
    const val SHAKE: Long = 170
    const val BANNER_IN: Long = 300
    const val BONUS_IN: Long = 500
    const val CURL: Long = 400
    const val POCKET_KIRA_AFTER_CURL: Long = 120
    const val POCKET_MAGIC_AFTER_CURL: Long = 200
    const val POCKET_MAGIC: Long = 320

    /** Riders: at most 24, 45 ms apart from 0.85 s, 550 ms each: the last lands by 2.45 s. */
    const val MAX_RIDERS: Int = 24
    const val RIDE_AT: Long = 850
    const val RIDE_GAP: Long = 45
    const val RIDE_EACH: Long = 550

    /** The summary's floor (D25) and its reduced-motion floor (§11.4). */
    const val SUMMARY_FLOOR: Long = 4000
    const val SUMMARY_FLOOR_RM: Long = 6000

    /** The §19.2 contract: the fixture's beats for [listName], [stats] and [petHatched]. */
    fun of(listName: String, stats: TripStatsValue, petHatched: Boolean): List<Beat> =
        plan(listName, stats, petHatched).beats

    /**
     * The whole storyboard. [total] is the LCD's denominator (every item, so equal to the stickers at completion).
     * Reduced motion: only the summary frame, faded in over 120 ms at 0 and held max(6000, readHold(all its words)).
     * [closingBar]: the summary shows the washi progress bar and its label "Closing… tap to keep it" (§0.4 Q7); its
     * words arrive with the summary, so they count (§9). Off under TalkBack, where there is no countdown.
     */
    fun plan(
        listName: String,
        stats: TripStatsValue,
        petHatched: Boolean,
        reduced: Boolean = false,
        copy: CelebrationCopy = CelebrationCopy.English,
        total: Int = stats.stickers,
        closingBar: Boolean = true,
    ): CelebrationPlan {
        val listChip = "♡ $listName"
        val lcd = "${stats.stickers}/$total★"
        val banner = copy.banner(stats.stickers, TripStats.formatMinutes(stats.minutes))
        // Everything on screen at the summary: the title, the chip, the LCD, PERFECT, the banner, the bonus and the
        // closing bar's label (not "tap to skip", which left at the curl).
        val summaryText = (listOf(copy.title, listChip, lcd, copy.perfect, banner, copy.bonus) + listOfNotNull(copy.closing.takeIf { closingBar })).joinToString(" ")
        val summaryWords = words(summaryText)
        if (reduced) {
            val hold = readHold(summaryWords, SUMMARY_FLOOR_RM)
            val fade = PicnicMotion.rmFade.durationMillis.toLong()
            val beats = listOf(
                Beat(BeatName.Summary, 0, fade, settle = fade, hold = hold, words = summaryWords, text = summaryText),
                Beat(BeatName.Close, fade + hold, fade + hold + fade),
            )
            return CelebrationPlan(beats, listChip, lcd, banner, hold, summaryWords, reduced = true)
        }

        val beats = ArrayList<Beat>()
        beats += Beat(BeatName.Flash, 0, 170)
        beats += Beat(BeatName.Iris, 40, 460)
        beats += Beat(BeatName.BurstStar, 100, 600)

        // Text beat 1: the title, the list chip, the LCD and "tap to skip" (all newly on screen with it).
        val titleText = listOf(copy.title, listChip, lcd, copy.skip).joinToString(" ")
        val titleWords = words(titleText)
        val s1 = TITLE_AT + TITLE_IN
        val titleHold = readHold(titleWords, PicnicMotion.floorBeat)
        beats += Beat(BeatName.Title, TITLE_AT, s1, settle = s1, hold = titleHold, words = titleWords, text = titleText)
        beats += Beat(BeatName.SkipChip, TITLE_AT, 300)
        beats += Beat(BeatName.Hamper, 150, 550)
        beats += Beat(BeatName.Kira, 250, 400 + 250)
        beats += Beat(BeatName.RibbonDraw, 350, 350 + 1200)
        beats += Beat(BeatName.Ride, RIDE_AT, RIDE_AT + (MAX_RIDERS - 1) * RIDE_GAP + RIDE_EACH)
        beats += Beat(BeatName.Spread, 1950, 1950 + 4 * 50 + 400)
        beats += Beat(BeatName.StampCard, 2300, 2700)
        beats += Beat(BeatName.Flip, 2700, 3200)
        // The card glint runs on the card back only; it ends before PERFECT settles.
        beats += Beat(BeatName.CardGlint, 3200, 3200L + PicnicMotion.glint.durationMillis)

        // Text beat 2: PER-FECT!, from the title's settle + its hold; still after the ink ring and the shake.
        val perfectAt = s1 + titleHold
        val perfectWords = words(copy.perfect)
        val shakeAt = perfectAt + PERFECT_SLAM
        val s2 = shakeAt + SHAKE
        val perfectHold = readHold(perfectWords, PicnicMotion.floorBeat)
        beats += Beat(BeatName.Perfect, perfectAt, s2, settle = s2, hold = perfectHold, words = perfectWords, text = copy.perfect)
        beats += Beat(BeatName.Shake, shakeAt, s2)
        // The title glint sweeps the title only, after its hold.
        beats += Beat(BeatName.TitleGlint, perfectAt, perfectAt + PicnicMotion.glint.durationMillis.toLong())

        // Text beat 3: the ribbon banner with the trip's real stats.
        val bannerAt = s2 + perfectHold
        val bannerWords = words(banner)
        val s3 = bannerAt + BANNER_IN
        val bannerHold = readHold(bannerWords, PicnicMotion.floorBeat)
        beats += Beat(BeatName.Banner, bannerAt, s3, settle = s3, hold = bannerHold, words = bannerWords, text = banner)
        beats += Beat(BeatName.Confetti, bannerAt, bannerAt + 600 + 11 * 35 + 1800)
        if (petHatched) beats += Beat(BeatName.PetDance, bannerAt, bannerAt + 4 * 500)

        // Text beat 4: the bonus card.
        val bonusAt = s3 + bannerHold
        val bonusWords = words(copy.bonus)
        val s4 = bonusAt + BONUS_IN
        val bonusHold = readHold(bonusWords, PicnicMotion.floorBeat)
        beats += Beat(BeatName.CapsuleWobble, bonusAt - 600, bonusAt)
        beats += Beat(BeatName.Bonus, bonusAt, s4, settle = s4, hold = bonusHold, words = bonusWords, text = copy.bonus)
        beats += Beat(BeatName.CapsulePop, bonusAt + 100, bonusAt + 400)

        // The page curl (beside the bonus card, never over text) and the bonus into its album pocket.
        val curlAt = s4 + bonusHold
        val curlEnd = curlAt + CURL
        beats += Beat(BeatName.Curl, curlAt, curlEnd)
        beats += Beat(BeatName.SkipChipOut, curlAt, curlAt + PicnicMotion.rmFade.durationMillis.toLong())
        beats += Beat(BeatName.PocketKira, curlEnd + POCKET_KIRA_AFTER_CURL, curlEnd + POCKET_KIRA_AFTER_CURL + 400)
        val magicAt = curlEnd + POCKET_MAGIC_AFTER_CURL
        val summaryAt = magicAt + POCKET_MAGIC
        beats += Beat(BeatName.PocketMagic, magicAt, summaryAt)

        // The summary: everything holds still, then the scene closes (a tap closes it earlier).
        val summaryHold = readHold(summaryWords, SUMMARY_FLOOR)
        beats += Beat(BeatName.Summary, summaryAt, summaryAt, settle = summaryAt, hold = summaryHold, words = summaryWords, text = summaryText)
        beats += Beat(BeatName.Close, summaryAt + summaryHold, summaryAt + summaryHold + 200)
        return CelebrationPlan(beats.sortedBy { it.at }, listChip, lcd, banner, summaryHold, summaryWords, reduced = false)
    }
}
