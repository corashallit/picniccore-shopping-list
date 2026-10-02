package earth.cora.picnic.ui.aisles

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.assistant.AisleSync
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.core.store.InMemoryStateStore
import earth.cora.picnic.ui.common.FakeAssistant
import earth.cora.picnic.ui.common.Samples
import earth.cora.picnic.ui.theme.PicnicTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Back with unsaved aisle edits asks first; a second Back dismisses the prompt instead of discarding. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AislesBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aDoubleBackNeverDropsTheDraft() {
        val store = InMemoryStateStore(Samples.shoppingState)
        val baskets = IconCatalog.default.baskets
        val vm = AislesViewModel(store, AisleSync(store, FakeAssistant(), baskets), baskets, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        var left = 0
        compose.setContent { PicnicTheme { AislesRoute(vm, onBack = { left++ }) } }
        compose.runOnIdle { vm.setText("Produce\nBakery\nFrozen") }
        compose.waitForIdle()

        fun back() {
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
        }
        back()
        compose.onNodeWithText("Save your aisle changes?").assertExists()
        back()
        compose.onNodeWithText("Save your aisle changes?").assertDoesNotExist()
        assertEquals("still on the screen", 0, left)
        assertEquals("the draft is intact", "Produce\nBakery\nFrozen", vm.state.value.text)

        back()
        compose.onNodeWithText("Discard").performClick()
        compose.waitForIdle()
        assertEquals(1, left)
    }
}
