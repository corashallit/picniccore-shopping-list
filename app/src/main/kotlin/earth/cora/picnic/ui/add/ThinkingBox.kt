package earth.cora.picnic.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.cora.picnic.R
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.platform.Feedback
import earth.cora.picnic.ui.a11y.decorative
import earth.cora.picnic.ui.theme.DecorSticker
import earth.cora.picnic.ui.theme.Families
import earth.cora.picnic.ui.theme.Glyph
import earth.cora.picnic.ui.theme.ItemIcon
import earth.cora.picnic.ui.theme.LocalReducedMotion
import earth.cora.picnic.ui.theme.LocalThemeVariant
import earth.cora.picnic.ui.theme.PicnicGlyph
import earth.cora.picnic.ui.theme.PicnicMotion
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.StickerFamily
import earth.cora.picnic.ui.theme.ThemeVariant
import earth.cora.picnic.ui.theme.hardShadow
import earth.cora.picnic.ui.theme.rememberLoopTime
import androidx.compose.runtime.LaunchedEffect

/*
 * The thinking box (docs/DESIGN_STICKER.md §4.18, D16, items 202, 205, 324): a backing box with a 52 dp gashapon capsule
 * in the target aisle's mid wobbling ±8° in 3 poses, a headline and the steps. While waiting it reads "Reading your
 * note…" (it claims nothing); the recipe wording only when the answer has a recipe; the steps come only from real fields
 * of the answer, 250 ms apart, each tick landing at 1.3 in 2 frames as its line darkens to ballpoint; then the capsule
 * pops ("pon!") and the stickers fan out into the card's rows. Owner: WP7.
 */

const val THINKING_TAG: String = "add_thinking"

/**
 * The thinking box. [steps] = null while waiting for the answer; with an answer, [clock] runs the reveal ([steps] land on
 * its plan, the capsule pops at its pop beat and cues [feedback] CAPSULE_POP once). [recipe] switches the headline.
 */
@Composable
fun ThinkingBox(
    steps: List<ThinkingStep>?,
    recipe: Boolean,
    clock: RevealClock?,
    family: StickerFamily,
    modifier: Modifier = Modifier,
    feedback: Feedback? = null,
) {
    val reduced = LocalReducedMotion.current
    val waiting = steps == null
    val popped = clock?.popped() ?: false
    val loop by rememberLoopTime(enabled = !reduced && !popped)
    // The pop: 1.25 → 0.92 → 1, one pose each (still under reduced motion).
    val popAge = if (clock != null) clock.since(clock.plan.popAt) else -1
    val popScale = when {
        reduced || !popped -> 1f
        popAge < PicnicMotion.pose -> 1.25f
        popAge < 2L * PicnicMotion.pose -> 0.92f
        else -> 1f
    }
    val wobble = if (reduced || popped) 0f else PicnicMotion.gashaponPoses[((loop / PicnicMotion.gashaponPoseMillis) % 3).toInt()].rotation
    if (clock != null && feedback != null) {
        LaunchedEffect(clock.id, popped) {
            // "pon!" + CONFIRM once, as it pops (not when a played reveal is shown again).
            if (popped && popAge in 0..(2L * PicnicMotion.pose)) feedback.cue(Cue.CAPSULE_POP)
        }
    }
    PaperTheme {
        val c = PicnicTheme.colors
        val shape = PicnicTheme.shapes.section
        Row(
            modifier
                .fillMaxWidth()
                .testTag(THINKING_TAG)
                .hardShadow(shape, PicnicTheme.shadows)
                .clip(shape)
                .background(c.backing)
                .border(PicnicTheme.dimens.border, c.stickerWhite, shape)
                .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                ItemIcon(
                    IconRef(IconCatalog.CAPSULE),
                    contentDescription = null,
                    size = 52.dp,
                    aisleIndex = Families.rotation.indexOf(family),
                    modifier = Modifier.graphicsLayer {
                        rotationZ = wobble
                        scaleX = popScale
                        scaleY = popScale
                    },
                )
                if (popped && popAge in 0 until 2L * PicnicMotion.pose && !reduced) PopBurst()
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val headline = stringResource(if (!waiting && recipe) R.string.add_thinking_recipe else R.string.add_thinking_reading)
                Text(
                    headline,
                    style = PicnicTheme.type.label.copy(fontSize = 15.sp, lineHeight = 20.sp, color = c.ballpoint),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (reduced && waiting) {
                    Text(stringResource(R.string.theme_loader_working), style = PicnicTheme.type.caption.copy(color = c.ballpointSoft))
                }
                if (steps != null && clock != null) {
                    val shown = clock.stepsShown()
                    steps.take(shown).forEachIndexed { i, step ->
                        StepLine(stepText(step), age = clock.since(clock.plan.stepAt[i]), reduced = reduced)
                    }
                }
            }
        }
    }
}

/** One step: its tick lands at 1.3 in 2 frames while the line darkens from ballpointSoft to ballpoint (§4.18). */
@Composable
private fun StepLine(text: String, age: Long, reduced: Boolean) {
    val c = PicnicTheme.colors
    val landed = age >= RevealPlan.TICK_MILLIS
    val tickScale = if (reduced || landed) 1f else if (age < PicnicMotion.pose) 1.3f else 1.12f
    val alpha = if (reduced) (age.toFloat() / PicnicMotion.rmFade.durationMillis).coerceIn(0f, 1f) else 1f
    Row(
        Modifier.graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Glyph(
            PicnicGlyph.Check,
            contentDescription = null,
            tint = if (landed) c.ballpoint else c.ballpointSoft,
            size = 16.dp,
            modifier = Modifier.padding(top = 2.dp).graphicsLayer { scaleX = tickScale; scaleY = tickScale },
        )
        Text(
            text,
            style = PicnicTheme.type.body.copy(fontSize = 15.sp, lineHeight = 20.sp, color = if (landed) c.ballpoint else c.ballpointSoft),
        )
    }
}

/** Four tiny stars that burst off the capsule as it pops (a transient burst, decorative). */
@Composable
private fun PopBurst() {
    Box(Modifier.size(56.dp).decorative()) {
        DecorSticker("star4", Modifier.offset((-6).dp, (-4).dp), size = 12.dp, shadow = false)
        DecorSticker("star", Modifier.offset(46.dp, (-6).dp), size = 12.dp, shadow = false)
        DecorSticker("star4", Modifier.offset(48.dp, 44.dp), size = 11.dp, shadow = false)
        DecorSticker("heart", Modifier.offset((-6).dp, 44.dp), size = 11.dp, shadow = false)
    }
}

/** A step's words (§16). */
@Composable
fun stepText(step: ThinkingStep): String = when (step) {
    is ThinkingStep.SearchedWeb -> stringResource(R.string.add_step_web, step.query)
    is ThinkingStep.ComparedRecipes -> {
        val recipes = step.recipes
        val servings = step.servings
        when {
            recipes != null && servings != null -> pluralStringResource(R.plurals.add_step_compared, recipes, recipes) + "\u00A0· " +
                stringResource(R.string.add_step_makes, servings)
            recipes != null -> pluralStringResource(R.plurals.add_step_compared, recipes, recipes)
            servings != null -> stringResource(R.string.add_step_makes_alone, servings)
            else -> ""
        }
    }
    is ThinkingStep.CheckedPantry -> stringResource(R.string.add_step_pantry, step.leftOut)
    is ThinkingStep.SortedAisles ->
        if (step.aisles > 0) pluralStringResource(R.plurals.add_step_sorted, step.aisles, step.aisles) else stringResource(R.string.add_step_checked)
}

/**
 * White paper and light boxes (the recipe card, the polaroid, the thinking box, the trays) read the same in every
 * variant: in Glow their insides use the Day tokens (ballpoint text, Day spots and rings), since paper and stickers
 * never change colour in the dark (§2.2).
 */
@Composable
fun PaperTheme(content: @Composable () -> Unit) {
    if (LocalThemeVariant.current == ThemeVariant.Glow) {
        earth.cora.picnic.ui.theme.PicnicTheme(variant = ThemeVariant.Day, content = content)
    } else {
        content()
    }
}

/** A skipped row's sticker (§4.17): 45 % with 60 % desaturation (a colour matrix on one layer; nothing is blurred). */
fun Modifier.skippedSticker(): Modifier = drawWithContent {
    val paint = Paint().apply {
        colorFilter = Desaturate60
        alpha = 0.45f
    }
    val pad = 8.dp.toPx()
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset(-pad, -pad), androidx.compose.ui.geometry.Size(size.width + pad * 2, size.height + pad * 2)), paint)
        drawContent()
        canvas.restore()
    }
}

private val Desaturate60: ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.4f) })
