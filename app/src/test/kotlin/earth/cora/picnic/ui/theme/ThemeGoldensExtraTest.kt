package earth.cora.picnic.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.core.model.IconRef
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * R2 density goldens (checklist 54): the unchecked spot, the hanko, the ghost outline and the basket's landing spot
 * at 1, 2, 2.625, 3, 3.5 and 4× density, in Day and Glow: no half dash, clipped ring or blur at any of them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ThemeDensityGoldensTest(private val variant: ThemeVariant, private val v: String, private val density: Float, private val d: String) {
    @get:Rule
    val compose = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{3}_{1}")
        fun params(): List<Array<Any>> {
            val densities = listOf(1f to "1x", 2f to "2x", 2.625f to "2_625x", 3f to "3x", 3.5f to "3_5x", 4f to "4x")
            return dayAndGlow().flatMap { (variant, v) -> densities.map { (density, d) -> arrayOf(variant, v, density, d) } }
        }
    }

    @Test fun checkboxSpot() = compose.recordGolden("checkbox_spot_${d}_$v", variant, density = density) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckMark(checked = false, seed = "flour")
            StickerSpot()
            // An odd offset: the dashes still divide the ring at a fractional position.
            StickerSpot(Modifier.padding(start = 0.3.dp, top = 0.7.dp))
        }
    }

    @Test fun hanko() = compose.recordGolden("hanko_${d}_$v", variant, density = density) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckMark(checked = true, seed = "flour")
            CheckMark(checked = true, seed = "maple syrup")
            HankoStamp(HankoText.GotIt)
        }
    }

    @Test fun ghost() = compose.recordGolden("ghost_${d}_$v", variant, density = density) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            for (k in listOf("flour", "coffee", "apple")) ItemIcon(IconRef(k), null, size = 52.dp, ghost = true)
        }
    }

    @Test fun landingSpot() = compose.recordGolden("landing_spot_${d}_$v", variant, density = density) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            LandingSpot()
            LandingSpot(Modifier.padding(start = 0.3.dp, top = 0.7.dp))
            Box(Modifier.width(80.dp).height(24.dp).background(PicnicTheme.colors.pad), contentAlignment = Alignment.Center) { LandingSpot() }
        }
    }
}

/** Goldens named for one variant (checklist 36, 52, 64): Dusk and Golden buttons, the golden shadow, sun and moon. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ThemeVariantGoldensTest(private val name: String) {
    @get:Rule
    val compose = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = listOf(
            "buttons_matrix_dusk", "buttons_matrix_golden", "itemicon_shadow_golden", "appbar_sun_day", "appbar_moon_dusk", "appbar_moon_glow",
        ).map { arrayOf(it) }
    }

    @Test
    fun record() {
        val variant = when (name.substringAfterLast('_')) {
            "dusk" -> ThemeVariant.Dusk
            "golden" -> ThemeVariant.Golden
            "glow" -> ThemeVariant.Glow
            else -> ThemeVariant.Day
        }
        when {
            name.startsWith("buttons_matrix") -> compose.recordGolden(name, variant) { ButtonsKit() }
            name.startsWith("itemicon_shadow") -> compose.recordGolden(name, variant) { ShadowRow() }
            else -> compose.recordGolden(name, variant, gutter = false) { AppBarKit(collapsed = false) }
        }
    }
}

/** Day / Glow goldens beyond the component kits (checklist 10, 38, 264, 334). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ThemeExtraGoldensTest(private val variant: ThemeVariant, private val v: String) {
    @get:Rule
    val compose = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlow()
    }

    /** A list-shaped scene: the page leaves the 8 dp gutters, where Glow's stars (and only they) sit. */
    @Test fun listGlowStars() = compose.recordGolden("list_glow_stars_$v", variant, gutter = false) {
        Box(Modifier.fillMaxWidth().height(700.dp).padding(horizontal = 8.dp, vertical = 12.dp).background(PicnicTheme.colors.pad))
    }

    @Test fun holoVector() = compose.recordGolden("itemicon_holo_vector_$v", variant) { StickerLooksRow(StickerLook.Holo) }

    @Test fun pixel() = compose.recordGolden("itemicon_pixel_$v", variant) { StickerLooksRow(StickerLook.Pixel) }

    @Test fun holoBitmap() {
        val art = LiveStickerFixtures.art()
        compose.recordGolden("itemicon_holo_bitmap_$v", variant) {
            CompositionLocalProvider(LocalStickerArt provides art) {
                // The live stickers in holo (foil clipped to the bitmap's alpha), then the same stickers plain.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((item, _) in LiveStickerFixtures.V4) ItemIcon(LiveStickerFixtures.v4Ref(item), null, holo = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((item, _) in LiveStickerFixtures.V4) ItemIcon(LiveStickerFixtures.v4Ref(item), null)
                }
            }
        }
    }

    /**
     * A legacy live icon (checklist 47; §3.3 step 5): drawn as it is, with its own baked outline, border and shadow and
     * none added, at row, basket-mini and book sizes; its ghost is the monochrome silhouette at 18 % (no cut to peel
     * along). Below, at the 120 dp hero size, the legacy icon beside the same item redrawn in v4.
     */
    @Test fun legacyBitmap() {
        val art = LiveStickerFixtures.art()
        compose.recordGolden("itemicon_legacy_bitmap_$v", variant) {
            CompositionLocalProvider(LocalStickerArt provides art) {
                val legacy = LiveStickerFixtures.legacyRef()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemIcon(legacy, null, size = 52.dp)
                    ItemIcon(legacy, null, size = 52.dp, ghost = true)
                    ItemIcon(legacy, null, size = 30.dp)
                    ItemIcon(legacy, null, size = 22.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ItemIcon(legacy, null, size = 120.dp)
                    ItemIcon(LiveStickerFixtures.v4Ref(LiveStickerFixtures.LEGACY), null, size = 120.dp)
                }
            }
        }
    }

    /** The LCD star is a moon in Dusk and Glow; the Day file shows the Day star beside a Dusk moon. */
    @Test fun lcdMoon() = compose.recordGolden("lcd_moon_$v", variant) {
        LcdChip(LcdState(LcdMode.Count, count = 10, total = 24))
        if (variant == ThemeVariant.Day) PicnicTheme(ThemeVariant.Dusk) { LcdChip(LcdState(LcdMode.Count, count = 10, total = 24)) }
    }

    /** Titles ellipsized in the app bar and a narrow box: the stroke and shadow stay whole (checklist 334). */
    @Test fun puffyEllipsized() = compose.recordGolden("puffy_title_ellipsized_$v", variant, gutter = false) {
        BinderTopBar(
            title = "Saturday shop at Hillcrest Market and the farmers' market",
            subtitle = "Hillcrest Market · 14 stops",
            lcd = LcdState(LcdMode.Count, count = 104, total = 128),
            onMore = {},
            onSunTap = {},
            state = remember { AppBarState(false) },
        )
        Box(Modifier.padding(horizontal = 14.dp).width(150.dp)) {
            PuffyText("Everything else", PicnicTheme.type.aisleTab.copy(color = PicnicTheme.colors.ink), fill = PicnicTheme.colors.ink, stroke = 6.dp)
        }
    }
}

/** Live 208 px bitmaps at 52 dp on xxxhdpi (checklist 41): one bitmap pixel per screen pixel, crisp. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxxhdpi")
class ThemeLiveBitmapGoldensTest(private val variant: ThemeVariant, private val v: String) {
    @get:Rule
    val compose = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlow()
    }

    /** A library sticker, then the three live v4 stickers: the shadow and the die-cut edge come from each one's cut path. */
    @Test fun liveBitmap() {
        val art = LiveStickerFixtures.art()
        compose.recordGolden("itemicon_live_bitmap_xxxhdpi_$v", variant) {
            CompositionLocalProvider(LocalStickerArt provides art) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ItemIcon(IconRef("strawberry"), null, size = 52.dp)
                    for ((item, _) in LiveStickerFixtures.V4) ItemIcon(LiveStickerFixtures.v4Ref(item), null, size = 52.dp)
                }
            }
        }
    }
}

/**
 * Frame strips (Appendix C): `lcd_tick_<t>` (the digits tick −2 → +1 → 0 dp in 2 steps over 160 ms, checklist 264)
 * and `holo_card_frames_<t>` (the glint's 12 steps over 1.2 s, checklist 12), in Day.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ThemeStripGoldensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String) = compose.onNodeWithTag(GOLDEN_TAG).captureRoboImage("src/test/screenshots/$name.png")

    @Test
    fun lcdTick() {
        var count by mutableIntStateOf(9)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PicnicTheme(ThemeVariant.Day) {
                PicnicBackground(Modifier.testTag(GOLDEN_TAG)) {
                    Box(Modifier.padding(14.dp)) { LcdChip(LcdState(LcdMode.Count, count = count, total = 24)) }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { count = 10 }
        // The change recomposes on the next frame; the first pose (−2 dp) draws on the one after.
        for ((t, step) in listOf(0L to 32L, 80L to 80L, 160L to 80L)) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(step)
            compose.waitForIdle()
            capture("lcd_tick_$t")
        }
    }

    @Test
    fun holoCardFrames() {
        var glint by mutableFloatStateOf(0f)
        compose.setContent {
            PicnicTheme(ThemeVariant.Day) {
                PicnicBackground(Modifier.testTag(GOLDEN_TAG)) {
                    Box(Modifier.padding(14.dp)) { HoloCardSample(glint) }
                }
            }
        }
        for (step in 0 until Holo.glint.steps) {
            val t = step * Holo.glint.durationMillis / Holo.glint.steps
            glint = Holo.glint.progressAt(t.toLong())
            compose.waitForIdle()
            capture("holo_card_frames_$t")
        }
    }
}
