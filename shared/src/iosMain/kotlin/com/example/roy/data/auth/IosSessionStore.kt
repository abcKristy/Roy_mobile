@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.example.roy.data.auth

import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

internal class IosSessionStore : SecureSessionStore() {
    private val record = IosSecureRecord("com.example.roy.session", "current-session")
    override suspend fun readRecord() = record.read()
    override suspend fun replaceRecord(record: String) = this.record.replace(record)
    override suspend fun clearRecord() = record.clear()
}

internal class IosSecureRecord(private val service: String, private val account: String) {
    suspend fun read(): String? = withContext(Dispatchers.Default) {
        memScoped {
            val result = alloc<CPointerVarOf<CFTypeRef>>()
            val fields = query + mapOf(
                key(kSecReturnData) to true,
                key(kSecMatchLimit) to key(kSecMatchLimitOne),
            )
            val status = withDictionary(fields) { SecItemCopyMatching(it, result.ptr) }
            if (status == errSecItemNotFound) return@memScoped null
            check(status == errSecSuccess)
            val data = CFBridgingRelease(result.value) as NSData
            checkNotNull(NSString.create(data, NSUTF8StringEncoding)).toString()
        }
    }

    suspend fun replace(record: String): Unit = withContext(Dispatchers.Default) {
        val bytes = record.encodeToByteArray()
        val data = bytes.usePinned {
            NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong())
        }
        val attributes = mapOf(key(kSecValueData) to data)
        val insertion = query + attributes +
            (key(kSecAttrAccessible) to key(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly))
        val status = withDictionary(insertion) { SecItemAdd(it, null) }
        if (status == errSecDuplicateItem) {
            withDictionary(query) { item ->
                withDictionary(attributes) { value -> check(SecItemUpdate(item, value) == errSecSuccess) }
            }
        } else {
            check(status == errSecSuccess)
        }
    }

    suspend fun clear(): Unit = withContext(Dispatchers.Default) {
        val status = withDictionary(query, ::SecItemDelete)
        check(status == errSecSuccess || status == errSecItemNotFound)
    }

    private val query get() = mapOf(
        key(kSecClass) to key(kSecClassGenericPassword),
        key(kSecAttrService) to service,
        key(kSecAttrAccount) to account,
    )

    private fun key(value: CFTypeRef?): String {
        // The constants are immortal CFStrings. Bridge ownership for the ARC conversion.
        return CFBridgingRelease(platform.CoreFoundation.CFRetain(value)) as String
    }

    private fun <T> withDictionary(fields: Map<String, Any>, operation: (CFDictionaryRef) -> T): T {
        val reference = checkNotNull(CFBridgingRetain(fields))
        return try {
            operation(reference.reinterpret())
        } finally {
            CFRelease(reference)
        }
    }
}
