package earth.cora.picnic.ui.album

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import earth.cora.picnic.core.store.Album
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.common.dayAndGlowScreens
import earth.cora.picnic.ui.common.goldenName
import earth.cora.picnic.ui.common.recordScreen
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sticker album goldens (checklist 165, 230, Appendix C), Day and Glow: `album_{groceries,picnic,seasonal}`,
 * `album_tripbooks`, `album_early`, `album_full`, `album_holo_glint`, `album_face_blink`, `album_back_pocket`,
 * `album_empty`, and two states Appendix C does not name yet, `album_tripbook_open` (a filed book opened read-only, §6.6)
 * and `album_pocket_lift` (§4.25); Day only: `album_fs130`, `album_fs200`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class AlbumGoldensTest(private val variant: ThemeVariant, @Suppress("unused") private val label: String) {
    @get:Rule
    val compose = createComposeRule()

    private val day get() = variant == ThemeVariant.Day

    private fun record(
        name: String,
        album: Album,
        tab: AlbumTab = AlbumTab.Groceries,
        exact: Boolean = false,
        fontScale: Float? = null,
        settleMillis: Long = 800,
        frozen: Boolean = false,
        lifted: String? = null,
        book: String? = null,
        blink: String? = null,
        before: ComposeContentTestRule.() -> Unit = {},
    ) = compose.recordScreen(
        if (exact) name else goldenName(name, variant),
        variant,
        fontScale = fontScale,
        settleMillis = settleMillis,
        frozen = frozen,
        before = before,
    ) {
        AlbumScreen(AlbumSamples.state(album, tab), AlbumActions(), initialLifted = lifted, initialBook = book, initialBlink = blink)
    }

    @Test
    fun groceries() = record("album_groceries", AlbumSamples.mockup)

    @Test
    fun picnic() = record("album_picnic", AlbumSamples.full, AlbumTab.Picnic)

    @Test
    fun seasonal() = record("album_seasonal", AlbumSamples.full, AlbumTab.Seasonal)

    @Test
    fun tripBooks() = record("album_tripbooks", AlbumSamples.mockup, before = { onNodeWithTag(AlbumTags.LIST).performScrollToKey("books") })

    @Test
    fun tripBookOpen() {
        record("album_tripbook_open", AlbumSamples.mockup, book = AlbumSamples.perfectBook.id)
    }

    @Test
    fun early() {
        record("album_early", AlbumSamples.early)
    }

    @Test
    fun full() {
        record("album_full", AlbumSamples.full, before = { onNodeWithTag(AlbumTags.LIST).performScrollToIndex(14) })
    }

    @Test
    fun holoGlint() {
        // 600 ms into the 3.2 s cycle: the glint is half-way across the maple-syrup and croissant pockets.
        record("album_holo_glint", AlbumSamples.mockup, frozen = true, settleMillis = 600)
    }

    @Test
    fun pocketLift() {
        record("album_pocket_lift", AlbumSamples.mockup, frozen = true, lifted = "apple")
    }

    @Test
    fun faceBlink() {
        // The blink's second frame (eyes shut), 90–180 ms after the long-press.
        record("album_face_blink", AlbumSamples.mockup, frozen = true, settleMillis = 120, blink = "apple")
    }

    @Test
    fun backPocket() = record("album_back_pocket", AlbumSamples.backPocket, AlbumTab.Seasonal, before = {
        onNodeWithTag(AlbumTags.LIST).performScrollToIndex(9)
    })

    @Test
    fun empty() {
        record("album_empty", Album())
    }

    @Test
    fun fontScale130() {
        if (day) record("album_fs130", AlbumSamples.mockup, exact = true, fontScale = 1.3f)
    }

    @Test
    fun fontScale200() {
        if (day) record("album_fs200", AlbumSamples.mockup, exact = true, fontScale = 2f)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlowScreens()
    }
}
