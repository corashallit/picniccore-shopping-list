package earth.cora.picnic.release

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import earth.cora.picnic.ui.a11y.Decorative
import earth.cora.picnic.ui.a11y.RepeatedNumeral
import earth.cora.picnic.ui.theme.LETTERSET_TAG

/*
 * One fixture captured for the text audits (WP10, docs/DESIGN_STICKER.md §2.7, §4.1, §15): what the screen draws (every
 * run of text, which of it is seen, what lies behind it) and what its semantics tree says about it (its text nodes and
 * layouts, its decorative nodes, its repeated numerals, its sheets).
 */

/** A fixture as captured at one [Combo]. */
class Capture(
    val fixture: Fixture,
    val combo: Combo,
    val screen: DrawnScreen,
    val nodes: List<TextNode>,
    /** Nodes marked decorative (doodles, page stickers): their words carry no information (§2.7, §4.1). */
    val decorative: List<Rect>,
    /** Nodes marked as repeated numerals (the aisle chip's number circle, §2.7). */
    val repeatedNumerals: List<Rect>,
    /** The letter-set sheets on screen (each its own surface, §2.7). */
    val sheets: List<Rect>,
) {
    val density: Float get() = screen.density

    /** "list_midtrip [day fs200]". */
    val where: String get() = "${fixture.name} [$combo]"

    /** Whether [run]'s middle lies inside one of [rects]. */
    fun inside(run: DrawnRun, rects: List<Rect>): Boolean {
        val c = Offset(run.bounds.centerX(), run.bounds.centerY())
        return rects.any { it.contains(c) }
    }

    /** The surface [run] is on: a sheet's when it lies on one, else the fixture's own (the screen a sheet opens over is the list). */
    fun surfaceOf(run: DrawnRun): Surface = when {
        fixture.surface.isSheet -> if (inside(run, sheets)) fixture.surface else Surface.List
        else -> fixture.surface
    }

    /** A puffy title's fill: the same words are drawn as a stroke pass under it (its own halo, §2.7). */
    fun isPuffy(run: DrawnRun): Boolean = run.stroke || screen.runs.any {
        it !== run && it.stroke && it.text == run.text && kotlin.math.abs(it.bounds.centerY() - run.bounds.centerY()) < 3f * density
    }
}

/** Whether this surface is a letter-set sheet over the list. */
val Surface.isSheet: Boolean
    get() = this in setOf(Surface.AddSheet, Surface.EditSheet, Surface.NewTripSheet, Surface.TripBookSheet, Surface.SwitcherSheet)

/** Runs [fixture] at [combo] on [compose] and captures it; [pixels] also measures which runs are seen and what lies behind them. */
fun captureFixture(compose: ComposeContentTestRule, fixture: Fixture, combo: Combo, pixels: Boolean = true): Capture {
    val stage = FixtureStage(compose, combo)
    try {
        fixture.run(stage)
        val screen = stage.drawScreen(pixels)
        val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        val all = ArrayList<SemanticsNode>()
        fun visit(n: SemanticsNode) {
            all += n
            n.children.forEach(::visit)
        }
        roots.forEach(::visit)
        return Capture(
            fixture, combo, screen, compose.textNodes(),
            decorative = all.filter { it.config.getOrNull(Decorative) != null }.map { it.boundsInWindow },
            repeatedNumerals = all.filter { it.config.getOrNull(RepeatedNumeral) != null }.map { it.boundsInWindow },
            sheets = all.filter { it.config.getOrNull(SemanticsProperties.TestTag) == LETTERSET_TAG }.map { it.boundsInWindow },
        )
    } finally {
        stage.close()
    }
}
