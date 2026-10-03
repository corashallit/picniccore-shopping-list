package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.list
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.platform.Cue
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
import kotlin.math.PI
import kotlin.math.abs

/**
 * Items 240 and 335 (egg 3): a commit with a `roll` or `hop` item (also in French) sends its sticker from the add bar
 * to its aisle's basket, bumps it (the basket wobbles hello, "pff"), and settles it on its line, never in the basket;
 * the produce spins with its travel, θ = Δx ⁄ r (r = 22 dp), clockwise moving right, anticlockwise moving left, and
 * keeps its angle while it only drops.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggRollingTest {
    @get:Rule
    val compose = createComposeRule()

    private val px = 3f // xxhdpi

    /** Adds [name] to Produce (opened first, as the lab does) and returns the commit's frame time. */
    private fun EggRig.addProduce(name: String, icon: String? = null): Long {
        act {
            model.set { it.setOpenAisle(model.listId, "produce") }
            val c = earth.cora.picnic.core.catalog.IconCatalog.default
            model.add(
                listOf(
                    earth.cora.picnic.core.model.GroceryItem(
                        id = "t-$name", name = name, aisleId = "produce", icon = icon ?: c.match(name)!!.key,
                        addedAt = clock.now(), source = earth.cora.picnic.core.model.ItemSource.TYPED,
                    ),
                ),
            )
        }
        return now
    }

    @Test
    fun rollsToItsBasketAndSettlesOnItsLine() {
        val r = EggRig(compose).start()
        val gottenBefore = r.model.grouped().sections.single { it.id == "produce" }.gotten.size
        val t0 = r.addProduce("Lemons")
        val fx = r.eggs.rolling
        assertNotNull(fx)
        assertFalse("lemons roll", fx!!.hop)
        assertEquals("produce", fx.sectionId)
        // The row's own sticker hides while its twin travels.
        assertTrue(r.eggs.hidesRowSticker(fx.itemId))
        // The bump at the basket: it wobbles hello and says "pff" (LAND).
        r.advanceTo(t0 + RollingMotion.BUMP_AT + 50)
        assertEquals("produce", r.eggs.basketHello?.sectionId)
        assertTrue(r.feedback.times(Cue.LAND).any { it >= t0 + RollingMotion.BUMP_AT - 20 })
        // Settled on its line by 3.2 s: the effect is over, the item is still to get, the basket holds what it held.
        r.advanceTo(t0 + 3_200)
        assertNull(r.eggs.rolling)
        val row = r.list.items.single { it.name == "Lemons" }
        assertFalse(row.checked)
        val produce = r.model.grouped().sections.single { it.id == "produce" }
        assertEquals("it never enters the basket", gottenBefore, produce.gotten.size)
        assertTrue(produce.rows.any { it.id == row.id })
        // Its last pose sat on its row's sticker.
        val last = r.probe.frames.last { it.tag == "roller" }
        val spot = r.anchor(earth.cora.picnic.ui.list.motion.AnchorRegistry.rowKey(row.id))
        assertEquals(spot.center.x, last.center.x, 4f * px)
        assertEquals(spot.center.y, last.center.y, 4f * px)
    }

    @Test
    fun berriesHop() {
        val r = EggRig(compose).start()
        r.addProduce("Raspberries")
        assertTrue(r.eggs.rolling!!.hop)
    }

    @Test
    fun frenchNamesMatchByKey() {
        val e = EngineRig()
        try {
            e.engine.onListShown(e.listId)
            val pommes = e.item("p1", "pommes", "produce")
            assertEquals("apple", pommes.icon)
            e.engine.onCommitted(listOf(pommes))
            assertNotNull(e.eggs.rolling)
            e.engine.effectDone(e.eggs.rolling!!.id)
            val fraises = e.item("f1", "fraises", "produce")
            e.engine.onCommitted(listOf(fraises))
            assertTrue(e.eggs.rolling!!.hop)
            e.engine.effectDone(e.eggs.rolling!!.id)
            // One per commit; a plain item rolls nothing.
            e.engine.onCommitted(listOf(e.item("b1", "Bread", "bakery", "sliced_bread")))
            assertNull(e.eggs.rolling)
        } finally {
            e.close()
        }
    }

    @Test
    fun spinMatchesTravel() {
        val r = EggRig(compose).start()
        r.probe.clear()
        val t0 = r.addProduce("Lemons")
        r.advanceTo(t0 + RollingMotion.DURATION + 100)
        val frames = r.probe.frames.filter { it.tag == "roller" }.distinctBy { it.time }.sortedBy { it.time }
        assertTrue("sampled the roll", frames.size > 20)
        val radius = RollingMotion.RADIUS_DP * px
        var right = 0
        var left = 0
        var drops = 0
        frames.zipWithNext { a, b ->
            val dx = b.center.x - a.center.x
            val dTheta = b.rotation - a.rotation
            val expected = (dx / radius * 180.0 / PI).toFloat()
            assertEquals("θ = Δx ⁄ r between ${a.time} and ${b.time} ms", expected, dTheta, 5f)
            when {
                dx > 0.5f -> {
                    right++
                    assertTrue("clockwise while it moves right", dTheta > 0f)
                }
                dx < -0.5f -> {
                    left++
                    assertTrue("anticlockwise while it moves left", dTheta < 0f)
                }
                abs(b.center.y - a.center.y) > 0.5f -> {
                    drops++
                    assertEquals("it keeps its angle while it only drops", 0f, dTheta, 0.01f)
                }
            }
        }
        assertTrue("it rolled right", right > 3)
        assertTrue("it rolled left", left > 3)
        assertTrue("it dropped", drops > 0)
        // One full turn per 2πr = 138 dp.
        assertEquals(138.2f, (2 * PI * RollingMotion.RADIUS_DP).toFloat(), 0.1f)
    }

    @Test
    fun reducedMotionAddsItNormally() {
        val r = EggRig(compose, reduced = true).start()
        r.addProduce("Lemons")
        assertNull(r.eggs.rolling)
        assertTrue(r.list.items.any { it.name == "Lemons" })
    }

}
