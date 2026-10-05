package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
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
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.WashiPattern
import earth.cora.picnic.ui.theme.WashiTape
import earth.cora.picnic.ui.theme.hardShadow
import earth.cora.picnic.ui.theme.holdOnFrameClock
import kotlinx.coroutines.withContext

/*
 * The Surprise lab's content (docs/DESIGN_STICKER.md §6.9, §12.3) inside WP8's shell (SurpriseLabScreen): the 18 egg
 * tiles with their "Try it" lines, a Try it that really plays each egg on the sandbox, and the practice list it plays
 * on (WP5's real List screen over the sandbox, PracticeListScreen), scrolled into view under its "Surprise lab ·
 * practice list" ribbon. It works with Easter eggs off (the sandbox
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

    /** The egg last tried: the practice list opens under its tile (and stays there through "Start over"). */
    private var lastTried by mutableStateOf<Int?>(null)

    override val practiceAfter: Int? get() = lastTried

    /** The sandbox while the lab is open and an egg has been tried (tests). */
    val sandbox: SurpriseLabSandbox? get() = sandboxHolder

    @Composable
    override fun eggs(): List<LabEgg> = ShellSurpriseLab.eggs()

    override val canFire: Boolean = true

    override fun fire(number: Int) {
        if (number !in 1..EGGS) return
        lastTried = number
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
        lastTried = null
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
 * "Start the practice list over" under it ([onStartOver]; none in tests that show the frame alone). With large text the
 * window grows by what the binder top, the book and the add bar grow (§15), so the list keeps room for each scene; an
 * egg that plays in the list (not in the binder top or the book) brings the list and its add bar into view.
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
    val density = androidx.compose.ui.platform.LocalDensity.current
    val frameHeight = height + LARGE_TEXT_ROOM * ((density.fontScale - 1f) / (2f - 1f)).coerceIn(0f, 1f)
    var columnTop by remember { mutableStateOf<Float?>(null) }
    var columnWidth by remember { mutableStateOf(0) }
    LaunchedEffect(fired, sandbox) {
        val egg = fired ?: return@LaunchedEffect
        requester.bringIntoView()
        if (egg in TOP_STACK_EGGS) return@LaunchedEffect
        // The list and its add bar, where the scene plays (its own scroll brings the scene above the bar).
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        val top = columnTop ?: return@LaunchedEffect
        val list = sandbox.model.anchors.bounds(EggAnchors.LIST) ?: return@LaunchedEffect
        val bottom = with(density) { frameHeight.toPx() }
        requester.bringIntoView(androidx.compose.ui.geometry.Rect(0f, (list.top - top).coerceIn(0f, bottom), columnWidth.toFloat(), bottom))
    }
    SandboxFollowUps(sandbox)
    // The phone's dark mode, unless the egg forces Day (§12.3: Dusk binder, Golden hour).
    val dark = isSystemInDarkTheme()
    LaunchedEffect(dark, sandbox) { sandbox.onSystemDark(dark) }
    val variant by sandbox.variants.variant.collectAsState()
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .bringIntoViewRequester(requester)
            .onGloballyPositioned {
                columnTop = it.positionInWindow().y
                columnWidth = it.size.width
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                // As wide as her phone (past the lab's gutters): the real List screen lays out here as on her list.
                .bleed(earth.cora.picnic.ui.common.ScreenGutter)
                .fillMaxWidth()
                .height(frameHeight)
                .testTag(LAB_PRACTICE_TAG)
                .hardShadow(shape, PicnicTheme.shadows, large = true)
                .clip(shape)
                .border(3.dp, PicnicTheme.colors.stickerWhite, shape),
        ) {
            PicnicTheme(variant = variant, reducedMotion = LocalReducedMotion.current) {
                // The real List screen under the "Surprise lab · practice list" ribbon (§6.9).
                PicnicBackground(Modifier.fillMaxSize()) {
                    // Egg 1's wash reaches the ribbon's strip of binder too (the list washes its own).
                    DuskWash(sandbox.engine.state.collectAsState().value)
                    Column(Modifier.fillMaxSize()) {
                        LabRibbon()
                        PracticeListScreen(sandbox, Modifier.weight(1f))
                    }
                }
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

/** Widens this by [by] on each side, past its parent's gutters (it reports its parent's width, so nothing else moves). */
private fun Modifier.bleed(by: androidx.compose.ui.unit.Dp): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val side = by.roundToPx()
        val wider = if (constraints.hasBoundedWidth) {
            constraints.copy(minWidth = constraints.minWidth + 2 * side, maxWidth = constraints.maxWidth + 2 * side)
        } else {
            constraints
        }
        val placeable = measurable.measure(wider)
        layout((placeable.width - 2 * side).coerceAtLeast(0), placeable.height) { placeable.place(-side, 0) }
    },
)

/** At font scale 2.0 the practice window is this much taller (the binder top, the book and the add bar grow so much). */
private val LARGE_TEXT_ROOM = 380.dp

/** The eggs that play in the binder top or the book (the sun, the title, the stamps, the album card, the LCD). */
private val TOP_STACK_EGGS = setOf(1, 7, 10, 12, 14, 15, 18)

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

/**
 * "Surprise lab · practice list": a lemon-dot washi ribbon across the practice list's top. The washi sits before the
 * label's patch, its end tucked over the patch's border only (never over the words), at any font scale; a label too
 * long for one line wraps beside it.
 */
@Composable
private fun LabRibbon() {
    val c = PicnicTheme.colors
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp, start = 8.dp, end = 8.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .zIndex(1f)
                    .offset(x = RIBBON_TAPE_TUCK)
                    .clearAndSetSemantics {
                        this[earth.cora.picnic.ui.a11y.Decorative] = Unit
                        testTag = LAB_RIBBON_WASHI_TAG
                    },
            ) {
                WashiTape(WashiPattern.LemonDots, length = 40.dp, rotation = -6f)
            }
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.patch)
                    .border(2.dp, c.stickerWhite, RoundedCornerShape(9.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
                    .semantics { heading() },
            ) {
                Text(stringResource(R.string.lab_ribbon), style = PicnicTheme.type.label.copy(color = c.onPad), modifier = Modifier.testTag(LAB_RIBBON_TEXT_TAG))
            }
        }
    }
}

/** How far the ribbon's washi reaches over the patch's left edge: within its 2 dp border and 10 dp padding. */
private val RIBBON_TAPE_TUCK = 7.dp

/** The ribbon's washi and words (tests). */
const val LAB_RIBBON_WASHI_TAG: String = "lab_ribbon_washi"
const val LAB_RIBBON_TEXT_TAG: String = "lab_ribbon_text"

/** The practice list's frame in the lab (tests). */
const val LAB_PRACTICE_TAG: String = "lab_practice"

/** "Start the practice list over" (tests). */
const val LAB_RESET_TAG: String = "lab_reset"
