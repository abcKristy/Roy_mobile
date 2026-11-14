package com.example.roy.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class AndroidSessionStore(context: Context) : SecureSessionStore() {
    private val file = AtomicFile(File(context.noBackupFilesDir, "roy-session"))
    private val keyStore by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }

    override suspend fun readRecord(): String? = withContext(Dispatchers.IO) {
        val encrypted = try {
            file.readFully()
        } catch (missing: FileNotFoundException) {
            if (file.baseFile.exists()) throw missing
            return@withContext null
        }
        if (encrypted.contentEquals(ClearedRecord)) return@withContext null
        check(encrypted.size > IvSize)
        val key = checkNotNull(keyStore.getKey(Alias, null) as? SecretKey)
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TagBits, encrypted, 0, IvSize))
        cipher.doFinal(encrypted, IvSize, encrypted.size - IvSize).decodeToString(throwOnInvalidSequence = true)
    }

    override suspend fun replaceRecord(record: String): Unit = withContext(Dispatchers.IO) {
        val key = keyStore.getKey(Alias, null) as? SecretKey ?: generateKey()
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        check(cipher.iv.size == IvSize)
        val encrypted = cipher.iv + cipher.doFinal(record.encodeToByteArray())
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            stream.fd.sync()
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
        file.finishWrite(stream)
        check(file.readFully().contentEquals(encrypted))
    }

    override suspend fun clearRecord(): Unit = withContext(Dispatchers.IO) {
        // Overwrite the prior record atomically first, so a failed delete cannot restore a session.
        val stream = file.startWrite()
        try {
            stream.write(ClearedRecord)
            stream.fd.sync()
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
        file.finishWrite(stream)
        check(file.readFully().contentEquals(ClearedRecord))
        file.delete()
    }

    private fun generateKey(): SecretKey = KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        .apply {
            init(
                KeyGenParameterSpec.Builder(Alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }
        .generateKey()

    private companion object {
        const val Alias = "roy-session-key"
        const val Transformation = "AES/GCM/NoPadding"
        const val IvSize = 12
        const val TagBits = 128
        val ClearedRecord = byteArrayOf(0)
    }
}
