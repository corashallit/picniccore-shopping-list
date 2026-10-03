package earth.cora.picnic.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sqrt

/*
 * Egg 4's shake (docs/DESIGN_STICKER.md §12.2): two acceleration peaks over 2.7 g within 500 ms. Pure detection
 * ([ShakeDetector], ShakeDetectorTest) and the phone's accelerometer while the list is resumed ([ShakeEffect]). The
 * engine adds the rest of the rule (list in the foreground, reduced motion off, at most once per 5.2 s). Owner: WP9.
 */

/**
 * Counts peaks: a sample whose magnitude rises above [thresholdG] (in g, gravity included) starts a peak, which ends when
 * it falls back below it. The second peak to START within [windowMs] of the first is a shake; detection then starts
 * over (a third peak needs a new first). Times are the sensor's, in ms.
 */
class ShakeDetector(
    private val thresholdG: Float = 2.7f,
    private val windowMs: Long = 500,
    private val onShake: () -> Unit,
) {
    private var inPeak = false
    private var firstPeakAt: Long? = null

    /** One accelerometer sample: [x], [y], [z] in m/s² at [timeMs]. */
    fun onSample(x: Float, y: Float, z: Float, timeMs: Long) {
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        onMagnitude(g, timeMs)
    }

    /** One sample by its magnitude in g. */
    fun onMagnitude(g: Float, timeMs: Long) {
        val above = g > thresholdG
        if (above && !inPeak) peak(timeMs)
        inPeak = above
    }

    private fun peak(timeMs: Long) {
        val first = firstPeakAt
        if (first != null && timeMs - first <= windowMs) {
            firstPeakAt = null
            onShake()
        } else {
            firstPeakAt = timeMs
        }
    }

    /** Forgets any half-seen shake (the screen paused). */
    fun reset() {
        inPeak = false
        firstPeakAt = null
    }
}

/**
 * Listens to the accelerometer while the screen is resumed and [enabled], calling [onShake] for each shake. No
 * accelerometer (some tablets, Robolectric): nothing happens. Game rate, unregistered on pause.
 */
@Composable
fun ShakeEffect(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val shake by rememberUpdatedState(onShake)
    val detector = remember { ShakeDetector { shake() } }
    DisposableEffect(enabled, lifecycle, context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                detector.onSample(event.values[0], event.values[1], event.values[2], event.timestamp / 1_000_000)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var registered = false
        fun register(on: Boolean) {
            if (manager == null || sensor == null) return
            if (on && !registered) registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            if (!on && registered) {
                manager.unregisterListener(listener)
                registered = false
                detector.reset()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register(enabled)
                Lifecycle.Event.ON_PAUSE -> register(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) register(enabled)
        onDispose {
            lifecycle.removeObserver(observer)
            register(false)
        }
    }
}
