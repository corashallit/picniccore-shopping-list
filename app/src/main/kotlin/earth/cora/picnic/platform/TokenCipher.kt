package earth.cora.picnic.platform

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the API token with a non-exportable AES-256 key in AndroidKeyStore (AES/GCM/NoPadding).
 * Stored form: base64(iv) + ":" + base64(ciphertext+tag). Never log either part.
 * (androidx.security:security-crypto / EncryptedSharedPreferences is deprecated since 1.1.0.)
 */
class TokenCipher(private val alias: String = "picnic_token_key") {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

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

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(cipher.iv) + ":" + b64(ct)
    }

    /** Returns null if the blob can't be decrypted (key wiped/invalidated, restored backup, tampering). */
    fun decrypt(stored: String): String? = try {
        val (iv, ct) = stored.split(':', limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
            .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        String(cipher.doFinal(ct), Charsets.UTF_8)
    } catch (e: KeyPermanentlyInvalidatedException) {
        keyStore.deleteEntry(alias); null
    } catch (e: GeneralSecurityException) {
        null // includes AEADBadTagException
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
