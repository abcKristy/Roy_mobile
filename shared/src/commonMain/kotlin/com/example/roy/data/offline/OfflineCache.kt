package com.example.roy.data.offline

import com.example.roy.domain.chat.ChatDetail
import com.example.roy.domain.chat.ChatListItem
import com.example.roy.domain.chat.ChatMessage
import com.example.roy.domain.map.Point
import com.example.roy.domain.map.PointFilter
import com.example.roy.domain.map.Position
import com.example.roy.domain.map.ViewportQuery
import com.example.roy.domain.offline.CachedChatList
import com.example.roy.domain.offline.CachedPins
import com.example.roy.domain.offline.CachedRoom
import com.example.roy.domain.offline.ChatCacheStore
import com.example.roy.domain.offline.PinCacheStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

internal class OfflineCache(
    private val pins: OfflineRecord,
    private val chats: OfflineRecord,
    private val clock: Clock = Clock.System,
) : PinCacheStore, ChatCacheStore {
    private val pinLock = Mutex()
    private val chatLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun readPins(query: ViewportQuery): CachedPins? = pinLock.withLock {
        val records = read<PinRecords>(pins)?.entries.orEmpty()
        val b = query.bbox
        records.firstOrNull {
            val area = it.query.bbox
            it.query.filter == query.filter && area.west <= b.west && area.east >= b.east &&
                area.south <= b.south && area.north >= b.north
        }?.let { record ->
            record.copy(points = record.points.filter {
                it.position.longitude in b.west..b.east && it.position.latitude in b.south..b.north
            })
        }
    }

    override suspend fun lastPins(filter: PointFilter): CachedPins? = pinLock.withLock {
        read<PinRecords>(pins)?.entries?.firstOrNull { it.query.filter == filter }
    }

    override suspend fun savePins(query: ViewportQuery, points: List<Point>, center: Position, zoom: Double) = pinLock.withLock {
        val current = read<PinRecords>(pins)?.entries.orEmpty()
        val entry = CachedPins(query, points, clock.now(), center, zoom)
        val retained = current.filterNot { it.query == query }.map { cached ->
            if (cached.query.filter != query.filter) return@map cached
            val updated = cached.points.filterNot { query.contains(it) } + points.filter { cached.query.contains(it) }
            cached.copy(points = updated.distinctBy { it.id })
        }
        val snapshots = (listOf(entry) + retained).take(6)
        for (size in snapshots.size downTo 1) {
            if (write(pins, PinRecords(snapshots.take(size)))) break
        }
        Unit
    }

    override suspend fun evictPoint(pointId: String) = pinLock.withLock {
        val current = read<PinRecords>(pins) ?: return@withLock
        write(pins, PinRecords(current.entries.map { row -> row.copy(points = row.points.filterNot { it.id == pointId }) }))
        Unit
    }

    override suspend fun readChatList(userId: String): CachedChatList? = chatLock.withLock {
        read<PrivateRecords>(chats)?.takeIf { it.userId == userId }?.list
    }

    override suspend fun saveChatList(userId: String, rows: List<ChatListItem>, complete: Boolean, refresh: Boolean) =
        chatLock.withLock {
            val current = privateRecords(userId)
            val snapshot = CachedChatList(rows.take(300), if (refresh) clock.now() else current.list?.fetchedAt ?: clock.now())
            val rooms = if (complete) current.rooms.filter { room -> rows.any { it.chatId == room.detail.id } } else current.rooms
            savePrivate(current.copy(list = snapshot, rooms = rooms))
        }

    override suspend fun readRoom(userId: String, chatId: String): CachedRoom? = chatLock.withLock {
        read<PrivateRecords>(chats)?.takeIf { it.userId == userId }?.rooms?.firstOrNull {
            it.detail.id == chatId && it.detail.userId == userId
        }
    }

    override suspend fun saveRoom(userId: String, detail: ChatDetail, messages: List<ChatMessage>, nextBeforeSeq: Long?, refresh: Boolean) =
        chatLock.withLock {
            if (detail.userId != userId) return@withLock
            val current = privateRecords(userId)
            val previous = current.rooms.firstOrNull { it.detail.id == detail.id }
            val sorted = messages.filter { it.chatId == detail.id }.distinctBy { it.id }
                .sortedByDescending { it.messageSeq }
            val entry = CachedRoom(
                detail,
                sorted.take(150),
                if (refresh) clock.now() else previous?.fetchedAt ?: return@withLock,
                if (sorted.size > 150) sorted[149].messageSeq else nextBeforeSeq,
            )
            savePrivate(current.copy(rooms = (listOf(entry) + current.rooms.filterNot { it.detail.id == detail.id }).take(12)))
        }

    override suspend fun evictRoom(userId: String, chatId: String) = chatLock.withLock {
        val current = read<PrivateRecords>(chats)?.takeIf { it.userId == userId } ?: return@withLock
        val updated = current.copy(
            list = current.list?.let { list -> list.copy(rows = list.rows.filterNot { it.chatId == chatId }) },
            rooms = current.rooms.filterNot { it.detail.id == chatId },
        )
        if (!write(chats, updated)) ignoreStorageFailure { chats.clear() }
        Unit
    }

    override suspend fun clearPrivate() = chatLock.withLock { chats.clear() }

    private suspend fun privateRecords(userId: String) =
        read<PrivateRecords>(chats)?.takeIf { it.userId == userId } ?: PrivateRecords(userId)

    private suspend fun savePrivate(record: PrivateRecords) {
        for (size in record.rooms.size downTo 0) {
            if (write(chats, record.copy(rooms = record.rooms.take(size)))) return
        }
    }

    private fun ViewportQuery.contains(point: Point): Boolean = with(bbox) {
        point.position.longitude in west..east && point.position.latitude in south..north
    }

    private suspend inline fun <reified T> read(record: OfflineRecord): T? =
        ignoreStorageFailure { record.read()?.let { json.decodeFromString<T>(it) } }

    private suspend inline fun <reified T> write(record: OfflineRecord, value: T): Boolean =
        ignoreStorageFailure {
            val encoded = json.encodeToString(value)
            if (encoded.encodeToByteArray().size > 450_000) false
            else {
                record.replace(encoded)
                true
            }
        } ?: false

    private inline fun <T> ignoreStorageFailure(block: () -> T): T? = try {
        block()
    } catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        null
    }
}

internal class OfflineRecord(
    val read: suspend () -> String?,
    val replace: suspend (String) -> Unit,
    val clear: suspend () -> Unit,
)

@Serializable
private data class PinRecords(val entries: List<CachedPins>)

@Serializable
private data class PrivateRecords(
    val userId: String,
    val list: CachedChatList? = null,
    val rooms: List<CachedRoom> = emptyList(),
)
