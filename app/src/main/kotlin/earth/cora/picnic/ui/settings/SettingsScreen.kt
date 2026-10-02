package earth.cora.picnic.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import earth.cora.picnic.R
import earth.cora.picnic.ui.common.BackButton
import earth.cora.picnic.ui.common.HintText
import earth.cora.picnic.ui.common.ScreenScaffold
import earth.cora.picnic.ui.common.SectionCard
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.LoadingSparkle
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicSwitch
import earth.cora.picnic.ui.theme.PicnicTextField
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.PicnicTopBar
import earth.cora.picnic.ui.theme.SectionDivider
import kotlinx.coroutines.Dispatchers

@Stable
class SettingsActions(
    val onBack: () -> Unit = {},
    val onTokenChange: (String) -> Unit = {},
    val onToggleTokenVisible: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onClearToken: () -> Unit = {},
    val onDrawIconsChange: (Boolean) -> Unit = {},
    val onTest: () -> Unit = {},
)

@Composable
fun SettingsRoute(vm: SettingsViewModel, onBack: () -> Unit) {
    // Main.immediate: text-field edits round-trip through the VM within the same frame.
    val state by vm.state.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val actions = remember(vm) {
        SettingsActions(
            onBack = onBack,
            onTokenChange = vm::setTokenInput,
            onToggleTokenVisible = vm::toggleTokenVisible,
            onSave = vm::saveConnection,
            onClearToken = vm::clearToken,
            onDrawIconsChange = vm::setDrawIcons,
            onTest = vm::testConnection,
        )
    }
    SettingsScreen(state, actions)
}

/** Stateless Settings screen: your OpenAI key, Icons, advice about the key, and the app version. */
@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    val d = PicnicTheme.dimens
    ScreenScaffold(
        modifier = modifier,
        topBar = {
            PicnicTopBar(
                title = stringResource(R.string.settings_title),
                navigation = { BackButton(actions.onBack) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = d.gutter, end = d.gutter, top = d.xs, bottom = d.xxl),
            verticalArrangement = Arrangement.spacedBy(d.m),
        ) {
            KeyCard(state, actions)
            IconsCard(state, actions)
            SectionCard(stringResource(R.string.settings_help_title)) {
                HelpLine(stringResource(R.string.settings_help_budget))
                HelpLine(stringResource(R.string.settings_help_restricted))
                HelpLine(stringResource(R.string.settings_help_guide))
                HelpLine(stringResource(R.string.settings_help_privacy))
            }
            HintText(
                stringResource(R.string.settings_version, stringResource(R.string.app_name), state.appVersion),
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyCard(state: SettingsUiState, actions: SettingsActions) {
    val d = PicnicTheme.dimens
    SectionCard(stringResource(R.string.settings_key_title), glyph = PicnicGlyph.Key) {
        Text(stringResource(R.string.settings_key_intro), style = PicnicTheme.type.body, color = PicnicTheme.colors.inkSoft)
        PicnicTextField(
            value = state.tokenInput,
            onValueChange = actions.onTokenChange,
            label = stringResource(R.string.settings_key_label),
            placeholder = if (state.hasToken) {
                stringResource(R.string.settings_key_saved_placeholder, state.tokenHint.orEmpty())
            } else {
                stringResource(R.string.settings_key_placeholder)
            },
            singleLine = true,
            enabled = !state.loading,
            supportingText = stringResource(if (state.hasToken) R.string.settings_key_saved else R.string.settings_key_missing),
            visualTransformation = if (state.tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            trailing = if (state.tokenInput.isNotEmpty()) {
                {
                    PicnicButton(
                        stringResource(if (state.tokenVisible) R.string.settings_key_hide else R.string.settings_key_show),
                        onClick = actions.onToggleTokenVisible,
                        style = PicnicButtonStyle.Text,
                    )
                }
            } else {
                null
            },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(d.s), verticalArrangement = Arrangement.spacedBy(d.xs)) {
            PicnicButton(stringResource(R.string.settings_save), onClick = actions.onSave, enabled = state.canSave, glyph = PicnicGlyph.Check)
            if (state.hasToken) {
                PicnicButton(stringResource(R.string.settings_remove_key), onClick = actions.onClearToken, style = PicnicButtonStyle.Text, glyph = PicnicGlyph.Delete)
            }
        }
        state.notice?.let { Notice(it) }
        HintText(stringResource(R.string.settings_key_storage))
        SectionDivider()
        PicnicButton(
            stringResource(R.string.settings_test),
            onClick = actions.onTest,
            enabled = state.test != ConnectionTestUi.Running && (state.hasToken || state.tokenInput.isNotBlank()),
            style = PicnicButtonStyle.Secondary,
            glyph = PicnicGlyph.Sparkle,
        )
        TestResult(state.test)
    }
}

/** A one-line note about the last save, announced politely. */
@Composable
private fun Notice(text: String) {
    val c = PicnicTheme.colors
    Row(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(PicnicTheme.dimens.s),
    ) {
        Glyph(PicnicGlyph.Sparkle, contentDescription = null, tint = c.highlight, size = 20.dp)
        Text(text, style = PicnicTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TestResult(test: ConnectionTestUi) {
    val c = PicnicTheme.colors
    val d = PicnicTheme.dimens
    val live = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
    when (test) {
        ConnectionTestUi.Idle -> Unit
        ConnectionTestUi.Running -> LoadingSparkle(label = stringResource(R.string.settings_test_running))
        is ConnectionTestUi.Ok -> Row(live, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.s)) {
            Glyph(PicnicGlyph.Check, contentDescription = null, tint = c.primary)
            Text(stringResource(R.string.settings_test_ok, test.latencyMs), style = PicnicTheme.type.body, color = c.ink)
        }
        is ConnectionTestUi.Failed -> Row(live, horizontalArrangement = Arrangement.spacedBy(d.s)) {
            Glyph(PicnicGlyph.Sparkle, contentDescription = null, tint = c.error, size = 20.dp)
            Text(
                stringResource(R.string.settings_test_failed, test.message),
                style = PicnicTheme.type.body,
                color = c.ink,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun IconsCard(state: SettingsUiState, actions: SettingsActions) {
    val d = PicnicTheme.dimens
    SectionCard(stringResource(R.string.settings_icons), glyph = PicnicGlyph.Sparkle) {
        Row(
            Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.m),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d.xxs)) {
                Text(stringResource(R.string.settings_draw_icons), style = PicnicTheme.type.itemName, color = PicnicTheme.colors.ink)
                HintText(stringResource(R.string.settings_draw_icons_note))
            }
            PicnicSwitch(checked = state.drawIcons, onCheckedChange = actions.onDrawIconsChange, enabled = !state.loading)
        }
    }
}

@Composable
private fun HelpLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(PicnicTheme.dimens.s)) {
        Glyph(PicnicGlyph.Sparkle, contentDescription = null, tint = PicnicTheme.colors.primary, size = 14.dp, modifier = Modifier.padding(top = 4.dp))
        Text(text, style = PicnicTheme.type.body, color = PicnicTheme.colors.ink, modifier = Modifier.weight(1f))
    }
}
