package earth.cora.picnic.platform

/**
 * Polite TalkBack announcements (docs/DESIGN_STICKER.md §15), de-duplicated within 1 s by the
 * implementation. Owner: WP10. PHASE-0 CONTRACT; [NoOpAnnouncer] stands in until WP10's lands.
 */
interface Announcer {
    fun polite(text: String)
}

/** Announces nothing. */
object NoOpAnnouncer : Announcer {
    override fun polite(text: String) = Unit
}
