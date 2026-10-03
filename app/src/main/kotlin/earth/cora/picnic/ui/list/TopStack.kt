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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
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
import earth.cora.picnic.ui.theme.NameTag
import earth.cora.picnic.ui.theme.PadPatch
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicIconButton
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.PuffyText
import earth.cora.picnic.ui.theme.StickerBook
import earth.cora.picnic.ui.theme.SunSticker
import earth.cora.picnic.ui.theme.ThemeVariant
import kotlin.math.ceil
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
                // Above font scale 1.3 the stamp name tag joins the date's caption row (§7.4, §7.7, item 157).
                var captionTag by remember { mutableStateOf<String?>(null) }
                BookWithCaption(
                    caption = if (dateOnPage) null else { { BookDateCaption(book.dateLabel, season = book.season) } },
                    tag = captionTag?.let { text -> @Composable { NameTag(text) } },
                    book = {
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
                            onNameTag = if (dateOnPage) null else { text -> captionTag = text },
                        )
                    },
                )
            }
        }
    }
}

/**
 * The book under its date caption (large text, §7.4), and the stamp name tag while it holds: in the caption row after
 * the date, growing down over the book's right side as far as the book goes, never past it (so never over the list).
 * The tag lies over the caption and the book; it moves nothing.
 */
@Composable
private fun BookWithCaption(caption: (@Composable () -> Unit)?, tag: (@Composable () -> Unit)?, book: @Composable () -> Unit) {
    Layout(
        content = {
            if (caption != null) Box(Modifier.layoutId("caption")) { caption() }
            Box(Modifier.layoutId("book")) { book() }
            if (tag != null) Box(Modifier.layoutId("tag")) { tag() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = 6.dp.roundToPx()
        val captionP = measurables.firstOrNull { it.layoutId == "caption" }?.measure(loose)
        val bookP = measurables.first { it.layoutId == "book" }.measure(loose)
        val bookY = captionP?.let { it.height + gap } ?: 0
        val width = maxOf(bookP.width, captionP?.width ?: 0)
        val height = bookY + bookP.height
        val tagX = captionP?.let { it.width + 8.dp.roundToPx() } ?: 0
        val tagP = measurables.firstOrNull { it.layoutId == "tag" }?.measure(
            Constraints(maxWidth = (width - tagX).coerceAtLeast(0), maxHeight = height.coerceAtLeast(0)),
        )
        layout(width, height) {
            captionP?.place(0, 0)
            bookP.place(0, bookY)
            tagP?.place(tagX, 0, zIndex = 1f)
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
    val large = LocalDensity.current.fontScale >= LARGE_TEXT
    val spin = remember { Animatable(0f) }
    LaunchedEffect(ui.sunSpin) {
        if (ui.sunSpin == 0 || reduced) return@LaunchedEffect
        withContext(FixedMotionScale) {
            spin.snapTo(0f)
            spin.animateTo(360f, tween(400))
            spin.snapTo(0f)
        }
    }
    val titleRow: @Composable (Modifier) -> Unit = { m ->
        Row(m, verticalAlignment = Alignment.CenterVertically) {
            PuffyText(
                title,
                style = if (collapsed) t.screenTitleCollapsed else t.screenTitle,
                fill = c.ink,
                stroke = 6.dp,
                minFontSize = 20.sp,
                // One line at every font scale (§4.2, item 24): it shrinks 24 → 20 sp, then ellipsizes with its stroke
                // whole (item 334); at large text the bar grows to two rows, and TalkBack reads the whole title.
                maxLines = 1,
                heading = true,
                modifier = Modifier.weight(1f, fill = false),
            )
            with(anchors) {
                SunSticker(
                    onTap = if (moon) actions.onMoonTap else actions.onSunTap,
                    modifier = Modifier
                        .overhang(SUN_ROOM_W, SUN_ROOM_H)
                        .anchor(AnchorRegistry.SUN)
                        .graphicsLayer { rotationZ = spin.value },
                )
            }
        }
    }
    val subtitleRow: @Composable (Modifier) -> Unit = { m ->
        if (subtitle != null && !collapsed) {
            PadPatch(m.padding(start = 2.dp, bottom = 4.dp)) {
                // Large text may wrap the line: the star stays on the first line and the patch hugs the widest one.
                val style = t.appBarSubtitle.copy(color = c.onPadSoft)
                val firstLine = with(LocalDensity.current) { style.lineHeight.toDp() }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.heightIn(min = firstLine), contentAlignment = Alignment.Center) {
                        FlatStar(Modifier.size(13.dp), fill = Families.lemon.mid)
                    }
                    SnugText(subtitle, style)
                }
            }
        }
    }
    val lcdChip: @Composable () -> Unit = {
        // An empty list has nothing to count: the LCD comes with the first item (§4.21).
        if (!state.loading && state.total > 0) {
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
    }
    val more: @Composable () -> Unit = {
        Box(
            Modifier
                .testTag(ListTags.MORE)
                .onGloballyPositioned { onMoreBounds(it.boundsInWindow()) },
        ) {
            PicnicIconButton(PicnicGlyph.More, stringResource(R.string.theme_more_options), onMore)
        }
    }
    if (large) {
        // Large text (§4.2): the title and More on the first row, the subtitle (wrapping) and the LCD under them.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(start = 8.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                titleRow(Modifier.weight(1f))
                more()
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { subtitleRow(Modifier) }
                lcdChip()
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                titleRow(Modifier)
                subtitleRow(Modifier)
            }
            lcdChip()
            more()
        }
    }
}

/**
 * From this font scale the bar takes two rows (§4.2: the bar grows): beside the LCD and More, "Saturday shop" would
 * already ellipsize at 1.3 even at its 20 sp floor. The book keeps its date on the page up to 1.3 (§7.4).
 */
private const val LARGE_TEXT: Float = 1.3f

/**
 * Lays out a 48 dp target as [w] × [h] dp, centred on it, so the 22 dp sun sits by the title without taking the
 * title's room: the target still receives touches over its full 48 dp (it is placed after the title, and before the LCD
 * and More, so those keep their own touches where they overlap).
 */
private fun Modifier.overhang(w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp): Modifier =
    layout { measurable, _ ->
        val p = measurable.measure(androidx.compose.ui.unit.Constraints())
        val lw = w.roundToPx()
        val lh = h.roundToPx()
        layout(lw, lh) { p.place((lw - p.width) / 2, (lh - p.height) / 2) }
    }

/**
 * Text that, when it wraps, is only as wide as its widest line (plain Text takes the whole width it was offered), so
 * the pad patch around a wrapped subtitle has no empty tail.
 */
@Composable
private fun SnugText(text: String, style: TextStyle) {
    val measurer = rememberTextMeasurer()
    Layout(content = { Text(text, style = style) }) { measurables, constraints ->
        val probe = measurer.measure(text, style, constraints = Constraints(maxWidth = constraints.maxWidth))
        val widest = (0 until probe.lineCount).maxOf { probe.getLineRight(it) - probe.getLineLeft(it) }
        val w = if (probe.lineCount > 1) ceil(widest).toInt().coerceIn(constraints.minWidth, constraints.maxWidth) else constraints.maxWidth
        val p = measurables.single().measure(constraints.copy(minWidth = 0, maxWidth = w))
        layout(p.width, p.height) { p.place(0, 0) }
    }
}

/** The sun's layout room by the title (its face is 22 dp, its target 48 dp). */
private val SUN_ROOM_W = 30.dp
private val SUN_ROOM_H = 32.dp

/** The LCD word while the list re-syncs (§6.1). */
private const val SYNC: String = "SYNC"
