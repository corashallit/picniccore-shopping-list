package earth.cora.picnic.ui.common

import earth.cora.picnic.core.assistant.AudioClip
import earth.cora.picnic.core.assistant.CallMeta
import earth.cora.picnic.core.assistant.ConnectionCheck
import earth.cora.picnic.core.assistant.DrawnIcon
import earth.cora.picnic.core.assistant.GroceryAssistant
import earth.cora.picnic.core.assistant.IconDrawRequest
import earth.cora.picnic.core.assistant.IngestContext
import earth.cora.picnic.core.assistant.IngestInput
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.assistant.ProposedItem
import earth.cora.picnic.core.assistant.Transcript
import earth.cora.picnic.core.assistant.WebSource
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.Correction
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.SectionIds
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.icons.IconRepository
import earth.cora.picnic.platform.AudioRecorder
import earth.cora.picnic.platform.PhotoPreparer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.IOException

/**
 * The app scope for ViewModel tests: foreground work on the test scheduler, so advanceUntilIdle()
 * waits for commits (backgroundScope work is not waited for).
 */
fun TestScope.testAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

/** Swaps Dispatchers.Main (viewModelScope) for a test dispatcher; runTest then shares its scheduler. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = kotlinx.coroutines.Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = kotlinx.coroutines.Dispatchers.resetMain()
}

/** GroceryAssistant with a scripted answer per call; unset calls fail loudly. */
class FakeAssistant(
    var ingest: suspend (IngestInput, IngestContext) -> IngestResult = { _, _ -> error("ingest not scripted") },
    var transcribe: suspend (AudioClip) -> Transcript = { error("transcribe not scripted") },
    var recategorize: suspend (List<NamedItem>, List<Aisle>) -> Map<String, String?> = { items, _ -> items.associate { it.id to null } },
    var assignBaskets: suspend (List<Aisle>) -> Map<String, BasketKind> = { emptyMap() },
    var testConnection: suspend () -> ConnectionCheck = { ConnectionCheck("gpt-5.6-terra", meta(412)) },
) : GroceryAssistant {
    val ingested = mutableListOf<Pair<IngestInput, IngestContext>>()
    val transcribed = mutableListOf<Pair<AudioClip, List<String>>>()
    val recategorized = mutableListOf<List<NamedItem>>()
    var tests = 0

    override suspend fun ingest(input: IngestInput, context: IngestContext): IngestResult {
        ingested += input to context
        return ingest.invoke(input, context)
    }

    override suspend fun transcribe(clip: AudioClip, keywords: List<String>): Transcript {
        transcribed += clip to keywords
        return transcribe.invoke(clip)
    }

    override suspend fun recategorize(items: List<NamedItem>, aisles: List<Aisle>, corrections: List<Correction>): Map<String, String?> {
        recategorized += items
        return recategorize.invoke(items, aisles)
    }

    override suspend fun assignBaskets(aisles: List<Aisle>): Map<String, BasketKind> = assignBaskets.invoke(aisles)

    override suspend fun drawIcon(request: IconDrawRequest): DrawnIcon = error("drawIcon not expected in screen tests")

    override suspend fun testConnection(): ConnectionCheck {
        tests++
        return testConnection.invoke()
    }

    companion object {
        fun meta(latencyMs: Long = 900) = CallMeta(model = "gpt-5.6-terra", requestId = "req_test", latencyMs = latencyMs)

        fun result(
            items: List<ProposedItem>,
            message: String = "",
            sources: List<WebSource> = emptyList(),
            skipped: List<String> = emptyList(),
        ) = IngestResult(items, message, sources, skipped, meta())
    }
}

/** Records what the screens hand to the icon cascade; [busy] can be driven by the test. */
class RecordingIconRepository(busyNames: Set<String> = emptySet()) : IconRepository {
    val busyFlow = MutableStateFlow(busyNames)
    override val busy: StateFlow<Set<String>> = busyFlow
    val submitted = mutableListOf<List<GroceryItem>>()
    val redrawn = mutableListOf<String>()
    val forgotten = mutableListOf<String>()

    override fun isBundled(key: String): Boolean = false
    override fun submit(items: List<GroceryItem>) {
        submitted += items
    }
    override fun resumePending() = Unit
    override fun redraw(itemName: String) {
        redrawn += itemName
    }
    override fun forgetBespoke(itemName: String) {
        forgotten += itemName
    }
}

/** A scripted recorder: [levels] are returned in turn; [stopResult] decides what stop() yields. */
class FakeRecorder(
    private val levels: List<Float> = listOf(0.2f, 0.6f, 0.9f, 0.4f),
    var stopResult: () -> File? = { null },
    private val failOnStart: Boolean = false,
) : AudioRecorder {
    var started = 0
    var stopped = 0
    var cancelled = 0
    var limitReached = false
    private var i = 0

    override fun start(): File {
        if (failOnStart) throw IOException("mic busy")
        started++
        return File("unused.m4a")
    }

    override fun level(): Float = levels[i++ % levels.size]

    override fun stop(): File? {
        stopped++
        return stopResult()
    }

    override fun reachedLimit(): Boolean = limitReached

    override fun cancel() {
        cancelled++
    }
}

class FakePhotos(var bytes: () -> ByteArray = { byteArrayOf(1, 2, 3) }) : PhotoPreparer {
    val discarded = mutableListOf<android.net.Uri>()
    override suspend fun jpegBytes(uri: android.net.Uri): ByteArray = bytes()
    override suspend fun discard(uri: android.net.Uri) {
        discarded += uri
    }
}

/** Realistic sample data (the default Ontario store) for ViewModel and screenshot tests. */
object Samples {
    const val LIST_ID = "list-groceries"
    const val PARTY_ID = "list-party"
    const val NOW = 1_759_330_000_000L

    fun item(
        id: String,
        name: String,
        icon: String,
        aisleId: String?,
        quantity: String? = null,
        note: String? = null,
        checked: Boolean = false,
        fit: IconFit = IconFit.EXACT,
        source: ItemSource = ItemSource.AI_TEXT,
        at: Long = NOW,
        customIconId: String? = null,
    ) = GroceryItem(
        id = id, name = name, quantity = quantity, note = note, aisleId = aisleId, icon = icon, iconFit = fit,
        customIconId = customIconId, checked = checked, addedAt = at, source = source,
    )

    /** A week's shop in mid-trip: unsorted items, a collapsed aisle, a cleared aisle, a drawing and checked items. */
    val shoppingItems: List<GroceryItem> = listOf(
        item("i-lemons", "Lemons", "lemon", "produce", quantity = "2"),
        item("i-spinach", "Baby spinach", "spinach", "produce", quantity = "1 clamshell"),
        item("i-avocado", "Avocados", "avocado", "produce", quantity = "3", note = "ripe ones"),
        item("i-baguette", "Baguette", "baguette", "bakery", note = "crusty", checked = true),
        item("i-bagels", "Bagels", "bagel", "bakery", quantity = "6", checked = true),
        item("i-coffee", "Coffee beans", "coffee", "aisle-1-breakfast-coffee-tea", quantity = "340 g", checked = true),
        item("i-flour", "All-purpose flour", "flour", "aisle-2-baking-spices", quantity = "2 kg"),
        item("i-bp", "Baking powder", "baking_generic", "aisle-2-baking-spices", fit = IconFit.APPROXIMATE),
        item("i-gochujang", "Gochujang", "condiments_generic", "aisle-3-pasta-rice-international", quantity = "500 g tub", note = "for the bibimbap", fit = IconFit.APPROXIMATE),
        item("i-milk", "Milk", "milk_bag", "dairy-eggs", quantity = "4 L bag"),
        item("i-eggs", "Eggs", "eggs", "dairy-eggs", quantity = "1 dozen"),
        item("i-yogurt", "Greek yogurt", "yogurt", "dairy-eggs", quantity = "750 g"),
        item("i-butter", "Butter", "butter", "dairy-eggs", note = "unsalted"),
        item("i-rack", "Dish rack", "paper_bag", null, fit = IconFit.PENDING, source = ItemSource.TYPED),
        item("i-candles", "Birthday candles", "candle", null, source = ItemSource.TYPED),
    )

    val shoppingState: AppState = AppState(
        lists = listOf(
            GroceryList(LIST_ID, "Groceries", NOW, shoppingItems, collapsedAisles = setOf("dairy-eggs")),
            GroceryList(
                PARTY_ID, "Saturday party", NOW,
                listOf(
                    item("p-chips", "Tortilla chips", "tortilla_chips", "aisle-5-snacks-drinks"),
                    item("p-salsa", "Salsa", "salsa", "aisle-3-pasta-rice-international"),
                ),
            ),
        ),
        currentListId = LIST_ID,
        store = StoreProfile(name = "No Frills on Bank St"),
    )

    val emptyState: AppState = AppState(
        lists = listOf(GroceryList(LIST_ID, "Groceries", NOW)),
        currentListId = LIST_ID,
        store = StoreProfile(name = "No Frills on Bank St"),
    )

    /** Busy names for the drawing sparkle in screenshots. */
    val busy: Set<String> = setOf("gochujang", "dish rack")

    val collapsedChecked: Set<String> = setOf(SectionIds.CHECKED)
}
