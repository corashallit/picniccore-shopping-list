package earth.cora.picnic.ui.list

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.toggleChecked
import earth.cora.picnic.core.model.addItems
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.common.PHONE_360
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Scratch: dumps frames for a visual check (not committed). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class ScratchFramesTest {
    @get:Rule
    val compose = createComposeRule()

    private val out = File(System.getProperty("scratch.dir") ?: "/tmp/claude-0/-home-user-picniccore-shopping-list/0abd3c0d-8466-5393-83a4-67568bc936fe/scratchpad/frames").apply { mkdirs() }

    private fun save(name: String) {
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun foldPinned() {
        var s = ListFixture.longAisle()
        for (id in listOf("dish-soap", "cat-food", "toothpaste", "paper-towels", "laundry", "sponges", "garbage-bags", "foil", "batteries")) {
            s = s.toggleChecked(ListFixture.LIST_ID, id, ListFixture.TRIP_START + 30 * ListFixture.MIN)
        }
        val rig = ListRig(s)
        compose.show(rig)
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("tissues")
        compose.waitForIdle()
        save("f1_000_before")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.check("tissues"), useUnmergedTree = true).performClick()
        var t = 0L
        val marks = listOf(600L, 1000L, 1400L, 1640L, 1720L, 1736L, 1752L, 1800L, 1880L, 1950L, 2050L, 2300L, 2600L)
        for (m in marks) {
            compose.advanceMs(m - t)
            t = m
            save("f1_t$m")
        }
        compose.mainClock.autoAdvance = true
    }

    private fun longProduce(): earth.cora.picnic.core.model.AppState {
        var s = ListFixture.state()
        val extra = listOf("Blueberries" to "blueberries", "Lemons" to "lemon", "Avocados" to "avocado", "Red onions" to "onion", "Baby spinach" to "spinach", "Limes" to "lime")
        extra.forEachIndexed { i, (n, icon) ->
            val item = earth.cora.picnic.core.model.GroceryItem(id = "x$i", name = n, quantity = "1 bag", aisleId = "produce", icon = icon, iconFit = earth.cora.picnic.core.model.IconFit.EXACT, addedAt = 1_790_722_800_000L + 40 + i)
            s = s.addItemsFor(item)
        }
        for (id in listOf("strawberries", "apples", "x0", "x1", "x2", "x3", "x4")) s = s.toggleChecked(ListFixture.LIST_ID, id, ListFixture.TRIP_START + 30 * ListFixture.MIN)
        return s.setOpenAisleFor("produce")
    }

    private fun earth.cora.picnic.core.model.AppState.addItemsFor(item: earth.cora.picnic.core.model.GroceryItem) =
        addItems(ListFixture.LIST_ID, listOf(item), item.addedAt).first

    private fun earth.cora.picnic.core.model.AppState.setOpenAisleFor(id: String) = setOpenAisle(ListFixture.LIST_ID, id)

    private fun run(name: String, marks: List<Long>, act: () -> Unit) {
        compose.mainClock.autoAdvance = false
        act()
        var t = 0L
        for (m in marks) {
            compose.advanceMs(m - t)
            t = m
            save("${name}_t$m")
        }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun foldPinnedProduce() {
        val rig = ListRig(longProduce())
        compose.show(rig)
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("x5")
        compose.waitForIdle()
        compose.onNodeWithTag(ListTags.LIST).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, -330f) }
        compose.waitForIdle()
        save("f1b_000_before")
        run("f1b", listOf(1736L, 1950L, 2000L, 2050L, 2100L, 2150L, 2200L, 2250L, 2300L, 2350L, 2400L, 2500L, 2600L)) {
            compose.onNodeWithTag(ListTags.check("x5"), useUnmergedTree = true).performClick()
        }
    }

    @Test
    fun foldPinnedProduceTrace() {
        val rig = ListRig(longProduce())
        compose.show(rig)
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("x5")
        compose.waitForIdle()
        compose.onNodeWithTag(ListTags.LIST).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, -330f) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.check("x5"), useUnmergedTree = true).performClick()
        val sb = StringBuilder()
        for (i in 0 until 200) {
            compose.frames(1)
            val t = i * 16
            if (t < 1700) continue
            fun b(tag: String) = if (compose.exists(tag)) compose.boundsOf(tag).let { "%.0f..%.0f".format(it.top, it.bottom) } else "-"
            sb.append("t=$t eng=${rig.engine.time} header=${b(ListTags.header("bakery"))} sour=${b(ListTags.rowSlot("sourdough"))} crois=${b(ListTags.rowSlot("croissants"))} cap=${b(ListTags.cap("bakery"))} deli=${b(ListTags.strip("deli-meat"))} prod=${b(ListTags.strip("produce"))}\n")
        }
        File(out, "trace.txt").writeText(sb.toString())
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun tearUndo() {
        val s = ListFixture.state().toggleChecked(ListFixture.LIST_ID, "sourdough", ListFixture.TRIP_START + 7 * ListFixture.MIN).setOpenAisleFor("bakery")
        val rig = ListRig(s)
        compose.show(rig)
        val item = rig.item("baguette")
        val index = rig.app.items().indexOfFirst { it.id == "baguette" }
        save("f2_000_before")
        run("f2", listOf(300L, 700L, 900L, 1100L, 1300L, 1700L, 2100L, 2500L, 3200L)) {
            compose.onNodeWithTag(ListTags.row("baguette")).performTouchInputLeft()
        }
        run("f2u", listOf(16L, 100L, 200L, 320L, 500L, 800L, 1200L, 1600L)) {
            compose.runOnIdle { rig.vm.undoDelete(item, index, ListFixture.LIST_ID) }
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performTouchInputLeft() = performTouchInput {
        down(center)
        repeat(4) { moveBy(androidx.compose.ui.geometry.Offset(-20 * density, 0f)) }
        up()
    }

    @Test
    fun reducedNext() {
        val rig = ListRig(ListFixture.state())
        compose.show(rig, reducedMotion = true)
        run("f11", listOf(1900L, 1916L, 1932L, 1948L, 1964L, 1980L, 1996L, 2012L, 2100L)) {
            compose.onNodeWithTag(ListTags.check("baking-powder"), useUnmergedTree = true).performClick()
        }
    }

    @Test
    fun undoDelete() {
        val s = ListFixture.state().toggleChecked(ListFixture.LIST_ID, "sourdough", ListFixture.TRIP_START + 7 * ListFixture.MIN).setOpenAisleFor("bakery")
        val rig = ListRig(s)
        compose.show(rig)
        val item = rig.item("baguette")
        val index = rig.app.items().indexOfFirst { it.id == "baguette" }
        run("u0", listOf(1000L, 2400L)) { compose.runOnIdle { rig.vm.delete("baguette") } }
        val sb = StringBuilder()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { rig.vm.undoDelete(item, index, ListFixture.LIST_ID) }
        for (i in 0 until 60) {
            compose.frames(1)
            fun b(tag: String) = if (compose.exists(tag)) compose.boundsOf(tag).let { "%.0f..%.0f".format(it.top, it.bottom) } else "-"
            sb.append("i=$i header=${b(ListTags.header("bakery"))} bag=${b(ListTags.rowSlot("baguette"))} sour=${b(ListTags.rowSlot("sourdough"))} bstrip=${b(ListTags.strip("bakery"))} baking=${b(ListTags.strip("aisle-2-baking"))} scroll=${rig.engine.current.value.scroll}\n")
            if (i % 6 == 0) save("u1_$i")
        }
        File(out, "trace2.txt").writeText(sb.toString())
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun tearTrace() {
        val rig = ListRig(ListFixture.state().setOpenAisleFor("produce"))
        compose.show(rig)
        val sb = StringBuilder()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.row("apples")).performTouchInputLeft()
        for (i in 0 until 110) {
            compose.frames(1)
            fun b(tag: String) = if (compose.exists(tag)) compose.boundsOf(tag).let { "%.0f..%.0f".format(it.top, it.bottom) } else "-"
            sb.append("i=$i apples=${b(ListTags.rowSlot("apples"))} carrots=${b(ListTags.rowSlot("carrots"))} header=${b(ListTags.header("produce"))} gone=${rig.app.items().none { it.id == "apples" }}\n")
            if (i in listOf(50, 60, 70, 80, 90, 100)) save("tt_$i")
        }
        File(out, "trace3.txt").writeText(sb.toString())
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun fs200Strips() {
        val state = ListFixture.state()
            .toggleChecked(ListFixture.LIST_ID, "baking-powder", ListFixture.TRIP_START + 20 * ListFixture.MIN)
            .setOpenAisleFor("")
        val rig = ListRig(state)
        compose.show(rig, fontScale = 2f)
        compose.waitForIdle()
        save("fs200_a")
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("strip:frozen")
        compose.waitForIdle()
        save("fs200_b")
    }
}
