package earth.cora.picnic.platform

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * Polite TalkBack announcements (docs/DESIGN_STICKER.md §15): short status lines spoken without moving focus,
 * never in place of a control's own state, and de-duplicated within 1 s. Owner: WP10 (AnnouncerTest).
 * CONTRACT: [Announcer]. [AndroidAnnouncer] is the phone's implementation; [NoOpAnnouncer] is for previews and
 * screenshots; tests record announcements with FakeAnnouncer (test sources).
 *
 * The §15 moments: "<Name> added to today's sticker book" / "peeled out of today's sticker book", "<Aisle> done",
 * "Next: aisle 5, Breakfast", "List complete! …", toasts (their words as they drop in, from the toast host, and
 * "Undone: …" once their Undo ran), loader labels (once), "Receipt saved to your sticker book" (its toast). A
 * check-off itself is NOT announced here: the row's checkbox state change says it.
 */
interface Announcer {
    /** Speaks [text] politely. Blank text, and the same text again within 1 s, are dropped. Never throws. */
    fun polite(text: String)
}

/** Announces nothing. For previews and screenshots. */
object NoOpAnnouncer : Announcer {
    override fun polite(text: String) = Unit
}

/**
 * The 1 s de-duplication of §15, shared by every [Announcer]: [admit] says whether [text] may be spoken now.
 * The same text (after trimming) is dropped when it was last ADMITTED less than [windowMs] ago; a dropped
 * repeat does not extend the window, so a text repeated every half second is still spoken once a second.
 * Different texts never block each other. Thread-safe.
 */
class AnnouncementDeduper(
    private val clock: () -> Long,
    private val windowMs: Long = DEDUP_WINDOW_MS,
) {
    private val lastAdmitted = LinkedHashMap<String, Long>()

    @Synchronized
    fun admit(text: String): Boolean {
        val key = text.trim()
        if (key.isEmpty()) return false
        val now = clock()
        // Forget texts whose window has passed (keeps the map to the last second's worth).
        lastAdmitted.entries.removeAll { now - it.value >= windowMs }
        if (key in lastAdmitted) return false
        lastAdmitted[key] = now
        return true
    }

    companion object {
        /** §15: "de-duplicated within 1 s". */
        const val DEDUP_WINDOW_MS: Long = 1000
    }
}

/**
 * The phone's [Announcer]: an announcement event through [AccessibilityManager], sent only while an
 * accessibility service is on, after [AnnouncementDeduper].
 *
 * Why an event and not a live region: these moments (a twin appearing in the book, an aisle done, the Next
 * card, the celebration) happen in whichever window is in front (the list, a sheet, the celebration overlay),
 * and an announcement event is spoken from any of them without moving focus or adding a stray node to the
 * screen. Android 16 deprecates announcement events to steer apps away from chatty, disruptive speech; ours are
 * the short, rare, polite lines of §15, never a substitute for a control's own state (the row's checkbox
 * state). A toast is one of them: a live region on a note that has only just appeared is not reliably read.
 */
class AndroidAnnouncer(
    context: Context,
    clock: () -> Long = SystemClock::elapsedRealtime,
) : Announcer {
    private val packageName = context.packageName
    private val accessibility: AccessibilityManager? = context.getSystemService(AccessibilityManager::class.java)
    private val deduper = AnnouncementDeduper(clock)

    override fun polite(text: String) {
        try {
            val manager = accessibility ?: return
            // Off: nothing is listening, and sending an event would throw on the main thread.
            if (!manager.isEnabled) return
            if (!deduper.admit(text)) return
            manager.sendAccessibilityEvent(announcement(text.trim()))
        } catch (e: RuntimeException) {
            PicnicLog.w("announcement failed", e)
        }
    }

    @Suppress("DEPRECATION") // TYPE_ANNOUNCEMENT (API 36) and obtain() (API 30): see the class comment.
    private fun announcement(text: String): AccessibilityEvent {
        val type = AccessibilityEvent.TYPE_ANNOUNCEMENT
        val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) AccessibilityEvent(type) else AccessibilityEvent.obtain(type)
        event.packageName = packageName
        event.className = View::class.java.name
        event.text.add(text)
        return event
    }
}
