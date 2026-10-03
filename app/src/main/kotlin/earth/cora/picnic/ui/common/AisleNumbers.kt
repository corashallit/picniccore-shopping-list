package earth.cora.picnic.ui.common

/**
 * The store's own aisle number at the start of an aisle name (D9, the "A2" chip): "Aisle 2 - Baking & spices" -> 2,
 * "12 Frozen" -> 12, "Allée 4 : conserves" -> 4, "Aisle 7" -> 7; names without one ("Produce", "Dairy & eggs") -> null.
 *
 * A delegate to core's [earth.cora.picnic.core.model.storeAisleNumber] (WP3, §19.1), kept only because the v1
 * ui/list/ListUiState.kt (WP5) still imports it from here; WP7's Add sheet already uses core's. WP8 deletes this file
 * (and AisleNumbersTest) once WP5 switches that import to `earth.cora.picnic.core.model.storeAisleNumber`. New code
 * imports core's directly.
 */
fun storeAisleNumber(name: String): Int? = earth.cora.picnic.core.model.storeAisleNumber(name)
