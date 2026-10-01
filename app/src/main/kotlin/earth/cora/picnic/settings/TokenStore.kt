package earth.cora.picnic.settings

/**
 * Persistence for the encrypted token. Production: KeystoreTokenStore (app-shell) writes
 * TokenCipher's "iv:ciphertext" blob to noBackupFilesDir/token.bin. AndroidKeyStore does not exist
 * under Robolectric, so tests use [InMemoryTokenStore].
 */
interface TokenStore {
    suspend fun read(): String?
    suspend fun write(token: String?)
}

class InMemoryTokenStore(private var token: String? = null) : TokenStore {
    override suspend fun read(): String? = token
    override suspend fun write(token: String?) {
        this.token = token
    }
}
