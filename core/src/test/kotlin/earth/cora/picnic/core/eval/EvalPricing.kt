package earth.cora.picnic.core.eval

import earth.cora.picnic.core.assistant.TokenUsage

/**
 * Standard-tier list prices used for eval cost estimates (developers.openai.com pricing, fetched
 * 2026-10-01; research/openai_api.md §8). USD per 1M tokens. Estimates only: the invoice is the truth.
 */
object EvalPricing {
    private class TextPrice(val input: Double, val cached: Double, val output: Double, val explicitCache: Boolean)

    private val TEXT: Map<String, TextPrice> = mapOf(
        "gpt-6-astra" to TextPrice(10.00, 1.00, 50.00, true),
        "gpt-6.1-sol" to TextPrice(2.00, 0.10, 10.00, true),
        "gpt-6-sol" to TextPrice(2.00, 0.20, 10.00, true),
        "gpt-6-luna" to TextPrice(0.10, 0.01, 0.50, true),
        "gpt-5.6-sol" to TextPrice(4.00, 0.40, 20.00, true),
        "gpt-5.6-terra" to TextPrice(2.00, 0.20, 12.00, true),
        "gpt-5.6-luna" to TextPrice(0.20, 0.02, 1.20, true),
        "gpt-5.5" to TextPrice(5.00, 0.50, 30.00, false),
        "gpt-5.4" to TextPrice(2.50, 0.25, 15.00, false),
        "gpt-5.4-mini" to TextPrice(0.75, 0.075, 4.50, false),
        "gpt-5.4-nano" to TextPrice(0.20, 0.02, 1.25, false),
        "gpt-5-mini" to TextPrice(0.25, 0.025, 2.00, false),
        "gpt-5-nano" to TextPrice(0.05, 0.005, 0.40, false),
    )

    /** Explicit cache writes on GPT-5.6 / GPT-6 cost 1.25x input. */
    private const val CACHE_WRITE_MULTIPLIER = 1.25
    const val WEB_SEARCH_PER_CALL = 0.01
    private val TRANSCRIBE_PER_MINUTE = mapOf("gpt-transcribe" to 0.0045, "gpt-4o-transcribe" to 0.006, "gpt-4o-mini-transcribe" to 0.003)

    /** The longest known model id that [model] equals or starts with ("gpt-5.6-terra-2026-09-12" -> "gpt-5.6-terra"). */
    private fun <T> lookup(table: Map<String, T>, model: String?): T? {
        if (model == null) return null
        return table.entries.filter { model == it.key || model.startsWith(it.key + "-") }.maxByOrNull { it.key.length }?.value
    }

    fun known(model: String?): Boolean = lookup(TEXT, model) != null || lookup(TRANSCRIBE_PER_MINUTE, model) != null

    /** USD for one /responses call. Unknown model -> 0.0 (the report flags it). */
    fun responses(model: String?, usage: TokenUsage?, webSearches: Int): Double {
        val price = lookup(TEXT, model) ?: return webSearches * WEB_SEARCH_PER_CALL
        val u = usage ?: return webSearches * WEB_SEARCH_PER_CALL
        val write = if (price.explicitCache) u.cacheWriteTokens else 0
        val fresh = (u.inputTokens - u.cachedTokens - write).coerceAtLeast(0)
        val dollars = fresh * price.input + write * price.input * CACHE_WRITE_MULTIPLIER + u.cachedTokens * price.cached + u.outputTokens * price.output
        return dollars / 1_000_000 + webSearches * WEB_SEARCH_PER_CALL
    }

    /** USD for one transcription billed per second. */
    fun transcription(model: String, seconds: Double): Double = (lookup(TRANSCRIBE_PER_MINUTE, model) ?: 0.0) * seconds / 60.0
}
