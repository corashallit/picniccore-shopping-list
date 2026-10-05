package earth.cora.picnic.ui.pantry

import earth.cora.picnic.core.assistant.IconDecider
import earth.cora.picnic.core.assistant.NamedItem
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.localIcon
import earth.cora.picnic.core.model.normalizeName
import earth.cora.picnic.core.model.setPantryIcon
import earth.cora.picnic.core.assistant.resolve
import earth.cora.picnic.core.store.StateStore
import earth.cora.picnic.platform.PicnicLog
import earth.cora.picnic.ui.theme.gen.StickerManifest

/*
 * Pantry stickers (docs/DESIGN_STICKER.md §6.13, checklist 328): the learned icon override for the name, else the local
 * alias match (both through core's localIcon), else, with a key, the IconDecider's answer (ONE batched call per commit
 * of the screen; the capsule while it waits), else the category generic, else the capsule (D5). Pantry entries never
 * start a live drawing, and nothing calls the network without a key. A commit made offline leaves its unknown entries
 * on the capsule; the catch-up when the connection comes back decides them ([decidePending]). Owner: WP8 (PantryIconsTest).
 */
object PantryIcons {
    /** What a shelf sticker shows: its resolved sticker, or the gashapon capsule while it has none. */
    fun display(icon: IconRef?): IconRef = icon ?: IconRef(StickerManifest.CAPSULE)

    /** Step 1, offline: every unresolved entry with a learned override or a local match gets that sticker. */
    fun resolveLocally(state: AppState, catalog: IconCatalog): AppState =
        state.pantry.filter { it.icon == null }.fold(state) { s, entry ->
            val key = s.localIcon(entry.name, catalog) ?: return@fold s
            s.setPantryIcon(normalizeName(entry.name), IconRef(key))
        }

    /** The entries step 2 would ask about: still unresolved after [resolveLocally] (id = normalized name). */
    fun unresolved(state: AppState): List<NamedItem> =
        state.pantry.filter { it.icon == null }.map { NamedItem(id = normalizeName(it.name), name = it.name) }.distinctBy { it.id }

    /**
     * Step 2: one IconDecider call for [names] (it batches internally), mapped to stickers: a match is its key; "none" is
     * the category generic, or the capsule when the category is unknown (stored, so it is not asked again). Any LlmError
     * leaves them unresolved for the next commit. Never draws.
     */
    suspend fun decide(decider: IconDecider, names: List<NamedItem>, catalog: IconCatalog): Map<String, IconRef> {
        if (names.isEmpty()) return emptyMap()
        val decisions = try {
            decider.decide(names)
        } catch (e: LlmError) {
            PicnicLog.w("pantry stickers undecided", e)
            return emptyMap()
        }
        return names.associate { named ->
            val decision = decisions[named.id]
            val ref = when {
                decision == null -> IconRef(StickerManifest.CAPSULE)
                else -> {
                    val resolved = decision.resolve(catalog)
                    when {
                        resolved.fit == IconFit.EXACT -> IconRef(resolved.icon)
                        decision.category != null && resolved.icon != IconCatalog.DEFAULT_ICON -> IconRef(resolved.icon)
                        else -> IconRef(StickerManifest.CAPSULE)
                    }
                }
            }
            named.id to ref
        }
    }

    /**
     * The catch-up (docs/DESIGN_STICKER.md §0.4, 5 October): the entries still without a sticker (a commit made offline, or
     * one whose call failed) get step 2's answer now, in one call, as a commit of the screen would. The caller checks the
     * key and the network; an LlmError leaves them for the next time.
     */
    suspend fun decidePending(store: StateStore, decider: IconDecider, catalog: IconCatalog) {
        val unknown = unresolved(store.current())
        if (unknown.isEmpty()) return
        val decided = decide(decider, unknown, catalog)
        if (decided.isNotEmpty()) store.update { apply(it, decided) }
    }

    /** Applies step 2's answers to the entries that are still unresolved (a sticker she picked meanwhile wins). */
    fun apply(state: AppState, decided: Map<String, IconRef>): AppState =
        decided.entries.fold(state) { s, (key, ref) ->
            if (s.pantry.any { normalizeName(it.name) == key && it.icon == null }) s.setPantryIcon(key, ref) else s
        }
}
