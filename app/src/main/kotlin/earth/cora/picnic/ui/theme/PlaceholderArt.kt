package earth.cora.picnic.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import earth.cora.picnic.core.model.BasketKind

/*
 * PLACEHOLDER ART (the art pass replaces this file with real vectors/bitmaps). Two families:
 * - item stickers: one drawing per catalog CATEGORY (fruit -> apple, dairy -> milk carton, ...) on
 *   a white die-cut disc, shown when no bundled/bespoke bitmap exists for an icon key;
 * - baskets: one container per BasketKind, drawn as a BACK layer (handles, open lids, stems) and a
 *   FRONT layer (body, weave, rim, cloth) so mini item icons can peek out between the two.
 * Objects keep their own colours in both themes and use a fixed plum ink, like printed stickers.
 */

/** Fixed art colours (objects, not UI). */
internal object Art {
    val ink = Color(0xFF2A1730)
    val lemon = Color(0xFFFFD43B)
    val lemonShade = Color(0xFFF2A63A)
    val blush = Color(0xFFFF9EC4)
    val lavender = Color(0xFFDDD5F5)
    val red = Color(0xFFF04462)
    val redShade = Color(0xFFC92A55)
    val leaf = Color(0xFF55C26A)
    val leafShade = Color(0xFF2E9E63)
    val orange = Color(0xFFFF9548)
    val tan = Color(0xFFE9B872)
    val tanShade = Color(0xFFC98455)
    val kraft = Color(0xFFD9B88C)
    val wood = Color(0xFFD9A066)
    val woodShade = Color(0xFFA8703E)
    val cream = Color(0xFFFFF4E0)
    val sky = Color(0xFF9BD3F5)
    val skyShade = Color(0xFF4F9FD6)
    val ice = Color(0xFFDDF3FF)
    val metal = Color(0xFFC9CDD8)
    val metalShade = Color(0xFF8E94A6)
    val pink = Color(0xFFF7B6CF)
    val pinkShade = Color(0xFFD9789F)
    val grapeLight = Color(0xFFC8B6F5)
    val roast = Color(0xFFE58A5F)
    val honey = Color(0xFFFFB347)
    val mint = Color(0xFF8ED8B4)
    val teal = Color(0xFF6CC9C2)
    val lacquer = Color(0xFFE0475B)
    val lacquerShade = Color(0xFFA8233A)
    val white = Color.White
}

private fun o(x: Float, y: Float) = Offset(x, y)

/* =============================== item stickers =============================== */

/** Line width of sticker objects in the 24-unit grid. */
private const val STICKER_LINE = 1.1f

/**
 * A placeholder item sticker in a 24 x 24 grid: lavender drop, white die-cut disc with a [rim] line,
 * then the category's object. [categoryKey] null or unknown and [isDefault] draw the paper bag.
 * [disc] false draws just the object (tiny sizes, where the disc would hide it).
 */
internal fun DrawScope.drawItemSticker(categoryKey: String?, isDefault: Boolean, rim: Color, disc: Boolean = true) {
    if (disc) {
        drawCircle(Art.lavender, 10.9f, o(12.8f, 13f))
        drawCircle(Art.white, 10.9f, o(12f, 12f))
        drawCircle(rim, 10.9f, o(12f, 12f), style = Stroke(0.9f))
    }
    // Without the disc (mini icons peeking out of a basket) the object itself fills the box.
    scale(if (disc) 0.94f else 1.3f, pivot = o(12f, 12f)) {
        if (isDefault) drawPaperBag() else drawCategoryObject(categoryKey)
    }
}

private fun DrawScope.drawCategoryObject(category: String?) {
    val ink = Art.ink
    val w = STICKER_LINE
    when (category) {
        "fruit" -> {
            fillAndInk(path {
                moveTo(12f, 8.5f); cubicTo(9.5f, 6.5f, 5.5f, 8f, 6f, 12.5f); cubicTo(6.5f, 16.5f, 9.5f, 18.5f, 12f, 17.3f)
                cubicTo(14.5f, 18.5f, 17.5f, 16.5f, 18f, 12.5f); cubicTo(18.5f, 8f, 14.5f, 6.5f, 12f, 8.5f); close()
            }, Art.red, ink, w)
            inkLine(ink, w, o(12f, 8.5f), o(12.6f, 5.4f))
            fillAndInk(path { moveTo(12.6f, 6.6f); cubicTo(14f, 4.8f, 16f, 5.1f, 16.6f, 5.9f); cubicTo(15.2f, 7.2f, 13.7f, 7.3f, 12.6f, 6.6f); close() }, Art.leaf, ink, w * 0.8f)
            drawOval(Art.white, o(8.2f, 9.6f), Size(1.6f, 2.4f))
        }
        "vegetables" -> {
            for ((x, y) in listOf(15.3f to 4.4f, 17.6f to 5.2f, 19f to 7.3f)) inkLine(Art.leafShade, 1.5f, o(15.8f, 8.2f), o(x, y))
            fillAndInk(path {
                moveTo(15.8f, 8.2f); cubicTo(17.6f, 9.8f, 17.2f, 11.6f, 15.8f, 13f); lineTo(8f, 19.2f)
                cubicTo(7.3f, 19.7f, 6.4f, 18.9f, 6.9f, 18.2f); lineTo(13.2f, 10.4f); cubicTo(14.3f, 9f, 15f, 8.2f, 15.8f, 8.2f); close()
            }, Art.orange, ink, w)
            inkLine(Art.tanShade, 0.8f, o(12.2f, 13.2f), o(13.4f, 14f))
            inkLine(Art.tanShade, 0.8f, o(10f, 15.8f), o(11f, 16.6f))
        }
        "bakery" -> {
            fillAndInk(path {
                moveTo(5.5f, 14f); cubicTo(5.5f, 9f, 9f, 7.5f, 12f, 7.5f); cubicTo(15f, 7.5f, 18.5f, 9f, 18.5f, 14f)
                lineTo(18.5f, 16.8f); quadraticTo(18.5f, 18f, 17.3f, 18f); lineTo(6.7f, 18f); quadraticTo(5.5f, 18f, 5.5f, 16.8f); close()
            }, Art.tan, ink, w)
            for (x in listOf(8.6f, 11.6f, 14.6f)) inkLine(Art.tanShade, 1f, o(x, 10.2f), o(x + 1.5f, 12.8f))
        }
        "deli" -> {
            val wedge = path { moveTo(5f, 12.6f); lineTo(19f, 8.4f); lineTo(19f, 17.6f); lineTo(5f, 17.6f); close() }
            drawPath(wedge, Art.lemon)
            clipPath(wedge) {
                drawCircle(Art.lemonShade, 1.3f, o(9f, 15f))
                drawCircle(Art.lemonShade, 1.5f, o(14.2f, 13.4f))
                drawCircle(Art.lemonShade, 1f, o(16.8f, 16.2f))
            }
            drawPath(wedge, ink, style = Stroke(w))
            inkLine(ink, w * 0.8f, o(5f, 12.6f), o(19f, 12.6f))
        }
        "meat" -> {
            inkLine(ink, 3.6f, o(11f, 13f), o(7.4f, 16.6f))
            inkLine(Art.white, 2f, o(11f, 13f), o(7.4f, 16.6f))
            for (c in listOf(o(6.4f, 15.8f), o(8.1f, 17.6f))) {
                drawCircle(Art.white, 1.3f, c)
                drawCircle(ink, 1.3f, c, style = Stroke(w * 0.8f))
            }
            fillAndInk(path {
                moveTo(10.5f, 13.5f); cubicTo(8.5f, 11f, 9.5f, 6.8f, 13.5f, 6.5f); cubicTo(17.5f, 6.2f, 19.2f, 10.2f, 17.2f, 12.8f)
                cubicTo(15.5f, 15f, 12.2f, 15.2f, 10.5f, 13.5f); close()
            }, Art.roast, ink, w)
            drawOval(Art.white.copy(alpha = 0.7f), o(12.5f, 8f), Size(2.4f, 1.4f))
        }
        "seafood" -> {
            fillAndInk(path { moveTo(15.6f, 12f); lineTo(19.6f, 8.8f); lineTo(19.6f, 15.2f); close() }, Art.sky, ink, w)
            fillAndInk(path { moveTo(4.5f, 12f); cubicTo(7f, 7.8f, 13.4f, 7.4f, 16.5f, 12f); cubicTo(13.4f, 16.6f, 7f, 16.2f, 4.5f, 12f); close() }, Art.sky, ink, w)
            drawCircle(ink, 0.9f, o(7.9f, 11.4f))
            drawArc(Art.skyShade, -50f, 100f, false, o(8.6f, 9.4f), Size(3.4f, 5.2f), style = Stroke(0.9f, cap = StrokeCap.Round))
        }
        "dairy" -> {
            fillAndInk(path { moveTo(13f, 10f); lineTo(17f, 8.4f); lineTo(17f, 17.6f); lineTo(13f, 19.2f); close() }, Art.sky, ink, w)
            fillAndInk(path { moveTo(10f, 6f); lineTo(14.6f, 5f); lineTo(17f, 8.4f); lineTo(13f, 10f); close() }, Art.skyShade, ink, w)
            fillAndInk(path { moveTo(7f, 10f); lineTo(13f, 10f); lineTo(13f, 19.2f); lineTo(7f, 19.2f); close() }, Art.white, ink, w)
            fillAndInk(path { moveTo(7f, 10f); lineTo(10f, 6f); lineTo(13f, 10f); close() }, Art.white, ink, w)
            drawRect(Art.sky, o(7.6f, 13f), Size(4.8f, 2.6f))
        }
        "plant_based" -> {
            fillAndInk(path { moveTo(6f, 18f); cubicTo(6f, 10f, 10.5f, 6f, 18f, 6f); cubicTo(18f, 13.5f, 14f, 18f, 6.5f, 18f); close() }, Art.leaf, ink, w)
            inkLine(Art.leafShade, 1f, o(7f, 17f), o(16f, 8f))
            inkLine(Art.leafShade, 0.8f, o(10.5f, 13.5f), o(10.2f, 10.5f))
            inkLine(Art.leafShade, 0.8f, o(12.5f, 11.5f), o(15f, 12.2f))
        }
        "breakfast" -> {
            fillAndInk(path { addRoundRect(RoundRect(7f, 9.2f, 17f, 19.2f, CornerRadius(2.4f))) }, Art.honey, ink, w)
            fillAndInk(path { addRoundRect(RoundRect(6.6f, 6.2f, 17.4f, 9.4f, CornerRadius(1f))) }, Art.red, ink, w)
            drawGingham(Art.white.copy(alpha = 0.55f), 1.2f, Rect(7.2f, 6.8f, 16.8f, 8.8f))
            drawCircle(Art.cream, 2.6f, o(12f, 14.2f))
            drawCircle(ink, 2.6f, o(12f, 14.2f), style = Stroke(w * 0.7f))
        }
        "baking" -> {
            fillAndInk(path {
                moveTo(7.5f, 8.6f); cubicTo(6.8f, 12f, 6.4f, 15.6f, 7.4f, 18.8f); lineTo(16.6f, 18.8f)
                cubicTo(17.6f, 15.6f, 17.2f, 12f, 16.5f, 8.6f); close()
            }, Art.cream, ink, w)
            fillAndInk(path { moveTo(8.6f, 8.6f); lineTo(10f, 5.8f); lineTo(14f, 5.8f); lineTo(15.4f, 8.6f); close() }, Art.cream, ink, w)
            inkLine(Art.red, 1.2f, o(9.2f, 8.6f), o(14.8f, 8.6f))
            inkLine(Art.tanShade, 1f, o(12f, 17f), o(12f, 11.2f))
            for (y in listOf(12.2f, 14f, 15.8f)) {
                drawOval(Art.tan, o(10.4f, y - 0.6f), Size(1.6f, 1.2f))
                drawOval(Art.tan, o(12f, y - 0.6f), Size(1.6f, 1.2f))
            }
        }
        "condiments" -> {
            fillAndInk(path { addRoundRect(RoundRect(10.2f, 4.4f, 13.8f, 6.6f, CornerRadius(0.8f))) }, Art.white, ink, w)
            fillAndInk(path {
                moveTo(10.5f, 6.6f); lineTo(13.5f, 6.6f); lineTo(13.5f, 9.4f); lineTo(15.4f, 11f); lineTo(15.6f, 18.4f)
                quadraticTo(15.6f, 19.4f, 14.6f, 19.4f); lineTo(9.4f, 19.4f); quadraticTo(8.4f, 19.4f, 8.4f, 18.4f)
                lineTo(8.6f, 11f); lineTo(10.5f, 9.4f); close()
            }, Art.red, ink, w)
            drawRect(Art.cream, o(9.2f, 13f), Size(5.6f, 3f))
        }
        "pantry" -> {
            fillAndInk(path { addRect(Rect(7f, 8f, 17f, 18.4f)) }, Art.metal, ink, w)
            drawRect(Art.red, o(7.5f, 10.6f), Size(9f, 5.2f))
            fillAndInk(path { addOval(Rect(7f, 6.8f, 17f, 9.4f)) }, Art.ice, ink, w)
            drawArc(ink, 0f, 180f, false, o(7f, 17.1f), Size(10f, 2.6f), style = Stroke(w))
            drawCircle(Art.cream, 1.4f, o(12f, 13.2f))
        }
        "snacks" -> {
            fillAndInk(path { moveTo(8.4f, 12f); lineTo(4.5f, 9f); lineTo(4.5f, 15f); close() }, Art.pink, ink, w)
            fillAndInk(path { moveTo(15.6f, 12f); lineTo(19.5f, 9f); lineTo(19.5f, 15f); close() }, Art.pink, ink, w)
            val candy = path { addOval(Rect(7.6f, 8.8f, 16.4f, 15.2f)) }
            drawPath(candy, Art.pink)
            clipPath(candy) {
                for (x in listOf(9.2f, 12f, 14.8f)) inkLine(Art.white, 1.1f, o(x - 1.4f, 15.6f), o(x + 1.4f, 8.4f))
            }
            drawPath(candy, ink, style = Stroke(w))
        }
        "beverages" -> {
            inkLine(Art.red, 1.4f, o(13.6f, 10.4f), o(15.8f, 4.4f))
            fillAndInk(path { moveTo(7.4f, 7f); lineTo(16.6f, 7f); lineTo(15.2f, 19f); lineTo(8.8f, 19f); close() }, Art.ice, ink, w)
            drawPath(path { moveTo(7.8f, 10.4f); lineTo(16.2f, 10.4f); lineTo(15.25f, 18.5f); lineTo(8.75f, 18.5f); close() }, Art.orange)
            fillAndInk(path { addOval(Rect(14.4f, 6f, 18.4f, 10f)) }, Art.lemon, ink, w * 0.8f)
        }
        "frozen" -> {
            fillAndInk(path { addRoundRect(RoundRect(11f, 14.5f, 13f, 20.2f, CornerRadius(1f))) }, Art.wood, ink, w)
            fillAndInk(path {
                moveTo(8f, 15.5f); lineTo(8f, 7.4f); cubicTo(8f, 5f, 9.4f, 4.4f, 12f, 4.4f); cubicTo(14.6f, 4.4f, 16f, 5f, 16f, 7.4f)
                lineTo(16f, 15.5f); close()
            }, Art.sky, ink, w)
            drawRoundRect(Art.white.copy(alpha = 0.8f), o(9.4f, 6.4f), Size(1.4f, 6f), CornerRadius(0.7f))
            drawRect(Art.skyShade, o(8.5f, 12.4f), Size(7f, 1.2f))
        }
        "baby" -> {
            fillAndInk(path { moveTo(10f, 8.6f); cubicTo(10f, 5f, 14f, 5f, 14f, 8.6f); close() }, Art.honey, ink, w)
            fillAndInk(path { addRoundRect(RoundRect(8f, 8.4f, 16f, 10.6f, CornerRadius(0.8f))) }, Art.grapeLight, ink, w)
            val body = path { addRoundRect(RoundRect(8.6f, 10.6f, 15.4f, 19.6f, CornerRadius(2f))) }
            drawPath(body, Art.white)
            clipPath(body) { drawRect(Art.cream, o(8f, 14.6f), Size(8f, 6f)) }
            drawPath(body, ink, style = Stroke(w))
            for (y in listOf(12.8f, 15f, 17.2f)) inkLine(Art.metalShade, 0.7f, o(13.2f, y), o(15f, y))
        }
        "pet" -> {
            var bone = Path().apply { addRoundRect(RoundRect(7.6f, 10.4f, 16.4f, 13.6f, CornerRadius(1f))) }
            for (c in listOf(o(7.4f, 10.2f), o(7.4f, 13.8f), o(16.6f, 10.2f), o(16.6f, 13.8f))) {
                bone = Path.combine(PathOperation.Union, bone, Path().apply { addOval(Rect(c, 2.1f)) })
            }
            fillAndInk(bone, Art.cream, ink, w)
        }
        "household" -> {
            fillAndInk(path { addRoundRect(RoundRect(10.4f, 8.4f, 13.6f, 11.2f, CornerRadius(0.6f))) }, Art.white, ink, w)
            fillAndInk(path { moveTo(9f, 5.8f); lineTo(15.4f, 5.8f); lineTo(17.6f, 6.8f); lineTo(15.4f, 8.6f); lineTo(9f, 8.6f); close() }, Art.white, ink, w)
            fillAndInk(path { moveTo(8.6f, 11.2f); lineTo(15.4f, 11.2f); lineTo(16f, 19.2f); lineTo(8f, 19.2f); close() }, Art.teal, ink, w)
            drawRect(Art.white, o(9.4f, 13.6f), Size(5.4f, 3f))
        }
        "health" -> {
            fillAndInk(path { addRoundRect(RoundRect(6f, 6f, 18f, 18f, CornerRadius(3f))) }, Art.mint, ink, w)
            drawRect(Art.white, o(10.6f, 8f), Size(2.8f, 8f))
            drawRect(Art.white, o(8f, 10.6f), Size(8f, 2.8f))
        }
        "garden" -> {
            inkLine(Art.leafShade, 1.5f, o(12f, 12f), o(12f, 20f))
            fillAndInk(path { moveTo(12f, 17f); cubicTo(13f, 14.8f, 15.4f, 14.4f, 16.6f, 15f); cubicTo(15.6f, 16.8f, 13.6f, 17.4f, 12f, 17f); close() }, Art.leaf, ink, w * 0.8f)
            for (i in 0 until 5) {
                val a = Math.toRadians(-90.0 + i * 72.0)
                val c = o(12f + 3.2f * kotlin.math.cos(a).toFloat(), 10f + 3.2f * kotlin.math.sin(a).toFloat())
                fillAndInk(path { addOval(Rect(c, 2.6f)) }, Art.pink, ink, w * 0.8f)
            }
            fillAndInk(path { addOval(Rect(o(12f, 10f), 2f)) }, Art.lemon, ink, w * 0.8f)
        }
        else -> drawPaperBag()
    }
}

/** IconCatalog.DEFAULT_ICON ("some groceries"): a kraft paper bag with a baguette peeking out. */
private fun DrawScope.drawPaperBag() {
    val ink = Art.ink
    fillAndInk(path { addRoundRect(RoundRect(13.4f, 3.8f, 16.4f, 11f, CornerRadius(1.5f))) }, Art.tan, ink, STICKER_LINE)
    inkLine(Art.tanShade, 0.8f, o(14.2f, 6f), o(15.6f, 5.4f))
    fillAndInk(path {
        moveTo(6.6f, 8.6f); lineTo(8f, 7.6f); lineTo(9.4f, 8.6f); lineTo(10.8f, 7.6f); lineTo(12.2f, 8.6f); lineTo(13.6f, 7.6f)
        lineTo(15f, 8.6f); lineTo(16.4f, 7.6f); lineTo(17.4f, 8.6f); lineTo(18.2f, 19.6f); lineTo(5.8f, 19.6f); close()
    }, Art.kraft, ink, STICKER_LINE)
    drawRect(Art.tanShade.copy(alpha = 0.5f), o(14.6f, 9.2f), Size(3.1f, 10f))
    inkLine(Art.tanShade, 0.8f, o(8f, 12.2f), o(16f, 12.2f))
}

/* =============================== baskets =============================== */

private enum class Body { HAMPER, BOWL, DEEP_BOWL, CRATE, PUNNET, PAIL, TOTE, BOX, JAR, SHELF }
private enum class Weave { WICKER, SLATS, GRID, HOLES, RIBS, PLAIN }
private enum class Handle { NONE, LOOP, TALL_ARCH, SWING, CENTER_BAR, STRAPS }

private class BasketLook(val body: Body, val weave: Weave, val handle: Handle, val fill: Color, val shade: Color, val glass: Boolean = false)

private fun lookOf(kind: BasketKind): BasketLook = when (kind) {
    BasketKind.WICKER_PICNIC -> BasketLook(Body.HAMPER, Weave.WICKER, Handle.LOOP, Art.tan, Art.tanShade)
    BasketKind.BREAD_BASKET -> BasketLook(Body.BOWL, Weave.WICKER, Handle.NONE, Color(0xFFF0C987), Art.tanShade)
    BasketKind.FRUIT_CRATE -> BasketLook(Body.CRATE, Weave.SLATS, Handle.NONE, Art.wood, Art.woodShade)
    BasketKind.BERRY_PUNNET -> BasketLook(Body.PUNNET, Weave.GRID, Handle.SWING, Color(0xFFA8E0A0), Art.leafShade)
    BasketKind.WIRE_BASKET -> BasketLook(Body.DEEP_BOWL, Weave.GRID, Handle.SWING, Art.metal.copy(alpha = 0.55f), Art.metalShade)
    BasketKind.EGG_BASKET -> BasketLook(Body.DEEP_BOWL, Weave.GRID, Handle.TALL_ARCH, Color(0xFFF2D49B), Color(0xFFC9A35A))
    BasketKind.MILK_CRATE -> BasketLook(Body.CRATE, Weave.HOLES, Handle.NONE, Art.sky, Art.skyShade)
    BasketKind.COOLER -> BasketLook(Body.BOX, Weave.PLAIN, Handle.SWING, Color(0xFFFF8FA3), Art.lacquer)
    BasketKind.ICE_BUCKET -> BasketLook(Body.PAIL, Weave.RIBS, Handle.SWING, Art.metal, Art.metalShade)
    BasketKind.BENTO_BOX -> BasketLook(Body.BOX, Weave.PLAIN, Handle.NONE, Art.lacquer, Art.lacquerShade)
    BasketKind.JAR_SHELF -> BasketLook(Body.SHELF, Weave.PLAIN, Handle.NONE, Art.wood, Art.woodShade)
    BasketKind.CANVAS_TOTE -> BasketLook(Body.TOTE, Weave.PLAIN, Handle.STRAPS, Color(0xFFF6EBD3), Color(0xFFD9C7A0))
    BasketKind.FLOWER_BUCKET -> BasketLook(Body.PAIL, Weave.RIBS, Handle.NONE, Color(0xFFAFC3D1), Art.metalShade)
    BasketKind.BOTTLE_CARRIER -> BasketLook(Body.CRATE, Weave.SLATS, Handle.CENTER_BAR, Art.wood, Art.woodShade)
    BasketKind.CANDY_JAR -> BasketLook(Body.JAR, Weave.PLAIN, Handle.NONE, Art.ice.copy(alpha = 0.55f), Art.skyShade, glass = true)
    BasketKind.VANITY_CASE -> BasketLook(Body.BOX, Weave.PLAIN, Handle.NONE, Art.pink, Art.pinkShade)
}

/** Where the mini icons' centres sit (y, in the 64-unit grid) for a basket kind. */
internal fun basketIconLine(kind: BasketKind): Float = when (kind) {
    BasketKind.JAR_SHELF -> 27f
    BasketKind.BREAD_BASKET -> 26f
    BasketKind.BENTO_BOX, BasketKind.VANITY_CASE, BasketKind.COOLER -> 25f
    else -> 23f
}

private const val BASKET_LINE = 2f

/** Handles, open lids, stems and other parts BEHIND the mini icons (64-unit grid). [ink] = outline colour. */
internal fun DrawScope.drawBasketBack(kind: BasketKind, ink: Color) {
    val look = lookOf(kind)
    when (look.handle) {
        Handle.LOOP -> twoToneArc(path { moveTo(18f, 30f); cubicTo(18f, 6f, 46f, 6f, 46f, 30f) }, look.fill, ink, 4.6f)
        Handle.TALL_ARCH -> twoToneArc(path { moveTo(11f, 30f); cubicTo(11f, -1f, 53f, -1f, 53f, 30f) }, look.fill, ink, 4.2f)
        Handle.SWING -> twoToneArc(path { moveTo(12f, 31f); cubicTo(12f, 9f, 52f, 9f, 52f, 31f) }, Art.metal, ink, 3f)
        Handle.STRAPS -> {
            twoToneArc(path { moveTo(16f, 32f); cubicTo(16f, 5f, 30f, 5f, 30f, 32f) }, look.fill, ink, 4.2f)
            twoToneArc(path { moveTo(34f, 32f); cubicTo(34f, 5f, 48f, 5f, 48f, 32f) }, look.fill, ink, 4.2f)
        }
        Handle.CENTER_BAR -> {
            fillAndInk(path { addRoundRect(RoundRect(30f, 9f, 34f, 31f, CornerRadius(1f))) }, look.fill, ink, BASKET_LINE)
            fillAndInk(path { addRoundRect(RoundRect(22f, 6f, 42f, 11f, CornerRadius(2.5f))) }, look.fill, ink, BASKET_LINE)
        }
        Handle.NONE -> Unit
    }
    when (kind) {
        BasketKind.WICKER_PICNIC -> {
            fillAndInk(path { moveTo(6f, 30f); lineTo(30f, 30f); lineTo(25f, 18f); lineTo(3f, 21f); close() }, look.fill, ink, BASKET_LINE)
            fillAndInk(path { moveTo(34f, 30f); lineTo(58f, 30f); lineTo(61f, 21f); lineTo(39f, 18f); close() }, look.fill, ink, BASKET_LINE)
        }
        BasketKind.COOLER -> fillAndInk(path { moveTo(7f, 32f); lineTo(57f, 32f); lineTo(54f, 22f); lineTo(10f, 22f); close() }, Art.white, ink, BASKET_LINE)
        BasketKind.BENTO_BOX -> fillAndInk(path { moveTo(8f, 32f); lineTo(56f, 32f); lineTo(53f, 21f); lineTo(11f, 21f); close() }, look.shade, ink, BASKET_LINE)
        BasketKind.VANITY_CASE -> {
            fillAndInk(path { addRoundRect(RoundRect(8f, 11f, 56f, 32f, CornerRadius(5f))) }, look.shade, ink, BASKET_LINE)
            fillAndInk(path { addOval(Rect(19f, 14f, 45f, 29f)) }, Art.ice, ink, BASKET_LINE * 0.8f)
            inkLine(Art.white, 1.6f, o(24f, 19f), o(28f, 16.5f))
        }
        BasketKind.ICE_BUCKET -> for ((x, y) in listOf(13f to 24f, 25f to 21f, 37f to 23f, 47f to 22f)) {
            fillAndInk(path { addRoundRect(RoundRect(x, y, x + 8f, y + 8f, CornerRadius(2f))) }, Art.ice, ink, BASKET_LINE * 0.7f)
            inkLine(Art.white, 1.2f, o(x + 2f, y + 2.5f), o(x + 4f, y + 2f))
        }
        BasketKind.FLOWER_BUCKET -> {
            val blooms = listOf(Triple(16f, 6f, Art.pink), Triple(32f, 3f, Art.lemon), Triple(48f, 7f, Art.grapeLight), Triple(24f, 12f, Art.red))
            for ((x, y, color) in blooms) {
                inkLine(Art.leafShade, 2f, o(32f + (x - 32f) * 0.3f, 30f), o(x, y + 4f))
                for (i in 0 until 5) {
                    val a = Math.toRadians(-90.0 + i * 72.0)
                    drawCircle(color, 2.6f, o(x + 3f * kotlin.math.cos(a).toFloat(), y + 3f * kotlin.math.sin(a).toFloat()))
                }
                drawCircle(Art.lemonShade, 1.6f, o(x, y))
            }
        }
        BasketKind.BOTTLE_CARRIER -> for (x in listOf(12f, 46f)) {
            fillAndInk(path { addRoundRect(RoundRect(x, 13f, x + 6f, 31f, CornerRadius(2f))) }, Art.leaf.copy(alpha = 0.85f), ink, BASKET_LINE * 0.8f)
            fillAndInk(path { addRect(Rect(x - 0.5f, 10.5f, x + 6.5f, 13.5f)) }, Art.lemonShade, ink, BASKET_LINE * 0.8f)
        }
        BasketKind.CANDY_JAR -> {
            fillAndInk(path { addOval(Rect(40f, 13f, 60f, 21f)) }, Art.ice, ink, BASKET_LINE)
            fillAndInk(path { addOval(Rect(o(50f, 12f), 3f)) }, Art.red, ink, BASKET_LINE * 0.8f)
        }
        else -> Unit
    }
}

/** Body, weave, rim and front details IN FRONT of the mini icons (64-unit grid). */
internal fun DrawScope.drawBasketFront(kind: BasketKind, ink: Color) {
    val look = lookOf(kind)
    if (look.body == Body.SHELF) {
        drawJarShelf(ink)
        return
    }
    val body = bodyPath(look.body)
    drawPath(body, look.fill)
    clipPath(body) {
        drawWeave(look.weave, look.shade)
        // Cel shading: the right quarter in the shade colour.
        drawRect(look.shade.copy(alpha = if (look.glass) 0.25f else 0.35f), o(47f, 0f), Size(20f, 64f))
        when (kind) {
            BasketKind.COOLER -> drawRect(Art.white, o(0f, 50f), Size(64f, 14f))
            BasketKind.VANITY_CASE -> drawRect(Art.white.copy(alpha = 0.7f), o(0f, 42f), Size(64f, 3f))
            BasketKind.BENTO_BOX -> drawRect(Art.blush.copy(alpha = 0.85f), o(27f, 30f), Size(10f, 30f))
            BasketKind.CANDY_JAR -> for ((i, c) in listOf(Art.red, Art.lemon, Art.leaf, Art.pink, Art.sky, Art.orange, Art.grapeLight).withIndex()) {
                drawCircle(c, 3f, o(16f + (i * 5.3f), 52f - (i % 3) * 3.5f))
            }
            else -> Unit
        }
    }
    drawPath(body, ink, style = Stroke(BASKET_LINE, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    val rimFill = if (look.glass) Art.ice else look.fill.copy(alpha = 1f)
    rimRect(look.body)?.let { fillAndInk(path { addRoundRect(it) }, rimFill, ink, BASKET_LINE) }

    when (kind) {
        BasketKind.WICKER_PICNIC -> {
            val cloth = path { moveTo(14f, 31f); lineTo(30f, 31f); lineTo(21f, 42f); close() }
            drawPath(cloth, Art.red)
            clipPath(cloth) { drawGingham(Art.white.copy(alpha = 0.55f), 2.2f, Rect(12f, 30f, 32f, 44f)) }
            drawPath(cloth, ink, style = Stroke(BASKET_LINE * 0.8f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        BasketKind.BREAD_BASKET -> {
            fillAndInk(path { moveTo(7f, 31f); lineTo(20f, 31f); lineTo(11f, 42f); close() }, Art.cream, ink, BASKET_LINE * 0.8f)
            fillAndInk(path { moveTo(44f, 31f); lineTo(57f, 31f); lineTo(53f, 42f); close() }, Art.cream, ink, BASKET_LINE * 0.8f)
        }
        BasketKind.FRUIT_CRATE -> {
            for (x in listOf(2f, 57f)) drawArc(ink, 90f, 180f, false, o(x, 37f), Size(5f, 8f), style = Stroke(1.8f))
            fillAndInk(path { moveTo(29f, 50f); cubicTo(29f, 45f, 32f, 43f, 36f, 43f); cubicTo(36f, 48f, 33f, 50f, 29f, 50f); close() }, look.shade, ink, 1f)
        }
        BasketKind.EGG_BASKET -> for (x in listOf(10f, 18f, 27f, 36f, 45f, 53f)) inkLine(Art.lemonShade, 1.4f, o(x, 27.5f), o(x + 3f, 32.5f))
        BasketKind.MILK_CRATE -> fillAndInk(path { addRoundRect(RoundRect(25f, 33f, 39f, 37.5f, CornerRadius(2.2f))) }, look.shade, ink, 1.2f)
        BasketKind.COOLER -> fillAndInk(path { addRect(Rect(51f, 52f, 56f, 55f)) }, Art.metal, ink, 1.2f)
        BasketKind.ICE_BUCKET -> for ((x, y) in listOf(18f to 42f, 40f to 48f, 30f to 53f)) {
            drawPath(path { moveTo(x, y - 2.5f); quadraticTo(x + 1.6f, y, x, y + 1.5f); quadraticTo(x - 1.6f, y, x, y - 2.5f); close() }, Art.white.copy(alpha = 0.8f))
        }
        BasketKind.BENTO_BOX -> {
            inkLine(ink, 1.4f, o(7f, 45f), o(57f, 45f))
            fillAndInk(path { moveTo(32f, 33f); lineTo(25f, 28f); lineTo(25f, 37f); close() }, Art.blush, ink, 1.4f)
            fillAndInk(path { moveTo(32f, 33f); lineTo(39f, 28f); lineTo(39f, 37f); close() }, Art.blush, ink, 1.4f)
            fillAndInk(path { addOval(Rect(o(32f, 33f), 2.6f)) }, Art.pink, ink, 1.4f)
        }
        BasketKind.CANVAS_TOTE -> {
            drawLine(look.shade, o(10f, 35f), o(54f, 35f), 1.2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.5f, 2f)))
            fillAndInk(path {
                moveTo(39f, 43f); cubicTo(35.5f, 43f, 34f, 45.5f, 34.5f, 48f); cubicTo(35f, 51f, 37.5f, 53f, 39f, 54f)
                cubicTo(40.5f, 53f, 43f, 51f, 43.5f, 48f); cubicTo(44f, 45.5f, 42.5f, 43f, 39f, 43f); close()
            }, Art.red, ink, 1.3f)
            fillAndInk(path { moveTo(36f, 43.5f); lineTo(39f, 41f); lineTo(42f, 43.5f); lineTo(39f, 44.5f); close() }, Art.leaf, ink, 1f)
        }
        BasketKind.FLOWER_BUCKET -> drawLine(look.shade, o(11f, 44f), o(53f, 44f), 1.6f)
        BasketKind.VANITY_CASE -> fillAndInk(path { addRoundRect(RoundRect(28f, 31f, 36f, 37f, CornerRadius(1.5f))) }, Art.lemon, ink, 1.4f)
        BasketKind.CANDY_JAR -> {
            inkLine(Art.white, 2f, o(15f, 38f), o(14f, 47f))
            inkLine(Art.white, 1.4f, o(17.5f, 36f), o(17f, 38.5f))
        }
        else -> Unit
    }
}

private fun DrawScope.twoToneArc(p: Path, fill: Color, ink: Color, width: Float) {
    drawPath(p, ink, style = Stroke(width + BASKET_LINE * 1.4f, cap = StrokeCap.Round))
    drawPath(p, fill.copy(alpha = 1f), style = Stroke(width, cap = StrokeCap.Round))
}

private fun bodyPath(body: Body): Path = when (body) {
    Body.HAMPER -> path {
        moveTo(6f, 30f); lineTo(58f, 30f); lineTo(53f, 57f); quadraticTo(52.5f, 59f, 50.5f, 59f)
        lineTo(13.5f, 59f); quadraticTo(11.5f, 59f, 11f, 57f); close()
    }
    Body.BOWL -> path { moveTo(5f, 33f); lineTo(59f, 33f); cubicTo(59f, 47f, 50f, 56f, 32f, 56f); cubicTo(14f, 56f, 5f, 47f, 5f, 33f); close() }
    Body.DEEP_BOWL -> path { moveTo(7f, 30f); lineTo(57f, 30f); cubicTo(57f, 48f, 48f, 59f, 32f, 59f); cubicTo(16f, 59f, 7f, 48f, 7f, 30f); close() }
    Body.CRATE -> path { addRoundRect(RoundRect(6f, 30f, 58f, 58f, CornerRadius(2f))) }
    Body.PUNNET -> path { moveTo(13f, 32f); lineTo(51f, 32f); lineTo(48f, 58f); lineTo(16f, 58f); close() }
    Body.PAIL -> path {
        moveTo(9f, 30f); lineTo(55f, 30f); lineTo(50f, 58f); quadraticTo(49.6f, 59.5f, 48f, 59.5f)
        lineTo(16f, 59.5f); quadraticTo(14.4f, 59.5f, 14f, 58f); close()
    }
    Body.TOTE -> path { moveTo(9f, 31f); lineTo(55f, 31f); lineTo(52.5f, 59f); lineTo(11.5f, 59f); close() }
    Body.BOX -> path { addRoundRect(RoundRect(7f, 32f, 57f, 58f, CornerRadius(5f))) }
    Body.JAR -> path {
        moveTo(15f, 31f); lineTo(49f, 31f); cubicTo(58f, 36f, 59f, 54f, 47f, 59f); lineTo(17f, 59f); cubicTo(5f, 54f, 6f, 36f, 15f, 31f); close()
    }
    Body.SHELF -> path { addRect(Rect(3f, 51f, 61f, 57f)) }
}

private fun rimRect(body: Body): RoundRect? = when (body) {
    Body.HAMPER -> RoundRect(4f, 27f, 60f, 32f, CornerRadius(2f))
    Body.BOWL -> RoundRect(3f, 30.5f, 61f, 35.5f, CornerRadius(2.5f))
    Body.DEEP_BOWL -> RoundRect(5f, 27.5f, 59f, 32.5f, CornerRadius(2.5f))
    Body.PUNNET -> RoundRect(11f, 29.5f, 53f, 34f, CornerRadius(1.5f))
    Body.PAIL -> RoundRect(7f, 27.5f, 57f, 32.5f, CornerRadius(2.5f))
    Body.JAR -> RoundRect(14f, 28f, 50f, 32f, CornerRadius(2f))
    Body.CRATE, Body.TOTE, Body.BOX, Body.SHELF -> null
}

private fun DrawScope.drawWeave(weave: Weave, shade: Color) {
    when (weave) {
        Weave.WICKER -> {
            var row = 0
            var y = 35f
            while (y < 62f) {
                drawLine(shade, o(0f, y), o(64f, y), 1.4f)
                var x = if (row % 2 == 0) 6f else 9.5f
                while (x < 64f) {
                    drawLine(shade, o(x, y - 5f), o(x, y), 1.2f)
                    x += 7f
                }
                y += 5f
                row++
            }
        }
        Weave.SLATS -> {
            var y = 37f
            while (y < 62f) {
                drawLine(shade, o(0f, y), o(64f, y), 1.4f)
                y += 7f
            }
            for (y2 in listOf(33.5f, 40.5f, 47.5f, 54.5f)) {
                drawCircle(shade, 0.9f, o(10f, y2))
                drawCircle(shade, 0.9f, o(54f, y2))
            }
        }
        Weave.GRID -> {
            var x = 4f
            while (x < 64f) {
                drawLine(shade, o(x, 28f), o(x, 64f), 1.2f)
                x += 6f
            }
            var y = 36f
            while (y < 64f) {
                drawLine(shade, o(0f, y), o(64f, y), 1.2f)
                y += 6f
            }
        }
        Weave.HOLES -> {
            var y = 40f
            while (y < 58f) {
                var x = 10f
                while (x < 56f) {
                    drawRoundRect(shade, o(x, y), Size(6f, 5f), CornerRadius(1.5f))
                    x += 9f
                }
                y += 8f
            }
        }
        Weave.RIBS -> {
            var x = 14f
            while (x < 54f) {
                drawLine(shade, o(x, 30f), o(x, 64f), 1.2f)
                x += 6f
            }
            drawLine(shade, o(0f, 40f), o(64f, 40f), 1.6f)
        }
        Weave.PLAIN -> Unit
    }
}

/** JAR_SHELF front: three glass jars with gingham lids standing on a plank with brackets. */
private fun DrawScope.drawJarShelf(ink: Color) {
    val jars = listOf(Triple(Rect(7f, 38f, 21f, 51f), Art.red, 0.55f), Triple(Rect(25f, 35f, 39f, 51f), Art.lemon, 0.45f), Triple(Rect(43f, 39f, 57f, 51f), Art.orange, 0.6f))
    for ((r, jam, level) in jars) {
        val jar = path { addRoundRect(RoundRect(r, CornerRadius(3f))) }
        drawPath(jar, Art.ice.copy(alpha = 0.7f))
        clipPath(jar) { drawRect(jam, o(r.left, r.bottom - r.height * level), Size(r.width, r.height * level)) }
        drawPath(jar, ink, style = Stroke(BASKET_LINE))
        val lid = path { addRoundRect(RoundRect(r.left - 1f, r.top - 3f, r.right + 1f, r.top + 0.5f, CornerRadius(1f))) }
        drawPath(lid, Art.red)
        clipPath(lid) { drawGingham(Art.white.copy(alpha = 0.6f), 1.5f, Rect(r.left - 1f, r.top - 3f, r.right + 1f, r.top + 1f)) }
        drawPath(lid, ink, style = Stroke(BASKET_LINE * 0.8f))
        inkLine(Art.white, 1.4f, o(r.left + 2.5f, r.top + 3f), o(r.left + 2.5f, r.top + 6f))
    }
    fillAndInk(path { moveTo(9f, 57f); lineTo(15f, 57f); lineTo(9f, 62f); close() }, Art.woodShade, ink, BASKET_LINE * 0.8f)
    fillAndInk(path { moveTo(55f, 57f); lineTo(49f, 57f); lineTo(55f, 62f); close() }, Art.woodShade, ink, BASKET_LINE * 0.8f)
    fillAndInk(path { addRoundRect(RoundRect(3f, 51f, 61f, 57f, CornerRadius(1.5f))) }, Art.wood, ink, BASKET_LINE)
}
