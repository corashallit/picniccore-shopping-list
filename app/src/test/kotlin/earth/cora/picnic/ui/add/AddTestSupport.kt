package earth.cora.picnic.ui.add

import androidx.lifecycle.SavedStateHandle
import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.platform.FakeFeedback
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.FakePhotos
import earth.cora.picnic.ui.common.FakeRecorder
import earth.cora.picnic.ui.common.RecordingIconRepository
import earth.cora.picnic.ui.eggs.SurpriseEngine
import earth.cora.picnic.ui.eggs.SurpriseState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A SurpriseEngine that records what the add flows tell it (eggs 6, 7 and the commit hooks of 3, 9, 17). */
class RecordingSurprises(
    private val magicWords: Set<String> = setOf("kirakira", "kira kira", "kira-kira", "キラキラ", "きらきら"),
    /** Rarities handed out in turn by [rollRarity]; NONE once used up. */
    rarities: List<Rarity> = emptyList(),
) : SurpriseEngine {
    private val queue = ArrayDeque(rarities)
    val intercepted = mutableListOf<String>()
    val committed = mutableListOf<List<GroceryItem>>()
    var rolls = 0

    override val state: StateFlow<SurpriseState> = MutableStateFlow(SurpriseState())

    override fun interceptText(text: String): Boolean {
        intercepted += text
        return text.trim().trimEnd('!').lowercase() in magicWords
    }

    override fun rollRarity(): Rarity {
        rolls++
        return queue.removeFirstOrNull() ?: Rarity.NONE
    }

    override fun onCommitted(items: List<GroceryItem>) {
        committed += items
    }

    override fun onSunTap() = Unit
    override fun onMoonTap() = Unit
    override fun onStampTap(position: Int) = Unit
    override fun onLcdHold() = Unit
    override fun onBasketHold(sectionId: String, heldMs: Long) = Unit
    override fun onShake() = Unit
    override fun onIconDrawn(item: GroceryItem): Rarity = Rarity.NONE
}

/** One add-flows ViewModel with its fakes. */
class AddRig(
    val vm: AddViewModel,
    val store: InMemoryStateStore,
    val assistant: FakeAssistant,
    val icons: RecordingIconRepository,
    val photos: FakePhotos,
    val recorder: FakeRecorder,
    val surprises: RecordingSurprises,
    val feedback: FakeFeedback,
)

/** Builds the ViewModel over [state] with fakes; the clock reads [now] and ids count up. */
fun addRig(
    appScope: CoroutineScope,
    state: AppState = AddFixtures.state,
    assistant: FakeAssistant = FakeAssistant(),
    hasKey: Boolean = true,
    surprises: RecordingSurprises = RecordingSurprises(),
    recorder: FakeRecorder = FakeRecorder(),
    savedState: SavedStateHandle = SavedStateHandle(),
    now: () -> Long = { AddFixtures.NOW },
    /** File reads run here; Unconfined keeps them on the test's scheduler (Dispatchers.IO would race advanceUntilIdle). */
    io: CoroutineDispatcher = Dispatchers.Unconfined,
): AddRig {
    val store = InMemoryStateStore(state)
    val icons = RecordingIconRepository()
    val photos = FakePhotos()
    val feedback = FakeFeedback()
    var next = 0
    val vm = AddViewModel(
        store = store,
        assistant = assistant,
        icons = icons,
        aisleSync = AisleSync(store, assistant, IconCatalog.default.baskets, now),
        catalog = IconCatalog.default,
        photos = photos,
        recorderFactory = { recorder },
        clock = now,
        newId = { "new-${next++}" },
        appScope = appScope,
        savedState = savedState,
        hasKey = { hasKey },
        surprises = surprises,
        feedback = feedback,
        thumbs = { null },
        ioDispatcher = io,
    )
    vm.showList(AddFixtures.LIST_ID)
    return AddRig(vm, store, assistant, icons, photos, recorder, surprises, feedback)
}
