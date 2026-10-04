package earth.cora.picnic.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.displayName
import earth.cora.picnic.ui.a11y.TextOverflowed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

/**
 * Every [LargeTextComponentsTest] again under Android's own font scaling (§15, item 337). From Android 14 a phone at
 * 130 % or 200 % scales text non-linearly (big text grows less than small text) through the platform's
 * FontScaleConverter, and Compose scales line heights by the font size's ratio there, not on their own. The parent
 * shows its pieces at a linear [Density]; here each scale's density comes from a configuration at that font scale, so
 * the converter applies, and the number circles, chips, pills and ruled fields must hold under it too.
 */
class LargeTextPlatformScaleTest : LargeTextComponentsTest() {
    override fun densityAt(density: Float, fontScale: Float): Density {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(context.resources.configuration).apply {
            this.fontScale = fontScale
            densityDpi = (density * 160f).roundToInt()
        }
        return Density(context.createConfigurationContext(config))
    }

    /** The densities above are the platform's: non-linear at 1.3 and 2.0 (24 sp is less than 2 × 24 dp at 2.0), linear at 1.0. */
    @Test
    fun theFontScaleIsThePlatformsNonLinearOne() {
        val px = 3f
        with(densityAt(px, 1f)) { assertEquals(24f * px, 24.sp.toPx(), 0.01f) }
        with(densityAt(px, 2f)) {
            assertEquals(2f, fontScale, 0f)
            assertTrue("24 sp at 2.0 is ${24.sp.toPx() / px} dp, not 48", 24.sp.toPx() < 24f * 2f * px - 1f)
            assertEquals("small text still doubles", 8f * 2f * px, 8.sp.toPx(), 1f)
        }
        with(densityAt(px, 1.3f)) { assertTrue("24 sp at 1.3 is ${24.sp.toPx() / px} dp", 24.sp.toPx() < 24f * 1.3f * px - 0.5f) }
    }

    /**
     * The phone itself at 200 % (the activity's own density, nothing overridden): the longest real labels in pills and
     * aisle chips, the store and rarity chips and a ruled field are whole, with no line lost, nothing ellipsized and no
     * TextOverflowed.
     */
    @Test
    @Config(qualifiers = "w360dp-h1600dp-xxhdpi", fontScale = 2.0f)
    fun thePhoneAt200PercentLosesNoWord() {
        var seen: Density? = null
        val aisles = Aisles.parse(Aisles.DEFAULT_TEXT)
        compose.setContent {
            seen = LocalDensity.current
            PicnicTheme(ThemeVariant.Day) {
                Column(Modifier.width(308.dp)) {
                    Box(Modifier.testTag("keep")) { PicnicButton("Keep everything, uncheck it all", {}, style = PicnicButtonStyle.Secondary) }
                    Box(Modifier.testTag("stick")) { PicnicButton("Stick all 12 on my list", {}, leadingStar = true) }
                    Box(Modifier.testTag("reading")) { PicnicButton("Reading… 6 of 9", {}, enabled = false, busy = true) }
                    Box(Modifier.testTag("settings")) { PicnicButton("Open phone settings", {}, style = PicnicButtonStyle.Secondary, glyph = PicnicGlyph.Settings) }
                    aisles.forEach { a -> Box(Modifier.testTag("aisle${a.position}")) { AisleChip(a.position, a.displayName(), AisleLooks.of(a.position), selected = false, onClick = {}) } }
                    Box(Modifier.testTag("store")) { StoreAisleChip(12) }
                    Box(Modifier.testTag("rarity")) { RarityChip(jackpot = true) }
                    Box(Modifier.testTag("field")) { PicnicTextField("pancakes\nmaple syrup", {}, minLines = 2) }
                }
            }
        }
        compose.waitForIdle()
        with(requireNotNull(seen)) {
            assertEquals(2f, fontScale, 0f)
            assertTrue("the platform's converter applies (24 sp is ${24.sp.toPx() / density} dp)", 24.sp.toPx() < 47f * density)
        }
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap { walk(it) }
        val tags = listOf("keep", "stick", "reading", "settings", "field") + aisles.map { "aisle${it.position}" }
        for (tag in tags) {
            val root = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            for (n in walk(root)) {
                assertTrue("$tag: TextOverflowed", n.config.getOrNull(TextOverflowed) != true)
                val action = n.config.getOrNull(SemanticsActions.GetTextLayoutResult) ?: continue
                if (n.config.getOrNull(SemanticsProperties.Text) == null && n.config.getOrNull(SemanticsProperties.EditableText) == null) continue
                val results = mutableListOf<TextLayoutResult>()
                action.action?.invoke(results)
                val r = results.singleOrNull() ?: continue
                assertFalse("$tag '${r.layoutInput.text}': lines cut", r.didOverflowHeight || r.multiParagraph.didExceedMaxLines)
                for (line in 0 until r.lineCount) assertFalse("$tag '${r.layoutInput.text}': line $line ellipsized", r.isLineEllipsized(line))
                assertTrue("$tag '${r.layoutInput.text}': clipped by an ancestor ${n.clippedAndWhole()}", n.isWhole())
            }
        }
        // The two decorative chips hide their words from TalkBack; their faces are at least their 1.0 size.
        for (tag in listOf("store", "rarity")) {
            val h = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
            assertTrue("$tag chip ${h / 3f} dp tall at 200 %", h >= 22f * 3f - 0.5f)
        }
    }
}
