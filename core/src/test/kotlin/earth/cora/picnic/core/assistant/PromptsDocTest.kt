package earth.cora.picnic.core.assistant

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * docs/PROMPTS.md quotes every prompt file verbatim after a `<!-- prompt:<file> -->` marker.
 * This keeps the documentation honest when a prompt changes. Skipped when the repo docs are not
 * next to core (core built on its own elsewhere).
 */
class PromptsDocTest {
    private val promptFiles = listOf(
        "ingest_rules.txt",
        "recategorize_rules.txt",
        "baskets_rules.txt",
        "icon_decider_rules.txt",
        "icon_prompt.txt",
    )

    @Test
    fun promptsMdQuotesEveryPromptFileVerbatim() {
        val doc = File("../docs/PROMPTS.md")
        assumeTrue(doc.isFile, "docs/PROMPTS.md not found next to core")
        val text = doc.readText()
        for (name in promptFiles) {
            val marker = "<!-- prompt:$name -->"
            val start = text.indexOf(marker)
            assertNotNull(start.takeIf { it >= 0 }, "PROMPTS.md lacks $marker")
            val fenceStart = text.indexOf("```text\n", start) + "```text\n".length
            val fenceEnd = text.indexOf("\n```", fenceStart)
            assertEquals(Prompts.resource(name), text.substring(fenceStart, fenceEnd).trim(), "PROMPTS.md is out of date for $name")
        }
    }
}
