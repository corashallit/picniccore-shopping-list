package earth.cora.picnic.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import earth.cora.picnic.core.calendar.Place
import earth.cora.picnic.core.calendar.PlaceSource
import earth.cora.picnic.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.Executor
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * "Use my area" (Cora's answer to Q6, docs/DESIGN_STICKER.md §0.4): where sunset (egg 12) and the forecast (eggs 11 and
 * 16) are computed for. Owner: WP8 (PlaceSourceTest); WP9's SunClock, ThemeVariantSource and weather consume it through
 * core's [PlaceSource].
 *
 * - Off (the default), or coarse location not granted: Toronto (43.65 N, 79.38 W), exactly as before, and nothing about
 *   location is stored (a stored point is deleted).
 * - On and granted: the platform LocationManager's coarse fix (network, fused or passive providers; never GPS, no Google
 *   Play services, no background location) is rounded to 0.1° (about 10 km) BEFORE any use or storage, then matched to
 *   the nearest Environment Canada forecast city of a bundled list ([EcSites]). Only the rounded point, the city code
 *   and the time are stored, and the fix is refreshed at most every 6 h.
 * - It never throws: any failure falls back to the last stored place, else Toronto.
 */

/** A point in degrees (east positive). */
data class LatLon(val lat: Double, val lon: Double)

/** One Environment Canada city forecast site ([EC_SITES]). [code] is EC's site code ("s0000458" = Toronto). */
data class EcSite(val code: String, val name: String, val province: String, val lat: Double, val lon: Double) {
    /**
     * The city's forecast feed ([Place.forecastFeedUrl]): requested for the CITY's own coordinates (the same for everyone
     * near it), never for the phone's point.
     */
    val feedUrl: String get() = Place.forecastFeedUrl(lat, lon)
}

/** The bundled forecast cities and the pure parts of "Use my area" (PlaceSourceTest). */
object EcSites {
    /** Toronto's site, [Place.TORONTO]'s city. */
    val TORONTO: EcSite = EC_SITES.first { it.code == Place.TORONTO.cityCode }

    val all: List<EcSite> get() = EC_SITES

    /** The site for a [Place.cityCode] (an EC site code). Null when unknown. */
    fun byCode(code: String): EcSite? = EC_SITES.firstOrNull { it.code == code }

    /** The [Place] for a rounded [point] and its forecast [site]: the site's code and its own coordinates. */
    fun place(point: LatLon, site: EcSite): Place = Place(lat = point.lat, lon = point.lon, cityCode = site.code, siteLat = site.lat, siteLon = site.lon)

    /** The nearest bundled forecast city to [point] (great-circle distance). */
    fun nearest(point: LatLon): EcSite = EC_SITES.minBy { distanceKm(point, LatLon(it.lat, it.lon)) }

    /**
     * Rounds to 0.1° (about 10 km) before the point is used or stored (§0.4): on the decimal value, halves away from
     * zero (43.65 -> 43.7, -79.45 -> -79.5), so a binary 43.649999… still rounds the way it reads.
     */
    fun round(value: Double): Double =
        if (value.isNaN() || value.isInfinite()) value else BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toDouble()

    fun round(point: LatLon): LatLon = LatLon(round(point.lat), round(point.lon))

    /** The place for a raw coarse fix: rounded first, then its nearest city. */
    fun placeFor(fix: LatLon): Place {
        val rounded = round(fix)
        return place(rounded, nearest(rounded))
    }

    /** The great-circle distance in km. */
    fun distanceKm(a: LatLon, b: LatLon): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}

/** What a "Use my area" lookup found (Settings' hint, §0.4: say plain true things). */
sealed interface AreaLookup {
    /** The switch is off, or approximate location isn't allowed: Toronto. */
    data object Off : AreaLookup

    /** A lookup is running. */
    data object Finding : AreaLookup

    /** Her area: [place] (rounded) and its forecast [city]'s name. */
    data class Found(val place: Place, val city: String) : AreaLookup

    /** On and allowed, but the phone had no fix (and none is stored): Toronto for now. */
    data object NoFix : AreaLookup
}

/** The phone's coarse location, as far as "Use my area" needs it. */
interface CoarseLocator {
    /** ACCESS_COARSE_LOCATION is granted. */
    fun hasPermission(): Boolean

    /** A coarse fix (not yet rounded), or null when the phone has none. Never throws. */
    suspend fun locate(): LatLon?
}

/** The one stored place: the rounded point, its city and when it was found. */
data class StoredPlace(val lat: Double, val lon: Double, val cityCode: String, val at: Long) {
    /** The place, with its city's own coordinates from the bundled list (an unknown code: the nearest city's). */
    val place: Place get() = LatLon(lat, lon).let { p -> EcSites.place(p, EcSites.byCode(cityCode) ?: EcSites.nearest(p)) }
}

/** Where the rounded place lives between runs. [write] null deletes it. */
interface PlaceCache {
    suspend fun read(): StoredPlace?

    suspend fun write(place: StoredPlace?)
}

class InMemoryPlaceCache(var stored: StoredPlace? = null) : PlaceCache {
    override suspend fun read(): StoredPlace? = stored

    override suspend fun write(place: StoredPlace?) {
        stored = place
    }
}

/**
 * The "Use my area" [PlaceSource] (§0.4 Q6, PlaceSourceTest). [useMyArea] reads the Surprises switch; the [locator]'s
 * fix is rounded to 0.1° before anything else sees it; a stored place younger than [REFRESH_MILLIS] is reused; a lookup
 * that found no fix is not tried again for [MISS_MILLIS] (the stored place or Toronto meanwhile), so a phone with
 * location services off is not asked every minute.
 */
class AreaPlaceSource(
    private val useMyArea: suspend () -> Boolean,
    private val locator: CoarseLocator,
    private val cache: PlaceCache,
    private val clock: () -> Long,
) : PlaceSource {
    private val mutex = Mutex()

    /** When the last lookup found no fix (guarded by [mutex]); cleared by a fix and by [forget]. */
    private var lastMissAt: Long? = null

    override suspend fun place(): Place = mutex.withLock {
        try {
            resolve()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PicnicLog.w("place unavailable; using Toronto", e)
            Place.TORONTO
        }
    }

    /**
     * Deletes the stored point and city at once: "Use my area" went off, or location was refused (§0.4, PRIVACY.md: turning
     * it off deletes the stored area). Waits for a lookup in progress, so that lookup can't store it again. Never throws.
     */
    suspend fun forget() = mutex.withLock {
        // Switched off (or refused): the next time it is on, the phone is asked at once.
        lastMissAt = null
        try {
            if (cache.read() != null) cache.write(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PicnicLog.w("couldn't delete the stored area", e)
        }
    }

    /**
     * The place, and whether it is really her area: [AreaLookup.Found] with its forecast city, [AreaLookup.NoFix] when the
     * switch and the permission are on but the phone has no fix yet (location services off, airplane mode: Toronto
     * meanwhile), [AreaLookup.Off] when it is off or not allowed.
     */
    suspend fun lookup(): AreaLookup {
        val place = place()
        val on = try {
            useMyArea() && locator.hasPermission()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        return when {
            !on -> AreaLookup.Off
            place == Place.TORONTO -> AreaLookup.NoFix
            else -> AreaLookup.Found(place, EcSites.byCode(place.cityCode)?.name ?: EcSites.nearest(LatLon(place.lat, place.lon)).name)
        }
    }

    private suspend fun resolve(): Place {
        if (!useMyArea() || !locator.hasPermission()) {
            // Off or denied: Toronto, and no location stays on the phone.
            if (cache.read() != null) cache.write(null)
            return Place.TORONTO
        }
        val now = clock()
        val stored = cache.read()
        if (stored != null && now - stored.at in 0 until REFRESH_MILLIS) return stored.place
        if (lastMissAt?.let { now - it in 0 until MISS_MILLIS } == true) return stored?.place ?: Place.TORONTO
        val fix = try {
            locator.locate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PicnicLog.w("no coarse fix", e)
            null
        }
        if (fix == null) {
            lastMissAt = now
            return stored?.place ?: Place.TORONTO
        }
        lastMissAt = null
        val place = EcSites.placeFor(fix)
        cache.write(StoredPlace(place.lat, place.lon, place.cityCode, now))
        return place
    }

    companion object {
        /** The fix is refreshed at most every 6 h (§0.4). */
        const val REFRESH_MILLIS: Long = 6L * 60 * 60 * 1000

        /** After a lookup with no fix, the phone is not asked again for 30 min. */
        const val MISS_MILLIS: Long = 30L * 60 * 1000

        /** The phone's implementation: LocationManager + a small DataStore ("place"). One per process (AppContainer). */
        fun create(context: Context, settings: SettingsRepository, clock: () -> Long): AreaPlaceSource {
            val app = context.applicationContext
            return AreaPlaceSource(
                useMyArea = { settings.current().useMyArea },
                locator = AndroidCoarseLocator(app),
                cache = DataStorePlaceCache(PreferenceDataStoreFactory.create { app.preferencesDataStoreFile("place") }),
                clock = clock,
            )
        }
    }
}

/** [PlaceCache] on a Preferences DataStore: `place_lat`, `place_lon`, `place_city`, `place_at`; nothing else. */
class DataStorePlaceCache(private val store: DataStore<Preferences>) : PlaceCache {
    override suspend fun read(): StoredPlace? = try {
        val p = store.data.first()
        val lat = p[LAT]
        val lon = p[LON]
        val city = p[CITY]
        val at = p[AT]
        if (lat == null || lon == null || city == null || at == null) null else StoredPlace(lat, lon, city, at)
    } catch (e: IOException) {
        null
    }

    override suspend fun write(place: StoredPlace?) {
        try {
            store.edit {
                if (place == null) {
                    it.remove(LAT); it.remove(LON); it.remove(CITY); it.remove(AT)
                } else {
                    it[LAT] = place.lat; it[LON] = place.lon; it[CITY] = place.cityCode; it[AT] = place.at
                }
            }
        } catch (e: IOException) {
            PicnicLog.w("couldn't store the place", e)
        }
    }

    private companion object {
        val LAT = doublePreferencesKey("place_lat")
        val LON = doublePreferencesKey("place_lon")
        val CITY = stringPreferencesKey("place_city")
        val AT = longPreferencesKey("place_at")
    }
}

/**
 * Coarse location from the platform LocationManager (no Google Play services): the newest last-known fix of the
 * network, fused (API 31+) or passive providers when it is recent, else one current fix (at most [TIMEOUT_MILLIS]),
 * else any last-known fix. Coarse permission only, so the platform hands out an approximate point anyway.
 */
class AndroidCoarseLocator(private val context: Context) : CoarseLocator {
    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override suspend fun locate(): LatLon? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        val providers = providers(manager)
        val known = providers.mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }.maxByOrNull { it.time }
        if (known != null && System.currentTimeMillis() - known.time < AreaPlaceSource.REFRESH_MILLIS) return@withContext known.toLatLon()
        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER && isEnabled(manager, it) }
        val current = provider?.let { withTimeoutOrNull(TIMEOUT_MILLIS) { currentLocation(manager, it) } }
        (current ?: known)?.toLatLon()
    }

    private fun providers(manager: LocationManager): List<String> {
        val all = try {
            manager.allProviders.toSet()
        } catch (e: SecurityException) {
            emptySet()
        }
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        return wanted.filter { it in all }
    }

    private fun isEnabled(manager: LocationManager, provider: String): Boolean = try {
        manager.isProviderEnabled(provider)
    } catch (e: Exception) {
        false
    }

    private suspend fun currentLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            val direct = Executor { it.run() }
            try {
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, direct) { location ->
                    if (cont.isActive) cont.resumeWith(Result.success(location))
                }
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resumeWith(Result.success(null))
            } catch (e: IllegalArgumentException) {
                if (cont.isActive) cont.resumeWith(Result.success(null))
            }
        }

    private fun Location.toLatLon() = LatLon(latitude, longitude)

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
