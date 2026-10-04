package earth.cora.picnic.ui.add

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.a11y.focusRingIndication
import earth.cora.picnic.ui.theme.ButtonGloss
import earth.cora.picnic.ui.theme.FaceKind
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.LcdChip
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.LcdMode
import earth.cora.picnic.ui.theme.LcdState
import earth.cora.picnic.ui.theme.LoaderKind
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.LocalThemeVariant
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicLoader
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.hardShadow
import earth.cora.picnic.ui.theme.rememberPressFraction
import earth.cora.picnic.ui.theme.rememberLoopTime
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/*
 * Speak (docs/DESIGN_STICKER.md §4.20, items 210, 211, 333): a generic pink cassette (130 × 86 dp: sakura pastel body,
 * white border, sakura trace, a white label window with a lemon tape window, two pink reels with white spokes turning in 8
 * steps per second; no brand) with a 48 dp hanko-red stop sticker on its bottom-right corner (a 52 dp target); the dot equaliser (7 columns ×
 * 6 stacked 10 dp puffy dots, lemon / peach / sakura mid from the bottom, whole dots switching on from the bottom up in 3
 * stepped poses per 750 ms, driven by the mic level, never squashed); an LCD timer and a 10 dp hanko REC dot blinking 1.2 s
 * per cycle. Owner: WP7.
 */

const val CASSETTE_TAG: String = "add_cassette"
const val STOP_STICKER_TAG: String = "add_stop_recording"
const val RECORD_STICKER_TAG: String = "add_record"
const val RECORD_PILL_TAG: String = "add_record_pill"
const val EQUALISER_TAG: String = "add_equaliser"
const val REC_DOT_TAG: String = "add_rec_dot"
const val HEARD_TRAY_TAG: String = "add_heard_tray"

/** The reels' turn (§4.20, §8.5): 8 steps per second, 45° each. */
fun reelAngle(elapsedMs: Long): Float = ((elapsedMs / 125) % 8) * 45f

/** Lit dots (0..6, from the bottom) for a mic [level] (0..1): whole dots only, never squashed. */
fun litDots(level: Float): Int = ceil(level.coerceIn(0f, 1f) * EQ_ROWS - 0.0001f).toInt().coerceIn(0, EQ_ROWS)

/**
 * The equaliser's 7 columns from the level history (newest on the right), refreshed in 3 stepped poses per 750 ms (one
 * every 250 ms of [elapsedMs]). Under reduced motion every column shows the current level, still.
 */
fun equaliserColumns(levels: List<Float>, elapsedMs: Long, reduced: Boolean): List<Int> {
    if (levels.isEmpty()) return List(EQ_COLUMNS) { 0 }
    if (reduced) return List(EQ_COLUMNS) { litDots(levels.last()) }
    // The history as it was at the last 250 ms pose (polls are 100 ms apart).
    val pose = elapsedMs / EQ_POSE_MS
    val upTo = (levels.size - ((elapsedMs - pose * EQ_POSE_MS) / 100).toInt()).coerceIn(1, levels.size)
    val window = levels.subList(0, upTo).takeLast(EQ_COLUMNS)
    return List(EQ_COLUMNS - window.size) { 0 } + window.map(::litDots)
}

const val EQ_COLUMNS: Int = 7
const val EQ_ROWS: Int = 6
const val EQ_POSE_MS: Long = 250

/**
 * The cassette (§4.20). [turning] turns the reels (8 fps; still under reduced motion). [onStop] shows the stop sticker
 * on its corner (recording); [onRecord] the record sticker (idle). Decorative body; the stickers are buttons.
 */
@Composable
fun Cassette(turning: Boolean, modifier: Modifier = Modifier, onStop: (() -> Unit)? = null, onRecord: (() -> Unit)? = null) {
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = turning && !reduced)
    val angle = if (turning) reelAngle(t) else 0f
    val white = PicnicTheme.colors.stickerWhite
    val shadow = PicnicTheme.shadows.color
    Box(modifier.size(width = 150.dp, height = 104.dp).testTag(CASSETTE_TAG)) {
        Canvas(Modifier.size(width = 130.dp, height = 86.dp).decorative()) { drawCassette(angle, white, shadow) }
        val corner = Modifier.align(Alignment.BottomEnd)
        when {
            onStop != null -> HankoSticker(stop = true, label = stringResource(R.string.add_speak_stop), onClick = onStop, modifier = corner.testTag(STOP_STICKER_TAG))
            onRecord != null -> HankoSticker(stop = false, label = stringResource(R.string.add_speak_record), onClick = onRecord, modifier = corner.testTag(RECORD_STICKER_TAG))
        }
    }
}

/**
 * Draws the cassette body into 130 × 86 dp with the reels at [reelDegrees]: [white] is the sticker white (the die-cut,
 * the label window, the spokes, the gloss) and [shadow] the variant's hard-shadow colour, as every other sticker casts.
 */
private fun DrawScope.drawCassette(reelDegrees: Float, white: Color, shadow: Color) {
    val u = size.width / 130f
    val trace = Families.sakura.trace
    val body = Families.sakura.pastel
    val shadowAt = Offset(1.6f * u, 2.2f * u)
    val radius = CornerRadius(12f * u)
    // Hard shadow, white die-cut border, the body and its trace.
    drawRoundRect(shadow, topLeft = shadowAt, size = size, cornerRadius = radius)
    drawRoundRect(white, size = size, cornerRadius = radius)
    val inset = 3.5f * u
    val inner = Size(size.width - inset * 2, size.height - inset * 2)
    drawRoundRect(body, topLeft = Offset(inset, inset), size = inner, cornerRadius = CornerRadius(9f * u))
    drawRoundRect(trace, topLeft = Offset(inset, inset), size = inner, cornerRadius = CornerRadius(9f * u), style = Stroke(2f * u))
    // The puffy band along the bottom (the body's mid).
    val band = Path().apply {
        moveTo(inset + 20f * u, size.height - inset)
        lineTo(inset + 28f * u, size.height - 24f * u)
        lineTo(size.width - inset - 28f * u, size.height - 24f * u)
        lineTo(size.width - inset - 20f * u, size.height - inset)
        close()
    }
    drawPath(band, Families.sakura.mid)
    drawPath(band, trace, style = Stroke(2f * u, join = StrokeJoin.Round))
    drawCircle(white, 3.2f * u, Offset(size.width / 2f - 14f * u, size.height - 14f * u))
    drawCircle(trace, 3.2f * u, Offset(size.width / 2f - 14f * u, size.height - 14f * u), style = Stroke(1.5f * u))
    drawCircle(white, 3.2f * u, Offset(size.width / 2f + 14f * u, size.height - 14f * u))
    drawCircle(trace, 3.2f * u, Offset(size.width / 2f + 14f * u, size.height - 14f * u), style = Stroke(1.5f * u))
    // The white label window with the lemon tape window.
    val label = Size(96f * u, 36f * u)
    val labelAt = Offset((size.width - label.width) / 2f, 14f * u)
    drawRoundRect(white, topLeft = labelAt, size = label, cornerRadius = CornerRadius(7f * u))
    drawRoundRect(trace, topLeft = labelAt, size = label, cornerRadius = CornerRadius(7f * u), style = Stroke(1.5f * u))
    val tape = Size(40f * u, 9f * u)
    drawRoundRect(Families.lemon.pastel, topLeft = Offset((size.width - tape.width) / 2f, labelAt.y + (label.height - tape.height) / 2f), size = tape, cornerRadius = CornerRadius(4.5f * u))
    drawRoundRect(Families.lemon.trace, topLeft = Offset((size.width - tape.width) / 2f, labelAt.y + (label.height - tape.height) / 2f), size = tape, cornerRadius = CornerRadius(4.5f * u), style = Stroke(1.2f * u))
    // The two reels with white spokes.
    for (cx in listOf(labelAt.x + 16f * u, labelAt.x + label.width - 16f * u)) {
        val c = Offset(cx, labelAt.y + label.height / 2f)
        drawCircle(Families.sakura.mid, 11f * u, c)
        drawCircle(trace, 11f * u, c, style = Stroke(1.5f * u))
        rotate(reelDegrees, c) {
            for (k in 0 until 3) {
                val a = Math.toRadians((k * 120).toDouble())
                drawLine(white, c, Offset(c.x + (7.5f * u * cos(a)).toFloat(), c.y + (7.5f * u * sin(a)).toFloat()), 2.2f * u, cap = StrokeCap.Round)
            }
        }
        drawCircle(white, 3.2f * u, c)
        drawCircle(trace, 3.2f * u, c, style = Stroke(1.2f * u))
    }
    // The one gloss on the body (upper left).
    drawLine(white, Offset(inset + 10f * u, inset + 8f * u), Offset(inset + 26f * u, inset + 5.5f * u), 2.6f * u, cap = StrokeCap.Round)
}

/**
 * The 48 dp hanko-red sticker on the cassette's corner (§4.20) in a 52 dp target: stop (a white rounded square; "Stop
 * recording") or record (a white dot; "Record a voice note"). [StickerHanko] in every variant (stickers keep their
 * colours); 3 dp white border, hs, one gloss crescent and one dot (R3). Pressed: flat (the face drops onto its shadow
 * in 2 steps, §4.6); no Material ripple.
 */
@Composable
private fun HankoSticker(stop: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shadows = PicnicTheme.shadows
    val white = PicnicTheme.colors.stickerWhite
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPressFraction(pressed)
    Box(
        modifier
            .size(52.dp)
            .clickable(interaction, indication = focusRingIndication(CircleShape), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(HANKO_STICKER)
                .hardShadow(CircleShape, shadows.color, shadows.hs)
                .offset(shadows.hs.x * press, shadows.hs.y * press)
                .clip(CircleShape)
                .background(white)
                .padding(3.dp)
                .clip(CircleShape)
                .background(StickerHanko),
        ) {
            val g = ButtonGloss.of(FaceKind.Circle, size, null, this)
            drawPath(g.crescent, white)
            drawCircle(white, g.dotRadius, g.dotCenter)
            val c = Offset(size.width / 2f, size.height / 2f)
            if (stop) {
                val s = size.minDimension * 0.32f
                drawRoundRect(white, topLeft = Offset(c.x - s / 2f, c.y - s / 2f), size = Size(s, s), cornerRadius = CornerRadius(s * 0.2f))
            } else {
                drawCircle(white, size.minDimension * 0.17f, c)
            }
        }
    }
}

/** The stop / record sticker's face (§4.20). */
internal val HANKO_STICKER: Dp = 48.dp

/**
 * The dot equaliser (§4.20): 7 columns × 6 stacked 10 dp puffy dots (1.6 dp white border, a small hard shadow): lemon mid
 * (dots 1–2 from the bottom), peach mid (3–4), sakura mid (5–6); unlit dots are their pastel. Whole dots switch on from
 * the bottom up; never squashed. In Glow the unlit dots sink into the navy sheet (a faint pastel in a dim ring), so the
 * lit ones read as on, as they do on the Day pad. Decorative (the timer and the REC state carry the information).
 */
@Composable
fun DotEqualiser(columns: List<Int>, modifier: Modifier = Modifier) {
    val shadow = PicnicTheme.shadows
    val white = PicnicTheme.colors.stickerWhite
    val glow = LocalThemeVariant.current == ThemeVariant.Glow
    Canvas(modifier.size(width = EQ_WIDTH, height = EQ_HEIGHT).testTag(EQUALISER_TAG).decorative()) {
        val dot = 10.dp.toPx()
        val ring = 1.6.dp.toPx()
        val gapX = (size.width - EQ_COLUMNS * dot) / (EQ_COLUMNS - 1)
        val gapY = (size.height - EQ_ROWS * dot) / (EQ_ROWS - 1)
        for (col in 0 until EQ_COLUMNS) {
            val lit = columns.getOrElse(col) { 0 }
            for (row in 0 until EQ_ROWS) {
                val family = when (row) {
                    0, 1 -> Families.lemon
                    2, 3 -> Families.peach
                    else -> Families.sakura
                }
                val c = Offset(col * (dot + gapX) + dot / 2f, size.height - (row * (dot + gapY) + dot / 2f))
                val on = row < lit
                if (on) drawCircle(shadow.color, dot / 2f, c + Offset(1.dp.toPx(), 1.4.dp.toPx()))
                if (on || !glow) {
                    // The white die-cut disc, then the mid (lit) or the pastel (unlit, on the Day pad).
                    drawCircle(white, dot / 2f, c)
                    drawCircle(if (on) family.mid else family.pastel.copy(alpha = DAY_OFF_FILL), dot / 2f - ring, c)
                } else {
                    // Glow, unlit: sunk into the navy sheet, a faint pastel in a dim ring (no white disc behind it).
                    drawCircle(family.pastel.copy(alpha = GLOW_OFF_FILL), dot / 2f - ring, c)
                    drawCircle(white.copy(alpha = GLOW_OFF_RING), dot / 2f - ring / 2f, c, style = Stroke(ring))
                }
            }
        }
    }
}

/** An unlit dot's pastel: on the Day pad, and sunk into the Glow sheet (with a ring at 40 % white). */
private const val DAY_OFF_FILL = 0.75f
private const val GLOW_OFF_FILL = 0.28f
private const val GLOW_OFF_RING = 0.4f

private val EQ_WIDTH: Dp = 122.dp
private val EQ_HEIGHT: Dp = 82.dp

/** The 10 dp hanko REC dot with a white ring, blinking 1.2 s per cycle (0.83 Hz; steady under reduced motion). */
@Composable
fun RecDot(size: Dp = 10.dp, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val t by rememberLoopTime(enabled = !reduced)
    val on = reduced || (t % PicnicMotion.recBlinkPeriod) < PicnicMotion.recBlinkPeriod / 2
    Box(
        modifier
            .size(size + 4.dp)
            .testTag(REC_DOT_TAG)
            .decorative()
            .graphicsLayer { alpha = if (on) 1f else 0.25f }
            .clip(CircleShape)
            .background(PicnicTheme.colors.stickerWhite)
            .padding(2.dp)
            .clip(CircleShape)
            .background(StickerHanko),
    )
}

/**
 * The Speak section (§4.20, §6.2 "Speak"): the prompt ("Anything else? Say it out loud." once a review is open: after a
 * photo, D19, a note or an earlier voice note), the cassette with its corner sticker, the REC dot + LCD timer over the
 * equaliser while recording; the bee loop while the words are written down (the LCD keeps the recording's length, and
 * Stop stops it, as in the thinking state); then the transcript in quotes (Klee One 16 sp, the heard items on a
 * highlighter band). With a review open, a labelled "Record a voice note" pill sits under the cassette (the sheet's
 * footer is the review's "Stick all" then; with no review the footer carries the same label).
 */
@Composable
fun SpeakSection(
    phase: AddPhase,
    transcript: String?,
    afterReview: Boolean,
    heardNames: List<String>,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit = {},
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val reduced = LocalReducedMotion.current
    val recording = phase as? AddPhase.Recording
    val busy = phase.busy
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Glyph(PicnicGlyph.Cassette, null, tint = c.ink, size = 18.dp)
            Text(
                stringResource(if (afterReview) R.string.add_speak_after_photo else R.string.add_speak_prompt),
                style = t.label.copy(color = c.ink),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cassette(
                turning = recording != null,
                onStop = if (recording != null) onStop else null,
                onRecord = if (!busy) onRecord else null,
            )
            Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // While the words are written down the LCD keeps the length just recorded (it never jumps to 0:00).
                val elapsed = formatElapsed(recording?.elapsedMs ?: (phase as? AddPhase.Transcribing)?.elapsedMs ?: 0L)
                val timerLabel = stringResource(R.string.add_speak_recording, elapsed)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (recording != null) RecDot() else Spacer(Modifier.size(14.dp))
                    LcdChip(
                        LcdState(mode = LcdMode.Timer, text = elapsed),
                        modifier = Modifier.clearAndSetSemantics { if (recording != null) contentDescription = timerLabel },
                    )
                }
                val columns = remember(recording?.levels, recording?.elapsedMs, reduced) {
                    equaliserColumns(recording?.levels.orEmpty(), recording?.elapsedMs ?: 0L, reduced)
                }
                DotEqualiser(columns)
            }
        }
        if (afterReview && !busy) {
            PicnicButton(
                stringResource(R.string.add_speak_record),
                onClick = onRecord,
                style = PicnicButtonStyle.Secondary,
                glyph = PicnicGlyph.Cassette,
                modifier = Modifier.testTag(RECORD_PILL_TAG),
            )
        }
        if (phase is AddPhase.Transcribing) {
            PicnicLoader(LoaderKind.BeeLoop, label = stringResource(R.string.add_speak_transcribing), modifier = Modifier.fillMaxWidth())
            PicnicButton(stringResource(R.string.add_stop), onClick = onCancel, style = PicnicButtonStyle.Text, glyph = PicnicGlyph.Close)
        }
        if (transcript != null && phase !is AddPhase.Recording) Transcript(transcript, heardNames)
        if (recording != null) {
            Text(stringResource(R.string.add_speak_limit), style = t.caption.copy(color = c.inkSoft))
        }
    }
}

/**
 * The transcript in quotes (Klee One 16 sp on ruled lines), the heard items on a highlighter band (§4.20). Klee One is
 * a Japanese face that draws "…" as three dots at mid-height, so the ellipsis is shown as three full stops.
 */
@Composable
fun Transcript(transcript: String, names: List<String>, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val text = transcript.replace("\u2026", "...")
    val quoted = stringResource(R.string.add_speak_quoted, text)
    val offset = quoted.indexOf(text).coerceAtLeast(0)
    val ranges = remember(text, names) { AddRules.keywordRanges(text, names) }
    val annotated = buildAnnotatedString {
        append(quoted)
        for (r in ranges) {
            addStyle(SpanStyle(background = Families.lemon.pastel, color = c.ballpoint), r.first + offset, r.last + 1 + offset)
        }
    }
    Text(
        annotated,
        style = t.voiceTranscript.copy(color = c.ink),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    )
}

/** "1:05" for 65 s. */
internal fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** The Heard tray (§4.20): the rows the voice note found ("Heard · 2 more stickers" when they joined a review, D19). */
@Composable
fun HeardTray(rows: List<ReviewRow>, afterReview: Boolean, clock: RevealClock?, onToggleKeep: (Int) -> Unit, modifier: Modifier = Modifier) {
    val n = rows.count { it.keep }
    StickerTray(
        title = if (afterReview) pluralStringResource(R.plurals.add_speak_heard_more, n, n) else pluralStringResource(R.plurals.add_speak_heard, n, n),
        count = "",
        family = Families.sakura,
        rows = rows,
        clock = clock,
        onToggleKeep = onToggleKeep,
        modifier = modifier.testTag(HEARD_TRAY_TAG),
    )
}
