# Picnic List

A picniccore grocery list for Android, styled after early-90s magical-girl anime.

## What it does

- **Walks the store with you.** You keep your store's aisles as a plain text list, in the order you walk them. New items are sorted into those aisles by an LLM, and the list is shown aisle by aisle. Collapsed aisles show up as little baskets holding their items.
- **Takes requests.** Type "ingredients for pancakes from scratch" and a reasoning model with web search turns it into the ingredients you need.
- **Reads your handwriting.** Photograph a handwritten list and it gets transcribed into items.
- **Listens.** Say what you need out loud; the recording is transcribed and sorted the same way.
- **Draws every item.** Each item gets its own little icon. Items without one in the bundled library can get a new icon drawn live in the same style.

## Status

Early work in progress. The visual direction is being chosen from three candidates; see [`design/concepts`](design/concepts/README.md).

## API keys

The app never ships with an API key. It talks to an OpenAI-compatible endpoint set in its settings, in one of two ways:

- **Proxy (recommended):** a small proxy (for example on `cora.earth`) holds the real key. It accepts only the endpoints the app uses and only per-device access tokens.
- **Your own key:** paste your own OpenAI key in settings. It is stored encrypted with the Android Keystore.

## Releasing

- **Sideloading:** every push builds an installable APK; tags `v*` attach it to a GitHub Release. See [docs/BUILDING.md](docs/BUILDING.md).
- **Google Play:** the [Play release workflow](.github/workflows/play-release.yml) builds the signed App Bundle and uploads it to a Play track (internal testing by default). The one-time setup, the first manual upload, and the Play Console forms are in [docs/RELEASING.md](docs/RELEASING.md).
- **Privacy:** [docs/PRIVACY.md](docs/PRIVACY.md) is the privacy policy draft, written to match what the app actually stores and sends.
