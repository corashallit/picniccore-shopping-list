package earth.cora.picnic.core.store

import earth.cora.picnic.core.model.STATE_SCHEMA_VERSION
import kotlinx.serialization.json.JsonObject

/**
 * Migration hook for state.json. Each step turns the raw JSON of version N into version N+1
 * (and sets "version" to N+1). A file without "version" is version 1.
 * To change the schema: bump STATE_SCHEMA_VERSION, add a step here, add a fixture test.
 */
object StateMigrations {
    /** Index i migrates version (i + 1) -> (i + 2). Empty while STATE_SCHEMA_VERSION == 1. */
    val steps: List<(JsonObject) -> JsonObject> = emptyList()

    /** Runs every step from [fromVersion] up to STATE_SCHEMA_VERSION. Versions above current are returned unchanged. */
    fun migrate(raw: JsonObject, fromVersion: Int): JsonObject {
        var json = raw
        for (version in fromVersion until STATE_SCHEMA_VERSION) {
            json = steps[version - 1](json)
        }
        return json
    }
}
