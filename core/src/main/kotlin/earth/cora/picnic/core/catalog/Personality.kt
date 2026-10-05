package earth.cora.picnic.core.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How a sticker hops into its basket (docs/DESIGN_STICKER.md §8.3 "Personality hops"). The catalog carries
 * it per icon (`"personality"`, owner WP3) and WP1's generated StickerManifest repeats it per drawable;
 * the check-off chain (WP6's ToggleEvent) reads it. Persisted by serial name in the catalog JSON.
 */
@Serializable
enum class Personality {
    @SerialName("none") NONE,
    @SerialName("berry") BERRY,
    @SerialName("egg") EGG,
    @SerialName("bread") BREAD,
    @SerialName("can") CAN,
    @SerialName("frozen") FROZEN,
}
