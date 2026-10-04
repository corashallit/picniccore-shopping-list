package earth.cora.picnic

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import earth.cora.picnic.ui.PicnicAppRoot
import earth.cora.picnic.ui.a11y.LocalAnnouncer
import earth.cora.picnic.ui.theme.LocalPinholeWork
import earth.cora.picnic.ui.theme.LocalStickerArt
import earth.cora.picnic.ui.theme.LocalTimeouts
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.PinholeWork
import earth.cora.picnic.ui.theme.SystemBarsHost
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.applySystemBars
import earth.cora.picnic.ui.theme.readReducedMotion

/**
 * Single activity (app-shell). Edge-to-edge (enforced at targetSdk 36); predictive back via
 * android:enableOnBackInvokedCallback + Compose BackHandler only (onBackPressed is never called).
 *
 * Theme (docs/DESIGN_STICKER.md §2.5, D12, owner WP4): the variant comes from ThemeVariantSource (WP9), fed the
 * system dark mode here; the status and navigation bar icon styles follow the RESOLVED variant (dark icons on Day, Dusk
 * and Golden, light on Glow), not the system theme, and the celebration forces Day bars while it shows (ForceDayBars).
 * Reduced motion is on when either Android animation scale is 0, read from Settings.Global at start and on every
 * resume (§8.8). Orientation is never locked.
 */
class MainActivity : ComponentActivity() {
    /** Reduced motion (§8.8). Re-read on resume: it can change while we're away. */
    internal val reducedMotion = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Transparent bars with Day icons until the first composition resolves the variant (the splash is Day too).
        applySystemBars(ThemeVariant.Day)
        reducedMotion.value = readReducedMotion(contentResolver)
        val container = (application as PicnicApp).container
        // Once per process, in the background: parse the icon catalog, then resume the icon cascade
        // (PENDING names from an offline or token-less session), sweep captures, watch for a token.
        // Not tied to savedInstanceState: a process killed in the background restarts with one.
        container.startOnce()
        // The splash stays up for the few frames the catalog takes; nothing composes before it is
        // ready, so no ViewModel factory parses it on the main thread.
        splash.setKeepOnScreenCondition { !container.ready.value }
        val variants = container.themeVariants
        // Resolve before the first frame so a dark system never flashes Day (a uiMode change recreates the activity).
        variants.onSystemDark(
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES,
        )
        setContent {
            val ready by container.ready.collectAsState()
            val dark = isSystemInDarkTheme()
            LaunchedEffect(dark) { variants.onSystemDark(dark) }
            val variant by variants.variant.collectAsState()
            SystemBarsHost(variant) {
                PicnicTheme(variant = variant, reducedMotion = reducedMotion.value) {
                    if (ready) {
                        CompositionLocalProvider(
                            LocalStickerArt provides container.stickerArt,
                            LocalTimeouts provides container.timeouts,
                            // Toasts speak through the app's Announcer (§15 Announcements).
                            LocalAnnouncer provides container.announcer,
                            // Puffy titles scan for pinholes off the main thread in the app (§2.7, item 308).
                            LocalPinholeWork provides PinholeWork.Background,
                        ) {
                            PicnicAppRoot(container)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reducedMotion.value = readReducedMotion(contentResolver)
    }
}
