package earth.cora.picnic.ui.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
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

@Composable
internal fun PeekNote(section: SectionUi, modifier: Modifier = Modifier) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val inBasket = androidx.compose.ui.res.stringResource(earth.cora.picnic.R.string.list_peek_in_basket, "%s")
    val spoken = buildString {
        append(section.fullName)
        append(": ")
        append(section.rows.joinToString(", ") { r -> if (r.checked) inBasket.replace("%s", r.name) else r.name })
    }
    Column(
        modifier
            .width(168.dp)
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
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(section.displayName, style = t.smallTitle.copy(color = c.ballpoint, fontSize = 14.sp), maxLines = 2)
        for (r in section.rows) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(7.dp)) { drawCircle(if (r.checked) Families.melon.mid else Families.strawberry.mid) }
                StruckText(
                    r.name,
                    style = t.label.copy(color = c.ballpoint, fontSize = 13.sp),
                    struck = r.checked,
                    strike = PicnicTheme.colors.hanko.takeIf { !c.isDark } ?: Families.strawberry.trace,
                )
            }
        }
    }
}
