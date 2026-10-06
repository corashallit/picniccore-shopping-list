package earth.cora.picnic.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A [NetworkStatus] the test drives: [goOffline], [goOnline] (an arrival), [switchNetwork] (an arrival while online). */
class FakeNetworkStatus(online: Boolean = true) : NetworkStatus {
    private val _online = MutableStateFlow(online)
    private val _arrivals = MutableStateFlow(0)
    override val online: StateFlow<Boolean> = _online.asStateFlow()
    override val arrivals: StateFlow<Int> = _arrivals.asStateFlow()

    /** The isOnline hook core's OpenAiClient reads. */
    val isOnline: () -> Boolean = { _online.value }

    fun goOffline() {
        _online.value = false
    }

    fun goOnline() {
        if (_online.value) return
        _online.value = true
        _arrivals.update { it + 1 }
    }

    fun switchNetwork() {
        _online.value = true
        _arrivals.update { it + 1 }
    }
}
