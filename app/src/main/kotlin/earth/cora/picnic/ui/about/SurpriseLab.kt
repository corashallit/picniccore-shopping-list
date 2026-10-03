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
import earth.cora.picnic.ui.theme.ChipFace
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.Families
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

/** What a lab tile shows: a decor sticker (DecorManifest) or a library sticker (StickerManifest). */
@Immutable
sealed interface LabSticker {
    data class Decor(val name: String) : LabSticker
    data class Item(val key: String) : LabSticker
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

    /** The sandbox's practice list ("Surprise lab · practice list" ribbon), shown above the tiles while an egg plays. */
    @Composable
    fun Practice(modifier: Modifier) = Unit

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
    private val stickers: List<LabSticker> = listOf(
        LabSticker.Decor("moon"), LabSticker.Decor("ladybug"), LabSticker.Item("apple"), LabSticker.Decor("petal"),
        LabSticker.Decor("glowstar"), LabSticker.Decor("star"), LabSticker.Decor("kira"), LabSticker.Decor("cat"),
        LabSticker.Decor("cake"), LabSticker.Decor("star4"), LabSticker.Decor("pumpkin"), LabSticker.Decor("sun"),
        LabSticker.Item(StickerManifest.CAPSULE), LabSticker.Decor("heart"), LabSticker.Decor("bee"),
        LabSticker.Decor("umbrella"), LabSticker.Decor("ant"), LabSticker.Decor("ribbon"),
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
 * The Surprise lab screen (§6.9): a note that everything runs on a practice copy, the sandbox's practice list while an
 * egg plays ([SurpriseLab.Practice]), then one sticker tile per egg with its "Try it" line and a Try it button; under
 * reduced motion the pure-motion eggs say "needs animations on" instead. It works even with Easter eggs off.
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
            lab.Practice(Modifier.fillMaxWidth())
            for (egg in lab.eggs()) EggTile(egg, reduced, lab)
        }
    }
}

@Composable
private fun EggTile(egg: LabEgg, reduced: Boolean, lab: SurpriseLab) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val tryLabel = stringResource(R.string.lab_try_egg, egg.name)
    PicnicSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    when (val s = egg.sticker) {
                        is LabSticker.Decor -> DecorSticker(s.name, size = 40.dp)
                        is LabSticker.Item -> ItemIcon(IconRef(s.key), contentDescription = null, size = 44.dp, aisleIndex = 4)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.lab_egg_number, egg.number, egg.name), style = t.smallTitle.copy(color = c.ink))
                    Text(egg.tryIt, style = t.caption.copy(color = c.inkSoft))
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                if (reduced && egg.needsAnimations) {
                    Box(Modifier.padding(vertical = 8.dp)) {
                        ChipFace(fill = Families.neutral.pastel) {
                            Text(stringResource(R.string.lab_needs_motion), style = t.label.copy(color = c.ballpoint))
                        }
                    }
                } else {
                    PicnicButton(
                        stringResource(R.string.lab_try),
                        onClick = { lab.fire(egg.number) },
                        enabled = lab.canFire,
                        style = PicnicButtonStyle.Secondary,
                        modifier = Modifier.semantics { contentDescription = tryLabel },
                    )
                }
            }
        }
    }
}
