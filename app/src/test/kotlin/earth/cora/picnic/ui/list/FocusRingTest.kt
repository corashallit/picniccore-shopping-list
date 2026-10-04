package earth.cora.picnic.ui.list

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.model.setOpenAisle
import earth.cora.picnic.ui.common.PHONE_360
import earth.cora.picnic.ui.theme.BookTags
import earth.cora.picnic.ui.theme.DayColors
import earth.cora.picnic.ui.theme.GlowColors
import earth.cora.picnic.ui.theme.LocalPieceProbe
import earth.cora.picnic.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Keyboard and switch access on the list (docs/DESIGN_STICKER.md §4.4, §15; items 58, 287): a focused row wears the
 * 2 dp ring 3 dp outside its 48 dp checkbox target, a focused strip the same ring round its face, the open tray's
 * chevron round its round face, and the sticker book round its outline, all in the page's ink (ballpoint in Day,
 * white in Glow). Moving focus with the keyboard moves the ring: the pixels at the old ring go back to what they
 * were, and the ring's ink appears at the new one. After a tap (touch mode) nothing is ringed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class FocusRingTest {
    @get:Rule
    val compose = createComposeRule()

    private val dp get() = compose.density.density

    private var input: InputModeManager? = null

    private fun show(rig: ListRig, probe: RecordingProbe, variant: ThemeVariant = ThemeVariant.Day) {
        compose.setContent {
            input = LocalInputModeManager.current
            CompositionLocalProvider(LocalPieceProbe provides probe) { rig.Themed(variant) }
        }
        compose.waitForIdle()
    }

    private fun shot(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

    private fun near(a: Int, b: Int, tol: Int): Boolean =
        abs(((a shr 16) and 0xff) - ((b shr 16) and 0xff)) <= tol &&
            abs(((a shr 8) and 0xff) - ((b shr 8) and 0xff)) <= tol &&
            abs((a and 0xff) - (b and 0xff)) <= tol

    /**
     * Pixels in [bmp] within [inner, outer] px of [center], on the circle's left and right arcs (within [halfBand] px of
     * the centre's height, so a neighbour's ring above or below is never counted), that are the ring's [ink].
     */
    private fun inkOnCircle(bmp: Bitmap, center: Offset, inner: Float, outer: Float, ink: Int, halfBand: Float = outer * 0.7f): Int {
        var n = 0
        val r = outer.toInt() + 1
        for (y in (center.y.toInt() - r)..(center.y.toInt() + r)) for (x in (center.x.toInt() - r)..(center.x.toInt() + r)) {
            if (x !in 0 until bmp.width || y !in 0 until bmp.height) continue
            if (abs(y + 0.5f - center.y) > halfBand) continue
            val d = hypot(x + 0.5f - center.x, y + 0.5f - center.y)
            if (d in inner..outer && near(bmp.getPixel(x, y), ink, 40)) n++
        }
        return n
    }

    /**
     * Pixels in [bmp] in the bands [out1, out2] px left and right of [rect] (the sides of the ring round a face, level
     * with it, so a neighbour's ring above or below is never counted) that are the ring's [ink].
     */
    private fun inkAround(bmp: Bitmap, rect: Rect, out1: Float, out2: Float, ink: Int): Int {
        var n = 0
        val o = out2.toInt() + 1
        for (y in rect.top.toInt()..rect.bottom.toInt()) for (x in (rect.left.toInt() - o)..(rect.right.toInt() + o)) {
            if (x !in 0 until bmp.width || y !in 0 until bmp.height) continue
            val px = x + 0.5f
            val d = maxOf(rect.left - px, px - rect.right)
            if (d in out1..out2 && near(bmp.getPixel(x, y), ink, 40)) n++
        }
        return n
    }

    private fun focused(tag: String): Boolean =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true

    private fun keyboard() = compose.runOnIdle { input!!.requestInputMode(InputMode.Keyboard) }

    /** The ring round a row's 48 dp target: its centre line 28 dp out (24 + 3 + half of 2), drawn 27–29 dp out. */
    private fun rowRing(bmp: Bitmap, probe: RecordingProbe, id: String, ink: Int): Int =
        inkOnCircle(bmp, probe["check:$id"].center, 26.6f * dp, 29.4f * dp, ink)

    private fun rowsMoveTheRing(variant: ThemeVariant, ink: Int) {
        val rig = ListRig()
        val probe = RecordingProbe()
        show(rig, probe, variant)
        val open = rig.vm.state.value.sections.first { it.id == rig.vm.state.value.openSectionId }
        val (a, b) = open.rows.take(2).map { it.id }
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("header:${open.id}")
        compose.waitForIdle()
        val rest = shot()
        val restA = rowRing(rest, probe, a, ink)
        val restB = rowRing(rest, probe, b, ink)

        // The keyboard focuses the first row: its ring appears round the checkbox target.
        keyboard()
        compose.onNodeWithTag(ListTags.row(a)).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        assertTrue("$variant: $a has focus", focused(ListTags.row(a)))
        val onA = shot()
        val ringA = rowRing(onA, probe, a, ink) - restA
        assertTrue("$variant: a ring of ink round $a's target ($ringA px)", ringA > 400)
        assertTrue("$variant: none round $b yet", rowRing(onA, probe, b, ink) - restB < 60)

        // Tab moves focus to the next row: the ring goes with it.
        compose.onNodeWithTag(ListTags.row(a)).performKeyInput { pressKey(Key.Tab) }
        compose.waitForIdle()
        assertTrue("$variant: Tab moved focus to $b", focused(ListTags.row(b)))
        val onB = shot()
        assertTrue("$variant: the ring left $a (${rowRing(onB, probe, a, ink) - restA} px)", rowRing(onB, probe, a, ink) - restA < 60)
        val ringB = rowRing(onB, probe, b, ink) - restB
        assertTrue("$variant: the ring is round $b ($ringB px)", ringB > 400)
    }

    @Test
    fun rowsMoveTheRingDay() = rowsMoveTheRing(ThemeVariant.Day, DayColors.ink.toArgb())

    @Test
    fun rowsMoveTheRingGlow() = rowsMoveTheRing(ThemeVariant.Glow, GlowColors.ink.toArgb())

    /** Strips: the ring 3 dp outside the strip face (drawn 3–5 dp out), moved strip to strip by Tab. */
    @Test
    fun stripsMoveTheRing() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, ""))
        val probe = RecordingProbe()
        show(rig, probe)
        val ink = DayColors.ink.toArgb()
        val (a, b) = rig.vm.state.value.sections.take(2).map { it.id }
        fun ring(bmp: Bitmap, id: String) = inkAround(bmp, compose.boundsOf(ListTags.strip(id)), 3.2f * dp, 4.8f * dp, ink)
        val rest = shot()
        keyboard()
        compose.onNodeWithTag(ListTags.strip(a)).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        assertTrue("$a has focus", focused(ListTags.strip(a)))
        val onA = shot()
        assertTrue("a ring round $a (${ring(onA, a) - ring(rest, a)} px)", ring(onA, a) - ring(rest, a) > 300)
        compose.onNodeWithTag(ListTags.strip(a)).performKeyInput { pressKey(Key.Tab) }
        compose.waitForIdle()
        assertTrue("Tab moved focus to $b", focused(ListTags.strip(b)))
        val onB = shot()
        assertTrue("the ring left $a", ring(onB, a) - ring(rest, a) < 100)
        assertTrue("a ring round $b (${ring(onB, b) - ring(rest, b)} px)", ring(onB, b) - ring(rest, b) > 300)
    }

    /**
     * The open tray's chevron: the ring 3 dp outside its 30 dp face. The sticker book: focusable by the keyboard, ringed
     * round its outline, and Enter opens the Trip book sheet (§7.7).
     */
    @Test
    fun chevronAndBookWearTheRing() {
        val rig = ListRig()
        val probe = RecordingProbe()
        show(rig, probe)
        val ink = DayColors.ink.toArgb()
        val open = rig.vm.state.value.openSectionId!!
        compose.onNodeWithTag(ListTags.LIST).performScrollToKey("header:$open")
        compose.waitForIdle()
        val chevron = compose.boundsOf(ListTags.chevron(open))
        // The face is centred 1 dp up and left of the target's centre; the ring's centre line is 15 + 4 = 19 dp out.
        val face = chevron.center - Offset(dp, dp)
        fun chevronRing(bmp: Bitmap) = inkOnCircle(bmp, face, 17.6f * dp, 20.4f * dp, ink)
        val book = compose.boundsOf(BookTags.BOOK)
        fun bookRing(bmp: Bitmap) = inkAround(bmp, Rect(book.left, book.top, book.right, book.top + 60 * dp), 3.2f * dp, 4.8f * dp, ink)
        val rest = shot()
        keyboard()
        compose.onNodeWithTag(ListTags.chevron(open)).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        val onChevron = shot()
        assertTrue("a ring round the chevron (${chevronRing(onChevron) - chevronRing(rest)} px)", chevronRing(onChevron) - chevronRing(rest) > 250)
        assertTrue("not round the book", bookRing(onChevron) - bookRing(rest) < 100)

        compose.onNodeWithTag(BookTags.BOOK).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        assertTrue("the book takes keyboard focus", focused(BookTags.BOOK))
        val onBook = shot()
        assertTrue("the ring left the chevron", chevronRing(onBook) - chevronRing(rest) < 60)
        assertTrue("a ring round the book (${bookRing(onBook) - bookRing(rest)} px)", bookRing(onBook) - bookRing(rest) > 300)
        compose.onNodeWithTag(BookTags.BOOK).performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertTrue("Enter opens the Trip book sheet", compose.exists(TripBookTags.SHEET))
    }

    /** Touch mode: a focused strip (a fold hands focus to it, §8.5) wears no ring; the ring is the keyboard's cursor. */
    @Test
    fun noRingInTouchMode() {
        val rig = ListRig(ListFixture.state().setOpenAisle(ListFixture.LIST_ID, ""))
        val probe = RecordingProbe()
        show(rig, probe)
        val a = rig.vm.state.value.sections.first().id
        compose.runOnIdle { input!!.requestInputMode(InputMode.Touch) }
        val rest = shot()
        compose.onNodeWithTag(ListTags.strip(a)).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        assertTrue("$a has focus", focused(ListTags.strip(a)))
        assertEquals("touch mode: focus draws nothing", InputMode.Touch, input!!.inputMode)
        assertTrue("touch mode: the strip looks as it did", rest.sameAs(shot()))
    }
}
