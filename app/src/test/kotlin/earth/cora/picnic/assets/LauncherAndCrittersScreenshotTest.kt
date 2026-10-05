package earth.cora.picnic.assets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import earth.cora.picnic.R
import earth.cora.picnic.ui.theme.gen.DecorManifest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for checklist items 49 and 297 (WP2): the adaptive launcher icon under the circle, squircle and teardrop masks
 * and as a themed (monochrome) icon, on a Day and a Glow home screen (`launcher_{circle,squircle,teardrop,mono}_<variant>`),
 * and the critter / decor sticker sheet for the IP review (`critters_contact_<variant>`: the nap cat without a forehead
 * mark or bow, the ant always carrying food, the bee, the ladybug, and every other decor sticker), drawn from the
 * generated drawables with their hard shadow at render time (§3.3). Record with ./gradlew :app:recordRoborazziDebug.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class LauncherAndCrittersScreenshotTest(private val variant: String) {
    @get:Rule
    val compose = createComposeRule()

    private val glow get() = variant == "glow"
    private val binder get() = if (glow) Color(0xFF161A3E) else Color(0xFFE6F6EE)
    private val ink get() = if (glow) Color.White else Color(0xFF25307C)
    private val shadow get() = if (glow) Color(0xFF0B0D24) else Color(0xFF6E7BD0)

    @Test
    fun launcherCircle() = snapLauncher("circle", MaskShape(CIRCLE))

    @Test
    fun launcherSquircle() = snapLauncher("squircle", MaskShape(SQUIRCLE))

    @Test
    fun launcherTeardrop() = snapLauncher("teardrop", MaskShape(TEARDROP))

    @Test
    fun launcherMono() = snap("launcher_mono") {
        // a themed icon: the launcher fills the mask with its own colour and tints the monochrome layer
        Box(Modifier.size(LAYER_VISIBLE).clip(CircleShape).background(if (glow) Color(0xFF25307C) else Color(0xFFE4E8FF)),
            contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.sk_l_launcher_monochrome), contentDescription = null,
                colorFilter = ColorFilter.tint(if (glow) Color(0xFFDCE1FF) else Color(0xFF25307C)),
                modifier = Modifier.requiredSize(LAYER))
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun crittersContact() = snap("critters_contact") {
        val label = TextStyle(fontFamily = FontFamily(Font(R.font.zen_maru_gothic_bold, FontWeight.Bold)), fontSize = 11.sp,
            color = ink, textAlign = TextAlign.Center)
        FlowRow(Modifier.width(344.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((name, id) in DecorManifest.stickers.toSortedMap(compareBy({ it !in CRITTERS }, { it }))) {
                Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(52.dp)) {
                        val painter = painterResource(id)
                        Image(painter, null, colorFilter = ColorFilter.tint(shadow),
                            modifier = Modifier.size(52.dp).offset(1.6.dp, 2.2.dp))
                        Image(painter, null, modifier = Modifier.size(52.dp))
                    }
                    Text(name, style = label, maxLines = 1)
                }
            }
        }
    }

    private fun snapLauncher(mask: String, shape: Shape) = snap("launcher_$mask") {
        Box(Modifier.size(LAYER_VISIBLE).clip(shape), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.sk_l_launcher_background), null, modifier = Modifier.requiredSize(LAYER))
            Image(painterResource(R.drawable.sk_l_launcher_foreground), null, modifier = Modifier.requiredSize(LAYER))
        }
    }

    private fun snap(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            Box(Modifier.background(binder).padding(8.dp), contentAlignment = Alignment.Center) { content() }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_$variant.png")
    }

    /** an AOSP icon mask (`config_icon_mask`, a 100 x 100 path) scaled to the shape's size */
    private class MaskShape(private val d: String) : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
            val path = PathParser().parsePathString(d).toPath()
            path.transform(Matrix().apply { scale(size.width / 100f, size.height / 100f) })
            return Outline.Generic(path)
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = listOf(arrayOf("day"), arrayOf("glow"))

        /** the 108 dp layers at 2x, and the 72 dp a launcher shows of them */
        private val LAYER = 216.dp
        private val LAYER_VISIBLE = 144.dp
        private const val CIRCLE = "M50 0A50 50 0 1 1 50 100A50 50 0 1 1 50 0Z"
        private const val SQUIRCLE = "M50 0C10 0 0 10 0 50S10 100 50 100 100 90 100 50 90 0 50 0Z"
        private const val TEARDROP = "M50 0A50 50 0 0 1 100 50V85A15 15 0 0 1 85 100H50A50 50 0 0 1 50 0Z"
        private val CRITTERS = setOf("cat", "ant", "antcrumb", "bee", "ladybug", "ladybugfly", "dustbunny")
    }
}
