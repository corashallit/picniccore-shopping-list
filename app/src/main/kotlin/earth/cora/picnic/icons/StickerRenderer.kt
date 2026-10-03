package earth.cora.picnic.icons

import android.content.res.Resources
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Draws a library sticker (`sk_i_<key>.xml`, docs/DESIGN_STICKER.md §3.2) into a bitmap outside composition, with a
 * choice of its named top-level groups. The live pipeline (§18.3 step 5) needs the **art only** (`fills`, `lines`,
 * `gloss`: no white die-cut, no shadow) for the neighbour sheet; contact sheets and the end-to-end check draw the
 * sticker with its `cut` too. `dominant` (clip-only) and `face` never draw here.
 *
 * The drawable's viewport (52 × 52, the DSL's viewBox −2 −2 52 52 shifted by +2) maps onto a [px] square, so a
 * neighbour drawn at 512 px sits in its sheet cell exactly as the mockup's `make4.py` placed it.
 */
internal object StickerRenderer {
    /** The art groups: what the model sees in the sheet and what every sticker keeps above its die-cut. */
    val ART_GROUPS: Set<String> = setOf("fills", "lines", "gloss")

    /** Art plus the white die-cut: the finished sticker without its (render-time) shadow. */
    val STICKER_GROUPS: Set<String> = ART_GROUPS + "cut"

    fun load(resources: Resources, @DrawableRes id: Int): ImageVector = ImageVector.vectorResource(null, resources, id)

    /** Draws [vector]'s top-level groups named in [groups] into [canvas] at ([left], [top]), [px] square. */
    fun draw(vector: ImageVector, groups: Set<String>, canvas: Canvas, left: Float, top: Float, px: Float, canvasSize: Size) {
        val scale = px / vector.viewportWidth
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, canvasSize) {
            withTransform({
                translate(left, top)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                for (node in vector.root) {
                    if (node is VectorGroup && node.name in groups) drawGroup(node)
                }
            }
        }
    }

    /** One sticker on its own transparent [px] square. */
    fun render(vector: ImageVector, groups: Set<String>, px: Int): Bitmap {
        val image = ImageBitmap(px, px)
        draw(vector, groups, Canvas(image), 0f, 0f, px.toFloat(), Size(px.toFloat(), px.toFloat()))
        return image.asAndroidBitmap()
    }

    private fun DrawScope.drawGroup(group: VectorGroup) {
        val transformed = group.rotation != 0f || group.scaleX != 1f || group.scaleY != 1f ||
            group.translationX != 0f || group.translationY != 0f
        val body: DrawScope.() -> Unit = {
            if (group.clipPathData.isNotEmpty()) {
                clipPath(group.clipPathData.toPath()) { drawChildren(group) }
            } else {
                drawChildren(group)
            }
        }
        if (!transformed) {
            body()
            return
        }
        // As VectorComponent applies a group's transform: about its pivot, then translated.
        withTransform({
            translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
            rotate(group.rotation, pivot = Offset.Zero)
            scale(group.scaleX, group.scaleY, pivot = Offset.Zero)
            translate(-group.pivotX, -group.pivotY)
        }) { body() }
    }

    private fun DrawScope.drawChildren(group: VectorGroup) {
        for (node in group) {
            when (node) {
                is VectorGroup -> drawGroup(node)
                is VectorPath -> drawVectorPath(node)
            }
        }
    }

    private fun DrawScope.drawVectorPath(p: VectorPath) {
        val path = p.pathData.toPath().apply { fillType = p.pathFillType }
        p.fill?.let { drawPath(path, it, alpha = p.fillAlpha, style = Fill) }
        val stroke = p.stroke
        if (stroke != null && p.strokeLineWidth > 0f) {
            drawPath(
                path,
                stroke,
                alpha = p.strokeAlpha,
                style = Stroke(width = p.strokeLineWidth, miter = p.strokeLineMiter, cap = p.strokeLineCap, join = p.strokeLineJoin),
            )
        }
    }
}
