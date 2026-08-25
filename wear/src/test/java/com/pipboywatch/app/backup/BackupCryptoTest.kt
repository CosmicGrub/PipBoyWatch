package com.pipboywatch.app.backup

import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {

    @Test
    fun `encrypt then decrypt with the right passphrase recovers the original bytes`() {
        val plaintext = "the wasteland is a scary place".toByteArray(Charsets.UTF_8)
        val encrypted = BackupCrypto.encrypt(plaintext, "correct horse battery staple".toCharArray())
        val decrypted = BackupCrypto.decrypt(encrypted, "correct horse battery staple".toCharArray())
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `wrong passphrase throws AEADBadTagException rather than returning garbage`() {
        val encrypted = BackupCrypto.encrypt("secret".toByteArray(), "right-passphrase".toCharArray())
        assertThrows(AEADBadTagException::class.java) {
            BackupCrypto.decrypt(encrypted, "wrong-passphrase".toCharArray())
        }
    }

    @Test
    fun `two encryptions of the same plaintext produce different ciphertext`() {
        // Confirms salt+IV are actually randomized per call, not reused.
        val a = BackupCrypto.encrypt("same input".toByteArray(), "pw".toCharArray())
        val b = BackupCrypto.encrypt("same input".toByteArray(), "pw".toCharArray())
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun `corrupted ciphertext is rejected rather than silently decrypting`() {
        val encrypted = BackupCrypto.encrypt("secret".toByteArray(), "pw".toCharArray())
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1] + 1).toByte()
        assertThrows(AEADBadTagException::class.java) {
            BackupCrypto.decrypt(encrypted, "pw".toCharArray())
        }
    }
}
