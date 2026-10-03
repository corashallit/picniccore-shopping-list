package earth.cora.picnic.core.weather

import earth.cora.picnic.core.calendar.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/*
 * The forecast behind the weather eggs (docs/DESIGN_STICKER.md §12.4: egg 16 "Rainy day" and the first-snow sheet of
 * egg 11). Asked for only while Surprises → Weather surprises is on (the app's WeatherCache decides when; this file
 * only fetches and parses). The feed is Environment Canada's city forecast for the nearest forecast city of the place
 * (§0.4 Q6: the approximate area when "Use my area" is on, else Toronto): the request names only that city's own
 * coordinates, never the phone's point, and carries no identifier. Owner: WP9 (WeatherSourceTest).
 */

/** What the eggs need from today's forecast. */
data class WeatherToday(
    /** The day it was read for (America/Toronto). */
    val date: LocalDate,
    /** Today's or tonight's forecast mentions rain, showers or drizzle, or a probability of 40 % or more (§12.4). */
    val rain: Boolean,
    /** A forecast entry mentions snow or flurries (the first-snow sheet, egg 11). */
    val snow: Boolean,
)

/** Today's forecast; null = off, offline or unparseable (any error is no weather, never a crash). */
fun interface WeatherSource {
    suspend fun today(): WeatherToday?
}

/**
 * Environment Canada's city forecast feed (§12.4): `https://weather.gc.ca/rss/weather/<lat>_<lon>_e.xml` for the
 * forecast city of [place] ([Place.forecastFeedUrl], the city's own coordinates). One GET, 10 s timeout, no retry;
 * any failure is null. [baseUrl] replaces the host in tests (MockWebServer).
 */
class EnvironmentCanadaWeather(
    private val http: () -> OkHttpClient,
    private val place: suspend () -> Place = { Place.TORONTO },
    private val date: () -> LocalDate = { LocalDate.now(TORONTO_ZONE) },
    private val baseUrl: String = EC_HOST,
) : WeatherSource {
    /** The phone's form: core's own OkHttp client, built on first use off the main thread. */
    constructor(
        place: suspend () -> Place = { Place.TORONTO },
        date: () -> LocalDate = { LocalDate.now(TORONTO_ZONE) },
    ) : this(http = { defaultClient }, place = place, date = date)

    /** The feed URL for [p]: its forecast city's coordinates on [baseUrl]. */
    fun feedUrl(p: Place): String = baseUrl.trimEnd('/') + p.forecastFeedUrl.removePrefix(EC_HOST)

    override suspend fun today(): WeatherToday? = try {
        val p = place()
        val day = date()
        val body = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(feedUrl(p)).get().build()
            http().newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body.string()
            }
        }
        body?.let { EcForecastParser.parse(it, day) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        null
    } catch (e: RuntimeException) {
        // A malformed URL or response: no weather, silently (§12.4).
        null
    }

    companion object {
        const val EC_HOST: String = "https://weather.gc.ca"

        /** The calendar the weather eggs keep (§12.2). */
        val TORONTO_ZONE: ZoneId = ZoneId.of("America/Toronto")

        /** 10 s for the whole call, no retry (§12.4). */
        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .callTimeout(10, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(true)
                .build()
        }
    }
}

/**
 * Reads Environment Canada's Atom forecast feed (§12.4). Pure. Only the `<entry>` elements whose `<category term>` is
 * "Weather Forecasts" count; each title reads like "Saturday: Rain. High 12." or "Sunday: Chance of showers. High 21.
 * POP 40%", with the summary carrying "40 percent chance of showers".
 * - **Rain today**: today's entries ("Saturday", "Saturday night", "Today", "Tonight"; the feed's first period when no
 *   title names today) mention rain, showers or drizzle, or give a probability of 40 % or more for anything but snow
 *   (a "40 percent chance of flurries" is snow, not rain).
 * - **Snow**: any forecast entry mentions snow or flurries.
 * Null for a feed that is not a whole Atom feed or holds no forecast entry.
 */
object EcForecastParser {
    private val ENTRY = Regex("<entry\\b[^>]*>(.*?)</entry>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val TITLE = Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val SUMMARY = Regex("<summary\\b[^>]*>(.*?)</summary>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val CATEGORY = Regex("<category\\b[^>]*\\bterm\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val RAIN = Regex("\\b(rain|showers?|drizzle)", RegexOption.IGNORE_CASE)
    private val SNOW = Regex("\\b(snow|flurr)", RegexOption.IGNORE_CASE)
    private val PROBABILITY = Regex("(\\d{1,3})\\s*(%|percent)", RegexOption.IGNORE_CASE)
    private const val FORECASTS = "Weather Forecasts"
    private const val RAIN_PROBABILITY = 40

    /** One forecast period: its title's period ("Saturday night") and its words. */
    data class Period(val name: String, val text: String)

    fun parse(xml: String, today: LocalDate): WeatherToday? {
        val trimmed = xml.trim()
        if (!trimmed.contains("<feed", ignoreCase = true) || !trimmed.endsWith("</feed>", ignoreCase = true)) return null
        val periods = periods(trimmed)
        if (periods.isEmpty()) return null
        val day = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val todays = periods.filter { isToday(it.name, day) }.ifEmpty { firstPeriod(periods) }
        return WeatherToday(
            date = today,
            rain = todays.any(::rains),
            snow = periods.any { SNOW.containsMatchIn(it.text) },
        )
    }

    /** The forecast periods in feed order. */
    fun periods(xml: String): List<Period> = ENTRY.findAll(xml).mapNotNull { m ->
        val entry = m.groupValues[1]
        val category = CATEGORY.find(entry)?.groupValues?.get(1)?.trim()
        if (!category.equals(FORECASTS, ignoreCase = true)) return@mapNotNull null
        val title = TITLE.find(entry)?.groupValues?.get(1)?.let(::clean).orEmpty()
        val summary = SUMMARY.find(entry)?.groupValues?.get(1)?.let(::clean).orEmpty()
        val name = title.substringBefore(':', missingDelimiterValue = "").trim()
        Period(name, "$title $summary")
    }.toList()

    private fun isToday(name: String, weekday: String): Boolean {
        val n = name.lowercase(Locale.ROOT)
        val d = weekday.lowercase(Locale.ROOT)
        return n == "today" || n == "tonight" || n == d || n == "$d night"
    }

    /** No title names today (a feed fetched just after midnight, or a holiday name): the feed's current period(s). */
    private fun firstPeriod(periods: List<Period>): List<Period> {
        val first = periods.first()
        val second = periods.getOrNull(1)
        val night = second != null && second.name.lowercase(Locale.ROOT) == first.name.lowercase(Locale.ROOT) + " night"
        return if (night) listOf(first, second!!) else listOf(first)
    }

    private fun rains(p: Period): Boolean {
        if (RAIN.containsMatchIn(p.text)) return true
        val probable = PROBABILITY.findAll(p.text).any { (it.groupValues[1].toIntOrNull() ?: 0) >= RAIN_PROBABILITY }
        return probable && !SNOW.containsMatchIn(p.text)
    }

    /** CDATA, tags and the few entities the feed uses, as plain text. */
    private fun clean(raw: String): String = raw
        .replace("<![CDATA[", "")
        .replace("]]>", "")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&deg;", "°")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#39;", "'")
        .replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ")
        .trim()
}
