package earth.cora.picnic

import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import earth.cora.picnic.ui.PicnicAppRoot
import earth.cora.picnic.ui.theme.LocalIconImages
import earth.cora.picnic.ui.theme.PicnicTheme

/**
 * Single activity (app-shell). Edge-to-edge (enforced at targetSdk 36); predictive back via
 * android:enableOnBackInvokedCallback + Compose BackHandler only (onBackPressed is never called).
 * Theme and motion follow the system (no in-app theme setting, amendment A1). Orientation is never locked.
 */
class MainActivity : ComponentActivity() {
    /** System "Remove animations" (animator duration scale 0). Re-read on resume: it can change while we're away. */
    private val reducedMotion = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Transparent bars whose icons follow the system night mode (dark icons on the noon ground,
        // light icons at dusk). The 3-button nav bar keeps the platform's contrast scrim. A uiMode
        // change recreates the activity, so this runs again with the new mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        reducedMotion.value = systemReducedMotion()
        val container = (application as PicnicApp).container
        // Once per process, in the background: parse the icon catalog, then resume the icon cascade
        // (PENDING names from an offline or token-less session), sweep captures, watch for a token.
        // Not tied to savedInstanceState: a process killed in the background restarts with one.
        container.startOnce()
        // The splash stays up for the few frames the catalog takes; nothing composes before it is
        // ready, so no ViewModel factory parses it on the main thread.
        splash.setKeepOnScreenCondition { !container.ready.value }
        setContent {
            val ready by container.ready.collectAsState()
            val dark = isSystemInDarkTheme()
            PicnicTheme(darkTheme = dark, reducedMotion = reducedMotion.value) {
                if (ready) {
                    CompositionLocalProvider(LocalIconImages provides container.iconImages) {
                        PicnicAppRoot(container)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reducedMotion.value = systemReducedMotion()
    }

    private fun systemReducedMotion(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
