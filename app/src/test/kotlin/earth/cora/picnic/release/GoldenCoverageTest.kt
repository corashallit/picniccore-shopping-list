package earth.cora.picnic.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Item 304 (docs/DESIGN_STICKER.md §20.20, Appendix C): every state the spec's golden tables name has a golden, in each
 * variant its row asks for, and every golden in `app/src/test/screenshots/` is one of them (a frame of a named strip
 * included): none is left over from a state that no longer exists. The manifest (`manifest.txt` beside the goldens) is
 * Appendix C expanded by its own rules, so the two cannot drift; every golden a §20 item names exists too. That a test
 * still writes each golden (no orphan) is checked on CI's full compare run by the golden gate (item 305,
 * `checkComparedGoldens` in app/build.gradle.kts). Owner: WP10.
 */
class GoldenCoverageTest {
    /** One golden the spec asks for: its manifest label and the file names (without .png) that satisfy it. */
    data class Need(val label: String, val pattern: Regex, val where: String)

    private val doc: String = File(Repo.root, "docs/DESIGN_STICKER.md").readText()
    private val shotsDir = File(Repo.appTest, "screenshots")
    private val shots: List<String> = shotsDir.listFiles().orEmpty().filter { it.extension == "png" }.map { it.nameWithoutExtension }.sorted()
    private val manifest = File(shotsDir, "manifest.txt")

    private val variants = listOf("day", "glow", "dusk", "golden", "rm")
    private val variantSuffix = Regex("_(${variants.joinToString("|")})$")
    private val dayOnlyEnding = Regex("_(fs130|fs200|w411)$")
    private val densities = listOf("1x", "2x", "2_625x", "3x", "3_5x", "4x")
    private val categories: List<String> get() = Catalog.categories
    private val fontScaleScreens = listOf("list", "add", "edit", "mystore", "settings", "album", "nextcard", "celebration_summary", "receipt")

    private fun braces(name: String): List<String> {
        val m = Regex("""\{([^}]*)\}""").find(name) ?: return listOf(name)
        return m.groupValues[1].split(',').flatMap { braces(name.substring(0, m.range.first) + it.trim() + name.substring(m.range.last + 1)) }
    }

    /** Appendix C's expansion rules for one name of a row ([dayOnly]: the row says "Day only"). */
    fun expand(raw: String, strip: Boolean, dayOnly: Boolean, where: String): List<Need> {
        if (raw == "<preview>") return listOf(Need("earth.cora.picnic.*", Regex("""earth\.cora\.picnic\..+"""), where))
        if (raw == "*_fs130" || raw == "*_fs200") return fontScaleScreens.map { s -> (s + raw.drop(1)).let { Need(it, Regex(Regex.escape(it)), where) } }
        val asWritten = raw.contains("{day,glow}") || raw.contains("<variant>") || variantSuffix.containsMatchIn(raw)
        val out = ArrayList<Need>()
        for (n in braces(raw)) {
            var alts = listOf(n)
            if ("<density>" in n) alts = alts.flatMap { a -> densities.map { a.replace("<density>", it) } }
            if ("<category>" in n) alts = alts.flatMap { a -> categories.map { a.replace("<category>", it) } }
            if ("<variant>" in n && "<n>" !in n) alts = alts.flatMap { a -> listOf("day", "glow").map { a.replace("<variant>", it) } }
            for (a in alts) {
                when {
                    a == "egg_<n>_end_<variant>" -> (1..18).forEach { e -> out += Need("egg_${e}_end_*", Regex("egg_${e}_end_(day|glow|dusk|golden)"), where) }
                    strip -> out += Need("${a}_<t>", Regex(Regex.escape(a) + (if (variantSuffix.containsMatchIn(a)) "" else "(_(day|glow))?") + "_\\d+"), where)
                    a.endsWith("_*") -> out += Need(a, Regex(Regex.escape(a.dropLast(2)) + "_.+"), where)
                    asWritten || dayOnly || variantSuffix.containsMatchIn(a) || dayOnlyEnding.containsMatchIn(a) -> out += Need(a, Regex(Regex.escape(a)), where)
                    else -> listOf("day", "glow").forEach { v -> out += Need("${a}_$v", Regex(Regex.escape("${a}_$v")), where) }
                }
            }
        }
        return out
    }

    /** Every golden Appendix C asks for, row by row. */
    val appendixC: List<Need> by lazy {
        val section = doc.substring(doc.indexOf("## Appendix C"))
        section.lines().filter { it.startsWith("| ") && !Regex("""^\| (Item|Screen|Area) \|""").containsMatchIn(it) }.flatMap { line ->
            val cells = line.trim().trim('|').split('|').map { it.trim() }
            val dayOnly = "(Day only" in cells.getOrElse(1) { "" }
            val names = Regex("`([^`]+)`").findAll(cells.getOrElse(1) { "" }).map { it.groupValues[1] }.toList()
            val strips = Regex("`([^`]+)`").findAll(cells.getOrElse(2) { "" }).map { it.groupValues[1] }.toList()
            names.flatMap { expand(it, strip = false, dayOnly = dayOnly, where = "Appendix C ${cells[0]}") } +
                strips.flatMap { expand(it, strip = true, dayOnly = false, where = "Appendix C ${cells[0]} (strip)") }
        }
    }

    /** The goldens and strips the §20 items name in their tests (retired items aside). */
    val section20: List<Pair<String, String>> by lazy {
        val section = doc.substring(doc.indexOf("## 20. Acceptance checklist"), doc.indexOf("## 21. Work packages"))
        section.lines().filter { Regex("""^\d+\. \*\*\[""").containsMatchIn(it) && "[Retired]" !in it }.flatMap { line ->
            val item = line.substringBefore('.')
            val test = line.substringAfter("*Test:*", "")
            Regex("""\b(goldens?|strips?)\s+((?:`[^`]+`(?:\s*\([^)]*\))?(?:,\s*|\s+and\s+)?)+)""").findAll(test).flatMap { m ->
                Regex("`([^`]+)`").findAll(m.groupValues[2]).map { it.groupValues[1] }.filter { Regex("""^[a-z0-9_{},<>*]+$""").matches(it) }
                    .map { name -> item to name }
            }.toList()
        }
    }

    /** The manifest as Appendix C's rules write it. */
    private fun generatedManifest(): String = buildString {
        appendLine("# The goldens docs/DESIGN_STICKER.md Appendix C asks for, expanded by its rules (GoldenCoverageTest).")
        appendLine("# Regenerate: GOLDEN_MANIFEST_WRITE=1 ./gradlew :app:testDebugUnitTest --tests '*GoldenCoverageTest'")
        appendixC.map { it.label }.distinct().sorted().forEach { appendLine(it) }
    }

    @Test
    fun manifestIsAppendixC() {
        assertTrue("Appendix C is read (${appendixC.size} goldens)", appendixC.size >= 500)
        val generated = generatedManifest()
        if (System.getenv("GOLDEN_MANIFEST_WRITE") == "1") manifest.writeText(generated)
        assertEquals("manifest.txt is Appendix C expanded (regenerate it as its header says)", generated, manifest.readText())
    }

    @Test
    fun everyNamedGoldenExists() {
        assertTrue("the goldens folder (${shots.size})", shots.size >= 500)
        val missing = appendixC.filter { need -> shots.none { need.pattern.matches(it) } }.map { "${it.where}: ${it.label}" }.distinct()
        assertTrue("goldens Appendix C asks for that do not exist:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun everyGoldenIsNamed() {
        val unnamed = shots.filter { s -> appendixC.none { it.pattern.matches(s) } }
        assertTrue("goldens no row of Appendix C names (add the state to the appendix, or retire the golden):\n" + unnamed.joinToString("\n"), unnamed.isEmpty())
    }

    /** Every golden a §20 item names exists, in some variant (the variants each needs are Appendix C's). */
    @Test
    fun everyGoldenOfSection20Exists() {
        assertTrue("§20's goldens are read (${section20.size})", section20.size >= 150)
        val missing = section20.filter { (_, raw) ->
            val needs = expand(raw, strip = false, dayOnly = true, where = "")
            needs.any { need ->
                val base = need.pattern.pattern
                val anyVariant = Regex("($base)((_(${variants.joinToString("|")}))?(_\\d+)?)")
                shots.none { anyVariant.matches(it) }
            }
        }.map { (item, name) -> "item $item: $name" }
        assertTrue("goldens §20 names that do not exist:\n" + missing.joinToString("\n"), missing.isEmpty())
    }
}
