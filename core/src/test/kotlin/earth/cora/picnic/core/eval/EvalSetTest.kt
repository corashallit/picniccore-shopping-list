package earth.cora.picnic.core.eval

import earth.cora.picnic.core.TestSupport
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.BasketKind
import earth.cora.picnic.core.model.IconFit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Offline validation of the eval set: every reference resolves, and each expectation matches its own phrases. */
class EvalSetTest {
    private val file = EvalFile.load()
    private val catalog = TestSupport.catalog

    @Test
    fun hasEnoughCasesWithUniqueIds() {
        assertTrue(file.cases.size >= 25, "about 25 cases")
        assertEquals(file.cases.size, file.cases.map { it.id }.toSet().size, "unique ids")
        CaseKind.entries.forEach { kind -> assertTrue(file.cases.any { it.kind == kind }, "a $kind case") }
    }

    @Test
    fun storesResolve() {
        assertEquals(Aisles.DEFAULT_TEXT, file.store("ontario14").aislesText, "ontario14 is the default store")
        file.stores.forEach { (key, store) ->
            val ids = store.aisles.map { it.id }.toSet()
            store.aliases.forEach { (alias, id) -> assertTrue(id in ids, "store $key: alias $alias -> unknown aisle id $id (have $ids)") }
            assertEquals(ids, store.aliases.values.toSet(), "store $key: every aisle has an alias")
        }
    }

    @Test
    fun everyReferenceIsValid() {
        val basketKeys = BasketKind.entries.map { it.key }.toSet()
        for (case in file.cases) {
            val store = file.store(case.store)
            val expects = case.expect + case.names
            for (e in expects) {
                e.aisle.forEach { store.resolve(it) }
                e.icons.forEach { assertTrue(it in catalog, "${case.id}: icon $it must be a catalog key") }
                e.qty?.let { Regex(it.removePrefix("re:")) }
                e.detail?.let { Regex(it) }
                (e.match + e.exclude).forEach { Phrase.regex(it) }
                assertTrue(!e.many || e.optional, "${case.id}: 'many' is for optional expectations only")
            }
            val correctionStore = if (case.kind == CaseKind.AISLE_EDIT) file.store(requireNotNull(case.newStore)) else store
            case.corrections.values.forEach { assertTrue(correctionStore.resolve(it) != null, "${case.id}: correction to a real aisle") }
            (case.forbidden + case.transcript + case.message.mustNot).forEach { Phrase.regex(it) }
            when (case.kind) {
                CaseKind.TEXT -> assertTrue(!case.input.isNullOrBlank(), "${case.id}: input")
                CaseKind.PHOTO, CaseKind.VOICE -> assertTrue(EvalResources.exists(requireNotNull(case.file)), "${case.id}: ${case.file} on the test classpath")
                CaseKind.AISLE_EDIT -> {
                    val new = file.store(requireNotNull(case.newStore))
                    assertTrue(case.items.isNotEmpty())
                    case.items.forEach { store.resolve(it.from); it.aisle.forEach(new::resolve) }
                    case.baskets.forEach { (alias, keys) ->
                        new.resolve(alias)
                        assertTrue(keys.all { it in basketKeys }, "${case.id}: basket keys $keys")
                    }
                }
                CaseKind.ICONS -> assertTrue(case.names.all { it.name != null && it.scoresIcon }, "${case.id}: every name has an icon expectation")
            }
        }
    }

    /** A perfect answer built from the expectations themselves must score 100%: catches typos and conflicting excludes. */
    @Test
    fun idealAnswersScorePerfectly() {
        for (case in file.cases.filter { it.kind.isIngest }) {
            val store = file.store(case.store)
            val ideal = case.expect.filter { !it.optional }.map { e ->
                val phrase = e.match.first { !it.startsWith("re:") }
                val (icon, fit) = if (e.icons.isNotEmpty()) e.icons.first() to IconFit.EXACT else "paper_bag" to IconFit.APPROXIMATE
                Produced(name = phrase, aisleId = e.aisle.firstOrNull()?.let(store::resolve), icon = icon, fit = fit)
            }
            val score = EvalScoring.scoreIngest(case, store, ideal, message = "", webSearches = 0)
            assertEquals(score.requiredTotal, score.requiredMatched, "${case.id}: misses ${score.misses}")
            assertEquals(score.produced, score.producedMatched, "${case.id}: extras ${score.extras}")
            assertEquals(score.aisleTotal, score.aisleOk, "${case.id}: ${score.wrong}")
            assertEquals(score.iconTotal, score.iconOk, "${case.id}: ${score.wrong}")
            assertTrue(score.checks.none { it.what == "no forbidden items" && !it.ok }, "${case.id}: an expected phrase is also forbidden")
        }
    }
}
