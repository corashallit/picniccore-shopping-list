package earth.cora.picnic.ui.list.motion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.core.model.AisleSection
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.core.model.iconRef
import earth.cora.picnic.core.model.list
import earth.cora.picnic.ui.theme.AisleLook
import earth.cora.picnic.ui.theme.AisleLooks
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.HankoStamp
import earth.cora.picnic.ui.theme.HankoText
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LcdChip
import earth.cora.picnic.ui.theme.LcdState
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.PuffyText
import earth.cora.picnic.ui.theme.StickerSpot
import earth.cora.picnic.ui.theme.StoreAisleChip
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.drawDashedCircle
import earth.cora.picnic.ui.theme.hankoAngle
import earth.cora.picnic.ui.theme.hardShadow
import earth.cora.picnic.ui.theme.holoBrush
import earth.cora.picnic.ui.theme.tabPattern

/*
 * WP6's harness screen: a stand-in for WP5's list (not built yet) with the real anchors (rows, checkboxes, baskets, book
 * spots, stamps, the LCD), so every chain plays end to end on the test clock. It draws the list pieces from the
 * committed state plus the engine's presentation exactly as §8.7 tells WP5 to: rows by RowMotion, baskets by
 * MotionBasket, twins by BookTwin, stamps by StampCircle, the tray's curl and fold, the strip's ribbon. Test-only.
 */

/** Test tags. */
object HarnessTags {
    const val ROOT = "harness_root"
    const val BOOK = "harness_book"
    const val LIST = "harness_list"
    fun row(id: String) = "row_$id"
    fun name(id: String) = "name_$id"
    fun detail(id: String) = "detail_$id"
    fun tray(id: String) = "tray_$id"
    fun strip(id: String) = "strip_$id"
    fun twin(slot: Int) = "twin_$slot"
    fun chevron(id: String) = "chevron_$id"
}

/** The whole harness: top bar, book, list, add bar, the overlay and the celebration, with the engine running. */
@Composable
fun MotionHarness(
    model: HarnessModel,
    toasts: ToastHostState = remember { ToastHostState() },
    listState: LazyListState = rememberLazyListState(),
    nextStopCard: Boolean = true,
    celebration: Boolean = true,
    talkBack: Boolean = false,
) {
    val engine = model.engine
    val p = engine.current.value
    MotionEngineHost(engine, nextStopCard = nextStopCard, celebration = celebration, talkBackOverride = talkBack)
    Box(Modifier.fillMaxSize().testTag(HarnessTags.ROOT)) {
        PicnicBackground {
            Column(Modifier.fillMaxSize()) {
                HarnessTopBar(model, p)
                HarnessBook(model, p, Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp))
                HarnessList(model, p, listState, Modifier.weight(1f))
            }
            HarnessAddBar(Modifier.align(Alignment.BottomCenter))
        }
        MotionOverlay(engine, p, toasts, bottomInset = 84.dp)
        CelebrationHost(engine, p)
    }
}

@Composable
private fun HarnessTopBar(model: HarnessModel, p: ListPresentation) {
    val list = model.state.list(model.listId)!!
    val checked = list.items.count { it.checked }
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        PuffyText(list.title, style = PicnicTheme.type.screenTitle, fill = PicnicTheme.colors.ballpoint, stroke = 6.dp, modifier = Modifier.weight(1f))
        with(model.engine.anchors) {
            LcdChip(LcdState(count = p.lcdCount(checked), total = list.items.size), Modifier.anchor(AnchorRegistry.LCD))
        }
    }
}

/* ------------------------------------------------------------------------------------------------
 * The book stand-in: stamp page | spine | sticker page (§7.3), with the magic in place.
 * ------------------------------------------------------------------------------------------------ */

@Composable
fun HarnessBook(model: HarnessModel, p: ListPresentation, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val book = c.book
    val clock = model.engine.clock
    val g = model.grouped()
    val list = model.state.list(model.listId)!!
    val anchors = model.engine.anchors
    val shape = RoundedCornerShape(12.dp)
    val perfect = list.trip?.completedAt != null && list.trip?.finaleShownAt != null && !p.bookFx.perfectHeld
    Row(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .testTag(HarnessTags.BOOK)
            .hardShadow(shape, PicnicTheme.shadows, large = true)
            .clip(shape)
            .background(c.stickerWhite)
            .padding(3.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(book.page),
    ) {
        // The stamp page.
        Box(Modifier.width(117.dp).fillMaxSize().padding(6.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in g.sections.chunked(4)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (s in row) {
                            with(anchors) {
                                StampCircle(
                                    look = lookOf(s),
                                    number = s.position.takeIf { it > 0 },
                                    stamped = s.complete,
                                    now = s.id == g.nowSectionId,
                                    phase = p.stamps[s.id],
                                    clock = clock,
                                    modifier = Modifier.anchor(AnchorRegistry.stampKey(s.id)),
                                )
                            }
                        }
                    }
                }
            }
            if (perfect || p.bookFx.perfectT0 != null) {
                SpinePerfect(p.bookFx, clock, Modifier.align(Alignment.TopEnd).offset(x = 22.dp, y = 4.dp))
            }
        }
        // The spine: cloth stripes and three rings.
        Canvas(Modifier.width(12.dp).fillMaxSize()) {
            val stripes = book.spineStripes
            val w = size.width / stripes.size
            stripes.forEachIndexed { i, col -> drawRect(col, Offset(i * w, 0f), Size(w, size.height)) }
            for (y in listOf(9f, 31f, 53f)) {
                drawRoundRect(book.ringRim, Offset(-5.dp.toPx(), y.dp.toPx()), Size(22.dp.toPx(), 8.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                drawRoundRect(book.ringFace, Offset(-4.dp.toPx(), (y + 1.5f).dp.toPx()), Size(20.dp.toPx(), 5.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            }
        }
        // The sticker page: release paper, one spot per item, the twins in place.
        val items = list.items
        val bySlot = items.filter { it.checked && it.bookSlot != null }.associateBy { it.bookSlot!! }
        val lowestFree = (0 until items.size).firstOrNull { it !in bySlot }
        val next = p.bookFx.holdNext ?: lowestFree
        Box(
            Modifier
                .fillMaxSize()
                .bookPageLift(p.bookFx, clock)
                .bookPageTurn(p.bookFx, clock)
                .background(book.releasePaper)
                .drawBehind { drawBookGlint(p.bookFx, clock.now) }
                .padding(start = 7.dp, top = 4.dp),
        ) {
            Column {
                for (r in 0 until 3) {
                    Row {
                        for (col in 0 until 8) {
                            val slot = r * 8 + col
                            if (slot >= items.size) {
                                Spacer(Modifier.size(23.dp, 22.dp))
                                continue
                            }
                            Box(Modifier.size(23.dp, 22.dp), contentAlignment = Alignment.Center) {
                                with(anchors) {
                                    Canvas(Modifier.size(13.dp).anchor(AnchorRegistry.spotKey(slot))) {
                                        val r0 = size.minDimension / 2f - 0.75.dp.toPx()
                                        if (slot == next) {
                                            drawCircle(holoBrush(center), r0 + 0.75.dp.toPx())
                                            drawCircle(Color.White, r0 + 0.75.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                                        } else {
                                            drawCircle(book.spotFill, r0)
                                            drawDashedCircle(book.spotDash, center, r0, 1.5.dp.toPx(), 2.6.dp.toPx(), 2.dp.toPx())
                                        }
                                    }
                                }
                                val item = bySlot[slot]
                                val phase = p.spots[slot]
                                when {
                                    phase is SpotPhase.Peeling && phase.icon != null ->
                                        BookTwin(phase.icon, slot, phase, clock, Modifier.testTag(HarnessTags.twin(slot)), holo = phase.holo)
                                    item != null && phase != SpotPhase.Empty ->
                                        BookTwin(item.iconRef(), slot, phase, clock, Modifier.testTag(HarnessTags.twin(slot)), holo = item.rarity != Rarity.NONE)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The look of a section (§2.3). */
fun lookOf(s: AisleSection): AisleLook = if (s.kind == SectionKind.UNSORTED) AisleLooks.everythingElse else AisleLooks.of(s.position)

/* ------------------------------------------------------------------------------------------------
 * The list stand-in: folded strips and the open tray.
 * ------------------------------------------------------------------------------------------------ */

@Composable
fun HarnessList(model: HarnessModel, p: ListPresentation, state: LazyListState, modifier: Modifier = Modifier) {
    val g = model.grouped()
    val open = p.openSection(g.openSectionId)
    val scroll = p.scroll
    LaunchedEffect(scroll?.t0) {
        val s = scroll ?: return@LaunchedEffect
        val i = g.sections.indexOfFirst { it.id == s.sectionId }
        if (i >= 0) state.animateScrollToItem(i)
    }
    LazyColumn(
        modifier.fillMaxWidth().testTag(HarnessTags.LIST),
        state = state,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(g.sections, key = { it.id }) { s ->
            val chevron = remember { FocusRequester() }
            val strip = remember { FocusRequester() }
            val isOpen = s.id == open
            FoldFocusEffect(isOpen, anyOpen = !open.isNullOrEmpty(), userToggles = model.toggles.of(s.id), chevron = chevron, strip = strip)
            if (isOpen) HarnessTray(model, s, p, current = true, chevron = chevron) else HarnessStrip(model, s, p, strip)
        }
    }
}

private fun minis(s: AisleSection): List<BasketMini> = s.gotten.map { BasketMini(it.id, it.iconRef(), holo = it.rarity != Rarity.NONE, aisleIndex = s.position - 1) }

@Composable
private fun HarnessStrip(model: HarnessModel, s: AisleSection, p: ListPresentation, focus: FocusRequester) {
    val c = PicnicTheme.colors
    val look = lookOf(s)
    val clock = model.engine.clock
    val reduced = LocalReducedMotion.current
    val shape = RoundedCornerShape(6.dp)
    val ap = p.aisles[s.id]
    Box(
        Modifier
            .fillMaxWidth()
            .height(70.dp)
            .testTag(HarnessTags.strip(s.id))
            .unfoldMotion(ap?.foldT0, clock, reduced)
            .focusRequester(focus)
            .focusProperties { canFocus = true }
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, role = Role.Button) { model.tapStrip(s.id) },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.BottomStart)
                .hardShadow(shape, PicnicTheme.shadows)
                .clip(shape)
                .background(c.pad),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 6.dp)) {
            Tab(s, look, height = 32.dp, ballpoint = false)
            val toGet = s.toGet + (p.baskets[s.id]?.toGetLag ?: 0)
            Text(
                if (toGet == 0) "${s.total} of ${s.total} in the basket" else "$toGet left of ${s.total}",
                style = PicnicTheme.type.stripMeta,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
        if (s.complete || ap?.untieT0 != null) {
            StripRibbon(s.complete, ap, clock, 56.dp, Modifier.align(Alignment.BottomStart).offset(x = 196.dp))
        }
        with(model.engine.anchors) {
            MotionBasket(
                kind = s.basket,
                look = look,
                gotten = minis(s),
                toGet = s.toGet,
                complete = s.complete,
                presentation = p.baskets[s.id],
                clock = clock,
                size = DpSize(80.dp, 64.dp),
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-34).dp, y = 0.dp).basketAnchor(s.id, s.basket, priority = 0),
            )
        }
    }
}

@Composable
private fun Tab(s: AisleSection, look: AisleLook, height: Dp, ballpoint: Boolean) {
    val c = PicnicTheme.colors
    val shape = PicnicTheme.shapes.tab
    Row(
        Modifier
            .height(height)
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(c.stickerWhite)
            .padding(3.dp)
            .clip(shape)
            .background(look.family.pastel)
            .tabPattern(look)
            .padding(start = 5.dp, end = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(Color.White).border(2.dp, look.family.trace, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (s.position > 0) Text(s.position.toString(), style = PicnicTheme.type.tabNumber.copy(color = look.family.trace, fontSize = 13.sp))
        }
        Text(s.displayName, style = PicnicTheme.type.aisleTab.copy(color = if (ballpoint) c.ballpoint else look.family.trace, fontSize = 19.sp), maxLines = 1)
    }
}

@Composable
private fun HarnessTray(model: HarnessModel, s: AisleSection, p: ListPresentation, current: Boolean, chevron: FocusRequester) {
    val c = PicnicTheme.colors
    val look = lookOf(s)
    val clock = model.engine.clock
    val reduced = LocalReducedMotion.current
    val shape = RoundedCornerShape(12.dp)
    val ap = p.aisles[s.id]
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(HarnessTags.tray(s.id))
            .unfoldMotion(ap?.foldT0, clock, reduced)
            .trayCurl(ap?.curlT0, clock, flap = look.family.pastel, shadow = PicnicTheme.shadows.color, reduced = reduced)
            .then(if (current) Modifier.border(3.dp, Families.lemon.mid, RoundedCornerShape(15.dp)).padding(3.dp) else Modifier)
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(c.stickerWhite)
            .padding(3.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(look.family.pastel)
            .padding(start = 5.dp, end = 5.dp, bottom = 18.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tab(s, look, height = 38.dp, ballpoint = true)
                s.storeAisle?.let { StoreAisleChip(it) }
            }
            // The fold chevron (a 48 dp target), between the tab and the basket.
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-88).dp)
                    .size(48.dp)
                    .testTag(HarnessTags.chevron(s.id))
                    .focusRequester(chevron)
                    .focusProperties { canFocus = true }
                    // A stand-in target with no drawn indication (WP5 draws the real chevron and its focus ring).
                    .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = "Fold") { model.tapChevron(s.id) },
            )
            with(model.engine.anchors) {
                MotionBasket(
                    kind = s.basket,
                    look = look,
                    gotten = minis(s),
                    toGet = s.toGet,
                    complete = s.complete,
                    presentation = p.baskets[s.id],
                    clock = clock,
                    size = DpSize(64.dp, 51.dp),
                    tray = true,
                    modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-14).dp).basketAnchor(s.id, s.basket, priority = 1),
                )
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.pad)) {
            s.rows.forEachIndexed { i, item ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
                HarnessRow(model, item, p)
            }
        }
    }
}

@Composable
fun HarnessRow(model: HarnessModel, item: GroceryItem, p: ListPresentation) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val clock = model.engine.clock
    val reduced = LocalReducedMotion.current
    val phase = p.rows[item.id]
    val start = p.rowStarts[item.id]
    val anchors = model.engine.anchors
    val holo = item.rarity != Rarity.NONE
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(HarnessTags.row(item.id))
            .clearAndSetSemantics {
                contentDescription = item.name
                role = Role.Checkbox
            }
            .clickable { model.toggle(item.id) }
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp)) {
            with(anchors) {
                Box(Modifier.size(52.dp).anchor(AnchorRegistry.rowKey(item.id))) {
                    val ghostAlpha = RowMotion.ghostAlpha(item.checked, phase, start, clock.now, reduced)
                    if (ghostAlpha > 0f) ItemIcon(item.iconRef(), null, Modifier.graphicsLayer { alpha = RowMotion.ghostAlpha(item.checked, phase, start, clock.now, reduced) }, ghost = true)
                    if (ghostAlpha < 1f) {
                        ItemIcon(
                            item.iconRef(), null,
                            Modifier
                                .rowStickerPeel(phase, start, clock)
                                .graphicsLayer { alpha = 1f - RowMotion.ghostAlpha(item.checked, phase, start, clock.now, reduced) },
                            holo = holo,
                        )
                    }
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(item.name, style = t.itemName.copy(color = if (item.checked) c.ballpointSoft else c.ballpoint), modifier = Modifier.testTag(HarnessTags.name(item.id)))
            item.quantity?.let { Text(it, style = t.itemDetail, modifier = Modifier.testTag(HarnessTags.detail(item.id))) }
        }
        with(anchors) {
            Box(Modifier.size(48.dp).anchor(AnchorRegistry.checkKey(item.id)), contentAlignment = Alignment.Center) {
                val hanko = RowMotion.hankoScale(item.checked, phase, start, clock.now, reduced)
                val lifting = RowMotion.liftsHanko(phase)
                if (!item.checked && !lifting) StickerSpot()
                if (item.checked || lifting) {
                    if (lifting) StickerSpot()
                    Box(Modifier.size(48.dp).rowHanko(item.checked, phase, start, clock, reduced)) {
                        if (hanko > 0f || phase == null) {
                            HankoStamp(HankoText.GotIt, Modifier.offset(4.dp, 4.dp).graphicsLayer { rotationZ = hankoAngle(item.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HarnessAddBar(modifier: Modifier) {
    val c = PicnicTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(c.pad)
            .drawBehind {
                // The perforated top edge.
                var x = 5.dp.toPx()
                while (x < size.width) {
                    drawCircle(c.binder, 2.dp.toPx(), Offset(x, 0f))
                    x += 10.dp.toPx()
                }
            }
            .padding(start = 18.dp, top = 22.dp),
    ) {
        Text("Write a note…", style = PicnicTheme.type.fieldValue.copy(color = c.ballpointSoft))
    }
}
