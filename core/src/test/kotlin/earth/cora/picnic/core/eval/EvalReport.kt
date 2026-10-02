package earth.cora.picnic.core.eval

import kotlinx.serialization.Serializable
import java.util.Locale

/** Micro-averaged scores over all case runs (a case repeated N times counts N times). */
@Serializable
data class EvalSummary(
    val runs: Int,
    val clean: Int,
    val errors: Int,
    /** Ingest (text, photo, voice): required items found / required items. */
    val recall: Ratio,
    /** Ingest: produced items that were expected (required or allowed) / produced items. */
    val precision: Ratio,
    val aisle: Ratio,
    val icon: Ratio,
    /** Ingest items with icon fit EXACT / produced items (the rest are live-drawing candidates). */
    val exact: Ratio,
    val checks: Ratio,
    val recategorize: Ratio,
    val baskets: Ratio,
    val decider: Ratio,
    val perKind: Map<String, KindSummary>,
    val ingestLatencyMs: Percentiles,
    val transcribeLatencyMs: Percentiles,
    val costUsd: Double,
    val ingestCostUsd: Percentiles,
    val webSearches: Int,
) {
    companion object {
        fun of(results: List<CaseResult>): EvalSummary {
            val ingest = results.filter { it.kind.isIngest }
            fun ratio(list: List<CaseResult>, ok: (CaseScore) -> Int, total: (CaseScore) -> Int) =
                Ratio(list.sumOf { ok(it.score) }, list.sumOf { total(it.score) })
            val perKind = ingest.groupBy { it.kind.name.lowercase() }.mapValues { (_, list) ->
                KindSummary(
                    recall = ratio(list, { it.requiredMatched }, { it.requiredTotal }),
                    precision = ratio(list, { it.producedMatched }, { it.produced }),
                    aisle = ratio(list, { it.aisleOk }, { it.aisleTotal }),
                    icon = ratio(list, { it.iconOk }, { it.iconTotal }),
                    latencyMs = Percentiles.of(list.map { r -> r.calls.filter { it.path == "responses" }.sumOf { it.latencyMs } }),
                )
            }
            val edits = results.filter { it.kind == CaseKind.AISLE_EDIT }
            val icons = results.filter { it.kind == CaseKind.ICONS }
            return EvalSummary(
                runs = results.size,
                clean = results.count { it.error == null && it.score.clean },
                errors = results.count { it.error != null },
                recall = ratio(ingest, { it.requiredMatched }, { it.requiredTotal }),
                precision = ratio(ingest, { it.producedMatched }, { it.produced }),
                aisle = ratio(ingest, { it.aisleOk }, { it.aisleTotal }),
                icon = ratio(ingest, { it.iconOk }, { it.iconTotal }),
                exact = ratio(ingest, { it.exact }, { it.produced }),
                checks = Ratio(results.sumOf { r -> r.score.checks.count { it.ok } }, results.sumOf { it.score.checks.size }),
                recategorize = ratio(edits, { it.aisleOk }, { it.aisleTotal }),
                baskets = ratio(edits, { it.basketOk }, { it.basketTotal }),
                decider = ratio(icons, { it.iconOk }, { it.iconTotal }),
                perKind = perKind,
                ingestLatencyMs = Percentiles.of(ingest.map { r -> r.calls.filter { it.path == "responses" }.sumOf { it.latencyMs } }),
                transcribeLatencyMs = Percentiles.of(results.flatMap { r -> r.calls.filter { it.path == "audio/transcriptions" }.map { it.latencyMs } }),
                costUsd = results.sumOf { it.costUsd },
                ingestCostUsd = Percentiles.ofDouble(ingest.map { r -> r.calls.filter { it.path == "responses" }.sumOf { it.costUsd } }),
                webSearches = results.sumOf { it.webSearches },
            )
        }
    }
}

@Serializable
data class KindSummary(val recall: Ratio, val precision: Ratio, val aisle: Ratio, val icon: Ratio, val latencyMs: Percentiles)

@Serializable
data class Ratio(val ok: Int, val total: Int) {
    val value: Double? get() = if (total == 0) null else ok.toDouble() / total
    override fun toString(): String = if (total == 0) "n/a" else String.format(Locale.ROOT, "%.1f%% (%d/%d)", 100.0 * ok / total, ok, total)
    fun pct(): String = if (total == 0) "n/a" else String.format(Locale.ROOT, "%.0f%%", 100.0 * ok / total)
}

@Serializable
data class Percentiles(val n: Int, val median: Double, val p90: Double, val max: Double, val mean: Double) {
    companion object {
        fun of(values: List<Long>): Percentiles = ofDouble(values.map { it.toDouble() })

        fun ofDouble(values: List<Double>): Percentiles {
            if (values.isEmpty()) return Percentiles(0, 0.0, 0.0, 0.0, 0.0)
            val sorted = values.sorted()
            fun pick(q: Double) = sorted[((sorted.size - 1) * q).let { kotlin.math.ceil(it).toInt() }]
            return Percentiles(sorted.size, pick(0.5), pick(0.9), sorted.last(), sorted.average())
        }
    }
}

object EvalReportFormat {
    private fun secs(ms: Number) = String.format(Locale.ROOT, "%.1f s", ms.toDouble() / 1000)
    private fun usd(v: Double) = String.format(Locale.ROOT, "$%.4f", v)
    private fun frac(ok: Int, total: Int) = if (total == 0) "-" else "$ok/$total"

    /** One console line per case run, plus indented findings. */
    fun line(r: CaseResult): String = buildString {
        val s = r.score
        val status = when {
            r.error != null -> "ERROR"
            s.clean -> "clean"
            else -> "  -  "
        }
        append("[$status] ${r.id}#${r.rep}  ")
        if (r.kind.isIngest) {
            append("req ${frac(s.requiredMatched, s.requiredTotal)}  prec ${frac(s.producedMatched, s.produced)}  ")
        }
        if (s.aisleTotal > 0) append("aisle ${frac(s.aisleOk, s.aisleTotal)}  ")
        if (s.iconTotal > 0) append("icon ${frac(s.iconOk, s.iconTotal)}  ")
        if (s.basketTotal > 0) append("basket ${frac(s.basketOk, s.basketTotal)}  ")
        if (s.checks.isNotEmpty()) append("checks ${frac(s.checks.count { it.ok }, s.checks.size)}  ")
        append("${secs(r.wallMs)}  ${usd(r.costUsd)}")
        if (r.webSearches > 0) append("  ${r.webSearches} search(es)")
        r.error?.let { append("\n      error: $it") }
        findings(r).forEach { append("\n      $it") }
    }

    private fun findings(r: CaseResult): List<String> = buildList {
        val s = r.score
        if (s.misses.isNotEmpty()) add("missing: ${s.misses.joinToString()}")
        if (s.extras.isNotEmpty()) add("unexpected: ${s.extras.joinToString()}")
        if (s.wrong.isNotEmpty()) add("wrong: ${s.wrong.joinToString("; ")}")
        s.failedChecks.forEach { add("failed: ${it.what}${if (it.detail.isNotEmpty()) " — ${it.detail}" else ""}") }
    }

    fun summaryText(report: EvalReport): String = buildString {
        val s = report.summary
        appendLine("== ${report.label}: smart ${report.smart}, fast ${report.fast}, ${s.runs} run(s), clean ${s.clean}/${s.runs}, errors ${s.errors}")
        appendLine("   ingest  recall ${s.recall}  precision ${s.precision}  aisle ${s.aisle}  icon ${s.icon}  exact-fit ${s.exact}")
        s.perKind.forEach { (kind, k) ->
            appendLine("     $kind: recall ${k.recall.pct()} precision ${k.precision.pct()} aisle ${k.aisle.pct()} icon ${k.icon.pct()} latency median ${secs(k.latencyMs.median)} p90 ${secs(k.latencyMs.p90)}")
        }
        appendLine("   checks ${s.checks}   recategorize ${s.recategorize}   baskets ${s.baskets}   icon decider ${s.decider}")
        appendLine(
            "   ingest latency median ${secs(s.ingestLatencyMs.median)} p90 ${secs(s.ingestLatencyMs.p90)} max ${secs(s.ingestLatencyMs.max)}; " +
                "transcribe median ${secs(s.transcribeLatencyMs.median)}",
        )
        append("   cost total ${usd(s.costUsd)}; per ingest median ${usd(s.ingestCostUsd.median)} mean ${usd(s.ingestCostUsd.mean)}; web searches ${s.webSearches}")
    }

    fun markdown(report: EvalReport, file: EvalFile): String = buildString {
        val s = report.summary
        appendLine("# Eval ${report.label}")
        appendLine()
        appendLine("Smart `${report.smart}`, fast `${report.fast}`, transcription `${report.transcribe}`, repeat ${report.repeat}, started ${report.startedAt}.")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|---|---|")
        appendLine("| Clean case runs | ${s.clean}/${s.runs} (errors ${s.errors}) |")
        appendLine("| Item recall (required) | ${s.recall} |")
        appendLine("| Item precision | ${s.precision} |")
        appendLine("| Aisle accuracy (ingest) | ${s.aisle} |")
        appendLine("| Icon accuracy (ingest) | ${s.icon} |")
        appendLine("| Exact icon fit (ingest) | ${s.exact} |")
        appendLine("| Checks (qty, details, forbidden, search, message, transcript) | ${s.checks} |")
        appendLine("| Recategorize after aisle edit | ${s.recategorize} |")
        appendLine("| Baskets | ${s.baskets} |")
        appendLine("| Icon cascade (local + decider) | ${s.decider} |")
        appendLine("| Ingest latency median / p90 / max | ${secs(s.ingestLatencyMs.median)} / ${secs(s.ingestLatencyMs.p90)} / ${secs(s.ingestLatencyMs.max)} |")
        appendLine("| Transcription latency median | ${secs(s.transcribeLatencyMs.median)} |")
        appendLine("| Cost total / per ingest median / mean | ${usd(s.costUsd)} / ${usd(s.ingestCostUsd.median)} / ${usd(s.ingestCostUsd.mean)} |")
        appendLine("| Web searches | ${s.webSearches} |")
        appendLine()
        appendLine("## Per case")
        appendLine()
        appendLine("| Case | Kind | Req | Prec | Aisle | Icon | Checks | Time | Cost | Clean |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|")
        report.results.forEach { r ->
            val c = r.score
            appendLine(
                "| ${r.id}#${r.rep} | ${r.kind.name.lowercase()} | ${frac(c.requiredMatched, c.requiredTotal)} | ${frac(c.producedMatched, c.produced)} | " +
                    "${frac(c.aisleOk, c.aisleTotal)} | ${frac(c.iconOk, c.iconTotal)} | ${frac(c.checks.count { it.ok }, c.checks.size)} | " +
                    "${secs(r.wallMs)} | ${usd(r.costUsd)} | ${if (r.error == null && c.clean) "yes" else "no"} |",
            )
        }
        appendLine()
        appendLine("## Findings")
        report.results.forEach { r ->
            val case = file.cases.firstOrNull { it.id == r.id }
            appendLine()
            appendLine("### ${r.id}#${r.rep}")
            case?.about?.takeIf { it.isNotEmpty() }?.let { appendLine("_${it}_") }
            appendLine()
            r.error?.let { appendLine("- error: $it") }
            r.transcript?.let { appendLine("- transcript: \"$it\"") }
            r.message?.let { appendLine("- message: \"$it\"") }
            findings(r).forEach { appendLine("- $it") }
            if (r.skipped.isNotEmpty()) appendLine("- skipped as duplicates: ${r.skipped.joinToString()}")
            if (r.sources.isNotEmpty()) appendLine("- sources: ${r.sources.joinToString()}")
            r.notes.forEach { appendLine("- $it") }
            if (r.kind.isIngest && r.produced.isNotEmpty()) {
                val store = file.store(case?.store ?: "ontario14")
                appendLine("- items:")
                r.produced.forEach {
                    appendLine("  - ${it.name} | ${it.quantity ?: "-"} | ${it.note ?: "-"} | ${store.aliasOf(it.aisleId)} | ${it.icon} ${it.fit.name.lowercase()}")
                }
            }
        }
    }
}
