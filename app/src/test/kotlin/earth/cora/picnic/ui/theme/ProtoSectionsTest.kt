package earth.cora.picnic.ui.theme

// TEMPORARY (ui-feedback prototypes): renders ProtoSections variants to the scratchpad. Delete before finishing.

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val OUT = "/tmp/claude-0/-home-user-picniccore-shopping-list/0abd3c0d-8466-5393-83a4-67568bc936fe/scratchpad/ui-feedback"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class ProtoSectionsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { PicnicTheme(darkTheme = dark) { content() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$OUT/$name.png")
    }

    @Test fun bandLight() = shot("grouping_band_light", false) { ProtoList(ProtoGrouping.BandRules, ProtoCheck.Circle) }
    @Test fun bandDark() = shot("grouping_band_dark", true) { ProtoList(ProtoGrouping.BandRules, ProtoCheck.Circle) }
    @Test fun trayLight() = shot("grouping_tray_light", false) { ProtoList(ProtoGrouping.Tray, ProtoCheck.Square) }
    @Test fun trayDark() = shot("grouping_tray_dark", true) { ProtoList(ProtoGrouping.Tray, ProtoCheck.Square) }
    @Test fun spineLight() = shot("grouping_spine_light", false) { ProtoList(ProtoGrouping.Spine, ProtoCheck.Star) }
    @Test fun spineDark() = shot("grouping_spine_dark", true) { ProtoList(ProtoGrouping.Spine, ProtoCheck.Star) }
    @Test fun buttonsLight() = shot("buttons_light", false) { ProtoButtons() }
    @Test fun buttonsDark() = shot("buttons_dark", true) { ProtoButtons() }
}
