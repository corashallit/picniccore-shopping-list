package earth.cora.picnic.platform

/**
 * Android's timing settings (docs/DESIGN_STICKER.md §9, §15). Owner: WP10 (TimeoutsTest).
 * PHASE-0 CONTRACT; [DefaultTimeouts] is what the settings give at their defaults.
 */
interface Timeouts {
    /**
     * "Time to take action": AccessibilityManager.getRecommendedTimeoutMillis([holdMs], FLAG_CONTENT_TEXT,
     * plus FLAG_CONTENT_CONTROLS when the moment carries an action ([controls]): toasts with Undo or New trip,
     * the Next card). Returns [holdMs] unchanged at the default setting; never shorter than it.
     */
    fun recommended(holdMs: Long, controls: Boolean): Long

    /** "Touch & hold delay": every press-and-hold (LCD, peek) waits max(440 ms, ViewConfiguration.longPressTimeout). */
    val holdThresholdMs: Long
}

/** The defaults: holds unchanged, a 440 ms hold threshold. The phase-0 stand-in. */
object DefaultTimeouts : Timeouts {
    override fun recommended(holdMs: Long, controls: Boolean): Long = holdMs

    override val holdThresholdMs: Long = 440
}
