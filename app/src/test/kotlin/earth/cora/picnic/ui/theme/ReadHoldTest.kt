package earth.cora.picnic.ui.theme

import earth.cora.picnic.platform.DefaultTimeouts
import earth.cora.picnic.platform.Timeouts
import org.junit.Assert.assertEquals
import org.junit.Test

/** The reading rule (§9; checklist 166). */
class ReadHoldTest {
    @Test
    fun formula() {
        assertEquals(2600L, readHold(3, PicnicMotion.floorCard))
        assertEquals(3000L, readHold(6, PicnicMotion.floorCard))
        assertEquals(4000L, readHold(6, PicnicMotion.floorToast))
        assertEquals(3600L, readHold(8, PicnicMotion.floorBeat))
        assertEquals(1500L, readHold(1, PicnicMotion.floorBeat))
        assertEquals(2400L, readHold(4, PicnicMotion.floorBeat))
        assertEquals(1800L, readHold(2, PicnicMotion.floorBeat))
        assertEquals(4800L, readHold(12, 4000))
    }

    @Test
    fun wordsDropPunctuationOnlyTokens() {
        assertEquals(6, words("NEXT 7 Pasta, rice & international A3"))
        assertEquals(3, words("NEXT ✦ 5 Breakfast"))
        assertEquals(4, words("24 stickers · 41 min"))
        assertEquals(2, words("bonus sticker!"))
        assertEquals(6, words("Added 5 items to Saturday shop ♡"))
        assertEquals(4, words("4 · Baking · 1 left"))
        assertEquals(1, words("PER-FECT!"))
        assertEquals(0, words(" - – — & / + : "))
        // §9's table lists 6 for this toast; the formula's own count is 7 (the hold is the 4000 ms floor either way).
        assertEquals(7, words("Rain on the way · bring the tote!"))
        assertEquals(4000L, readHold(words("Rain on the way · bring the tote!"), PicnicMotion.floorToast))
        assertEquals(0, words(""))
    }

    @Test
    fun toastHoldsThroughTheTimeoutsSetting() {
        val undo = ToastMessage("Oat cereal torn out", kind = "tear", actionLabel = "Undo", onAction = {})
        assertEquals(4000L, toastHold(undo, DefaultTimeouts))
        val doubled = object : Timeouts {
            override fun recommended(holdMs: Long, controls: Boolean): Long = if (controls) holdMs * 2 else holdMs
            override val holdThresholdMs: Long = 440
        }
        assertEquals(8000L, toastHold(undo, doubled))
        assertEquals(4000L, toastHold(ToastMessage("Sorted 6 items"), doubled))
    }
}
