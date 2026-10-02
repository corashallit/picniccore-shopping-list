package earth.cora.picnic.platform

import android.app.Application
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Checklist 290, service part (docs/DESIGN_STICKER.md §9, §15): "Time to take action" lengthens holds (never
 * shortens them), with FLAG_CONTENT_CONTROLS only for moments that carry an action; "Touch & hold delay" sets
 * the hold threshold max(440 ms, long-press timeout), and the ladybug fires 560 ms after it. The phone here has
 * "Time to take action" at 10 s and "Touch & hold delay" at 1.5 s.
 */
@RunWith(AndroidJUnit4::class)
class TimeoutsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val accessibility = shadowOf(app.getSystemService(AccessibilityManager::class.java))

    @Test
    fun atTheDefaultSettingHoldsAreUnchanged() {
        val timeouts = AndroidTimeouts(app)
        assertEquals(4000L, timeouts.recommended(4000, controls = true)) // Undo toast
        assertEquals(2600L, timeouts.recommended(2600, controls = false)) // stamp name tag
        assertEquals(6000L, timeouts.recommended(6000, controls = true)) // nudge
    }

    @Test
    fun timeToTakeActionAtTenSeconds() {
        accessibility.setInteractiveUiTimeout(10_000)
        accessibility.setNonInteractiveUiTimeout(10_000)
        val timeouts = AndroidTimeouts(app)
        assertEquals(10_000L, timeouts.recommended(4000, controls = true)) // Undo toast
        assertEquals(10_000L, timeouts.recommended(2600, controls = true)) // Next card
        assertEquals(10_000L, timeouts.recommended(2600, controls = false)) // stamp name tag
        assertEquals(12_000L, timeouts.recommended(12_000, controls = true)) // never shorter than the hold
    }

    @Test
    fun onlyMomentsWithAnActionAskForTheInteractiveTimeout() {
        accessibility.setInteractiveUiTimeout(10_000)
        accessibility.setNonInteractiveUiTimeout(0)
        val timeouts = AndroidTimeouts(app)
        assertEquals(10_000L, timeouts.recommended(4000, controls = true))
        assertEquals(4000L, timeouts.recommended(4000, controls = false))

        accessibility.setInteractiveUiTimeout(0)
        accessibility.setNonInteractiveUiTimeout(7000)
        assertEquals(7000L, timeouts.recommended(4000, controls = true)) // text is always part of the moment
        assertEquals(7000L, timeouts.recommended(2600, controls = false))
    }

    @Test
    fun settingsAreReadLive() {
        val timeouts = AndroidTimeouts(app)
        assertEquals(4000L, timeouts.recommended(4000, controls = true))
        accessibility.setInteractiveUiTimeout(10_000)
        assertEquals(10_000L, timeouts.recommended(4000, controls = true))

        var longPress = 400
        val live = AndroidTimeouts(app) { longPress }
        assertEquals(440L, live.holdThresholdMs)
        longPress = 1500
        assertEquals(1500L, live.holdThresholdMs)
    }

    @Test
    fun holdThresholdFollowsTouchAndHoldDelay() {
        assertEquals(1500L, AndroidTimeouts(app) { 1500 }.holdThresholdMs) // "Touch & hold delay": long
        assertEquals(440L, AndroidTimeouts(app) { 300 }.holdThresholdMs) // never under 440 ms
        assertEquals(500L, AndroidTimeouts(app).holdThresholdMs) // ViewConfiguration's default
        assertEquals(440L, DefaultTimeouts.holdThresholdMs)
    }

    @Test
    fun theLadybugFires560MsAfterTheThreshold() {
        assertEquals(1000L, DefaultTimeouts.ladybugMs) // 1 s by default (§4.3)
        assertEquals(2060L, AndroidTimeouts(app) { 1500 }.ladybugMs)
        assertEquals(2060L, FakeTimeouts(holdThresholdMs = 1500).ladybugMs)
    }

    @Test
    @Config(sdk = [26])
    fun beforeApi29ThereIsNoSettingAndHoldsAreUnchanged() {
        val timeouts = AndroidTimeouts(app)
        assertEquals(4000L, timeouts.recommended(4000, controls = true))
        assertEquals(2600L, timeouts.recommended(2600, controls = false))
    }

    @Test
    fun nonPositiveHoldsPassThrough() {
        accessibility.setInteractiveUiTimeout(10_000)
        val timeouts = AndroidTimeouts(app)
        assertEquals(0L, timeouts.recommended(0, controls = true))
        assertEquals(Long.MAX_VALUE, timeouts.recommended(Long.MAX_VALUE, controls = true))
    }

    /** FakeTimeouts, which other packages' tests use, answers exactly as the phone does for the same settings. */
    @Test
    fun fakeTimeoutsAgreeWithThePhone() {
        val phone = AndroidTimeouts(app)
        for (interactive in listOf(0, 3000, 10_000)) {
            for (nonInteractive in listOf(0, 5000, 10_000)) {
                accessibility.setInteractiveUiTimeout(interactive)
                accessibility.setNonInteractiveUiTimeout(nonInteractive)
                val fake = FakeTimeouts(actionTimeoutMs = interactive.toLong(), textTimeoutMs = nonInteractive.toLong())
                for (hold in listOf(1500L, 2600L, 4000L, 6000L, 12_000L)) {
                    for (controls in listOf(true, false)) {
                        assertEquals(
                            "hold $hold controls $controls interactive $interactive text $nonInteractive",
                            phone.recommended(hold, controls),
                            fake.recommended(hold, controls),
                        )
                    }
                }
            }
        }
    }
}
