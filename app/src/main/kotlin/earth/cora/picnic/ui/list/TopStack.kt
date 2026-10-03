package earth.cora.picnic.ui.list

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import earth.cora.picnic.ui.list.motion.BookPaging
import earth.cora.picnic.ui.list.motion.ListPresentation
import earth.cora.picnic.ui.list.motion.MotionChoreographer
import earth.cora.picnic.ui.theme.BookDateCaption
import earth.cora.picnic.ui.theme.BookLayout
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.FixedMotionScale
import earth.cora.picnic.ui.theme.FlatStar
import earth.cora.picnic.ui.theme.LcdChip
import earth.cora.picnic.ui.theme.LcdMode
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PadPatch
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicIconButton
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.PuffyText
import earth.cora.picnic.ui.theme.StickerBook
import earth.cora.picnic.ui.theme.SunSticker
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlinx.coroutines.withContext

/*
 * The top stack (docs/DESIGN_STICKER.md §6.1 item 1, §4.2, §7): the status bar inset, the binder top (the puffy title,
 * the sun, the subtitle on a pad patch, the LCD and More) and the trip's sticker book with 12 dp side margins and 8 dp
 * below. Nothing scrolls above or around it (R6): the list's viewport starts under it. On scroll-down the app bar and the
 * book collapse together (56 + 40 dp); every height change animates (`standard`, 200 ms). Owner: WP5.
 */

/** What the top stack shows of the screen's transient state. */
internal class TopStackUi(
    val barCollapsed: Boolean,
    val bookCollapsed: Boolean,
    /** Pull to refresh: the LCD reads SYNC (1500 ms). */
    val syncing: Boolean,
    /** Bumped on each refresh: the sun spins 360° once (400 ms). */
    val sunSpin: Int,
)

@Composable
internal fun TopStack(
    state: ListUiState,
    presentation: ListPresentation,
    engine: MotionChoreographer,
    ui: TopStackUi,
    actions: ListActions,
    onMore: () -> Unit,
    onMoreBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
    onOpenBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    Column(modifier.fillMaxWidth().statusBarsPadding().testTag(ListTags.TOP_STACK)) {
        AppBar(state, presentation, engine.anchors, ui, actions, onMore, onMoreBounds)
        val book = state.book
        if (book != null && !state.loading) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp), contentAlignment = Alignment.TopCenter) {
                val width = BookLayout.widthFor(maxWidth.value + 24f).dp
                val layout = remember(width, book.stamps.size, book.total) { BookLayout.of(width.value, book.stamps.size, book.total) }
                val shown = layout.shownPage(book.takenSlots)
                androidx.compose.runtime.SideEffect { engine.bookPaging = BookPaging(layout.perPage, shown) }
                val dateOnPage = fontScale <= 1.3f
                Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!dateOnPage) BookDateCaption(book.dateLabel)
                    StickerBook(
                        state = book,
                        presentation = presentation.book,
                        clock = engine.clock,
                        width = width,
                        collapsed = ui.bookCollapsed,
                        anchors = engine.anchors,
                        dateOnPage = dateOnPage,
                        turnKey = state.bookTurn,
                        onOpen = onOpenBook,
                        onStampTap = actions.onStampTap,
                    )
                }
            }
        }
    }
}

/**
 * The binder top (§4.2): no bar shape. The puffy title (24 sp, shrinking to 20 sp to stay on one line, then an
 * ellipsis; 20 sp collapsed), the sun sticker (the moon in Dusk and Glow), the subtitle "★ Hillcrest Market · 8 stops"
 * on a pad patch (hidden collapsed); on the right the LCD and the 40 dp More round button. 72 dp, 56 dp collapsed; it
 * grows with large text.
 */
@Composable
private fun AppBar(
    state: ListUiState,
    presentation: ListPresentation,
    anchors: AnchorRegistry,
    ui: TopStackUi,
    actions: ListActions,
    onMore: () -> Unit,
    onMoreBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val reduced = LocalReducedMotion.current
    val collapsed = ui.barCollapsed
    val minHeight by animateDpAsState(
        if (collapsed) PicnicTheme.dimens.appBarCollapsed else PicnicTheme.dimens.appBar,
        animationSpec = if (reduced) snap() else tween(PicnicMotion.standard.durationMillis, easing = PicnicMotion.standard.easing),
        label = "appBar",
    )
    val moon = c.variant == ThemeVariant.Dusk || c.variant == ThemeVariant.Glow
    val title = state.title.ifEmpty { stringResource(R.string.app_name) }
    val subtitle = when {
        state.loading -> null
        state.total == 0 -> state.storeName.ifBlank { null }
        else -> {
            val stops = pluralStringResource(R.plurals.list_stops, state.stops, state.stops)
            if (state.storeName.isBlank()) stops else stringResource(R.string.list_subtitle, state.storeName, stops)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PuffyText(
                    title,
                    style = if (collapsed) t.screenTitleCollapsed else t.screenTitle,
                    fill = c.ink,
                    stroke = 6.dp,
                    minFontSize = 20.sp,
                    heading = true,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val spin = remember { Animatable(0f) }
                LaunchedEffect(ui.sunSpin) {
                    if (ui.sunSpin == 0 || reduced) return@LaunchedEffect
                    withContext(FixedMotionScale) {
                        spin.snapTo(0f)
                        spin.animateTo(360f, tween(400))
                        spin.snapTo(0f)
                    }
                }
                with(anchors) {
                    SunSticker(
                        onTap = if (moon) actions.onMoonTap else actions.onSunTap,
                        modifier = Modifier.anchor(AnchorRegistry.SUN).graphicsLayer { rotationZ = spin.value },
                    )
                }
            }
            if (subtitle != null && !collapsed) {
                PadPatch(Modifier.padding(start = 2.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        FlatStar(Modifier.size(13.dp), fill = Families.lemon.mid)
                        Text(subtitle, style = t.appBarSubtitle.copy(color = c.onPadSoft))
                    }
                }
            }
        }
        if (!state.loading) {
            val lcd = when {
                ui.syncing -> state.lcd.copy(mode = LcdMode.Text, text = SYNC)
                else -> {
                    val shown = presentation.lcdCount(state.checked)
                    val mode = presentation.lcd?.mode ?: state.lcd.mode
                    state.lcd.copy(count = shown, mode = mode)
                }
            }
            with(anchors) {
                LcdChip(
                    lcd,
                    Modifier.padding(horizontal = 2.dp).anchor(AnchorRegistry.LCD),
                    onHold = if (state.eggs) actions.onLcdHold else null,
                )
            }
        }
        Box(
            Modifier
                .testTag(ListTags.MORE)
                .onGloballyPositioned { onMoreBounds(it.boundsInWindow()) },
        ) {
            PicnicIconButton(PicnicGlyph.More, stringResource(R.string.theme_more_options), onMore)
        }
    }
}

/** The LCD word while the list re-syncs (§6.1). */
private const val SYNC: String = "SYNC"
