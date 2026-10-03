package earth.cora.picnic.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.ui.theme.gen.DecorManifest
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
 * the 40 dp [collapsed] strip (IME open, a short window, scrolled down with the app bar, §7.4). Every height change
 * animates (`standard`, 200 ms; at once under reduced motion) while the two faces cross-fade, so the list under it never
 * jumps (§6.1, item 161). [presentation] plays the chains on [clock]; [anchors] records the spots and stamps. Tap the
 * right page → [onOpen] (the Trip book sheet); swipe it to turn pages (it returns to the shown page after 6 s idle); tap
 * the left page → the nearest stamp boings, its name tag is clipped onto the right page for its reading hold, and
 * [onStampTap] feeds egg 10. TalkBack: one node with the summary and the action "Open trip book"; the twins have no
 * nodes. [dateOnPage] false: the date is a caption above the book (font scale over 1.3), and the name tag goes there too
 * through [onNameTag] (its text while it holds, then null), where it can grow with the text. [turnKey] changes: the page
 * turns to a fresh book (New trip).
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
    onNameTag: ((String?) -> Unit)? = null,
) {
    val c = PicnicTheme.colors
    val shapes = PicnicTheme.shapes
    val reduced = LocalReducedMotion.current
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
        // The book's height follows its state on the frame clock, never in one frame (§6.1, §7.4, item 161).
        val target = if (collapsed) BookLayout.COLLAPSED else layout.height
        val height = remember { Animatable(target) }
        LaunchedEffect(target, reduced) {
            if (reduced) {
                height.snapTo(target)
            } else {
                withContext(FixedMotionScale) {
                    height.animateTo(target, tween(PicnicMotion.standard.durationMillis, easing = PicnicMotion.standard.easing))
                }
            }
        }
        val h = height.value
        // 0 = the expanded pages, 1 = the collapsed strip; the two faces cross-fade while the height moves.
        val folded = ((layout.height - h) / (layout.height - BookLayout.COLLAPSED)).coerceIn(0f, 1f)
        val showPages = folded < 1f || !collapsed
        // The saved receipt pokes out of the fore-edge behind the page (§7.3, egg 18).
        if (state.receiptSaved && showPages) ReceiptSlip(Modifier.align(Alignment.CenterEnd).offset(x = 9.dp, y = 6.dp).graphicsLayer { alpha = 1f - folded })
        val shape = shapes.bookOuter
        // The page shown (§7.4): the engine's, else the one she swiped to (for 6 s), else the page of the next free spot.
        val paging = remember { PagingState() }
        val page = (presentation.fx.showPage ?: paging.userPage ?: layout.shownPage(state.takenSlots)).coerceIn(0, layout.pages - 1)
        Box(
            Modifier
                .fillMaxWidth()
                .height(h.dp)
                .hardShadow(shape, PicnicTheme.shadows, large = true)
                .clip(shape)
                .background(c.stickerWhite)
                .padding(3.dp)
                .clip(shapes.bookPage)
                .background(c.book.page),
        ) {
            if (showPages) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .height((layout.height - BookLayout.BORDERS).dp)
                        .graphicsLayer { alpha = 1f - folded },
                ) {
                    BookPages(state, presentation, clock, layout, anchors, dateOnPage, freshTurn, onStampTap, onOpen, paging, page, onNameTag)
                }
            }
            if (folded > 0f) {
                CollapsedPages(
                    state, presentation, clock, dateOnPage,
                    Modifier
                        .testTag(BookTags.COLLAPSED)
                        .fillMaxWidth()
                        .height((BookLayout.COLLAPSED - BookLayout.BORDERS).dp)
                        .graphicsLayer { alpha = folded },
                )
            }
        }
        // The page number (§7.3) clips onto the page's bottom edge at the right: inside the page it would sit on the
        // last spot of a full page. It hangs into the 8 dp under the book and ends where the list's viewport begins.
        if (layout.pages > 1 && showPages) {
            PageLcd("${page + 1}/${layout.pages}", Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = PAGE_LCD_DROP).graphicsLayer { alpha = 1f - folded })
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
    onNameTag: ((String?) -> Unit)?,
) {
    val c = PicnicTheme.colors
    val fx = presentation.fx
    val timeouts = LocalTimeouts.current
    val perfect = BookRules.perfect(state, fx)
    var tag by remember { mutableStateOf<StampTag?>(null) }
    var boing by remember { mutableStateOf<String?>(null) }
    val resources = LocalResources.current
    val gridTop = stampTop(dateOnPage, layout)
    // Off the page (large text), the tag shows above the book: it hears the text while the tag holds, then null.
    val offPage by rememberUpdatedState(onNameTag)
    LaunchedEffect(tag) { offPage?.invoke(tag?.text) }
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
                if (dateOnPage) DateTag(state.dateLabel, Modifier.padding(start = 5.dp).offset(y = DATE_TAG_Y.dp).probe(LocalPieceProbe.current, "dateTag"), onPage = true, season = state.season)
                // On the holo stamp page (PERFECT) the stamps keep their Day inks in every variant: the Glow ones fade
                // into the light foil (§2.4, §7.3; the lid stamps make the same choice, §5.3).
                HoloInks(perfect) { StampGrid(state.stamps, layout, presentation, clock, anchors, boing, top = gridTop) }
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
                    // At the page's art scale (it never leaves the book): up to three lines across the sticker page.
                    if (onNameTag == null) NameTag(t.text, Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 6.dp, end = 9.dp), onPage = true)
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
        if (perfect) HoloInks(true) { SpinePerfect(fx, clock, Modifier.offset(x = (layout.leftWidth + 24f - 40f).dp, y = 6.dp)) }
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

/**
 * Where the stamp grid starts (dp, in the left page of [layout]): below everything the date tag draws (its −2° turn and
 * its hard shadow included, [DATE_TAG_REACH]), centred in the height left under it; centred in the whole page when the
 * date is a caption above the book (font scale over 1.3, §7.4). Two rows of 24 dp stamps fill the room under the tag
 * exactly.
 */
fun stampTop(dateOnPage: Boolean, layout: BookLayout): Float {
    val page = layout.height - BookLayout.BORDERS
    val grid = layout.stampRows * layout.stampSize + (layout.stampRows - 1) * BookLayout.STAMP_GAP
    val above = if (dateOnPage) DATE_TAG_REACH else 0f
    return above + ((page - above - grid) / 2f).coerceAtLeast(0f)
}

/** The on-page date tag's top (dp): tucked 1 dp under the page's top edge. */
private const val DATE_TAG_Y: Float = -1f

/** The on-page date tag's height (dp; the mockup's tag, which leaves the stamps their room). */
const val DATE_TAG_ON_PAGE: Float = 16f

/** How far down the on-page date tag draws (dp): its top, its height, the −2° turn at its left end and its hs shadow. */
const val DATE_TAG_REACH: Float = 19f
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
 * The date tag (§7.3): lemon pastel, radius 5 9 9 5, 2 dp white border, hs, −2°, a 12 dp sakura heart, the date and, when
 * a seasonal sheet is active ([season], egg 11; only with the eggs on), its 12 dp pictogram. [onPage]: the tag is part
 * of the page's art, which keeps its size (§7.4): 16 dp tall at every font scale up to 1.3, so it and its shadow stay
 * clear of the first row of stamps ([DATE_TAG_REACH]); above 1.3 the date leaves the page as a caption that grows with
 * the text. Elsewhere (the collapsed strip) it is at least 18 dp. The book's TalkBack line carries the date either way
 * (the tag itself is decorative).
 */
@Composable
fun DateTag(label: String, modifier: Modifier = Modifier, onPage: Boolean = false, season: Season? = null) {
    val c = PicnicTheme.colors
    val density = LocalDensity.current
    val k = if (onPage) 1f / density.fontScale.coerceAtLeast(1f) else 1f
    val style = PicnicTheme.type.label.copy(color = c.ballpoint, fontSize = (13f * k).sp, lineHeight = (14f * k).sp)
    // The lemon face holds the letters' own ink (cap tops to descenders, 0.92 em): 12 dp on the page, at least 14 dp
    // elsewhere, growing with the text off the page.
    val face = if (onPage) (DATE_TAG_ON_PAGE - 4f).dp else maxOf(14.dp, with(density) { (style.fontSize * GLYPH_SPAN_EM).toDp() } + 2.dp)
    val shape = RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp, topEnd = 9.dp, bottomEnd = 9.dp)
    Row(
        modifier
            .rotate(-2f)
            .decorative()
            .height(face + 4.dp)
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(c.stickerWhite)
            .padding(2.dp)
            .clip(shape)
            .background(Families.lemon.pastel)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        DecorSticker("heart", size = 12.dp, shadow = false)
        Text(label, style = style, maxLines = 1, modifier = Modifier.glyphsCentred(face, style.fontSize))
        if (season != null) SeasonMark(season, 12.dp)
    }
}

/**
 * Zen Maru Gothic's letters run from the cap height (0.718 em) to the descenders (−0.199 em): 0.917 em of ink in a
 * 1.448 em line. A tag face shorter than the line centres that ink, not the line box, so nothing is cut ([glyphsCentred]).
 */
private const val GLYPH_SPAN_EM: Float = 0.917f
private const val GLYPH_CENTRE_EM: Float = (0.718f - 0.199f) / 2f

/** Lays the text out [height] tall with its letters' ink centred in it (its first baseline placed by [GLYPH_CENTRE_EM]). */
private fun Modifier.glyphsCentred(height: Dp, fontSize: androidx.compose.ui.unit.TextUnit): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
    val h = height.roundToPx()
    val baseline = p[androidx.compose.ui.layout.FirstBaseline]
    val target = h / 2f + GLYPH_CENTRE_EM * fontSize.toPx()
    layout(p.width, h) { p.place(0, kotlin.math.round(target - baseline).toInt()) }
}

/** The pictogram of a seasonal sheet (egg 11), in its season's ink: maple, pumpkin, ghost, snowflake, heart, envelope. */
@Composable
fun SeasonMark(season: Season, size: Dp, modifier: Modifier = Modifier) {
    val art = rememberVectorArt(DecorManifest.pictograms.getValue(seasonPictogram(season).key))
    val ink = SolidColor(seasonInk(season))
    Canvas(modifier.size(size).decorative().probe(LocalPieceProbe.current, "season:${season.name}")) {
        drawArt(art, this.size, paint = { path, _ -> if (path.name == "ink") ink else null })
    }
}

/** The pictogram a season's sheet wears (§4.13's seasonal set). */
fun seasonPictogram(season: Season): Pictogram = when (season) {
    Season.CANADA_DAY -> Pictogram.Maple
    Season.THANKSGIVING -> Pictogram.Pumpkin
    Season.HALLOWEEN -> Pictogram.Ghost
    Season.FIRST_SNOW -> Pictogram.Snowflake
    Season.VALENTINES -> Pictogram.Heart
    Season.LUNAR_NEW_YEAR -> Pictogram.Envelope
}

/** Its ink: a trace that holds on the lemon tag and on the pad patch (≥3:1 for a graphic). */
private fun seasonInk(season: Season): Color = when (season) {
    Season.CANADA_DAY -> Families.strawberry.trace
    Season.THANKSGIVING -> Families.peach.trace
    Season.HALLOWEEN -> Families.grape.trace
    Season.FIRST_SNOW -> Families.ramune.trace
    Season.VALENTINES -> Families.sakura.trace
    Season.LUNAR_NEW_YEAR -> Families.strawberry.trace
}

/**
 * The holo stamp page's inks (PERFECT, §7.3): on the light foil the stamps, their rings and numerals and the PER-FECT!
 * hanko keep their Day colours in every variant, as the lid stamps do (§5.3). [holo] false: the page's own theme.
 */
@Composable
internal fun HoloInks(holo: Boolean, content: @Composable () -> Unit) {
    if (holo && PicnicTheme.colors.isDark) PicnicTheme(variant = ThemeVariant.Day, content = content) else content()
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

/**
 * The page number LCD (§7.3): a small LCD face, "2/3" at pitch 1.2 dp. Above font scale 1.3 its pitch follows the font
 * scale, up to 1.6× (§7.4), so the number grows with the text round it while the pages keep their size.
 */
@Composable
private fun PageLcd(text: String, modifier: Modifier) {
    val c = PicnicTheme.colors
    val shape = RoundedCornerShape(5.dp)
    val pitch = PAGE_LCD_PITCH * pageLcdScale(LocalDensity.current.fontScale)
    Box(
        modifier
            .probe(LocalPieceProbe.current, "pageLcd:$text")
            .testTag(BookTags.PAGE_LCD)
            .decorative()
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(c.stickerWhite)
            .padding(2.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(c.lcd)
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        LcdText(text, pitch)
    }
}

/** The page LCD's pitch at font scale 1.0 (§7.3). */
private val PAGE_LCD_PITCH = 1.2.dp

/** How much the page LCD's pitch grows at [fontScale] (§7.4): 1 up to 1.3, then the font scale, at most 1.6. */
internal fun pageLcdScale(fontScale: Float): Float = if (fontScale > 1.3f) fontScale.coerceAtMost(1.6f) else 1f

/**
 * The stamp name tag (§7.7): a small lemon tag clipped onto the right page by the spine, over the release paper. [onPage]:
 * at the page's art scale (like the date tag, it keeps its size up to font scale 1.3) and at most three lines; off the
 * page (the caption row above the book at larger text) it grows with the text and takes as many lines as its room
 * holds. It wraps rather than clips, and reports a lost word to the piece probe ("nameTagOverflow").
 */
@Composable
fun NameTag(text: String, modifier: Modifier = Modifier, onPage: Boolean = false) {
    val c = PicnicTheme.colors
    val shape = RoundedCornerShape(4.dp)
    val probe = LocalPieceProbe.current
    val style = nameTagStyle(onPage)
    BoxWithConstraints(
        modifier
            .testTag(BookTags.NAME_TAG)
            .probe(probe, "nameTag:$text")
            .rotate(-2f)
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(c.stickerWhite)
            .padding(NAME_TAG_RIM)
            .clip(shape)
            .background(Families.lemon.pastel)
            .padding(horizontal = NAME_TAG_PAD_X, vertical = NAME_TAG_PAD_Y),
    ) {
        val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
        val room = if (constraints.hasBoundedHeight) (maxHeight / lineHeight).toInt().coerceAtLeast(1) else Int.MAX_VALUE
        val lines = if (onPage) minOf(3, room) else room
        Text(
            text,
            style = style,
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (it.hasVisualOverflow) probe?.report("nameTagOverflow:$text", Rect.Zero, null) },
        )
    }
}

/** The name tag's text: 13 sp on 15 sp lines, at the page's art scale on the page ([NameTag]). */
@Composable
fun nameTagStyle(onPage: Boolean = false): TextStyle {
    val k = if (onPage) 1f / LocalDensity.current.fontScale.coerceAtLeast(1f) else 1f
    return PicnicTheme.type.label.copy(color = PicnicTheme.colors.ballpoint, fontSize = (13f * k).sp, lineHeight = (15f * k).sp)
}

/** The name tag's white rim, and its label's padding across and down: the frame around its text ([NameTag]). */
val NAME_TAG_RIM = 2.dp
val NAME_TAG_PAD_X = 6.dp
val NAME_TAG_PAD_Y = 2.dp

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
 * The collapsed book's page (§7.4), inside the book's frame: one strip with the date tag, the stamps as dots (stamped
 * dots filled in the aisle trace), and the newest twins at 22 dp, right-aligned, with a "+n" pearl before them. The
 * twins are fitted first: room for the newest three (and the pearl when more remain) is kept whatever the stops, so the
 * magic appear always has its newest place to land (item 161). The stamps take what is left: 12 dp dots up to 8 stops,
 * 10 dp above or when 12 dp ones do not fit, and when even those do not fit, a run that always holds the "now" stop with
 * a small "+n" pill for the stops it leaves out ([CollapsedDots]). Any room still over goes to more twins, up to six.
 */
@Composable
private fun CollapsedPages(state: BookUi, presentation: BookPresentation, clock: MotionClock, dateOnPage: Boolean, modifier: Modifier) {
    val newestFirst = remember(state.stickers) { state.stickers.sortedByDescending { it.slot } }
    val stamps = state.stamps
    // The stop the run of dots keeps in view: the current one, else the first not stamped yet, else the last.
    val anchor = stamps.indexOfFirst { it.now }.takeIf { it >= 0 } ?: stamps.indexOfFirst { !it.stamped }.takeIf { it >= 0 } ?: stamps.lastIndex
    val probe = LocalPieceProbe.current
    androidx.compose.ui.layout.SubcomposeLayout(modifier.padding(horizontal = 6.dp)) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val h = constraints.maxHeight
        val w = constraints.maxWidth
        val gap = 6.dp.roundToPx()
        val date = if (dateOnPage) subcompose("date") { DateTag(state.dateLabel, season = state.season) }.map { it.measure(loose) } else emptyList()
        val dateW = date.sumOf { it.width }
        val dotsX = dateW + if (dateW > 0) gap else 0
        // The twins' room first: the newest three (22 dp, overlapping 3 dp) and the pearl before them when more remain.
        val twinStep = (22 - 3).dp.roundToPx()
        val twinEdge = 3.dp.roundToPx()
        fun twinsWidth(n: Int) = if (n <= 0) 0 else n * twinStep + twinEdge
        val total = newestFirst.size
        val least = minOf(BookLayout.COLLAPSED_MIN_TWINS, total)
        val pearlGap = 4.dp.roundToPx()
        val pearlRoom = if (total > least) subcompose("pearlRoom") { MorePearl(total - least) }.sumOf { it.measure(loose).width } + pearlGap else 0
        val pillRoom = subcompose("pillRoom") { MoreDot(stamps.size) }.sumOf { it.measure(loose).width }
        val dotsRoom = w - dotsX - gap - twinsWidth(least) - pearlRoom
        val plan = CollapsedDots.plan(stamps.size, anchor, dotsRoom.toDp().value, pillRoom.toDp().value)
        val dots = subcompose("dots") { CollapsedStamps(stamps, plan, probe) }.map { it.measure(loose) }
        val dotsEnd = dotsX + dots.sumOf { it.width }
        // What is left after the dots goes to more twins, up to six.
        val left = w - dotsEnd - gap
        val fit = (least..minOf(COLLAPSED_TWINS, total)).lastOrNull { n -> twinsWidth(n) + (if (total > n) pearlRoom else 0) <= left } ?: least
        val shown = newestFirst.take(fit).asReversed()
        val more = total - shown.size
        val twins = subcompose("twins") {
            Row(horizontalArrangement = Arrangement.spacedBy((-3).dp)) {
                for (t in shown) {
                    BookTwin(t.icon, t.slot, presentation.spots[t.slot], clock, Modifier.testTag(BookTags.twin(t.slot)).probe(probe, "collapsedTwin:${t.slot}"), size = 22.dp, holo = t.holo, pixel = state.pixel, aisleIndex = t.aisleIndex)
                }
            }
        }.map { it.measure(loose) }
        val pearl = if (more > 0) subcompose("pearl") { MorePearl(more, Modifier.probe(probe, "collapsedPearl")) }.map { it.measure(loose) } else emptyList()
        layout(w, h) {
            date.forEach { it.place(0, (h - it.height) / 2) }
            dots.forEach { it.place(dotsX, (h - it.height) / 2) }
            val twinsW = twins.sumOf { it.width }
            twins.forEach { it.place(w - twinsW, (h - it.height) / 2) }
            // Never back over the dots, whatever the pearl's width.
            pearl.forEach { it.place(maxOf(dotsEnd + gap, w - twinsW - it.width - pearlGap), (h - it.height) / 2) }
        }
    }
}

/** The collapsed strip's stamps per [plan]: a "+n" pill for the stops left out before the run, the dots, one for after. */
@Composable
private fun CollapsedStamps(stamps: List<StampUi>, plan: CollapsedDots, probe: PieceProbe?) {
    val c = PicnicTheme.colors
    val variant = PicnicTheme.variant
    val size = plan.size.dp
    Row(horizontalArrangement = Arrangement.spacedBy(plan.gap.dp), verticalAlignment = Alignment.CenterVertically) {
        if (plan.hiddenBefore > 0) MoreDot(plan.hiddenBefore, Modifier.probe(probe, "collapsedMore:before:${plan.hiddenBefore}"))
        for (s in stamps.subList(plan.first, plan.first + plan.count)) {
            Canvas(Modifier.size(size).decorative().probe(probe, "collapsedDot:${s.sectionId}")) {
                val k = this.size.minDimension / 12.dp.toPx()
                val r = this.size.minDimension / 2f - 0.75.dp.toPx() * k
                if (s.stamped) {
                    drawCircle(s.look.stampInk(variant), r)
                } else {
                    drawDashedCircle(if (s.now) Families.strawberry.trace else c.inkSoft, center, r, 1.5.dp.toPx() * k, 2.4.dp.toPx() * k, 1.8.dp.toPx() * k)
                }
            }
        }
        if (plan.hiddenAfter > 0) MoreDot(plan.hiddenAfter, Modifier.probe(probe, "collapsedMore:after:${plan.hiddenAfter}"))
    }
}

/**
 * The collapsed strip's "+n" for stops its dots leave out: a 12 dp pill in the undone dots' ink, its numeral at the
 * page's art scale (10 sp at font scale 1.0, like the stamp page's "+n" circle; the book's pages keep their size, §7.4).
 * Decorative: the book's TalkBack line counts every stop.
 */
@Composable
private fun MoreDot(n: Int, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val k = 1f / LocalDensity.current.fontScale
    Box(
        modifier
            .height(12.dp)
            .widthIn(min = 18.dp)
            .decorative()
            .drawBehind {
                val sw = 1.2.dp.toPx()
                drawRoundRect(
                    c.inkSoft,
                    topLeft = Offset(sw / 2f, sw / 2f),
                    size = Size(size.width - sw, size.height - sw),
                    cornerRadius = CornerRadius((size.height - sw) / 2f),
                    style = Stroke(sw),
                )
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "+$n",
            style = PicnicTheme.type.stampNumeral.copy(color = c.inkSoft, fontSize = (10f * k).sp, lineHeight = (11f * k).sp),
            maxLines = 1,
            modifier = Modifier.wrapContentHeight(unbounded = true),
        )
    }
}

/** The collapsed book's "+n" (§4.12): a white pill with a periwinkle inner ring, DynaPuff 13 sp. Decorative. */
@Composable
private fun MorePearl(n: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(20.dp)
            .widthIn(min = 30.dp)
            .clip(CircleShape)
            .background(PicnicTheme.colors.stickerWhite)
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

/**
 * The date as a caption row above the book (font scale over 1.3, §7.4): Zen Maru 700 13 sp, scaled, with the heart and
 * the season's pictogram (egg 11) when a seasonal sheet is active.
 */
@Composable
fun BookDateCaption(label: String, modifier: Modifier = Modifier, season: Season? = null) {
    // The heart and the season's mark keep their size beside the text as it grows (12 dp at 1.0).
    val mark = with(LocalDensity.current) { 12.sp.toDp() }
    PadPatch(modifier.decorative()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DecorSticker("heart", size = mark, shadow = false)
            Text(label, style = PicnicTheme.type.label.copy(color = PicnicTheme.colors.onPad, fontSize = 13.sp))
            if (season != null) SeasonMark(season, mark)
        }
    }
}
private const val PAGE_TURN_MS: Long = 240

/** How far the page number hangs below the book: the top stack's 8 dp gap above the list (§7.3). */
private val PAGE_LCD_DROP = 8.dp
