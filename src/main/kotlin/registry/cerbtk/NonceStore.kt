package registry.cerbtk

import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

object NonceStore {
    private val random = SecureRandom()
    private val ttlSeconds: Long by lazy {
        System.getProperty("cerbtk.nonceTtlSeconds", "300").toLong()
    }
    private val store = mutableMapOf<String, NonceEntry>()

    data class NonceEntry(val nonce: String, val expiresAt: Instant)

    fun issue(deviceId: String): NonceEntry {
        val nonce = generateNonce()
        val entry = NonceEntry(nonce, Instant.now().plusSeconds(ttlSeconds))
        synchronized(store) {
            store[deviceId] = entry
        }
        return entry
    }

    fun verify(deviceId: String, nonce: String): Boolean {
        val now = Instant.now()
        synchronized(store) {
            store.entries.removeIf { it.value.expiresAt.isBefore(now) }
            val entry = store[deviceId] ?: return false
            if (entry.expiresAt.isBefore(now)) {
                store.remove(deviceId)
                return false
            }
            if (entry.nonce != nonce) return false
            store.remove(deviceId)
            return true
        }
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
