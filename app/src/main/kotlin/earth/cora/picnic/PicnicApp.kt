package earth.cora.picnic

import android.app.Application

/** Owns the manual DI graph. Keep onCreate cheap: Robolectric tests instantiate this class too. */
class PicnicApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
