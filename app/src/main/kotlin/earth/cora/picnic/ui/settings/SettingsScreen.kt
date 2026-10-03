package earth.cora.picnic.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import earth.cora.picnic.R
import earth.cora.picnic.settings.Surprise
import earth.cora.picnic.ui.common.DoneButton
import earth.cora.picnic.ui.common.accentInk
import earth.cora.picnic.ui.common.HankoTextButton
import earth.cora.picnic.ui.common.LinkRow
import earth.cora.picnic.ui.common.MoreMenuButton
import earth.cora.picnic.ui.common.PadCard
import earth.cora.picnic.ui.common.RowRule
import earth.cora.picnic.ui.common.ScreenColumn
import earth.cora.picnic.ui.common.SecondaryScreen
import earth.cora.picnic.ui.common.StarLine
import earth.cora.picnic.ui.common.SwitchRow
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.KeyField
import earth.cora.picnic.ui.theme.LcdChip
import earth.cora.picnic.ui.theme.LcdMode
import earth.cora.picnic.ui.theme.LcdState
import earth.cora.picnic.ui.theme.LetterSetSheet
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.MenuItem
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.SectionDivider
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage
import earth.cora.picnic.ui.theme.rememberLoopTime
import kotlinx.coroutines.Dispatchers

/** Everything the Settings screen can ask for. Defaults do nothing. */
@Stable
class SettingsActions(
    val onBack: () -> Unit = {},
    val onTokenChange: (String) -> Unit = {},
    val onRevealChange: (Boolean) -> Unit = {},
    val onPaste: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onClearToken: () -> Unit = {},
    val onDrawIconsChange: (Boolean) -> Unit = {},
    val onTest: () -> Unit = {},
    val onSurpriseChange: (Surprise, Boolean) -> Unit = { _, _ -> },
    val onAreaDenied: () -> Unit = {},
    val onAreaRefresh: () -> Unit = {},
    val onNoticeShown: () -> Unit = {},
    val onOpenAbout: () -> Unit = {},
    val onBackToList: () -> Unit = {},
    val onOpenMyStore: () -> Unit = {},
    val onOpenAlbum: () -> Unit = {},
)

/** Coarse location for "Use my area" (§0.4): whether it is granted, and the system prompt. */
interface LocationPermission {
    fun granted(): Boolean

    /** Shows the system prompt for ACCESS_COARSE_LOCATION only; [onResult] gets whether it was granted. */
    fun request(onResult: (Boolean) -> Unit)

    /** Always denied, never prompts (previews, screenshots). */
    object Denied : LocationPermission {
        override fun granted() = false
        override fun request(onResult: (Boolean) -> Unit) = onResult(false)
    }
}

/** The phone's coarse-location permission through the activity-result API. */
@Composable
fun rememberCoarseLocationPermission(): LocationPermission {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.invoke(granted)
        pending = null
    }
    return remember(context, launcher) {
        object : LocationPermission {
            override fun granted() = context.hasCoarseLocation()

            override fun request(onResult: (Boolean) -> Unit) {
                pending = onResult
                launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }
}

private fun Context.hasCoarseLocation(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun SettingsRoute(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onBackToList: () -> Unit = onBack,
    onOpenMyStore: () -> Unit = {},
    onOpenAlbum: () -> Unit = {},
) {
    // Main.immediate: text-field edits round-trip through the VM within the same frame.
    val state by vm.state.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val clipboard = LocalClipboardManager.current
    val location = rememberCoarseLocationPermission()
    val actions = remember(vm) {
        SettingsActions(
            onBack = onBack,
            onTokenChange = vm::setTokenInput,
            onRevealChange = vm::setTokenVisible,
            onPaste = { clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let(vm::setTokenInput) },
            onSave = vm::saveConnection,
            onClearToken = vm::clearToken,
            onDrawIconsChange = vm::setDrawIcons,
            onTest = vm::testConnection,
            onSurpriseChange = vm::setSurprise,
            onAreaDenied = vm::onAreaDenied,
            onAreaRefresh = vm::refreshArea,
            onNoticeShown = vm::noticeShown,
            onOpenAbout = onOpenAbout,
            onBackToList = onBackToList,
            onOpenMyStore = onOpenMyStore,
            onOpenAlbum = onOpenAlbum,
        )
    }
    SettingsScreen(state, actions, location)
}

/**
 * Stateless Settings (§6.8): binder background, puffy title, pad cards with punch holes: Your OpenAI key, Stickers,
 * Surprises (seven switches, "Use my area" asking for approximate location after its rationale), About your key, and
 * the About & licences row. A failed write is a sticky-note toast.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    location: LocationPermission = LocationPermission.Denied,
    modifier: Modifier = Modifier,
    toasts: ToastHostState = remember { ToastHostState() },
    scroll: ScrollState = rememberScrollState(),
    initialRationale: Boolean = false,
) {
    val resources = LocalResources.current
    var showRationale by rememberSaveable { mutableStateOf(initialRationale) }
    var menu by remember { mutableStateOf(false) }

    // The permission can change in Android settings while we are away: look again on every resume.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var granted by remember { mutableStateOf(location.granted()) }
    val onAreaRefresh by rememberUpdatedState(actions.onAreaRefresh)
    LaunchedEffect(lifecycle) {
        if (lifecycle == Lifecycle.State.RESUMED) {
            val now = location.granted()
            if (now != granted) {
                granted = now
                onAreaRefresh()
            }
        }
    }

    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        toasts.show(ToastMessage(notice, kind = "notice"))
        actions.onNoticeShown()
    }

    fun onArea(on: Boolean) {
        when {
            !on -> actions.onSurpriseChange(Surprise.UseMyArea, false)
            location.granted() -> {
                granted = true
                actions.onSurpriseChange(Surprise.UseMyArea, true)
            }
            else -> showRationale = true
        }
    }

    SecondaryScreen(
        title = stringResource(R.string.settings_title),
        onBack = actions.onBack,
        modifier = modifier,
        toasts = toasts,
        actions = {
            MoreMenuButton(
                items = listOf(
                    MenuItem(backToListLabel(state.listTitle), PicnicGlyph.Back, actions.onBackToList),
                    MenuItem(stringResource(R.string.menu_my_store), PicnicGlyph.Aisles, actions.onOpenMyStore),
                    MenuItem(stringResource(R.string.menu_album), PicnicGlyph.Album, actions.onOpenAlbum),
                ),
                expanded = menu,
                onExpandedChange = { menu = it },
            )
        },
        overlay = {
            LetterSetSheet(
                visible = showRationale,
                onDismissRequest = { showRationale = false },
                footer = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PicnicButton(stringResource(R.string.settings_area_not_now), onClick = { showRationale = false }, style = PicnicButtonStyle.Text)
                        PicnicButton(
                            stringResource(R.string.settings_area_continue),
                            onClick = {
                                showRationale = false
                                location.request { ok ->
                                    granted = ok
                                    if (ok) {
                                        actions.onSurpriseChange(Surprise.UseMyArea, true)
                                    } else {
                                        actions.onAreaDenied()
                                        toasts.show(ToastMessage(resources.getString(R.string.settings_area_denied), kind = "area"))
                                    }
                                }
                            },
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.settings_area_rationale_title), style = PicnicTheme.type.cardTitle.copy(color = PicnicTheme.colors.ink))
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.settings_area_rationale), style = PicnicTheme.type.body.copy(color = PicnicTheme.colors.ink))
            }
        },
    ) {
        ScreenColumn(state = scroll) {
            KeyCard(state, actions)
            PadCard(title = stringResource(R.string.settings_stickers), glyph = PicnicGlyph.Sparkle, glyphTint = accentInk(Families.grape)) {
                SwitchRow(
                    label = stringResource(R.string.settings_draw_icons),
                    hint = stringResource(R.string.settings_draw_icons_note),
                    checked = state.drawIcons,
                    onCheckedChange = actions.onDrawIconsChange,
                    enabled = !state.loading,
                )
            }
            SurprisesCard(state, actions, granted, ::onArea)
            PadCard(title = stringResource(R.string.settings_help_title), glyph = PicnicGlyph.Key, glyphTint = accentInk(Families.ramune)) {
                StarLine(stringResource(R.string.settings_help_budget))
                StarLine(stringResource(R.string.settings_help_restricted))
                StarLine(stringResource(R.string.settings_help_guide))
                StarLine(stringResource(R.string.settings_help_privacy))
            }
            PadCard {
                LinkRow(
                    title = stringResource(R.string.settings_about_row),
                    hint = stringResource(R.string.settings_about_row_hint),
                    onClick = actions.onOpenAbout,
                    glyph = PicnicGlyph.Heart,
                )
            }
        }
    }
}

/** "Back to Saturday shop" (§4.24), or "Back to your list" before the title is known. */
@Composable
internal fun backToListLabel(title: String): String =
    if (title.isBlank()) stringResource(R.string.menu_back_to_your_list) else stringResource(R.string.menu_back_to_list, title)

/** "sk-…a1b2" -> "sk-•••••••••• a1b2" (§4.8: masked after save, never pre-filled). */
internal fun savedMask(hint: String): String = hint.replace("…", "•••••••••• ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyCard(state: SettingsUiState, actions: SettingsActions) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val failed = state.test as? ConnectionTestUi.Failed
    PadCard(title = stringResource(R.string.settings_key_title), glyph = PicnicGlyph.Key, glyphTint = accentInk(Families.strawberry)) {
        Text(stringResource(R.string.settings_key_intro), style = t.body.copy(color = c.ink))
        KeyField(
            value = state.tokenInput,
            onValueChange = actions.onTokenChange,
            label = stringResource(R.string.settings_key_label),
            savedMask = state.tokenHint?.let(::savedMask) ?: stringResource(R.string.settings_key_placeholder),
            revealed = state.tokenVisible,
            onRevealChange = actions.onRevealChange,
            onPaste = actions.onPaste,
            isError = failed != null,
            supportingText = failed?.message
                ?: stringResource(if (state.hasToken) R.string.settings_key_saved else R.string.settings_key_missing),
            enabled = !state.loading,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            PicnicButton(stringResource(R.string.settings_save), onClick = actions.onSave, enabled = state.canSave)
            if (state.hasToken) HankoTextButton(stringResource(R.string.settings_remove_key), onClick = actions.onClearToken)
        }
        SectionDivider()
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val running = state.test == ConnectionTestUi.Running
            PicnicButton(
                stringResource(if (running) R.string.settings_test_running else R.string.settings_test),
                onClick = actions.onTest,
                enabled = !running && (state.hasToken || state.tokenInput.isNotBlank()),
                busy = running,
                style = PicnicButtonStyle.Secondary,
                glyph = PicnicGlyph.Sparkle,
            )
            if (state.test is ConnectionTestUi.Ok) OkLcd()
        }
        Text(stringResource(R.string.settings_key_storage), style = t.caption.copy(color = c.inkSoft))
    }
}

/**
 * The passing test on an LCD chip (§6.8): "OK" → "OK." → "OK.." ticking every 0.6 s while it is the latest result
 * (still "OK" under reduced motion). TalkBack: "Connected", announced once.
 */
@Composable
private fun OkLcd() {
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = !reduced)
    val dots = if (reduced) 0 else ((t / OK_TICK_MILLIS) % 3).toInt()
    val label = stringResource(R.string.settings_test_ok)
    Box(Modifier.clearAndSetSemantics { contentDescription = label; liveRegion = LiveRegionMode.Polite }) {
        LcdChip(LcdState(mode = LcdMode.Ok, text = "OK" + ".".repeat(dots)), pitch = 1.6.dp)
    }
}

private const val OK_TICK_MILLIS = 600L

@Composable
private fun SurprisesCard(state: SettingsUiState, actions: SettingsActions, granted: Boolean, onArea: (Boolean) -> Unit) {
    val s = state.settings
    val city = state.areaCity
    PadCard(title = stringResource(R.string.settings_surprises), glyph = PicnicGlyph.Sparkle, glyphTint = accentInk(Families.sakura), spacing = 2.dp) {
        val rows = listOf(
            Surprise.EasterEggs to (R.string.settings_eggs to stringResource(R.string.settings_eggs_hint)),
            Surprise.Sounds to (R.string.settings_sounds to stringResource(R.string.settings_sounds_hint)),
            Surprise.Haptics to (R.string.settings_haptics to stringResource(R.string.settings_haptics_hint)),
            Surprise.NextStopCard to (R.string.settings_next_card to stringResource(R.string.settings_next_card_hint)),
            Surprise.Celebration to (R.string.settings_celebration to stringResource(R.string.settings_celebration_hint)),
            Surprise.Weather to (
                R.string.settings_weather to
                    if (s.useMyArea && granted && city != null) stringResource(R.string.settings_weather_hint_area, city) else stringResource(R.string.settings_weather_hint)
                ),
            Surprise.UseMyArea to (
                R.string.settings_area to when {
                    !s.useMyArea -> stringResource(R.string.settings_area_hint)
                    !granted -> stringResource(R.string.settings_area_hint_denied)
                    city != null -> stringResource(R.string.settings_area_hint_on, city)
                    else -> stringResource(R.string.settings_area_hint_finding)
                }
                ),
        )
        rows.forEachIndexed { i, (surprise, text) ->
            if (i > 0) RowRule()
            SwitchRow(
                label = stringResource(text.first),
                hint = text.second,
                checked = s.allows(surprise),
                onCheckedChange = { on -> if (surprise == Surprise.UseMyArea) onArea(on) else actions.onSurpriseChange(surprise, on) },
                enabled = !state.loading,
            )
        }
        RowRule()
        Column(Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.settings_surprises_footer), style = PicnicTheme.type.caption.copy(color = PicnicTheme.colors.inkSoft))
        }
    }
}
