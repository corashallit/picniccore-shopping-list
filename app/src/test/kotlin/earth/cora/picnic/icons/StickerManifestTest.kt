package earth.cora.picnic.icons

import android.content.Context
import android.graphics.drawable.VectorDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.ui.theme.VectorArtCache
import earth.cora.picnic.ui.theme.gen.StickerManifest
import earth.cora.picnic.ui.theme.gen.StickerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The library sticker set (WP1, docs/DESIGN_STICKER.md §3.2; checklist items 28 and 35): one generated `sk_i_<key>`
 * vector per catalog key, each with the five named groups, and kawaii faces only as separate overlays that list
 * stickers never draw.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StickerManifestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun everyKeyHasVector() {
        val keys = IconCatalog.default.keys
        assertEquals("catalog keys (§3.2: the 235 of today plus peameal_bacon)", 236, keys.size)
        assertTrue("keys still missing art: ${StickerManifest.missing}", StickerManifest.missing.isEmpty())
        val unbundled = keys.filterNot { StickerManifest.isBundled(it) }
        assertTrue("catalog keys with no sk_i_ vector: $unbundled", unbundled.isEmpty())
        assertTrue("the capsule sentinel", StickerManifest.isBundled(StickerManifest.CAPSULE))
        assertEquals("one entry per catalog key plus the capsule", keys.size + 1, StickerManifest.entries.size)
        for (entry in StickerManifest.entries) {
            val name = context.resources.getResourceEntryName(entry.drawable)
            assertEquals(entry.key, "sk_i_" + entry.key.trimStart('_'), name)
            assertEquals(entry.key, StickerSource.DSL, entry.source)
            assertTrue(entry.key, entry.families.size in 1..2)
            val d = ContextCompat.getDrawable(context, entry.drawable)
            assertTrue(entry.key, d is VectorDrawable && d.intrinsicWidth > 0)
            val art = VectorArtCache.get(context.resources, entry.drawable)
            assertEquals(entry.key, 52f, art.viewport.width)
            for (group in StickerManifest.GROUPS) assertTrue("${entry.key} has no `$group` group", art.has(group))
        }
    }

    @Test
    fun facesOnlyWhereAllowed() {
        val withFaces = StickerManifest.entries.filter { it.hasFace }
        assertTrue("some album stickers have a face overlay", withFaces.isNotEmpty())
        for (entry in StickerManifest.entries) {
            // A list sticker's own vector never carries a face: the overlay is its own `sk_f_` drawable.
            val art = VectorArtCache.get(context.resources, entry.drawable)
            assertTrue("${entry.key}: sk_i_ vector carries a face", art.paths.none { it.group == "face" || it.name == "face" })
            val face = entry.face ?: continue
            assertNotEquals(entry.key, entry.drawable, face)
            assertEquals(entry.key, "sk_f_" + entry.key.trimStart('_'), context.resources.getResourceEntryName(face))
            assertTrue(entry.key, VectorArtCache.get(context.resources, face).has("face"))
        }
        // Only the album, achievements, empty states (and the kit's album sample) ask ItemIcon for its face.
        val sources = listOf(File("src/main/kotlin"), File("app/src/main/kotlin")).first { it.isDirectory }
        val allowed = Regex("""/ui/(album|achievements?|empty)/|AlbumPieces\.kt$|EmptyState\w*\.kt$|ThemePreviews\.kt$""")
        val offenders = sources.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                if (Regex("""\bface\s*=\s*true""").containsMatchIn(line.substringBefore("//")) && !allowed.containsMatchIn(f.invariantSeparatorsPath)) {
                    "${f.name}:${i + 1}"
                } else {
                    null
                }
            }
        }.toList()
        assertTrue("faces drawn outside the album, achievements and empty states: $offenders", offenders.isEmpty())
    }
}
