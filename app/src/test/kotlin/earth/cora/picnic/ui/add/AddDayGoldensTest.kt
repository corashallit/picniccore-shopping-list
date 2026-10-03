package earth.cora.picnic.ui.add

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.ui.a11y.TextOverflowed
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * WP7's Day-only goldens: the gashapon pop and fan-out strip (item 205, one frame every 83 ms from the pop) and the
 * recipe card with pantry rows at font scale 1.3 and 2.0 (`add_fs130`, `add_fs200`, §15), which must clip no text
 * ([assertNoClippedText]; every other state at 1.3 and 2.0 is AddLargeTextTest's).
 * Record: ./gradlew :app:recordRoborazziDebug --tests '*AddDayGoldensTest*'.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class AddDayGoldensTest {
    @get:Rule
    val compose = createComposeRule()

    private val f = AddFixtures

    private fun recipeState(withPantry: Boolean, revealed: Boolean) = AddUiState(
        listId = f.LIST_ID, sheetOpen = true, mode = AddMode.WRITE, text = PANCAKES, sentText = PANCAKES, phase = AddPhase.Preview,
        review = f.review(3, f.pancakes(withPantry), ItemSource.AI_TEXT, PANCAKES, revealed = revealed),
    )

    /**
     * The pop and the fan-out (§4.18): 13 frames 83 ms apart from the pop at 750 ms, on a tall screen so the whole card
     * shows, cropped from the thinking box to the end of the card.
     */
    @Test
    @Config(qualifiers = "w360dp-h1500dp-xxhdpi")
    fun gashaponFanout() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PicnicTheme(ThemeVariant.Day, reducedMotion = false) { SheetStage { AddSheet(recipeState(withPantry = false, revealed = false), AddActions()) } }
        }
        compose.mainClock.advanceTimeByFrame()
        val pop = 750L
        compose.mainClock.advanceTimeBy(pop)
        val density = compose.density.density
        val frames = mutableListOf<Pair<Long, Bitmap>>()
        for (i in 0 until 13) {
            val root = compose.onRoot().captureToImage().asAndroidBitmap()
            val top = (compose.onNodeWithTag(THINKING_TAG).fetchSemanticsNode().boundsInRoot.top - 12 * density).toInt().coerceAtLeast(0)
            val height = (1_120 * density).toInt().coerceAtMost(root.height - top)
            frames += (pop + i * 83L) to Bitmap.createBitmap(root, 0, top, root.width, height)
            compose.mainClock.advanceTimeBy(83)
        }
        strip(frames).captureRoboImage("src/test/screenshots/gashapon_fanout.png")
    }

    /** Frames at 40 % in rows of four, each labelled with its time. */
    private fun strip(frames: List<Pair<Long, Bitmap>>): Bitmap {
        val scale = 0.3f
        val w = (frames.first().second.width * scale).toInt()
        val h = (frames.maxOf { it.second.height } * scale).toInt()
        val label = 44
        val gap = 12
        val cols = 4
        val rows = (frames.size + cols - 1) / cols
        val out = Bitmap.createBitmap(cols * (w + gap) + gap, rows * (h + label + gap) + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x2E, 0x3A, 0x8C)
            textSize = 30f
            typeface = Typeface.MONOSPACE
        }
        val filter = Paint(Paint.FILTER_BITMAP_FLAG)
        frames.forEachIndexed { i, (t, bmp) ->
            val x = gap + (i % cols) * (w + gap)
            val y = gap + (i / cols) * (h + label + gap)
            canvas.drawText("${t} ms", x.toFloat(), (y + 32).toFloat(), text)
            canvas.drawBitmap(bmp, null, Rect(x, y + label, x + w, y + label + (bmp.height * scale).toInt()), filter)
        }
        return out
    }

    @Test
    fun fontScale130() = fontScale("add_fs130", 1.3f)

    @Test
    fun fontScale200() = fontScale("add_fs200", 2f)

    /** The recipe card with pantry rows at [scale]: nothing clips (no Text with visual overflow, no TextOverflowed). */
    private fun fontScale(name: String, scale: Float) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                PicnicTheme(ThemeVariant.Day, reducedMotion = false) {
                    SheetStage { AddSheet(recipeState(withPantry = true, revealed = true), AddActions(onEditPantry = {})) }
                }
            }
        }
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(RECIPE_CARD_TAG))
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        if (scale >= 2f) {
            // The top of the sheet too: the tabs, the note and the thinking box.
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(ADD_TABS_TAG))
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("src/test/screenshots/${name}_top.png")
        }
        compose.assertNoClippedText("recipe card at $scale", earth.cora.picnic.ui.theme.LETTERSET_TAG)
    }

}
