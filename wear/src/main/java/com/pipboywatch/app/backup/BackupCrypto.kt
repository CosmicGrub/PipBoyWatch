package com.pipboywatch.app.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encryption for the backup system's restore tier — deliberately
 * independent of DatabasePassphrase/SQLCipher (at-rest DB encryption and
 * app-level backup encryption stay orthogonal; this file never touches
 * the DB passphrase).
 *
 * PBKDF2WithHmacSHA256 rather than scrypt: scrypt has no built-in JDK/
 * Android provider, so using it would mean pulling in Bouncy Castle for
 * one KDF call. PBKDF2 at a high iteration count is still a current
 * OWASP-recommended choice and ships in every Android version this app
 * targets — the right tradeoff for a personal backup file that isn't
 * defending against a well-resourced targeted attacker with ASIC/GPU
 * farms, which is specifically the threat scrypt's memory-hardness is
 * for.
 *
 * File layout: [salt(16 bytes)][iv(12 bytes)][AES-256-GCM ciphertext+tag]
 * GCM's authentication tag means a wrong passphrase or a corrupted/
 * tampered file both surface as AEADBadTagException from decrypt() —
 * there is no way to "successfully" decrypt to garbage.
 */
object BackupCrypto {
    private const val PBKDF2_ITERATIONS = 210_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return salt + iv + cipher.doFinal(plaintext)
    }

    /** Throws javax.crypto.AEADBadTagException on a wrong passphrase or a
     * corrupted/tampered file. */
    fun decrypt(data: ByteArray, passphrase: CharArray): ByteArray {
        require(data.size > SALT_BYTES + GCM_IV_BYTES) { "Backup file is too short to be valid" }
        val salt = data.copyOfRange(0, SALT_BYTES)
        val iv = data.copyOfRange(SALT_BYTES, SALT_BYTES + GCM_IV_BYTES)
        val ciphertext = data.copyOfRange(SALT_BYTES + GCM_IV_BYTES, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
