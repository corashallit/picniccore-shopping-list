# R8 rules for the (future) release build. Debug APKs are not minified.
#
# Nothing app-specific is needed today, and `./gradlew :app:assembleRelease` is verified to build:
# - kotlinx.serialization (state.json, icon catalog, type-safe navigation routes) ships its own
#   consumer rules for @Serializable classes and their generated serializers;
# - OkHttp, DataStore (protobuf-lite) and Compose ship consumer rules too;
# - core's resources (icon_catalog.json, prompts/*.txt) are Java resources loaded by name and are
#   packaged untouched (resource shrinking only applies to res/).
#
# If a release build ever fails at runtime with a missing serializer, add for example:
# -keep,includedescriptorclasses class earth.cora.picnic.**$$serializer { *; }
