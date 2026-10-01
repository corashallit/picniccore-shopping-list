package earth.cora.picnic.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI glyphs (not item icons). The app does not depend on material-icons: every glyph is drawn by
 * the theme layer. CONTRACT: screens use these names; the art pass draws them (vector paths).
 * [placeholder] is only for the skeleton rendering.
 */
enum class PicnicGlyph(val placeholder: String) {
    Add("+"), Close("×"), Check("✓"), Back("←"), More("⋯"),
    Camera("◉"), Gallery("▣"), Mic("●"), Stop("■"), Send("➤"),
    Settings("⚙"), Aisles("≡"), Lists("☰"), Edit("✎"), Delete("⌫"),
    Undo("↶"), Refresh("↻"), Sparkle("✦"), Expand("▾"), Collapse("▴"),
    Link("↗"), Drag("⋮"), Key("⚿"),
}

/** Draws a UI glyph at [size] (default 24 dp) in [tint] (default ink). Decorative when [contentDescription] is null. */
@Composable
fun Glyph(
    glyph: PicnicGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = PicnicTheme.colors.ink,
    size: Dp = 24.dp,
) {
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Box(modifier.then(semantics).size(size), contentAlignment = Alignment.Center) {
        Text(glyph.placeholder, color = tint, fontSize = (size.value * 0.75f).sp)
    }
}
