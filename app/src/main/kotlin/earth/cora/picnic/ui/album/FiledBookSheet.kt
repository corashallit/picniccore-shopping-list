package earth.cora.picnic.ui.album

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import earth.cora.picnic.core.model.Receipt
import earth.cora.picnic.core.model.TripStats
import earth.cora.picnic.core.store.TripBook
import earth.cora.picnic.ui.a11y.focusRingIndication
import earth.cora.picnic.ui.a11y.spokenDuration
import earth.cora.picnic.ui.common.ReceiptLine
import earth.cora.picnic.ui.common.ReceiptLines
import earth.cora.picnic.ui.common.ReceiptSticker
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.HankoStamp
import earth.cora.picnic.ui.theme.HankoText
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LetterSetSheet
import earth.cora.picnic.ui.theme.PicnicTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

/*
 * A filed trip book, read-only (docs/DESIGN_STICKER.md §6.6, §4.25, checklist 165). Owner: WP8. WP5's TripBookSheet is
 * the book drawn at 2× with page turns; it is not in this package's base, so the album shows a filed book through this
 * sheet: the date and the stats, the stamp page (32 dp stamps, aisle names under them, each its own TalkBack node), the
 * stickers, the PER-FECT! hanko for a perfect trip and, if one was printed, the receipt itself (§13.2: LCD dots on
 * thermal paper, [ReceiptSticker]), which a tap shows full size. Nothing here can be changed.
 */

/**
 * The read-only sheet of the filed [book] (null = closed). [date] is the trip's date (the first check-off of its last
 * session, §7.1; the row's date), printed on the receipt. Tapping the receipt slip shows it full size (§13.2).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiledBookSheet(
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
        LetterSetSheet(visible = book != null, onDismissRequest = onClose) {
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
                    HankoStamp(HankoText.PerFect, Modifier.align(Alignment.TopEnd).rotate(-10f).clearAndSetSemantics { }, size = 44.dp)
                }
            }
            if (book.stamps.isNotEmpty()) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    book.stamps.forEach { s ->
                        val family = Families.byName(s.family) ?: Families.periwinkle
                        val desc = if (s.done) stringResource(R.string.book_sheet_stamped, s.aisleName) else stringResource(R.string.book_sheet_not_yet, s.aisleName)
                        Column(
                            Modifier.width(64.dp).semantics(mergeDescendants = true) { contentDescription = desc },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                StampCircle(family, s.pictogram, s.done, size = 32.dp)
                            }
                            Text(
                                s.aisleName,
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
            val stickers = book.stickers.sortedBy { it.slot } + listOfNotNull(book.bonus)
            if (stickers.isNotEmpty()) {
                Text(
                    stringResource(R.string.book_sheet_stickers),
                    style = t.label.copy(color = c.ink),
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp).semantics { heading() },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stickers.forEach { s ->
                        ItemIcon(s.icon, contentDescription = s.name, size = 40.dp, holo = s.rarity != Rarity.NONE)
                    }
                }
            }
            if (slip != null && spoken != null) {
                Text(
                    stringResource(R.string.book_sheet_receipt),
                    style = t.label.copy(color = c.ink),
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp).semantics { heading() },
                )
                // The receipt itself (§13.2), as it was printed; a tap shows it full size.
                val show = stringResource(R.string.book_receipt_show)
                Box(
                    Modifier
                        .padding(start = 4.dp, top = 6.dp, bottom = 14.dp)
                        .testTag(FiledBookTags.RECEIPT)
                        .semantics(mergeDescendants = true) { contentDescription = spoken }
                        .clickable(interactionSource = null, indication = focusRingIndication(RoundedCornerShape(4.dp)), role = Role.Button, onClickLabel = show) { fullReceipt = true },
                ) {
                    ReceiptSticker(slip.lines, Modifier.clearAndSetSemantics { })
                }
            }
            // Room for the last card or the receipt's torn foot to scroll clear of the navigation bar.
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)).height(12.dp).testTag(FiledBookTags.END))
        }
        if (fullReceipt && book != null && slip != null && spoken != null) {
            ReceiptViewer(slip.lines, spoken, onClose = { fullReceipt = false })
        }
    }
}

/** Test tags. */
object FiledBookTags {
    const val RECEIPT: String = "filed_book_receipt"
    const val END: String = "filed_book_end"
    const val RECEIPT_FULL: String = "filed_book_receipt_full"
}

/** A saved receipt, ready to print: its 21-column lines and what TalkBack says. */
@Immutable
internal data class ReceiptSlip(
    val lines: List<ReceiptLine>,
    val date: LocalDate,
    val storeWord: String?,
    val stickers: Int,
    val baskets: Int,
    val minutes: Int?,
    val shiny: Int,
)

/** The receipt of [book] as printed: its frozen values, the trip's [date] and the store word (§13.2). */
internal fun receiptOf(book: TripBook, receipt: Receipt, date: LocalDate?, appName: String): ReceiptSlip {
    val day = date ?: Instant.ofEpochMilli(book.startedAt).atZone(TORONTO).toLocalDate()
    val store = receipt.store.ifBlank { book.storeName }
    return ReceiptSlip(
        lines = ReceiptLines.layout(appName, day, store, book.listTitle, receipt.stickers, receipt.baskets, receipt.minutes, receipt.shiny),
        date = day,
        storeWord = ReceiptLines.storeWord(store, book.listTitle),
        stickers = receipt.stickers,
        baskets = receipt.baskets,
        minutes = receipt.minutes,
        shiny = receipt.shiny,
    )
}

/** "Trip receipt: Saturday 26 September, Hillcrest, 19 stickers, 8 baskets, 41 minutes, 1 shiny" (§13.2). */
internal fun spokenReceipt(resources: android.content.res.Resources, slip: ReceiptSlip): String {
    val parts = listOfNotNull(
        SPOKEN_DATE.format(slip.date),
        slip.storeWord,
        resources.getQuantityString(R.plurals.book_receipt_stickers, slip.stickers, slip.stickers),
        resources.getQuantityString(R.plurals.book_receipt_baskets, slip.baskets, slip.baskets),
        slip.minutes?.let(::spokenDuration),
        resources.getString(R.string.book_receipt_shiny, slip.shiny),
    )
    return resources.getString(R.string.book_receipt_spoken, parts.joinToString(", "))
}

/**
 * The receipt full size (§13.2: "tapping the slip in the Trip book sheet shows it full size"): over a scrim, as wide as
 * the phone allows (21 columns at up to 2.2 dp a dot); a tap anywhere or Back puts it away.
 */
@Composable
private fun ReceiptViewer(lines: List<ReceiptLine>, spoken: String, onClose: () -> Unit) {
    val c = PicnicTheme.colors
    BackHandler(onBack = onClose)
    val close = stringResource(R.string.book_receipt_close)
    val scale = min(LocalDensity.current.fontScale, 1.6f).coerceAtLeast(1f)
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

private val TORONTO: ZoneId = ZoneId.of("America/Toronto")

private val SPOKEN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.US)
