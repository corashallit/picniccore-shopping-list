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

/** The lab's state holder: one sandbox, made on the first Try it, discarded when she leaves. */
class SurpriseLabViewModel(
    private val sandboxFactory: () -> SurpriseLabSandbox,
) : ViewModel(), SurpriseLab {
    private var sandboxHolder by mutableStateOf<SurpriseLabSandbox?>(null)

    /** The sandbox while the lab is open and an egg has been tried (tests). */
    val sandbox: SurpriseLabSandbox? get() = sandboxHolder

    @Composable
    override fun eggs(): List<LabEgg> = ShellSurpriseLab.eggs()

    override val canFire: Boolean = true

    override fun fire(number: Int) {
        val box = sandboxHolder ?: sandboxFactory().also { sandboxHolder = it }
        box.fire(number)
    }

    @Composable
    override fun Practice(modifier: Modifier) {
        val box = sandboxHolder ?: return
        PracticeFrame(box, modifier)
    }

    override fun close() {
        sandboxHolder?.close()
        sandboxHolder = null
    }

    override fun onCleared() {
        close()
    }

    companion object {
        /** The phone's lab: the real Feedback (sounds and haptics follow her switches), timeouts and announcer. */
        fun factory(feedback: Feedback, announcer: Announcer, timeouts: Timeouts): () -> SurpriseLabViewModel = {
            SurpriseLabViewModel { SurpriseLabSandbox(feedback = feedback, announcer = announcer, timeouts = timeouts) }
        }
    }
}

/** For previews and tests: a lab whose sandbox is built by [factory]. */
fun previewLab(factory: () -> SurpriseLabSandbox = { SurpriseLabSandbox(feedback = NoOpFeedback, announcer = NoOpAnnouncer, timeouts = DefaultTimeouts) }): SurpriseLabViewModel =
    SurpriseLabViewModel(factory)

/** The practice list's frame in the lab: a phone-sized window under the ribbon, scrolled into view at each Try it. */
@Composable
fun PracticeFrame(sandbox: SurpriseLabSandbox, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 640.dp) {
    val fired by sandbox.fired.collectAsState()
    val followUp by sandbox.followUp.collectAsState()
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(fired, sandbox) { if (fired != null) requester.bringIntoView() }
    followUp?.let { f ->
        LaunchedEffect(f.id, f.afterMs) {
            withContext(FixedMotionScale) { holdOnFrameClock(f.afterMs) }
            f.run()
            sandbox.followUpDone(f.id)
        }
    }
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
