package earth.cora.picnic.ui.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.platform.FakeTimeouts
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.eggs.EggTags
import earth.cora.picnic.ui.eggs.JackpotFx
import earth.cora.picnic.ui.eggs.LadybugFx
import earth.cora.picnic.ui.eggs.PracticeFixture
import earth.cora.picnic.ui.eggs.PracticeListScreen
import earth.cora.picnic.ui.eggs.RollingFx
import earth.cora.picnic.ui.eggs.SandboxClock
import earth.cora.picnic.ui.eggs.ShinyFx
import earth.cora.picnic.ui.eggs.SurpriseLabSandbox
import earth.cora.picnic.ui.eggs.SurpriseState
import earth.cora.picnic.ui.list.ListFixture
import earth.cora.picnic.ui.list.ListRig
import earth.cora.picnic.ui.theme.LocalTimeouts
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 286 (docs/DESIGN_STICKER.md §15 "Decor", §4.1, §12.1): decor has no semantics. Three ways round:
 * - every node marked decorative on every screen fixture (the walker, Glow at 1.0 and Day at 2.0) carries nothing a
 *   screen reader, a switch or a keyboard could use, and nothing under it does either;
 * - every decor piece the eggs can lay on the real List at once (the season's tape and pictogram, petals, ants,
 *   confetti, the kirakira twinkle, the rain umbrella, the nap cat, the ladybug, a rolling sticker, a shiny burst, the
 *   jackpot reveal, golden hour) leaves what TalkBack is handed exactly as it was, frame after frame;
 * - each of the 18 eggs, fired from the Surprise lab's practice list, adds nothing to the egg overlay layer that TalkBack
 *   could stop on, except the printed receipt, which is a real button (§13.2).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class DecorSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun talkBackOn() = TalkBackTree.screenReader(true)

    @After
    fun talkBackOff() = TalkBackTree.screenReader(false)

    @Test
    fun decorNodesCarryNothingGlow1() = decorNodes(Look.Glow1)

    @Test
    fun decorNodesCarryNothingDay2() = decorNodes(Look.Day2)

    private fun decorNodes(look: Look) {
        val problems = LinkedHashSet<String>()
        var decor = 0
        ScreenWalker(compose).walk(A11yScreens.all, listOf(look)) {
            pages {
                for (n in unmergedNodes()) {
                    if (n.config.getOrNull(Decorative) == null) continue
                    decor++
                    DecorRules.problems(n).forEach { problems += "$where: decor ${n.config.getOrNull(SemanticsProperties.TestTag) ?: n.id}: $it" }
                }
            }
        }
        assertTrue("decorative nodes seen: $decor", decor > 100)
        assertTrue("decor with semantics:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** What TalkBack is handed, as comparable words: label, role, state, actions. */
    private fun heard(): List<String> = TalkBackTree.stops().map { "${it.label}|${it.role}|${it.state}|${it.clickLabel}|${it.actions}" }.sorted()

    /**
     * On a window tall enough for the whole list: the umbrella sheltering the add bar (egg 16) takes a little of the
     * list's height, so on a phone-height window the last strips would leave the viewport and the comparison would be
     * about scrolling, not decor.
     */
    @Test
    @Config(qualifiers = "w360dp-h1600dp-xxhdpi")
    fun theEggsDecorAddsNothingToTalkBack() {
        val season = ListFixture.seasonAt(ListFixture.NOW)
        val quiet = SurpriseState(season = season)
        val storm = SurpriseState(
            season = season,
            petals = true, petalsId = 101,
            ants = true, antsId = 102,
            confettiId = 103,
            kirakira = true, kirakiraId = 104,
            rain = true,
            napSectionId = "frozen",
            ladybug = LadybugFx(105, "produce"),
            rolling = RollingFx(106, "strawberries", "produce", IconRef("strawberry"), hop = false),
            shinyBursts = listOf(ShinyFx(107, "flour")),
            jackpot = JackpotFx(108, "baking-powder", IconRef("baking_soda"), reduced = false),
            golden = true, goldenMinutesLeft = 20,
        )
        val rig = ListRig()
        rig.surprises.states.value = quiet
        var state by mutableStateOf(0)
        compose.setContent {
            key(state) { PicnicTheme(ThemeVariant.Day) { Box(Modifier.fillMaxSize()) { rig.Content() } } }
        }
        compose.waitForIdle()
        val before = heard()
        assertTrue("the list is heard: $before", before.size > 15)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { rig.surprises.states.value = storm }
        var frames = 0
        repeat(6) { round ->
            repeat(15) {
                Snapshot.sendApplyNotifications()
                compose.waitForIdle()
                compose.mainClock.advanceTimeByFrame()
                frames++
            }
            compose.waitForIdle()
            assertEquals("the decor on frame $frames (round $round) changed what TalkBack hears", before, heard())
        }
        // And the overlay layer itself holds nothing a screen reader could use.
        val overlay = compose.onAllNodesWithTag(EggTags.OVERLAY, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("the eggs' overlay layer is on the list", overlay.isNotEmpty())
        overlay.forEach { o -> assertEquals(emptyList<String>(), DecorRules.underneath(o)) }
        compose.mainClock.autoAdvance = true
        rig.scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    /** Each lab egg on its own practice copy: the overlay layer never holds a node TalkBack could stop on. */
    @Test
    fun everyEggOverlaySaysNothing() {
        var box: SurpriseLabSandbox? by mutableStateOf(null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val b = box
            if (b != null) {
                key(b) {
                    CompositionLocalProvider(LocalTimeouts provides b.timeouts) {
                        PicnicTheme(ThemeVariant.Day) { Box(Modifier.fillMaxSize()) { PracticeListScreen(b) } }
                    }
                }
            }
        }
        val problems = ArrayList<String>()
        var overlaysSeen = 0
        for (egg in 1..18) {
            box?.close()
            box = SurpriseLabSandbox(
                clock = SandboxClock(PracticeFixture.NOW) { compose.mainClock.currentTime },
                timeouts = FakeTimeouts(),
                minuteTicks = false,
            )
            frames(2)
            compose.runOnIdle { box!!.fire(egg) }
            // The overlays play over the next seconds: look at them as they go.
            for (sample in 0 until 5) {
                frames(24)
                val overlay = compose.onAllNodesWithTag(EggTags.OVERLAY, useUnmergedTree = true).fetchSemanticsNodes()
                overlaysSeen += overlay.size
                overlay.forEach { o -> DecorRules.underneath(o).forEach { problems += "egg $egg: $it" } }
            }
        }
        box?.close()
        assertTrue("the overlay layer was looked at: $overlaysSeen", overlaysSeen > 50)
        assertTrue("egg overlays with semantics:\n" + problems.distinct().joinToString("\n"), problems.isEmpty())
    }

    private fun frames(n: Int) = repeat(n) {
        Snapshot.sendApplyNotifications()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }
}

/** What decor may and may not carry (§15 "Decor"). */
object DecorRules {
    /** The keys a screen reader, a switch or a keyboard can use. */
    private val meaningful: List<SemanticsPropertyKey<*>> = listOf(
        SemanticsProperties.Text, SemanticsProperties.ContentDescription, SemanticsProperties.Role,
        SemanticsProperties.StateDescription, SemanticsProperties.ToggleableState, SemanticsProperties.Selected,
        SemanticsProperties.Heading, SemanticsProperties.LiveRegion, SemanticsProperties.PaneTitle,
        SemanticsProperties.EditableText, SemanticsProperties.Focused,
        SemanticsActions.OnClick, SemanticsActions.OnLongClick, SemanticsActions.CustomActions, SemanticsActions.ScrollBy,
    )

    /**
     * What a piece inside decor could still do although its words are cleared: be pressed, held, scrolled or typed in,
     * or take the keyboard's focus (focus is not semantics: a focusable doodle would still be a Tab stop).
     */
    private val usable: List<SemanticsPropertyKey<*>> = listOf(
        SemanticsActions.OnClick, SemanticsActions.OnLongClick, SemanticsActions.CustomActions, SemanticsActions.ScrollBy,
        SemanticsProperties.EditableText, SemanticsProperties.Focused,
    )

    private fun carried(n: SemanticsNode, keys: List<SemanticsPropertyKey<*>> = meaningful): List<String> =
        keys.filter { n.config.getOrNull(it) != null }.map { it.name }

    /**
     * A decorative node's own semantics (a modifier before `decorative()` survives its clearing) and anything under it
     * that could be used (the words under it are cleared; a control under it would be a hidden look-alike).
     */
    fun problems(n: SemanticsNode): List<String> = buildList {
        carried(n).forEach { add("carries $it") }
        fun under(c: SemanticsNode) {
            carried(c, usable).forEach { add("${c.config.getOrNull(SemanticsProperties.TestTag) ?: "node ${c.id}"} under it carries $it") }
            c.children.forEach(::under)
        }
        n.children.forEach(::under)
    }

    /** Every node under [layer] (the eggs' overlay layer) that carries semantics, the printed receipt aside. */
    fun underneath(layer: SemanticsNode): List<String> {
        val out = ArrayList<String>()
        fun go(n: SemanticsNode) {
            val tag = n.config.getOrNull(SemanticsProperties.TestTag)
            // The printed receipt is a real button: "Trip receipt: …", double-tap to save it (§13.2).
            if (tag == EggTags.RECEIPT || tag == EggTags.RECEIPT_POOF) return
            carried(n).forEach { out += "${tag ?: "node ${n.id}"} carries $it (${n.spoken()})" }
            n.children.forEach(::go)
        }
        layer.children.forEach(::go)
        return out
    }
}
