package earth.cora.picnic.assets

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.VectorDrawable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.res.vectorResource
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.R
import earth.cora.picnic.ui.theme.gen.BasketManifest
import earth.cora.picnic.ui.theme.gen.DecorManifest
import earth.cora.picnic.ui.theme.gen.LcdGlyphs
import earth.cora.picnic.ui.theme.gen.PatternTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The emitter check on Android (WP2, docs/DESIGN_STICKER.md §5, §13.1, §2.12, §3.4; checklist items 49 and 101): every
 * generated basket, decor, pictogram, glyph, tile and launcher drawable inflates both as a framework VectorDrawable and
 * as a Compose ImageVector, and the generated manifests describe a complete, consistent set. The pixels are reviewed on
 * the DSL contact sheets (design/stickers/tools/baskets_contact.py, decor_contact.py) and the launcher and critter
 * goldens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeneratedArtTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun wp2Drawables(): Map<String, Int> =
        R.drawable::class.java.fields
            .filter { f -> WP2_PREFIXES.any { f.name.startsWith(it) } }
            .associate { it.name to it.getInt(null) }

    @Test
    fun everyWp2DrawableInflatesOnAndroidAndInCompose() {
        val all = wp2Drawables()
        assertTrue("found ${all.size}", all.size >= 150)
        for ((name, id) in all) {
            val d = ContextCompat.getDrawable(context, id)
            assertTrue(name, d is VectorDrawable)
            assertTrue(name, d!!.intrinsicWidth > 0 && d.intrinsicHeight > 0)
            val v = ImageVector.vectorResource(context.theme, context.resources, id)
            assertTrue(name, v.root.size > 0)
        }
    }

    @Test
    fun seventeenBasketKindsWithEveryLayer() {
        assertEquals(17, BasketManifest.kinds.size)
        assertEquals(KINDS, BasketManifest.kinds.map { it.kind }.toSet())
        assertEquals(DECORATED, BasketManifest.kinds.filter { it.deco != null }.map { it.kind }.toSet())
        assertEquals(setOf("bento_box", "cooler", "vanity_case"), BasketManifest.kinds.filter { it.lidOpenStandsBehind }.map { it.kind }.toSet())
        val density = context.resources.displayMetrics.density
        for (art in BasketManifest.kinds) {
            for ((layer, id) in listOf("back" to art.back, "front" to art.front, "lid_open" to art.lidOpen, "lid_closed" to art.lidClosed)) {
                val v = ImageVector.vectorResource(context.theme, context.resources, id)
                assertEquals("${art.kind} $layer", 80f, v.viewportWidth)
                assertEquals("${art.kind} $layer", 64f, v.viewportHeight)
                val d = ContextCompat.getDrawable(context, id)!!
                assertEquals("${art.kind} $layer width", (80 * density).toInt(), d.intrinsicWidth)
                val groups = v.root.filterIsInstance<VectorGroup>().map { it.name }
                val want = when (layer) {
                    "back" -> listOf("cut", "art")
                    "front" -> listOf("art", "gloss")
                    "lid_open" -> listOf("cut", "art")
                    else -> listOf("cut", "art", "gloss")
                }
                assertEquals("${art.kind} $layer groups", want, groups)
            }
            art.deco?.let { assertNotNull(ContextCompat.getDrawable(context, it)) }
            // the decoration also lives in the back layer, so a decorated basket keeps it when empty (R4)
            val backGroups = ImageVector.vectorResource(context.theme, context.resources, art.back).root
                .filterIsInstance<VectorGroup>().first { it.name == "art" }
                .filterIsInstance<VectorGroup>().map { it.name }
            assertEquals(art.kind, art.kind in DECORATED, "deco" in backGroups)
            assertTrue(art.kind, art.rimY in 10f..40f && art.hankoX in 0f..80f && art.hankoY in 0f..64f)
        }
        assertEquals((1..BasketManifest.MAX_MINIS).toSet(), BasketManifest.MINI_SLOTS.keys)
        for ((n, slots) in BasketManifest.MINI_SLOTS) assertEquals(n, slots.size)
    }

    @Test
    fun glyphSetIsComplete() {
        assertTrue(DecorManifest.glyphs.keys.containsAll(SPEC_GLYPHS + INLINE_GLYPHS))
        assertTrue(DecorManifest.pictograms.keys.containsAll(PICTOGRAMS))
        assertTrue(DecorManifest.stickers.keys.containsAll(DECOR))
    }

    @Test
    fun lcdGlyphTableCoversTheSpecSet() {
        val needed = " !#%&'()+,-./0123456789:?ABCDEFGHIJKLMNOPQRSTUVWXYZ·♪★●♡"
        for (c in needed) {
            assertNotNull("'$c'", LcdGlyphs.glyph(c))
            assertEquals("'$c'", LcdGlyphs.ROWS, LcdGlyphs.glyphs.getValue(c).rows.size)
            assertTrue("'$c'", LcdGlyphs.glyphs.getValue(c).width in 1..5)
        }
        assertEquals(setOf('★', '●', '♪', '♡'), LcdGlyphs.extras.keys)
        for ((c, e) in LcdGlyphs.extras) assertTrue("$c", e.advance > 0f && e.pathData.startsWith("M"))
        // DotGothic16-style shapes: the zero has its slash, the I is three columns wide
        assertTrue(LcdGlyphs.glyph('0')!!.lit(3, 2))
        assertEquals(3, LcdGlyphs.glyph('I')!!.width)
    }

    @Test
    fun patternsAndWashi() {
        assertEquals(listOf("gingham", "polka", "stripes", "hearts", "stars", "clouds", "check", "waves", "flowers", "plain"),
            PatternTiles.tiles.keys.toList())
        assertTrue(PatternTiles.washi.keys.containsAll(listOf("sakura", "gingham", "dots", "mint", "maple", "pumpkin", "ghost",
            "snow", "hearts", "redenvelope", "raindrop")))
        assertEquals(setOf("CANADA_DAY", "THANKSGIVING", "HALLOWEEN", "FIRST_SNOW", "VALENTINES", "LUNAR_NEW_YEAR"),
            PatternTiles.seasonWashi.keys)
        assertTrue(PatternTiles.seasonWashi.values.all { it in PatternTiles.washi })
    }

    @Test
    fun adaptiveLauncherIconHasItsThreeLayers() {
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val icon = ContextCompat.getDrawable(context, id) as AdaptiveIconDrawable
            assertNotNull(icon.background)
            assertNotNull(icon.foreground)
            assertNotNull(icon.monochrome)
        }
    }

    companion object {
        val WP2_PREFIXES = listOf("sk_b_", "sk_d_", "sk_p_", "sk_g_", "sk_t_", "sk_l_")
        val KINDS = setOf("wicker_picnic", "bread_basket", "bento_box", "mixing_bowl", "fruit_crate", "egg_basket", "cooler",
            "canvas_tote", "wire_basket", "berry_punnet", "milk_crate", "ice_bucket", "flower_bucket", "bottle_carrier",
            "candy_jar", "jar_shelf", "vanity_case")
        val DECORATED = setOf("ice_bucket", "flower_bucket", "bottle_carrier", "candy_jar")
        /** §3.4 */
        val SPEC_GLYPHS = listOf("add", "close", "check", "back", "more", "camera", "gallery", "mic", "stop", "send",
            "settings", "aisles", "lists", "edit", "delete", "undo", "refresh", "sparkle", "expand", "collapse", "link",
            "drag", "key", "eye", "web", "search", "sort", "scissors", "album", "trip", "cassette", "envelope", "polaroid")
        /** the small glyphs other sections name, and the inline ♡ ★ ☆ ♪ of copy (§2.7) */
        val INLINE_GLYPHS = listOf("jar", "basket", "torn", "paste", "heart", "star", "star_outline", "note")
        /** §2.3 and §4.13, seasonal included, and the capsule of "Everything else" */
        val PICTOGRAMS = listOf("strawberry", "bee", "star", "heart", "cloud", "flower", "ladybug", "teacup", "sandwich", "sun",
            "capsule", "maple", "pumpkin", "ghost", "snowflake", "envelope")
        /** the page stickers, critters and egg stickers the screens place (§4.1, §4.27, §6.13, §12) */
        val DECOR = listOf("star", "star4", "heart", "cloud", "bee", "ladybug", "ladybugfly", "ant", "antcrumb", "blanket", "sun", "moon",
            "glowstar", "cat", "petal", "umbrella", "kira", "scissors", "envelope", "basketdeco", "dustbunny", "hamper", "cake",
            "ribbon", "mapleleaf", "pumpkin", "snowflake", "redenvelope")
    }
}
