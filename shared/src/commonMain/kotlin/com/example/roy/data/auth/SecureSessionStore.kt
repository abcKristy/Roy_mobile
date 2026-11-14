package com.example.roy.data.auth

import com.example.roy.domain.auth.AuthSession
import com.example.roy.domain.auth.SessionStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Each platform replaces the encoded snapshot as a single confidential record. */
internal abstract class SecureSessionStore : SessionStore {
    private val recordLock = Mutex()

    final override suspend fun read(): AuthSession? = recordLock.withLock {
        readRecord()?.let(AuthSessionCodec::decode)
    }

    final override suspend fun replace(session: AuthSession) = recordLock.withLock {
        replaceRecord(AuthSessionCodec.encode(session))
    }

    final override suspend fun clear() = recordLock.withLock { clearRecord() }

    protected abstract suspend fun readRecord(): String?
    protected abstract suspend fun replaceRecord(record: String)
    protected abstract suspend fun clearRecord()
}
