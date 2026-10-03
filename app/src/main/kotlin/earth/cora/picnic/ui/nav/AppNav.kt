package earth.cora.picnic.ui.nav

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Every way to reach a screen (docs/DESIGN_STICKER.md §4.24, §6.13), provided by PicnicAppRoot around the NavHost so any
 * screen, sheet or menu can navigate without threading lambdas through other packages' signatures:
 * - the list's overflow menu (WP5): [openMyStore], [openPantry], [openAlbum], [openSettings];
 * - the recipe card's "Edit my pantry" (WP7): [openPantry] (Back returns to the same card, keep switches as she left them:
 *   the Add sheet's state lives in the List destination, which stays on the back stack);
 * - the Album and Settings menus: [backToList] ("Back to <list>"), [openMyStore], [openSettings] / [openAlbum];
 * - Settings: [openAbout]; About: [openSurpriseLab].
 * Defaults do nothing (previews, screenshot tests). Owner: WP8 (NavTest).
 */
@Immutable
class AppNav(
    val openMyStore: () -> Unit = {},
    val openPantry: () -> Unit = {},
    val openAlbum: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val openAbout: () -> Unit = {},
    val openSurpriseLab: () -> Unit = {},
    val back: () -> Unit = {},
    val backToList: () -> Unit = {},
) {
    /** The recipe card's "Edit my pantry" (§4.17): the same route as the overflow menu's "My pantry". */
    val onEditPantry: () -> Unit get() = openPantry

    companion object {
        val None: AppNav = AppNav()
    }
}

/** The app's navigation (see [AppNav]); [AppNav.None] outside PicnicAppRoot. */
val LocalAppNav = staticCompositionLocalOf { AppNav.None }
