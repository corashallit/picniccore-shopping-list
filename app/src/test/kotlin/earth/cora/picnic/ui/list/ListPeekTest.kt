package earth.cora.picnic.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Development aid: renders list states to a folder for a look (not goldens). Runs only with WP5_PEEK=<dir>.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class ListPeekTest {
    @get:Rule
    val compose = createComposeRule()

    private val dir: String? = System.getenv("WP5_PEEK")?.takeIf { File(it).isDirectory }

    private fun shot(name: String, variant: ThemeVariant = ThemeVariant.Day, fontScale: Float? = null, before: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale ?: base.fontScale)) {
                PicnicTheme(variant = variant) { Box(Modifier.fillMaxSize()) { content() } }
            }
        }
        compose.waitForIdle()
        before()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().captureRoboImage(File(dir, "$name.png").path)
    }

    @Test
    fun midtrip() {
        assumeTrue(dir != null)
        shot("midtrip") { ListScreen(ListFixture.ui(), ListActions()) }
    }

    @Test
    fun midtripGlow() {
        assumeTrue(dir != null)
        shot("midtrip_glow", ThemeVariant.Glow) { ListScreen(ListFixture.ui(), ListActions()) }
    }
}
