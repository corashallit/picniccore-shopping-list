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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import earth.cora.picnic.AppContainer
import earth.cora.picnic.BuildConfig
import earth.cora.picnic.ui.add.AddViewModel
import earth.cora.picnic.ui.aisles.AislesViewModel
import earth.cora.picnic.ui.list.ListViewModel
import earth.cora.picnic.ui.nav.AislesRoute
import earth.cora.picnic.ui.nav.ListRoute
import earth.cora.picnic.ui.nav.SettingsRoute
import earth.cora.picnic.ui.nav.navigateOnce
import earth.cora.picnic.ui.nav.popOnce
import earth.cora.picnic.ui.settings.SettingsViewModel
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.aisles.AislesRoute as AislesDestination
import earth.cora.picnic.ui.list.ListRoute as ListDestination
import earth.cora.picnic.ui.settings.SettingsRoute as SettingsDestination

/**
 * Entry point of the UI (app-screens), called by MainActivity inside PicnicTheme with
 * LocalIconImages provided. A type-safe NavHost: List (start) -> Aisles / Settings. The Add and
 * Edit sheets belong to the List destination. Back is navigation's own (predictive back included).
 */
@Composable
fun PicnicAppRoot(container: AppContainer) {
    val nav = rememberNavController()
    val motion = PicnicTheme.motion
    val reduced = motion.reduced
    val short = motion.shortMillis
    val medium = motion.mediumMillis

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
                onOpenAisles = { nav.navigateOnce(AislesRoute) },
                onOpenSettings = { nav.navigateOnce(SettingsRoute) },
            )
        }
        composable<AislesRoute> {
            AislesDestination(
                vm = viewModel(factory = AislesViewModel.factory(container)),
                onBack = { nav.popOnce() },
            )
        }
        composable<SettingsRoute> {
            SettingsDestination(
                vm = viewModel(factory = SettingsViewModel.factory(container, BuildConfig.VERSION_NAME)),
                onBack = { nav.popOnce() },
            )
        }
    }
}
