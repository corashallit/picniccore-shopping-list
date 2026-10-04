package earth.cora.picnic.release

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Item 293 (docs/DESIGN_STICKER.md §17): none of the IP reference words (the franchises, their characters and phrases,
 * the 1990s sticker makers) and no real brand mark or store appears in anything the app ships or shows: its string
 * resources, its assets (names and text), its generated sources and the sticker, basket and decor DSL names (the
 * manifests and the drawable, raw, font and mipmap file names), the catalog's labels, the copy in the app's Kotlin, and
 * the fixtures and samples that render into the goldens. The words on the screens themselves are ScreenCopyTest's.
 *
 * The input side is §17's exception (Cora, 3 October): catalog aliases, the offline parser, prompts and eval inputs may
 * name brands, because that is what people type and say; they are matched to a generic item and never shown. So the
 * catalog's `aliases` and `drawHint` (sent to the image model, never shown), `core/src/main/resources/prompts/` and the
 * parser's tests are not scanned. Owner: WP10.
 */
class IpWordsTest {
    private fun assertClean(what: String, found: List<Found>, minimum: Int) {
        assertTrue("$what: the scan found ${found.size} texts, expected at least $minimum", found.size >= minimum)
        val offences = CopyRules.offences(found, CopyRules.ip)
        assertTrue("$what:\n" + offences.joinToString("\n"), offences.isEmpty())
    }

    /** A DSL or file name read as words: "sk_i_toilet_paper.xml" → "sk i toilet paper xml" (brands matched in any case). */
    private fun nameAsWords(name: String): String = name.replace(Regex("[_.\\-/]+"), " ")

    private fun assertNamesClean(what: String, names: List<Found>, minimum: Int) {
        assertTrue("$what: the scan found ${names.size} names, expected at least $minimum", names.size >= minimum)
        // Names are lower-case identifiers: compare them with the word lists in any case.
        val offences = names.flatMap { n ->
            val words = nameAsWords(n.text)
            CopyRules.ip.flatMap { rule -> (rule.matches(words) + rule.matches(titleCase(words))).distinct().map { "${rule.name}: '$it' in $n" } }
        }
        assertTrue("$what:\n" + offences.joinToString("\n"), offences.isEmpty())
    }

    private fun titleCase(words: String): String = words.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } +
        " " + words.uppercase()

    @Test
    fun stringResources() = assertClean("string resources", StringResources.all.map { Found("${it.file} ${it.name}", it.text) }, 300)

    @Test
    fun assetsAndResourceFiles() {
        val files = Repo.files(File(Repo.appMain, "assets")) + Repo.files(File(Repo.appMain, "res"))
        assertNamesClean("asset and resource file names", files.map { Found(Repo.rel(it), it.nameWithoutExtension) }, 450)
        // The text assets (the licences page's files) are shown as they are.
        val texts = files.filter { it.extension in setOf("txt", "json") }
        assertTrue("the licence texts are scanned", texts.size >= 5)
        assertClean("text assets", texts.flatMap { f -> f.readLines().mapIndexed { i, l -> Found("${Repo.rel(f)}:${i + 1}", l) } }, 100)
    }

    /** The generated manifests (StickerManifest, BasketManifest, DecorManifest, LcdGlyphs, PatternTiles): their names. */
    @Test
    fun generatedSourcesAndDslNames() {
        val gen = Repo.files(File(Repo.appMain, "kotlin/earth/cora/picnic/ui/theme/gen"), "kt")
        assertTrue("the generated manifests", gen.size >= 5)
        val literals = gen.flatMap { f -> KotlinText.literals(f).map { Found("${f.name}:${it.line}", it.text) } }
        assertNamesClean("generated sources (the DSL's sticker, basket, decor and glyph names)", literals, 300)
        assertNamesClean("catalog keys", Catalog.keys.map { Found("icon_catalog.json", it) }, 120)
    }

    @Test
    fun catalogLabels() = assertClean("catalog labels (what the sticker names read)", Catalog.labels, 140)

    /** The copy and names in the app's own Kotlin (and the core's output side: its messages and default aisles). */
    @Test
    fun sourceLiterals() {
        val app = Repo.files(File(Repo.appMain, "kotlin"), "kt").flatMap { f -> KotlinText.literals(f).map { Found("${Repo.rel(f)}:${it.line}", it.text) } }
        assertClean("the app's string literals", app, 2_000)
        val core = Repo.files(File(Repo.coreMain, "kotlin"), "kt")
            .flatMap { f -> KotlinText.literals(f).map { Found("${Repo.rel(f)}:${it.line}", it.text) } }
        assertClean("the core's string literals", core, 300)
    }

    /** §17: "every fixture that renders into a golden" uses generic names and the fixture store "Hillcrest Market". */
    @Test
    fun fixturesAndSamples() {
        assertTrue("the fixture files: ${Fixtures.files.map { it.name }}", Fixtures.files.size >= 10)
        assertClean("fixtures and samples", Fixtures.literals, 500)
    }
}
