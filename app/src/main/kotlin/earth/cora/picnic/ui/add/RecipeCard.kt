package earth.cora.picnic.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import earth.cora.picnic.R
import earth.cora.picnic.core.assistant.SkipReason
import earth.cora.picnic.ui.a11y.spokenItemLabel
import earth.cora.picnic.ui.theme.AisleLook
import earth.cora.picnic.ui.theme.AisleLooks
import earth.cora.picnic.ui.theme.ChipFace
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.KeepSwitch
import earth.cora.picnic.ui.theme.NumberCircle
import earth.cora.picnic.ui.theme.PicnicButton
import earth.cora.picnic.ui.theme.PicnicButtonStyle
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicText
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.PostageCard
import earth.cora.picnic.ui.theme.StoreAisleChip
import earth.cora.picnic.ui.theme.patternFill

/*
 * The recipe card (docs/DESIGN_STICKER.md §4.17, items 203, 204, 323): a postage-edged white card with the dish's title,
 * "from the kitchen of ♡ the web" (when the web was searched), a meta row ("web search" chip, "3 recipes · 5 of 10
 * kept"), then one compact aisle tray per aisle ("Baking · 3 of 6 kept") with ingredient rows (40 dp stickers, 16 sp
 * names, "2.5 kg bag · 2 cups" details) and keep switches. Skipped rows stay readable: faded, never struck, the reason in
 * the detail line ("already on your list", "in your pantry · ½ tsp", "optional · skipped"). "Edit my pantry" sits under
 * the last tray when a pantry row shows. Owner: WP7.
 */

const val RECIPE_CARD_TAG: String = "add_recipe_card"
const val EDIT_PANTRY_TAG: String = "add_edit_pantry"

/** Rows grouped by aisle, "Everything else" first, then walking order (the list's order). */
internal fun groupByAisle(rows: List<ReviewRow>): List<List<ReviewRow>> =
    rows.groupBy { it.aisleIndex }.toSortedMap().values.map { group -> group.sortedBy { it.key } }

/**
 * The card. [clock] runs the fan-out (each row's sticker pops at its beat); null shows everything. [onEditPantry] null
 * hides "Edit my pantry" (no route to My pantry yet).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeCard(
    review: AddReview,
    rows: List<ReviewRow>,
    clock: RevealClock?,
    onToggleKeep: (Int) -> Unit,
    onEditPantry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    PostageCard(modifier.fillMaxWidth().testTag(RECIPE_CARD_TAG)) {
        PaperTheme {
            val c = PicnicTheme.colors
            val t = PicnicTheme.type
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                review.recipe?.let { recipe ->
                    Text(recipe.title, style = t.cardTitle.copy(color = c.ballpoint), modifier = Modifier.semantics { heading() })
                    if (review.webSearch) {
                        PicnicText(stringResource(R.string.add_card_kitchen), style = t.transcript.copy(color = c.ballpointSoft))
                    }
                }
                review.message?.let { Text(it, style = t.body.copy(fontSize = 15.sp, lineHeight = 21.sp, color = c.ballpointSoft)) }
                MetaRow(review, rows)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (group in groupByAisle(rows)) AisleTray(group, clock, onToggleKeep)
                }
                if (onEditPantry != null && rows.any { it.skipReason == SkipReason.IN_PANTRY }) {
                    PicnicButton(
                        stringResource(R.string.add_edit_pantry),
                        onClick = onEditPantry,
                        style = PicnicButtonStyle.Text,
                        glyph = PicnicGlyph.Jar,
                        modifier = Modifier.padding(top = 4.dp).testTag(EDIT_PANTRY_TAG),
                    )
                }
                if (review.sources.isNotEmpty()) {
                    Text(
                        stringResource(R.string.add_sources),
                        style = t.label.copy(color = c.ballpoint),
                        modifier = Modifier.padding(top = 6.dp).semantics { heading() },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        review.sources.take(MAX_SOURCES).forEach { source ->
                            SourceLink(
                                text = source.title?.takeIf { it.length <= MAX_SOURCE_TITLE } ?: hostOf(source.url),
                                onClick = { runCatching { uriHandler.openUri(source.url) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "web search" (ramune chip, when it ran) and "3 recipes · 5 of 10 kept" (the count of kept rows, live). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaRow(review: AddReview, rows: List<ReviewRow>) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val kept = rows.count { it.keep }
    val parts = buildList {
        review.recipe?.recipesCompared?.let { add(pluralStringResource(R.plurals.add_card_recipes, it, it)) }
        add(stringResource(R.string.add_card_kept, kept, rows.size))
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        if (review.webSearch) {
            ChipFace(fill = Families.ramune.pastel, height = chipHeight(30.dp)) {
                Glyph(PicnicGlyph.Web, null, tint = c.ballpoint, size = 16.dp)
                Text(stringResource(R.string.add_card_web), style = t.label.copy(color = c.ballpoint), maxLines = 1)
            }
        }
        Text(parts.joinToString(" · "), style = t.label.copy(color = c.ballpointSoft, fontWeight = FontWeight.Medium))
    }
}

/**
 * One compact aisle tray (§4.17): the aisle pastel, 3 dp white border, radius 12; a header band with the number circle,
 * the aisle's display name (DynaPuff 20 sp in its trace, wrapping rather than shrinking) and "3 of 6 kept"; the rows on a
 * white inner panel with 1 dp rules; a patterned end cap.
 */
@Composable
internal fun AisleTray(rows: List<ReviewRow>, clock: RevealClock?, onToggleKeep: (Int) -> Unit, modifier: Modifier = Modifier) {
    val first = rows.first()
    val look = AisleLooks.of(first.aisleIndex + 1, first.style)
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val shape = PicnicTheme.shapes.section
    val name = if (first.aisleIndex < 0) stringResource(R.string.add_everything_else) else first.aisleTitle
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(look.family.pastel)
            .border(PicnicTheme.dimens.border, c.stickerWhite, shape),
    ) {
        val kept = stringResource(R.string.add_tray_kept, rows.count { it.keep }, rows.size)
        // From font scale 1.3 the store chip and the count move under the name, so the name never breaks inside a word.
        val stacked = LocalDensity.current.fontScale >= LARGE_TEXT
        Column(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 6.dp).semantics(mergeDescendants = true) { heading() },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FixedSizeBadge { NumberCircle(if (first.aisleIndex < 0) null else first.aisleIndex + 1, look) }
                if (stacked) {
                    Text(name, style = t.aisleTab.copy(color = look.family.trace), modifier = Modifier.weight(1f))
                } else {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            name,
                            style = t.aisleTab.copy(color = look.family.trace),
                            maxLines = 2,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        first.storeAisle?.let { FixedSizeBadge { StoreAisleChip(it) } }
                    }
                    Text(kept, style = t.label.copy(color = c.ballpoint), maxLines = 1)
                }
            }
            if (stacked) {
                Row(
                    Modifier.padding(start = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    first.storeAisle?.let { FixedSizeBadge { StoreAisleChip(it) } }
                    Text(kept, style = t.label.copy(color = c.ballpoint))
                }
            }
        }
        Column(
            Modifier
                .padding(horizontal = 5.dp)
                .fillMaxWidth()
                .clip(PicnicTheme.shapes.sectionInner)
                .background(c.pad)
                .border(1.5.dp, look.family.mid.copy(alpha = 0.6f), PicnicTheme.shapes.sectionInner),
        ) {
            rows.forEachIndexed { i, row ->
                ReviewRowView(row, clock, onToggleKeep, ruleAbove = i > 0, look = look)
            }
        }
        Box(Modifier.fillMaxWidth().height(10.dp).patternFill(look, alpha = 0.55f))
    }
}

/**
 * One ingredient row (§4.17): a 40 dp sticker, the name (16 sp) over the detail, the keep switch (48 dp on a dashed spot;
 * kept = a lemon star pressed on). The whole row is the switch (Role.Switch, "Flour, 2.5 kilogram bag, 2 cups"). A
 * skipped row is faded (sticker 45 % with 60 % desaturation, name ballpointSoft, never struck) with its reason; a row
 * already on the list has no switch (duplicates are never added twice). Fans out at its beat on [clock].
 */
@Composable
internal fun ReviewRowView(
    row: ReviewRow,
    clock: RevealClock?,
    onToggleKeep: (Int) -> Unit,
    ruleAbove: Boolean,
    look: AisleLook,
    modifier: Modifier = Modifier,
) {
    val c = PicnicTheme.colors
    val t = PicnicTheme.type
    val shown = clock?.rowShown(row.key) ?: true
    val age = clock?.since(clock.plan.rowAt[row.key] ?: clock.plan.popAt) ?: Long.MAX_VALUE
    val pop = when {
        !shown -> 0f
        age < 83 -> 1.2f
        else -> 1f
    }
    val faded = !row.keep
    val detail = rowDetail(row)
    val label = spokenItemLabel(row.item.name, detail)
    // From font scale 1.5 the words get the row's whole width under the sticker and the switch (no word breaks).
    val stacked = LocalDensity.current.fontScale >= VERY_LARGE_TEXT
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = PicnicTheme.dimens.rowCompact)
        .drawBehind { if (ruleAbove) drawRect(c.rule, Offset.Zero, androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) }
        .then(
            if (row.canKeep) {
                Modifier.toggleable(value = row.keep, role = Role.Switch, onValueChange = { onToggleKeep(row.key) })
                    .semantics { contentDescription = label }
            } else {
                Modifier.clearAndSetSemantics { contentDescription = label }
            },
        )
        .graphicsLayer { alpha = if (shown) 1f else 0f }
        .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
    val sticker = @Composable {
        ItemIcon(
            row.icon,
            contentDescription = null,
            size = PicnicTheme.dimens.stickerCompact,
            aisleIndex = row.aisleIndex,
            modifier = Modifier
                .graphicsLayer { scaleX = pop; scaleY = pop }
                .then(if (faded) Modifier.skippedSticker() else Modifier),
        )
    }
    val words = @Composable { m: Modifier ->
        Column(m.clearAndSetSemantics { }) {
            Text(row.item.name, style = t.itemNameCompact.copy(color = if (faded) c.ballpointSoft else c.ballpoint))
            if (detail != null) Text(AddRules.keepTogether(detail), style = t.itemDetailCompact.copy(color = c.ballpointSoft))
        }
    }
    val switch = @Composable {
        if (row.canKeep) {
            // The row is the switch; the spot is its picture (no second TalkBack node).
            KeepSwitch(row.keep, onCheckedChange = { onToggleKeep(row.key) }, modifier = Modifier.clearAndSetSemantics { })
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
    if (stacked) {
        Column(rowModifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                sticker()
                Spacer(Modifier.weight(1f))
                switch()
            }
            words(Modifier.fillMaxWidth().padding(end = 6.dp))
        }
    } else {
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            sticker()
            words(Modifier.weight(1f))
            switch()
        }
    }
}

/** From this font scale the tray headers stack (§15: nothing clips, names never break inside a word). */
internal const val LARGE_TEXT: Float = 1.3f

/** From this font scale the review rows stack their words under the sticker and the switch. */
internal const val VERY_LARGE_TEXT: Float = 1.5f

/**
 * The number circle and the store chip are fixed-size badges (24 dp, 22 dp) whose numerals would clip at large text;
 * they keep their drawn size (TalkBack still hears the aisle in the header's words).
 */
@Composable
internal fun FixedSizeBadge(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, minOf(d.fontScale, 1f)), content = content)
}

/** A chip's height that grows with the text (never below [min]): the label's line plus its padding. */
@Composable
internal fun chipHeight(min: Dp): Dp {
    val lineHeight = PicnicTheme.type.label.lineHeight
    val grown = with(LocalDensity.current) { lineHeight.toDp() } + 12.dp
    return max(min, grown)
}

/** A row's detail line (§4.17): the reason for a skipped row, else the pack size · recipe amount · note. */
@Composable
internal fun rowDetail(row: ReviewRow): String? = when (row.skipReason) {
    SkipReason.ALREADY_ON_LIST -> stringResource(R.string.add_reason_already)
    SkipReason.IN_PANTRY -> {
        val reason = stringResource(R.string.add_reason_pantry)
        AddRules.recipeAmount(row.item.quantity)?.let { "$reason · $it" } ?: reason
    }
    SkipReason.OPTIONAL -> stringResource(R.string.add_reason_optional)
    SkipReason.NONE -> AddRules.detailLine(row.item)
}

/**
 * A source chip (§4.17): the theme's SourceChip look (ramune pastel, link glyph, label type) on a chip that grows with
 * the text, so a title never clips at large text.
 */
@Composable
private fun SourceLink(text: String, onClick: () -> Unit) {
    val c = PicnicTheme.colors
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ChipFace(fill = Families.ramune.pastel, height = chipHeight(32.dp)) {
            Glyph(PicnicGlyph.Link, null, tint = c.ballpoint, size = 15.dp)
            Text(text, style = PicnicTheme.type.label.copy(color = c.ballpoint), maxLines = 1)
        }
    }
}

/** "allrecipes.com" for a source chip. */
internal fun hostOf(url: String): String =
    runCatching { url.toUri().host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: url

private const val MAX_SOURCES = 4
private const val MAX_SOURCE_TITLE = 28
