// :app:checkStickers (docs/DESIGN_STICKER.md §18.3 step 1, checklist items 29-32): regenerates the sticker drawables
// (res/drawable/sk_*.xml) and the generated manifests (ui/theme/gen/*.kt) in memory from the DSL in design/stickers/,
// runs the palette, gloss, geometry and basket validators, and fails on any lint error or any drift from the files in
// the tree. Applied by app/build.gradle.kts. Needs Python 3.11+ with design/stickers/requirements.txt installed;
// set PICNIC_PYTHON to use a particular interpreter (default python3). To fix drift, run
// `python3 design/stickers/build_android.py` and commit the result.
val stickersPython = providers.environmentVariable("PICNIC_PYTHON").orElse("python3")
val stickersRoot = rootDir

tasks.register<Exec>("checkStickers") {
    group = "verification"
    description = "Fails when the generated sticker art drifts from the DSL in design/stickers/ or breaks its validators."
    workingDir = stickersRoot
    commandLine(stickersPython.get(), "design/stickers/build_android.py", "--check")
}
