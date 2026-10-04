package earth.cora.picnic.ui.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.eggs.EggAnchors
import earth.cora.picnic.ui.eggs.EggRules
import earth.cora.picnic.ui.eggs.NapCatInBasket
import earth.cora.picnic.ui.eggs.SnackDoodle
import earth.cora.picnic.ui.eggs.StripLadybug
import earth.cora.picnic.ui.eggs.TabMetaDoodle
import earth.cora.picnic.ui.eggs.basketHello
import earth.cora.picnic.ui.eggs.pageSticker
import earth.cora.picnic.ui.eggs.snackDoodleAt
import earth.cora.picnic.ui.list.motion.AislePresentation
import earth.cora.picnic.ui.list.motion.AmbientPresentation
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.BasketPresentation
import earth.cora.picnic.ui.list.motion.MotionBasket
import earth.cora.picnic.ui.list.motion.MotionClock
import earth.cora.picnic.ui.list.motion.StripRibbon
import earth.cora.picnic.ui.list.motion.badgeOverhang
import earth.cora.picnic.ui.theme.BasketSize
import earth.cora.picnic.ui.theme.ChevronFace
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.IndexTab
import earth.cora.picnic.ui.theme.LocalPieceProbe
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.LocalTimeouts
import earth.cora.picnic.ui.theme.PadPatch
import earth.cora.picnic.ui.theme.PicnicText
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.StoreAisleChip
import earth.cora.picnic.ui.theme.TabSize
import earth.cora.picnic.ui.theme.TrayMetrics
import earth.cora.picnic.ui.theme.TrayPart
import earth.cora.picnic.ui.theme.chipFloorScale
import earth.cora.picnic.ui.theme.indexTabLeastWidth
import earth.cora.picnic.ui.theme.focusRing
import earth.cora.picnic.ui.theme.showsFocusRing
import earth.cora.picnic.ui.theme.drawDashedCircle
import earth.cora.picnic.ui.theme.groundBacking
import earth.cora.picnic.ui.theme.probe
import earth.cora.picnic.ui.theme.rememberLoopTime
import earth.cora.picnic.ui.theme.rememberPatternBrush
import earth.cora.picnic.ui.theme.stripFace
import earth.cora.picnic.ui.theme.traySlice
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.withTimeoutOrNull

/*
 * One stop on the list (docs/DESIGN_STICKER.md §4.10): the folded strip, or the open tray's band (the pinned plate when
 * its rows scroll), its end cap, the strip's ribbon and the basket in its states, each played from WP6's presentation.
 * Owner: WP5.
 */

/** Test tags. */
object ListTags {
    const val LIST: String = "picnic_list"
    const val END_NOTE: String = "picnic_list_end_note"
    const val TOP_STACK: String = "picnic_top_stack"
    const val MORE: String = "picnic_list_more"
    /** The app bar's LCD (its slot: on the title row, beside the subtitle or on a line of its own at large text). */
    const val LCD_SLOT: String = "picnic_list_lcd"
    const val EMPTY: String = "picnic_list_empty"
    const val LOADING: String = "picnic_list_loading"
    const val BUNTING: String = "picnic_list_bunting"
    const val PEEK: String = "picnic_peek_note"
    fun strip(id: String): String = "picnic_strip_$id"
    fun stripTab(id: String): String = "picnic_strip_tab_$id"
    fun stripMeta(id: String): String = "picnic_strip_meta_$id"
    fun stripChip(id: String): String = "picnic_strip_store_$id"
    fun ribbon(id: String): String = "picnic_strip_ribbon_$id"
    fun basket(id: String): String = "picnic_basket_$id"
    fun header(id: String): String = "picnic_tray_header_$id"
    fun headerTab(id: String): String = "picnic_tray_tab_$id"
    fun storeChip(id: String): String = "picnic_tray_store_$id"
    fun chevron(id: String): String = "picnic_tray_chevron_$id"
    fun cap(id: String): String = "picnic_tray_cap_$id"
    fun row(id: String): String = "picnic_row_$id"
    fun rowSlot(id: String): String = "picnic_row_slot_$id"
    fun name(id: String): String = "picnic_row_name_$id"
    fun detail(id: String): String = "picnic_row_detail_$id"
    fun rarityChip(id: String): String = "picnic_row_rarity_$id"
    fun check(id: String): String = "picnic_row_check_$id"
}

/**
 * The aisle's whole name as TalkBack reads it (§15), never ellipsized: the display name with "&" as "and", then the
 * store's own number ("Baking, store aisle 2"), as the Next card says it.
 */
@Composable
internal fun spokenAisleName(s: SectionUi): String {
    val name = earth.cora.picnic.ui.list.motion.MotionStrings.spoken(s.displayName)
    return s.storeAisle?.let { stringResource(R.string.list_aisle_store_spoken, name, it) } ?: name
}

/** The strip's TalkBack line (§15): "Aisle 1, Produce: 2 left of 5" / "Everything else: 5 of 5 in the basket". */
@Composable
internal fun stripLabel(s: SectionUi, toGet: Int): String {
    val counts = if (toGet == 0) stringResource(R.string.list_strip_done_spoken, s.total, s.total) else stringResource(R.string.list_strip_left_spoken, toGet, s.total)
    val name = spokenAisleName(s)
    return if (s.position > 0) stringResource(R.string.list_strip_label, s.position, name, counts) else stringResource(R.string.list_strip_label_unsorted, name, counts)
}

/** The open tray's heading for TalkBack (§15): "Aisle 4, Baking, store aisle 2" / "Everything else". */
@Composable
internal fun trayHeading(s: SectionUi): String =
    if (s.position > 0) stringResource(R.string.list_aisle_heading, s.position, spokenAisleName(s)) else spokenAisleName(s)

/**
 * A folded strip (§4.10): white 56 dp, radius 6, hs: the tab on the top-left, the meta line under it (led by the
 * store-aisle chip, "[A3] · 2 left of 5"), the 80 × 64 basket on the right overhanging the top, its badge, the 48 dp chevron. The whole strip is
 * one button ("Aisle 1, Produce: 2 left of 5", action "Expand"). A finished strip is tied with the sakura ribbon, placed
 * from the end of the text, never over it. Holding the basket peeks into it ([onPeek]); that hold never opens the strip.
 */
@Composable
internal fun FoldedStrip(
    section: SectionUi,
    basket: BasketPresentation?,
    aisle: AislePresentation?,
    clock: MotionClock,
    anchors: AnchorRegistry,
    focus: FocusRequester,
    onOpen: () -> Unit,
    onPeek: (held: Boolean, heldMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    peeked: Boolean = false,
    ambient: AmbientPresentation? = null,
    ambientClock: MotionClock? = null,
    /** Egg 2: the basket is still held at the hold threshold + 560 ms (the ladybug sets off, and walks on after release). */
    onHeld: (heldMs: Long) -> Unit = {},
    /** WP6's chains have played out (the nap cat curls up after the check-off that finished the other stops, §12.2 egg 8). */
    chainsDone: Boolean = true,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val eggs = LocalListEggs.current
    val surprises = LocalListSurprises.current
    val held by rememberUpdatedState(onHeld)
    val toGet = section.toGet + (basket?.toGetLag ?: 0)
    val label = stripLabel(section, toGet)
    val expand = stringResource(R.string.list_strip_expand)
    val open by rememberUpdatedState(onOpen)
    val peek by rememberUpdatedState(onPeek)
    val threshold = LocalTimeouts.current.holdThresholdMs
    val probe = LocalPieceProbe.current
    val badgeGap = basketGap(section, 6.dp)
    val metaText = buildAnnotatedString {
        // After the store-aisle chip that leads the line (§4.10): "[A3] · 2 left of 5".
        if (section.storeAisle != null) append(stringResource(R.string.list_strip_after_chip))
        if (toGet == 0) {
            withStyle(SpanStyle(fontWeight = t.stripMetaCount.fontWeight, color = c.ink)) {
                append(stringResource(R.string.list_strip_done_count, section.total, section.total))
            }
            append(stringResource(R.string.list_strip_done_tail))
        } else {
            withStyle(SpanStyle(fontWeight = t.stripMetaCount.fontWeight, color = c.ink)) {
                append(stringResource(R.string.list_strip_left_count, toGet))
            }
            append(stringResource(R.string.list_strip_left_tail, section.total))
        }
    }
    val complete = section.complete
    val presentedComplete = basket?.lidClosed ?: complete
    // Keyboard / switch access: the strip face wears the 2 dp ink ring 3 dp outside it (§4.4, §15).
    val interaction = remember { MutableInteractionSource() }
    val ringed = showsFocusRing(interaction)
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ListTags.strip(section.id))
            .focusRequester(focus)
            .focusProperties { canFocus = true }
            // The click (and its focus) outside the clearing semantics, so the one node keeps both (§15).
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = expand) { open() }
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
        content = {
            // The white card is the eggs' `strip:` anchor (egg 2's ladybug walks its bottom edge, §12.2).
            Box(
                Modifier
                    .layoutId("face")
                    .then(with(anchors) { Modifier.anchor(EggAnchors.strip(section.id)) })
                    .focusRing(PicnicTheme.shapes.strip, c.ink, ringed)
                    .stripFace(),
            )
            // The tab at its own fitted width, the meta line under it; egg 5's snack doodle only in the room the tab leaves
            // (it never shrinks the tab), else after the meta line (§4.10, §12.2).
            TabMetaDoodle(
                tab = {
                    IndexTab(
                        position = section.position,
                        displayName = section.displayName,
                        look = section.look,
                        size = TabSize.Strip,
                        ballpoint = false,
                        modifier = Modifier
                            .testTag(ListTags.stripTab(section.id))
                            .then(with(anchors) { Modifier.anchor(EggAnchors.tab(section.id)) }),
                        probeKey = "tab:${section.id}",
                        nameModifier = with(anchors) { Modifier.anchor(EggAnchors.TEXT_PREFIX + "tab:" + section.id) },
                        numberModifier = with(anchors) { Modifier.anchor(EggAnchors.TEXT_PREFIX + "num:" + section.id) },
                    )
                },
                meta = {
                    // Led by the store-aisle chip when the aisle has one (§4.7, §4.10), its "A3" on the line's baseline;
                    // decorative, since the strip's one TalkBack line already says "store aisle 3" (§15). The chip's
                    // white border keeps it off the tab, so it needs no extra room above it: the strip stays within a
                    // couple of dp of its neighbours' height.
                    Row(Modifier.padding(start = 3.dp, top = if (section.storeAisle != null) 0.dp else 2.dp)) {
                        section.storeAisle?.let { n ->
                            StoreAisleChip(
                                n,
                                Modifier
                                    .alignByBaseline()
                                    .padding(end = 4.dp)
                                    .testTag(ListTags.stripChip(section.id))
                                    .decorative()
                                    .probe(probe, "stripChip:${section.id}")
                                    .then(with(anchors) { Modifier.anchor(EggAnchors.TEXT_PREFIX + "stripChip:" + section.id) }),
                            )
                        }
                        Text(
                            metaText,
                            style = t.stripMeta.copy(color = c.inkSoft),
                            modifier = Modifier
                                .alignByBaseline()
                                .testTag(ListTags.stripMeta(section.id))
                                .probe(probe, "meta:${section.id}")
                                .then(with(anchors) { Modifier.anchor(EggAnchors.TEXT_PREFIX + "meta:" + section.id) }),
                        )
                    }
                },
                doodle = if (eggs.snackDoodleAt(section.id)) ({ SnackDoodle(eggs, section.id) }) else null,
                modifier = Modifier.layoutId("text"),
            )
            if (complete || aisle?.untieT0 != null) {
                Box(Modifier.layoutId("ribbon").testTag(ListTags.ribbon(section.id)).probe(probe, "ribbon:${section.id}")) {
                    // The band runs the strip's whole height, however tall large text makes it (§4.10, §15).
                    StripRibbon(complete, aisle, clock, height = Dp.Unspecified)
                }
                Box(Modifier.layoutId("bow").testTag(ListTags.ribbon(section.id) + "_bow").probe(probe, "bow:${section.id}")) {
                    StripRibbon(complete, aisle, clock, height = 30.dp, bowOnly = true)
                }
            }
            Box(
                Modifier
                    .layoutId("basket")
                    .testTag(ListTags.basket(section.id))
                    .probe(probe, "basket:${section.id}")
                    .pointerInput(section.id, threshold) {
                        // Press and hold for the hold threshold: the lid opens and the peek note shows; the click is
                        // swallowed so the strip does not open (§5.3). A shorter tap falls through to the strip. A
                        // finger that moves past the touch slop, or a scroll that takes the gesture, is not a hold: the
                        // list keeps the gesture and nothing peeks (item 114).
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = down.uptimeMillis
                            if (!awaitHold(down, threshold)) return@awaitEachGesture
                            peek(true, 0)
                            var end = start
                            var up = false
                            suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.untilUp() {
                                while (!up) {
                                    val e = awaitPointerEvent()
                                    e.changes.forEach { it.consume() }
                                    end = e.changes.firstOrNull()?.uptimeMillis ?: end
                                    if (e.changes.none { it.pressed }) up = true
                                }
                            }
                            // Egg 2: still held 560 ms past the threshold, the ladybug sets off while she holds (§4.3, §12.2).
                            withTimeoutOrNull(EggRules.LADYBUG_AFTER_THRESHOLD_MS) { untilUp() }
                            if (!up) {
                                held(threshold + EggRules.LADYBUG_AFTER_THRESHOLD_MS)
                                untilUp()
                            }
                            peek(false, end - start)
                        }
                    },
            ) {
                with(anchors) {
                    MotionBasket(
                        kind = section.basket,
                        look = section.look,
                        gotten = section.gotten,
                        toGet = section.toGet,
                        complete = complete,
                        presentation = if (peeked) (basket ?: BasketPresentation()).copy(peek = true, lidClosed = false, minisHidden = false) else basket,
                        clock = clock,
                        size = BasketSize.Strip.size,
                        modifier = Modifier.basketAnchor(section.id, section.basket, priority = 0).basketHello(eggs, section.id),
                        ambientPeekItem = ambient?.peekItem.takeIf { ambient?.wobbleSection == section.id },
                        ambientT0 = ambient?.wobbleT0.takeIf { ambient?.wobbleSection == section.id },
                        ambientClock = ambientClock,
                    )
                }
                // Egg 8: the nap cat curls up in the still-empty basket (it scrolls with its strip).
                NapCatInBasket(eggs, section.id, section.basket, surprises, chainsDone = chainsDone)
            }
            Box(Modifier.layoutId("chevron").size(48.dp).decorative(), contentAlignment = Alignment.Center) { ChevronFace(up = false) }
            // Egg 2: the ladybug walks the card's bottom edge, drawn by the strip so it scrolls with it.
            Box(Modifier.layoutId("ladybug")) { StripLadybug(section.id) }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val px = { d: Dp -> d.roundToPx() }
        val chevron = measurables.first { it.layoutId == "chevron" }.measure(Constraints.fixed(px(48.dp), px(48.dp)))
        val basketM = measurables.first { it.layoutId == "basket" }.measure(Constraints())
        val chevronX = w - chevron.width
        // The badge overhangs the basket box (12 dp, more as a pill): the basket stands far enough left to keep it clear
        // of the chevron (item 107).
        val basketX = chevronX - px(badgeGap) - basketM.width
        val start = px(6.dp)
        // The tab ends 8 dp before the basket (§4.10); the meta line wraps under it.
        val textMax = (basketX - px(8.dp) - start).coerceAtLeast(px(48.dp))
        val text = measurables.first { it.layoutId == "text" }.measure(Constraints(maxWidth = textMax))
        val h = max(px(56.dp), text.height + px(6.dp))
        val face = measurables.first { it.layoutId == "face" }.measure(Constraints.fixed(w, h))
        // The ribbon: from the end of the text + 12 dp, at most the basket's left edge − 22 dp; else a bow on the
        // basket's left shoulder, never over text (§4.10).
        val ribbonMeasurable = measurables.firstOrNull { it.layoutId == "ribbon" }
        val bowMeasurable = measurables.firstOrNull { it.layoutId == "bow" }
        val textEnd = start + text.width
        val bandX = textEnd + px(12.dp)
        val fits = bandX <= basketX - px(22.dp)
        val ribbon = if (fits) ribbonMeasurable?.measure(Constraints.fixedHeight(h)) else null
        val bow = if (!fits) bowMeasurable?.measure(Constraints()) else null
        // Egg 2's lane: StripLadybug walks 2 dp below the bottom of the box it is given. The strip's words sit close to its
        // bottom edge and the next strip's tab 7 dp below it, so the 15 dp bug walks a little lower, in the gap between
        // the two, clear of this strip's meta line and the next one's tab words (§12.2 "never crossing item text").
        val ladybug = measurables.firstOrNull { it.layoutId == "ladybug" }?.measure(Constraints.fixed(w, h + px(LADYBUG_LANE_DROP)))
        layout(w, h) {
            face.place(0, 0)
            text.place(start, ((h - text.height) / 2).coerceAtLeast(px(3.dp)))
            // The ribbon box is 30 dp wide with its 10 dp band at 10–20 dp.
            ribbon?.place(bandX - px(10.dp), 0)
            // On the basket's left shoulder, and never back over the end of a long name. The bow follows the basket down
            // when large text makes the strip taller (the basket stands on the strip's bottom edge): 12 dp below the
            // basket's top, as at 1.0, where the strip is 56 dp and the basket overhangs it by 8 dp.
            val basketY = h - basketM.height
            bow?.place(maxOf(basketX - px(14.dp), textEnd + px(1.dp)), basketY + px(BOW_BELOW_BASKET_TOP))
            basketM.place(basketX, basketY)
            chevron.place(chevronX, (h - chevron.height) / 2)
            ladybug?.place(0, 0)
        }
    }
}

/**
 * Waits out the hold threshold after [down] (§4.3) without consuming anything. True only when the finger is still down,
 * still within the touch slop of where it landed, and nothing else (the list's scroll) has taken the gesture by then;
 * false on a release, a cancellation or a move, which are all different from running out the clock.
 */
internal suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitHold(
    down: androidx.compose.ui.input.pointer.PointerInputChange,
    thresholdMs: Long,
): Boolean {
    val slop = viewConfiguration.touchSlop
    var ended = false
    withTimeoutOrNull(thresholdMs) {
        while (true) {
            val e = awaitPointerEvent()
            val change = e.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed || e.changes.any { it.isConsumed } || (change.position - down.position).getDistance() > slop) {
                ended = true
                return@withTimeoutOrNull
            }
            // A parent (the list's scroll) consumes on the Main pass after this child; look again on the Final pass.
            val after = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
            if (after.changes.any { it.isConsumed }) {
                ended = true
                return@withTimeoutOrNull
            }
        }
    }
    return !ended
}

/**
 * The open tray's band (§4.10): tab (ballpoint label), store chip, doodle, the 64 × 51 basket + badge and the
 * chevron-up ("Collapse Baking"). [pinned]: the band is the pinned plate over its own rows, backed by the ground from
 * the viewport top across the full width (R6), its lemon ring and offset shadow unbroken into the tray below (§0.4).
 * The bee flies its figure 8 above it (off under reduced motion).
 */
@Composable
internal fun TrayHeader(
    section: SectionUi,
    basket: BasketPresentation?,
    clock: MotionClock,
    anchors: AnchorRegistry,
    pinned: Boolean,
    chevronFocus: FocusRequester,
    onFold: () -> Unit,
    modifier: Modifier = Modifier,
    current: Boolean = true,
    /** WP6's chains have played out (the nap cat curls up after them, §12.2 egg 8). */
    chainsDone: Boolean = true,
) {
    val c = PicnicTheme.colors
    val shadows = PicnicTheme.shadows
    val ring = currentRing(current)
    val probe = LocalPieceProbe.current
    val eggs = LocalListEggs.current
    val surprises = LocalListSurprises.current
    val snack = eggs.snackDoodleAt(section.id)
    val collapse = stringResource(R.string.list_tray_collapse, earth.cora.picnic.ui.list.motion.MotionStrings.spoken(section.displayName))
    val heading = trayHeading(section)
    val fold by rememberUpdatedState(onFold)
    val badgeGap = basketGap(section, 4.dp)
    // The narrowest tab that still shows the whole name: the store chip gives way to it (below).
    val tabLeast = indexTabLeastWidth(section.position, section.displayName, section.look, TabSize.Tray)
    val chevronInteraction = remember { MutableInteractionSource() }
    val chevronRinged = showsFocusRing(chevronInteraction)
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .height(TrayMetrics.headerHeight)
            .testTag(ListTags.header(section.id))
            .then(if (pinned) Modifier.groundBacking() else Modifier)
            .traySlice(TrayPart.Header, section.look, ring, shadows.color, c.pad, c.rule),
        content = {
            IndexTab(
                position = section.position,
                displayName = section.displayName,
                look = section.look,
                size = TabSize.Tray,
                ballpoint = true,
                // The tab is the tray's heading: TalkBack reads the whole name even when the tab ellipsizes (§15).
                modifier = Modifier.layoutId("tab").testTag(ListTags.headerTab(section.id)).semantics {
                    contentDescription = heading
                    heading()
                }.then(with(anchors) { Modifier.anchor(EggAnchors.tab(section.id)) }),
                probeKey = "trayTab:${section.id}",
                nameModifier = with(anchors) { Modifier.anchor(EggAnchors.TEXT_PREFIX + "tab:" + section.id) },
                numberModifier = with(anchors) { Modifier.anchor(EggAnchors.TEXT_PREFIX + "num:" + section.id) },
            )
            section.storeAisle?.let {
                // Decorative here: the tab's heading already reads "…, store aisle 2" (§15), so TalkBack says it once. The
                // chip sizes itself as the kit does (§4.7: its face and glyph scale with its label, keeping its proportions).
                Box(Modifier.layoutId("chip").testTag(ListTags.storeChip(section.id)).decorative().then(with(anchors) { Modifier.anchor(EggAnchors.TEXT_PREFIX + "chip:" + section.id) })) {
                    StoreAisleChip(it, Modifier.probe(probe, "chip:${section.id}"))
                }
            }
            if (snack) {
                // Egg 5 at midnight: the snack star and "snack?" take the doodle's place after the tab (its own width).
                Box(Modifier.layoutId("doodle")) { SnackDoodle(eggs, section.id) }
            } else {
                section.doodle?.let {
                    Text(
                        it,
                        style = PicnicTheme.type.doodle.copy(color = c.doodle),
                        maxLines = 1,
                        modifier = Modifier.layoutId("doodle").rotate(-4f).decorative().then(with(anchors) { Modifier.anchor(EggAnchors.TEXT_PREFIX + "doodle:" + section.id) }),
                    )
                }
            }
            Box(Modifier.layoutId("basket")) {
                with(anchors) {
                    MotionBasket(
                        kind = section.basket,
                        look = section.look,
                        gotten = section.gotten,
                        toGet = section.toGet,
                        complete = section.complete,
                        presentation = basket,
                        clock = clock,
                        size = BasketSize.Tray.size,
                        tray = true,
                        modifier = Modifier.testTag(ListTags.basket(section.id)).probe(probe, "trayBasket:${section.id}").basketAnchor(section.id, section.basket, priority = 1).basketHello(eggs, section.id),
                    )
                }
                // Egg 8: the nap cat in this aisle's still-empty basket.
                NapCatInBasket(eggs, section.id, section.basket, surprises, chainsDone = chainsDone)
            }
            Box(
                Modifier
                    .layoutId("chevron")
                    .size(48.dp)
                    .testTag(ListTags.chevron(section.id))
                    .focusRequester(chevronFocus)
                    // Focusable by touch too, so her unfold can hand focus to it (§15, item 193).
                    .focusProperties { canFocus = true }
                    .clickable(chevronInteraction, indication = null, role = Role.Button, onClickLabel = collapse) { fold() }
                    .clearAndSetSemantics {
                        contentDescription = collapse
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Keyboard / switch access: the ring 3 dp outside the chevron's 30 dp face (§4.4), centred on it.
                Box(Modifier.offset((-1).dp, (-1).dp).size(30.dp).focusRing(CircleShape, c.ink, chevronRinged))
                ChevronFace(up = true)
            }
            if (current) Bee(Modifier.layoutId("bee"))
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val px = { d: Dp -> d.roundToPx() }
        val bandTop = px(TrayMetrics.ring + TrayMetrics.border)
        val bandH = px(TrayMetrics.band)
        val chevron = measurables.first { it.layoutId == "chevron" }.measure(Constraints.fixed(px(48.dp), px(48.dp)))
        val basketM = measurables.first { it.layoutId == "basket" }.measure(Constraints())
        val chevronX = w - px(TrayMetrics.border) - px(2.dp) - chevron.width
        val basketX = chevronX - px(badgeGap) - basketM.width
        val start = px(TrayMetrics.border) + px(6.dp)
        // The name is what she reads (§4.10): under a large font the store chip gives way before the name is cut. It is
        // drawn smaller (StoreAisleChip: down to its 1.0 size, never under 13 sp) to leave the tab its whole name at
        // 16 sp; at 1.0, or with room to spare, it keeps its natural width. When even its 1.0 size would cut the name
        // (a long name at any scale; "Baking" from about 1.5 on a 360 dp phone, the tab's number circle grown too), the
        // chip leaves the header and the name takes its room: it is not placed, the heading still reads "…, store aisle
        // 2", and the folded strip's meta line shows the number.
        val chip = measurables.firstOrNull { it.layoutId == "chip" }?.let { m ->
            val natural = m.maxIntrinsicWidth(Constraints.Infinity)
            val least = kotlin.math.ceil(natural * chipFloorScale()).toInt()
            val room = basketX - px(CHIP_TO_BASKET) - px(CHIP_GAP) - start - tabLeast.roundToPx()
            if (room < least) null else m.measure(Constraints(maxWidth = room.coerceIn(least, natural)))
        }
        // The tab ends 8 dp before the store chip (or the basket, §4.10); a chip may run on to 2 dp from the basket's box
        // (its art has its own margin), so a short name keeps its letters at large text ("Baking" at 1.3).
        val tabEnd = if (chip != null) basketX - px(CHIP_TO_BASKET) - chip.width - px(CHIP_GAP) else basketX - px(8.dp)
        val tabMax = (tabEnd - start).coerceAtLeast(px(60.dp))
        val tab = measurables.first { it.layoutId == "tab" }.measure(Constraints(maxWidth = tabMax))
        val doodle = measurables.firstOrNull { it.layoutId == "doodle" }?.measure(Constraints())
        val bee = measurables.firstOrNull { it.layoutId == "bee" }?.measure(Constraints())
        layout(w, h) {
            val tabY = bandTop + (bandH - tab.height) / 2
            tab.place(start, tabY)
            var x = start + tab.width + px(CHIP_GAP)
            chip?.let {
                it.place(x, bandTop + (bandH - it.height) / 2)
                x += it.width + px(6.dp)
            }
            doodle?.let { d -> if (x + d.width <= basketX - px(4.dp)) d.place(x, bandTop + (bandH - d.height) / 2 - px(6.dp)) }
            basketM.place(basketX, bandTop + (bandH - basketM.height) / 2)
            chevron.place(chevronX, bandTop + (bandH - chevron.height) / 2)
            // The bee hovers over the band's top edge, left of the basket: an edge sticker, never over text. Pinned, the
            // list's top edge would cut it in half, so it rides just inside the plate's ring.
            bee?.place(basketX - bee.width - px(2.dp), if (pinned) px(1.dp) else -px(10.dp))
        }
    }
}

/**
 * Where the bow-only ribbon's box stands below the strip basket's top: its 30 dp bow is drawn 12 dp above the box, so
 * the bow's top lines up with the basket's top and the knot sits on the basket's left shoulder (§4.10).
 */
internal val BOW_BELOW_BASKET_TOP: Dp = 12.dp

/** How far below the strip's bottom edge egg 2's ladybug walks, past StripLadybug's own 2 dp (the centre of its lane). */
private val LADYBUG_LANE_DROP: Dp = 2.5.dp

/** The room between the tray tab's end and the store chip (§4.10): 8 dp. */
private val CHIP_GAP: Dp = 8.dp

/** The least room between the store chip and the basket's box. */
private val CHIP_TO_BASKET: Dp = 2.dp

/** The bee flying a small figure 8 (3.2 s, 38 steps) over the current tray; still under reduced motion. Decorative. */
@Composable
private fun Bee(modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = !reduced)
    Box(
        modifier
            .size(22.dp)
            // A page sticker (§4.1: at most 3 per screen; DecorBudgetTest counts it): no TalkBack node.
            .pageSticker("bee")
            .graphicsLayer {
                if (reduced) return@graphicsLayer
                val step = ((t % 3200L) * 38 / 3200L).toFloat() / 38f
                val a = step * 2f * Math.PI.toFloat()
                translationX = sin(a) * 6.dp.toPx()
                translationY = sin(2f * a) * 3.dp.toPx()
            },
    ) { DecorSticker("bee", size = 22.dp) }
}

/**
 * The tray's patterned end cap (§4.10): the 15 dp band of the aisle pattern at 55 % under the panel, the bottom of the
 * border, ring and shadow; the ladybug strolls on it (14 dp hops; still under reduced motion).
 */
@Composable
internal fun TrayCap(section: SectionUi, modifier: Modifier = Modifier, current: Boolean = true) {
    val c = PicnicTheme.colors
    val shadows = PicnicTheme.shadows
    val ring = currentRing(current)
    val tile = rememberPatternBrush(section.look.pattern)
    Box(
        modifier
            .fillMaxWidth()
            .height(TrayMetrics.capHeight)
            .testTag(ListTags.cap(section.id))
            .traySlice(TrayPart.Cap, section.look, ring, shadows.color, c.pad, c.rule, patternTile = tile),
    ) {
        // The ladybug steps off while the nap cat and the umbrella both show (§4.1: at most 3 page stickers).
        if (current && !LocalListEggs.current.ladybugStepsOff) Ladybug(Modifier.offset(x = 40.dp, y = 1.dp))
    }
}

/**
 * The current aisle's ring colour, or null for an open tray that is not current: lemon mid in every variant, Glow
 * included (§4.10; §0.4 and item 313, which override §2.4's glow-star ring), unbroken from the pinned plate down the tray.
 */
@Composable
internal fun currentRing(current: Boolean): androidx.compose.ui.graphics.Color? = if (current) Families.lemon.mid else null

/** The ladybug on the current end cap (§8.5): 22 dp hops to 130 dp, a pause, gone, again every 9 s; waddles ±5°. */
@Composable
private fun Ladybug(modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = !reduced)
    Box(
        modifier
            .size(14.dp)
            .pageSticker("ladybug")
            .graphicsLayer {
                if (reduced) return@graphicsLayer
                val phase = t % 9000L
                val hop = min(6L, phase / 720L)
                translationX = hop * 22.dp.toPx()
                rotationZ = if ((t / 250L) % 2L == 0L) -5f else 5f
                alpha = if (phase > 8000L) 0f else 1f
            },
    ) { DecorSticker("ladybug", size = 14.dp) }
}

/** The end note (§6.1): "♡ end of the list · 24 stickers" in Hachi Maru on a pad patch, centred. */
@Composable
internal fun EndNote(total: Int, modifier: Modifier = Modifier, anchors: AnchorRegistry? = null) {
    val text = androidx.compose.ui.res.pluralStringResource(R.plurals.list_end_note, total, total)
    // Its words are the eggs' keep-out too (§12.1).
    val words = if (anchors != null) with(anchors) { Modifier.anchor(EggAnchors.TEXT_PREFIX + "endnote") } else Modifier
    Box(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        PadPatch(Modifier.testTag(ListTags.END_NOTE).then(words)) {
            PicnicText(text, style = PicnicTheme.type.doodle.copy(color = PicnicTheme.colors.doodle, fontSize = 14.sp))
        }
    }
}

/**
 * A skeleton strip while the list loads (§6.1 "loading"): a white strip with a dashed tab spot, bars for the tab and
 * the meta line, a dotted basket spot and a disc where the chevron goes.
 */
@Composable
internal fun SkeletonStrip(modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    Box(modifier.fillMaxWidth().height(56.dp).stripFace().decorative()) {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val dash = c.ghostDash
            val bar = if (c.isDark) c.rule else c.backing
            val r = 11.dp.toPx()
            drawDashedCircle(dash, androidx.compose.ui.geometry.Offset(22.dp.toPx(), 22.dp.toPx()), r, 1.8.dp.toPx(), 3.dp.toPx(), 2.dp.toPx())
            // The basket's landing spot where a strip's basket sits, and a plain disc for its round chevron button.
            drawDashedCircle(dash, androidx.compose.ui.geometry.Offset(size.width - 100.dp.toPx(), 28.dp.toPx()), 16.dp.toPx(), 1.8.dp.toPx(), 3.2.dp.toPx(), 2.4.dp.toPx())
            drawCircle(bar, 14.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width - 34.dp.toPx(), 28.dp.toPx()))
            drawRoundRect(bar, androidx.compose.ui.geometry.Offset(42.dp.toPx(), 14.dp.toPx()), androidx.compose.ui.geometry.Size(110.dp.toPx(), 16.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            drawRoundRect(bar, androidx.compose.ui.geometry.Offset(12.dp.toPx(), 38.dp.toPx()), androidx.compose.ui.geometry.Size(70.dp.toPx(), 9.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(4.5.dp.toPx()))
        }
    }
}

/**
 * The room between the basket's box and the chevron's 48 dp target beside it: at least [least], and enough that the
 * badge overhanging the basket (12 dp as the 22 dp circle, more as a pill with large text or a long count) ends
 * [BADGE_CLEARANCE] short of the chevron's round face, which starts [CHEVRON_FACE_INSET] inside its target. It is sized
 * for the aisle's whole count, the most the badge ever shows, so the basket does not shift as she checks things off
 * (§4.12, item 107).
 */
@Composable
private fun basketGap(section: SectionUi, least: Dp): Dp = maxOf(least, badgeOverhang(section.total) + BADGE_CLEARANCE - CHEVRON_FACE_INSET)

/** The chevron's round face starts this far inside its 48 dp target (ChevronFace: a 30 dp face in a 34 dp canvas). */
private val CHEVRON_FACE_INSET = 8.dp

/**
 * Air between the badge's white face and the chevron's face: the strip's spacing at font scale 1.0 (a 22 dp badge, 6 dp
 * between the basket's box and the chevron's target), kept as the badge grows; its 1.6 dp hard shadow sits in it.
 */
private val BADGE_CLEARANCE = 2.dp
