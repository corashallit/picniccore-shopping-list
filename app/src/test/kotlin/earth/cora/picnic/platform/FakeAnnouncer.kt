package earth.cora.picnic.platform

/**
 * Records announcements instead of speaking them (docs/DESIGN_STICKER.md §15). [messages] is every call as
 * made; [spoken] is what the phone would say: blank text dropped and, when a [clock] is given, the same text
 * again within 1 s dropped (the production [AnnouncementDeduper]). Thread-safe. Shared by every package's tests
 * (WP10 provides it).
 */
class FakeAnnouncer(clock: (() -> Long)? = null) : Announcer {
    private val deduper = clock?.let { AnnouncementDeduper(it) }
    private val calls = mutableListOf<String>()
    private val said = mutableListOf<String>()

    /** Every [polite] call, as passed. */
    val messages: List<String> get() = synchronized(calls) { calls.toList() }

    /** What TalkBack would have spoken, trimmed. */
    val spoken: List<String> get() = synchronized(calls) { said.toList() }

    override fun polite(text: String) {
        synchronized(calls) {
            calls += text
            val admitted = deduper?.admit(text) ?: text.isNotBlank()
            if (admitted) said += text.trim()
        }
    }

    fun clear() = synchronized(calls) {
        calls.clear()
        said.clear()
    }
}
