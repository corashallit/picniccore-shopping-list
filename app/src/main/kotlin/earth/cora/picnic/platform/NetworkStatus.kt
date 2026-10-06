package earth.cora.picnic.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/*
 * Whether the phone can reach the internet at all (docs/DESIGN_STICKER.md §0.4, 5 October: the offline experience;
 * docs/ARCHITECTURE.md §10.1). Read by core's OpenAiClient before every call (no network: LlmError.Offline at once),
 * by the add flows (Send adds as typed), by the icon cascade (nothing is drawn or decided offline) and by the catch-up
 * that runs when the connection comes back. Needs ACCESS_NETWORK_STATE (a normal permission: no prompt).
 */

/**
 * The phone's connection, as far as Android knows it. [online] is false only when there is **no** network that offers
 * internet (airplane mode, no signal, Wi-Fi and data off). A network that is there but not validated (a captive portal,
 * or validation blocked on that network) still counts as online: requests are tried on it, since validation is
 * unreliable on some networks, and a call that cannot connect fails on its own as unreachable.
 */
interface NetworkStatus {
    val online: StateFlow<Boolean>

    /**
     * Counts each time a usable connection arrives: the phone comes online, the default network changes (Wi-Fi swapped
     * for mobile data), or the network it has passes validation. The catch-up runs after each (debounced).
     */
    val arrivals: StateFlow<Int>

    /** For previews, tests that do not care, and when Android cannot tell: always online, nothing ever arrives. */
    object AlwaysOnline : NetworkStatus {
        override val online: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
        override val arrivals: StateFlow<Int> = MutableStateFlow(0).asStateFlow()
    }
}

/**
 * [NetworkStatus] from ConnectivityManager's default-network callback. The state starts from the active network's
 * capabilities (read once, synchronously) and follows the callback. If Android refuses the callback (no permission on
 * an odd build), it stays online: the app never claims to be offline without evidence.
 */
class AndroidNetworkStatus private constructor(private val cm: ConnectivityManager?) : NetworkStatus {
    private val state = MutableStateFlow(Snapshot(online = initialOnline()))
    private val _online = MutableStateFlow(state.value.online)
    private val _arrivals = MutableStateFlow(0)
    override val online: StateFlow<Boolean> = _online.asStateFlow()
    override val arrivals: StateFlow<Int> = _arrivals.asStateFlow()

    /**
     * What the callback last said: whether there is a default network with internet, which one, and if it is validated.
     * [known] turns true at the first full report (capabilities), so registering is never mistaken for an arrival.
     */
    private data class Snapshot(val online: Boolean, val network: Network? = null, val validated: Boolean = false, val known: Boolean = false)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // The default network always offers internet; its capabilities follow at once (onCapabilitiesChanged).
            publish { it.copy(online = true, network = network, validated = it.validated && it.network == network) }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            publish {
                it.copy(
                    online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    network = network,
                    validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    known = true,
                )
            }
        }

        override fun onLost(network: Network) {
            // Only the network we follow: a late "lost" for the one it replaced changes nothing.
            publish { if (it.network == null || it.network == network) Snapshot(online = false, known = true) else it }
        }

        override fun onUnavailable() {
            publish { Snapshot(online = false, known = true) }
        }
    }

    private fun initialOnline(): Boolean = try {
        val manager = cm ?: return true
        val active = manager.activeNetwork ?: return false
        // Capabilities Android cannot give yet are no evidence of being offline.
        manager.getNetworkCapabilities(active)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: true
    } catch (e: RuntimeException) {
        true
    }

    private fun publish(change: (Snapshot) -> Snapshot) {
        var arrived = false
        state.update { before ->
            val after = change(before)
            // A usable connection arrived: online now and not before; or, once the first report is in, another default
            // network, or the same one newly validated.
            val cameOnline = after.online && !before.online
            val moved = before.known && after.online && before.network != null && after.network != null && after.network != before.network
            val validated = before.known && after.online && after.network == before.network && after.validated && !before.validated
            arrived = cameOnline || moved || validated
            after
        }
        _online.value = state.value.online
        if (arrived) _arrivals.update { it + 1 }
    }

    private fun register(): Boolean = try {
        cm?.registerDefaultNetworkCallback(callback)
        cm != null
    } catch (e: RuntimeException) {
        // SecurityException without ACCESS_NETWORK_STATE, or a vendor quirk: assume online (see the class comment).
        PicnicLog.w("network callback unavailable; assuming online", e)
        false
    }

    companion object {
        /** The phone's monitor (one per process, AppContainer): registered at once, for the process's lifetime. */
        fun create(context: Context): NetworkStatus {
            val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
            val status = AndroidNetworkStatus(cm)
            return if (status.register()) status else NetworkStatus.AlwaysOnline
        }
    }
}
