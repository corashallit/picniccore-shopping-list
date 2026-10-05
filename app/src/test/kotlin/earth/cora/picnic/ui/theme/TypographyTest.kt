package earth.cora.picnic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PicnicTypography's roles match §2.7 (family, weight, size, colour; checklist 22). */
class TypographyTest {
    private val day = typographyFor(DayColors)
    private val glow = typographyFor(GlowColors)
    private val puff = PicnicFonts.dynaPuff
    private val zen = PicnicFonts.zenMaru
    private val klee = PicnicFonts.kleeOne
    private val hachi = PicnicFonts.hachiMaru

    private fun check(name: String, s: TextStyle, family: FontFamily, weight: FontWeight, size: TextUnit, color: Color? = null) {
        assertEquals("$name family", family, s.fontFamily)
        assertEquals("$name weight", weight, s.fontWeight)
        assertEquals("$name size", size, s.fontSize)
        if (color != null) assertEquals("$name colour", color, s.color)
    }

    @Test
    fun rolesMatchSpec() {
        val c = DayColors
        check("wordmark", day.wordmarkLarge, puff, FontWeight.Bold, 40.sp, Families.strawberry.mid)
        check("wordmark compact", day.wordmarkCompact, puff, FontWeight.Bold, 30.sp)
        check("screen title", day.screenTitle, puff, FontWeight.Bold, 24.sp, c.ballpoint)
        check("screen title collapsed", day.screenTitleCollapsed, puff, FontWeight.Bold, 20.sp, c.ballpoint)
        check("app-bar subtitle", day.appBarSubtitle, zen, FontWeight.Bold, 13.sp, c.ballpointSoft)
        check("aisle tab", day.aisleTab, puff, FontWeight.Bold, 20.sp)
        check("tab number", day.tabNumber, puff, FontWeight.Bold, 14.sp)
        check("item name", day.itemName, zen, FontWeight.Bold, 17.sp, c.ballpoint)
        check("item name compact", day.itemNameCompact, zen, FontWeight.Bold, 16.sp)
        check("item detail", day.itemDetail, zen, FontWeight.Medium, 13.5.sp, c.ballpointSoft)
        check("item detail compact", day.itemDetailCompact, zen, FontWeight.Medium, 13.sp)
        check("strip meta", day.stripMeta, zen, FontWeight.Medium, 13.sp, c.ballpointSoft)
        check("strip meta count", day.stripMetaCount, zen, FontWeight.Bold, 13.sp, c.ballpoint)
        check("body", day.body, zen, FontWeight.Medium, 16.sp, c.ballpoint)
        check("label", day.label, zen, FontWeight.Bold, 13.5.sp, c.ballpoint)
        assertEquals(zen, day.hanko.fontFamily)
        assertEquals(FontWeight.Black, day.hanko.fontWeight)
        assertEquals(c.hanko, day.hanko.color)
        check("primary button", day.primaryButton, puff, FontWeight.Bold, 18.sp, Color.White)
        check("secondary button", day.secondaryButton, puff, FontWeight.Bold, 16.sp, c.ballpoint)
        check("text button", day.textButton, zen, FontWeight.Bold, 15.sp, c.ballpoint)
        check("transcript", day.transcript, klee, FontWeight.SemiBold, 15.sp, c.ballpointSoft)
        assertEquals(22.sp, day.transcript.lineHeight)
        check("voice transcript", day.voiceTranscript, klee, FontWeight.SemiBold, 16.sp)
        check("aisles text", day.aislesText, klee, FontWeight.SemiBold, 16.sp)
        assertEquals(24.sp, day.aislesText.lineHeight)
        assertEquals(hachi, day.doodle.fontFamily)
        assertEquals(FontWeight.Normal, day.doodle.fontWeight)
        assertTrue(day.doodle.fontSize.value in 13f..15f)
        assertEquals(Families.sakura.trace, day.doodle.color)
        check("NEXT", day.nextLabel, puff, FontWeight.Bold, 26.sp, c.ballpoint)
        check("Next aisle", day.nextAisle, puff, FontWeight.Bold, 23.sp, c.ballpoint)
        check("celebration title", day.celebrationTitle, puff, FontWeight.Bold, 34.sp, Families.strawberry.mid)
        check("toast", day.toast, zen, FontWeight.Bold, 14.sp, c.ballpoint)
        check("stamp numerals", day.stampNumeral, puff, FontWeight.Bold, 12.sp, c.ballpointSoft)
        check("chip number", day.chipNumber, puff, FontWeight.Bold, 11.sp)
        check("field label", day.fieldLabel, zen, FontWeight.Bold, 13.sp, c.ballpointSoft)
        check("field value", day.fieldValue, zen, FontWeight.Bold, 16.sp, c.ballpoint)
    }

    @Test
    fun glowTextIsInk() {
        assertEquals(Color.White, glow.screenTitle.color)
        assertEquals(GlowColors.inkSoft, glow.itemDetail.color)
        assertEquals(Color(0xFFFF9FC8), glow.doodle.color)
        // The current aisle's name and the Next card stay ballpoint on their pastels in the dark.
        assertEquals(DayColors.ballpoint, glow.nextAisle.color)
    }

    @Test
    fun nothingBelowThe13spFloor() {
        val exempt = setOf("stampNumeral", "chipNumber", "hanko")
        val roles = mapOf(
            "wordmark" to day.wordmark, "screenTitle" to day.screenTitle, "aisleTab" to day.aisleTab, "tabNumber" to day.tabNumber,
            "itemName" to day.itemName, "itemNameCompact" to day.itemNameCompact, "body" to day.body, "detail" to day.detail,
            "itemDetailCompact" to day.itemDetailCompact, "label" to day.label, "caption" to day.caption, "doodle" to day.doodle,
            "transcript" to day.transcript, "counter" to day.counter, "appBarSubtitle" to day.appBarSubtitle, "stripMeta" to day.stripMeta,
            "primaryButton" to day.primaryButton, "secondaryButton" to day.secondaryButton, "textButton" to day.textButton,
            "toast" to day.toast, "fieldLabel" to day.fieldLabel, "fieldValue" to day.fieldValue, "badgeNumeral" to day.badgeNumeral,
            "stampNumeral" to day.stampNumeral, "chipNumber" to day.chipNumber, "hanko" to day.hanko,
        )
        for ((name, style) in roles) {
            if (name in exempt) continue
            assertTrue("$name is ${style.fontSize}", style.fontSize.value >= 13f)
        }
        // Item names are never below 16 sp and never in a display face.
        assertTrue(day.itemNameCompact.fontSize.value >= 16f)
        assertEquals(zen, day.itemName.fontFamily)
    }
}
