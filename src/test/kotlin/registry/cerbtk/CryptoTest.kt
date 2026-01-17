package registry.cerbtk

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

internal class CryptoTest {

    @Test
    fun `Verify signed payload, valid signature returns true`() {
        val keysFile = java.io.File("config/trusted_keys.txt")
        keysFile.parentFile.mkdirs()

        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()

        keysFile.writeText(Base64.getEncoder().encodeToString(keyPair.public.encoded))
        System.setProperty("cerbtk.trustedKeysPath", keysFile.path)

        val payload = SignedRegistrationPayload(
            deviceId = "device-123",
            owner = "owner-abc",
            timestamp = "2026-01-17T10:00:00Z",
            publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded),
            signature = "",
            firmwareHash = "sha256:abc",
            buildId = "yocto-2026-01-17",
            recipe = "core-image-minimal",
            boardRev = "rev-a",
            algorithm = "SHA256withRSA"
        )

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(keyPair.private)
        signature.update(canonicalMessage(payload).toByteArray())
        val signed = signature.sign()

        val signedPayload = payload.copy(
            signature = Base64.getEncoder().encodeToString(signed)
        )

        assertTrue(verifySignedPayload(signedPayload))

        keysFile.delete()
    }

    @Test
    fun `Verify signed payload, invalid signature returns false`() {
        val keysFile = java.io.File("config/trusted_keys.txt")
        keysFile.parentFile.mkdirs()

        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()

        keysFile.writeText(Base64.getEncoder().encodeToString(keyPair.public.encoded))
        System.setProperty("cerbtk.trustedKeysPath", keysFile.path)

        val payload = SignedRegistrationPayload(
            deviceId = "device-123",
            owner = "owner-abc",
            timestamp = "2026-01-17T10:00:00Z",
            publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded),
            signature = Base64.getEncoder().encodeToString("bad".toByteArray()),
            algorithm = "SHA256withRSA"
        )

        assertFalse(verifySignedPayload(payload))

        keysFile.delete()
    }
}
