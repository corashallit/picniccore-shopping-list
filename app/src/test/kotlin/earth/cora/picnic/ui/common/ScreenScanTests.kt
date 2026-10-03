package earth.cora.picnic.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.R
import earth.cora.picnic.ui.about.AboutActions
import earth.cora.picnic.ui.about.AboutScreen
import earth.cora.picnic.ui.about.AboutUiState
import earth.cora.picnic.ui.about.Licences
import earth.cora.picnic.ui.about.SurpriseLabScreen
import earth.cora.picnic.ui.aisles.AislesActions
import earth.cora.picnic.ui.aisles.AislesScreen
import earth.cora.picnic.ui.aisles.MyStoreSamples
import earth.cora.picnic.ui.album.AlbumActions
import earth.cora.picnic.ui.album.AlbumSamples
import earth.cora.picnic.ui.album.AlbumScreen
import earth.cora.picnic.ui.edit.EditItemSheet
import earth.cora.picnic.ui.edit.EditSamples
import earth.cora.picnic.ui.list.ListSwitcherSheet
import earth.cora.picnic.ui.list.SwitcherSamples
import earth.cora.picnic.ui.pantry.PantryActions
import earth.cora.picnic.ui.pantry.PantryScreen
import earth.cora.picnic.ui.pantry.PantrySamples
import earth.cora.picnic.ui.settings.LocationPermission
import earth.cora.picnic.ui.settings.SettingsActions
import earth.cora.picnic.ui.settings.SettingsSamples
import earth.cora.picnic.ui.settings.SettingsScreen
import earth.cora.picnic.ui.theme.PicnicTheme
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** WP8's screens, each rendered whole (the walkers below look at everything they show). */
private val wp8Screens: List<Pair<String, @Composable () -> Unit>> = listOf(
    "settings" to { SettingsScreen(SettingsSamples.ok, SettingsActions(), location = LocationPermission.Denied) },
    "about" to {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        AboutScreen(AboutUiState("0.4.0", Licences.load(context), labVisible = true, canUnlock = false), AboutActions())
    },
    "lab" to { SurpriseLabScreen(onBack = {}) },
    "mystore" to { AislesScreen(MyStoreSamples.default, AislesActions()) },
    "mystore_editing" to { AislesScreen(MyStoreSamples.default.copy(editing = "bakery"), AislesActions()) },
    "pantry" to { PantryScreen(PantrySamples.few, PantryActions()) },
    "album" to { AlbumScreen(AlbumSamples.state(AlbumSamples.mockup), AlbumActions()) },
    "edit" to { EditItemSheet(EditSamples.milk(), { _, _, _ -> }, {}, {}, {}, {}, {}) },
    "switcher" to { ListSwitcherSheet(SwitcherSamples.lists, {}, {}, {}, { _, _ -> }, {}) },
)

private fun SemanticsNode.allNodes(): List<SemanticsNode> = listOf(this) + children.flatMap { it.allNodes() }

/**
 * No model names anywhere in what the screens show or say (checklist 225, A1): My store has no AI card and nothing names a
 * model; the strings of WP8's screens don't either.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class NoModelNamesTest {
    @get:Rule
    val compose = createComposeRule()

    private val modelName = Regex("""(?i)\b(gpt|dall-?e|whisper|davinci|o[134](-mini)?|tts-1|text-embedding|claude|gemini|llama)\b|-(flare|terra|luna|transcribe)\b""")

    @Test
    fun screensNameNoModel() {
        var screen by mutableStateOf(wp8Screens.first())
        compose.setContent { PicnicTheme { Box(Modifier.fillMaxSize()) { screen.second() } } }
        val offenders = mutableListOf<String>()
        for (s in wp8Screens) {
            screen = s
            compose.waitForIdle()
            compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().allNodes().forEach { node ->
                val words = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                    listOfNotNull(node.config.getOrNull(SemanticsProperties.EditableText)?.text)
                words.filter { modelName.containsMatchIn(it) }.forEach { offenders += "${s.first}: $it" }
            }
        }
        assertTrue("model names on screen: $offenders", offenders.isEmpty())
        // My store has no AI card.
        assertTrue(compose.onAllNodesWithText("AI connection", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun screenStringsNameNoModel() {
        val file = File("src/main/res/values/strings_screens.xml")
        val offenders = file.readLines().filter { it.contains("<string") || it.contains("<item") }.filter { modelName.containsMatchIn(it.substringAfter('>')) }
        assertTrue("model names in strings: $offenders", offenders.isEmpty())
    }
}

/**
 * No secondary screen uses Material's defaults (checklist 235): the sources import nothing from Material but its plain
 * Text (always given a kit style and colour), and every text the screens render has a colour of its own (never the
 * Material default content colour).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class NoMaterialDefaultsTest {
    @get:Rule
    val compose = createComposeRule()

    private val sources = listOf("settings", "about", "aisles", "album", "pantry", "edit", "nav")
        .flatMap { File("src/main/kotlin/earth/cora/picnic/ui/$it").walkTopDown().filter { f -> f.extension == "kt" }.toList() } +
        listOf("ScreenChrome.kt", "ScreenBits.kt", "MagicAppear.kt").map { File("src/main/kotlin/earth/cora/picnic/ui/common/$it") } +
        File("src/main/kotlin/earth/cora/picnic/ui/list/ListSwitcherSheet.kt")

    @Test
    fun sourcesUseTheKit() {
        assertTrue(sources.size > 20)
        val banned = listOf(
            Regex("""import androidx\.compose\.material3\.(?!Text$)"""),
            Regex("""import androidx\.compose\.material\."""),
            Regex("""\bMaterialTheme\b"""),
            Regex("""\bIcons\."""),
            Regex("""\b(Button|Card|Switch|TextField|Checkbox|RadioButton|Slider|Chip)Defaults\b"""),
        )
        val offenders = sources.flatMap { f ->
            f.readLines().filter { line -> banned.any { it.containsMatchIn(line) } }.map { "${f.name}: ${it.trim()}" }
        }
        assertTrue("Material defaults in WP8 screens: $offenders", offenders.isEmpty())
    }

    @Test
    fun everyTextHasAKitColourDay() = checkTextColours(ThemeVariant.Day)

    @Test
    fun everyTextHasAKitColourGlow() = checkTextColours(ThemeVariant.Glow)

    private fun checkTextColours(variant: ThemeVariant) {
        var screen by mutableStateOf(wp8Screens.first())
        compose.setContent { PicnicTheme(variant = variant) { Box(Modifier.fillMaxSize()) { screen.second() } } }
        val offenders = mutableListOf<String>()
        for (s in wp8Screens) {
            screen = s
            compose.waitForIdle()
            compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().allNodes().forEach { node ->
                val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult) ?: return@forEach
                val results = mutableListOf<TextLayoutResult>()
                action.action?.invoke(results)
                results.forEach { r ->
                    val color = r.layoutInput.style.color
                    if (color == Color.Unspecified || color == Color.Black || color == MATERIAL_ON_SURFACE) {
                        offenders += "${s.first}: '${r.layoutInput.text}' in $color"
                    }
                }
            }
        }
        assertTrue("texts without a kit colour ($variant): $offenders", offenders.isEmpty())
    }

    private companion object {
        /** Material 3's default onSurface (what an unstyled Text would fall back to inside a MaterialTheme). */
        val MATERIAL_ON_SURFACE = Color(0xFF1D1B20)
    }
}

/** The launcher label and the wordmark read the one app-name resource (checklist 236). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class AppNameTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun labelAndWordmarkReadAppName() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Kirakira Basket", appName)
        assertEquals(R.string.app_name, context.applicationInfo.labelRes)
        assertEquals(appName, context.getString(R.string.wordmark))
        // The wordmark is an alias of app_name, not a second copy of the name.
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(Regex("""name="wordmark"[^>]*>@string/app_name<""").containsMatchIn(strings))
        // And the About page shows it.
        compose.setContent {
            PicnicTheme { AboutScreen(AboutUiState("0.4.0", emptyList(), labVisible = false), AboutActions()) }
        }
        assertTrue(compose.onAllNodesWithText(appName).fetchSemanticsNodes().isNotEmpty())
    }
}
