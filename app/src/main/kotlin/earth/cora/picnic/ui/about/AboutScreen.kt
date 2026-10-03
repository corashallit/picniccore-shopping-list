package earth.cora.picnic.ui.about

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import earth.cora.picnic.ui.common.highlighterPress
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
    AboutScreen(state, actions, clock = vm.clock)
}

/**
 * The version line's tap counter (§6.9): [UNLOCK_TAPS] taps, each within [TAP_GAP_MILLIS] of the one before (read from
 * [clock], the app's clock; tests pass the compose test clock), unlock the Surprise lab. A longer pause starts again.
 */
class UnlockTaps(private val clock: () -> Long) {
    private var taps = 0
    private var last = 0L

    /** One tap on the version line; true when it is the tap that unlocks the lab. */
    fun tap(): Boolean {
        val now = clock()
        taps = if (taps == 0 || now - last > TAP_GAP_MILLIS) 1 else taps + 1
        last = now
        if (taps < UNLOCK_TAPS) return false
        taps = 0
        return true
    }
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
    clock: () -> Long = System::currentTimeMillis,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val resources = LocalResources.current
    val unlock = remember(clock) { UnlockTaps(clock) }
    var open by rememberSaveable { mutableStateOf(initiallyOpen.toList()) }

    SecondaryScreen(title = stringResource(R.string.about_title), onBack = actions.onBack, modifier = modifier, toasts = toasts) {
        ScreenColumn(state = scroll) {
            PadCard(spacing = 6.dp) {
                Wordmark(stringResource(R.string.wordmark), Modifier.padding(top = 4.dp))
                // The whole 48 dp box is the target (§15); the line looks like plain text (the lab is a secret, §6.9).
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .heightIn(min = 48.dp)
                        .widthIn(min = 48.dp)
                        .clickable(interactionSource = null, indication = null) {
                            if (state.canUnlock && unlock.tap()) {
                                actions.onUnlockLab()
                                toasts.show(ToastMessage(resources.getString(R.string.about_lab_unlocked), kind = "lab"))
                            }
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(stringResource(R.string.about_version, state.appVersion), style = t.fieldValue.copy(color = c.ink))
                }
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
                .clickable(interactionSource = null, indication = highlighterPress(), role = Role.Button, onClick = onToggle)
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
const val TAP_GAP_MILLIS: Long = 2_000L
