package earth.cora.picnic.core.eval

import earth.cora.picnic.core.model.Aisle
import earth.cora.picnic.core.model.Aisles
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The LLM eval set (core/src/test/resources/eval/cases.json, docs/PROMPTS.md §10). Test-only: the
 * runner ([EvalMain]) calls the real API; [EvalSetTest] validates the file offline.
 */
@Serializable
data class EvalFile(
    val about: String = "",
    val stores: Map<String, EvalStore>,
    val cases: List<EvalCase>,
) {
    fun store(key: String): EvalStore = stores[key] ?: error("unknown store '$key'")

    companion object {
        const val RESOURCE = "eval/cases.json"
        private val json = Json { ignoreUnknownKeys = false }

        fun parse(text: String): EvalFile = json.decodeFromString(serializer(), text)

        fun load(): EvalFile = parse(EvalResources.text(RESOURCE))
    }
}

/** A store layout. [text] null = Aisles.DEFAULT_TEXT. [aliases] map short names used by cases to aisle ids. */
@Serializable
data class EvalStore(
    val about: String = "",
    val text: String? = null,
    val aliases: Map<String, String> = emptyMap(),
) {
    val aislesText: String get() = text ?: Aisles.DEFAULT_TEXT
    val aisles: List<Aisle> by lazy { Aisles.parse(aislesText) }

    /** Alias -> aisle id; "unsorted" -> null. Unknown alias -> error (EvalSetTest catches typos). */
    fun resolve(alias: String): String? =
        if (alias == UNSORTED) null else aliases[alias] ?: error("unknown aisle alias '$alias'")

    /** Aisle id -> alias for reports ("aisle-2-baking-spices" -> "baking"). */
    fun aliasOf(id: String?): String = if (id == null) UNSORTED else aliases.entries.firstOrNull { it.value == id }?.key ?: id

    companion object {
        const val UNSORTED = "unsorted"
    }
}

@Serializable
enum class CaseKind {
    @SerialName("text") TEXT,
    @SerialName("photo") PHOTO,
    @SerialName("voice") VOICE,
    @SerialName("aisle-edit") AISLE_EDIT,
    @SerialName("icons") ICONS,
    ;

    val isIngest: Boolean get() = this == TEXT || this == PHOTO || this == VOICE
}

@Serializable
enum class SearchExpectation {
    /** The model may or may not search. */
    @SerialName("any") ANY,

    /** A pasted link: the model must open it (at least one web_search call). */
    @SerialName("required") REQUIRED,

    /** A plain list or a well-known recipe: searching only adds latency and cost. */
    @SerialName("avoid") AVOID,
}

@Serializable
data class EvalCase(
    val id: String,
    val kind: CaseKind,
    val tags: List<String> = emptyList(),
    val about: String = "",
    /** TEXT input. */
    val input: String? = null,
    /** PHOTO / VOICE: a test-classpath resource path. */
    val file: String? = null,
    /** Store the list lives in (aliases in [expect] refer to it). */
    val store: String = "ontario14",
    /** AISLE_EDIT: the layout the user switches to (aliases in [items].aisle and [baskets] refer to it). */
    val newStore: String? = null,
    /** Unchecked names already on the list. */
    val existing: List<String> = emptyList(),
    /** Pantry entries as she wrote them (§6.13): the model leaves out worked-out items they cover. */
    val pantry: List<String> = emptyList(),
    /** Learned corrections: item name -> aisle alias (in the case's current store; newStore for AISLE_EDIT). */
    val corrections: Map<String, String> = emptyMap(),
    val search: SearchExpectation = SearchExpectation.ANY,
    /** Expected items (TEXT/PHOTO/VOICE). An empty list means "nothing should be added". */
    val expect: List<ItemExpect> = emptyList(),
    /** Phrases that must not appear as item names. */
    val forbidden: List<String> = emptyList(),
    val message: MessageExpect = MessageExpect(),
    /** VOICE: phrases the transcript must contain. */
    val transcript: List<String> = emptyList(),
    /** AISLE_EDIT: items placed in the old store, and where they belong in the new one. */
    val items: List<PlacedItem> = emptyList(),
    /** AISLE_EDIT: acceptable basket keys per new-store aisle alias (only aisles listed are scored). */
    val baskets: Map<String, List<String>> = emptyMap(),
    /** ICONS: typed names and the icons that depict them. */
    val names: List<ItemExpect> = emptyList(),
)

@Serializable
data class ItemExpect(
    /** Name phrases (any one matches); see [Phrase]. */
    val match: List<String> = emptyList(),
    /** ICONS cases: the typed name. */
    val name: String? = null,
    /** Phrases that disqualify a candidate name ("milk" must not take "Oat milk"). */
    val exclude: List<String> = emptyList(),
    /** Acceptable aisle aliases ("unsorted" allowed). Empty = not scored. */
    val aisle: List<String> = emptyList(),
    /** Catalog keys that depict the item (expected with fit EXACT). */
    val icons: List<String> = emptyList(),
    /** True when no catalog key depicts the item: an APPROXIMATE fit (live drawing) is also correct. */
    val approx: Boolean = false,
    /** Regex on the lowercased quantity (a null quantity fails). */
    val qty: String? = null,
    /** Regex on the loose (lowercased, accent-folded) "name | note | quantity". */
    val detail: String? = null,
    /** Allowed but not required (recipes have reasonable variations). */
    val optional: Boolean = false,
    /** Optional only: may absorb any number of produced items (e.g. several spices). */
    val many: Boolean = false,
    /** The skip_reason the matched row must carry ("none", "in_pantry", "optional"); null = not scored. */
    val skip: String? = null,
) {
    val scoresIcon: Boolean get() = icons.isNotEmpty() || approx
}

@Serializable
data class PlacedItem(
    val name: String,
    /** Old-store aisle alias. */
    val from: String,
    /** Acceptable new-store aisle aliases. */
    val aisle: List<String>,
    /** Placed by hand (moveItemToAisle) into [from]. */
    val manual: Boolean = false,
)

@Serializable
data class MessageExpect(
    val mustNot: List<String> = emptyList(),
    /** "fr" / "en": the message should be in French / English. */
    val lang: String? = null,
)

/** Test-classpath resources (the eval runs from the test runtime classpath). */
object EvalResources {
    fun bytes(path: String): ByteArray {
        val stream = EvalResources::class.java.getResourceAsStream("/$path") ?: error("eval resource $path missing")
        return stream.use { it.readBytes() }
    }

    fun text(path: String): String = bytes(path).decodeToString()

    fun exists(path: String): Boolean = EvalResources::class.java.getResource("/$path") != null
}
