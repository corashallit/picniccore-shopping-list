package earth.cora.picnic.platform

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The cue map is exactly docs/DESIGN_STICKER.md §14.3 (sound + haptic per moment), and the clips are the ten of
 * §14.2. CueMapTest (the audit) then checks every moment asks for its cue.
 */
class CueTableTest {
    private val table: Map<Cue, Pair<Sound?, Haptic?>> = mapOf(
        Cue.HANKO to (Sound.THUNK to Haptic.CONFIRM),
        Cue.PEEL to (Sound.TK to Haptic.CLOCK_TICK),
        Cue.LAND to (Sound.PFF to Haptic.TEXT_HANDLE_MOVE),
        Cue.LCD_TICK to (Sound.BEEP to null),
        Cue.MAGIC_APPEAR to (Sound.KIRA to null),
        Cue.UNCHECK to (Sound.PEEL to null),
        Cue.TEAR_OFF to (Sound.PEEL to Haptic.CLOCK_TICK),
        Cue.AISLE_DONE to (Sound.THUNK to Haptic.CONFIRM),
        Cue.STAMP to (Sound.THUNK to Haptic.CONFIRM),
        Cue.NEXT_CARD to (Sound.PON to null),
        Cue.CELEBRATION to (Sound.JINGLE to null),
        Cue.PERFECT to (Sound.THUNK to Haptic.CONFIRM),
        Cue.CAPSULE_POP to (Sound.PON to Haptic.CONFIRM),
        Cue.CASSETTE to (Sound.CLUNK to Haptic.KEYBOARD_TAP),
        Cue.PETAL_SHAKE to (Sound.KIRA to Haptic.CLOCK_TICK),
        Cue.PET_FED to (Sound.BEEP to null),
        Cue.RECEIPT_PRINT to (Sound.PRINT to Haptic.CLOCK_TICK),
        Cue.RECEIPT_TEAR to (Sound.TK to null),
        Cue.PULL_REFRESH to (Sound.PON to Haptic.CLOCK_TICK),
        Cue.BONUS_JINGLE to (Sound.JINGLE to null),
    )

    @Test
    fun everyCueMatchesSection14_3() {
        assertEquals(Cue.entries.toSet(), table.keys)
        for (cue in Cue.entries) {
            assertEquals("$cue", table.getValue(cue), cue.sound to cue.haptic)
        }
    }

    @Test
    fun theTenClipsOfSection14_2() {
        assertEquals(
            listOf("thunk", "tk", "pff", "clunk", "beep", "pon", "kira", "peel", "jingle", "print"),
            Sound.entries.map { it.clip },
        )
        // Every clip is used by at least one moment.
        assertEquals(Sound.entries.toSet(), Cue.entries.mapNotNull { it.sound }.toSet())
    }

    @Test
    fun bonusJingleEveryFourSeconds() {
        assertEquals(4000L, BONUS_JINGLE_PERIOD_MS)
    }
}
