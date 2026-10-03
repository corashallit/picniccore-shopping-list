package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import earth.cora.picnic.core.assistant.OfflineParser
import earth.cora.picnic.core.assistant.toGroceryItem
import earth.cora.picnic.core.model.AisleSection
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroupedList
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.core.model.iconRef
import earth.cora.picnic.core.model.ingestContext
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.platform.NoOpFeedback
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.a11y.spokenItemLabel
import earth.cora.picnic.ui.add.AddBar
import earth.cora.picnic.ui.add.AddBarUi
import earth.cora.picnic.ui.add.AddRules
import earth.cora.picnic.ui.add.toast
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.BasketMini
import earth.cora.picnic.ui.list.motion.CelebrationHost
import earth.cora.picnic.ui.list.motion.FinaleKeeper
import earth.cora.picnic.ui.list.motion.ListPresentation
import earth.cora.picnic.ui.list.motion.MotionBasket
import earth.cora.picnic.ui.list.motion.MotionEngineHost
import earth.cora.picnic.ui.list.motion.MotionOverlay
import earth.cora.picnic.ui.list.motion.RowMotion
import earth.cora.picnic.ui.list.motion.StripRibbon
import earth.cora.picnic.ui.list.motion.rowHanko
import earth.cora.picnic.ui.list.motion.rowStickerPeel
import earth.cora.picnic.ui.theme.AisleLook
import earth.cora.picnic.ui.theme.BinderTopBar
import earth.cora.picnic.ui.theme.DayShadows
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.HankoStamp
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LcdMode
import earth.cora.picnic.ui.theme.LcdState
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.RarityChip
import earth.cora.picnic.ui.theme.StickerFlag
import earth.cora.picnic.ui.theme.StickerSpot
import earth.cora.picnic.ui.theme.StoreAisleChip
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.groundBacking
import earth.cora.picnic.ui.theme.hankoAngle
import earth.cora.picnic.ui.theme.hardShadow
import earth.cora.picnic.ui.theme.patternFill
import earth.cora.picnic.ui.theme.tabPattern
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The Surprise lab's practice list (docs/DESIGN_STICKER.md §6.9): the practice copy of "Saturday shop" drawn as the list
 * draws it (the binder top with the sun and the LCD, the sticker book, folded strips and the open tray whose header pins
 * as a plate over its scrolling rows, the add bar), with WP6's chains and overlay and every egg overlay mounted, so each
 * egg plays on it as it plays on her list. Its anchors are the ones the eggs look for (`sun`, `lcd`, `addbar`, `row:`,
 * `check:`, `basket:`, `stamp:`, and WP9's `strip:`, `tab:`, `text:`). Owner: WP9. WP5's list screen is the real one;
 * this practice copy goes once the lab hosts it.
 */

/** Test tags. */
object PracticeTags {
    const val ROOT: String = "practice_root"
    const val LIST: String = "practice_list"
    const val BOOK: String = "practice_book"
    const val STAMP_PAGE: String = "practice_stamps"
    const val PAGE_LCD: String = "practice_page_lcd"
    const val SLIP: String = "practice_receipt_slip"
    const val ADD_BAR: String = "practice_add_bar"
    fun row(id: String): String = "practice_row_$id"
    fun hanko(id: String): String = "practice_hanko_$id"
    fun strip(id: String): String = "practice_strip_$id"
    fun basket(id: String): String = "practice_basket_$id"
    fun sticker(id: String): String = "practice_sticker_$id"
    fun stamp(id: String): String = "practice_stamp_$id"
    fun header(id: String): String = "practice_header_$id"
    fun fold(id: String): String = "practice_fold_$id"
}

/** The lazy list's entries: a folded strip, or the open tray in slices (its header pins, R1, R6). */
private sealed interface PracticeEntry {
    val section: AisleSection
    val key: String

    data class Strip(override val section: AisleSection) : PracticeEntry {
        override val key: String get() = "strip:${section.id}"
    }

    data class Header(override val section: AisleSection) : PracticeEntry {
        override val key: String get() = "head:${section.id}"
    }

    data class Rows(override val section: AisleSection) : PracticeEntry {
        override val key: String get() = "rows:${section.id}"
    }

    data class Cap(override val section: AisleSection) : PracticeEntry {
        override val key: String get() = "cap:${section.id}"
    }

    /** One pixel, pinned after the tray: it pushes the plate off as the tray ends. */
    data class End(override val section: AisleSection) : PracticeEntry {
        override val key: String get() = "end:${section.id}"
    }
}

private fun entries(g: GroupedList, open: String?): List<PracticeEntry> = g.sections.flatMap { s ->
    if (s.id == open) {
        listOf(PracticeEntry.Header(s), PracticeEntry.Rows(s), PracticeEntry.Cap(s), PracticeEntry.End(s))
    } else {
        listOf(PracticeEntry.Strip(s))
    }
}

/**
 * The practice list for [model], under [ribbon] (the lab's "Surprise lab · practice list" strip, or nothing). [listState]
 * drives the lazy list (the rolling produce scrolls its aisle into view, §12.2 egg 3). The eggs are mounted through
 * [EggListHost], the same hand-off WP5's list uses, so the lab exercises the code her list runs. [imeVisible] is the
 * keyboard test hook (null: the phone's own insets).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PracticeList(
    model: PracticeModel,
    modifier: Modifier = Modifier,
    feedback: Feedback = NoOpFeedback,
    listState: LazyListState = rememberLazyListState(),
    ribbon: (@Composable () -> Unit)? = null,
    talkBack: Boolean = false,
    imeVisible: Boolean? = null,
) {
    val engine = model.engine
    val state by model.store.states.collectAsState()
    val eggsNow by engine.state.collectAsState()
    val album by model.album.album.collectAsState(initial = null)
    model.albumNow = album
    val everythingElse = stringResource(R.string.egg_practice_everything_else)
    SideEffect { model.everythingElse = everythingElse }
    val motion = model.motion
    val p = motion.current.value
    MotionEngineHost(motion, eggs = eggsNow.enabled, talkBackOverride = talkBack)
    // The finale's state (finaleShownAt, the bonus) on the practice copy.
    LaunchedEffect(motion) { FinaleKeeper(model.store, model.album).let { keeper -> motion.events.collect { keeper.handle(it) } } }
    // The add flows' sticky note for each add (WP7's words and Undo).
    val resources = LocalResources.current
    LaunchedEffect(model, resources) {
        model.commits.collect { commit -> model.toasts.show(commit.toast(resources) { model.undo(commit) }) }
    }
    val list = model.list(state)
    val grouped = model.grouped(state)
    val open = p.openSection(grouped.openSectionId)
    val entries = remember(grouped, open) { entries(grouped, open) }
    val indexOf = { id: String -> entries.indexOfFirst { it.section.id == id } }
    // An aisle scrolled to sits 4 dp under the list's top, so only the gap after the strip above it shows, never a
    // sliver of that strip.
    val peek = LIST_TOP - STRIP_GAP
    val density = LocalDensity.current
    // An add brings its row into the list's room if it is not wholly there (egg 9's cake row, egg 6's shiny row, egg 13's
    // jackpot row): its aisle first, then the row above the add bar, at any font scale.
    val focus = model.focus
    LaunchedEffect(focus) {
        val f = focus ?: return@LaunchedEffect
        val i = indexOf(f.sectionId)
        if (i < 0) return@LaunchedEffect
        // As ever, the aisle comes to the top when its last slice (its rows, or its strip) is not wholly in view.
        val last = entries.indexOfLast { it.section.id == f.sectionId && it !is PracticeEntry.End }
        val sliceHidden = sliceHidden@{
            if (!f.aisleFirst) return@sliceHidden false
            val info = listState.layoutInfo
            val seen = info.visibleItemsInfo.firstOrNull { it.index == last }
            seen == null || seen.offset < 0 || seen.offset + seen.size > info.viewportEndOffset - info.afterContentPadding
        }
        bringIntoRoom(listState, model.anchors, i, with(density) { peek.roundToPx() }, with(density) { 6.dp.toPx() }, plateIndex = i, scrollFirst = sliceHidden) {
            f.itemId?.let { id -> model.anchors.bounds(EggAnchors.line(id))?.let { it to true } }
                ?: model.anchors.bounds(EggAnchors.strip(f.sectionId))?.let { it to false }
        }
    }
    val host: @Composable EggListScope.() -> Unit = {
        val eggs = this.eggs
        Box(modifier.fillMaxSize().testTag(PracticeTags.ROOT)) {
            PicnicBackground {
                // Egg 1: the ground being left fades out over the new one.
                Wash()
                Column(Modifier.fillMaxSize()) {
                    ribbon?.invoke()
                    PracticeTopBar(model, list.title, state.store.name, grouped.stops, list.items.count { it.checked }, list.items.size, eggs, p)
                    PracticeBook(model, state, grouped, eggs, p, Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp))
                    LazyColumn(
                        with(model.anchors) { Modifier.weight(1f).fillMaxWidth().testTag(PracticeTags.LIST).anchor(EggAnchors.LIST) },
                        state = listState,
                        // The add bar's measured height + 26 dp (+ the umbrella's room while it rains): the last strip
                        // always scrolls wholly above the bar, at any font scale.
                        contentPadding = PaddingValues(top = LIST_TOP + listTopExtra, bottom = listBottomPadding()),
                    ) {
                        entries.forEachIndexed { index, e ->
                            val s = e.section
                            val current = s.id == grouped.nowSectionId
                            when (e) {
                                is PracticeEntry.Strip -> item(key = e.key, contentType = "strip") { PracticeStrip(model, s, eggs, p) }
                                is PracticeEntry.Header -> stickyHeader(key = e.key, contentType = "header") {
                                    val pinned by remember(listState, index) {
                                        derivedStateOf {
                                            listState.firstVisibleItemIndex > index ||
                                                (listState.firstVisibleItemIndex == index && listState.firstVisibleItemScrollOffset > 0)
                                        }
                                    }
                                    PracticeTrayHeader(model, s, eggs, p, current, pinned)
                                }
                                is PracticeEntry.Rows -> item(key = e.key, contentType = "rows") { PracticeTrayRows(model, s, eggs, p, current) }
                                is PracticeEntry.Cap -> item(key = e.key, contentType = "cap") { PracticeTrayCap(model, s, eggs, current) }
                                is PracticeEntry.End -> stickyHeader(key = e.key, contentType = "end") {
                                    Spacer(Modifier.fillMaxWidth().height(with(LocalDensity.current) { 1.toDp() }))
                                }
                            }
                        }
                    }
                }
                PracticeAddBar(model, eggs, Modifier.align(Alignment.BottomCenter).eggAddBar())
            }
            // The toasts sit 20 dp above the bar's measured top (above the umbrella's canopy while it rains).
            MotionOverlay(motion, p, model.toasts, bottomInset = overlayInset(), eggs = overlays)
            CelebrationHost(motion, p)
        }
    }
    if (imeVisible != null) {
        EggListHost(
            engine, model.listId, model.anchors, listState, indexOf, model.toasts, motion, feedback,
            peek = peek, imeVisible = imeVisible, content = host,
        )
    } else {
        EggListHost(engine, model.listId, model.anchors, listState, indexOf, model.toasts, motion, feedback, peek = peek, content = host)
    }
}

/* ------------------------------------------------------------------------------------------------
 * The binder top.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun PracticeTopBar(
    model: PracticeModel,
    title: String,
    store: String,
    stops: Int,
    checked: Int,
    total: Int,
    eggs: SurpriseState,
    p: ListPresentation,
) {
    val engine = model.engine
    val anchors = model.anchors
    val pose = rememberPetPose(eggs)
    val variant = PicnicTheme.variant
    val base = LcdState(mode = LcdMode.Count, count = p.lcdCount(checked), total = total)
    val subtitle = pluralStringResource(R.plurals.egg_practice_subtitle, stops, store, stops)
    with(anchors) {
        BinderTopBar(
            title = title,
            subtitle = subtitle,
            lcd = eggs.lcd(base, pose),
            onLcdHold = if (eggs.enabled) engine::onLcdHold else null,
            onSunTap = { engine.onTitleStickerTap(eggs, variant) },
            goldenMinutesLeft = eggs.goldenMinutesLeft,
            titleBrush = rememberKirakiraBrush(eggs),
            sunModifier = Modifier
                .anchor(AnchorRegistry.SUN)
                // Egg 1's transition draws the sun and the moon itself while it plays.
                .eggTitleSticker(eggs, variant),
            // The LCD's digits are words too: bursts are cut clear of them.
            lcdModifier = Modifier.anchor(AnchorRegistry.LCD).anchor(EggAnchors.TEXT_PREFIX + "lcd"),
            titleModifier = Modifier.anchor(EggAnchors.TEXT_PREFIX + "title"),
            subtitleModifier = Modifier.anchor(EggAnchors.TEXT_PREFIX + "subtitle"),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/* ------------------------------------------------------------------------------------------------
 * Tabs.
 * ------------------------------------------------------------------------------------------------ */

/** The aisle's name on its tab: the display name, or "Everything else". */
@Composable
private fun tabName(s: AisleSection): String =
    if (s.kind == SectionKind.UNSORTED) stringResource(R.string.egg_practice_everything_else) else s.displayName

@Composable
private fun PracticeTab(s: AisleSection, look: AisleLook, height: Dp, current: Boolean, model: PracticeModel, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val shape = PicnicTheme.shapes.tab
    // Large text: the number circle grows with its numeral (up to 1.6×).
    val grow = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
    with(model.anchors) {
        Row(
            modifier
                .anchor(EggAnchors.tab(s.id))
                .heightIn(min = height)
                .hardShadow(shape, PicnicTheme.shadows)
                .clip(shape)
                .background(c.stickerWhite)
                .padding(3.dp)
                .clip(shape)
                .background(look.family.pastel)
                .tabPattern(look)
                .padding(start = 4.dp, end = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(24.dp * grow).clip(CircleShape).background(c.stickerWhite).border(2.dp, look.family.trace, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (s.position > 0) {
                    Text(s.position.toString(), style = PicnicTheme.type.tabNumber.copy(color = look.family.trace), modifier = Modifier.anchor(EggAnchors.TEXT_PREFIX + "num:" + s.id))
                } else {
                    CapsuleGlyph(look.family.trace)
                }
            }
            TabName(
                tabName(s),
                PicnicTheme.type.aisleTab.copy(color = if (current) c.ballpoint else look.family.trace),
                Modifier.weight(1f, fill = false).anchor(EggAnchors.TEXT_PREFIX + "tab:" + s.id),
            )
        }
    }
}

/**
 * A tab's name (§4.10 "Fit"): one line, at 20 sp if it fits the room the tab has, else 1 sp smaller each step down to
 * 16 sp, else 16 sp ellipsized. Sizes are sp, so they scale with the font like every other text; TalkBack reads the
 * whole name either way.
 */
@Composable
internal fun TabName(name: String, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    BoxWithConstraints(modifier) {
        val room = constraints.maxWidth
        var size = TAB_MAX_SP
        if (room != Constraints.Infinity) {
            while (size > TAB_MIN_SP && measurer.measure(name, style.copy(fontSize = size.sp), maxLines = 1, softWrap = false).size.width > room) size -= 1f
        }
        Text(name, style = style.copy(fontSize = size.sp), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** The tab name's sizes (§4.10): 20 sp, down to 16 sp, then an ellipsis. */
internal const val TAB_MAX_SP: Float = 20f
internal const val TAB_MIN_SP: Float = 16f

/** What [s]'s basket holds as the practice list draws it (holo pulls stay holo, R4). */
internal fun minis(s: AisleSection, pixel: Boolean): List<BasketMini> =
    s.gotten.map { BasketMini(it.id, it.iconRef(), holo = it.rarity != Rarity.NONE, aisleIndex = s.position - 1, pixel = pixel) }

/* ------------------------------------------------------------------------------------------------
 * A folded strip.
 * ------------------------------------------------------------------------------------------------ */

/** The strip meta (§16): "**2 left** of 5" / "**5 of 5** in the basket". */
@Composable
private fun stripMeta(toGet: Int, total: Int): AnnotatedString {
    val bold = if (toGet == 0) stringResource(R.string.egg_practice_meta_done_count, total, total) else stringResource(R.string.egg_practice_meta_left_count, toGet)
    val full = if (toGet == 0) stringResource(R.string.egg_practice_meta_done, bold) else stringResource(R.string.egg_practice_meta_left, bold, total)
    return buildAnnotatedString {
        append(full)
        val at = full.indexOf(bold)
        if (at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = PicnicTheme.colors.ink), at, at + bold.length)
    }
}

@Composable
private fun PracticeStrip(model: PracticeModel, s: AisleSection, eggs: SurpriseState, p: ListPresentation) {
    val c = PicnicTheme.colors
    val look = practiceLook(s, model.state)
    val clock = model.motion.clock
    val shape = RoundedCornerShape(6.dp)
    val anchors = model.anchors
    val threshold = earth.cora.picnic.ui.theme.LocalTimeouts.current.holdThresholdMs
    val engine by rememberUpdatedState(model.engine)
    val density = LocalDensity.current
    val toGet = s.toGet + (p.baskets[s.id]?.toGetLag ?: 0)
    val name = tabName(s)
    val spokenCount = if (toGet == 0) stringResource(R.string.egg_practice_strip_done, s.total, s.total) else stringResource(R.string.egg_practice_strip_left, toGet, s.total)
    val label = if (s.position > 0) stringResource(R.string.egg_practice_strip, s.position, name, spokenCount) else stringResource(R.string.egg_practice_strip_unsorted, name, spokenCount)
    // The strip's height (it grows with large text) and where its words end, for the ribbon (§4.10 folded strip).
    var stripPx by remember { mutableIntStateOf(0) }
    var wordsPx by remember { mutableIntStateOf(0) }
    with(anchors) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = STRIP_GAP)
                .heightIn(min = 70.dp)
                .onSizeChanged { stripPx = it.height }
                .testTag(PracticeTags.strip(s.id))
                // The whole strip is one button (§4.10): "Aisle 1, Produce: 2 left of 5. Expand".
                .clearAndSetSemantics {
                    contentDescription = label
                    role = Role.Button
                }
                .clickable(remember { MutableInteractionSource() }, indication = null) { model.open(s.id) },
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(top = STRIP_TOP)
                    .anchor(EggAnchors.strip(s.id))
                    .hardShadow(shape, PicnicTheme.shadows)
                    .clip(shape)
                    .background(c.pad),
            )
            // The words keep 8 dp clear of the basket (its 80 dp box sits 44 dp in from the end) at any font scale.
            // The tab at its own fitted width, the meta line under it; egg 5's doodle only in the room the tab leaves (it
            // never shrinks the tab), else after the meta line.
            TabMetaDoodle(
                tab = { PracticeTab(s, look, 32.dp, current = false, model = model) },
                meta = {
                    FlowRow(
                        Modifier.padding(start = 4.dp, top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        s.storeAisle?.let { StoreAisleChip(it, Modifier.anchor(EggAnchors.TEXT_PREFIX + "meta-chip:" + s.id)) }
                        Text(
                            stripMeta(toGet, s.total),
                            style = PicnicTheme.type.stripMeta.copy(color = c.inkSoft),
                            modifier = Modifier.anchor(EggAnchors.TEXT_PREFIX + "meta:" + s.id),
                        )
                    }
                },
                doodle = if (eggs.snackDoodleAt(s.id)) ({ SnackDoodle(eggs, s.id) }) else null,
                modifier = Modifier.padding(start = 6.dp, top = 11.dp, bottom = 6.dp, end = STRIP_TEXT_END).onSizeChanged { wordsPx = it.width },
            )
            if (s.complete && stripPx > 0) {
                // The ribbon is placed from the words, never over them: 12 dp after the farthest of the tab, the meta
                // line and the doodle, and at most 22 dp before the basket; with no such gap, the bow alone ties at the
                // basket's left shoulder, tied behind it (§4.10).
                val textEnd = with(density) { 6.dp + wordsPx.toDp() }
                val basketLeft = maxWidth - 124.dp
                val band = textEnd + 12.dp
                val height = with(density) { stripPx.toDp() } - STRIP_TOP
                if (band + 5.dp <= basketLeft - 22.dp) {
                    StripRibbon(true, p.aisles[s.id], clock, height, Modifier.offset(x = band - 10.dp, y = STRIP_TOP))
                } else {
                    StripRibbon(true, p.aisles[s.id], clock, 30.dp, Modifier.offset(x = basketLeft + 6.dp, y = STRIP_TOP - 6.dp), bowOnly = true)
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-44).dp, y = 0.dp)
                    .size(80.dp, 64.dp)
                    .testTag(PracticeTags.basket(s.id))
                    // Egg 2: a hold of the threshold + 560 ms sends the ladybug (a hold of the threshold alone peeks).
                    .pointerInput(s.id, threshold) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val goal = threshold + EggRules.LADYBUG_AFTER_THRESHOLD_MS
                            // A tap opens the strip (its clickable); a press past the threshold is a hold, never a tap.
                            if (withTimeoutOrNull(threshold) { waitForUpOrCancellation() } != null) return@awaitEachGesture
                            val early = withTimeoutOrNull(goal - threshold) { waitForUpOrCancellation() }
                            if (early != null) {
                                early.consume()
                                return@awaitEachGesture
                            }
                            engine.onBasketHold(s.id, goal)
                            // The ladybug walks on after the release; the release itself opens nothing.
                            waitForUpOrCancellation()?.consume()
                        }
                    },
            ) {
                MotionBasket(
                    kind = s.basket,
                    look = look,
                    gotten = minis(s, eggs.pixelStickers),
                    toGet = s.toGet,
                    complete = s.complete,
                    presentation = p.baskets[s.id],
                    clock = clock,
                    size = DpSize(80.dp, 64.dp),
                    modifier = Modifier.basketAnchor(s.id, s.basket, priority = 0).basketHello(eggs, s.id),
                )
                NapCatInBasket(eggs, s.id, s.basket, engine, chainsDone = p.chainsDone)
            }
            Chevron(Modifier.align(Alignment.BottomEnd).offset(y = (-4).dp), up = false)
            // Egg 2: the ladybug walks this strip's bottom edge, scrolling with it.
            StripLadybug(s.id)
        }
    }
}

/** The list's top padding (§6.1) and the gap after each strip and tray. */
private val LIST_TOP = 11.dp
private val STRIP_GAP = 7.dp

/** The folded strip's white body starts this far down its box (the tab pokes above it). */
private val STRIP_TOP = 14.dp

/** The strip's words end this far before its end: 8 dp before the basket's 80 dp box, which sits 44 dp in (§4.10). */
private val STRIP_TEXT_END = 132.dp

@Composable
private fun Chevron(modifier: Modifier, up: Boolean) {
    val c = PicnicTheme.colors
    Box(modifier.size(48.dp).decorative(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(30.dp)) {
            drawCircle(c.stickerShadow, size.minDimension / 2f, center + Offset(1.6.dp.toPx(), 2.2.dp.toPx()))
            drawCircle(c.pad, size.minDimension / 2f)
            drawCircle(Families.periwinkle.pastel, size.minDimension / 2f - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            val w = 9.dp.toPx()
            val h = 5.dp.toPx() * if (up) -1 else 1
            val path = Path().apply {
                moveTo(center.x - w / 2, center.y - h / 2)
                lineTo(center.x, center.y + h / 2)
                lineTo(center.x + w / 2, center.y - h / 2)
            }
            drawPath(path, c.ink, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * The open tray (R1), drawn slice by slice so its header can pin as the plate over its rows (R6, §0.4 item 313).
 * ------------------------------------------------------------------------------------------------ */

/** The tray's look for its slices: the pastel, the current aisle's lemon ring, the shadow under the whole tray. */
private class TrayLook(val pastel: Color, val white: Color, val ring: Color?, val shadow: Color, val shadowOffset: DpOffset)

@Composable
private fun trayLook(look: AisleLook, current: Boolean): TrayLook {
    val c = PicnicTheme.colors
    val shadows = PicnicTheme.shadows
    // The current aisle wears a 3 dp lemon ring and a (2.6, 3.6) offset shadow (Golden lengthens every shadow to (3, 4)).
    val ring = if (!current) null else if (c.variant == ThemeVariant.Glow) c.glowStar else Families.lemon.mid
    val offset = when {
        !current -> shadows.hs
        shadows.hs2 == DayShadows.hs2 -> DpOffset(2.6.dp, 3.6.dp)
        else -> shadows.hs2
    }
    return TrayLook(look.family.pastel, c.stickerWhite, ring, shadows.color, offset)
}

/** The ring's width (§4.10 current-aisle emphasis) and the tray's white border. */
private val RING: Dp = 3.dp
private val TRAY_BORDER: Dp = 3.dp

/**
 * One slice of the open tray: the shadow under the whole tray, the lemon ring (current aisle), the white border and the
 * pastel, each with corners only on the edges this slice closes ([top], [bottom]). Open edges run past the slice, so
 * the slices join into one tray, and the plate's ring runs on unbroken down the tray (§0.4: the lemon ring never breaks).
 */
private fun Modifier.traySlice(top: Boolean, bottom: Boolean, look: TrayLook): Modifier = drawBehind {
    val over = 40.dp.toPx()
    val ringW = if (look.ring != null) RING.toPx() else 0f
    val border = TRAY_BORDER.toPx()
    val t = if (top) 0f else -over
    val b = if (bottom) size.height else size.height + over
    fun outline(inset: Float, radius: Float): Path {
        val r = CornerRadius(radius)
        val rect = Rect(inset, t + if (top) inset else 0f, size.width - inset, b - if (bottom) inset else 0f)
        return Path().apply {
            addRoundRect(
                RoundRect(
                    rect,
                    topLeft = if (top) r else CornerRadius.Zero,
                    topRight = if (top) r else CornerRadius.Zero,
                    bottomRight = if (bottom) r else CornerRadius.Zero,
                    bottomLeft = if (bottom) r else CornerRadius.Zero,
                ),
            )
        }
    }
    val outer = outline(0f, (if (look.ring != null) 15 else 12).dp.toPx())
    val dx = look.shadowOffset.x.toPx()
    val dy = look.shadowOffset.y.toPx()
    clipRect(-over, 0f, size.width + over, size.height + if (bottom) dy + 1f else 0f) {
        translate(dx, dy) { drawPath(outer, look.shadow) }
    }
    clipRect(-over, 0f, size.width + over, size.height) {
        if (look.ring != null) drawPath(outer, look.ring)
        drawPath(outline(ringW, 12.dp.toPx()), look.white)
        drawPath(outline(ringW + border, 9.dp.toPx()), look.pastel)
    }
}

/** The tray's inner horizontal edge: the ring (current) and the white border. */
@Composable
private fun trayInset(current: Boolean): Dp = (if (current) RING else 0.dp) + TRAY_BORDER

/**
 * The tray's header band: tab, store chip, doodle, basket, and the chevron that folds it ("Collapse Baking"). It pins as
 * the plate while the rows scroll ([pinned]): the binder ground is repainted behind it from the viewport top, across the
 * full width (R6), and its ring and shadow run on into the rows below.
 */
@Composable
private fun PracticeTrayHeader(model: PracticeModel, s: AisleSection, eggs: SurpriseState, p: ListPresentation, current: Boolean, pinned: Boolean) {
    val look = practiceLook(s, model.state)
    val tray = trayLook(look, current)
    val inset = trayInset(current)
    val collapse = stringResource(R.string.egg_practice_collapse, tabName(s))
    Box(
        Modifier
            .fillMaxWidth()
            .testTag(PracticeTags.header(s.id))
            .then(if (pinned) Modifier.groundBacking() else Modifier)
            .padding(horizontal = 8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .traySlice(top = true, bottom = false, look = tray)
                .padding(start = inset + 5.dp, end = inset + 5.dp, top = inset)
                .heightIn(min = 56.dp),
        ) {
            with(model.anchors) {
                // The tab and the store-aisle chip keep clear of the basket (64 dp, 52 dp in); with large text the chip
                // wraps under the tab rather than sliding behind the basket.
                FlowRow(
                    Modifier.align(Alignment.CenterStart).padding(top = 6.dp, bottom = 6.dp, end = 122.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    PracticeTab(s, look, 38.dp, current = true, model = model)
                    s.storeAisle?.let { StoreAisleChip(it, Modifier.anchor(EggAnchors.TEXT_PREFIX + "chip:" + s.id)) }
                    SnackDoodle(eggs, s.id)
                }
                Box(Modifier.align(Alignment.CenterEnd).offset(x = (-52).dp)) {
                    MotionBasket(
                        kind = s.basket,
                        look = look,
                        gotten = minis(s, eggs.pixelStickers),
                        toGet = s.toGet,
                        complete = s.complete,
                        presentation = p.baskets[s.id],
                        clock = model.motion.clock,
                        size = DpSize(64.dp, 51.dp),
                        tray = true,
                        modifier = Modifier.basketAnchor(s.id, s.basket, priority = 1).basketHello(eggs, s.id),
                    )
                    NapCatInBasket(eggs, s.id, s.basket, model.engine, chainsDone = p.chainsDone)
                }
                // The fold button (48 dp): "Collapse Baking".
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .testTag(PracticeTags.fold(s.id))
                        .clearAndSetSemantics {
                            contentDescription = collapse
                            role = Role.Button
                            onClick { model.open(""); true }
                        }
                        .clickable(remember { MutableInteractionSource() }, indication = null) { model.open("") },
                ) {
                    Chevron(Modifier, up = true)
                }
            }
        }
        // The standing bee flies its loop over the current aisle's basket (§4.27), outside the tray's clip so its loop
        // can rise over the tray's top edge as the mockup's does.
        if (current) TrayBee(Modifier.align(Alignment.TopEnd).offset(x = (-72).dp, y = (-3).dp))
    }
}

/** The tray's rows on their white ruled panel. */
@Composable
private fun PracticeTrayRows(model: PracticeModel, s: AisleSection, eggs: SurpriseState, p: ListPresentation, current: Boolean) {
    val c = PicnicTheme.colors
    val tray = trayLook(practiceLook(s, model.state), current)
    val inset = trayInset(current)
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .traySlice(top = false, bottom = false, look = tray)
                .padding(horizontal = inset + 5.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.pad),
        ) {
            s.rows.forEachIndexed { i, item ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
                PracticeRow(model, item, s, eggs, p, i)
            }
        }
    }
}

/** The tray's patterned end cap (15 dp) with the standing ladybug, closing the tray. */
@Composable
private fun PracticeTrayCap(model: PracticeModel, s: AisleSection, eggs: SurpriseState, current: Boolean) {
    val look = practiceLook(s, model.state)
    val tray = trayLook(look, current)
    val inset = trayInset(current)
    Box(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = STRIP_GAP)) {
        Box(
            Modifier
                .fillMaxWidth()
                .traySlice(top = false, bottom = true, look = tray)
                .padding(start = inset, end = inset, bottom = inset),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(15.dp)
                    .clip(RoundedCornerShape(bottomStart = 9.dp, bottomEnd = 9.dp))
                    .patternFill(look, 0.55f),
            ) {
                // The standing ladybug on the end cap (it steps off while the nap cat and the umbrella both show).
                EndCapLadybug(eggs, Modifier.align(Alignment.BottomStart).offset(x = 30.dp, y = (-1).dp))
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * A row.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun PracticeRow(model: PracticeModel, item: GroceryItem, s: AisleSection, eggs: SurpriseState, p: ListPresentation, index: Int) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val clock = model.motion.clock
    val reduced = LocalReducedMotion.current
    val phase = p.rows[item.id]
    val start = p.rowStarts[item.id]
    val anchors = model.anchors
    val holo = item.rarity != Rarity.NONE
    val hidden = eggs.hidesRowSticker(item.id)
    val catalog = model.catalog
    // TalkBack (§15): "Strawberries, 1 litre basket" + "not checked" / "got it", and its Check / Uncheck action.
    val spoken = spokenItemLabel(item.name, item.quantity)
    val stateWords = stringResource(if (item.checked) R.string.egg_practice_row_checked else R.string.egg_practice_row_unchecked)
    val action = stringResource(if (item.checked) R.string.egg_practice_uncheck else R.string.egg_practice_check)
    val toggle = { model.toggle(item.id) }
    // Egg 3 rolls home along this line beneath the row's words (drawn first, so the words draw over it).
    Box(Modifier.fillMaxWidth()) {
    RowRoller(item.id)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (LocalDensity.current.fontScale >= 1.3f) 64.dp else 56.dp)
            .testTag(PracticeTags.row(item.id))
            .then(with(anchors) { Modifier.anchor(EggAnchors.line(item.id)) })
            .toggleable(value = item.checked, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Checkbox) { toggle() }
            .clearAndSetSemantics {
                contentDescription = spoken
                stateDescription = stateWords
                toggleableState = ToggleableState(item.checked)
                role = Role.Checkbox
                onClick(label = action) { toggle(); true }
                customActions = listOf(CustomAccessibilityAction(action) { toggle(); true })
            }
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        with(anchors) {
            Box(Modifier.size(52.dp).anchor(AnchorRegistry.rowKey(item.id)).testTag(PracticeTags.sticker(item.id))) {
                if (!hidden) {
                    val ghostAlpha = RowMotion.ghostAlpha(item.checked, phase, start, clock.now, reduced)
                    if (ghostAlpha > 0f) ItemIcon(item.iconRef(), null, Modifier.graphicsLayer { alpha = RowMotion.ghostAlpha(item.checked, phase, start, clock.now, reduced) }, ghost = true)
                    if (ghostAlpha < 1f) {
                        ItemIcon(
                            if (eggs.goldCapsule(item.id)) item.iconRef().copy(key = earth.cora.picnic.core.catalog.IconCatalog.CAPSULE) else item.iconRef(),
                            null,
                            Modifier
                                .eggTwinkle(eggs, index)
                                .rowStickerPeel(phase, start, clock)
                                .graphicsLayer { alpha = 1f - RowMotion.ghostAlpha(item.checked, phase, start, clock.now, reduced) },
                            holo = holo,
                            pixel = eggs.pixelStickers,
                            jackpot = eggs.goldCapsule(item.id),
                            aisleIndex = s.position - 1,
                            flags = buildSet {
                                if (holo) add(StickerFlag.Shiny)
                            },
                        )
                    }
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 8.dp, end = 4.dp)) {
            with(anchors) {
                Text(
                    item.name,
                    style = t.itemName.copy(color = if (item.checked) c.inkSoft else c.ink),
                    modifier = Modifier.anchor(EggAnchors.TEXT_PREFIX + "name:" + item.id).strike(item.checked, c.hanko),
                )
                // The rarity chip wraps under a long detail (never squeezed or cut, at any font scale).
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    item.quantity?.let {
                        Text(it, style = t.itemDetail.copy(color = c.inkSoft), modifier = Modifier.anchor(EggAnchors.TEXT_PREFIX + "detail:" + item.id))
                    }
                    if (item.rarity != Rarity.NONE) RarityChip(jackpot = item.rarity == Rarity.JACKPOT, modifier = Modifier.anchor(EggAnchors.TEXT_PREFIX + "chip:" + item.id))
                }
            }
        }
        with(anchors) {
            Box(Modifier.size(48.dp).anchor(AnchorRegistry.checkKey(item.id)), contentAlignment = Alignment.Center) {
                val lifting = RowMotion.liftsHanko(phase)
                if (!item.checked && !lifting && !eggs.hidesCheckSpot(item.id)) StickerSpot()
                if (item.checked || lifting) {
                    if (lifting) StickerSpot()
                    Box(Modifier.size(48.dp).rowHanko(item.checked, phase, start, clock, reduced)) {
                        HankoStamp(
                            eggs.hankoFor(item, catalog),
                            Modifier.offset(4.dp, 4.dp).graphicsLayer { rotationZ = hankoAngle(item.id) }.testTag(PracticeTags.hanko(item.id)),
                        )
                    }
                }
            }
        }
    }
    }
}

/** The 2 dp hanko strike-through drawn under the text (§4.11), not LineThrough. */
private fun Modifier.strike(on: Boolean, color: Color): Modifier = if (!on) this else drawBehind {
    val y = size.height * 0.55f
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
}

/* ------------------------------------------------------------------------------------------------
 * The add bar (WP7's), wearing the season's (or the rain's) washi.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun PracticeAddBar(model: PracticeModel, eggs: SurpriseState, modifier: Modifier) {
    var text by remember { mutableStateOf("") }
    val engine = model.engine
    with(model.anchors) {
        AddBar(
            state = AddBarUi(text = text),
            onTextChange = { text = it },
            onSend = {
                val t = text.trim()
                if (t.isNotEmpty()) {
                    if (!engine.interceptText(t)) {
                        val s = model.state
                        val items = OfflineParser.parse(t, model.catalog, s.ingestContext(model.listId))
                        model.add(items.mapIndexed { i, it -> it.toGroceryItem("practice-${model.nextId()}-$i", model.now(), ItemSource.TYPED) })
                    }
                    text = ""
                }
            },
            onSnap = {},
            onChoosePhoto = {},
            onSpeak = {},
            // The practice copy has no camera or microphone: Snap, Choose a photo and Speak are off here.
            captureEnabled = false,
            tape = AddRules.addBarTape(eggs),
            // Egg 16: the umbrella stands at the bar's left end; the tape moves along, past the doodle.
            tapeAfterDoodle = eggs.umbrella,
            // The whole bar is a keep-out: its field, placeholder and doodle are words, and bursts fall behind it.
            modifier = modifier.anchor(AnchorRegistry.ADD_BAR).anchor(EggAnchors.TEXT_PREFIX + "addbar").testTag(PracticeTags.ADD_BAR),
        )
    }
}

/** WP6's check-off chains have played out (no hop in flight, no burst, no celebration): the nap cat may curl up. */
private val ListPresentation.chainsDone: Boolean get() = flights.isEmpty() && bursts.isEmpty() && celebration == null
