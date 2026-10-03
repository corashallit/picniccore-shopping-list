package earth.cora.picnic.ui.list

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import earth.cora.picnic.ui.list.motion.ListPresentation

/**
 * One frame of the real list's motion: the engine's frame time (it stands still while the engine has nothing to play),
 * the test clock's time, and what the engine presented.
 */
data class ListFrame(val time: Long, val p: ListPresentation, val clock: Long = time)

/**
 * Runs held frames for about [ms] milliseconds on [rig]'s real List destination and returns each frame's presentation
 * (finite: never waits on a condition).
 */
fun ComposeContentTestRule.sample(rig: ListRig, ms: Long, each: (ListFrame) -> Unit = {}): List<ListFrame> {
    val out = ArrayList<ListFrame>()
    repeat(((ms + 15) / 16).toInt()) {
        frames(1)
        val f = ListFrame(rig.engine.time, rig.engine.current.value, mainClock.currentTime)
        each(f)
        out += f
    }
    return out
}

/** The first frame time at which [test] holds, or null. */
fun List<ListFrame>.firstTime(test: (ListPresentation) -> Boolean): Long? = firstOrNull { test(it.p) }?.time

/** The tap time of [itemId]'s chain: when its row's phase started. */
fun List<ListFrame>.tapTime(itemId: String): Long = first { it.p.rowStarts[itemId] != null }.p.rowStarts.getValue(itemId)

/** Asserts [actual] happened at [expected] within a frame or two. */
fun assertBeat(what: String, expected: Long, actual: Long?) {
    org.junit.Assert.assertNotNull("$what never happened", actual)
    org.junit.Assert.assertTrue("$what at ${actual!! - expected} ms from its beat", actual in (expected - 17)..(expected + 34))
}

/** When [itemId]'s sticker lands after its tap at [tap] (§8.3): 620 ms, 680 ms after a personality hop. */
fun landTime(rig: ListRig, itemId: String, tap: Long): Long {
    val personality = rig.vm.state.value.row(itemId)?.personality ?: earth.cora.picnic.core.catalog.Personality.NONE
    return tap + earth.cora.picnic.ui.list.motion.Beats.HOP + earth.cora.picnic.ui.list.motion.Beats.hopMillis(personality)
}
