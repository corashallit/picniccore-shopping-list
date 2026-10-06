package earth.cora.picnic.settings

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checklist 316 (docs/DESIGN_STICKER.md §0.4 Q6): the merged manifest asks for approximate location only. No
 * ACCESS_FINE_LOCATION, no ACCESS_BACKGROUND_LOCATION; and nothing else location-, camera- or tracking-related
 * slipped in (PRIVACY.md lists the permissions). ACCESS_NETWORK_STATE (§0.4, 5 October: the offline experience) is a
 * normal permission: no prompt.
 */
@RunWith(AndroidJUnit4::class)
class ManifestPermissionsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun requested(): Set<String> =
        app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()

    @Test
    fun approximateLocationOnly() {
        val permissions = requested()
        assertTrue(Manifest.permission.ACCESS_COARSE_LOCATION in permissions)
        assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in permissions)
        assertFalse(Manifest.permission.ACCESS_BACKGROUND_LOCATION in permissions)
    }

    @Test
    fun thePermissionsPrivacyMdLists() {
        val ours = requested().filterNot { it.endsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }.toSet()
        assertEquals(
            setOf(
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            ours,
        )
    }
}
