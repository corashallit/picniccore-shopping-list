package earth.cora.picnic.ui.album

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import earth.cora.picnic.R
import earth.cora.picnic.ui.common.MoreMenuButton
import earth.cora.picnic.ui.common.ScreenGutter
import earth.cora.picnic.ui.common.SecondaryScreen
import earth.cora.picnic.ui.settings.backToListLabel
import earth.cora.picnic.ui.theme.EmptyState
import earth.cora.picnic.ui.theme.LcdChip
import earth.cora.picnic.ui.theme.LcdMode
import earth.cora.picnic.ui.theme.LcdState
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.MenuItem
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.rememberLoopTime

/** Everything the album can ask for. Defaults do nothing. */
@Stable
class AlbumActions(
    val onBack: () -> Unit = {},
    val onSelectTab: (AlbumTab) -> Unit = {},
    val onAppeared: (key: String) -> Unit = {},
    /** A newly filed trip book's row played its magic appear (§7.8). */
    val onBookAppeared: (bookId: String) -> Unit = {},
    val onBackToList: () -> Unit = {},
    val onOpenMyStore: () -> Unit = {},
    val onOpenPantry: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

@Composable
fun AlbumRoute(
    vm: AlbumViewModel,
    onBack: () -> Unit,
    onBackToList: () -> Unit,
    onOpenMyStore: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPantry: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val actions = remember(vm) {
        AlbumActions(
            onBack = onBack,
            onSelectTab = vm::selectTab,
            onAppeared = vm::appeared,
            onBookAppeared = vm::bookAppeared,
            onBackToList = onBackToList,
            onOpenMyStore = onOpenMyStore,
            onOpenPantry = onOpenPantry,
            onOpenSettings = onOpenSettings,
        )
    }
    AlbumScreen(state, actions)
}

/** One slice of the vinyl page: a group title, a row of four pockets, or a seasonal card. */
@Immutable
private sealed interface PageSlice {
    val key: String

    data class Title(override val key: String, val text: String) : PageSlice
    data class Pockets(override val key: String, val pockets: List<PocketUi>) : PageSlice
    data class Season(override val key: String, val card: SeasonCardUi) : PageSlice
}

/** Test tags. */
object AlbumTags {
    const val LIST: String = "album_list"
    fun pocket(key: String): String = "album_pocket_$key"
}

/**
 * The sticker album (§6.10): Back, "Sticker album", "★ 64 of 216 collected · 3 holo" on a pad patch, the LCD "64/216"
 * and More; the Groceries · Picnic · Seasonal tabs over the pink pinstripe vinyl page of pockets (4 across); then the
 * trip sticker books (newest first; a tap opens one read-only), the perfect-weeks card and, once open, the back pocket.
 * Tap a pocket to lift its sticker (tap again or elsewhere to drop it); long-press a face pocket and it blinks.
 */
@Composable
fun AlbumScreen(
    state: AlbumUiState,
    actions: AlbumActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    initialLifted: String? = null,
    initialBook: String? = null,
    initialBlink: String? = null,
) {
    var lifted by rememberSaveable { mutableStateOf(initialLifted) }
    var openBook by rememberSaveable { mutableStateOf(initialBook) }
    var menu by remember { mutableStateOf(false) }
    var blinks by remember { mutableStateOf(initialBlink?.let { mapOf(it to 1) } ?: emptyMap()) }
    val reduced = LocalReducedMotion.current
    val loop by rememberLoopTime(enabled = !reduced)
    val cycle by remember { derivedStateOf { loop / WOBBLE_MILLIS } }

    val subtitle = if (state.holo > 0) {
        stringResource(R.string.album_subtitle, state.collected, state.collectible, state.holo)
    } else {
        stringResource(R.string.album_subtitle_no_holo, state.collected, state.collectible)
    }
    val lcdLabel = stringResource(R.string.album_lcd_label, state.collected, state.collectible)
    val drawnTitle = stringResource(R.string.album_drawn_for_you)
    val picnicTitle = stringResource(R.string.album_picnic_sheet)
    val sakuraTitle = stringResource(R.string.album_sakura_sheet)
    val slices = remember(state.tab, state.groceries, state.picnic, state.sakura, state.seasons) {
        pageSlices(state, drawnTitle, picnicTitle, sakuraTitle)
    }
    val empties = remember(slices) { slices.filterIsInstance<PageSlice.Pockets>().flatMap { it.pockets }.filterNot { it.collected }.map { it.key } }
    val wobbleKey = if (cycle > 0 && empties.isNotEmpty()) empties[Math.floorMod(cycle * 7919L, empties.size.toLong()).toInt()] else null

    val largeText = LocalDensity.current.fontScale > LARGE_TEXT
    val lcd: @Composable () -> Unit = {
        Box(Modifier.clearAndSetSemantics { contentDescription = lcdLabel }) {
            // "64/216" (§6.10): the count without the trip LCD's star.
            LcdChip(LcdState(mode = LcdMode.Text, text = "${state.collected}/${state.collectible}"), pitch = 1.6.dp)
        }
    }

    SecondaryScreen(
        title = stringResource(R.string.album_title),
        subtitle = if (state.loading) null else subtitle,
        onBack = actions.onBack,
        modifier = modifier,
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!state.loading && !largeText) lcd()
                MoreMenuButton(
                    items = listOf(
                        MenuItem(backToListLabel(state.listTitle), PicnicGlyph.Back, actions.onBackToList),
                        MenuItem(stringResource(R.string.menu_my_store), PicnicGlyph.Aisles, actions.onOpenMyStore),
                        // §6.13: My pantry sits between My store & aisles and the last item.
                        MenuItem(stringResource(R.string.menu_my_pantry), PicnicGlyph.Jar, actions.onOpenPantry),
                        MenuItem(stringResource(R.string.menu_settings), PicnicGlyph.Settings, actions.onOpenSettings),
                    ),
                    expanded = menu,
                    onExpandedChange = { menu = it },
                )
            }
        },
        // With large text the LCD steps down beside the subtitle, so the title keeps the width (§4.2).
        subtitleTrailing = if (largeText && !state.loading) lcd else null,
        overlay = {
            val row = state.books.firstOrNull { it.id == openBook }
            FiledBookSheet(book = row?.book, dateLabel = row?.dateLabel.orEmpty(), onClose = { openBook = null })
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(AlbumTags.LIST)
                .pointerInput(Unit) {
                    // A tap that no pocket took (anywhere else on the page) drops the lifted sticker.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                        if (waitForUpOrCancellation(PointerEventPass.Final) != null) lifted = null
                    }
                },
            contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, top = 8.dp, bottom = 32.dp),
        ) {
            if (state.loading) return@LazyColumn
            if (state.empty) {
                item("empty") {
                    EmptyState(stringResource(R.string.album_empty_title), stringResource(R.string.album_empty_body))
                }
                item("bottom") { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) }
                return@LazyColumn
            }
            item("tabs") {
                AlbumTabs(state.tab, onSelect = {
                    lifted = null
                    actions.onSelectTab(it)
                })
            }
            itemsIndexed(slices, key = { _, s -> s.key }, contentType = { _, s -> s::class }) { i, slice ->
                val top = i == 0
                val bottom = i == slices.lastIndex
                Box(
                    Modifier
                        .fillMaxWidth()
                        .vinylSlice(top = top, bottom = bottom)
                        .padding(start = 8.dp, end = 8.dp, top = if (top) 8.dp else 3.dp, bottom = if (bottom) 10.dp else 3.dp),
                ) {
                    when (slice) {
                        is PageSlice.Title -> VinylTitle(slice.text)
                        is PageSlice.Season -> SeasonCard(
                            slice.card,
                            nextLabel = slice.card.nextDate?.let {
                                stringResource(R.string.album_season_next, slice.card.sheet.holiday, AlbumViewModel.MONTH_DAY.format(it))
                            },
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        is PageSlice.Pockets -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            slice.pockets.forEach { p ->
                                Pocket(
                                    p,
                                    lifted = lifted == p.key,
                                    onTap = { lifted = if (lifted == p.key) null else p.key },
                                    onLongPress = {
                                        if (p.face) blinks = blinks + (p.key to (blinks[p.key] ?: 0) + 1)
                                    },
                                    modifier = Modifier.weight(1f).testTag(AlbumTags.pocket(p.key)),
                                    blinkKey = blinks[p.key] ?: 0,
                                    wobble = p.key == wobbleKey,
                                    onAppeared = { actions.onAppeared(p.key) },
                                )
                            }
                            repeat(POCKETS_PER_ROW - slice.pockets.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (state.books.isNotEmpty()) {
                item("books") {
                    TripBooksCard(state.books, onOpen = { openBook = it.id }, modifier = Modifier.padding(top = 18.dp), onAppeared = actions.onBookAppeared)
                }
            }
            item("perfect") {
                PerfectWeeksCard(state.perfectStreak, state.perfectEarned, state.perfectHolo, Modifier.padding(top = 26.dp))
            }
            if (state.backPocketOpen) {
                item("back") { BackPocketCard(Modifier.padding(top = 18.dp)) }
            }
            item("bottom") {
                Spacer(Modifier.height(8.dp).windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
            }
        }
    }
}

/** Pockets per row of the vinyl page (§4.25). */
const val POCKETS_PER_ROW: Int = 4

/** From this font scale the LCD leaves the binder top's right side for the subtitle's row. */
private const val LARGE_TEXT = 1.15f

/** A random "?" wobbles every 6 s (§4.25). */
private const val WOBBLE_MILLIS: Long = 6000

private fun pageSlices(state: AlbumUiState, drawnTitle: String, picnicTitle: String, sakuraTitle: String): List<PageSlice> = buildList {
    fun pockets(prefix: String, list: List<PocketUi>) {
        list.chunked(POCKETS_PER_ROW).forEachIndexed { i, row -> add(PageSlice.Pockets("$prefix#$i", row)) }
    }
    when (state.tab) {
        AlbumTab.Groceries -> state.groceries.forEach { g ->
            val title = if (g.title == AlbumViewModel.DRAWN_FOR_YOU) drawnTitle else g.title
            add(PageSlice.Title("g:${g.title}", title))
            pockets("g:${g.title}", g.pockets)
        }
        AlbumTab.Picnic -> {
            add(PageSlice.Title("p:title", picnicTitle))
            pockets("p", state.picnic)
            state.sakura?.let {
                add(PageSlice.Title("s:title", sakuraTitle))
                pockets("s", it)
            }
        }
        AlbumTab.Seasonal -> state.seasons.forEach { add(PageSlice.Season("season:${it.sheet.id}", it)) }
    }
}
