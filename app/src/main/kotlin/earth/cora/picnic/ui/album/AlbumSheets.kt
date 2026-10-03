package earth.cora.picnic.ui.album

import androidx.compose.runtime.Immutable
import earth.cora.picnic.core.calendar.Season
import earth.cora.picnic.ui.theme.WashiPattern

/*
 * The album's sheets (docs/DESIGN_STICKER.md §6.10, §4.25, eggs 11 and 14): the picnic sheet that celebration bonus
 * stickers fill (WP6 adds to Album.picnic with these keys), the sakura sheet a perfect week unlocks ("sakura" in
 * Album.sheets, WP9), and the six seasonal sheets (their unlock ids "<season id>-<year>" in Album.sheets, WP9).
 * Owner: WP8; the other packages read these lists rather than keep their own.
 */

/** One sticker of a sheet: a library sticker ("i:<catalog key>") or a decor sticker ("d:<decor name>"), with its label. */
@Immutable
data class SheetSticker(val ref: String, val label: String) {
    val isDecor: Boolean get() = ref.startsWith("d:")
    val key: String get() = ref.substringAfter(':')
}

/** A seasonal sheet: its season, name, washi tape and three stickers. */
@Immutable
data class SeasonSheet(val season: Season, val id: String, val name: String, val holiday: String, val tape: WashiPattern, val stickers: List<SheetSticker>)

object AlbumSheets {
    /** The picnic sheet (§6.10): filled by celebration bonus stickers, in this order (Album.picnic holds the refs won). */
    val PICNIC: List<SheetSticker> = listOf(
        SheetSticker("i:picnic_blanket", "Picnic blanket"),
        SheetSticker("d:basketdeco", "Picnic basket"),
        SheetSticker("i:lemonade", "Lemonade"),
        SheetSticker("d:sandwich", "Sandwich"),
        SheetSticker("i:watermelon", "Watermelon"),
        SheetSticker("i:cherries", "Cherries"),
        SheetSticker("d:teacup", "Teacup"),
        SheetSticker("d:bee", "Bee"),
        SheetSticker("d:ladybug", "Ladybug"),
        SheetSticker("i:strawberry", "Strawberries"),
        SheetSticker("i:cookies", "Cookies"),
        SheetSticker("d:flower", "Daisy"),
    )

    /** The sakura sheet (egg 14): unlocked by a perfect week; shown whole once [SAKURA] is in Album.sheets. */
    val SAKURA_STICKERS: List<SheetSticker> = listOf(
        SheetSticker("d:petal", "Sakura petal"),
        SheetSticker("d:heart", "Heart"),
        SheetSticker("i:bouquet", "Bouquet"),
        SheetSticker("d:ribbon", "Ribbon"),
        SheetSticker("i:raspberries", "Raspberries"),
        SheetSticker("i:cake", "Cake"),
    )

    /** Album.sheets id of the sakura sheet. */
    const val SAKURA: String = "sakura"

    /** Album.sheets id of "sticker sheet 2 of 2" (egg 14's back pocket). */
    const val SHEET_2: String = "sheet2"

    /** The back pocket's sheet ("sticker sheet 2 of 2"), closing the loop the empty state's sheet 1 of 2 starts. */
    val SHEET_2_STICKERS: List<SheetSticker> = listOf(
        SheetSticker("d:cake", "Cake"),
        SheetSticker("d:teacup", "Teacup"),
        SheetSticker("i:peach", "Peach"),
        SheetSticker("d:sun", "Sun"),
        SheetSticker("d:cloud", "Cloud"),
    )

    /** The six seasonal sheets (egg 11), in calendar order. */
    val SEASONS: List<SeasonSheet> = listOf(
        SeasonSheet(
            Season.LUNAR_NEW_YEAR, "lunar-new-year", "Lunar New Year sheet", "Lunar New Year", WashiPattern.LunarNewYear,
            listOf(SheetSticker("d:redenvelope", "Red envelope"), SheetSticker("i:clementine", "Clementine"), SheetSticker("i:noodles", "Noodles")),
        ),
        SeasonSheet(
            Season.VALENTINES, "valentines", "Valentine sheet", "Valentine’s Day", WashiPattern.Valentines,
            listOf(SheetSticker("d:heart", "Heart"), SheetSticker("i:strawberry", "Strawberries"), SheetSticker("i:chocolate_bar", "Chocolate")),
        ),
        SeasonSheet(
            Season.CANADA_DAY, "canada-day", "Canada Day sheet", "Canada Day", WashiPattern.CanadaDay,
            listOf(SheetSticker("d:mapleleaf", "Maple leaf"), SheetSticker("i:watermelon", "Watermelon"), SheetSticker("i:butter_tart", "Butter tart")),
        ),
        SeasonSheet(
            Season.THANKSGIVING, "thanksgiving", "Autumn sheet", "Thanksgiving", WashiPattern.Thanksgiving,
            listOf(SheetSticker("i:pie", "Pumpkin pie"), SheetSticker("i:apple", "McIntosh"), SheetSticker("d:mapleleaf", "Maple leaf")),
        ),
        SeasonSheet(
            Season.HALLOWEEN, "halloween", "Halloween sheet", "Halloween", WashiPattern.Halloween,
            listOf(SheetSticker("d:pumpkin", "Pumpkin"), SheetSticker("i:candy", "Candy"), SheetSticker("i:apple", "Candy apple")),
        ),
        SeasonSheet(
            Season.FIRST_SNOW, "first-snow", "First-snow sheet", "First snow", WashiPattern.FirstSnow,
            listOf(SheetSticker("d:snowflake", "Snowflake"), SheetSticker("i:marshmallows", "Marshmallows"), SheetSticker("i:cocoa_powder", "Hot cocoa")),
        ),
    )

    /** The Album.sheets id a seasonal sheet gets when it unlocks in [year]: "thanksgiving-2026", "first-snow-2026". */
    fun seasonId(season: Season, year: Int): String = "${sheetOf(season).id}-$year"

    fun sheetOf(season: Season): SeasonSheet = SEASONS.first { it.season == season }

    /** The sheet is unlocked in any year. */
    fun unlocked(season: Season, sheets: Set<String>): Boolean {
        val prefix = sheetOf(season).id + "-"
        return sheets.any { it.startsWith(prefix) && it.removePrefix(prefix).all(Char::isDigit) }
    }
}
