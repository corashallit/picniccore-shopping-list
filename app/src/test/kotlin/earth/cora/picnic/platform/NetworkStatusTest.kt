package earth.cora.picnic.platform

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * The network monitor (docs/DESIGN_STICKER.md §0.4, 5 October; item 348): offline only when there is no network that
 * offers internet; a network that is there but not validated (a captive portal, or validation blocked) still counts as
 * online; an arrival is counted when the phone comes online, moves to another network or gets validated, never for the
 * first report at registration.
 */
@RunWith(AndroidJUnit4::class)
class NetworkStatusTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val cm: ConnectivityManager = app.getSystemService(ConnectivityManager::class.java)

    private fun caps(vararg capabilities: Int): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also { c -> capabilities.forEach { shadowOf(c).addCapability(it) } }

    private fun callback(): ConnectivityManager.NetworkCallback = shadowOf(cm).networkCallbacks.single()

    @Test
    fun anUnvalidatedNetworkStillCountsAsOnline() {
        // The phone starts on Wi-Fi that offers internet (Robolectric's active network, given its capabilities).
        val wifi = requireNotNull(cm.activeNetwork) { "Robolectric has an active network" }
        shadowOf(cm).setNetworkCapabilities(wifi, caps(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        val status = AndroidNetworkStatus.create(app)
        assertTrue(status.online.value)
        val cb = callback()
        cb.onAvailable(wifi)
        // Wi-Fi behind a captive portal: internet offered, not validated. Requests are still tried on it.
        cb.onCapabilitiesChanged(wifi, caps(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        assertTrue(status.online.value)
        assertEquals("the first report is not an arrival", 0, status.arrivals.value)

        // Airplane mode: no network at all.
        cb.onLost(wifi)
        assertFalse(status.online.value)

        // Back: an arrival; then validated on the same network: another.
        cb.onAvailable(wifi)
        cb.onCapabilitiesChanged(wifi, caps(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        assertTrue(status.online.value)
        assertEquals(1, status.arrivals.value)
        cb.onCapabilitiesChanged(wifi, caps(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        assertEquals(2, status.arrivals.value)

        // Wi-Fi handed over to mobile data while online: an arrival; the old network's late "lost" changes nothing.
        val mobile = ShadowNetwork.newInstance(4242)
        cb.onAvailable(mobile)
        assertEquals(3, status.arrivals.value)
        cb.onLost(wifi)
        assertTrue(status.online.value)

        // A default network that does not offer internet (rare): offline.
        cb.onCapabilitiesChanged(mobile, caps())
        assertFalse(status.online.value)
    }

    @Test
    fun noActiveNetworkStartsOffline() {
        shadowOf(cm).setActiveNetworkInfo(null)
        val status = AndroidNetworkStatus.create(app)
        assertFalse(status.online.value)
        val cb = callback()
        val wifi = ShadowNetwork.newInstance(4343)
        cb.onAvailable(wifi)
        assertTrue(status.online.value)
        assertEquals("coming online is an arrival, even as the first report", 1, status.arrivals.value)
    }
}
