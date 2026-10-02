package earth.cora.picnic.ui.a11y

import android.app.Application
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The §15 semantics helpers: decor carries nothing TalkBack reads (only the test marker), and the screen-reader
 * state the Next card and celebration depend on follows touch exploration.
 */
@RunWith(AndroidJUnit4::class)
class SemanticsHelpersTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun decorHasNoSemantics() {
        rule.setContent {
            Column {
                Text("Saturday shop")
                Box(Modifier.decorative().semantics { contentDescription = "sparkle" }.clickable {}) {
                    Text("♡ picnic day ☆")
                }
            }
        }
        rule.onNodeWithText("Saturday shop").assertIsDisplayed()
        // The merged tree is what TalkBack walks: nothing inside the decor is in it.
        rule.onAllNodesWithText("♡ picnic day ☆").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("sparkle").assertCountEquals(0)
        val decor = rule.onAllNodes(SemanticsMatcher.keyIsDefined(Decorative))
        decor.assertCountEquals(1)
        val config = decor.fetchSemanticsNodes().single().config
        assertFalse("decor is never a tappable look-alike", config.contains(SemanticsActions.OnClick))
        assertFalse(config.contains(SemanticsProperties.ContentDescription))
        assertFalse(config.contains(SemanticsProperties.Text))
    }

    @Test
    fun screenReaderFollowsTouchExploration() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.getSystemService(AccessibilityManager::class.java)).setTouchExplorationEnabled(true)
        var on: Boolean? = null
        rule.setContent { on = isScreenReaderOn() }
        rule.waitForIdle()
        assertEquals(true, on)
    }

    @Test
    fun screenReaderOffByDefault() {
        var on: Boolean? = null
        rule.setContent { on = isScreenReaderOn() }
        rule.waitForIdle()
        assertEquals(false, on)
    }
}
