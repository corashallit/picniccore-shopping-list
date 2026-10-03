package earth.cora.picnic.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * WP4 component goldens in Day and Glow (docs/DESIGN_STICKER.md §20.1–§20.4, Appendix C): ground, tab patterns, washi
 * and edges, puffy lettering, buttons (R3), chips, fields, switch, toasts, sheet, dividers, empty state, loaders, LCD,
 * app bar, overflow menu, item icons, the hanko and the row harness.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xxhdpi")
class ThemeGoldensTest(private val variant: ThemeVariant, private val v: String) {
    @get:Rule
    val compose = createComposeRule()

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = dayAndGlow()
    }

    // ---- §20.1 ground, patterns, washi, edges ----

    @Test fun groundPinstripe() = compose.recordGolden("ground_pinstripe_$v", variant) { GroundKit() }

    @Test fun tabPatternsAll() = compose.recordGolden("tab_patterns_all_$v", variant) { TabPatternsKit() }

    @Test fun washiAll() = compose.recordGolden("washi_all_$v", variant) { WashiKit() }

    @Test fun perforationAddbar() = compose.recordGolden("perforation_addbar_$v", variant) { PerforationKit() }

    @Test fun postageRecipeCard() = compose.recordGolden("postage_recipe_card_$v", variant) { PostageKit() }

    @Test fun padcardPunchholes() = compose.recordGolden("padcard_punchholes_$v", variant) { PunchHolesKit() }

    // ---- §20.2 lettering ----

    @Test fun puffyTitle() = compose.recordGolden("puffy_title_$v", variant) { PuffyKit() }

    @Test fun puffyTitleLong() = compose.recordGolden("puffy_title_${v}_long", variant) { PuffyKit(long = true) }

    @Test fun wordmark() = compose.recordGolden("wordmark_$v", variant) { Wordmark("Kirakira Basket"); Wordmark("Kirakira Basket", compact = true) }

    // ---- §20.4 components ----

    @Test fun buttonsMatrix() = compose.recordGolden("buttons_matrix_$v", variant) { ButtonsKit() }

    @Test fun primaryRest() = compose.recordGolden("button_primary_rest_$v", variant) { PrimaryButtonState("rest") }

    @Test fun primaryPressed() = compose.recordGolden("button_primary_pressed_$v", variant) { PrimaryButtonState("pressed") }

    @Test fun primaryDisabled() = compose.recordGolden("button_primary_disabled_$v", variant) { PrimaryButtonState("disabled") }

    @Test fun primaryFocused() = compose.recordGolden("button_primary_focused_$v", variant) { PrimaryButtonState("focused") }

    @Test fun secondaryRest() = compose.recordGolden("button_secondary_rest_$v", variant) { SecondaryButtonState("rest") }

    @Test fun secondaryPressed() = compose.recordGolden("button_secondary_pressed_$v", variant) { SecondaryButtonState("pressed") }

    @Test fun secondaryDisabled() = compose.recordGolden("button_secondary_disabled_$v", variant) { SecondaryButtonState("disabled") }

    @Test fun secondaryFocused() = compose.recordGolden("button_secondary_focused_$v", variant) { SecondaryButtonState("focused") }

    @Test fun textRest() = compose.recordGolden("button_text_rest_$v", variant) { TextButtonState("rest") }

    @Test fun textPressed() = compose.recordGolden("button_text_pressed_$v", variant) { TextButtonState("pressed") }

    @Test fun textDisabled() = compose.recordGolden("button_text_disabled_$v", variant) { TextButtonState("disabled") }

    @Test fun roundRest() = compose.recordGolden("button_round_rest_$v", variant) { RoundButtonsState("rest") }

    @Test fun roundPressed() = compose.recordGolden("button_round_pressed_$v", variant) { RoundButtonsState("pressed") }

    @Test fun roundDisabled() = compose.recordGolden("button_round_disabled_$v", variant) { RoundButtonsState("disabled") }

    @Test fun fabRest() = compose.recordGolden("fab_rest_$v", variant) { FabState("rest") }

    @Test fun fabPressed() = compose.recordGolden("fab_pressed_$v", variant) { FabState("pressed") }

    @Test fun chipsAll() = compose.recordGolden("chips_all_$v", variant) { ChipsKit() }

    @Test fun switchStates() = compose.recordGolden("switch_states_$v", variant) { SwitchKit() }

    @Test fun fieldRest() = compose.recordGolden("field_rest_$v", variant) { FieldState("rest") }

    @Test fun fieldFocus() = compose.recordGolden("field_focus_$v", variant) { FieldState("focus") }

    @Test fun fieldError() = compose.recordGolden("field_error_$v", variant) { FieldState("error") }

    @Test fun fieldDisabled() = compose.recordGolden("field_disabled_$v", variant) { FieldState("disabled") }

    @Test fun fieldMultiline() = compose.recordGolden("field_multiline_$v", variant) { FieldState("multiline") }

    @Test fun fieldKey() = compose.recordGolden("field_key_$v", variant) { FieldState("key") }

    @Test fun toastUndo() = compose.recordGolden("toast_undo_$v", variant) { ToastState("undo") }

    @Test fun toastLong() = compose.recordGolden("toast_long_$v", variant) { ToastState("long") }

    @Test fun toastMerged() = compose.recordGolden("toast_merged_$v", variant) { ToastState("merged") }

    @Test fun sheetLetterset() = compose.recordGolden("sheet_letterset_$v", variant, gutter = false) {
        Box(Modifier.fillMaxWidth().height(24.dp))
        SheetKit()
    }

    @Test fun dividers() = compose.recordGolden("dividers_$v", variant) { DividersKit() }

    @Test fun emptyState() = compose.recordGolden("empty_state_$v", variant) { EmptyKit() }

    @Test fun loaders() = compose.recordGolden("loaders_animated_$v", variant) { LoadersKit() }

    @Test fun loadersStatic() = compose.recordGolden("loaders_static_$v", variant, reducedMotion = true) { LoadersKit(reduced = true) }

    @Test fun lcdModes() = compose.recordGolden("lcd_modes_$v", variant) { LcdKit() }

    @Test fun appbarExpanded() = compose.recordGolden("appbar_expanded_$v", variant, gutter = false) { AppBarKit(collapsed = false) }

    @Test fun appbarCollapsed() = compose.recordGolden("appbar_collapsed_$v", variant, gutter = false) { AppBarKit(collapsed = true) }

    @Test fun appbarSecondary() = compose.recordGolden("appbar_secondary_$v", variant, gutter = false) { SecondaryBarKit() }

    @Test fun overflowMenu() = compose.recordGolden("overflow_menu_$v", variant) { MenuKit() }

    @Test fun checkboxHankoAngles() = compose.recordGolden("checkbox_hanko_angles_$v", variant) { HankoAnglesKit() }

    @Test fun hankoVariants() = compose.recordGolden("hanko_variants_$v", variant) { HankoVariantsKit() }

    @Test fun rowStates() = compose.recordGolden("checkbox_states_$v", variant) { CheckboxKit() }

    @Test fun rowStatesFs200() = compose.recordGolden("row_states_fs200_$v", variant, fontScale = 2f) {
        Box(Modifier.width(332.dp)) { CheckboxKit() }
    }

    @Test fun itemIcons() = compose.recordGolden("itemicon_kit_$v", variant) { ItemIconsKit() }

    @Test fun itemIconGhost() = compose.recordGolden("itemicon_ghost_$v", variant) { GhostRow() }

    @Test fun itemIconShadow() = compose.recordGolden("itemicon_shadow_$v", variant) { ShadowRow() }

    @Test fun capsuleAisles() = compose.recordGolden("itemicon_capsule_aisles_$v", variant) { CapsuleRow() }

    @Test fun rowFlags() = compose.recordGolden("row_flags_$v", variant) { FlagsRow() }
}
