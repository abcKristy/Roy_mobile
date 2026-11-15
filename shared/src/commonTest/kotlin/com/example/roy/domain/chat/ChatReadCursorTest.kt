package com.example.roy.domain.chat

import com.example.roy.domain.auth.AuthError
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatReadCursorTest {
    @Test
    fun writesOnlyVisibleSequencesWithinTheServerBoundAndUsesCanonicalCursor() = runTest {
        val attempts = mutableListOf<Long>()
        val cursor = ChatReadCursor { sequence ->
            attempts += sequence
            AuthResult.Success(12)
        }
        cursor.advance(14, 13)
        cursor.advance(9, 13)
        cursor.advance(11, 13)
        assertEquals(listOf(9L), attempts)
    }

    @Test
    fun failedWriteCanBeRetriedWhileTheMessageIsStillVisible() = runTest {
        var attempts = 0
        val cursor = ChatReadCursor {
            ++attempts
            if (attempts == 1) AuthResult.Failure(AuthError(AuthErrorCode.Network))
            else AuthResult.Success(it)
        }
        cursor.advance(7, 8)
        cursor.advance(7, 8)
        cursor.advance(7, 8)
        assertEquals(2, attempts)
    }
}
