package earth.cora.picnic.core

import kotlinx.serialization.json.Json

/** The one Json configuration used for state.json, the catalog and API payload decoding. */
object CoreJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }
}
