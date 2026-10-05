package earth.cora.picnic.ui.theme

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The window-aligned ground and its cached tiles (checklist 9, 306). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class GroundTest {
    @get:Rule
    val compose = createComposeRule()

    private val screen by lazy { SwappableContent(compose) }

    private fun render(variant: ThemeVariant, backed: Boolean) {
        screen.show {
            PicnicTheme(variant) {
                PicnicBackground(Modifier.fillMaxSize()) {
                    if (backed) {
                        // A pinned piece at an odd offset repaints the ground behind itself, window-aligned.
                        Box(Modifier.offset(23.dp, 37.dp).size(173.dp, 53.dp).groundBacking())
                        Box(Modifier.offset(7.dp, 411.dp).size(301.dp, 41.dp).binderGround(PicnicTheme.colors))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun pinstripeAlignedToWindow() {
        for (variant in ThemeVariant.entries) {
            render(variant, backed = false)
            val plain = compose.onRoot().captureToImage().toPixelMap()
            render(variant, backed = true)
            val backed = compose.onRoot().captureToImage().toPixelMap()
            assertEquals(plain.width, backed.width)
            // Glow stars and Dusk sparkles are gutter texture a pinned piece may cover; compare the ground inside them.
            val gutter = if (variant == ThemeVariant.Glow || variant == ThemeVariant.Dusk) 12 * 3 else 0
            var diffs = 0
            for (y in 0 until plain.height step 3) for (x in gutter until plain.width - gutter) if (plain[x, y] != backed[x, y]) diffs++
            assertEquals("$variant: the backing repaints the ground pixel for pixel", 0, diffs)
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ShaderCacheTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tilesAreCachedNotRedrawn() {
        var tick by mutableIntStateOf(0)
        compose.setContent {
            PicnicTheme(ThemeVariant.Day) {
                PicnicBackground(Modifier.fillMaxSize()) {
                    Column {
                        // Read the tick so this recomposes and redraws every time.
                        Box(Modifier.size((10 + tick % 3).dp))
                        TabPatternsKit()
                    }
                }
            }
        }
        compose.waitForIdle()
        // Draw once first: the ground's pinstripe is rasterised on the first draw, which Robolectric only runs on a
        // capture. Without this the count depends on whether an earlier class in the same JVM cached that tile.
        compose.onRoot().captureToImage()
        val after = PatternCache.tileCreations.get()
        repeat(12) {
            tick++
            compose.waitForIdle()
        }
        compose.onRoot().captureToImage()
        assertEquals("no tile is rasterised again on recomposition or redraw", after, PatternCache.tileCreations.get())
    }
}

/** Reduced motion is read from both scales at start and on resume (checklist 195). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReducedMotionTest {
    /**
     * The activity below is launched by the test itself; this rule gives its compositions the test recomposer. Without
     * it, the list's ambient loops (sparkles, LCD blink, bee) run as ordinary frames and Robolectric's looper idle in
     * moveToState never ends; with it they wait, as in every other Compose test.
     */
    @get:Rule
    val compose = createEmptyComposeRule()

    private val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver

    private fun scales(animator: Float, transition: Float) {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, animator)
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, transition)
    }

    @Test
    fun detection() {
        scales(1f, 1f)
        assertFalse(readReducedMotion(resolver))
        scales(0f, 1f)
        assertTrue(readReducedMotion(resolver))
        scales(1f, 0f)
        assertTrue(readReducedMotion(resolver))
        scales(0.5f, 2f)
        assertFalse(readReducedMotion(resolver))
    }

    @Test
    fun mainActivityReadsAtStartAndOnResume() {
        scales(1f, 0f)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertTrue("transition scale 0 at start", it.reducedMotion.value) }
            scales(1f, 1f)
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertFalse("re-read on resume", it.reducedMotion.value) }
        }
        scales(1f, 1f)
    }
}

/** The system bar icon styles follow the resolved variant, and the celebration forces Day bars (checklist 17). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityBarsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun lightIcons(): Boolean {
        val window = compose.activity.window
        return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
    }

    private fun lightNavIcons(): Boolean {
        val window = compose.activity.window
        return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars
    }

    @Test
    fun forcedGlowOnALightSystem() {
        compose.setContent { SystemBarsHost(ThemeVariant.Glow) { } }
        compose.waitForIdle()
        assertFalse("Glow: light icons (dark-appearance bars)", lightIcons())
        assertFalse(lightNavIcons())
    }

    @Test
    @Config(qualifiers = "night")
    fun duskOnADarkSystem() {
        compose.setContent { SystemBarsHost(ThemeVariant.Dusk) { } }
        compose.waitForIdle()
        assertTrue("Dusk: dark icons even on a dark system", lightIcons())
        assertTrue(lightNavIcons())
    }

    @Test
    fun celebrationOverGlowForcesDayBars() {
        var celebrating by mutableStateOf(true)
        compose.setContent { SystemBarsHost(ThemeVariant.Glow) { if (celebrating) ForceDayBars() } }
        compose.waitForIdle()
        assertTrue("dark icons while the celebration shows", lightIcons())
        celebrating = false
        compose.waitForIdle()
        assertFalse("back to the variant's style when it closes", lightIcons())
    }
}
