package earth.cora.picnic.ui.about

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import earth.cora.picnic.R
import earth.cora.picnic.ui.common.LinkRow
import earth.cora.picnic.ui.common.accentInk
import earth.cora.picnic.ui.common.PadCard
import earth.cora.picnic.ui.common.RowRule
import earth.cora.picnic.ui.common.ScreenColumn
import earth.cora.picnic.ui.common.SecondaryScreen
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicText
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage
import earth.cora.picnic.ui.theme.Wordmark

/** Everything the About screen can ask for. */
@Stable
class AboutActions(
    val onBack: () -> Unit = {},
    val onUnlockLab: () -> Unit = {},
    val onOpenLab: () -> Unit = {},
)

@Composable
fun AboutRoute(vm: AboutViewModel, onBack: () -> Unit, onOpenLab: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val actions = remember(vm) { AboutActions(onBack = onBack, onUnlockLab = vm::unlockLab, onOpenLab = onOpenLab) }
    AboutScreen(state, actions)
}

/**
 * About & licences (§6.9): the wordmark (40 sp, read from the one app-name resource), the version line (tap it 7 times
 * to unlock the Surprise lab: persisted, with a toast; debug builds show the lab without taps), "Made for one friend in
 * Ontario", the egg hint in Hachi Maru, the five OFL texts (each expandable) and the sticker-art credit.
 */
@Composable
fun AboutScreen(
    state: AboutUiState,
    actions: AboutActions,
    modifier: Modifier = Modifier,
    toasts: ToastHostState = remember { ToastHostState() },
    scroll: ScrollState = rememberScrollState(),
    initiallyOpen: Set<String> = emptySet(),
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val resources = LocalResources.current
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var open by rememberSaveable { mutableStateOf(initiallyOpen.toList()) }

    SecondaryScreen(title = stringResource(R.string.about_title), onBack = actions.onBack, modifier = modifier, toasts = toasts) {
        ScreenColumn(state = scroll) {
            PadCard(spacing = 6.dp) {
                Wordmark(stringResource(R.string.wordmark), Modifier.padding(top = 4.dp))
                Text(
                    stringResource(R.string.about_version, state.appVersion),
                    style = t.fieldValue.copy(color = c.ink),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(top = 12.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            if (!state.canUnlock) return@clickable
                            val now = System.currentTimeMillis()
                            taps = if (now - lastTap > TAP_GAP_MILLIS) 1 else taps + 1
                            lastTap = now
                            if (taps >= UNLOCK_TAPS) {
                                taps = 0
                                actions.onUnlockLab()
                                toasts.show(ToastMessage(resources.getString(R.string.about_lab_unlocked), kind = "lab"))
                            }
                        },
                )
                Text(stringResource(R.string.about_made_for), style = t.body.copy(color = c.ink))
                PicnicText(
                    "♡ " + stringResource(R.string.about_sun_hint),
                    style = t.doodle.copy(color = c.doodle),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .graphicsLayer { rotationZ = -2f }
                        .clearAndSetSemantics { },
                )
            }
            if (state.labVisible) {
                PadCard {
                    LinkRow(
                        title = stringResource(R.string.about_lab),
                        hint = stringResource(R.string.about_lab_hint),
                        onClick = actions.onOpenLab,
                        glyph = PicnicGlyph.Sparkle,
                    )
                }
            }
            PadCard(title = stringResource(R.string.about_licences), glyph = PicnicGlyph.Heart, glyphTint = accentInk(Families.sakura), spacing = 4.dp) {
                Text(stringResource(R.string.about_licences_intro), style = t.body.copy(color = c.ink), modifier = Modifier.padding(bottom = 6.dp))
                state.licences.forEachIndexed { i, licence ->
                    if (i > 0) RowRule()
                    LicenceRow(
                        licence = licence,
                        expanded = licence.family in open,
                        onToggle = { open = if (licence.family in open) open - licence.family else open + licence.family },
                    )
                }
            }
            PadCard(title = stringResource(R.string.about_art), glyph = PicnicGlyph.Sparkle, glyphTint = accentInk(Families.grape)) {
                Text(stringResource(R.string.about_art_credit), style = t.body.copy(color = c.ink))
            }
        }
    }
}

/** One licence (§6.9): the family and its use, a chevron; expanded, the full OFL text under it. One Role.Button node. */
@Composable
private fun LicenceRow(licence: Licence, expanded: Boolean, onToggle: () -> Unit) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val state = stringResource(if (expanded) R.string.about_licence_expanded else R.string.about_licence_collapsed)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = state }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(licence.family, style = t.fieldValue.copy(color = c.ink))
                Text(stringResource(R.string.about_licence_use, licence.licence, licence.use), style = t.caption.copy(color = c.inkSoft))
            }
            Glyph(if (expanded) PicnicGlyph.Collapse else PicnicGlyph.Expand, contentDescription = null, tint = c.inkSoft, size = 22.dp)
        }
        if (expanded) {
            Text(
                licence.text,
                style = t.caption.copy(color = c.ink),
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
        }
    }
}

/** Taps on the version line that unlock the lab (§6.9). */
const val UNLOCK_TAPS: Int = 7

/** A longer pause than this between two taps starts the count again. */
private const val TAP_GAP_MILLIS = 2_000L
