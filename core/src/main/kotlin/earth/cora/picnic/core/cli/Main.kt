package earth.cora.picnic.core.cli

import earth.cora.picnic.core.assistant.AudioClip
import earth.cora.picnic.core.assistant.CallMeta
import earth.cora.picnic.core.assistant.IconDrawRequest
import earth.cora.picnic.core.assistant.IngestContext
import earth.cora.picnic.core.assistant.IngestInput
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.assistant.OfflineParser
import earth.cora.picnic.core.assistant.OpenAiGroceryAssistant
import earth.cora.picnic.core.assistant.ResponsesIconDecider
import earth.cora.picnic.core.assistant.resolve
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.ConfigSource
import earth.cora.picnic.core.llm.LlmConfig
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.llm.LlmLog
import earth.cora.picnic.core.llm.ModelDefaults
import earth.cora.picnic.core.llm.ModelSet
import earth.cora.picnic.core.llm.OpenAiClient
import earth.cora.picnic.core.llm.ReasoningEffort
import earth.cora.picnic.core.llm.TokenSource
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.Correction
import earth.cora.picnic.core.model.normalizeName
import kotlinx.coroutines.runBlocking
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.system.exitProcess

/**
 * Developer CLI for prompt work (owned by core; not part of the app). Commands:
 *   ingest "<text>"              text ingest (web search allowed), printed in walking order
 *   photo <file.jpg|png>         handwritten-list photo ingest (downscaled to 1600 px JPEG like the app)
 *   transcribe <file.m4a> [--ingest]   transcription, optionally followed by ingest
 *   recat "<item>"...            recategorize items into the aisles
 *   baskets [aisles.txt]         basket per aisle (model vs keyword heuristic)
 *   icons "<item>"...            icon cascade steps 1-2: local match, then the IconDecider for the rest
 *   match "<item>"...            local icon match only (no network)
 *   parse "<text>"               OfflineParser only (no network)
 *   draw "<item name>" <out.webp> [--style=<png>] [--hint=<text>]   live icon (costs ~US$0.015)
 *   test                         GET /models/{smart model}
 * Options: --aisles=<file>, --existing="Butter,Bananas", --correction="oat milk=dairy-eggs" (repeatable),
 *   --quiet (no HTTP log lines). Developer-only model flags (the app has no equivalent):
 *   --smart=<model> --smart-effort=<effort> --fast=<model> --fast-effort=<effort>.
 * Reads OPENAI_API_KEY (and optional PICNIC_BASE_URL) from the inherited environment; never prints the key.
 * Usage: ./gradlew -p core run --args="ingest 'ingredients for pancakes from scratch'"
 */
fun main(args: Array<String>) {
    val options = args.filter { it.startsWith("--") }
    val positional = args.filterNot { it.startsWith("--") }
    val command = positional.firstOrNull()
    if (command == null || command == "help") {
        printUsage()
        return
    }
    val cli = Cli(options)
    val code = try {
        runBlocking { cli.run(command, positional.drop(1)) }
    } catch (e: LlmError) {
        System.err.println("Error: ${e.message}")
        System.err.println("User would see: ${e.friendlyMessage}")
        1
    } catch (e: IllegalArgumentException) {
        System.err.println("Error: ${e.message}")
        2
    }
    exitProcess(code)
}

private fun printUsage() {
    println(
        """
        Picnic core CLI. Commands: ingest "<text>" | photo <jpg> | transcribe <m4a> [--ingest] | recat "<item>"... |
          baskets [aisles.txt] | icons "<item>"... | match "<item>"... | parse "<text>" | draw "<name>" <out.webp> | test
        Options: --aisles=<file> --existing="A,B" --correction="name=aisle-id" --quiet
                 --smart=<model> --smart-effort=<e> --fast=<model> --fast-effort=<e> (developer only)
        Env: OPENAI_API_KEY (required for network commands), PICNIC_BASE_URL (default ${LlmConfig.DEFAULT_BASE_URL})
        Models: smart=${ModelDefaults.SMART.id}/${ModelDefaults.SMART.effort.wire} fast=${ModelDefaults.FAST.id}/${ModelDefaults.FAST.effort.wire} transcribe=${ModelDefaults.TRANSCRIBE.id} image=${ModelDefaults.IMAGE.id}
        """.trimIndent(),
    )
}

private class Cli(private val options: List<String>) {
    private val catalog = IconCatalog.load()

    private fun option(name: String): String? = options.lastOrNull { it.startsWith("--$name=") }?.substringAfter('=')
    private fun options(name: String): List<String> = options.filter { it.startsWith("--$name=") }.map { it.substringAfter('=') }
    private fun flag(name: String): Boolean = "--$name" in options

    private val models: ModelSet by lazy {
        fun effort(name: String) = option(name)?.let {
            requireNotNull(ReasoningEffort.fromWire(it)) { "unknown effort '$it'" }
        }
        val base = ModelDefaults.models
        base.copy(
            smart = base.smart.copy(id = option("smart") ?: base.smart.id, effort = effort("smart-effort") ?: base.smart.effort),
            fast = base.fast.copy(id = option("fast") ?: base.fast.id, effort = effort("fast-effort") ?: base.fast.effort),
        )
    }

    private val client: OpenAiClient by lazy {
        val baseUrl = System.getenv("PICNIC_BASE_URL")?.takeIf { it.isNotBlank() } ?: LlmConfig.DEFAULT_BASE_URL
        val log = if (flag("quiet")) LlmLog.None else LlmLog { System.err.println("  [http] $it") }
        OpenAiClient(
            config = ConfigSource { LlmConfig(baseUrl = baseUrl, safetyIdentifier = "picnic-cli") },
            token = TokenSource { System.getenv("OPENAI_API_KEY") },
            log = log,
        )
    }
    private val assistant by lazy { OpenAiGroceryAssistant(client, catalog, models) }

    private val aisles: List<Aisle> by lazy {
        val text = option("aisles")?.let { File(it).readText() } ?: Aisles.DEFAULT_TEXT
        Aisles.parse(text)
    }

    private val context: IngestContext by lazy {
        val corrections = options("correction").mapIndexed { i, spec ->
            val (name, aisle) = spec.split('=', limit = 2).also { require(it.size == 2) { "--correction needs name=aisle-id" } }
            Correction(normalizeName(name), aisle.trim(), at = (i + 1).toLong())
        }.reversed() // newest first, like AppState.ingestContext
        val existing = option("existing")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        IngestContext(aisles = aisles, corrections = corrections, existingNames = existing)
    }

    suspend fun run(command: String, args: List<String>): Int {
        when (command) {
            "ingest" -> printIngest(assistant.ingest(IngestInput.Text(args.joinToString(" ").requireText()), context))
            "photo" -> printIngest(assistant.ingest(IngestInput.Image(jpegForUpload(File(args.requireOne("photo")))), context))
            "transcribe" -> transcribe(File(args.requireOne("transcribe")))
            "recat" -> recategorize(args)
            "baskets" -> baskets(args.firstOrNull())
            "icons" -> icons(args)
            "match" -> args.forEach { name -> println("${name.padEnd(28)} -> ${catalog.match(name)?.key ?: "(no local match)"}") }
            "parse" -> OfflineParser.parse(args.joinToString(" ").requireText(), catalog, context).forEach {
                println("${it.name.padEnd(28)} qty=${it.quantity ?: "-"}  icon=${it.icon} (${it.iconFit.name.lowercase()})")
            }
            "draw" -> draw(args)
            "test" -> {
                val check = assistant.testConnection()
                println("OK: ${check.model} reachable in ${check.meta.latencyMs} ms (${check.meta.requestId ?: "no request id"})")
            }
            else -> throw IllegalArgumentException("unknown command '$command' (try: help)")
        }
        return 0
    }

    private suspend fun transcribe(file: File) {
        val clip = AudioClip(file.readBytes(), fileName = "recording.${file.extension.ifEmpty { "m4a" }}")
        val keywords = context.existingNames + context.corrections.map { it.name }
        val transcript = assistant.transcribe(clip, keywords)
        println("Transcript: ${transcript.text.ifEmpty { "(nothing heard)" }}")
        if (flag("ingest") && !transcript.isEmpty) {
            printIngest(assistant.ingest(IngestInput.Transcript(transcript.text), context))
        }
    }

    private suspend fun recategorize(names: List<String>) {
        require(names.isNotEmpty()) { "recat needs item names" }
        val items = names.mapIndexed { i, n -> NamedItem("item${i + 1}", n) }
        val started = System.nanoTime()
        val result = assistant.recategorize(items, aisles, context.corrections)
        val byId = aisles.associateBy { it.id }
        items.forEach { println("${it.name.padEnd(28)} -> ${result[it.id]?.let { id -> byId[id]?.name } ?: "Unsorted"}") }
        println("(${(System.nanoTime() - started) / 1_000_000} ms, model ${models.fast.id})")
    }

    private suspend fun baskets(file: String?) {
        val list = file?.let { Aisles.parse(File(it).readText()) } ?: aisles
        val assigned = assistant.assignBaskets(list)
        list.forEach { aisle ->
            val model = assigned[aisle.id]?.key ?: "(missing)"
            val guess = catalog.baskets.guess(aisle).key
            println("${aisle.name.padEnd(40)} model=${model.padEnd(15)} heuristic=$guess")
        }
    }

    private suspend fun icons(names: List<String>) {
        require(names.isNotEmpty()) { "icons needs item names" }
        val pending = mutableListOf<NamedItem>()
        names.forEach { name ->
            val local = catalog.match(name)
            if (local != null) println("${name.padEnd(28)} -> ${local.key} (step 1: local match, exact)") else pending += NamedItem(normalizeName(name), name)
        }
        if (pending.isEmpty()) return
        val decider = ResponsesIconDecider(client, catalog, models.fast)
        val started = System.nanoTime()
        val decisions = decider.decide(pending)
        pending.forEach { item ->
            val decision = decisions.getValue(item.id)
            val resolved = decision.resolve(catalog)
            val what = decision.icon ?: "none (category ${decision.category ?: "?"}; would draw a bespoke icon)"
            println("${item.name.padEnd(28)} -> $what  => ${resolved.icon} (${resolved.fit.name.lowercase()})")
        }
        println("(step 2 IconDecider: ${(System.nanoTime() - started) / 1_000_000} ms, model ${models.fast.id})")
    }

    private suspend fun draw(args: List<String>) {
        require(args.size >= 2) { "draw needs \"<item name>\" <out.webp>" }
        val style = File(option("style") ?: "../app/src/main/assets/icons/_style_reference.png")
        require(style.isFile) { "style reference not found at ${style.path} (pass --style=<png>)" }
        val icon = assistant.drawIcon(
            IconDrawRequest(itemName = args[0], hint = option("hint"), styleReference = style.readBytes(), styleReferenceFileName = style.name),
        )
        File(args[1]).writeBytes(icon.bytes)
        println("Wrote ${icon.bytes.size} bytes (${icon.mimeType}) to ${args[1]} in ${icon.meta.latencyMs} ms")
    }

    private fun printIngest(result: IngestResult) {
        printMeta(result.meta)
        println("Message: ${result.message}")
        val byAisle = result.items.groupBy { it.aisleId }
        val sections = listOf<Pair<String, String?>>("Unsorted" to null) + aisles.map { it.name to it.id }
        for ((title, id) in sections) {
            val items = byAisle[id] ?: continue
            println()
            println(title)
            items.forEach { item ->
                val fit = item.iconFit.name.lowercase()
                val details = listOfNotNull(item.quantity, item.note).joinToString(" · ")
                val corrected = if (item.aisleFromCorrection) "  [correction]" else ""
                println("  ${"[${item.icon} $fit]".padEnd(34)} ${item.name}${if (details.isNotEmpty()) " — $details" else ""}$corrected")
            }
        }
        if (result.skippedDuplicates.isNotEmpty()) println("\nSkipped (already on the list): ${result.skippedDuplicates.joinToString()}")
        if (result.sources.isNotEmpty()) {
            println("\nSources:")
            result.sources.forEach { println("  ${it.title ?: ""} ${it.url}".trimEnd()) }
        }
    }

    private fun printMeta(meta: CallMeta) {
        val usage = meta.usage?.let { u ->
            " · tokens in ${u.inputTokens} (cached ${u.cachedTokens}, cache write ${u.cacheWriteTokens}) out ${u.outputTokens} (reasoning ${u.reasoningTokens})"
        }.orEmpty()
        println("${meta.model ?: "?"} · ${meta.latencyMs} ms · ${meta.webSearches} web search(es)$usage · ${meta.requestId ?: "no request id"}")
    }

    private fun String.requireText(): String = also { require(it.isNotBlank()) { "missing text" } }
    private fun List<String>.requireOne(command: String): String =
        requireNotNull(firstOrNull()) { "$command needs a file argument" }
}

/** Like the app's PhotoPreparer: JPEG, long edge <= 1600 px, quality ~85. */
private fun jpegForUpload(file: File): ByteArray = jpegForUpload(file.readBytes(), file.path)

/** Like the app's PhotoPreparer: JPEG, long edge <= 1600 px, quality ~85 (also used by the live eval). */
internal fun jpegForUpload(image: ByteArray, label: String = "image"): ByteArray {
    val source = ImageIO.read(image.inputStream()) ?: throw IllegalArgumentException("not an image: $label")
    val scale = minOf(1.0, 1600.0 / maxOf(source.width, source.height))
    val w = (source.width * scale).toInt().coerceAtLeast(1)
    val h = (source.height * scale).toInt().coerceAtLeast(1)
    val rgb = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    rgb.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        drawImage(source, 0, 0, w, h, null)
        dispose()
    }
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val out = ByteArrayOutputStream()
    ImageIO.createImageOutputStream(out).use { ios ->
        writer.output = ios
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = 0.85f
        }
        writer.write(null, IIOImage(rgb, null, null), params)
        writer.dispose()
    }
    return out.toByteArray()
}
