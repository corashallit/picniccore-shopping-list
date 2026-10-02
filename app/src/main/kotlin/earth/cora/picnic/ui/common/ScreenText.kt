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
    const val MIC_DENIED = "Kirakira Basket needs the microphone to hear your list. You can allow it in Android settings."
    const val CAMERA_UNAVAILABLE = "No camera app was found. Choose a photo from the gallery instead."
    const val SAVE_FAILED = "Couldn't save. Try again."
    const val NOTHING_NEW = "Everything there is already on your list."
}

/**
 * True when the fix is in Settings: no OpenAI key yet, or a key OpenAI rejected or that lacks a
 * permission (the UI then offers "Open Settings").
 */
internal fun LlmError.needsSettings(): Boolean = this is LlmError.NotConfigured || this is LlmError.Unauthorized
