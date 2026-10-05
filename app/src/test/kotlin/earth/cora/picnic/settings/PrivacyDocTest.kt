package earth.cora.picnic.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checklist 234, 316 and 330 (docs/DESIGN_STICKER.md §0.4, §6.13, §12.4): docs/PRIVACY.md describes the opt-in
 * Environment Canada request (also in its Data safety notes), "Use my area" in §0.4's own words, and the pantry
 * (stored on the phone, sent to OpenAI with each AI request, never sent without a key). A doc grep: item 330 is also a
 * review gate for Cora.
 */
class PrivacyDocTest {
    private val doc: String = listOf(File("../docs/PRIVACY.md"), File("docs/PRIVACY.md")).first { it.isFile }.readText()

    /** The doc with its Markdown emphasis and line structure flattened, for phrase checks. */
    private val plain: String = doc.replace("**", "").replace(Regex("\\s+"), " ")

    @Test
    fun theOptInEnvironmentCanadaRequest() {
        assertTrue("names Environment Canada", "Environment Canada" in plain)
        assertTrue("names the host", "weather.gc.ca" in plain)
        assertTrue("says it is off by default", "Weather surprises (Settings → Surprises, off by default)" in plain)
        assertTrue("says how often", "at most every 3 hours" in plain)
        // The feed is requested for the city's own coordinates (Place.forecastFeedUrl), so the doc says so, truly.
        assertTrue("says what the request carries", "names that city by its published coordinates (the same for everyone near it), never your own location" in plain)
        assertFalse("not a city code: the request carries coordinates", "only the code of the nearest forecast city leaves the phone" in plain)
        val safety = plain.substringAfter("Data safety notes")
        assertTrue("the Data safety notes cover it", "Environment Canada" in safety && "Weather surprises" in safety)
        assertTrue("and what the request carries", "by that city's published coordinates" in safety)
    }

    @Test
    fun useMyAreaInTheSpecsOwnWords() {
        val rationale = "Kirakira Basket can use your approximate area to find tonight's sunset and the local forecast for " +
            "its weather surprises. Your area stays on this phone; only the forecast request names the nearest city."
        assertTrue("§0.4's sentence", rationale in plain)
        assertTrue("approximate location only", "approximate location only" in plain)
        assertTrue("rounded to 0.1°", "0.1°" in plain)
        assertTrue("every 6 hours", "every 6 hours" in plain)
        assertTrue("Toronto when off or denied", "everything uses Toronto" in plain)
        assertTrue("no precise and no background location", "never precise location, and never in the background" in plain)
        assertFalse("the old promise is gone", "never reads your phone's location" in plain)
    }

    @Test
    fun thePantry() {
        val pantry = plain.substringAfter("Your pantry is stored on your phone").substringBefore("Location.")
        assertTrue("stored on the phone", plain.contains("Your pantry is stored on your phone"))
        assertTrue("sent with each AI request", "goes with every request to the AI" in pantry)
        assertTrue("never without a key", "never sent anywhere without a key" in pantry)
        assertTrue("the first 200", "first 200" in plain)
    }

    /**
     * Item 360 (§0.4, 5 October): the network-state permission is named and said to be granted without a prompt, and a
     * photo or voice note that could not be sent is said to be kept, where, until when, and for 7 days at most.
     */
    @Test
    fun theNetworkStateAndKeptCaptures() {
        val permissions = plain.substringAfter("Permissions the app asks for").substringBefore("How long things are kept")
        assertTrue("names the permission", "Network state:" in permissions)
        assertTrue("never a prompt", "never asks you about it" in permissions)
        assertTrue("kept when it couldn't be sent", "couldn't be sent" in plain)
        assertTrue("where", "the app's private storage" in plain)
        assertTrue("until when", "until it is sent, you take a new photo or record a new note, or you tap Delete photo or Delete voice note" in plain)
        assertTrue("7 days at most", "7 days at most" in plain)
        assertTrue("the source table names the manifest's permissions", "INTERNET, ACCESS_NETWORK_STATE, RECORD_AUDIO, ACCESS_COARSE_LOCATION" in plain)
    }

    @Test
    fun theAppsName() {
        assertTrue(doc.startsWith("# Kirakira Basket privacy policy"))
        assertFalse("Picnic List" in doc)
    }
}
