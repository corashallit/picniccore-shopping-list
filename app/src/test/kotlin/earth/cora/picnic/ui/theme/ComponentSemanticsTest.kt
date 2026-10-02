package earth.cora.picnic.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Chips: the store-aisle chip's label and the toggle chip's state (checklist 65). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ChipTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun storeChipLabel() {
        compose.setContent { PicnicTheme(ThemeVariant.Day) { StoreAisleChip(2) } }
        compose.onNodeWithContentDescription("store aisle 2").assertExists()
        compose.onNodeWithText("A2").assertDoesNotExist()
    }

    @Test
    fun chipIsACheckboxWithState() {
        compose.setContent { PicnicTheme(ThemeVariant.Day) { PicnicChip("Favourites", selected = true, onClick = {}) } }
        compose.onNodeWithText("Favourites").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        compose.onNodeWithText("Favourites").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "On"))
    }

}

/** Text fields: the key field is never pre-filled; labels belong to the editable node (checklist 66). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class FieldTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun keyFieldNeverPrefilled() {
        compose.setContent {
            PicnicTheme(ThemeVariant.Day) {
                var typed by remember { mutableStateOf("") }
                KeyField(typed, { typed = it }, label = "Your OpenAI key", savedMask = "sk-•••••••••• 3f9a", onRevealChange = {}, onPaste = {})
            }
        }
        // The editable node starts empty (a saved key is only the placeholder mask) and is announced with its label.
        compose.onNode(hasSetTextAction() and hasContentDescription("Your OpenAI key"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithContentDescription("Show key").assertExists()
        compose.onNodeWithContentDescription("Paste key").assertExists()
    }

    @Test
    fun fieldLabelBelongsToTheEditableNode() {
        compose.setContent { PicnicTheme(ThemeVariant.Day) { PicnicTextField("2", {}, label = "Amount") } }
        compose.onNode(hasSetTextAction() and hasContentDescription("Amount")).assertExists()
    }

}

/** The toggle switch is a Role.Switch (checklist 67). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class SwitchTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun semantics() {
        compose.setContent {
            PicnicTheme(ThemeVariant.Day) {
                var on by remember { mutableStateOf(false) }
                PicnicSwitch(on, { on = it })
            }
        }
        val node = compose.onNode(isToggleable())
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        node.assertIsOff()
        node.performClick()
        node.assertIsOn()
    }

    @Test
    fun keepSwitchIsASwitch() {
        compose.setContent { PicnicTheme(ThemeVariant.Day) { KeepSwitch(true, {}, contentDescription = "Keep flour") } }
        compose.onNode(isToggleable()).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsOn()
    }

}

/** The LCD keeps the count as its label while a word shows (checklist 263). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class LcdChipTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun modesAndLabels() {
        var state by mutableStateOf(LcdState(LcdMode.Count, count = 10, total = 24))
        var holds = 0
        compose.setContent { PicnicTheme(ThemeVariant.Day) { LcdChip(state, onHold = { holds++ }) } }
        val label = "10 of 24 in the basket"
        compose.onNodeWithContentDescription(label).assertExists()
        // While a word shows, TalkBack keeps the count label (words are decorative).
        for (mode in listOf(LcdMode.Text, LcdMode.Pet, LcdMode.Ok, LcdMode.Timer, LcdMode.Page)) {
            state = state.copy(mode = mode, text = "SNACK?")
            compose.waitForIdle()
            compose.onNodeWithContentDescription(label).assertExists()
        }
        // The receipt action exists only when the trip is complete (and eggs are on: the caller passes onHold).
        state = state.copy(mode = LcdMode.Count, receiptReady = false)
        compose.waitForIdle()
        assertNull(compose.onNodeWithContentDescription(label).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions))
        state = state.copy(receiptReady = true)
        compose.waitForIdle()
        val actions = compose.onNodeWithContentDescription(label).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Print the trip receipt"), actions.map { it.label })
        actions.single().action()
        assertEquals(1, holds)
    }

    @Test
    fun lcdTextOverflowsInsteadOfClipping() {
        compose.setContent { PicnicTheme(ThemeVariant.Day) { Box(Modifier.width(40.dp)) { LcdText("SATURDAY HILLCREST", 1.9.dp) } } }
        compose.onNode(hasText("SATURDAY HILLCREST")).assert(SemanticsMatcher.expectValue(earth.cora.picnic.ui.a11y.TextOverflowed, true))
    }

}

/** Loaders announce their label politely and turn static + "working…" under reduced motion (checklist 72). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class LoaderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun reducedMotionStatic() {
        compose.setContent {
            PicnicTheme(ThemeVariant.Day, reducedMotion = true) {
                Column {
                    PicnicLoader(LoaderKind.Gashapon, "Reading your note…")
                    PicnicLoader(LoaderKind.BusySparkle, null)
                }
            }
        }
        compose.onNodeWithText("Reading your note… · working…").assertExists()
        compose.onNodeWithText("working…").assertExists()
    }

    @Test
    fun loaderLabelIsAPoliteLiveRegion() {
        compose.setContent { PicnicTheme(ThemeVariant.Day) { PicnicLoader(LoaderKind.BeeLoop, "Reading your list…") } }
        val node = compose.onNodeWithText("Reading your list…").fetchSemanticsNode()
        assertEquals(androidx.compose.ui.semantics.LiveRegionMode.Polite, node.config[SemanticsProperties.LiveRegion])
    }

}

/** Inline glyphs stay silent; a disabled menu item says why (checklist 27, 51). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ComponentSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun inlineGlyphsAreVectorsAndSilent() {
        compose.setContent { PicnicTheme(ThemeVariant.Day) { PicnicText("Added 5 items to Saturday shop ♡", PicnicTheme.type.toast) } }
        compose.onNodeWithText("Added 5 items to Saturday shop").assertExists()
        val texts = compose.onNode(hasText("Added", substring = true)).fetchSemanticsNode().config[SemanticsProperties.Text]
        assertTrue(texts.none { t -> t.text.any { it in INLINE_GLYPHS } })
    }

    @Test
    fun overflowMenuDisabledItemSaysWhy() {
        compose.setContent {
            PicnicTheme(ThemeVariant.Day) {
                OverflowMenu(listOf(MenuItem("New trip", PicnicGlyph.Trip, {}, enabled = false, disabledState = "No trip yet")), onDismissRequest = {})
            }
        }
        compose.onNodeWithText("New trip").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "No trip yet"))
        compose.onNodeWithText("New trip").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
    }
}
