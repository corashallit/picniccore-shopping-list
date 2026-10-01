package earth.cora.picnic.settings

import earth.cora.picnic.platform.KeystoreUnavailableException
import earth.cora.picnic.platform.SecretCipher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import java.security.ProviderException

class KeystoreTokenStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Reversible stand-in for the AndroidKeyStore cipher (not available on the JVM). */
    private class FakeCipher(var failEncrypt: Boolean = false, var decryptFailure: Exception? = null) : SecretCipher {
        override fun encrypt(plain: String): String {
            if (failEncrypt) throw GeneralSecurityException("keystore says no")
            return "iv:" + plain.reversed().map { it + 1 }.joinToString("")
        }

        override fun decrypt(stored: String): String? {
            decryptFailure?.let { throw it }
            return if (!stored.startsWith("iv:")) null else stored.removePrefix("iv:").map { it - 1 }.joinToString("").reversed()
        }
    }

    @Test
    fun roundTripsAndNeverWritesPlaintext() = runTest {
        val file = File(tmp.root, "token.bin")
        val s = KeystoreTokenStore(file, { FakeCipher() }, StandardTestDispatcher(testScheduler))
        s.write("sk-proj-secret-1234")
        assertTrue(file.exists())
        assertFalse(file.readText().contains("secret"))
        assertEquals("sk-proj-secret-1234", s.read())
        assertFalse(File(tmp.root, "token.bin.tmp").exists())
    }

    @Test
    fun nullClearsTheFile() = runTest {
        val file = File(tmp.root, "token.bin")
        val s = KeystoreTokenStore(file, { FakeCipher() }, StandardTestDispatcher(testScheduler))
        s.write("pk_abc")
        s.write(null)
        assertFalse(file.exists())
        assertNull(s.read())
    }

    @Test
    fun undecryptableBlobReadsAsUnsetAndIsDeleted() = runTest {
        val file = File(tmp.root, "token.bin").apply { writeText("garbage-from-another-device") }
        val s = KeystoreTokenStore(file, { FakeCipher() }, StandardTestDispatcher(testScheduler))
        assertNull(s.read())
        assertFalse(file.exists())
    }

    @Test
    fun aTransientKeystoreFailureKeepsTheBlob() = runTest {
        val file = File(tmp.root, "token.bin")
        val cipher = FakeCipher()
        val s = KeystoreTokenStore(file, { cipher }, StandardTestDispatcher(testScheduler))
        s.write("sk-proj-secret-1234")
        for (failure in listOf(KeystoreUnavailableException(KeyStoreException("busy")), ProviderException("keystore2 restarting"))) {
            cipher.decryptFailure = failure
            try {
                s.read()
                throw AssertionError("expected IOException for ${failure.javaClass.simpleName}")
            } catch (e: IOException) {
                assertTrue("the token survives a ${failure.javaClass.simpleName}", file.exists())
            }
        }
        cipher.decryptFailure = null
        assertEquals("sk-proj-secret-1234", s.read())
    }

    @Test
    fun keystoreFailureOnWriteIsAnIOException() = runTest {
        val file = File(tmp.root, "token.bin")
        val s = KeystoreTokenStore(file, { FakeCipher(failEncrypt = true) }, StandardTestDispatcher(testScheduler))
        try {
            s.write("sk-x")
            throw AssertionError("expected IOException")
        } catch (e: IOException) {
            assertFalse(file.exists())
            assertFalse(e.message.orEmpty().contains("sk-x"))
        }
    }

    @Test
    fun missingFileIsNoToken() = runTest {
        val s = KeystoreTokenStore(File(tmp.root, "none.bin"), { FakeCipher() }, StandardTestDispatcher(testScheduler))
        assertNull(s.read())
    }
}
