package com.example.roy.data.chat

import com.example.roy.data.auth.AuthHttpRequest
import com.example.roy.data.auth.AuthHttpResponse
import com.example.roy.data.auth.AuthHttpTransport
import com.example.roy.domain.auth.AuthErrorCode
import com.example.roy.domain.auth.AuthResult
import com.example.roy.domain.chat.ChatHistoryPage
import com.example.roy.domain.chat.ChatReadReply
import com.example.roy.domain.chat.ChatSearchPage
import com.example.roy.presentation.chat.mergeChatMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class ChatHistoryRemoteDataSourceTest {
    @Test
    fun historyRetainsServerCursorsAndDecodesSystemKind() = runTest {
        val transport = FixtureTransport(
            AuthHttpResponse(200, """{"items":[$SystemMessage],"nextBeforeSeq":7,"nextAfterSeq":null}"""),
        )
        val source = ChatHistoryRemoteDataSource(ORIGIN, transport)
        val result = assertIs<AuthResult.Success<ChatReadReply<ChatHistoryPage>>>(
            source.history(CHAT_ID, "test-bearer", beforeSeq = 10),
        )
        val page = assertIs<ChatReadReply.Data<ChatHistoryPage>>(result.value).value

        assertEquals("$ORIGIN/api/v1/chats/$CHAT_ID/messages?limit=30&beforeSeq=10", transport.request.url)
        assertEquals("test-bearer", transport.request.accessToken)
        assertEquals(7L, page.nextBeforeSeq)
        assertEquals("member_left", page.items.single().systemKind)
    }

    @Test
    fun searchPreservesLiteralUnicodeAndQueryDelimiters() = runTest {
        val transport = FixtureTransport(AuthHttpResponse(200, """{"items":[],"nextCursor":null}"""))
        val source = ChatHistoryRemoteDataSource(ORIGIN, transport)
        val result = assertIs<AuthResult.Success<ChatReadReply<ChatSearchPage>>>(
            source.search(CHAT_ID, "test-bearer", "я &+=%", "cursor.signature"),
        )

        assertIs<ChatReadReply.Data<ChatSearchPage>>(result.value)
        assertEquals(
            "$ORIGIN/api/v1/chats/$CHAT_ID/messages/search?limit=30&q=%D1%8F%20%26%2B%3D%25&cursor=cursor.signature",
            transport.request.url,
        )
    }

    @Test
    fun readDistinguishesNetworkFailureFromAuthAndResourceErrors() = runTest {
        val transport = FixtureTransport(null)
        val source = ChatHistoryRemoteDataSource(ORIGIN, transport)
        assertEquals(AuthErrorCode.Network, assertIs<AuthResult.Failure>(source.detail(CHAT_ID, "token")).error.code)
        transport.response = AuthHttpResponse(403, """{"error":{"code":"NOT_MEMBER"}}""")
        assertEquals(AuthResult.Success(ChatReadReply.NotMember), source.detail(CHAT_ID, "token"))
        transport.response = AuthHttpResponse(404, """{"error":{"code":"RESOURCE_GONE"}}""")
        assertEquals(AuthResult.Success(ChatReadReply.Gone), source.detail(CHAT_ID, "token"))
        transport.response = AuthHttpResponse(401, """{"error":{"code":"SESSION_EXPIRED"}}""")
        assertEquals(AuthErrorCode.SessionExpired, assertIs<AuthResult.Failure>(source.detail(CHAT_ID, "token")).error.code)
    }

    @Test
    fun duplicateDeliveryAndOlderPageShareTheSameCanonicalOrder() = runTest {
        val transport = FixtureTransport(
            AuthHttpResponse(200, """{"items":[$SystemMessage],"nextBeforeSeq":null,"nextAfterSeq":null}"""),
        )
        val source = ChatHistoryRemoteDataSource(ORIGIN, transport)
        val result = assertIs<AuthResult.Success<ChatReadReply<ChatHistoryPage>>>(source.history(CHAT_ID, "token"))
        val first = assertIs<ChatReadReply.Data<ChatHistoryPage>>(result.value).value.items.single()
        val newest = first.copy(id = "second", messageSeq = 12)
        assertEquals(listOf(newest, first), mergeChatMessages(listOf(newest), listOf(first, newest)))
        assertEquals(listOf(newest, first), mergeChatMessages(listOf(first, newest), listOf(newest)))
    }

    private class FixtureTransport(var response: AuthHttpResponse?) : AuthHttpTransport {
        lateinit var request: AuthHttpRequest
            private set

        override suspend fun execute(request: AuthHttpRequest): AuthHttpResponse? {
            this.request = request
            return response
        }
    }

    private companion object {
        const val ORIGIN = "http://127.0.0.1:18080"
        const val CHAT_ID = "887e4ede-8faa-4ca3-b8ed-22ec53c6cb7e"
        val SystemMessage = """
            {
                "id":"729ead3d-e908-4708-964a-295cdf6d4414","chatId":"$CHAT_ID","messageSeq":7,
                "type":"system","author":null,"text":null,"system":{"kind":"member_left","actorUserId":"unknown"},
                "createdAt":"2026-11-12T15:00:00Z"
            }
        """.trimIndent()
    }
}
