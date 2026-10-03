package earth.cora.picnic.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.TripStats
import earth.cora.picnic.core.store.BookStamp
import earth.cora.picnic.core.store.TripBook
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.album.FiledBookTags
import earth.cora.picnic.ui.album.receiptOf
import earth.cora.picnic.ui.album.spokenReceipt
import earth.cora.picnic.ui.common.ReceiptLines
import earth.cora.picnic.ui.common.ReceiptSticker
import earth.cora.picnic.ui.list.motion.MotionClock
import earth.cora.picnic.ui.list.motion.StampCircle
import earth.cora.picnic.ui.theme.AisleLook
import earth.cora.picnic.ui.theme.AisleLooks
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.HankoStamp
import earth.cora.picnic.ui.theme.HankoText
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LcdText
import earth.cora.picnic.ui.theme.LetterSetSheet
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.Pictogram
import earth.cora.picnic.ui.theme.TabPattern
import earth.cora.picnic.ui.theme.drawDashedCircle
import earth.cora.picnic.ui.theme.hardShadow
import earth.cora.picnic.ui.theme.holoBrush
import java.time.LocalDate

/*
 * The Trip book sheet (docs/DESIGN_STICKER.md §6.6): the trip's book at 2×, read-only. Opened by tapping the book's right
 * page, and by a trip-book row in the album (WP8 reuses it for filed books, item 165). A header with the date and
 * "10 of 24 stickers · 1 of 8 aisles stamped" (and, for a finished trip, the shop time and the PER-FECT! hanko), the
 * stamp page with 32 dp stamps in 48 dp targets and the aisle names under them (each stamp its own TalkBack node), the
 * sticker pages with 40 dp stickers, swipeable page by page (page dots + the LCD "2/3"), and the receipt if it was
 * saved (a tap shows it full size). Owner: WP5.
 */

/** Test tags (the receipt's are the album's, so the album's tests read either sheet). */
object TripBookTags {
    const val SHEET: String = "picnic_trip_book_sheet"
    const val PAGES: String = "picnic_trip_book_pages"
    fun sticker(slot: Int): String = "picnic_trip_book_sticker_$slot"
}

/**
 * The read-only sheet of [book] (null = closed). [dateLabel] is the trip's date ("Sat 3 Oct"); [date] prints on the
 * receipt. Same call shape as the album's former FiledBookSheet, so the album swaps one for the other.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripBookSheet(
    book: TripBook?,
    dateLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    date: LocalDate? = null,
    initialFullReceipt: Boolean = false,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val resources = LocalResources.current
    var fullReceipt by rememberSaveable(book?.id) { mutableStateOf(initialFullReceipt) }
    val appName = stringResource(R.string.app_name)
    val slip = remember(book, date, appName) { book?.let { b -> b.receipt?.let { receiptOf(b, it, date, appName) } } }
    val spoken = slip?.let { spokenReceipt(resources, it) }
    Box(modifier.fillMaxSize()) {
        LetterSetSheet(visible = book != null, onDismissRequest = onClose, modifier = Modifier.testTag(TripBookTags.SHEET)) {
            if (book == null) return@LetterSetSheet
            val stamped = book.stamps.count { it.done }
            val stats = buildList {
                add(pluralStringResource(R.plurals.book_sheet_stickers_of, book.total, book.stickers.size, book.total))
                add(pluralStringResource(R.plurals.book_sheet_aisles_of, book.stamps.size, stamped, book.stamps.size))
                if (book.completedAt != null && book.minutes != null) add(TripStats.formatMinutes(book.minutes))
            }.joinToString(" · ")
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(end = if (book.perfect) 52.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(dateLabel, style = t.cardTitle.copy(color = c.ink), modifier = Modifier.semantics { heading() })
                    val where = listOf(book.listTitle, book.storeName).filter { it.isNotBlank() }.joinToString(" · ")
                    if (where.isNotEmpty()) Text(where, style = t.caption.copy(color = c.inkSoft))
                    Text(stats, style = t.body.copy(color = c.ink))
                }
                if (book.perfect) {
                    HankoStamp(HankoText.PerFect, Modifier.align(Alignment.TopEnd).rotate(-12f).clearAndSetSemantics { }, size = 44.dp)
                }
            }
            // The stamp page at 2×: 32 dp stamps in 48 dp targets, names under them (§6.6).
            if (book.stamps.isNotEmpty()) {
                val clock = remember { MotionClock() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .hardShadow(RoundedCornerShape(12.dp), PicnicTheme.shadows, large = true)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (book.perfect) Color.Transparent else c.book.page)
                        .then(if (book.perfect) Modifier.background(holoBrush(Offset(400f, 200f))) else Modifier)
                        .padding(8.dp),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        book.stamps.forEach { s ->
                            val name = s.aisleName
                            val desc = if (s.done) stringResource(R.string.book_sheet_stamped, name) else stringResource(R.string.book_sheet_not_yet, name)
                            Column(
                                Modifier.width(64.dp).semantics(mergeDescendants = true) { contentDescription = desc },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    StampCircle(lookOf(s), s.position.takeIf { it > 0 }, s.done, now = false, phase = null, clock = clock, size = 32.dp)
                                }
                                Text(
                                    name,
                                    style = t.caption.copy(color = c.ink),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.clearAndSetSemantics { },
                                )
                            }
                        }
                    }
                }
            }
            val stickers = book.stickers.sortedBy { it.slot }
            if (book.total > 0) {
                Text(
                    stringResource(R.string.book_sheet_stickers),
                    style = t.label.copy(color = c.ink),
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp).semantics { heading() },
                )
                StickerPages(book.total, stickers.associateBy { it.slot }, full = stickers.size >= book.total || book.perfect)
            }
            book.bonus?.let { bonus ->
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ItemIcon(bonus.icon, contentDescription = bonus.name, size = 40.dp, holo = true)
                    Text(stringResource(R.string.list_trip_book_bonus), style = t.label.copy(color = c.ink))
                }
            }
            if (slip != null && spoken != null) {
                Text(
                    stringResource(R.string.book_sheet_receipt),
                    style = t.label.copy(color = c.ink),
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp).semantics { heading() },
                )
                val show = stringResource(R.string.book_receipt_show)
                Box(
                    Modifier
                        .padding(start = 4.dp, top = 6.dp, bottom = 14.dp)
                        .testTag(FiledBookTags.RECEIPT)
                        .semantics(mergeDescendants = true) { contentDescription = spoken }
                        .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = show) { fullReceipt = true },
                ) {
                    ReceiptSticker(slip.lines, Modifier.clearAndSetSemantics { })
                }
            }
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)).height(12.dp).testTag(FiledBookTags.END))
        }
        if (fullReceipt && book != null && slip != null && spoken != null) {
            FullReceipt(slip.lines, spoken, onClose = { fullReceipt = false })
        }
    }
}

/** A filed stamp's look from its stored names (colour + pattern + pictogram, §7.2). */
internal fun lookOf(s: BookStamp): AisleLook {
    if (s.position <= 0) return AisleLooks.everythingElse
    val base = AisleLooks.of(s.position)
    return base.copy(
        family = Families.byName(s.family) ?: base.family,
        pattern = TabPattern.byKey(s.pattern) ?: base.pattern,
        pictogram = Pictogram.entries.firstOrNull { it.key == s.pictogram } ?: base.pictogram,
    )
}

/** The sticker pages at 2×: 40 dp stickers on release paper, a page at a time; swipe to turn, page dots and the LCD. */
@Composable
private fun StickerPages(total: Int, bySlot: Map<Int, earth.cora.picnic.core.store.BookSticker>, full: Boolean) {
    val c = PicnicTheme.colors
    val book = c.book
    var page by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = 46.dp
        val cols = ((maxWidth - 24.dp) / cell).toInt().coerceAtLeast(4)
        val rows = 4
        val perPage = cols * rows
        val pages = ((total + perPage - 1) / perPage).coerceAtLeast(1)
        val shown = page.coerceIn(0, pages - 1)
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .testTag(TripBookTags.PAGES)
                    .hardShadow(RoundedCornerShape(12.dp), PicnicTheme.shadows, large = true)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .then(if (full) Modifier.background(holoBrush(Offset(300f, 200f))) else Modifier.background(book.releasePaper))
                    .pointerInput(pages) {
                        var dx = 0f
                        detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                            if (dx < -40 * density) page = (shown + 1).coerceAtMost(pages - 1)
                            if (dx > 40 * density) page = (shown - 1).coerceAtLeast(0)
                        }) { _, a -> dx += a }
                    }
                    .padding(horizontal = 9.dp, vertical = 8.dp),
            ) {
                Column {
                    for (r in 0 until rows) {
                        Row {
                            for (col in 0 until cols) {
                                val slot = shown * perPage + r * cols + col
                                Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                                    if (slot < total) {
                                        val s = bySlot[slot]
                                        if (s == null) {
                                            Canvas(Modifier.size(26.dp).decorative()) {
                                                val rr = size.minDimension / 2f - 1.dp.toPx()
                                                drawCircle(book.spotFill, rr)
                                                drawDashedCircle(book.spotDash, center, rr, 2.dp.toPx(), 4.dp.toPx(), 3.dp.toPx())
                                            }
                                        } else {
                                            ItemIcon(
                                                s.icon, contentDescription = s.name, size = 40.dp, holo = s.rarity != Rarity.NONE,
                                                modifier = Modifier.testTag(TripBookTags.sticker(slot)).rotate(earth.cora.picnic.ui.list.motion.BookMotion.tilt(slot)),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (pages > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (p in 0 until pages) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (p == shown) c.ink else c.inkSoft.copy(alpha = 0.4f))
                                .decorative(),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(c.lcd)
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                    ) { LcdText("${shown + 1}/$pages", 1.2.dp) }
                }
            }
        }
    }
}

/** The receipt full size over a scrim (§13.2); a tap anywhere or Back puts it away. */
@Composable
private fun FullReceipt(lines: List<earth.cora.picnic.ui.common.ReceiptLine>, spoken: String, onClose: () -> Unit) {
    val c = PicnicTheme.colors
    BackHandler(onBack = onClose)
    val close = stringResource(R.string.book_receipt_close)
    val scale = kotlin.math.min(LocalDensity.current.fontScale, 1.6f).coerceAtLeast(1f)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(c.scrim)
            .testTag(FiledBookTags.RECEIPT_FULL)
            .clickable(interactionSource = null, indication = null, onClickLabel = close, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        val pitch = ((maxWidth - 64.dp) / (ReceiptLines.COLUMNS * 6 * scale)).coerceAtMost(2.2.dp)
        ReceiptSticker(lines, Modifier.clearAndSetSemantics { contentDescription = spoken }, pitch = pitch)
    }
}
