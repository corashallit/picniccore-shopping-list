package earth.cora.picnic.ui.a11y

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.common.PHONE_360
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 280 (docs/DESIGN_STICKER.md §15 "Targets"): every clickable, long-clickable, toggleable or selectable node on
 * every screen fixture the walker shows (the List in each state, every sheet, the overlays, every other screen), on
 * every page of each, is at least 48 × 48 dp, its touch bounds counted: a smaller face counts the 48 dp Compose gives
 * its pointer input only while no other target's own bounds sit inside that area (a neighbour there takes the touch).
 * Only what can be touched counts: a node a scrim, a sheet or a scene covers (Android leaves it out of what it hands
 * TalkBack) is neither a target nor a neighbour, and a neighbour drawn under the target's layer never crowds it (the
 * layer on top takes the touch). Day at font scale 1.0 and Glow at 2.0: every screen in both variants and at both scales.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class TouchTargetAuditTest {
    @get:Rule
    val compose = createComposeRule()

    // On, so Android works out which nodes a scrim, a sheet or a scene covers (TalkBackTree.uncoveredIds).
    @Before
    fun talkBackOn() = TalkBackTree.screenReader(true)

    @After
    fun talkBackOff() = TalkBackTree.screenReader(false)

    @Test
    fun everyTargetDayAtFontScale1() = audit(Look.Day1)

    @Test
    fun everyTargetGlowAtFontScale2() = audit(Look.Glow2)

    private fun audit(look: Look) {
        val small = LinkedHashSet<String>()
        var measured = 0
        val walker = ScreenWalker(compose)
        walker.walk(A11yScreens.all, listOf(look)) {
            pages {
                val found = TouchTargets.audit(this)
                measured += found.measured
                small += found.small
            }
        }
        assertTrue("targets measured: $measured", measured > 400)
        assertTrue("touch targets under 48 × 48 dp:\n" + small.joinToString("\n"), small.isEmpty())
    }
}

/** The 48 dp rule, as one page of a visit sees it. */
object TouchTargets {
    class Found(val measured: Int, val small: List<String>)

    private const val MIN_DP = 47.5f

    fun audit(visit: Visit): Found {
        val d = visit.density
        val uncovered = TalkBackTree.uncoveredIds()
        val targets = visit.nodes().filter { it.actionable && visit.shownAtAll(it) && it.id in uncovered }
        val small = ArrayList<String>()
        var measured = 0
        for (t in targets) {
            if (!visit.wholly(t)) continue
            measured++
            val face = t.boundsInRoot
            val touch = t.touchBoundsInRoot
            val bigEnough = touch.width / d >= MIN_DP && touch.height / d >= MIN_DP
            // Compose widens a small face's pointer input to 48 dp, but a neighbouring target whose own bounds lie in
            // that ring takes the touches there: only a face that is itself 48 dp needs no room around it.
            val faceBig = face.width / d >= MIN_DP && face.height / d >= MIN_DP
            val crowdedBy = if (faceBig) {
                null
            } else {
                targets.firstOrNull { o ->
                    o.id != t.id && o.root().id == t.root().id && !o.boundsInRoot.holds(face) && !face.holds(o.boundsInRoot) &&
                        o.boundsInRoot.overlaps(touch) && !drawnUnder(o, t)
                }
            }
            if (!bigEnough || crowdedBy != null) {
                small += "${visit.where}: ${t.spoken()} ${"%.0f".format(face.width / d)}×${"%.0f".format(face.height / d)} dp" +
                    (if (!bigEnough) ", touch ${"%.0f".format(touch.width / d)}×${"%.0f".format(touch.height / d)} dp" else "") +
                    (crowdedBy?.let { ", crowded by ${it.spoken()}" } ?: "")
            }
        }
        return Found(measured, small)
    }

    /** Whether [a] is drawn under [b]: under their nearest common parent, [a]'s branch comes first (z order). */
    private fun drawnUnder(a: SemanticsNode, b: SemanticsNode): Boolean {
        fun path(n: SemanticsNode): List<SemanticsNode> = generateSequence(n) { it.parent }.toList().reversed()
        val pa = path(a)
        val pb = path(b)
        var i = 0
        while (i < pa.size && i < pb.size && pa[i].id == pb[i].id) i++
        if (i == 0 || i >= pa.size || i >= pb.size) return false
        val siblings = pa[i - 1].children.map { it.id }
        return siblings.indexOf(pa[i].id) < siblings.indexOf(pb[i].id)
    }

    private fun Rect.holds(o: Rect) = left <= o.left + 0.5f && top <= o.top + 0.5f && right >= o.right - 0.5f && bottom >= o.bottom - 0.5f
}
