package earth.cora.picnic.ui.pantry

import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.IconDecider
import earth.cora.picnic.core.assistant.IconDecision
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.IconOverride
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.settings.InMemorySettingsRepository
import earth.cora.picnic.ui.common.MainDispatcherRule
import earth.cora.picnic.ui.common.Samples
import earth.cora.picnic.ui.common.testAppScope
import earth.cora.picnic.ui.theme.gen.StickerManifest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pantry stickers (§6.13, checklist 328): override → local alias → IconDecider (the key set, ONE batched call per
 * commit) → category generic or capsule; never a live drawing; no network call without a key.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PantryIconsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalog = IconCatalog.default

    /** A fake decider that counts its calls (each call is one request, it batches internally). */
    private class CountingDecider(var answer: (List<NamedItem>) -> Map<String, IconDecision> = { emptyMap() }) : IconDecider {
        val calls = mutableListOf<List<NamedItem>>()
        override suspend fun decide(items: List<NamedItem>): Map<String, IconDecision> {
            calls += items
            return answer(items)
        }
    }

    private data class Setup(val vm: PantryViewModel, val store: InMemoryStateStore)

    private fun TestScope.setup(state: AppState = Samples.emptyState, token: String? = null, decider: IconDecider = CountingDecider()): Setup {
        val store = InMemoryStateStore(state)
        val vm = PantryViewModel(store, InMemorySettingsRepository(storedToken = token), decider, catalog, { Samples.NOW }, testAppScope(), previewDelayMillis = 0)
        return Setup(vm, store)
    }

    private fun TestScope.add(vm: PantryViewModel, text: String) {
        vm.setAddInput(text)
        vm.add()
        advanceUntilIdle()
    }

    private suspend fun InMemoryStateStore.icon(name: String): IconRef? = current().pantry.first { it.name == name }.icon

    @Test
    fun learnedOverrideWinsOverTheLocalMatch() = runTest {
        val state = Samples.emptyState.copy(iconOverrides = listOf(IconOverride(name = "flour", icon = "sugar", at = Samples.NOW)))
        val decider = CountingDecider()
        val (vm, store) = setup(state, token = "sk-test", decider = decider)
        add(vm, "flour, salt")
        assertEquals(IconRef("sugar"), store.icon("Flour"))
        assertEquals(IconRef("salt"), store.icon("Salt"))
        assertTrue("nothing unknown: no request", decider.calls.isEmpty())
    }

    @Test
    fun unknownNamesAskTheDeciderOnceBatched() = runTest {
        val decider = CountingDecider { items ->
            items.associate { item ->
                item.id to when (item.id) {
                    "furikake" -> IconDecision(icon = "spice_jar", category = "pantry")
                    "doenjang" -> IconDecision(icon = null, category = "condiments")
                    else -> IconDecision(icon = null, category = null)
                }
            }
        }
        val (vm, store) = setup(token = "sk-test", decider = decider)
        add(vm, "furikake, doenjang, zorblax, 2 kg rice")
        assertEquals("one request for the commit", 1, decider.calls.size)
        assertEquals(setOf("furikake", "doenjang", "zorblax"), decider.calls.single().map { it.id }.toSet())
        assertEquals(IconRef("spice_jar"), store.icon("Furikake"))
        assertEquals(IconRef(catalog.categories.first { it.key == "condiments" }.fallbackIcon), store.icon("Doenjang"))
        assertEquals(IconRef(StickerManifest.CAPSULE), store.icon("Zorblax"))
        // The quantity is dropped and the local alias needs no request.
        assertEquals(IconRef("rice"), store.icon("Rice"))
        // Nothing is ever drawn for the pantry.
        assertTrue(store.current().pantry.all { it.icon?.customIconId == null })

        // A later commit with nothing new unknown makes no request.
        add(vm, "salt")
        assertEquals(1, decider.calls.size)
    }

    @Test
    fun noKeyNoNetwork() = runTest {
        val decider = CountingDecider { error("must not be called without a key") }
        val (vm, store) = setup(token = null, decider = decider)
        add(vm, "furikake, salt")
        assertTrue(decider.calls.isEmpty())
        assertNull("waits as the capsule", store.icon("Furikake"))
        assertEquals(IconRef(StickerManifest.CAPSULE), PantryIcons.display(store.icon("Furikake")))
        assertEquals(IconRef("salt"), store.icon("Salt"))
    }

    @Test
    fun deciderErrorLeavesThemForTheNextCommit() = runTest {
        var fail = true
        val decider = CountingDecider { items ->
            if (fail) throw LlmError.Network(null)
            items.associate { it.id to IconDecision(icon = "spice_jar", category = "pantry") }
        }
        val (vm, store) = setup(token = "sk-test", decider = decider)
        add(vm, "furikake")
        assertNull(store.icon("Furikake"))
        fail = false
        add(vm, "salt")
        assertEquals(2, decider.calls.size)
        assertEquals(IconRef("spice_jar"), store.icon("Furikake"))
    }

    /**
     * The catch-up (§0.4, 5 October): a commit made offline leaves its unknown entries on the capsule (the decider fails
     * at once, nothing waits); when the connection is back, PantryIcons.decidePending gives them their stickers in one call.
     */
    @Test
    fun theCatchUpDecidesWhatACommitMadeOfflineLeft() = runTest {
        var online = false
        val decider = CountingDecider { items ->
            if (!online) throw LlmError.Offline()
            items.associate { it.id to IconDecision(icon = "spice_jar", category = "pantry") }
        }
        val (vm, store) = setup(token = "test-token", decider = decider)
        add(vm, "furikake, za'atar")
        assertNull(store.icon("Furikake"))
        online = true
        PantryIcons.decidePending(store, decider, catalog)
        assertEquals(2, decider.calls.size)
        assertEquals(2, decider.calls.last().size)
        assertEquals(IconRef("spice_jar"), store.icon("Furikake"))
        // Nothing left: no call.
        PantryIcons.decidePending(store, decider, catalog)
        assertEquals(2, decider.calls.size)
    }
}
