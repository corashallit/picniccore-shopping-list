package earth.cora.picnic.platform

import android.app.Application
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Checklist 284, platform part (docs/DESIGN_STICKER.md §15): the phone's announcer speaks politely (an
 * announcement event, no focus move), only while an accessibility service is on, and de-duplicates within 1 s.
 */
@Suppress("DEPRECATION") // TYPE_ANNOUNCEMENT: see AndroidAnnouncer's class comment.
@RunWith(AndroidJUnit4::class)
class AndroidAnnouncerTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val accessibility = shadowOf(app.getSystemService(AccessibilityManager::class.java))
    private var now = 0L
    private val announcer = AndroidAnnouncer(app, clock = { now })

    private fun sent(): List<AccessibilityEvent> = accessibility.sentAccessibilityEvents

    @Test
    fun nothingIsSentWhileAccessibilityIsOff() {
        accessibility.setEnabled(false)
        announcer.polite("Produce done")
        assertEquals(0, sent().size)
    }

    @Test
    fun anAnnouncementEventCarriesTheText() {
        accessibility.setEnabled(true)
        announcer.polite("Strawberries added to today's sticker book")
        val event = sent().single()
        assertEquals(AccessibilityEvent.TYPE_ANNOUNCEMENT, event.eventType)
        assertEquals(listOf("Strawberries added to today's sticker book"), event.text.map { it.toString() })
        assertEquals(app.packageName, event.packageName)
    }

    @Test
    fun deDuplicatedWithinOneSecond() {
        accessibility.setEnabled(true)
        announcer.polite("Next: aisle 5, Breakfast")
        now += 500
        announcer.polite("Next: aisle 5, Breakfast")
        announcer.polite("Breakfast done")
        announcer.polite("  ")
        now += 500
        announcer.polite("Next: aisle 5, Breakfast")
        assertEquals(
            listOf("Next: aisle 5, Breakfast", "Breakfast done", "Next: aisle 5, Breakfast"),
            sent().map { it.text.joinToString() },
        )
    }

    @Test
    @Config(sdk = [26])
    fun worksOnTheOldestSupportedApi() {
        accessibility.setEnabled(true)
        announcer.polite("Receipt saved to your sticker book")
        assertEquals(AccessibilityEvent.TYPE_ANNOUNCEMENT, sent().single().eventType)
    }
}
