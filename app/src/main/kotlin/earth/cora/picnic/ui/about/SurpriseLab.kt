package earth.cora.picnic.ui.about

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import earth.cora.picnic.R
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.ui.common.PadCard
import earth.cora.picnic.ui.common.ScreenColumn
import earth.cora.picnic.ui.common.SecondaryScreen
import earth.cora.picnic.ui.eggs.BookReceiptSlip
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicSurface
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.WashiPattern
import earth.cora.picnic.ui.theme.WashiTape
import earth.cora.picnic.ui.theme.gen.StickerManifest

/*
 * The Surprise lab's SHELL (docs/DESIGN_STICKER.md §6.9, §12.3). WP8 owns the route, the screen frame and the tiles;
 * WP9 owns what the eggs do and the sandbox (SurpriseLabSandbox: the Appendix A fixture in an InMemoryStateStore, an
 * in-memory album and DataStore, a fake clock, discarded on leaving). WP9 provides its [SurpriseLab] through
 * [LocalSurpriseLab]; until then [ShellSurpriseLab] lists the 18 eggs with their "Try it" lines and fires nothing.
 */

/**
 * What a lab tile shows: a decor sticker (DecorManifest), a library sticker (StickerManifest; [Item.pixel] draws it as
 * the bonus stage's 8-bit sprite), the sun with the moon rising behind it (egg 1), or the book's receipt slip (egg 18).
 */
@Immutable
sealed interface LabSticker {
    data class Decor(val name: String) : LabSticker
    data class Item(val key: String, val pixel: Boolean = false) : LabSticker
    data object SunToMoon : LabSticker
    data object Receipt : LabSticker
}

/** One egg tile (§12.3): its number, name, "Try it" line and sticker; [needsAnimations] = pure motion (eggs 2, 3, 4, 17). */
@Immutable
data class LabEgg(val number: Int, val name: String, val tryIt: String, val sticker: LabSticker, val needsAnimations: Boolean = false)

/** The lab's content provider (WP9). */
interface SurpriseLab {
    /** The 18 eggs, in order. */
    @Composable
    fun eggs(): List<LabEgg>

    /** Whether [fire] does anything yet (false in the shell: its Try it buttons stay disabled). */
    val canFire: Boolean

    /** Plays egg [number] on the sandbox (never on her lists, §6.9). */
    fun fire(number: Int)

    /** The sandbox's practice list ("Surprise lab · practice list" ribbon), shown under the tile of the egg that plays. */
    @Composable
    fun Practice(modifier: Modifier) = Unit

    /** The egg whose tile the practice list sits under (the last one tried), or null before any Try it. */
    val practiceAfter: Int? get() = null

    /**
     * She left the lab: the sandbox is discarded. Called once when the lab screen leaves for good (any Back or pop), not
     * on a rotation.
     */
    fun close() = Unit
}

/** The lab WP9 fills; [ShellSurpriseLab] until then. */
val LocalSurpriseLab = staticCompositionLocalOf<SurpriseLab> { ShellSurpriseLab }

/** The shell: the 18 eggs from strings_screens.xml, nothing to fire. */
object ShellSurpriseLab : SurpriseLab {
    // Each sticker says what its egg does: the sun turning to moon, the moon of midnight, the bonus stage's 8-bit sprite,
    // the sakura flower of the perfect-weeks sheet, the receipt the LCD prints.
    private val stickers: List<LabSticker> = listOf(
        LabSticker.SunToMoon, LabSticker.Decor("ladybug"), LabSticker.Item("apple"), LabSticker.Decor("petal"),
        LabSticker.Decor("moon"), LabSticker.Decor("star"), LabSticker.Decor("kira"), LabSticker.Decor("cat"),
        LabSticker.Decor("cake"), LabSticker.Item("strawberry", pixel = true), LabSticker.Decor("pumpkin"), LabSticker.Decor("sun"),
        LabSticker.Item(StickerManifest.CAPSULE), LabSticker.Decor("flower"), LabSticker.Decor("bee"),
        LabSticker.Decor("umbrella"), LabSticker.Decor("ant"), LabSticker.Receipt,
    )
    private val motionOnly = setOf(2, 3, 4, 17)

    @Composable
    override fun eggs(): List<LabEgg> {
        val names = stringArrayResource(R.array.lab_egg_names)
        val tries = stringArrayResource(R.array.lab_egg_tries)
        return names.indices.map { i -> LabEgg(i + 1, names[i], tries[i], stickers[i], needsAnimations = (i + 1) in motionOnly) }
    }

    override val canFire: Boolean = false

    override fun fire(number: Int) = Unit
}

/**
 * The Surprise lab screen (§6.9): a note that everything runs on a practice list, then one sticker tile per egg with
 * its "Try it" line and a Try it button at the end of its title row; under reduced motion the pure-motion eggs say
 * "needs animations on" instead. The sandbox's practice list ([SurpriseLab.Practice]) opens right under the tile that was
 * tried, so the next egg is never a long scroll away. It works even with Easter eggs off.
 */
@Composable
fun SurpriseLabScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    lab: SurpriseLab = LocalSurpriseLab.current,
    scroll: ScrollState = rememberScrollState(),
) {
    val reduced = LocalReducedMotion.current
    // She left the lab, however she left (the Back button, system or predictive back, "Back to <list>", any pop): the
    // sandbox goes with the screen. A rotation recreates the screen without her leaving, so the sandbox stays then.
    val activity = LocalActivity.current
    DisposableEffect(lab) {
        onDispose { if (activity?.isChangingConfigurations != true) lab.close() }
    }
    SecondaryScreen(
        title = stringResource(R.string.lab_title),
        subtitle = stringResource(R.string.lab_subtitle),
        onBack = onBack,
        modifier = modifier,
    ) {
        ScreenColumn(state = scroll, spacing = 12.dp) {
            Box {
                PadCard {
                    Text(stringResource(R.string.lab_note), style = PicnicTheme.type.body.copy(color = PicnicTheme.colors.ink))
                }
                WashiTape(WashiPattern.LemonDots, Modifier.align(Alignment.TopCenter).offset(y = (-7).dp), length = 72.dp, rotation = -3f)
            }
            val after = lab.practiceAfter
            if (after == null) lab.Practice(Modifier.fillMaxWidth())
            for (egg in lab.eggs()) {
                EggTile(egg, reduced, lab)
                if (egg.number == after) lab.Practice(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun EggTile(egg: LabEgg, reduced: Boolean, lab: SurpriseLab) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val tryLabel = stringResource(R.string.lab_try_egg, egg.name)
    val blocked = reduced && egg.needsAnimations
    PicnicSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { TileSticker(egg.sticker) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.lab_egg_number, egg.number, egg.name), style = t.smallTitle.copy(color = c.ink))
                    Text(egg.tryIt, style = t.caption.copy(color = c.inkSoft))
                }
                if (!blocked) {
                    PicnicButton(
                        stringResource(R.string.lab_try),
                        onClick = { lab.fire(egg.number) },
                        enabled = lab.canFire,
                        style = PicnicButtonStyle.Secondary,
                        modifier = Modifier.semantics { contentDescription = tryLabel },
                    )
                }
            }
            if (blocked) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { NeedsMotionNote() }
            }
        }
    }
}

@Composable
private fun TileSticker(sticker: LabSticker) {
    when (sticker) {
        is LabSticker.Decor -> DecorSticker(sticker.name, size = 40.dp)
        is LabSticker.Item -> ItemIcon(IconRef(sticker.key), contentDescription = null, size = 44.dp, aisleIndex = 4, pixel = sticker.pixel)
        // The sun by the title, and the moon it turns into peeking out behind it.
        LabSticker.SunToMoon -> Box(Modifier.size(48.dp)) {
            DecorSticker("moon", Modifier.align(Alignment.BottomEnd), size = 26.dp)
            DecorSticker("sun", Modifier.align(Alignment.TopStart), size = 34.dp)
        }
        // The slip the LCD prints into the sticker book.
        LabSticker.Receipt -> BookReceiptSlip(Modifier.graphicsLayer { scaleX = 1.4f; scaleY = 1.4f })
    }
}

/**
 * Under reduced motion a pure-motion egg says it needs animations: a flat note with a dashed outline (no shadow, so it
 * never looks like a button), and where to turn them on.
 */
@Composable
private fun NeedsMotionNote() {
    val c = PicnicTheme.colors
    val ring = c.ballpointSoft
    Text(
        stringResource(R.string.lab_needs_motion),
        style = PicnicTheme.type.label.copy(color = c.ballpoint),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    ring,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
