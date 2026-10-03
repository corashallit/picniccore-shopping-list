package earth.cora.picnic.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.list.motion.BasketMini
import earth.cora.picnic.ui.list.motion.BasketPresentation
import earth.cora.picnic.ui.list.motion.MotionBasket
import earth.cora.picnic.ui.list.motion.MotionClock

/*
 * The aisle basket (docs/DESIGN_STICKER.md §5, rule R4), at rest: the C art of its kind (WP2's two-layer vectors from
 * BasketManifest, drawn by WP6's MotionBasket so a basket at rest and a basket in a chain are one drawing). CONTRACT
 * (§19.2): it holds only GOTTEN items, in check-off order, newest on top (1–5 laid out, more = the newest 5 and a "+n"
 * tag); empty, it shows its dotted landing spot (decorated kinds keep their decoration); the badge counts what is still
 * to get and hides at 0, never drawn on the art; a done aisle's lid is shut with GOT IT on the lid. Decorative: the strip
 * or tray carries the counts for TalkBack. Owner: WP5.
 */

/** The three basket sizes (§5.1). */
enum class BasketSize(val size: DpSize, val tray: Boolean) {
    /** 80 × 64 dp on a folded strip. */
    Strip(DpSize(80.dp, 64.dp), tray = false),

    /** 64 × 51 dp in an open tray's header (its badge 2 dp from the top). */
    Tray(DpSize(64.dp, 51.dp), tray = true),

    /** 120 × 96 dp on cards and in the aisle editor. */
    Card(DpSize(120.dp, 96.dp), tray = false),
}

/** A basket's state at rest: [complete] (lid shut, GOT IT on the lid, badge hidden), [peek] (held open, §5.3). */
@Immutable
data class BasketVisual(val complete: Boolean = false, val peek: Boolean = false)

/**
 * A basket of [kind] holding [gotten] (check-off order; never an item still to get) with the [toGet] badge in [look]'s
 * trace. [state]: done (lid + hanko) or peeked open (lid open, minis raised).
 */
@Composable
fun AisleBasket(
    kind: BasketKind,
    gotten: List<BasketMini>,
    toGet: Int,
    state: BasketVisual,
    modifier: Modifier = Modifier,
    size: BasketSize = BasketSize.Strip,
    look: AisleLook = AisleLooks.everythingElse,
) {
    val clock = remember { MotionClock() }
    val presentation = if (state.peek) BasketPresentation(peek = true, lidClosed = false, minisHidden = false) else null
    MotionBasket(
        kind = kind,
        look = look,
        gotten = gotten,
        toGet = if (state.complete) 0 else toGet,
        complete = state.complete,
        presentation = presentation,
        clock = clock,
        modifier = modifier,
        size = size.size,
        tray = size.tray,
    )
}

/**
 * The v1 call shape (ARCHITECTURE §11.4), kept for the aisle editor's basket-kind row: [inBasket] gotten stickers and the
 * [count] badge, drawn with the C art in a [size] square (the basket is as wide as [size] and 4/5 as tall, centred).
 */
@Composable
fun AisleBasket(
    kind: BasketKind,
    inBasket: List<IconRef>,
    count: Int,
    modifier: Modifier = Modifier,
    size: Dp = PicnicTheme.dimens.basket,
) {
    val clock = remember { MotionClock() }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        MotionBasket(
            kind = kind,
            look = AisleLooks.everythingElse,
            gotten = inBasket.mapIndexed { i, icon -> BasketMini("mini-$i", icon) },
            toGet = count,
            complete = false,
            presentation = null,
            clock = clock,
            size = DpSize(size, size * 0.8f),
        )
    }
}

/** What a basket shows of its gotten stickers (§5.3, item 106): the newest five in their fill-order slots, and "+n". */
@Immutable
data class BasketFill(val shown: List<BasketMini>, val slots: List<earth.cora.picnic.ui.theme.gen.MiniSlot>, val extra: Int)

/** The basket's mini layout, shared by every basket drawing (pure, unit-tested). */
object BasketLayout {
    /** [gotten] in check-off order: the newest [earth.cora.picnic.ui.theme.gen.BasketManifest.MAX_MINIS] shown, newest on top, the rest counted in "+n". */
    fun fill(gotten: List<BasketMini>): BasketFill {
        val shown = gotten.takeLast(earth.cora.picnic.ui.theme.gen.BasketManifest.MAX_MINIS)
        return BasketFill(shown, earth.cora.picnic.ui.theme.gen.BasketManifest.MINI_SLOTS[shown.size].orEmpty(), gotten.size - shown.size)
    }
}
