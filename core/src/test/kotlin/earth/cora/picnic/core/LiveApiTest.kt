package earth.cora.picnic.core

import earth.cora.picnic.core.assistant.AudioClip
import earth.cora.picnic.core.assistant.IconDrawRequest
import earth.cora.picnic.core.assistant.IngestContext
import earth.cora.picnic.core.assistant.IngestInput
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.assistant.OpenAiGroceryAssistant
import earth.cora.picnic.core.assistant.ResponsesIconDecider
import earth.cora.picnic.core.assistant.resolve
import earth.cora.picnic.core.llm.ConfigSource
import earth.cora.picnic.core.llm.LlmConfig
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.llm.LlmLog
import earth.cora.picnic.core.llm.OpenAiClient
import earth.cora.picnic.core.llm.TokenSource
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.Correction
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.normalizeName
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Real OpenAI calls with the production prompts and ModelDefaults. Run only with
 * `OPENAI_API_KEY=… ./gradlew -p core test -Plive=true` (excluded otherwise; never in CI).
 * Optional: PICNIC_BASE_URL (another OpenAI-compatible endpoint; default api.openai.com),
 * PICNIC_STYLE_REF (PNG for the drawing test; defaults to the app's bundled
 * assets/icons/_style_reference.png when present). Cost per full run: about US$0.10.
 */
@Tag("live")
class LiveApiTest {
    private val key: String? = System.getenv("OPENAI_API_KEY")
    private val catalog = TestSupport.catalog
    private val aisles = Aisles.parse(Aisles.DEFAULT_TEXT)
    private val ids = aisles.map { it.id }.toSet()

    private val client by lazy {
        OpenAiClient(
            config = ConfigSource { LlmConfig(baseUrl = System.getenv("PICNIC_BASE_URL")?.takeIf { it.isNotBlank() } ?: LlmConfig.DEFAULT_BASE_URL, safetyIdentifier = "picnic-live-test") },
            token = TokenSource { key },
            log = LlmLog { println("  [http] $it") },
        )
    }
    private val assistant by lazy { OpenAiGroceryAssistant(client, catalog) }

    @BeforeEach
    fun requireKey() = assumeTrue(!key.isNullOrBlank(), "OPENAI_API_KEY not set")

    private fun context(existing: List<String> = emptyList(), corrections: List<Correction> = emptyList()) =
        IngestContext(aisles = aisles, corrections = corrections, existingNames = existing)

    private fun IngestResult.print(title: String): IngestResult = also {
        println("== $title: ${meta.model} ${meta.latencyMs} ms, ${meta.webSearches} search(es), ${items.size} items")
        println("   message: $message")
        items.forEach { println("   - ${it.name} | ${it.quantity ?: "-"} | ${it.note ?: "-"} | ${it.aisleId ?: "unsorted"} | ${it.icon} ${it.iconFit.name.lowercase()}") }
        if (skippedDuplicates.isNotEmpty()) println("   skipped: $skippedDuplicates")
        if (sources.isNotEmpty()) println("   sources: ${sources.map { it.url }}")
    }

    private fun IngestResult.keys() = items.map { normalizeName(it.name) }

    private fun IngestResult.assertWellFormed() {
        assertTrue(items.all { it.aisleId == null || it.aisleId in ids })
        assertTrue(items.all { it.icon in catalog })
        assertTrue(items.all { it.iconFit != IconFit.PENDING })
        assertTrue(message.isNotBlank())
        assertFalse(message.contains("](http"), "markdown links are stripped")
    }

    @Test
    fun testConnection() = runBlocking {
        val check = assistant.testConnection()
        println("== testConnection: ${check.model} in ${check.meta.latencyMs} ms")
        assertTrue(check.model.startsWith("gpt-5.6-terra"))
    }

    @Test
    fun recipeIngestSkipsWhatIsOnTheList() = runBlocking {
        val result = assistant.ingest(IngestInput.Text("ingredients for pancakes from scratch"), context(existing = listOf("Butter", "Paper towels"))).print("pancakes")
        result.assertWellFormed()
        val keys = result.keys()
        assertTrue(keys.any { "flour" in it }, "flour")
        assertTrue(keys.any { it.startsWith("egg") }, "eggs")
        assertTrue(keys.any { "milk" in it }, "milk")
        assertFalse(keys.any { it == "butter" || it == "unsalted butter" }, "butter is already on the list")
        assertFalse(keys.any { "paper towel" in it }, "never adds unrelated list items")
        assertEquals("aisle-2-baking-spices", result.items.first { "flour" in normalizeName(it.name) }.aisleId)
    }

    @Test
    fun plainListWithTyposShorthandAndACorrection() = runBlocking {
        val result = assistant.ingest(
            IngestInput.Text("milk, 2 lemons, eggs, brocoli, OJ, nooch"),
            context(corrections = listOf(Correction("nutritional yeast", "bulk-natural-foods", 1))),
        ).print("plain list")
        result.assertWellFormed()
        assertEquals(6, result.items.size, "one item per thing mentioned, nothing added")
        val byKey = result.items.associateBy { normalizeName(it.name) }
        assertNotNull(byKey["broccoli"], "typo fixed")
        assertNotNull(byKey["orange juice"], "OJ expanded")
        assertTrue(byKey.getValue("lemon").quantity!!.contains("2"))
        assertEquals("bulk-natural-foods", byKey.getValue("nutritional yeast").aisleId, "learned correction")
    }

    @Test
    fun handwrittenPhoto() = runBlocking {
        val photo = TestSupport.resourceBytes("live/handwritten_list.jpg")
        val result = assistant.ingest(IngestInput.Image(photo), context(existing = listOf("Bananas"))).print("photo")
        result.assertWellFormed()
        val keys = result.keys()
        assertTrue(result.items.size in 10..14, "11 readable lines (+ the margin note)")
        assertFalse(keys.any { "spinach" in it }, "crossed-out line skipped")
        assertFalse(keys.any { "banana" in it }, "already on the list")
        assertTrue(keys.any { "candle" in it }, "margin note kept")
        assertTrue(keys.any { "batter" in it }, "AA batteries")
        val candles = result.items.first { "candle" in normalizeName(it.name) }
        assertEquals("candle" to IconFit.EXACT, candles.icon to candles.iconFit, "curated alias: no drawing needed")
    }

    @Test
    fun voiceNoteTranscribedThenIngested() = runBlocking {
        val clip = AudioClip(TestSupport.resourceBytes("live/grocery_voice.m4a"))
        val transcript = assistant.transcribe(clip, keywords = listOf("Oatly", "za'atar", "PC Blue Menu", "Kraft Dinner"))
        println("== transcript: ${transcript.text}")
        assertTrue(transcript.text.contains("lemons", ignoreCase = true))
        assertTrue(transcript.text.contains("paper towels", ignoreCase = true))
        val result = assistant.ingest(IngestInput.Transcript(transcript.text), context()).print("voice")
        result.assertWellFormed()
        val keys = result.keys()
        assertFalse(keys.any { "paper towel" in it }, "the speaker scratched the paper towels")
        assertTrue(keys.any { it == "lemon" })
        assertEquals("milk_bag", result.items.first { "milk" in normalizeName(it.name) && "oat" !in normalizeName(it.name) }.icon)
    }

    @Test
    fun silenceIsAnEmptyTranscriptNotAnError() = runBlocking {
        val transcript = assistant.transcribe(AudioClip(TestSupport.resourceBytes("live/silence_2s.m4a")))
        println("== silence: '${transcript.text}'")
        assertTrue(transcript.text.length < 15, "nothing (or a stray word) heard")
    }

    @Test
    fun recategorizeAndBaskets() = runBlocking {
        val items = listOf("Sriracha", "Parmigiano", "Diapers", "Chocolate chips", "Hummus", "Frozen edamame", "Cat litter")
            .mapIndexed { i, n -> NamedItem("id$i", n) }
        val placed = assistant.recategorize(items, aisles, emptyList())
        println("== recategorize: ${items.map { it.name to placed[it.id] }}")
        assertEquals(items.size, placed.size)
        assertEquals("aisle-7-health-beauty-baby", placed["id2"])
        assertEquals("aisle-2-baking-spices", placed["id3"])
        assertEquals("frozen", placed["id5"])

        val baskets = assistant.assignBaskets(aisles)
        println("== baskets: $baskets")
        assertEquals(aisles.size, baskets.size)
        assertEquals(BasketKind.BREAD_BASKET, baskets["bakery"])
        assertTrue(baskets["frozen"] in setOf(BasketKind.ICE_BUCKET, BasketKind.COOLER))
    }

    @Test
    fun iconDeciderSaysNoneForProductsWithoutAnIcon() = runBlocking {
        val decider = ResponsesIconDecider(client, catalog)
        val names = listOf("Gochujang", "Spatula", "Cheez-Its", "Miso paste")
        val decisions = decider.decide(names.map { NamedItem(normalizeName(it), it) })
        println("== decider: ${names.map { it to decisions[normalizeName(it)] }}")
        assertNull(decisions.getValue("gochujang").icon)
        assertNull(decisions.getValue("spatula").icon)
        assertNull(decisions.getValue("miso paste").icon)
        assertEquals("crackers", decisions.getValue("cheez its").icon)
        assertEquals(IconFit.APPROXIMATE, decisions.getValue("gochujang").resolve(catalog).fit)
    }

    @Test
    fun drawIcon() = runBlocking {
        val style = (System.getenv("PICNIC_STYLE_REF")?.let(::File) ?: File("../app/src/main/assets/icons/_style_reference.png"))
        assumeTrue(style.isFile, "no style reference (set PICNIC_STYLE_REF)")
        val icon = assistant.drawIcon(IconDrawRequest("Gochujang", hint = "a red plastic tub of Korean chili paste", styleReference = style.readBytes(), styleReferenceFileName = style.name))
        println("== drawIcon: ${icon.bytes.size} bytes in ${icon.meta.latencyMs} ms")
        assertEquals("RIFF", String(icon.bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WEBP", String(icon.bytes, 8, 4, Charsets.US_ASCII))
    }

    @Test
    fun badKeyIsUnauthorized() = runBlocking {
        val bad = OpenAiGroceryAssistant(OpenAiClient(ConfigSource { LlmConfig() }, TokenSource { "sk-invalid-000" }, log = LlmLog.None), catalog)
        val e = assertFailsWith<LlmError.Unauthorized> { bad.testConnection() }
        assertFalse(e.message!!.contains("sk-inval"))
    }
}
