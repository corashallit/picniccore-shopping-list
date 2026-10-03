package earth.cora.picnic.ui.list.motion

/*
 * The beat scheduler (docs/DESIGN_STICKER.md §8.1, §8.8). Every beat of every chain is a one-shot action at an
 * absolute frame-clock time. The engine advances it once per frame (withFrameMillis), so a beat fires on the first
 * frame at or after its time, under the fixed motion scale: Android's animation scales never move a beat or shorten
 * a hold. Pure and single-threaded (the main thread); Owner: WP6.
 */

/** One scheduled beat: [run] at [at] (frame-clock ms), in group [group] so a chain can cancel its pending beats. */
class ScheduledBeat internal constructor(val at: Long, val group: String, internal val seq: Long, internal val run: () -> Unit)

class Timeline {
    private val pending = ArrayList<ScheduledBeat>()
    private var seq = 0L

    /** Schedules [run] at [at] in [group]. Beats at the same time run in the order they were scheduled. */
    fun at(at: Long, group: String, run: () -> Unit): ScheduledBeat {
        val beat = ScheduledBeat(at, group, seq++, run)
        // Insertion keeps the list sorted by (at, seq); chains schedule a dozen beats, so a linear search is plenty.
        var i = pending.size
        while (i > 0 && pending[i - 1].at > at) i--
        pending.add(i, beat)
        return beat
    }

    /** Drops every pending beat of [group]. */
    fun cancel(group: String) {
        pending.removeAll { it.group == group }
    }

    /** Drops every pending beat whose group starts with [prefix]. */
    fun cancelPrefix(prefix: String) {
        pending.removeAll { it.group.startsWith(prefix) }
    }

    /** Drops one beat. */
    fun cancel(beat: ScheduledBeat) {
        pending.remove(beat)
    }

    /** True when [group] still has a beat to run. */
    fun has(group: String): Boolean = pending.any { it.group == group }

    /** When [group]'s next beat runs, or null. */
    fun nextIn(group: String): Long? = pending.firstOrNull { it.group == group }?.at

    /**
     * Runs every beat due at [now], in time order. A beat may schedule more beats; those that are already due run in
     * this same call (so a chain can never fall a frame behind its own schedule).
     */
    fun advanceTo(now: Long) {
        while (pending.isNotEmpty() && pending[0].at <= now) {
            val beat = pending.removeAt(0)
            beat.run()
        }
    }

    /** The earliest pending beat's time, or null. */
    val nextAt: Long? get() = pending.firstOrNull()?.at

    val isEmpty: Boolean get() = pending.isEmpty()

    /** Pending beats (tests). */
    val size: Int get() = pending.size

    fun clear() = pending.clear()
}
