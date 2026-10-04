package earth.cora.picnic.release

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import earth.cora.picnic.ui.a11y.TextOverflowed
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * What a fixture shows, captured for the audits: the text runs the screen draws, which of them are really seen, the
 * screen as it is behind its words, and the semantics tree's text nodes with their layouts.
 */

/** A screen as drawn: its text runs, and with pixels, how much of each run's ink is on the screen ([DrawnRun.seenPixels]). */
class DrawnScreen(
    val runs: List<DrawnRun>,
    /** Whether [DrawnRun.seenPixels] was measured. */
    val pixels: Boolean,
    /** px per dp. */
    val density: Float,
    /** The font scale the screen runs at. */
    val fontScale: Float,
) {
    /** The runs that are seen (some of their ink is on the screen), when the pixels were drawn; all of them otherwise. */
    val seen: List<DrawnRun> get() = if (!pixels) runs else runs.filter { it.seenPixels >= SEEN_PIXELS }

    /**
     * What lies straight behind [run]'s words: what was painted before it under its own letters (the paper the reader
     * sees each letter on). Its edges are the pixels there that differ from a neighbour; [Behind.lines] counts the
     * separate lines they form under the letters: a pattern's stripes, dots and checks, or art, are many; a plain patch,
     * a pad or a smooth gradient has none, and a patch the words overlap the edge of has one or two (its edge is not a
     * pattern). Left out, as the words' own lines and not what they sit on: the ruled lines a pad's words sit on (rows
     * of one colour across the run's whole area) and, for a checked row's name, its strike-through (drawn under its
     * glyphs by StruckText, §4.11).
     */
    fun behindOf(run: DrawnRun): Behind {
        val bw = run.area.width()
        val bh = run.area.height()
        val bg = run.behind
        val ink = run.ink
        if (bw <= 0 || bh <= 0 || ink.none { it }) return Behind(0, 0, 0, 0)
        val ruled = BooleanArray(bh) { y -> (0 until bw).all { x -> close(bg[y * bw + x], bg[y * bw]) } }
        // A struck name's strike: StruckText draws a 2 dp line at 0.66 of the way from the line's top to its baseline.
        val struck = run.drawnBy("StruckText")
        val strikeY = run.baseline + run.ascent * 0.34f
        val strikeHalf = 2.5f * density
        val pt = FloatArray(2)
        fun onStrike(x: Int, y: Int): Boolean {
            if (!struck) return false
            pt[0] = run.area.left + x + 0.5f
            pt[1] = run.area.top + y + 0.5f
            run.toLocal.mapPoints(pt)
            return abs(pt[1] - strikeY) <= strikeHalf
        }
        // The edge map of what lies behind (the whole area, so a line is followed past the letters).
        val edge = BooleanArray(bw * bh)
        for (y in 0 until bh) {
            if (ruled[y]) continue
            for (x in 0 until bw) {
                if (onStrike(x, y)) continue
                val c = bg[y * bw + x]
                val right = if (x + 1 < bw) bg[y * bw + x + 1] else c
                val below = if (y + 1 < bh && !ruled[y + 1]) bg[(y + 1) * bw + x] else c
                edge[y * bw + x] = !close(c, right) || !close(c, below)
            }
        }
        var pixels = 0
        var edges = 0
        val colours = HashMap<Int, Int>()
        for (y in 0 until bh) {
            if (ruled[y]) continue
            for (x in 0 until bw) {
                val i = y * bw + x
                if (!ink[i] || onStrike(x, y)) continue
                pixels++
                colours[bg[i] and 0xF0F0F0] = (colours[bg[i] and 0xF0F0F0] ?: 0) + 1
                if (edge[i]) edges++
            }
        }
        // The separate lines (8-connected edge pixels) that run under the letters, each of a few pixels at least.
        val label = IntArray(bw * bh)
        var lines = 0
        val queue = IntArray(bw * bh)
        for (start in 0 until bw * bh) {
            if (!edge[start] || label[start] != 0) continue
            val id = start + 1
            var head = 0
            var tail = 0
            queue[tail++] = start
            label[start] = id
            var size = 0
            var underInk = false
            while (head < tail) {
                val i = queue[head++]
                size++
                if (ink[i]) underInk = true
                val x = i % bw
                val y = i / bw
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= bw || ny >= bh) continue
                    val j = ny * bw + nx
                    if (edge[j] && label[j] == 0) {
                        label[j] = id
                        queue[tail++] = j
                    }
                }
            }
            if (underInk && size >= LINE_PIXELS) lines++
        }
        return Behind(pixels, edges, colours.values.maxOrNull() ?: 0, lines)
    }

    companion object {
        /** A run is seen when this many of its ink pixels are on the screen. */
        const val SEEN_PIXELS = 4

        /** An edge this short is an anti-aliasing speck, not a line of what lies behind. */
        private const val LINE_PIXELS = 4

        /** Two pixels are the same paper when no channel differs by more than this (anti-aliased art or a pattern does). */
        private const val SAME = 8

        fun close(a: Int, b: Int): Boolean =
            abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= SAME &&
                abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= SAME &&
                abs((a and 0xFF) - (b and 0xFF)) <= SAME
    }
}

/**
 * What lies behind a run's words: [pixels] looked at, [edges] of them that differ from a neighbour, the [dominant]
 * colour's share, and the separate [lines] those edges form under the letters.
 */
data class Behind(val pixels: Int, val edges: Int, val dominant: Int, val lines: Int) {
    val edgeShare: Float get() = if (pixels == 0) 0f else edges.toFloat() / pixels
    val dominantShare: Float get() = if (pixels == 0) 1f else dominant.toFloat() / pixels
}

/**
 * Draws every window as the screen shows it now and records its text runs (with their ink and what lies behind them).
 * With [pixels], it also draws the screen with each run in its own colour ([IdCanvas]) and counts how much of each
 * run's ink is on the screen.
 */
fun FixtureStage.drawScreen(pixels: Boolean): DrawnScreen {
    compose.waitForIdle()
    val (w, h) = screenSize()
    var result: DrawnScreen? = null
    compose.runOnIdle {
        val behind = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val recorder = TextRecordingCanvas(behind, compose.density.density, suppressText = true)
        drawWindows(recorder)
        behind.recycle()
        if (pixels) {
            val ids = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawWindows(IdCanvas(ids))
            val px = IntArray(w * h)
            ids.getPixels(px, 0, w, 0, 0, w, h)
            ids.recycle()
            for (run in recorder.runs) {
                val colour = IdCanvas.idColour(run.call)
                var seen = 0
                for (y in run.area.top until run.area.bottom) for (x in run.area.left until run.area.right) if (px[y * w + x] == colour) seen++
                run.seenPixels = seen
            }
        }
        result = DrawnScreen(recorder.runs, pixels, compose.density.density, compose.density.fontScale)
    }
    return requireNotNull(result)
}

/** A text node of the semantics tree (any window), with what TalkBack and the audits read from it. */
class TextNode(
    val label: String,
    val tag: String?,
    /** Where it lies in its window (px). */
    val bounds: Rect,
    /** Its unclipped size (px). */
    val width: Int,
    val height: Int,
    val layouts: List<TextLayoutResult>,
    val overflowed: Boolean,
    /** An index tab's fitted name size (sp), from its TabNameFontSize key; null for other nodes. */
    val tabSp: Float?,
)

/** Every node that shows words (a Text's layout) or reports TextOverflowed, in every window, the unmerged tree. */
fun ComposeContentTestRule.textNodes(): List<TextNode> {
    val out = ArrayList<TextNode>()
    fun visit(n: SemanticsNode) {
        val texts = n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }
        val label = texts?.joinToString(" ") ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            ?: n.config.getOrNull(SemanticsProperties.TestTag) ?: "node ${n.id}"
        val layouts = ArrayList<TextLayoutResult>()
        n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val overflowed = n.config.getOrNull(TextOverflowed) == true
        if (layouts.isNotEmpty() || overflowed) {
            out += TextNode(
                label, n.config.getOrNull(SemanticsProperties.TestTag), n.boundsInWindow, n.size.width, n.size.height, layouts, overflowed,
                n.config.getOrNull(earth.cora.picnic.ui.theme.TabNameFontSize),
            )
        }
        n.children.forEach(::visit)
    }
    onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::visit)
    return out
}

/** Bounds in the window (px) of the nodes tagged [tag], in every window. */
fun ComposeContentTestRule.boundsOfTag(tag: String): List<Rect> =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInWindow }
