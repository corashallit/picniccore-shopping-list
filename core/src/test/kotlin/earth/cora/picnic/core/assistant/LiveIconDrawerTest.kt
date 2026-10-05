package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.Correction
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class LiveIconDrawerTest {
    private class FakeDecider(var answer: (List<NamedItem>) -> Map<String, IconDecision>) : IconDecider {
        val calls = mutableListOf<List<NamedItem>>()
        override suspend fun decide(items: List<NamedItem>): Map<String, IconDecision> {
            calls += items
            return answer(items)
        }
    }

    private class FakeAssistant(val results: ArrayDeque<() -> DrawnIcon>) : GroceryAssistant {
        val requests = mutableListOf<IconDrawRequest>()
        override suspend fun drawIcon(request: IconDrawRequest): DrawnIcon {
            requests += request
            return results.removeFirst()()
        }
        override suspend fun ingest(input: IngestInput, context: IngestContext) = error("unused")
        override suspend fun transcribe(clip: AudioClip, keywords: List<String>) = error("unused")
        override suspend fun recategorize(items: List<NamedItem>, aisles: List<Aisle>, corrections: List<Correction>) = error("unused")
        override suspend fun assignBaskets(aisles: List<Aisle>): Map<String, BasketKind> = error("unused")
        override suspend fun testConnection() = error("unused")
    }

    private val drawn = DrawnIcon(byteArrayOf(1), meta = CallMeta(model = "m", requestId = "r", latencyMs = 1))
    private val request = IconDrawRequest("Kimchi", byteArrayOf(1), byteArrayOf(2), "one jar of kimchi", listOf("strawberry", "melon"))
    private val kimchi = IconDecision(null, "pantry", "one jar of kimchi", listOf("strawberry", "melon"))

    @Test
    fun aDecisionWithLookAndFamiliesIsUsedAsItIs() = runTest {
        val decider = FakeDecider { error("not asked") }
        val drawer = LiveIconDrawer(FakeAssistant(ArrayDeque()), decider)
        assertSame(kimchi, drawer.brief("kimchi", "Kimchi", kimchi))
        assertTrue(decider.calls.isEmpty())
    }

    @Test
    fun withoutALookTheDeciderIsAskedAboutTheOneName() = runTest {
        val decider = FakeDecider { items -> mapOf(items.single().id to kimchi) }
        val drawer = LiveIconDrawer(FakeAssistant(ArrayDeque()), decider)
        assertEquals(kimchi, drawer.brief("kimchi", "Kimchi", IconDecision(null, "pantry")))
        assertEquals(listOf(NamedItem("kimchi", "Kimchi")), decider.calls.single())
        assertEquals(kimchi, drawer.brief("kimchi", "Kimchi", null))
    }

    @Test
    fun aDeciderFailureFallsBackToWhatWasKnown() = runTest {
        val decider = FakeDecider { throw LlmError.Network(java.io.IOException("offline")) }
        val drawer = LiveIconDrawer(FakeAssistant(ArrayDeque()), decider)
        val known = IconDecision(null, "pantry")
        assertSame(known, drawer.brief("kimchi", "Kimchi", known))
        assertNull(drawer.brief("kimchi", "Kimchi", null))
    }

    @Test
    fun aTransientFailureIsRetriedOnceAfterTheDelay() = runTest {
        val sleeps = mutableListOf<Duration>()
        val assistant = FakeAssistant(ArrayDeque(listOf({ throw LlmError.RateLimited(null) }, { drawn })))
        val drawer = LiveIconDrawer(assistant, FakeDecider { emptyMap() }, retryDelay = 3.seconds, sleep = { sleeps += it })
        val outcome = drawer.draw(request)
        assertIs<DrawOutcome.Drawn>(outcome)
        assertSame(drawn, outcome.icon)
        assertEquals(listOf(3.seconds), sleeps)
        assertEquals(2, assistant.requests.size)
    }

    @Test
    fun atMostTwoRequestsPerDrawing() = runTest {
        val assistant = FakeAssistant(ArrayDeque(listOf({ throw LlmError.Timeout() }, { throw LlmError.Network(java.io.IOException("x")) })))
        val outcome = LiveIconDrawer(assistant, FakeDecider { emptyMap() }, sleep = {}).draw(request)
        assertIs<LlmError.Network>(assertIs<DrawOutcome.Failed>(outcome).error)
        assertEquals(2, assistant.requests.size)
    }

    @Test
    fun moderationIsARefusalAndOtherErrorsAreNotRetried() = runTest {
        val refused = FakeAssistant(ArrayDeque(listOf({ throw LlmError.Refusal("blocked") })))
        assertSame(DrawOutcome.Refused, LiveIconDrawer(refused, FakeDecider { emptyMap() }, sleep = {}).draw(request))
        assertEquals(1, refused.requests.size)
        val bad = FakeAssistant(ArrayDeque(listOf({ throw LlmError.BadResponse("nope") })))
        assertIs<DrawOutcome.Failed>(LiveIconDrawer(bad, FakeDecider { emptyMap() }, sleep = {}).draw(request))
        assertEquals(1, bad.requests.size)
    }

    @Test
    fun thePaletteMatchesTheSpecAndNormalizes() {
        assertEquals(10, StickerPalette.FAMILIES.size)
        val kimchi = StickerPalette.family("Strawberry")!!
        assertEquals(listOf("#FFC4D1", "#FF6F8E", "#C3264F", "#EC587A"), kimchi.tones)
        assertEquals(listOf("sakura", "melon"), StickerPalette.normalize(listOf(" Sakura", "sakura", "neutral", "melon", "cocoa")))
        assertEquals(StickerPalette.DEFAULT_FAMILIES, StickerPalette.normalize(emptyList()))
        // deep = mid mixed 32 % toward trace (§2.2), ±1 per channel.
        StickerPalette.FAMILIES.forEach { f ->
            fun rgb(h: String) = listOf(1, 3, 5).map { h.substring(it, it + 2).toInt(16) }
            val expected = rgb(f.mid).zip(rgb(f.trace)) { m, t -> m + (t - m) * 0.32 }
            rgb(f.deep).zip(expected).forEach { (a, e) -> assertTrue(kotlin.math.abs(a - e) <= 1.0, "${f.name} deep") }
        }
    }

    @Test
    fun theFallbackSubjectNamesTheItem() {
        assertEquals("one Dragon fruit, as it is sold in a Canadian grocery store", LiveIconDrawer.fallbackSubject(" Dragon\nfruit "))
    }
}
