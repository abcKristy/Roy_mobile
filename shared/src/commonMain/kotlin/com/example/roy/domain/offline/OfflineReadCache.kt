package com.example.roy.domain.offline

import com.example.roy.domain.chat.ChatDetail
import com.example.roy.domain.chat.ChatListItem
import com.example.roy.domain.chat.ChatMessage
import com.example.roy.domain.map.Point
import com.example.roy.domain.map.PointFilter
import com.example.roy.domain.map.Position
import com.example.roy.domain.map.ViewportQuery
import kotlinx.serialization.Serializable
import kotlin.time.Instant

internal interface PinCacheStore {
    suspend fun readPins(query: ViewportQuery): CachedPins?
    suspend fun lastPins(filter: PointFilter): CachedPins?
    suspend fun savePins(query: ViewportQuery, points: List<Point>, center: Position, zoom: Double)
    suspend fun evictPoint(pointId: String)
}

internal interface ChatCacheStore {
    suspend fun readChatList(userId: String): CachedChatList?
    suspend fun saveChatList(userId: String, rows: List<ChatListItem>, complete: Boolean, refresh: Boolean)
    suspend fun readRoom(userId: String, chatId: String): CachedRoom?
    suspend fun saveRoom(userId: String, detail: ChatDetail, messages: List<ChatMessage>, nextBeforeSeq: Long?, refresh: Boolean)
    suspend fun evictRoom(userId: String, chatId: String)
    suspend fun clearPrivate()
}

@Serializable
internal data class CachedPins(
    val query: ViewportQuery,
    val points: List<Point>,
    val fetchedAt: Instant,
    val center: Position,
    val zoom: Double,
)

@Serializable
internal data class CachedChatList(val rows: List<ChatListItem>, val fetchedAt: Instant)

@Serializable
internal data class CachedRoom(
    val detail: ChatDetail,
    val messages: List<ChatMessage>,
    val fetchedAt: Instant,
    val nextBeforeSeq: Long? = null,
)
