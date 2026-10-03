package earth.cora.picnic.ui.eggs

import androidx.compose.runtime.Immutable
import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.Receipt
import earth.cora.picnic.ui.list.motion.MotionEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/*
 * Every Easter-egg trigger (docs/DESIGN_STICKER.md §12), with an injectable Random and clock. Effects are
 * overlays in WP6's overlay host (EggOverlays, mounted in MotionOverlay's `eggs` slot) or state changes through
 * StateOps. Gated by Surprises → Easter eggs; with it off no egg effect renders (§2.5). Owner: WP9
 * (SurpriseEngineTest, Egg*Test). [AppSurpriseEngine] is the engine; [NoOpSurpriseEngine] the phase-0 stand-in.
 *
 * WP9 additions to the phase-0 contract (§19.2), all ADDITIVE with defaults, so phase-0 call sites still compile:
 * - [SurpriseEngine.events]: the eggs' toasts (rain, bonus stage, back pocket, receipt saved); EggOverlays shows them.
 * - [SurpriseEngine.onListShown] / [SurpriseEngine.onListHidden]: the list screen came up or went (SNACK? on entry,
 *   the weather check, the receipt's auto-tuck, the bonus stage ending, ants on open).
 * - [SurpriseEngine.onReceiptTap], [SurpriseEngine.onNapTap]: taps on the printed receipt and the nap cat (EggOverlays).
 * - [SurpriseEngine.onMotionEvent]: WP6's MotionEvents (the pet chomps at each landing); EggOverlays forwards them.
 * - [SurpriseEngine.onImeVisible]: the ants never march while the keyboard is open.
 * - [SurpriseEngine.onIconDrawing]: a live drawing started (egg 13's capsule is gold while it draws).
 * - [SurpriseEngine.refresh]: the minute tick (midnight, golden hour, seasons); ThemeVariantSource and EggOverlays call it.
 * - [SurpriseEngine.effectDone], [SurpriseEngine.setReducedMotion], [SurpriseEngine.onDuskSunset],
 *   [SurpriseEngine.onBasketHello]: the overlays report when an effect has played out on the frame clock (and egg 3's
 *   bump), and the reduced-motion setting.
 * - [SurpriseState] gains the effects' fields below (ids are fresh per firing, so an overlay restarts on a new one).
 */
interface SurpriseEngine {
    /** What the eggs currently show. */
    val state: StateFlow<SurpriseState>

    /** Egg 7: true = the text was the magic word: nothing is added and no request is sent. */
    fun interceptText(text: String): Boolean

    /** Egg 6: 1 in 200 shiny for a committed item (the Surprise lab can force the next one). */
    fun rollRarity(): Rarity

    /** A commit landed (eggs 3, 6, 9, 17). */
    fun onCommitted(items: List<GroceryItem>)

    /** Egg 1: a tap on the sun sticker (in Dusk the sticker is the moon: the tap undoes Dusk). */
    fun onSunTap()

    /** Egg 1: a tap on the moon (undoes Dusk). */
    fun onMoonTap()

    /** Egg 10: a tap on stamp [position] (0-based on the stamp page, the "Everything else" capsule stamp included). */
    fun onStampTap(position: Int)

    /** Eggs 15 and 18: the LCD was held for the hold threshold (receipt if the trip is complete, else the pet; D21). */
    fun onLcdHold()

    /** Egg 2: a folded basket held for [heldMs] (the ladybug walks at the hold threshold + 560 ms). */
    fun onBasketHold(sectionId: String, heldMs: Long)

    /** Egg 4: a shake (ShakeDetector). */
    fun onShake()

    /** Egg 13: a live sticker arrived for an APPROXIMATE item; returns its rarity (1 in 100 jackpot). */
    fun onIconDrawn(item: GroceryItem): Rarity

    // ---------------------------------------------------------------------------------------------
    // WP9 additions (additive; defaults do nothing).
    // ---------------------------------------------------------------------------------------------

    /** The eggs' toasts, for the list's toast host (EggOverlays shows them). */
    val events: Flow<EggToast> get() = emptyFlow()

    /** The list screen is showing [listId] (on open, on resume, on a list switch). */
    fun onListShown(listId: String) = Unit

    /** The list screen left (another screen, the app in the background): auto-tuck, the bonus stage ends. */
    fun onListHidden() = Unit

    /** Egg 18: a tap on the printed receipt tucks it into the book. */
    fun onReceiptTap() = Unit

    /** Egg 8: a tap on the nap cat sends it off (it does not return this trip). */
    fun onNapTap() = Unit

    /** WP6's chain events (egg 15's pet chomps at every landing). */
    fun onMotionEvent(event: MotionEvent) = Unit

    /** The keyboard opened or closed (egg 17 never starts while it is open). */
    fun onImeVisible(visible: Boolean) = Unit

    /** Egg 13: a live drawing started for an APPROXIMATE [item]; the gold roll happens now, so the capsule draws gold. */
    fun onIconDrawing(item: GroceryItem) = Unit

    /** Re-reads the clock (the minute tick, on resume): midnight, golden hour, the season. */
    fun refresh() = Unit

    /** The overlay for the effect [id] finished playing (on the frame clock). */
    fun effectDone(id: Long) = Unit

    /** Egg 1: the sun has set in the transition [id] (600 ms): the Dusk palette washes in now. */
    fun onDuskSunset(id: Long) = Unit

    /** Reduced motion (§8.8): the four pure-motion eggs do not run; the rest apply in place. */
    fun setReducedMotion(on: Boolean) = Unit

    /** Egg 3's bump: [sectionId]'s basket wobbles hello (and egg 8's cat wobbles it on its way out). */
    fun onBasketHello(sectionId: String) = Unit
}

/** The eggs' toasts (§16 copy), shown by EggOverlays in the list's toast host. */
enum class EggToast {
    /** Egg 16: "Rain on the way · bring the tote!", once per day. */
    RainToday,

    /** Egg 10: "BONUS STAGE! ♪". */
    BonusStage,

    /** Egg 14: "Your album's back pocket opened!" with "Look". */
    BackPocket,

    /** Egg 18: "Receipt saved to your sticker book ♡". */
    ReceiptSaved,
}

/** What the eggs show right now (§12.2). Effect fields carry a fresh id per firing. */
@Immutable
data class SurpriseState(
    /** Surprises → Easter eggs. Off: nothing below shows (§2.5). */
    val enabled: Boolean = true,
    /** Egg 1: the Dusk binder. */
    val dusk: Boolean = false,
    /** Egg 5: midnight snack (forces Glow). */
    val midnight: Boolean = false,
    /** Egg 12: golden hour. */
    val golden: Boolean = false,
    /** Egg 7: the screen title is holo for 5 s. */
    val kirakira: Boolean = false,
    /** Egg 10: every sticker is a pixel sprite. */
    val bonusStage: Boolean = false,
    /** Egg 4: petals are on the binder. */
    val petals: Boolean = false,
    /** Egg 17: the ants are marching. */
    val ants: Boolean = false,
    /** Egg 16: rain today (Weather surprises on). */
    val rain: Boolean = false,
    /** Egg 11: the active seasonal sheet. */
    val season: Season? = null,
    /** Egg 8: the section whose basket the nap cat sleeps in. */
    val napSectionId: String? = null,

    // ---- WP9 additions ----

    /** Egg 12: minutes left before sunset (0–30) while golden; the sun sinks 0.5 dp per minute gone. */
    val goldenMinutesLeft: Int? = null,
    /** Egg 1: the sun-to-moon (or moon-to-sun) transition playing. */
    val duskFx: DuskFx? = null,
    /** Egg 7: the twinkle and holo title, while [kirakira]. */
    val kirakiraId: Long? = null,
    /** Egg 4: the petal scatter, while [petals]. */
    val petalsId: Long? = null,
    /** Egg 17: the parade, while [ants]. */
    val antsId: Long? = null,
    /** Egg 2: the ladybug's stroll along a folded strip. */
    val ladybug: LadybugFx? = null,
    /** Egg 3: a committed fruit rolling (or berries hopping) to its basket and onto its line. */
    val rolling: RollingFx? = null,
    /** Egg 9: the confetti rain. */
    val confettiId: Long? = null,
    /** Egg 6: the 8-sticker bursts at newly shiny rows. */
    val shinyBursts: List<ShinyFx> = emptyList(),
    /** Egg 13: items whose live drawing will come out gold (their capsule draws gold while drawing). */
    val goldCapsules: Set<String> = emptySet(),
    /** Egg 13: the JACK / POT! reveal at a row. */
    val jackpot: JackpotFx? = null,
    /** Eggs 5, 10, 15, 18: a word on the LCD (SNACK?, BONUS★, FED, PRINT★) and how long it holds; null = the count. */
    val lcdWord: LcdWord? = null,
    /** Egg 15: the pixel bee lives in the LCD on this trip. */
    val pet: Boolean = false,
    /** Egg 15 with no trip: the bee visits for 1.5 s and leaves; nothing is persisted. */
    val petVisitId: Long? = null,
    /** Egg 15: the pet chomps (2 frames, 520 ms) at a landing. */
    val petChompId: Long? = null,
    /** Egg 18: the printed receipt, waiting for a tap. */
    val receipt: ReceiptFx? = null,
    /** Egg 18: the receipt just tapped, poofing into the book. */
    val receiptTuckId: Long? = null,
    /** Egg 18: the trip is complete and eggs are on: the LCD's hold prints (and its TalkBack action shows). */
    val receiptReady: Boolean = false,
    /** Egg 8: the cat stretching and trotting off after a tap. */
    val napLeaving: NapLeaveFx? = null,
    /** Egg 5: the snack star and "snack?" doodle sit by this section's tab (the frozen aisle). */
    val snackSectionId: String? = null,
    /** Egg 14: every book's stamp page is holo (a perfect week, 7 days). */
    val perfectWeek: Boolean = false,
    /** Eggs 3 and 8: a basket wobbling hello (−6 / +5 / −2 / 0°, 360 ms); WP5's basket plays it once per id. */
    val basketHello: BasketHelloFx? = null,
) {
    /** Egg 16: the umbrella shelters the add bar's left end. */
    val umbrella: Boolean get() = enabled && rain

    /**
     * §4.1: at most 3 page stickers; the list's standing pair is the bee and the ladybug, so when two egg stickers
     * show at once (the nap cat and the umbrella) the ladybug steps off.
     */
    val ladybugStepsOff: Boolean get() = napSectionId != null && umbrella

    /** Egg 3: the row's own sticker hides while its rolling twin travels (it settles on its line at the end). */
    fun hidesRowSticker(itemId: String): Boolean = rolling?.itemId == itemId

    /** Egg 13: the row's empty check spot steps aside while JACK / POT! is stamped over its column (1.8 s). */
    fun hidesCheckSpot(itemId: String): Boolean = jackpot?.itemId == itemId

    companion object {
        /** Eggs off: nothing shows. */
        val Off: SurpriseState = SurpriseState(enabled = false)
    }
}

/** Egg 1's transition: [toDusk] (sun sinks, moon rises) or back to day (moon tapped); [reduced] = a 120 ms fade. */
@Immutable
data class DuskFx(val id: Long, val toDusk: Boolean, val reduced: Boolean)

/** A basket's hello wobble (eggs 3 and 8). */
@Immutable
data class BasketHelloFx(val id: Long, val sectionId: String)

/** Egg 2: the ladybug walks the folded strip [sectionId]. */
@Immutable
data class LadybugFx(val id: Long, val sectionId: String)

/** Egg 3: [itemId]'s sticker [icon] travels from the add bar to [sectionId]'s basket and onto its line. */
@Immutable
data class RollingFx(val id: Long, val itemId: String, val sectionId: String, val icon: IconRef, val hop: Boolean, val holo: Boolean = false, val aisleIndex: Int = -1)

/** Egg 6: an 8-sticker burst at [itemId]'s row. */
@Immutable
data class ShinyFx(val id: Long, val itemId: String)

/** Egg 13: [itemId]'s gold capsule wobbles twice, pops, and JACK / POT! holds 1.8 s beside it. [reduced]: fades. */
@Immutable
data class JackpotFx(val id: Long, val itemId: String, val icon: IconRef, val reduced: Boolean)

/** An LCD word ([text]) that holds [holdMs] (0 = until the effect ends by itself, BONUS★). */
@Immutable
data class LcdWord(val id: Long, val text: String, val holdMs: Long)

/** Egg 18: the receipt printed for [listId] ([receipt]'s frozen values); [reduced]: it fades in printed. */
@Immutable
data class ReceiptFx(val id: Long, val listId: String, val listTitle: String, val receipt: Receipt, val reduced: Boolean)

/** Egg 8: the cat leaving [sectionId]'s basket. */
@Immutable
data class NapLeaveFx(val id: Long, val sectionId: String, val reduced: Boolean)

/** No eggs: never intercepts, never rolls a rarity. The phase-0 stand-in. */
object NoOpSurpriseEngine : SurpriseEngine {
    private val none = MutableStateFlow(SurpriseState(enabled = false))

    override val state: StateFlow<SurpriseState> = none.asStateFlow()

    override fun interceptText(text: String): Boolean = false

    override fun rollRarity(): Rarity = Rarity.NONE

    override fun onCommitted(items: List<GroceryItem>) = Unit

    override fun onSunTap() = Unit

    override fun onMoonTap() = Unit

    override fun onStampTap(position: Int) = Unit

    override fun onLcdHold() = Unit

    override fun onBasketHold(sectionId: String, heldMs: Long) = Unit

    override fun onShake() = Unit

    override fun onIconDrawn(item: GroceryItem): Rarity = Rarity.NONE
}
