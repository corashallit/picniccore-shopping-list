package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import earth.cora.picnic.core.assistant.OfflineParser
import earth.cora.picnic.core.assistant.toGroceryItem
import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.core.model.AisleSection
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.core.model.TripStats
import earth.cora.picnic.core.model.iconRef
import earth.cora.picnic.core.model.ingestContext
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.platform.NoOpFeedback
import earth.cora.picnic.platform.ShakeEffect
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.add.AddBar
import earth.cora.picnic.ui.add.AddBarUi
import earth.cora.picnic.ui.add.AddRules
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.BasketMini
import earth.cora.picnic.ui.list.motion.BookTwin
import earth.cora.picnic.ui.list.motion.CelebrationHost
import earth.cora.picnic.ui.list.motion.FinaleKeeper
import earth.cora.picnic.ui.list.motion.ListPresentation
import earth.cora.picnic.ui.list.motion.MotionEngineHost
import earth.cora.picnic.ui.list.motion.MotionOverlay
import earth.cora.picnic.ui.list.motion.RowMotion
import earth.cora.picnic.ui.list.motion.SpinePerfect
import earth.cora.picnic.ui.list.motion.SpotPhase
import earth.cora.picnic.ui.list.motion.StampCircle
import earth.cora.picnic.ui.list.motion.StripRibbon
import earth.cora.picnic.ui.list.motion.MotionBasket
import earth.cora.picnic.ui.list.motion.rowHanko
import earth.cora.picnic.ui.list.motion.rowStickerPeel
import earth.cora.picnic.ui.theme.AisleLook
import earth.cora.picnic.ui.theme.AisleLooks
import earth.cora.picnic.ui.theme.BinderTopBar
import earth.cora.picnic.ui.theme.DecorSticker
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
import earth.cora.picnic.ui.theme.hankoAngle
import earth.cora.picnic.ui.theme.hardShadow
import earth.cora.picnic.ui.theme.holoBrush
import earth.cora.picnic.ui.theme.patternFill
import earth.cora.picnic.ui.theme.rememberVectorArt
import earth.cora.picnic.ui.theme.tabPattern
import earth.cora.picnic.ui.theme.drawDashedCircle
import earth.cora.picnic.ui.theme.gen.DecorManifest
import earth.cora.picnic.ui.theme.drawArt
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The Surprise lab's practice list (docs/DESIGN_STICKER.md §6.9): the practice copy of "Saturday shop" drawn as the list
 * draws it (the binder top with the sun and the LCD, the sticker book, folded strips and the open tray, the add bar),
 * with WP6's chains and overlay and every egg overlay mounted, so each egg plays on it as it plays on her list. Its
 * anchors are the ones the eggs look for (`sun`, `lcd`, `addbar`, `row:`, `check:`, `basket:`, `stamp:`, and WP9's
 * `strip:`, `tab:`, `text:`). Owner: WP9. (WP5's list screen is the real one; this is the lab's practice copy.)
 */

/** Test tags. */
object PracticeTags {
    const val ROOT: String = "practice_root"
    const val LIST: String = "practice_list"
    const val BOOK: String = "practice_book"
    const val STAMP_PAGE: String = "practice_stamps"
    const val SLIP: String = "practice_receipt_slip"
    const val ADD_BAR: String = "practice_add_bar"
    fun row(id: String): String = "practice_row_$id"
    fun hanko(id: String): String = "practice_hanko_$id"
    fun strip(id: String): String = "practice_strip_$id"
    fun basket(id: String): String = "practice_basket_$id"
    fun sticker(id: String): String = "practice_sticker_$id"
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
private val TORONTO: ZoneId = ZoneId.of("America/Toronto")

/**
 * The practice list for [model], under [ribbon] (the lab's "Surprise lab · practice list" strip, or nothing). [listState]
 * drives the lazy list (the rolling produce scrolls its aisle into view, §12.2 egg 3).
 */
@Composable
fun PracticeList(
    model: PracticeModel,
    modifier: Modifier = Modifier,
    feedback: Feedback = NoOpFeedback,
    listState: LazyListState = rememberLazyListState(),
    ribbon: (@Composable () -> Unit)? = null,
    talkBack: Boolean = false,
) {
    val engine = model.engine
    val state by model.store.states.collectAsState()
    val eggs by engine.state.collectAsState()
    val album by model.album.album.collectAsState(initial = null)
    model.albumNow = album
    val motion = model.motion
    val p = motion.current.value
    val anchors = motion.anchors
    MotionEngineHost(motion, eggs = eggs.enabled, talkBackOverride = talkBack)
    // The finale's state (finaleShownAt, the bonus) on the practice copy.
    LaunchedEffect(motion) { FinaleKeeper(model.store, model.album).let { keeper -> motion.events.collect { keeper.handle(it) } } }
    DisposableEffect(engine) {
        engine.onListShown(model.listId)
        onDispose { engine.onListHidden() }
    }
    // Egg 4 on the practice copy too: a shake while it shows.
    ShakeEffect(enabled = eggs.enabled) { engine.onShake() }
    val list = model.list(state)
    val grouped = model.grouped(state)
    val open = p.openSection(grouped.openSectionId)
    // Egg 3: the list scrolls to the rolling item's aisle.
    val rolling = eggs.rolling
    LaunchedEffect(rolling?.id) {
        val r = rolling ?: return@LaunchedEffect
        val i = grouped.sections.indexOfFirst { it.id == r.sectionId }
        if (i >= 0) listState.animateScrollToItem(i)
    }
    // An add brings its aisle into view if it is not wholly on screen (egg 9's cake row, egg 6's shiny row).
    val focus = model.focus
    LaunchedEffect(focus) {
        val f = focus ?: return@LaunchedEffect
        val i = grouped.sections.indexOfFirst { it.id == f.sectionId }
        if (i < 0) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { } // the row the add made is laid out
        val info = listState.layoutInfo
        val seen = info.visibleItemsInfo.firstOrNull { it.index == i }
        val bottom = info.viewportEndOffset - info.afterContentPadding
        if (seen == null || seen.offset < 0 || seen.offset + seen.size > bottom) listState.animateScrollToItem(i)
    }
    // Egg 8: the basket the cat sleeps in comes into view (two strips above it stay on screen).
    LaunchedEffect(eggs.napSectionId) {
        val nap = eggs.napSectionId ?: return@LaunchedEffect
        val i = grouped.sections.indexOfFirst { it.id == nap }
        if (i >= 0) listState.animateScrollToItem((i - 2).coerceAtLeast(0))
    }
    Box(modifier.fillMaxSize().testTag(PracticeTags.ROOT)) {
        PicnicBackground {
            Column(Modifier.fillMaxSize()) {
                ribbon?.invoke()
                PracticeTopBar(model, list.title, state.store.name, grouped.stops, list.items.count { it.checked }, list.items.size, eggs, p)
                PracticeBook(model, state, grouped, eggs, p, Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp))
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().testTag(PracticeTags.LIST),
                    state = listState,
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(grouped.sections, key = { it.id }) { s ->
                        if (s.id == open) PracticeTray(model, s, eggs, p, current = s.id == grouped.nowSectionId) else PracticeStrip(model, s, eggs, p)
                    }
                }
            }
            PracticeAddBar(model, eggs, Modifier.align(Alignment.BottomCenter))
        }
        MotionOverlay(motion, p, model.toasts, bottomInset = 84.dp) {
            EggOverlays(engine, anchors, model.toasts, motion, feedback, listPlacesDecor = true)
        }
        CelebrationHost(motion, p)
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
    val subtitle = "$store · " + if (stops == 1) "1 stop" else "$stops stops"
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
                .graphicsLayer { alpha = if (eggs.duskFx != null) 0f else 1f },
            // The LCD's digits are words too: bursts are cut clear of them.
            lcdModifier = Modifier.anchor(AnchorRegistry.LCD).anchor(EggAnchors.TEXT_PREFIX + "lcd"),
            titleModifier = Modifier.anchor(EggAnchors.TEXT_PREFIX + "title"),
            subtitleModifier = Modifier.anchor(EggAnchors.TEXT_PREFIX + "subtitle"),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/* ------------------------------------------------------------------------------------------------
 * The sticker book (§7.3): the stamp page, the spine, the sticker page.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun PracticeBook(
    model: PracticeModel,
    state: earth.cora.picnic.core.model.AppState,
    grouped: earth.cora.picnic.core.model.GroupedList,
    eggs: SurpriseState,
    p: ListPresentation,
    modifier: Modifier = Modifier,
) {
    val c = PicnicTheme.colors
    val book = c.book
    val clock = model.motion.clock
    val anchors = model.anchors
    val list = model.list(state)
    val trip = list.trip
    val shape = RoundedCornerShape(12.dp)
    val perfect = trip?.completedAt != null && trip.finaleShownAt != null && !p.bookFx.perfectHeld
    val engine = model.engine
    val start = TripStats.lastSessionStart(list)
    val date = DATE.format(Instant.ofEpochMilli(start ?: model.now()).atZone(TORONTO))
    Box(modifier.fillMaxWidth().height(76.dp)) {
        Row(
            Modifier
                .fillMaxSize()
                .testTag(PracticeTags.BOOK)
                .hardShadow(shape, PicnicTheme.shadows, large = true)
                .clip(shape)
                .background(c.stickerWhite)
                .padding(3.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(book.page),
        ) {
            // The stamp page: taps go to the nearest stamp (egg 10); holo for a perfect week (egg 14).
            val sections = grouped.sections
            Box(
                Modifier
                    .width(117.dp)
                    .fillMaxHeight()
                    .then(if (eggs.perfectWeek || perfect) Modifier.drawBehind { drawRect(holoBrush(center), alpha = 0.55f) } else Modifier)
                    .testTag(PracticeTags.STAMP_PAGE)
                    .pointerInput(sections.size) {
                        detectTapGestures { at ->
                            val col = ((at.x - 6.dp.toPx()) / 27.dp.toPx()).toInt().coerceIn(0, 3)
                            val row = if (at.y > size.height / 2f + 6.dp.toPx()) 1 else 0
                            val index = row * 4 + col
                            if (index < sections.size) engine.onStampTap(index)
                        }
                    }
                    .padding(start = 6.dp, top = 24.dp, end = 4.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (r in sections.chunked(4)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (s in r) {
                                with(anchors) {
                                    Box(contentAlignment = Alignment.Center) {
                                        StampCircle(
                                            look = lookOf(s, state),
                                            number = s.position.takeIf { it > 0 },
                                            stamped = s.complete,
                                            now = s.id == grouped.nowSectionId,
                                            phase = p.stamps[s.id],
                                            clock = clock,
                                            modifier = Modifier.anchor(AnchorRegistry.stampKey(s.id)),
                                        )
                                        // "Everything else" has no number: its circle carries the capsule glyph (§5).
                                        if (s.position <= 0 && !s.complete) CapsuleGlyph(if (s.id == grouped.nowSectionId) Families.strawberry.trace else c.ballpointSoft)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spine(book)
            // The sticker page.
            val items = list.items
            val bySlot = items.filter { it.checked && it.bookSlot != null }.associateBy { it.bookSlot!! }
            val next = p.bookFx.holdNext ?: (0 until items.size).firstOrNull { it !in bySlot }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(book.releasePaper)
                    .padding(start = 6.dp, top = 3.dp),
            ) {
                // Eight columns always fit: a narrower page (the lab's framed practice list) takes narrower cells.
                BoxWithConstraints {
                val cell = minOf(23.dp, maxWidth / 8)
                Column {
                    for (r in 0 until 3) {
                        Row {
                            for (col in 0 until 8) {
                                val slot = r * 8 + col
                                Box(Modifier.size(cell, 22.dp), contentAlignment = Alignment.Center) {
                                    if (slot >= items.size) return@Box
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
                                        phase is SpotPhase.Peeling && phase.icon != null -> BookTwin(phase.icon, slot, phase, clock, holo = phase.holo, pixel = eggs.pixelStickers)
                                        item != null && phase != SpotPhase.Empty -> BookTwin(
                                            item.iconRef(), slot, phase, clock,
                                            Modifier.eggTwinkle(eggs, slot),
                                            holo = item.rarity != Rarity.NONE,
                                            pixel = eggs.pixelStickers,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
            // The fore-edge.
            Box(Modifier.width(5.dp).fillMaxHeight().background(book.foreEdge))
        }
        // The date tag, a little crooked, on the stamp page (with the season's pictogram, egg 11).
        DateTag(date, eggs.season.takeIf { eggs.enabled }, Modifier.offset(x = 8.dp, y = 4.dp))
        if (perfect || p.bookFx.perfectT0 != null) {
            SpinePerfect(p.bookFx, clock, Modifier.align(Alignment.TopStart).offset(x = 104.dp, y = 6.dp))
        }
        // Egg 18: the saved receipt's slip pokes out of the fore-edge.
        if (trip?.receiptSaved == true) {
            BookReceiptSlip(
                Modifier.align(Alignment.CenterEnd).offset(x = 10.dp, y = 6.dp).testTag(PracticeTags.SLIP),
                appearingId = eggs.receiptTuckId,
            )
        }
    }
}

/** The capsule glyph in the "Everything else" stamp's circle, in [ink]. Decorative. */
@Composable
private fun CapsuleGlyph(ink: Color) {
    val art = rememberVectorArt(DecorManifest.pictograms.getValue("capsule"))
    Canvas(Modifier.size(14.dp).decorative()) {
        drawArt(art, size, paint = { path, _ -> if (path.name == "ink") androidx.compose.ui.graphics.SolidColor(ink) else null })
    }
}

@Composable
private fun Spine(book: earth.cora.picnic.ui.theme.BookColors) {
    Canvas(Modifier.width(12.dp).fillMaxHeight().decorative()) {
        val stripes = book.spineStripes
        val w = size.width / stripes.size
        stripes.forEachIndexed { i, col -> drawRect(col, Offset(i * w, 0f), Size(w, size.height)) }
        for (y in listOf(9f, 31f, 53f)) {
            drawRoundRect(book.ringRim, Offset(-5.dp.toPx(), y.dp.toPx()), Size(22.dp.toPx(), 8.dp.toPx()), CornerRadius(4.dp.toPx()))
            drawRoundRect(book.ringFace, Offset(-4.dp.toPx(), (y + 1.5f).dp.toPx()), Size(20.dp.toPx(), 5.dp.toPx()), CornerRadius(3.dp.toPx()))
        }
    }
}

/** The book's date tag (§7.3): a lemon-pastel tag, a sakura heart, the date, and the season's pictogram (egg 11). */
@Composable
private fun DateTag(date: String, season: Season?, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val shape = RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp, topEnd = 9.dp, bottomEnd = 9.dp)
    Row(
        modifier
            .graphicsLayer { rotationZ = -2f }
            .heightIn(min = 18.dp)
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(c.stickerWhite)
            .padding(2.dp)
            .clip(shape)
            .background(Families.lemon.pastel)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        DecorSticker("heart", size = 11.dp, shadow = false)
        Text(
            date,
            style = PicnicTheme.type.label.copy(
                color = c.ballpoint,
                fontSize = 13.sp,
                lineHeight = 13.sp,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            maxLines = 1,
        )
        val pictogram = season?.let { seasonPictogram(it) }
        if (pictogram != null) {
            val art = rememberVectorArt(DecorManifest.pictograms.getValue(pictogram))
            Canvas(Modifier.size(12.dp).decorative()) { drawArt(art, size) }
        }
    }
}

/** The pictogram on the book's date label for [season] (§4.13). */
fun seasonPictogram(season: Season): String = when (season) {
    Season.CANADA_DAY -> "maple"
    Season.THANKSGIVING -> "pumpkin"
    Season.HALLOWEEN -> "ghost"
    Season.FIRST_SNOW -> "snowflake"
    Season.VALENTINES -> "heart"
    Season.LUNAR_NEW_YEAR -> "envelope"
}

private fun lookOf(s: AisleSection, state: earth.cora.picnic.core.model.AppState): AisleLook =
    if (s.kind == SectionKind.UNSORTED) AisleLooks.everythingElse else AisleLooks.of(s.position, s.aisle?.let { state.store.aisleStyles[it.id] })

/* ------------------------------------------------------------------------------------------------
 * Strips and the open tray.
 * ------------------------------------------------------------------------------------------------ */

@Composable
private fun PracticeTab(s: AisleSection, look: AisleLook, height: androidx.compose.ui.unit.Dp, current: Boolean, model: PracticeModel) {
    val c = PicnicTheme.colors
    val shape = PicnicTheme.shapes.tab
    val name = if (s.kind == SectionKind.UNSORTED) stringResource(R.string.egg_practice_everything_else) else s.displayName
    // Large text: the tab grows with its words and the number circle with its numeral (up to 1.6×).
    val grow = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
    with(model.anchors) {
        Row(
            Modifier
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
                Modifier.size(24.dp * grow).clip(CircleShape).background(Color.White).border(2.dp, look.family.trace, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (s.position > 0) {
                    Text(s.position.toString(), style = PicnicTheme.type.tabNumber.copy(color = look.family.trace), modifier = Modifier.anchor(EggAnchors.TEXT_PREFIX + "num:" + s.id))
                } else {
                    CapsuleGlyph(look.family.trace)
                }
            }
            TabName(
                name,
                PicnicTheme.type.aisleTab.copy(color = if (current) c.ballpoint else look.family.trace, fontSize = 19.sp),
                Modifier.anchor(EggAnchors.TEXT_PREFIX + "tab:" + s.id),
            )
        }
    }
}

/**
 * A tab's name at large text: it wraps between words onto a second line rather than losing words, and steps its size
 * down (never under 13 sp, §2.7) until its longest word fits on a line, so no word is ever broken mid-way.
 */
@Composable
private fun TabName(name: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 8)
    BoxWithConstraints(modifier) {
        val room = constraints.maxWidth
        val longest = name.split(' ').filter { it.isNotEmpty() }
        var size = style.fontSize.value
        fun fits(sp: Float) = longest.all { word ->
            measurer.measure(word, style.copy(fontSize = sp.sp), maxLines = 1, softWrap = false).size.width <= room
        }
        while (size > 13f && room != Constraints.Infinity && !fits(size)) size -= 0.5f
        Text(name, style = style.copy(fontSize = size.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** What [s]'s basket holds as the practice list draws it (holo pulls stay holo, R4). */
internal fun minis(s: AisleSection, pixel: Boolean): List<BasketMini> =
    s.gotten.map { BasketMini(it.id, it.iconRef(), holo = it.rarity != Rarity.NONE, aisleIndex = s.position - 1, pixel = pixel) }

@Composable
private fun PracticeStrip(model: PracticeModel, s: AisleSection, eggs: SurpriseState, p: ListPresentation) {
    val c = PicnicTheme.colors
    val look = lookOf(s, model.state)
    val clock = model.motion.clock
    val shape = RoundedCornerShape(6.dp)
    val anchors = model.anchors
    val threshold = earth.cora.picnic.ui.theme.LocalTimeouts.current.holdThresholdMs
    val engine by rememberUpdatedState(model.engine)
    val density = LocalDensity.current
    // The strip's height (it grows with large text) and where its words end, for the ribbon (§4.10 folded strip).
    var stripPx by remember { mutableStateOf(0) }
    var tabPx by remember { mutableStateOf(0) }
    var metaPx by remember { mutableStateOf(0) }
    with(anchors) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 70.dp)
                .onSizeChanged { stripPx = it.height }
                .testTag(PracticeTags.strip(s.id))
                .semantics(mergeDescendants = true) { role = Role.Button }
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
            // The words keep clear of the basket (its 80 dp box sits 44 dp in from the end) at any font scale.
            Column(Modifier.padding(start = 6.dp, top = 11.dp, bottom = 6.dp, end = STRIP_TEXT_END)) {
                Row(Modifier.onSizeChanged { tabPx = it.width }) {
                    PracticeTab(s, look, 32.dp, current = false, model = model)
                    SnackDoodle(eggs, s.id, Modifier.padding(start = 4.dp).offset(y = (-2).dp))
                }
                val toGet = s.toGet + (p.baskets[s.id]?.toGetLag ?: 0)
                Row(Modifier.padding(start = 4.dp, top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (toGet == 0) "${s.total} of ${s.total} in the basket" else "$toGet left of ${s.total}",
                        style = PicnicTheme.type.stripMeta.copy(color = c.inkSoft),
                        modifier = Modifier.anchor(EggAnchors.TEXT_PREFIX + "meta:" + s.id).onSizeChanged { metaPx = it.width },
                    )
                }
            }
            if (s.complete && stripPx > 0) {
                // The ribbon is placed from the words, never over them: 12 dp after the farther of the tab and the meta
                // line, and at most 22 dp before the basket; with no such gap, the bow alone ties at the basket's left
                // shoulder, tied behind it (§4.10).
                val textEnd = with(density) { 6.dp + maxOf(tabPx, metaPx + 4.dp.roundToPx()).toDp() }
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
        }
    }
}

/** The folded strip's white body starts this far down its box (the tab pokes above it). */
private val STRIP_TOP = 14.dp

/** The strip's words end this far before its end: no further than the basket's art (its 80 dp box sits 44 dp in). */
private val STRIP_TEXT_END = 120.dp

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
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x - w / 2, center.y - h / 2)
                lineTo(center.x, center.y + h / 2)
                lineTo(center.x + w / 2, center.y - h / 2)
            }
            drawPath(path, c.ink, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

@Composable
private fun PracticeTray(model: PracticeModel, s: AisleSection, eggs: SurpriseState, p: ListPresentation, current: Boolean) {
    val c = PicnicTheme.colors
    val look = lookOf(s, model.state)
    val clock = model.motion.clock
    val shape = RoundedCornerShape(12.dp)
    val ring = if (c.variant == earth.cora.picnic.ui.theme.ThemeVariant.Glow) c.glowStar else Families.lemon.mid
    with(model.anchors) {
      Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(3.dp, ring, RoundedCornerShape(15.dp))
                .padding(3.dp)
                .hardShadow(shape, PicnicTheme.shadows)
                .clip(shape)
                .background(c.stickerWhite)
                .padding(3.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(look.family.pastel),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 5.dp)) {
                // The tab and the store-aisle chip keep clear of the basket (64 dp, 52 dp in); with large text the chip
                // wraps under the tab rather than sliding behind the basket.
                androidx.compose.foundation.layout.FlowRow(
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
                        clock = clock,
                        size = DpSize(64.dp, 51.dp),
                        tray = true,
                        modifier = Modifier.basketAnchor(s.id, s.basket, priority = 1).basketHello(eggs, s.id),
                    )
                    NapCatInBasket(eggs, s.id, s.basket, model.engine, chainsDone = p.chainsDone)
                }
                Box(Modifier.align(Alignment.CenterEnd).clickable(remember { MutableInteractionSource() }, indication = null) { model.open("") }) {
                    Chevron(Modifier, up = true)
                }
            }
            Column(
                Modifier
                    .padding(horizontal = 5.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.pad),
            ) {
                s.rows.forEachIndexed { i, item ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
                    PracticeRow(model, item, s, eggs, p, i)
                }
            }
            Box(Modifier.fillMaxWidth().height(15.dp).patternFill(look, 0.55f)) {
                // The standing ladybug on the end cap (it steps off while the nap cat and the umbrella both show).
                EndCapLadybug(eggs, Modifier.align(Alignment.BottomStart).offset(x = 30.dp, y = (-1).dp))
            }
        }
        // The standing bee flies its loop over the current aisle's basket (§4.27), outside the tray's clip so its loop
        // can rise over the tray's top edge as the mockup's does (header content edge: 9 dp in, 5 dp padding).
        if (current) TrayBee(Modifier.align(Alignment.TopEnd).offset(x = (-72).dp, y = (-3).dp))
      }
    }
}

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
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(PracticeTags.row(item.id))
            .clearAndSetSemantics {
                contentDescription = item.name
                role = Role.Checkbox
            }
            .clickable(remember { MutableInteractionSource() }, indication = null) { model.toggle(item.id) }
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
                androidx.compose.foundation.layout.FlowRow(
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
            tape = AddRules.addBarTape(eggs),
            // The whole bar is a keep-out: its field, placeholder and doodle are words, and bursts fall behind it.
            modifier = modifier.anchor(AnchorRegistry.ADD_BAR).anchor(EggAnchors.TEXT_PREFIX + "addbar").testTag(PracticeTags.ADD_BAR),
        )
    }
}

/** WP6's check-off chains have played out (no hop in flight, no burst, no celebration): the nap cat may curl up. */
private val ListPresentation.chainsDone: Boolean get() = flights.isEmpty() && bursts.isEmpty() && celebration == null
