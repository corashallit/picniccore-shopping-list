package earth.cora.picnic.ui.theme

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The visible label of a PicnicTextField is announced by the editable node itself, so TalkBack says
 * "Amount, 2, edit box" instead of a bare "2, edit box" (and doesn't read the label twice).
 */
@RunWith(AndroidJUnit4::class)
class PicnicTextFieldSemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theLabelBelongsToTheEditableNode() {
        rule.setContent {
            PicnicTheme { PicnicTextField(value = "2", onValueChange = {}, label = "Amount") }
        }
        rule.onNode(hasSetTextAction() and hasContentDescription("Amount")).assertTextEquals("2")
        rule.onAllNodesWithText("Amount").assertCountEquals(0)
    }
}
