package earth.cora.picnic.ui.nav

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.PicnicApp
import earth.cora.picnic.ui.PicnicAppRoot
import earth.cora.picnic.ui.theme.LocalStickerArt
import earth.cora.picnic.ui.theme.LocalTimeouts
import earth.cora.picnic.ui.theme.PicnicTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app's routes on the real container (JSON store, DataStore settings, no key, so no network) (checklist 233). The
 * Album is reached by tapping through the real screens: List → Settings → More options → Sticker album (§4.24: the
 * Settings and Album menus). The list's own overflow menu (My pantry, Sticker album) belongs to WP5's List screen; until
 * it lands, the routes it will call are exercised through the same [AppNav] it will use.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class NavTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private var appNav: AppNav? = null

    private fun launch() {
        val app = ApplicationProvider.getApplicationContext<PicnicApp>()
        val container = app.container
        container.startOnce()
        compose.setContent {
            val ready by container.ready.collectAsState()
            PicnicTheme {
                if (ready) {
                    CompositionLocalProvider(LocalStickerArt provides container.stickerArt, LocalTimeouts provides container.timeouts) {
                        nav = rememberNavController()
                        PicnicAppRoot(container, nav, onAppNav = { appNav = it })
                    }
                }
            }
        }
        compose.waitUntil(10_000) { appNav != null }
        compose.waitForIdle()
    }

    private fun waitForText(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun atList(): Boolean = nav.currentDestination?.hasRoute<ListRoute>() == true

    private fun at(route: kotlin.reflect.KClass<*>): Boolean = when (route) {
        AlbumRoute::class -> nav.currentDestination?.hasRoute<AlbumRoute>() == true
        SettingsRoute::class -> nav.currentDestination?.hasRoute<SettingsRoute>() == true
        else -> false
    }

    /** Taps "More options", then the menu item [item]. */
    private fun menu(item: String) {
        compose.waitForIdle() // the screen change has finished: one More button on screen
        compose.onNodeWithContentDescription("More options").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(item, substring = true).performClick()
    }

    @Test
    fun albumRoute() {
        launch()
        assertTrue(atList())
        // List → Settings (its top-bar button) → More options → Sticker album, all by taps.
        compose.onNodeWithContentDescription("Settings").performClick()
        waitForText("Your OpenAI key")
        menu("Sticker album")
        compose.waitUntil(5_000) { at(AlbumRoute::class) }
        waitForText("Sticker album")
        // Back returns to the screen she came from.
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(5_000) { at(SettingsRoute::class) }
        // And the album's own menu goes straight back to the list.
        menu("Sticker album")
        compose.waitUntil(5_000) { at(AlbumRoute::class) }
        menu("Back to")
        compose.waitUntil(5_000) { atList() }
        assertTrue(nav.previousBackStackEntry == null)
    }

    /** The route the list's overflow menu "Sticker album" (WP5) will call: Back returns to the list. */
    @Test
    fun albumFromTheListMenuRoute() {
        launch()
        compose.runOnIdle { appNav!!.openAlbum() }
        compose.waitUntil(5_000) { at(AlbumRoute::class) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(5_000) { atList() }
    }

    @Test
    fun pantryRouteFromTheMenuAndTheRecipeCard() {
        launch()
        compose.runOnIdle { appNav!!.openPantry() }
        waitForText("My pantry")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(5_000) { atList() }
        // The recipe card's "Edit my pantry" is the same route.
        compose.runOnIdle { appNav!!.onEditPantry() }
        waitForText("My pantry")
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(5_000) { atList() }
    }

    @Test
    fun settingsAboutLabAndBackToList() {
        launch()
        compose.runOnIdle { appNav!!.openSettings() }
        waitForText("Your OpenAI key")
        compose.runOnIdle { appNav!!.openAbout() }
        waitForText("About & licences")
        compose.runOnIdle { appNav!!.openSurpriseLab() }
        waitForText("Surprise lab")
        // "Back to <list>" from any depth pops to the list.
        compose.runOnIdle { appNav!!.backToList() }
        compose.waitUntil(5_000) { atList() }
    }

    @Test
    fun myStoreRoute() {
        launch()
        compose.runOnIdle { appNav!!.openMyStore() }
        waitForText("My store")
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(5_000) { atList() }
    }
}
