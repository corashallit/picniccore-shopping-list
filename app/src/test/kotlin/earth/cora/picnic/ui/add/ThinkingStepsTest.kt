package earth.cora.picnic.ui.add

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.RecipeInfo
import earth.cora.picnic.core.assistant.SkipReason
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The thinking box (docs/DESIGN_STICKER.md §4.18, D16; items 202, 324): neutral while waiting, the recipe wording only
 * with a recipe, only real steps, 250 ms apart on the reveal clock, and the pantry step only with in_pantry rows.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ThinkingStepsTest {
    @get:Rule
    val compose = createComposeRule()

    private val f = AddFixtures

    private fun answer(vararg items: earth.cora.picnic.core.assistant.ProposedItem, webSearches: Int = 0, recipe: RecipeInfo? = null) =
        IngestResult(items.toList(), "", emptyList(), emptyList(), f.meta(webSearches), recipe = recipe)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { PicnicTheme(ThemeVariant.Day, reducedMotion = false) { content() } }
        compose.waitForIdle()
    }

    /** A clock [elapsed] ms into the reveal of [steps] steps. */
    private fun clockAt(steps: Int, elapsed: Long) =
        RevealClock(1, RevealPlan.thinking(steps, emptyList(), reduced = false), alreadyPlayed = false).also { it.elapsed = elapsed }

    @Test
    fun neutralWhileWaiting() {
        // Even when the note asks for a recipe, the waiting box claims nothing: no recipe wording, no steps.
        show { ThinkingBox(steps = null, recipe = true, clock = null, family = Families.melon) }
        compose.onNodeWithText("Reading your note…").assertExists()
        compose.onNodeWithText("Looking up a recipe…").assertDoesNotExist()
        compose.onNodeWithText("Searched the web", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Checked your list", substring = true).assertDoesNotExist()
    }

    @Test
    fun onlyRealSteps() {
        // A plain list: no web search, no recipe, no pantry. One step, and the plain headline.
        val plain = AddRules.thinkingSteps(answer(f.proposed("Lemons", "lemon", "produce"), f.proposed("Eggs", "eggs", "dairy-eggs")), "lemons, eggs")
        assertEquals(listOf(ThinkingStep.SortedAisles(2)), plain)
        show { ThinkingBox(plain, recipe = false, clock = clockAt(plain.size, RevealClock.DONE), family = Families.melon) }
        compose.onNodeWithText("Reading your note…").assertExists()
        compose.onNodeWithText("Checked your list, sorted into 2 aisles").assertExists()
        compose.onNodeWithText("Searched the web", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Compared", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Checked your pantry", substring = true).assertDoesNotExist()

        // Recipe meta only from the fields present: servings alone says "Makes 12", with no "Compared".
        val servingsOnly = AddRules.thinkingSteps(answer(f.proposed("Eggs", "eggs", "dairy-eggs"), recipe = RecipeInfo("Crepes", null, 12)), "crepes")
        assertEquals(listOf(ThinkingStep.ComparedRecipes(null, 12), ThinkingStep.SortedAisles(1)), servingsOnly)
        // A recipe with no meta adds no recipe step.
        val bare = AddRules.thinkingSteps(answer(f.proposed("Eggs", "eggs", "dairy-eggs"), recipe = RecipeInfo("Crepes", null, null)), "crepes")
        assertEquals(listOf(ThinkingStep.SortedAisles(1)), bare)
    }

    @Test
    fun stepsLand250MsApart() {
        val steps = AddRules.thinkingSteps(f.pancakes(), PANCAKES)
        assertEquals(3, steps.size)
        val plan = RevealPlan.thinking(steps.size, emptyList(), reduced = false)
        assertEquals(listOf(0L, 250L, 500L), plan.stepAt)
        assertEquals("the capsule pops a beat after the last step", 750L, plan.popAt)
        val clock = clockAt(steps.size, 0)
        show { ThinkingBox(steps, recipe = true, clock = clock, family = Families.lemon) }
        compose.onNodeWithText("Looking up a recipe…").assertExists()
        compose.onNodeWithText("Searched the web: “$PANCAKES”").assertExists()
        compose.onNodeWithText("Compared 3 recipes", substring = true).assertDoesNotExist()
        clock.elapsed = 249
        compose.waitForIdle()
        compose.onNodeWithText("Compared 3 recipes", substring = true).assertDoesNotExist()
        clock.elapsed = 250
        compose.waitForIdle()
        compose.onNodeWithText("Compared 3 recipes · makes 12").assertExists()
        compose.onNodeWithText("Checked your list", substring = true).assertDoesNotExist()
        clock.elapsed = 500
        compose.waitForIdle()
        compose.onNodeWithText("Checked your list, sorted into 4 aisles").assertExists()
    }

    @Test
    fun pantry() {
        // in_pantry rows: "Checked your pantry · left out n" (n is their count), before the sorting step.
        val withPantry = AddRules.thinkingSteps(f.pancakes(withPantry = true), PANCAKES)
        assertTrue(ThinkingStep.CheckedPantry(4) in withPantry)
        assertEquals(withPantry.size - 2, withPantry.indexOf(ThinkingStep.CheckedPantry(4)))
        show { ThinkingBox(withPantry, recipe = true, clock = clockAt(withPantry.size, RevealClock.DONE), family = Families.lemon) }
        compose.onNodeWithText("Checked your pantry · left out 4").assertExists()

        // No in_pantry rows (an empty pantry, or nothing in it): no pantry step.
        assertTrue(AddRules.thinkingSteps(f.pancakes(), PANCAKES).none { it is ThinkingStep.CheckedPantry })
        val one = answer(f.proposed("Salt", "salt", "aisle-2-baking", skip = SkipReason.IN_PANTRY), f.proposed("Eggs", "eggs", "dairy-eggs"))
        assertTrue(ThinkingStep.CheckedPantry(1) in AddRules.thinkingSteps(one, "x"))
    }
}
