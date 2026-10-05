package earth.cora.picnic.ui.theme

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.core.catalog.IconCatalog
import earth.cora.picnic.ui.theme.gen.StickerManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The sticker render path reads WP1's generated drawables by their named groups (§3.2, §3.3). */
@RunWith(AndroidJUnit4::class)
class VectorArtTest {
    private val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    @Test
    fun everyLibraryStickerHasItsGroupsAndHoloClip() {
        for (entry in StickerManifest.entries) {
            val art = VectorArtCache.get(resources, entry.drawable)
            for (g in listOf("cut", "fills", "lines")) assertTrue("${entry.key} lacks $g", art.paths.any { it.group == g })
            assertNotNull("${entry.key} lacks the dominant clip", art.groupClips["dominant"])
        }
    }

    @Test
    fun unknownAndPendingKeysResolveToTheCapsule() {
        val catalog = IconCatalog.default
        // The default key is the real "Shopping bag" sticker; rows show the capsule for it through displayRef.
        assertEquals("paper_bag", StickerLibrary.resolve(IconCatalog.DEFAULT_ICON, catalog)?.key)
        assertEquals(StickerManifest.CAPSULE, StickerLibrary.resolve(StickerManifest.CAPSULE, catalog)?.key)
        assertEquals("strawberry", StickerLibrary.resolve("strawberry", catalog)?.key)
        // A key without its own art yet falls back to its category's generic (or the capsule), never to nothing.
        assertEquals(StickerManifest.CAPSULE, StickerLibrary.resolve("definitely_not_a_key", catalog)?.key)
    }

    @Test
    fun capsulePathsAreNamedForRecolouring() {
        val capsule = StickerLibrary.capsule!!
        val art = VectorArtCache.get(resources, capsule.drawable)
        val names = art.paths.map { it.name }.toSet()
        assertTrue(names.containsAll(listOf("cap", "cap_p", "cap_tr")))
    }
}
