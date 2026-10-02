package earth.cora.picnic.ui.eggs

import androidx.compose.runtime.Immutable
import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.Rarity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * Every Easter-egg trigger (docs/DESIGN_STICKER.md §12), with an injectable Random and clock. Effects are
 * overlays in WP6's overlay host or state changes through StateOps. Gated by Surprises → Easter eggs; with
 * it off no egg effect renders (§2.5). Owner: WP9 (SurpriseEngineTest, Egg*Test). PHASE-0 CONTRACT;
 * [NoOpSurpriseEngine] is the stand-in WP5 and WP7 build against. Field and hook names may grow.
 */
interface SurpriseEngine {
    /** What the eggs currently show. */
    val state: StateFlow<SurpriseState>

    /** Egg 7: true = the text was the magic word: nothing is added and no request is sent. */
    fun interceptText(text: String): Boolean

    /** Egg 6: 1 in 200 shiny for a committed item (the Surprise lab can force the next one). */
    fun rollRarity(): Rarity

    /** A commit landed (eggs 3, 9, 17). */
    fun onCommitted(items: List<GroceryItem>)

    /** Egg 1: a tap on the sun sticker. */
    fun onSunTap()

    /** Egg 1: a tap on the moon (undoes Dusk). */
    fun onMoonTap()

    /** Egg 10: a tap on stamp [position] (0-based on the stamp page). */
    fun onStampTap(position: Int)

    /** Eggs 15 and 18: the LCD was held for the hold threshold (receipt if the trip is complete, else the pet; D21). */
    fun onLcdHold()

    /** Egg 2: a folded basket held for [heldMs] (the ladybug walks at the hold threshold + 560 ms). */
    fun onBasketHold(sectionId: String, heldMs: Long)

    /** Egg 4: a shake (ShakeDetector). */
    fun onShake()

    /** Egg 13: a live sticker arrived for an APPROXIMATE item; returns its rarity (1 in 100 jackpot). */
    fun onIconDrawn(item: GroceryItem): Rarity
}

/** What the eggs show right now (§12.2). */
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
)

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
