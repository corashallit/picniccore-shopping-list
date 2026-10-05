package earth.cora.picnic.ui.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.StruckText
import earth.cora.picnic.ui.theme.hardShadow

/*
 * The peek note (docs/DESIGN_STICKER.md §4.23): holding a folded basket shows what is in it and what is still to get.
 * A 168 dp lemon sticky note at −2°, 3 dp white border, hs2; the aisle's name (DynaPuff 14 sp), then one line per item
 * with a 7 dp dot: strawberry = still to get, melon + strike = already in the basket. role=status. Owner: WP5 (PeekTest).
 */

/**
 * The note for [section]. [maxHeight] caps it (the room on its side of the strip, inside the list): what does not fit
 * ends in a "+n more" line; TalkBack still reads every item.
 */
@Composable
internal fun PeekNote(section: SectionUi, modifier: Modifier = Modifier, maxHeight: Dp = Dp.Infinity) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val inBasket = androidx.compose.ui.res.stringResource(earth.cora.picnic.R.string.list_peek_in_basket, "%s")
    val resources = LocalResources.current
    val name = spokenAisleName(section)
    val spoken = buildString {
        append(name)
        append(": ")
        append(section.rows.joinToString(", ") { r -> if (r.checked) inBasket.replace("%s", r.name) else r.name })
    }
    val lineStyle = t.label.copy(color = c.ballpoint, fontSize = 13.sp)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    Box(
        modifier
            .width(168.dp)
            .heightIn(max = maxHeight)
            .testTag(ListTags.PEEK)
            .rotate(-2f)
            .clearAndSetSemantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            }
            .hardShadow(RectangleShape, PicnicTheme.shadows, large = true)
            .background(c.stickerWhite)
            .padding(3.dp)
            .clip(RectangleShape)
            .background(Families.lemon.pastel)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        SubcomposeLayout { constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val gap = 3.dp.roundToPx()
            val title = subcompose("title") {
                // The aisle's whole name, wrapping onto balanced lines with "&" kept with the word after it (§0.4).
                Text(
                    earth.cora.picnic.ui.theme.keepWithNextWord(section.displayName),
                    style = t.smallTitle.copy(color = c.ballpoint, fontSize = 14.sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
                )
            }.single().measure(loose)
            val lines = section.rows.mapIndexed { i, r ->
                subcompose("row:$i") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Canvas(Modifier.size(7.dp)) { drawCircle(if (r.checked) Families.melon.mid else Families.strawberry.mid) }
                        StruckText(
                            r.name,
                            style = lineStyle,
                            struck = r.checked,
                            strike = PicnicTheme.colors.hanko.takeIf { !c.isDark } ?: Families.strawberry.trace,
                        )
                    }
                }.single().measure(loose)
            }
            val natural = title.height + lines.sumOf { it.height + gap }
            var shown = lines.size
            var more: androidx.compose.ui.layout.Placeable? = null
            if (constraints.hasBoundedHeight && natural > constraints.maxHeight) {
                // Ballpoint, which holds on the lemon note in every variant (§2.2: ballpointSoft never sits on a pastel,
                // and Glow's soft ink is a pale lilac); a lighter weight sets it apart from the items.
                val moreText: @Composable (Int) -> Unit = { n ->
                    Text(resources.getQuantityString(earth.cora.picnic.R.plurals.list_peek_more, n, n), style = lineStyle.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                }
                // The last line is one line of text whatever n is: measured, not composed, so it leaves no stray node.
                val moreHeight = measurer.measure(resources.getQuantityString(earth.cora.picnic.R.plurals.list_peek_more, lines.size, lines.size), lineStyle, maxLines = 1).size.height
                var h = title.height + gap + moreHeight
                shown = 0
                while (shown < lines.size && h + lines[shown].height + gap <= constraints.maxHeight) {
                    h += lines[shown].height + gap
                    shown++
                }
                more = subcompose("more") { moreText(lines.size - shown) }.single().measure(loose)
            }
            val height = title.height + lines.take(shown).sumOf { it.height + gap } + (more?.let { it.height + gap } ?: 0)
            layout(constraints.maxWidth, height.coerceAtMost(constraints.maxHeight)) {
                var y = 0
                title.place(0, y)
                y += title.height
                for (l in lines.take(shown)) {
                    y += gap
                    l.place(0, y)
                    y += l.height
                }
                more?.let {
                    y += gap
                    it.place(0, y)
                }
            }
        }
    }
}
