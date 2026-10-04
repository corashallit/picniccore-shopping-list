package earth.cora.picnic.ui.a11y

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

/*
 * What TalkBack is handed (docs/DESIGN_STICKER.md §15): the AccessibilityNodeInfo tree Compose builds for each window on
 * screen, read through the windows' own AccessibilityNodeProvider as an accessibility service receives it, so Compose's
 * own rules apply (merged nodes, the extra child nodes it adds for a merged node's label and role, nodes covered by a
 * scrim or a scene left out, hidden and transparent nodes left out). From it, the stops TalkBack's swipe lands on and
 * what it says at each: the node's own label, else its non-focusable descendants' words in order, then its role and
 * state. The TalkBack and decor audits read these, not the test-only semantics tree.
 */

/** One place TalkBack's focus can land, and what it says there. */
class Stop(
    /** What TalkBack reads: the node's own label, else its non-focusable descendants' words, in order. */
    val label: String,
    /** The semantics node it is (its virtual view id). */
    val id: Int,
    /** The node's role as TalkBack names it ("Button", "CheckBox", "Switch", …), or null for plain content. */
    val role: String?,
    val state: String?,
    val clickable: Boolean,
    val longClickable: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val enabled: Boolean,
    val heading: Boolean,
    /** Custom actions (the actions menu), by label. */
    val actions: List<String>,
    /** The label of a double tap, when the node names it ("Open trip book"). */
    val clickLabel: String?,
    val bounds: Rect,
    val liveRegion: Int,
    val paneTitle: String?,
    /** The window it is in (0: the screen, then popups). */
    val window: Int,
    /** A text field's hint (its label, when the field holds it), as TalkBack reads it after "Edit box". */
    val hint: String? = null,
    /** A password field: TalkBack reads its length, never its masked text. */
    val password: Boolean = false,
) {
    val actionable: Boolean get() = enabled && (clickable || longClickable || checkable)

    override fun toString(): String = buildString {
        append('"').append(label).append('"')
        role?.let { append(" (").append(it).append(')') }
        state?.let { append(" [").append(it).append(']') }
        if (checkable) append(if (checked) " {checked}" else " {not checked}")
        if (actions.isNotEmpty()) append(" actions=").append(actions)
        if (!enabled) append(" disabled")
        hint?.let { append(" hint=\"").append(it).append('"') }
        if (password) append(" password")
    }
}

object TalkBackTree {
    /** Turns TalkBack on for the test's phone (touch exploration, as TalkBack does), or off. */
    fun screenReader(on: Boolean) {
        val manager = ApplicationProvider.getApplicationContext<android.content.Context>().getSystemService(AccessibilityManager::class.java)
        shadowOf(manager).setEnabled(on)
        shadowOf(manager).setTouchExplorationEnabled(on)
    }

    /** Every window's root view (the screen first, then popups). */
    private fun rootViews(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val field = global.getDeclaredField("mViews").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return (field.get(instance) as List<View>).toList().filter { it.isAttachedToWindow }
    }

    private fun composeViews(v: View, out: MutableList<View>) {
        if (v.javaClass.name.endsWith("AndroidComposeView")) {
            out += v
            return
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) composeViews(v.getChildAt(i), out)
    }

    private val getChildId by lazy { AccessibilityNodeInfo::class.java.getDeclaredMethod("getChildId", Int::class.javaPrimitiveType).apply { isAccessible = true } }

    /**
     * Compose's extra child nodes for a merged node's role (ids 10⁹ + id) and label (2 × 10⁹ + id): TalkBack reads them
     * as part of their parent, never as stops of their own.
     */
    private const val FAKE_IDS = 1_000_000_000
    private const val FAKE_LABEL_IDS = 2_000_000_000

    private class Info(val id: Int, val info: AccessibilityNodeInfo, val children: List<Info>)

    private fun tree(): List<Info> {
        val views = ArrayList<View>()
        rootViews().forEach { composeViews(it, views) }
        return views.mapNotNull { view ->
            val provider = view.accessibilityNodeProvider ?: return@mapNotNull null
            fun build(id: Int, depth: Int): Info? {
                if (depth > 200) return null
                val info = provider.createAccessibilityNodeInfo(id) ?: return null
                val kids = (0 until info.childCount).mapNotNull { i -> build(((getChildId.invoke(info, i) as Long) ushr 32).toInt(), depth + 1) }
                return Info(id, info, kids)
            }
            build(View.NO_ID, 0)
        }
    }

    /** The node's own words: its label, else its text (never a password field's masked text). */
    private fun AccessibilityNodeInfo.own(): String? =
        (contentDescription?.toString() ?: text?.toString()?.takeUnless { isPassword })?.trim()?.takeIf { it.isNotEmpty() }

    private fun roleOf(className: CharSequence?): String? =
        className?.toString()?.substringAfterLast('.')?.takeIf { it != "View" && it != "ViewGroup" }

    private fun isStop(n: Info, focusableAncestor: Boolean): Boolean {
        val i = n.info
        if (!i.isVisibleToUser || n.id >= FAKE_IDS) return false
        if (i.isScreenReaderFocusable || i.isFocusable || i.isClickable || i.isLongClickable || i.isCheckable) return true
        // Words with nothing focusable above them are a stop of their own.
        return !focusableAncestor && i.own() != null
    }

    /**
     * The semantics node ids Android hands TalkBack (every window): the nodes not covered by a scrim, a sheet or a scene
     * drawn over them, not hidden and not transparent. Needs [screenReader] on.
     */
    fun uncoveredIds(): Set<Int> {
        val out = HashSet<Int>()
        fun add(n: Info) {
            if (n.id in 0 until FAKE_IDS) out += n.id
            n.children.forEach(::add)
        }
        tree().forEach(::add)
        return out
    }

    /** Every place TalkBack can land, window by window, in the provider's order. */
    fun stops(): List<Stop> {
        val out = ArrayList<Stop>()
        tree().forEachIndexed { window, root ->
            fun words(n: Info, into: MutableList<String>) {
                for (c in n.children) {
                    if (!c.info.isVisibleToUser) continue
                    if (c.id < FAKE_IDS && isStop(c, true)) continue
                    c.info.own()?.let { into += it }
                    c.info.stateDescription?.toString()?.let { into += it }
                    words(c, into)
                }
            }
            fun walk(n: Info, focusableAncestor: Boolean) {
                val stop = isStop(n, focusableAncestor)
                if (stop) {
                    val i = n.info
                    val hint = i.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    // A field with no words of its own is named by its hint ("Your OpenAI key, edit box").
                    val label = i.own() ?: mutableListOf<String>().also { words(n, it) }.joinToString(", ").ifEmpty { hint.orEmpty() }
                    val role = roleOf(i.className) ?: n.children.firstOrNull { it.id in FAKE_IDS until FAKE_LABEL_IDS }?.let { roleOf(it.info.className) }
                    val standard = setOf(AccessibilityNodeInfo.ACTION_CLICK, AccessibilityNodeInfo.ACTION_LONG_CLICK)
                    out += Stop(
                        label = label,
                        id = n.id,
                        role = role,
                        state = i.stateDescription?.toString(),
                        clickable = i.isClickable,
                        longClickable = i.isLongClickable,
                        checkable = i.isCheckable,
                        checked = i.isChecked,
                        enabled = i.isEnabled,
                        heading = i.isHeading,
                        actions = i.actionList.filter { it.id !in standard && it.label != null && it.id > 0x01FFFFFF }.map { it.label.toString() },
                        clickLabel = i.actionList.firstOrNull { it.id == AccessibilityNodeInfo.ACTION_CLICK }?.label?.toString(),
                        bounds = Rect().also { i.getBoundsInScreen(it) },
                        liveRegion = i.liveRegion,
                        paneTitle = i.paneTitle?.toString(),
                        window = window,
                        hint = hint,
                        password = i.isPassword,
                    )
                }
                val focusable = focusableAncestor || (stop && (n.info.isFocusable || n.info.isClickable || n.info.isScreenReaderFocusable))
                n.children.forEach { walk(it, focusable) }
            }
            walk(root, false)
        }
        return out
    }
}
