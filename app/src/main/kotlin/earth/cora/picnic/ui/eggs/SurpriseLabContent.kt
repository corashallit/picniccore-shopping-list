package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import earth.cora.picnic.R
import earth.cora.picnic.platform.Announcer
import earth.cora.picnic.platform.DefaultTimeouts
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.platform.NoOpAnnouncer
import earth.cora.picnic.platform.NoOpFeedback
import earth.cora.picnic.platform.Timeouts
import earth.cora.picnic.ui.about.LabEgg
import earth.cora.picnic.ui.about.ShellSurpriseLab
import earth.cora.picnic.ui.about.SurpriseLab
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.WashiPattern
import earth.cora.picnic.ui.theme.WashiTape
import earth.cora.picnic.ui.theme.hardShadow
import earth.cora.picnic.ui.theme.holdOnFrameClock
import kotlinx.coroutines.withContext

/*
 * The Surprise lab's content (docs/DESIGN_STICKER.md §6.9, §12.3) inside WP8's shell (SurpriseLabScreen): the 18 egg
 * tiles with their "Try it" lines, a Try it that really plays each egg on the sandbox, and the practice list it plays
 * on, scrolled into view under its "Surprise lab · practice list" ribbon. It works with Easter eggs off (the sandbox
 * forces them on, on itself only). The sandbox lives in a ViewModel, so a rotation keeps it; leaving the lab discards
 * it. Owner: WP9 (SurpriseLabTest).
 */

/**
 * The lab's state holder. Every Try it plays on a FRESH practice copy (§12.3 "on the sandbox"): the fixture at 10:24, an
 * empty practice album, no forecast, an engine that has fired nothing yet, its clock running from there. So the eggs
 * play as often as she likes and in any order: a second petal shake is never held back by the first one's cooldown, a
 * second ant parade by "once per process", a second midnight by the SNACK? already shown, and "LCD pet" after "LCD
 * receipt" finds a trip that is not complete. "Start the practice list over" gives a fresh copy with nothing playing.
 * Leaving the lab discards the copy.
 */
class SurpriseLabViewModel(
    private val sandboxFactory: () -> SurpriseLabSandbox,
) : ViewModel(), SurpriseLab {
    private var sandboxHolder by mutableStateOf<SurpriseLabSandbox?>(null)
    private val tries = IntArray(EGGS + 1)

    /** The sandbox while the lab is open and an egg has been tried (tests). */
    val sandbox: SurpriseLabSandbox? get() = sandboxHolder

    @Composable
    override fun eggs(): List<LabEgg> = ShellSurpriseLab.eggs()

    override val canFire: Boolean = true

    override fun fire(number: Int) {
        if (number !in 1..EGGS) return
        fresh().fire(number, attempt = tries[number]++)
    }

    /** "Start the practice list over": a fresh practice copy, nothing playing. */
    fun startOver() {
        fresh()
    }

    private fun fresh(): SurpriseLabSandbox {
        sandboxHolder?.close()
        return sandboxFactory().also { sandboxHolder = it }
    }

    @Composable
    override fun Practice(modifier: Modifier) {
        val box = sandboxHolder ?: return
        // A fresh copy is a fresh practice list: its scroll, its chains and its overlays start over with it.
        key(box) { PracticeFrame(box, modifier, onStartOver = ::startOver) }
    }

    override fun close() {
        sandboxHolder?.close()
        sandboxHolder = null
    }

    override fun onCleared() {
        close()
    }

    companion object {
        private const val EGGS = 18

        /** The phone's lab: the real Feedback (sounds and haptics follow her switches), timeouts and announcer. */
        fun factory(feedback: Feedback, announcer: Announcer, timeouts: Timeouts): () -> SurpriseLabViewModel = {
            SurpriseLabViewModel { SurpriseLabSandbox(feedback = feedback, announcer = announcer, timeouts = timeouts) }
        }
    }
}

/** For previews and tests: a lab whose sandbox is built by [factory]. */
fun previewLab(factory: () -> SurpriseLabSandbox = { SurpriseLabSandbox(feedback = NoOpFeedback, announcer = NoOpAnnouncer, timeouts = DefaultTimeouts) }): SurpriseLabViewModel =
    SurpriseLabViewModel(factory)

/**
 * The practice list's frame in the lab: a phone-sized window under the ribbon, scrolled into view at each Try it, with
 * "Start the practice list over" under it ([onStartOver]; none in tests that show the frame alone).
 */
@Composable
fun PracticeFrame(
    sandbox: SurpriseLabSandbox,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 640.dp,
    onStartOver: (() -> Unit)? = null,
) {
    val fired by sandbox.fired.collectAsState()
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(fired, sandbox) { if (fired != null) requester.bringIntoView() }
    SandboxFollowUps(sandbox)
    val dark = isSystemInDarkTheme()
    LaunchedEffect(dark) { sandbox.variants.onSystemDark(dark) }
    val variant by sandbox.variants.variant.collectAsState()
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.bringIntoViewRequester(requester), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .testTag(LAB_PRACTICE_TAG)
                .hardShadow(shape, PicnicTheme.shadows, large = true)
                .clip(shape)
                .border(3.dp, PicnicTheme.colors.stickerWhite, shape),
        ) {
            PicnicTheme(variant = variant, reducedMotion = LocalReducedMotion.current) {
                PracticeList(sandbox.model, ribbon = { LabRibbon() })
            }
        }
        if (fired == 14) {
            val album by sandbox.album.album.collectAsState(initial = null)
            album?.let { a ->
                earth.cora.picnic.ui.album.PerfectWeeksCard(
                    a.perfectStreak,
                    earned = a.perfectStreak >= EggRules.PERFECT_WEEK_TRIPS,
                    holo = (a.perfectUntil ?: 0L) > sandbox.clock.now(),
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                )
            }
        }
        if (onStartOver != null) {
            PicnicButton(
                stringResource(R.string.egg_lab_reset),
                onClick = onStartOver,
                style = PicnicButtonStyle.Secondary,
                modifier = Modifier.padding(top = 14.dp).testTag(LAB_RESET_TAG),
            )
        }
    }
}

/** Runs the sandbox's follow-ups (the cake's check-off, the jackpot's arrival) on the frame clock. */
@Composable
fun SandboxFollowUps(sandbox: SurpriseLabSandbox) {
    val followUp by sandbox.followUp.collectAsState()
    followUp?.let { f ->
        LaunchedEffect(f.id, f.afterMs) {
            withContext(FixedMotionScale) { holdOnFrameClock(f.afterMs) }
            f.run()
            sandbox.followUpDone(f.id)
        }
    }
}

/** "Surprise lab · practice list": a lemon-dot washi ribbon across the practice list's top. */
@Composable
private fun LabRibbon() {
    val c = PicnicTheme.colors
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(c.patch)
                .border(2.dp, c.stickerWhite, RoundedCornerShape(9.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.lab_ribbon), style = PicnicTheme.type.label.copy(color = c.onPad))
        }
        WashiTape(WashiPattern.LemonDots, Modifier.align(Alignment.CenterStart).padding(start = 18.dp), length = 40.dp, rotation = -6f)
    }
}

/** The practice list's frame in the lab (tests). */
const val LAB_PRACTICE_TAG: String = "lab_practice"

/** "Start the practice list over" (tests). */
const val LAB_RESET_TAG: String = "lab_reset"
