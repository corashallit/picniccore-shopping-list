package earth.cora.picnic.release

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import androidx.test.core.app.ApplicationProvider
import earth.cora.picnic.R
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * What the screen really draws (WP10's audits, docs/DESIGN_STICKER.md §2.7, §15): every window is drawn into a software
 * canvas that records each run of text as it is drawn (its words, its font, its size after every transform, its place
 * and its own ink on the screen, and what was already painted behind it) whatever drew it: a Text, PuffyText's passes,
 * a hanko, a numeral painted in a circle, a decorative doodle with no semantics. Compose draws a hardware-accelerated
 * window into a software canvas by running every draw block in place (layers concatenate their transform onto the
 * canvas), so the canvas's matrix is the run's real transform and nothing escapes the record.
 */

/** One run of text as the screen draws it (device px). */
class DrawnRun(
    /** The words drawn (zero-width no-break spaces from the platform's ellipsizer removed). */
    val text: String,
    /** The kit family it is drawn in ([KitFonts]), or "unknown …". */
    val family: String,
    /** The font size on the screen: the paint's size times the canvas's scale (px). */
    val sizePx: Float,
    /** A stroke pass (a puffy title's halo) rather than a fill. */
    val stroke: Boolean,
    /** The paint's alpha (0-255). */
    val alpha: Int,
    /** The run's line box on the screen (px): its advance by its ascent and descent. */
    val bounds: RectF,
    /** The app's frames that drew it, innermost first (a text drawn by a Text composable has none: Compose draws it). */
    val stack: List<String>,
    /** Which text draw call of the frame drew it (every call counts, the skipped ones too): its id in [IdCanvas]. */
    val call: Int,
    /** Where [ink] and [behind] lie on the screen (px): the run's box grown by [HALO_PAD_DP]. */
    val area: Rect,
    /** The run's own ink in [area] (row by row), as if nothing covered it. */
    val ink: BooleanArray,
    /** What was painted in [area] before the run was drawn: what lies straight behind its words (ARGB, row by row). */
    val behind: IntArray,
    /** Drawn inside an offscreen layer: [behind] then holds only the screen under the layer, not the layer's own paint. */
    val inLayer: Boolean,
    /** Maps a screen pixel to the run's own coordinates, where its baseline is [baseline] and its font's [ascent] (negative). */
    val toLocal: Matrix,
    val baseline: Float,
    val ascent: Float,
) {
    /** How many of its ink pixels are on the screen ([IdCanvas]): 0 when something opaque covers it or it is transparent. */
    var seenPixels: Int = -1

    /** How many ink pixels it has. */
    val inkPixels: Int = ink.count { it }

    /** The share of its ink that is on the screen. */
    val seenShare: Float get() = if (inkPixels == 0) 0f else seenPixels.toFloat() / inkPixels

    /** The size in dp (on a screen of [density] px per dp). */
    fun dp(density: Float): Float = sizePx / density

    /** Whether one of the app's frames that drew it is named [name] (its function, at the start of a frame's name). */
    fun drawnBy(name: String): Boolean = stack.any { it.substringAfter('.').startsWith(name) }

    override fun toString(): String = "'$text' ($family)"

    companion object {
        /** How far round a run's box its [DrawnRun.behind] reaches (dp). */
        const val HALO_PAD_DP = 3f
    }
}

/** The bundled families (§2.7), told apart by their metrics so a derived typeface (a weight, a synthesis) still maps. */
object KitFonts {
    const val DYNAPUFF = "DynaPuff"
    const val ZEN_MARU = "Zen Maru Gothic"
    const val KLEE_ONE = "Klee One"
    const val HACHI_MARU = "Hachi Maru Pop"
    const val SYSTEM = "system default"

    private const val PROBE = "Hamburgefonstiv 0123 &?"

    private data class Signature(val width: Float, val ascent: Float, val descent: Float)

    private fun signature(tf: Typeface?): Signature {
        val p = Paint().apply {
            typeface = tf
            textSize = 100f
        }
        val m = p.fontMetrics
        return Signature(p.measureText(PROBE), m.ascent, m.descent)
    }

    private val known: List<Pair<Signature, String>> by lazy {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(
            R.font.dynapuff_semibold to DYNAPUFF, R.font.dynapuff_bold to DYNAPUFF,
            R.font.zen_maru_gothic_medium to ZEN_MARU, R.font.zen_maru_gothic_bold to ZEN_MARU, R.font.zen_maru_gothic_black to ZEN_MARU,
            R.font.klee_one_semibold to KLEE_ONE, R.font.hachi_maru_pop_regular to HACHI_MARU,
        ).map { (id, name) -> signature(context.resources.getFont(id)) to name } +
            listOf(Typeface.DEFAULT, Typeface.DEFAULT_BOLD, Typeface.SANS_SERIF, Typeface.SERIF, Typeface.MONOSPACE).map { signature(it) to SYSTEM }
    }

    private val cache = IdentityHashMap<Typeface, String>()

    /** The family [tf] draws in. */
    @Synchronized
    fun familyOf(tf: Typeface?): String {
        val key = tf ?: Typeface.DEFAULT
        return cache.getOrPut(key) {
            val s = signature(key)
            known.firstOrNull { (k, _) -> abs(k.width - s.width) < 0.5f && abs(k.ascent - s.ascent) < 0.5f && abs(k.descent - s.descent) < 0.5f }?.second
                ?: "unknown ($key, ${"%.1f".format(s.width)})"
        }
    }
}

/**
 * A software canvas over [bitmap] that records every run of text drawn into it ([runs]), with its own ink and what was
 * already painted behind it. With [suppressText] the runs are recorded but not painted.
 */
class TextRecordingCanvas(
    private val bitmap: Bitmap,
    private val density: Float,
    private val suppressText: Boolean = false,
) : Canvas(bitmap) {
    val runs = ArrayList<DrawnRun>()
    private var calls = 0
    private val ctm = Matrix()
    private val values = FloatArray(9)
    private val clip = Rect()
    private val mask = Paint()

    /** For every level of the save stack, whether it is an offscreen layer. */
    private val layers = ArrayList<Boolean>()

    private fun record(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
        val call = calls++
        val raw = text.subSequence(start, end).toString()
        val words = raw.replace("﻿", "")
        if (words.isBlank()) return
        @Suppress("DEPRECATION")
        getMatrix(ctm)
        ctm.getValues(values)
        val scale = sqrt(abs(values[Matrix.MSCALE_X] * values[Matrix.MSCALE_Y] - values[Matrix.MSKEW_X] * values[Matrix.MSKEW_Y]))
        val metrics = paint.fontMetrics
        val box = RectF(x, y + metrics.ascent, x + paint.measureText(raw), y + metrics.descent)
        // A run wholly outside the clip is never seen (a row scrolled past the viewport's edge, still composed). The
        // clip is in the canvas's current coordinates, as the run is before its transform.
        if (getClipBounds(clip) && !RectF.intersects(box, RectF(clip))) return
        ctm.mapRect(box)
        val pad = DrawnRun.HALO_PAD_DP * density
        val area = Rect(
            max(0, floor(box.left - pad).toInt()), max(0, floor(box.top - pad).toInt()),
            min(bitmap.width, ceil(box.right + pad).toInt()), min(bitmap.height, ceil(box.bottom + pad).toInt()),
        )
        val w = max(1, area.width())
        val h = max(1, area.height())
        val behind = IntArray(w * h)
        if (area.width() > 0 && area.height() > 0) bitmap.getPixels(behind, 0, w, area.left, area.top, w, h)
        // The run's own ink: the same words, paint and transform, opaque and without anti-aliasing, alone.
        val inkBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mask.set(paint)
        mask.shader = null
        mask.colorFilter = null
        mask.isAntiAlias = false
        mask.color = 0xFF000000.toInt()
        val inkCanvas = Canvas(inkBitmap)
        inkCanvas.translate(-area.left.toFloat(), -area.top.toFloat())
        inkCanvas.concat(ctm)
        inkCanvas.drawText(raw, 0, raw.length, x, y, mask)
        val inkPx = IntArray(w * h)
        inkBitmap.getPixels(inkPx, 0, w, 0, 0, w, h)
        inkBitmap.recycle()
        val ink = BooleanArray(w * h) { (inkPx[it] ushr 24) != 0 }
        val toLocal = Matrix().also { ctm.invert(it) }
        runs += DrawnRun(
            words, KitFonts.familyOf(paint.typeface), paint.textSize * scale, paint.style == Paint.Style.STROKE, paint.alpha, box,
            appFrames(), call, area, ink, behind, layers.any { it }, toLocal, y, metrics.ascent,
        )
    }

    override fun save(): Int {
        layers += false
        return super.save()
    }

    override fun saveLayer(bounds: RectF?, paint: Paint?): Int {
        layers += true
        return super.saveLayer(bounds, paint)
    }

    override fun saveLayer(left: Float, top: Float, right: Float, bottom: Float, paint: Paint?): Int {
        layers += true
        return super.saveLayer(left, top, right, bottom, paint)
    }

    override fun saveLayerAlpha(bounds: RectF?, alpha: Int): Int {
        layers += true
        return super.saveLayerAlpha(bounds, alpha)
    }

    override fun saveLayerAlpha(left: Float, top: Float, right: Float, bottom: Float, alpha: Int): Int {
        layers += true
        return super.saveLayerAlpha(left, top, right, bottom, alpha)
    }

    override fun restore() {
        if (layers.isNotEmpty()) layers.removeAt(layers.size - 1)
        super.restore()
    }

    override fun restoreToCount(saveCount: Int) {
        // The canvas starts at a save count of 1; level n of [layers] is save count n + 1.
        while (layers.size >= saveCount && layers.isNotEmpty()) layers.removeAt(layers.size - 1)
        super.restoreToCount(saveCount)
    }

    override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) {
        record(String(text), index, index + count, x, y, paint)
        if (!suppressText) super.drawText(text, index, count, x, y, paint)
    }

    override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        record(text, 0, text.length, x, y, paint)
        if (!suppressText) super.drawText(text, x, y, paint)
    }

    override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
        record(text, start, end, x, y, paint)
        if (!suppressText) super.drawText(text, start, end, x, y, paint)
    }

    override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
        record(text, start, end, x, y, paint)
        if (!suppressText) super.drawText(text, start, end, x, y, paint)
    }

    override fun drawTextRun(text: CharArray, index: Int, count: Int, contextIndex: Int, contextCount: Int, x: Float, y: Float, isRtl: Boolean, paint: Paint) {
        record(String(text), index, index + count, x, y, paint)
        if (!suppressText) super.drawTextRun(text, index, count, contextIndex, contextCount, x, y, isRtl, paint)
    }

    override fun drawTextRun(text: CharSequence, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, isRtl: Boolean, paint: Paint) {
        record(text, start, end, x, y, paint)
        if (!suppressText) super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint)
    }

    override fun drawTextRun(text: android.graphics.text.MeasuredText, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, isRtl: Boolean, paint: Paint) {
        error("a MeasuredText run is drawn: WP10's text audits cannot read its words; teach TextRecordingCanvas to")
    }

    override fun drawTextOnPath(text: String, path: android.graphics.Path, hOffset: Float, vOffset: Float, paint: Paint) {
        error("text on a path (\"$text\"): WP10's text audits cannot place it; teach TextRecordingCanvas to")
    }

    override fun drawTextOnPath(text: CharArray, index: Int, count: Int, path: android.graphics.Path, hOffset: Float, vOffset: Float, paint: Paint) {
        error("text on a path: WP10's text audits cannot place it; teach TextRecordingCanvas to")
    }

    override fun drawGlyphs(glyphIds: IntArray, glyphIdOffset: Int, positions: FloatArray, positionOffset: Int, glyphCount: Int, font: android.graphics.fonts.Font, paint: Paint) {
        error("glyphs drawn by id: WP10's text audits cannot read them; teach TextRecordingCanvas to")
    }

    companion object {
        /** The app's own frames on the current stack (earth.cora.picnic, outside the tests), innermost first. */
        fun appFrames(): List<String> = Throwable().stackTrace
            .filter { it.className.startsWith("earth.cora.picnic.") && !it.className.startsWith("earth.cora.picnic.release.") }
            .map { "${it.className.substringAfterLast('.')}.${it.methodName}" }
            .distinct()
    }
}

/**
 * Draws the screen with every run of text in a colour of its own (its draw call's id, [idColour]), opaque and without
 * anti-aliasing, so the pixels left of that colour are the run's ink that is really seen: what is drawn over it later
 * covers it. Layers composite opaque (a fade or a tint would change the ids); translucent paint over a run (a sheet's
 * scrim) still hides it, so a run under a scrim counts as unseen.
 */
class IdCanvas(bitmap: Bitmap) : Canvas(bitmap) {
    private var calls = 0
    private val id = Paint()

    private fun idPaint(paint: Paint): Paint {
        val call = calls++
        id.set(paint)
        id.shader = null
        id.colorFilter = null
        id.maskFilter = null
        id.xfermode = null
        id.isAntiAlias = false
        id.color = idColour(call)
        return id
    }

    override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) = super.drawText(text, index, count, x, y, idPaint(paint))
    override fun drawText(text: String, x: Float, y: Float, paint: Paint) = super.drawText(text, x, y, idPaint(paint))
    override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) = super.drawText(text, start, end, x, y, idPaint(paint))
    override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) = super.drawText(text, start, end, x, y, idPaint(paint))
    override fun drawTextRun(text: CharArray, index: Int, count: Int, contextIndex: Int, contextCount: Int, x: Float, y: Float, isRtl: Boolean, paint: Paint) =
        super.drawTextRun(text, index, count, contextIndex, contextCount, x, y, isRtl, idPaint(paint))
    override fun drawTextRun(text: CharSequence, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, isRtl: Boolean, paint: Paint) =
        super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, idPaint(paint))

    override fun saveLayer(bounds: RectF?, paint: Paint?): Int = super.saveLayer(bounds, null)
    override fun saveLayer(left: Float, top: Float, right: Float, bottom: Float, paint: Paint?): Int = super.saveLayer(left, top, right, bottom, null)
    override fun saveLayerAlpha(bounds: RectF?, alpha: Int): Int = super.saveLayer(bounds, null)
    override fun saveLayerAlpha(left: Float, top: Float, right: Float, bottom: Float, alpha: Int): Int = super.saveLayer(left, top, right, bottom, null)

    companion object {
        /** The colour of text draw call [call]: its number in red and green, a blue no kit colour uses with them. */
        fun idColour(call: Int): Int = (0xFF shl 24) or (((call + 1) and 0xFFFF) shl 8) or 0x5A
    }
}

/** Every window's root view, bottom first (the activity, then popups and dialogs), as Android's window manager holds them. */
fun windowRoots(): List<View> {
    val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
    @Suppress("UNCHECKED_CAST")
    val views = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<View>
    return views.filter { it.isAttachedToWindow && it.visibility == View.VISIBLE && it.width > 0 && it.height > 0 }
}

/** Draws every window into [canvas] at its place on the screen. */
fun drawWindows(canvas: Canvas) {
    val at = IntArray(2)
    for (root in windowRoots()) {
        root.getLocationOnScreen(at)
        val save = canvas.save()
        canvas.translate(at[0].toFloat(), at[1].toFloat())
        root.draw(canvas)
        canvas.restoreToCount(save)
    }
}

/** The screen's size (px): the first window's. */
fun screenSize(): Pair<Int, Int> = windowRoots().first().let { it.width to it.height }
