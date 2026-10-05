package earth.cora.picnic.ui.eggs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import earth.cora.picnic.ui.list.motion.BeatName
import earth.cora.picnic.ui.theme.PetPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 254 (egg 15), `feedsAndDances`: the pet bobs 2 steps a second in the LCD, chomps in 2 frames (520 ms) at every
 * landing, and dances in the finale (the celebration's PetDance beat); under reduced motion it sits still.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class LcdPetTest {
    @get:Rule
    val compose = createComposeRule()

    /** The pose the LCD's bee is drawn in, every frame. */
    @Composable
    private fun PoseProbe(engine: SurpriseEngine, record: (PetPose) -> Unit) {
        val s by engine.state.collectAsState()
        val pose = rememberPetPose(s)
        SideEffect { record(pose) }
    }

    @Test
    fun feedsAndDances() {
        val poses = ArrayList<Pair<Long, PetPose>>()
        val r = EggRig(compose)
        r.start {
            PracticeListScreen(r.sandbox)
            PoseProbe(r.engine) { poses += r.now to it }
        }
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        assertTrue(r.eggs.pet)
        r.advance(2_000)
        // It bobs, 2 steps a second.
        val bob = poses.filter { it.first >= r.now - 1_000 }.map { it.second }.toSet()
        assertTrue("bobbing: $bob", PetPose.Bob in bob && PetPose.Rest in bob)
        // A check-off: the sticker lands in its basket and the pet chomps, 2 frames over 520 ms.
        poses.clear()
        r.act { r.model.toggle("baking-powder") }
        var guard = 0
        while (r.eggs.petChompId == null && guard++ < 200) r.frame()
        assertNotNull("a landing feeds it", r.eggs.petChompId)
        val landed = r.now
        r.advance(700)
        val chomp = poses.filter { it.first >= landed }.map { it.second }
        val first1 = chomp.indexOf(PetPose.Chomp1)
        val first2 = chomp.indexOf(PetPose.Chomp2)
        assertTrue("Chomp1 then Chomp2: $chomp", first1 >= 0 && first2 > first1)
        val chompFrames = poses.filter { it.first >= landed && (it.second == PetPose.Chomp1 || it.second == PetPose.Chomp2) }
        assertEquals(EggRules.CHOMP_MS.toDouble(), (chompFrames.last().first - chompFrames.first().first).toDouble(), 40.0)
        // Finish the list: the finale's scene has the pet's dance.
        val rest = r.list.items.filter { !it.checked }.map { it.id }
        for (id in rest) {
            r.act { r.model.toggle(id) }
            r.advance(150)
        }
        guard = 0
        while (r.model.motion.current.value.celebration?.plan == null && guard++ < 3_000) r.frame()
        val cele = r.model.motion.current.value.celebration
        assertNotNull("the celebration plays", cele?.plan)
        assertTrue("the pet hatched this trip", cele!!.content!!.petHatched)
        assertTrue("it dances (4 frames) in the finale", cele.plan!!.beats.any { it.name == BeatName.PetDance })
        assertTrue(r.store().list(PracticeFixture.LIST_ID)!!.trip!!.pet)
    }

    @Test
    fun reducedMotionSitsStill() {
        val poses = ArrayList<PetPose>()
        val r = EggRig(compose, reduced = true)
        r.start {
            PracticeListScreen(r.sandbox)
            PoseProbe(r.engine) { poses += it }
        }
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        r.advance(2_000)
        r.act { r.model.toggle("baking-powder") }
        r.advance(1_500)
        assertTrue("the bee sits still: ${poses.toSet()}", poses.all { it == PetPose.Rest })
    }

    private fun EggRig.store() = state
}
