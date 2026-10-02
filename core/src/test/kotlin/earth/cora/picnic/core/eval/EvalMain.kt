package earth.cora.picnic.core.eval

import earth.cora.picnic.core.CoreJson
import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.assistant.AudioClip
import earth.cora.picnic.core.assistant.IngestContext
import earth.cora.picnic.core.assistant.IngestInput
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.assistant.OpenAiGroceryAssistant
import earth.cora.picnic.core.assistant.ResponsesIconDecider
import earth.cora.picnic.core.assistant.ResponsesOutput
import earth.cora.picnic.core.assistant.resolve
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.cli.jpegForUpload
import earth.cora.picnic.core.llm.ApiResult
import earth.cora.picnic.core.llm.ConfigSource
import earth.cora.picnic.core.llm.LlmConfig
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.llm.LlmLog
import earth.cora.picnic.core.llm.ModelDefaults
import earth.cora.picnic.core.llm.ModelSet
import earth.cora.picnic.core.llm.OpenAiClient
import earth.cora.picnic.core.llm.ReasoningEffort
import earth.cora.picnic.core.llm.TokenSource
import earth.cora.picnic.core.model.Correction
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.StateOps
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.model.setAislesText
import earth.cora.picnic.core.store.InMemoryStateStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

/**
 * Live LLM eval (docs/PROMPTS.md §10): runs every case in eval/cases.json against the real API with
 * the production prompts and ModelDefaults (or developer overrides), scores the answers and writes
 * build/eval/<label>.{json,md}.
 *
 *   source <env with OPENAI_API_KEY>
 *   ./gradlew -p core liveEval -Plive=true
 *   ./gradlew -p core liveEval -Plive=true --args="--only=recipe-pancakes,photo-sticky-note --repeat=2"
 *   ./gradlew -p core liveEval -Plive=true --args="--smart=gpt-6.1-sol --smart-effort=low --kind=text,photo,voice"
 *
 * Options: --only=<ids> --tag=<tags> --kind=<kinds> (comma lists) --repeat=N --parallel=N --label=<name>
 * --out=<dir> --verbose; developer-only model overrides --smart= --smart-effort= --fast= --fast-effort=.
 * --rescore=<report.json> re-scores a saved run with the current cases.json and scorer (no API calls).
 * The key is read from the environment and never printed.
 */
fun main(args: Array<String>) {
    val code = try {
        val options = EvalOptions.parse(args)
        if (options.rescore != null) EvalRescore.run(options) else runBlocking { EvalRunner(options).run() }
    } catch (e: IllegalArgumentException) {
        System.err.println("eval: ${e.message}")
        2
    }
    exitProcess(code)
}

data class EvalOptions(
    val only: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val kinds: Set<String> = emptySet(),
    val repeat: Int = 1,
    val parallel: Int = 1,
    val label: String? = null,
    val outDir: File = File("build/eval"),
    val verbose: Boolean = false,
    val smart: String? = null,
    val smartEffort: ReasoningEffort? = null,
    val fast: String? = null,
    val fastEffort: ReasoningEffort? = null,
    val rescore: File? = null,
) {
    companion object {
        fun parse(args: Array<String>): EvalOptions {
            var o = EvalOptions()
            fun list(v: String) = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            fun effort(v: String) = requireNotNull(ReasoningEffort.fromWire(v)) { "unknown effort '$v'" }
            for (arg in args) {
                val (k, v) = if ('=' in arg) arg.substringBefore('=') to arg.substringAfter('=') else arg to ""
                o = when (k) {
                    "--only" -> o.copy(only = list(v))
                    "--tag" -> o.copy(tags = list(v))
                    "--kind" -> o.copy(kinds = list(v))
                    "--repeat" -> o.copy(repeat = v.toInt().coerceIn(1, 10))
                    "--parallel" -> o.copy(parallel = v.toInt().coerceIn(1, 8))
                    "--label" -> o.copy(label = v)
                    "--out" -> o.copy(outDir = File(v))
                    "--verbose" -> o.copy(verbose = true)
                    "--smart" -> o.copy(smart = v)
                    "--smart-effort" -> o.copy(smartEffort = effort(v))
                    "--fast" -> o.copy(fast = v)
                    "--fast-effort" -> o.copy(fastEffort = effort(v))
                    "--rescore" -> o.copy(rescore = File(v))
                    else -> throw IllegalArgumentException("unknown option '$arg'")
                }
            }
            return o
        }
    }
}

/** One HTTP call made while running a case (from OpenAiClient.resultObserver). */
@Serializable
data class CallRecord(
    val path: String,
    val model: String? = null,
    val latencyMs: Long,
    val inputTokens: Int = 0,
    val cachedTokens: Int = 0,
    val cacheWriteTokens: Int = 0,
    val outputTokens: Int = 0,
    val reasoningTokens: Int = 0,
    /** Billed searches (tool_usage.web_search.num_requests). */
    val webSearches: Int = 0,
    /** web_search_call output items: searches and opened pages (open_page is not billed as a search). */
    val toolCalls: Int = 0,
    val audioSeconds: Double = 0.0,
    val costUsd: Double = 0.0,
)

/** Attributes calls to the case whose coroutine made them (works with --parallel). */
class CallMeter : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CallMeter>

    val calls: MutableList<CallRecord> = Collections.synchronizedList(mutableListOf())
}

@Serializable
data class CaseResult(
    val id: String,
    val kind: CaseKind,
    val rep: Int,
    val wallMs: Long,
    val score: CaseScore,
    val error: String? = null,
    val model: String? = null,
    val message: String? = null,
    val transcript: String? = null,
    val produced: List<Produced> = emptyList(),
    val skipped: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    /** AISLE_EDIT: basket key per aisle id after the sync. */
    val baskets: Map<String, String> = emptyMap(),
    val calls: List<CallRecord> = emptyList(),
) {
    val costUsd: Double get() = calls.sumOf { it.costUsd }
    val webSearches: Int get() = calls.sumOf { it.webSearches }
    val toolCalls: Int get() = calls.sumOf { it.toolCalls }
}

@Serializable
data class EvalReport(
    val label: String,
    val startedAt: String,
    val smart: String,
    val fast: String,
    val transcribe: String,
    val repeat: Int,
    val results: List<CaseResult>,
    val summary: EvalSummary,
)

class EvalRunner(private val options: EvalOptions) {
    private val file = EvalFile.load()
    private val catalog = IconCatalog.load()

    private val models: ModelSet = ModelDefaults.models.let { base ->
        base.copy(
            smart = base.smart.copy(id = options.smart ?: base.smart.id, effort = options.smartEffort ?: base.smart.effort),
            fast = base.fast.copy(id = options.fast ?: base.fast.id, effort = options.fastEffort ?: base.fast.effort),
        )
    }

    private val client: OpenAiClient = OpenAiClient(
        config = ConfigSource {
            LlmConfig(baseUrl = System.getenv("PICNIC_BASE_URL")?.takeIf { it.isNotBlank() } ?: LlmConfig.DEFAULT_BASE_URL, safetyIdentifier = "picnic-eval")
        },
        token = TokenSource { System.getenv("OPENAI_API_KEY") },
        log = if (options.verbose) LlmLog { println("    [http] $it") } else LlmLog.None,
    ).also { it.resultObserver = ::observe }

    private val assistant = OpenAiGroceryAssistant(client, catalog, models)
    private val decider = ResponsesIconDecider(client, catalog, models.fast)

    private suspend fun observe(path: String, result: ApiResult) {
        val meter = currentCoroutineContext()[CallMeter] ?: return
        val json = result.json
        meter.calls += if (path == "audio/transcriptions") {
            val seconds = (json["usage"]?.jsonObject?.get("seconds") as? JsonPrimitive)?.doubleOrNull ?: 0.0
            CallRecord(path, models.transcribe.id, result.latencyMs, audioSeconds = seconds, costUsd = EvalPricing.transcription(models.transcribe.id, seconds))
        } else {
            val model = ResponsesOutput.model(json)
            val usage = ResponsesOutput.usage(json)
            val searches = if (path == "responses") ResponsesOutput.webSearches(json) else 0
            val toolCalls = (json["output"] as? JsonArray)?.count { (it as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "web_search_call" } ?: 0
            CallRecord(
                path = path,
                model = model,
                latencyMs = result.latencyMs,
                inputTokens = usage?.inputTokens ?: 0,
                cachedTokens = usage?.cachedTokens ?: 0,
                cacheWriteTokens = usage?.cacheWriteTokens ?: 0,
                outputTokens = usage?.outputTokens ?: 0,
                reasoningTokens = usage?.reasoningTokens ?: 0,
                webSearches = searches,
                toolCalls = toolCalls,
                costUsd = if (path == "responses") EvalPricing.responses(model, usage, searches) else 0.0,
            )
        }
    }

    suspend fun run(): Int {
        require(!System.getenv("OPENAI_API_KEY").isNullOrBlank()) { "OPENAI_API_KEY is not set (source the env file first)" }
        val selected = file.cases.filter { case ->
            (options.only.isEmpty() || case.id in options.only) &&
                (options.tags.isEmpty() || case.tags.any { it in options.tags }) &&
                (options.kinds.isEmpty() || case.kind.name.lowercase().replace('_', '-') in options.kinds)
        }
        require(selected.isNotEmpty()) { "no case matches the filters" }
        val started = LocalDateTime.now()
        val label = options.label
            ?: "${started.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}_${models.smart.id}-${models.smart.effort.wire}_${models.fast.id}-${models.fast.effort.wire}"
        println("Eval '$label': ${selected.size} case(s) x ${options.repeat}, smart=${models.smart.id}/${models.smart.effort.wire}, fast=${models.fast.id}/${models.fast.effort.wire}, parallel=${options.parallel}")

        val jobs = (1..options.repeat).flatMap { rep -> selected.map { it to rep } }
        val semaphore = Semaphore(options.parallel)
        val results = coroutineScope {
            // The first case runs alone so the prompt-cache prefix is written once before parallel calls.
            val first = runCase(jobs.first().first, jobs.first().second).also(::printLine)
            listOf(first) + jobs.drop(1).map { (case, rep) ->
                async { semaphore.withPermit { runCase(case, rep).also(::printLine) } }
            }.awaitAll()
        }

        val summary = EvalSummary.of(results)
        val report = EvalReport(
            label = label,
            startedAt = started.toString(),
            smart = "${models.smart.id}/${models.smart.effort.wire}",
            fast = "${models.fast.id}/${models.fast.effort.wire}",
            transcribe = models.transcribe.id,
            repeat = options.repeat,
            results = results,
            summary = summary,
        )
        options.outDir.mkdirs()
        val json = File(options.outDir, "$label.json")
        json.writeText(CoreJson.json.encodeToString(EvalReport.serializer(), report))
        val md = File(options.outDir, "$label.md")
        md.writeText(EvalReportFormat.markdown(report, file))
        println()
        println(EvalReportFormat.summaryText(report))
        println("Report: ${md.path} (details: ${json.path})")
        return 0
    }

    private fun printLine(result: CaseResult) = println(EvalReportFormat.line(result))

    private suspend fun runCase(case: EvalCase, rep: Int): CaseResult {
        val meter = CallMeter()
        val start = System.nanoTime()
        val partial = try {
            withContext(meter) {
                when (case.kind) {
                    CaseKind.TEXT -> ingestCase(case, IngestInput.Text(requireNotNull(case.input)))
                    CaseKind.PHOTO -> ingestCase(case, IngestInput.Image(jpegForUpload(EvalResources.bytes(requireNotNull(case.file)), case.file)))
                    CaseKind.VOICE -> voiceCase(case)
                    CaseKind.AISLE_EDIT -> aisleEditCase(case)
                    CaseKind.ICONS -> iconsCase(case)
                }
            }
        } catch (e: LlmError) {
            val empty = if (case.kind.isIngest) {
                EvalScoring.scoreIngest(case, file.store(case.store), emptyList(), "", 0)
            } else {
                CaseScore()
            }
            CaseResult(case.id, case.kind, rep, 0, empty.copy(checks = empty.checks + Check("call succeeded", false, e.message.orEmpty())), error = "${e.javaClass.simpleName}: ${e.message}")
        }
        val wall = (System.nanoTime() - start) / 1_000_000
        return partial.copy(rep = rep, wallMs = wall, calls = meter.calls.toList())
    }

    private fun context(case: EvalCase): IngestContext {
        val store = file.store(case.store)
        val corrections = case.corrections.entries.mapIndexed { i, (name, alias) ->
            Correction(normalizeName(name), requireNotNull(store.resolve(alias)) { "correction to unsorted" }, at = i + 1L)
        }.reversed() // newest first, like AppState.ingestContext
        return IngestContext(aisles = store.aisles, corrections = corrections, existingNames = case.existing)
    }

    private suspend fun ingestCase(case: EvalCase, input: IngestInput, transcript: String? = null, notes: List<String> = emptyList()): CaseResult {
        val result: IngestResult = assistant.ingest(input, context(case))
        val produced = result.items.map { Produced(it.name, it.quantity, it.note, it.aisleId, it.icon, it.iconFit) }
        val toolCalls = currentCoroutineContext()[CallMeter]?.calls?.filter { it.path == "responses" }?.sumOf { it.toolCalls } ?: 0
        val score = EvalScoring.scoreIngest(case, file.store(case.store), produced, result.message, toolCalls, transcript)
        return CaseResult(
            id = case.id,
            kind = case.kind,
            rep = 0,
            wallMs = 0,
            score = score,
            model = result.meta.model,
            message = result.message,
            transcript = transcript,
            produced = produced,
            skipped = result.skippedDuplicates,
            sources = result.sources.map { it.url },
            notes = notes,
        )
    }

    private suspend fun voiceCase(case: EvalCase): CaseResult {
        val bytes = EvalResources.bytes(requireNotNull(case.file))
        val keywords = case.existing + case.corrections.keys
        val transcript = assistant.transcribe(AudioClip(bytes, "recording.m4a"), keywords)
        if (transcript.isEmpty) {
            val score = EvalScoring.scoreIngest(case, file.store(case.store), emptyList(), "", 0, transcript = "")
            return CaseResult(case.id, case.kind, 0, 0, score, error = "empty transcript", transcript = "")
        }
        return ingestCase(case, IngestInput.Transcript(transcript.text), transcript = transcript.text)
    }

    private suspend fun aisleEditCase(case: EvalCase): CaseResult {
        val old = file.store(case.store)
        val new = file.store(requireNotNull(case.newStore))
        val listId = "eval"
        val items = case.items.mapIndexed { i, placed ->
            GroceryItem(
                id = "item-$i",
                name = placed.name,
                aisleId = old.resolve(placed.from),
                icon = catalog.match(placed.name)?.key ?: IconCatalog.DEFAULT_ICON,
                iconFit = IconFit.EXACT,
                addedAt = i.toLong(),
                manuallyPlaced = placed.manual,
            )
        }
        val corrections = case.corrections.entries.mapIndexed { i, (name, alias) ->
            Correction(normalizeName(name), requireNotNull(new.resolve(alias)), at = i + 1L)
        }
        val initial = StateOps.initialState(listId, 0).copy(store = StoreProfile(aislesText = old.aislesText), corrections = corrections)
            .addItems(listId, items)
        val store = InMemoryStateStore(initial)
        store.update { it.setAislesText(new.aislesText, catalog.baskets::guess) }
        val report = AisleSync(store, assistant, catalog.baskets).sync()
        val state = store.current()
        val finalAisles = requireNotNull(state.list(listId)).items.associate { it.name to it.aisleId }
        val baskets = state.store.aisleBaskets.mapValues { it.value.key }
        report.error?.let { throw it }
        val score = EvalScoring.scoreAisleEdit(case, old, new, finalAisles, baskets)
        val notes = listOf("recategorized ${report.recategorized}, left unsorted ${report.leftUnsorted}, baskets from model: ${report.basketsFromModel}")
        return CaseResult(
            id = case.id,
            kind = case.kind,
            rep = 0,
            wallMs = 0,
            score = score,
            produced = case.items.map { Produced(it.name, aisleId = finalAisles[it.name], icon = "", fit = IconFit.EXACT) },
            notes = notes,
            baskets = baskets,
        )
    }

    private suspend fun iconsCase(case: EvalCase): CaseResult {
        val resolved = LinkedHashMap<String, Pair<String, IconFit>>()
        val pending = mutableListOf<NamedItem>()
        val notes = mutableListOf<String>()
        for (expect in case.names) {
            val name = requireNotNull(expect.name)
            val local = catalog.match(name)
            if (local != null) {
                resolved[name] = local.key to IconFit.EXACT
                notes += "$name -> ${local.key} (local)"
            } else {
                pending += NamedItem(normalizeName(name), name)
            }
        }
        val decisions = decider.decide(pending)
        for (item in pending) {
            val decision = decisions.getValue(item.id)
            val r = decision.resolve(catalog)
            resolved[item.name] = r.icon to r.fit
            notes += "${item.name} -> ${decision.icon ?: "none"} / ${decision.category ?: "?"} (decider)"
        }
        val score = EvalScoring.scoreIcons(case, resolved)
        return CaseResult(
            id = case.id,
            kind = case.kind,
            rep = 0,
            wallMs = 0,
            score = score,
            produced = resolved.map { (name, r) -> Produced(name, icon = r.first, fit = r.second) },
            notes = notes,
        )
    }
}

/**
 * Re-scores a saved report with the current cases.json and scorer (no API calls): for fixing eval
 * expectations or the matcher without paying for a new run. Writes <label>-rescored.{json,md}.
 */
object EvalRescore {
    fun run(options: EvalOptions): Int {
        val source = requireNotNull(options.rescore)
        val file = EvalFile.load()
        val old = CoreJson.json.decodeFromString(EvalReport.serializer(), source.readText())
        val results = old.results.map { r ->
            val case = file.cases.firstOrNull { it.id == r.id } ?: return@map r
            val score = when (r.kind) {
                CaseKind.TEXT, CaseKind.PHOTO, CaseKind.VOICE -> {
                    // Older reports have no toolCalls: a non-empty source list means the web was used.
                    val searched = if (r.calls.any { it.toolCalls > 0 }) r.toolCalls else if (r.sources.isNotEmpty()) 1 else 0
                    if (r.error != null && r.produced.isEmpty()) r.score else EvalScoring.scoreIngest(case, file.store(case.store), r.produced, r.message.orEmpty(), searched, r.transcript)
                }
                CaseKind.AISLE_EDIT -> EvalScoring.scoreAisleEdit(
                    case, file.store(case.store), file.store(requireNotNull(case.newStore)),
                    r.produced.associate { it.name to it.aisleId }, r.baskets,
                ).let { if (r.baskets.isEmpty()) it.copy(basketTotal = 0, basketOk = 0, wrong = it.wrong.filterNot { w -> w.startsWith("basket ") }) else it }
                CaseKind.ICONS -> EvalScoring.scoreIcons(case, r.produced.associate { it.name to (it.icon to it.fit) })
            }
            r.copy(score = score)
        }
        val report = old.copy(label = old.label + "-rescored", results = results, summary = EvalSummary.of(results))
        val outDir = source.absoluteFile.parentFile
        File(outDir, "${report.label}.json").writeText(CoreJson.json.encodeToString(EvalReport.serializer(), report))
        File(outDir, "${report.label}.md").writeText(EvalReportFormat.markdown(report, file))
        results.forEach { println(EvalReportFormat.line(it)) }
        println()
        println(EvalReportFormat.summaryText(report))
        return 0
    }
}
