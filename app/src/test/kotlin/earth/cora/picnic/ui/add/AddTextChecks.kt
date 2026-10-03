package earth.cora.picnic.ui.add

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import earth.cora.picnic.ui.a11y.TextOverflowed
import org.junit.Assert.assertTrue

/*
 * The add flows' clipped-text check (docs/DESIGN_STICKER.md §15: nothing clips up to font scale 2.0; §2.7 puffy text).
 * A text is clipped when it lost words, however that happened: a line cut by maxLines (didExceedMaxLines, "Write a" for
 * "Write a note…"), a box too short for its lines (didOverflowHeight, "Add as plain" in a fixed-height pill), an
 * ellipsis on any line, or a line's last visible glyph drawn past its box's width. Words TalkBack never reads (a row's
 * words under its own label, decorative print) report TextOverflowed instead, which counts too.
 *
 * Two allowances, both for blank space only: didOverflowWidth alone is not used (it is true for any single word wider
 * than a soft-wrapped box even when every line fits), and a box at most [LEADING_DP] shorter than its one line (a fixed
 * 22 dp chip whose font's line box is 19 dp in its 18 dp inside) is the line's blank leading, never ink: any real cut
 * (a wrapped word, a second line) overflows by a whole line.
 */

/** The blank leading a line box may lose without losing ink. */
private const val LEADING_DP = 1.5f

/** Why [r] lost text, or null when it shows every word. */
fun lostWords(r: TextLayoutResult, density: Float): String? {
    if (r.lineCount == 0) return null
    val reasons = buildList {
        val over = r.multiParagraph.height - r.size.height
        if (r.multiParagraph.didExceedMaxLines) add("cut by maxLines")
        else if (r.didOverflowHeight && over > LEADING_DP * density) add("taller than its box (${r.multiParagraph.height} px in ${r.size.height} px)")
        if ((0 until r.lineCount).any { r.isLineEllipsized(it) }) add("ellipsized")
        // Line positions are in the paragraph's own width: a centred line in a box that hugs it sits inside that width.
        val width = maxOf(r.size.width.toFloat(), r.multiParagraph.width)
        val past = (0 until r.lineCount).any { line ->
            val end = r.getLineEnd(line, visibleEnd = true)
            end > r.getLineStart(line) && r.getBoundingBox(end - 1).right > width + density
        }
        if (past) add("past its width")
    }
    return reasons.joinToString().ifEmpty { null }
}

/**
 * Every clipped text under the nodes tagged [scopes] (the bar, the sheet; the binder's app bar behind them is WP5's),
 * as "text: why".
 */
fun ComposeContentTestRule.clippedTexts(vararg scopes: String): List<String> {
    val roots = if (scopes.isEmpty()) {
        listOf(onRoot(useUnmergedTree = true).fetchSemanticsNode())
    } else {
        scopes.flatMap { onAllNodesWithTag(it, useUnmergedTree = true).fetchSemanticsNodes() }
    }
    val out = mutableListOf<String>()
    fun visit(node: SemanticsNode) {
        val name = node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: node.config.getOrNull(SemanticsProperties.TestTag)
            ?: "node ${node.id}"
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { action ->
            val results = mutableListOf<TextLayoutResult>()
            action(results)
            results.firstNotNullOfOrNull { lostWords(it, density.density) }?.let { out += "\"$name\": $it" }
        }
        if (node.config.getOrNull(TextOverflowed) == true) out += "\"$name\": TextOverflowed"
        node.children.forEach(::visit)
    }
    roots.forEach(::visit)
    return out.distinct()
}

/** Fails with every clipped text under [scopes] ([where] names the state). */
fun ComposeContentTestRule.assertNoClippedText(where: String, vararg scopes: String) {
    waitForIdle()
    val clipped = clippedTexts(*scopes)
    assertTrue("$where: clipped text $clipped", clipped.isEmpty())
}
