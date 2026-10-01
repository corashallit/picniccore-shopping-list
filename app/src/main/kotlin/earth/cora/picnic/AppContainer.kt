package earth.cora.picnic

import android.app.Application
import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.assistant.GroceryAssistant
import earth.cora.picnic.core.assistant.OpenAiGroceryAssistant
import earth.cora.picnic.core.catalog.BasketCatalog
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.ConfigSource
import earth.cora.picnic.core.llm.OpenAiClient
import earth.cora.picnic.core.llm.TokenSource
import earth.cora.picnic.core.model.StateOps
import earth.cora.picnic.core.store.JsonFileStateStore
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.icons.IconImages
import earth.cora.picnic.icons.IconRepository
import earth.cora.picnic.platform.AudioRecorder
import earth.cora.picnic.platform.CapturePhotoPreparer
import earth.cora.picnic.platform.PhotoPreparer
import earth.cora.picnic.platform.VoiceRecorder
import earth.cora.picnic.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID

/**
 * Manual DI graph (one per process, owned by PicnicApp). CONTRACT (docs/ARCHITECTURE.md §10):
 * everything is lazy and touching a property never blocks the main thread on I/O.
 * ViewModels take the individual dependencies (not the container) so tests can pass fakes.
 */
class AppContainer(private val app: Application) {
    /** Process-lifetime scope for work that must outlive a screen (aisle sync, icon queue, commits). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Epoch millis. */
    val clock: () -> Long = System::currentTimeMillis

    /** New random id for lists and items. */
    fun newId(): String = UUID.randomUUID().toString()

    val catalog: IconCatalog by lazy { IconCatalog.default }

    val baskets: BasketCatalog get() = catalog.baskets

    val store: StateStore by lazy {
        JsonFileStateStore(
            file = File(app.filesDir, "state.json"),
            initial = { StateOps.initialState(newId(), clock()) },
        )
    }

    /** app-shell: DataStoreSettingsRepository(app, KeystoreTokenStore(...)). */
    val settings: SettingsRepository by lazy { TODO("app-shell: settings repository") }

    /** Reads settings before every call (SettingsRepository fills llm.safetyIdentifier from the install id). */
    val configSource: ConfigSource = ConfigSource { settings.current().llm }

    val tokenSource: TokenSource = TokenSource { settings.token() }

    val assistant: GroceryAssistant by lazy {
        OpenAiGroceryAssistant(OpenAiClient(configSource, tokenSource), configSource, catalog)
    }

    val aisleSync: AisleSync by lazy { AisleSync(store, assistant, baskets) }

    /** app-shell: bundled + bespoke icons and the live drawing queue. */
    val icons: IconRepository by lazy { TODO("app-shell: icon repository") }

    /** app-shell: bitmap loader provided to the theme layer through LocalIconImages. */
    val iconImages: IconImages by lazy { IconImages.None }

    val photos: PhotoPreparer by lazy { CapturePhotoPreparer(app) }

    /** A fresh recorder per recording session (the Add sheet owns it; stop it in ON_STOP). */
    fun newVoiceRecorder(): AudioRecorder = VoiceRecorder(app)
}
