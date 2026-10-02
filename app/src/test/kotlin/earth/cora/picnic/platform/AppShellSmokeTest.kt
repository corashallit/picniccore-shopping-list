package earth.cora.picnic.platform

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.MainActivity
import earth.cora.picnic.PicnicApp
import earth.cora.picnic.core.llm.LlmConfig
import earth.cora.picnic.icons.AssetIconImages
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The app shell wires up on a real (Robolectric) Android: splash theme, edge-to-edge, the container's
 * lazy graph (DataStore settings, Keystore token store, state store, icon cascade, asset images) and
 * MainActivity's resumePending(). AndroidKeyStore is absent here; with no token stored it is never touched.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppShellSmokeTest {
    @Test
    fun mainActivityLaunchesAndContainerResolves() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val container = (activity.application as PicnicApp).container
                assertTrue(container.iconImages is AssetIconImages)
                assertFalse(container.icons.isBundled("definitely_not_a_key"))
                assertTrue(container.icons.busy.value.isEmpty())
            }
        }
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
}
