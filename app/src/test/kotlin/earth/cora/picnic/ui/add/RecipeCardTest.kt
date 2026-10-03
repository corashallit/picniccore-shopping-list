package earth.cora.picnic.ui.add

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The row of the review called [name] (its TalkBack label starts with the name). */
fun reviewRow(name: String) = SemanticsMatcher("review row \"$name\"") { node ->
    val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() ?: return@SemanticsMatcher false
    label == name || label.startsWith("$name, ")
}

/**
 * The recipe card (docs/DESIGN_STICKER.md §4.17; items 203, 204, 323): the CTA follows the keeps, the keep switch is a
 * Role.Switch that presses a star on in the appear poses, and pantry rows start off with their reason.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class RecipeCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val f = AddFixtures

    private fun review(h: HarnessRig) {
        compose.onNodeWithTag(ADD_FIELD_TAG).performTextInput(PANCAKES)
        compose.waitForIdle()
        h.vm.send()
        compose.waitForIdle()
    }

    private fun footer(text: String) = compose.onNode(hasText(text, substring = true) and hasClickAction())

    private fun toggle(name: String) {
        compose.onNode(reviewRow(name) and isToggleable()).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun ctaCountTracksKeeps() {
        val h = compose.setHarness(FakeAssistant(ingest = { _, _ -> f.pancakes() }))
        review(h)
        compose.onNodeWithTag(RECIPE_CARD_TAG).assertExists()
        footer("Stick all 6 on my list").assertExists()
        compose.onNodeWithText("3 recipes · 6 of 10 kept").assertExists()

        toggle("Blueberries")
        footer("Stick all 7 on my list").assertExists()
        compose.onNodeWithText("3 recipes · 7 of 10 kept").assertExists()

        // Switch every kept row off: the CTA says what to do and does nothing.
        for (name in listOf("Blueberries", "All-purpose flour", "White sugar", "Baking powder", "Salt", "Eggs", "Unsalted butter")) toggle(name)
        compose.onNode(hasText("Switch one on to add it")).assertIsNotEnabled()
        toggle("Eggs")
        footer("Stick 1 on my list").assertExists()
    }

    @Test
    fun keepSwitch() {
        var current by mutableStateOf(f.review(2, f.pancakes(), ItemSource.AI_TEXT, PANCAKES))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PicnicTheme(ThemeVariant.Day, reducedMotion = false) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
                    RecipeCard(current, current.mainRows, clock = null, onToggleKeep = { key ->
                        current = current.copy(rows = current.rows.map { if (it.key == key && it.canKeep) it.copy(keep = !it.keep) else it })
                    }, onEditPantry = null)
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        // Each row that can be kept is one switch, labelled with its words; a row already on the list has none.
        val eggs = compose.onNode(reviewRow("Eggs") and isToggleable())
        eggs.assert(hasRole(Role.Switch)).assertIsOn()
        compose.onNode(reviewRow("Blueberries") and isToggleable()).assertIsOff()
        compose.onNode(reviewRow("Milk, 2 %")).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
        compose.onNode(reviewRow("All-purpose flour") and isToggleable())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("All-purpose flour, 2.5 kilogram bag, 2 cups")))

        // The star presses on in the appear poses: 0.3 → 1.3 → 0.92 → 1 (one 80 ms pose each).
        val blueberries = compose.onNode(reviewRow("Blueberries") and isToggleable())
        blueberries.performScrollTo()
        compose.mainClock.advanceTimeBy(100)
        val off = blueberries.captureToImage().asAndroidBitmap()
        blueberries.performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val small = starPixels(off, blueberries.captureToImage().asAndroidBitmap())
        compose.mainClock.advanceTimeBy(80)
        val big = starPixels(off, blueberries.captureToImage().asAndroidBitmap())
        compose.mainClock.advanceTimeBy(80)
        val under = starPixels(off, blueberries.captureToImage().asAndroidBitmap())
        compose.mainClock.advanceTimeBy(400)
        val rest = starPixels(off, blueberries.captureToImage().asAndroidBitmap())
        blueberries.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        assertTrue("0.3 < 0.92: $small, $under", small in 1 until under)
        assertTrue("0.92 < 1: $under, $rest", under < rest)
        assertTrue("1 < 1.3: $rest, $big", rest < big)
    }

    /** Pixels in the right-hand 56 dp of the row (the switch) that changed from [off] to [on]. */
    private fun starPixels(off: Bitmap, on: Bitmap): Int {
        val density = compose.density.density
        val from = (on.width - 56 * density).toInt().coerceAtLeast(0)
        var n = 0
        for (y in 0 until on.height) for (x in from until on.width) {
            val a = off.getPixel(x, y)
            val b = on.getPixel(x, y)
            val d = Math.abs((a shr 16 and 255) - (b shr 16 and 255)) + Math.abs((a shr 8 and 255) - (b shr 8 and 255)) + Math.abs((a and 255) - (b and 255))
            if (d > 60) n++
        }
        return n
    }

    @Test
    fun pantryRows() {
        val h = compose.setHarness(FakeAssistant(ingest = { _, _ -> f.pancakes(withPantry = true) }), state = f.stateWithPantry)
        review(h)
        // Pantry rows: faded with their reason and the recipe amount, switched off; the CTA counts only kept rows.
        val salt = compose.onNode(reviewRow("Salt") and isToggleable())
        salt.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Salt, in your pantry, ½ teaspoon"))).assertIsOff()
        compose.onNode(reviewRow("All-purpose flour") and isToggleable())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("All-purpose flour, in your pantry, 2 cups"))).assertIsOff()
        footer("Stick all 2 on my list").assertExists()
        compose.onNodeWithTag(EDIT_PANTRY_TAG).performScrollTo().assert(hasText("Edit my pantry"))

        // Switching one on adds it this time; the pantry never changes.
        toggle("Salt")
        footer("Stick all 3 on my list").assertExists().performClick()
        compose.waitForIdle()
        val added = h.commits.single().added.map { it.name }
        assertEquals(listOf("Salt", "Eggs", "Unsalted butter"), added)
        assertEquals(f.stateWithPantry.pantry, runBlocking { h.rig.store.current().pantry })
    }

    @Test
    fun noPantryRowsNoEditButton() {
        val h = compose.setHarness(FakeAssistant(ingest = { _, _ -> f.pancakes() }))
        review(h)
        compose.onNodeWithTag(RECIPE_CARD_TAG).assertExists()
        compose.onNodeWithTag(EDIT_PANTRY_TAG).assertDoesNotExist()
    }
}
