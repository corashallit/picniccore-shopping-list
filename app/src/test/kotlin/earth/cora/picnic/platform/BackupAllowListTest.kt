package earth.cora.picnic.platform

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The backup allow-lists name exactly the two files that hold her data, `state.json` and `album.json`
 * (docs/ARCHITECTURE.md §6.1, docs/DESIGN_STICKER.md §7.2), in Auto Backup (API 26-30) and in both API 31+ sections;
 * the token, the icon cache and the settings never leave the phone.
 */
class BackupAllowListTest {
    private val xml: File = listOf(File("src/main/res/xml"), File("app/src/main/res/xml")).first { it.isDirectory }

    private fun includes(text: String): List<String> =
        Regex("""<include\s+domain="file"\s+path="([^"]+)"\s*/>""").findAll(text).map { it.groupValues[1] }.toList()

    @Test
    fun autoBackupKeepsTheListsAndTheAlbum() {
        assertEquals(listOf("state.json", "album.json"), includes(File(xml, "backup_rules.xml").readText()))
    }

    @Test
    fun cloudBackupAndDeviceTransferKeepTheListsAndTheAlbum() {
        val text = File(xml, "data_extraction_rules.xml").readText()
        for (section in listOf("cloud-backup", "device-transfer")) {
            val body = text.substringAfter("<$section>").substringBefore("</$section>")
            assertEquals(section, listOf("state.json", "album.json"), includes(body))
        }
    }
}
