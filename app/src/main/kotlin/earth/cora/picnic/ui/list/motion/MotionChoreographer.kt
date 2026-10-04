package earth.cora.picnic.ui.list.motion

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.Offset
import earth.cora.picnic.core.catalog.Personality
import earth.cora.picnic.platform.Announcer
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.platform.DefaultTimeouts
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.platform.NoOpAnnouncer
import earth.cora.picnic.platform.NoOpFeedback
import earth.cora.picnic.platform.Timeouts
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.readHold
import earth.cora.picnic.ui.theme.words
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * The choreography engine (docs/DESIGN_STICKER.md §7.6, §8.3, §8.4, §8.8, §10, §11). Owner: WP6.
 *
 * Every chain is an object with its start time; the presentation at any moment is a pure function of the chains
 * alive and the frame-clock time, and the side effects (cues, announcements, events, the chains that follow) are
 * beats on a [Timeline]. The host runs [run] in a LaunchedEffect, which ticks once per frame (withFrameMillis) while
 * anything is in flight and sleeps otherwise, so the test clock idles and Android's animation scales never move a
 * beat or shorten a hold (§8.8). Tests may drive [tick] directly. Main thread only.
 */

/** The switches and the platform state the engine follows; the host keeps it current. */
data class MotionSettings(
    /** Surprises → "Next stop" card (§6.8). Off: no card; the next aisle still opens and is announced. */
    val nextStopCard: Boolean = true,
    /** Surprises → Celebration. Off: no scene; the book's PERFECT and the announcement still happen. */
    val celebration: Boolean = true,
    /** Surprises → Easter eggs: the LCD's "ready" wiggle after the finale (egg 18's hint). */
    val eggs: Boolean = true,
    /** Either Android animation scale at 0 (§8.8): every movement becomes a 120 ms fade at the same beat. */
    val reducedMotion: Boolean = false,
    /** TalkBack on: the Next card's hold doubles and the celebration waits for a tap (§10, §11.3). */
    val talkBack: Boolean = false,
    /** Display density (px per dp), for the hop's arc in window px. */
    val density: Float = 1f,
)

/** The book's paging (§7.4), so the book can turn to a twin's page before it appears (§7.6). */
data class BookPaging(val perPage: Int, val shownPage: Int) {
    fun pageOf(slot: Int): Int = if (perPage <= 0) 0 else slot / perPage
}

/** What the engine says out loud and the Next card's words (strings_motion.xml; [MotionStrings.from] reads them). */
data class MotionStrings(
    val next: String = "NEXT",
    val aisleDone: (String) -> String = { "$it done" },
    val nextStop: (MotionSection) -> String = { s ->
        when {
            s.position <= 0 -> "Next: ${spoken(s.fullName)}"
            s.storeAisle != null -> "Next: aisle ${s.position}, ${spoken(s.displayName)}, store aisle ${s.storeAisle}"
            else -> "Next: aisle ${s.position}, ${spoken(s.displayName)}"
        }
    },
    val listComplete: (stickers: Int, minutes: Int?) -> String = { n, m ->
        val stickers = if (n == 1) "1 sticker" else "$n stickers"
        if (m == null) "List complete! $stickers. Perfect!" else "List complete! $stickers in ${spokenMinutes(m)}. Perfect!"
    },
    val celebration: CelebrationCopy = CelebrationCopy.English,
) {
    companion object {
        val English: MotionStrings = MotionStrings()

        /** "&" reads as "and" (§15: "Next: aisle 7, Pasta, rice and international, store aisle 3"). */
        fun spoken(name: String): String = name.replace(" & ", " and ").replace("&", " and ")

        /** "41 minutes", "1 hour 5 minutes", "2 hours". */
        fun spokenMinutes(m: Int): String {
            val h = m / 60
            val r = m % 60
            val hours = if (h == 1) "1 hour" else "$h hours"
            val minutes = if (r == 1) "1 minute" else "$r minutes"
            return when {
                h == 0 -> minutes
                r == 0 -> hours
                else -> "$hours $minutes"
            }
        }
    }
}

/** What the engine tells WP9 (eggs) and WP10 (and the host that persists the finale). */
sealed interface MotionEvent {
    /** A sticker landed in its basket (egg 15's pet chomps). */
    data class Landed(val itemId: String, val sectionId: String) : MotionEvent

    /** A twin started to appear in the book. */
    data class MagicAppeared(val itemId: String, val slot: Int) : MotionEvent

    /** An aisle's chain reached its fold ("Baking done"). */
    data class AisleDone(val sectionId: String) : MotionEvent

    /** The next stop opened behind the Next card ([card] false when the card is switched off). */
    data class NextShown(val sectionId: String, val card: Boolean) : MotionEvent

    /** The trip's finale moment ([scene] false when the celebration is switched off or the finale was stale). */
    data class TripComplete(val listId: String?, val scene: Boolean) : MotionEvent

    /** The scene started: persist finaleShownAt and the bonus (FinaleKeeper). */
    data class CelebrationStarted(val listId: String, val bonus: BonusSticker?) : MotionEvent

    data class CelebrationClosed(val listId: String) : MotionEvent

    /** The finale is owed but plays no scene (switched off, or stale on resume): persist finaleShownAt only. */
    data class FinaleMarked(val listId: String?) : MotionEvent

    /** PER-FECT! was pressed across the book's spine. */
    data object BookPerfect : MotionEvent
}

/** One flight the overlay drew (FlightTest: only ever row ↔ its basket). */
data class FlightRecord(val itemId: String, val fromKey: String, val toKey: String, val back: Boolean)

/** The frame-clock time the motion pieces draw at (snapshot state: read it in draw or layer lambdas). */
@Stable
class MotionClock {
    var now: Long by mutableLongStateOf(0L)
        internal set
}

/** The engine. See the file header. */
class MotionChoreographer(
    private val feedback: Feedback = NoOpFeedback,
    private val announcer: Announcer = NoOpAnnouncer,
    private val timeouts: Timeouts = DefaultTimeouts,
    val anchors: AnchorRegistry = AnchorRegistry(),
) : Choreographer {
    var settings: MotionSettings = MotionSettings()
    var strings: MotionStrings = MotionStrings.English

    /** The current list (events and the finale's persistence name it). */
    var listId: String? = null

    /** What the celebration shows, read when it starts (WP5: CelebrationContent.from on the committed state). */
    var celebrationSource: (() -> CelebrationContent?)? = null

    /** The book's paging; null = one page (no page turns). */
    var bookPaging: BookPaging? = null

    private val sections = HashMap<String, MotionSection>()

    /** Tells the engine every section the list shows (the Next card's stop, the "done" announcement). */
    fun setSections(list: List<MotionSection>) {
        sections.clear()
        for (s in list) sections[s.id] = s
    }

    fun section(id: String): MotionSection? = sections[id]

    private val state = MutableStateFlow(ListPresentation.Identity)
    override val presentation: StateFlow<ListPresentation> = state.asStateFlow()

    /**
     * The presentation as snapshot state: hosts read this in composition. It changes inside the frame's tick, so the
     * frame that first shows a committed change (a check, an un-check) already shows its chain: no frame shows the new
     * state without its choreography, and the pieces' poses (read off [clock]) match it. The StateFlow follows it for
     * collectors outside composition (it reaches a collectAsState one frame later).
     */
    val current: androidx.compose.runtime.State<ListPresentation> get() = snapshot
    private val snapshot = androidx.compose.runtime.mutableStateOf(ListPresentation.Identity)

    /** The frame-clock time the pieces draw at. */
    val clock: MotionClock = MotionClock()

    private val eventFlow = MutableSharedFlow<MotionEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<MotionEvent> = eventFlow.asSharedFlow()

    /** Every event so far (tests), newest last, capped. */
    val eventLog: List<MotionEvent> get() = log.toList()
    private val log = ArrayDeque<MotionEvent>()

    /** Every flight so far (tests: FlightTest). */
    val flightLog: List<FlightRecord> get() = flightRecords.toList()
    private val flightRecords = ArrayList<FlightRecord>()

    private val timeline = Timeline()
    private val pending = ArrayDeque<(Long) -> Unit>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var now = 0L
    private var ticked = false

    // ---- The chains alive ----
    private val checks = LinkedHashMap<String, CheckChain>()
    private val unchecks = LinkedHashMap<String, UncheckChain>()
    private val aisleChains = LinkedHashMap<String, AisleChain>()
    private val reopens = LinkedHashMap<String, ReopenChain>()
    private var trip: TripChain? = null
    private var perfect: PerfectChain? = null
    private var nextCard: NextCardPresentation? = null
    private var celebration: CelebrationPresentation? = null
    private var scroll: ScrollRequest? = null
    private var burstSeq = 0

    // =============================================================================================
    // The Choreographer contract.
    // =============================================================================================

    override fun onToggle(event: ToggleEvent) = enqueue { t -> if (event.checked) startCheck(event, t) else startUncheck(event, t) }

    override fun onChange(change: ListChange) = enqueue { t -> startChange(change, t) }

    override fun skipNextCard() = enqueue { t -> exitNextCard(t) }

    override fun closeCelebration() = enqueue { t -> startClose(t) }

    override fun keepCelebration() = enqueue { t -> keep(t) }

    override fun onUserOpen(sectionId: String?) = enqueue { t -> userOpen(sectionId?.takeIf { it.isNotEmpty() }, t) }

    /**
     * Drops every chain and override at once: the list shows its committed state. The engine's owner calls it when the
     * list screen is really gone (its ViewModel is cleared) or another list is shown; never on a configuration change,
     * which only re-creates the composition (MotionEngineHost picks the chains up where they are).
     */
    fun finishAll() {
        pending.clear()
        timeline.clear()
        checks.clear()
        unchecks.clear()
        aisleChains.clear()
        reopens.clear()
        trip = null
        perfect = null
        nextCard = null
        queuedCard = null
        celebration = null
        scroll = null
        bookTurns.clear()
        sceneTripReopened = false
        snapshot.value = ListPresentation.Identity
        state.value = ListPresentation.Identity
    }

    private fun enqueue(start: (Long) -> Unit) {
        pending += start
        wake.trySend(Unit)
    }

    // =============================================================================================
    // The frame loop.
    // =============================================================================================

    /** Ticks once per frame while anything is in flight; sleeps otherwise. Run it in the host's LaunchedEffect. */
    suspend fun run() {
        while (true) {
            if (!busy()) wake.receive()
            withFrameMillis { tick(it) }
        }
    }

    /** True while a chain, a beat or a queued event needs frames. */
    fun busy(): Boolean = pending.isNotEmpty() || !timeline.isEmpty || checks.isNotEmpty() || unchecks.isNotEmpty() ||
        aisleChains.isNotEmpty() || reopens.isNotEmpty() || perfect != null || nextCard != null || queuedCard != null || scroll != null ||
        (trip != null) || bookTurns.isNotEmpty() || celebrationMoving()

    private fun celebrationMoving(): Boolean {
        val c = celebration ?: return false
        return c.closingT0 != null
    }

    /** Advances the engine to frame time [time]: starts queued events, runs due beats, publishes the presentation. */
    fun tick(time: Long) {
        val prev = now
        val wasTicked = ticked
        now = if (ticked) maxOf(now, time) else time
        ticked = true
        // Frames stopped while beats were due (the screen was away: a rotation, another screen, the app in the
        // background, with the engine kept by its ViewModel): what fell due meanwhile catches up silently, so coming
        // back never plays a burst of stale sounds or announcements. Events still go out (the finale is persisted).
        if (wasTicked && now - prev > CATCH_UP_GAP && !timeline.isEmpty) {
            quiet = true
            try {
                timeline.advanceTo(now - 1)
            } finally {
                quiet = false
            }
        }
        while (pending.isNotEmpty()) pending.removeFirst()(now)
        timeline.advanceTo(now)
        expire(now)
        clock.now = now
        val p = build(now)
        snapshot.value = p
        state.value = p
    }

    /** The engine's current frame time (tests). */
    val time: Long get() = now

    // =============================================================================================
    // The check-off chain (§8.3).
    // =============================================================================================

    private inner class CheckChain(
        val event: ToggleEvent,
        val t0: Long,
        val reduced: Boolean,
        /** A re-check while the sticker was hopping back: it flies to the basket from here (window px), no peel. */
        val resumeFrom: Offset?,
        val resumeScale: Float,
    ) {
        val id get() = event.itemId
        val section get() = event.sectionId
        val personality: Personality = if (resumeFrom != null) Personality.NONE else event.personality
        val hopAt: Long = if (resumeFrom != null) t0 else t0 + Beats.HOP
        val land: Long = hopAt + Beats.hopMillis(personality)
        /** A new sticker book (§0.4 Q9): the book turns to the fresh page at the tap; its twin then appears on that page. */
        val freshTurn: Boolean = event.newBook && !reduced

        /** The new book was undone before this twin appeared (§0.4 Q9): the book shows the old book; no twin here. */
        var bookMoot: Boolean = false
        val page: Int? = if (event.newBook) null else event.slot?.let { s -> bookPaging?.takeIf { it.pageOf(s) != it.shownPage }?.pageOf(s) }
        val turn: Boolean = page != null && !reduced
        val turnAt: Long = land + Beats.MAGIC_AFTER_LAND
        val magic: Long = turnAt + if (turn) Beats.PAGE_TURN else 0
        val full: Boolean = event.tripCompleted
        val glintAt: Long = magic + Beats.FULL_GLINT_AFTER_TWIN
        var flight: Flight? = null
        val burst: Burst?
        val endsAt: Long

        init {
            val center = anchors.bounds(AnchorRegistry.checkKey(id))?.center
            burst = if (center != null && !reduced && resumeFrom == null) Burst("burst-${burstSeq++}", center, t0, id.hashCode()) else null
            endsAt = maxOf(magic + Beats.TWIN, if (full) glintAt + PicnicMotion.glint.durationMillis else 0L, t0 + HankoBurst.DURATION, if (freshTurn) t0 + Beats.PAGE_TURN else 0L)
        }
    }

    private fun startCheck(e: ToggleEvent, t: Long) {
        val reduced = settings.reducedMotion
        // Rapid re-tap: a sticker hopping back is caught mid-air and flies to the basket again from where it is.
        val back = unchecks.remove(e.itemId)
        timeline.cancel(rowGroup(e.itemId))
        var resumeFrom: Offset? = null
        var resumeScale = 1f
        if (back != null) {
            val f = back.flight
            if (f != null && t < f.t0 + f.durationMs) {
                val frame = hopFrame(f, t)
                resumeFrom = frame.center
                resumeScale = frame.scaleX
            }
        }
        reopens.remove(e.sectionId)?.let { timeline.cancel(reopenGroup(e.sectionId)) }
        val chain = CheckChain(e, t, reduced, resumeFrom, resumeScale)
        checks[e.itemId] = chain
        val g = rowGroup(e.itemId)
        cue(Cue.HANKO)
        // The peel's "tk" sounds under reduced motion too: it silences movement, never sound or haptics (§14.3).
        if (resumeFrom == null) timeline.at(t + Beats.PEEL, g) { cue(Cue.PEEL) }
        // The fresh page's turn sounds like any page turn ("peel", §14.3), under reduced motion too (it silences movement only).
        if (e.newBook) cue(Cue.UNCHECK)
        timeline.at(chain.hopAt, g) { launchHop(chain) }
        timeline.at(chain.land, g) { land(chain) }
        if (chain.turn) timeline.at(chain.turnAt, g) { cue(Cue.UNCHECK) }
        timeline.at(chain.magic, g) {
            if (!chain.bookMoot) {
                cue(Cue.MAGIC_APPEAR)
                e.slot?.let { emit(MotionEvent.MagicAppeared(e.itemId, it)) }
            }
        }
        val origin = checkCentre(e.itemId)
        if (e.aisleCompleted) {
            val form = if (e.aisleWasOpen) AisleForm.Tap else AisleForm.Folded
            startAisleDone(e.sectionId, chain.land, form, e.nextSectionId, e.tripCompleted, e.finaleShown, origin, t, inPlace = reduced)
        } else if (e.tripCompleted) {
            // A trip completes with its last aisle; should an event say otherwise, the finale still follows the landing.
            startTrip(chain.land, Beats.CELEBRATION, e.finaleShown, origin, t)
        }
    }

    private fun checkCentre(itemId: String): Offset? = anchors.bounds(AnchorRegistry.checkKey(itemId))?.center

    private fun launchHop(chain: CheckChain) {
        if (chain.reduced) return
        val e = chain.event
        val rowKey = AnchorRegistry.rowKey(e.itemId)
        val basketKey = AnchorRegistry.basketKey(e.sectionId)
        val row = anchors.bounds(rowKey)
        val basket = anchors.bounds(basketKey)
        val mouth = anchors.mouth(basketKey)
        // Off-screen basket (not laid out): the chain lands in place, no hop (§8.3).
        if (basket == null || mouth == null) return
        val from = row ?: chain.resumeFrom?.let { androidx.compose.ui.geometry.Rect(it, androidx.compose.ui.geometry.Size(1f, 1f)) } ?: return
        val f = Flight(
            itemId = e.itemId,
            sectionId = e.sectionId,
            fromKey = rowKey,
            toKey = basketKey,
            from = from,
            to = basket,
            mouth = mouth,
            t0 = chain.hopAt,
            durationMs = chain.land - chain.hopAt,
            personality = chain.personality,
            back = false,
            icon = e.icon,
            holo = e.holo,
            aisleIndex = e.aisleIndex,
            startAt = chain.resumeFrom,
            startScale = if (chain.resumeFrom != null) chain.resumeScale else null,
        )
        chain.flight = f
        flightRecords += FlightRecord(e.itemId, rowKey, basketKey, back = false)
    }

    private fun land(chain: CheckChain) {
        chain.flight = null
        cue(Cue.LAND)
        cue(Cue.LCD_TICK)
        emit(MotionEvent.Landed(chain.id, chain.section))
    }

    // =============================================================================================
    // The un-check (§8.4).
    // =============================================================================================

    private inner class UncheckChain(
        val event: ToggleEvent,
        val t0: Long,
        val reduced: Boolean,
        /** The twin was in the book (or appearing): it peels off. */
        val peelTwin: Boolean,
        var flight: Flight?,
        /** The basket wobbles (the sticker leaves it). */
        val wobble: Boolean,
        /**
         * The sticker never left the row (un-checked before its hop, an accidental double tap): no flight, no ghost,
         * no badge tick (the badge never counted it down); only the hanko lifts.
         */
        val home: Boolean = false,
    ) {
        val endsAt: Long = t0 + maxOf(if (home) Beats.HANKO_LIFT else Beats.GHOST_UNTIL, if (peelTwin) Beats.PEEL_OFF else 0)
    }

    private fun startUncheck(e: ToggleEvent, t: Long) {
        val reduced = settings.reducedMotion
        val check = checks.remove(e.itemId)
        timeline.cancel(rowGroup(e.itemId))
        unchecks.remove(e.itemId)
        val rowKey = AnchorRegistry.rowKey(e.itemId)
        val basketKey = AnchorRegistry.basketKey(e.sectionId)
        val row = anchors.bounds(rowKey)
        // Where the sticker is now: still in the row (before the hop), mid-air, or in the basket.
        var startAt: Offset? = null
        var startScale = HopMotion.END_SCALE
        var inBasket = true
        var twinShown = e.slot != null
        if (check != null) {
            val cf = check.flight
            when {
                t < check.hopAt -> inBasket = false
                cf != null && t < check.land -> {
                    val frame = hopFrame(cf, t)
                    startAt = frame.center
                    startScale = frame.scaleX
                    inBasket = false
                }
                else -> Unit
            }
            twinShown = e.slot != null && t >= check.magic
        }
        val flyBack = !reduced && row != null && (inBasket || startAt != null) && (startAt != null || anchors.isLaidOut(basketKey))
        var flight: Flight? = null
        if (flyBack) {
            val mouth = startAt ?: anchors.mouth(basketKey)
            val basket = anchors.bounds(basketKey)
            if (mouth != null && row != null) {
                flight = Flight(
                    itemId = e.itemId,
                    sectionId = e.sectionId,
                    fromKey = basketKey,
                    toKey = rowKey,
                    from = row,
                    to = basket ?: row,
                    mouth = mouth,
                    t0 = t,
                    durationMs = Beats.HOP_BACK,
                    personality = Personality.NONE,
                    back = true,
                    icon = e.icon,
                    holo = e.holo,
                    aisleIndex = e.aisleIndex,
                    startAt = mouth,
                    startScale = startScale,
                )
                flightRecords += FlightRecord(e.itemId, basketKey, rowKey, back = true)
            }
        }
        // Still on the row: before its hop (under reduced motion, before its landing fade).
        val home = check != null && check.resumeFrom == null && (t < check.hopAt || (check.reduced && t < check.land))
        unchecks[e.itemId] = UncheckChain(e, t, reduced, peelTwin = twinShown, flight = flight, wobble = inBasket, home = home)
        cue(Cue.UNCHECK)
        if (e.aisleCompleted) {
            reopenAisle(e.sectionId, t)
            // Its aisle is the one to finish again (the list re-opens it): a card naming a later stop is moot.
            dropCardsAwayFrom(setOf(e.sectionId), t)
        }
        if (e.tripCompleted) reopenTrip(t)
    }

    // =============================================================================================
    // The aisle-done chain (§8.4) and its reversal.
    // =============================================================================================

    private enum class AisleForm {
        /** A check-off tap in the open tray: lid, stamp, curl, fold, then the Next card or the celebration. */
        Tap,

        /** The aisle was folded when it completed (rare): lid, stamp, then the card or celebration at L + 700. */
        Folded,

        /** A ListChange on the open aisle: 120 ms fades at the same beats, the fold at L + 1100 (no curl). */
        InPlaceOpen,

        /** A ListChange on a folded aisle: fades; the card at L + 1300 or the celebration at L + 1350, as for any ListChange. */
        InPlaceFolded,
    }

    private inner class AisleChain(
        val sectionId: String,
        val L: Long,
        val form: AisleForm,
        val next: String?,
        val trip: Boolean,
        val created: Long,
        val inPlace: Boolean,
    ) {
        val open: Boolean = form == AisleForm.Tap || form == AisleForm.InPlaceOpen
        val curl: Boolean = form == AisleForm.Tap && !inPlace

        /** The curl's "peel" sounds whenever a tap finished the open tray, also under reduced motion (§14.3). */
        val curlCue: Boolean = form == AisleForm.Tap

        /** Only a check-off tap on a folded aisle brings the card (or the scene) forward to L + 700 (§8.4). */
        val folded: Boolean = form == AisleForm.Folded
        val nextAt: Long = L + if (folded) Beats.FOLDED_NEXT else Beats.NEXT_CARD
        val tripAt: Long = L + if (folded) Beats.FOLDED_NEXT else Beats.CELEBRATION
        val endsAt: Long = if (open) L + Beats.BOW + 300 else L + Beats.STAMP + StampMotion.THUNK

        /**
         * She opened (or folded) a section herself at this time (§6.1): from then on the chain no longer holds the tray
         * open or the list folded, and a curl or fold not yet started does not play (her tap folded the tray already).
         */
        var userOpenAt: Long? = null

        /** The section she opened last (null: she folded everything). */
        var userSection: String? = null
    }

    private fun startAisleDone(
        sectionId: String,
        L: Long,
        form: AisleForm,
        next: String?,
        tripDone: Boolean,
        finaleShown: Boolean,
        origin: Offset?,
        created: Long,
        inPlace: Boolean,
    ) {
        timeline.cancel(aisleGroup(sectionId))
        reopens.remove(sectionId)
        val chain = AisleChain(sectionId, L, form, next, tripDone, created, inPlace || form == AisleForm.InPlaceOpen || form == AisleForm.InPlaceFolded)
        aisleChains[sectionId] = chain
        val g = aisleGroup(sectionId)
        timeline.at(L + Beats.LID, g) { cue(Cue.AISLE_DONE) }
        timeline.at(L + Beats.STAMP, g) { cue(Cue.STAMP) }
        if (chain.curlCue) timeline.at(L + Beats.CURL, g) { cue(Cue.UNCHECK) }
        if (chain.open) {
            timeline.at(L + Beats.FOLD, g) { aisleDone(sectionId) }
        } else {
            timeline.at(L + Beats.STAMP, g) { aisleDone(sectionId) }
        }
        if (tripDone) {
            startTrip(L, chain.tripAt - L, finaleShown, origin, created)
        } else if (next != null) {
            timeline.at(chain.nextAt, g) {
                // Her own open since the aisle finished wins (§6.1): the next stop opens behind its card only if she has
                // not opened another section; if she opened this very stop herself, the card still names it, unscrolled.
                when {
                    chain.userOpenAt == null -> openNext(next, chain.nextAt)
                    chain.userSection == next -> openNext(next, chain.nextAt, scrollTo = false)
                }
            }
        }
    }

    private fun aisleDone(sectionId: String) {
        val name = sections[sectionId]?.displayName
        if (name != null) say(strings.aisleDone(name))
        emit(MotionEvent.AisleDone(sectionId))
    }

    /** An aisle re-opens (un-check, delete, move, add): pending beats go, shown ones reverse in place (§8.3, §8.4). */
    private inner class ReopenChain(
        val sectionId: String,
        val t0: Long,
        val lidWasShut: Boolean,
        val hankoWasOn: Boolean,
        val ribbonWasTied: Boolean,
        val inPlace: Boolean,
    ) {
        val endsAt: Long = t0 + 160
    }

    private fun reopenAisle(sectionId: String, t: Long, inPlace: Boolean = settings.reducedMotion) {
        val chain = aisleChains.remove(sectionId)
        timeline.cancel(aisleGroup(sectionId))
        val lidShut: Boolean
        val hanko: Boolean
        val ribbon: Boolean
        if (chain != null) {
            lidShut = t >= chain.L + Beats.LID
            hanko = t >= chain.L + Beats.LID_HANKO
            ribbon = chain.open && t >= chain.L + Beats.RIBBON
            // The Next card this chain raised exits at once (or never rises, if it was waiting its turn).
            if (nextCard != null && chain.next != null && nextCard?.sectionId == chain.next && nextCard!!.t0 >= chain.nextAt) exitNextCard(t)
            if (chain.next != null && queuedCard?.sectionId == chain.next) {
                timeline.cancel(nextGroup(queuedCard!!.t0))
                queuedCard = null
            }
            if (scroll?.sectionId == chain.next) scroll = null
        } else {
            // A settled done aisle: lid shut with its GOT IT, ribbon tied.
            lidShut = true
            hanko = true
            ribbon = true
        }
        if (lidShut || hanko || ribbon) reopens[sectionId] = ReopenChain(sectionId, t, lidShut, hanko, ribbon, inPlace)
    }

    // =============================================================================================
    // The trip: the celebration, or PERFECT alone (§7.1, §11).
    // =============================================================================================

    private inner class TripChain(
        val L: Long,
        val at: Long,
        val finaleShown: Boolean,
        var origin: Offset?,
        val created: Long,
    )

    private fun startTrip(L: Long, offset: Long, finaleShown: Boolean, origin: Offset?, created: Long) {
        timeline.cancel(TRIP)
        sceneTripReopened = false
        val chain = TripChain(L, L + offset, finaleShown, origin, created)
        trip = chain
        timeline.at(chain.at, TRIP) { finale(chain) }
    }

    private fun finale(chain: TripChain) {
        val at = chain.at
        if (chain.finaleShown) {
            trip = null
            // A re-completion while the scene is still up: its close presses PERFECT (+450 ms), as for the first one.
            if (celebration?.closingT0 == null && celebration != null) return
            // A re-completion: PERFECT again at L + 1800, no scene (§7.1).
            pressPerfect(at + Beats.PERFECT_AFTER_CLOSE, inPlace = settings.reducedMotion)
            return
        }
        // What the scene shows; null when the list is gone or nothing is wired: then no scene, as when it is switched off
        // (an empty scene would only swallow her first tap and Back, §11.3).
        val content = celebrationSource?.invoke()
        if (settings.celebration && content != null) {
            startCelebration(at, chain.origin, content)
        } else {
            // Switched off: no scene; the announcement and PERFECT still happen (§11.3).
            trip = null
            announceComplete(content)
            emit(MotionEvent.TripComplete(content?.listId ?: listId, scene = false))
            emit(MotionEvent.FinaleMarked(content?.listId ?: listId))
            pressPerfect(at + Beats.PERFECT_AFTER_CLOSE, inPlace = settings.reducedMotion)
        }
    }

    /**
     * The trip stopped being complete (an un-check, a delete, a move, an add, a sync): the pending finale and a PERFECT
     * not pressed yet are cancelled, each on its own (§7.1: PERFECT only for a complete trip; §8.3). A scene already up
     * stays until she closes it, but its close then presses no PERFECT.
     */
    private fun reopenTrip(t: Long) {
        trip?.let {
            if (t < it.at) {
                trip = null
                timeline.cancel(TRIP)
            }
        }
        perfect?.let {
            if (t < it.t0) {
                perfect = null
                timeline.cancel(PERFECT)
            }
        }
        celebration?.let { if (it.closingT0 == null) sceneTripReopened = true }
    }

    /** The trip re-opened while the scene was up (and has not completed again since): its close presses no PERFECT. */
    private var sceneTripReopened = false

    private inner class PerfectChain(val t0: Long, val inPlace: Boolean) {
        val endsAt: Long = t0 + 260 + HankoBurst.DURATION + PicnicMotion.glint.durationMillis
    }

    private fun pressPerfect(at: Long, inPlace: Boolean) {
        timeline.cancel(PERFECT)
        perfect = PerfectChain(at, inPlace)
        timeline.at(at, PERFECT) {
            cue(Cue.PERFECT)
            emit(MotionEvent.BookPerfect)
        }
    }

    private fun announceComplete(content: CelebrationContent?) {
        val stats = content?.stats ?: return
        say(strings.listComplete(stats.stickers, stats.minutes))
    }

    private fun startCelebration(at: Long, origin: Offset?, content: CelebrationContent) {
        val id = content.listId
        val reduced = settings.reducedMotion
        val talkBack = settings.talkBack
        val plan = CelebrationTimeline.plan(content.listName, content.stats, content.petHatched, reduced, strings.celebration, content.total, closingBar = !talkBack)
        // The summary carries an action ("tap to keep it"): its hold follows "Time to take action" (§9, §15).
        val summaryHold = timeouts.recommended(plan.summaryHold, controls = true)
        val summarySettle = plan.beat(BeatName.Summary).settle ?: plan.summaryAt
        celebration = CelebrationPresentation(
            listId = id,
            t0 = at,
            content = content,
            plan = plan,
            origin = origin,
            reduced = reduced,
            talkBack = talkBack,
            summaryHoldMs = summaryHold,
        )
        trip = null
        sceneTripReopened = false
        cue(Cue.CELEBRATION)
        announceComplete(content)
        emit(MotionEvent.TripComplete(id, scene = true))
        emit(MotionEvent.CelebrationStarted(id, content.bonus))
        if (!reduced) {
            plan.beatOrNull(BeatName.Perfect)?.let { b -> timeline.at(at + b.at, CELEBRATION) { cue(Cue.PERFECT) } }
            plan.beatOrNull(BeatName.CapsulePop)?.let { b -> timeline.at(at + b.at, CELEBRATION) { cue(Cue.CAPSULE_POP) } }
            plan.beatOrNull(BeatName.Curl)?.let { b -> timeline.at(at + b.at, CELEBRATION) { cue(Cue.UNCHECK) } }
            plan.beatOrNull(BeatName.PocketMagic)?.let { b -> timeline.at(at + b.at, CELEBRATION) { cue(Cue.MAGIC_APPEAR) } }
        }
        // The summary closes by itself at the end of its hold, except with TalkBack on: then it waits for a tap.
        if (!talkBack) {
            val closeAt = at + summarySettle + summaryHold
            timeline.at(closeAt, CELEBRATION) { startClose(closeAt) }
        } else {
            // Keep the beats running to the summary so the scene settles, then sleep until a tap.
            timeline.at(at + plan.summaryAt, CELEBRATION) { }
        }
    }

    private fun keep(t: Long) {
        val c = celebration ?: return
        if (c.closingT0 != null || c.keptAt != null || c.talkBack) return
        val summaryAt = c.t0 + (c.plan?.summaryAt ?: 0)
        if (t < summaryAt) {
            // Before the summary, one tap anywhere closes it (§11.3).
            startClose(t)
            return
        }
        celebration = c.copy(keptAt = t)
        timeline.cancel(CELEBRATION)
    }

    private fun startClose(t: Long) {
        val c = celebration ?: return
        if (c.closingT0 != null) return
        timeline.cancel(CELEBRATION)
        celebration = c.copy(closingT0 = t)
        val fade = if (c.reduced) Beats.RM_FADE else Beats.CELEBRATION_FADE
        timeline.at(t + fade, CELEBRATION) {
            celebration = null
            emit(MotionEvent.CelebrationClosed(c.listId))
        }
        // PERFECT only for a complete trip (§7.1): not when it re-opened while the scene was up.
        if (!sceneTripReopened) pressPerfect(t + Beats.PERFECT_AFTER_CLOSE, inPlace = settings.reducedMotion)
        sceneTripReopened = false
    }

    // =============================================================================================
    // Her own open (§6.1): it always wins over an aisle-done chain.
    // =============================================================================================

    private fun userOpen(sectionId: String?, t: Long) {
        for (a in aisleChains.values) {
            if (a.userOpenAt == null) a.userOpenAt = t
            a.userSection = sectionId
        }
        // No programmatic scroll away from what she opened.
        if (scroll != null && scroll?.sectionId != sectionId) {
            scroll = null
            timeline.cancel(SCROLL)
        }
        // A card naming another aisle leaves (or never rises): it would point away from the aisle she chose.
        queuedCard?.let {
            if (it.sectionId != sectionId) {
                timeline.cancel(nextGroup(it.t0))
                queuedCard = null
            }
        }
        nextCard?.let { if (it.sectionId != sectionId) exitNextCard(t) }
    }

    // =============================================================================================
    // The Next card (§10).
    // =============================================================================================

    private fun openNext(next: String, at: Long, scrollTo: Boolean = true) {
        if (scrollTo) {
            scroll = ScrollRequest(next, at)
            timeline.at(at + SCROLL_HOLD, SCROLL) { if (scroll?.t0 == at) scroll = null }
        }
        val stop = sections[next]
        if (stop != null) say(strings.nextStop(stop))
        if (!settings.nextStopCard || stop == null) {
            emit(MotionEvent.NextShown(next, card = false))
            return
        }
        // Another aisle finished while a card is up (she checks Breakfast's last item while "NEXT 5 Breakfast" holds):
        // that card leaves now, and the new one rises once it has gone, so each card gets its full hold and the two
        // never share the screen. A card still waiting to rise is replaced.
        queuedCard?.let { timeline.cancel(nextGroup(it.t0)) }
        queuedCard = null
        val up = nextCard
        val riseAt = if (up == null) at else {
            exitNextCard(at)
            val leaving = nextCard!!
            leaving.exitT0!! + if (leaving.reduced) Beats.RM_FADE else NextCardMotion.EXIT
        }
        val reduced = settings.reducedMotion
        val hold = nextHold(stop)
        val settled = riseAt + if (reduced) Beats.RM_FADE else NextCardMotion.RISE
        val card = NextCardPresentation(next, hold, riseAt, stop, settledAt = settled, exitT0 = null, reduced = reduced)
        if (riseAt <= at) {
            showCard(card)
        } else {
            queuedCard = card
            timeline.at(riseAt, nextGroup(riseAt)) {
                if (queuedCard?.t0 == riseAt) {
                    queuedCard = null
                    showCard(card)
                }
            }
        }
    }

    /** The card rises ("pon!") and holds; its scheduled exit only ever acts on this card. */
    private fun showCard(card: NextCardPresentation) {
        nextCard = card
        cue(Cue.NEXT_CARD)
        emit(MotionEvent.NextShown(card.sectionId, card = true))
        val exitAt = card.settledAt + card.holdMs
        timeline.at(exitAt, nextGroup(card.t0)) { if (nextCard?.t0 == card.t0) exitNextCard(exitAt) }
    }

    /** The Next card's still hold: readHold(words("NEXT 5 Breakfast"), 2600), doubled with TalkBack, then the recommended timeout. */
    fun nextHold(stop: MotionSection): Long {
        val text = buildString {
            append(strings.next)
            if (stop.position > 0) append(' ').append(stop.position)
            append(' ').append(stop.displayName)
            stop.storeAisle?.let { append(" A").append(it) }
        }
        var hold = readHold(words(text), PicnicMotion.floorCard)
        if (settings.talkBack) hold *= 2
        return timeouts.recommended(hold, controls = true)
    }

    private fun exitNextCard(t: Long) {
        val card = nextCard ?: return
        if (card.exitT0 != null) return
        timeline.cancel(nextGroup(card.t0))
        nextCard = card.copy(exitT0 = t)
        val out = if (card.reduced) Beats.RM_FADE else NextCardMotion.EXIT
        timeline.at(t + out, nextGroup(card.t0)) { if (nextCard?.t0 == card.t0) nextCard = null }
    }

    /** A card waiting for the one before it to leave (two aisles done in a row). */
    private var queuedCard: NextCardPresentation? = null

    /**
     * An aisle in [reopened] has something to get again and opens (§8.4): a Next card pointing anywhere else exits at
     * once (300 ms, or the 120 ms fade under reduced motion), a card still waiting to rise never does, and a scroll to
     * that stop is dropped, whenever the re-opening comes, during the chain or long after it (item 176, §8.3).
     */
    private fun dropCardsAwayFrom(reopened: Set<String>, t: Long) {
        nextCard?.let { if (it.sectionId !in reopened) exitNextCard(t) }
        queuedCard?.let {
            if (it.sectionId !in reopened) {
                timeline.cancel(nextGroup(it.t0))
                queuedCard = null
            }
        }
        scroll?.let {
            if (it.itemId == null && it.sectionId !in reopened) {
                scroll = null
                timeline.cancel(SCROLL)
            }
        }
    }

    /** The list brings [itemId] (in [sectionId]) into view, clear of the plate (a row an Undo brought back, §8.4). */
    private fun reveal(sectionId: String, itemId: String, at: Long) {
        scroll = ScrollRequest(sectionId, at, itemId)
        timeline.at(at + SCROLL_HOLD, REVEAL) { if (scroll?.t0 == at && scroll?.itemId == itemId) scroll = null }
    }

    // =============================================================================================
    // ListChange: the in-place form (§8.4).
    // =============================================================================================

    private fun startChange(c: ListChange, t: Long) {
        if (c.cause == ChangeCause.NEW_BOOK_UNDO) startBookTurnBack(c.slot, t)
        for (s in c.aisleReopened) reopenAisle(s, t, inPlace = true)
        // A re-opened aisle is current again: a card pointing past it goes, also after the chain has played out.
        if (c.aisleReopened.isNotEmpty() && c.aisleCompleted.isEmpty()) dropCardsAwayFrom(c.aisleReopened, t)
        if (c.revealItemId != null && c.revealSectionId != null) reveal(c.revealSectionId, c.revealItemId, t)
        if (c.tripReopened) reopenTrip(t)
        if (c.finaleStale) {
            // Owed but too old to play (§7.1): marked shown, PERFECT pressed in place.
            emit(MotionEvent.FinaleMarked(listId))
            pressPerfect(t, inPlace = true)
            return
        }
        val done = c.aisleCompleted.toList()
        val lead = done.firstOrNull { it == c.openSectionId } ?: done.firstOrNull()
        for (s in done) {
            val form = if (s == c.openSectionId) AisleForm.InPlaceOpen else AisleForm.InPlaceFolded
            val isLead = s == lead
            startAisleDone(
                sectionId = s,
                L = t,
                form = form,
                next = if (isLead && !c.tripCompleted) c.nextSectionId else null,
                tripDone = false,
                finaleShown = c.finaleShown,
                origin = null,
                created = t,
                inPlace = true,
            )
        }
        // The in-place form has no folded exception (§8.4): the celebration at L + 1350 whichever aisle finished it.
        if (c.tripCompleted) startTrip(t, Beats.CELEBRATION, c.finaleShown, origin = null, created = t)
    }

    // =============================================================================================
    // The automatic new book's Undo (§0.4 Q9, §7.6).
    // =============================================================================================

    /**
     * Undo on "New sticker book for today": the old book shows again with a page turn back (3 frames, 240 ms), and the
     * tapped item's twin fades into its old spot [slot] (120 ms) once the turn has shown the page. Reduced motion: no
     * turn; the twin fades in at once.
     */
    private inner class BookTurnBack(val t0: Long, val slot: Int?, val reduced: Boolean) {
        val turn: Boolean = !reduced
        val fadeAt: Long = t0 + if (turn) Beats.PAGE_TURN else 0
        val endsAt: Long = fadeAt + Beats.RM_FADE
    }

    private val bookTurns = ArrayList<BookTurnBack>()

    private fun startBookTurnBack(slot: Int?, t: Long) {
        // A check chain still waiting to show its twin in the fresh book is moot now: the item is in the old book.
        for (c in checks.values) if (c.event.newBook) c.bookMoot = true
        bookTurns.clear()
        val chain = BookTurnBack(t, slot, settings.reducedMotion)
        bookTurns += chain
        cue(Cue.UNCHECK)
    }

    // =============================================================================================
    // Expiry and the presentation.
    // =============================================================================================

    private fun expire(t: Long) {
        checks.values.removeAll { t >= it.endsAt }
        unchecks.values.removeAll { t >= it.endsAt }
        aisleChains.values.removeAll { t >= it.endsAt && !timeline.has(aisleGroup(it.sectionId)) }
        reopens.values.removeAll { t >= it.endsAt }
        bookTurns.removeAll { t >= it.endsAt }
        perfect?.let { if (t >= it.endsAt) perfect = null }
    }

    private fun hopFrame(f: Flight, t: Long): HopFrame {
        val d = settings.density
        return if (f.back) {
            HopMotion.back(f.startAt ?: f.mouth, liveCenter(f.toKey) ?: f.from.center, t - f.t0, f.startScale ?: HopMotion.END_SCALE, d)
        } else {
            HopMotion.forward(f.startAt ?: liveCenter(f.fromKey) ?: f.from.center, anchors.mouth(f.toKey) ?: f.mouth, t - f.t0, f.personality, d)
        }
    }

    private fun liveCenter(key: String): Offset? = anchors.bounds(key)?.center

    /** Where a flight's sticker is at [t] (window px), for the overlay and tests. */
    fun flightFrame(f: Flight, t: Long = now): HopFrame = hopFrame(f, t)

    private fun build(t: Long): ListPresentation {
        val rows = HashMap<String, RowPhase>()
        val rowStarts = HashMap<String, Long>()
        val flights = ArrayList<Flight>()
        val bursts = ArrayList<Burst>()
        val baskets = HashMap<String, BasketAcc>()
        val spots = HashMap<Int, SpotPhase>()
        val stamps = HashMap<String, StampPhase>()
        val aisles = HashMap<String, AislePresentation>()
        var open: OpenOverride? = null
        var holdNext: Int? = null
        var pageTurn: PageTurn? = null
        var showPage: Int? = null
        var liftT0: Long? = null
        var fullHeld = false
        var fullGlint: Long? = null
        var lcdLag = 0
        fun basket(id: String) = baskets.getOrPut(id) { BasketAcc() }

        for (c in checks.values) {
            val e = c.event
            val since = t - c.t0
            // The row: the hanko slams (its pose follows rowStarts whatever the phase), the sticker peels, then hops.
            val phase = when {
                c.reduced -> when {
                    t < c.land -> RowPhase.Slam
                    t < c.land + Beats.RM_FADE -> RowPhase.Away
                    else -> null
                }
                c.resumeFrom != null -> if (t < c.land) RowPhase.Away else null
                since < Beats.PEEL -> RowPhase.Slam
                since < Beats.HOP -> RowPhase.Peeling
                t < c.land -> RowPhase.Away
                else -> null
            }
            if (phase != null) {
                rows[e.itemId] = phase
                // Under reduced motion, Away is the 120 ms fade from sticker to ghost at the landing.
                rowStarts[e.itemId] = if (c.reduced && phase == RowPhase.Away) c.land else c.t0
            }
            // The flight and the burst.
            c.flight?.let { if (t >= it.t0 && t < it.t0 + it.durationMs) flights += it }
            c.burst?.let { if (since < HankoBurst.DURATION) bursts += it }
            // The basket: the sticker is not in it until it lands; then it squashes, the basket wobbles, a heart rises.
            val b = basket(e.sectionId)
            if (t < c.land) {
                b.hidden += e.itemId
                b.toGetLag += 1
                lcdLag += 1
            } else {
                val l = c.land
                if (c.reduced) {
                    b.inPlace = true
                    if (t < l + Beats.RM_FADE) b.squash(e.itemId, l)
                } else {
                    if (t < l + 160) b.squash(e.itemId, l)
                    if (t < l + BasketMotion.WOBBLE) b.wobble(l)
                    if (t < l + 600) b.heart(l)
                }
                if (t < l + 300) b.badgePop(l)
            }
            // A new sticker book (§0.4 Q9): the book turns to the fresh page at the tap (the old twins leave with it).
            if (c.freshTurn && !c.bookMoot && t < c.t0 + Beats.PAGE_TURN) {
                val fresh = e.slot?.let { s -> bookPaging?.pageOf(s) } ?: 0
                pageTurn = PageTurn(fresh, c.t0)
                showPage = fresh
            }
            // The book: the spot waits empty (still the holo "next") until the magic, then the twin appears.
            val slot = if (c.bookMoot) null else e.slot
            if (slot != null) {
                if (t < c.magic) {
                    spots[slot] = SpotPhase.Empty
                    holdNext = minOf(holdNext ?: slot, slot)
                    if (c.turn && t >= c.turnAt) {
                        pageTurn = PageTurn(c.page!!, c.turnAt)
                        showPage = c.page
                    }
                } else if (t < c.magic + Beats.TWIN) {
                    spots[slot] = SpotPhase.Appearing(c.magic)
                    if (c.page != null) showPage = c.page
                    if (!c.reduced && t < c.magic + 160) liftT0 = maxOf(liftT0 ?: 0, c.magic)
                }
            }
            if (c.full) {
                if (t < c.glintAt) fullHeld = true
                if (!c.reduced && t >= c.glintAt && t < c.glintAt + PicnicMotion.glint.durationMillis) fullGlint = c.glintAt
            }
        }

        for (u in unchecks.values) {
            val e = u.event
            val since = t - u.t0
            if (u.home) {
                // The sticker never left: it stays on the row while the hanko lifts off it.
                if (since < (if (u.reduced) Beats.RM_FADE else Beats.HANKO_LIFT)) {
                    rows[e.itemId] = RowPhase.Lifting
                    rowStarts[e.itemId] = u.t0
                }
            } else if (since < (if (u.reduced) Beats.RM_FADE else Beats.GHOST_UNTIL)) {
                rows[e.itemId] = RowPhase.Landing
                rowStarts[e.itemId] = u.t0
            }
            // The copy stays on the row (its last pose) until the row's own sticker returns, so no frame shows neither.
            u.flight?.let { if (since < maxOf(it.durationMs, Beats.GHOST_UNTIL)) flights += it }
            val b = basket(e.sectionId)
            if (u.reduced) b.inPlace = true
            if (u.wobble && !u.reduced && since < BasketMotion.WOBBLE) b.wobble(u.t0)
            if (!u.home && since < 300) b.badgePop(u.t0)
            val slot = e.slot
            if (slot != null && u.peelTwin && since < (if (u.reduced) Beats.RM_FADE else Beats.PEEL_OFF)) {
                spots[slot] = SpotPhase.Peeling(u.t0, e.icon, e.holo)
                bookPaging?.let { p -> if (p.pageOf(slot) != p.shownPage) showPage = p.pageOf(slot) }
            }
        }

        for (a in aisleChains.values) {
            val L = a.L
            val b = basket(a.sectionId)
            if (a.inPlace) b.inPlace = true
            // The lid and its GOT IT: open until L + 300.
            if (t < L + Beats.LID) {
                b.lidClosed = false
                b.minisHidden = false
            } else {
                b.lidClosed = true
                if (t < L + Beats.LID + 160) b.lidT0 = L + Beats.LID
                val hankoAt = if (a.inPlace) L + Beats.LID else L + Beats.LID_HANKO
                if (t < hankoAt) b.lidHankoT0 = null else if (t < hankoAt + 200) b.lidHankoT0 = hankoAt
                b.lidHankoHidden = t < hankoAt
                if (!a.inPlace && t < L + Beats.LID + 360) b.hopT0 = L + Beats.LID
                // The minis stay under the shutting lid until it has landed (240 ms), then the state hides them.
                if (t < L + (if (a.inPlace) Beats.LID + Beats.RM_FADE else Beats.MINIS_HIDE)) b.minisHidden = false
            }
            // The stamp: the "now" ring until L + 500, then the pictogram thunks in; the ring moves on.
            val stampAt = L + Beats.STAMP
            when {
                t < stampAt -> stamps[a.sectionId] = StampPhase.Now
                t < stampAt + StampMotion.THUNK -> stamps[a.sectionId] = StampPhase.Thunking(stampAt, inPlace = a.inPlace)
            }
            if (a.next != null && t < stampAt) stamps.putIfAbsent(a.next, StampPhase.Waiting)
            // The tray: open until the fold, curls at L + 800, folds at L + 1100; the next opens at L + 1300.
            if (a.open) {
                // Her own open (§6.1) ends the chain's hold on which section shows open, at once.
                val mine = a.userOpenAt
                if (mine == null) {
                    when {
                        t < L + Beats.FOLD -> open = OpenOverride(a.sectionId)
                        t < a.nextAt && (a.next != null || a.trip) -> open = OpenOverride(null)
                    }
                }
                var ap = AislePresentation(inPlace = a.inPlace)
                // A curl or fold that had not started when she opened a section herself does not play: her tap folded
                // the tray already (one already under way finishes).
                val curlAt = L + Beats.CURL
                val foldAt = L + Beats.FOLD
                if (a.curl && t >= curlAt && t < foldAt && (mine == null || mine > curlAt)) ap = ap.copy(curlT0 = curlAt)
                if (t >= foldAt && t < foldAt + FoldMotion.DURATION && (mine == null || mine > foldAt)) ap = ap.copy(foldT0 = foldAt)
                ap = when {
                    t < L + Beats.RIBBON -> ap.copy(ribbon = false)
                    t < L + Beats.BOW + 300 -> ap.copy(ribbon = true, ribbonT0 = L + Beats.RIBBON, bowT0 = L + Beats.BOW)
                    else -> ap
                }
                aisles[a.sectionId] = ap
            } else if (t < L + Beats.LID) {
                aisles[a.sectionId] = AislePresentation(ribbon = false, inPlace = a.inPlace)
            } else if (t < L + Beats.LID + 300) {
                aisles[a.sectionId] = AislePresentation(ribbon = true, ribbonT0 = L + Beats.LID, bowT0 = L + Beats.LID + 100, inPlace = a.inPlace)
            }
        }

        for (b in bookTurns) {
            val slot = b.slot
            val page = slot?.let { s -> bookPaging?.pageOf(s) } ?: 0
            if (b.turn && t < b.t0 + Beats.PAGE_TURN) {
                pageTurn = PageTurn(page, b.t0)
                showPage = page
            }
            if (slot != null) {
                if (t < b.fadeAt) {
                    spots[slot] = SpotPhase.Empty
                    holdNext = minOf(holdNext ?: slot, slot)
                } else {
                    spots[slot] = SpotPhase.Appearing(b.fadeAt, inPlace = true)
                    if (bookPaging?.let { it.pageOf(slot) != it.shownPage } == true) showPage = page
                }
            }
        }

        for (r in reopens.values) {
            val b = basket(r.sectionId)
            if (r.inPlace) b.inPlace = true
            if (r.lidWasShut) {
                b.lidClosed = false
                b.lidT0 = r.t0
            }
            if (r.hankoWasOn) b.lidHankoLiftT0 = r.t0
            if (r.ribbonWasTied) aisles[r.sectionId] = AislePresentation(ribbon = false, untieT0 = r.t0, inPlace = r.inPlace)
        }

        // The trip: PERFECT waits for the celebration to close (+450 ms) or L + 1800.
        val perfectChain = perfect
        val perfectHeld = trip != null || celebration != null || (perfectChain != null && t < perfectChain.t0)
        val perfectT0 = perfectChain?.takeIf { t >= it.t0 }?.t0

        return ListPresentation(
            rows = rows,
            baskets = baskets.mapValues { it.value.toPresentation() },
            spots = spots,
            stamps = stamps,
            lcd = null,
            nextCard = nextCard,
            celebration = celebration,
            rowStarts = rowStarts,
            flights = flights,
            bursts = bursts,
            aisles = aisles,
            open = open,
            scroll = scroll,
            bookFx = BookFx(
                holdNext = holdNext,
                pageTurn = pageTurn,
                showPage = showPage,
                liftT0 = liftT0,
                fullHeld = fullHeld,
                fullGlintT0 = fullGlint,
                perfectHeld = perfectHeld,
                perfectT0 = perfectT0,
            ),
            lcdLag = lcdLag,
            lcdWiggleT0 = if (settings.eggs && perfectT0 != null) perfectT0 else null,
        )
    }

    /** One basket's overrides, accumulated over the chains touching it. */
    private class BasketAcc {
        val hidden = LinkedHashSet<String>()
        var toGetLag = 0
        var squashItem: String? = null
        var squashT0: Long? = null
        var wobbleT0: Long? = null
        var heartT0: Long? = null
        var badgePopT0: Long? = null
        var lidClosed: Boolean? = null
        var lidT0: Long? = null
        var lidHankoT0: Long? = null
        var lidHankoHidden = false
        var lidHankoLiftT0: Long? = null
        var hopT0: Long? = null
        var minisHidden: Boolean? = null
        var inPlace = false

        fun squash(item: String, t0: Long) {
            if (squashT0 == null || t0 >= squashT0!!) {
                squashItem = item
                squashT0 = t0
            }
        }

        fun wobble(t0: Long) {
            wobbleT0 = maxOf(wobbleT0 ?: t0, t0)
        }

        fun heart(t0: Long) {
            heartT0 = maxOf(heartT0 ?: t0, t0)
        }

        fun badgePop(t0: Long) {
            badgePopT0 = maxOf(badgePopT0 ?: t0, t0)
        }

        fun toPresentation(): BasketPresentation = BasketPresentation(
            lidClosed = lidClosed,
            hiddenMinis = hidden,
            toGetLag = toGetLag,
            squashItem = squashItem,
            squashT0 = squashT0,
            wobbleT0 = wobbleT0,
            heartT0 = heartT0,
            lidT0 = lidT0,
            lidHankoT0 = lidHankoT0,
            lidHankoLiftT0 = lidHankoLiftT0,
            hopT0 = hopT0,
            minisHidden = minisHidden ?: if (lidClosed == false) false else null,
            badgePopT0 = badgePopT0,
            inPlace = inPlace,
            lidHankoHidden = lidHankoHidden,
        )
    }

    /** True while stale beats catch up after the frames stopped (see [tick]). */
    private var quiet = false

    private fun cue(c: Cue) {
        if (!quiet) feedback.cue(c)
    }

    private fun say(text: String) {
        if (!quiet) announcer.polite(text)
    }

    private fun emit(event: MotionEvent) {
        log.addLast(event)
        while (log.size > 256) log.removeFirst()
        eventFlow.tryEmit(event)
    }

    private fun rowGroup(itemId: String) = "row:$itemId"
    private fun aisleGroup(sectionId: String) = "aisle:$sectionId"
    private fun reopenGroup(sectionId: String) = "reopen:$sectionId"
    private fun nextGroup(t0: Long) = "next:$t0"

    private companion object {
        const val TRIP = "trip"
        const val PERFECT = "perfect"
        const val CELEBRATION = "celebration"
        const val SCROLL = "scroll"
        const val REVEAL = "reveal"

        /** A scroll request stays in the presentation this long (the list runs each request once). */
        const val SCROLL_HOLD: Long = 1200

        /** A gap between frames longer than this, with beats due, means the frames stopped (see [tick]). */
        const val CATCH_UP_GAP: Long = 500
    }
}
