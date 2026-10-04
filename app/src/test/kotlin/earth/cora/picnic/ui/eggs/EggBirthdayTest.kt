package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.platform.Cue
import earth.cora.picnic.ui.theme.HankoText
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
 * Item 246 (egg 9): a commit with a cake, candles, a "birthday…", "gâteau d'anniversaire" or "bougies" item rains 26
 * confetti stickers for 2 s (jingle), and that item's hanko reads HAPPY / B-DAY! whenever it is checked; under reduced
 * motion there is no confetti but the hanko still reads HAPPY / B-DAY!.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggBirthdayTest {
    @get:Rule
    val compose = createComposeRule()

    private val catalog = IconCatalog.default

    private fun item(name: String, icon: String = catalog.match(name)?.key ?: "paper_bag") =
        GroceryItem(id = name, name = name, icon = icon, addedAt = PracticeFixture.NOW, source = ItemSource.TYPED)

    @Test
    fun whatCountsAsABirthday() {
        val yes = listOf(
            item("Birthday cake", "cake"),
            item("Carrot cake", "cake"),
            item("Candles", "candle"),
            item("Birthday balloons"),
            item("BIRTHDAY card"),
            item("Gâteau d'anniversaire"),
            item("gateau d’anniversaire"),
            item("Bougies"),
            item("bougies d'anniversaire"),
        )
        yes.forEach { assertTrue("${it.name} (${it.icon}) is a birthday item", EggRules.isBirthday(it, catalog)) }
        val no = listOf(item("Carrots"), item("Butter", "butter"), item("Cupcake liners", "paper_bag"), item("Gâteau aux carottes", "paper_bag"))
        no.forEach { assertFalse("${it.name} is not", EggRules.isBirthday(it, catalog)) }
        // The hanko follows: HAPPY / B-DAY! for a birthday item, GOT IT! for anything else, Easter eggs on.
        val on = SurpriseState()
        assertEquals(HankoText.HappyBday, on.hankoFor(yes.first().copy(checked = true), catalog))
        assertEquals(HankoText.GotIt, on.hankoFor(no.first().copy(checked = true), catalog))
        assertEquals(HankoText.GotIt, SurpriseState.Off.hankoFor(yes.first().copy(checked = true), catalog))
    }

    @Test
    fun confettiForTwoSecondsThenHappyBday() {
        val r = EggRig(compose).start()
        r.probe.clear()
        val t0 = r.fire(9)
        assertNotNull(r.eggs.confettiId)
        assertEquals("the jingle", 1, r.feedback.count(Cue.CELEBRATION))
        r.advanceTo(t0 + 1_000)
        assertNotNull("still raining at 1 s", r.eggs.confettiId)
        r.advanceTo(t0 + ConfettiMotion.RAIN_MS + 100)
        assertNull("gone after 2 s", r.eggs.confettiId)
        val pieces = r.probe.frames.filter { it.tag.startsWith("confetti") }
        assertEquals("26 confetti stickers", ConfettiMotion.COUNT, pieces.map { it.tag }.distinct().size)
        assertTrue("all within the 2 s", pieces.all { it.time < ConfettiMotion.RAIN_MS })
        // Each falls for 1.3–1.8 s.
        ConfettiMotion.pieces(ConfettiMotion.lanes(1080f, 3f, emptyList()), 3f).forEach { assertTrue(it.fallMs in 1_300L..1_800L) }
        // The lab checks it once the confetti has fallen: the hanko reads HAPPY / B-DAY!.
        r.advanceTo(t0 + 2_200 + 1_000)
        val cake = r.list.items.single { it.name == "Birthday cake" }
        assertTrue(cake.checked)
        assertEquals(HankoText.HappyBday, r.eggs.hankoFor(cake, catalog))
        // Every other checked row still reads GOT IT!.
        r.list.items.filter { it.checked && it.id != cake.id }.forEach { assertEquals(HankoText.GotIt, r.eggs.hankoFor(it, catalog)) }
    }

    @Test
    fun reducedMotionNoConfettiStillHappyBday() {
        val r = EggRig(compose, reduced = true).start()
        val t0 = r.fire(9)
        assertNull(r.eggs.confettiId)
        assertTrue(r.probe.frames.none { it.tag.startsWith("confetti") })
        r.advanceTo(t0 + 3_400)
        val cake = r.list.items.single { it.name == "Birthday cake" }
        assertTrue(cake.checked)
        assertEquals(HankoText.HappyBday, r.eggs.hankoFor(cake, catalog))
    }

    @Test
    fun oneRainPerCommit() {
        val e = EngineRig()
        try {
            e.engine.onListShown(e.listId)
            e.engine.onCommitted(listOf(item("Birthday cake", "cake"), item("Candles", "candle")))
            val first = e.eggs.confettiId
            assertNotNull(first)
            assertEquals(1, e.feedback.count(Cue.CELEBRATION))
            e.engine.onCommitted(listOf(item("Milk", "milk_bag")))
            assertEquals("a plain commit starts nothing", first, e.eggs.confettiId)
        } finally {
            e.close()
        }
    }
}

/**
 * Item 246's rain, as she sees it on the real List screen (§12.2 egg 9: "confetti stickers rain over the screen for
 * 2 s"): mid-rain at least 20 of the 26 pieces are on screen, every one wholly (never sliced by the screen's edge), and
 * none over a word; the strip `egg_9_strip_{day,glow}_<t>` at 0, 500, 1000 and 1500 ms.
 */
@RunWith(org.robolectric.ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ConfettiRainTest(private val variant: earth.cora.picnic.ui.theme.ThemeVariant) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rainsWholeOnScreenBesideTheWords() {
        val r = EggRig(compose, variant = variant).start()
        r.probe.clear()
        val t0 = r.fire(9)
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        for (t in listOf(0L, 500L, 1_000L, 1_500L)) {
            r.advanceTo(t0 + t)
            if (t == 1_000L) {
                val last = r.probe.frames.filter { it.tag.startsWith("confetti") }.groupBy { it.tag }.mapValues { it.value.last() }
                val now = last.values.filter { it.time >= last.values.maxOf { f -> f.time } - 20 }
                val whole = now.filter { f -> f.bounds.left >= screen.left && f.bounds.right <= screen.right && f.bounds.top >= screen.top && f.bounds.bottom <= screen.bottom }
                assertTrue("mid-rain: at least 20 pieces wholly on screen (${whole.size} of ${now.size})", whole.size >= 20)
                val words = wordBoxes(compose, r.anchors)
                now.forEach { f -> words.forEach { (w, box) -> assertFalse("confetti over \"$w\"", box.overlaps(f.bounds.deflate(2f))) } }
            }
            r.capture("egg_9_strip_${r.v}_$t")
        }
    }

    companion object {
        @JvmStatic
        @org.robolectric.ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = dayAndGlow()
    }
}
