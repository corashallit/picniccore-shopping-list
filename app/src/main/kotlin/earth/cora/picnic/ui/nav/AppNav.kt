package earth.cora.picnic.ui.nav

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Every way to reach a screen (docs/DESIGN_STICKER.md §4.24, §6.13), provided by PicnicAppRoot around the NavHost so any
 * screen, sheet or menu can navigate without threading lambdas through other packages' signatures:
 * - the list's overflow menu (WP5): [openMyStore], [openPantry], [openAlbum], [openSettings];
 * - the recipe card's "Edit my pantry" (WP7): [openPantry] (Back returns to the same card, keep switches as she left them:
 *   the Add sheet's state lives in the List destination, which stays on the back stack);
 * - the Album and Settings menus: [backToList] ("Back to <list>"), [openMyStore], [openPantry], [openSettings] /
 *   [openAlbum]. The four menu screens are siblings: opening one from another replaces it, so Back is the list;
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
    /** The album at [AlbumTarget]: egg 14's "Look" opens it at the back pocket (§12.2). */
    val openAlbumAt: (AlbumTarget) -> Unit = { openAlbum() },
) {
    /** The recipe card's "Edit my pantry" (§4.17): the same route as the overflow menu's "My pantry". */
    val onEditPantry: () -> Unit get() = openPantry

    companion object {
        val None: AppNav = AppNav()
    }
}

/** Where the album opens: at its top (the menus), or at the back pocket (egg 14's toast, §12.2). */
enum class AlbumTarget { Top, BackPocket }

/** The app's navigation (see [AppNav]); [AppNav.None] outside PicnicAppRoot. */
val LocalAppNav = staticCompositionLocalOf { AppNav.None }
