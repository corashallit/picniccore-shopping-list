package earth.cora.picnic.ui.common

/**
 * The store's own aisle number at the start of an aisle name, for the aisle medallion:
 * "Aisle 2 - Baking & spices" -> 2, "12 Frozen" -> 12, "Allée 4 : conserves" -> 4, "Aisle 7" -> 7.
 * Names without one ("Produce", "Dairy & eggs") -> null, and the medallion shows a star.
 *
 * Why not the walking position: in the store people look for the aisle signs, and a "5" next to
 * "Aisle 1 - Breakfast" sends them to the wrong aisle. Walking order is already the list order.
 */
fun storeAisleNumber(name: String): Int? =
    STORE_NUMBER.find(name.trim())?.groupValues?.get(1)?.toIntOrNull()

private val STORE_NUMBER = Regex(
    """^(?:(?:aisle|isle|allée|allee|rayon|row)\s*)?#?\s*(\d{1,3})(?=\s*[-–—:.)]|\s|$)""",
    RegexOption.IGNORE_CASE,
)
