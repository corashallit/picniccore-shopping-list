package earth.cora.picnic.ui.add

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import earth.cora.picnic.core.assistant.PhotoLine
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.a11y.focusRingIndication
import earth.cora.picnic.ui.a11y.spokenItemLabel
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.Holo
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LoaderKind
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicLoader
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.WashiPattern
import earth.cora.picnic.ui.theme.WashiTape
import earth.cora.picnic.ui.theme.drawGlint
import earth.cora.picnic.ui.theme.hardShadow
import earth.cora.picnic.ui.theme.rememberLoopTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Snap (docs/DESIGN_STICKER.md §4.19, items 207, 211): the polaroid (150 dp, a 9 dp white frame with a 26 dp lip, hs2,
 * −4°, washi on two corners, a Klee One caption from the capture day); while reading, a 40 dp holo glint band sweeps every
 * 2.6 s and a 3 dp sakura scan line runs top to bottom beside the bee loop; after the answer the lines read off the paper
 * appear one by one (250 ms apart; crossed-out lines struck in ballpointSoft and dropped; corrections as "bread →
 * sourdough") and each kept line pops into a sticker row ("Popped into stickers 4 of 7"). Owner: WP7.
 */

const val POLAROID_TAG: String = "add_polaroid"
const val POPPED_TRAY_TAG: String = "add_popped_tray"

private val PolaroidWidth: Dp = 150.dp
private const val SCAN_PERIOD_MS = 2600L

/** Test tag of the polaroid's caption. */
const val POLAROID_CAPTION_TAG: String = "add_polaroid_caption"

/**
 * The polaroid. [reading] runs the glint and the scan line (still under reduced motion). It is a fixed-size object (150
 * dp, like the cassette), so its decorative print (the caption, "no photo yet") is drawn at font scale 1 and always fits
 * its lip; TalkBack hears "Your photo".
 */
@Composable
fun Polaroid(photo: SnapPhoto?, reading: Boolean, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = reading && !reduced)
    // Offset so a still frame shows the scan line part-way down rather than at the top.
    val phase = ((t + SCAN_PERIOD_MS * 2 / 5) % SCAN_PERIOD_MS).toFloat() / SCAN_PERIOD_MS
    val caption = if (photo != null) stringResource(R.string.add_snap_caption, dayOf(photo.takenAt)) else ""
    val description = stringResource(R.string.add_snap_photo)
    val shape = PicnicTheme.shapes.toast
    Box(
        modifier
            .width(PolaroidWidth + 16.dp)
            .testTag(POLAROID_TAG)
            .semantics { contentDescription = description },
    ) {
        Column(
            Modifier
                .padding(top = 8.dp, start = 8.dp)
                .width(PolaroidWidth)
                .graphicsLayer { rotationZ = -4f }
                .hardShadow(shape, PicnicTheme.shadows, large = true)
                .background(PicnicTheme.colors.stickerWhite, shape)
                .padding(start = 9.dp, end = 9.dp, top = 9.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(PHOTO_HEIGHT)
                    .clip(PicnicTheme.shapes.toast)
                    .background(Families.periwinkle.pastel.copy(alpha = 0.5f))
                    .drawWithContent {
                        drawContent()
                        if (reading) {
                            if (!reduced) drawGlint(phase, Holo.glint.copy(widthDp = 40f, angleDeg = 25f, alpha = 0.45f))
                            // The sakura scan line (still at a third of the way under reduced motion).
                            val y = (if (reduced) 0.33f else phase) * size.height
                            clipRect { drawRect(Families.sakura.mid, Offset(0f, y - 1.5.dp.toPx()), Size(size.width, 3.dp.toPx())) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val thumb = photo?.thumb
                if (thumb != null) {
                    Image(thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else if (photo == null) {
                    // Only an empty polaroid says so; a photo still being decoded (or one too odd to show small) is blank.
                    PaperTheme {
                        FixedFontScale {
                            Text(
                                stringResource(R.string.add_snap_no_photo),
                                style = PicnicTheme.type.transcript.copy(fontSize = 13.sp, lineHeight = 18.sp, color = PicnicTheme.colors.ballpointSoft),
                                modifier = Modifier.clearAndSetSemantics { },
                            )
                        }
                    }
                }
            }
            // The 26 dp lip; it would grow (rather than cut the caption) if the print ever needed more.
            Box(Modifier.fillMaxWidth().heightIn(min = 26.dp), contentAlignment = Alignment.CenterStart) {
                PaperTheme {
                    FixedFontScale {
                        var lost by remember { mutableStateOf(false) }
                        Text(
                            caption,
                            style = PicnicTheme.type.transcript.copy(fontSize = 13.sp, lineHeight = 18.sp, color = PicnicTheme.colors.ballpointSoft),
                            onTextLayout = { lost = it.lostText() },
                            modifier = Modifier.decorativeText(POLAROID_CAPTION_TAG, lost),
                        )
                    }
                }
            }
        }
        // Washi on two corners: gingham −38° top-left, lemon dots +38° top-right.
        WashiTape(WashiPattern.PinkGingham, Modifier.offset(x = (-6).dp, y = 10.dp), length = 46.dp, height = 16.dp, rotation = -38f)
        WashiTape(WashiPattern.LemonDots, Modifier.offset(x = 122.dp, y = 0.dp), length = 46.dp, height = 16.dp, rotation = 38f)
    }
}

private val PHOTO_HEIGHT: Dp = 132.dp

/** "Sat" for the capture day (Canadian English, the device's zone). */
internal fun dayOf(millis: Long?): String {
    val at = millis ?: return ""
    return DateTimeFormatter.ofPattern("EEE", Locale.CANADA).format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
}

/**
 * The lines read off the paper (§4.19), on ruled lines in Klee One 15 / 22 sp: while reading, the bee loop and
 * "Reading your list…"; then each line as it lands, a tick at its left; crossed-out lines struck in ballpointSoft;
 * corrections "bread → sourdough".
 */
@Composable
fun PhotoLines(lines: List<PhotoLine>, reading: Boolean, clock: RevealClock?, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val shown = if (reading) 0 else clock?.linesShown() ?: lines.size
    val revealing = !reading && clock != null && !clock.done
    Column(modifier) {
        if (reading || revealing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Glyph(PicnicGlyph.Sparkle, null, tint = c.ink, size = 18.dp)
                Text(
                    stringResource(R.string.add_snap_reading),
                    style = t.label.copy(fontSize = 15.sp, lineHeight = 20.sp, color = c.ink),
                    // §15 "loader labels once": TalkBack hears that the photo is being read (the bee loop is silent).
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
        }
        if (reading) {
            PicnicLoader(LoaderKind.BeeLoop, label = null, modifier = Modifier.padding(top = 6.dp))
        }
        lines.take(shown).forEachIndexed { i, line ->
            val age = clock?.since(clock.plan.lineAt.getOrElse(i) { 0 }) ?: Long.MAX_VALUE
            PhotoLineRow(line, landed = age >= RevealPlan.TICK_MILLIS)
        }
    }
}

@Composable
private fun PhotoLineRow(line: PhotoLine, landed: Boolean) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val crossed = stringResource(R.string.add_snap_crossed)
    val text: AnnotatedString = buildAnnotatedString {
        val correction = line.correctionOf
        if (line.crossedOut) {
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = c.inkSoft)) { append(line.text) }
        } else if (correction != null) {
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = c.inkSoft)) { append(correction) }
            // The arrow stays with the correction's new word when the line wraps.
            append(" →\u00A0")
            append(line.text)
        } else {
            append(line.text)
        }
    }
    val spoken = if (line.crossedOut) "${line.text}, $crossed" else line.correctionOf?.let { "$it → ${line.text}" } ?: line.text
    val lost = rememberLostText()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .drawBehind { drawRect(c.rule, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) }
            .clearedWith(lost) { contentDescription = spoken },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tick = Modifier.padding(top = 4.dp)
        if (line.crossedOut) {
            Spacer(tick.size(16.dp))
        } else {
            Glyph(if (landed) PicnicGlyph.Check else PicnicGlyph.Sparkle, null, tint = if (landed) c.ink else c.inkSoft, size = 16.dp, modifier = tick)
        }
        Text(text, style = t.transcript.copy(color = if (line.crossedOut) c.inkSoft else c.ink), onTextLayout = lost.layout(0))
    }
}

/**
 * "Popped into stickers · 4 of 7" (§4.19): the photo's rows in a periwinkle tray, two per line (one at large text), each
 * popping in at its beat; a cell is the keep switch (tap to leave one out; a left-out cell fades).
 */
@Composable
fun PoppedTray(rows: List<ReviewRow>, order: List<Int>, clock: RevealClock?, onToggleKeep: (Int) -> Unit, modifier: Modifier = Modifier) {
    val byKey = rows.associateBy { it.key }
    val ordered = order.mapNotNull(byKey::get)
    val popped = ordered.count { clock?.rowShown(it.key) ?: true }
    StickerTray(
        title = stringResource(R.string.add_snap_popped),
        count = stringResource(R.string.add_snap_popped_count, popped, ordered.size),
        family = Families.periwinkle,
        rows = ordered,
        clock = clock,
        onToggleKeep = onToggleKeep,
        modifier = modifier.testTag(POPPED_TRAY_TAG),
    )
}

/** A pastel tray of sticker cells with a puffy title and a count (the Snap and Speak trays). */
@Composable
internal fun StickerTray(
    title: String,
    count: String,
    family: earth.cora.picnic.ui.theme.StickerFamily,
    rows: List<ReviewRow>,
    clock: RevealClock?,
    onToggleKeep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    PaperTheme {
        val c = PicnicTheme.colors
        val t = PicnicTheme.type
        val shape = PicnicTheme.shapes.section
        val columns = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f) 1 else 2
        Column(
            modifier
                .fillMaxWidth()
                .hardShadow(shape, PicnicTheme.shadows)
                .clip(shape)
                .background(family.pastel)
                .border(PicnicTheme.dimens.border, c.stickerWhite, shape),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 6.dp).semantics(mergeDescendants = true) { heading() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.stickerWhite)
                        .border(2.dp, family.trace, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Glyph(PicnicGlyph.Sparkle, null, tint = family.trace, size = 14.dp) }
                Text(title, style = t.smallTitle.copy(fontSize = 17.sp, lineHeight = 22.sp, color = c.ballpoint), modifier = Modifier.weight(1f))
                Text(count, style = t.label.copy(color = c.ballpoint), maxLines = 1)
            }
            // Only the stickers that have popped take a place: the tray fills as they land.
            val shownRows = rows.filter { clock?.rowShown(it.key) ?: true }
            if (shownRows.isNotEmpty()) {
                Column(
                    Modifier
                        .padding(horizontal = 5.dp)
                        .fillMaxWidth()
                        .clip(PicnicTheme.shapes.sectionInner)
                        .background(c.pad)
                        .border(1.5.dp, family.mid.copy(alpha = 0.6f), PicnicTheme.shapes.sectionInner),
                ) {
                    shownRows.chunked(columns).forEachIndexed { line, pair ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .drawBehind { if (line > 0) drawRect(c.rule, Offset.Zero, Size(size.width, 1.dp.toPx())) },
                        ) {
                            pair.forEach { row -> StickerCell(row, clock, onToggleKeep, Modifier.weight(1f)) }
                            if (pair.size < columns) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(12.dp).decorative().drawBehind {
                    // A row of tiny stars on the cap (the tray's pattern).
                    val step = 12.dp.toPx()
                    var x = step / 2
                    while (x < size.width) {
                        drawCircle(family.mid.copy(alpha = 0.55f), 1.6.dp.toPx(), Offset(x, size.height / 2))
                        x += step
                    }
                },
            )
        }
    }
}

/**
 * One sticker cell (40 dp sticker, name, detail): the cell is its keep switch (Role.Switch). A small keep spot on the
 * sticker's corner shows what a tap does, as the recipe card's spot does: kept = the lemon star pressed on; left out =
 * the empty dashed spot and a faded sticker. A row already on her list has no spot (it can never be added twice).
 * Pressed: the highlighter band behind the words; no Material ripple.
 */
@Composable
private fun StickerCell(row: ReviewRow, clock: RevealClock?, onToggleKeep: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val shown = clock?.rowShown(row.key) ?: true
    val age = clock?.since(clock.plan.rowAt[row.key] ?: 0) ?: Long.MAX_VALUE
    val pop = when {
        !shown -> 0f
        age < 83 -> 1.25f
        age < 166 -> 0.92f
        else -> 1f
    }
    val faded = !row.keep
    val detail = rowDetail(row)
    val label = spokenItemLabel(row.item.name, detail)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .heightIn(min = 52.dp)
            .then(
                if (row.canKeep) {
                    Modifier.toggleable(
                        value = row.keep,
                        interactionSource = interaction,
                        indication = focusRingIndication(),
                        role = Role.Switch,
                        onValueChange = { onToggleKeep(row.key) },
                    ).semantics { contentDescription = label }
                } else {
                    Modifier.clearAndSetSemantics { contentDescription = label }
                },
            )
            .graphicsLayer { alpha = if (shown) 1f else 0f }
            .padding(start = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The sticker with its keep spot on the corner (two cells share a line, so the spot is the small one).
        Box(Modifier.size(width = 50.dp, height = 48.dp)) {
            ItemIcon(
                row.icon,
                contentDescription = null,
                size = PicnicTheme.dimens.stickerCompact,
                aisleIndex = row.aisleIndex,
                modifier = Modifier
                    .graphicsLayer { scaleX = pop; scaleY = pop }
                    .then(if (faded) Modifier.skippedSticker() else Modifier),
            )
            if (row.canKeep) {
                KeepSpot(
                    row.keep,
                    Modifier.align(Alignment.BottomEnd).graphicsLayer { scaleX = pop; scaleY = pop },
                    box = 24.dp,
                    spot = 20.dp,
                    star = 22.dp,
                )
            }
        }
        val lost = rememberLostText()
        Column(Modifier.weight(1f).padding(end = 6.dp).clearedWith(lost).pressedBand(pressed, c.pressedBand)) {
            Text(row.item.name, style = t.itemNameCompact.copy(color = if (faded) c.ballpointSoft else c.ballpoint), onTextLayout = lost.layout(0))
            if (detail != null) {
                Text(AddRules.keepTogether(detail), style = t.itemDetailCompact.copy(color = c.ballpointSoft), onTextLayout = lost.layout(1))
            }
        }
    }
}
