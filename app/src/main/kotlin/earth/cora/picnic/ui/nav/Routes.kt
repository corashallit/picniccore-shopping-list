package earth.cora.picnic.ui.nav

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import kotlinx.serialization.Serializable

/*
 * Type-safe navigation destinations (navigation-compose 2.10, docs/ARCHITECTURE.md §10.2, docs/DESIGN_STICKER.md §4.24).
 * The Add, Edit, list-switcher, New trip and Trip book sheets are not destinations: the List screen owns them.
 * Owner: WP8 (NavTest).
 */

/** The shopping list (start destination). */
@Serializable
data object ListRoute

/** "My store": the sticker-sheet aisle editor (§6.7). */
@Serializable
data object AislesRoute

/** Settings: the OpenAI key, Stickers, Surprises, About your key (§6.8). */
@Serializable
data object SettingsRoute

/** The sticker album (§6.10). */
@Serializable
data object AlbumRoute

/** My pantry (§6.13): from the overflow menu, the recipe card's "Edit my pantry" and the Edit sheet. */
@Serializable
data object PantryRoute

/** About & licences (§6.9). */
@Serializable
data object AboutRoute

/** The Surprise lab (§6.9): a shell here, its eggs and sandbox come from WP9 ([earth.cora.picnic.ui.about.SurpriseLab]). */
@Serializable
data object SurpriseLabRoute

/**
 * Navigates only from a settled (RESUMED) destination, so a double tap on a top-bar button can't
 * push the same screen twice.
 */
fun NavController.navigateOnce(route: Any) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navigate(route)
}

/**
 * Opens one of the overflow menu's screens (My store, My pantry, Sticker album, Settings: §4.24) as a sibling of the
 * others: whatever menu screen is showing is replaced, so Back always returns to the list (Settings → More → Sticker
 * album → Back is the list, not Settings). From the list itself this is a plain push. Like [navigateOnce], only from a
 * settled destination.
 */
fun NavController.navigateFromMenu(route: Any) {
    if (currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return
    navigate(route) {
        popUpTo<ListRoute> { inclusive = false }
        launchSingleTop = true
    }
}

/**
 * Pops back from a settled destination; never pops the start destination (which would leave a
 * blank screen) when a Back tap races the system back gesture.
 */
fun NavController.popOnce() {
    val entry = currentBackStackEntry ?: return
    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED && previousBackStackEntry != null) popBackStack()
}

/** "Back to <list>" from any screen: pops everything above the list. */
fun NavController.backToList() {
    val entry = currentBackStackEntry ?: return
    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) popBackStack(ListRoute, inclusive = false)
}
