package earth.cora.picnic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import earth.cora.picnic.ui.PicnicAppRoot
import earth.cora.picnic.ui.theme.LocalIconImages
import earth.cora.picnic.ui.theme.PicnicTheme

/**
 * Single activity (app-shell). Edge-to-edge (enforced at targetSdk 36), predictive back via
 * android:enableOnBackInvokedCallback + Compose BackHandler only. Skeleton: app-shell wires
 * ThemeMode/reduceMotion from SettingsRepository and the splash screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PicnicApp).container
        setContent {
            PicnicTheme {
                CompositionLocalProvider(LocalIconImages provides container.iconImages) {
                    PicnicAppRoot(container)
                }
            }
        }
    }
}
