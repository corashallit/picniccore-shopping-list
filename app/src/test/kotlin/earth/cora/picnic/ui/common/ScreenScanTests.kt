package earth.cora.picnic.ui.common

import androidx.compose.foundation.LocalIndication
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
import org.junit.Assert.assertSame
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
 * Text (always given a kit style and colour); no tap target falls back on Material's ripple (PicnicTheme wraps a
 * MaterialTheme whose LocalIndication is a ripple, so every clickable, selectable, toggleable and combinedClickable in
 * WP8's files names its indication, and every WP8 screen and sheet root runs under [NoMaterialPress]); and every text
 * the screens render has a colour of its own (never the Material default content colour).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class NoMaterialDefaultsTest {
    @get:Rule
    val compose = createComposeRule()

    // ScreenParts.kt is the v1 List screen's Material scaffold and bottom sheet (ListScreen, the v1 Add sheet); it goes
    // when WP5's List and WP7's Add land, and no WP8 screen uses it.
    private val sources = listOf("settings", "about", "aisles", "album", "pantry", "edit", "nav", "common")
        .flatMap { File("src/main/kotlin/earth/cora/picnic/ui/$it").walkTopDown().filter { f -> f.extension == "kt" && f.name != "ScreenParts.kt" }.toList() } +
        File("src/main/kotlin/earth/cora/picnic/ui/list/ListSwitcherSheet.kt") +
        File("src/main/kotlin/earth/cora/picnic/ui/PicnicAppRoot.kt")

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

    /**
     * Every clickable, selectable, toggleable, triStateToggleable and combinedClickable in WP8's files passes an explicit
     * `indication` (null where the target shows its own pressed state, or the kit's highlighter band), so none falls back
     * on LocalIndication, which inside PicnicTheme is Material's ripple.
     */
    @Test
    fun everyTapTargetNamesItsIndication() {
        val call = Regex("""\.(clickable|toggleable|selectable|combinedClickable|triStateToggleable)\(""")
        val offenders = mutableListOf<String>()
        var calls = 0
        for (file in sources) {
            val text = file.readText()
            for (m in call.findAll(text)) {
                calls++
                if (!Regex("""\bindication\s*=""").containsMatchIn(argumentsAt(text, m.range.last))) {
                    offenders += "${file.name}:${text.substring(0, m.range.first).count { it == '\n' } + 1} ${m.value}"
                }
            }
        }
        assertTrue("the scan found the tap targets ($calls)", calls >= 15)
        assertTrue("tap targets with Material's ripple (no explicit indication): $offenders", offenders.isEmpty())
    }

    /** The screens' and sheets' roots replace the ripple for anything inside them, kit pieces included. */
    @Test
    fun screenAndSheetRootsHaveNoRipple() {
        var inPicnicTheme: Any? = null
        var inScreen: Any? = null
        compose.setContent {
            PicnicTheme {
                inPicnicTheme = LocalIndication.current
                SecondaryScreen(title = "My store", onBack = {}) { inScreen = LocalIndication.current }
            }
        }
        compose.waitForIdle()
        // The hazard this guards against: PicnicTheme's MaterialTheme provides a ripple.
        assertTrue("PicnicTheme's default indication: $inPicnicTheme", inPicnicTheme !== NoPressIndication)
        assertSame(NoPressIndication, inScreen)
        // The two sheets WP8 owns open their content under NoMaterialPress too.
        for (name in listOf("edit/EditItemSheet.kt", "list/ListSwitcherSheet.kt")) {
            val text = File("src/main/kotlin/earth/cora/picnic/ui/$name").readText()
            assertTrue("$name runs under NoMaterialPress", text.contains("NoMaterialPress {"))
        }
    }

    /** The text between the "(" at [open] and its matching ")". */
    private fun argumentsAt(text: String, open: Int): String {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return text.substring(open + 1, i)
            }
        }
        return text.substring(open + 1)
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
