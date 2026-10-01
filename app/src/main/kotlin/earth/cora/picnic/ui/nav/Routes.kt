package earth.cora.picnic.ui.nav

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import kotlinx.serialization.Serializable

/*
 * Type-safe navigation destinations (navigation-compose 2.10, docs/ARCHITECTURE.md §10.2).
 * The Add and Edit sheets are not destinations: the List screen owns them.
 */

/** The shopping list (start destination). */
@Serializable
data object ListRoute

/** Store name + the free-text aisle list. */
@Serializable
data object AislesRoute

/** Connection (base URL + token) and "Draw new icons with AI". */
@Serializable
data object SettingsRoute

/**
 * Navigates only from a settled (RESUMED) destination, so a double tap on a top-bar button can't
 * push the same screen twice.
 */
fun NavController.navigateOnce(route: Any) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navigate(route)
}

/**
 * Pops back from a settled destination; never pops the start destination (which would leave a
 * blank screen) when a Back tap races the system back gesture.
 */
fun NavController.popOnce() {
    val entry = currentBackStackEntry ?: return
    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED && previousBackStackEntry != null) popBackStack()
}
