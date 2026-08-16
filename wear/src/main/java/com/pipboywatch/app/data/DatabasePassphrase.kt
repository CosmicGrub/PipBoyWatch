package com.pipboywatch.app.data

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

private const val PREFS_NAME = "pipboy_db_key"
private const val KEY_PASSPHRASE = "passphrase_b64"
private const val PASSPHRASE_BYTES = 32

/**
 * Generates (once) and retrieves the SQLCipher passphrase for
 * PipBoyDatabase. The passphrase is random, never hardcoded, and is itself
 * stored only inside EncryptedSharedPreferences — which wraps it with a key
 * held in the Android Keystore, so the passphrase is never written to disk
 * in plaintext either.
 */
object DatabasePassphrase {
    fun getOrCreate(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val existing = prefs.getString(KEY_PASSPHRASE, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }

        val fresh = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_PASSPHRASE, Base64.encodeToString(fresh, Base64.NO_WRAP)).apply()
        return fresh
    }
}
