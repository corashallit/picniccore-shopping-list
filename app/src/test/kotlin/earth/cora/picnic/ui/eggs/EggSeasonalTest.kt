package earth.cora.picnic.ui.eggs

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.core.calendar.Seasons
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.weather.WeatherToday
import earth.cora.picnic.settings.AppSettings
import earth.cora.picnic.ui.add.AddRules
import earth.cora.picnic.ui.album.AlbumStateBuilder
import earth.cora.picnic.ui.theme.WashiPattern
import earth.cora.picnic.ui.theme.gen.DecorManifest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime

/**
 * Item 248 (egg 11) on the fake clock (America/Toronto): in each season's window the add bar wears that season's washi,
 * the book's date label its pictogram, and its album sheet unlocks (once, idempotent); outside every window none of it;
 * the album flags the next sheet with its computed date; first snow (Weather surprises on) holds its tape for 7 days.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class EggSeasonalTest {
    @get:Rule
    val compose = createComposeRule()

    private data class Case(val season: Season, val date: LocalDate, val tape: WashiPattern, val pictogram: String)

    private val cases = listOf(
        Case(Season.CANADA_DAY, LocalDate.of(2027, 6, 18), WashiPattern.CanadaDay, "maple"),
        Case(Season.CANADA_DAY, LocalDate.of(2027, 7, 1), WashiPattern.CanadaDay, "maple"),
        Case(Season.THANKSGIVING, LocalDate.of(2026, 10, 3), WashiPattern.Thanksgiving, "pumpkin"),
        Case(Season.THANKSGIVING, LocalDate.of(2026, 10, 12), WashiPattern.Thanksgiving, "pumpkin"),
        Case(Season.HALLOWEEN, LocalDate.of(2026, 10, 18), WashiPattern.Halloween, "ghost"),
        Case(Season.HALLOWEEN, LocalDate.of(2026, 10, 31), WashiPattern.Halloween, "ghost"),
        Case(Season.LUNAR_NEW_YEAR, LocalDate.of(2027, 2, 3), WashiPattern.LunarNewYear, "envelope"),
        Case(Season.VALENTINES, LocalDate.of(2027, 2, 11), WashiPattern.Valentines, "heart"),
        Case(Season.LUNAR_NEW_YEAR, LocalDate.of(2028, 1, 30), WashiPattern.LunarNewYear, "envelope"),
    )

    @Test
    fun eachWindowWearsItsTapeAndPictogramAndUnlocksItsSheet() {
        for (c in cases) {
            val e = EngineRig()
            try {
                e.at(c.date, LocalTime.of(10, 24))
                e.engine.onListShown(e.listId)
                assertEquals("${c.date}", c.season, e.eggs.season)
                assertEquals("${c.date}: the add bar's tape", c.tape, AddRules.addBarTape(e.eggs))
                assertEquals("${c.date}: the book's date pictogram", c.pictogram, earth.cora.picnic.ui.theme.seasonPictogram(c.season).key)
                assertTrue("${c.pictogram} is drawn", c.pictogram in DecorManifest.pictograms)
                val sheet = EggRules.sheetId(c.season, c.date)
                val album = runBlocking { e.albumStore.current() }
                assertTrue("${c.date}: $sheet unlocks", sheet in album.sheets)
                // Once: a second look changes nothing.
                e.engine.refresh()
                assertEquals(album, runBlocking { e.albumStore.current() })
            } finally {
                e.close()
            }
        }
    }

    @Test
    fun outsideEveryWindowNothingShows() {
        val e = EngineRig(clock = FakeClock(torontoMs(LocalTime.of(10, 24), LocalDate.of(2026, 8, 20))))
        try {
            e.at(LocalDate.of(2026, 8, 20), LocalTime.of(10, 24))
            assertNull(e.eggs.season)
            assertEquals(WashiPattern.SakuraStripe, AddRules.addBarTape(e.eggs))
            assertTrue(runBlocking { e.albumStore.current() }.sheets.isEmpty())
        } finally {
            e.close()
        }
    }

    @Test
    fun theNextSheetShowsItsComputedDate() {
        val builder = AlbumStateBuilder(IconCatalog.default, today = { LocalDate.of(2026, 10, 3) })
        // On 3 October, inside Thanksgiving's window, its sheet not yet unlocked: "next! · Thanksgiving Oct 12".
        assertEquals(Season.THANKSGIVING to LocalDate.of(2026, 10, 12), builder.upcoming(LocalDate.of(2026, 10, 3), emptySet()))
        // Unlocked already: the next window to start (Halloween, Oct 31).
        assertEquals(Season.HALLOWEEN to LocalDate.of(2026, 10, 31), builder.upcoming(LocalDate.of(2026, 10, 3), setOf("thanksgiving-2026")))
        // After Halloween: Valentine's, Feb 14 2027 (Lunar New Year 2027 starts its window later, on Feb 3).
        assertEquals(Seasons.next(LocalDate.of(2026, 11, 2)), builder.upcoming(LocalDate.of(2026, 11, 2), emptySet()))
    }

    @Test
    fun firstSnowHoldsItsTapeForSevenDays() {
        val snowDay = LocalDate.of(2026, 11, 20)
        val weather = CountingWeather { day -> WeatherToday(day, rain = false, snow = day == snowDay) }
        val e = EngineRig(settings = AppSettings(eggs = true, weather = true), weatherSource = weather)
        try {
            e.at(snowDay, LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            assertEquals(Season.FIRST_SNOW, e.eggs.season)
            assertEquals(WashiPattern.FirstSnow, AddRules.addBarTape(e.eggs))
            assertTrue("first-snow-2026" in runBlocking { e.albumStore.current() }.sheets)
            // Six days later, still the snow tape (the forecast no longer says snow; the date is kept).
            e.at(snowDay.plusDays(6), LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            assertEquals(Season.FIRST_SNOW, e.eggs.season)
            // The seventh day after it: gone.
            e.at(snowDay.plusDays(7), LocalTime.of(9, 0))
            e.engine.onListShown(e.listId)
            assertNotEquals(Season.FIRST_SNOW, e.eggs.season)
        } finally {
            e.close()
        }
    }

    @Test
    fun theLabMovesIntoTheNextWindow() {
        val r = EggRig(compose).start()
        // The fixture's day is in Thanksgiving's window; the lab moves the practice date to the next one (Halloween).
        assertEquals(Season.THANKSGIVING, r.eggs.season)
        r.fire(11)
        r.advance(300)
        assertEquals(Season.HALLOWEEN, r.eggs.season)
        assertEquals(WashiPattern.Halloween, AddRules.addBarTape(r.eggs))
        assertTrue("halloween-2026" in runBlocking { r.sandbox.album.current() }.sheets)
    }
}
