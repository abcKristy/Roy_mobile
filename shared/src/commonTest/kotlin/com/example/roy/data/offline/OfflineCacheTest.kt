package com.example.roy.data.offline

import com.example.roy.domain.chat.ChatDetail
import com.example.roy.domain.chat.ChatListItem
import com.example.roy.domain.chat.ChatMessage
import com.example.roy.domain.map.BoundingBox
import com.example.roy.domain.map.Point
import com.example.roy.domain.map.PointFilter
import com.example.roy.domain.map.Position
import com.example.roy.domain.map.ViewportQuery
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class OfflineCacheTest {
    @Test
    fun persistedPinsRetainTheirCoverageAndFreshnessAcrossRestarts() = runTest {
        val fixture = Fixture()
        val area = ViewportQuery(BoundingBox(-2.0, -2.0, 2.0, 2.0))
        val nearer = ViewportQuery(BoundingBox(-1.0, -1.0, 1.0, 1.0))
        val point = Point("point", Position(0.0, 0.0), "Адрес", "manual", 1, emptyList(), first, first)
        fixture.create().savePins(area, listOf(point), point.position, 12.0)
        val restarted = fixture.create()
        assertEquals(listOf(point), restarted.readPins(nearer)?.points)
        assertNull(restarted.readPins(nearer.copy(filter = PointFilter(topicKey = "another"))))
        assertNull(restarted.readPins(ViewportQuery(BoundingBox(-3.0, -3.0, 3.0, 3.0))))
        fixture.instant = second
        restarted.savePins(nearer, emptyList(), point.position, 13.0)
        assertEquals(first, restarted.readPins(area)?.fetchedAt)
        assertEquals(emptyList(), restarted.readPins(area)?.points)
        assertEquals(second, restarted.readPins(nearer)?.fetchedAt)
    }

    @Test
    fun privateHistoryRequiresTheSameAccountAndPaginationDoesNotFreshenTheSnapshot() = runTest {
        val fixture = Fixture()
        val cache = fixture.create()
        val room = ChatDetail("room", "point", "topic", "Тема", "Адрес", "owner", 0, 3)
        val row = ChatListItem("room", "point", "topic", "test", "Тема", "Адрес", null, null, first, 0)
        fun message(seq: Long) = ChatMessage("msg-$seq", "room", seq, "text", null, "Тест", null, first)
        cache.saveChatList("owner", listOf(row), complete = false, refresh = true)
        cache.saveRoom("owner", room, listOf(message(3), message(2)), 2, refresh = true)
        fixture.instant = second
        val restarted = fixture.create()
        restarted.saveRoom("owner", room, listOf(message(3), message(2), message(2), message(1)), null, refresh = false)
        assertEquals(listOf(3L, 2L, 1L), restarted.readRoom("owner", room.id)?.messages?.map { it.messageSeq })
        assertEquals(first, restarted.readRoom("owner", room.id)?.fetchedAt)
        assertNull(restarted.readRoom("another-user", room.id))
        assertNull(restarted.readChatList("another-user"))
        restarted.saveChatList("owner", emptyList(), complete = true, refresh = true)
        assertNull(restarted.readRoom("owner", room.id))
        assertNotNull(restarted.readChatList("owner"))
        restarted.clearPrivate()
        assertNull(fixture.create().readChatList("owner"))
    }

    private class Fixture {
        private var pins: String? = null
        private var chats: String? = null
        var instant = first
        private val clock = object : Clock { override fun now(): Instant = instant }

        fun create() = OfflineCache(
            OfflineRecord({ pins }, { pins = it }, { pins = null }),
            OfflineRecord({ chats }, { chats = it }, { chats = null }),
            clock,
        )
    }

    private companion object {
        val first = Instant.parse("2026-11-16T08:00:00Z")
        val second = Instant.parse("2026-11-16T10:00:00Z")
    }
}
