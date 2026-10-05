package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.ui.add.ADD_FIELD_TAG
import earth.cora.picnic.ui.list.motion.AnchorRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 257 (egg 17): at most once a day (§0.4), when the list holds three or more UNCHECKED distinct `picnic` keys and the
 * keyboard is closed, five ants march right to left along the add bar's perforation (the middle one, 30 dp, carrying a
 * strawberry; the others 22 dp), 21 two-frame steps over 4.2 s, 0.42 s apart, never across the text field.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggAntsTest {
    @get:Rule
    val compose = createComposeRule()

    private val catalog = IconCatalog.default
    private val list = PracticeFixture.LIST_ID

    private fun item(id: String, name: String, icon: String) =
        GroceryItem(id = id, name = name, icon = icon, addedAt = PracticeFixture.NOW, source = ItemSource.TYPED)

    @Test
    fun threeUncheckedPicnicKeysOncePerDay() {
        // The fixture has two unchecked picnic things (strawberries, baguette): no ants.
        val repo = earth.cora.picnic.settings.InMemorySettingsRepository(earth.cora.picnic.settings.AppSettings(eggs = true, weather = false, sounds = false))
        val e = EngineRig(repo = repo)
        try {
            assertFalse(EggRules.antsWanted(e.list, catalog))
            e.engine.onListShown(e.listId)
            assertNull(e.eggs.antsId)
            // A third: they march.
            e.store.apply { it.addItems(list, listOf(item("w", "Watermelon", "watermelon")), PracticeFixture.NOW).first }
            e.engine.onCommitted(listOf(item("w", "Watermelon", "watermelon")))
            val first = e.eggs.antsId
            assertNotNull(first)
            e.engine.effectDone(first!!)
            // Never twice in one process, whatever happens next.
            e.store.apply { it.addItems(list, listOf(item("l", "Lemonade", "lemonade")), PracticeFixture.NOW).first }
            e.engine.onCommitted(listOf(item("l", "Lemonade", "lemonade")))
            e.engine.onListShown(e.listId)
            assertNull(e.eggs.antsId)
        } finally {
            e.close()
        }
        // Android ends the process between most openings: a new one the same day stays quiet (the day is persisted),
        // and the next day they march again.
        val withPicnic = PracticeFixture.state().addItems(list, listOf(item("w", "Watermelon", "watermelon")), PracticeFixture.NOW).first
        val sameDay = EngineRig(state = withPicnic, repo = repo, clock = FakeClock(PracticeFixture.NOW + 3 * 60 * 60 * 1000L))
        try {
            sameDay.engine.onListShown(sameDay.listId)
            assertNull("a new process the same day", sameDay.eggs.antsId)
        } finally {
            sameDay.close()
        }
        val nextDay = EngineRig(state = withPicnic, repo = repo, clock = FakeClock(PracticeFixture.NOW + 24 * 60 * 60 * 1000L))
        try {
            nextDay.engine.onListShown(nextDay.listId)
            assertNotNull("the next day", nextDay.eggs.antsId)
        } finally {
            nextDay.close()
        }
    }

    @Test
    fun checkedOnesAndDuplicatesDoNotCount() {
        val s = PracticeFixture.state().addItems(
            list,
            listOf(item("w", "Watermelon", "watermelon"), item("s2", "More strawberries", "strawberry")),
            PracticeFixture.NOW,
        ).first.toggleChecked(list, "w", PracticeFixture.NOW + 1)
        // Unchecked picnic keys: strawberry (twice) and baguette: two distinct keys.
        assertFalse(EggRules.antsWanted(s.list(list), catalog))
    }

    @Test
    fun notWhileTheKeyboardIsOpen() {
        val s = PracticeFixture.state().addItems(list, listOf(item("w", "Watermelon", "watermelon")), PracticeFixture.NOW).first
        val e = EngineRig(state = s)
        try {
            e.engine.onImeVisible(true)
            e.engine.onListShown(e.listId)
            assertNull("the keyboard is open", e.eggs.antsId)
            e.engine.onImeVisible(false)
            assertNotNull("they set off once it closes", e.eggs.antsId)
        } finally {
            e.close()
        }
    }

    /**
     * On the list itself (EggListHost's keyboard observer): she types three picnic things into the add bar and sends
     * them with the keyboard still up: no ants while it shows; they set off once it closes.
     */
    @Test
    fun notWhileTheKeyboardIsUpOnTheList() {
        val r = EggRig(compose)
        r.ime.value = false
        r.start()
        // The field focused, the keyboard reported up.
        compose.onNodeWithTag(ADD_FIELD_TAG).performClick()
        r.act { r.ime.value = true }
        compose.onNodeWithTag(ADD_FIELD_TAG).performTextInput("watermelon, lemonade, potato chips")
        r.frame()
        compose.onNodeWithTag(ADD_FIELD_TAG).performImeAction()
        r.advance(300)
        assertTrue("three picnic things are on the list", EggRules.antsWanted(r.list, catalog))
        assertNull("no ants with the keyboard up", r.eggs.antsId)
        r.advance(1_000)
        assertNull(r.eggs.antsId)
        // The keyboard closes: they march.
        r.act { r.ime.value = false }
        r.advance(100)
        assertNotNull("the ants set off once it closes", r.eggs.antsId)
    }

    @Test
    fun theyMarchAlongThePerforationClearOfTheField() {
        val r = EggRig(compose).start()
        r.probe.clear()
        val t0 = r.fire(17)
        assertNotNull(r.eggs.antsId)
        r.advanceTo(t0 + AntMotion.DURATION + 100)
        assertNull(r.eggs.antsId)
        val bar = r.anchor(AnchorRegistry.ADD_BAR)
        val field = compose.onNodeWithTag(ADD_FIELD_TAG).fetchSemanticsNode().boundsInRoot
        val frames = r.probe.frames.filter { it.tag.startsWith("ant") }
        assertEquals(AntMotion.COUNT, frames.map { it.tag }.distinct().size)
        for (i in 0 until AntMotion.COUNT) {
            val mine = frames.filter { it.tag == "ant$i" }.distinctBy { it.time }.sortedBy { it.time }
            // Right to left, never backwards; feet on the perforation (the bar's top edge).
            mine.zipWithNext { a, b -> assertTrue("ant$i walks left", b.center.x <= a.center.x) }
            mine.forEach { f ->
                assertTrue("ant$i on the perforation", f.bounds.bottom in bar.top - 3f * 3..bar.top + 8f * 3)
                assertFalse("ant$i never crosses the text field", f.bounds.deflate(2f).overlaps(field))
            }
            // Its own start: 0.42 s after the one before it.
            assertEquals((i * AntMotion.APART_MS).toDouble(), mine.first().time.toDouble(), 40.0)
            // 21 steps in its 4.2 s.
            assertEquals(AntMotion.STEPS, mine.map { it.center.x }.distinct().size)
        }
        // The middle one carries the strawberry and is the big one.
        val sizes = (0 until AntMotion.COUNT).map { AntMotion.sizeDp(it) }
        assertEquals(listOf(22f, 22f, 30f, 22f, 22f), sizes)
        assertTrue(AntMotion.carriesStrawberry(2))
    }

    @Test
    fun notUnderReducedMotion() {
        val r = EggRig(compose, reduced = true).start()
        r.fire(17)
        r.advance(1_000)
        assertNull(r.eggs.antsId)
        assertTrue(r.probe.frames.none { it.tag.startsWith("ant") })
    }
}
