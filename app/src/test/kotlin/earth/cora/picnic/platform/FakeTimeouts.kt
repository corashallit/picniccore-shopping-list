package earth.cora.picnic.platform

/**
 * Android's timing settings as plain numbers (docs/DESIGN_STICKER.md §9, §15), with the platform's own rule:
 * a moment's hold becomes max(hold, [textTimeoutMs], and [actionTimeoutMs] when it carries an action). 0 means
 * "Time to take action" is at its default. [requests] records every [recommended] call so a test can check
 * which moments pass through it and with which flag. Shared by every package's tests (WP10 provides it).
 *
 * `FakeTimeouts(actionTimeoutMs = 10_000, textTimeoutMs = 10_000, holdThresholdMs = 1500)` is the phone of
 * TimeoutsTest: "Time to take action" at 10 s, "Touch & hold delay" at 1.5 s.
 */
class FakeTimeouts(
    var actionTimeoutMs: Long = 0,
    var textTimeoutMs: Long = 0,
    override var holdThresholdMs: Long = MIN_HOLD_THRESHOLD_MS,
) : Timeouts {
    private val calls = mutableListOf<Pair<Long, Boolean>>()

    /** Every [recommended] call: (holdMs, controls). */
    val requests: List<Pair<Long, Boolean>> get() = synchronized(calls) { calls.toList() }

    override fun recommended(holdMs: Long, controls: Boolean): Long {
        synchronized(calls) { calls += holdMs to controls }
        if (holdMs <= 0) return holdMs
        return maxOf(holdMs, textTimeoutMs, if (controls) actionTimeoutMs else 0)
    }
}
