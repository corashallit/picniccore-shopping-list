package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import earth.cora.picnic.ui.a11y.Decorative
import earth.cora.picnic.ui.theme.DecorSticker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.ui.theme.gen.BasketManifest
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.binderGround
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
 * first. Nothing under reduced motion (§12.3): the sticker keeps its pose. Each pose it is drawn at is reported to the
 * test probe as `twinkle<index>` (its turn and scale).
 */
@Composable
fun Modifier.eggTwinkle(state: SurpriseState, index: Int = 0): Modifier {
    val id = state.kirakiraId
    if (id == null || !state.enabled) return this
    val probe = LocalEggProbe.current
    if (LocalReducedMotion.current) {
        if (probe != null) SideEffect { probe.record("twinkle$index", 0L, Rect.Zero, 0f, 1f) }
        return this
    }
    val t by rememberEffectTime(id, 600L + index * 60L)
    val pose = EggBeats.twinkle(t - index * 60L)
    return graphicsLayer {
        rotationZ = pose.rotation
        scaleX = pose.scaleX
        scaleY = pose.scaleY
        probe?.record("twinkle$index", t, Rect.Zero, pose.rotation, pose.scaleX)
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
    earth.cora.picnic.ui.theme.Families.strawberry.deep,
    earth.cora.picnic.ui.theme.Families.ramune.deep,
    earth.cora.picnic.ui.theme.Families.mint.deep,
    earth.cora.picnic.ui.theme.Families.lemon.deep,
    earth.cora.picnic.ui.theme.Families.grape.deep,
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
 * scrolls with its strip). Draws nothing unless it is midnight and [sectionId] is the frozen aisle. Decorative. It never
 * takes room from the tab (§4.10 tab fit, §12.1 "none hides information"): give it its own width after the tab's fitted
 * one, as [TabMetaDoodle] does, never a share of the tab's row.
 */
@Composable
fun SnackDoodle(state: SurpriseState, sectionId: String, modifier: Modifier = Modifier) {
    if (!state.enabled || state.snackSectionId != sectionId) return
    SnackDoodleContent(modifier)
}

/** True when egg 5's snack doodle shows by [sectionId]'s tab. */
fun SurpriseState.snackDoodleAt(sectionId: String): Boolean = enabled && snackSectionId == sectionId

/**
 * A folded strip's words with egg 5's doodle (§4.10, §12.2): the [tab] is laid out first at its own fitted width (§4.10
 * Fit: 20 sp down to 16 sp, then an ellipsis), then the [meta] line under it; the [doodle] (null: none) goes beside the
 * tab only in the room the tab leaves, else after the meta line, else on a line of its own under it. The doodle never
 * shrinks the tab. The layout is as wide as the farthest of the three ends (the strip's ribbon is placed after it).
 */
@Composable
fun TabMetaDoodle(
    tab: @Composable () -> Unit,
    meta: @Composable () -> Unit,
    doodle: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    gap: androidx.compose.ui.unit.Dp = 4.dp,
) {
    androidx.compose.ui.layout.Layout(
        contents = listOf(tab, meta, doodle ?: {}),
        modifier = modifier,
    ) { (tabM, metaM, doodleM), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val t = tabM.map { it.measure(loose) }
        val m = metaM.map { it.measure(loose) }
        val d = doodleM.map { it.measure(androidx.compose.ui.unit.Constraints()) }
        val tabW = t.maxOfOrNull { it.width } ?: 0
        val tabH = t.maxOfOrNull { it.height } ?: 0
        val metaW = m.maxOfOrNull { it.width } ?: 0
        val metaH = m.maxOfOrNull { it.height } ?: 0
        val dW = d.maxOfOrNull { it.width } ?: 0
        val dH = d.maxOfOrNull { it.height } ?: 0
        val g = gap.roundToPx()
        val lift = 2.dp.roundToPx()
        val max = constraints.maxWidth
        var dx = 0
        var dy = 0
        if (d.isNotEmpty()) {
            when {
                tabW + g + dW <= max -> {
                    dx = tabW + g
                    dy = -lift
                }
                metaW + g + dW <= max -> {
                    dx = metaW + g
                    dy = tabH + metaH - dH
                    if (dx < tabW) dy = maxOf(dy, tabH)
                }
                else -> {
                    dx = g
                    dy = tabH + metaH
                }
            }
        }
        val width = maxOf(tabW, metaW, if (d.isEmpty()) 0 else dx + dW).coerceIn(constraints.minWidth, max)
        val height = maxOf(tabH + metaH, if (d.isEmpty()) 0 else dy + dH).coerceAtLeast(constraints.minHeight)
        layout(width, height) {
            t.forEach { it.place(0, 0) }
            m.forEach { it.place(0, tabH) }
            d.forEach { it.place(dx, dy) }
        }
    }
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
 * Egg 2's walk, shared by the overlay and the list (§12.2: "never crossing item text", §4.1): the ladybug walks along its
 * folded strip drawn by the STRIP itself ([StripLadybug]), so it scrolls with the strip, never lags it, and is clipped
 * with the list (under the binder top, behind the add bar). The overlay keeps its time and ends it ([show]); a list that
 * places no StripLadybug leaves the overlay to draw it. Provide one per list with [LocalLadybugHome] (EggListHost does).
 */
@androidx.compose.runtime.Stable
class LadybugHome(internal val anchors: earth.cora.picnic.ui.list.motion.AnchorRegistry? = null) {
    internal var fx by mutableStateOf<LadybugFx?>(null)
    internal var time by mutableStateOf<androidx.compose.runtime.State<Long>?>(null)

    /** The strips that can draw it (composed now). */
    internal val strips: MutableSet<String> = androidx.compose.runtime.snapshots.SnapshotStateSet()

    internal fun show(fx: LadybugFx, time: androidx.compose.runtime.State<Long>) {
        if (this.fx != fx) this.fx = fx
        if (this.time !== time) this.time = time
    }

    internal fun clear(id: Long) {
        if (fx?.id != id) return
        fx = null
        time = null
    }
}

/** The list's [LadybugHome] (null: the overlay draws the whole walk itself). */
val LocalLadybugHome = androidx.compose.runtime.staticCompositionLocalOf<LadybugHome?> { null }

/**
 * Egg 2: the ladybug walking [sectionId]'s folded strip. Call it inside the strip's box whose bottom is the strip card's
 * bottom edge and whose left is the card's left (the list's own strip box), after the strip's content. It walks the
 * bottom edge from 22 dp in, in the gap under the card (past no words), then flies off the nearer side of the list.
 * Decorative.
 */
@Composable
fun BoxScope.StripLadybug(sectionId: String) {
    val home = LocalLadybugHome.current ?: return
    DisposableEffect(home, sectionId) {
        home.strips += sectionId
        onDispose { home.strips -= sectionId }
    }
    val fx = home.fx?.takeIf { it.sectionId == sectionId } ?: return
    val t = home.time?.value ?: return
    var origin by remember { mutableStateOf<Offset?>(null) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val width = androidx.compose.ui.platform.LocalView.current.width.toFloat()
    val probe = LocalEggProbe.current
    Box(
        Modifier
            .matchParentSize()
            .onGloballyPositioned {
                origin = it.positionInWindow()
                box = it.size
            },
    ) {
        val o = origin ?: return@Box
        if (box == IntSize.Zero) return@Box
        val d = LocalDensity.current.density
        val list = home.anchors?.bounds(EggAnchors.LIST)
        val left = (list?.left ?: 0f) - o.x
        val right = (list?.right ?: width) - o.x
        val (pose, flying) = LadybugMotion.at(Offset(22 * d, box.height + 2 * d), t, d, left, right) ?: return@Box
        PlacedSticker(
            if (flying) "ladybugfly" else "ladybug",
            pose,
            LadybugMotion.SIZE_DP.dp,
            // Where it is drawn (window px), as laid out, for the test probe.
            placed = Modifier.onGloballyPositioned { c ->
                probe?.record("ladybug", t, Rect(c.positionInWindow(), c.size.let { androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }), pose.rotation)
            },
        )
    }
}

/**
 * Egg 1: the sticker by the title (the sun, the Dusk moon) hides while the transition draws its own sun and moon over
 * it. Not in Glow: the static Glow moon stays. WP5's SunSticker modifier.
 */
fun Modifier.eggTitleSticker(state: SurpriseState, variant: ThemeVariant): Modifier {
    val hide = state.enabled && state.duskFx != null && variant != ThemeVariant.Glow
    return graphicsLayer { alpha = if (hide) 0f else 1f }
}

/**
 * Egg 1: a tap on the sticker by the title in [variant]. The sun (Day, Golden) counts toward Dusk; the Dusk moon undoes
 * it; the Glow moon (system dark, midnight) is static. Nothing with Easter eggs off. WP5's BinderTopBar `onSunTap`.
 */
fun SurpriseEngine.onTitleStickerTap(state: SurpriseState, variant: ThemeVariant) {
    when {
        !state.enabled -> Unit
        variant == ThemeVariant.Glow -> Unit
        state.dusk -> onMoonTap()
        else -> onSunTap()
    }
}

/**
 * A page sticker (§4.1 decor taxonomy: the bee, the ladybug, the nap cat, the umbrella; at most 3 per screen). Test-visible
 * only: DecorBudgetTest counts the nodes carrying it; nothing reaches accessibility services (the node is [Decorative]).
 */
val PageSticker: SemanticsPropertyKey<String> = SemanticsPropertyKey("PageSticker")

/** Marks a page sticker named [name]: no semantics besides the test-visible markers ([tag] for tests). */
fun Modifier.pageSticker(name: String, tag: String? = null): Modifier = clearAndSetSemantics {
    this[Decorative] = Unit
    this[PageSticker] = name
    if (tag != null) testTag = tag
}

/**
 * The list's standing bee (§4.1, §4.27): a 22 dp bee flying a figure of eight over the current tray's basket, one lap
 * per 3.2 s in 12 fps poses, tilted toward where it flies; still under reduced motion. A page sticker in a 64 × 30 dp
 * box: WP5's tray places it over its basket, as the practice list does.
 */
@Composable
fun TrayBee(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = !reduced)
    val pose = if (reduced) EggBeats.beeLoop(0) else EggBeats.beeLoop(t)
    Box(modifier.size(64.dp, 30.dp)) {
        DecorSticker(
            "bee",
            Modifier
                .offset((pose.center.x - 11f).dp, (pose.center.y - 11f).dp)
                .graphicsLayer { rotationZ = pose.rotation }
                .pageSticker("bee"),
            size = 22.dp,
        )
    }
}

/**
 * The list's standing ladybug on the open tray's end cap (§4.1, §4.27): 14 dp, waddling ±5° every 250 ms (still under
 * reduced motion). It steps off while the nap cat and the umbrella both show ([SurpriseState.ladybugStepsOff]), so the
 * screen never holds more than 3 page stickers. A page sticker.
 */
@Composable
fun EndCapLadybug(state: SurpriseState, modifier: Modifier = Modifier) {
    if (state.ladybugStepsOff) return
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = !reduced)
    val waddle = if (reduced) 0f else if ((t / LadybugMotion.WADDLE_MS) % 2 == 0L) -5f else 5f
    DecorSticker("ladybug", modifier.graphicsLayer { rotationZ = 90f + waddle }.pageSticker("ladybug"), size = 14.dp)
}

/**
 * Egg 3's last stretch, shared by the overlay and the list (§12.2, §4.1): the sticker rolls home along its own line,
 * drawn by its ROW beneath the row's words (RowRoller), so the words stay whole and on top. The overlay publishes the
 * travel ([show]); a row showing [RollingFx.itemId] draws it from the drop onwards. A list that has no RowRoller for the
 * item (it is not on screen) leaves the overlay to draw it. Provide one per list with [LocalRollHome].
 */
@androidx.compose.runtime.Stable
class RollHome {
    internal var fx by mutableStateOf<RollingFx?>(null)

    /** The travel in WINDOW px. */
    internal var path by mutableStateOf<RollingPath?>(null)
    internal var time by mutableStateOf<androidx.compose.runtime.State<Long>?>(null)

    /** The items whose rows can draw it. */
    internal val rows: MutableSet<String> = androidx.compose.runtime.snapshots.SnapshotStateSet()

    internal fun show(fx: RollingFx, windowPath: RollingPath, time: androidx.compose.runtime.State<Long>) {
        if (this.fx != fx) this.fx = fx
        if (path != windowPath) path = windowPath
        if (this.time !== time) this.time = time
    }

    internal fun clear(id: Long) {
        if (fx?.id != id) return
        fx = null
        path = null
        time = null
    }
}

/** The list's [RollHome] (null: the overlay draws the whole roll itself). */
val LocalRollHome = androidx.compose.runtime.staticCompositionLocalOf<RollHome?> { null }

/**
 * Egg 3: the rolling sticker on its own line, beneath the row's words. Call it inside the row's box BEFORE the row's
 * content (so the words draw over it), filling the row ([itemId]: the row's item).
 */
@Composable
fun BoxScope.RowRoller(itemId: String) {
    val home = LocalRollHome.current ?: return
    DisposableEffect(home, itemId) {
        home.rows += itemId
        onDispose { home.rows -= itemId }
    }
    val fx = home.fx?.takeIf { it.itemId == itemId } ?: return
    val path = home.path ?: return
    val t = home.time?.value ?: return
    if (!RollingMotion.home(path, t)) return
    val pose = RollingMotion.at(path, t) ?: return
    var origin by remember { mutableStateOf<Offset?>(null) }
    val d = LocalDensity.current.density
    val half = RollingMotion.SIZE_DP * d / 2f
    Box(
        Modifier
            .matchParentSize()
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        val o = origin ?: return@Box
        earth.cora.picnic.ui.theme.ItemIcon(
            fx.icon,
            contentDescription = null,
            size = RollingMotion.SIZE_DP.dp,
            holo = fx.holo,
            aisleIndex = fx.aisleIndex,
            modifier = Modifier
                .clearAndSetSemantics { this[Decorative] = Unit }
                .offset { androidx.compose.ui.unit.IntOffset((pose.center.x - o.x - half).toInt(), (pose.center.y - o.y - half).toInt()) }
                .graphicsLayer {
                    rotationZ = pose.rotation
                    scaleX = pose.scaleX
                    scaleY = pose.scaleY
                },
        )
        Probe("rollhome", t, androidx.compose.ui.geometry.Rect(pose.center.x - half, pose.center.y - half, pose.center.x + half, pose.center.y + half), pose.rotation, Offset.Zero)
    }
}

/**
 * Egg 1: the Dusk palette washes in (§12.2: "the Dusk variant washes in (bands, gutter sparkles)"). The list places it
 * first inside its PicnicBackground (beneath everything else): while the change plays, the ground it had (Day, or Golden
 * at golden hour) lies over the Dusk ground and fades out in 12 fps steps over the moon's rise (600 ms after the sun has
 * set), or over 120 ms under reduced motion (§12.3); the moon tapped, the Dusk ground lies over the Day ground and fades
 * out the same way. Nothing in Glow (the Glow moon is static). Decorative.
 */
@Composable
fun BoxScope.DuskWash(state: SurpriseState) {
    val fx = state.duskFx?.takeIf { state.enabled } ?: return
    // In Glow (the phone turned dark mid-change) there is no Day or Dusk ground to wash between: the Glow ground stays.
    if (PicnicTheme.variant == ThemeVariant.Glow) return
    val total = when {
        fx.reduced -> DuskMotion.FADE_MS
        fx.toDusk -> DuskMotion.DURATION
        else -> DuskMotion.UNDO_MS
    }
    val t by rememberEffectTime("wash:${fx.id}", total)
    val from = remember(fx.id) {
        when {
            !fx.toDusk -> ThemeVariant.Dusk
            state.golden -> ThemeVariant.Golden
            else -> ThemeVariant.Day
        }
    }
    val a = duskWashAlpha(fx, t)
    if (a <= 0f) return
    Box(
        Modifier
            .matchParentSize()
            .clearAndSetSemantics { this[Decorative] = Unit }
            .graphicsLayer { alpha = a }
            .binderGround(earth.cora.picnic.ui.theme.colorsFor(from)),
    )
}

/**
 * How much of the ground being left still shows over the new one at [elapsed] in [fx]: all of it until the sun has set
 * (the palette is still the old one then), then 1 → 0 in 12 fps steps over the moon's rise; 1 → 0 over the whole undo;
 * 1 → 0 over 120 ms under reduced motion.
 */
fun duskWashAlpha(fx: DuskFx, elapsed: Long): Float = when {
    fx.reduced -> 1f - (elapsed.toFloat() / DuskMotion.FADE_MS).coerceIn(0f, 1f)
    fx.toDusk -> if (elapsed < DuskMotion.SINK_MS) 1f else 1f - (stepped(elapsed - DuskMotion.SINK_MS, POSE_MS).toFloat() / DuskMotion.RISE_MS).coerceIn(0f, 1f)
    else -> 1f - (stepped(elapsed, POSE_MS).toFloat() / DuskMotion.UNDO_MS).coerceIn(0f, 1f)
}
