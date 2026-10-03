package earth.cora.picnic.ui.eggs

import android.content.res.Resources
import earth.cora.picnic.R
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.Receipt
import earth.cora.picnic.core.model.TripStats
import earth.cora.picnic.ui.album.ReceiptSlip
import earth.cora.picnic.ui.album.spokenReceipt
import earth.cora.picnic.ui.common.ReceiptLine
import earth.cora.picnic.ui.common.ReceiptLines
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * The LCD receipt's content (egg 18, docs/DESIGN_STICKER.md §13.2): its frozen values from the trip, its 9 lines at 21
 * columns (WP8's ReceiptLines, shared with the album's saved slip) and what TalkBack reads. Pure. Owner: WP9
 * (ReceiptTest).
 */
object ReceiptMaker {
    private val TORONTO: ZoneId = ZoneId.of("America/Toronto")

    /**
     * The receipt [list]'s trip prints at [now]: stickers = checked items, baskets = [stops] ("Everything else"
     * included), minutes = the shop time frozen at the first completion (null for a migrated trip: "--"), shiny = shiny +
     * jackpot stickers, the store as the trip recorded it (else [storeName]), and the trip's date (the first check-off of
     * its last session, §7.1) as an ISO date in [Receipt.dateLabel]. A receipt printed before is printed again unchanged
     * (its stats are frozen at the first print).
     */
    fun receipt(list: GroceryList, stops: Int, storeName: String, now: Long, zone: ZoneId = TORONTO): Receipt {
        list.trip?.receipt?.let { return it }
        val stats = TripStats.of(list, stops)
        val start = TripStats.lastSessionStart(list) ?: now
        val date = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        return Receipt(
            printedAt = now,
            dateLabel = date.toString(),
            store = list.trip?.storeName?.takeIf { it.isNotBlank() } ?: storeName,
            stickers = stats.stickers,
            baskets = stats.baskets,
            minutes = stats.minutes,
            shiny = stats.shiny,
        )
    }

    /** The receipt's date: its ISO [Receipt.dateLabel], else the day it was printed. */
    fun date(receipt: Receipt, zone: ZoneId = TORONTO): LocalDate =
        runCatching { LocalDate.parse(receipt.dateLabel) }.getOrNull() ?: Instant.ofEpochMilli(receipt.printedAt).atZone(zone).toLocalDate()

    /** The 9 lines (§13.2): the wordmark, "SAT 3 OCT · HILLCREST", a rule, the four stats, a rule, "THANK YOU ♪". */
    fun lines(appName: String, receipt: Receipt, listTitle: String): List<ReceiptLine> =
        ReceiptLines.layout(appName, date(receipt), receipt.store, listTitle, receipt.stickers, receipt.baskets, receipt.minutes, receipt.shiny)

    /** "Trip receipt: Saturday 3 October, Hillcrest, 24 stickers, 8 baskets, 41 minutes, 1 shiny" (§13.2). */
    fun spoken(resources: Resources, receipt: Receipt, listTitle: String): String {
        val slip = ReceiptSlip(
            lines = emptyList(),
            date = date(receipt),
            storeWord = ReceiptLines.storeWord(receipt.store, listTitle),
            stickers = receipt.stickers,
            baskets = receipt.baskets,
            minutes = receipt.minutes,
            shiny = receipt.shiny,
        )
        return spokenReceipt(resources, slip)
    }

    /** The app's name, as the receipt's header prints it. */
    fun appName(resources: Resources): String = resources.getString(R.string.app_name)
}
