package earth.cora.picnic.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/**
 * A compose rule's content can be set once per test; tests that walk several fixtures swap them through this instead.
 */
class SwappableContent(private val rule: ComposeContentTestRule) {
    private var content: @Composable () -> Unit by mutableStateOf({})
    private var started = false

    fun show(next: @Composable () -> Unit) {
        content = next
        if (!started) {
            started = true
            rule.setContent { content() }
        }
        rule.waitForIdle()
    }
}
