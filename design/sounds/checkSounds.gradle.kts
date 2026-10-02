// :app:checkSounds (docs/DESIGN_STICKER.md §14.2, checklist item 275): re-renders the ten clips from
// design/sounds/make_sounds.py in memory and fails when a committed res/raw/sk_*.wav differs from its recipe (or a stale
// clip is left behind). Pure Python 3 (math + wave), no packages. Applied by app/build.gradle.kts. Set PICNIC_PYTHON to
// use a particular interpreter (default python3). To fix drift, run `python3 design/sounds/make_sounds.py` and commit.
val soundsPython = providers.environmentVariable("PICNIC_PYTHON").orElse("python3")
val soundsRoot = rootDir

tasks.register<Exec>("checkSounds") {
    group = "verification"
    description = "Fails when the sound clips in res/raw drift from their recipes in design/sounds/make_sounds.py."
    workingDir = soundsRoot
    commandLine(soundsPython.get(), "design/sounds/make_sounds.py", "--check")
}
