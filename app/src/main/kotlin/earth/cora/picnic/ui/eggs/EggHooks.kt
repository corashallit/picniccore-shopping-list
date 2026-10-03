package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.ui.theme.gen.BasketManifest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.HankoText
import earth.cora.picnic.ui.theme.LcdMode
import earth.cora.picnic.ui.theme.LcdState
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PetPose
import earth.cora.picnic.ui.theme.rememberLoopTime
import kotlinx.coroutines.withContext

/*
 * What the list's own pieces read from the eggs (docs/DESIGN_STICKER.md §12, §13): WP5 builds its LCD, hankos, sticker
 * looks, title and baskets from these, so every list shows the eggs the same way (the Surprise lab's practice list
 * included). Owner: WP9.
 */

/**
 * The LCD as the eggs show it (§13.1): a word (SNACK? 2.6 s, BONUS★ while the stage lasts, FED 1.5 s with the bee,
 * PRINT★ 1.7 s), the pet bee beside the count while it lives there (egg 15; the star hides), and the receipt's TalkBack
 * action once the trip is complete (egg 18). [base] is the count the list shows; with Easter eggs off it is returned
 * as it is (no pet, no words, no receipt action, §2.5).
 */
fun SurpriseState.lcd(base: LcdState, petPose: PetPose = PetPose.Rest): LcdState {
    if (!enabled) return base.copy(receiptReady = false)
    val word = lcdWord?.text
    val bee = pet || petVisitId != null
    return when {
        bee -> base.copy(mode = LcdMode.Pet, text = word.orEmpty(), petPose = petPose, receiptReady = receiptReady)
        word != null -> base.copy(mode = LcdMode.Text, text = word, receiptReady = receiptReady)
        else -> base.copy(receiptReady = receiptReady)
    }
}

/**
 * The pet's pose (egg 15): it bobs 2 steps per second, chomps in 2 frames (520 ms) at each landing, and sits still
 * under reduced motion. The celebration's dance is WP6's.
 */
@Composable
fun rememberPetPose(state: SurpriseState): PetPose {
    val reduced = LocalReducedMotion.current
    val living = state.enabled && (state.pet || state.petVisitId != null)
    val loop by rememberLoopTime(enabled = living && !reduced)
    val chomp = state.petChompId
    val since = remember(chomp) { mutableLongStateOf(-1L) }
    if (chomp != null && !reduced) {
        LaunchedEffect(chomp) {
            withContext(FixedMotionScale) {
                val start = withFrameMillis { it }
                while (true) {
                    val t = withFrameMillis { it } - start
                    since.longValue = t
                    if (t >= EggRules.CHOMP_MS) break
                }
            }
        }
    }
    val t = since.longValue
    return when {
        !living || reduced -> PetPose.Rest
        chomp != null && t in 0 until EggRules.CHOMP_MS / 2 -> PetPose.Chomp1
        chomp != null && t in EggRules.CHOMP_MS / 2 until EggRules.CHOMP_MS -> PetPose.Chomp2
        EggBeats.petBob(loop) -> PetPose.Bob
        else -> PetPose.Rest
    }
}

/**
 * The hanko a checked [item] wears (§4.5): HAPPY / B-DAY! for a birthday item (egg 9) while Easter eggs are on, else
 * GOT / IT!.
 */
fun SurpriseState.hankoFor(item: GroceryItem, catalog: IconCatalog? = null): HankoText =
    if (enabled && EggRules.isBirthday(item, catalog)) HankoText.HappyBday else HankoText.GotIt

/** Egg 10: every sticker on the list screen is a pixel sprite (rows, minis, book twins). */
val SurpriseState.pixelStickers: Boolean get() = enabled && bonusStage

/** Egg 13: [itemId]'s capsule is gold while its live sticker draws. */
fun SurpriseState.goldCapsule(itemId: String): Boolean = enabled && itemId in goldCapsules

/**
 * Egg 7: every visible sticker twinkles once (the one twinkle rule, 3 poses at 8 fps), the [index]-th 60 ms after the
 * first. Nothing under reduced motion (§12.3).
 */
@Composable
fun Modifier.eggTwinkle(state: SurpriseState, index: Int = 0): Modifier {
    val id = state.kirakiraId
    if (id == null || !state.enabled || LocalReducedMotion.current) return this
    val t by rememberEffectTime(id, 600L + index * 60L)
    val pose = EggBeats.twinkle(t - index * 60L)
    return graphicsLayer {
        rotationZ = pose.rotation
        scaleX = pose.scaleX
        scaleY = pose.scaleY
    }
}

/**
 * Egg 7: the screen title's holo fill (animated foil, 2.4 s cycle) for its 5 s, or null. Static foil under reduced
 * motion. Pass it to BinderTopBar's `titleBrush`.
 */
@Composable
fun rememberKirakiraBrush(state: SurpriseState): (() -> Brush)? {
    val id = state.kirakiraId
    if (id == null || !state.enabled) return null
    val reduced = LocalReducedMotion.current
    val t by rememberEffectTime(id, if (reduced) 0L else EggRules.KIRAKIRA_MS)
    return { holoTitleBrush(if (reduced) 0f else (t % HOLO_CYCLE_MS).toFloat() / HOLO_CYCLE_MS) }
}

private const val HOLO_CYCLE_MS = 2400L

/** The holo foil (§2.11 sticker holo stops) as a moving diagonal band, [phase] 0–1 across one cycle. */
fun holoTitleBrush(phase: Float): Brush {
    val span = 220f
    val shift = phase * span
    return Brush.linearGradient(
        colors = HOLO_TEXT,
        start = Offset(shift, 0f),
        end = Offset(shift + span, span * 0.35f),
        tileMode = TileMode.Mirror,
    )
}

/**
 * The foil for TEXT: the §2.11 holo hues taken deep enough to read (the pastel stops alone would vanish into the white
 * stroke): strawberry, ramune, mint, lemon and grape deeps (§2.2), mirrored so the sweep never shows a seam.
 */
private val HOLO_TEXT: List<Color> = listOf(
    Color(0xFFEC587A), Color(0xFF5EA9DB), Color(0xFF53B594), Color(0xFFDAB632), Color(0xFF9B7CDE),
)

/**
 * Eggs 3 and 8: [sectionId]'s basket wobbles hello (−6 / +5 / −2 / 0°, 4 steps, 360 ms, pivot 50 % / 90 %) when the
 * eggs ask. WP5's basket wraps itself in this.
 */
@Composable
fun Modifier.basketHello(state: SurpriseState, sectionId: String): Modifier {
    val hello = state.basketHello?.takeIf { it.sectionId == sectionId && state.enabled } ?: return this
    if (LocalReducedMotion.current) return this
    val t by rememberEffectTime(hello.id, 360L)
    val angle = EggBeats.wobble(t)
    return graphicsLayer {
        rotationZ = angle
        transformOrigin = TransformOrigin(0.5f, 0.9f)
    }
}

/**
 * Egg 5: the snack-time star sticker and the "snack?" doodle, for the list to place right after [sectionId]'s tab (it
 * scrolls with its strip). Draws nothing unless it is midnight and [sectionId] is the frozen aisle. Decorative.
 */
@Composable
fun SnackDoodle(state: SurpriseState, sectionId: String, modifier: Modifier = Modifier) {
    if (!state.enabled || state.snackSectionId != sectionId) return
    SnackDoodleContent(modifier)
}

/**
 * Egg 8: the nap cat curled in [sectionId]'s basket, drawn by the list over the basket itself (call it inside the
 * basket's box, the size the basket art is drawn at), so it scrolls with its strip. [kind] places the rim; [chainsDone]
 * is false while the check-off chain that finished the other stops still plays (the cat curls up after it). Pair it
 * with `EggOverlays(listPlacesDecor = true)`.
 */
@Composable
fun BoxScope.NapCatInBasket(state: SurpriseState, sectionId: String, kind: BasketKind, engine: SurpriseEngine, chainsDone: Boolean = true) {
    if (!state.enabled || state.napSectionId != sectionId) return
    if (!rememberNapSettled(sectionId, chainsDone)) return
    var box by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .matchParentSize()
            .onGloballyPositioned {
                box = it.size
                origin = it.positionInWindow()
            },
    ) {
        if (box == IntSize.Zero) return@Box
        val d = LocalDensity.current.density
        val rimFraction = (BasketManifest.art(kind.key)?.rimY ?: 30f) / BasketManifest.VIEWPORT_HEIGHT
        val rimY = box.height * rimFraction + 6 * d
        val center = Offset(box.width / 2f - 2 * d, rimY - 9 * d)
        NapCatPiece(sectionId, center, rimY, engine, origin)
    }
}

/**
 * Egg 1: a tap on the sticker by the title in [variant]. The sun (Day, Golden) counts toward Dusk; the Dusk moon undoes
 * it; the Glow moon (system dark, midnight) is static. Nothing with Easter eggs off. WP5's BinderTopBar `onSunTap`.
 */
fun SurpriseEngine.onTitleStickerTap(state: SurpriseState, variant: earth.cora.picnic.ui.theme.ThemeVariant) {
    when {
        !state.enabled -> Unit
        variant == earth.cora.picnic.ui.theme.ThemeVariant.Glow -> Unit
        state.dusk -> onMoonTap()
        else -> onSunTap()
    }
}
