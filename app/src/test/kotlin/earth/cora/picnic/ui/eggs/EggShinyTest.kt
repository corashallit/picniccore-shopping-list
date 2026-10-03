package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.model.Rarity
import earth.cora.picnic.core.model.iconRef
import earth.cora.picnic.platform.Cue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Item 243 (egg 6): each committed item rolls 1 in 200 on the injected Random; a shiny row arrives holo with its
 * "shiny!" chip, an 8-sticker burst and "kira", and stays holo in its basket, in the book and in the album (a holo
 * pocket).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggShinyTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun oneIn200OnTheInjectedRandom() {
        // The engine's roll IS the injected Random's nextInt(200) == 0, draw for draw.
        val seed = 2026
        val rig = EngineRig(random = Random(seed))
        try {
            val reference = Random(seed)
            repeat(20_000) { i ->
                val expected = if (reference.nextInt(EggRules.SHINY_ODDS) == 0) Rarity.SHINY else Rarity.NONE
                assertEquals("roll $i", expected, rig.engine.rollRarity())
            }
        } finally {
            rig.close()
        }
        // And that is 1 in 200: 200 000 rolls on another seed give 1000 ± 10 %.
        val many = EngineRig(random = Random(11))
        try {
            val shiny = (1..200_000).count { many.engine.rollRarity() == Rarity.SHINY }
            assertEquals(1_000.0, shiny.toDouble(), 100.0)
            // Never a jackpot from a commit (that is egg 13's, for live drawings only).
            assertTrue((1..2_000).none { many.engine.rollRarity() == Rarity.JACKPOT })
        } finally {
            many.close()
        }
    }

    @Test
    fun theLabCanForceTheNextOne() {
        val rig = EngineRig(random = Random(1))
        try {
            rig.engine.forceShinyNext = true
            assertEquals(Rarity.SHINY, rig.engine.rollRarity())
            // Only the next one: then the injected Random decides again, from where it was (the forced one drew nothing).
            val reference = Random(1)
            repeat(500) {
                val expected = if (reference.nextInt(EggRules.SHINY_ODDS) == 0) Rarity.SHINY else Rarity.NONE
                assertEquals(expected, rig.engine.rollRarity())
            }
        } finally {
            rig.close()
        }
    }

    @Test
    fun aShinyRowIsHoloInTheRowBasketBookAndAlbum() {
        val r = EggRig(compose).start()
        val kiraBefore = r.feedback.count(Cue.MAGIC_APPEAR)
        val t0 = r.fire(6)
        val maple = r.list.items.last { it.name == "Maple syrup" }
        assertEquals(Rarity.SHINY, maple.rarity)
        // The row: holo with its chip, an 8-sticker burst and "kira".
        assertEquals(listOf(maple.id), r.eggs.shinyBursts.map { it.itemId })
        assertEquals(kiraBefore + 1, r.feedback.count(Cue.MAGIC_APPEAR))
        r.advanceTo(t0 + 1_000)
        assertTrue("the shiny! chip shows on its row", r.anchors.isLaidOut(EggAnchors.TEXT_PREFIX + "chip:" + maple.id))
        r.advanceTo(t0 + 2_000)
        assertTrue("the burst has played out", r.eggs.shinyBursts.isEmpty())

        // Checked: it hops into its basket holo, its twin in the book is holo, and the album pockets it holo.
        r.act { r.model.toggle(maple.id) }
        r.advance(2_000)
        val breakfast = r.model.grouped().sections.single { it.id == "breakfast" }
        val mini = minis(breakfast, pixel = false).single { it.itemId == maple.id }
        assertTrue("holo in its basket", mini.holo)
        val checked = r.list.items.single { it.id == maple.id }
        assertTrue(checked.checked)
        assertNotNull("it has its spot in the book", checked.bookSlot)
        assertEquals("the book draws the same holo sticker", Rarity.SHINY, checked.rarity)
        val key = IconCatalog.default.collectKey(checked)!!
        val pocket = runBlocking { r.sandbox.album.current() }.collected[key]
        assertNotNull("collected into the album", pocket)
        assertTrue("a holo pocket", pocket!!.holo)
        assertEquals(maple.iconRef().key, checked.iconRef().key)
    }

    @Test
    fun withEggsOffNothingIsShinyButWhatWasStaysHolo() {
        val rig = EngineRig(settings = earth.cora.picnic.settings.AppSettings(eggs = false), random = Random(0))
        try {
            assertTrue((1..5_000).all { rig.engine.rollRarity() == Rarity.NONE })
            // The fixture's maple syrup was pulled shiny before: it stays shiny (rarity belongs to the item, §2.5).
            assertEquals(Rarity.SHINY, rig.list.items.single { it.id == "maple" }.rarity)
            assertFalse(rig.eggs.enabled)
        } finally {
            rig.close()
        }
    }
}
