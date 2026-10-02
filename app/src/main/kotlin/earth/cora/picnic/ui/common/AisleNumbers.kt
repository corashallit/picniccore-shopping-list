package earth.cora.picnic.ui.common

/**
 * The store's own aisle number at the start of an aisle name, for the aisle medallion:
 * "Aisle 2 - Baking & spices" -> 2, "12 Frozen" -> 12, "Allée 4 : conserves" -> 4, "Aisle 7" -> 7.
 * Names without one ("Produce", "Dairy & eggs") -> null, and the medallion shows a star.
 *
 * Why not the walking position: in the store people look for the aisle signs, and a "5" next to
 * "Aisle 1 - Breakfast" sends them to the wrong aisle. Walking order is already the list order.
 *
 * Moved to core (earth.cora.picnic.core.model.storeAisleNumber, same regex, §19.1); this delegates until
 * WP8 deletes the file.
 */
fun storeAisleNumber(name: String): Int? = earth.cora.picnic.core.model.storeAisleNumber(name)
