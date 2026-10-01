package earth.cora.picnic.ui.add

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import earth.cora.picnic.core.assistant.ProposedItem
import earth.cora.picnic.core.assistant.WebSource
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.llm.LlmError
import earth.cora.picnic.core.model.Aisles
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.IconRef
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.ui.common.Samples
import earth.cora.picnic.ui.common.snap
import earth.cora.picnic.ui.common.themeParameters
import earth.cora.picnic.ui.list.ListActions
import earth.cora.picnic.ui.list.ListScreen
import earth.cora.picnic.ui.list.buildListUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

/** Goldens: add_write, add_thinking, add_preview (sources, skipped, a removed row), add_failed, add_recording, add_voice. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AddSheetScreenshotTest(private val dark: Boolean, @Suppress("unused") private val theme: String) {
    @get:Rule
    val compose = createComposeRule()

    private val aisles = Aisles.parse(Aisles.DEFAULT_TEXT).associateBy { it.id }

    private fun preview(vararg items: ProposedItem, removed: Set<String> = emptySet()): List<PreviewItemUi> =
        items.mapIndexed { i, item ->
            val aisle = item.aisleId?.let(aisles::get)
            PreviewItemUi(
                key = i,
                item = item,
                keep = item.name !in removed,
                aisleTitle = aisle?.name ?: "Unsorted",
                icon = IconRef(item.icon),
                aisleIndex = aisle?.let { it.position - 1 } ?: -1,
            )
        }.sortedWith(compareBy({ it.aisleIndex }, { it.key }))

    private fun item(name: String, aisleId: String?, icon: String, quantity: String? = null, note: String? = null, fit: IconFit = IconFit.EXACT) =
        ProposedItem(name, quantity, note, aisleId, icon, fit)

    private val pancakes = preview(
        item("All-purpose flour", "aisle-2-baking-spices", "flour", "2 cups"),
        item("Baking powder", "aisle-2-baking-spices", "baking_generic", "1 tbsp", fit = IconFit.APPROXIMATE),
        item("Granulated sugar", "aisle-2-baking-spices", "sugar", "2 tbsp"),
        item("Buttermilk", "dairy-eggs", "dairy_generic", "500 mL", fit = IconFit.APPROXIMATE),
        item("Butter", "dairy-eggs", "butter", note = "for the pan"),
        item("Maple syrup", "aisle-1-breakfast-coffee-tea", "maple_syrup", "250 mL", note = "the real stuff"),
        item("Blueberries", "produce", "blueberries", "1 pint"),
        removed = setOf("Granulated sugar"),
    )

    /** The Add sheet over the mid-trip list, as the user sees it. */
    private fun sheet(name: String, state: AddUiState) = compose.snap(name, dark, wholeScreen = true) {
        Background()
        AddSheet(state, AddActions(), onDismiss = {})
    }

    @Composable
    private fun Background() {
        ListScreen(buildListUiState(Samples.shoppingState, emptySet(), true, null, IconCatalog.default), ListActions())
    }

    @Test
    fun write() = sheet("add_write", AddUiState(Samples.LIST_ID, text = "ingredients for pancakes from scratch"))

    @Test
    fun thinking() = sheet(
        "add_thinking",
        AddUiState(Samples.LIST_ID, text = "ingredients for pancakes from scratch", phase = AddPhase.Thinking(ItemSource.AI_TEXT, recipe = true)),
    )

    @Test
    fun previewWithSources() = sheet(
        "add_preview",
        AddUiState(
            Samples.LIST_ID,
            text = "ingredients for pancakes from scratch",
            phase = AddPhase.Preview,
            preview = pancakes,
            message = "Fluffy buttermilk pancakes for about 4. You already have eggs and milk on the list.",
            sources = listOf(
                WebSource("https://www.allrecipes.com/recipe/162760/fluffy-pancakes/", "Fluffy Pancakes"),
                WebSource("https://www.ricardocuisine.com/en/recipes/5459-buttermilk-pancakes"),
            ),
            skipped = listOf("Eggs", "Milk"),
            previewSource = ItemSource.AI_TEXT,
        ),
    )

    @Test
    fun failedWithPlainLines() = sheet(
        "add_failed",
        AddUiState(
            Samples.LIST_ID,
            text = "oat milk, 2 lemons, tofu, gochujang",
            phase = LlmError.Network(IOException("offline")).let {
                AddPhase.Failed(it.friendlyMessage, it.retryable, canUsePlainLines = true, needsSettings = false)
            },
        ),
    )

    @Test
    fun recording() = sheet(
        "add_recording",
        AddUiState(
            Samples.LIST_ID,
            mode = AddMode.SPEAK,
            phase = AddPhase.Recording(
                elapsedMs = 12_400,
                level = 0.62f,
                levels = listOf(0.05f, 0.1f, 0.3f, 0.55f, 0.8f, 0.62f, 0.4f, 0.7f, 0.92f, 0.5f, 0.35f, 0.6f, 0.2f, 0.45f, 0.62f),
            ),
        ),
    )

    @Test
    fun voicePreview() = sheet(
        "add_voice",
        AddUiState(
            Samples.LIST_ID,
            mode = AddMode.SPEAK,
            phase = AddPhase.Preview,
            transcript = "Milk, two lemons and stuff for tacos",
            preview = preview(
                item("Ground beef", "meat-seafood", "ground_beef", "450 g"),
                item("Taco shells", "aisle-3-pasta-rice-international", "tortillas", "1 box", fit = IconFit.APPROXIMATE),
                item("Shredded cheese", "deli-cheese", "shredded_cheese", "320 g"),
                item("Lettuce", "produce", "lettuce", "1 head"),
                item("Salsa", "aisle-3-pasta-rice-international", "salsa"),
            ),
            message = "Tacos for four. Milk and lemons are already on your list.",
            skipped = listOf("Milk", "Lemons"),
            previewSource = ItemSource.VOICE,
        ),
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun params() = themeParameters()
    }
}
