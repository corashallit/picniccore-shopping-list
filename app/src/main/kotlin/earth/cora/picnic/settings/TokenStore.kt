package earth.cora.picnic.settings

import android.content.Context
import earth.cora.picnic.platform.KeystoreUnavailableException
import earth.cora.picnic.platform.PicnicLog
import earth.cora.picnic.platform.SecretCipher
import earth.cora.picnic.platform.TokenCipher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException

/**
 * Persistence for the encrypted token. Production: [KeystoreTokenStore] writes TokenCipher's
 * "iv:ciphertext" blob to noBackupFilesDir/token.bin. AndroidKeyStore does not exist under
 * Robolectric, so tests use [InMemoryTokenStore] (or KeystoreTokenStore with a fake cipher).
 */
interface TokenStore {
    /**
     * The decrypted token, or null if none is stored or it can never be decrypted again (then the
     * blob is deleted). Throws [IOException] when it can't be read right now (file I/O, a transient
     * Keystore failure): the stored token is kept and may be readable on the next call.
     */
    suspend fun read(): String?

    /** Stores [token] encrypted; null clears it. Throws [IOException] if it couldn't be stored. */
    suspend fun write(token: String?)
}

class InMemoryTokenStore(private var token: String? = null) : TokenStore {
    override suspend fun read(): String? = token
    override suspend fun write(token: String?) {
        this.token = token
    }
}

/**
 * The token encrypted with an AndroidKeyStore AES-256-GCM key, stored in noBackupFilesDir/token.bin
 * (outside the backup allow-list; a restored blob couldn't be decrypted on another device anyway).
 * A blob that can never be decrypted (key invalidated, AEAD tag mismatch, malformed) is deleted and
 * reads as "no token"; a transient Keystore failure throws and keeps it.
 */
class KeystoreTokenStore(
    private val file: File,
    cipherFactory: () -> SecretCipher = { TokenCipher() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TokenStore {
    constructor(context: Context) : this(File(context.noBackupFilesDir, FILE_NAME))

    // Lazy: creating the Keystore handle is I/O, and the container must stay cheap to build.
    private val cipher: SecretCipher by lazy(cipherFactory)
    private val mutex = Mutex()

    override suspend fun read(): String? = withContext(io) {
        mutex.withLock {
            if (!file.exists()) return@withLock null
            // An IOException here (file, or KeystoreUnavailableException) propagates: the blob is kept.
            val blob = file.readText(Charsets.UTF_8).trim()
            val plain = try {
                cipher.decrypt(blob)
            } catch (e: IOException) {
                throw e
            } catch (e: GeneralSecurityException) {
                // A SecretCipher that doesn't classify its failures: assume they may be temporary.
                throw KeystoreUnavailableException(e)
            } catch (e: RuntimeException) {
                throw KeystoreUnavailableException(e)
            }
            if (plain == null) {
                PicnicLog.w("stored token can never be decrypted; clearing it")
                file.delete()
            }
            plain
        }
    }

    override suspend fun write(token: String?) = withContext(io) {
        mutex.withLock {
            if (token == null) {
                if (file.exists() && !file.delete()) throw IOException("Couldn't delete the token file")
                return@withLock
            }
            val blob = try {
                cipher.encrypt(token)
            } catch (e: GeneralSecurityException) {
                throw IOException("Couldn't encrypt the token", e)
            } catch (e: RuntimeException) {
                throw IOException("Keystore unavailable", e)
            }
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.outputStream().use { out ->
                out.write(blob.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            Unit
        }
    }

    companion object {
        const val FILE_NAME: String = "token.bin"
    }
}
