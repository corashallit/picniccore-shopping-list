package earth.cora.picnic.platform

/*
 * Sounds and haptics as one service (docs/DESIGN_STICKER.md §14). Owner: WP10 (FeedbackGateTest).
 * PHASE-0 CONTRACT: [Feedback], [Cue], [Sound], [Haptic]; [NoOpFeedback] stands in until WP10's
 * SoundPool + View.performHapticFeedback implementation lands (AppContainer wires it).
 */

/** The synthesized clips (§14.2), `res/raw/sk_<clip>.wav` (WP2). */
enum class Sound(val clip: String) {
    THUNK("thunk"),
    TK("tk"),
    PFF("pff"),
    CLUNK("clunk"),
    BEEP("beep"),
    PON("pon"),
    KIRA("kira"),
    PEEL("peel"),
    JINGLE("jingle"),
    PRINT("print"),
}

/**
 * The haptics (§14.3), mapped to HapticFeedbackConstants by the implementation: CONFIRM (API 30+, else
 * VIRTUAL_KEY), CLOCK_TICK, TEXT_HANDLE_MOVE (API 27+, else CLOCK_TICK), KEYBOARD_TAP.
 */
enum class Haptic { CONFIRM, CLOCK_TICK, TEXT_HANDLE_MOVE, KEYBOARD_TAP }

/** One moment of the cue map (§14.3): its sound and its haptic (either may be absent). */
enum class Cue(val sound: Sound?, val haptic: Haptic?) {
    /** The hanko on a check-off. */
    HANKO(Sound.THUNK, Haptic.CONFIRM),

    /** The peel before the hop. */
    PEEL(Sound.TK, Haptic.CLOCK_TICK),

    /** The sticker lands in its basket. */
    LAND(Sound.PFF, Haptic.TEXT_HANDLE_MOVE),

    /** The LCD digits tick (sound only). */
    LCD_TICK(Sound.BEEP, null),

    /** The twin's magic appear in the book. */
    MAGIC_APPEAR(Sound.KIRA, null),

    /** Un-check, page turn, tray curl. */
    UNCHECK(Sound.PEEL, null),

    /** Tear-off delete. */
    TEAR_OFF(Sound.PEEL, Haptic.CLOCK_TICK),

    /** Aisle done: the lid and its GOT IT. */
    AISLE_DONE(Sound.THUNK, Haptic.CONFIRM),

    /** The pictogram thunks into its stamp. */
    STAMP(Sound.THUNK, Haptic.CONFIRM),

    /** The Next card rises. */
    NEXT_CARD(Sound.PON, null),

    /** The celebration starts. */
    CELEBRATION(Sound.JINGLE, null),

    /** PER-FECT! (scene and book). */
    PERFECT(Sound.THUNK, Haptic.CONFIRM),

    /** A capsule pops (AI results, jackpot, bonus). */
    CAPSULE_POP(Sound.PON, Haptic.CONFIRM),

    /** The cassette starts or stops. */
    CASSETTE(Sound.CLUNK, Haptic.KEYBOARD_TAP),

    /** Egg 4: one soft tick. */
    PETAL_SHAKE(Sound.KIRA, Haptic.CLOCK_TICK),

    /** Egg 15: the pet is fed. */
    PET_FED(Sound.BEEP, null),

    /** Egg 18: the receipt prints. */
    RECEIPT_PRINT(Sound.PRINT, Haptic.CLOCK_TICK),

    /** Egg 18: the receipt tears off. */
    RECEIPT_TEAR(Sound.TK, null),

    /** Pull to refresh is released. */
    PULL_REFRESH(Sound.PON, Haptic.CLOCK_TICK),

    /** Egg 10: the bonus-stage jingle (every 4 s). */
    BONUS_JINGLE(Sound.JINGLE, null),
}

/**
 * Plays a cue, gated by Surprises → Sounds (off by default; never in silent or vibrate) and Haptics (on by
 * default; the phone's own touch-feedback setting applies). Reduced motion does not silence it. Main-safe,
 * never blocks, never throws.
 */
interface Feedback {
    fun cue(cue: Cue)
}

/** Plays nothing. The phase-0 stand-in, and the switch-off behaviour. */
object NoOpFeedback : Feedback {
    override fun cue(cue: Cue) = Unit
}
