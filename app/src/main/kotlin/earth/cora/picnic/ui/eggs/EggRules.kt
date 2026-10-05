package earth.cora.picnic.ui.eggs

import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.core.calendar.Seasons
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AisleSection
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.GroupedList
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.core.store.TripBook
import earth.cora.picnic.core.store.unlockSheet
import java.text.Normalizer
import java.time.LocalDate
import java.time.Month
import java.util.Locale

/*
 * The eggs' exact triggers (docs/DESIGN_STICKER.md §12.2), pure and unit-tested (SurpriseEngineTest, Egg*Test).
 * The engine (AppSurpriseEngine) applies them to the state, the clock and its Random. Owner: WP9.
 */
object EggRules {
    // ---- Odds (§12.2) ----

    /** Egg 6: a committed item is shiny 1 time in 200. */
    const val SHINY_ODDS: Int = 200

    /** Egg 13: a finished live drawing comes out gold 1 time in 100. */
    const val JACKPOT_ODDS: Int = 100

    // ---- Egg 1: Dusk binder (one tap on the sun sets it) ----

    /** The sun sinks (3 poses, 600 ms), then the moon rises (3 poses, 600 ms) with its sparkle burst. */
    const val DUSK_TRANSITION_MS: Long = 1200
    const val DUSK_SINK_MS: Long = 600

    // ---- Egg 2: ladybug ----

    /** The ladybug walks out this long after the hold threshold (§4.3). */
    const val LADYBUG_AFTER_THRESHOLD_MS: Long = 560

    // ---- Egg 4: petals ----

    /** Two acceleration peaks over 2.7 g within 500 ms; at most once per 5.2 s. */
    const val SHAKE_PEAK_G: Float = 2.7f
    const val SHAKE_WINDOW_MS: Long = 500
    const val PETAL_COOLDOWN_MS: Long = 5200

    // ---- Egg 7: the magic word ----

    /**
     * "kirakira", "kira kira", "kira-kira", "キラキラ" or "きらきら", case- and space-insensitive, a trailing "!" (or
     * several) allowed. Nothing else: "kirakira milk" is an ordinary note.
     */
    fun isMagicWord(text: String): Boolean {
        val t = Normalizer.normalize(text, Normalizer.Form.NFKC).trim().trimEnd('!', '！').trim()
        if (t.isEmpty()) return false
        val folded = t.lowercase(Locale.ROOT).filterNot { it.isWhitespace() }
        return folded == "kirakira" || folded == "kira-kira" || folded == "キラキラ" || folded == "きらきら"
    }

    /** The title stays holo this long (§12.2 egg 7). */
    const val KIRAKIRA_MS: Long = 5000

    // ---- Egg 3: rolling produce ----

    /** How a committed item travels (egg 3): [Roll] for `roll`-tagged keys, [Hop] for `hop`-tagged berries. */
    enum class Travel { Roll, Hop }

    /** The item's travel by its icon key's catalog tags (so "fraises" and "pommes" match by key), or null. */
    fun travelOf(item: GroceryItem, catalog: IconCatalog): Travel? {
        val tags = catalog.tagsOf(item.icon)
        return when {
            "roll" in tags -> Travel.Roll
            "hop" in tags -> Travel.Hop
            else -> null
        }
    }

    /** One per commit: the first committed item that rolls or hops. */
    fun roller(items: List<GroceryItem>, catalog: IconCatalog): GroceryItem? = items.firstOrNull { travelOf(it, catalog) != null }

    // ---- Egg 9: birthday ----

    private val BIRTHDAY_WORDS = listOf("birthday", "b-day", "bday", "anniversaire")
    private val PLAIN_CAKES = setOf("cake", "cakes", "gateau", "gateaux")

    /**
     * A birthday item (egg 9): a name containing "birthday", "b-day", "bday" or "anniversaire" (accents and case
     * folded: "bougies d'anniversaire", "gâteau d'anniversaire"), or a plain cake on the list (key `cake` and the name
     * just "cake", "cakes" or "gâteau"). Cheesecake, carrot cake, tealights and scented candles are everyday buys, not a
     * birthday. Its check-off hanko reads HAPPY / B-DAY!. [catalog] is unused (kept for the callers' signature).
     */
    @Suppress("UNUSED_PARAMETER")
    fun isBirthday(item: GroceryItem, catalog: IconCatalog? = null): Boolean {
        val name = fold(item.name).trim()
        if (BIRTHDAY_WORDS.any { it in name }) return true
        return item.icon == "cake" && name in PLAIN_CAKES
    }

    /** Confetti rains this long (§12.2 egg 9). */
    const val CONFETTI_MS: Long = 2000

    // ---- Egg 17: ant parade ----

    /** The list holds ≥ 3 UNCHECKED distinct keys tagged `picnic`. */
    fun antsWanted(list: GroceryList?, catalog: IconCatalog): Boolean {
        val keys = list?.items.orEmpty().filter { !it.checked }.map { it.icon }.toSet()
        return keys.count { "picnic" in catalog.tagsOf(it) } >= 3
    }

    // ---- Egg 5: midnight snack ----

    /** The frozen aisle: basket COOLER or ICE_BUCKET, or a name containing "frozen" / "surgelé". Walking order first. */
    fun frozenSection(grouped: GroupedList): AisleSection? = grouped.sections.firstOrNull { s ->
        s.kind == SectionKind.AISLE && (
            s.basket == BasketKind.COOLER || s.basket == BasketKind.ICE_BUCKET ||
                fold(s.aisle?.name ?: s.displayName).let { "frozen" in it || "surgele" in it }
            )
    }

    // ---- Egg 8: nap time ----

    /**
     * The aisle whose basket the cat sleeps in: a trip is open (the cat has not trotted off this trip), one aisle stop
     * has nothing gotten yet, and every other stop is done. "Everything else" never holds the cat (it is not an aisle
     * basket, as when a closed trip files its nap aisle).
     */
    fun napSection(list: GroceryList?, grouped: GroupedList): String? {
        val trip = list?.trip ?: return null
        if (trip.napDismissed) return null
        val stops = grouped.sections.filter { it.total > 0 }
        val empty = stops.filter { it.kind == SectionKind.AISLE && it.gotten.isEmpty() }
        if (empty.size != 1) return null
        val cat = empty.single()
        return if (stops.all { it.id == cat.id || it.complete }) cat.id else null
    }

    // ---- Egg 10: bonus stage ----

    /** The order, by position among the aisle stamps (0 = the first aisle stamp): 1st 1st 2nd 2nd 3rd 4th 3rd 4th. */
    val BONUS_SEQUENCE: List<Int> = listOf(0, 0, 1, 1, 2, 3, 2, 3)
    const val BONUS_LIMIT_MS: Long = 6000
    const val BONUS_MIN_AISLE_STOPS: Int = 4

    /**
     * Where a stamp-page tap at [stampIndex] (0-based over every stamp, the "Everything else" capsule included) falls
     * among the AISLE stamps, or null for the capsule stamp (it is skipped: the sequence is by position, not by printed
     * numbers, §12.2 egg 10).
     */
    fun aisleStampIndex(stampIndex: Int, grouped: GroupedList): Int? {
        val stamp = grouped.sections.getOrNull(stampIndex) ?: return null
        if (stamp.kind != SectionKind.AISLE) return null
        return grouped.sections.take(stampIndex + 1).count { it.kind == SectionKind.AISLE } - 1
    }

    /** The aisle stops on the stamp page. */
    fun aisleStops(grouped: GroupedList): Int = grouped.sections.count { it.kind == SectionKind.AISLE }

    // ---- Egg 11: seasons ----

    /** First snow's tape shows for this many days from `first_snow_date`. */
    const val FIRST_SNOW_DAYS: Long = 7

    /**
     * The active season on [date]: the calendar windows (Seasons) plus first snow ([firstSnow], 7 days from it). Where
     * windows overlap, the season whose day is the earliest still to come wins; one whose day has passed wins only
     * alone (the most recent then), the same rule Seasons applies to the calendar ones.
     */
    fun season(date: LocalDate, firstSnow: LocalDate?): Season? {
        val candidates = buildList {
            for (year in date.year - 1..date.year + 1) {
                for (season in Season.entries) {
                    val day = Seasons.dayOf(season, year) ?: continue
                    if (date in Seasons.window(season to day)) add(season to day)
                }
            }
            if (firstSnow != null && !date.isBefore(firstSnow) && date.isBefore(firstSnow.plusDays(FIRST_SNOW_DAYS))) add(Season.FIRST_SNOW to firstSnow)
        }
        if (candidates.isEmpty()) return null
        val toCome = candidates.filter { (_, day) -> !day.isBefore(date) }
        return (toCome.minByOrNull { it.second } ?: candidates.maxByOrNull { it.second })?.first
    }

    /** The album sheet id a season unlocks in the window containing [date] ("thanksgiving-2026", "first-snow-2026"). */
    fun sheetId(season: Season, date: LocalDate): String {
        val day = if (season == Season.FIRST_SNOW) date else (date.year - 1..date.year + 1)
            .mapNotNull { Seasons.dayOf(season, it) }
            .firstOrNull { date in Seasons.window(season to it) } ?: date
        return Seasons.sheetId(season, day)
    }

    /**
     * The first-snow date to keep (§12.4): the first snow day on or after 1 October of the snow season [date] is in (1
     * October to 30 September). A stored date from an earlier season is replaced by today's snow; a date of this season
     * stays.
     */
    fun firstSnowDate(stored: LocalDate?, date: LocalDate, snow: Boolean): LocalDate? {
        val seasonStart = if (date.month >= Month.OCTOBER) LocalDate.of(date.year, Month.OCTOBER, 1) else LocalDate.of(date.year - 1, Month.OCTOBER, 1)
        val current = stored?.takeIf { !it.isBefore(seasonStart) }
        return current ?: if (snow) date else stored
    }

    // ---- Egg 14: perfect week, back pocket ----

    const val PERFECT_WEEK_TRIPS: Int = 4
    const val PERFECT_WEEK_MS: Long = 7L * 24 * 60 * 60 * 1000
    const val BACK_POCKET_TRIPS: Int = 10
    const val SAKURA_SHEET: String = "sakura"
    const val SHEET_2: String = "sheet2"

    /**
     * The album after egg 14's rules: every fourth complete trip in a row (4, 8, …) makes every book's stamp page holo
     * for 7 days from that filing (`perfectUntil`) and unlocks the sakura sheet; ten finished trips open the back pocket
     * ("sticker sheet 2 of 2"). Idempotent: the week a filing granted is granted once.
     *
     * The rules follow the books as they are now, so a book taken back out (Undo of the automatic new book, §0.4 Q9,
     * `Album.unfile`) takes back what its filing granted: the week, the sakura sheet if no other week was ever granted,
     * and the back pocket (and sheet 2) when the finished trips fall back under ten. The weeks are recomputed from the
     * books only while every finished trip is still in the album (none dropped by the album's cap); past that, a week
     * once granted stays.
     */
    fun perfectWeek(album: Album): Album {
        var a = album
        val finishedBooks = a.books.count { it.stickers.isNotEmpty() }
        // The books tell the whole story only while every finished trip is still filed (and the counters agree).
        if (finishedBooks >= a.finishedTrips && a.finishedTrips >= a.perfectStreak) {
            val grants = perfectWeekGrants(a.books)
            val until = grants.maxOrNull()
            if (a.perfectUntil != until) a = a.copy(perfectUntil = until)
            a = if (grants.isEmpty()) a.lockSheet(SAKURA_SHEET) else a.unlockSheet(SAKURA_SHEET)
        } else {
            // Older books have left the album: the newest finished book is the one that moved the streak to 4, 8, ….
            val newest = a.books.firstOrNull { it.stickers.isNotEmpty() }
            if (newest != null && a.perfectStreak > 0 && a.perfectStreak % PERFECT_WEEK_TRIPS == 0) {
                val until = newest.closedAt + PERFECT_WEEK_MS
                if (a.perfectUntil == null || a.perfectUntil!! < until) a = a.copy(perfectUntil = until).unlockSheet(SAKURA_SHEET)
            }
        }
        val pocket = a.finishedTrips >= BACK_POCKET_TRIPS
        if (a.backPocketOpen != pocket) a = a.copy(backPocketOpen = pocket)
        a = if (pocket) a.unlockSheet(SHEET_2) else a.lockSheet(SHEET_2)
        return a
    }

    /**
     * The end of every perfect week [books] (newest first, as the album keeps them) granted: walking the finished books
     * in filing order, a complete one adds one to the streak and an incomplete one resets it (a book with no sticker is
     * not a finished trip and does neither, §7.8); each streak of 4, 8, … grants 7 days from that book's filing.
     */
    fun perfectWeekGrants(books: List<TripBook>): List<Long> {
        var streak = 0
        val grants = ArrayList<Long>()
        for (book in books.asReversed()) {
            if (book.stickers.isEmpty()) continue
            streak = if (book.perfect) streak + 1 else 0
            if (streak > 0 && streak % PERFECT_WEEK_TRIPS == 0) grants += book.closedAt + PERFECT_WEEK_MS
        }
        return grants
    }

    private fun Album.lockSheet(sheet: String): Album = if (sheet in sheets) copy(sheets = sheets - sheet) else this

    // ---- Egg 18 and 15 ----

    /**
     * The LCD's words (§9 table): FED 1.5 s, SNACK? 2.6 s, BONUS★ while the stage lasts, and PRINT★ for as long as the
     * receipt prints, up to its settle at 1.96 s, when the slot line goes and the LCD returns to the count (§13.1, §13.2).
     */
    const val FED_MS: Long = 1500
    const val SNACK_MS: Long = 2600
    const val PRINT_MS: Long = ReceiptMotion.SETTLE_AT
    const val FED: String = "FED"
    const val SNACK: String = "SNACK?"
    const val PRINT: String = "PRINT★"
    const val BONUS: String = "BONUS★"

    /** Every word the eggs show on the app bar's LCD: its text slot keeps the widest one's room (§13.1, item 259). */
    val LCD_WORDS: List<String> = listOf(FED, SNACK, PRINT, BONUS)

    /** The pet's chomp at each landing: 2 frames, 520 ms. */
    const val CHOMP_MS: Long = 520

    /** Accents and case folded ("Gâteau" → "gateau"). */
    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(COMBINING, "")

    private val COMBINING = Regex("\\p{Mn}+")
}
