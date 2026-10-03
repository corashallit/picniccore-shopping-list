package earth.cora.picnic.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
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
import earth.cora.picnic.core.store.TripBook
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.HankoStamp
import earth.cora.picnic.ui.theme.HankoText
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LetterSetSheet
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.hardShadow

/*
 * A filed trip book, read-only (docs/DESIGN_STICKER.md §6.6, §4.25, checklist 165). Owner: WP8. WP5's TripBookSheet is
 * the book drawn at 2× with page turns; it is not in this package's base, so the album shows a filed book through this
 * sheet: the date and the stats, the stamp page (32 dp stamps, aisle names under them, each its own TalkBack node), the
 * stickers, the PER-FECT! hanko for a perfect trip and the receipt slip if one was printed. Nothing here can be changed.
 */

/** The read-only sheet of the filed [book] (null = closed). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiledBookSheet(book: TripBook?, dateLabel: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    LetterSetSheet(visible = book != null, onDismissRequest = onClose, modifier = modifier) {
        if (book == null) return@LetterSetSheet
        val stamped = book.stamps.count { it.done }
        val stats = buildString {
            append(stringResource(R.string.book_sheet_stats, book.stickers.size, book.total, stamped, book.stamps.size))
            if (book.completedAt != null && book.minutes != null) append(" · ").append(TripStats.formatMinutes(book.minutes))
        }
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
        val receipt = book.receipt
        if (receipt != null) {
            Text(
                stringResource(R.string.book_sheet_receipt),
                style = t.label.copy(color = c.ink),
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp).semantics { heading() },
            )
            ReceiptSlip(
                lines = listOfNotNull(
                    receipt.dateLabel,
                    receipt.store.takeIf { it.isNotBlank() },
                    stringResource(R.string.book_receipt_stickers, receipt.stickers),
                    stringResource(R.string.book_receipt_baskets, receipt.baskets),
                    receipt.minutes?.let { TripStats.formatMinutes(it) },
                    stringResource(R.string.book_receipt_shiny, receipt.shiny).takeIf { receipt.shiny > 0 },
                ),
            )
        }
    }
}

/** Thermal-receipt paper: a warm off-white, so the slip reads as paper on the white pad. */
private val ReceiptPaper = Color(0xFFFFFBEF)

/** The receipt slip (§13.1 egg 12's print, saved into the book): paper with a torn zigzag foot, ballpoint lines. */
@Composable
private fun ReceiptSlip(lines: List<String>) {
    val c = PicnicTheme.colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
    Column(
        Modifier
            .widthIn(max = 220.dp)
            .offset(x = 4.dp)
            .rotate(-1.5f)
            .hardShadow(shape, PicnicTheme.shadows)
            .background(ReceiptPaper, shape)
            .border(1.dp, Families.periwinkle.pastel, shape)
            .drawBehind {
                val tooth = 6.dp.toPx()
                val y = size.height
                val path = Path().apply {
                    moveTo(0f, y)
                    var x = 0f
                    var down = true
                    while (x < size.width) {
                        x += tooth / 2f
                        lineTo(x.coerceAtMost(size.width), if (down) y + tooth / 2f else y)
                        down = !down
                    }
                    lineTo(size.width, y)
                    close()
                }
                drawPath(path, ReceiptPaper)
                drawLine(Families.periwinkle.pastel, Offset(12.dp.toPx(), 30.dp.toPx()), Offset(size.width - 12.dp.toPx(), 30.dp.toPx()), 1.dp.toPx())
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        lines.forEachIndexed { i, line ->
            Text(
                line,
                style = (if (i == 0) PicnicTheme.type.label else PicnicTheme.type.caption).copy(color = c.ballpoint),
                modifier = if (i == 0) Modifier.padding(bottom = 6.dp) else Modifier,
            )
        }
    }
}
