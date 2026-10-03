package earth.cora.picnic.ui.add

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import earth.cora.picnic.core.assistant.CallMeta
import earth.cora.picnic.core.assistant.IngestResult
import earth.cora.picnic.core.assistant.PhotoLine
import earth.cora.picnic.core.assistant.ProposedItem
import earth.cora.picnic.core.assistant.RecipeInfo
import earth.cora.picnic.core.assistant.SkipReason
import earth.cora.picnic.core.assistant.WebSource
import earth.cora.picnic.core.model.AppState
import earth.cora.picnic.core.model.GroceryItem
import earth.cora.picnic.core.model.GroceryList
import earth.cora.picnic.core.model.IconFit
import earth.cora.picnic.core.model.ItemSource
import earth.cora.picnic.core.model.PantryEntry
import earth.cora.picnic.core.model.StoreProfile
import earth.cora.picnic.core.model.aisles
import earth.cora.picnic.ui.theme.BinderTopBar
import earth.cora.picnic.ui.theme.LcdState
import earth.cora.picnic.ui.theme.PicnicBackground

/*
 * The Appendix A fixture ("Saturday shop" at Hillcrest Market, docs/DESIGN_STICKER.md) and the answers the add-flow
 * tests and goldens use: a pancake recipe (with and without the pantry), a photographed fridge list and a voice note.
 */
object AddFixtures {
    const val LIST_ID = "list-saturday"
    const val NOW = 1_791_037_440_000L // Saturday 3 October 2026, 10:24 in Toronto

    const val AISLES = "Produce\nBakery\nDeli & meat\nAisle 2 - Baking\nBreakfast\nDairy & eggs\nFrozen\nHousehold"

    private fun item(id: String, name: String, icon: String, aisle: String?, quantity: String?, checked: Boolean = false, note: String? = null) =
        GroceryItem(
            id = id, name = name, quantity = quantity, note = note, aisleId = aisle, icon = icon, iconFit = IconFit.EXACT,
            checked = checked, checkedAt = if (checked) NOW - 60_000 else null, addedAt = NOW - 86_400_000, source = ItemSource.AI_TEXT,
        )

    val items: List<GroceryItem> = listOf(
        item("i-strawberries", "Strawberries", "strawberry", "produce", "1 L basket"),
        item("i-bananas", "Bananas", "banana", "produce", "6 bananas", checked = true),
        item("i-apples", "McIntosh apples", "apple", "produce", "1.36 kg bag"),
        item("i-carrots", "Carrots", "carrot", "produce", "907 g bag", checked = true),
        item("i-broccoli", "Broccoli", "broccoli", "produce", "1 crown", checked = true),
        item("i-sourdough", "Sourdough", "sliced_bread", "bakery", "800 g boule"),
        item("i-croissants", "Croissants", "croissant", "bakery", "4 pack", checked = true),
        item("i-baguette", "Baguette", "baguette", "bakery", "1 stick"),
        item("i-ham", "Spiral ham", "deli_meat", "deli-meat", "1.5 kg", checked = true),
        item("i-peameal", "Peameal bacon", "peameal_bacon", "deli-meat", "600 g roast", checked = true),
        item("i-flour", "All-purpose flour", "flour", "aisle-2-baking", "2.5 kg bag", checked = true),
        item("i-sugar", "White sugar", "sugar", "aisle-2-baking", "2 kg bag", checked = true),
        item("i-bp", "Baking powder", "baking_soda", "aisle-2-baking", "225 g tin", note = "from your recipe"),
        item("i-coffee", "Coffee beans", "coffee", "breakfast", "340 g bag", checked = true),
        item("i-maple", "Maple syrup", "maple_syrup", "breakfast", "540 mL tin"),
        item("i-milk", "Milk, 2 %", "milk_bag", "dairy-eggs", "4 L bag"),
        item("i-eggs", "Eggs, large", "eggs", "dairy-eggs", "dozen"),
        item("i-butter", "Butter, salted", "butter", "dairy-eggs", "454 g brick", checked = true),
        item("i-peas", "Frozen peas", "frozen_vegetables", "frozen", "750 g bag"),
        item("i-icecream", "Ice cream", "ice_cream", "frozen", "1.5 L tub"),
        item("i-tp", "Toilet paper", "toilet_paper", "household", "12 double rolls"),
        item("i-soap", "Dish soap", "dish_soap", "household", "740 mL"),
        item("i-catfood", "Dry cat food", "cat_food", "household", "7 kg bag", note = "pets"),
        item("i-toothpaste", "Toothpaste", "toothpaste", "household", "100 mL tube"),
    )

    val state: AppState = AppState(
        lists = listOf(GroceryList(LIST_ID, "Saturday shop", NOW - 7 * 86_400_000L, items, openAisle = "aisle-2-baking")),
        currentListId = LIST_ID,
        store = StoreProfile(name = "Hillcrest Market", aislesText = AISLES),
    )

    val pantry: List<PantryEntry> = listOf("Flour", "Sugar", "Salt", "Baking powder").map { PantryEntry(it, null, NOW) }

    val stateWithPantry: AppState = state.copy(pantry = pantry)

    fun proposed(
        name: String,
        icon: String,
        aisle: String?,
        quantity: String? = null,
        note: String? = null,
        skip: SkipReason = SkipReason.NONE,
        named: Boolean = false,
        pantryEntry: String? = null,
        fit: IconFit = IconFit.EXACT,
    ) = ProposedItem(name, quantity, note, aisle, icon, fit, skipReason = skip, named = named, pantryEntry = pantryEntry)

    fun meta(webSearches: Int = 0, queries: List<String> = emptyList()) =
        CallMeta(model = "test-model", requestId = "req", latencyMs = 900, webSearches = webSearches, searchQueries = queries)

    /** What the model searched for the pancakes (its own query, not her note). */
    const val PANCAKE_QUERY = "fluffy pancakes from scratch recipe"

    /** "ingredients for pancakes from scratch": a recipe compared across 3, makes 12, web search used. */
    fun pancakes(withPantry: Boolean = false): IngestResult {
        fun pantryOr(skip: SkipReason, entry: String) = if (withPantry) SkipReason.IN_PANTRY else skip
        return IngestResult(
            items = listOf(
                proposed("Blueberries", "blueberries", "produce", "170 g clamshell · 1 cup", skip = SkipReason.OPTIONAL),
                proposed("All-purpose flour", "flour", "aisle-2-baking", "2.5 kg bag · 2 cups", skip = pantryOr(SkipReason.NONE, "Flour"), pantryEntry = if (withPantry) "Flour" else null),
                proposed("White sugar", "sugar", "aisle-2-baking", "2 kg bag · 2 tbsp", skip = pantryOr(SkipReason.NONE, "Sugar"), pantryEntry = if (withPantry) "Sugar" else null),
                proposed("Baking powder", "baking_soda", "aisle-2-baking", "225 g tin · 2 tsp", skip = pantryOr(SkipReason.NONE, "Baking powder"), pantryEntry = if (withPantry) "Baking powder" else null),
                proposed("Salt", "salt", "aisle-2-baking", "1 kg box · ½ tsp", skip = pantryOr(SkipReason.NONE, "Salt"), pantryEntry = if (withPantry) "Salt" else null),
                proposed("Pure vanilla extract", "vanilla", "aisle-2-baking", "43 mL bottle · 1 tsp", skip = SkipReason.OPTIONAL),
                proposed("Milk, 2 %", "milk_bag", "dairy-eggs", "4 L bag · 1½ cups", skip = SkipReason.ALREADY_ON_LIST),
                proposed("Eggs", "eggs", "dairy-eggs", "dozen · 2"),
                proposed("Unsalted butter", "butter", "dairy-eggs", "454 g brick · 3 tbsp"),
                proposed("Maple syrup", "maple_syrup", "breakfast", "540 mL tin · to serve", skip = SkipReason.OPTIONAL),
            ),
            message = "",
            sources = listOf(WebSource("https://www.example.ca/recipes/pancakes", "Fluffy pancakes")),
            skippedDuplicates = listOf("Milk, 2 %"),
            meta = meta(webSearches = 1, queries = listOf(PANCAKE_QUERY)),
            recipe = RecipeInfo("Pancakes from scratch", recipesCompared = 3, servings = 12),
        )
    }

    /** A photographed fridge list (§4.19): a correction, a crossed-out line, a bare paper number. */
    fun photo(): IngestResult = IngestResult(
        items = listOf(
            proposed("Milk", "milk_bag", "dairy-eggs", "4 L bag", named = true),
            proposed("Eggs", "eggs", "dairy-eggs", "dozen", named = true),
            proposed("Sourdough", "sliced_bread", "bakery", "800 g boule", skip = SkipReason.ALREADY_ON_LIST, named = true),
            proposed("Bananas", "banana", "produce", "6 bananas", named = true),
            proposed("Old cheddar", "cheddar", "deli-meat", "400 g block", named = true),
            proposed("Tomatoes", "canned_tomatoes", null, "796 mL can", named = true),
            proposed("Dish soap", "dish_soap", "household", "740 mL", skip = SkipReason.ALREADY_ON_LIST, named = true),
        ),
        message = "",
        sources = emptyList(),
        skippedDuplicates = listOf("Sourdough", "Dish soap"),
        meta = meta(),
        lines = listOf(
            PhotoLine("milk (bag)", crossedOut = false, correctionOf = null, itemIndex = 0),
            PhotoLine("eggs ×12", crossedOut = false, correctionOf = null, itemIndex = 1),
            PhotoLine("sourdough", crossedOut = false, correctionOf = "bread", itemIndex = 2),
            PhotoLine("bananas", crossedOut = false, correctionOf = null, itemIndex = 3),
            PhotoLine("old cheddar", crossedOut = false, correctionOf = null, itemIndex = 4),
            PhotoLine("chips", crossedOut = true, correctionOf = null, itemIndex = null),
            PhotoLine("tomatoes 796", crossedOut = false, correctionOf = null, itemIndex = 5),
            PhotoLine("dish soap", crossedOut = false, correctionOf = null, itemIndex = 6),
        ),
    )

    const val TRANSCRIPT = "…oh and frozen corn, and the big jug of litter for Mochi…"

    /** The voice note after the photo (D19): two more things. */
    fun voice(): IngestResult = IngestResult(
        items = listOf(
            proposed("Frozen corn", "frozen_vegetables", "frozen", "750 g bag", named = true),
            proposed("Cat litter", "cat_litter", "household", "9 kg jug", named = true),
            proposed("Eggs", "eggs", "dairy-eggs", "dozen", named = true),
        ),
        message = "",
        sources = emptyList(),
        skippedDuplicates = emptyList(),
        meta = meta(),
    )

    /** A review state as the ViewModel builds it. */
    fun review(id: Long, result: IngestResult, source: ItemSource, query: String?, st: AppState = state, revealed: Boolean = true): AddReview {
        val r = AddRules.review(id, result, source, query, st.store.aisles(), st.store.aisleStyles)
        return if (revealed) r.copy(revealed = setOf(id)) else r
    }

    /** A hand-drawn lined-paper list for the polaroid (a test stand-in for her photo). */
    fun paperPhoto(): ImageBitmap {
        val w = 396
        val h = 396
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFFFFFBF0.toInt())
        val rule = Paint().apply { color = 0xFFBCD0FF.toInt(); strokeWidth = 3f }
        var y = 52f
        while (y < h) {
            canvas.drawLine(0f, y, w.toFloat(), y, rule)
            y += 42f
        }
        canvas.drawLine(48f, 0f, 48f, h.toFloat(), Paint().apply { color = 0xFFFF9CC2.toInt(); strokeWidth = 3f })
        val ink = Paint().apply {
            color = 0xFF2B3A8C.toInt()
            textSize = 30f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        val lines = listOf("milk (bag)", "eggs x12", "bread  sourdough", "bananas", "old cheddar", "chips", "tomatoes 796", "dish soap")
        lines.forEachIndexed { i, text -> canvas.drawText(text, 62f, 44f + i * 42f, ink) }
        val strike = Paint().apply { color = 0xFF2B3A8C.toInt(); strokeWidth = 3f }
        canvas.drawLine(60f, 44f + 5 * 42f - 10f, 150f, 44f + 5 * 42f - 8f, strike)
        canvas.drawLine(60f, 44f + 2 * 42f - 10f, 132f, 44f + 2 * 42f - 9f, strike)
        return bmp.asImageBitmap()
    }
}

/**
 * The list behind a sheet in the goldens: the binder, the app bar and the LCD (the list itself is WP5's), under a 24 dp
 * stand-in for the status bar. The sheet hears where the app bar ends ([LocalAppBarHeight]), as the List screen tells it.
 */
@Composable
fun SheetStage(content: @Composable () -> Unit) {
    var barBottom by remember { mutableStateOf<Dp?>(null) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        PicnicBackground {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                BinderTopBar(
                    title = "Saturday shop",
                    subtitle = "Hillcrest Market · 8 stops",
                    lcd = LcdState(count = 10, total = 24),
                    onMore = {},
                    modifier = Modifier.onGloballyPositioned { c ->
                        barBottom = with(density) { (c.positionInRoot().y + c.size.height).toDp() }
                    },
                )
            }
        }
        CompositionLocalProvider(LocalAppBarHeight provides barBottom) { content() }
    }
}

/** The goldens' crop of a stage smaller than the screen. */
const val STAGE_TAG: String = "add_stage"

/** The bar docked at the bottom of a strip of binder [height] tall (its goldens crop to it). */
@Composable
fun BarStage(height: Dp = 180.dp, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().height(height).testTag(STAGE_TAG)) {
        PicnicBackground { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { content() } }
    }
}
