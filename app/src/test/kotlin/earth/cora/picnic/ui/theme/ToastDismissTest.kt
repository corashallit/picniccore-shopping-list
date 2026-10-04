package earth.cora.picnic.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Retiring a toast whose offer no longer holds (docs/DESIGN_STICKER.md §4.15): [ToastHostState.dismiss] drops the queued
 * ones of its kind and marks the one showing to lift off now; it takes no merge meanwhile, and the next one shows once it
 * has gone.
 */
class ToastDismissTest {
    @Test
    fun dismissRetiresTheCurrentAndDropsTheQueued() {
        val state = ToastHostState()
        val nudge = ToastMessage("New shop? Start a fresh sticker book", kind = "nudge", actionLabel = "New trip", onAction = {})
        val queued = ToastMessage("another nudge", kind = "nudge")
        val trip = ToastMessage("New trip · a fresh sticker book ♡", kind = "trip")
        state.show(nudge)
        state.show(queued)
        state.show(trip)
        assertEquals(listOf(queued.id, trip.id), state.queue.map { it.id })

        state.dismiss("nudge")
        assertEquals("the queued nudge goes", listOf(trip.id), state.queue.map { it.id })
        assertEquals("the one showing lifts off", nudge.id, state.retiring)
        assertEquals(nudge.id, state.current?.id)

        // A retiring Undo takes no merge: a new one of its kind waits for its own turn.
        val book = ToastMessage("New sticker book for today", kind = "nudge", actionLabel = "Undo", onAction = {})
        state.show(book)
        assertEquals(nudge.text, state.current?.text)
        assertTrue(state.queue.any { it.id == book.id })

        state.finished(nudge.id)
        assertNull("retiring clears once it has gone", state.retiring)
        assertEquals(trip.id, state.current?.id)
    }

    @Test
    fun dismissOfAnotherKindLeavesTheCurrentAlone() {
        val state = ToastHostState()
        val torn = ToastMessage("Oat cereal torn out", kind = "tear", actionLabel = "Undo", onAction = {})
        state.show(torn)
        state.dismiss("nudge")
        assertNull(state.retiring)
        assertEquals(torn.id, state.current?.id)
    }
}
