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
 * - [SurpriseEngine.onToastShown]: the toast host took an egg toast (the rain toast is marked as today's then).
 * - [SurpriseEngine.onMotionEvent]: WP6's MotionEvents (the pet chomps at each landing); EggOverlays forwards them.
 * - [SurpriseEngine.onImeVisible]: the ants never march while the keyboard is open.
 * - [SurpriseEngine.onIconDrawing], [SurpriseEngine.onIconDrawFailed]: a live drawing started or failed (egg 13's
 *   capsule is gold while it draws). AppIconRepository calls them, with [onIconDrawn], through `LiveDrawJackpot`.
 * - [SurpriseEngine.refresh]: the minute tick (midnight, golden hour, seasons); ThemeVariantSource and EggOverlays call it.
 * - [SurpriseEngine.effectDone], [SurpriseEngine.setReducedMotion], [SurpriseEngine.onDuskSunset],
 *   [SurpriseEngine.onBasketHello]: the overlays report when an effect has played out on the frame clock (and egg 3's
 *   bump), and the reduced-motion setting.
 * - [SurpriseState] gains the effects' fields below (ids are fresh per firing, so an overlay restarts on a new one).
 * - The engine takes the resolved theme variant (`variant`): the Glow moon (dark mode, midnight) is static, so a tap on it
 *   neither counts toward Dusk nor undoes it, whichever hook the list calls (onSunTap or onMoonTap).
 *
 * ---------------------------------------------------------------------------------------------------------------------
 * THE LIST SCREEN'S CHECKLIST (WP5's ListScreen, which the Surprise lab's PracticeListScreen hosts too, so the lab runs
 * the code her list runs). Wrap the screen in [EggListHost] (EggListHost.kt): it calls onListShown / onListHidden (screen,
 * list switch, app stop and start), runs the shake detector and the keyboard observer (onImeVisible), provides
 * LocalRollHome and LocalListRoom, and scrolls each scene into the list's room above the add bar (the rolling item's
 * aisle and row on every list; the nap cat's strip and the frozen aisle's tab in the Surprise lab only, `sceneScrolls`).
 * Its [EggListScope] hands the content the rest:
 *   - `Wash()` first inside PicnicBackground (egg 1's Dusk wash);
 *   - `overlays` into MotionOverlay's `eggs` slot, with `bottomInset = overlayInset()` (the toasts sit above the bar's
 *     MEASURED top, and above the umbrella's canopy while it rains);
 *   - the lazy list's `contentPadding(bottom = listBottomPadding())`: the measured bar + 26 dp (+ the umbrella's room),
 *     so the last strip always scrolls wholly above the bar at any font scale; its viewport's top inset `listTopExtra`
 *     (`Modifier.padding(top = …)` before the `list` anchor) while a printed receipt hangs into it, its outer top
 *     reported to `LocalListRoom.listTop`; the binder top and the book stay open while `receiptWaits`;
 *   - the LCD's window bounds reported to `LocalListRoom.lcd` (the receipt hangs from it);
 *   - `Modifier.eggAddBar(folded)` on the docked add bar (it is measured and followed; `folded` while it is folded
 *     into the FAB), with `tape = AddRules.addBarTape(eggs)` and
 *     `tapeAfterDoodle = tapeAfterDoodle` (AddBar / AddDock: the rain's tape goes after the doodle while the umbrella
 *     stands at the bar's left end).
 * Anchors (`with(anchors) { Modifier.anchor(key) }`; the overlays find everything through them):
 *   - `sun` on the sticker by the title; `lcd` on the LCD chip; `addbar` on the docked add bar; `list` (EggAnchors.LIST)
 *     on the LazyColumn (the list's viewport: the scenes' room, the receipt's room, where the ladybug may walk);
 *   - `row:<item>` on each row's 52 dp sticker box, `check:<item>` on its check spot, `line:<item>` (EggAnchors.line)
 *     on the whole row; `basket:<section>` (basketAnchor) on each strip's basket (priority 0) and the open tray's
 *     (priority 1); `stamp:<section>` on the book's stamps;
 *   - `strip:<section>` (EggAnchors.strip) on each folded strip's white card (the ladybug walks its bottom edge),
 *     `tab:<section>` (EggAnchors.tab) on each tab, strip and tray header alike;
 *   - `text:<anything>` (EggAnchors.TEXT_PREFIX) on EVERY word on the screen: the title, the subtitle, the LCD, each
 *     tab's name and number, each strip's meta and store chip, each row's name, detail and rarity chip, the book's date,
 *     and the whole add bar (`text:addbar`). Every never-over-text guarantee (the bursts' keep-out clip, the receipt's
 *     placement, the confetti's ceiling) reads these.
 * Pieces, where they go:
 *   - binder top: `lcd = eggs.lcd(base, rememberPetPose(eggs))`; `onLcdHold = engine::onLcdHold` with eggs on; the sun
 *     sticker's tap `engine.onTitleStickerTap(eggs, variant)` and `Modifier.eggTitleSticker(eggs, variant)`;
 *     `goldenMinutesLeft = eggs.goldenMinutesLeft`; `titleBrush = rememberKirakiraBrush(eggs)`;
 *   - book: `pixel = eggs.pixelStickers`; the holo stamp page while `eggs.perfectWeek`; the season's pictogram from
 *     `eggs.season`; each stamp's tap `engine.onStampTap(position)` (0-based, the capsule stamp included);
 *   - folded strip: `TabMetaDoodle(tab, meta, doodle = SnackDoodle when eggs.snackDoodleAt(section))` (the doodle never
 *     shrinks the tab); the basket's `Modifier.basketHello(eggs, section)` and, inside the basket's box,
 *     `NapCatInBasket(eggs, section, kind, engine, chainsDone)`; a hold on the basket `engine.onBasketHold(section, ms)`
 *     at the hold threshold + 560 ms (a hold of the threshold alone only peeks);
 *   - open tray: `SnackDoodle(eggs, section)` after its tab (its own width, after the tab's); the basket's
 *     `basketHello` and `NapCatInBasket`; `TrayBee` over the current tray's basket; `EndCapLadybug(eggs)` on its cap;
 *   - row: `RowRoller(item)` FIRST inside the row's box (egg 3 rolls home beneath the words); its sticker hidden while
 *     `eggs.hidesRowSticker(item)`, else `Modifier.eggTwinkle(eggs, index)`, `pixel = eggs.pixelStickers`, the capsule
 *     drawn gold while `eggs.goldCapsule(item)`; its empty check spot hidden while `eggs.hidesCheckSpot(item)`; the
 *     hanko's words `eggs.hankoFor(item, catalog)`;
 *   - add flows: `engine.interceptText(text)` before anything is added or sent (true: nothing happens, the field
 *     clears); `engine.rollRarity()` for each new row; `engine.onCommitted(items)` after each commit.
 * Tests a list runs on itself: EggOverlayBoundsTest.neverOverText and DecorBudgetTest take the list composable as a
 * parameter (`EggListUnderTest`), so WP5's ListScreen can run them on merge.
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

    /** The toast host has taken [toast] from [events] and shows it (egg 16's rain toast counts as today's then). */
    fun onToastShown(toast: EggToast) = Unit

    /** Egg 8: a tap on the nap cat sends it off (it does not return this trip). */
    fun onNapTap() = Unit

    /** WP6's chain events (egg 15's pet chomps at every landing). */
    fun onMotionEvent(event: MotionEvent) = Unit

    /** The keyboard opened or closed (egg 17 never starts while it is open). */
    fun onImeVisible(visible: Boolean) = Unit

    /** Egg 13: a live drawing started for an APPROXIMATE [item]; the gold roll happens now, so the capsule draws gold. */
    fun onIconDrawing(item: GroceryItem) = Unit

    /** Egg 13: [item]'s live drawing failed or was dropped: its capsule stops drawing gold (no jackpot). */
    fun onIconDrawFailed(item: GroceryItem) = Unit

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
     * show at once (the nap cat and the umbrella) the end cap's ladybug steps off. It also steps off while egg 2's
     * ladybug strolls along a strip (§12.2 "a ladybug appears"): there is one ladybug on the list, the one out walking.
     */
    val ladybugStepsOff: Boolean get() = (napSectionId != null && umbrella) || (enabled && ladybug != null)

    /**
     * §4.1: the egg page stickers already take the screen's room beside the bee (the nap cat and the umbrella together):
     * no other page sticker may join them, so egg 2's ladybug does not set off.
     */
    val pageStickerBudgetFull: Boolean get() = enabled && napSectionId != null && umbrella

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

/**
 * Egg 18: the receipt printed for [listId]'s trip [tripId] ([receipt]'s frozen values); [reduced]: it fades in printed.
 * It goes when that trip is filed (New trip, the list deleted) or the receipt is tucked in.
 */
@Immutable
data class ReceiptFx(val id: Long, val listId: String, val listTitle: String, val receipt: Receipt, val reduced: Boolean, val tripId: String? = null)

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
