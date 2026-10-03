package earth.cora.picnic.ui.eggs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.core.model.AisleSection
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroupedList
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.SectionKind
import earth.cora.picnic.core.model.TripStats
import earth.cora.picnic.core.model.iconRef
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.BookTwin
import earth.cora.picnic.ui.list.motion.ListPresentation
import earth.cora.picnic.ui.list.motion.SpinePerfect
import earth.cora.picnic.ui.list.motion.SpotPhase
import earth.cora.picnic.ui.list.motion.StampCircle
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.LcdText
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.drawArt
import earth.cora.picnic.ui.theme.drawDashedCircle
import earth.cora.picnic.ui.theme.gen.DecorManifest
import earth.cora.picnic.ui.theme.hardShadow
import earth.cora.picnic.ui.theme.holdOnFrameClock
import earth.cora.picnic.ui.theme.holoBrush
import earth.cora.picnic.ui.theme.rememberVectorArt
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 * The practice list's trip sticker book (docs/DESIGN_STICKER.md §7.3, §7.4): the stamp page for 1–24 stops (two rows of
 * 24 dp stamps up to 8 stops, 20 dp above, a third row above 16, a "+n" circle above 24), the spine, and the sticker page
 * one page at a time with its "2/3" page LCD. A stand-in for WP5's StickerBook on the Surprise lab's practice copy, laid
 * out by the same §7.4 formula; it goes when the lab hosts the real List screen. Owner: WP9.
 */

/** §7.4's layout, in dp (the formula WP5's BookLayout implements; the practice copy's own until it hosts the real book). */
internal data class PracticeBookLayout(
    val stampSize: Float,
    val stampRows: Int,
    val stampCols: Int,
    val leftWidth: Float,
    val cols: Int,
    val rows: Int,
    val circles: Int,
    val moreStops: Int,
    val spots: Int,
) {
    val perPage: Int get() = cols * rows
    val pages: Int get() = max(1, ceil(spots.toDouble() / perPage).toInt())

    /** 76 dp with two stamp rows, 102 dp with three. */
    val height: Float get() = if (stampRows >= 3) 102f else 76f

    fun pageOf(slot: Int): Int = (slot / perPage).coerceIn(0, pages - 1)

    /** The page of the lowest free spot, or the last page when every spot is taken (§7.4 `shownPage`). */
    fun shownPage(taken: Set<Int>, next: Int?): Int {
        val free = next ?: (0 until spots).firstOrNull { it !in taken } ?: return pages - 1
        return pageOf(free)
    }

    /** The centre (dp) of stamp [i] on the left page, the grid starting [top] dp down. */
    fun stampCentre(i: Int, top: Float): Offset {
        val col = i % stampCols
        val row = i / stampCols
        return Offset(6f + col * (stampSize + GAP) + stampSize / 2f, top + row * (stampSize + GAP) + stampSize / 2f)
    }

    /** The stamp whose centre is nearest [p] (dp in the left page). */
    fun nearestStamp(p: Offset, top: Float, count: Int): Int? {
        var best: Int? = null
        var bestD = Float.MAX_VALUE
        for (i in 0 until min(count, circles)) {
            val c = stampCentre(i, top)
            val d = hypot(c.x - p.x, c.y - p.y)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    companion object {
        const val GAP: Float = 3f
        const val SPOT_W: Float = 23f
        const val SPOT_H: Float = 22f
        const val MAX_CIRCLES: Int = 24

        /** §7.4 for a book [width] dp wide with [stops] stops and [spots] spots. */
        fun of(width: Float, stops: Int, spots: Int): PracticeBookLayout {
            val s = stops.coerceAtLeast(1)
            val stampSize = if (s <= 8) 24f else 20f
            val stampRows = if (s <= 16) 2 else 3
            val stampCols = max(4, ceil(min(s, MAX_CIRCLES) / stampRows.toDouble()).toInt())
            val leftWidth = 12f + stampCols * stampSize + (stampCols - 1) * GAP
            val rightWidth = width - 6f - leftWidth - 12f
            val cols = max(4, floor((rightWidth - 12f) / SPOT_W).toInt())
            val more = if (s > MAX_CIRCLES) s - (MAX_CIRCLES - 1) else 0
            return PracticeBookLayout(
                stampSize = stampSize,
                stampRows = stampRows,
                stampCols = stampCols,
                leftWidth = leftWidth,
                cols = cols,
                rows = stampRows + 1,
                circles = if (more > 0) MAX_CIRCLES else s,
                moreStops = more,
                spots = spots,
            )
        }
    }
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
private val TORONTO: ZoneId = ZoneId.of("America/Toronto")

/** Where the stamp grid starts under the date tag (dp), or near the page top when the date is a caption above (§7.4). */
private const val STAMPS_UNDER_DATE: Float = 19f
private const val STAMPS_NO_DATE: Float = 8f

/** Above this font scale the date leaves the page and becomes a caption above the book (§7.4). */
private const val DATE_CAPTION_SCALE: Float = 1.3f

/** A turned page goes back to the shown page after this long idle (§7.4). */
private const val PAGE_IDLE_MS: Long = 6000

/**
 * The practice copy's sticker book (§7.3): its stamp page (taps go to the nearest stamp, egg 10; holo for a perfect week,
 * egg 14), the spine, the sticker page showing `shownPage` (a swipe turns pages; it comes back after 6 s idle) with the
 * page LCD, the date tag (a caption above the book over font scale 1.3), PER-FECT! and the receipt slip.
 */
@Composable
internal fun PracticeBook(
    model: PracticeModel,
    state: AppState,
    grouped: GroupedList,
    eggs: SurpriseState,
    p: ListPresentation,
    modifier: Modifier = Modifier,
) {
    val c = PicnicTheme.colors
    val list = model.list(state)
    val trip = list.trip
    val start = TripStats.lastSessionStart(list)
    val date = DATE.format(Instant.ofEpochMilli(start ?: model.now()).atZone(TORONTO))
    val dateOnPage = LocalDensity.current.fontScale <= DATE_CAPTION_SCALE
    val season = eggs.season.takeIf { eggs.enabled }
    Column(modifier.fillMaxWidth()) {
        if (!dateOnPage) DateTag(date, season, onPage = false, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val layout = PracticeBookLayout.of(maxWidth.value, grouped.stops, list.items.size)
            val perfect = trip?.completedAt != null && trip.finaleShownAt != null && !p.bookFx.perfectHeld
            Box(Modifier.fillMaxWidth().height(layout.height.dp)) {
                val shape = RoundedCornerShape(12.dp)
                val stamped = grouped.sections.count { it.complete }
                val label = stringResource(
                    R.string.egg_practice_book, date, list.items.count { it.checked }, list.items.size, stamped, grouped.stops,
                )
                Row(
                    Modifier
                        .fillMaxSize()
                        .testTag(PracticeTags.BOOK)
                        // One node (§7.7): the stickers, the date tag and the page number are decorative.
                        .semantics(mergeDescendants = true) { contentDescription = label }
                        .hardShadow(shape, PicnicTheme.shadows, large = true)
                        .clip(shape)
                        .background(c.stickerWhite)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.book.page),
                ) {
                    StampPage(model, grouped, state, eggs, p, layout, perfect || eggs.perfectWeek, dateOnPage)
                    Spine(c.book)
                    StickerPage(model, state, eggs, p, layout, Modifier.weight(1f))
                }
                if (dateOnPage) DateTag(date, season, onPage = true, modifier = Modifier.offset(x = 8.dp, y = 0.dp))
                if (perfect || p.bookFx.perfectT0 != null) {
                    SpinePerfect(p.bookFx, model.motion.clock, Modifier.offset(x = (3f + layout.leftWidth + 24f - 40f).dp, y = 6.dp))
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
    }
}

@Composable
private fun StampPage(
    model: PracticeModel,
    grouped: GroupedList,
    state: AppState,
    eggs: SurpriseState,
    p: ListPresentation,
    layout: PracticeBookLayout,
    holo: Boolean,
    dateOnPage: Boolean,
) {
    val c = PicnicTheme.colors
    val sections = grouped.sections
    val engine = model.engine
    // Three rows of 20 dp stamps leave room to sit a little lower, clear of the tilted date tag.
    val top = if (!dateOnPage) STAMPS_NO_DATE else if (layout.stampRows >= 3) STAMPS_UNDER_DATE + 3f else STAMPS_UNDER_DATE
    val shown = sections.take(layout.circles - if (layout.moreStops > 0) 1 else 0)
    val current by rememberUpdatedState(sections.size)
    Box(
        Modifier
            .width(layout.leftWidth.dp)
            .fillMaxHeight()
            .then(if (holo) Modifier.drawBehind { drawRect(holoBrush(center), alpha = 0.55f) } else Modifier)
            .testTag(PracticeTags.STAMP_PAGE)
            // The left page is the target; the nearest centre picks the stamp (§7.7), and the taps feed egg 10.
            .pointerInput(layout, top) {
                detectTapGestures { at ->
                    val hit = layout.nearestStamp(Offset(at.x / density, at.y / density), top, current) ?: return@detectTapGestures
                    engine.onStampTap(hit)
                }
            },
    ) {
        val size = layout.stampSize.dp
        shown.forEachIndexed { i, s ->
            val at = layout.stampCentre(i, top)
            with(model.anchors) {
                Box(
                    Modifier
                        .offset((at.x - layout.stampSize / 2f).dp, (at.y - layout.stampSize / 2f).dp)
                        .size(size)
                        .testTag(PracticeTags.stamp(s.id)),
                    contentAlignment = Alignment.Center,
                ) {
                    StampCircle(
                        look = practiceLook(s, state),
                        number = s.position.takeIf { it > 0 },
                        stamped = s.complete,
                        now = s.id == grouped.nowSectionId,
                        phase = p.stamps[s.id],
                        clock = model.motion.clock,
                        size = size,
                        modifier = Modifier.anchor(AnchorRegistry.stampKey(s.id)),
                    )
                    // "Everything else" has no number: its circle carries the capsule glyph (§4.10).
                    if (s.kind == SectionKind.UNSORTED && !s.complete) {
                        CapsuleGlyph(if (s.id == grouped.nowSectionId) Families.strawberry.trace else c.ballpointSoft, size * 0.58f)
                    }
                }
            }
        }
        if (layout.moreStops > 0) {
            val at = layout.stampCentre(layout.circles - 1, top)
            MoreStops(layout.moreStops, size, Modifier.offset((at.x - layout.stampSize / 2f).dp, (at.y - layout.stampSize / 2f).dp))
        }
    }
}

/** The "+n" circle above 24 stops. */
@Composable
private fun MoreStops(n: Int, size: Dp, modifier: Modifier) {
    val c = PicnicTheme.colors
    Box(modifier.size(size).decorative(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawDashedCircle(c.ballpointSoft, center, this.size.minDimension / 2f - 1.dp.toPx(), 2.dp.toPx(), 3.4.dp.toPx(), 2.6.dp.toPx())
        }
        Text("+$n", style = PicnicTheme.type.stampNumeral.copy(color = c.ballpointSoft, fontSize = 10.sp, lineHeight = 11.sp), maxLines = 1)
    }
}

@Composable
private fun StickerPage(
    model: PracticeModel,
    state: AppState,
    eggs: SurpriseState,
    p: ListPresentation,
    layout: PracticeBookLayout,
    modifier: Modifier,
) {
    val c = PicnicTheme.colors
    val book = c.book
    val clock = model.motion.clock
    val items = model.list(state).items
    val bySlot = items.filter { it.checked && it.bookSlot != null }.associateBy { it.bookSlot!! }
    val next = p.bookFx.holdNext ?: (0 until items.size).firstOrNull { it !in bySlot }
    val shownPage = layout.shownPage(bySlot.keys, next)
    // A swipe turns pages; 6 s idle brings it back to the page the next sticker goes on (§7.4).
    var turned by remember { mutableStateOf<Int?>(null) }
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(turned, touched) {
        if (turned == null) return@LaunchedEffect
        withContext(FixedMotionScale) { holdOnFrameClock(PAGE_IDLE_MS) }
        turned = null
    }
    val page = (turned ?: shownPage).coerceIn(0, layout.pages - 1)
    val pages = layout.pages
    val pageNow by rememberUpdatedState(page)
    Box(
        modifier
            .fillMaxHeight()
            .background(book.releasePaper)
            .pointerInput(pages) {
                var dx = 0f
                detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                    val to = when {
                        dx < -40f * density -> pageNow + 1
                        dx > 40f * density -> pageNow - 1
                        else -> pageNow
                    }.coerceIn(0, pages - 1)
                    if (to != pageNow) {
                        turned = to
                        touched++
                    }
                }) { _, amount -> dx += amount }
            },
    ) {
        val pageHeight = layout.height - 6f
        val gridTop = ((pageHeight - layout.rows * PracticeBookLayout.SPOT_H) / 2f).coerceAtLeast(1f)
        val first = page * layout.perPage
        val last = min(items.size, first + layout.perPage)
        for (slot in first until last) {
            val i = slot - first
            val x = 7f + (i % layout.cols) * PracticeBookLayout.SPOT_W
            val y = gridTop + (i / layout.cols) * PracticeBookLayout.SPOT_H
            Box(
                Modifier.offset(x.dp, y.dp).size(PracticeBookLayout.SPOT_W.dp, PracticeBookLayout.SPOT_H.dp),
                contentAlignment = Alignment.Center,
            ) {
                with(model.anchors) {
                    Canvas(Modifier.size(13.dp).anchor(AnchorRegistry.spotKey(slot)).decorative()) {
                        val r0 = size.minDimension / 2f - 0.75.dp.toPx()
                        if (slot == next) {
                            drawCircle(holoBrush(center), r0 + 0.75.dp.toPx())
                            drawCircle(c.stickerWhite, r0 + 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                            drawCircle(book.spotDash, r0 - 1.dp.toPx(), style = Stroke(1.2.dp.toPx()))
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
        // The fore-edge.
        Box(Modifier.align(Alignment.CenterEnd).width(5.dp).fillMaxHeight().background(book.foreEdge))
        if (pages > 1) PageLcd("${page + 1}/$pages", Modifier.align(Alignment.BottomEnd).padding(end = 9.dp, bottom = 4.dp))
    }
}

/** The page number (§7.3): a small LCD face, "2/3" at pitch 1.2 dp. Decorative (the book's TalkBack line says it). */
@Composable
private fun PageLcd(text: String, modifier: Modifier) {
    val c = PicnicTheme.colors
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier
            .testTag(PracticeTags.PAGE_LCD)
            .decorative()
            .hardShadow(shape, PicnicTheme.shadows)
            .clip(shape)
            .background(c.stickerWhite)
            .padding(2.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(c.lcd)
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        LcdText(text, 1.2.dp)
    }
}

/** The capsule glyph in the "Everything else" stamp's circle, in [ink]. Decorative. */
@Composable
internal fun CapsuleGlyph(ink: Color, size: Dp = 14.dp) {
    val art = rememberVectorArt(DecorManifest.pictograms.getValue("capsule"))
    Canvas(Modifier.size(size).decorative()) {
        drawArt(art, this.size, paint = { path, _ -> if (path.name == "ink") androidx.compose.ui.graphics.SolidColor(ink) else null })
    }
}

@Composable
private fun Spine(book: earth.cora.picnic.ui.theme.BookColors) {
    Canvas(Modifier.width(12.dp).fillMaxHeight().decorative()) {
        val stripes = book.spineStripes
        val w = size.width / stripes.size
        stripes.forEachIndexed { i, col -> drawRect(col, Offset(i * w, 0f), Size(w, size.height)) }
        // Three rings at 9, 31 and 53 dp of the 76 dp book, centred the same way on the taller one.
        val shift = (size.height - 70.dp.toPx()) / 2f
        for (y in listOf(9f, 31f, 53f)) {
            drawRoundRect(book.ringRim, Offset(-5.dp.toPx(), y.dp.toPx() + shift), Size(22.dp.toPx(), 8.dp.toPx()), CornerRadius(4.dp.toPx()))
            drawRoundRect(book.ringFace, Offset(-4.dp.toPx(), (y + 1.5f).dp.toPx() + shift), Size(20.dp.toPx(), 5.dp.toPx()), CornerRadius(3.dp.toPx()))
        }
    }
}

/**
 * The book's date tag (§7.3): a lemon-pastel tag, a sakura heart, the date, and the season's pictogram (egg 11). [onPage]:
 * part of the page's art (the page keeps its size, §7.4), 18 dp with its words at 13 sp of the page; otherwise a caption
 * above the book that grows with the text. Decorative: the book's own TalkBack line carries the date.
 */
@Composable
private fun DateTag(date: String, season: Season?, onPage: Boolean, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val shape = RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp, topEnd = 9.dp, bottomEnd = 9.dp)
    val scale = LocalDensity.current.fontScale
    // On the page the words are art at the page's size (13 sp at 1.0); as a caption they scale with the text.
    val size = if (onPage) (13f / scale.coerceAtLeast(1f)).sp else 13.sp
    Row(
        modifier
            .decorative()
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
                fontSize = size,
                lineHeight = size,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            maxLines = 1,
        )
        val pictogram = season?.let { seasonPictogram(it) }
        if (pictogram != null) {
            val art = rememberVectorArt(DecorManifest.pictograms.getValue(pictogram))
            Canvas(Modifier.size(12.dp).decorative()) { drawArt(art, this.size) }
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

internal fun practiceLook(s: AisleSection, state: AppState): earth.cora.picnic.ui.theme.AisleLook =
    if (s.kind == SectionKind.UNSORTED) {
        earth.cora.picnic.ui.theme.AisleLooks.everythingElse
    } else {
        earth.cora.picnic.ui.theme.AisleLooks.of(s.position, s.aisle?.let { state.store.aisleStyles[it.id] })
    }
