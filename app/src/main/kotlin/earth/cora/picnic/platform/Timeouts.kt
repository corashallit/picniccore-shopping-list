package earth.cora.picnic.platform

import android.content.Context
import android.os.Build
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager

/**
 * Android's timing settings (docs/DESIGN_STICKER.md §9, §15). Owner: WP10 (TimeoutsTest).
 * CONTRACT: [Timeouts]. [AndroidTimeouts] reads the phone's settings on every call (they can change while the
 * app runs); [DefaultTimeouts] is what they give at their defaults (previews, screenshots); tests use
 * FakeTimeouts (test sources).
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

/** The shortest press-and-hold in the app (§4.3), ms. "Touch & hold delay" can only lengthen it. */
const val MIN_HOLD_THRESHOLD_MS: Long = 440

/** Egg 2's ladybug walks out this long after the hold threshold (§4.3), ms. */
const val LADYBUG_AFTER_HOLD_MS: Long = 560

/** When egg 2's ladybug fires while the LCD is held: the hold threshold + 560 ms (1 s by default, §4.3). */
val Timeouts.ladybugMs: Long get() = holdThresholdMs + LADYBUG_AFTER_HOLD_MS

/** The defaults: holds unchanged, a 440 ms hold threshold. For previews and screenshots. */
object DefaultTimeouts : Timeouts {
    override fun recommended(holdMs: Long, controls: Boolean): Long = holdMs

    override val holdThresholdMs: Long = MIN_HOLD_THRESHOLD_MS
}

/**
 * The phone's [Timeouts], read live: [recommended] asks [AccessibilityManager] (API 29+, where "Time to take
 * action" exists; earlier it returns the hold) and [holdThresholdMs] reads [longPressTimeoutMs]
 * (`ViewConfiguration.getLongPressTimeout()`, which follows "Touch & hold delay").
 */
class AndroidTimeouts(
    context: Context,
    private val longPressTimeoutMs: () -> Int = ViewConfiguration::getLongPressTimeout,
) : Timeouts {
    private val accessibility: AccessibilityManager? = context.getSystemService(AccessibilityManager::class.java)

    override fun recommended(holdMs: Long, controls: Boolean): Long {
        if (holdMs <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return holdMs
        val manager = accessibility ?: return holdMs
        val flags = AccessibilityManager.FLAG_CONTENT_TEXT or
            (if (controls) AccessibilityManager.FLAG_CONTENT_CONTROLS else 0)
        val asked = holdMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val recommended = try {
            manager.getRecommendedTimeoutMillis(asked, flags).toLong()
        } catch (e: RuntimeException) {
            PicnicLog.w("recommended timeout unavailable", e)
            holdMs
        }
        return maxOf(holdMs, recommended)
    }

    override val holdThresholdMs: Long
        get() = maxOf(MIN_HOLD_THRESHOLD_MS, longPressTimeoutMs().toLong())
}
