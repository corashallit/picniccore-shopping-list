package earth.cora.picnic.ui.theme

import androidx.compose.runtime.Immutable
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * The trip sticker book's layout (docs/DESIGN_STICKER.md §7.4): pure numbers in dp, unit-tested (BookLayoutTest). The
 * stamp page holds one circle per stop and adapts to 1-24 stops; the sticker page shows one spot per item, a page at a
 * time. Owner: WP5.
 */

/** The book laid out for a width, a number of stops and a number of spots (§7.4). All sizes in dp. */
@Immutable
data class BookLayout(
    /** Book width (the screen width − 24 dp, at most 456 dp). */
    val width: Float,
    /** Stops on the stamp page ("Everything else" included). */
    val stops: Int,
    /** Spots on the sticker pages (the items on the list). */
    val spots: Int,
    /** 24 dp circles, 20 dp above 8 stops. */
    val stampSize: Float,
    /** 2 rows, 3 above 16 stops. */
    val stampRows: Int,
    /** At least 4 columns. */
    val stampCols: Int,
    /** The left page's width (12 dp of padding + the columns + 3 dp gaps). */
    val leftWidth: Float,
    /** The right page's width (the book − 6 dp of borders − the left page − the 12 dp spine). */
    val rightWidth: Float,
    /** Sticker spots per row on a page (23 dp each, at least 4). */
    val cols: Int,
    /** Rows of spots on a page: the stamp rows + 1. */
    val rows: Int,
    /** Circles drawn: every stop, or 23 + a "+n" circle above 24 stops. */
    val circles: Int,
    /** The "+n" circle's n above 24 stops, else 0. */
    val moreStops: Int,
) {
    val perPage: Int get() = cols * rows
    val pages: Int get() = max(1, ceil(spots.toDouble() / perPage).toInt())

    /** 76 dp with two stamp rows, 102 dp with three (§2.9). */
    val height: Float get() = if (stampRows >= 3) HEIGHT_THREE_ROWS else HEIGHT

    /** The page a spot is on. */
    fun pageOf(slot: Int): Int = if (perPage <= 0) 0 else (slot / perPage).coerceIn(0, pages - 1)

    /**
     * The page the book shows (§7.4): the page of the lowest free spot, or the last page when every spot is taken.
     * [taken] are the book slots in use.
     */
    fun shownPage(taken: Set<Int>): Int {
        val free = (0 until spots).firstOrNull { it !in taken } ?: return pages - 1
        return pageOf(free)
    }

    companion object {
        const val HEIGHT: Float = 76f
        const val HEIGHT_THREE_ROWS: Float = 102f
        const val COLLAPSED: Float = 40f
        const val SPOT_W: Float = 23f
        const val SPOT_H: Float = 22f
        const val SPOT: Float = 13f
        const val SPINE: Float = 12f
        const val BORDERS: Float = 6f
        const val STAMP_GAP: Float = 3f
        const val MAX_WIDTH: Float = 456f
        const val MAX_CIRCLES: Int = 24

        /** The book's width on a screen [screenWidth] dp wide (12 dp margins, at most 456 dp, centred). */
        fun widthFor(screenWidth: Float): Float = min(screenWidth - 24f, MAX_WIDTH)

        /** §7.4's formula. */
        fun of(width: Float, stops: Int, spots: Int): BookLayout {
            val s = stops.coerceAtLeast(1)
            val stampSize = if (s <= 8) 24f else 20f
            val stampRows = if (s <= 16) 2 else 3
            val stampCols = max(4, ceil(min(s, MAX_CIRCLES) / stampRows.toDouble()).toInt())
            val leftWidth = 12f + stampCols * stampSize + (stampCols - 1) * STAMP_GAP
            val rightWidth = width - BORDERS - leftWidth - SPINE
            val cols = max(4, floor((rightWidth - 12f) / SPOT_W).toInt())
            val more = if (s > MAX_CIRCLES) s - (MAX_CIRCLES - 1) else 0
            return BookLayout(
                width = width,
                stops = stops,
                spots = spots,
                stampSize = stampSize,
                stampRows = stampRows,
                stampCols = stampCols,
                leftWidth = leftWidth,
                rightWidth = rightWidth,
                cols = cols,
                rows = stampRows + 1,
                circles = if (more > 0) MAX_CIRCLES else s,
                moreStops = more,
            )
        }
    }
}
