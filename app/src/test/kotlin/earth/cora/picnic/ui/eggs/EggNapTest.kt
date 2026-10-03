package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.NewTripMode
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.dismissNap
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.core.store.TripFiling
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 245 (egg 8): the cat curls up in the aisle basket that is still empty once every other stop is done, after the
 * chain that finished them; a tap stretches it, wobbles the basket and sends it trotting off, and it does not come back
 * this trip; a trip closed with an empty basket files that aisle (`napAisleId`) for the album's book page, unless the
 * cat already left.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggNapTest {
    @get:Rule
    val compose = createComposeRule()

    private val list = PracticeFixture.LIST_ID

    /** The fixture with every item checked but [left]'s ([PracticeFixture.TRIP_START] + 30 min on). */
    private fun allBut(vararg left: String): AppState {
        val s = PracticeFixture.state()
        var t = PracticeFixture.TRIP_START + 30 * PracticeFixture.MIN
        return s.list(list)!!.items.filter { !it.checked && it.aisleId !in left }.fold(s) { acc, item ->
            t += 20_000
            acc.toggleChecked(list, item.id, t)
        }
    }

    @Test
    fun theCatCurlsUpWhenEveryOtherStopIsDone() {
        // Two stops still to do: no cat yet.
        val two = EngineRig(state = allBut("frozen", "household"))
        try {
            assertNull(two.eggs.napSectionId)
        } finally {
            two.close()
        }
        // Frozen alone: the cat sleeps in its basket.
        val one = EngineRig(state = allBut("frozen"))
        try {
            assertEquals("frozen", one.eggs.napSectionId)
            // A stop that has something gotten but is not done blocks it too.
            val half = PracticeFixture.state().list(list)!!.items.first { it.aisleId == "frozen" }
            val partly = allBut("frozen").toggleChecked(list, half.id, PracticeFixture.NOW + 3_600_000)
            one.store.apply { partly }
            assertNull("frozen is not empty any more", one.eggs.napSectionId)
        } finally {
            one.close()
        }
    }

    @Test
    fun everythingElseNeverHoldsTheCat() {
        // Every aisle done; only "Everything else" has something left, nothing gotten there.
        val s = allBut().let { st ->
            st.addItems(list, listOf(GroceryItem(id = "x", name = "Birthday card", icon = "paper_bag", addedAt = PracticeFixture.NOW, source = ItemSource.TYPED)), PracticeFixture.NOW).first
        }
        val rig = EngineRig(state = s)
        try {
            assertNull(rig.eggs.napSectionId)
        } finally {
            rig.close()
        }
    }

    @Test
    fun aTapSendsItOffForTheRestOfTheTrip() {
        val r = EggRig(compose).start()
        assertNull(r.eggs.napSectionId)
        val t0 = r.fire(8)
        assertEquals("frozen", r.eggs.napSectionId)
        // The chain that finished the other stops plays out, then the cat curls up (chain end + 0.5 s).
        r.advanceTo(t0 + 2_500)
        val curled = r.probe.frames.lastOrNull { it.tag == "nap" }
        assertNotNull("the cat is curled in its basket", curled)
        val basket = r.anchor(AnchorRegistry.basketKey("frozen"))
        assertTrue("in frozen's basket: ${curled!!.center} in $basket", basket.inflate(6f).contains(curled.center))
        // A tap: it stretches and trots off, the basket wobbles, and it is saved.
        r.tapAt(curled.center)
        assertNull(r.eggs.napSectionId)
        assertNotNull(r.eggs.napLeaving)
        assertEquals("frozen", r.eggs.basketHello?.sectionId)
        r.advance(NapMotion.LEAVE_MS + 200)
        assertNull(r.eggs.napLeaving)
        assertTrue("saved on the trip", r.list.trip!!.napDismissed)
        // It never comes back this trip, whatever else changes.
        r.act { r.model.set { it.setOpenAisle(list, "frozen") } }
        r.advance(500)
        assertNull(r.eggs.napSectionId)
        assertTrue(r.probe.frames.none { it.tag == "nap" && it.time > NapMotion.LEAVE_MS })
    }

    @Test
    fun aClosedTripFilesTheEmptyAisleForTheAlbum() {
        val rig = EngineRig(state = PracticeFixture.state())
        try {
            // Frozen and Household are still empty; the first in walking order is filed.
            val book = runBlocking { TripFiling.newTrip(rig.store, rig.albumStore, list, NewTripMode.CLEAR_GOTTEN, PracticeFixture.NOW) }
            assertEquals("frozen", book!!.napAisleId)
            assertEquals("frozen", runBlocking { rig.albumStore.current() }.books.first().napAisleId)
        } finally {
            rig.close()
        }
        // The cat already trotted off: no nap on that book's page.
        val gone = EngineRig(state = PracticeFixture.state().dismissNap(list))
        try {
            val book = runBlocking { TripFiling.newTrip(gone.store, gone.albumStore, list, NewTripMode.CLEAR_GOTTEN, PracticeFixture.NOW) }
            assertNull(book!!.napAisleId)
        } finally {
            gone.close()
        }
    }

    @Test
    fun reducedMotionCurledStaticAndAFade() {
        val r = EggRig(compose, reduced = true).start()
        r.fire(8)
        r.advance(2_500)
        val curled = r.probe.frames.lastOrNull { it.tag == "nap" }
        assertNotNull(curled)
        r.tapAt(curled!!.center)
        val leaving = r.eggs.napLeaving
        assertNotNull(leaving)
        assertTrue("it fades out", leaving!!.reduced)
        assertNull("no wobble under reduced motion", r.eggs.basketHello)
        r.advance(300)
        assertNull(r.eggs.napLeaving)
    }
}
