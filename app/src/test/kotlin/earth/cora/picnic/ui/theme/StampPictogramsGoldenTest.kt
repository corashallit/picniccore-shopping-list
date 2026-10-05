package earth.cora.picnic.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import earth.cora.picnic.ui.theme.gen.BasketArt
import earth.cora.picnic.ui.theme.gen.BasketManifest
import earth.cora.picnic.ui.theme.gen.DecorManifest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The stamp pictograms on the sticker book's stamp page (§4.13), in Day and Glow (`stamp_pictograms_<variant>`): every
 * aisle's 24 dp stamp circle with its pictogram at 0.75 of the circle, rotated −10°, inked by [AisleLook.stampInk] (the
 * aisle trace on the Day page, the aisle mid on the Glow page, where the traces fall below 3 : 1). Drawn from the
 * generated `sk_p_*` drawables, `ink` paths recoloured, `paper` paths white.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class StampPictogramsGoldenTest(private val variant: ThemeVariant, private val v: String) {
    @get:Rule
    val compose = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params(): List<Array<Any>> = dayAndGlow()
    }

    @Test fun stampPage() = compose.recordGolden("stamp_pictograms_$v", variant) {
        val looks = (1..10).map { AisleLooks.of(it) } + AisleLooks.everythingElse
        Column(
            Modifier.background(PicnicTheme.colors.book.page, PicnicTheme.shapes.bookPage).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (row in looks.chunked(6)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (look in row) Stamp(look, variant)
                }
            }
        }
    }

    /**
     * The GOT IT stamp on closed basket lids (§5.3, `basket_lid_hanko_<variant>`): Day vermilion on its white backing
     * ring in every variant, on the mid-coloured lids where the Glow hanko colour disappeared (candy jar, vanity case,
     * ice bucket, tote).
     */
    @Test fun basketLidHanko() = compose.recordGolden("basket_lid_hanko_$v", variant) {
        val kinds = listOf("candy_jar", "vanity_case", "ice_bucket", "canvas_tote")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (kind in kinds) LidWithHanko(BasketManifest.kinds.first { it.kind == kind })
        }
    }
}

@Composable
private fun LidWithHanko(basket: BasketArt) {
    val back = rememberVectorArt(basket.back)
    val front = rememberVectorArt(basket.front)
    val lid = rememberVectorArt(basket.lidClosed)
    val measurer = rememberTextMeasurer()
    val style = PicnicTheme.type.hanko
    Canvas(Modifier.size(80.dp, 64.dp)) {
        drawArt(back)
        drawArt(front)
        drawArt(lid)
        val u = size.width / BasketManifest.VIEWPORT_WIDTH
        val s = BasketManifest.HANKO_SIZE * u
        translate(basket.hankoX * u - s / 2f, basket.hankoY * u - s / 2f) {
            rotate(BasketManifest.HANKO_ROTATION, Offset(s / 2f, s / 2f)) {
                drawIntoSquare(s) { drawBasketHanko(HankoText.GotIt, measurer, style) }
            }
        }
    }
}

/** Runs [block] with the draw area set to an [s] x [s] square at the current origin. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoSquare(s: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    val scope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
    scope.draw(this, layoutDirection, drawContext.canvas, Size(s, s), block)
}

@Composable
private fun Stamp(look: AisleLook, variant: ThemeVariant) {
    val art = rememberVectorArt(DecorManifest.pictograms.getValue(look.pictogram.key))
    val ring = PicnicTheme.colors.ballpointSoft
    val ink = SolidColor(look.stampInk(variant))
    Canvas(Modifier.size(24.dp)) {
        val r = size.minDimension / 2f - 1.dp.toPx()
        val dashes = DashSpec.forCircle(r, 3.4.dp.toPx(), 2.6.dp.toPx())
        drawPath(DashSpec.circlePath(center, r), ring, style = Stroke(2.dp.toPx(), pathEffect = dashes.pathEffect()))
        val s = size.width * 0.75f
        rotate(-10f) {
            drawArt(art, Size(s, s), Offset((size.width - s) / 2f, (size.height - s) / 2f), paint = { p, _ -> if (p.name == "ink") ink else null })
        }
    }
}
