package earth.cora.picnic.platform

import android.util.Log

/**
 * Tiny logging seam. Never pass tokens, request bodies or item lists here: only ids, sizes,
 * class names and outcomes. On a plain JVM (unit tests, where android.util.Log is an unmocked stub)
 * it falls back to stderr so failures in background work stay visible.
 */
internal object PicnicLog {
    private const val TAG = "Picnic"

    fun i(message: String) {
        runCatching { Log.i(TAG, message) }.onFailure { System.err.println("$TAG I $message") }
    }

    fun w(message: String, error: Throwable? = null) {
        // Only the class name of the error: messages can echo server text.
        val suffix = error?.let { " (${it.javaClass.simpleName})" }.orEmpty()
        runCatching { Log.w(TAG, message + suffix) }.onFailure { System.err.println("$TAG W $message$suffix") }
    }
}
