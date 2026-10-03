package earth.cora.picnic.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.list.BookStickerUi
import earth.cora.picnic.ui.list.BookUi
import earth.cora.picnic.ui.list.StampUi
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.BookFx
import earth.cora.picnic.ui.list.motion.BookMotion
import earth.cora.picnic.ui.list.motion.BookPresentation
import earth.cora.picnic.ui.list.motion.BookTwin
import earth.cora.picnic.ui.list.motion.MotionClock
import earth.cora.picnic.ui.list.motion.SpinePerfect
import earth.cora.picnic.ui.list.motion.SpotPhase
import earth.cora.picnic.ui.list.motion.StampCircle
import earth.cora.picnic.ui.list.motion.bookPageLift
import earth.cora.picnic.ui.list.motion.drawBookGlint
import earth.cora.picnic.ui.list.motion.showsNewPage
import kotlin.math.hypot
import kotlinx.coroutines.withContext

/*
 * The trip's sticker book (docs/DESIGN_STICKER.md §7.3–§7.7): a ring-bound mini album under the app bar. Left page = the
 * trip's stamp card, one circle per stop; right page = one spot per item on release paper, the twins hand-stuck in
 * their spots. Everything about it updates IN PLACE (R5): WP6's BookTwin plays the magic appear and the peel off, the
 * stamp thunk and PER-FECT! from the presentation; nothing ever flies here. Owner: WP5 (BookUiTest, BookInteractionTest,
 * BookA11yTest, the book goldens).
 */

/** Test tags. */
object BookTags {
    const val BOOK: String = "picnic_sticker_book"
    const val LEFT: String = "picnic_book_left"
    const val RIGHT: String = "picnic_book_right"
    const val NAME_TAG: String = "picnic_book_name_tag"
    const val COLLAPSED: String = "picnic_book_collapsed"
    const val PAGE_LCD: String = "picnic_book_page_lcd"
    fun twin(slot: Int): String = "picnic_book_twin_$slot"
    fun spot(slot: Int): String = "picnic_book_spot_$slot"
    fun stamp(sectionId: String): String = "picnic_book_stamp_$sectionId"
}

/** What the right page shows: the page, the holo next spot, whether the paper is holo (tests: BookUiTest). */
object BookRules {
    /** The spot that shows holo: [fx]'s held spot while a twin waits, else the lowest free spot (none when full). */
    fun nextSpot(book: BookUi, fx: BookFx): Int? = fx.holdNext ?: (0 until book.total).firstOrNull { it !in book.takenSlots }

    /** The fore-edge (and the release paper) turn holo only once the book is full or PERFECT (§2.6, §7.5). */
    fun foreEdgeHolo(book: BookUi, fx: BookFx): Boolean = (book.full && !fx.fullHeld) || perfect(book, fx)

    /** PERFECT shows once the finale is over (held while the celebration plays) or while it is being pressed. */
    fun perfect(book: BookUi, fx: BookFx): Boolean = (book.perfect && !fx.perfectHeld) || fx.perfectT0 != null

    /** A twin's tilt (§7.3): ROTS[slot % 12]. */
    fun tilt(slot: Int): Float = BookMotion.tilt(slot)
}

/**
 * The sticker book, [width] wide (the screen − 24 dp, at most 456 dp): expanded 76 dp (102 dp with three stamp rows), or
 * the 40 dp [collapsed] strip (IME open, a short window, scrolled down with the app bar, §7.4). [presentation] plays the
 * chains on [clock]; [anchors] records the spots and stamps. Tap the right page → [onOpen] (the Trip book sheet); swipe
 * it to turn pages (it returns to the shown page after 6 s idle); tap the left page → the nearest stamp boings, its
 * name tag is clipped onto the right page for its reading hold, and [onStampTap] feeds egg 10. TalkBack: one node with
 * the summary and the action "Open trip book"; the twins have no nodes. [dateOnPage] false: the date is a caption above
 * the book (font scale over 1.3). [turnKey] changes: the page turns to a fresh book (New trip).
 */
@Composable
fun StickerBook(
    state: BookUi,
    presentation: BookPresentation,
    clock: MotionClock,
    width: Dp,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    anchors: AnchorRegistry? = null,
    dateOnPage: Boolean = true,
    turnKey: Int = 0,
    onOpen: () -> Unit = {},
    onStampTap: (position: Int) -> Unit = {},
) {
    val c = PicnicTheme.colors
    val shapes = PicnicTheme.shapes
    val layout = remember(width, state.stamps.size, state.total) { BookLayout.of(width.value, state.stamps.size, state.total) }
    val summary = bookSummary(state)
    val openLabel = stringResource(R.string.list_book_open)
    val open by rememberUpdatedState(onOpen)
    // The book's own turn to a fresh page on New trip (3 frames, 240 ms); the engine turns it for an automatic new book.
    val freshTurn = rememberFreshTurn(turnKey)
    Box(
        modifier
            .width(width)
            .testTag(BookTags.BOOK)
            .clearAndSetSemantics {
                contentDescription = summary
                role = Role.Button
                onClick(label = openLabel) {
                    open()
                    true
                }
            },
    ) {
        if (collapsed) {
            CollapsedBook(state, presentation, clock, Modifier.testTag(BookTags.COLLAPSED), dateOnPage)
            return@Box
        }
        // The saved receipt pokes out of the fore-edge behind the page (§7.3, egg 18).
        if (state.receiptSaved) ReceiptSlip(Modifier.align(Alignment.CenterEnd).offset(x = 9.dp, y = 6.dp))
        val shape = shapes.bookOuter
        // The page shown (§7.4): the engine's, else the one she swiped to (for 6 s), else the page of the next free spot.
        val paging = remember { PagingState() }
        val page = (presentation.fx.showPage ?: paging.userPage ?: layout.shownPage(state.takenSlots)).coerceIn(0, layout.pages - 1)
        Box(
            Modifier
                .fillMaxWidth()
                .height(layout.height.dp)
                .hardShadow(shape, PicnicTheme.shadows, large = true)
                .clip(shape)
                .background(c.stickerWhite)
                .padding(3.dp)
                .clip(shapes.bookPage)
                .background(c.book.page),
        ) {
            BookPages(state, presentation, clock, layout, anchors, dateOnPage, freshTurn, onStampTap, onOpen, paging, page)
        }
        // The page number (§7.3) clips onto the page's bottom edge at the right: inside the page it would sit on the
        // last spot of a full page. It hangs into the 8 dp under the book and ends where the list's viewport begins.
        if (layout.pages > 1) {
            PageLcd("${page + 1}/${layout.pages}", Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = PAGE_LCD_DROP))
        }
    }
}

/** The book's TalkBack line (§7.7): "Trip sticker book, Sat 3 Oct: 10 of 24 stickers, 1 of 8 aisles stamped". */
@Composable
fun bookSummary(state: BookUi): String {
    val stickers = pluralStringResource(R.plurals.list_book_stickers, state.total, state.checked, state.total)
    val aisles = pluralStringResource(R.plurals.list_book_aisles, state.stamps.size, state.stamped, state.stamps.size)
    return stringResource(R.string.list_book_summary, state.dateLabel, stickers, aisles)
}

/** A New trip's page turn, started when [turnKey] changes (never on first composition). Null when none plays. */
@Composable
private fun rememberFreshTurn(turnKey: Int): Long? {
    val reduced = LocalReducedMotion.current
    var first by remember { mutableStateOf(turnKey) }
    var t0 by remember { mutableStateOf<Long?>(null) }
    var now by remember { mutableStateOf(0L) }
    LaunchedEffect(turnKey) {
        if (turnKey == first || reduced) {
            first = turnKey
            return@LaunchedEffect
        }
        first = turnKey
        withContext(FixedMotionScale) {
            val start = androidx.compose.runtime.withFrameMillis { it }
            t0 = start
            while (true) {
                val n = androidx.compose.runtime.withFrameMillis { it }
                now = n - start
                if (n - start >= PAGE_TURN_MS) break
            }
            t0 = null
        }
    }
    return t0?.let { now }
}

@Composable
private fun BookPages(
    state: BookUi,
    presentation: BookPresentation,
    clock: MotionClock,
    layout: BookLayout,
    anchors: AnchorRegistry?,
    dateOnPage: Boolean,
    freshTurnElapsed: Long?,
    onStampTap: (Int) -> Unit,
    onOpen: () -> Unit,
    paging: PagingState,
    page: Int,
) {
    val c = PicnicTheme.colors
    val fx = presentation.fx
    val reduced = LocalReducedMotion.current
    val timeouts = LocalTimeouts.current
    val perfect = BookRules.perfect(state, fx)
    var tag by remember { mutableStateOf<StampTag?>(null) }
    var boing by remember { mutableStateOf<String?>(null) }
    val resources = LocalResources.current
    val gridTop = stampTop(dateOnPage)
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            // ---- The stamp page (left) ----
            Box(
                Modifier
                    .width(layout.leftWidth.dp)
                    .fillMaxHeight()
                    .testTag(BookTags.LEFT)
                    .probe(LocalPieceProbe.current, "bookLeft")
                    .then(if (perfect) Modifier.drawBehind { drawRect(holoBrush(center)) } else Modifier)
                    .pointerInput(state.stamps, layout, gridTop) {
                        detectTapGestures { pos ->
                            val hit = nearestStamp(state.stamps, layout, Offset(pos.x / density, pos.y / density), gridTop) ?: return@detectTapGestures
                            val s = state.stamps[hit]
                            boing = s.sectionId
                            tag = StampTag(s.sectionId, stampTagText(resources, s), System.nanoTime())
                            onStampTap(hit)
                        }
                    },
            ) {
                if (dateOnPage) DateTag(state.dateLabel, Modifier.padding(start = 5.dp).offset(y = (-1).dp), onPage = true)
                StampGrid(state.stamps, layout, presentation, clock, anchors, boing, top = gridTop)
            }
            // ---- The spine ----
            Spine(Modifier.width(BookLayout.SPINE.dp).fillMaxHeight())
            // ---- The sticker page (right) ----
            LaunchedEffect(paging.userPage, paging.touchedAt) {
                if (paging.userPage == null) return@LaunchedEffect
                withContext(FixedMotionScale) { holdOnFrameClock(PAGE_IDLE_MS) }
                paging.userPage = null
            }
            val pageCount = layout.pages
            // The swipe handler outlives a composition: it reads the page shown now, not the one it was made with.
            val pageNow by rememberUpdatedState(page)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(BookTags.RIGHT)
                    .probe(LocalPieceProbe.current, "bookRight")
                    .bookPageLift(fx, clock)
                    .graphicsLayer {
                        // The page turns about the spine: the engine's (a twin on another page, a new book) or New trip's.
                        val engineTurn = fx.pageTurn
                        val rotY = when {
                            engineTurn != null -> BookMotion.pageTurn(clock.now - engineTurn.t0).first
                            freshTurnElapsed != null -> BookMotion.pageTurn(freshTurnElapsed).first
                            else -> 0f
                        }
                        rotationY = rotY
                        cameraDistance = 12f * density
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    }
                    .pointerInput(pageCount) {
                        var dx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dx = 0f },
                            onDragEnd = {
                                val cur = paging.userPage ?: pageNow
                                val to = when {
                                    dx < -SWIPE_PX * density -> cur + 1
                                    dx > SWIPE_PX * density -> cur - 1
                                    else -> cur
                                }.coerceIn(0, pageCount - 1)
                                if (to != cur || paging.userPage != null) {
                                    paging.userPage = to
                                    paging.touchedAt++
                                }
                            },
                        ) { _, amount -> dx += amount }
                    }
                    .pointerInput(Unit) { detectTapGestures { onOpen() } },
            ) {
                ReleasePaper(state, fx, clock)
                Spots(state, presentation, clock, layout, page, anchors, fresh = freshTurnElapsed != null)
                // The fore-edge: plain periwinkle mid until the book is full or PERFECT, then holo (§2.6, §2.11).
                val holo = BookRules.foreEdgeHolo(state, fx)
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(5.dp)
                        .fillMaxHeight()
                        .drawBehind { if (holo) drawRect(holoBrush(center)) else drawRect(c.book.foreEdge) },
                )
                val t = tag
                if (t != null) {
                    NameTag(t.text, Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 6.dp))
                    LaunchedEffect(t) {
                        val hold = timeouts.recommended(readHold(words(t.text), PicnicMotion.floorCard), controls = false)
                        withContext(FixedMotionScale) {
                            holdOnFrameClock(BOING_MS)
                            boing = null
                            holdOnFrameClock(hold)
                        }
                        if (tag == t) tag = null
                    }
                }
            }
        }
        // PERFECT (§7.3): the 40 dp PER-FECT! hanko pressed across the spine, its right edge 24 dp past the stamp page's,
        // 6 dp from the top; drawn over the spine and the sticker page.
        if (perfect) SpinePerfect(fx, clock, Modifier.offset(x = (layout.leftWidth + 24f - 40f).dp, y = 6.dp))
    }
}

private class PagingState {
    var userPage by mutableStateOf<Int?>(null)
    var touchedAt by mutableIntStateOf(0)
}

private data class StampTag(val sectionId: String, val text: String, val at: Long)

/** "4 · Baking · 1 left" / "3 · Deli & meat · stamped" / "Everything else · 2 left" (§7.7, the display name). */
internal fun stampTagText(resources: android.content.res.Resources, s: StampUi): String {
    val name = if (s.position > 0) resources.getString(R.string.list_stamp_tag_name, s.position, s.displayName) else s.displayName
    return if (s.stamped) resources.getString(R.string.list_stamp_tag_stamped, name) else resources.getString(R.string.list_stamp_tag_left, name, s.toGet)
}

/** The stamp whose centre is nearest [p] (dp, in the left page whose grid starts at [top]). */
internal fun nearestStamp(stamps: List<StampUi>, layout: BookLayout, p: Offset, top: Float): Int? {
    if (stamps.isEmpty()) return null
    var best: Int? = null
    var bestD = Float.MAX_VALUE
    for (i in 0 until minOf(stamps.size, layout.circles)) {
        val (x, y) = stampCentre(i, layout, top)
        val d = hypot(x - p.x, y - p.y)
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return best
}

/** The centre (dp) of stamp [i] on the left page. */
internal fun stampCentre(i: Int, layout: BookLayout, top: Float): Pair<Float, Float> {
    val col = i % layout.stampCols
    val row = i / layout.stampCols
    val x = 6f + col * (layout.stampSize + BookLayout.STAMP_GAP) + layout.stampSize / 2f
    val y = top + row * (layout.stampSize + BookLayout.STAMP_GAP) + layout.stampSize / 2f
    return x to y
}

/** Where the stamp grid starts (dp): under the date tag, or near the page top when the date is a caption (§7.4). */
internal fun stampTop(dateOnPage: Boolean): Float = if (dateOnPage) DATE_ROOM else STAMP_TOP_NO_DATE

/** Where the stamp grid starts under the 18 dp date tag (dp): two rows of 24 dp stamps then end at the page bottom. */
private const val DATE_ROOM: Float = 19f
private const val STAMP_TOP_NO_DATE: Float = 8f
private const val PAGE_IDLE_MS: Long = 6000
private const val SWIPE_PX: Float = 40f
private const val BOING_MS: Long = 166

@Composable
private fun StampGrid(
    stamps: List<StampUi>,
    layout: BookLayout,
    presentation: BookPresentation,
    clock: MotionClock,
    anchors: AnchorRegistry?,
    boing: String?,
    top: Float,
) {
    val size = layout.stampSize.dp
    val shown = stamps.take(layout.circles - if (layout.moreStops > 0) 1 else 0)
    shown.forEachIndexed { i, s ->
        val (x, y) = stampCentre(i, layout, top)
        val anchor = if (anchors != null) with(anchors) { Modifier.anchor(AnchorRegistry.stampKey(s.sectionId)) } else Modifier
        StampCircle(
            look = s.look,
            number = s.position.takeIf { it > 0 },
            stamped = s.stamped,
            now = s.now,
            phase = presentation.stamps[s.sectionId],
            clock = clock,
            size = size,
            modifier = Modifier
                .offset((x - layout.stampSize / 2f).dp, (y - layout.stampSize / 2f).dp)
                .then(anchor)
                .testTag(BookTags.stamp(s.sectionId))
                .graphicsLayer {
                    // The 2-frame boing when she taps it (§7.7).
                    if (boing == s.sectionId) {
                        scaleX = 1.22f
                        scaleY = 1.22f
                    }
                },
        )
    }
    if (layout.moreStops > 0) {
        val (x, y) = stampCentre(layout.circles - 1, layout, top)
        MoreCircle(layout.moreStops, size, Modifier.offset((x - layout.stampSize / 2f).dp, (y - layout.stampSize / 2f).dp))
    }
}

/** The "+n" circle above 24 stops. */
@Composable
private fun MoreCircle(n: Int, size: Dp, modifier: Modifier) {
    val c = PicnicTheme.colors
    Box(modifier.size(size).decorative(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawDashedCircle(c.ballpointSoft, center, this.size.minDimension / 2f - 1.dp.toPx(), 2.dp.toPx(), 3.4.dp.toPx(), 2.6.dp.toPx())
        }
        Text("+$n", style = PicnicTheme.type.stampNumeral.copy(color = c.ballpointSoft, fontSize = 10.sp, lineHeight = 11.sp), maxLines = 1)
    }
}

/**
 * The date tag (§7.3): lemon pastel, 18 dp, radius 5 9 9 5, 2 dp white border, hs, −2°, a 12 dp sakura heart. [onPage]:
 * the tag is part of the page's art, which keeps its size (§7.4), so it stays 18 dp and clear of the stamp grid up to
 * font scale 1.3; above it the date leaves the page as a caption that grows with the text. The book's TalkBack line
 * carries the date either way (the tag itself is decorative).
 */
@Composable
fun DateTag(label: String, modifier: Modifier = Modifier, onPage: Boolean = false) {
    val c = PicnicTheme.colors
    val k = if (onPage) 1f / LocalDensity.current.fontScale.coerceAtLeast(1f) else 1f
    val style = PicnicTheme.type.label.copy(color = c.ballpoint, fontSize = (13f * k).sp, lineHeight = (14f * k).sp)
    val shape = RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp, topEnd = 9.dp, bottomEnd = 9.dp)
    Row(
        modifier
            .rotate(-2f)
            .decorative()
            .heightIn(min = 18.dp)
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(Color.White)
            .padding(2.dp)
            .clip(shape)
            .background(Families.lemon.pastel)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        DecorSticker("heart", size = 12.dp, shadow = false)
        Text(label, style = style, maxLines = 1)
    }
}

/** The spine (§7.3): 12 dp of periwinkle cloth tape and three binder rings 22 × 8 dp at 9, 31 and 53 dp. */
@Composable
private fun Spine(modifier: Modifier) {
    val book = PicnicTheme.colors.book
    Canvas(modifier.decorative()) {
        val stripes = book.spineStripes
        val w = size.width / stripes.size
        stripes.forEachIndexed { i, col -> drawRect(col, Offset(i * w, 0f), Size(w + 0.5f, size.height)) }
        for (y in RINGS) {
            val top = y.dp.toPx()
            if (top + 8.dp.toPx() > size.height) continue
            val left = size.width / 2f - 11.dp.toPx()
            drawRoundRect(Color.White, Offset(left - 2.dp.toPx(), top - 2.dp.toPx()), Size(26.dp.toPx(), 12.dp.toPx()), CornerRadius(6.dp.toPx()))
            drawRoundRect(book.ringRim, Offset(left, top), Size(22.dp.toPx(), 8.dp.toPx()), CornerRadius(4.dp.toPx()))
            drawRoundRect(book.ringFace, Offset(left + 2.dp.toPx(), top + 2.dp.toPx()), Size(18.dp.toPx(), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
        }
    }
}

private val RINGS = listOf(9f, 31f, 53f)

/**
 * The release paper (§2.6): pink (Glow violet) with white dots (r 1.1 dp every 10 dp) and one 8 dp sheen band at 115°;
 * holo once the book is full or PERFECT; the full book's one glint.
 */
@Composable
private fun ReleasePaper(state: BookUi, fx: BookFx, clock: MotionClock) {
    val book = PicnicTheme.colors.book
    val holo = BookRules.foreEdgeHolo(state, fx)
    Canvas(Modifier.fillMaxSize().decorative()) {
        if (holo) drawRect(holoBrush(center)) else drawRect(book.releasePaper)
        val step = 10.dp.toPx()
        val r = 1.1.dp.toPx()
        var y = step / 2f
        while (y < size.height) {
            var x = step / 2f
            while (x < size.width) {
                drawCircle(book.paperDots, r, Offset(x, y))
                x += step
            }
            y += step
        }
        // One white sheen band at 115°.
        val band = 8.dp.toPx()
        val cx = size.width * 0.62f
        rotate(25f, Offset(cx, size.height / 2f)) {
            drawRect(Color.White.copy(alpha = 0.35f), Offset(cx - band / 2f, -size.height), Size(band, size.height * 3f))
        }
        drawBookGlint(fx, clock.now)
    }
}

/** The spots of [page] and the twins in them (§7.3, §7.6). */
@Composable
private fun Spots(state: BookUi, presentation: BookPresentation, clock: MotionClock, layout: BookLayout, page: Int, anchors: AnchorRegistry?, fresh: Boolean) {
    val book = PicnicTheme.colors.book
    val fx = presentation.fx
    val bySlot = remember(state.stickers) { state.stickers.associateBy { it.slot } }
    // The engine's turn to a fresh book (§0.4 Q9): until the turn shows the new page, the old twins stay (they leave with it).
    val turningOld = fx.pageTurn != null && !fx.showsNewPage(clock.now)
    val previous = remember { arrayOfNulls<Map<Int, BookStickerUi>>(1) }
    val shown = if (turningOld && previous[0] != null) previous[0]!! else bySlot
    if (!turningOld) previous[0] = bySlot
    val next = BookRules.nextSpot(state, fx)
    val perPage = layout.perPage
    val first = page * perPage
    val last = minOf(state.total, first + perPage)
    val pageHeight = (layout.height - BookLayout.BORDERS)
    val gridTop = ((pageHeight - layout.rows * BookLayout.SPOT_H) / 2f).coerceAtLeast(1f)
    for (slot in first until last) {
        val i = slot - first
        val col = i % layout.cols
        val row = i / layout.cols
        val x = 7f + col * BookLayout.SPOT_W
        val y = gridTop + row * BookLayout.SPOT_H
        Box(
            Modifier
                .offset(x.dp, y.dp)
                .size(BookLayout.SPOT_W.dp, BookLayout.SPOT_H.dp)
                .testTag(BookTags.spot(slot)),
            contentAlignment = Alignment.Center,
        ) {
            val anchor = if (anchors != null) with(anchors) { Modifier.anchor(AnchorRegistry.spotKey(slot)) } else Modifier
            val isNext = slot == next && !fresh
            Canvas(Modifier.size(BookLayout.SPOT.dp).then(anchor).decorative()) {
                val r = size.minDimension / 2f - 0.75.dp.toPx()
                if (isNext) {
                    drawCircle(holoBrush(center), r + 0.75.dp.toPx())
                    drawCircle(Color.White, r + 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                    drawCircle(book.spotDash, r - 1.dp.toPx(), style = Stroke(1.2.dp.toPx()))
                } else {
                    drawCircle(book.spotFill, r)
                    drawDashedCircle(book.spotDash, center, r, 1.5.dp.toPx(), 2.6.dp.toPx(), 2.dp.toPx())
                }
            }
            val phase = presentation.spots[slot]
            val sticker = if (fresh) null else shown[slot]
            when {
                phase is SpotPhase.Peeling && phase.icon != null ->
                    BookTwin(phase.icon, slot, phase, clock, Modifier.testTag(BookTags.twin(slot)), holo = phase.holo, pixel = state.pixel)
                sticker != null && phase != SpotPhase.Empty ->
                    BookTwin(sticker.icon, slot, phase, clock, Modifier.testTag(BookTags.twin(slot)), holo = sticker.holo, pixel = state.pixel, aisleIndex = sticker.aisleIndex)
            }
        }
    }
}

/** The page number LCD (§7.3): a small LCD face, "2/3" at pitch 1.2 dp. */
@Composable
private fun PageLcd(text: String, modifier: Modifier) {
    val c = PicnicTheme.colors
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier
            .probe(LocalPieceProbe.current, "pageLcd:$text")
            .testTag(BookTags.PAGE_LCD)
            .decorative()
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(Color.White)
            .padding(2.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(c.lcd)
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        LcdText(text, 1.2.dp)
    }
}

/** The stamp name tag (§7.7): a small lemon tag clipped onto the right page by the spine, over the release paper. */
@Composable
private fun NameTag(text: String, modifier: Modifier) {
    val c = PicnicTheme.colors
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .testTag(BookTags.NAME_TAG)
            .probe(LocalPieceProbe.current, "nameTag:$text")
            .rotate(-2f)
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(Color.White)
            .padding(2.dp)
            .clip(shape)
            .background(Families.lemon.pastel)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = PicnicTheme.type.label.copy(color = c.ballpoint, fontSize = 13.sp, lineHeight = 15.sp), maxLines = 2)
    }
}

/** The saved receipt's slip (§7.3): 26 × 32 dp cream paper with three ink lines, rotated −7°, behind the page. */
@Composable
private fun ReceiptSlip(modifier: Modifier) {
    val c = PicnicTheme.colors
    val shadows = PicnicTheme.shadows
    Canvas(modifier.size(26.dp, 32.dp).rotate(-7f).decorative()) {
        drawRect(shadows.color, Offset(shadows.hs.x.toPx(), shadows.hs.y.toPx()), size)
        drawRect(Color.White, size = size)
        val inset = 2.dp.toPx()
        drawRect(c.receiptPaper, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2))
        val line = 1.2.dp.toPx()
        for (i in 0 until 3) {
            val y = 8.dp.toPx() + i * 6.dp.toPx()
            drawRect(c.ballpoint, Offset(5.dp.toPx(), y), Size(size.width - (if (i == 2) 12 else 10).dp.toPx(), line))
        }
    }
}

/**
 * The collapsed book (§7.4): one 40 dp strip with the date tag, the stamps as 12 dp dots (stamped dots filled in the
 * aisle trace), and the newest twins at 22 dp, right-aligned, with a "+n" pearl before them.
 */
@Composable
private fun CollapsedBook(state: BookUi, presentation: BookPresentation, clock: MotionClock, modifier: Modifier, dateOnPage: Boolean) {
    val c = PicnicTheme.colors
    val shapes = PicnicTheme.shapes
    val shape = shapes.bookOuter
    val variant = PicnicTheme.variant
    val newestFirst = remember(state.stickers) { state.stickers.sortedByDescending { it.slot } }
    androidx.compose.ui.layout.SubcomposeLayout(
        modifier
            .fillMaxWidth()
            .height(BookLayout.COLLAPSED.dp)
            .hardShadow(shape, PicnicTheme.shadows, large = true)
            .clip(shape)
            .background(c.stickerWhite)
            .padding(3.dp)
            .clip(shapes.bookPage)
            .background(c.book.page)
            .padding(horizontal = 6.dp),
    ) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val h = constraints.maxHeight
        val w = constraints.maxWidth
        val gap = 6.dp.roundToPx()
        val date = if (dateOnPage) subcompose("date") { DateTag(state.dateLabel) }.map { it.measure(loose) } else emptyList()
        val dots = subcompose("dots") {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (s in state.stamps.take(12)) {
                    Canvas(Modifier.size(12.dp).decorative()) {
                        val r = size.minDimension / 2f - 0.75.dp.toPx()
                        if (s.stamped) {
                            drawCircle(s.look.stampInk(variant), r)
                        } else {
                            drawDashedCircle(if (s.now) Families.strawberry.trace else c.inkSoft, center, r, 1.5.dp.toPx(), 2.4.dp.toPx(), 1.8.dp.toPx())
                        }
                    }
                }
            }
        }.map { it.measure(loose) }
        val dateW = date.sumOf { it.width }
        val dotsX = dateW + if (dateW > 0) gap else 0
        val dotsW = dots.sumOf { it.width }
        // The newest stickers, right-aligned: as many 22 dp twins (overlapping 3 dp) as fit after the dots and the
        // "+n" pearl, at most six (§7.4).
        val twinStep = (22 - 3).dp.roundToPx()
        val pearlRoom = 34.dp.roundToPx()
        val room = w - dotsX - dotsW - gap - pearlRoom
        val fit = ((room - 3.dp.roundToPx()) / twinStep).coerceIn(0, COLLAPSED_TWINS).coerceAtMost(newestFirst.size)
        val shown = newestFirst.take(fit).asReversed()
        val more = state.stickers.size - shown.size
        val twins = subcompose("twins") {
            Row(horizontalArrangement = Arrangement.spacedBy((-3).dp)) {
                for (t in shown) {
                    BookTwin(t.icon, t.slot, presentation.spots[t.slot], clock, Modifier.testTag(BookTags.twin(t.slot)).probe(LocalPieceProbe.current, "collapsedTwin:${t.slot}"), size = 22.dp, holo = t.holo, pixel = state.pixel, aisleIndex = t.aisleIndex)
                }
            }
        }.map { it.measure(loose) }
        val pearl = if (more > 0) subcompose("pearl") { MorePearl(more) }.map { it.measure(loose) } else emptyList()
        layout(w, h) {
            date.forEach { it.place(0, (h - it.height) / 2) }
            dots.forEach { it.place(dotsX, (h - it.height) / 2) }
            val twinsW = twins.sumOf { it.width }
            var x = w - twinsW
            twins.forEach { it.place(x, (h - it.height) / 2) }
            pearl.forEach {
                x -= it.width + 4.dp.roundToPx()
                it.place(x, (h - it.height) / 2)
            }
        }
    }
}

/** The collapsed book's "+n" (§4.12): a white pill with a periwinkle inner ring, DynaPuff 13 sp. Decorative. */
@Composable
private fun MorePearl(n: Int) {
    Box(
        Modifier
            .height(20.dp)
            .widthIn(min = 30.dp)
            .clip(CircleShape)
            .background(Color.White)
            .drawBehind {
                val sw = 1.5.dp.toPx()
                val inset = 1.5.dp.toPx() + sw / 2f
                drawRoundRect(
                    Families.periwinkle.mid,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius((size.height - inset * 2) / 2f),
                    style = Stroke(sw),
                )
            }
            .padding(horizontal = 6.dp)
            .decorative(),
        contentAlignment = Alignment.Center,
    ) {
        Text("+$n", style = PicnicTheme.type.counter.copy(color = PicnicTheme.colors.ballpoint, fontSize = 13.sp, lineHeight = 14.sp), maxLines = 1)
    }
}

private const val COLLAPSED_TWINS: Int = 6

/** The date as a caption row above the book (font scale over 1.3, §7.4): Zen Maru 700 13 sp, scaled. */
@Composable
fun BookDateCaption(label: String, modifier: Modifier = Modifier) {
    PadPatch(modifier.decorative()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DecorSticker("heart", size = 12.dp, shadow = false)
            Text(label, style = PicnicTheme.type.label.copy(color = PicnicTheme.colors.onPad, fontSize = 13.sp))
        }
    }
}
private const val PAGE_TURN_MS: Long = 240

/** How far the page number hangs below the book: the top stack's 8 dp gap above the list (§7.3). */
private val PAGE_LCD_DROP = 8.dp
