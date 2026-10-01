package earth.cora.picnic.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MaskTokenTest {
    @Test
    fun openAiKeysKeepPrefixAndLastFour() {
        assertEquals("sk-…wxyz", maskToken("sk-proj-abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun proxyTokensKeepUnderscorePrefix() {
        assertEquals("pk_…7890", maskToken("pk_live_abcdef1234567890"))
    }

    @Test
    fun shortTokensRevealLess() {
        assertEquals("…ef", maskToken("abcdef12ef"))
        assertEquals("…", maskToken("abc12"))
    }

    @Test
    fun neverContainsTheMiddleOfTheToken() {
        val token = "sk-proj-SECRETSECRETSECRET-tail"
        assertFalse(maskToken(token).contains("SECRET"))
    }
}
