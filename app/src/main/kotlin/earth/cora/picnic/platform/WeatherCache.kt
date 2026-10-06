package earth.cora.picnic.platform

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import earth.cora.picnic.core.weather.WeatherSource
import earth.cora.picnic.core.weather.WeatherToday
import earth.cora.picnic.ui.eggs.EggRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * The weather eggs' cache (docs/DESIGN_STICKER.md §12.4; eggs 11 and 16): the DataStore keys `weather_fetched_at`,
 * `weather_cache` (the last parsed day, rain today, snow seen), `rain_toast_day` and `first_snow_date`, in their own
 * small DataStore ("weather"), device-local and never backed up, plus `weather_failed_at` (a failed ask, retried after
 * 15 minutes; §0.4, 5 October). [WeatherService] decides when to ask the forecast: only with Weather surprises on, at
 * most every 3 h after an answer when the list opens, 15 minutes after a failed ask (or at once when the connection comes
 * back), the cache surviving restarts, silent offline. Owner: WP9 (WeatherCacheTest).
 */

/** What is cached. */
data class WeatherMemo(
    /** When the forecast last answered (epoch ms; before 5 October: when it was last asked for, answered or not). */
    val fetchedAt: Long? = null,
    /** When the last ask failed (epoch ms; offline, unreachable or unreadable), null after an answer: retried 15 min later. */
    val failedAt: Long? = null,
    /** The last forecast read: its day, rain today, snow. */
    val day: LocalDate? = null,
    val rain: Boolean = false,
    val snow: Boolean = false,
    /** The day the rainy-day toast last showed (at most once per day). */
    val rainToastDay: LocalDate? = null,
    /** The first snow day of this snow season (from 1 October); the first-snow tape shows for 7 days from it. */
    val firstSnowDate: LocalDate? = null,
    /**
     * The forecast city the last answer was for (its Environment Canada site code, Place.cityCode; §0.4 Q6): when the
     * area moves to another city, the forecast is asked again at once, whatever its age.
     */
    val city: String? = null,
)

interface WeatherCache {
    suspend fun read(): WeatherMemo

    suspend fun write(memo: WeatherMemo)
}

class InMemoryWeatherCache(var memo: WeatherMemo = WeatherMemo()) : WeatherCache {
    override suspend fun read(): WeatherMemo = memo

    override suspend fun write(memo: WeatherMemo) {
        this.memo = memo
    }
}

/** [WeatherCache] on a Preferences DataStore. A read or write that fails is ignored (no weather, never a crash). */
class DataStoreWeatherCache(private val store: DataStore<Preferences>) : WeatherCache {
    override suspend fun read(): WeatherMemo = try {
        val p = store.data.first()
        val cache = p[CACHE]?.split('|')
        WeatherMemo(
            fetchedAt = p[FETCHED_AT],
            failedAt = p[FAILED_AT],
            day = cache?.getOrNull(0)?.let(::date),
            rain = cache?.getOrNull(1) == "rain",
            snow = cache?.getOrNull(2) == "snow",
            city = cache?.getOrNull(3)?.takeIf { it.isNotEmpty() },
            rainToastDay = p[RAIN_TOAST_DAY]?.let(::date),
            firstSnowDate = p[FIRST_SNOW_DATE]?.let(::date),
        )
    } catch (e: IOException) {
        WeatherMemo()
    }

    override suspend fun write(memo: WeatherMemo) {
        try {
            store.edit { p ->
                memo.fetchedAt?.let { p[FETCHED_AT] = it } ?: p.remove(FETCHED_AT)
                memo.failedAt?.let { p[FAILED_AT] = it } ?: p.remove(FAILED_AT)
                val day = memo.day
                if (day == null) p.remove(CACHE) else p[CACHE] = "$day|${if (memo.rain) "rain" else "dry"}|${if (memo.snow) "snow" else "none"}|${memo.city.orEmpty()}"
                memo.rainToastDay?.let { p[RAIN_TOAST_DAY] = it.toString() } ?: p.remove(RAIN_TOAST_DAY)
                memo.firstSnowDate?.let { p[FIRST_SNOW_DATE] = it.toString() } ?: p.remove(FIRST_SNOW_DATE)
            }
        } catch (e: IOException) {
            PicnicLog.w("couldn't keep the weather", e)
        }
    }

    private fun date(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    companion object {
        val FETCHED_AT = longPreferencesKey("weather_fetched_at")
        val FAILED_AT = longPreferencesKey("weather_failed_at")
        val CACHE = stringPreferencesKey("weather_cache")
        val RAIN_TOAST_DAY = stringPreferencesKey("rain_toast_day")
        val FIRST_SNOW_DATE = stringPreferencesKey("first_snow_date")

        /** The phone's: a DataStore of its own ("weather"). One per process (AppContainer). */
        fun create(context: Context): DataStoreWeatherCache =
            DataStoreWeatherCache(PreferenceDataStoreFactory.create { context.applicationContext.preferencesDataStoreFile("weather") })
    }
}

/** The weather the eggs see today. */
data class EggWeather(val rain: Boolean, val firstSnowDate: LocalDate?, val rainToastDue: Boolean)

/**
 * When to ask the forecast (§12.4): [check] runs when the list opens; with Weather surprises off it requests nothing
 * and returns no weather; with it on it asks [source] when the last answer is 3 h old or more, or was for another
 * forecast city than [city] names now ("Use my area" turned on or off, or her area moved to another city, §0.4 Q6), keeps
 * the answer, and keeps the first-snow date of this season. An unanswered ask (offline, unreachable) keeps the old
 * forecast and is asked again [RETRY_MS] later, not 3 h later; with `retryFailed` (the catch-up when the connection
 * comes back, §0.4 5 October) at once.
 */
class WeatherService(
    private val source: WeatherSource,
    private val cache: WeatherCache,
    private val clock: () -> Long,
    private val zone: ZoneId = ZoneId.of("America/Toronto"),
    /** The forecast city the source asks for now (PlaceSource's Place.cityCode; Toronto's by default). */
    private val city: suspend () -> String = { earth.cora.picnic.core.calendar.Place.TORONTO.cityCode },
) {
    private val mutex = Mutex()

    /** Requests made (tests). */
    @Volatile
    var requests: Int = 0
        private set

    suspend fun check(weatherOn: Boolean, retryFailed: Boolean = false): EggWeather? = mutex.withLock {
        try {
            val now = clock()
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            var memo = cache.read()
            if (!weatherOn) return@withLock null
            val here = city()
            val failedAt = memo.failedAt
            val due = when {
                memo.city != here -> true
                // The last ask failed: again 15 minutes later (the old forecast meanwhile), or at once after the catch-up.
                failedAt != null -> retryFailed || now - failedAt !in 0 until RETRY_MS
                else -> memo.fetchedAt == null || now - memo.fetchedAt!! !in 0 until REFETCH_MS
            }
            if (due) {
                requests++
                val answer: WeatherToday? = source.today()
                memo = if (answer != null) {
                    memo.copy(
                        fetchedAt = now,
                        failedAt = null,
                        day = answer.date,
                        rain = answer.rain,
                        snow = answer.snow,
                        firstSnowDate = EggRules.firstSnowDate(memo.firstSnowDate, answer.date, answer.snow),
                        city = here,
                    )
                } else if (memo.city != here) {
                    // No answer for the new city: the old city's rain is not hers.
                    memo.copy(failedAt = now, day = null, rain = false, snow = false, city = here)
                } else {
                    memo.copy(failedAt = now)
                }
                cache.write(memo)
            }
            val rainToday = memo.day == today && memo.rain
            EggWeather(rain = rainToday, firstSnowDate = memo.firstSnowDate, rainToastDue = rainToday && memo.rainToastDay != today)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PicnicLog.w("weather check failed", e)
            null
        }
    }

    /** The rainy-day toast showed today: not again until tomorrow. */
    suspend fun markRainToast() = mutex.withLock {
        val today = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()
        cache.write(cache.read().copy(rainToastDay = today))
    }

    /**
     * "Use my area" is off or was refused (§0.4 Q6, PRIVACY.md): the forecast city worked out from her area is forgotten,
     * with what its forecast said, so only the place store ever keeps her area (and it is deleted with the switch).
     * Toronto's own city is not hers and stays. The next check with Weather surprises on asks Toronto's forecast.
     */
    suspend fun forgetCity() = mutex.withLock {
        val memo = cache.read()
        if (memo.city == null || memo.city == earth.cora.picnic.core.calendar.Place.TORONTO.cityCode) return@withLock
        cache.write(memo.copy(city = null, day = null, rain = false, snow = false))
    }

    /** The first-snow date kept, whatever the switch (the tape keeps its 7 days). */
    suspend fun firstSnowDate(): LocalDate? = mutex.withLock { cache.read().firstSnowDate }

    companion object {
        /** The forecast is asked at most every 3 h after an answer (§12.4). */
        const val REFETCH_MS: Long = 3L * 60 * 60 * 1000

        /** A failed ask is tried again this much later (§0.4, 5 October). */
        const val RETRY_MS: Long = 15L * 60 * 1000
    }
}
