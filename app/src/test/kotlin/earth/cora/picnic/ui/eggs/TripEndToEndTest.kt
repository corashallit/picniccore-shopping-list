package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
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
 * Item 274 end to end, on the practice list with WP6's chains and the real overlays: check all 24 fixture items → the
 * finale → close it → hold the LCD → the receipt prints → tap it → the slip is in the book → New trip → the album's book
 * carries the receipt.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class TripEndToEndTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun checkEverythingToTheReceiptInTheAlbum() {
        val r = EggRig(compose, fixture = PracticeFixture.unchecked()).start()
        assertNull(r.list.trip)
        // Check all 24, a third of a second apart, as she would going round the store.
        val ids = r.list.items.map { it.id }
        assertEquals(24, ids.size)
        for (id in ids) {
            r.act { r.model.toggle(id) }
            r.advance(320)
        }
        assertTrue(r.list.items.all { it.checked })
        assertNotNull(r.list.trip!!.completedAt)
        // The finale plays; she closes it.
        var guard = 0
        while (r.model.motion.current.value.celebration?.plan == null && guard++ < 3_000) r.frame()
        assertNotNull("the celebration plays", r.model.motion.current.value.celebration)
        r.advance(2_000)
        r.act { r.model.motion.closeCelebration() }
        guard = 0
        while (r.model.motion.current.value.celebration != null && guard++ < 1_000) r.frame()
        assertNull("closed", r.model.motion.current.value.celebration)
        assertNotNull("the finale was shown", r.list.trip!!.finaleShownAt)
        // Hold the LCD: the receipt prints (never the pet).
        r.advance(500)
        r.holdLcd(r.timeouts.holdThresholdMs + 20)
        val printed = r.list.trip!!.receipt
        assertNotNull(printed)
        assertEquals(24, printed!!.stickers)
        assertEquals(8, printed.baskets)
        assertTrue(!r.list.trip!!.pet)
        r.advance(2_100)
        // Tap it: into the book, the slip at the fore-edge.
        compose.onNodeWithTag(EggTags.RECEIPT).performClick()
        r.advance(900)
        assertTrue(r.list.trip!!.receiptSaved)
        compose.onNodeWithTag(PracticeTags.SLIP, useUnmergedTree = true).assertExists()
        // New trip: the book is filed in the album with its receipt; the list starts over.
        r.act { r.sandbox.newTrip() }
        r.advance(300)
        val album = runBlocking { r.sandbox.album.current() }
        val book = album.books.first()
        assertEquals(printed, book.receipt)
        assertTrue(book.perfect)
        assertEquals(24, book.stickers.size)
        assertNull(r.state.list(PracticeFixture.LIST_ID)!!.trip)
    }
}
