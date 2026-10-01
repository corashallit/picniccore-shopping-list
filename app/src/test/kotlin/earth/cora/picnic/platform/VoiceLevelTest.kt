package earth.cora.picnic.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLevelTest {
    @Test
    fun levelIsPerceptualAndClamped() {
        assertEquals(0f, VoiceRecorder.amplitudeToLevel(0), 0f)
        assertEquals(1f, VoiceRecorder.amplitudeToLevel(32_767), 1e-4f)
        assertEquals(1f, VoiceRecorder.amplitudeToLevel(99_999), 1e-4f)
        // -20 dBFS (amplitude ~3277) maps to 0.6 on the -50..0 dB scale.
        assertEquals(0.6f, VoiceRecorder.amplitudeToLevel(3_277), 0.01f)
        assertTrue(VoiceRecorder.amplitudeToLevel(30) < 0.1f)
    }
}
