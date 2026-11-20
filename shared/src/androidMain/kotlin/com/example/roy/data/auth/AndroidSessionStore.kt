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
    private val record = AndroidSecureRecord(context, "roy-session", "roy-session-key")
    override suspend fun readRecord() = record.read()
    override suspend fun replaceRecord(record: String) = this.record.replace(record)
    override suspend fun clearRecord() = record.clear()
}

internal class AndroidSecureRecord(context: Context, filename: String, private val alias: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, filename))
    private val keyStore by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }

    suspend fun read(): String? = withContext(Dispatchers.IO) {
        val encrypted = try {
            file.readFully()
        } catch (missing: FileNotFoundException) {
            if (file.baseFile.exists()) throw missing
            return@withContext null
        }
        if (encrypted.contentEquals(ClearedRecord)) return@withContext null
        check(encrypted.size > IV_SIZE)
        val key = checkNotNull(keyStore.getKey(alias, null) as? SecretKey)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, encrypted, 0, IV_SIZE))
        cipher.doFinal(encrypted, IV_SIZE, encrypted.size - IV_SIZE).decodeToString(throwOnInvalidSequence = true)
    }

    suspend fun replace(record: String): Unit = withContext(Dispatchers.IO) {
        val key = keyStore.getKey(alias, null) as? SecretKey ?: generateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        check(cipher.iv.size == IV_SIZE)
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

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
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
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }
        .generateKey()

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        val ClearedRecord = byteArrayOf(0)
    }
}
