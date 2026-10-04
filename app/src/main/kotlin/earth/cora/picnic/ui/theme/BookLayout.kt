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
    /** The cells in a page's grid of spots. */
    val cells: Int get() = cols * rows

    /**
     * The spots a page holds: every cell, or, when the spots run to more than one page, all but the bottom-right
     * [PAGE_NUMBER_CELLS] cells, where the page number sits inside the page (§7.3): never over a sticker, never past
     * the book's edge.
     */
    val perPage: Int get() = if (spots > cells) cells - PAGE_NUMBER_CELLS else cells
    val pages: Int get() = max(1, ceil(spots.toDouble() / perPage).toInt())

    /** The top of the right page's grid of spots (dp, inside the page). */
    val spotsTop: Float get() = ((height - BORDERS - rows * SPOT_H) / 2f).coerceAtLeast(1f)

    /** 76 dp with two stamp rows, 102 dp with three (§2.9). */
    val height: Float get() = if (stampRows >= 3) HEIGHT_THREE_ROWS else HEIGHT

    /** The page a spot is on. */
    fun pageOf(slot: Int): Int = if (perPage <= 0) 0 else (slot / perPage).coerceIn(0, pages - 1)

    /**
     * The page the book shows (§7.4): the page of the lowest free spot, or the last page when every spot is taken.
     * [taken] are the book slots in use.
     */
    fun shownPage(taken: Set<Int>, finished: Boolean = false): Int {
        // After the finale the keepsake shows its full first sheet, with PERFECT (§7.3).
        if (finished) return 0
        val free = (0 until spots).firstOrNull { it !in taken } ?: return pages - 1
        return pageOf(free)
    }

    /**
     * Where the PER-FECT! hanko is pressed after the finale (§7.3), in dp inside the pages (left, top, size): on the
     * right page's release paper when [page] leaves a free 2 × 2 block of cells at its bottom right (a one-page book with
     * spots to spare), 40 dp; else across the spine, in the gutter between the last stamp column and the first column of
     * stickers, as large as the gutter allows and centred in the page's height. Never over a sticker or a stamp.
     */
    fun perfectSpot(page: Int): PerfectSpot {
        val onPage = (min(spots, (page + 1) * perPage) - page * perPage).coerceAtLeast(0)
        val rightStart = leftWidth + SPINE
        if (pages == 1 && rows >= 2 && cols >= 2 && (rows - 2) * cols + (cols - 2) >= onPage) {
            val cx = rightStart + SPOTS_LEFT + (cols - 1) * SPOT_W
            val cy = spotsTop + (rows - 1) * SPOT_H
            return PerfectSpot(cx - PERFECT / 2f, cy - PERFECT / 2f, PERFECT)
        }
        // The gutter: from the stamp page's last column (6 dp padding) to the first sticker's art (a 26 dp twin centred
        // in its 23 dp cell), 1 dp clear of each.
        val from = leftWidth - 6f
        val to = rightStart + SPOTS_LEFT + (SPOT_W - TWIN) / 2f
        val size = min(PERFECT, to - from - 2f)
        val pageHeight = height - BORDERS
        return PerfectSpot(from + (to - from - size) / 2f, (pageHeight - size) / 2f, size)
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

        /** The cells a page of a book with more than one page gives its page number (§7.3). */
        const val PAGE_NUMBER_CELLS: Int = 2

        /** The spots' left padding on the right page, a book twin's size and the PER-FECT! hanko's (dp). */
        const val SPOTS_LEFT: Float = 7f
        const val TWIN: Float = 26f
        const val PERFECT: Float = 40f

        /** The newest twins the collapsed strip always keeps room for (§7.4: the magic appear lands there). */
        const val COLLAPSED_MIN_TWINS: Int = 3

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

/** Where the PER-FECT! hanko goes on the book's pages (dp): its top-left and its size ([BookLayout.perfectSpot]). */
@Immutable
data class PerfectSpot(val left: Float, val top: Float, val size: Float)

/**
 * The collapsed strip's stamps (§7.4), laid out in the room the newest twins and their "+n" pearl leave them: every stop
 * as a dot when they fit, 12 dp on 3 dp gaps up to 8 stops (2 dp gaps when that is what fits), else 10 dp on 2 dp gaps;
 * when even those do not fit, a run of [count] dots from [first] that always holds the anchor (the "now" stop), with a
 * "+n" pill for the [hiddenBefore] stops before it and one for the [hiddenAfter] after it, so no stop goes missing
 * without a sign. All sizes in dp.
 */
@Immutable
data class CollapsedDots(
    val size: Float,
    val gap: Float,
    val first: Int,
    val count: Int,
    val hiddenBefore: Int,
    val hiddenAfter: Int,
) {
    /** The run's width with pills [pillWidth] dp wide. */
    fun width(pillWidth: Float): Float {
        var w = if (count > 0) count * size + (count - 1) * gap else 0f
        if (hiddenBefore > 0) w += pillWidth + if (count > 0) gap else 0f
        if (hiddenAfter > 0) w += pillWidth + if (count > 0 || hiddenBefore > 0) gap else 0f
        return w
    }

    companion object {
        const val DOT: Float = 12f
        const val DOT_GAP: Float = 3f
        const val SMALL_DOT: Float = 10f
        const val SMALL_GAP: Float = 2f

        /**
         * The dots for [stops] stops in [room] dp, keeping [anchor] (the now stop's index) in view; [pillWidth] is the
         * widest "+n" pill. Above 8 stops the dots are always the small ones.
         */
        fun plan(stops: Int, anchor: Int, room: Float, pillWidth: Float): CollapsedDots {
            if (stops <= 0) return CollapsedDots(DOT, DOT_GAP, 0, 0, 0, 0)
            fun row(n: Int, size: Float, gap: Float) = n * size + (n - 1) * gap
            if (stops <= 8 && row(stops, DOT, DOT_GAP) <= room) return CollapsedDots(DOT, DOT_GAP, 0, stops, 0, 0)
            // Up to 8 stops the 12 dp dots stay when closing their gaps to 2 dp is enough (a grown date tag).
            if (stops <= 8 && row(stops, DOT, SMALL_GAP) <= room) return CollapsedDots(DOT, SMALL_GAP, 0, stops, 0, 0)
            if (row(stops, SMALL_DOT, SMALL_GAP) <= room) return CollapsedDots(SMALL_DOT, SMALL_GAP, 0, stops, 0, 0)
            val step = SMALL_DOT + SMALL_GAP
            val a = anchor.coerceIn(0, stops - 1)
            // One pill: k dots, a gap, the pill.
            val one = floor((room - pillWidth) / step).toInt().coerceIn(1, stops - 1)
            if (a < one) return CollapsedDots(SMALL_DOT, SMALL_GAP, 0, one, 0, stops - one)
            if (a >= stops - one) return CollapsedDots(SMALL_DOT, SMALL_GAP, stops - one, one, stops - one, 0)
            // A pill on each side: the last stop before the anchor, the anchor, then what comes next.
            val two = floor((room - 2 * pillWidth - SMALL_GAP) / step).toInt().coerceIn(1, stops - 2)
            val start = (a - min(1, two - 1)).coerceIn(1, stops - 1 - two)
            return CollapsedDots(SMALL_DOT, SMALL_GAP, start, two, start, stops - start - two)
        }
    }
}
