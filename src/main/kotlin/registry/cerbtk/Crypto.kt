package registry.cerbtk

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class SignedRegistrationPayload(
    val deviceId: String,
    val owner: String,
    val timestamp: String,
    val publicKey: String,
    val signature: String,
    val firmwareHash: String? = null,
    val buildId: String? = null,
    val recipe: String? = null,
    val boardRev: String? = null,
    val nonce: String? = null,
    val algorithm: String? = null
)

fun verifySignedPayload(payload: SignedRegistrationPayload): Boolean {
    return try {
        val algorithm = payload.algorithm
            ?: System.getProperty("cerbtk.crypto.algorithm", "SHA256withRSA")
        val trustedKeys = loadTrustedKeys(algorithm)
        if (trustedKeys.isEmpty()) return false
        val signature = Signature.getInstance(algorithm)
        val message = canonicalMessage(payload).toByteArray()
        val signatureBytes = Base64.getDecoder().decode(payload.signature)

        trustedKeys.any { key ->
            signature.initVerify(key)
            signature.update(message)
            signature.verify(signatureBytes)
        }
    } catch (ex: Exception) {
        false
    }
}

fun canonicalMessage(payload: SignedRegistrationPayload): String =
    payload.deviceId + "|" + payload.owner + "|" + payload.timestamp + "|" +
        (payload.firmwareHash ?: "") + "|" + (payload.buildId ?: "") + "|" +
        (payload.recipe ?: "") + "|" + (payload.boardRev ?: "") + "|" +
        (payload.nonce ?: "")

private fun loadTrustedKeys(algorithm: String): List<java.security.PublicKey> {
    val path = System.getProperty("cerbtk.trustedKeysPath", "config/trusted_keys.txt")
    val file = java.io.File(path)
    if (!file.exists()) return emptyList()
    val keyFactory = when {
        algorithm.contains("ECDSA", ignoreCase = true) -> KeyFactory.getInstance("EC")
        algorithm.contains("EC", ignoreCase = true) -> KeyFactory.getInstance("EC")
        else -> KeyFactory.getInstance("RSA")
    }
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            try {
                val keySpec = X509EncodedKeySpec(Base64.getDecoder().decode(line))
                keyFactory.generatePublic(keySpec)
            } catch (ex: Exception) {
                null
            }
        }
}
