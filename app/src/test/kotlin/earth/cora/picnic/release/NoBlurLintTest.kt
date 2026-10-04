package earth.cora.picnic.release

import earth.cora.picnic.ui.theme.ButtonKind
import earth.cora.picnic.ui.theme.DayColors
import earth.cora.picnic.ui.theme.FaceOp
import earth.cora.picnic.ui.theme.GlowColors
import earth.cora.picnic.ui.theme.faceOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Item 307 (docs/DESIGN_STICKER.md §2.10, §17 "avoid: blurred shadows, semi-transparent gloss"; R3): no RenderEffect,
 * blur or live filter is used anywhere, no shadow is soft (every shadow is the element's own shape at a hard offset,
 * §2.10), and no gloss is semi-transparent (the buttons' crescent and dot, and every sticker's, basket's and decor's
 * gloss group, are opaque white). A scan of the app's Kotlin (comments aside) and every resource XML. Owner: WP10.
 */
class NoBlurLintTest {
    private val kotlin: List<File> = Repo.files(File(Repo.appMain, "kotlin"), "kt")
    private val xml: List<File> = Repo.files(File(Repo.appMain, "res"), "xml") + Repo.file("app/src/main/AndroidManifest.xml")

    /** Blur, render effects and live filters, and Compose's and Material's soft (blurred, elevation) shadows. */
    private val bannedKotlin = listOf(
        Regex("""\bRenderEffect\b""") to "RenderEffect",
        Regex("""\bBlurEffect\b""") to "BlurEffect",
        Regex("""\.blur\s*\(""") to "Modifier.blur",
        Regex("""\bBlurMaskFilter\b""") to "BlurMaskFilter",
        Regex("""\bEmbossMaskFilter\b""") to "EmbossMaskFilter",
        Regex("""\bsetShadowLayer\s*\(""") to "Paint.setShadowLayer (a blurred shadow)",
        Regex("""\bRuntimeShader\b""") to "RuntimeShader (a live filter)",
        Regex("""\brenderEffect\s*=""") to "graphicsLayer renderEffect",
        Regex("""(?<![\w.])Modifier\s*\.\s*shadow\s*\(|\)\s*\.\s*shadow\s*\(|^\s*\.shadow\s*\(""", RegexOption.MULTILINE) to "Modifier.shadow (a soft elevation shadow)",
        Regex("""\bshadowElevation\s*=\s*(?!0(\.0)?f?\b)""") to "graphicsLayer shadowElevation",
        Regex("""\b(ambientShadowColor|spotShadowColor)\b""") to "elevation shadow colours",
        Regex("""\b(tonalElevation|shadowElevation|elevation)\s*=\s*(?!0\.dp\b)[\d.]+\.dp""") to "Material elevation",
        Regex("""\b\w+Defaults\.\w*[eE]levation\s*\(""") to "Material component elevation",
    )

    /** The same in resource XML: elevation shadows and window blurs. */
    private val bannedXml = listOf(
        Regex("""android:(elevation|translationZ)\s*=\s*"(?!0(dp)?")""") to "View elevation (a soft shadow)",
        Regex("""android:(shadowRadius|shadowDx|shadowDy)\s*=""") to "text shadow",
        Regex("""(windowBlurBehind|windowBackgroundBlurRadius|backgroundBlurRadius|windowBlurBehindRadius)""") to "window blur",
    )

    @Test
    fun noBlurNoSoftShadowNoLiveFilter() {
        assertTrue("the scan reads the app's code (${kotlin.size} files)", kotlin.size >= 120)
        assertTrue("and its resources (${xml.size} files)", xml.size >= 400)
        val offences = kotlin.flatMap { f ->
            val code = KotlinText.code(f.readText())
            bannedKotlin.flatMap { (rx, what) ->
                rx.findAll(code).map { m -> "$what: ${Repo.rel(f)}:${code.substring(0, m.range.first).count { it == '\n' } + 1} '${m.value.trim()}'" }.toList()
            }
        } + xml.flatMap { f ->
            val text = f.readText()
            bannedXml.flatMap { (rx, what) ->
                rx.findAll(text).map { m -> "$what: ${Repo.rel(f)}:${text.substring(0, m.range.first).count { it == '\n' } + 1} '${m.value}'" }.toList()
            }
        }
        assertTrue(offences.joinToString("\n"), offences.isEmpty())
    }

    /** R3, §17: every button's gloss (one crescent, one dot) is opaque white, in every kind and theme variant. */
    @Test
    fun buttonGlossIsOpaque() {
        var glosses = 0
        for (colors in listOf(DayColors, GlowColors)) for (kind in ButtonKind.entries) for (enabled in listOf(true, false)) {
            for (op in faceOps(kind, enabled, colors, leadingStar = true)) {
                assertEquals("$kind enabled=$enabled: nothing on a face is blurred", 0f, op.blur)
                if (op is FaceOp.Crescent || op is FaceOp.Dot) {
                    glosses++
                    assertEquals("$kind enabled=$enabled: the gloss is opaque (${op.color})", 1f, op.color.alpha)
                    assertEquals("$kind: the gloss is white", androidx.compose.ui.graphics.Color.White, op.color)
                }
            }
        }
        assertTrue("every enabled kind has its gloss ($glosses)", glosses >= 2 * ButtonKind.entries.size * 2)
    }

    /**
     * §3.2, §17: every generated sticker, basket and decor drawable's `gloss` group is opaque white, on no translucent
     * group: no fill or stroke alpha below 1, no colour with an alpha channel below FF.
     */
    @Test
    fun stickerGlossIsOpaque() {
        val drawables = Repo.files(File(Repo.appMain, "res/drawable"), "xml").filter { it.name.startsWith("sk_") }
        var groups = 0
        val offences = ArrayList<String>()
        for (f in drawables) {
            val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(f)
            val all = doc.getElementsByTagName("group")
            for (i in 0 until all.length) {
                val g = all.item(i) as Element
                if (g.getAttribute("android:name") != "gloss") continue
                groups++
                fun check(e: Element) {
                    for (attr in listOf("android:fillAlpha", "android:strokeAlpha", "android:alpha")) {
                        val a = e.getAttribute(attr)
                        if (a.isNotEmpty() && a.toFloat() < 1f) offences += "${f.name}: $attr=$a in the gloss"
                    }
                    for (attr in listOf("android:fillColor", "android:strokeColor")) {
                        val c = e.getAttribute(attr)
                        if (c.length == 9 && !c.substring(1, 3).equals("FF", ignoreCase = true)) offences += "${f.name}: $attr=$c in the gloss"
                    }
                    val kids = e.childNodes
                    for (k in 0 until kids.length) (kids.item(k) as? Element)?.let(::check)
                }
                check(g)
                // A translucent ancestor group fades the gloss as well.
                var p = g.parentNode
                while (p is Element) {
                    val a = p.getAttribute("android:alpha")
                    if (a.isNotEmpty() && a.toFloat() < 1f) offences += "${f.name}: a parent group's alpha $a fades the gloss"
                    p = p.parentNode
                }
            }
        }
        assertTrue("the stickers' gloss groups are scanned ($groups)", groups >= 100)
        assertTrue(offences.joinToString("\n"), offences.isEmpty())
    }
}
