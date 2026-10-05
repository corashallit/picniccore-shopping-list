package earth.cora.picnic.platform

/**
 * Records every cue instead of playing it (docs/DESIGN_STICKER.md §14.1: "a test can record cues"). Gating is
 * AndroidFeedback's job (FeedbackGateTest); a screen or engine test asserts which cues a moment asks for.
 * Thread-safe. Shared by every package's tests (WP10 provides it).
 */
class FakeFeedback : Feedback {
    private val recorded = mutableListOf<Cue>()

    /** Every cue so far, in order. */
    val cues: List<Cue> get() = synchronized(recorded) { recorded.toList() }

    override fun cue(cue: Cue) {
        synchronized(recorded) { recorded += cue }
    }

    /** How many times [cue] was asked for. */
    fun count(cue: Cue): Int = synchronized(recorded) { recorded.count { it == cue } }

    fun clear() = synchronized(recorded) { recorded.clear() }
}
