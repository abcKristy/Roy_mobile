package com.example.roy.data.auth

import com.example.roy.domain.auth.AuthSession
import com.example.roy.domain.auth.AuthUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class SecureSessionStoreTest {
    @Test
    fun replacesTheEntireSnapshotAndClearsItAsOneRecord() = runTest {
        val store = TestStore()
        val first = session("first")
        val second = session("second")

        assertNull(store.read())
        store.replace(first)
        assertEquals(first.accessToken, store.read()?.accessToken)
        assertEquals(first.refreshToken, store.read()?.refreshToken)

        store.replace(second)
        val read = checkNotNull(store.read())
        assertEquals(second.accessToken, read.accessToken)
        assertEquals(second.refreshToken, read.refreshToken)
        assertEquals(second.expiresAt, read.expiresAt)
        assertEquals(second.refreshExpiresAt, read.refreshExpiresAt)
        assertEquals(second.user.id, read.user.id)

        store.clear()
        assertNull(store.read())
        assertEquals(2, store.writes)
        assertEquals(1, store.clears)
    }

    @Test
    fun invalidRecordDoesNotProduceAPartialSession() = runTest {
        val store = TestStore()
        store.record = """{"accessToken":"fixture-only"}"""

        assertFails { store.read() }
    }

    private fun session(label: String): AuthSession {
        val issued = Instant.parse("2030-01-01T00:00:00Z")
        return AuthSession(
            "fixture-access-$label",
            Instant.parse("2030-01-01T00:15:00Z"),
            "fixture-refresh-$label",
            Instant.parse("2030-02-01T00:00:00Z"),
            AuthUser("fixture-$label", "Имя", null, "+123", 21, null, issued),
        )
    }

    private class TestStore : SecureSessionStore() {
        var record: String? = null
        var writes = 0
        var clears = 0

        override suspend fun readRecord() = record
        override suspend fun replaceRecord(record: String) {
            this.record = record
            writes++
        }
        override suspend fun clearRecord() {
            record = null
            clears++
        }
    }
}
