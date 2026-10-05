package earth.cora.picnic.assets

import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import earth.cora.picnic.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.core.splashscreen.R as SplashR

/**
 * Checklist item 50 (WP2, docs/DESIGN_STICKER.md §2.5): the splash screen is the Day binder (`#E6F6EE`) behind the
 * picnic hamper sticker (the launcher's foreground layer), and the night splash is the Glow binder (`#161A3E`) behind
 * the same sticker.
 */
@RunWith(AndroidJUnit4::class)
class SplashThemeTest {
    @Test
    fun daySplashIsTheMintBinderAndTheHamper() = assertSplash(0xFFE6F6EE.toInt())

    @Test
    @Config(qualifiers = "night")
    fun nightSplashIsTheGlowBinderAndTheHamper() = assertSplash(0xFF161A3E.toInt())

    private fun assertSplash(binder: Int) {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Picnic_Starting)
        val value = TypedValue()
        context.theme.resolveAttribute(SplashR.attr.windowSplashScreenBackground, value, true)
        assertEquals(Integer.toHexString(binder), Integer.toHexString(value.data))
        context.theme.resolveAttribute(SplashR.attr.windowSplashScreenAnimatedIcon, value, true)
        assertEquals(R.drawable.sk_l_launcher_foreground, value.resourceId)
        context.theme.resolveAttribute(SplashR.attr.postSplashScreenTheme, value, true)
        assertEquals(R.style.Theme_Picnic, value.resourceId)
    }
}
