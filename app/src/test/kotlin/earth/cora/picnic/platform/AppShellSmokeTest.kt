package earth.cora.picnic.platform

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.MainActivity
import earth.cora.picnic.PicnicApp
import earth.cora.picnic.core.llm.LlmConfig
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.icons.AppStickerArt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The app shell wires up on a real (Robolectric) Android: splash theme, edge-to-edge, the container's
 * lazy graph (DataStore settings, Keystore token store, state store, icon cascade, sticker art) and
 * MainActivity's resumePending(). AndroidKeyStore is absent here; with no token stored it is never touched.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppShellSmokeTest {
    /**
     * The activity below is launched by the test itself; this rule gives its compositions the test recomposer. Without
     * it, the list's ambient loops (the add bar's washi flutter, the LCD, the sparkles) run as ordinary frames, and
     * Robolectric's looper idle in moveToState never ends (as in ReducedMotionTest).
     */
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun mainActivityLaunchesAndContainerResolves() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val container = (activity.application as PicnicApp).container
                assertTrue(container.stickerArt is AppStickerArt)
                assertFalse(container.icons.isBundled("definitely_not_a_key"))
                assertTrue(container.icons.busy.value.isEmpty())
            }
        }
    }

    /**
     * startOnce keeps album.json in step with state.json (TripFiling.launchAlbumSync, §7.2, checklist 138 and 232): a
     * check-off on the real stores is collected without any screen asking for it.
     */
    @Test
    fun startOnceCollectsCheckOffsIntoTheAlbum() = runBlocking {
        val container = ApplicationProvider.getApplicationContext<PicnicApp>().container
        container.startOnce()
        withTimeout(TIMEOUT_MS) { container.ready.first { it } }
        val now = container.clock()
        val listId = container.store.current().currentListId
        container.store.update { s ->
            val milk = GroceryItem(id = "i-milk", name = "Milk", icon = "milk_carton", addedAt = now)
            s.addItems(listId, listOf(milk), now).first.toggleChecked(listId, "i-milk", now)
        }
        val album = withTimeout(TIMEOUT_MS) { container.album.album.first { it.collected.isNotEmpty() } }
        assertEquals(setOf("milk_carton"), album.collected.keys)
        assertEquals(1, album.collected.getValue("milk_carton").count)
    }

    @Test
    fun settingsDefaultsWithoutAToken() = runBlocking {
        val container = ApplicationProvider.getApplicationContext<PicnicApp>().container
        val s = container.settings.current()
        assertEquals(LlmConfig.DEFAULT_BASE_URL, container.configSource.config().baseUrl)
        assertTrue(s.drawIcons)
        assertFalse(s.hasToken)
        assertEquals(null, container.tokenSource.token())
        assertTrue(container.configSource.config().safetyIdentifier!!.matches(Regex("[0-9a-f]{32}")))
        assertTrue(container.store.current().lists.isNotEmpty())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
