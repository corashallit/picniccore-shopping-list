package earth.cora.picnic.settings

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Checklist 138 (docs/DESIGN_STICKER.md §7.2, ARCHITECTURE §6.1): `album.json` is on the backup allow-list next to
 * `state.json` (the lists and the pantry), in Auto Backup (API 26-30) and in both API 31+ sections, and nothing else is:
 * the key, the settings, the stored area ("Use my area") and the icon cache stay on the phone. Read from the compiled
 * resources the manifest points at.
 */
@RunWith(AndroidJUnit4::class)
class BackupRulesTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    /** Every `<include domain="file" path=…>` under [section] (null = anywhere) of the xml resource [id]. */
    private fun includes(id: Int, section: String? = null): List<String> {
        val parser = app.resources.getXml(id)
        val out = mutableListOf<String>()
        var inSection = section == null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when {
                    parser.name == section -> inSection = true
                    parser.name == "include" && inSection -> {
                        assertEquals("file", parser.getAttributeValue(null, "domain"))
                        out += parser.getAttributeValue(null, "path")
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == section) inSection = false
            }
        }
        return out
    }

    @Test
    fun autoBackupKeepsTheListsAndTheAlbum() {
        assertEquals(listOf("state.json", "album.json"), includes(R.xml.backup_rules))
    }

    @Test
    fun cloudBackupAndDeviceTransferKeepTheListsAndTheAlbum() {
        for (section in listOf("cloud-backup", "device-transfer")) {
            assertEquals(section, listOf("state.json", "album.json"), includes(R.xml.data_extraction_rules, section))
        }
    }

    @Test
    fun theManifestUsesTheseRules() {
        val info = app.packageManager.getApplicationInfo(app.packageName, 0)
        assertEquals(ApplicationInfo.FLAG_ALLOW_BACKUP, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
