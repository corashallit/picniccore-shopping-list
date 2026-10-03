package earth.cora.picnic.ui.eggs

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.list
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.add.ADD_FIELD_TAG
import earth.cora.picnic.ui.add.AddOrigin
import earth.cora.picnic.ui.add.AddViewModel
import earth.cora.picnic.ui.add.AddFixtures
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.FakePhotos
import earth.cora.picnic.ui.common.FakeRecorder
import earth.cora.picnic.ui.common.MainDispatcherRule
import earth.cora.picnic.ui.common.RecordingIconRepository
import earth.cora.picnic.ui.common.testAppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 244 (egg 7), the add flows' half: the magic word, sent from the add bar or the Write tab, adds nothing and makes
 * no assistant call (a counting fake assistant), clears the field, and starts the twinkle and the holo title on the real
 * SurpriseEngine; anything else ("kirakira milk") is an ordinary note.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MagicWordTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Rig(val eggs: EngineRig, val vm: AddViewModel, val assistant: FakeAssistant)

    private fun TestScope.rig(): Rig {
        val e = EngineRig()
        val assistant = FakeAssistant(
            ingest = { _, _ -> FakeAssistant.result(listOf(AddFixtures.proposed("Milk", "milk_bag", "dairy-eggs", "4 L bag"))) },
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        var next = 0
        val vm = AddViewModel(
            store = e.store,
            assistant = assistant,
            icons = RecordingIconRepository(),
            aisleSync = AisleSync(e.store, assistant, IconCatalog.default.baskets, e.clock::now),
            catalog = IconCatalog.default,
            photos = FakePhotos(),
            recorderFactory = { FakeRecorder() },
            clock = e.clock::now,
            newId = { "new-${next++}" },
            appScope = testAppScope(),
            savedState = SavedStateHandle(),
            hasKey = { true },
            surprises = e.engine,
            thumbs = { null },
            ioDispatcher = Dispatchers.Unconfined,
            words = context::getString,
        )
        vm.showList(e.listId)
        e.engine.onListShown(e.listId)
        return Rig(e, vm, assistant)
    }

    @Test
    fun theMagicWordAddsNothingAndCallsNoOne() = runTest {
        val r = rig()
        try {
            val words = listOf("kirakira", "Kira Kira!", "KIRA-KIRA!!", "キラキラ", "きらきら", "  kirakira  ", "kira kira！")
            for ((i, word) in words.withIndex()) {
                val before = r.eggs.store.value
                val origin = if (i % 2 == 0) AddOrigin.BAR else AddOrigin.SHEET
                r.vm.setText(word)
                r.vm.send(origin)
                advanceUntilIdle()
                assertEquals("no assistant call for \"$word\"", 0, r.assistant.ingested.size)
                assertEquals("nothing added for \"$word\"", before, r.eggs.store.value)
                assertEquals("the field clears", "", r.vm.state.value.text)
                assertTrue("\"$word\" twinkles and turns the title holo", r.eggs.eggs.kirakira)
                assertNotNull(r.eggs.eggs.kirakiraId)
                r.eggs.engine.effectDone(r.eggs.eggs.kirakiraId!!)
                assertFalse(r.eggs.eggs.kirakira)
            }
            assertEquals("kira, once per word", words.size, r.eggs.feedback.cues.count { it == Cue.MAGIC_APPEAR })
        } finally {
            r.eggs.close()
        }
    }

    @Test
    fun anythingElseIsAnOrdinaryNote() = runTest {
        val r = rig()
        try {
            r.vm.setText("kirakira milk")
            r.vm.send()
            advanceUntilIdle()
            assertEquals("an ordinary note goes to the assistant", 1, r.assistant.ingested.size)
            assertFalse(r.eggs.eggs.kirakira)
            assertTrue(r.eggs.store.value.list(r.eggs.listId)!!.items.any { it.name == "Milk" && !it.checked })
        } finally {
            r.eggs.close()
        }
    }

    @Test
    fun withEggsOffItIsJustText() = runTest {
        val r = rig()
        try {
            kotlinx.coroutines.runBlocking { r.eggs.settings.setSurprise(earth.cora.picnic.settings.Surprise.EasterEggs, false) }
            r.vm.setText("kirakira")
            r.vm.send()
            advanceUntilIdle()
            assertEquals("with Easter eggs off the word is an ordinary note", 1, r.assistant.ingested.size)
            assertFalse(r.eggs.eggs.kirakira)
        } finally {
            r.eggs.close()
        }
    }
}

/**
 * Item 244 (egg 7) on screen: the magic word typed into the practice list's add bar adds no row; every sticker twinkles
 * once, the screen title is holo for 5 s (then ballpoint again), "kira" plays; under reduced motion the title is holo
 * (static) for its 5 s and nothing twinkles.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class MagicWordScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun EggRig.typeAndSend(text: String) {
        compose.onNodeWithTag(ADD_FIELD_TAG).performTextInput(text)
        frame()
        compose.onNodeWithTag(ADD_FIELD_TAG).performImeAction()
        frame()
    }

    @Test
    fun theTitleIsHoloForFiveSeconds() {
        val r = EggRig(compose).start()
        val rows = r.list.items.size
        r.typeAndSend("Kira kira!")
        val t0 = r.now
        assertEquals("nothing added", rows, r.list.items.size)
        assertTrue(r.eggs.kirakira)
        assertEquals(1, r.feedback.count(Cue.MAGIC_APPEAR))
        r.advanceTo(t0 + EggRules.KIRAKIRA_MS - 200)
        assertTrue("still holo just before 5 s", r.eggs.kirakira)
        r.advanceTo(t0 + EggRules.KIRAKIRA_MS + 200)
        assertFalse("back to ballpoint after 5 s", r.eggs.kirakira)
        assertNull(r.eggs.kirakiraId)
    }

    @Test
    fun theStickersTwinkleOnce() {
        val r = EggRig(compose).start()
        r.probe.clear()
        r.typeAndSend("kirakira")
        val t0 = r.now
        r.advanceTo(t0 + 2_400)
        val twinkles = r.probe.frames.filter { it.tag.startsWith("twinkle") }
        assertTrue("the visible stickers twinkle", twinkles.map { it.tag }.distinct().size >= 3)
        assertTrue("a turn", twinkles.any { it.rotation != 0f })
        assertTrue("a scale", twinkles.any { it.scale != 1f })
        // Once: every sticker ends at rest.
        val last = twinkles.groupBy { it.tag }.mapValues { it.value.last() }
        assertTrue(last.values.all { it.rotation == 0f && it.scale == 1f })
    }

    @Test
    fun reducedMotionHoloWithoutTwinkle() {
        val r = EggRig(compose, reduced = true).start()
        r.probe.clear()
        r.typeAndSend("kirakira")
        val t0 = r.now
        assertTrue(r.eggs.kirakira)
        r.advanceTo(t0 + 2_400)
        // Every sticker the twinkle would move reports its pose: never turned, never scaled.
        val twinkles = r.probe.frames.filter { it.tag.startsWith("twinkle") }
        assertTrue("the stickers are there (${twinkles.size})", twinkles.map { it.tag }.distinct().size >= 3)
        assertTrue("no turn", twinkles.all { it.rotation == 0f })
        assertTrue("no scale", twinkles.all { it.scale == 1f })
        assertTrue("the title holo all the same", r.eggs.kirakira)
        r.advanceTo(t0 + EggRules.KIRAKIRA_MS + 200)
        assertFalse(r.eggs.kirakira)
    }
}
