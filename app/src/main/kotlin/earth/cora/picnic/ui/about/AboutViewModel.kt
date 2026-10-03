package earth.cora.picnic.ui.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import earth.cora.picnic.AppContainer
import earth.cora.picnic.platform.PicnicLog
import earth.cora.picnic.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/*
 * About & licences (docs/DESIGN_STICKER.md §6.9, checklist 21 and 229). Owner: WP8 (AboutScreenTest).
 */

/** One OFL licence of `assets/licenses/` (WP2): the family, what the app uses it for, and the full licence text. */
@Serializable
data class Licence(val family: String, val file: String, val licence: String, val use: String, val source: String, val text: String = "")

/** Reads `assets/licenses/licenses.json` and each listed text file. Five entries: the four bundled families and DotGothic16. */
object Licences {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<Licence> {
        val assets = context.assets
        val index = assets.open("licenses/licenses.json").bufferedReader().use { it.readText() }
        return json.decodeFromString<List<Licence>>(index).map { l ->
            l.copy(text = assets.open("licenses/${l.file}").bufferedReader().use { it.readText() }.trim())
        }
    }
}

data class AboutUiState(
    val appVersion: String = "",
    val licences: List<Licence> = emptyList(),
    /** The Surprise lab is unlocked (persisted) or this is a debug build: its row shows. */
    val labVisible: Boolean = false,
    /** Seven taps unlock it only in a release build that has not unlocked it yet. */
    val canUnlock: Boolean = true,
)

class AboutViewModel(
    private val settings: SettingsRepository,
    private val appVersion: String,
    private val debugBuild: Boolean,
    /** The app's clock (AppContainer.clock): the version line's seven taps are timed with it. */
    val clock: () -> Long = System::currentTimeMillis,
    private val loadLicences: suspend () -> List<Licence>,
) : ViewModel() {
    private val licences = MutableStateFlow<List<Licence>>(emptyList())

    val state: StateFlow<AboutUiState> = combine(settings.settings, licences) { s, l ->
        AboutUiState(appVersion, l, labVisible = debugBuild || s.surpriseLab, canUnlock = !debugBuild && !s.surpriseLab)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AboutUiState(appVersion, labVisible = debugBuild, canUnlock = !debugBuild))

    init {
        viewModelScope.launch {
            licences.value = try {
                loadLicences()
            } catch (e: IOException) {
                PicnicLog.w("licences unreadable", e)
                emptyList()
            }
        }
    }

    /** The seventh tap on the version line (§6.9): the lab stays unlocked. */
    fun unlockLab() {
        viewModelScope.launch {
            try {
                settings.setSurpriseLab(true)
            } catch (e: IOException) {
                PicnicLog.w("couldn't unlock the lab", e)
            }
        }
    }

    companion object {
        fun factory(c: AppContainer, context: Context, appVersion: String, debugBuild: Boolean): ViewModelProvider.Factory = viewModelFactory {
            val app = context.applicationContext
            initializer {
                AboutViewModel(c.settings, appVersion, debugBuild, loadLicences = { withContext(Dispatchers.IO) { Licences.load(app) } }, clock = c.clock)
            }
        }
    }
}
