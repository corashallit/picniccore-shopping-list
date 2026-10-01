package earth.cora.picnic.ui.common

import earth.cora.picnic.core.llm.LlmError

/**
 * Messages that ViewModels put into UI state (AddPhase.Failed, ConnectionTestUi.Failed, ...).
 * They sit next to core's LlmError.friendlyMessage, which is English too, so every message a
 * ViewModel produces comes from code and ViewModels never hold a Context. Labels, titles and
 * formatted counts live in res/values/strings_screens.xml.
 */
internal object ScreenText {
    const val PHOTO_UNREADABLE = "Couldn't open that photo. Try another one."
    const val NOTHING_HEARD = "Didn't catch that. Try again a little closer to the mic."
    const val TOO_SHORT = "That was a bit short. Tap the mic and say your list again."
    const val MIC_UNAVAILABLE = "Couldn't start the microphone. Is another app using it?"
    const val MIC_DENIED = "Picnic List needs the microphone to hear your list. You can allow it in Android settings."
    const val CAMERA_UNAVAILABLE = "No camera app was found. Choose a photo from the gallery instead."
    const val SAVE_FAILED = "Couldn't save. Try again."
    const val BASE_URL_INVALID = "Use an https:// address, like https://picnic.cora.earth/v1"
    const val NOTHING_NEW = "Everything there is already on your list."
    const val TOKEN_FOR_NEW_SERVER = "New address saved. Paste the key or token for it: the old one was removed so it is never sent to a different server."
}

/**
 * True when the fix is in Settings: no token, a rejected token, or a base URL that isn't an
 * OpenAI-compatible API (the UI then offers "Open Settings").
 */
internal fun LlmError.needsSettings(): Boolean = when (this) {
    is LlmError.NotConfigured, is LlmError.Unauthorized -> true
    is LlmError.BadResponse -> code == LlmError.ENDPOINT_NOT_FOUND
    else -> false
}
