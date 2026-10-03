package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.platform.NoOpFeedback
import earth.cora.picnic.platform.RepeatingCue
import earth.cora.picnic.platform.BONUS_JINGLE_PERIOD_MS
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.HankoBurst
import earth.cora.picnic.ui.list.motion.MotionChoreographer
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.HankoStamp
import earth.cora.picnic.ui.theme.HankoText
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage
import earth.cora.picnic.ui.theme.binderGround
import earth.cora.picnic.ui.theme.holdOnFrameClock
import earth.cora.picnic.ui.theme.rememberLoopTime
import androidx.compose.material3.Text
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The egg overlays (docs/DESIGN_STICKER.md §12, §13.2): the pieces that play over the list, mounted in WP6's overlay
 * host (MotionOverlay's `eggs` slot), above the list and the add bar, below sheets (§6.1 item 4). They find what they
 * play over through the AnchorRegistry (`sun`, `lcd`, `addbar`, `row:<id>`, `check:<id>`, `basket:<section>`, and the
 * WP9 keys below), play each effect on the frame clock under the fixed motion scale (§8.8), and report back to the
 * engine when it has played out. Decor (§4.1): no semantics, never over text (text keep-outs clip the bursts), at the
 * edges only. Owner: WP9.
 */

/** The anchor keys WP9 adds to the registry (WP5's list registers them; the overlays fall back without them). */
object EggAnchors {
    /** A folded strip's whole card (the ladybug walks its bottom edge). */
    fun strip(sectionId: String): String = "strip:$sectionId"

    /** A section's tab (the midnight snack star and doodle sit beside it). */
    fun tab(sectionId: String): String = "tab:$sectionId"

    /** The screen title (the holo title of egg 7, the Dusk transition's horizon). */
    const val TITLE: String = "title"

    /** Every text the overlays must keep clear of: register each text node's box as `text:<anything>`. */
    const val TEXT_PREFIX: String = "text:"
}

/** Test hook: every frame, each overlay piece reports where it is drawn (window px) and how it is turned. */
class EggProbe {
    data class Frame(val tag: String, val time: Long, val bounds: Rect, val rotation: Float, val center: Offset)

    private val recorded = ArrayList<Frame>()

    val frames: List<Frame> get() = synchronized(recorded) { recorded.toList() }

    fun record(tag: String, time: Long, bounds: Rect, rotation: Float) {
        synchronized(recorded) { recorded += Frame(tag, time, bounds, rotation, bounds.center) }
    }

    fun clear() = synchronized(recorded) { recorded.clear() }
}

val LocalEggProbe = staticCompositionLocalOf<EggProbe?> { null }

/** Test tags of the interactive egg pieces. */
object EggTags {
    const val RECEIPT: String = "egg_receipt"
    const val NAP_CAT: String = "egg_nap_cat"
    const val UMBRELLA: String = "egg_umbrella"
    const val OVERLAY: String = "egg_overlays"
}

/**
 * Every egg's overlay. [anchors] is the list's registry; [toasts] its toast host (the eggs' toasts show there);
 * [motion] its choreographer (the pet chomps at each landing); [onOpenAlbum] is the back pocket toast's "Look".
 */
@Composable
fun BoxScope.EggOverlays(
    engine: SurpriseEngine,
    anchors: AnchorRegistry,
    toasts: ToastHostState? = null,
    motion: MotionChoreographer? = null,
    feedback: Feedback = NoOpFeedback,
    onOpenAlbum: () -> Unit = {},
    /**
     * True when the list draws the nap cat and the snack doodle in its own content ([NapCatInBasket], [SnackDoodle]),
     * so they scroll with their strip; false draws them here from the anchors (a list that does not place them).
     */
    listPlacesDecor: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val state by engine.state.collectAsState()
    val reduced = LocalReducedMotion.current
    SideEffect { engine.setReducedMotion(reduced) }
    val resources = LocalResources.current
    val openAlbum by rememberUpdatedState(onOpenAlbum)
    LaunchedEffect(engine, toasts) {
        val host = toasts ?: return@LaunchedEffect
        engine.events.collect { toast ->
            host.show(
                when (toast) {
                    EggToast.RainToday -> ToastMessage(resources.getString(R.string.egg_toast_rain), kind = "egg-rain")
                    EggToast.BonusStage -> ToastMessage(resources.getString(R.string.egg_toast_bonus), kind = "egg-bonus")
                    EggToast.ReceiptSaved -> ToastMessage(resources.getString(R.string.egg_toast_receipt), kind = "egg-receipt")
                    EggToast.BackPocket -> ToastMessage(
                        resources.getString(R.string.egg_toast_back_pocket),
                        kind = "egg-pocket",
                        actionLabel = resources.getString(R.string.egg_toast_look),
                        onAction = { openAlbum() },
                        undo = false,
                    )
                },
            )
        }
    }
    LaunchedEffect(engine, motion) { motion?.events?.collect { engine.onMotionEvent(it) } }
    // Egg 10: the 8-bit jingle every 4 s while the stage lasts and the list is resumed (sound on, §14.3).
    RepeatingCue(feedback, Cue.BONUS_JINGLE, BONUS_JINGLE_PERIOD_MS, active = state.enabled && state.bonusStage)
    // The LCD's words hold their time on the frame clock, then the LCD returns to the count (§9 LCD word floor).
    state.lcdWord?.let { word ->
        if (word.holdMs > 0) {
            LaunchedEffect(word.id) {
                withContext(FixedMotionScale) { holdOnFrameClock(word.holdMs) }
                engine.effectDone(word.id)
            }
        }
    }
    state.kirakiraId?.let { id ->
        LaunchedEffect(id) {
            withContext(FixedMotionScale) { holdOnFrameClock(EggRules.KIRAKIRA_MS) }
            engine.effectDone(id)
        }
    }
    state.petVisitId?.let { id -> EffectTimer(id, EggRules.FED_MS) { engine.effectDone(id) } }
    state.petChompId?.let { id -> EffectTimer(id, EggRules.CHOMP_MS) { engine.effectDone(id) } }
    if (!state.enabled) return

    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .matchParentSize()
            .testTag(EggTags.OVERLAY)
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
      androidx.compose.runtime.CompositionLocalProvider(LocalOverlayOrigin provides origin) {
        val local = { r: Rect -> r.translate(-origin) }
        val keepOut = { keepOutRects(anchors).map(local) }
        state.duskFx?.let { DuskSun(it, anchors.bounds(AnchorRegistry.SUN)?.let(local), engine, feedback) }
        if (!listPlacesDecor) state.snackSectionId?.let { SnackStar(it, anchors, local) }
        if (state.umbrella) anchors.bounds(AnchorRegistry.ADD_BAR)?.let { Umbrella(local(it)) }
        // The cat curls up once the chain that finished the other stops has played out (§12.3: chain end + 0.5 s).
        val chainsDone = motion?.current?.value?.let { p -> p.flights.isEmpty() && p.bursts.isEmpty() && p.celebration == null } ?: true
        if (!listPlacesDecor) state.napSectionId?.let { NapCat(it, anchors, local, engine, chainsDone) }
        state.napLeaving?.let { NapLeaving(it, anchors, local, engine) }
        state.ladybug?.let { Ladybug(it, anchors, local, engine) }
        // The bursts: clipped clear of every text box.
        KeepOut(keepOut) {
            state.petalsId?.let { Petals(it, anchors, local, engine) }
            state.antsId?.let { Ants(it, anchors, local, engine) }
            state.confettiId?.let { Confetti(it, anchors, local, engine) }
            state.shinyBursts.forEach { ShinyBurst(it, anchors, local, engine) }
        }
        state.rolling?.let { Rolling(it, anchors, local, engine, feedback) }
        state.jackpot?.let { Jackpot(it, anchors, local, engine, feedback) }
        state.receipt?.let { ReceiptPrint(it, anchors, local, engine, feedback) }
        state.receiptTuckId?.let { ReceiptPoof(it, engine) }
      }
    }
}

/* ------------------------------------------------------------------------------------------------
 * Plumbing.
 * ------------------------------------------------------------------------------------------------ */

/** Text boxes registered as `text:*` anchors (window px). */
internal fun keepOutRects(anchors: AnchorRegistry): List<Rect> =
    anchors.keys().filter { it.startsWith(EggAnchors.TEXT_PREFIX) }.mapNotNull { anchors.bounds(it) }

/** Draws [content] with every rect of [rects] cut out (§4.1: bursts are never drawn over text). */
@Composable
private fun KeepOut(rects: () -> List<Rect>, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                val cut = rects()
                if (cut.isEmpty()) {
                    drawContent()
                } else {
                    val path = Path().apply { cut.forEach { addRect(it) } }
                    clipPath(path, ClipOp.Difference) { this@drawWithContent.drawContent() }
                }
            },
        content = content,
    )
}

/**
 * Milliseconds since effect [key] first showed, on the frame clock (recomposes every frame while it runs); calls
 * [onDone] once [durationMs] has passed (null: runs until the caller stops reading it).
 */
@Composable
internal fun rememberEffectTime(key: Any, durationMs: Long?, onDone: () -> Unit = {}): State<Long> {
    val elapsed = remember(key) { mutableLongStateOf(0L) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(key) {
        withContext(FixedMotionScale) {
            val start = withFrameMillis { it }
            while (true) {
                val t = withFrameMillis { it } - start
                elapsed.longValue = t
                if (durationMs != null && t >= durationMs) {
                    done()
                    break
                }
            }
        }
    }
    return elapsed
}

@Composable
private fun EffectTimer(key: Long, ms: Long, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(key) {
        withContext(FixedMotionScale) { holdOnFrameClock(ms) }
        done()
    }
}

/** Reports a piece's window bounds to the test probe. */
@Composable
private fun Probe(tag: String, time: Long, localBounds: Rect, rotation: Float, origin: Offset) {
    val probe = LocalEggProbe.current ?: return
    SideEffect { probe.record(tag, time, localBounds.translate(origin), rotation) }
}

/** A sticker of [size] centred at [pose] (overlay px), decorative; a [page] sticker counts toward the §4.1 budget. */
@Composable
private fun PlacedSticker(name: String, pose: EggPose, size: Dp, modifier: Modifier = Modifier, page: String? = null, tag: String? = null) {
    val half = with(LocalDensity.current) { size.toPx() / 2f }
    DecorSticker(
        name,
        modifier
            .offset { IntOffset((pose.center.x - half).roundToInt(), (pose.center.y - half).roundToInt()) }
            .graphicsLayer {
                rotationZ = pose.rotation
                scaleX = pose.scaleX
                scaleY = pose.scaleY
                alpha = pose.alpha
            }
            .then(if (page != null) Modifier.pageSticker(page, tag) else Modifier),
        size = size,
    )
}

private fun Rect.sized(center: Offset, size: Float): Rect = Rect(center.x - size / 2, center.y - size / 2, center.x + size / 2, center.y + size / 2)

@Composable
private fun origin(): Offset = LocalOverlayOrigin.current

private val LocalOverlayOrigin = staticCompositionLocalOf { Offset.Zero }

/* ------------------------------------------------------------------------------------------------
 * Egg 1: the sun sets behind its horizon and the moon rises; the moon tapped, the sun comes back.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun DuskSun(fx: DuskFx, sun: Rect?, engine: SurpriseEngine, feedback: Feedback) {
    val total = when {
        fx.reduced -> DuskMotion.FADE_MS
        fx.toDusk -> DuskMotion.DURATION
        else -> DuskMotion.UNDO_MS
    }
    val t by rememberEffectTime(fx.id, total) { engine.effectDone(fx.id) }
    if (fx.toDusk && !fx.reduced) {
        LaunchedEffect(fx.id) {
            withContext(FixedMotionScale) { holdOnFrameClock(DuskMotion.SINK_MS) }
            engine.onDuskSunset(fx.id)
            // "kira" with the moon's sparkle burst.
            withContext(FixedMotionScale) { holdOnFrameClock(DuskMotion.BURST_AT - DuskMotion.SINK_MS) }
            feedback.cue(Cue.MAGIC_APPEAR)
        }
    }
    val box = sun ?: return
    val d = LocalDensity.current
    val sizeDp = with(d) { box.width.toDp() }
    Box(
        Modifier
            .offset { IntOffset(box.left.roundToInt(), box.top.roundToInt()) }
            .size(sizeDp, with(d) { box.height.toDp() })
            .decorative()
            // The ground patch hides the sticker under it while the transition draws its own (§2.12: window-aligned, so
            // it is the binder exactly as it is there).
            .binderGround(PicnicTheme.colors)
            .clipToBounds(),
    ) {
        val sticker = 22.dp
        val center = Offset(box.width / 2f, box.height / 2f)
        val dropPx = { dp: Float -> dp * d.density }
        when {
            fx.reduced -> {
                val a = (t.toFloat() / DuskMotion.FADE_MS).coerceIn(0f, 1f)
                PlacedSticker(if (fx.toDusk) "moon" else "sun", EggPose(center, alpha = a), sticker)
            }
            fx.toDusk -> {
                DuskMotion.sunDrop(t)?.let { PlacedSticker("sun", EggPose(center + Offset(0f, dropPx(it))), sticker) }
                DuskMotion.moonDrop(t)?.let { PlacedSticker("moon", EggPose(center + Offset(0f, dropPx(it))), sticker) }
            }
            else -> {
                val (moon, drop) = DuskMotion.undo(t)
                PlacedSticker(if (moon) "moon" else "sun", EggPose(center + Offset(0f, dropPx(drop))), sticker)
            }
        }
    }
    // The six-star sparkle burst round the moon once it has risen, outside its 48 dp box (never on the title: it bursts
    // up and to the right, where the bar has nothing but binder).
    if (fx.toDusk && !fx.reduced) {
        DuskMotion.burst(t)?.let { (reach, scale) ->
            val c = box.center
            for (i in 0 until 6) {
                val a = Math.toRadians((-150.0 + i * 30.0))
                val r = 26f * d.density * reach
                PlacedSticker("star4", EggPose(Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat()), 0f, scale), 10.dp)
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = sizeDp
}

/* ------------------------------------------------------------------------------------------------
 * Egg 5: the snack star and "snack?" doodle by the frozen aisle's tab.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun SnackStar(sectionId: String, anchors: AnchorRegistry, local: (Rect) -> Rect) {
    val tab = anchors.bounds(EggAnchors.tab(sectionId))?.let(local)
    val basket = anchors.bounds(AnchorRegistry.basketKey(sectionId))?.let(local)
    val d = LocalDensity.current.density
    // Beside the tab, outside it; without a tab anchor, a little left of the basket.
    val at = when {
        tab != null -> Offset(tab.right + 4 * d, tab.top - 2 * d)
        basket != null -> Offset(basket.left - 74 * d, basket.top + 4 * d)
        else -> return
    }
    SnackDoodleContent(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) })
}

/** The snack-time star sticker and the "snack?" doodle (decorative). */
@Composable
internal fun SnackDoodleContent(modifier: Modifier = Modifier) {
    Box(modifier.decorative()) {
        DecorSticker("glowstar", Modifier.offset(y = 2.dp), size = 20.dp)
        Text(
            androidx.compose.ui.res.stringResource(R.string.egg_snack_doodle),
            style = PicnicTheme.type.doodle.copy(fontSize = 13.sp, color = PicnicTheme.colors.doodle, fontStyle = FontStyle.Normal),
            modifier = Modifier.offset(x = 22.dp, y = 2.dp).graphicsLayer { rotationZ = -4f },
        )
    }
}

/* ------------------------------------------------------------------------------------------------
 * Egg 16: the umbrella sheltering the add bar's left end.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun Umbrella(addBar: Rect) {
    val reduced = LocalReducedMotion.current
    val t by rememberEffectTime("umbrella", if (reduced) 120 else 320)
    val d = LocalDensity.current.density
    val size = 36.dp
    val center = Offset(addBar.left + 30 * d, addBar.top)
    val pose = if (reduced) EggPose(center, alpha = (t / 120f).coerceIn(0f, 1f)) else EggBeats.magicAppear(t).copy(center = center, rotation = -10f + EggBeats.magicAppear(t).rotation)
    PlacedSticker("umbrella", pose, size, page = "umbrella", tag = EggTags.UMBRELLA)
    Probe("umbrella", t, Rect(Offset.Zero, Size.Zero).sized(center, 36 * d), pose.rotation, origin())
}

/* ------------------------------------------------------------------------------------------------
 * Egg 8: the nap cat in the still-empty basket; a tap sends it off.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun NapCat(sectionId: String, anchors: AnchorRegistry, local: (Rect) -> Rect, engine: SurpriseEngine, chainsDone: Boolean) {
    if (!rememberNapSettled(sectionId, chainsDone)) return
    val basket = anchors.bounds(AnchorRegistry.basketKey(sectionId))?.let(local) ?: return
    val o = origin()
    val mouth = anchors.mouth(AnchorRegistry.basketKey(sectionId))?.let { it - o }
    val d = LocalDensity.current.density
    val rimY = (mouth?.y ?: (basket.top + basket.height * 0.45f)) + 6 * d
    val center = Offset(basket.center.x - 2 * d, rimY - 9 * d)
    NapCatPiece(sectionId, center, rimY, engine, o)
}

/**
 * The cat waits for the check-off chain that finished the other stops (the hop, the stamp's burst) to play out, then a
 * beat ([NapMotion.SETTLE_DELAY_MS]), then curls up (§12.3: chain end + 0.5 s). True once it may show.
 */
@Composable
internal fun rememberNapSettled(sectionId: String, chainsDone: Boolean): Boolean {
    val settled = remember(sectionId) { mutableStateOf(false) }
    if (!settled.value && chainsDone) {
        LaunchedEffect(sectionId) {
            withContext(FixedMotionScale) { holdOnFrameClock(NapMotion.SETTLE_DELAY_MS) }
            settled.value = true
        }
    }
    return settled.value
}

/**
 * The curled cat at [center] (px in the enclosing box), clipped at the basket's rim [rimY] so it lies IN the basket,
 * breathing, with "z" / "Z" floating up, and its 48 dp tap target ([SurpriseEngine.onNapTap]). [probeOrigin] maps the
 * box to the window for the test probe.
 */
@Composable
internal fun NapCatPiece(sectionId: String, center: Offset, rimY: Float, engine: SurpriseEngine, probeOrigin: Offset) {
    val reduced = LocalReducedMotion.current
    val since by rememberEffectTime("nap:$sectionId", 320)
    val breathing by rememberLoopTime(enabled = !reduced)
    val d = LocalDensity.current.density
    val catSize = 34.dp
    val pop = if (reduced) EggPose(center, alpha = (since / 120f).coerceIn(0f, 1f)) else EggBeats.magicAppear(since).copy(center = center)
    val breath = if (reduced) 1f else NapMotion.breath(breathing)
    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent { clipAbove(right = size.width, bottom = rimY) { this@drawWithContent.drawContent() } },
    ) {
        PlacedSticker("cat", pop.copy(scaleX = pop.scaleX * breath, scaleY = pop.scaleY * breath), catSize, page = "nap cat")
    }
    if (!reduced) {
        val (z, big) = NapMotion.z(breathing)
        val zAt = Offset(center.x - 8 * d - z * 10 * d, center.y - 14 * d - z * 22 * d)
        Text(
            if (big) "Z" else "z",
            style = PicnicTheme.type.doodle.copy(fontSize = 13.sp, color = PicnicTheme.colors.doodle),
            modifier = Modifier
                .decorative()
                .offset { IntOffset(zAt.x.roundToInt(), zAt.y.roundToInt()) }
                .graphicsLayer { alpha = 1f - z },
        )
    }
    // The 48 dp target: a tap sends it off (no TalkBack node: a page sticker, §4.1).
    Box(
        Modifier
            .offset { IntOffset((center.x - 24 * d).roundToInt(), (center.y - 24 * d).roundToInt()) }
            .requiredSize(48.dp)
            .testTag(EggTags.NAP_CAT)
            .pointerInput(sectionId) { detectTapGestures { engine.onNapTap() } },
    )
    Probe("nap", since, Rect(Offset.Zero, Size.Zero).sized(center, 34 * d), 0f, probeOrigin)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.clipAbove(right: Float, bottom: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) =
    clipRect(0f, -10_000f, right, bottom, ClipOp.Intersect, block)

@Composable
private fun NapLeaving(fx: NapLeaveFx, anchors: AnchorRegistry, local: (Rect) -> Rect, engine: SurpriseEngine) {
    val total = if (fx.reduced) 120L else NapMotion.LEAVE_MS
    val t by rememberEffectTime(fx.id, total) { engine.effectDone(fx.id) }
    val basket = anchors.bounds(AnchorRegistry.basketKey(fx.sectionId))?.let(local) ?: return
    val d = LocalDensity.current.density
    val center = Offset(basket.center.x - 2 * d, basket.top + basket.height * 0.45f - 3 * d)
    if (fx.reduced) {
        PlacedSticker("cat", EggPose(center, alpha = 1f - (t / 120f).coerceIn(0f, 1f)), 34.dp, page = "nap cat")
        return
    }
    val pose = NapMotion.leave(t, 160f) ?: return
    PlacedSticker("cat", EggPose(center + Offset(pose.center.x * d, pose.center.y * d), pose.rotation, pose.scaleX, pose.scaleY), 34.dp, page = "nap cat")
}

/* ------------------------------------------------------------------------------------------------
 * Egg 2: the ladybug's stroll.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun Ladybug(fx: LadybugFx, anchors: AnchorRegistry, local: (Rect) -> Rect, engine: SurpriseEngine) {
    val t by rememberEffectTime(fx.id, LadybugMotion.DURATION) { engine.effectDone(fx.id) }
    val d = LocalDensity.current.density
    // Remember where the strip was when it set off: it keeps walking where it started (it keeps going after release).
    val start = remember(fx.id) { mutableStateOf<Offset?>(null) }
    if (start.value == null) {
        val strip = anchors.bounds(EggAnchors.strip(fx.sectionId))?.let(local)
        val basket = anchors.bounds(AnchorRegistry.basketKey(fx.sectionId))?.let(local)
        start.value = when {
            strip != null -> Offset(strip.left + 22 * d, strip.bottom + 2 * d)
            basket != null -> Offset(14 * d + 22 * d, basket.bottom)
            else -> null
        }
    }
    val at = start.value ?: return
    val (pose, flying) = LadybugMotion.at(at, t, d) ?: return
    PlacedSticker(if (flying) "ladybugfly" else "ladybug", pose, LadybugMotion.SIZE_DP.dp)
    Probe("ladybug", t, Rect(Offset.Zero, Size.Zero).sized(pose.center, LadybugMotion.SIZE_DP * d), pose.rotation, origin())
}

/* ------------------------------------------------------------------------------------------------
 * Egg 4: petals.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun Petals(id: Long, anchors: AnchorRegistry, local: (Rect) -> Rect, engine: SurpriseEngine) {
    val t by rememberEffectTime(id, PetalMotion.TOTAL) { engine.effectDone(id) }
    val d = LocalDensity.current.density
    val bar = anchors.bounds(AnchorRegistry.ADD_BAR)?.let(local)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val petals = remember(id, w, h) { PetalMotion.petals(w, top = 150 * d, bottom = (bar?.top ?: h) - 30 * d, inset = 10 * d) }
        petals.forEachIndexed { i, p ->
            PetalMotion.at(p, t, d)?.let {
                PlacedSticker("petal", it, 14.dp)
                Probe("petal$i", t, Rect(Offset.Zero, Size.Zero).sized(it.center, 14 * d), it.rotation, origin())
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * Egg 17: the ant parade.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun Ants(id: Long, anchors: AnchorRegistry, local: (Rect) -> Rect, engine: SurpriseEngine) {
    val t by rememberEffectTime(id, AntMotion.DURATION) { engine.effectDone(id) }
    val bar = anchors.bounds(AnchorRegistry.ADD_BAR)?.let(local) ?: return
    val d = LocalDensity.current.density
    for (i in 0 until AntMotion.COUNT) {
        val pose = AntMotion.at(i, t, bar.right, bar.top, d) ?: continue
        val size = AntMotion.sizeDp(i)
        PlacedSticker(if (AntMotion.carriesStrawberry(i)) "ant" else "antcrumb", pose, size.dp)
        Probe("ant$i", t, Rect(Offset.Zero, Size.Zero).sized(pose.center, size * d), pose.rotation, origin())
    }
}

/* ------------------------------------------------------------------------------------------------
 * Egg 9: confetti.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun Confetti(id: Long, anchors: AnchorRegistry, local: (Rect) -> Rect, engine: SurpriseEngine) {
    val t by rememberEffectTime(id, ConfettiMotion.RAIN_MS) { engine.effectDone(id) }
    val d = LocalDensity.current.density
    val bar = anchors.bounds(AnchorRegistry.ADD_BAR)?.let(local)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val pieces = remember(id, w) { ConfettiMotion.pieces(w, d) }
        pieces.forEachIndexed { i, p ->
            ConfettiMotion.at(p, t, top = 0f, bottom = bar?.top ?: h, density = d)?.let {
                PlacedSticker(p.kind, it, p.sizeDp.dp)
                Probe("confetti$i", t, Rect(Offset.Zero, Size.Zero).sized(it.center, p.sizeDp * d), it.rotation, origin())
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * Egg 6: the shiny row's 8-sticker burst, out over the gutter (away from its text).
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun ShinyBurst(fx: ShinyFx, anchors: AnchorRegistry, local: (Rect) -> Rect, engine: SurpriseEngine) {
    val t by rememberEffectTime(fx.id, HankoBurst.DURATION) { engine.effectDone(fx.id) }
    val row = anchors.bounds(AnchorRegistry.rowKey(fx.itemId))?.let(local) ?: return
    val d = LocalDensity.current.density
    val particles = remember(fx.id) { HankoBurst.particles(fx.id.toInt()) }
    for (p in particles) {
        val pose = HankoBurst.at(p, t) ?: continue
        // Mirrored: the burst opens to the left of the sticker, into the gutter, never across the item's name.
        val c = row.center + Offset(-pose.dx * d, pose.dy * d)
        PlacedSticker(p.kind, EggPose(c, -pose.rotation, pose.scaleX, alpha = pose.alpha), p.sizeDp.dp)
    }
}

/* ------------------------------------------------------------------------------------------------
 * Egg 3: rolling produce.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun Rolling(fx: RollingFx, anchors: AnchorRegistry, local: (Rect) -> Rect, engine: SurpriseEngine, feedback: Feedback) {
    val d = LocalDensity.current.density
    val o = origin()
    // Its line: the new row once it is placed (the aisle may be opening in this very frame, so the anchors are read on
    // the frame clock, after layout); the folded strip it joins only if no row shows up; nothing at all, and the item is
    // simply on its line.
    val path = remember(fx.id) { mutableStateOf<RollingPath?>(null) }
    val lineKey = remember(fx.id) { mutableStateOf(AnchorRegistry.rowKey(fx.itemId)) }
    LaunchedEffect(fx.id) {
        withContext(FixedMotionScale) {
            var frames = 0
            while (path.value == null) {
                withFrameMillis { }
                frames++
                val stripKey = EggAnchors.strip(fx.sectionId)
                val key = when {
                    anchors.isLaidOut(AnchorRegistry.rowKey(fx.itemId)) -> AnchorRegistry.rowKey(fx.itemId)
                    frames >= RollingMotion.ROW_WAIT_FRAMES && anchors.isLaidOut(stripKey) -> stripKey
                    else -> null
                }
                val row = key?.let { anchors.bounds(it) }
                val mouth = anchors.mouth(AnchorRegistry.basketKey(fx.sectionId))
                val bar = anchors.bounds(AnchorRegistry.ADD_BAR)
                if (key != null && row != null && mouth != null && bar != null) {
                    val b = local(bar)
                    lineKey.value = key
                    path.value = RollingMotion.path(local(row).center, mouth - o, b.top, b.width, d, fx.hop)
                } else if (frames >= RollingMotion.GIVE_UP_FRAMES) {
                    engine.effectDone(fx.id)
                    break
                }
            }
        }
    }
    val laid = path.value ?: return
    val t by rememberEffectTime(fx.id, RollingMotion.DURATION) { engine.effectDone(fx.id) }
    // The list may still be scrolling its aisle into view: the row and the basket are followed where they are now (the
    // launch point in the add bar stays put, so the turn still follows the sideways travel exactly, θ = Δx ⁄ r).
    val liveRow = anchors.bounds(lineKey.value)?.let(local)?.center
    val liveMouth = anchors.mouth(AnchorRegistry.basketKey(fx.sectionId))?.let { it - o }
    val p = RollingMotion.follow(laid, liveRow, liveMouth)
    LaunchedEffect(fx.id) {
        withContext(FixedMotionScale) { holdOnFrameClock(RollingMotion.BUMP_AT) }
        feedback.cue(Cue.LAND)
        engine.onBasketHello(fx.sectionId)
    }
    val pose = RollingMotion.at(p, t) ?: return
    val size = RollingMotion.SIZE_DP.dp
    val half = RollingMotion.SIZE_DP * d / 2f
    ItemIcon(
        fx.icon,
        contentDescription = null,
        size = size,
        holo = fx.holo,
        aisleIndex = fx.aisleIndex,
        modifier = Modifier
            .decorative()
            .offset { IntOffset((pose.center.x - half).roundToInt(), (pose.center.y - half).roundToInt()) }
            .graphicsLayer {
                rotationZ = pose.rotation
                scaleX = pose.scaleX
                scaleY = pose.scaleY
            },
    )
    // The basket's hello: a rim heart rising off it at the bump.
    val heart = EggBeats.rimHeart(t - RollingMotion.BUMP_AT)
    if (heart != null) {
        val (rise, scale, alpha) = heart
        PlacedSticker("heart", EggPose(Offset(p.basketX, p.laneY + 10 * d - rise * d), 0f, scale, alpha = alpha), 14.dp)
    }
    Probe("roller", t, Rect(Offset.Zero, Size.Zero).sized(pose.center, RollingMotion.SIZE_DP * d), pose.rotation, origin())
}

/* ------------------------------------------------------------------------------------------------
 * Egg 13: JACK / POT!
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun Jackpot(fx: JackpotFx, anchors: AnchorRegistry, local: (Rect) -> Rect, engine: SurpriseEngine, feedback: Feedback) {
    val total = JackpotMotion.DURATION
    val t by rememberEffectTime(fx.id, total) { engine.effectDone(fx.id) }
    LaunchedEffect(fx.id) {
        withContext(FixedMotionScale) { holdOnFrameClock(if (fx.reduced) 0 else JackpotMotion.WOBBLE_MS) }
        feedback.cue(Cue.CAPSULE_POP)
    }
    val row = anchors.bounds(AnchorRegistry.rowKey(fx.itemId))?.let(local) ?: return
    val check = anchors.bounds(AnchorRegistry.checkKey(fx.itemId))?.let(local)
    val d = LocalDensity.current.density
    val stickerSize = with(LocalDensity.current) { row.width.toDp() }
    if (fx.reduced) {
        // The gold capsule fades to the sticker; JACK / POT! fades in and holds 1.8 s.
        val capsuleAlpha = 1f - (t / 120f).coerceIn(0f, 1f)
        if (capsuleAlpha > 0f) {
            ItemIcon(fx.icon.copy(key = earth.cora.picnic.core.catalog.IconCatalog.CAPSULE, customIconId = null), null, size = stickerSize, jackpot = true, modifier = Modifier.decorative().offset { IntOffset(row.left.roundToInt(), row.top.roundToInt()) }.graphicsLayer { alpha = capsuleAlpha })
        }
    } else {
        JackpotMotion.capsule(t)?.let { pose ->
            ItemIcon(
                fx.icon.copy(key = earth.cora.picnic.core.catalog.IconCatalog.CAPSULE, customIconId = null), null, size = stickerSize, jackpot = true,
                modifier = Modifier
                    .decorative()
                    .offset { IntOffset(row.left.roundToInt(), row.top.roundToInt()) }
                    .graphicsLayer {
                        rotationZ = pose.rotation
                        scaleX = pose.scaleX
                        scaleY = pose.scaleY
                        alpha = pose.alpha
                    },
            )
        }
    }
    val hanko = if (fx.reduced) {
        val since = t - JackpotMotion.SLAM_AT
        if (since < 0 || t >= total) null else EggPose(Offset.Zero, -14f, 1f, alpha = (since / 120f).coerceIn(0f, 1f) * (if (t > total - JackpotMotion.FADE_MS) (total - t) / JackpotMotion.FADE_MS.toFloat() else 1f))
    } else {
        JackpotMotion.hanko(t)
    }
    if (hanko != null) {
        // Over the row's checkbox column, where its GOT IT would sit: beside the sticker's row, never over its name.
        val center = check?.center?.let { it - Offset(6 * d, 0f) } ?: Offset(row.right + 240 * d, row.center.y)
        val half = 30 * d
        HankoStamp(
            HankoText.JackPot,
            size = 60.dp,
            modifier = Modifier
                .decorative()
                .offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }
                .graphicsLayer {
                    rotationZ = hanko.rotation
                    scaleX = hanko.scaleX
                    scaleY = hanko.scaleY
                    alpha = hanko.alpha
                },
        )
        Probe("jackpot", t, Rect(Offset.Zero, Size.Zero).sized(center, 60 * d), hanko.rotation, origin())
    }
}
