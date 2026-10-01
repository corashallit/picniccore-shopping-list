package earth.cora.picnic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import earth.cora.picnic.AppContainer
import earth.cora.picnic.ui.theme.EmptyState
import earth.cora.picnic.ui.theme.PicnicBackground

/**
 * Entry point of the UI (owned by app-screens): navigation host (List, Aisles, Settings) + Add sheet.
 * Called by MainActivity inside PicnicTheme with LocalIconImages provided.
 * Skeleton placeholder until app-screens lands.
 */
@Composable
fun PicnicAppRoot(container: AppContainer) {
    PicnicBackground(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            EmptyState(title = "Picnic List", body = "The basket is being packed. Screens arrive soon.")
        }
    }
}
