package earth.cora.picnic.ui.eggs

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.ui.album.AlbumActions
import earth.cora.picnic.ui.album.AlbumSamples
import earth.cora.picnic.ui.album.AlbumScreen
import earth.cora.picnic.ui.album.BACK_POCKET_TAG
import earth.cora.picnic.ui.nav.AlbumTarget
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 253 (egg 14's back pocket, §12.2: "→ the album opens to the back pocket with a page curl revealing 'sticker sheet
 * 2 of 2'"): opened at the back pocket, the album goes past its pocket page to it, the corner curls open and the sheet's
 * stickers appear, all on screen; golden `album_back_pocket_reveal_{day,glow}` at the end of the reveal. (The toast's
 * Look on her list: WholeTripInTheAppTest.theBackPocketsLookOpensTheAlbumThere.)
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class BackPocketRevealTest(private val variant: ThemeVariant) {
    @get:Rule
    val compose = createComposeRule()

    private fun frame() {
        Snapshot.sendApplyNotifications()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        Snapshot.sendApplyNotifications()
        compose.waitForIdle()
    }

    @Test
    fun opensAtTheBackPocketAndRevealsSheetTwo() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PicnicTheme(variant = variant) {
                AlbumScreen(AlbumSamples.state(AlbumSamples.backPocket), AlbumActions(), openAt = AlbumTarget.BackPocket)
            }
        }
        // The scroll and the reveal: bounded (2.5 s of frames).
        repeat(150) { frame() }
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag(BACK_POCKET_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("the back pocket on screen ($card in $screen)", card.top >= screen.top && card.bottom <= screen.bottom)
        compose.onNodeWithText("sticker sheet 2 of 2").assertIsDisplayed()
        compose.onRoot().captureRoboImage("$EGG_SHOTS/album_back_pocket_reveal_${if (variant == ThemeVariant.Glow) "glow" else "day"}.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = dayAndGlow()
    }
}
