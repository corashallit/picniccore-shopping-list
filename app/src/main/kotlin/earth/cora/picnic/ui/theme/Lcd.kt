package earth.cora.picnic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The pastel LCD toy (§4.3, §13.1). Owner: WP4 (LcdGlyphsTest, LcdChipTest); WP6 and WP9 animate the pet.
 * PHASE-0 CONTRACT: the types and signatures below. The bodies are plain stand-ins until WP4 draws the
 * 5 × 7 dot matrix from WP2's LcdGlyphs.
 */

/** What the LCD shows (§13.1 `LcdMode`). */
enum class LcdMode {
    /** "10/24★": [LcdState.count] of [LcdState.total], the star (a moon in Dusk and Glow). */
    Count,

    /** The pixel bee (egg 15) + the count; the star hides. */
    Pet,

    /** One word: FED 1.5 s, SNACK? 2.6 s, BONUS★, SYNC 1.5 s, PRINT★ 1.7 s ([LcdState.text]). */
    Text,

    /** Settings "Test connection": OK, OK., OK.. ticking every 0.6 s ([LcdState.text]). */
    Ok,

    /** The Speak timer "0:12" ([LcdState.text]). */
    Timer,

    /** The book's page number "2/3" ([LcdState.text]). */
    Page,
}

/**
 * The LCD's content. TalkBack reads the count ("10 of 24 in the basket") whatever the mode shows, since
 * words are decorative. [receiptReady] exposes the long-press as the custom action "Print the trip receipt"
 * (only after the trip is complete and with Easter eggs on; the pet stays a secret).
 */
@Immutable
data class LcdState(
    val mode: LcdMode = LcdMode.Count,
    val count: Int = 0,
    val total: Int = 0,
    val text: String = "",
    val receiptReady: Boolean = false,
)

/** The pixel bee's sprite frames (egg 15, 9 × 7 px): bob 2 steps per second, a 2-frame chomp, a 4-frame dance. */
enum class PetPose { Rest, Bob, Chomp1, Chomp2, Dance1, Dance2, Dance3, Dance4 }

/**
 * A 5 × 7 dot-matrix line at [pitch] (app bar 1.9 dp, page number 1.2 dp, receipt 1.3 dp, settings 1.6 dp;
 * scaled with the font scale up to 1.6×). Lowercase prints as uppercase, accents fold, anything else is a
 * space. Sets the TextOverflowed semantics key when it does not fit. PHASE-0 STUB: monospace text.
 */
@Composable
fun LcdText(text: String, pitch: Dp, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, fontFamily = FontFamily.Monospace, color = PicnicTheme.colors.ink)
}

/**
 * The app-bar LCD chip (§4.3): face `lcd`, 3 dp white border, radius 9; digits tick on change; the star
 * blinks at 0.25 Hz. [onHold] fires after the hold threshold (Timeouts.holdThresholdMs): the receipt when
 * the trip is complete, otherwise the pet (D21). PHASE-0 STUB: a plain label, no hold.
 */
@Composable
fun LcdChip(state: LcdState, modifier: Modifier = Modifier, onHold: (() -> Unit)? = null) {
    val label = when (state.mode) {
        LcdMode.Count, LcdMode.Pet -> "${state.count}/${state.total}"
        else -> state.text
    }
    Box(
        modifier
            .background(PicnicTheme.colors.surfaceRaised, PicnicTheme.shapes.chip)
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .clearAndSetSemantics { contentDescription = label },
    ) {
        LcdText(label, pitch = 1.9.dp)
    }
}

/** The pixel bee in the LCD (egg 15). Drawn by WP4; WP6 (celebration dance) and WP9 (chomp, bob) pick the pose. PHASE-0 STUB: empty. */
@Composable
fun LcdPetSprite(pose: PetPose, modifier: Modifier = Modifier) {
    Box(modifier.size(18.dp, 14.dp))
}
