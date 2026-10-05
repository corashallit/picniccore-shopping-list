package earth.cora.picnic.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Item 303 (docs/DESIGN_STICKER.md §0.2 "no placeholder art or Material default survives", §20.20): `PlaceholderArt.kt`
 * is gone with no references; no placeholder or stand-in art, token or TODO-art note remains in the app's code or
 * resources; no art comes from Material's icon set or Android's launcher template; and none of the 52 legacy goldens of
 * the pre-art-pass app (`7be9b87`, `src/test/resources/release/legacy_goldens.txt`: each name with its git blob id) is
 * still in the screenshots folder unchanged: every one was re-recorded or retired. Owner: WP10.
 */
class NoPlaceholderTest {
    private val sources: List<File> = listOf(Repo.appMain, Repo.appTest, Repo.coreMain, Repo.coreTest).flatMap { Repo.files(it, "kt", "kts", "java") }
    private val mainCode: List<File> = Repo.files(File(Repo.appMain, "kotlin"), "kt")
    private val resources: List<File> = Repo.files(File(Repo.appMain, "res")) + Repo.files(File(Repo.appMain, "assets"))

    private fun lineOf(text: String, index: Int) = text.substring(0, index).count { it == '\n' } + 1

    @Test
    fun placeholderArtIsGone() {
        assertTrue("the scan reads the sources (${sources.size})", sources.size >= 300)
        val file = (sources + Repo.files(Repo.file("app/src"))).filter { it.name == "PlaceholderArt.kt" }
        assertEquals("PlaceholderArt.kt is deleted", emptyList<File>(), file)
        val refs = sources.filter { it.name != "NoPlaceholderTest.kt" }.flatMap { f ->
            val text = f.readText()
            Regex("""\bPlaceholderArt\b""").findAll(text).map { "${Repo.rel(f)}:${lineOf(text, it.range.first)}" }.toList()
        }
        assertTrue("references to PlaceholderArt: $refs", refs.isEmpty())
    }

    /**
     * No placeholder stand-ins in the app's code: no `Placeholder…` art, icon, image, sticker or theme token, no
     * "placeholder / stand-in / temporary / dummy art" and no TODO, FIXME or XXX about art, icons, stickers or goldens
     * (comments included). A text field's placeholder (its hint) and Compose's inline-content `Placeholder` are not art.
     */
    @Test
    fun noPlaceholderArtInTheCode() {
        assertTrue("the app's code (${mainCode.size})", mainCode.size >= 120)
        val banned = listOf(
            Regex("""\bPlaceholder\w*(Art|Icon|Image|Sticker|Drawable|Colors|Typography|Shapes|Theme|Tokens|Glyph|Basket)\b"""),
            Regex("""\b\w*(StandIn|Stub|Dummy|Temp)(Art|Icon|Image|Sticker|Drawable)\b"""),
            Regex("""(?i)\b(placeholder|stand-?in|temporary|temp|dummy|fake|stub)\s+(art|artwork|icons?|images?|stickers?|drawings?|baskets?)\b"""),
            Regex("""(?i)\b(TODO|FIXME|XXX)\b[^\n]*\b(art|artwork|icons?|stickers?|drawables?|goldens?|placeholders?|images?)\b"""),
        )
        val offences = (mainCode + resources.filter { it.extension == "xml" }).flatMap { f ->
            val text = f.readText()
            banned.flatMap { rx -> rx.findAll(text).map { "${Repo.rel(f)}:${lineOf(text, it.range.first)} '${it.value}'" }.toList() }
        }
        assertTrue(offences.joinToString("\n"), offences.isEmpty())
    }

    /** No resource or asset is named as a placeholder, a temporary or a sample. */
    @Test
    fun noPlaceholderFiles() {
        assertTrue("the resources and assets (${resources.size})", resources.size >= 450)
        val named = resources.filter { Regex("""(?i)(placeholder|todo|temp|tmp|dummy|stub|sample|test|untitled|copy)""").containsMatchIn(it.nameWithoutExtension) }
        assertTrue("placeholder-named files: ${named.map { Repo.rel(it) }}", named.isEmpty())
    }

    /** No art from Material's icon set; the launcher's layers are the sticker DSL's, not Android's template. */
    @Test
    fun noMaterialOrTemplateArt() {
        val material = mainCode.flatMap { f ->
            val code = KotlinText.code(f.readText())
            Regex("""androidx\.compose\.material\.icons|\bIcons\.(Default|Filled|Outlined|Rounded|Sharp|TwoTone|AutoMirrored)\b""")
                .findAll(code).map { "${Repo.rel(f)}:${lineOf(code, it.range.first)} '${it.value}'" }.toList()
        }
        assertTrue("Material icons: $material", material.isEmpty())
        val launcher = Repo.files(File(Repo.appMain, "res"), "xml").filter { it.parentFile.name.startsWith("mipmap") }
        assertTrue("the adaptive launcher icons", launcher.size >= 2)
        for (f in launcher) {
            val layers = Regex("""@drawable/(\w+)""").findAll(f.readText()).map { it.groupValues[1] }.toList()
            assertTrue("${f.name} has its layers", layers.size >= 2)
            assertTrue("${f.name}: every layer is the DSL's (sk_l_*): $layers", layers.all { it.startsWith("sk_l_") })
        }
        val template = Repo.files(File(Repo.appMain, "res")).filter { it.nameWithoutExtension in setOf("ic_launcher_background", "ic_launcher_foreground") }
        assertTrue("Android's template launcher art: $template", template.isEmpty())
    }

    /** The 52 legacy goldens (§20.20 item 303): each re-recorded or retired, none left as it was. */
    @Test
    fun legacyGoldensAreAllReRecorded() {
        val legacy = File(Repo.appTest, "resources/release/legacy_goldens.txt").readLines().filter { it.isNotBlank() }.map { line ->
            val (id, name) = line.trim().split(Regex("\\s+"), limit = 2)
            name to id
        }
        assertEquals("the legacy goldens of 7be9b87", 52, legacy.size)
        val shots = Repo.files(File(Repo.appTest, "screenshots"), "png")
        assertTrue("the goldens folder (${shots.size})", shots.size >= 500)
        val ids = legacy.map { it.second }.toSet()
        val unchanged = shots.filter { gitBlobId(it.readBytes()) in ids }.map { it.name }
        assertTrue("legacy goldens still as they were before the art pass: $unchanged", unchanged.isEmpty())
    }
}
