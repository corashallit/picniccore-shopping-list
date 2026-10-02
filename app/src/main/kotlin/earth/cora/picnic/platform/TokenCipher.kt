package earth.cora.picnic.platform

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.UnrecoverableEntryException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Reversible encryption of a short secret into a printable blob. Production: [TokenCipher]
 * (AndroidKeyStore). AndroidKeyStore doesn't exist under Robolectric, so tests use a fake.
 */
interface SecretCipher {
    /** Returns the stored form. Throws [GeneralSecurityException] if the key is unusable. */
    fun encrypt(plain: String): String

    /**
     * Returns null only when [stored] can never be decrypted (key invalidated or replaced, restored
     * backup, tampering, a malformed blob). Throws [KeystoreUnavailableException] when the Keystore
     * fails in a way that may be temporary (busy keystore2, early boot): the blob must be kept then.
     */
    fun decrypt(stored: String): String?
}

/** The Keystore could not be used right now; the stored token may still be readable later. */
class KeystoreUnavailableException(cause: Throwable) : IOException("Keystore unavailable (${cause.javaClass.simpleName})", cause)

/**
 * Encrypts the API token with a non-exportable AES-256 key in AndroidKeyStore (AES/GCM/NoPadding).
 * Stored form: base64(iv) + ":" + base64(ciphertext+tag). Never log either part.
 * (androidx.security:security-crypto / EncryptedSharedPreferences is deprecated since 1.1.0.)
 * No StrongBox and no user-authentication requirement: slow, flaky and pointless for an API token.
 */
class TokenCipher(private val alias: String = "picnic_token_key") : SecretCipher {
    // Lazy: a transient failure to open the Keystore surfaces as KeystoreUnavailableException from
    // decrypt (or GeneralSecurityException from encrypt), never from the constructor.
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    private fun key(): SecretKey =
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: generate()

    private fun generate(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Randomized encryption is the default: the Keystore picks the IV; we must not supply one.
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        } catch (e: GeneralSecurityException) {
            if (e !is KeyPermanentlyInvalidatedException && e !is UnrecoverableKeyException && e !is UnrecoverableEntryException) throw e
            // The old key is unusable (e.g. lock screen removed on some OEMs): the user is saving a
            // new token anyway, so start over with a new key.
            keyStore.deleteEntry(alias)
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        }
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(cipher.iv) + ":" + b64(ct)
    }

    override fun decrypt(stored: String): String? {
        val parts = stored.split(':', limit = 2)
        if (parts.size != 2) return null
        val (iv, ct) = try {
            parts.map { Base64.decode(it, Base64.NO_WRAP) }
        } catch (e: IllegalArgumentException) {
            return null // bad base64: the blob is damaged for good
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
                .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv)) }
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: KeyPermanentlyInvalidatedException) {
            runCatching { keyStore.deleteEntry(alias) }
            null
        } catch (e: AEADBadTagException) {
            // Wrong key (the old one was wiped and key() made a new one), restored backup, tampering.
            null
        } catch (e: GeneralSecurityException) {
            // KeyStoreException, UnrecoverableEntryException, ProviderException's checked cousins:
            // possibly transient, so the blob is kept.
            throw KeystoreUnavailableException(e)
        } catch (e: RuntimeException) {
            // ProviderException and other keystore2 failures (busy, system error, early boot).
            throw KeystoreUnavailableException(e)
        }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
