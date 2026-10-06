package earth.cora.picnic.core.assistant

import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.llm.unreached
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The ten sticker families (docs/DESIGN_STICKER.md §2.2): pastel = body fill, mid = accents and the puffy band,
 * deep = mid mixed 32 % toward trace, trace = outline. Live stickers v4 are drawn in two of them plus white and
 * lemon pastel: the IconDecider names them, the prompt lists their colours and the device snaps every pixel to
 * them (the app's LiveStickerProcessor). Stickers never change colour with the theme. The neutral "Everything
 * else" family is not a sticker family.
 */
object StickerPalette {
    data class Family(val name: String, val pastel: String, val mid: String, val deep: String, val trace: String) {
        /** pastel, mid, trace, deep: the order the snap palette lists them in. */
        val tones: List<String> get() = listOf(pastel, mid, trace, deep)
    }

    val FAMILIES: List<Family> = listOf(
        Family("strawberry", "#FFC4D1", "#FF6F8E", "#EC587A", "#C3264F"),
        Family("peach", "#FFDCC6", "#FFA477", "#E5885A", "#AE4D1C"),
        Family("lemon", "#FFF3A6", "#FFDA4A", "#DAB632", "#8A6800"),
        Family("melon", "#D3F4C5", "#8EDB76", "#6FBF62", "#2E8239"),
        Family("mint", "#C9F2E3", "#6DD3B0", "#53B594", "#1C7558"),
        Family("ramune", "#CCEBFF", "#7CC6F5", "#5EA9DB", "#1E6AA3"),
        Family("periwinkle", "#DCE1FF", "#9AA8F5", "#7D8ADF", "#3E4BB0"),
        Family("grape", "#E4D6FF", "#B597F2", "#9B7CDE", "#6542B3"),
        Family("sakura", "#FFD6E8", "#FF9FC8", "#E67BAA", "#B02E6B"),
        Family("cocoa", "#F1DEC9", "#CB9C73", "#B1825B", "#7A4A28"),
    )

    val NAMES: List<String> = FAMILIES.map { it.name }

    const val WHITE: String = "#FFFFFF"

    /** Lemon pastel: the one detail colour every sticker may use (seeds, highlights), whatever its families. */
    const val LEMON_PASTEL: String = "#FFF3A6"

    /** Used when nothing names a known family (the app passes the approximate key's families first). */
    val DEFAULT_FAMILIES: List<String> = listOf("periwinkle")

    private val byName: Map<String, Family> = FAMILIES.associateBy { it.name }

    fun family(name: String): Family? = byName[name.trim().lowercase()]

    /** Known names only, lower-cased, distinct, at most two, body first; none known -> [DEFAULT_FAMILIES]. */
    fun normalize(families: List<String>): List<String> =
        families.map { it.trim().lowercase() }.filter { it in byName }.distinct().take(2).ifEmpty { DEFAULT_FAMILIES }
}

/** The result of one [LiveIconDrawer.draw]. */
sealed interface DrawOutcome {
    class Drawn(val icon: DrawnIcon) : DrawOutcome

    /** Image moderation refused the item: never draw this name automatically again. */
    data object Refused : DrawOutcome

    /**
     * Gave up (after the one retry for a rate limit, a network error or a timeout). [reached]: some attempt got to
     * OpenAI (anything but [LlmError.Offline] or a failure to connect), so it may have been billed; only a drawing that
     * never reached it gives its daily slot back (docs/ARCHITECTURE.md §8.3, §8.4).
     */
    class Failed(val error: LlmError, val reached: Boolean = !error.unreached) : DrawOutcome
}

/**
 * Live stickers v4, the network half (docs/DESIGN_STICKER.md §18.3 step 5). The app half composes the neighbour
 * sheet (SheetComposer) and post-processes the result (LiveStickerProcessor); this class:
 *
 * - [brief]: the subject line and the two families to draw in. The IconDecider gives them with every "none"
 *   ([IconDecision.look], [IconDecision.families]); an item that reached the drawing another way (ingest's
 *   "approximate", a redraw, a legacy icon) is asked about once more (one name, ~US$0.0002).
 * - [draw]: one drawIcon call, retried ONCE after [retryDelay] on RateLimited / Network / Timeout (imageEdit itself
 *   never retries, so a drawing is billed at most twice); a moderation refusal is [DrawOutcome.Refused].
 *
 * Main-safe and cancellable; never throws LlmError (it is folded into the outcome).
 */
class LiveIconDrawer(
    private val assistant: GroceryAssistant,
    private val decider: IconDecider,
    private val retryDelay: Duration = 3.seconds,
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) {
    /**
     * [known] when it already carries a look and families, else the decider's answer for [name] ([key] is its
     * normalized name). On an LlmError the answer is [known] (or null): the caller falls back to the plain
     * subject and the approximate key's families. A decision whose [IconDecision.icon] is set means the catalog
     * does have the item after all; the caller may use it instead of drawing.
     */
    suspend fun brief(key: String, name: String, known: IconDecision?): IconDecision? {
        if (known != null && !known.look.isNullOrBlank() && known.families.isNotEmpty()) return known
        return try {
            decider.decide(listOf(NamedItem(key, name)))[key] ?: known
        } catch (e: CancellationException) {
            throw e
        } catch (e: LlmError) {
            known
        }
    }

    /** One drawing with at most one retry (see the class comment). */
    suspend fun draw(request: IconDrawRequest): DrawOutcome {
        var retried = false
        var reached = false
        while (true) {
            try {
                return DrawOutcome.Drawn(assistant.drawIcon(request))
            } catch (e: CancellationException) {
                throw e
            } catch (e: LlmError.Refusal) {
                return DrawOutcome.Refused
            } catch (e: LlmError) {
                reached = reached || !e.unreached
                val transient = e is LlmError.RateLimited || e is LlmError.Network || e is LlmError.Timeout
                if (!transient || retried) return DrawOutcome.Failed(e, reached)
                retried = true
                sleep(retryDelay)
            }
        }
    }

    companion object {
        /** The plain subject when no look is known: "one Kimchi, as it is sold in a Canadian grocery store". */
        fun fallbackSubject(name: String): String =
            "one ${Prompts.oneLine(name, 80).ifEmpty { "grocery item" }}, as it is sold in a Canadian grocery store"
    }
}
