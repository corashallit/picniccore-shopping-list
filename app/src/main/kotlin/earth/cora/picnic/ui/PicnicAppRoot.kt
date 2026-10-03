package earth.cora.picnic.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import earth.cora.picnic.AppContainer
import earth.cora.picnic.BuildConfig
import earth.cora.picnic.ui.about.AboutViewModel
import earth.cora.picnic.ui.about.LocalSurpriseLab
import earth.cora.picnic.ui.about.SurpriseLabScreen
import earth.cora.picnic.ui.add.AddViewModel
import earth.cora.picnic.ui.aisles.AislesViewModel
import earth.cora.picnic.ui.album.AlbumViewModel
import earth.cora.picnic.ui.list.ListViewModel
import earth.cora.picnic.ui.nav.AboutRoute
import earth.cora.picnic.ui.nav.AislesRoute
import earth.cora.picnic.ui.nav.AlbumRoute
import earth.cora.picnic.ui.nav.AppNav
import earth.cora.picnic.ui.nav.ListRoute
import earth.cora.picnic.ui.nav.LocalAppNav
import earth.cora.picnic.ui.nav.PantryRoute
import earth.cora.picnic.ui.nav.SettingsRoute
import earth.cora.picnic.ui.nav.SurpriseLabRoute
import earth.cora.picnic.ui.nav.backToList
import earth.cora.picnic.ui.nav.navigateFromMenu
import earth.cora.picnic.ui.nav.navigateOnce
import earth.cora.picnic.ui.nav.popOnce
import earth.cora.picnic.ui.pantry.PantryViewModel
import earth.cora.picnic.ui.settings.SettingsViewModel
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.about.AboutRoute as AboutDestination
import earth.cora.picnic.ui.aisles.AislesRoute as AislesDestination
import earth.cora.picnic.ui.album.AlbumRoute as AlbumDestination
import earth.cora.picnic.ui.list.ListRoute as ListDestination
import earth.cora.picnic.ui.pantry.PantryRoute as PantryDestination
import earth.cora.picnic.ui.settings.SettingsRoute as SettingsDestination

/**
 * Entry point of the UI (app-screens), called by MainActivity inside PicnicTheme with LocalStickerArt provided. A
 * type-safe NavHost: List (start) → My store, My pantry, Sticker album, Settings (siblings: the Settings and Album menus
 * swap one for another, so Back from any of them is the list) → About & licences → Surprise lab. The
 * Add, Edit, list-switcher, New trip and Trip book sheets belong to the List destination. Back is navigation's own
 * (predictive back included). [LocalAppNav] carries the routes to every screen, sheet and menu (the list's overflow menu,
 * the recipe card's "Edit my pantry", the Album and Settings menus). Owner: WP8 (NavTest).
 */
@Composable
fun PicnicAppRoot(
    container: AppContainer,
    nav: NavHostController = rememberNavController(),
    /** Test hook (NavTest): the routes the screens and menus use. */
    onAppNav: (AppNav) -> Unit = {},
) {
    val motion = PicnicTheme.motion
    val reduced = motion.reduced
    val short = motion.shortMillis
    val medium = motion.mediumMillis
    val context = LocalContext.current

    // Forward: the new screen slides in a little from the end over a fade; back: the reverse.
    // Reduced motion: short cross-fades only.
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduced) fadeIn(tween(short)) else slideInHorizontally(tween(medium)) { it / 5 } + fadeIn(tween(medium))
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { fadeOut(tween(short)) }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { fadeIn(tween(short)) }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduced) fadeOut(tween(short)) else slideOutHorizontally(tween(medium)) { it / 5 } + fadeOut(tween(medium))
    }

    val appNav = remember(nav) {
        AppNav(
            // The overflow menu's four screens are siblings: Back from any of them is the list.
            openMyStore = { nav.navigateFromMenu(AislesRoute) },
            openPantry = { nav.navigateFromMenu(PantryRoute) },
            openAlbum = { nav.navigateFromMenu(AlbumRoute) },
            openSettings = { nav.navigateFromMenu(SettingsRoute) },
            openAbout = { nav.navigateOnce(AboutRoute) },
            openSurpriseLab = { nav.navigateOnce(SurpriseLabRoute) },
            back = { nav.popOnce() },
            backToList = { nav.backToList() },
        )
    }

    SideEffect { onAppNav(appNav) }

    CompositionLocalProvider(LocalAppNav provides appNav) {
        NavHost(
            navController = nav,
            startDestination = ListRoute,
            enterTransition = enter,
            exitTransition = exit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            composable<ListRoute> {
                ListDestination(
                    vm = viewModel(factory = ListViewModel.factory(container)),
                    addVm = viewModel(factory = AddViewModel.factory(container)),
                    onOpenAisles = appNav.openMyStore,
                    onOpenSettings = appNav.openSettings,
                )
            }
            composable<AislesRoute> {
                AislesDestination(
                    vm = viewModel(factory = AislesViewModel.factory(container)),
                    onBack = appNav.back,
                )
            }
            composable<PantryRoute> {
                PantryDestination(
                    vm = viewModel(factory = PantryViewModel.factory(container)),
                    onBack = appNav.back,
                )
            }
            composable<AlbumRoute> {
                AlbumDestination(
                    vm = viewModel(factory = AlbumViewModel.factory(container)),
                    onBack = appNav.back,
                    onBackToList = appNav.backToList,
                    onOpenMyStore = appNav.openMyStore,
                    onOpenPantry = appNav.openPantry,
                    onOpenSettings = appNav.openSettings,
                )
            }
            composable<SettingsRoute> {
                SettingsDestination(
                    vm = viewModel(factory = SettingsViewModel.factory(container, BuildConfig.VERSION_NAME)),
                    onBack = appNav.back,
                    onOpenAbout = appNav.openAbout,
                    onBackToList = appNav.backToList,
                    onOpenMyStore = appNav.openMyStore,
                    onOpenPantry = appNav.openPantry,
                    onOpenAlbum = appNav.openAlbum,
                )
            }
            composable<AboutRoute> {
                AboutDestination(
                    vm = viewModel(factory = AboutViewModel.factory(container, context, BuildConfig.VERSION_NAME, BuildConfig.DEBUG)),
                    onBack = appNav.back,
                    onOpenLab = appNav.openSurpriseLab,
                )
            }
            composable<SurpriseLabRoute> {
                // WP9's lab: the sandbox lives in this destination's ViewModel (a rotation keeps it; leaving drops it).
                val lab = viewModel { container.surpriseLab() }
                CompositionLocalProvider(LocalSurpriseLab provides lab) {
                    SurpriseLabScreen(onBack = appNav.back)
                }
            }
        }
    }
}
