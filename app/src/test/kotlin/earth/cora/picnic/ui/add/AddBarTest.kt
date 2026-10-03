package earth.cora.picnic.ui.add

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.SkipReason
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The docked add bar with the add flows behind it (docs/DESIGN_STICKER.md §6.2; items 115, 199, 200, 201, 208, 213),
 * through [AddFlowsHarness] with a fake assistant.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class AddBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val f = AddFixtures

    private fun button(label: String) = compose.onNode(hasContentDescription(label) and hasClickAction())

    private val field get() = compose.onNodeWithTag(ADD_FIELD_TAG)

    private fun answer(vararg items: earth.cora.picnic.core.assistant.ProposedItem) =
        IngestResult(items.toList(), "", emptyList(), emptyList(), f.meta())

    private fun send(text: String) {
        field.performTextInput(text)
        compose.waitForIdle()
        button("Send").performClick()
        compose.waitForIdle()
    }

    /** Item 115: 84 dp, the field and three labelled 52 dp buttons (60 dp targets); the doodle is decorative. */
    @Test
    fun buttonsLabelled() {
        var snaps = 0
        var speaks = 0
        var sends = 0
        compose.setContent {
            PicnicTheme(ThemeVariant.Day) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    AddBar(AddBarUi(), onTextChange = {}, onSend = { sends++ }, onSnap = { snaps++ }, onChoosePhoto = {}, onSpeak = { speaks++ })
                }
            }
        }
        compose.onNodeWithTag(ADD_BAR_TAG).assertHeightIsEqualTo(84.dp)
        compose.onNode(hasSetTextAction() and hasContentDescription("Write a note")).assertExists()
        for (label in listOf("Snap a photo of a list", "Speak a voice note", "Send")) {
            button(label).assert(hasRole(Role.Button)).assertIsEnabled().assertWidthIsAtLeast(52.dp).assertHeightIsEqualTo(60.dp)
        }
        button("Snap a photo of a list").performClick()
        button("Speak a voice note").performClick()
        button("Send").performClick()
        assertEquals(listOf(1, 1, 1), listOf(snaps, speaks, sends))
        // The doodle and the placeholder's example line say nothing to TalkBack.
        compose.onNode(hasText("♡ picnic day ☆")).assertDoesNotExist()
        compose.onNode(hasText("“pancakes from scratch”")).assertDoesNotExist()
        compose.onNode(hasText("Write a note…")).assertDoesNotExist()
    }

    /** Item 199: no key, so Send adds the text as typed (no request) with the toast that says so, and Undo. */
    @Test
    fun noKeyAddsAsTyped() {
        val h = compose.setHarness(hasKey = false)
        val before = h.items().map { it.id }
        send("2 lemons, oat milk")
        assertTrue("no request without a key", h.rig.assistant.ingested.isEmpty())
        val added = h.items().filter { it.id !in before }
        assertEquals(listOf("Lemons", "Oat milk"), added.map { it.name })
        assertTrue(added.all { it.source == ItemSource.TYPED })
        val toast = h.shown.last()
        assertEquals("Added as typed · add your OpenAI key for smart sorting", toast.text)
        assertEquals("Undo", toast.actionLabel)
        assertEquals("", h.vm.state.value.text)
        toast.onAction!!()
        compose.waitForIdle()
        assertEquals(before, h.items().map { it.id })
    }

    /** Item 200: while waiting Send is the wobbling capsule ("Stop reading"), the field is read-only, and it stops. */
    @Test
    fun waitingCapsule() {
        val gate = CompletableDeferred<IngestResult>()
        val h = compose.setHarness(FakeAssistant(ingest = { _, _ -> gate.await() }))
        send("pears")
        compose.onNodeWithTag(ADD_CAPSULE_TAG)
            .assert(hasContentDescription("Stop reading"))
            .assert(hasStateDescription("Reading your note…"))
            .assert(hasRole(Role.Button))
        button("Send").assertDoesNotExist()
        // The field keeps the note and takes no typing.
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.IsEditable, false))
        h.vm.setText("pears and plums")
        assertEquals("pears", h.vm.state.value.text)
        assertFalse("waiting stays in the bar", h.vm.state.value.sheetOpen)
        // Tapping the capsule stops the request; Send is back and the note is kept.
        compose.onNodeWithTag(ADD_CAPSULE_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(ADD_CAPSULE_TAG).assertDoesNotExist()
        button("Send").assertExists()
        assertEquals("pears", h.vm.state.value.text)
        gate.complete(answer())
    }

    /** Item 201: a quick answer commits at once with a toast; Undo removes exactly the added items. */
    @Test
    fun quickAddCommitsWithUndo() {
        val h = compose.setHarness(
            FakeAssistant(ingest = { _, _ -> answer(f.proposed("Lemons", "lemon", "produce", "2"), f.proposed("Oat milk", "plant_milk", "dairy-eggs", "1.89 L carton")) }),
        )
        val before = h.items().map { it.id to it.checked }
        send("2 lemons, oat milk")
        assertFalse("no sheet for a quick add", h.vm.state.value.sheetOpen)
        compose.onNodeWithTag(ADD_TABS_TAG).assertDoesNotExist()
        val commit = h.commits.single()
        assertEquals(listOf("Lemons", "Oat milk"), commit.added.map { it.name })
        val toast = h.shown.last()
        assertEquals("Added 2 items to Saturday shop ♡", toast.text)
        assertEquals("Undo", toast.actionLabel)
        toast.onAction!!()
        compose.waitForIdle()
        assertEquals("Undo removes exactly what was added", before, h.items().map { it.id to it.checked })
    }

    /** Item 208: the camera button's TalkBack action and its long-press open the picker; a tap still takes a photo. */
    @Test
    fun choosePhotoAction() {
        val h = compose.setHarness()
        val snap = button("Snap a photo of a list")
        snap.performCustomAction("Choose a photo")
        compose.waitForIdle()
        assertEquals(1, h.sources.picks)
        snap.performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(2, h.sources.picks)
        assertEquals("the long-press never takes a photo as well", 0, h.sources.photos)
        snap.performClick()
        compose.waitForIdle()
        assertEquals(1, h.sources.photos)
    }

    /** Item 213: a duplicate of an unchecked item adds no second row; its sticker pops and the toast says so. */
    @Test
    fun duplicatePopsExisting() {
        val h = compose.setHarness(
            FakeAssistant(ingest = { _, _ -> answer(f.proposed("Strawberries", "strawberry", "produce", "1 L basket", skip = SkipReason.ALREADY_ON_LIST)) }),
        )
        val before = h.items()
        send("strawberries")
        assertEquals(before.size, h.items().size)
        val commit = h.commits.single()
        assertTrue(commit.added.isEmpty())
        assertEquals("the list pops this sticker", listOf("i-strawberries"), commit.alreadyThere.map { it.id })
        val toast = h.shown.last()
        assertEquals("Strawberries is already on your list", toast.text)
        assertNull("nothing changed, so no Undo", toast.actionLabel)
    }

    /** Item 213: a duplicate of a checked item un-checks it ("back on your list"); Undo checks it again. */
    @Test
    fun duplicateOfCheckedRevives() {
        val h = compose.setHarness(hasKey = false)
        val before = h.items()
        send("bananas")
        val bananas = h.items().single { it.id == "i-bananas" }
        assertFalse(bananas.checked)
        assertEquals(before.size, h.items().size)
        assertEquals(listOf("i-bananas"), h.commits.single().revived.map { it.id })
        val toast = h.shown.last()
        assertEquals("Bananas is back on your list", toast.text)
        toast.onAction!!()
        compose.waitForIdle()
        val again = h.items().single { it.id == "i-bananas" }
        assertTrue(again.checked)
        assertEquals(before.single { it.id == "i-bananas" }.checkedAt, again.checkedAt)
    }

    @Test
    fun emptySendFocusesTheField() {
        compose.setHarness()
        button("Send").performClick()
        compose.waitForIdle()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true))
        compose.onNode(hasTestTag(ADD_TABS_TAG)).assertDoesNotExist()
    }
}
