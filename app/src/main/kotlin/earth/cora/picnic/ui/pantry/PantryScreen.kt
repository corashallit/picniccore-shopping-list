package earth.cora.picnic.ui.pantry

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import earth.cora.picnic.R
import earth.cora.picnic.ui.common.DoneButton
import earth.cora.picnic.ui.common.MagicAppearBox
import earth.cora.picnic.ui.common.PadCard
import earth.cora.picnic.ui.common.ScreenColumn
import earth.cora.picnic.ui.common.SecondaryScreen
import earth.cora.picnic.ui.edit.StickerPicker
import earth.cora.picnic.ui.edit.catalogChoices
import earth.cora.picnic.ui.theme.ButtonKind
import earth.cora.picnic.ui.theme.ChipFace
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LetterSetSheet
import earth.cora.picnic.ui.theme.LocalIconCatalog
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.MenuItem
import earth.cora.picnic.ui.theme.OverflowMenu
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicSurface
import earth.cora.picnic.ui.theme.PicnicTextField
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.RoundStickerButton
import earth.cora.picnic.ui.theme.ToastHostState
import earth.cora.picnic.ui.theme.ToastMessage
import earth.cora.picnic.ui.theme.drawGingham
import earth.cora.picnic.ui.theme.hardShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Everything the pantry screen can ask for. Defaults do nothing. */
@Stable
class PantryActions(
    val onBack: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onAddInput: (String) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onSuggestion: (String) -> Unit = {},
    val onText: (String) -> Unit = {},
    val onCommitText: () -> Unit = {},
    val onTakeOff: (key: String) -> Unit = {},
    val onChangeSticker: (key: String, iconKey: String) -> Unit = { _, _ -> },
    val onAppeared: (key: String) -> Unit = {},
)

@Composable
fun PantryRoute(vm: PantryViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val toasts = remember { ToastHostState() }
    val resources = LocalResources.current
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is PantryEvent.AlreadyThere -> toasts.show(ToastMessage(resources.getString(R.string.pantry_already, event.name), kind = "pantry-info"))
                is PantryEvent.TookOff -> toasts.show(
                    ToastMessage(
                        resources.getString(R.string.pantry_took_off, event.entry.name),
                        kind = "pantry-off",
                        actionLabel = resources.getString(R.string.screens_undo),
                        onAction = { vm.undoTakeOff(event.entry, event.index) },
                    ),
                )
                PantryEvent.SaveFailed -> toasts.show(ToastMessage(resources.getString(R.string.screens_save_failed), kind = "notice"))
            }
        }
    }
    val actions = remember(vm) {
        PantryActions(
            onBack = {
                vm.leave()
                onBack()
            },
            onDone = {
                vm.leave()
                onBack()
            },
            onAddInput = vm::setAddInput,
            onAdd = vm::add,
            onSuggestion = vm::addSuggestion,
            onText = vm::setText,
            onCommitText = vm::commitText,
            onTakeOff = vm::takeOff,
            onChangeSticker = vm::changeSticker,
            onAppeared = vm::appeared,
        )
    }
    PantryScreen(state, actions, toasts = toasts)
}

/**
 * My pantry (§6.13): the binder top (Back, "My pantry", its subtitle on a pad patch, "Done"); the "Add to pantry:" field
 * with its strawberry Add sticker button; the shelves in the JAR_SHELF basket's language (a gingham valance, wooden
 * boards, stickers standing on them at 52 dp with their names under the board, 4 per shelf, 5 from 412 dp); a tap opens
 * the pop card ("Take off the shelf", "Change sticker"); "Pantry as text:" (Klee One, ruled) previews the shelves as she
 * types and commits on Done or blur. Empty: a dusty shelf, the line and four suggestion chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantryScreen(
    state: PantryUiState,
    actions: PantryActions,
    modifier: Modifier = Modifier,
    toasts: ToastHostState = remember { ToastHostState() },
    scroll: ScrollState = rememberScrollState(),
    initialPopCard: String? = null,
    focusText: Boolean = false,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    var popCard by rememberSaveable { mutableStateOf(initialPopCard) }
    var changing by rememberSaveable { mutableStateOf<String?>(null) }
    val textFocus = remember { FocusRequester() }
    LaunchedEffect(focusText) { if (focusText) textFocus.requestFocus() }

    SecondaryScreen(
        title = stringResource(R.string.pantry_title),
        subtitle = stringResource(R.string.pantry_subtitle),
        onBack = actions.onBack,
        modifier = modifier,
        toasts = toasts,
        actions = { DoneButton(actions.onDone) },
        overlay = {
            val sticker = state.stickers.firstOrNull { it.key == changing }
            val catalog = LocalIconCatalog.current
            LetterSetSheet(visible = sticker != null, onDismissRequest = { changing = null }) {
                if (sticker != null) {
                    Text(stringResource(R.string.pantry_change_title, sticker.name), style = t.cardTitle.copy(color = c.ink))
                    Spacer(Modifier.height(12.dp))
                    val keys = remember(sticker.icon.key) { catalogChoices(sticker.icon.key.takeIf { it in catalog }, catalog) }
                    StickerPicker(
                        keys,
                        selected = sticker.icon.key,
                        onPick = { key ->
                            actions.onChangeSticker(sticker.key, key)
                            changing = null
                        },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    )
                }
            }
        },
    ) {
        ScreenColumn(state = scroll, spacing = 18.dp) {
            AddCard(state, actions)
            if (state.stickers.isEmpty() && !state.loading) {
                EmptyShelf(actions)
            } else {
                Shelves(
                    state = state,
                    popCard = popCard,
                    onPopCard = { popCard = it },
                    onTakeOff = { key ->
                        popCard = null
                        actions.onTakeOff(key)
                    },
                    onChange = { key ->
                        popCard = null
                        changing = key
                    },
                    onAppeared = actions.onAppeared,
                )
            }
            PadCard {
                var focused by remember { mutableStateOf(false) }
                val commit by rememberUpdatedState(actions.onCommitText)
                PicnicTextField(
                    value = state.text,
                    onValueChange = actions.onText,
                    label = stringResource(R.string.pantry_text_label),
                    minLines = 4,
                    textStyle = t.aislesText,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .focusRequester(textFocus)
                        .onFocusChanged {
                            if (focused && !it.isFocused) commit()
                            focused = it.isFocused
                        },
                )
                if (state.overLimit) {
                    Text(stringResource(R.string.pantry_limit), style = t.caption.copy(color = c.inkSoft))
                }
            }
        }
    }
}

/** "Add to pantry:" with the 48 dp strawberry Add sticker button (a 56 dp target); Enter adds too. */
@Composable
private fun AddCard(state: PantryUiState, actions: PantryActions) {
    PicnicSurface(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PicnicTextField(
                value = state.addInput,
                onValueChange = actions.onAddInput,
                label = stringResource(R.string.pantry_add_label),
                placeholder = stringResource(R.string.pantry_add_hint),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { actions.onAdd() }),
                modifier = Modifier.weight(1f),
            )
            RoundStickerButton(
                PicnicGlyph.Add,
                stringResource(R.string.pantry_add_action),
                onClick = actions.onAdd,
                enabled = state.addInput.isNotBlank(),
                kind = ButtonKind.AddBarSend,
                faceSize = 48.dp,
            )
        }
    }
}

/**
 * How many stickers stand on one shelf: 4 on a 360 dp phone, 5 from 412 dp (§6.13). From font scale 1.5 one fewer, so
 * a two-word name like "Black pepper" still fits its two lines.
 */
fun stickersPerShelf(screenWidth: Dp, fontScale: Float = 1f): Int =
    (if (screenWidth >= 412.dp) 5 else 4) - (if (fontScale >= 1.5f) 1 else 0)

/** The cabinet: a gingham valance, then one board per [stickersPerShelf] stickers. */
@Composable
private fun Shelves(
    state: PantryUiState,
    popCard: String?,
    onPopCard: (String?) -> Unit,
    onTakeOff: (String) -> Unit,
    onChange: (String) -> Unit,
    onAppeared: (String) -> Unit,
) {
    val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    val perShelf = stickersPerShelf(screen, androidx.compose.ui.platform.LocalDensity.current.fontScale)
    Cabinet {
        state.stickers.chunked(perShelf).forEach { row ->
            Shelf(
                row = row,
                perShelf = perShelf,
                openKey = popCard,
                onDismissCard = { onPopCard(null) },
                popCard = { sticker ->
                    OverflowMenu(
                        items = listOf(
                            MenuItem(stringResource(R.string.pantry_take_off), PicnicGlyph.Delete, onClick = { onTakeOff(sticker.key) }),
                            MenuItem(stringResource(R.string.pantry_change), PicnicGlyph.Gallery, onClick = { onChange(sticker.key) }),
                        ),
                        onDismissRequest = { onPopCard(null) },
                        width = 208.dp,
                    )
                },
            ) { sticker ->
                ShelfStickerView(
                    sticker = sticker,
                    fresh = sticker.key in state.fresh,
                    popped = state.popped?.takeIf { it.first == sticker.key }?.second,
                    onTap = { onPopCard(sticker.key) },
                    onTakeOff = { onTakeOff(sticker.key) },
                    onChange = { onChange(sticker.key) },
                    onAppeared = { onAppeared(sticker.key) },
                )
            }
        }
    }
}

/** The pantry cabinet (the JAR_SHELF basket at full width): cocoa back panel, white kiss-cut edge, hs2, valance on top. */
@Composable
private fun Cabinet(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .hardShadow(shape, PicnicTheme.shadows, large = true)
            .clip(shape)
            .background(PicnicTheme.colors.stickerWhite)
            .padding(3.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Families.cocoa.pastel),
    ) {
        Valance()
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 6.dp)) { content() }
    }
}

/** The gingham curtain valance: sakura gingham on white with a scalloped hem traced in sakura (decorative). */
@Composable
private fun Valance() {
    val curtain = PicnicTheme.colors.stickerWhite
    Box(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clearAndSetSemantics { }
            .drawBehind {
                val hem = 22.dp.toPx()
                val scallop = 22.dp.toPx()
                val r = scallop / 2f
                val body = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, hem)
                    var x = size.width
                    while (x > 0f) {
                        val nx = (x - scallop).coerceAtLeast(0f)
                        quadraticTo((x + nx) / 2f, hem + r * 1.3f, nx, hem)
                        x = nx
                    }
                    close()
                }
                drawPath(body, curtain)
                clipPath(body) { drawGingham(Families.sakura.mid.copy(alpha = 0.55f), 7.dp.toPx(), Rect(Offset.Zero, size)) }
                drawPath(body, Families.sakura.trace, style = Stroke(1.5.dp.toPx()))
                // The rod the curtain hangs from.
                drawRect(Families.cocoa.mid, Offset(0f, 0f), Size(size.width, 4.dp.toPx()))
                drawRect(Families.cocoa.trace, Offset(0f, 4.dp.toPx()), Size(size.width, 1.dp.toPx()))
            },
    )
}

/**
 * One shelf: [perShelf] equal slots; each sticker of [row] stands on the board and its name sits under the board, two
 * lines at most, centred (the sticker's own node already reads the name). The pop card of the sticker [openKey] names
 * opens under this shelf's name row, so the tapped sticker and its name stay in view; when there is no room below it,
 * it opens above the shelf instead ([ShelfCardPosition]).
 */
@Composable
private fun Shelf(
    row: List<ShelfSticker>,
    perShelf: Int,
    openKey: String?,
    onDismissCard: () -> Unit,
    popCard: @Composable (ShelfSticker) -> Unit,
    sticker: @Composable (ShelfSticker) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until perShelf) {
                Box(Modifier.weight(1f).height(64.dp), contentAlignment = Alignment.BottomCenter) {
                    row.getOrNull(i)?.let { sticker(it) }
                }
            }
        }
        Board()
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            for (i in 0 until perShelf) {
                Box(Modifier.weight(1f).padding(horizontal = 2.dp), contentAlignment = Alignment.TopCenter) {
                    val name = row.getOrNull(i)?.name
                    if (name != null) {
                        Text(
                            name,
                            style = PicnicTheme.type.label.copy(color = PicnicTheme.colors.ballpoint, fontSize = PicnicTheme.type.caption.fontSize),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                }
            }
        }
        val open = row.indexOfFirst { it.key == openKey }
        if (open >= 0) {
            val density = LocalDensity.current
            val position = remember(open, perShelf, density) { ShelfCardPosition(open, perShelf, with(density) { 8.dp.roundToPx() }) }
            Popup(popupPositionProvider = position, onDismissRequest = onDismissCard, properties = PopupProperties(focusable = true)) {
                // Room for the card's hard shadow (hs2) on its right and bottom.
                Box(Modifier.padding(end = 4.dp, bottom = 4.dp)) { popCard(row[open]) }
            }
        }
    }
}

/**
 * Where a shelf sticker's pop card opens: horizontally centred on its slot ([index] of [perShelf]), kept [margin] inside
 * the window; vertically just under the shelf's name row (the anchor is the whole shelf, names included), or, when it
 * would run off the bottom of the window, just above the shelf.
 */
internal class ShelfCardPosition(private val index: Int, private val perShelf: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val slot = anchorBounds.width / perShelf.toFloat()
        val centre = anchorBounds.left + slot * (index + 0.5f)
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = (centre - popupContentSize.width / 2f).roundToInt().coerceIn(margin, maxX)
        val below = anchorBounds.bottom
        val y = if (below + popupContentSize.height <= windowSize.height - margin) below else (anchorBounds.top - popupContentSize.height).coerceAtLeast(margin)
        return IntOffset(x, y)
    }
}

/** A wooden board: peach-brown (cocoa mid) with a light top edge, a cocoa trace outline and a 3 dp hard shadow under it. */
@Composable
private fun Board(modifier: Modifier = Modifier) {
    val shadow = PicnicTheme.shadows.color
    Box(
        modifier
            .fillMaxWidth()
            .height(15.dp)
            .clearAndSetSemantics { }
            .drawBehind {
                val board = 12.dp.toPx()
                val radius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                drawRoundRect(shadow, Offset(0f, 3.dp.toPx()), Size(size.width, board), radius)
                drawRoundRect(Families.cocoa.mid, Offset.Zero, Size(size.width, board), radius)
                drawRect(Families.cocoa.pastel, Offset(2.dp.toPx(), 1.5.dp.toPx()), Size(size.width - 4.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(Families.cocoa.trace, Offset.Zero, Size(size.width, board), radius, style = Stroke(1.5.dp.toPx()))
            },
    )
}

/**
 * One shelf sticker: 52 dp, standing on the board; new ones magic-appear, a name added again pops (.9 → 1.1 → 1, 400 ms).
 * A tap opens the pop card; TalkBack: a button "Salt, in your pantry" with "Take off the shelf" and "Change sticker".
 */
@Composable
private fun ShelfStickerView(
    sticker: ShelfSticker,
    fresh: Boolean,
    popped: Int?,
    onTap: () -> Unit,
    onTakeOff: () -> Unit,
    onChange: () -> Unit,
    onAppeared: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val label = stringResource(R.string.pantry_sticker, sticker.name)
    val takeOff = stringResource(R.string.pantry_take_off)
    val change = stringResource(R.string.pantry_change)
    val pop = remember { Animatable(1f) }
    LaunchedEffect(popped) {
        if (popped == null || reduced) return@LaunchedEffect
        withContext(FixedMotionScale) {
            for (s in listOf(0.9f, 1.1f, 1f)) {
                pop.snapTo(s)
                delay(POP_STEP_MILLIS)
            }
        }
    }
    Box(
        Modifier
            .size(width = 64.dp, height = 60.dp)
            // No ripple: the tap answers with the pop card (§6.13).
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onTap)
            .semantics {
                contentDescription = label
                role = Role.Button
                customActions = listOf(
                    CustomAccessibilityAction(takeOff) { onTakeOff(); true },
                    CustomAccessibilityAction(change) { onChange(); true },
                )
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        MagicAppearBox(key = sticker.key, play = fresh, onPlayed = onAppeared, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
            ItemIcon(sticker.icon, contentDescription = null, size = 52.dp, drawing = sticker.busy)
        }
    }
}

/** The empty pantry (§6.13): an empty shelf with a dust bunny, the line, and the four suggestion chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyShelf(actions: PantryActions) {
    val t = PicnicTheme.type
    val c = PicnicTheme.colors
    Cabinet {
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            DecorSticker("dustbunny", Modifier.align(Alignment.BottomStart).offset(x = 22.dp, y = 2.dp), size = 44.dp)
        }
        Board()
        Column(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.pantry_empty), style = t.body.copy(color = c.ballpoint), textAlign = TextAlign.Center)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                for (name in PantryViewModel.SUGGESTIONS) SuggestionChip(name) { actions.onSuggestion(name) }
            }
        }
    }
}

/** A suggestion chip: a lemon kiss-cut label with a plus; a button that adds its entry. */
@Composable
private fun SuggestionChip(name: String, onClick: () -> Unit) {
    val label = stringResource(R.string.pantry_suggest, name)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        ChipFace(fill = Families.lemon.pastel) {
            earth.cora.picnic.ui.theme.Glyph(PicnicGlyph.Add, contentDescription = null, tint = PicnicTheme.colors.ballpoint, size = 15.dp)
            Text(name, style = PicnicTheme.type.label.copy(color = PicnicTheme.colors.ballpoint), maxLines = 1)
        }
    }
}

private const val POP_STEP_MILLIS = 133L
