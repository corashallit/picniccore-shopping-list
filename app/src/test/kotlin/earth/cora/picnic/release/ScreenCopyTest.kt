package earth.cora.picnic.release

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Items 293, 294 and 295 on the screens themselves (docs/DESIGN_STICKER.md §16, §17): every screen fixture of the walker
 * (ScreenFixtures: the List in its states and its sheets, Add with Write, Snap, Speak and the recipe card, Edit, New
 * trip, the Trip book, the switcher, My store, My pantry, Settings, About, the Surprise lab, the album, the Next card, the
 * celebration's summary and the receipt), in Day at font scale 1.0 and in Glow at 2.0, shows and says no IP reference
 * word or real brand, no proxy, model name, price or emoji standing in for an icon, and only Canadian spelling and
 * metric units (cooking measures only in a recipe amount after its "·", §4.17). Everything TalkBack can read counts: each
 * node's text, editable text, content description, state description and action labels. Owner: WP10.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ScreenCopyTest(private val fixture: Fixture, private val combo: Combo) {
    @get:Rule
    val compose = createComposeRule()

    companion object {
        private val combos = listOf(Combo(ThemeVariant.Day, 1f), Combo(ThemeVariant.Glow, 2f, linear = true))

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun params(): List<Array<Any>> = ScreenFixtures.all.flatMap { f -> combos.map { arrayOf<Any>(f, it) } }
    }

    /** What [node] shows or says. */
    private fun words(node: SemanticsNode): List<String> {
        val c = node.config
        return c.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            listOfNotNull(c.getOrNull(SemanticsProperties.EditableText)?.text) +
            c.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            listOfNotNull(c.getOrNull(SemanticsProperties.StateDescription), c.getOrNull(SemanticsProperties.PaneTitle)) +
            c.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label } +
            listOfNotNull(c.getOrNull(SemanticsActions.OnClick)?.label, c.getOrNull(SemanticsActions.OnLongClick)?.label)
    }

    @Test
    fun theScreenSaysNothingItMayNot() {
        val stage = FixtureStage(compose, combo)
        val found = ArrayList<Found>()
        try {
            fixture.run(stage)
            fun visit(n: SemanticsNode) {
                words(n).filter { it.isNotBlank() }.forEach { found += Found("${fixture.name} [$combo] node ${n.id}", it) }
                n.children.forEach(::visit)
            }
            compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::visit)
        } finally {
            stage.close()
        }
        assertTrue("${fixture.name} [$combo] shows words (${found.size})", found.size >= 3)
        val offences = CopyRules.offences(found, CopyRules.ip + CopyRules.copy + CopyRules.canadianMetric) + CopyRules.cookingOffences(found)
        assertTrue("${fixture.name} [$combo]:\n" + offences.distinct().joinToString("\n"), offences.isEmpty())
    }
}
