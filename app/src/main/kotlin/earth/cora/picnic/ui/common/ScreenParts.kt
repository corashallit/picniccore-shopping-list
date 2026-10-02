package earth.cora.picnic.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.PicnicBackground
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicIconButton
import earth.cora.picnic.ui.theme.PicnicSnackbar
import earth.cora.picnic.ui.theme.PicnicSurface
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.SheetHandle

/*
 * Screen scaffolding shared by the List, Aisles and Settings screens and the sheets. Built only from
 * the theme layer (tokens + components); the art pass restyles these through ui/theme, not here.
 */

/**
 * A full screen: the decorative ground drawn edge to edge (under the system bars), a top bar kept
 * clear of the status bar and cutouts, and content padded by the Scaffold for the top bar and the
 * remaining safe-drawing insets (navigation bar, IME).
 */
@Composable
fun ScreenScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    PicnicBackground(modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = PicnicTheme.colors.ink,
            topBar = {
                // The ground is repainted behind the floating title card so scrolled content passes
                // under it instead of showing through the gaps around the card. Both grounds start
                // at the screen origin, so the pattern lines up.
                PicnicBackground(Modifier.fillMaxWidth()) {
                    Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                        topBar()
                    }
                }
            },
            snackbarHost = {
                if (snackbarHostState != null) SnackbarHost(snackbarHostState) { PicnicSnackbar(it) }
            },
            floatingActionButton = floatingActionButton,
            contentWindowInsets = WindowInsets.safeDrawing,
            content = content,
        )
    }
}

/** A themed modal bottom sheet: raised surface, the ribbon handle, the theme scrim, never tonal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PicnicSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = PicnicTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = PicnicTheme.shapes.sheet,
        containerColor = c.surfaceRaised,
        contentColor = c.ink,
        tonalElevation = 0.dp,
        scrimColor = c.scrim,
        dragHandle = { SheetHandle() },
        content = content,
    )
}

/** Title row of a sheet: screen-title type, announced as a heading, with optional trailing actions. */
@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val d = PicnicTheme.dimens
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = d.gutter, end = d.s, bottom = d.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = PicnicTheme.type.screenTitle,
            color = PicnicTheme.colors.ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing()
    }
}

/** A titled card on a screen (Settings, Aisles). Leaves room below for the card's hard shadow. */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    glyph: PicnicGlyph? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val d = PicnicTheme.dimens
    PicnicSurface(modifier.fillMaxWidth().padding(bottom = d.hardShadow.y)) {
        Column(Modifier.padding(d.l), verticalArrangement = Arrangement.spacedBy(d.m)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.s)) {
                    if (glyph != null) Glyph(glyph, contentDescription = null, tint = PicnicTheme.colors.primary)
                    Text(
                        title,
                        style = PicnicTheme.type.aisleTitle,
                        color = PicnicTheme.colors.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            content()
        }
    }
}

/** The top bar's back button. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PicnicIconButton(PicnicGlyph.Back, stringResource(R.string.screens_back), onClick, modifier)
}

/** Small explanatory text under a control. */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = PicnicTheme.type.caption, color = PicnicTheme.colors.inkSoft, modifier = modifier)
}
