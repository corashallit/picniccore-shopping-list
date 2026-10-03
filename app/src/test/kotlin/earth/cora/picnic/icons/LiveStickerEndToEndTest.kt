package earth.cora.picnic.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.DrawOutcome
import earth.cora.picnic.core.assistant.IconDrawRequest
import earth.cora.picnic.core.assistant.LiveIconDrawer
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.assistant.OpenAiGroceryAssistant
import earth.cora.picnic.core.assistant.ResponsesIconDecider
import earth.cora.picnic.core.assistant.StickerPalette
import earth.cora.picnic.core.assistant.resolve
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.ConfigSource
import earth.cora.picnic.core.llm.LlmConfig
import earth.cora.picnic.core.llm.OpenAiClient
import earth.cora.picnic.core.llm.TokenSource
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.ui.theme.gen.StickerManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Live stickers v4 end to end with the real API (docs/DESIGN_STICKER.md §18.3 step 5): the IconDecider's "none" with
 * its look and families, the neighbour sheet from the library, one gpt-image-2.5-flare edit at quality low, the
 * on-device post-processing. About US$0.05 for three items. Off unless `PICNIC_LIVE_STICKERS=1` and `OPENAI_API_KEY`
 * are set; `PICNIC_LIVE_OUT=<dir>` names where the results go (raw answer, brief, sticker, cut, neighbour renders).
 * The committed results under `icons/live/` are replayed offline by [LiveStickerFixturesTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveStickerEndToEndTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val catalog = IconCatalog.default

    @Test
    fun threeUnusualItemsBecomeStickers() {
        // The key from the environment, or from an env file (`PICNIC_OPENAI_ENV`, a line `[export ]OPENAI_API_KEY=…`).
        val key = System.getenv("OPENAI_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getenv("PICNIC_OPENAI_ENV")?.let(::File)?.takeIf { it.isFile }?.readLines()
                ?.firstNotNullOfOrNull { Regex("""^\s*(?:export\s+)?OPENAI_API_KEY=["']?([^"'\s]+)""").find(it)?.groupValues?.get(1) }
        assumeTrue("live run off", System.getenv("PICNIC_LIVE_STICKERS") == "1" && !key.isNullOrBlank())
        val out = File(requireNotNull(System.getenv("PICNIC_LIVE_OUT")) { "set PICNIC_LIVE_OUT" }).apply { mkdirs() }
        val names = (System.getenv("PICNIC_LIVE_ITEMS") ?: "Dragon fruit,Gochujang,Mochi").split(',').map { it.trim() }

        val client = OpenAiClient(ConfigSource { LlmConfig() }, TokenSource { key })
        val decider = ResponsesIconDecider(client, catalog)
        val assistant = OpenAiGroceryAssistant(client, catalog)
        val drawer = LiveIconDrawer(assistant, decider)
        val composer = SheetComposer(context.resources, catalog, BundledIcons(context)::mask)
        val processor = BitmapLiveStickerProcessor()

        runBlocking(Dispatchers.IO) {
            // Cascade step 1 would have caught a catalog name; these must reach the decider.
            names.forEach { assertNull("$it matches the catalog locally", catalog.match(it)) }
            // Step 2: one batched decider call, as the app's batcher makes it.
            val items = names.map { NamedItem(normalizeName(it), it) }
            val decisions = decider.decide(items)
            names.map { name ->
                async {
                    val id = normalizeName(name)
                    val slug = id.replace(' ', '_')
                    val decision = decisions.getValue(id)
                    val resolved = decision.resolve(catalog)
                    assertEquals("$name should have no catalog sticker", IconFit.APPROXIMATE, resolved.fit)
                    // Step 3, as AppIconRepository.draw does it.
                    val families = StickerPalette.normalize(decision.families.ifEmpty { StickerManifest.entry(resolved.icon)?.families.orEmpty() })
                    val subject = decision.look ?: LiveIconDrawer.fallbackSubject(name)
                    val sheet = composer.compose(resolved.icon, decision.category, families, "$name $subject")!!
                    val started = System.nanoTime()
                    val outcome = drawer.draw(IconDrawRequest(name, sheet.png, sheet.mask, subject, families))
                    val drawn = (outcome as? DrawOutcome.Drawn)?.icon ?: error("$name: $outcome")
                    val drawMs = (System.nanoTime() - started) / 1_000_000
                    val sticker = processor.process(drawn.bytes, families, sheet.targetAreaU2)
                    val stats = sticker.stats!!

                    val dir = File(out, slug).apply { mkdirs() }
                    File(dir, "raw.${drawn.mimeType.substringAfter('/')}").writeBytes(drawn.bytes)
                    File(dir, "sticker.webp").writeBytes(sticker.webp)
                    File(dir, "sticker.cut").writeText(sticker.cut)
                    File(dir, "sheet.png").writeBytes(sheet.png)
                    sheet.neighbours.forEachIndexed { i, n ->
                        val vector = StickerRenderer.load(context.resources, StickerManifest.drawable(n)!!)
                        File(dir, "neighbour_${i}_$n.png").writeBytes(png(StickerRenderer.render(vector, StickerRenderer.STICKER_GROUPS, 208)))
                    }
                    val brief = buildJsonObject {
                        put("name", name)
                        put("look", JsonPrimitive(decision.look))
                        putJsonArray("families") { families.forEach { add(JsonPrimitive(it)) } }
                        put("category", JsonPrimitive(decision.category))
                        put("approximate_key", resolved.icon)
                        putJsonArray("neighbours") { sheet.neighbours.forEach { add(JsonPrimitive(it)) } }
                        putJsonArray("neighbour_u2") { sheet.neighbourAreasU2.forEach { add(JsonPrimitive(Math.round(it * 10) / 10.0)) } }
                        put("target_u2", Math.round(sheet.targetAreaU2 * 10) / 10.0)
                        put("model", drawn.meta.model)
                        put("draw_ms", drawMs)
                        put("raw_bytes", drawn.bytes.size)
                        put("sticker_bytes", sticker.webp.size)
                        put("post", stats.toString())
                    }
                    File(dir, "brief.json").writeText(brief.toString())
                    println("$name: ${decision.look} | $families | ${sheet.neighbours} | $stats")

                    // The sticker is on the library's grid and palette.
                    assertTrue(stats.artBottomU in 41.7..42.3)
                    assertTrue(maxOf(stats.artRightU - stats.artLeftU, stats.artBottomU - stats.artTopU) <= 39.6)
                    assertTrue(stats.glossLengthU >= IconPostProcessorV4.GLOSS_MIN_U)
                    assertTrue(stats.millis < 400) // cold JIT here; IconPostProcessorV4Test times the warm path
                    val bmp = BitmapFactory.decodeByteArray(sticker.webp, 0, sticker.webp.size)
                    assertEquals(IconPostProcessorV4.OUT_PX, bmp.width)
                }
            }.awaitAll()
        }
    }

    private fun png(b: Bitmap): ByteArray = ByteArrayOutputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
}
