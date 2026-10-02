package earth.cora.picnic.icons

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BespokeIconCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val bytes = byteArrayOf(1, 2, 3)

    @Test
    fun putLookupForgetAndPersist() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val cache = BespokeIconCache(tmp.root, io)
        assertNull(cache.lookup("gochujang"))
        assertNull(cache.put("gochujang", "c-1", bytes))
        assertEquals("c-1", cache.lookup("gochujang"))
        assertTrue(cache.fileFor("c-1").isFile)

        // A second instance reads index.json from disk.
        assertEquals("c-1", BespokeIconCache(tmp.root, io).lookup("gochujang"))

        assertEquals("c-1", cache.put("gochujang", "c-2", bytes))
        assertEquals("c-2", cache.lookup("gochujang"))

        cache.forget("gochujang")
        assertNull(cache.lookup("gochujang"))
        assertTrue("files stay until swept", cache.fileFor("c-2").isFile)
    }

    @Test
    fun lookupIgnoresMissingFiles() = runTest {
        val cache = BespokeIconCache(tmp.root, StandardTestDispatcher(testScheduler))
        cache.put("kimchi", "c-9", bytes)
        cache.fileFor("c-9").delete()
        assertNull(cache.lookup("kimchi"))
    }

    @Test
    fun blockList() = runTest {
        val cache = BespokeIconCache(tmp.root, StandardTestDispatcher(testScheduler))
        assertFalse(cache.isBlocked("x"))
        cache.block("x")
        cache.block("x")
        assertTrue(cache.isBlocked("x"))
        assertTrue(BespokeIconCache(tmp.root, StandardTestDispatcher(testScheduler)).isBlocked("x"))
    }

    @Test
    fun dailyCapResetsEachDayAndRefunds() = runTest {
        val cache = BespokeIconCache(tmp.root, StandardTestDispatcher(testScheduler))
        repeat(3) { assertTrue(cache.tryConsumeDaily("2026-10-01", cap = 3)) }
        assertFalse(cache.tryConsumeDaily("2026-10-01", cap = 3))
        cache.refundDaily("2026-10-01")
        assertTrue(cache.tryConsumeDaily("2026-10-01", cap = 3))
        assertTrue(cache.tryConsumeDaily("2026-10-02", cap = 3))
    }

    @Test
    fun sweepRemovesOnlyOldUnreferencedFiles() = runTest {
        val cache = BespokeIconCache(tmp.root, StandardTestDispatcher(testScheduler))
        cache.put("a", "c-a", bytes) // in the index: kept
        val dir = cache.customDir
        val referenced = File(dir, "c-ref.webp").apply { writeBytes(bytes) }
        val orphan = File(dir, "c-old.webp").apply { writeBytes(bytes) }
        val fresh = File(dir, "c-new.webp").apply { writeBytes(bytes) }
        val now = 10_000_000L
        listOf(referenced, orphan, cache.fileFor("c-a")).forEach { it.setLastModified(now - 3_600_000) }
        fresh.setLastModified(now - 1_000)

        assertEquals(1, cache.sweep(referencedIds = setOf("c-ref"), now = now))
        assertFalse(orphan.exists())
        assertTrue(referenced.exists())
        assertTrue(fresh.exists())
        assertTrue(cache.fileFor("c-a").exists())
    }

    @Test
    fun corruptIndexStartsFresh() = runTest {
        File(tmp.root, "index.json").writeText("{not json")
        val cache = BespokeIconCache(tmp.root, StandardTestDispatcher(testScheduler))
        assertNull(cache.lookup("a"))
        cache.put("a", "c-a", bytes)
        assertEquals("c-a", cache.lookup("a"))
    }

    @Test
    fun iconIdsAreStableShapedAndUnique() {
        val id = BespokeIconCache.newIconId("gochujang", nowMs = 1_759_330_000_000)
        assertTrue(id, id.matches(Regex("c-[0-9a-f]{10}-[0-9a-z]+")))
        val taken = setOf(id)
        val next = BespokeIconCache.newIconId("gochujang", nowMs = 1_759_330_000_000) { it in taken }
        assertNotEquals(id, next)
    }
}
