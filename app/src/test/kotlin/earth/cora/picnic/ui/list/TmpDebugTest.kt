package earth.cora.picnic.ui.list

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.ui.common.PHONE_360
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_360)
class TmpDebugTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun openProduce() {
        val rig = ListRig()
        compose.show(rig)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(ListTags.strip("produce")).performClick()
        var last = ""
        for (f in 0 until 120) {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeByFrame()
            val focused = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            val p = rig.engine.current.value
            val header = compose.onAllNodes(hasTagPrefix("picnic_tray_header_")).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) + "@" + it.boundsInRoot.top.toInt() }
            val line = "f$f focus=$focused open=${p.open} scroll=${p.scroll} headers=$header state.open=${rig.vm.state.value.openSectionId}"
            if (line.substringAfter(" ") != last) println(line)
            last = line.substringAfter(" ")
        }
    }
}
